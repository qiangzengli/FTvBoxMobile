package com.github.tvbox.osc.picasso

import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.squareup.picasso.Downloader
import okhttp3.Cache
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder

/**
 * A [Downloader] which uses OkHttp to download images.
 */
class MyOkhttpDownLoader : Downloader {

    @VisibleForTesting
    val client: Call.Factory

    private val cache: Cache?
    private var sharedClient = true

    /**
     * Create a new downloader that uses the specified OkHttp instance. A response cache will not be
     * automatically configured.
     */
    constructor(client: OkHttpClient) {
        this.client = client
        this.cache = client.cache()
    }

    /**
     * Create a new downloader that uses the specified [Call.Factory] instance.
     */
    constructor(client: Call.Factory) {
        this.client = client
        this.cache = null
    }

    @Throws(IOException::class)
    override fun load(request: Request): Response {
        var url = request.url().toString()
        var header: String? = null
        var cookie: String? = null
        var ua: String? = null
        var referer: String? = null

        // 检查链接里面是否有自定义header
        if (url.contains("@Headers=")) {
            header = url.split("@Headers=".toRegex()).toTypedArray()[1].split("@".toRegex()).toTypedArray()[0]
            header = URLDecoder.decode(header, "UTF-8")
        }
        if (url.contains("@Cookie=")) cookie = url.split("@Cookie=".toRegex()).toTypedArray()[1].split("@".toRegex()).toTypedArray()[0]
        if (url.contains("@User-Agent=")) ua = url.split("@User-Agent=".toRegex()).toTypedArray()[1].split("@".toRegex()).toTypedArray()[0]
        if (url.contains("@Referer=")) referer = url.split("@Referer=".toRegex()).toTypedArray()[1].split("@".toRegex()).toTypedArray()[0]

        url = url.split("@".toRegex()).toTypedArray()[0]
        val mRequestBuilder = request.newBuilder().url(url)
        if (!TextUtils.isEmpty(header)) {
            val jsonInfo = Gson().fromJson(header, JsonObject::class.java)
            for (key in jsonInfo.keySet()) {
                val valStr = jsonInfo.get(key).asString
                if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(valStr)) {
                    mRequestBuilder.header(key, valStr.trim())
                }
            }
        }
        if (!TextUtils.isEmpty(cookie)) {
            mRequestBuilder.header("Cookie", cookie!!)
        }
        if (!TextUtils.isEmpty(ua)) {
            mRequestBuilder.header("User-Agent", ua!!)
        } else if (TextUtils.isEmpty(mRequestBuilder.build().header("User-Agent"))) {
            mRequestBuilder.header("User-Agent", DEFAULT_USER_AGENT)
        }
        if (!TextUtils.isEmpty(referer)) {
            mRequestBuilder.header("Referer", referer!!)
        }
        return client.newCall(mRequestBuilder.build()).execute()
    }

    override fun shutdown() {
        if (!sharedClient && cache != null) {
            try {
                cache.close()
            } catch (ignored: IOException) {
            }
        }
    }

    companion object {
        private const val DEFAULT_USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 13; M2102J2SC Build/TKQ1.220829.002)"
    }
}
