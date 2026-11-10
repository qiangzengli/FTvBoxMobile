package com.fbox.mobile.auth

import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.server.ControlManager
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

class PluginAuthClient(private val source: SourceBean) : AuthSession.Backend {
    private val base: String = ControlManager.get().getAddress(true)

    @Throws(Exception::class)
    override fun request(action: String, fields: Map<String, String>): JSONObject {
        if (!ConfigAuthProvider.supportsMobileApi(source) || ApiConfig.get().getSource(source.key) !== source) {
            throw IOException("配置线路已切换或不支持手机授权，请重新打开配置中心")
        }
        val address = HttpUrl.parse(base)
        if (address == null || !("127.0.0.1" == address.host() || "localhost" == address.host())) {
            throw IOException("本机配置服务不可用，请重试")
        }
        val url = address.newBuilder().encodedPath("/proxy").query(null)
            .addQueryParameter("do", "pan")
            .addQueryParameter("siteKey", source.key)
            .addQueryParameter("action", action)
        for ((key, value) in fields) {
            url.addQueryParameter(key, value)
        }
        HTTP.newCall(Request.Builder().url(url.build()).build()).execute().use { response ->
            if (!response.isSuccessful || response.body() == null) {
                throw IOException("配置服务暂时不可用，请重试")
            }
            val input = response.body()!!.source()
            input.request((1024 * 1024 + 1).toLong())
            if (input.buffer.size > 1024 * 1024) {
                throw IOException("配置服务响应异常")
            }
            return JSONObject(input.readUtf8())
        }
    }

    companion object {
        private val HTTP = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }
}
