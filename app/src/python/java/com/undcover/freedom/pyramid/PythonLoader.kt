package com.undcover.freedom.pyramid

import android.app.Application
import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.github.catvod.Proxy
import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderNull
import com.github.catvod.net.OkHttp
import com.github.tvbox.osc.util.OkGoHelper
import okhttp3.HttpUrl
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class PythonLoader {
    private val spiders = ConcurrentHashMap<String, Spider>()
    private var app: Application? = null
    private val siteMap = HashMap<String, JSONObject>()
    @JvmField var pyInstance: Python? = null
    @JvmField var pyApp: PyObject? = null
    @JvmField var androidPlatform: Python.Platform? = null
    private var cache = ""
    private var streamCallback: FileStreamCallback? = null
    private var stringCallback: FileStringCallback? = null

    companion object {
        private var sInstance: PythonLoader? = null

        @JvmStatic
        fun getInstance(): PythonLoader {
            if (sInstance == null) {
                synchronized(PyToast::class.java) {
                    if (sInstance == null) {
                        sInstance = PythonLoader()
                    }
                }
            }
            return sInstance!!
        }
    }

    fun clear() {
        for (spider in spiders.values) {
            spider.destroy()
        }
        spiders.clear()
        siteMap.clear()
    }

    private fun setSdk(context: Context) {
        val logLevel = PyLog.LEVEL_V
        PyLog.getInstance().setLogLevel(logLevel).setFilter(PyLog.FILTER_NW or PyLog.FILTER_LC)
        PyLog.TagConstant.TAG_APP = "PythonLoader"
        PyToast.init(context)
    }

    fun setConfig(config: String?) {
        if (config == null) return
        try {
            siteMap.clear()
            val configJo = JSONObject(config)
            val siteList = configJo.getJSONArray("sites")
            for (i in 0 until siteList.length()) {
                val jo = siteList.getJSONObject(i)
                val key = jo.optString("api")
                siteMap[key] = jo
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun setApplication(app: Application): PythonLoader {
        this.app = app
        setSdk(app)
        if (pyInstance == null) {
            try {
                if (!Python.isStarted()) {
                    val platform = AndroidPlatform(app)
                    androidPlatform = platform
                    Python.start(platform)
                }
                val instance = Python.getInstance()
                pyInstance = instance
                pyApp = instance.getModule("app")
            } catch (th: Throwable) {
                throw RuntimeException(th)
            }
        }
        val pyCache = File(app.cacheDir, "py")
        if (!pyCache.exists()) pyCache.mkdirs()
        setPluginConfig(pyCache.absolutePath)
        return this
    }

    fun setPluginConfig(config: String?): PythonLoader {
        cache = when {
            config.isNullOrEmpty() -> ""
            config.endsWith(File.separator) -> config
            else -> config + File.separator
        }
        return this
    }

    fun getCachePath(): String = cache

    fun getUrlByApi(api: String?): String {
        var key = ""
        var url = ""
        if (siteMap.containsKey(api)) {
            val jo = siteMap[api]
            if (jo != null) {
                key = jo.optString("key")
                url = jo.optString("ext")
            }
        }
        if (key.isNotEmpty() && url.isNotEmpty()) {
            return if (spiders.containsKey(key)) "" else url
        }
        return ""
    }

    @Throws(Exception::class)
    fun getSpider(key: String, url: String): Spider {
        return getSpider(key, url, "")
    }

    @Throws(Exception::class)
    fun getSpider(key: String, url: String, ext: String): Spider {
        val currentApp = app ?: throw Exception("set application first")
        if (spiders.containsKey(key)) {
            PyLog.d("$key :缓存加载成功！")
            return spiders[key]!!
        }

        val executor = Executors.newSingleThreadExecutor()
        var future: Future<*>? = null
        try {
            val sp = PythonSpider(key, cache)
            future = executor.submit {
                try {
                    sp.init(currentApp, url, ext)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            future.get(30, TimeUnit.SECONDS)
            if (!sp.isLoadSuccess()) return SpiderNull()
            spiders[key] = sp
            return sp
        } catch (e: TimeoutException) {
            PyLog.e("echo-init方法执行超时")
        } catch (e: ExecutionException) {
            PyLog.e("echo-init:ExecutionException|InterruptedException")
        } catch (e: InterruptedException) {
            PyLog.e("echo-init:ExecutionException|InterruptedException")
        } finally {
            if (future != null && !future.isDone) {
                future.cancel(true)
            }
            executor.shutdown()
        }
        return SpiderNull()
    }

    fun localProxyUrl(): String {
        return Proxy.getUrl(true)
    }

    fun str2map(header: String?): Map<String, String> {
        val map = HashMap<String, String>()
        if (header.isNullOrEmpty()) return map
        try {
            val jo = JSONObject(header)
            val it = jo.keys()
            while (it.hasNext()) {
                val key = it.next()
                map[key] = jo.optString(key)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return map
    }

    fun getFileStream(url: String, param: String?, header: String?): InputStream {
        val callback = streamCallback
        if (callback != null) {
            return callback.get(url, str2map(param), str2map(header))
        }
        return try {
            var client = OkGoHelper.getDefaultClient()
            if (client == null) client = OkHttp.client()
            val response = client.newCall(getRequest(url, str2map(param), str2map(header))).execute()
            if (response.body() != null) {
                response.body()!!.byteStream()
            } else {
                response.close()
                ByteArrayInputStream(ByteArray(0))
            }
        } catch (e: Exception) {
            ByteArrayInputStream(ByteArray(0))
        }
    }

    fun getFileString(url: String, header: String?): String {
        val callback = stringCallback
        return if (callback != null) {
            callback.get(url, str2map(header))
        } else {
            OkHttp.string(url, str2map(header))
        }
    }

    private fun getRequest(url: String, paramsMap: Map<String, String>?, headerMap: Map<String, String>?): Request {
        val httpUrl = HttpUrl.parse(url)
        var builtHttpUrl: HttpUrl? = null
        if (httpUrl != null && !paramsMap.isNullOrEmpty()) {
            val builder = httpUrl.newBuilder()
            for ((key, value) in paramsMap) {
                builder.addQueryParameter(key, value)
            }
            builtHttpUrl = builder.build()
        }
        val builder = Request.Builder()
        if (builtHttpUrl != null) {
            builder.url(builtHttpUrl)
        } else {
            builder.url(url)
        }
        if (headerMap != null) {
            for ((key, value) in headerMap) {
                builder.addHeader(key, value)
            }
        }
        return builder.build()
    }

    fun setFileStreamCallback(callback: FileStreamCallback?): PythonLoader {
        streamCallback = callback
        return this
    }

    fun setFileStringCallback(callback: FileStringCallback?): PythonLoader {
        stringCallback = callback
        return this
    }

    fun interface FileStreamCallback {
        fun get(url: String, paramsMap: Map<String, String>, headerMap: Map<String, String>): InputStream
    }

    fun interface FileStringCallback {
        fun get(url: String, headerMap: Map<String, String>): String
    }
}
