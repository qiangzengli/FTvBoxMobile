package xyz.doikki.videoplayer.util

import android.util.Log
import xyz.doikki.videoplayer.player.VideoViewManager

/**
 * 日志类
 * Created by Doikki on 2017/6/5.
 */
object L {

    private const val TAG = "DKPlayer"

    private var isDebug: Boolean = VideoViewManager.getConfig().mIsEnableLog

    @JvmStatic
    fun d(msg: String) {
        if (isDebug) {
            Log.d(TAG, msg)
        }
    }

    @JvmStatic
    fun e(msg: String) {
        if (isDebug) {
            Log.e(TAG, msg)
        }
    }

    @JvmStatic
    fun i(msg: String) {
        if (isDebug) {
            Log.i(TAG, msg)
        }
    }

    @JvmStatic
    fun w(msg: String) {
        if (isDebug) {
            Log.w(TAG, msg)
        }
    }

    @JvmStatic
    fun setDebug(isDebug: Boolean) {
        this.isDebug = isDebug
    }
}
