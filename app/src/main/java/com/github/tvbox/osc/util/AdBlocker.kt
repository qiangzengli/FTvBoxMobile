package com.github.tvbox.osc.util

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.ArrayList

object AdBlocker {
    private val AD_HOSTS: MutableList<String> = ArrayList()

    @JvmStatic
    fun clear() {
        AD_HOSTS.clear()
    }

    @JvmStatic
    fun isEmpty(): Boolean {
        return AD_HOSTS.isEmpty()
    }

    @JvmStatic
    fun addAdHost(host: String) {
        AD_HOSTS.add(host)
    }

    @JvmStatic
    fun hasHost(host: String): Boolean {
        return AD_HOSTS.contains(host)
    }

    @JvmStatic
    fun isAd(url: String): Boolean {
        val lowerUrl = url.lowercase()
        for (adHost in AD_HOSTS) {
            if (lowerUrl.contains(adHost)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun createEmptyResource(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
    }
}
