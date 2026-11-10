package com.github.catvod.crawler

import android.content.Context
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import com.github.catvod.Proxy
import com.github.catvod.net.OkHttp
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.server.RemoteServer
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.MD5
import com.lzy.okgo.OkGo
import dalvik.system.DexClassLoader
import okhttp3.Response
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.reflect.Method
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

class JarLoader {

    private val loaders = ConcurrentHashMap<String, DexClassLoader>()
    private val proxyMethods = ConcurrentHashMap<String, Method>()
    private val danmuClickMethods = ConcurrentHashMap<String, Method>()
    private val danmuLongClickMethods = ConcurrentHashMap<String, Method>()
    private val spiders = ConcurrentHashMap<String, Spider>()
    private val locks = ConcurrentHashMap<String, Any>()
    private val siteJarKeys = ConcurrentHashMap<String, String>()
    private val aliases = ConcurrentHashMap<String, String>()
    private val protectedInitJar = ProtectedInitJar()
    @Volatile
    private var recent = MAIN_KEY

    fun load(cache: String?): Boolean {
        if (cache == null) return false
        val success = load(MAIN_KEY, File(cache))
        if (success) recent = MAIN_KEY
        return success
    }

    fun setRecentJarKey(key: String?) {
        if (TextUtils.isEmpty(key)) return
        recent = realKey(key!!)
        injectProxyPort(loaders[recent])
    }

    fun loadLiveJar(jar: String?) {
        if (jar == null) return
        val key = jarKey(jar)
        parseJar(key, jar)
        setRecentJarKey(key)
    }

    fun clear() {
        for (spider in spiders.values) {
            try {
                spider.destroy()
            } catch (ignored: Throwable) {
            }
        }
        loaders.clear()
        proxyMethods.clear()
        danmuClickMethods.clear()
        danmuLongClickMethods.clear()
        spiders.clear()
        locks.clear()
        siteJarKeys.clear()
        aliases.clear()
        recent = MAIN_KEY
    }

    private fun load(key: String, file: File): Boolean {
        if (Thread.interrupted()) return false
        if (!exists(file)) return false
        if (loaders.containsKey(key)) return true
        return try {
            file.setReadOnly()
            val cachePath = jarDir().absolutePath
            val loader = DexClassLoader(file.absolutePath, cachePath, cachePath, App.getInstance().classLoader)
            if (!invokeInit(loader, file.absolutePath)) {
                Log.i(TAG, "load error key=$key, protected jar loader not bound")
                return false
            }
            invokeProxy(key, loader)
            invokeDanmaku(key, loader)
            injectProxyPort(loader)
            loaders[key] = loader
            Log.i(TAG, "load success key=$key, file=" + file.absolutePath)
            true
        } catch (e: Throwable) {
            Log.i(TAG, "load error key=" + key + ", msg=" + e.message)
            e.printStackTrace()
            false
        }
    }

    private fun invokeInit(loader: DexClassLoader, jar: String): Boolean {
        var protectedJar = false
        try {
            val clz = loader.loadClass("com.github.catvod.spider.Init")
            protectedJar = protectedInitJar.check(jar)
            if (protectedJar) {
                Log.i(TAG, "echo-load initProtectedJar file=$jar")
                return protectedInitJar.init(clz)
            } else {
                val method = clz.getMethod("init", Context::class.java)
                method.invoke(null, App.getInstance())
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return !protectedJar
    }

    private fun invokeProxy(key: String, loader: DexClassLoader) {
        try {
            val clz = loader.loadClass("com.github.catvod.spider.Proxy")
            val method = clz.getMethod("proxy", MutableMap::class.java)
            proxyMethods[key] = method
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun invokeDanmaku(key: String, loader: DexClassLoader) {
        try {
            val clz = loader.loadClass("com.github.catvod.spider.Danmaku")
            try {
                danmuClickMethods[key] = clz.getMethod("onClick", String::class.java, String::class.java)
            } catch (ignored: Throwable) {
            }
            try {
                danmuLongClickMethods[key] = clz.getMethod("onLongClick", String::class.java, String::class.java)
            } catch (ignored: Throwable) {
            }
        } catch (ignored: Throwable) {
        }
    }

    fun parseJar(key: String?, jar: String?) {
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(jar)) return
        if (loaders.containsKey(key)) return
        val lock = lock(key!!)
        synchronized(lock) {
            if (loaders.containsKey(key)) return
            var source = jar!!
            var md5 = ""
            val texts = jar.split(";md5;".toRegex()).toTypedArray()
            if (texts.size > 1) {
                source = texts[0]
                md5 = texts[1].trim()
            }
            aliases[jarKey(source)] = key
            if (md5.startsWith("http")) {
                val value = OkHttp.string(md5, null)
                md5 = value.trim()
            }
            val file = fileForJar(source)
            if (!TextUtils.isEmpty(md5) && exists(file) && MD5.getFileMd5(file).equals(md5, ignoreCase = true)) {
                load(key, file)
            } else if (TextUtils.isEmpty(md5) && exists(file) && !FileUtils.isWeekAgo(file)) {
                load(key, file)
            } else if (source.startsWith("http")) {
                load(key, download(source, file))
            } else if (source.startsWith("assets")) {
                load(key, copyAsset(source, file))
            } else if (source.startsWith("file")) {
                load(key, local(source))
            } else if (source.startsWith("clan://")) {
                load(key, download(clanToAddress(source), file))
            }
            Unit
        }
    }

    fun dex(jar: String?): DexClassLoader? {
        return try {
            val key = jarKey(jar)
            parseJar(key, jar)
            loaders[key]
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun getSpider(key: String?, api: String?, ext: String?, jar: String?): Spider {
        val safeKey = key ?: ""
        val safeApi = api ?: ""
        val safeExt = ext ?: ""
        val safeJar = jar ?: ""
        if (TextUtils.isEmpty(safeApi)) return SpiderNull()

        val jaKey = if (TextUtils.isEmpty(safeJar)) MAIN_KEY else jarKey(safeJar)
        val spKey = jaKey + safeKey
        recent = jaKey
        siteJarKeys[safeKey] = jaKey
        injectProxyPort(loaders[jaKey])

        val cached = spiders[spKey]
        if (cached != null) {
            Log.i(TAG, "getSpider cached key=$spKey")
            return cached
        }

        return try {
            if (MAIN_KEY != jaKey) parseJar(jaKey, safeJar)
            val loader = loaders[jaKey] ?: return SpiderNull()
            val spider = loader.loadClass("com.github.catvod.spider." + className(safeApi)).newInstance() as Spider
            spider.siteKey = safeKey
            spider.initApi(SpiderApi())
            spider.init(App.getInstance(), safeExt)
            spiders[spKey] = spider
            Log.i(TAG, "getSpider success key=$spKey")
            spider
        } catch (e: Throwable) {
            Log.i(TAG, "getSpider error key=" + spKey + ", msg=" + e.message)
            e.printStackTrace()
            SpiderNull()
        }
    }

    fun searchDanmuUi(name: String?, episode: String?, longClick: Boolean) {
        try {
            val methods = if (longClick) danmuLongClickMethods else danmuClickMethods
            var method = methods[recent]
            if (method == null) method = methods["main"]
            if (method == null) return
            method.invoke(null, name, episode)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun hasDanmuSearchUi(): Boolean {
        return danmuClickMethods.containsKey(recent) || danmuLongClickMethods.containsKey(recent)
    }

    fun jsonExt(key: String?, jxs: LinkedHashMap<String, String>?, url: String?): JSONObject? {
        return try {
            val clz = loadParserClass("com.github.catvod.parser.Json$key")
            val method = clz.getMethod("parse", LinkedHashMap::class.java, String::class.java)
            method.invoke(null, jxs, url) as? JSONObject
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun jsonExtMix(flag: String?, key: String?, name: String?, jxs: LinkedHashMap<String, HashMap<String, String>>?, url: String?): JSONObject? {
        return try {
            val clz = loadParserClass("com.github.catvod.parser.Mix$key")
            val method = clz.getMethod("parse", LinkedHashMap::class.java, String::class.java, String::class.java, String::class.java)
            method.invoke(null, jxs, name, flag, url) as? JSONObject
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun proxyInvoke(params: Map<String, String>?): Array<Any?>? {
        val siteKey = params?.get("siteKey")
        if (!TextUtils.isEmpty(siteKey)) {
            val result = proxyInvoke(proxyMethods[siteJarKeys[siteKey]], params)
            if (result != null) return result
        }
        var result = proxyInvoke(proxyMethods[recent], params)
        if (result != null) return result
        for (entry in proxyMethods.entries) {
            if (entry.key == recent) continue
            result = proxyInvoke(entry.value, params)
            if (result != null) return result
        }
        return null
    }

    private fun proxyInvoke(method: Method?, params: Map<String, String>?): Array<Any?>? {
        return try {
            if (method == null) null else method.invoke(null, params) as? Array<Any?>
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    private fun requireRecentLoader(): DexClassLoader {
        var loader = loaders[recent]
        if (loader == null) loader = loaders[MAIN_KEY]
        return loader ?: throw IllegalStateException("No jar loaded for recent key: $recent")
    }

    @Throws(ClassNotFoundException::class)
    private fun loadParserClass(name: String): Class<*> {
        var loader = loaders[recent]
        if (loader != null) {
            try {
                return loader.loadClass(name)
            } catch (ignored: ClassNotFoundException) {
            }
        }
        loader = loaders[MAIN_KEY]
        if (loader != null) return loader.loadClass(name)
        throw ClassNotFoundException(name)
    }

    private fun download(url: String, file: File): File {
        var isStream: InputStream? = null
        var os: FileOutputStream? = null
        try {
            val response: Response = OkGo.get<File>(url).execute()
            if (response.body() == null) return file
            isStream = response.body()!!.byteStream()
            os = FileOutputStream(create(file))
            val buffer = ByteArray(16384)
            var length: Int
            while (isStream.read(buffer).also { length = it } != -1) {
                if (Thread.interrupted()) return file
                os.write(buffer, 0, length)
            }
            os.flush()
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            close(isStream)
            close(os)
        }
        return file
    }

    private fun copyAsset(url: String, file: File): File {
        var isStream: InputStream? = null
        var os: FileOutputStream? = null
        try {
            val path = url.replace("assets://", "").replace("assets/", "")
            isStream = App.getInstance().assets.open(path)
            os = FileOutputStream(create(file))
            val buffer = ByteArray(16384)
            var length: Int
            while (isStream.read(buffer).also { length = it } != -1) {
                os.write(buffer, 0, length)
            }
            os.flush()
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            close(isStream)
            close(os)
        }
        return file
    }

    private fun local(path: String): File {
        val cleanPath = path.replace("file:/", "")
        val file = File(Environment.getExternalStorageDirectory(), cleanPath)
        return if (file.exists()) file else File(cleanPath)
    }

    private fun fileForJar(jar: String): File {
        return File(jarDir(), jarKey(jar) + ".jar")
    }

    private fun jarDir(): File {
        val dir = File(App.getInstance().cacheDir, "jar")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Throws(Exception::class)
    private fun create(file: File): File {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        if (file.exists()) file.delete()
        file.createNewFile()
        file.setReadable(true)
        file.setWritable(true)
        file.setExecutable(true)
        return file
    }

    private fun exists(file: File?): Boolean {
        return file != null && file.exists() && file.length() > 0
    }

    private fun lock(key: String): Any {
        val lock = locks[key]
        if (lock != null) return lock
        val created = Any()
        val old = locks.putIfAbsent(key, created)
        return old ?: created
    }

    private fun jarKey(jar: String?): String {
        val key = MD5.string2MD5(jar ?: "")
        return if (TextUtils.isEmpty(key)) MAIN_KEY else key
    }

    private fun realKey(key: String): String {
        val alias = aliases[key]
        return if (TextUtils.isEmpty(alias)) key else alias!!
    }

    private fun className(api: String): String {
        return if (api.contains("csp_")) api.split("csp_".toRegex()).toTypedArray()[1] else api
    }

    private fun clanToAddress(url: String): String {
        if (url.startsWith("clan://localhost/")) {
            return url.replace("clan://localhost/", ControlManager.get().getAddress(true) + "file/")
        }
        if (url.startsWith("clan://")) {
            val text = url.substring(7)
            val index = text.indexOf('/')
            if (index > 0) return "http://" + text.substring(0, index) + "/file/" + text.substring(index + 1)
        }
        return url
    }

    private fun injectProxyPort(loader: DexClassLoader?) {
        Proxy.set(getServerPort())
        if (loader == null) return
        try {
            val proxy = loader.loadClass("com.github.catvod.Proxy")
            val set = proxy.getMethod("set", Int::class.javaPrimitiveType)
            set.invoke(null, getServerPort())
        } catch (ignored: Throwable) {
        }
    }

    private fun getServerPort(): Int {
        try {
            val address = ControlManager.get().getAddress(true)
            if (address != null && address.startsWith("http://127.0.0.1:")) {
                val baseUrl = if (address.endsWith("/")) address.substring(0, address.length - 1) else address
                return baseUrl.substring(baseUrl.lastIndexOf(":") + 1).toInt()
            }
        } catch (ignored: Throwable) {
        }
        return RemoteServer.serverPort
    }

    private fun close(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (ignored: Throwable) {
        }
    }

    companion object {
        private const val TAG = "JarLoader"
        private const val MAIN_KEY = "main"
    }
}
