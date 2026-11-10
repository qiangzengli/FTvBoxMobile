package com.github.tvbox.osc.server

import fi.iki.elonen.NanoHTTPD

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
interface RequestProcess {
    /**
     * isRequest
     *
     * @param session
     * @param fileName
     * @return
     */
    fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String): Boolean

    /**
     * doResponse
     *
     * @param session
     * @param fileName
     * @param params
     * @param files
     * @return
     */
    fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String,
        params: MutableMap<String, String>?,
        files: MutableMap<String, String>?
    ): NanoHTTPD.Response

    companion object {
        const val KEY_ACTION_PRESSED = 0
        const val KEY_ACTION_DOWN = 1
        const val KEY_ACTION_UP = 2
    }
}
