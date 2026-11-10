package com.github.tvbox.osc.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.ArrayList

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
class CustomWebReceiver : BroadcastReceiver() {
    companion object {
        @JvmField
        var action: String = "android.content.movie.custom.web.Action"

        @JvmField
        var REFRESH_SOURCE: String = "source"
        @JvmField
        var REFRESH_LIVE: String = "live"
        @JvmField
        var REFRESH_PARSE: String = "parse"

        @JvmField
        var callback: MutableList<Callback> = ArrayList()
    }

    interface Callback {
        fun onChange(action: String, obj: Any?)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (action == intent.action && intent.extras != null) {
            val act = intent.extras?.getString("action") ?: return
            if (act == REFRESH_PARSE) {
                return
            } else if (act == REFRESH_LIVE) {
                return
            } else {
                return
            }
        }
    }
}
