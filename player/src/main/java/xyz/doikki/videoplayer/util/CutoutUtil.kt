package xyz.doikki.videoplayer.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.DisplayCutout
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import java.lang.reflect.Method

/**
 * 刘海屏工具
 */
object CutoutUtil {

    /**
     * 是否为允许全屏界面显示内容到刘海区域的刘海屏机型（与AndroidManifest中配置对应）
     */
    @JvmStatic
    fun allowDisplayToCutout(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val window = activity.window ?: return false
            val decorView = window.decorView
            val windowInsets = decorView.rootWindowInsets ?: return false
            val displayCutout = windowInsets.displayCutout ?: return false
            val boundingRects: List<Rect> = displayCutout.boundingRects
            boundingRects.isNotEmpty()
        } else {
            hasCutoutHuawei(activity) ||
                    hasCutoutOPPO(activity) ||
                    hasCutoutVIVO(activity) ||
                    hasCutoutXIAOMI(activity)
        }
    }

    private fun hasCutoutHuawei(activity: Activity): Boolean {
        if (!Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true)) {
            return false
        }
        return try {
            val cl = activity.classLoader
            val hwNotchSizeUtil = cl.loadClass("com.huawei.android.util.HwNotchSizeUtil")
            if (hwNotchSizeUtil != null) {
                val get: Method = hwNotchSizeUtil.getMethod("hasNotchInScreen")
                get.invoke(hwNotchSizeUtil) as Boolean
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun hasCutoutOPPO(activity: Activity): Boolean {
        if (!Build.MANUFACTURER.equals("oppo", ignoreCase = true)) {
            return false
        }
        return activity.packageManager.hasSystemFeature("com.oppo.feature.screen.heteromorphism")
    }

    @SuppressLint("PrivateApi")
    private fun hasCutoutVIVO(activity: Activity): Boolean {
        if (!Build.MANUFACTURER.equals("vivo", ignoreCase = true)) {
            return false
        }
        return try {
            val cl = activity.classLoader
            val ftFeatureUtil = cl.loadClass("android.util.FtFeature")
            if (ftFeatureUtil != null) {
                val get: Method = ftFeatureUtil.getMethod("isFeatureSupport", Int::class.javaPrimitiveType)
                get.invoke(ftFeatureUtil, 0x00000020) as Boolean
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("PrivateApi")
    private fun hasCutoutXIAOMI(activity: Activity): Boolean {
        if (!Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)) {
            return false
        }
        return try {
            val cl = activity.classLoader
            val systemProperties = cl.loadClass("android.os.SystemProperties")
            val getInt: Method = systemProperties.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
            val hasCutout = getInt.invoke(systemProperties, "ro.miui.notch", 0) as Int
            hasCutout == 1
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun adaptCutoutAboveAndroidP(context: Context?, isAdapt: Boolean) {
        val activity = PlayerUtils.scanForActivity(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = activity.window.attributes
            lp.layoutInDisplayCutoutMode = if (isAdapt) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            activity.window.attributes = lp
        }
    }

    @JvmStatic
    fun adaptCutoutAboveAndroidP(dialog: Dialog, isAdapt: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val window = dialog.window ?: return
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = if (isAdapt) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            window.attributes = lp
        }
    }
}
