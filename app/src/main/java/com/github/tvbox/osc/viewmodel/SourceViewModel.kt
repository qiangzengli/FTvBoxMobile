package com.github.tvbox.osc.viewmodel

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Base64
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.github.catvod.crawler.Spider
import com.github.catvod.net.OkHttp
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.bean.AbsJson
import com.github.tvbox.osc.bean.AbsSortJson
import com.github.tvbox.osc.bean.AbsSortXml
import com.github.tvbox.osc.bean.AbsXml
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.MovieSort
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.player.thirdparty.RemoteTVBox
import com.github.tvbox.osc.util.DefaultConfig
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.MD5
import com.github.tvbox.osc.util.thunder.Thunder
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
import com.orhanobut.hawk.Hawk
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.DomDriver
import okhttp3.Call
import org.greenrobot.eventbus.EventBus
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class SourceViewModel : ViewModel() {

    @JvmField
    val sortResult: MutableLiveData<AbsSortXml> = MutableLiveData()

    @JvmField
    val listResult: MutableLiveData<AbsXml> = MutableLiveData()

    @JvmField
    val searchResult: MutableLiveData<AbsXml> = MutableLiveData()

    @JvmField
    val quickSearchResult: MutableLiveData<AbsXml> = MutableLiveData()

    @JvmField
    val detailFallbackSearchResult: MutableLiveData<AbsXml> = MutableLiveData()

    @JvmField
    val detailResult: MutableLiveData<AbsXml> = MutableLiveData()

    @JvmField
    val actionResult: MutableLiveData<JSONObject> = MutableLiveData()

    @JvmField
    val playResult: MutableLiveData<JSONObject> = MutableLiveData()

    @JvmField
    var gson: Gson = Gson()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val playRequestSeq = AtomicInteger()

    fun getSort(sourceKey: String?) {
        if (sourceKey == null) {
            sortResult.postValue(AbsSortXml())
            return
        }

        val sourceBean = ApiConfig.get().getSource(sourceKey)
        if (sourceBean == null) {
            LOG.i("echo--getSort-source-null--$sourceKey")
            postSortResult(sourceKey, null)
            return
        }
        if (sourceBean.name != null && sourceBean.name.length <= 3 && sourceBean.name.endsWith("搜")) {
            postSortResult(sourceKey, null)
            return
        }

        if (!shouldBypassSortCache(sourceKey, sourceBean)) {
            val cached = sortCache[sourceKey]
            if (cached != null) {
                val homeRec = Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC)
                val cachedVideoList = cached.videoList
                val shouldUseCache = (homeRec != 1) || (!cachedVideoList.isNullOrEmpty())
                if (shouldUseCache) {
                    attachSortSource(sourceKey, cached)
                    postSortResult(sourceKey, cached)
                    return
                }
            }
        }

        val type = sourceBean.type
        if (type == 3) {
            val waitResponse = Runnable {
                val executor = Executors.newSingleThreadExecutor()
                val future: Future<String> = executor.submit(Callable {
                    val sp = ApiConfig.get().getCSP(sourceBean)
                    sp.homeContent(true)
                })
                var sortJson: String? = null
                try {
                    sortJson = future.get(30, TimeUnit.SECONDS)
                } catch (e: TimeoutException) {
                    LOG.i("echo--getSort-timeout--" + sourceBean.key)
                    e.printStackTrace()
                    future.cancel(true)
                } catch (e: InterruptedException) {
                    val cause = e.cause
                    LOG.i("echo--getSort-error--" + sourceBean.key + "--" + e.javaClass.simpleName + "--" + (if (cause != null) cause.javaClass.simpleName + ":" + cause.message else e.message))
                    e.printStackTrace()
                } catch (e: ExecutionException) {
                    val cause = e.cause
                    LOG.i("echo--getSort-error--" + sourceBean.key + "--" + e.javaClass.simpleName + "--" + (if (cause != null) cause.javaClass.simpleName + ":" + cause.message else e.message))
                    e.printStackTrace()
                } finally {
                    if (sortJson != null) {
                        val sortXml = sortJson(sortResult, sortJson)
                        attachSortSource(sourceKey, sortXml)
                        if (sortXml != null && Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1) {
                            val absXml = json(null, sortJson, sourceBean.key)
                            val movie = absXml?.movie
                            val vList = movie?.videoList
                            if (vList != null && vList.isNotEmpty()) {
                                sortXml.videoList = if (vList is ArrayList<*>) vList as ArrayList<Movie.Video> else ArrayList(vList)
                                postSortResult(sourceKey, sortXml)
                                cacheSort(sourceKey, sortXml)
                            } else {
                                getHomeRecList(sourceBean, null, object : HomeRecCallback {
                                    override fun done(videos: List<Movie.Video>?) {
                                        sortXml.videoList = if (videos is ArrayList<*>) videos as ArrayList<Movie.Video> else ArrayList(videos ?: emptyList())
                                        postSortResult(sourceKey, sortXml)
                                        cacheSort(sourceKey, sortXml)
                                    }
                                })
                            }
                        } else {
                            postSortResult(sourceKey, sortXml)
                            cacheSort(sourceKey, sortXml)
                        }
                    } else {
                        postSortResult(sourceKey, null)
                    }
                    try {
                        executor.shutdown()
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                }
            }
            httpPrepareThreadPool.execute(waitResponse)
        } else if (type == 0 || type == 1) {
            OkGo.get<String>(sourceBean.api)
                .tag(sourceBean.key + "_sort")
                .execute(object : AbsCallback<String>() {
                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        val body = response.body()
                        if (body != null) {
                            return body.string()
                        } else {
                            throw IllegalStateException("网络请求错误")
                        }
                    }

                    override fun onSuccess(response: Response<String>) {
                        var sortXml: AbsSortXml? = null
                        if (type == 0) {
                            val xml = response.body()
                            sortXml = sortXml(sortResult, xml)
                        } else if (type == 1) {
                            val json = response.body()
                            sortXml = sortJson(sortResult, json)
                        }
                        attachSortSource(sourceKey, sortXml)
                        val sList = sortXml?.list
                        val sVideoList = sList?.videoList
                        if (sortXml != null && Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1 && !sVideoList.isNullOrEmpty()) {
                            val ids = ArrayList<String>()
                            for (vod in sVideoList) {
                                vod.id?.let { ids.add(it) }
                            }
                            val finalSortXml = sortXml
                            getHomeRecList(sourceBean, ids, object : HomeRecCallback {
                                override fun done(videos: List<Movie.Video>?) {
                                    finalSortXml.videoList = if (videos is ArrayList<*>) videos as ArrayList<Movie.Video> else ArrayList(videos ?: emptyList())
                                    postSortResult(sourceKey, finalSortXml)
                                    cacheSort(sourceKey, finalSortXml)
                                }
                            })
                        } else {
                            postSortResult(sourceKey, sortXml)
                            cacheSort(sourceKey, sortXml)
                        }
                    }

                    override fun onError(response: Response<String>) {
                        super.onError(response)
                        postSortResult(sourceKey, null)
                    }
                })
        } else if (type == 4) {
            var extend = sourceBean.ext
            extend = getFixUrl(extend)
            if (URLEncoder.encode(extend ?: "", "UTF-8").length < 1000) {
                val request = OkGo.get<String>(sourceBean.api)
                    .tag(sourceBean.key + "_sort")
                    .params("filter", "true")
                if (!extend.isNullOrEmpty()) {
                    request.params("extend", extend)
                }
                request.execute(object : AbsCallback<String>() {
                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        val body = response.body()
                        if (body != null) {
                            return body.string()
                        } else {
                            throw IllegalStateException("网络请求错误")
                        }
                    }

                    override fun onSuccess(response: Response<String>) {
                        val sortJson = response.body()
                        if (sortJson != null) {
                            val sortXml = sortJson(sortResult, sortJson)
                            attachSortSource(sourceKey, sortXml)
                            if (sortXml != null && Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1) {
                                val absXml = json(null, sortJson, sourceBean.key)
                                val movie = absXml?.movie
                                val vList = movie?.videoList
                                if (vList != null && vList.isNotEmpty()) {
                                    sortXml.videoList = if (vList is ArrayList<*>) vList as ArrayList<Movie.Video> else ArrayList(vList)
                                    postSortResult(sourceKey, sortXml)
                                    cacheSort(sourceKey, sortXml)
                                } else {
                                    getHomeRecList(sourceBean, null, object : HomeRecCallback {
                                        override fun done(videos: List<Movie.Video>?) {
                                            sortXml.videoList = if (videos is ArrayList<*>) videos as ArrayList<Movie.Video> else ArrayList(videos ?: emptyList())
                                            postSortResult(sourceKey, sortXml)
                                            cacheSort(sourceKey, sortXml)
                                        }
                                    })
                                }
                            } else {
                                postSortResult(sourceKey, sortXml)
                                cacheSort(sourceKey, sortXml)
                            }
                        } else {
                            postSortResult(sourceKey, null)
                        }
                    }

                    override fun onError(response: Response<String>) {
                        super.onError(response)
                        postSortResult(sourceKey, null)
                    }
                })
            } else {
                try {
                    val params = HashMap<String, String>()
                    params["filter"] = "true"
                    if (!extend.isNullOrEmpty()) {
                        params["extend"] = extend
                    }
                    RemoteTVBox.post(sourceBean.api, params, object : okhttp3.Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            postSortResult(sourceKey, null)
                        }

                        @Throws(IOException::class)
                        override fun onResponse(call: Call, response: okhttp3.Response) {
                            val body = response.body() ?: return
                            val sortJson = body.string()
                            val sortXml = sortJson(sortResult, sortJson)
                            attachSortSource(sourceKey, sortXml)
                            if (sortXml != null && Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1) {
                                val absXml = json(null, sortJson, sourceBean.key)
                                val movie = absXml?.movie
                                val vList = movie?.videoList
                                if (vList != null && vList.isNotEmpty()) {
                                    sortXml.videoList = if (vList is ArrayList<*>) vList as ArrayList<Movie.Video> else ArrayList(vList)
                                    postSortResult(sourceKey, sortXml)
                                    cacheSort(sourceKey, sortXml)
                                } else {
                                    getHomeRecList(sourceBean, null, object : HomeRecCallback {
                                        override fun done(videos: List<Movie.Video>?) {
                                            sortXml.videoList = if (videos is ArrayList<*>) videos as ArrayList<Movie.Video> else ArrayList(videos ?: emptyList())
                                            postSortResult(sourceKey, sortXml)
                                            cacheSort(sourceKey, sortXml)
                                        }
                                    })
                                }
                            } else {
                                postSortResult(sourceKey, sortXml)
                                cacheSort(sourceKey, sortXml)
                            }
                        }
                    })
                } catch (ignored: Exception) {
                    postSortResult(sourceKey, null)
                }
            }
        } else {
            postSortResult(sourceKey, null)
        }
    }

    fun getList(sortData: MovieSort.SortData?, page: Int) {
        if (sortData == null) {
            LOG.i("echo-getList-sortData-null")
            listResult.postValue(null)
            return
        }
        val homeSourceBean = ApiConfig.get().getHomeSourceBean()
        val type = homeSourceBean.type
        if (type == 3) {
            spThreadPool.execute {
                val executor = Executors.newSingleThreadExecutor()
                val future: Future<String> = executor.submit(Callable {
                    val sp = ApiConfig.get().getCSP(homeSourceBean)
                    sp.categoryContent(sortData.id, page.toString() + "", true, sortData.filterSelect)
                })
                var json: String? = null
                try {
                    json = future.get(homeSourceBean.playTimeoutSeconds.toLong(), TimeUnit.SECONDS)
                } catch (e: TimeoutException) {
                    LOG.i("echo--getList-timeout--" + homeSourceBean.key)
                    e.printStackTrace()
                    future.cancel(true)
                } catch (e: Exception) {
                    val cause = e.cause
                    LOG.i("echo--getList-error--" + homeSourceBean.key + "--" + e.javaClass.simpleName + "--" + (if (cause != null) cause.javaClass.simpleName + ":" + cause.message else e.message))
                    e.printStackTrace()
                } finally {
                    executor.shutdown()
                    if (json != null) {
                        json(listResult, json, homeSourceBean.key)
                    } else {
                        listResult.postValue(null)
                    }
                }
            }
        } else if (type == 0 || type == 1) {
            OkGo.get<String>(homeSourceBean.api)
                .tag(homeSourceBean.api)
                .params("ac", if (type == 0) "videolist" else "detail")
                .params("t", sortData.id)
                .params("pg", page)
                .params(sortData.filterSelect)
                .params("f", if (sortData.filterSelect == null || sortData.filterSelect.size <= 0) "" else JSONObject(sortData.filterSelect as Map<*, *>).toString())
                .execute(object : AbsCallback<String>() {
                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        val body = response.body()
                        if (body != null) {
                            return body.string()
                        } else {
                            throw IllegalStateException("网络请求错误")
                        }
                    }

                    override fun onSuccess(response: Response<String>) {
                        if (type == 0) {
                            val xml = response.body()
                            xml(listResult, xml, homeSourceBean.key)
                        } else {
                            val json = response.body()
                            json(listResult, json, homeSourceBean.key)
                        }
                    }

                    override fun onError(response: Response<String>) {
                        super.onError(response)
                        listResult.postValue(null)
                    }
                })
        } else if (type == 4) {
            var ext = ""
            var extend = homeSourceBean.ext
            extend = getFixUrl(extend)
            if (sortData.filterSelect != null && sortData.filterSelect.size > 0) {
                try {
                    val selectExt = JSONObject(sortData.filterSelect as Map<*, *>).toString()
                    ext = Base64.encodeToString(selectExt.toByteArray(charset("UTF-8")), Base64.DEFAULT or Base64.NO_WRAP)
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
            } else {
                ext = Base64.encodeToString("{}".toByteArray(), Base64.DEFAULT or Base64.NO_WRAP)
            }

            val request = OkGo.get<String>(homeSourceBean.api)
                .tag(homeSourceBean.api)
                .params("ac", "detail")
                .params("filter", "true")
                .params("t", sortData.id)
                .params("pg", page)
                .params("ext", ext)
            if (!extend.isNullOrEmpty()) {
                request.params("extend", extend)
            }
            request.execute(object : AbsCallback<String>() {
                @Throws(Throwable::class)
                override fun convertResponse(response: okhttp3.Response): String {
                    try {
                        val body = response.body()
                        if (body != null) {
                            return body.string()
                        } else {
                            throw IllegalStateException("网络请求错误，response body 为 null")
                        }
                    } catch (e: Exception) {
                        LOG.i("echo-list: convertResponse error" + e.message)
                        throw e
                    }
                }

                override fun onSuccess(response: Response<String>) {
                    val json = response.body()
                    json(listResult, json, homeSourceBean.key)
                }

                override fun onError(response: Response<String>) {
                    super.onError(response)
                    listResult.postValue(null)
                }
            })
        } else {
            listResult.postValue(null)
        }
    }

    fun getList(sourceKey: String, id: String) {
        val sourceBean = ApiConfig.get().getSource(sourceKey)
        if (sourceBean == null || TextUtils.isEmpty(id)) {
            listResult.postValue(null)
            return
        }
        val type = sourceBean.type
        if (type == 3) {
            spThreadPool.execute {
                val executor = Executors.newSingleThreadExecutor()
                val future: Future<String> = executor.submit(Callable {
                    val sp = ApiConfig.get().getCSP(sourceBean)
                    sp.categoryContent(id, "1", true, HashMap())
                })
                var json: String? = null
                try {
                    json = future.get(sourceBean.playTimeoutSeconds.toLong(), TimeUnit.SECONDS)
                } catch (ignored: Throwable) {
                    future.cancel(true)
                } finally {
                    executor.shutdown()
                    if (json != null) {
                        json(listResult, json, sourceBean.key)
                    } else {
                        listResult.postValue(null)
                    }
                }
            }
        } else if (type == 0 || type == 1) {
            OkGo.get<String>(sourceBean.api)
                .tag(sourceBean.key + "_folder")
                .params("ac", if (type == 0) "videolist" else "detail")
                .params("t", id)
                .params("pg", 1)
                .execute(object : AbsCallback<String>() {
                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        val body = response.body()
                        if (body != null) return body.string()
                        throw IllegalStateException("网络请求错误")
                    }

                    override fun onSuccess(response: Response<String>) {
                        if (type == 0) xml(listResult, response.body(), sourceBean.key)
                        else json(listResult, response.body(), sourceBean.key)
                    }

                    override fun onError(response: Response<String>) {
                        super.onError(response)
                        listResult.postValue(null)
                    }
                })
        } else if (type == 4) {
            val extend = getFixUrl(sourceBean.ext)
            val request = OkGo.get<String>(sourceBean.api)
                .tag(sourceBean.key + "_folder")
                .params("ac", "detail")
                .params("filter", "true")
                .params("t", id)
                .params("pg", 1)
                .params("ext", Base64.encodeToString("{}".toByteArray(), Base64.DEFAULT or Base64.NO_WRAP))
            if (!TextUtils.isEmpty(extend)) request.params("extend", extend)
            request.execute(object : AbsCallback<String>() {
                @Throws(Throwable::class)
                override fun convertResponse(response: okhttp3.Response): String {
                    val body = response.body()
                    if (body != null) return body.string()
                    throw IllegalStateException("网络请求错误")
                }

                override fun onSuccess(response: Response<String>) {
                    json(listResult, response.body(), sourceBean.key)
                }

                override fun onError(response: Response<String>) {
                    super.onError(response)
                    listResult.postValue(null)
                }
            })
        } else {
            listResult.postValue(null)
        }
    }

    interface HomeRecCallback {
        fun done(videos: List<Movie.Video>?)
    }

    fun getHomeRecList(sourceBean: SourceBean, ids: ArrayList<String>?, callback: HomeRecCallback) {
        val type = sourceBean.type
        if (type == 3) {
            val waitResponse = Runnable {
                val executor = Executors.newSingleThreadExecutor()
                val future: Future<String> = executor.submit(Callable {
                    val sp = ApiConfig.get().getCSP(sourceBean)
                    sp.homeVideoContent()
                })
                var sortJson: String? = null
                try {
                    sortJson = future.get(20, TimeUnit.SECONDS)
                } catch (e: TimeoutException) {
                    e.printStackTrace()
                    future.cancel(true)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                } catch (e: ExecutionException) {
                    e.printStackTrace()
                } finally {
                    if (sortJson != null) {
                        val absXml = json(null, sortJson, sourceBean.key)
                        val movie = absXml?.movie
                        val vList = movie?.videoList
                        if (vList != null) {
                            callback.done(vList)
                        } else {
                            callback.done(null)
                        }
                    } else {
                        callback.done(null)
                    }
                    try {
                        executor.shutdown()
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                }
            }
            spThreadPool.execute(waitResponse)
        } else if (type == 0 || type == 1) {
            OkGo.get<String>(sourceBean.api)
                .tag("detail")
                .params("ac", if (sourceBean.type == 0) "videolist" else "detail")
                .params("ids", if (ids != null) TextUtils.join(",", ids) else "")
                .execute(object : AbsCallback<String>() {
                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        val body = response.body()
                        if (body != null) {
                            return body.string()
                        } else {
                            throw IllegalStateException("网络请求错误")
                        }
                    }

                    override fun onSuccess(response: Response<String>) {
                        val absXml: AbsXml? = if (sourceBean.type == 0) {
                            xml(null, response.body(), sourceBean.key)
                        } else {
                            json(null, response.body(), sourceBean.key)
                        }
                        val movie = absXml?.movie
                        val vList = movie?.videoList
                        if (vList != null) {
                            callback.done(vList)
                        } else {
                            callback.done(null)
                        }
                    }

                    override fun onError(response: Response<String>) {
                        super.onError(response)
                        callback.done(null)
                    }
                })
        } else {
            callback.done(null)
        }
    }

    @JvmOverloads
    fun getDetail(sourceKeyParam: String, urlidParam: String, fallback: Boolean = false) {
        var sourceKey = sourceKeyParam
        var urlid = urlidParam

        if (urlid.startsWith("push://") && ApiConfig.get().getSource(PUSH_AGENT) != null) {
            var pushUrl = urlid.substring(7)
            if (pushUrl.startsWith("b64:")) {
                try {
                    pushUrl = String(Base64.decode(pushUrl.substring(4), Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP), charset("UTF-8"))
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
            } else {
                pushUrl = URLDecoder.decode(pushUrl, "UTF-8")
            }
            sourceKey = if (isCastPushUrl(pushUrl)) PUSH_FALLBACK else PUSH_AGENT
            urlid = pushUrl
        } else if (PUSH_AGENT == sourceKey && isCastPushUrl(urlid)) {
            sourceKey = PUSH_FALLBACK
        }
        val id = urlid

        val sourceBean = ApiConfig.get().getSource(sourceKey)
        if (isPushFallback(sourceKey, sourceBean)) {
            detailResult.postValue(createPushDetail(urlid, sourceKey))
            return
        }
        if (sourceBean == null) {
            val data = AbsXml()
            data.sourceKey = sourceKey
            detailResult.postValue(data)
            return
        }

        val type = sourceBean.type
        if (type == 3) {
            spThreadPool.execute {
                val executor = Executors.newSingleThreadExecutor()
                val future: Future<String> = executor.submit(Callable {
                    val sp = ApiConfig.get().getCSP(sourceBean)
                    val ids = ArrayList<String>()
                    ids.add(id)
                    try {
                        sp.detailContent(ids)
                    } catch (e: Exception) {
                        LOG.i("echo--getDetail--error: " + e.message)
                        ""
                    }
                })

                var json: String? = null
                try {
                    json = future.get(if (fallback) 6 else 30, TimeUnit.SECONDS)
                } catch (e: TimeoutException) {
                    LOG.i("echo--getDetail--timeout")
                    future.cancel(true)
                } catch (e: Exception) {
                    LOG.i("echo--getDetail--error: " + e.message)
                } finally {
                    json(detailResult, json, sourceBean.key)
                    executor.shutdown()
                }
            }
        } else if (type == 0 || type == 1 || type == 4) {
            var extend = sourceBean.ext
            extend = if (fallback) getFixUrl(extend, 6) else getFixUrl(extend)

            val request = OkGo.get<String>(sourceBean.api)
                .tag("detail")
                .params("ac", if (type == 0) "videolist" else "detail")
                .params("ids", id)
            if (!extend.isNullOrEmpty()) {
                request.params("extend", extend)
            }
            request.execute(object : AbsCallback<String>() {
                @Throws(Throwable::class)
                override fun convertResponse(response: okhttp3.Response): String {
                    val body = response.body()
                    if (body != null) {
                        return body.string()
                    } else {
                        throw IllegalStateException("网络请求错误")
                    }
                }

                override fun onSuccess(response: Response<String>) {
                    if (type == 0) {
                        val xml = response.body()
                        xml(detailResult, xml, sourceBean.key)
                    } else {
                        val json = response.body()
                        LOG.i(json)
                        json(detailResult, json, sourceBean.key)
                    }
                }

                override fun onError(response: Response<String>) {
                    super.onError(response)
                    json(detailResult, "", sourceBean.key)
                }
            })
        } else {
            val data = AbsXml()
            data.sourceKey = sourceKey
            detailResult.postValue(data)
        }
    }

    fun action(sourceKey: String, action: String?) {
        val sourceBean = ApiConfig.get().getSource(sourceKey)
        if (sourceBean == null || action == null) {
            actionResult.postValue(null)
            return
        }
        if (sourceBean.type == 3) {
            spThreadPool.execute {
                try {
                    val sp = ApiConfig.get().getCSP(sourceBean)
                    val json = sp.action(action)
                    actionResult.postValue(if (TextUtils.isEmpty(json)) null else JSONObject(json))
                } catch (th: Throwable) {
                    th.printStackTrace()
                    actionResult.postValue(null)
                }
            }
        } else {
            actionResult.postValue(null)
        }
    }

    @JvmOverloads
    fun getSearch(sourceKey: String, wd: String, searchToken: String = "") {
        getSearch(sourceKey, wd, searchToken, searchResult, "search")
    }

    fun getDetailFallbackSearch(sourceKey: String, wd: String, searchToken: String) {
        getSearch(sourceKey, wd, searchToken, detailFallbackSearchResult, "detail_fallback_search")
    }

    private fun getSearch(sourceKey: String, wd: String, searchToken: String, result: MutableLiveData<AbsXml>, requestTag: String) {
        val sourceBean = ApiConfig.get().getSource(sourceKey)
        if (sourceBean == null) {
            postEmptySearchResult(result, sourceKey, searchToken)
            return
        }
        val type = sourceBean.type
        if (type == 3) {
            try {
                val sp = ApiConfig.get().getCSP(sourceBean)
                val search = sp.searchContent(wd, false)
                if (!TextUtils.isEmpty(search)) {
                    json(result, search, sourceBean.key, searchToken)
                } else {
                    json(result, "", sourceBean.key, searchToken)
                }
            } catch (th: Throwable) {
                th.printStackTrace()
                json(result, "", sourceBean.key, searchToken)
            }
        } else if (type == 0 || type == 1) {
            val req = OkGo.get<String>(sourceBean.api)
                .params("wd", wd)
                .tag(requestTag)
            if (type == 1) {
                req.params("ac", "detail")
            }
            req.execute(object : AbsCallback<String>() {
                @Throws(Throwable::class)
                override fun convertResponse(response: okhttp3.Response): String {
                    val body = response.body()
                    if (body != null) {
                        return body.string()
                    } else {
                        throw IllegalStateException("网络请求错误")
                    }
                }

                override fun onSuccess(response: Response<String>) {
                    if (type == 0) {
                        val xml = response.body()
                        xml(result, xml, sourceBean.key, searchToken)
                    } else {
                        val json = response.body()
                        json(result, json, sourceBean.key, searchToken)
                    }
                }

                override fun onError(response: Response<String>) {
                    super.onError(response)
                    postEmptySearchResult(result, sourceBean.key, searchToken)
                }
            })
        } else if (type == 4) {
            val searchWd = wd
            httpPrepareThreadPool.execute {
                var extend = sourceBean.ext
                extend = getFixUrlDirect(extend)
                var queryWd = searchWd
                try {
                    queryWd = URLEncoder.encode(queryWd, "UTF-8")
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }

                val request = OkGo.get<String>(sourceBean.api)
                    .tag(requestTag)
                    .params("wd", queryWd)
                    .params("ac", "detail")
                    .params("quick", "false")
                if (!extend.isNullOrEmpty()) {
                    request.params("extend", extend)
                }
                request.execute(object : AbsCallback<String>() {
                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        val body = response.body()
                        if (body != null) {
                            return body.string()
                        } else {
                            LOG.i("echo-t4 search-网络请求错误")
                            throw IllegalStateException("网络请求错误")
                        }
                    }

                    override fun onSuccess(response: Response<String>) {
                        val json = response.body()
                        json(result, json, sourceBean.key, searchToken)
                    }

                    override fun onError(response: Response<String>) {
                        LOG.i("echo-t4 search-onError")
                        super.onError(response)
                        postEmptySearchResult(result, sourceBean.key, searchToken)
                    }
                })
            }
        } else {
            postEmptySearchResult(result, sourceBean.key, searchToken)
        }
    }

    @JvmOverloads
    fun getQuickSearch(sourceKey: String, wd: String, searchToken: String = "") {
        val sourceBean = ApiConfig.get().getSource(sourceKey)
        if (sourceBean == null) {
            postEmptySearchResult(quickSearchResult, sourceKey, searchToken)
            return
        }
        val type = sourceBean.type
        if (type == 3) {
            try {
                val sp = ApiConfig.get().getCSP(sourceBean)
                json(quickSearchResult, sp.searchContent(wd, true), sourceBean.key, searchToken)
            } catch (th: Throwable) {
                th.printStackTrace()
                postEmptySearchResult(quickSearchResult, sourceBean.key, searchToken)
            }
        } else if (type == 0 || type == 1) {
            val req = OkGo.get<String>(sourceBean.api)
                .params("wd", wd)
                .tag("quick_search")
            if (type == 1) {
                req.params("ac", "detail")
            }
            req.execute(object : AbsCallback<String>() {
                @Throws(Throwable::class)
                override fun convertResponse(response: okhttp3.Response): String {
                    val body = response.body()
                    if (body != null) {
                        return body.string()
                    } else {
                        throw IllegalStateException("网络请求错误")
                    }
                }

                override fun onSuccess(response: Response<String>) {
                    if (type == 0) {
                        val xml = response.body()
                        xml(quickSearchResult, xml, sourceBean.key, searchToken)
                    } else {
                        val json = response.body()
                        json(quickSearchResult, json, sourceBean.key, searchToken)
                    }
                }

                override fun onError(response: Response<String>) {
                    super.onError(response)
                    postEmptySearchResult(quickSearchResult, sourceBean.key, searchToken)
                }
            })
        } else if (type == 4) {
            val searchWd = wd
            httpPrepareThreadPool.execute {
                var extend = sourceBean.ext
                extend = getFixUrlDirect(extend)

                val request = OkGo.get<String>(sourceBean.api)
                    .tag("search")
                    .params("wd", searchWd)
                    .params("ac", "detail")
                    .params("quick", "true")
                if (!extend.isNullOrEmpty()) {
                    request.params("extend", extend)
                }
                request.execute(object : AbsCallback<String>() {
                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        val body = response.body()
                        if (body != null) {
                            return body.string()
                        } else {
                            throw IllegalStateException("网络请求错误")
                        }
                    }

                    override fun onSuccess(response: Response<String>) {
                        val json = response.body()
                        LOG.i(json)
                        json(quickSearchResult, json, sourceBean.key, searchToken)
                    }

                    override fun onError(response: Response<String>) {
                        super.onError(response)
                        postEmptySearchResult(quickSearchResult, sourceBean.key, searchToken)
                    }
                })
            }
        } else {
            postEmptySearchResult(quickSearchResult, sourceBean.key, searchToken)
        }
    }

    fun getPlay(sourceKey: String, playFlag: String, progressKey: String, url: String, subtitleKey: String) {
        val requestSeq = playRequestSeq.incrementAndGet()
        val sourceBean = ApiConfig.get().getSource(sourceKey)
        val pushFallback = isPushFallback(sourceKey, sourceBean)
        val pushUrl = if (pushFallback) parsePushUrl(url) else createPushUrl(url)
        val requestUrl = pushUrl.url
        if (pushFallback) {
            postPlayResult(requestSeq, createPushPlayResult(url, pushUrl, progressKey, subtitleKey, playFlag))
            return
        }
        if (sourceBean == null) {
            postPlayResult(requestSeq, null)
            return
        }

        val type = sourceBean.type
        if (type == 3) {
            spThreadPool.execute {
                val executor = Executors.newSingleThreadExecutor()
                val future: Future<String> = executor.submit(Callable {
                    val sp = ApiConfig.get().getCSP(sourceBean)
                    if (TextUtils.isEmpty(requestUrl)) return@Callable ""
                    try {
                        LOG.i("echo--getPlay--id: $requestUrl")
                        sp.playerContent(playFlag, requestUrl, ApiConfig.get().getVipParseFlags())
                    } catch (e: Exception) {
                        LOG.i("echo--getPlay--error: " + e.message)
                        ""
                    }
                })

                try {
                    val json = future.get(sourceBean.playTimeoutSeconds.toLong(), TimeUnit.SECONDS)
                    LOG.i("echo--getPlay--result:$json")
                    if (!TextUtils.isEmpty(json)) {
                        val result = normalizePlayerResult(JSONObject(json))
                        if (result != null) {
                            result.put("key", url)
                            mergePushHeaders(result, pushUrl)
                            result.put("proKey", progressKey)
                            result.put("subtKey", subtitleKey)
                            if (!result.has("flag")) result.put("flag", playFlag)
                            if (TextUtils.isEmpty(result.optString("url", "")) && shouldDirectPlay(sourceBean, requestUrl)) {
                                postPlayResult(requestSeq, createDirectPlayResult(url, pushUrl, progressKey, subtitleKey, playFlag))
                            } else {
                                postPlayResult(requestSeq, result)
                            }
                        } else {
                            postPlayResult(requestSeq, null)
                        }
                    } else {
                        postPlayResult(requestSeq, null)
                    }
                } catch (e: TimeoutException) {
                    LOG.i("echo--getPlay--timeout")
                    future.cancel(true)
                    postPlayResult(requestSeq, null)
                } catch (e: Exception) {
                    LOG.i("echo--getPlay--error: " + e.message)
                    postPlayResult(requestSeq, null)
                } finally {
                    executor.shutdown()
                }
            }
        } else if (type == 0 || type == 1) {
            val result = JSONObject()
            try {
                result.put("key", url)
                val playUrl = sourceBean.playerUrl?.trim() ?: ""
                if (DefaultConfig.isVideoFormat(requestUrl) && playUrl.isEmpty()) {
                    result.put("parse", 0)
                    result.put("url", requestUrl)
                } else {
                    result.put("parse", 1)
                    result.put("url", requestUrl)
                }
                mergePushHeaders(result, pushUrl)
                result.put("proKey", progressKey)
                result.put("subtKey", subtitleKey)
                result.put("playUrl", playUrl)
                result.put("flag", playFlag)
                postPlayResult(requestSeq, result)
            } catch (th: Throwable) {
                th.printStackTrace()
                postPlayResult(requestSeq, null)
            }
        } else if (type == 4) {
            var extend = sourceBean.ext
            extend = getFixUrl(extend)

            val request = OkGo.get<String>(sourceBean.api)
                .tag("play")
                .params("play", requestUrl)
                .params("flag", playFlag)
            if (!extend.isNullOrEmpty()) {
                request.params("extend", extend)
            }
            request.execute(object : AbsCallback<String>() {
                @Throws(Throwable::class)
                override fun convertResponse(response: okhttp3.Response): String {
                    val body = response.body()
                    if (body != null) {
                        return body.string()
                    } else {
                        throw IllegalStateException("网络请求错误")
                    }
                }

                override fun onSuccess(response: Response<String>) {
                    val json = response.body()
                    LOG.i(json)
                    try {
                        val result = normalizePlayerResult(JSONObject(json))
                        if (result != null) {
                            result.put("key", url)
                            mergePushHeaders(result, pushUrl)
                            result.put("proKey", progressKey)
                            result.put("subtKey", subtitleKey)
                            if (!result.has("flag")) result.put("flag", playFlag)
                            postPlayResult(requestSeq, result)
                        } else {
                            postPlayResult(requestSeq, null)
                        }
                    } catch (th: Throwable) {
                        th.printStackTrace()
                        postPlayResult(requestSeq, null)
                    }
                }

                override fun onError(response: Response<String>) {
                    super.onError(response)
                    postPlayResult(requestSeq, null)
                }
            })
        } else {
            postPlayResult(requestSeq, null)
        }
    }

    fun cancelPlayRequest() {
        playRequestSeq.incrementAndGet()
    }

    private fun shouldDirectPlay(sourceBean: SourceBean?, requestUrl: String?): Boolean {
        return sourceBean != null &&
                !TextUtils.isEmpty(requestUrl) &&
                (requestUrl!!.startsWith("http://") || requestUrl.startsWith("https://"))
    }

    private fun createDirectPlayResult(rawUrl: String, pushUrl: PushUrl, progressKey: String, subtitleKey: String, playFlag: String): JSONObject? {
        return try {
            val result = JSONObject()
            result.put("key", rawUrl)
            result.put("proKey", progressKey)
            result.put("subtKey", subtitleKey)
            result.put("flag", playFlag)
            result.put("parse", 0)
            result.put("jx", 0)
            result.put("url", pushUrl.url)
            mergePushHeaders(result, pushUrl)
            LOG.i("echo--getPlay--direct:" + pushUrl.url)
            result
        } catch (th: Throwable) {
            th.printStackTrace()
            null
        }
    }

    private fun normalizePlayerResult(result: JSONObject?): JSONObject? {
        if (result == null) return null
        try {
            val playUrl = result.optString("playUrl", "")
            var url = result.optString("url", "")
            if (TextUtils.isEmpty(url)) return result
            if (url.startsWith("[") && url.endsWith("]")) {
                val array = JSONArray(url)
                for (i in 0 until array.length()) {
                    val item = array.get(i)
                    if (item is String) {
                        var str = item
                        if (str.startsWith("proxy://")) {
                            str = DefaultConfig.checkReplaceProxy(str)
                            array.put(i, str)
                        } else if (str.startsWith("video://")) {
                            str = str.substring(8)
                            array.put(i, str)
                        }
                    }
                }
                result.put("url", array.toString())
                result.put("parse", 0)
                return result
            }
            if (url.startsWith("video://")) {
                url = url.substring(8)
                result.put("url", url)
                result.put("parse", 1)
            } else if (url.startsWith("proxy://")) {
                url = DefaultConfig.checkReplaceProxy(url)
                result.put("url", url)
                result.put("parse", 0)
            } else if (playUrl.isEmpty() &&
                DefaultConfig.isVideoFormat(url) &&
                !result.has("parse") &&
                !result.has("jx")
            ) {
                result.put("parse", 0)
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        return result
    }

    private fun isPushFallback(sourceKey: String, sourceBean: SourceBean?): Boolean {
        return com.fbox.mobile.LocalVideos.SOURCE == sourceKey || PUSH_FALLBACK == sourceKey ||
                (sourceBean != null && PUSH_AGENT == sourceBean.key && sourceBean.type == -1)
    }

    private fun isCastPushUrl(url: String?): Boolean {
        return !TextUtils.isEmpty(url) && url!!.contains(PUSH_HEADERS_MARKER)
    }

    private fun createPushDetail(url: String, sourceKey: String): AbsXml {
        val data = AbsXml()
        data.sourceKey = sourceKey
        val movie = Movie()
        movie.videoList = ArrayList()
        val video = Movie.Video()
        video.id = url
        video.name = url
        video.type = "推送"
        if (com.fbox.mobile.LocalVideos.SOURCE == sourceKey) {
            video.name = com.fbox.mobile.LocalVideos.title(App.getInstance(), android.net.Uri.parse(url))
            video.type = "本地视频"
        }
        video.sourceKey = sourceKey
        val urlBean = Movie.Video.UrlBean()
        val infoList = ArrayList<Movie.Video.UrlBean.UrlInfo>()
        val urlInfo = Movie.Video.UrlBean.UrlInfo()
        urlInfo.flag = video.type
        urlInfo.urls = "播放$$url"
        val beanList = ArrayList<Movie.Video.UrlBean.UrlInfo.InfoBean>()
        beanList.add(Movie.Video.UrlBean.UrlInfo.InfoBean("播放", url))
        urlInfo.beanList = beanList
        infoList.add(urlInfo)
        urlBean.infoList = infoList
        video.urlBean = urlBean
        if (movie.videoList == null) {
            movie.videoList = ArrayList()
        }
        movie.videoList?.add(video)
        data.movie = movie
        return data
    }

    private fun createPushPlayResult(rawUrl: String, pushUrl: PushUrl, progressKey: String, subtitleKey: String, playFlag: String): JSONObject? {
        return try {
            val result = JSONObject()
            result.put("key", rawUrl)
            result.put("proKey", progressKey)
            result.put("subtKey", subtitleKey)
            result.put("flag", playFlag)
            result.put("parse", 0)
            result.put("url", pushUrl.url)
            mergePushHeaders(result, pushUrl)
            result
        } catch (th: Throwable) {
            th.printStackTrace()
            null
        }
    }

    private fun mergePushHeaders(result: JSONObject?, pushUrl: PushUrl?) {
        if (result == null || pushUrl == null || pushUrl.headers.isEmpty()) return
        try {
            var header = result.optJSONObject("header")
            if (header == null) header = result.optJSONObject("headers")
            if (header == null) header = JSONObject()
            for (key in pushUrl.headers.keys) {
                header.put(key, pushUrl.headers[key])
            }
            result.put("header", header)
        } catch (ignored: Throwable) {
        }
    }

    private fun createPushUrl(rawUrl: String?): PushUrl {
        val pushUrl = PushUrl()
        pushUrl.url = rawUrl ?: ""
        return pushUrl
    }

    private fun parsePushUrl(rawUrl: String?): PushUrl {
        val pushUrl = createPushUrl(rawUrl)
        parseMarkedHeaders(pushUrl)
        return pushUrl
    }

    private fun parseMarkedHeaders(pushUrl: PushUrl): Boolean {
        val marker = PUSH_HEADERS_MARKER
        val start = pushUrl.url.indexOf(marker)
        if (start < 0) return false
        val valueStart = start + marker.length
        val end = pushUrl.url.indexOf('@', valueStart)
        if (end < 0) return false
        return try {
            val text = URLDecoder.decode(pushUrl.url.substring(valueStart, end), "UTF-8")
            val json = JSONObject(text)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key, "")
                if (!TextUtils.isEmpty(key)) pushUrl.headers[key] = value
            }
            pushUrl.url = pushUrl.url.substring(0, start) + pushUrl.url.substring(end + 1)
            true
        } catch (ignored: Throwable) {
            false
        }
    }

    private class PushUrl {
        var url: String = ""
        var headers: HashMap<String, String> = HashMap()
    }

    private fun postPlayResult(requestSeq: Int, result: JSONObject?) {
        mainHandler.post {
            if (requestSeq != playRequestSeq.get()) {
                LOG.i("echo--getPlay--ignore stale result")
                return@post
            }
            playResult.value = result
        }
    }

    private fun getFixUrl(extend: String?): String {
        return getFixUrl(extend, 20)
    }

    private fun getFixUrl(extend: String?, timeoutSeconds: Long): String {
        if (TextUtils.isEmpty(extend)) return ""
        val ext = extend!!
        if (!ext.startsWith("http")) return ext
        val key = MD5.string2MD5(ext)
        if (extendCache.containsKey(key)) {
            LOG.i("echo-getFixUrl Cache")
            return extendCache[key] ?: ext
        }
        val future: Future<String> = spThreadPool.submit(Callable {
            var result = ext
            if (ext.startsWith("http://127.0.0.1")) {
                var path = ext.replace("^http.+/file/".toRegex(), FileUtils.getRootPath() + "/")
                path = path.replace("localhost/", "/")
                result = FileUtils.readFileToString(path, "UTF-8") ?: ext
                result = tryMinifyJson(result)
                extendCache.putIfAbsent(key, result)
            } else if (ext.startsWith("http")) {
                result = OkHttp.string(ext, null)
                if (!result.isEmpty()) {
                    result = tryMinifyJson(result)
                    if (result.length > 2500) result = ext
                    extendCache.putIfAbsent(key, result)
                }
            }
            result
        })

        return try {
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (te: TimeoutException) {
            te.printStackTrace()
            future.cancel(true)
            ext
        } catch (e: Exception) {
            e.printStackTrace()
            ext
        }
    }

    private fun tryMinifyJson(rawParam: String): String {
        return try {
            val raw = rawParam.trim()
            val jsonElement: JsonElement = JsonParser.parseString(raw)
            gson.toJson(jsonElement)
        } catch (e: Exception) {
            rawParam
        }
    }

    private fun getSortFilter(obj: JsonObject): MovieSort.SortFilter {
        val key = obj.get("key").asString
        val name = obj.get("name").asString
        val kv = obj.getAsJsonArray("value")
        val values = LinkedHashMap<String, String>()
        for (ele in kv) {
            val ele_obj = ele.asJsonObject
            val values_key = if (ele_obj.has("n")) ele_obj.get("n").asString else ""
            val values_value = if (ele_obj.has("v")) ele_obj.get("v").asString else ""
            values[values_key] = values_value
        }
        val filter = MovieSort.SortFilter()
        filter.key = key
        filter.name = name
        filter.values = values
        return filter
    }

    private fun sortJson(result: MutableLiveData<AbsSortXml>, json: String?): AbsSortXml? {
        try {
            if (TextUtils.isEmpty(json)) {
                return AbsSortJson().toAbsSortXml()
            }
            val obj = JsonParser.parseString(json).asJsonObject
            val sortJson: AbsSortJson = gson.fromJson(obj, object : TypeToken<AbsSortJson>() {}.type)
            val data = sortJson.toAbsSortXml()
            try {
                if (obj.has("filters")) {
                    val sortFilters = LinkedHashMap<String, ArrayList<MovieSort.SortFilter>>()
                    val filters = obj.getAsJsonObject("filters")
                    for (key in filters.keySet()) {
                        val sortFilter = ArrayList<MovieSort.SortFilter>()
                        val one = filters.get(key)
                        if (one.isJsonObject) {
                            sortFilter.add(getSortFilter(one.asJsonObject))
                        } else {
                            for (ele in one.asJsonArray) {
                                sortFilter.add(getSortFilter(ele.asJsonObject))
                            }
                        }
                        sortFilters[key] = sortFilter
                    }
                    val sortList = data?.classes?.sortList
                    if (sortList != null) {
                        for (sortData in sortList) {
                            if (sortFilters.containsKey(sortData.id)) {
                                val f = sortFilters[sortData.id]
                                if (f != null) {
                                    sortData.filters = f
                                }
                            }
                        }
                    }
                }
            } catch (th: Throwable) {
            }
            return data
        } catch (e: Exception) {
            return null
        }
    }

    private fun sortXml(result: MutableLiveData<AbsSortXml>, xml: String?): AbsSortXml? {
        return try {
            val xstream = XStream(DomDriver())
            xstream.autodetectAnnotations(true)
            xstream.processAnnotations(AbsSortXml::class.java)
            xstream.ignoreUnknownElements()
            val data = xstream.fromXML(xml) as AbsSortXml
            val sortList = data.classes?.sortList
            if (sortList != null) {
                for (sortData in sortList) {
                    if (sortData.filters == null) {
                        sortData.filters = ArrayList()
                    }
                }
            }
            data
        } catch (e: Exception) {
            null
        }
    }

    private fun absXml(data: AbsXml, sourceKey: String, searchToken: String = "") {
        data.sourceKey = sourceKey
        data.searchToken = searchToken
        val movie = data.movie
        val videoList = movie?.videoList
        if (videoList != null) {
            for (video in videoList) {
                val urlBean = video.urlBean
                val infoList = urlBean?.infoList
                if (infoList != null) {
                    for (urlInfo in infoList) {
                        val urls = urlInfo.urls
                        val str: Array<String> = if (urls != null && urls.contains("#")) {
                            urls.split("#".toRegex()).toTypedArray()
                        } else {
                            arrayOf(urls ?: "")
                        }
                        val infoBeanList = ArrayList<Movie.Video.UrlBean.UrlInfo.InfoBean>()
                        for (s in str) {
                            val ss = s.split("\\$".toRegex(), limit = 2).toTypedArray()
                            if (ss.isNotEmpty()) {
                                if (ss.size >= 2) {
                                    infoBeanList.add(Movie.Video.UrlBean.UrlInfo.InfoBean(ss[0], ss[1]))
                                } else {
                                    infoBeanList.add(Movie.Video.UrlBean.UrlInfo.InfoBean((infoBeanList.size + 1).toString(), ss[0]))
                                }
                            }
                        }
                        urlInfo.beanList = infoBeanList
                    }
                }
                video.sourceKey = sourceKey
            }
        }
    }

    private fun checkPush(data: AbsXml): AbsXml {
        val movie = data.movie
        val videoList = movie?.videoList
        if (videoList != null && videoList.size > 0) {
            val video = videoList[0]
            val urlBean = video?.urlBean
            val infoList = urlBean?.infoList
            if (video != null && urlBean != null && infoList != null && infoList.size > 0) {
                for (i in 0 until infoList.size) {
                    val urlinfo = infoList[i]
                    val beanList = urlinfo?.beanList
                    if (urlinfo != null && beanList != null && beanList.isNotEmpty()) {
                        for (infoBean in beanList) {
                            val infoUrl = infoBean.url
                            if (infoUrl != null && infoUrl.startsWith("push://")) {
                                var pushUrl = infoUrl.substring(7)
                                if (pushUrl.startsWith("b64:")) {
                                    try {
                                        pushUrl = String(Base64.decode(pushUrl.substring(4), Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP), charset("UTF-8"))
                                    } catch (e: UnsupportedEncodingException) {
                                        e.printStackTrace()
                                    }
                                } else {
                                    pushUrl = URLDecoder.decode(pushUrl, "UTF-8")
                                }

                                val resData = arrayOfNulls<AbsXml>(1)
                                val countDownLatch = CountDownLatch(1)
                                val threadPool = Executors.newSingleThreadExecutor()
                                val finalPushUrl = pushUrl
                                threadPool.execute {
                                    val sb = ApiConfig.get().getSource("push_agent")
                                    if (sb == null) {
                                        countDownLatch.countDown()
                                        return@execute
                                    }
                                    if (sb.type == 4) {
                                        OkGo.get<String>(sb.api)
                                            .tag("detail")
                                            .params("ac", "detail")
                                            .params("ids", finalPushUrl)
                                            .execute(object : AbsCallback<String>() {
                                                @Throws(Throwable::class)
                                                override fun convertResponse(response: okhttp3.Response): String {
                                                    val body = response.body()
                                                    return if (body != null) body.string() else ""
                                                }

                                                override fun onSuccess(response: Response<String>) {
                                                    val res = response.body()
                                                    if (!TextUtils.isEmpty(res)) {
                                                        try {
                                                            val absJson: AbsJson = gson.fromJson(res, object : TypeToken<AbsJson>() {}.type)
                                                            resData[0] = absJson.toAbsXml()
                                                            resData[0]?.let { absXml(it, sb.key) }
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                    countDownLatch.countDown()
                                                }

                                                override fun onError(response: Response<String>) {
                                                    super.onError(response)
                                                    countDownLatch.countDown()
                                                }
                                            })
                                    } else {
                                        try {
                                            val sp = ApiConfig.get().getCSP(sb)
                                            val ids = ArrayList<String>()
                                            ids.add(finalPushUrl)
                                            val res = sp.detailContent(ids)
                                            if (!TextUtils.isEmpty(res)) {
                                                try {
                                                    val absJson: AbsJson = gson.fromJson(res, object : TypeToken<AbsJson>() {}.type)
                                                    resData[0] = absJson.toAbsXml()
                                                    resData[0]?.let { absXml(it, sb.key) }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        } catch (th: Throwable) {
                                            th.printStackTrace()
                                        }
                                        countDownLatch.countDown()
                                    }
                                }
                                try {
                                    countDownLatch.await(15, TimeUnit.SECONDS)
                                    threadPool.shutdown()
                                } catch (e: InterruptedException) {
                                    e.printStackTrace()
                                }
                                val res = resData[0]
                                val resMovie = res?.movie
                                val resVideoList = resMovie?.videoList
                                if (res != null && resVideoList != null && resVideoList.size > 0) {
                                    val resVideo = resVideoList[0]
                                    val resUrlBean = resVideo?.urlBean
                                    val resInfoList = resUrlBean?.infoList
                                    if (resVideo != null && resUrlBean != null && resInfoList != null && resInfoList.size > 0) {
                                        if (beanList.size == 1) {
                                            infoList.removeAt(i)
                                        } else {
                                            beanList.remove(infoBean)
                                        }
                                        for (resUrlinfo in resInfoList) {
                                            val rbl = resUrlinfo?.beanList
                                            if (resUrlinfo != null && !rbl.isNullOrEmpty()) {
                                                infoList.add(resUrlinfo)
                                            }
                                        }
                                        video.sourceKey = "push_agent"
                                        return data
                                    }
                                }
                                infoBean.name = "解析失败 >>> " + infoBean.name
                            }
                        }
                    }
                }
            }
        }
        return data
    }

    fun checkThunder(data: AbsXml, index: Int) {
        var thunderParse = false
        val movie = data.movie
        val videoList = movie?.videoList
        if (videoList != null && videoList.size == 1) {
            val video = videoList[0]
            val urlBean = video?.urlBean
            val infoList = urlBean?.infoList
            if (video != null && urlBean != null && infoList != null) {
                var hasThunder = false
                thunderLoop@ for (idx in 0 until infoList.size) {
                    val urlInfo = infoList[idx]
                    val beanList = urlInfo.beanList
                    if (beanList != null) {
                        for (infoBean in beanList) {
                            val bUrl = infoBean.url
                            if (bUrl != null && Thunder.isSupportUrl(bUrl)) {
                                hasThunder = true
                                break@thunderLoop
                            }
                        }
                    }
                }
                if (hasThunder) {
                    thunderParse = true
                    Thunder.parse(App.getInstance(), urlBean, object : Thunder.ThunderCallback {
                        override fun status(code: Int, info: String) {
                            if (code >= 0) {
                                LOG.i(info)
                            } else {
                                val firstInfo = infoList.getOrNull(0)
                                val firstBean = firstInfo?.beanList?.getOrNull(0)
                                if (firstBean != null) {
                                    firstBean.name = info
                                }
                                detailResult.postValue(data)
                            }
                        }

                        override fun list(urlMap: Map<Int, String>) {
                            for (key in urlMap.keys) {
                                val playList = urlMap[key] ?: ""
                                if (key < infoList.size) {
                                    val uInfo = infoList[key]
                                    uInfo.urls = playList
                                    val str = playList.split("#".toRegex()).toTypedArray()
                                    val infoBeanList = ArrayList<Movie.Video.UrlBean.UrlInfo.InfoBean>()
                                    for (s in str) {
                                        if (s.contains("$")) {
                                            val ss = s.split("\\$".toRegex(), limit = 2).toTypedArray()
                                            if (ss.isNotEmpty()) {
                                                if (ss.size >= 2) {
                                                    infoBeanList.add(Movie.Video.UrlBean.UrlInfo.InfoBean(ss[0], ss[1]))
                                                } else {
                                                    infoBeanList.add(Movie.Video.UrlBean.UrlInfo.InfoBean((infoBeanList.size + 1).toString(), ss[0]))
                                                }
                                            }
                                        }
                                    }
                                    uInfo.beanList = infoBeanList
                                }
                            }
                            detailResult.postValue(data)
                        }

                        override fun play(url: String) {}
                    })
                }
            }
        }
        if (!thunderParse && index == 0) {
            detailResult.postValue(data)
        }
    }

    private fun xml(result: MutableLiveData<AbsXml>?, xml: String?, sourceKey: String, searchToken: String = ""): AbsXml? {
        return try {
            val xstream = XStream(DomDriver())
            xstream.autodetectAnnotations(true)
            xstream.processAnnotations(AbsXml::class.java)
            xstream.ignoreUnknownElements()
            var fixedXml = xml ?: ""
            if (fixedXml.contains("<year></year>")) {
                fixedXml = fixedXml.replace("<year></year>", "<year>0</year>")
            }
            if (fixedXml.contains("<state></state>")) {
                fixedXml = fixedXml.replace("<state></state>", "<state>0</state>")
            }
            val data = xstream.fromXML(fixedXml) as AbsXml
            absXml(data, sourceKey, searchToken)
            if (searchResult == result) {
                EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, data))
            } else if (quickSearchResult == result) {
                EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, data))
            } else if (result != null) {
                if (result == detailResult) {
                    val pushed = checkPush(data)
                    checkThunder(pushed, 0)
                } else {
                    postSearchResult(result, data)
                }
            }
            data
        } catch (e: Exception) {
            if (searchResult == result || quickSearchResult == result || detailFallbackSearchResult == result) {
                postEmptySearchResult(result, sourceKey, searchToken)
            } else if (result != null) {
                if (result == detailResult) {
                    val data = AbsXml()
                    data.sourceKey = sourceKey
                    result.postValue(data)
                } else {
                    result.postValue(null)
                }
            }
            null
        }
    }

    private fun json(result: MutableLiveData<AbsXml>?, json: String?, sourceKey: String, searchToken: String = ""): AbsXml? {
        return try {
            val absJson: AbsJson = gson.fromJson(json, object : TypeToken<AbsJson>() {}.type)
            val data = absJson.toAbsXml()
            absXml(data, sourceKey, searchToken)
            if (searchResult == result) {
                EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, data))
            } else if (quickSearchResult == result) {
                EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, data))
            } else if (result != null) {
                if (result == detailResult) {
                    val pushed = checkPush(data)
                    checkThunder(pushed, 0)
                } else {
                    postSearchResult(result, data)
                }
            }
            data
        } catch (e: Exception) {
            if (searchResult == result || quickSearchResult == result || detailFallbackSearchResult == result) {
                postEmptySearchResult(result, sourceKey, searchToken)
            } else if (result != null) {
                if (result == detailResult) {
                    val data = AbsXml()
                    data.sourceKey = sourceKey
                    result.postValue(data)
                } else {
                    result.postValue(null)
                }
            }
            null
        }
    }

    private fun getFixUrlDirect(extend: String?): String {
        if (TextUtils.isEmpty(extend)) return ""
        val ext = extend!!
        if (!ext.startsWith("http")) return ext
        val key = MD5.string2MD5(ext)
        if (extendCache.containsKey(key)) {
            return extendCache[key] ?: ext
        }
        var result = ext
        try {
            if (ext.startsWith("http://127.0.0.1")) {
                var path = ext.replace("^http.+/file/".toRegex(), FileUtils.getRootPath() + "/")
                path = path.replace("localhost/", "/")
                result = FileUtils.readFileToString(path, "UTF-8") ?: ext
                result = tryMinifyJson(result)
                extendCache.putIfAbsent(key, result)
            } else {
                result = OkHttp.string(ext, null)
                if (!TextUtils.isEmpty(result)) {
                    result = tryMinifyJson(result)
                    if (result.length > 2500) result = ext
                    extendCache.putIfAbsent(key, result)
                }
            }
        } catch (th: Throwable) {
            th.printStackTrace()
            return ext
        }
        return result
    }

    private fun postEmptySearchResult(result: MutableLiveData<AbsXml>?, sourceKey: String, searchToken: String = "") {
        val data = AbsXml()
        data.sourceKey = sourceKey
        data.searchToken = searchToken
        if (searchResult == result) {
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, data))
        } else if (quickSearchResult == result) {
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, data))
        } else if (result != null) {
            postSearchResult(result, data)
        }
    }

    private fun postSearchResult(result: MutableLiveData<AbsXml>, data: AbsXml) {
        if (result == detailFallbackSearchResult) {
            mainHandler.post {
                result.value = data
            }
        } else {
            result.postValue(data)
        }
    }

    companion object {
        private const val PUSH_AGENT = "push_agent"
        private const val PUSH_FALLBACK = "push_fallback"
        private const val PUSH_HEADERS_MARKER = "@Headers="

        @JvmField
        val spThreadPool: ExecutorService = Executors.newSingleThreadExecutor()
        private val httpPrepareThreadPool: ExecutorService = Executors.newFixedThreadPool(3)

        private val sortCache: MutableMap<String, AbsSortXml> = object : LinkedHashMap<String, AbsSortXml>(5, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AbsSortXml>?): Boolean {
                return size > 5
            }
        }

        private val extendCache = ConcurrentHashMap<String, String>()

        private fun cacheSort(sourceKey: String, sortXml: AbsSortXml?) {
            if (sortXml == null) return
            attachSortSource(sourceKey, sortXml)
            val sourceBean = ApiConfig.get().getSource(sourceKey)
            if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1 && !hasHomeRecVideos(sortXml)) {
                return
            }
            if (!shouldBypassSortCache(sourceKey, sourceBean) && !hasActionSort(sortXml)) {
                sortCache[sourceKey] = sortXml
            }
        }

        @JvmStatic
        fun clearSortCache(sourceKey: String) {
            sortCache.remove(sourceKey)
        }

        @JvmStatic
        fun clearRuntimeCache() {
            sortCache.clear()
            extendCache.clear()
        }

        private fun attachSortSource(sourceKey: String, sortXml: AbsSortXml?): AbsSortXml? {
            if (sortXml != null) {
                sortXml.sourceKey = sourceKey
            }
            return sortXml
        }

        private fun hasActionSort(sortXml: AbsSortXml?): Boolean {
            if (sortXml == null) return false
            if (hasActionVideo(sortXml.videoList)) return true
            val sList = sortXml.list
            return sList != null && hasActionVideo(sList.videoList)
        }

        private fun hasHomeRecVideos(sortXml: AbsSortXml?): Boolean {
            val vList = sortXml?.videoList
            return vList != null && vList.isNotEmpty()
        }

        private fun hasActionVideo(videos: List<Movie.Video>?): Boolean {
            if (videos == null) return false
            for (video in videos) {
                if (video != null && video.action != null) return true
            }
            return false
        }

        private fun shouldBypassSortCache(sourceKey: String, sourceBean: SourceBean?): Boolean {
            return isFirstSource(sourceKey) && isDoubanSource(sourceBean)
        }

        private fun isFirstSource(sourceKey: String): Boolean {
            val sources = ApiConfig.get().getSourceBeanList()
            return sources.isNotEmpty() && sources[0] != null && sourceKey == sources[0].key
        }

        private fun isDoubanSource(sourceBean: SourceBean?): Boolean {
            if (sourceBean == null) return false
            return containsDouban(sourceBean.key) ||
                    containsDouban(sourceBean.name) ||
                    containsDouban(sourceBean.api) ||
                    containsDouban(sourceBean.ext)
        }

        private fun containsDouban(value: String?): Boolean {
            if (TextUtils.isEmpty(value)) return false
            val lower = value!!.lowercase()
            return lower.contains("douban") || value.contains("豆瓣")
        }
    }

    private fun postSortResult(sourceKey: String, sortXml: AbsSortXml?) {
        var res = sortXml
        if (res == null) {
            res = AbsSortXml()
        }
        sortResult.postValue(attachSortSource(sourceKey, res))
    }
}
