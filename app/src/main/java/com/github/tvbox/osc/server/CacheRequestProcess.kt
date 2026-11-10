package com.github.tvbox.osc.server

import android.text.TextUtils
import com.orhanobut.hawk.Hawk
import fi.iki.elonen.NanoHTTPD

class CacheRequestProcess : RequestProcess {
    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean {
        return fileName.startsWith("/cache")
    }

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        paramsParam: MutableMap<String, String>?,
        files: MutableMap<String, String>?
    ): NanoHTTPD.Response {
        val params = paramsParam ?: HashMap(session.parms)
        if (files != null && files.isNotEmpty()) params.putAll(files)
        val action = params["do"]
        val key = params["key"]
        if (TextUtils.isEmpty(key)) return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "")
        val cacheKey = getKey(params["rule"], key!!)
        if ("get" == action) return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, Hawk.get(cacheKey, ""))
        if ("set" == action) {
            val value = params["value"]
            Hawk.put(cacheKey, value ?: "")
        }
        if ("del" == action) Hawk.delete(cacheKey)
        return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "OK")
    }

    private fun getKey(rule: String?, key: String): String {
        return "cache_" + (if (TextUtils.isEmpty(rule)) "" else rule + "_") + key
    }
}
