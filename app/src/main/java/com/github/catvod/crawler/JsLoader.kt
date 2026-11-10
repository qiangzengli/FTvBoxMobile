package com.github.catvod.crawler

import android.util.Log
import com.github.catvod.crawler.js.JsSpider
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.MD5
import com.lzy.okgo.OkGo
import com.whl.quickjs.wrapper.QuickJSContext
import dalvik.system.DexClassLoader
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class JsLoader {

    @Volatile
    private var recentKey = ""

    companion object {
        private val spiders = ConcurrentHashMap<String, Spider>()
        private val classes = ConcurrentHashMap<String, Class<*>>()

        @JvmStatic
        fun destroy() {
            for (spider in spiders.values) {
                spider.cancelByTag()
                spider.destroy()
            }
        }

        @JvmStatic
        fun stopAll() {
            for (spider in spiders.values) {
                spider.cancelByTag()
            }
        }
    }

    @Synchronized
    fun clear() {
        for (spider in spiders.values) {
            spider.cancelByTag()
            spider.destroy()
        }
        spiders.clear()
        classes.clear()
        recentKey = ""
    }

    private fun loadClassLoader(jar: String, key: String): Boolean {
        var success = false
        var classInit: Class<*>? = null
        try {
            val cacheDir = File(App.getInstance().cacheDir.absolutePath + "/catvod_jsapi")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val classLoader = DexClassLoader(jar, cacheDir.absolutePath, null, App.getInstance().classLoader)
            var count = 0
            do {
                try {
                    try {
                        classInit = classLoader.loadClass("com.github.catvod.js.Function")
                        classInit.getDeclaredConstructor(QuickJSContext::class.java)
                        Log.i("JSLoader", "echo-load_com.github.catvod.js.Function")
                    } catch (ignored: Throwable) {
                        classInit = classLoader.loadClass("com.github.catvod.js.Method")
                        classInit.getDeclaredConstructor(QuickJSContext::class.java)
                        Log.i("JSLoader", "echo-load_com.github.catvod.js.Method")
                    }
                    if (classInit != null) {
                        Log.i("JSLoader", "echo-自定义jsapi代码加载成功!")
                        success = true
                        break
                    }
                    Thread.sleep(200)
                } catch (th: Throwable) {
                    th.printStackTrace()
                }
                count++
            } while (count < 5)

            if (success && classInit != null) {
                classes[key] = classInit
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        return success
    }

    private fun loadJarInternal(jar: String, md5: String, key: String): Class<*>? {
        if (classes.containsKey(key)) {
            Log.i("JSLoader", "echo-loadJarInternal cached")
            return classes[key]
        }
        val cache = File(App.getInstance().filesDir.absolutePath + "/csp/" + key + ".jar")
        if (md5.isNotEmpty()) {
            if (cache.exists() && MD5.getFileMd5(cache).equals(md5, ignoreCase = true)) {
                loadClassLoader(cache.absolutePath, key)
                return classes[key]
            }
        } else {
            if (cache.exists() && !FileUtils.isWeekAgo(cache)) {
                if (loadClassLoader(cache.absolutePath, key)) {
                    return classes[key]
                }
            }
        }
        try {
            val response: Response = OkGo.get<File>(jar).execute()
            val isStream: InputStream = response.body()!!.byteStream()
            val os: OutputStream = FileOutputStream(cache)
            try {
                val buffer = ByteArray(2048)
                var length: Int
                while (isStream.read(buffer).also { length = it } > 0) {
                    os.write(buffer, 0, length)
                }
            } finally {
                try {
                    isStream.close()
                    os.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            loadClassLoader(cache.absolutePath, key)
            return classes[key]
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return null
    }

    @Synchronized
    fun getSpider(key: String, api: String, ext: String, jar: String): Spider {
        recentKey = key
        if (spiders.containsKey(key)) {
            Log.i("JSLoader", "echo-getSpider cached $key")
            return spiders[key]!!
        }
        var classLoader: Class<*>? = null
        if (jar.isNotEmpty()) {
            val urls = jar.split(";md5;".toRegex()).toTypedArray()
            val jarUrl = urls[0]
            val jarKey = MD5.string2MD5(jarUrl)
            val jarMd5 = if (urls.size > 1) urls[1].trim() else ""
            classLoader = loadJarInternal(jarUrl, jarMd5, jarKey)
        }
        try {
            Log.i("JSLoader", "echo-getSpider load")
            val sp: Spider = JsSpider(key, api, classLoader)
            sp.siteKey = key
            sp.init(App.getInstance(), ext)
            spiders[key] = sp
            return sp
        } catch (th: Throwable) {
            LOG.i("echo-getSpider-error " + th.message)
        }
        return SpiderNull()
    }

    fun proxyInvoke(params: Map<String, String>?): Array<Any?>? {
        try {
            val proxyFun = spiders[recentKey]
            if (proxyFun != null) {
                return proxyFun.proxyLocal(params)
            }
        } catch (ignored: Throwable) {
        }
        return null
    }
}
