package com.github.tvbox.osc.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * @author pj567
 * @date :2021/1/5
 * @description: 资源文件加载
 */
class RawRequestProcess(
    private val mContext: Context,
    private val fileName: String,
    private val resourceId: Int,
    private val mimeType: String
) : RequestProcess {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean {
        return session.method == NanoHTTPD.Method.GET && this.fileName.equals(fileName, ignoreCase = true)
    }

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: MutableMap<String, String>?,
        files: MutableMap<String, String>?
    ): NanoHTTPD.Response {
        val inputStream = mContext.resources.openRawResource(this.resourceId)
        return try {
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "$mimeType; charset=utf-8",
                inputStream,
                inputStream.available().toLong()
            )
        } catch (ioExc: IOException) {
            RemoteServer.createPlainTextResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "SERVER INTERNAL ERROR: IOException: " + ioExc.message
            )
        }
    }
}
