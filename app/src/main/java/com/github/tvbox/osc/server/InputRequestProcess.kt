package com.github.tvbox.osc.server

import fi.iki.elonen.NanoHTTPD

/**
 * @author pj567
 * @date :2021/1/5
 * @description: 响应按键和输入
 */
class InputRequestProcess(private val remoteServer: RemoteServer) : RequestProcess {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean {
        if (session.method == NanoHTTPD.Method.POST) {
            when (fileName) {
                "/action" -> return true
            }
        }
        return false
    }

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: MutableMap<String, String>?,
        files: MutableMap<String, String>?
    ): NanoHTTPD.Response {
        val mDataReceiver = remoteServer.dataReceiver
        when (fileName) {
            "/action" -> {
                if (params != null && params["do"] != null && mDataReceiver != null) {
                    val action = params["do"]
                    when (action) {
                        "search" -> {
                            val word = params["word"]
                            if (word != null) {
                                mDataReceiver.onTextReceived(word.trim())
                            }
                        }
                        "api" -> {
                            val url = params["url"]
                            if (url != null) {
                                mDataReceiver.onApiReceived(url.trim())
                            }
                        }
                        "liveApi" -> {
                            val url = params["url"]
                            if (url != null) {
                                mDataReceiver.onLiveApiReceived(url.trim())
                            }
                        }
                        "danmuApi" -> {
                            val url = params["url"]
                            if (url != null) {
                                mDataReceiver.onDanmuApiReceived(url.trim())
                            }
                        }
                        "push" -> {
                            val url = params["url"]
                            if (!url.isNullOrEmpty() && url.trim().isNotEmpty()) {
                                mDataReceiver.onPushReceived(url.trim())
                            }
                        }
                    }
                }
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok")
            }
            else -> return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found.")
        }
    }
}
