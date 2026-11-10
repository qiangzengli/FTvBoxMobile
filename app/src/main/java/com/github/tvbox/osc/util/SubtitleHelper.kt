package com.github.tvbox.osc.util

import android.app.Activity
import com.orhanobut.hawk.Hawk

object SubtitleHelper {
    @JvmStatic
    fun getSubtitleTextAutoSize(activity: Activity): Int {
        val screenSqrt = ScreenUtils.getSqrt(activity)
        var subtitleTextSize = 16
        if (screenSqrt > 7.0 && screenSqrt <= 13.0) {
            subtitleTextSize = 24
        } else if (screenSqrt > 13.0 && screenSqrt <= 50.0) {
            subtitleTextSize = 36
        } else if (screenSqrt > 50.0) {
            subtitleTextSize = 46
        }
        return subtitleTextSize
    }

    @JvmStatic
    fun getTextSize(activity: Activity): Int {
        val autoSize = getSubtitleTextAutoSize(activity)
        return Hawk.get(HawkConfig.SUBTITLE_TEXT_SIZE, autoSize)
    }

    @JvmStatic
    fun setTextSize(size: Int) {
        Hawk.put(HawkConfig.SUBTITLE_TEXT_SIZE, size)
    }

    @JvmStatic
    fun getTimeDelay(): Int {
        return Hawk.get(HawkConfig.SUBTITLE_TIME_DELAY, 0)
    }

    @JvmStatic
    fun setTimeDelay(delay: Int) {
        Hawk.put(HawkConfig.SUBTITLE_TIME_DELAY, delay)
    }

    @JvmStatic
    fun getExoSubtitleScale(): Int {
        return Hawk.get(HawkConfig.SUBTITLE_EXO_SCALE, 100)
    }

    @JvmStatic
    fun setExoSubtitleScale(scale: Int) {
        Hawk.put(HawkConfig.SUBTITLE_EXO_SCALE, scale)
    }

    @JvmStatic
    fun getExoSubtitlePosition(): Float {
        return Hawk.get(HawkConfig.SUBTITLE_EXO_POSITION, 0.0f)
    }

    @JvmStatic
    fun setExoSubtitlePosition(position: Float) {
        Hawk.put(HawkConfig.SUBTITLE_EXO_POSITION, position)
    }
}
