package xyz.doikki.videoplayer.player

import xyz.doikki.videoplayer.exo.ExoMediaPlayerFactory
import xyz.doikki.videoplayer.render.RenderViewFactory
import xyz.doikki.videoplayer.render.TextureRenderViewFactory

/**
 * 播放器全局配置
 */
class VideoViewConfig private constructor(builder: Builder) {

    @JvmField
    val mPlayOnMobileNetwork: Boolean = builder.mPlayOnMobileNetwork

    @JvmField
    val mEnableOrientation: Boolean = builder.mEnableOrientation

    @JvmField
    val mEnableAudioFocus: Boolean = builder.mEnableAudioFocus

    @JvmField
    val mIsEnableLog: Boolean = builder.mIsEnableLog

    @JvmField
    val mProgressManager: ProgressManager? = builder.mProgressManager

    @JvmField
    val mPlayerFactory: PlayerFactory<*>? = builder.mPlayerFactory ?: ExoMediaPlayerFactory.create()

    @JvmField
    val mScreenScaleType: Int = builder.mScreenScaleType

    @JvmField
    val mRenderViewFactory: RenderViewFactory? = builder.mRenderViewFactory ?: TextureRenderViewFactory.create()

    @JvmField
    val mAdaptCutout: Boolean = builder.mAdaptCutout

    companion object {
        @JvmStatic
        fun newBuilder(): Builder {
            return Builder()
        }
    }

    class Builder {
        @JvmField
        var mIsEnableLog = false
        @JvmField
        var mPlayOnMobileNetwork = true
        @JvmField
        var mEnableOrientation = false
        @JvmField
        var mEnableAudioFocus = true
        @JvmField
        var mProgressManager: ProgressManager? = null
        @JvmField
        var mPlayerFactory: PlayerFactory<*>? = null
        @JvmField
        var mScreenScaleType = 0
        @JvmField
        var mRenderViewFactory: RenderViewFactory? = null
        @JvmField
        var mAdaptCutout = true

        fun setEnableOrientation(enableOrientation: Boolean): Builder {
            mEnableOrientation = enableOrientation
            return this
        }

        fun setPlayOnMobileNetwork(playOnMobileNetwork: Boolean): Builder {
            mPlayOnMobileNetwork = playOnMobileNetwork
            return this
        }

        fun setEnableAudioFocus(enableAudioFocus: Boolean): Builder {
            mEnableAudioFocus = enableAudioFocus
            return this
        }

        fun setProgressManager(progressManager: ProgressManager?): Builder {
            mProgressManager = progressManager
            return this
        }

        fun setLogEnabled(enableLog: Boolean): Builder {
            mIsEnableLog = enableLog
            return this
        }

        fun setPlayerFactory(playerFactory: PlayerFactory<*>?): Builder {
            mPlayerFactory = playerFactory
            return this
        }

        fun setScreenScaleType(screenScaleType: Int): Builder {
            mScreenScaleType = screenScaleType
            return this
        }

        fun setRenderViewFactory(renderViewFactory: RenderViewFactory?): Builder {
            mRenderViewFactory = renderViewFactory
            return this
        }

        fun setAdaptCutout(adaptCutout: Boolean): Builder {
            mAdaptCutout = adaptCutout
            return this
        }

        fun build(): VideoViewConfig {
            return VideoViewConfig(this)
        }
    }
}
