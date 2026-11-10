package xyz.doikki.videoplayer.controller

import android.app.Activity
import android.content.pm.ActivityInfo

/**
 * 此类的目的是为了在ControlComponent中既能调用VideoView的api又能调用BaseVideoController的api，
 * 并对部分api做了封装，方便使用
 */
open class ControlWrapper(
    private val mPlayerControl: MediaPlayerControl,
    private val mController: IVideoController
) : MediaPlayerControl by mPlayerControl, IVideoController by mController {

    /**
     * 播放和暂停
     */
    open fun togglePlay() {
        if (isPlaying()) {
            pause()
        } else {
            start()
        }
    }

    /**
     * 横竖屏切换，会旋转屏幕
     */
    open fun toggleFullScreen(activity: Activity?) {
        if (activity == null || activity.isFinishing) return
        if (isFullScreen()) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            stopFullScreen()
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            startFullScreen()
        }
    }

    /**
     * 横竖屏切换，不会旋转屏幕
     */
    open fun toggleFullScreen() {
        if (isFullScreen()) {
            stopFullScreen()
        } else {
            startFullScreen()
        }
    }

    /**
     * 横竖屏切换，根据适配宽高决定是否旋转屏幕
     */
    open fun toggleFullScreenByVideoSize(activity: Activity?) {
        if (activity == null || activity.isFinishing) return
        val size = getVideoSize()
        val width = size[0]
        val height = size[1]
        if (isFullScreen()) {
            stopFullScreen()
            if (width > height) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        } else {
            startFullScreen()
            if (width > height) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    /**
     * 切换锁定状态
     */
    open fun toggleLockState() {
        setLocked(!isLocked())
    }

    /**
     * 切换显示/隐藏状态
     */
    open fun toggleShowState() {
        if (isShowing()) {
            hide()
        } else {
            show()
        }
    }
}
