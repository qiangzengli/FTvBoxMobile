package com.github.tvbox.osc.util

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.Context.UI_MODE_SERVICE
import android.content.res.Configuration
import android.telephony.TelephonyManager
import android.util.DisplayMetrics

object ScreenUtils {

    @JvmStatic
    fun getSqrt(activity: Activity): Double {
        val wm = activity.windowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getMetrics(dm)
        val x = Math.pow((dm.widthPixels / dm.xdpi).toDouble(), 2.0)
        val y = Math.pow((dm.heightPixels / dm.ydpi).toDouble(), 2.0)
        return Math.sqrt(x + y)
    }

    private fun checkScreenLayoutIsTv(context: Context): Boolean {
        return (context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) > Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    private fun checkIsPhone(context: Context): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false
        return telephonyManager.phoneType != TelephonyManager.PHONE_TYPE_NONE
    }

    @JvmStatic
    fun isTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(UI_MODE_SERVICE) as? UiModeManager
        return (uiModeManager != null && uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) ||
                (checkScreenLayoutIsTv(context) && !checkIsPhone(context))
    }
}
