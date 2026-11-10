package com.github.catvod.crawler

import android.util.Log

object SpiderDebug {

    @JvmStatic
    fun log(th: Throwable?) {
        try {
            Log.d("SpiderLog", th?.message, th)
        } catch (ignored: Throwable) {
        }
    }

    @JvmStatic
    fun log(msg: String?) {
        try {
            Log.d("SpiderLog", msg ?: "")
        } catch (ignored: Throwable) {
        }
    }

    @JvmStatic
    fun ec(i: Int): String = ""
}
