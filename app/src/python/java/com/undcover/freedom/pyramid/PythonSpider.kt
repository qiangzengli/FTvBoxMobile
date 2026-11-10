package com.undcover.freedom.pyramid

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.chaquo.python.PyObject
import com.github.catvod.crawler.Spider
import com.google.android.exoplayer2.util.UriUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.HashMap
import java.util.Objects

class PythonSpider @JvmOverloads constructor(
    private val name: String = "",
    cache: String? = PythonLoader.getInstance().getCachePath()
) : Spider() {
    private var app: PyObject? = null
    private var pySpider: PyObject? = null
    private var loadSuccess = false
    private val cachePath: String = cache ?: ""

    constructor(cache: String?) : this("", cache)

    override fun init(context: Context) {
        app?.callAttr("init", pySpider)
    }

    override fun init(context: Context, extend: String) {
        init(context, extend, "")
    }

    fun init(context: Context, url: String, extend: String) {
        val pyApp = PythonLoader.getInstance().pyApp ?: return
        app = pyApp
        val retValue = pyApp.callAttr("downloadPlugin", cachePath, url)
        val uri = Uri.parse(url)
        var extInfo = extend
        if (extInfo.isEmpty()) extInfo = uri.getQueryParameter("extend") ?: ""
        val path = retValue.toString()
        Log.i("PyLoader", "echo-init path: $path")
        val file = File(path)
        if (file.exists()) {
            val spiderObj = pyApp.callAttr("loadFromDisk", path)
            pySpider = spiderObj
            try {
                spiderObj.put("siteKey", name)
            } catch (ignored: Exception) {
            }

            val poList = pyApp.callAttr("getDependence", spiderObj).asList()
            for (po in poList) {
                val api = po.toString()
                Log.i("PyLoader", "echo-init api: $api")
                var depUrl = PythonLoader.getInstance().getUrlByApi(api)
                if (depUrl.isEmpty()) depUrl = resolveDependenceUrl(url, api)
                if (depUrl.isNotEmpty()) {
                    Log.i("PyLoader", "echo-init depUrl: $depUrl")
                    val tmpPath = pyApp.callAttr("downloadPlugin", cachePath, depUrl).toString()
                    if (!File(tmpPath).exists()) {
                        PyToast.showCancelableToast("$api加载失败!")
                        return
                    } else {
                        pyApp.callAttr("registerPluginAlias", api, tmpPath)
                        PyLog.d("$api: 加载插件依赖成功！")
                    }
                }
            }
            pyApp.callAttr("init", spiderObj, extInfo)
            loadSuccess = true
            PyLog.d("$name: 下載插件成功！")
        } else {
            PyToast.showCancelableToast("$name下载插件失败")
        }
    }

    private fun resolveDependenceUrl(baseUrl: String, api: String?): String {
        if (api.isNullOrEmpty()) return ""
        val dep = if (api.endsWith(".py")) api else "$api.py"
        return UriUtil.resolve(baseUrl, dep)
    }

    fun getName(): String {
        return if (name.isEmpty()) {
            val po = app?.callAttr("getName", pySpider)
            po?.toString() ?: ""
        } else {
            name
        }
    }

    fun isLoadSuccess(): Boolean {
        return loadSuccess && pySpider != null
    }

    fun map2json(extend: HashMap<String, String>?): JSONObject {
        val jo = JSONObject()
        try {
            if (extend != null) {
                for (key in extend.keys) {
                    jo.put(key, extend[key])
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return jo
    }

    fun map2json(extend: Map<*, *>?): JSONObject {
        val jo = JSONObject()
        try {
            if (extend != null) {
                for (key in extend.keys) {
                    if (key != null) jo.put(key.toString(), extend[key])
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return jo
    }

    fun list2json(array: List<String>?): JSONArray {
        val ja = JSONArray()
        if (array != null) {
            for (str in array) {
                ja.put(str)
            }
        }
        return ja
    }

    fun paramLog(vararg obj: Any?): String {
        val sb = StringBuilder()
        sb.append("request params:[")
        for (o in obj) {
            sb.append(o).append("-")
        }
        sb.append("]")
        return sb.toString()
    }

    fun proxyLocal(params: Map<String, String>): Array<Any?>? {
        val currentApp = app ?: return null
        val currentSpider = pySpider ?: return null
        val proxyResult = currentApp.callAttr("localProxy", currentSpider, map2json(params).toString()) ?: return null
        val list = proxyResult.asList()
        if (list == null || list.size < 3) return null
        val base64 = list.size > 4 && list[4].toInt() == 1
        val headerAvailable = list.size > 3 && list[3] != null
        val result = arrayOfNulls<Any>(4)
        result[0] = list[0].toInt()
        result[1] = list[1].toString()
        result[2] = getStream(list[2], base64)
        result[3] = if (headerAvailable) getHeader(list[3]) else null
        return result
    }

    private fun getHeader(headerObj: PyObject?): Map<String, String>? {
        if (headerObj == null) return null
        val headerMap = HashMap<String, String>()
        for (key in headerObj.asMap().keys) {
            headerMap[key.toString()] = Objects.requireNonNull(headerObj.asMap()[key]).toString()
        }
        return headerMap
    }

    private fun getStream(o: PyObject?, base64: Boolean): ByteArrayInputStream {
        if (o == null) return ByteArrayInputStream(ByteArray(0))
        val typeStr = o.type().toString()
        if (typeStr.contains("bytes")) return ByteArrayInputStream(o.toJava(ByteArray::class.java))
        var content = o.toString()
        if (base64 && content.contains("base64,")) {
            content = content.split("base64,")[1]
        }
        return ByteArrayInputStream(if (base64) decode(content) else content.toByteArray())
    }

    fun replaceLocalUrl(content: String): String {
        return content.replace("http://127.0.0.1:UndCover/proxy", PythonLoader.getInstance().localProxyUrl())
    }

    override fun homeContent(filter: Boolean): String {
        val currentApp = app ?: return "{}"
        val currentSpider = pySpider ?: return "{}"
        PyLog.nw("homeContent-$name", paramLog(filter))
        val po = currentApp.callAttr("homeContent", currentSpider, filter)
        val rsp = po.toString()
        PyLog.nw("homeContent-$name", rsp)
        return rsp
    }

    override fun homeVideoContent(): String {
        val currentApp = app ?: return "{}"
        val currentSpider = pySpider ?: return "{}"
        PyLog.nw("homeVideoContent-$name", "")
        val po = currentApp.callAttr("homeVideoContent", currentSpider)
        val rsp = po.toString()
        PyLog.nw("homeVideoContent-$name", rsp)
        return rsp
    }

    override fun categoryContent(tid: String, pg: String, filter: Boolean, extend: HashMap<String, String>?): String {
        val currentApp = app ?: return "{}"
        val currentSpider = pySpider ?: return "{}"
        PyLog.nw("categoryContent-$name", paramLog(tid, pg, filter, map2json(extend).toString()))
        val po = currentApp.callAttr("categoryContent", currentSpider, tid, pg, filter, map2json(extend).toString())
        val rsp = po.toString()
        PyLog.nw("categoryContent-$name", rsp)
        return rsp
    }

    override fun detailContent(ids: List<String>): String {
        val currentApp = app ?: return "{}"
        val currentSpider = pySpider ?: return "{}"
        PyLog.nw("detailContent-$name", paramLog(list2json(ids).toString()))
        val po = currentApp.callAttr("detailContent", currentSpider, list2json(ids).toString())
        val rsp = po.toString()
        PyLog.nw("detailContent-$name", rsp)
        return rsp
    }

    override fun searchContent(key: String, quick: Boolean): String {
        val currentApp = app ?: return "{}"
        val currentSpider = pySpider ?: return "{}"
        PyLog.nw("searchContent-$name", paramLog(key, quick))
        val po = currentApp.callAttr("searchContent", currentSpider, key, quick)
        val rsp = po.toString()
        PyLog.nw("searchContent-$name", rsp)
        return rsp
    }

    override fun playerContent(flag: String, id: String, vipFlags: List<String>): String {
        val currentApp = app ?: return "{}"
        val currentSpider = pySpider ?: return "{}"
        PyLog.nw("playerContent-$name", paramLog(flag, id, list2json(vipFlags).toString()))
        val po = currentApp.callAttr("playerContent", currentSpider, flag, id, list2json(vipFlags).toString())
        val rsp = replaceLocalUrl(po.toString())
        PyLog.nw("playerContent-$name", rsp)
        return rsp
    }

    fun liveContent(url: String): String {
        val currentApp = app ?: return "{}"
        val currentSpider = pySpider ?: return "{}"
        PyLog.nw("liveContent-$name", "")
        val po = currentApp.callAttr("liveContent", currentSpider, url)
        val rsp = po.toString()
        PyLog.nw("liveContent-$name", rsp)
        return rsp
    }

    override fun isVideoFormat(url: String): Boolean {
        return false
    }

    override fun manualVideoCheck(): Boolean {
        return false
    }

    override fun destroy() {
        try {
            if (app != null && pySpider != null) app!!.callAttr("destroy", pySpider)
        } catch (ignored: Exception) {
        }
    }

    companion object {
        @JvmStatic
        fun decode(s: String): ByteArray {
            return decode(s, Base64.DEFAULT or Base64.NO_WRAP)
        }

        @JvmStatic
        fun decode(s: String, flags: Int): ByteArray {
            return Base64.decode(s, flags)
        }
    }
}
