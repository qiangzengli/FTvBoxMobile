package com.undcover.freedom.pyramid

import android.content.Context
import android.widget.Toast

/**
 * Created by UndCover on 16/9/7.
 */
class PyToast {
    companion object {
        private var innerToast: Toast? = null
        private var mContext: Context? = null
        private var sInstance: PyToast? = null

        @JvmStatic
        fun init(context: Context) {
            mContext = context
        }

        @JvmStatic
        fun getInstance(): PyToast {
            if (sInstance == null) {
                synchronized(PyToast::class.java) {
                    if (sInstance == null) {
                        sInstance = PyToast()
                    }
                }
            }
            return sInstance!!
        }

        @JvmStatic
        fun showCancelableToast(msg: String) {
            showCancelableToast(msg, Toast.LENGTH_SHORT)
        }

        @JvmStatic
        fun showCancelableToast(msg: String, duration: Int) {
            innerToast?.cancel()
            val context = mContext ?: return
            innerToast = Toast.makeText(context, msg, duration).apply { show() }
        }

        @JvmStatic
        fun showMessage(msg: String, duration: Int) {
            val context = mContext ?: return
            Toast.makeText(context, msg, duration).show()
        }
    }
}
