package tv.danmaku.ijk.media.player.pragma

import android.util.Log
import java.util.Locale

@Suppress("SameParameterValue", "WeakerAccess")
object DebugLog {
    const val ENABLE_ERROR = Pragma.ENABLE_VERBOSE
    const val ENABLE_INFO = Pragma.ENABLE_VERBOSE
    const val ENABLE_WARN = Pragma.ENABLE_VERBOSE
    const val ENABLE_DEBUG = Pragma.ENABLE_VERBOSE
    const val ENABLE_VERBOSE = Pragma.ENABLE_VERBOSE

    @JvmStatic
    fun e(tag: String?, msg: String?) {
        if (ENABLE_ERROR) {
            Log.e(tag, msg.orEmpty())
        }
    }

    @JvmStatic
    fun e(tag: String?, msg: String?, tr: Throwable?) {
        if (ENABLE_ERROR) {
            Log.e(tag, msg.orEmpty(), tr)
        }
    }

    @JvmStatic
    fun efmt(tag: String?, fmt: String, vararg args: Any?) {
        if (ENABLE_ERROR) {
            val msg = String.format(Locale.US, fmt, *args)
            Log.e(tag, msg)
        }
    }

    @JvmStatic
    fun i(tag: String?, msg: String?) {
        if (ENABLE_INFO) {
            Log.i(tag, msg.orEmpty())
        }
    }

    @JvmStatic
    fun i(tag: String?, msg: String?, tr: Throwable?) {
        if (ENABLE_INFO) {
            Log.i(tag, msg.orEmpty(), tr)
        }
    }

    @JvmStatic
    fun ifmt(tag: String?, fmt: String, vararg args: Any?) {
        if (ENABLE_INFO) {
            val msg = String.format(Locale.US, fmt, *args)
            Log.i(tag, msg)
        }
    }

    @JvmStatic
    fun w(tag: String?, msg: String?) {
        if (ENABLE_WARN) {
            Log.w(tag, msg.orEmpty())
        }
    }

    @JvmStatic
    fun w(tag: String?, msg: String?, tr: Throwable?) {
        if (ENABLE_WARN) {
            Log.w(tag, msg.orEmpty(), tr)
        }
    }

    @JvmStatic
    fun wfmt(tag: String?, fmt: String, vararg args: Any?) {
        if (ENABLE_WARN) {
            val msg = String.format(Locale.US, fmt, *args)
            Log.w(tag, msg)
        }
    }

    @JvmStatic
    fun d(tag: String?, msg: String?) {
        if (ENABLE_DEBUG) {
            Log.d(tag, msg.orEmpty())
        }
    }

    @JvmStatic
    fun d(tag: String?, msg: String?, tr: Throwable?) {
        if (ENABLE_DEBUG) {
            Log.d(tag, msg.orEmpty(), tr)
        }
    }

    @JvmStatic
    fun dfmt(tag: String?, fmt: String, vararg args: Any?) {
        if (ENABLE_DEBUG) {
            val msg = String.format(Locale.US, fmt, *args)
            Log.d(tag, msg)
        }
    }

    @JvmStatic
    fun v(tag: String?, msg: String?) {
        if (ENABLE_VERBOSE) {
            Log.v(tag, msg.orEmpty())
        }
    }

    @JvmStatic
    fun v(tag: String?, msg: String?, tr: Throwable?) {
        if (ENABLE_VERBOSE) {
            Log.v(tag, msg.orEmpty(), tr)
        }
    }

    @JvmStatic
    fun vfmt(tag: String?, fmt: String, vararg args: Any?) {
        if (ENABLE_VERBOSE) {
            val msg = String.format(Locale.US, fmt, *args)
            Log.v(tag, msg)
        }
    }

    @JvmStatic
    fun printStackTrace(e: Throwable) {
        if (ENABLE_WARN) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun printCause(e: Throwable) {
        if (ENABLE_WARN) {
            var current: Throwable = e
            val cause = e.cause
            if (cause != null) {
                current = cause
            }
            printStackTrace(current)
        }
    }
}
