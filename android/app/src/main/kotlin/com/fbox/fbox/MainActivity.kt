package com.fbox.fbox

import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderApi
import dalvik.system.DexClassLoader
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

class MainActivity : FlutterActivity() {
    private val executor = Executors.newFixedThreadPool(6)
    private val spiders = mutableMapOf<String, Spider>()
    private var loadedJar = ""
    private var loader: DexClassLoader? = null
    private var bridgeServer: SpiderBridgeServer? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        if (bridgeServer == null) {
            bridgeServer = runCatching {
                SpiderBridgeServer(applicationContext, ::invokeSpider, ::invokeProxy)
                    .also { it.start() }
            }.onFailure { android.util.Log.e("FBoxSpiderBridge", "startup failed", it) }
                .getOrNull()
        }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.fbox.app/spider_bridge")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "address" -> result.success(bridgeServer?.address())
                    "localAddress" -> result.success(bridgeServer?.localAddress())
                    else -> result.notImplemented()
                }
            }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.fbox.app/spider")
            .setMethodCallHandler { call, result ->
                if (call.method != "invoke") {
                    result.notImplemented()
                    return@setMethodCallHandler
                }
                val args = call.arguments as? Map<*, *>
                if (args == null) {
                    result.error("bad_arguments", "Spider 参数无效", null)
                    return@setMethodCallHandler
                }
                executor.execute {
                    try {
                        val value = invokeSpider(args)
                        runOnUiThread { result.success(value) }
                    } catch (error: Throwable) {
                        android.util.Log.e("FBoxSpider", "Spider invocation failed", error)
                        runOnUiThread {
                            result.error("spider_error", error.message ?: error.javaClass.simpleName, null)
                        }
                    }
                }
            }
    }

    override fun onDestroy() {
        bridgeServer?.close()
        bridgeServer = null
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun invokeSpider(args: Map<*, *>): String {
        fun string(key: String) = args[key]?.toString() ?: ""
        val jar = string("jar")
        val api = string("api")
        val key = string("key")
        require(jar.isNotBlank() && api.startsWith("csp_") && key.isNotBlank()) {
            "缺少 Spider 包或站源标识"
        }
        val spider = getSpider(jar, api, key, string("ext"))
        return when (string("operation")) {
            "home" -> spider.homeContent(true)
            "homeVideo" -> spider.homeVideoContent()
            "category" -> {
                val filters = HashMap<String, String>()
                (args["filters"] as? Map<*, *>)?.forEach { (k, v) ->
                    filters[k.toString()] = v.toString()
                }
                spider.categoryContent(string("category"), string("page"), true, filters)
            }
            "search" -> spider.searchContent(string("query"), false, string("page"))
            "detail" -> spider.detailContent(listOf(string("id")))
            "player" -> spider.playerContent(string("flag"), string("id"), emptyList())
            else -> error("未知 Spider 操作")
        }
    }

    @Synchronized
    private fun invokeProxy(params: Map<String, String>): Array<Any?>? {
        val spider = spiders[params["siteKey"]] ?: spiders.values.lastOrNull()
        spider?.proxy(params)?.let { return it }
        val activeLoader = loader ?: return null
        val result = activeLoader.loadClass("com.github.catvod.spider.Init")
            .getMethod("proxyInvoke", Map::class.java)
            .invoke(null, params)
        return if (result is Array<*>) Array(result.size) { result[it] } else null
    }

    @Synchronized
    private fun getSpider(jar: String, api: String, key: String, ext: String): Spider {
        if (loadedJar != jar || loader == null) {
            spiders.values.forEach { runCatching { it.destroy() } }
            spiders.clear()
            val jarFile = obtainJar(jar)
            val next = DexClassLoader(
                jarFile.absolutePath,
                codeCacheDir.absolutePath,
                null,
                applicationContext.classLoader
            )
            val init = next.loadClass("com.github.catvod.spider.Init")
            init.getMethod("init", android.content.Context::class.java)
                .invoke(null, applicationContext)
            loader = next
            loadedJar = jar
        }
        return spiders.getOrPut(key) {
            val className = "com.github.catvod.spider.${api.removePrefix("csp_")}"
            val spider = loader!!.loadClass(className).getDeclaredConstructor().newInstance() as Spider
            spider.siteKey = key
            spider.initApi(SpiderApi(bridgeServer?.localAddress(), bridgeServer?.address()))
            spider.init(applicationContext, ext)
            spider
        }
    }

    private fun obtainJar(spec: String): File {
        val parts = spec.split(";md5;", limit = 2)
        val url = parts[0]
        val expected = parts.getOrNull(1)?.lowercase() ?: ""
        require(url.startsWith("https://") || url.startsWith("http://")) { "不支持的 Spider 包地址" }
        val name = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(filesDir, "spider-$name.jar")
        if (file.exists() && (expected.isEmpty() || md5(file) == expected)) return file
        val temporary = File(filesDir, "spider-$name.part")
        temporary.delete()
        val connection = URL(url).openConnection()
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.getInputStream().use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        require(expected.isEmpty() || md5(temporary) == expected) { "Spider 包 MD5 校验失败" }
        file.delete()
        check(temporary.renameTo(file)) { "无法保存 Spider 包" }
        file.setReadOnly()
        return file
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
