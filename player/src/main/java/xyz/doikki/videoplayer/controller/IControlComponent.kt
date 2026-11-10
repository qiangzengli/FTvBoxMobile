package xyz.doikki.videoplayer.controller

import android.view.View
import android.view.animation.Animation

/**
 * 可继承此接口实现自己的控制ui，以及监听播放器的状态
 */
interface IControlComponent {

    /**
     * 将 ControlWrapper 传递到当前 ControlComponent 中
     */
    fun attach(controlWrapper: ControlWrapper)

    /**
     * 如果 ControlComponent 是 View，返回当前控件（this）即可
     * 如果不是，返回null
     */
    fun getView(): View?

    /**
     * 回调控制器显示和隐藏状态，
     * 此方法可用于控制 ControlComponent 中的控件的跟随手指点击显示和隐藏
     * @param isVisible true 代表要显示， false 代表要隐藏
     * @param anim 显示和隐藏的动画，是一个补间Alpha动画
     */
    fun onVisibilityChanged(isVisible: Boolean, anim: Animation?)

    /**
     * 回调播放器的播放器状态
     * @param playState 播放状态
     */
    fun onPlayStateChanged(playState: Int)

    /**
     * 回调播放器的状态
     * @param playerState 播放器状态
     */
    fun onPlayerStateChanged(playerState: Int)

    /**
     * 回调播放进度，1秒回调一次
     * @param duration 视频总时长
     * @param position 播放进度
     */
    fun setProgress(duration: Int, position: Int)

    /**
     * 回调控制器是否被锁定
     * @param isLocked 是否锁定
     */
    fun onLockStateChanged(isLocked: Boolean)
}
