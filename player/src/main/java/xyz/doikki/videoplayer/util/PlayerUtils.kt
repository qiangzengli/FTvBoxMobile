package xyz.doikki.videoplayer.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Point
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.telephony.TelephonyManager
import android.util.TypedValue
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

/**
 * 播放器相关工具类
 * Created by Doikki on 2017/4/10.
 */
object PlayerUtils {
    private var lastTotalRxBytes: Long = 0
    private var lastTimeStamp: Long = 0

    const val NO_NETWORK = 0
    const val NETWORK_CLOSED = 1
    const val NETWORK_ETHERNET = 2
    const val NETWORK_WIFI = 3
    const val NETWORK_MOBILE = 4
    const val NETWORK_UNKNOWN = -1

    /**
     * 获取状态栏高度
     */
    @JvmStatic
    fun getStatusBarHeight(context: Context): Double {
        var statusBarHeight = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        }
        return statusBarHeight.toDouble()
    }

    /**
     * 获取竖屏下状态栏高度
     */
    @JvmStatic
    fun getStatusBarHeightPortrait(context: Context): Double {
        var statusBarHeight = 0
        val resourceId = context.resources.getIdentifier("status_bar_height_portrait", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        }
        return statusBarHeight.toDouble()
    }

    /**
     * 获取NavigationBar的高度
     */
    @JvmStatic
    fun getNavigationBarHeight(context: Context): Int {
        if (!hasNavigationBar(context)) {
            return 0
        }
        val resources: Resources = context.resources
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return resources.getDimensionPixelSize(resourceId)
    }

    /**
     * 是否存在NavigationBar
     */
    @JvmStatic
    fun hasNavigationBar(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val display = getWindowManager(context).defaultDisplay
            val size = Point()
            val realSize = Point()
            display.getSize(size)
            display.getRealSize(realSize)
            realSize.x != size.x || realSize.y != size.y
        } else {
            val menu = ViewConfiguration.get(context).hasPermanentMenuKey()
            val back = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK)
            !(menu || back)
        }
    }

    /**
     * 获取屏幕宽度
     */
    @JvmStatic
    fun getScreenWidth(context: Context, isIncludeNav: Boolean): Int {
        return if (isIncludeNav) {
            context.resources.displayMetrics.widthPixels + getNavigationBarHeight(context)
        } else {
            context.resources.displayMetrics.widthPixels
        }
    }

    /**
     * 获取屏幕高度
     */
    @JvmStatic
    fun getScreenHeight(context: Context, isIncludeNav: Boolean): Int {
        return if (isIncludeNav) {
            context.resources.displayMetrics.heightPixels + getNavigationBarHeight(context)
        } else {
            context.resources.displayMetrics.heightPixels
        }
    }

    /**
     * 获取Activity
     */
    @JvmStatic
    fun scanForActivity(context: Context?): Activity? {
        if (context == null) return null
        if (context is Activity) {
            return context
        } else if (context is ContextWrapper) {
            return scanForActivity(context.baseContext)
        }
        return null
    }

    /**
     * dp转为px
     */
    @JvmStatic
    fun dp2px(context: Context, dpValue: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dpValue,
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * sp转为px
     */
    @JvmStatic
    fun sp2px(context: Context, dpValue: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            dpValue,
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * 如果WindowManager还未创建，则创建一个新的WindowManager返回。否则返回当前已创建的WindowManager。
     */
    @JvmStatic
    fun getWindowManager(context: Context): WindowManager {
        return context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    /**
     * 边缘检测
     */
    @JvmStatic
    fun isEdge(context: Context, e: MotionEvent): Boolean {
        val edgeSize = dp2px(context, 40f)
        return (e.rawX < edgeSize ||
                e.rawX > getScreenWidth(context, true) - edgeSize ||
                e.rawY < edgeSize ||
                e.rawY > getScreenHeight(context, true) - edgeSize)
    }

    /**
     * 判断当前网络类型
     */
    @Suppress("DEPRECATION")
    @JvmStatic
    fun getNetworkType(context: Context): Int {
        val connectMgr = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return NO_NETWORK

        val networkInfo = connectMgr.activeNetworkInfo ?: return NO_NETWORK
        if (!networkInfo.isConnected) {
            return NETWORK_CLOSED
        }
        return when (networkInfo.type) {
            ConnectivityManager.TYPE_ETHERNET -> NETWORK_ETHERNET
            ConnectivityManager.TYPE_WIFI -> NETWORK_WIFI
            ConnectivityManager.TYPE_MOBILE -> {
                when (networkInfo.subtype) {
                    TelephonyManager.NETWORK_TYPE_GPRS,
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_CDMA,
                    TelephonyManager.NETWORK_TYPE_1xRTT,
                    TelephonyManager.NETWORK_TYPE_IDEN,
                    TelephonyManager.NETWORK_TYPE_UMTS,
                    TelephonyManager.NETWORK_TYPE_EVDO_0,
                    TelephonyManager.NETWORK_TYPE_EVDO_A,
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_EVDO_B,
                    TelephonyManager.NETWORK_TYPE_EHRPD,
                    TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_LTE,
                    TelephonyManager.NETWORK_TYPE_NR -> NETWORK_MOBILE
                    else -> NETWORK_UNKNOWN
                }
            }
            else -> NETWORK_UNKNOWN
        }
    }

    /**
     * 通过反射获取Application
     */
    @SuppressLint("PrivateApi")
    @Deprecated("不再使用，后期谷歌可能封掉该接口")
    @JvmStatic
    fun getApplication(): Application? {
        return try {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication").invoke(null) as? Application
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取当前系统时间
     */
    @JvmStatic
    fun getCurrentSystemTime(): String {
        val simpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return simpleDateFormat.format(Date())
    }

    /**
     * 格式化时间
     */
    @JvmStatic
    fun stringForTime(timeMs: Int): String {
        val safeMs = if (timeMs < 0) 0 else timeMs
        val totalSeconds = safeMs / 1000

        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 格式化时间2
     */
    @JvmStatic
    fun seconds2Time(timeMs: Int): String {
        val safeMs = if (timeMs < 0) 0 else timeMs
        val totalSeconds = safeMs / 1000

        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    @JvmStatic
    fun safeTimeMs(timeMs: Long): Int {
        if (timeMs <= 0) {
            return 0
        }
        return if (timeMs > Int.MAX_VALUE) {
            Int.MAX_VALUE
        } else {
            timeMs.toInt()
        }
    }

    /**
     * 获取集合的快照
     */
    @JvmStatic
    fun <T> getSnapshot(other: Collection<T>): List<T> {
        val result = ArrayList<T>(other.size)
        for (item in other) {
            if (item != null) {
                result.add(item)
            }
        }
        return result
    }

    @JvmStatic
    fun getNetSpeed(context: Context?): Long {
        if (context == null) {
            return 0
        }
        val uidRx = TrafficStats.getUidRxBytes(context.applicationInfo.uid)
        val nowTotalRxBytes = if (uidRx == TrafficStats.UNSUPPORTED.toLong()) 0 else TrafficStats.getTotalRxBytes()
        val nowTimeStamp = System.currentTimeMillis()
        val calculationTime = nowTimeStamp - lastTimeStamp
        if (calculationTime == 0L) {
            return calculationTime
        }

        val speed = (nowTotalRxBytes - lastTotalRxBytes) * 1000 / calculationTime
        lastTimeStamp = nowTimeStamp
        lastTotalRxBytes = nowTotalRxBytes
        return speed
    }
}
