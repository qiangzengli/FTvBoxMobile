package xyz.doikki.videoplayer.player

import android.app.Application
import xyz.doikki.videoplayer.util.L
import java.util.LinkedHashMap

/**
 * 视频播放器管理器，管理当前正在播放的VideoView，以及播放器配置
 * 你也可以用来保存常驻内存的VideoView，但是要注意通过Application Context创建，
 * 以免内存泄漏
 */
class VideoViewManager private constructor() {

    /**
     * 保存VideoView的容器
     */
    private val mVideoViews = LinkedHashMap<String, VideoView<*>>()

    /**
     * 是否在移动网络下直接播放视频
     */
    private var mPlayOnMobileNetwork: Boolean = getConfig().mPlayOnMobileNetwork

    companion object {
        private var sInstance: VideoViewManager? = null
        private var sConfig: VideoViewConfig? = null

        /**
         * 设置VideoViewConfig
         */
        @JvmStatic
        fun setConfig(config: VideoViewConfig?) {
            if (sConfig == null) {
                synchronized(VideoViewConfig::class.java) {
                    if (sConfig == null) {
                        sConfig = config ?: VideoViewConfig.newBuilder().build()
                    }
                }
            }
        }

        /**
         * 获取VideoViewConfig
         */
        @JvmStatic
        fun getConfig(): VideoViewConfig {
            setConfig(null)
            return sConfig!!
        }

        @JvmStatic
        fun instance(): VideoViewManager {
            if (sInstance == null) {
                synchronized(VideoViewManager::class.java) {
                    if (sInstance == null) {
                        sInstance = VideoViewManager()
                    }
                }
            }
            return sInstance!!
        }
    }

    /**
     * 获取是否在移动网络下直接播放视频配置
     */
    fun playOnMobileNetwork(): Boolean {
        return mPlayOnMobileNetwork
    }

    /**
     * 设置是否在移动网络下直接播放视频
     */
    fun setPlayOnMobileNetwork(playOnMobileNetwork: Boolean) {
        mPlayOnMobileNetwork = playOnMobileNetwork
    }

    /**
     * 添加VideoView
     * @param tag 相同tag的VideoView只会保存一个，如果tag相同则会release并移除前一个
     */
    fun add(videoView: VideoView<*>, tag: String) {
        if (videoView.context !is Application) {
            L.w(
                "The Context of this VideoView is not an Application Context," +
                        "you must remove it after release,or it will lead to memory leek."
            )
        }
        val old = get(tag)
        if (old != null) {
            old.release()
            remove(tag)
        }
        mVideoViews[tag] = videoView
    }

    fun get(tag: String): VideoView<*>? {
        return mVideoViews[tag]
    }

    fun remove(tag: String) {
        mVideoViews.remove(tag)
    }

    fun removeAll() {
        mVideoViews.clear()
    }

    /**
     * 释放掉和tag关联的VideoView，并将其从VideoViewManager中移除
     */
    @JvmOverloads
    fun releaseByTag(tag: String, isRemove: Boolean = true) {
        val videoView = get(tag)
        if (videoView != null) {
            videoView.release()
            if (isRemove) {
                remove(tag)
            }
        }
    }

    fun onBackPress(tag: String): Boolean {
        val videoView = get(tag) ?: return false
        return videoView.onBackPressed()
    }
}
