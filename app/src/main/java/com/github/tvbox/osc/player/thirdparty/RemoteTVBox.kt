package com.github.tvbox.osc.player.thirdparty

import android.app.Activity
import android.text.TextUtils
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.server.RemoteServer
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.OkGoHelper
import com.orhanobut.hawk.Hawk
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RemoteTVBox {

    abstract class Callback {
        abstract fun found(viewHost: String, end: Boolean)
        abstract fun fail(all: Boolean, end: Boolean)
    }

    companion object {
        @JvmStatic
        fun run(activity: Activity?, url: String, title: String?, subtitle: String?, headers: HashMap<String, String>?): Boolean {
        val actionUrl = getAvalibleActionUrl()
        if (TextUtils.isEmpty(actionUrl)) {
            return false
        }
        try {
            var finalUrl = url
            if (headers != null && headers.isNotEmpty()) {
                finalUrl = "$finalUrl|"
                var idx = 0
                for (hk in headers.keys) {
                    finalUrl += URLEncoder.encode(hk, "UTF-8") + "=" + URLEncoder.encode(headers[hk], "UTF-8")
                    if (idx < headers.keys.size - 1) {
                        finalUrl += "&"
                    }
                    idx++
                }
            }
            val params: MutableMap<String, String> = HashMap()
            params["do"] = "push"
            params["url"] = finalUrl
            post(actionUrl, params, object : okhttp3.Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    val pushResult = response.body()?.string()
                    if ("ok" == pushResult) {
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return true
    }

    @JvmStatic
    fun searchAvalible(callback: Callback) {
        val localIp = RemoteServer.getLocalIPAddress(App.getInstance())
        val divisionIp = if (TextUtils.isEmpty(localIp)) -1 else localIp.lastIndexOf(".")
        if (divisionIp <= 0) {
            callback.fail(true, true)
            return
        }
        val prefix = localIp.substring(0, divisionIp + 1)
        val port = 9978
        val finishedNum = AtomicInteger(0)
        val foundNum = AtomicInteger(0)
        val total = 254
        for (i in 1..255) {
            val ip = prefix + i
            if (ip == localIp) {
                continue
            }
            val actionUrl = "http://$ip:$port/action"
            val viewHost = "$ip:$port"
            try {
                post(actionUrl, null, object : okhttp3.Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        notifySearchFail(callback, foundNum, finishedNum, total)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val result = response.body()?.string() ?: ""
                            val end = finishedNum.incrementAndGet() == total
                            if ("ok".equals(result, ignoreCase = true)) {
                                foundNum.incrementAndGet()
                                callback.found(viewHost, end)
                            } else {
                                callback.fail(foundNum.get() == 0 && end, end)
                            }
                        } finally {
                            response.close()
                        }
                    }
                })
            } catch (e: Exception) {
                notifySearchFail(callback, foundNum, finishedNum, total)
            }
        }
    }

    private fun notifySearchFail(callback: Callback, foundNum: AtomicInteger, finishedNum: AtomicInteger, total: Int) {
        val end = finishedNum.incrementAndGet() == total
        callback.fail(foundNum.get() == 0 && end, end)
    }

    @JvmStatic
    fun getAvalible(): String? {
        return Hawk.get(HawkConfig.REMOTE_TVBOX, null)
    }

    @JvmStatic
    fun getAvalibleActionUrl(): String {
        val avail = getAvalible() ?: return ""
        return "http://$avail/action"
    }

    @JvmStatic
    fun setAvalible(viewHost: String?) {
        Hawk.put(HawkConfig.REMOTE_TVBOX, viewHost)
    }

    @JvmStatic
    fun post(url: String, params: Map<String, String>?, callback: okhttp3.Callback) {
        val base = OkGoHelper.getDefaultClient()
        val builder = if (base != null) base.newBuilder() else OkHttpClient.Builder().proxySelector(OkGoHelper.proxySelector()).proxyAuthenticator(OkGoHelper.proxyAuthenticator())
        builder.readTimeout(1000, TimeUnit.MILLISECONDS)
        builder.writeTimeout(1000, TimeUnit.MILLISECONDS)
        builder.connectTimeout(1000, TimeUnit.MILLISECONDS)
        val client = builder.build()
        val formBodyBuilder = FormBody.Builder()
        if (params != null && params.isNotEmpty()) {
            for ((key, value) in params) {
                formBodyBuilder.add(key, value)
            }
        }
        val formBody = formBodyBuilder.build()
        client.newCall(Request.Builder().url(url).post(formBody).build()).enqueue(callback)
    }
}
}
