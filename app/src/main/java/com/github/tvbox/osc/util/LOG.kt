package com.github.tvbox.osc.util

import android.util.Log

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
object LOG {
    private var TAG = "TVBox-runtime"
    private const val MAX_LOG_LENGTH = 3000

    @JvmStatic
    fun e(msg: String?) {
        Log.e(TAG, "" + msg)
    }

    @JvmStatic
    fun i(msg: String?) {
        Log.i(TAG, "" + msg)
    }

    @JvmStatic
    fun longI(prefix: String?, msg: String?) {
        longLog(Log.INFO, prefix, msg)
    }

    @JvmStatic
    fun longE(prefix: String?, msg: String?) {
        longLog(Log.ERROR, prefix, msg)
    }

    private fun longLog(priority: Int, prefix: String?, msg: String?) {
        val text = msg ?: "null"
        val title = prefix ?: ""
        val length = text.length
        if (length <= MAX_LOG_LENGTH) {
            Log.println(priority, TAG, title + text)
            return
        }
        val count = (length + MAX_LOG_LENGTH - 1) / MAX_LOG_LENGTH
        for (i in 0 until count) {
            val start = i * MAX_LOG_LENGTH
            val end = Math.min(start + MAX_LOG_LENGTH, length)
            Log.println(priority, TAG, title + "[" + (i + 1) + "/" + count + "] " + text.substring(start, end))
        }
    }
}
