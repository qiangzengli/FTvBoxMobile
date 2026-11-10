package com.github.tvbox.osc.server

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
interface DataReceiver {
    /**
     * @param text
     */
    fun onTextReceived(text: String)

    fun onApiReceived(url: String)

    fun onLiveApiReceived(url: String)

    fun onDanmuApiReceived(url: String)

    fun onPushReceived(url: String)
}
