package com.github.tvbox.osc.api

import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Base64
import com.fbox.mobile.LocalVideos
import com.github.catvod.crawler.JarLoader
import com.github.catvod.crawler.JsLoader
import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderNull
import com.github.catvod.crawler.pyLoader
import com.github.catvod.crawler.python.IPyLoader
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.bean.IJKCode
import com.github.tvbox.osc.bean.LiveChannelGroup
import com.github.tvbox.osc.bean.LiveChannelItem
import com.github.tvbox.osc.bean.LiveSettingGroup
import com.github.tvbox.osc.bean.LiveSettingItem
import com.github.tvbox.osc.bean.ParseBean
import com.github.tvbox.osc.bean.ProxyRule
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.util.AES
import com.github.tvbox.osc.util.AdBlocker
import com.github.tvbox.osc.util.DefaultConfig
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.M3u8
import com.github.tvbox.osc.util.MD5
import com.github.tvbox.osc.util.OkGoHelper
import com.github.tvbox.osc.util.Proxy
import com.github.tvbox.osc.util.RegexUtils.getPattern
import com.github.tvbox.osc.util.VideoParseRuler
import com.github.tvbox.osc.util.live.TxtSubscribe
import com.google.android.exoplayer2.util.UriUtil
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.orhanobut.hawk.Hawk
import org.json.JSONObject
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLDecoder
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ApiConfig private constructor() {
    private val sourceBeanList: LinkedHashMap<String, SourceBean> = LinkedHashMap()
    private var mHomeSource: SourceBean? = null
    private var mDefaultParse: ParseBean? = null
    private val liveChannelGroupList: MutableList<LiveChannelGroup> = ArrayList()
    private val parseBeanList: MutableList<ParseBean> = ArrayList()
    private var vipParseFlags: List<String>? = null
    private var myHosts: MutableMap<String, String>? = null
    private var ijkCodes: MutableList<IJKCode>? = null
    private var spider: String? = null
    private var currentPyKey = ""
    private var currentLivePyKey = ""
    private var currentPlaySourceKey = ""
    private var loadedLiveConfigUrl = ""
    @JvmField
    var wallpaper: String = ""
    @JvmField
    var danmaku: String = ""

    private val emptyHome = SourceBean()

    private val jarLoader = JarLoader()
    private val jsLoader = JsLoader()
    private val pyLoader: IPyLoader = pyLoader()
    private val gson: Gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val configLoadExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val jarLoadExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val danmuSearchExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val warmedSearchSpiderKeys: MutableSet<String> = HashSet()

    private val userAgent = "okhttp/3.15"

    private var TempKey: String? = null
    private var liveSpider = ""
    private var currentLiveSpider: String? = null
    private val liveSettingGroupList: MutableList<LiveSettingGroup> = ArrayList()
    private var searchSourceBeanList: MutableList<SourceBean> = ArrayList()

    init {
        clearLoader()
        Hawk.put(HawkConfig.LIVE_GROUP_LIST, JsonArray())
        loadDefaultConfig()
    }

    companion object {
        private var instance: ApiConfig? = null
        private const val LOAD_JAR_MAX_RETRY = 1
        private var jarCache = "true"

        @JvmStatic
        fun get(): ApiConfig {
            if (instance == null) {
                synchronized(ApiConfig::class.java) {
                    if (instance == null) {
                        instance = ApiConfig()
                    }
                }
            }
            return instance!!
        }

        @JvmStatic
        fun FindResult(json: String, configKey: String?): String {
            var content = json
            try {
                if (AES.isJson(content)) return content
                val pattern = getPattern("[A-Za-z0-9]{8}\\*\\*")
                val matcher = pattern.matcher(content)
                if (matcher.find()) {
                    content = content.substring(content.indexOf(matcher.group()) + 10)
                    content = String(Base64.decode(content, Base64.DEFAULT))
                }
                content = content.trim()
                if (content.startsWith("2423")) {
                    content = content.replace("\\s+".toRegex(), "")
                    val data = content.substring(content.indexOf("2324") + 4, content.length - 26)
                    content = String(AES.toBytes(content)).lowercase()
                    val key = AES.rightPadding(content.substring(content.indexOf("$#") + 2, content.indexOf("#$")), "0", 16)
                    val iv = AES.rightPadding(content.substring(content.length - 13), "0", 16)
                    return AES.CBC(data, key, iv)
                } else if (configKey != null && !AES.isJson(content)) {
                    return AES.ECB(content, configKey)
                } else {
                    return content
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return content
        }

        private fun getImgJar(body: String): ByteArray {
            var b = body
            val pattern = getPattern("[A-Za-z0-9]{8}\\*\\*")
            val matcher = pattern.matcher(b)
            if (matcher.find()) {
                b = b.substring(b.indexOf(matcher.group()) + 10)
                return Base64.decode(b, Base64.DEFAULT)
            }
            return "".toByteArray()
        }

        @JvmStatic
        fun getLiveGroupIndexKey(): String {
            val liveApiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "")
            return if (liveApiUrl.isNullOrEmpty()) {
                HawkConfig.LIVE_GROUP_INDEX
            } else {
                "${HawkConfig.LIVE_GROUP_INDEX}_$liveApiUrl"
            }
        }

        @JvmStatic
        fun getLiveGroupIndex(): Int {
            return Hawk.get(getLiveGroupIndexKey(), 0)
        }

        @JvmStatic
        fun setLiveGroupIndex(index: Int) {
            Hawk.put(getLiveGroupIndexKey(), index)
        }
    }

    private fun configUrl(apiUrl: String): String {
        TempKey = null
        var configUrl = ""
        val pk = ";pk;"
        var url = apiUrl.replace("file://", "clan://localhost/")
        if (url.contains(pk)) {
            val a = url.split(pk.toRegex()).toTypedArray()
            TempKey = a[1]
            if (url.startsWith("clan")) {
                configUrl = clanToAddress(a[0])
            } else if (url.startsWith("http")) {
                configUrl = a[0]
            } else {
                configUrl = "http://" + a[0]
            }
        } else if (url.startsWith("clan")) {
            configUrl = clanToAddress(url)
        } else if (!url.startsWith("http")) {
            configUrl = "http://$url"
        } else {
            configUrl = url
        }
        return configUrl
    }

    fun loadConfig(useCache: Boolean, callback: LoadConfigCallback, activity: Activity?) {
        val apiUrl = Hawk.get(HawkConfig.API_URL, "")
        if (apiUrl.isEmpty()) {
            callback.error("-1")
            return
        }
        val cache = File(App.getInstance().filesDir.absolutePath + "/" + MD5.encode(apiUrl))
        if (useCache && cache.exists()) {
            try {
                val json = readConfigFile(cache)
                if (switchApiCollectionIfNeeded(apiUrl, json)) {
                    loadConfig(false, callback, activity)
                    return
                }
                clearApiLinesIfUnmatched(apiUrl)
                parseJson(apiUrl, json)
                callback.success()
                return
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }
        val configUrl = configUrl(apiUrl)
        val configKey = TempKey

        fetchConfigAsync(apiUrl, configUrl, configKey, object : ConfigFetchCallback {
            override fun success(json: String) {
                try {
                    if (switchApiCollectionIfNeeded(apiUrl, json)) {
                        FileUtils.saveCache(cache, json)
                        loadConfig(false, callback, activity)
                        return
                    }
                    clearApiLinesIfUnmatched(apiUrl)
                    parseJson(apiUrl, json)
                    FileUtils.saveCache(cache, json)
                    callback.success()
                } catch (th: Throwable) {
                    th.printStackTrace()
                    callback.error("配置解析失败")
                }
            }

            override fun error(error: String) {
                if (cache.exists()) {
                    try {
                        val json = readConfigFile(cache)
                        if (switchApiCollectionIfNeeded(apiUrl, json)) {
                            loadConfig(false, callback, activity)
                            return
                        }
                        clearApiLinesIfUnmatched(apiUrl)
                        parseJson(apiUrl, json)
                        callback.success()
                        return
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                }
                callback.error("拉取配置失败\n$error")
            }
        })
    }

    fun loadLiveConfig(useCache: Boolean, callback: LoadConfigCallback) {
        var apiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "")
        if (apiUrl.isEmpty()) {
            apiUrl = Hawk.get(HawkConfig.API_URL, "")
        }
        if (apiUrl.isEmpty()) {
            callback.error("-1")
            return
        }
        val liveApiUrl = apiUrl
        val liveApiConfigUrl = configUrl(liveApiUrl)
        val liveConfigKey = TempKey
        val live_cache = File(App.getInstance().filesDir.absolutePath + "/" + MD5.encode(liveApiUrl))
        LOG.i("echo-load live config $liveApiUrl")
        if (useCache && live_cache.exists()) {
            try {
                parseLiveConfigContent(liveApiUrl, live_cache)
                if (hasLiveConfigResult()) {
                    loadedLiveConfigUrl = liveApiUrl
                    callback.success()
                    return
                }
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }
        fetchConfigAsync(liveApiUrl, liveApiConfigUrl, liveConfigKey, object : ConfigFetchCallback {
            override fun success(json: String) {
                try {
                    parseLiveConfigContent(liveApiUrl, json)
                    if (!hasLiveConfigResult()) {
                        callback.error("直播配置解析失败")
                        return
                    }
                    loadedLiveConfigUrl = liveApiUrl
                    FileUtils.saveCache(live_cache, json)
                    callback.success()
                } catch (th: Throwable) {
                    th.printStackTrace()
                    callback.error("直播配置解析失败")
                }
            }

            override fun error(error: String) {
                if (live_cache.exists()) {
                    try {
                        parseLiveConfigContent(liveApiUrl, live_cache)
                        if (hasLiveConfigResult()) {
                            loadedLiveConfigUrl = liveApiUrl
                            callback.success()
                            return
                        }
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                }
                callback.error("直播配置拉取失败")
            }
        })
    }

    private fun hasLiveConfigResult(): Boolean {
        return liveChannelGroupList.isNotEmpty()
    }

    fun shouldReloadLiveConfig(): Boolean {
        var apiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "")
        if (apiUrl.isEmpty()) apiUrl = Hawk.get(HawkConfig.API_URL, "")
        return liveChannelGroupList.isEmpty() || apiUrl != loadedLiveConfigUrl
    }

    fun loadJar(useCache: Boolean, spider: String, callback: LoadConfigCallback) {
        loadJar(useCache, spider, callback, 0)
    }

    private fun interface JarLoadCallback {
        fun complete(success: Boolean)
    }

    private fun interface JarDownloadCallback {
        fun complete(file: File?, error: String)
    }

    private interface ConfigFetchCallback {
        fun success(body: String)
        fun error(error: String)
    }

    private fun fetchConfigAsync(apiUrl: String, requestUrl: String, configKey: String?, callback: ConfigFetchCallback) {
        configLoadExecutor.execute {
            var result = ""
            var error = ""
            var response: okhttp3.Response? = null
            try {
                val request = okhttp3.Request.Builder().url(requestUrl).build()
                var client = OkGoHelper.getDefaultClient()
                if (client == null) client = com.github.catvod.net.OkHttp.client()
                response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    error = "HTTP ${response.code()}"
                } else if (response.body() == null) {
                    error = "empty body"
                } else {
                    result = FindResult(response.body()!!.string(), configKey)
                    if (apiUrl.startsWith("clan")) {
                        result = clanContentFix(clanToAddress(apiUrl), result)
                    }
                    result = fixContentPath(apiUrl, result)
                }
            } catch (th: Throwable) {
                error = th.message ?: th.toString()
            } finally {
                if (response != null) closeQuietly(response.body())
            }
            val finalResult = result
            val finalError = error
            mainHandler.post {
                if (TextUtils.isEmpty(finalError)) {
                    callback.success(finalResult)
                } else {
                    callback.error(finalError)
                }
            }
        }
    }

    private fun loadJarAsync(file: File?, callback: JarLoadCallback) {
        jarLoadExecutor.execute {
            var success = false
            try {
                success = file != null && file.exists() && jarLoader.load(file.absolutePath)
            } catch (th: Throwable) {
                LOG.e("echo---jar Loader threw exception: ${th.message}")
            }
            mainHandler.post {
                callback.complete(success)
            }
        }
    }

    private fun downloadJarAsync(url: String, isJarInImg: Boolean, cache: File, callback: JarDownloadCallback) {
        jarLoadExecutor.execute {
            var result: File? = null
            var error = ""
            var response: okhttp3.Response? = null
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            val temp = File(cache.absolutePath + ".tmp")
            try {
                val cacheDir = cache.parentFile
                if (cacheDir != null && !cacheDir.exists()) cacheDir.mkdirs()
                if (temp.exists()) temp.delete()
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .build()
                var client = OkGoHelper.getDefaultClient()
                if (client == null) client = com.github.catvod.net.OkHttp.client()
                response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    error = "HTTP ${response.code()}"
                } else if (response.body() == null) {
                    error = "empty body"
                } else if (isJarInImg) {
                    val respData = response.body()!!.string()
                    LOG.i("echo---jar Response: $respData")
                    val imgJar = getImgJar(respData)
                    if (imgJar.isEmpty()) {
                        error = "empty img jar"
                    } else {
                        outputStream = FileOutputStream(temp)
                        outputStream.write(imgJar)
                        outputStream.flush()
                        closeQuietly(outputStream)
                        outputStream = null
                        result = replaceCache(temp, cache)
                    }
                } else {
                    inputStream = response.body()!!.byteStream()
                    outputStream = FileOutputStream(temp)
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                    closeQuietly(outputStream)
                    outputStream = null
                    result = replaceCache(temp, cache)
                }
            } catch (th: Throwable) {
                error = th.message ?: ""
            } finally {
                closeQuietly(inputStream)
                closeQuietly(outputStream)
                if (response != null) closeQuietly(response.body())
                if (result == null && temp.exists()) temp.delete()
            }
            val finalResult = result
            val finalError = error
            mainHandler.post {
                callback.complete(finalResult, finalError)
            }
        }
    }

    @Throws(IOException::class)
    private fun replaceCache(temp: File, cache: File): File {
        if (cache.exists() && !cache.delete()) {
            LOG.i("echo---delete old jar cache failed:${cache.absolutePath}")
        }
        if (!temp.renameTo(cache)) {
            FileUtils.copyFile(temp, cache)
            temp.delete()
        }
        return cache
    }

    private fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (ignored: Throwable) {
        }
    }

    private fun loadJar(useCache: Boolean, spider: String, callback: LoadConfigCallback, retryCount: Int) {
        val urls = spider.split(";md5;".toRegex()).toTypedArray()
        var jarUrl = urls[0]
        val md5 = if (urls.size > 1) urls[1].trim() else ""
        val cache = File(App.getInstance().filesDir.absolutePath + "/csp/" + MD5.string2MD5(jarUrl) + ".jar")

        if (md5.isNotEmpty() || useCache) {
            if (cache.exists() && (useCache || MD5.getFileMd5(cache).equals(md5, ignoreCase = true))) {
                if (cache.exists()) {
                    loadJarAsync(cache) { success ->
                        if (success) {
                            callback.success()
                        } else {
                            callback.error("md5缓存失效")
                        }
                    }
                    return
                }
                if (jarLoader.load(cache.absolutePath)) {
                    callback.success()
                } else {
                    callback.error("md5缓存失效")
                }
                return
            }
        } else {
            if (jarCache.toBoolean() && cache.exists() && !FileUtils.isWeekAgo(cache)) {
                LOG.i("echo-load jar jarCache:$jarUrl")
                if (cache.exists()) {
                    loadJarAsync(cache) { success ->
                        if (success) {
                            callback.success()
                        } else {
                            loadJar(false, spider, callback, retryCount)
                        }
                    }
                    return
                }
                if (jarLoader.load(cache.absolutePath)) {
                    callback.success()
                    return
                }
            }
        }

        val isJarInImg = jarUrl.startsWith("img+")
        jarUrl = jarUrl.replace("img+", "")
        LOG.i("echo-load jar start:$jarUrl")
        val requestUrl = jarUrl
        downloadJarAsync(requestUrl, isJarInImg, cache) { file, error ->
            fun retryLoad(reason: String): Boolean {
                if (retryCount >= LOAD_JAR_MAX_RETRY) return false
                if (cache.exists() && !cache.delete()) {
                    LOG.i("echo---delete bad jar cache failed:${cache.absolutePath}")
                }
                LOG.i("echo---retry load jar reason:$reason url:$requestUrl retry:${retryCount + 1}")
                loadJar(false, spider, callback, retryCount + 1)
                return true
            }

            if (file != null && file.exists()) {
                loadJarAsync(file) { success ->
                    if (success) {
                        LOG.i("echo---load-jar-success")
                        callback.success()
                    } else {
                        LOG.e("echo---jar Loader returned false")
                        if (!retryLoad("loader_false")) {
                            callback.error("JAR加载失败")
                        }
                    }
                }
                return@downloadJarAsync
            }
            if (!TextUtils.isEmpty(error)) {
                LOG.i("echo---jar Request failed: $error")
            }
            if (cache.exists()) {
                loadJarAsync(cache) { success ->
                    if (success) {
                        callback.success()
                    } else {
                        if (!retryLoad("request_error")) {
                            callback.error("网络错误")
                        }
                    }
                }
                return@downloadJarAsync
            }
            if (!retryLoad("request_error")) {
                callback.error("网络错误")
            }
        }
    }

    private fun readConfigFile(f: File): String {
        val bReader = BufferedReader(InputStreamReader(FileInputStream(f), "UTF-8"))
        val sb = StringBuilder()
        var s: String?
        while (bReader.readLine().also { s = it } != null) {
            sb.append(s).append("\n")
        }
        bReader.close()
        return sb.toString()
    }

    private fun switchApiCollectionIfNeeded(apiUrl: String, jsonStr: String): Boolean {
        val apiLines = parseApiCollection(jsonStr)
        if (apiLines.isEmpty()) {
            return false
        }
        val firstApi = HistoryHelper.getApiLineUrl(apiLines[0])
        if (TextUtils.isEmpty(firstApi) || firstApi == apiUrl) {
            return false
        }
        Hawk.put(HawkConfig.API_LINE_LIST, apiLines)
        Hawk.put(HawkConfig.API_LINE_SOURCE, apiUrl)
        Hawk.put(HawkConfig.API_URL, firstApi)
        HistoryHelper.setApiHistory(apiUrl)
        val liveApiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "")
        if (TextUtils.isEmpty(liveApiUrl) || liveApiUrl == apiUrl) {
            Hawk.put(HawkConfig.LIVE_API_URL, firstApi)
            HistoryHelper.setLiveApiHistory(firstApi)
        }
        return true
    }

    private fun parseApiCollection(jsonStr: String): ArrayList<String> {
        val apiLines = ArrayList<String>()
        try {
            val json = trimJsonObject(jsonStr)
            if (TextUtils.isEmpty(json)) {
                return apiLines
            }
            val infoJson = gson.fromJson(json, JsonObject::class.java)
            if (infoJson == null || infoJson.has("sites") || !infoJson.has("urls") || !infoJson.get("urls").isJsonArray) {
                return apiLines
            }
            val urls = infoJson.get("urls").asJsonArray
            for (element in urls) {
                var name = ""
                var url = ""
                if (element.isJsonObject) {
                    val item = element.asJsonObject
                    name = DefaultConfig.safeJsonString(item, "name", "")
                    url = DefaultConfig.safeJsonString(item, "url", "")
                    if (TextUtils.isEmpty(url)) {
                        url = DefaultConfig.safeJsonString(item, "api", "")
                    }
                } else if (element.isJsonPrimitive) {
                    url = element.asString
                }
                if (!TextUtils.isEmpty(url)) {
                    apiLines.add(HistoryHelper.buildApiLine(name, url))
                }
            }
        } catch (ignored: Throwable) {
        }
        return apiLines
    }

    private fun trimJsonObject(content: String?): String {
        if (content == null) return ""
        val trimContent = content.trim()
        val start = trimContent.indexOf("{")
        val end = trimContent.lastIndexOf("}")
        return if (start >= 0 && end > start) {
            trimContent.substring(start, end + 1)
        } else {
            trimContent
        }
    }

    private fun resetConfigData() {
        clearSpiderCache()
        currentPlaySourceKey = ""
        sourceBeanList.clear()
        liveChannelGroupList.clear()
        parseBeanList.clear()
        searchSourceBeanList = ArrayList()
        Hawk.put(HawkConfig.LIVE_GROUP_LIST, JsonArray())
    }

    private fun clearApiLinesIfUnmatched(apiUrl: String) {
        val apiLines = Hawk.get(HawkConfig.API_LINE_LIST, ArrayList<String>())
        if (apiLines.isEmpty()) return
        for (apiLine in apiLines) {
            if (apiUrl == HistoryHelper.getApiLineUrl(apiLine)) {
                return
            }
        }
        HistoryHelper.clearApiLineList()
    }

    private fun parseJson(apiUrl: String, jsonStr: String) {
        resetConfigData()
        val infoJson = gson.fromJson(jsonStr, JsonObject::class.java)
        spider = DefaultConfig.safeJsonString(infoJson, "spider", "")
        jarCache = DefaultConfig.safeJsonString(infoJson, "jarCache", "true")
        danmaku = DefaultConfig.safeJsonString(infoJson, "danmaku", "")
        wallpaper = DefaultConfig.safeJsonString(infoJson, "wallpaper", "")

        var firstSite: SourceBean? = null
        if (infoJson.has("sites") && infoJson.get("sites").isJsonArray) {
            for (opt in infoJson.get("sites").asJsonArray) {
                val obj = opt.asJsonObject
                if (!obj.has("key") || !obj.has("type") || !obj.has("api")) {
                    LOG.i("echo-skip incomplete site config: $obj")
                    continue
                }
                val sb = SourceBean()
                val siteKey = obj.get("key").asString.trim()
                sb.key = siteKey
                sb.name = if (obj.has("name")) obj.get("name").asString.trim() else siteKey
                sb.type = obj.get("type").asInt
                sb.api = obj.get("api").asString.trim()
                sb.searchable = DefaultConfig.safeJsonInt(obj, "searchable", 1)
                sb.quickSearch = DefaultConfig.safeJsonInt(obj, "quickSearch", 1)
                sb.changeable = DefaultConfig.safeJsonInt(obj, "changeable", 1)
                if (siteKey.startsWith("py_")) {
                    sb.filterable = 1
                } else {
                    sb.filterable = DefaultConfig.safeJsonInt(obj, "filterable", 1)
                }
                sb.playerUrl = DefaultConfig.safeJsonString(obj, "playUrl", "")
                sb.ext = DefaultConfig.safeJsonString(obj, "ext", "")
                sb.jar = DefaultConfig.safeJsonString(obj, "jar", "")
                sb.playerType = DefaultConfig.safeJsonInt(obj, "playerType", -1)
                sb.categories = DefaultConfig.safeJsonStringList(obj, "categories")
                sb.timeout = DefaultConfig.safeJsonInt(obj, "timeout", 0)
                sb.clickSelector = DefaultConfig.safeJsonString(obj, "click", "")
                sb.style = DefaultConfig.safeJsonString(obj, "style", "")
                if (firstSite == null) firstSite = sb
                sourceBeanList[siteKey] = sb
            }
        }
        if (sourceBeanList.isNotEmpty()) {
            val home = Hawk.get(HawkConfig.HOME_API, "")
            val sh = getSource(home)
            if (sh == null) {
                if (firstSite != null) setSourceBean(firstSite)
            } else {
                setSourceBean(sh)
            }
        }
        vipParseFlags = DefaultConfig.safeJsonStringList(infoJson, "flags")
        parseBeanList.clear()
        if (infoJson.has("parses")) {
            val parses = infoJson.get("parses").asJsonArray
            for (opt in parses) {
                val obj = opt.asJsonObject
                val pb = ParseBean()
                pb.setName(obj.get("name").asString.trim())
                pb.setUrl(obj.get("url").asString.trim())
                val ext = if (obj.has("ext")) obj.get("ext").asJsonObject.toString() else ""
                pb.setExt(ext)
                pb.setType(DefaultConfig.safeJsonInt(obj, "type", 0))
                parseBeanList.add(pb)
            }
            if (parseBeanList.isNotEmpty()) addSuperParse()
        }
        if (parseBeanList.isNotEmpty()) {
            val defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "")
            if (!TextUtils.isEmpty(defaultParse)) {
                for (pb in parseBeanList) {
                    if (pb.getName() == defaultParse) setDefaultParse(pb)
                }
            }
            if (mDefaultParse == null) setDefaultParse(parseBeanList[0])
        }

        val live_api_url = Hawk.get(HawkConfig.LIVE_API_URL, "")
        if (live_api_url.isEmpty() || apiUrl == live_api_url) {
            LOG.i("echo-load-config_live")
            initLiveSettings()
            if (infoJson.has("lives")) {
                val lives_groups = infoJson.get("lives").asJsonArray
                var live_group_index = getLiveGroupIndex()
                if (live_group_index > lives_groups.size() - 1) live_group_index = 0
                Hawk.put(HawkConfig.LIVE_GROUP_LIST, lives_groups)
                try {
                    val liveSettingItemList = ArrayList<LiveSettingItem>()
                    for (i in 0 until lives_groups.size()) {
                        val jsonObject = lives_groups.get(i).asJsonObject
                        val name = if (jsonObject.has("name")) jsonObject.get("name").asString else "线路${i + 1}"
                        val liveSettingItem = LiveSettingItem()
                        liveSettingItem.setItemIndex(i)
                        liveSettingItem.setItemName(name)
                        liveSettingItemList.add(liveSettingItem)
                    }
                    liveSettingGroupList[5].setLiveSettingItems(liveSettingItemList)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val livesOBJ = lives_groups.get(live_group_index).asJsonObject
                loadLiveApi(livesOBJ)
            }
        }

        myHosts = HashMap()
        if (infoJson.has("hosts")) {
            val hostsArray = infoJson.getAsJsonArray("hosts")
            for (i in 0 until hostsArray.size()) {
                val entry = hostsArray.get(i).asString
                val parts = entry.split("=".toRegex(), limit = 2).toTypedArray()
                if (parts.size == 2) {
                    myHosts!![parts[0]] = parts[1]
                }
            }
        }

        loadProxyRules(infoJson)

        if (infoJson.has("rules")) {
            VideoParseRuler.clearRule()
            for (oneHostRule in infoJson.getAsJsonArray("rules")) {
                val obj = oneHostRule.asJsonObject
                if (obj.has("host")) {
                    val host = obj.get("host").asString
                    if (obj.has("rule")) {
                        val ruleJsonArr = obj.getAsJsonArray("rule")
                        val rule = ArrayList<String>()
                        for (one in ruleJsonArr) {
                            rule.add(one.asString)
                        }
                        if (rule.isNotEmpty()) {
                            VideoParseRuler.addHostRule(host, rule)
                        }
                    }
                    if (obj.has("filter")) {
                        val filterJsonArr = obj.getAsJsonArray("filter")
                        val filter = ArrayList<String>()
                        for (one in filterJsonArr) {
                            filter.add(one.asString)
                        }
                        if (filter.isNotEmpty()) {
                            VideoParseRuler.addHostFilter(host, filter)
                        }
                    }
                }
                if (obj.has("hosts") && obj.has("regex")) {
                    val rule = ArrayList<String>()
                    val ads = ArrayList<String>()
                    val regexArray = obj.getAsJsonArray("regex")
                    for (one in regexArray) {
                        val regex = one.asString
                        if (M3u8.isAd(regex)) ads.add(regex) else rule.add(regex)
                    }
                    val array = obj.getAsJsonArray("hosts")
                    for (one in array) {
                        val host = one.asString
                        VideoParseRuler.addHostRule(host, rule)
                        VideoParseRuler.addHostRegex(host, ads)
                    }
                }
                if (obj.has("hosts") && obj.has("script")) {
                    val scripts = ArrayList<String>()
                    val scriptArray = obj.getAsJsonArray("script")
                    for (one in scriptArray) {
                        scripts.add(one.asString)
                    }
                    val array = obj.getAsJsonArray("hosts")
                    for (one in array) {
                        val host = one.asString
                        VideoParseRuler.addHostScript(host, scripts)
                    }
                }
            }
        }

        if (infoJson.has("doh")) {
            val doh_json = infoJson.getAsJsonArray("doh").toString()
            if (Hawk.get(HawkConfig.DOH_JSON, "") != doh_json) {
                Hawk.put(HawkConfig.DOH_URL, 0)
                Hawk.put(HawkConfig.DOH_JSON, doh_json)
            }
        } else {
            Hawk.put(HawkConfig.DOH_JSON, "")
        }
        OkGoHelper.setDnsList()
        LOG.i("echo-api-config-----------load")
        if (infoJson.has("ads")) {
            for (host in infoJson.getAsJsonArray("ads")) {
                if (!AdBlocker.hasHost(host.asString)) {
                    AdBlocker.addAdHost(host.asString)
                }
            }
        }
    }

    private fun loadDefaultConfig() {
        val defaultIJKADS = "{\"ijk\":[{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"1\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-all-videos\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"0\"},{\"name\":\"max-buffer-size\",\"category\":4,\"value\":\"15728640\"}],\"group\":\"软解码\"},{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"1\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-all-videos\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"1\"},{\"name\":\"max-buffer-size\",\"category\":4,\"value\":\"15728640\"}],\"group\":\"硬解码\"}],\"ads\":[\"mimg.0c1q0l.cn\",\"www.googletagmanager.com\",\"www.google-analytics.com\",\"mc.usihnbcq.cn\",\"mg.g1mm3d.cn\",\"mscs.svaeuzh.cn\",\"cnzz.hhttm.top\",\"tp.vinuxhome.com\",\"cnzz.mmstat.com\",\"www.baihuillq.com\",\"s23.cnzz.com\",\"z3.cnzz.com\",\"c.cnzz.com\",\"stj.v1vo.top\",\"z12.cnzz.com\",\"img.mosflower.cn\",\"tips.gamevvip.com\",\"ehwe.yhdtns.com\",\"xdn.cqqc3.com\",\"www.jixunkyy.cn\",\"sp.chemacid.cn\",\"hm.baidu.com\",\"s9.cnzz.com\",\"z6.cnzz.com\",\"um.cavuc.com\",\"mav.mavuz.com\",\"wofwk.aoidf3.com\",\"z5.cnzz.com\",\"xc.hubeijieshikj.cn\",\"tj.tianwenhu.com\",\"xg.gars57.cn\",\"k.jinxiuzhilv.com\",\"cdn.bootcss.com\",\"ppl.xunzhuo123.com\",\"xomk.jiangjunmh.top\",\"img.xunzhuo123.com\",\"z1.cnzz.com\",\"s13.cnzz.com\",\"xg.huataisangao.cn\",\"z7.cnzz.com\",\"xg.huataisangao.cn\",\"z2.cnzz.com\",\"s96.cnzz.com\",\"q11.cnzz.com\",\"thy.dacedsfa.cn\",\"xg.whsbpw.cn\",\"s19.cnzz.com\",\"z8.cnzz.com\",\"s4.cnzz.com\",\"f5w.as12df.top\",\"ae01.alicdn.com\",\"www.92424.cn\",\"k.wudejia.com\",\"vivovip.mmszxc.top\",\"qiu.xixiqiu.com\",\"cdnjs.hnfenxun.com\",\"cms.qdwght.com\"]}"
        val defaultJson = gson.fromJson(defaultIJKADS, JsonObject::class.java)
        if (AdBlocker.isEmpty()) {
            for (host in defaultJson.getAsJsonArray("ads")) {
                AdBlocker.addAdHost(host.asString)
            }
        }
        if (ijkCodes == null) {
            val list = ArrayList<IJKCode>()
            var foundOldSelect = false
            var ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "硬解码")
            val ijkJsonArray = defaultJson.get("ijk").asJsonArray
            for (opt in ijkJsonArray) {
                val obj = opt.asJsonObject
                val name = obj.get("group").asString
                val baseOpt = LinkedHashMap<String, String>()
                for (cfg in obj.get("options").asJsonArray) {
                    val cObj = cfg.asJsonObject
                    val key = cObj.get("category").asString + "|" + cObj.get("name").asString
                    val value = cObj.get("value").asString
                    baseOpt[key] = value
                }
                val codec = IJKCode()
                codec.setName(name)
                codec.setOption(baseOpt)
                if (name == ijkCodec || TextUtils.isEmpty(ijkCodec)) {
                    codec.selected(true)
                    ijkCodec = name
                    foundOldSelect = true
                } else {
                    codec.selected(false)
                }
                list.add(codec)
            }
            if (!foundOldSelect && list.isNotEmpty()) {
                list[0].selected(true)
            }
            ijkCodes = list
        }
        LOG.i("echo-default-config-----------load")
    }

    private fun parseLiveConfigContent(apiUrl: String, f: File) {
        val bReader = BufferedReader(InputStreamReader(FileInputStream(f), "UTF-8"))
        val sb = StringBuilder()
        var s: String?
        while (bReader.readLine().also { s = it } != null) {
            sb.append(s).append("\n")
        }
        bReader.close()
        parseLiveConfigContent(apiUrl, sb.toString())
    }

    private fun parseLiveConfigContent(apiUrl: String, content: String) {
        val jsonContent = trimJsonObject(content)
        if (!TextUtils.isEmpty(jsonContent)) {
            try {
                val infoJson = gson.fromJson(jsonContent, JsonObject::class.java)
                if (infoJson != null && infoJson.has("lives")) {
                    parseLiveJson(apiUrl, jsonContent)
                    return
                }
            } catch (ignored: Throwable) {
            }
        }
        if (isLiveJsonContent(content)) {
            parseLiveJson(apiUrl, jsonContent)
        } else {
            parseLiveText(apiUrl, content)
        }
    }

    private fun isLiveJsonContent(content: String?): Boolean {
        if (content == null) return false
        var text = content.trim()
        if (text.startsWith("\ufeff")) text = text.substring(1).trim()
        return text.startsWith("{")
    }

    private fun parseLiveText(apiUrl: String, content: String) {
        liveChannelGroupList.clear()
        liveSpider = ""
        currentLiveSpider = ""
        currentLivePyKey = ""
        initLiveSettings()
        Hawk.put(HawkConfig.LIVE_GROUP_LIST, JsonArray())
        Hawk.put(HawkConfig.EPG_URL, extractLiveTextEpg(content))
        Hawk.put(HawkConfig.LIVE_PLAY_TYPE, Hawk.get(HawkConfig.PLAY_TYPE, 2))
        Hawk.put(HawkConfig.LIVE_WEB_HEADER, null)
        val livesArray = TxtSubscribe.parseToJsonArray(content)
        loadLives(livesArray)
        LOG.i("echo-live-text-config-----------load:$apiUrl")
    }

    private fun extractLiveTextEpg(content: String?): String {
        if (content == null) return ""
        val text = content.replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split("\n".toRegex()).toTypedArray()
        for (lineItem in lines) {
            var line = lineItem.trim()
            if (line.startsWith("\ufeff")) line = line.substring(1).trim()
            if (!line.startsWith("#EXTM3U")) continue
            var epg = extractQuotedAttr(line, "x-tvg-url")
            if (epg.isEmpty()) epg = extractQuotedAttr(line, "tvg-url")
            if (epg.isEmpty()) epg = extractQuotedAttr(line, "url-tvg")
            return epg
        }
        return ""
    }

    private fun extractQuotedAttr(line: String, key: String): String {
        val token = "$key=\""
        var start = line.indexOf(token)
        if (start < 0) return ""
        start += token.length
        val end = line.indexOf("\"", start)
        return if (end < 0) "" else line.substring(start, end).trim()
    }

    private fun parseLiveJson(apiUrl: String, jsonStr: String) {
        liveChannelGroupList.clear()
        val infoJson = gson.fromJson(jsonStr, JsonObject::class.java)
        liveSpider = DefaultConfig.safeJsonString(infoJson, "spider", "")
        initLiveSettings()
        if (infoJson.has("lives")) {
            val lives_groups = infoJson.get("lives").asJsonArray
            var live_group_index = getLiveGroupIndex()
            if (live_group_index > lives_groups.size() - 1) live_group_index = 0
            Hawk.put(HawkConfig.LIVE_GROUP_LIST, lives_groups)
            try {
                val liveSettingItemList = ArrayList<LiveSettingItem>()
                for (i in 0 until lives_groups.size()) {
                    val jsonObject = lives_groups.get(i).asJsonObject
                    val name = if (jsonObject.has("name")) jsonObject.get("name").asString else "线路${i + 1}"
                    val liveSettingItem = LiveSettingItem()
                    liveSettingItem.setItemIndex(i)
                    liveSettingItem.setItemName(name)
                    liveSettingItemList.add(liveSettingItem)
                }
                liveSettingGroupList[5].setLiveSettingItems(liveSettingItemList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val livesOBJ = lives_groups.get(live_group_index).asJsonObject
            loadLiveApi(livesOBJ)
        }

        myHosts = HashMap()
        if (infoJson.has("hosts")) {
            val hostsArray = infoJson.getAsJsonArray("hosts")
            for (i in 0 until hostsArray.size()) {
                val entry = hostsArray.get(i).asString
                val parts = entry.split("=".toRegex(), limit = 2).toTypedArray()
                if (parts.size == 2) {
                    myHosts!![parts[0]] = parts[1]
                }
            }
        }
        LOG.i("echo-api-live-config-----------load")
    }

    private fun initLiveSettings() {
        val groupNames = ArrayList(listOf("线路选择", "画面比例", "播放解码", "超时换源", "偏好设置", "多源切换", "配置切换"))
        val itemsArrayList = ArrayList<ArrayList<String>>()
        val sourceItems = ArrayList<String>()
        val scaleItems = ArrayList(listOf("默认", "16:9", "4:3", "填充", "原始", "裁剪"))
        val playerDecoderItems = ArrayList(listOf("ijk硬解", "ijk软解", "exo"))
        val timeoutItems = ArrayList(listOf("5s", "10s", "15s", "20s", "25s", "30s"))
        val personalSettingItems = ArrayList(listOf("显示时间", "显示网速", "换台反转", "跨选分类"))
        val yumItems = ArrayList<String>()
        val liveApiHistoryItems = ArrayList<String>()

        itemsArrayList.add(sourceItems)
        itemsArrayList.add(scaleItems)
        itemsArrayList.add(playerDecoderItems)
        itemsArrayList.add(timeoutItems)
        itemsArrayList.add(personalSettingItems)
        itemsArrayList.add(yumItems)
        itemsArrayList.add(liveApiHistoryItems)

        liveSettingGroupList.clear()
        for (i in groupNames.indices) {
            val liveSettingGroup = LiveSettingGroup()
            val liveSettingItemList = ArrayList<LiveSettingItem>()
            liveSettingGroup.setGroupIndex(i)
            liveSettingGroup.setGroupName(groupNames[i])
            val subItems = itemsArrayList[i]
            for (j in subItems.indices) {
                val liveSettingItem = LiveSettingItem()
                liveSettingItem.setItemIndex(j)
                liveSettingItem.setItemName(subItems[j])
                liveSettingItemList.add(liveSettingItem)
            }
            liveSettingGroup.setLiveSettingItems(liveSettingItemList)
            liveSettingGroupList.add(liveSettingGroup)
        }
        refreshLiveApiHistoryItems()
    }

    fun getLiveSettingGroupList(): List<LiveSettingGroup> {
        return liveSettingGroupList
    }

    fun refreshLiveApiHistoryItems() {
        if (liveSettingGroupList.size < 7) return
        val liveSettingItemList = ArrayList<LiveSettingItem>()
        val history = Hawk.get(HawkConfig.LIVE_API_HISTORY, ArrayList<String>())
        for (i in history.indices) {
            val liveSettingItem = LiveSettingItem()
            liveSettingItem.setItemIndex(i)
            liveSettingItem.setItemName(history[i])
            liveSettingItemList.add(liveSettingItem)
        }
        liveSettingGroupList[6].setLiveSettingItems(liveSettingItemList)
    }

    fun loadLives(livesArray: JsonArray) {
        liveChannelGroupList.clear()
        var groupIndex = 0
        var channelIndex: Int
        var channelNum = 0
        for (groupElement in livesArray) {
            val liveChannelGroup = LiveChannelGroup()
            liveChannelGroup.setLiveChannels(ArrayList())
            liveChannelGroup.setGroupIndex(groupIndex++)
            val groupName = (groupElement as JsonObject).get("group").asString.trim()
            val splitGroupName = groupName.split("_".toRegex(), limit = 2).toTypedArray()
            liveChannelGroup.setGroupName(splitGroupName[0])
            if (splitGroupName.size > 1) {
                liveChannelGroup.setGroupPassword(splitGroupName[1])
            } else {
                liveChannelGroup.setGroupPassword("")
            }
            channelIndex = 0
            for (channelElement in groupElement.get("channels").asJsonArray) {
                val obj = channelElement as JsonObject
                val liveChannelItem = LiveChannelItem()
                liveChannelItem.setChannelName(obj.get("name").asString.trim())
                liveChannelItem.setChannelLogo(DefaultConfig.safeJsonString(obj, "logo", ""))
                liveChannelItem.setChannelEpg(DefaultConfig.safeJsonString(obj, "epg", ""))
                liveChannelItem.setChannelUa(DefaultConfig.safeJsonString(obj, "ua", ""))
                liveChannelItem.setChannelClick(DefaultConfig.safeJsonString(obj, "click", ""))
                liveChannelItem.setChannelFormat(DefaultConfig.safeJsonString(obj, "format", ""))
                liveChannelItem.setChannelOrigin(DefaultConfig.safeJsonString(obj, "origin", ""))
                liveChannelItem.setChannelReferer(DefaultConfig.safeJsonString(obj, "referer", ""))
                liveChannelItem.setChannelTvgId(DefaultConfig.safeJsonString(obj, "tvg-id", ""))
                liveChannelItem.setChannelTvgName(DefaultConfig.safeJsonString(obj, "tvg-name", ""))
                if (obj.has("parse")) {
                    try {
                        liveChannelItem.setChannelParse(obj.get("parse").asInt)
                    } catch (ignored: Throwable) {
                    }
                }
                if (obj.has("catchup")) {
                    var catchupObj = JsonObject()
                    if (obj.get("catchup").isJsonObject) {
                        catchupObj = obj.getAsJsonObject("catchup")
                    } else {
                        catchupObj.addProperty("type", obj.get("catchup").asString)
                        if (obj.has("catchup-source")) catchupObj.addProperty("source", obj.get("catchup-source").asString)
                        if (obj.has("catchup-replace")) catchupObj.addProperty("replace", obj.get("catchup-replace").asString)
                    }
                    liveChannelItem.setChannelCatchup(catchupObj)
                }
                if (obj.has("header") && obj.get("header").isJsonObject) {
                    val headerObj = obj.getAsJsonObject("header")
                    val channelHeader = HashMap<String, String>()
                    for ((key, value) in headerObj.entrySet()) {
                        channelHeader[key] = value.asString
                    }
                    liveChannelItem.setChannelHeader(channelHeader)
                }
                val urls = DefaultConfig.safeJsonStringList(obj, "urls")
                val sourceNames = ArrayList<String>()
                val sourceUrls = ArrayList<String>()
                var sourceIndex = 1
                for (url in urls) {
                    val splitText = url.split("\\$".toRegex(), limit = 2).toTypedArray()
                    sourceUrls.add(splitText[0])
                    if (splitText.size > 1) {
                        sourceNames.add(splitText[1])
                    } else {
                        sourceNames.add("源$sourceIndex")
                    }
                    sourceIndex++
                }
                liveChannelItem.setChannelSourceNames(sourceNames)
                liveChannelItem.setChannelUrls(sourceUrls)
                val channels = liveChannelGroup.getLiveChannels() ?: ArrayList<LiveChannelItem>().also { liveChannelGroup.setLiveChannels(it) }
                if (mergeLiveChannel(channels, liveChannelItem)) {
                    liveChannelItem.setChannelIndex(channelIndex++)
                    liveChannelItem.setChannelNum(++channelNum)
                }
            }
            liveChannelGroupList.add(liveChannelGroup)
        }
    }

    private fun mergeLiveChannel(channelItems: ArrayList<LiveChannelItem>, newItem: LiveChannelItem): Boolean {
        val oldItem = findLiveChannel(channelItems, newItem.getChannelName())
        if (oldItem == null) {
            channelItems.add(newItem)
            return true
        }
        mergeLiveChannelUrls(oldItem, newItem)
        return false
    }

    private fun findLiveChannel(channelItems: ArrayList<LiveChannelItem>, channelName: String?): LiveChannelItem? {
        for (item in channelItems) {
            if (channelName != null && channelName == item.getChannelName()) return item
        }
        return null
    }

    private fun mergeLiveChannelUrls(oldItem: LiveChannelItem, newItem: LiveChannelItem) {
        var oldUrls = oldItem.getChannelUrls()
        var oldSourceNames = oldItem.getChannelSourceNames()
        if (oldUrls == null) {
            oldUrls = ArrayList()
            oldItem.setChannelUrls(oldUrls)
        }
        if (oldSourceNames == null) {
            oldSourceNames = ArrayList()
            oldItem.setChannelSourceNames(oldSourceNames)
        }
        while (oldSourceNames.size < oldUrls.size) {
            oldSourceNames.add("源${oldSourceNames.size + 1}")
        }
        val newUrls = newItem.getChannelUrls()
        val newSourceNames = newItem.getChannelSourceNames() ?: ArrayList()
        if (newUrls == null) return
        for (i in newUrls.indices) {
            val url = newUrls[i]
            if (oldUrls.contains(url)) continue
            oldUrls.add(url)
            if (i < newSourceNames.size) {
                oldSourceNames.add(newSourceNames[i])
            } else {
                oldSourceNames.add("源${oldSourceNames.size + 1}")
            }
        }
        oldItem.setChannelUrls(oldUrls)
        oldItem.setChannelSourceNames(oldSourceNames)
    }

    fun loadLiveApi(livesOBJ: JsonObject) {
        try {
            LOG.i("echo-loadLiveApi")
            liveChannelGroupList.clear()
            currentLiveSpider = ""
            currentLivePyKey = ""
            val lives = livesOBJ.toString()
            val index = lives.indexOf("proxy://")
            var url: String
            if (index != -1) {
                val endIndex = lives.lastIndexOf("\"")
                url = lives.substring(index, endIndex)
                url = DefaultConfig.checkReplaceProxy(url) ?: ""
                val extUrl = Uri.parse(url).getQueryParameter("ext")
                if (!extUrl.isNullOrEmpty()) {
                    var extUrlFix: String = if (extUrl.startsWith("http") || extUrl.startsWith("clan://")) {
                        extUrl
                    } else {
                        String(Base64.decode(extUrl, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                    }
                    extUrlFix = Base64.encodeToString(extUrlFix.toByteArray(Charsets.UTF_8), Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP)
                    url = url.replace(extUrl, extUrlFix)
                }
            } else {
                val api = if (livesOBJ.has("api")) livesOBJ.get("api").asString.trim() else ""
                val type = if (livesOBJ.has("type")) livesOBJ.get("type").asString else (if (isLiveSpiderApi(api)) "3" else "0")
                if (type == "0" || type == "3") {
                    url = if (livesOBJ.has("url")) livesOBJ.get("url").asString else ""
                    if (url.isEmpty()) url = api
                    LOG.i("echo-liveurl$url")
                    if (!url.startsWith("http://127.0.0.1")) {
                        if (url.startsWith("http")) {
                            url = Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP)
                        }
                        url = "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=$url"
                    }
                    if (type == "3") {
                        val jarUrl = if (livesOBJ.has("jar")) livesOBJ.get("jar").asString.trim() else ""
                        LOG.i("echo-liveApi1$api")
                        if (api.contains(".py")) {
                            LOG.i("echo-pyLoader.getSpider")
                            val ext = if (livesOBJ.has("ext") && (livesOBJ.get("ext").isJsonObject || livesOBJ.get("ext").isJsonArray)) {
                                livesOBJ.get("ext").toString()
                            } else {
                                DefaultConfig.safeJsonString(livesOBJ, "ext", "")
                            }
                            currentLivePyKey = MD5.string2MD5(api)
                            currentLiveSpider = api
                            pyLoader.getSpider(currentLivePyKey, api, ext)
                        } else if (api.contains(".js")) {
                            LOG.i("echo-jsLoader.getSpider")
                            val ext = if (livesOBJ.has("ext") && (livesOBJ.get("ext").isJsonObject || livesOBJ.get("ext").isJsonArray)) {
                                livesOBJ.get("ext").toString()
                            } else {
                                DefaultConfig.safeJsonString(livesOBJ, "ext", "")
                            }
                            currentLiveSpider = api
                            jsLoader.getSpider(MD5.string2MD5(api), api, ext, jarUrl)
                        }
                        if (jarUrl.isNotEmpty() && !isLiveSpiderApi(api)) {
                            jarLoader.loadLiveJar(jarUrl)
                            if (TextUtils.isEmpty(currentLiveSpider)) {
                                currentLiveSpider = jarUrl
                            }
                        } else if (liveSpider.isNotEmpty() && !isLiveSpiderApi(api)) {
                            jarLoader.loadLiveJar(liveSpider)
                            if (TextUtils.isEmpty(currentLiveSpider)) {
                                currentLiveSpider = liveSpider
                            }
                        }
                    }
                } else {
                    liveChannelGroupList.clear()
                    return
                }
            }
            if (livesOBJ.has("epg")) {
                val epg = livesOBJ.get("epg").asString
                Hawk.put(HawkConfig.EPG_URL, epg)
            } else {
                Hawk.put(HawkConfig.EPG_URL, "")
            }
            if (livesOBJ.has("playerType")) {
                val livePlayType = livesOBJ.get("playerType").asString
                Hawk.put(HawkConfig.LIVE_PLAY_TYPE, livePlayType)
            } else {
                Hawk.put(HawkConfig.LIVE_PLAY_TYPE, Hawk.get(HawkConfig.PLAY_TYPE, 2))
            }
            if (livesOBJ.has("timeout")) {
                val timeout = Math.max(5, Math.min(30, livesOBJ.get("timeout").asInt))
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, (timeout + 4) / 5 - 1)
            }
            if (livesOBJ.has("header")) {
                val headerObj = livesOBJ.getAsJsonObject("header")
                val liveHeader = HashMap<String, String>()
                for ((key, value) in headerObj.entrySet()) {
                    liveHeader[key] = value.asString
                }
                Hawk.put(HawkConfig.LIVE_WEB_HEADER, liveHeader)
            } else if (livesOBJ.has("ua")) {
                val ua = livesOBJ.get("ua").asString
                val liveHeader = HashMap<String, String>()
                liveHeader["User-Agent"] = ua
                Hawk.put(HawkConfig.LIVE_WEB_HEADER, liveHeader)
            } else {
                Hawk.put(HawkConfig.LIVE_WEB_HEADER, null)
            }
            val liveChannelGroup = LiveChannelGroup()
            liveChannelGroup.setGroupName(url)
            liveChannelGroupList.clear()
            liveChannelGroupList.add(liveChannelGroup)
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    fun setLiveJar(liveJar: String) {
        if (liveJar.contains(".py")) {
            currentLivePyKey = MD5.string2MD5(liveJar)
            pyLoader.getSpider(currentLivePyKey, liveJar, "")
            pyLoader.setRecentPyKey(currentLivePyKey)
        } else if (liveJar.contains(".js")) {
            jsLoader.getSpider(MD5.string2MD5(liveJar), liveJar, "", "")
        } else {
            val jarUrl = if (liveJar.isNotEmpty()) liveJar else liveSpider
            jarLoader.setRecentJarKey(MD5.string2MD5(jarUrl))
        }
        currentLiveSpider = liveJar
    }

    fun getSpider(): String? {
        return spider
    }

    fun getDanmaku(): String {
        return danmaku
    }

    fun getCSP(sourceBean: SourceBean): Spider {
        return if (sourceBean.api.endsWith(".js") || sourceBean.api.contains(".js?")) {
            currentPyKey = ""
            jsLoader.getSpider(sourceBean.key, sourceBean.api, sourceBean.ext, sourceBean.jar)
        } else if (sourceBean.api.contains(".py")) {
            currentPyKey = sourceBean.key
            pyLoader.setRecentPyKey(currentPyKey)
            pyLoader.getSpider(sourceBean.key, sourceBean.api, sourceBean.ext) ?: SpiderNull()
        } else {
            currentPyKey = ""
            jarLoader.getSpider(sourceBean.key, sourceBean.api, sourceBean.ext, sourceBean.jar)
        }
    }

    fun warmSearchSpiders() {
        val sources = ArrayList(sourceBeanList.values)
        val home = getHomeSourceBean()
        val sharedSpiderApis = HashSet<String>()
        val spiderApis = HashSet<String>()
        for (source in sources) {
            if (source.type != 3) continue
            val spiderApiKey = "${source.jar}|${source.api}"
            if (!spiderApis.add(spiderApiKey)) sharedSpiderApis.add(spiderApiKey)
        }
        configLoadExecutor.execute {
            LOG.i("echo-warm-spider start")
            var eligibleCount = 0
            for (source in sources) {
                if (source.type != 3 || !source.isSearchable()) continue
                if (TextUtils.equals(home.key, source.key)) continue
                if (sharedSpiderApis.contains("${source.jar}|${source.api}")) continue
                if (eligibleCount >= 10) break
                eligibleCount++
                val warmKey = "${source.key}|${source.api}|${source.jar}|${source.ext}"
                synchronized(warmedSearchSpiderKeys) {
                    if (warmedSearchSpiderKeys.contains(warmKey)) return@synchronized
                    warmedSearchSpiderKeys.add(warmKey)
                }
                try {
                    getCSP(source)
                } catch (th: Throwable) {
                    LOG.e("echo-warm-search-spider-error ${source.key}:${th.message}")
                }
            }
        }
    }

    fun getPyCSP(url: String): Spider {
        currentLivePyKey = MD5.string2MD5(url)
        currentLiveSpider = url
        return pyLoader.getSpider(currentLivePyKey, url, "") ?: SpiderNull()
    }

    fun getJsCSP(url: String): Spider {
        currentLiveSpider = url
        return jsLoader.getSpider(MD5.string2MD5(url), url, "", "")
    }

    fun getLiveCSP(url: String): Spider {
        return if (url.contains(".js")) getJsCSP(url) else getPyCSP(url)
    }

    fun searchDanmuUi(name: String, episode: String, longClick: Boolean) {
        danmuSearchExecutor.execute {
            try {
                jarLoader.searchDanmuUi(name, episode, longClick)
            } catch (th: Throwable) {
                LOG.e("ApiConfig searchDanmuUi error: ${th.message}")
                th.printStackTrace()
            }
        }
    }

    fun hasDanmuSearchUi(): Boolean {
        return jarLoader.hasDanmuSearchUi()
    }

    fun getLiveConnectTimeoutSeconds(): Int {
        return (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1) + 1) * 5
    }

    private fun isLiveSpiderApi(api: String): Boolean {
        return api.contains(".py") || api.contains(".js")
    }

    fun proxyLocal(param: MutableMap<String, String>): Array<Any?>? {
        val source = getCurrentProxySource(param)
        val api = source.api

        val siteKey = param["siteKey"]
        val action = param["do"]

        val isJs = "js" == action
        val isPy = "py" == action
        val isLive = Hawk.get(HawkConfig.PLAYER_IS_LIVE, false)
        val isApiJs = api.contains(".js")
        val isApiPy = api.contains(".py")

        val canUseType3 = !TextUtils.isEmpty(siteKey) &&
                source.type == 3 &&
                !isJs &&
                !isPy &&
                !isLive &&
                !isApiJs &&
                !isApiPy

        if (canUseType3) {
            try {
                val spider = getCSP(source)
                var result = spider.proxy(param)
                if (result != null) return result

                result = jarLoader.proxyInvoke(param)
                if (result != null) return result

                result = proxyDirect(param)
                if (result != null) return result

                return null
            } catch (th: Throwable) {
                LOG.e("echo-proxy siteKey error: ${th.message}")
                return null
            }
        }

        if (isJs) {
            return jsLoader.proxyInvoke(param)
        }

        if (isLive) {
            val liveApi = currentLiveSpider ?: ""
            if (liveApi.contains(".py")) {
                return pyLoader.proxyInvoke(param, currentLivePyKey)
            }
            if (liveApi.contains(".js")) {
                return jsLoader.proxyInvoke(param)
            }
            return jarLoader.proxyInvoke(param)
        }

        if (isPy) {
            return pyLoader.proxyInvoke(param, getCurrentPyKey())
        }

        if (isApiPy) {
            return pyLoader.proxyInvoke(param, getCurrentPyKey())
        }

        return jarLoader.proxyInvoke(param)
    }

    private fun proxyDirect(param: MutableMap<String, String>): Array<Any?>? {
        try {
            var url = param["url"]
            if (TextUtils.isEmpty(url)) return null
            url = URLDecoder.decode(url, "UTF-8")
            if (!url.startsWith("http://") && !url.startsWith("https://")) return null
            if (!DefaultConfig.isVideoFormat(url)) return null
            if (url.contains(".m3u8")) {
                param["url"] = url
                param["go"] = "live"
                param["type"] = "m3u8"
                return Proxy.itv(param)
            }
            return null
        } catch (th: Throwable) {
            LOG.e("echo-proxy direct fallback error: ${th.message}")
            return null
        }
    }

    private fun getCurrentProxySource(param: MutableMap<String, String>): SourceBean {
        var siteKey = param["siteKey"]
        if (TextUtils.isEmpty(siteKey)) {
            siteKey = currentPlaySourceKey
            if (!TextUtils.isEmpty(siteKey)) param["siteKey"] = siteKey
        }
        val sourceBean = if (TextUtils.isEmpty(siteKey)) null else getSource(siteKey)
        return sourceBean ?: get().getHomeSourceBean()
    }

    fun setCurrentPlaySourceKey(sourceKey: String?) {
        currentPlaySourceKey = sourceKey ?: ""
    }

    private fun getCurrentPyKey(): String {
        val sourceBean = getCurrentProxySource(HashMap())
        if (sourceBean.api.contains(".py")) {
            if (sourceBean.key != currentPyKey) {
                currentPyKey = sourceBean.key
                pyLoader.getSpider(currentPyKey, sourceBean.api, sourceBean.ext)
                pyLoader.setRecentPyKey(currentPyKey)
            }
            return currentPyKey
        }
        return currentPyKey
    }

    fun jsonExt(key: String, jxs: LinkedHashMap<String, String>, url: String): JSONObject? {
        return jarLoader.jsonExt(key, jxs, url)
    }

    fun jsonExtMix(flag: String, key: String, name: String, jxs: LinkedHashMap<String, HashMap<String, String>>, url: String): JSONObject? {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url)
    }

    interface LoadConfigCallback {
        fun success()
        fun error(msg: String)
        fun notice(msg: String)
    }

    interface FastParseCallback {
        fun success(parse: Boolean, url: String, header: Map<String, String>)
        fun fail(code: Int, msg: String)
    }

    fun getSource(key: String?): SourceBean? {
        if (key == null) return null
        if (LocalVideos.SOURCE == key) {
            val local = SourceBean()
            local.key = key
            local.name = "本地视频"
            local.type = -1
            return local
        }
        if (!sourceBeanList.containsKey(key)) {
            if ("push_agent" == key) {
                val sourceBean = SourceBean()
                sourceBean.key = "push_agent"
                sourceBean.name = "推送"
                sourceBean.type = -1
                return sourceBean
            }
            return null
        }
        return sourceBeanList[key]
    }

    fun setSourceBean(sourceBean: SourceBean) {
        this.mHomeSource = sourceBean
        Hawk.put(HawkConfig.HOME_API, sourceBean.key)
    }

    fun setDefaultParse(parseBean: ParseBean) {
        this.mDefaultParse?.setDefault(false)
        this.mDefaultParse = parseBean
        Hawk.put(HawkConfig.DEFAULT_PARSE, parseBean.getName())
        parseBean.setDefault(true)
    }

    fun getDefaultParse(): ParseBean? {
        return mDefaultParse
    }

    fun getSourceBeanList(): List<SourceBean> {
        return ArrayList(sourceBeanList.values)
    }

    fun getSwitchSourceBeanList(): List<SourceBean> {
        return ArrayList(sourceBeanList.values)
    }

    fun getSearchSourceBeanList(): List<SourceBean> {
        if (searchSourceBeanList.isEmpty()) {
            LOG.i("echo-第一次getSearchSourceBeanList")
            val list = ArrayList<SourceBean>()
            for (bean in sourceBeanList.values) {
                if (bean.isSearchable()) {
                    list.add(bean)
                }
            }
            searchSourceBeanList = list
        }
        return searchSourceBeanList
    }

    fun getParseBeanList(): List<ParseBean> {
        return parseBeanList
    }

    fun getVipParseFlags(): List<String>? {
        return vipParseFlags
    }

    fun getHomeSourceBean(): SourceBean {
        return mHomeSource ?: emptyHome
    }

    fun getChannelGroupList(): List<LiveChannelGroup> {
        return liveChannelGroupList
    }

    fun getIjkCodes(): List<IJKCode>? {
        return ijkCodes
    }

    fun getCurrentIJKCode(): IJKCode {
        val codeName = Hawk.get(HawkConfig.IJK_CODEC, "硬解码")
        return getIJKCodec(codeName)
    }

    fun getIJKCodec(name: String): IJKCode {
        val list = ijkCodes
        if (list != null) {
            for (code in list) {
                if (code.getName() == name) return code
            }
            if (list.isNotEmpty()) return list[0]
        }
        val defaultCode = IJKCode()
        defaultCode.setName(name)
        return defaultCode
    }

    fun clanToAddress(lanLink: String): String {
        return if (lanLink.startsWith("clan://localhost/")) {
            lanLink.replace("clan://localhost/", ControlManager.get().getAddress(true) + "file/")
        } else {
            val link = lanLink.substring(7)
            val end = link.indexOf('/')
            "http://" + link.substring(0, end) + "/file/" + link.substring(end + 1)
        }
    }

    fun clanContentFix(lanLink: String, content: String): String {
        val fix = lanLink.substring(0, lanLink.indexOf("/file/") + 6)
        return content.replace("clan://localhost/", fix).replace("file://", fix)
    }

    fun fixContentPath(urlStr: String, content: String): String {
        var url = urlStr
        var fixedContent = content
        if (fixedContent.contains("\"./") || fixedContent.contains("\"../")) {
            url = url.replace("file://", "clan://localhost/")
            if (!url.startsWith("http") && !url.startsWith("clan://")) {
                url = "http://$url"
            }
            if (url.startsWith("clan://")) url = clanToAddress(url)
            fixedContent = fixedContent.replace("../", UriUtil.resolve(url, "../"))
            fixedContent = fixedContent.replace("./", UriUtil.resolve(url, "./"))
        }
        return fixedContent
    }

    fun getMyHost(): Map<String, String>? {
        return myHosts
    }

    private fun loadProxyRules(infoJson: JsonObject) {
        if (!infoJson.has("proxy")) {
            OkGoHelper.setProxyList(null)
            return
        }
        try {
            OkGoHelper.setProxyList(ProxyRule.arrayFrom(infoJson.get("proxy")))
        } catch (th: Throwable) {
            th.printStackTrace()
            OkGoHelper.setProxyList(null)
        }
    }

    fun clearJarLoader() {
        jarLoader.clear()
    }

    private fun addSuperParse() {
        val superPb = ParseBean()
        superPb.setName("超级解析")
        superPb.setUrl("SuperParse")
        superPb.setExt("")
        superPb.setType(4)
        parseBeanList.add(0, superPb)
    }

    fun clearLoader() {
        jarLoader.clear()
        pyLoader.clear()
        jsLoader.clear()
        synchronized(warmedSearchSpiderKeys) {
            warmedSearchSpiderKeys.clear()
        }
    }

    fun clearSpiderCache() {
        currentPyKey = ""
        currentLivePyKey = ""
        currentLiveSpider = ""
        clearLoader()
    }
}
