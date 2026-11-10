package xyz.doikki.videoplayer.player

import android.content.res.AssetFileDescriptor
import android.view.Surface
import android.view.SurfaceHolder
import kotlin.math.max

/**
 * 抽象的播放器，继承此接口扩展自己的播放器
 * Created by Doikki on 2017/12/21.
 */
abstract class AbstractPlayer {

    companion object {
        /**
         * 视频/音频开始渲染
         */
        const val MEDIA_INFO_RENDERING_START = 3

        /**
         * 缓冲开始
         */
        const val MEDIA_INFO_BUFFERING_START = 701

        /**
         * 缓冲结束
         */
        const val MEDIA_INFO_BUFFERING_END = 702

        /**
         * 视频旋转信息
         */
        const val MEDIA_INFO_VIDEO_ROTATION_CHANGED = 10001
    }

    /**
     * 播放器事件回调
     */
    @JvmField
    protected var mPlayerEventListener: PlayerEventListener? = null

    @Volatile
    private var mStartPosition: Long = 0

    @Volatile
    private var mStartPositionApplied = false

    /**
     * 初始化播放器实例
     */
    abstract fun initPlayer()

    /**
     * 设置播放地址
     *
     * @param path    播放地址
     * @param headers 播放地址请求头
     */
    abstract fun setDataSource(path: String, headers: Map<String, String>?)

    /**
     * 用于播放raw和asset里面的视频文件
     */
    abstract fun setDataSource(fd: AssetFileDescriptor)

    /**
     * 播放
     */
    abstract fun start()

    /**
     * 暂停
     */
    abstract fun pause()

    /**
     * 停止
     */
    abstract fun stop()

    /**
     * 准备开始播放（异步）
     */
    abstract fun prepareAsync()

    /**
     * 设置当前数据源准备完成后的起始播放位置。
     * 播放器实现应在回调 [PlayerEventListener.onPrepared] 前应用该位置。
     */
    open fun setStartPosition(position: Long) {
        mStartPosition = max(0, position)
        mStartPositionApplied = false
    }

    protected fun getStartPosition(): Long = mStartPosition

    protected fun markStartPositionApplied() {
        mStartPositionApplied = true
    }

    open fun isStartPositionApplied(): Boolean = mStartPositionApplied

    /**
     * 重置播放器
     */
    abstract fun reset()

    /**
     * Whether the render view remains attached after reset.
     */
    open fun keepRenderViewOnReset(): Boolean = false

    /**
     * 是否正在播放
     */
    abstract fun isPlaying(): Boolean

    /**
     * 调整进度
     */
    abstract fun seekTo(time: Long)

    /**
     * 释放播放器
     */
    abstract fun release()

    /**
     * 获取当前播放的位置
     */
    abstract fun getCurrentPosition(): Long

    /**
     * 获取视频总时长
     */
    abstract fun getDuration(): Long

    /**
     * 获取缓冲百分比
     */
    abstract fun getBufferedPercentage(): Int

    /**
     * 设置渲染视频的View,主要用于TextureView
     */
    abstract fun setSurface(surface: Surface)

    /**
     * 设置渲染视频的View,主要用于SurfaceView
     */
    abstract fun setDisplay(holder: SurfaceHolder)

    /**
     * 设置音量
     */
    abstract fun setVolume(v1: Float, v2: Float)

    /**
     * 设置是否循环播放
     */
    abstract fun setLooping(isLooping: Boolean)

    /**
     * 设置其他播放配置
     */
    abstract fun setOptions()

    /**
     * 设置播放速度
     */
    abstract fun setSpeed(speed: Float)

    /**
     * 获取播放速度
     */
    abstract fun getSpeed(): Float

    /**
     * 获取当前缓冲的网速
     */
    abstract fun getTcpSpeed(): Long

    /**
     * 绑定VideoView
     */
    open fun setPlayerEventListener(playerEventListener: PlayerEventListener?) {
        this.mPlayerEventListener = playerEventListener
    }

    interface PlayerEventListener {
        fun onError()
        fun onCompletion()
        fun onInfo(what: Int, extra: Int)
        fun onPrepared()
        fun onVideoSizeChanged(width: Int, height: Int)
    }
}
