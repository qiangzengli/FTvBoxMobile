package xyz.doikki.videoplayer.ijk

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Surface
import android.view.SurfaceHolder
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import tv.danmaku.ijk.media.player.misc.ITrackInfo
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo
import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper
import xyz.doikki.videoplayer.player.AbstractPlayer
import xyz.doikki.videoplayer.player.VideoViewManager
import xyz.doikki.videoplayer.util.PlayerUtils
import kotlin.concurrent.thread

open class IjkPlayer(context: Context) : AbstractPlayer(),
    IMediaPlayer.OnErrorListener,
    IMediaPlayer.OnCompletionListener,
    IMediaPlayer.OnInfoListener,
    IMediaPlayer.OnBufferingUpdateListener,
    IMediaPlayer.OnPreparedListener,
    IMediaPlayer.OnVideoSizeChangedListener,
    IjkMediaPlayer.OnNativeInvokeListener {

    @JvmField
    protected var mMediaPlayer: IjkMediaPlayer? = null
    private var mBufferedPercent = 0
    private val mAppContext: Context = context

    override fun initPlayer() {
        val player = IjkMediaPlayer()
        mMediaPlayer = player
        IjkMediaPlayer.native_setLogLevel(
            if (VideoViewManager.getConfig().mIsEnableLog) IjkMediaPlayer.IJK_LOG_INFO else IjkMediaPlayer.IJK_LOG_SILENT
        )
        setOptions()
        player.setOnErrorListener(this)
        player.setOnCompletionListener(this)
        player.setOnInfoListener(this)
        player.setOnBufferingUpdateListener(this)
        player.setOnPreparedListener(this)
        player.setOnVideoSizeChangedListener(this)
        player.setOnNativeInvokeListener(this)
    }

    override fun setOptions() {
    }

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        try {
            val uri = Uri.parse(path)
            if (ContentResolver.SCHEME_ANDROID_RESOURCE == uri.scheme) {
                val rawDataSourceProvider = RawDataSourceProvider.create(mAppContext, uri)
                if (rawDataSourceProvider != null) {
                    mMediaPlayer?.setDataSource(rawDataSourceProvider)
                }
            } else {
                val mutableHeaders = headers?.toMutableMap()
                if (mutableHeaders != null) {
                    mutableHeaders.remove(ExoMediaSourceHelper.HEADER_FORMAT)
                    val userAgent = mutableHeaders["User-Agent"]
                    if (!TextUtils.isEmpty(userAgent)) {
                        mMediaPlayer?.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", userAgent)
                        mutableHeaders.remove("User-Agent")
                    }
                }
                mMediaPlayer?.setDataSource(mAppContext, uri, mutableHeaders)
            }
        } catch (e: Exception) {
            mPlayerEventListener?.onError()
        }
    }

    override fun setDataSource(fd: AssetFileDescriptor) {
        try {
            mMediaPlayer?.setDataSource(RawDataSourceProvider(fd))
        } catch (e: Exception) {
            mPlayerEventListener?.onError()
        }
    }

    override fun pause() {
        try {
            mMediaPlayer?.pause()
        } catch (e: IllegalStateException) {
            mPlayerEventListener?.onError()
        }
    }

    override fun start() {
        try {
            mMediaPlayer?.start()
        } catch (e: IllegalStateException) {
            mPlayerEventListener?.onError()
        }
    }

    override fun stop() {
        try {
            mMediaPlayer?.stop()
        } catch (e: IllegalStateException) {
            mPlayerEventListener?.onError()
        }
    }

    override fun prepareAsync() {
        try {
            mMediaPlayer?.prepareAsync()
        } catch (e: IllegalStateException) {
            mPlayerEventListener?.onError()
        }
    }

    override fun reset() {
        mMediaPlayer?.reset()
        mMediaPlayer?.setOnVideoSizeChangedListener(this)
        setOptions()
    }

    override fun isPlaying(): Boolean {
        return mMediaPlayer?.isPlaying() ?: false
    }

    override fun seekTo(time: Long) {
        try {
            mMediaPlayer?.seekTo(PlayerUtils.safeTimeMs(time).toLong())
        } catch (e: IllegalStateException) {
            mPlayerEventListener?.onError()
        }
    }

    override fun release() {
        val player = mMediaPlayer
        player?.setOnErrorListener(null)
        player?.setOnCompletionListener(null)
        player?.setOnInfoListener(null)
        player?.setOnBufferingUpdateListener(null)
        player?.setOnPreparedListener(null)
        player?.setOnVideoSizeChangedListener(null)
        thread {
            try {
                player?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getCurrentPosition(): Long {
        return mMediaPlayer?.getCurrentPosition() ?: 0L
    }

    override fun getDuration(): Long {
        return mMediaPlayer?.getDuration() ?: 0L
    }

    override fun getBufferedPercentage(): Int {
        return mBufferedPercent
    }

    override fun setSurface(surface: Surface?) {
        mMediaPlayer?.setSurface(surface)
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        mMediaPlayer?.setDisplay(holder)
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        mMediaPlayer?.setVolume(leftVolume, rightVolume)
    }

    override fun setLooping(isLooping: Boolean) {
        mMediaPlayer?.setLooping(isLooping)
    }

    override fun setSpeed(speed: Float) {
        mMediaPlayer?.setSpeed(speed)
    }

    override fun getSpeed(): Float {
        return mMediaPlayer?.getSpeed(0f) ?: 1f
    }

    override fun getTcpSpeed(): Long {
        return mMediaPlayer?.tcpSpeed ?: 0L
    }

    override fun onError(mp: IMediaPlayer, what: Int, extra: Int): Boolean {
        mPlayerEventListener?.onError()
        return true
    }

    override fun onCompletion(mp: IMediaPlayer) {
        mPlayerEventListener?.onCompletion()
    }

    override fun onInfo(mp: IMediaPlayer, what: Int, extra: Int): Boolean {
        mPlayerEventListener?.onInfo(what, extra)
        return true
    }

    override fun onBufferingUpdate(mp: IMediaPlayer, percent: Int) {
        mBufferedPercent = percent
    }

    override fun onPrepared(mp: IMediaPlayer) {
        val startPosition = getStartPosition()
        if (startPosition > 0) {
            try {
                mMediaPlayer?.seekTo(PlayerUtils.safeTimeMs(startPosition).toLong())
            } catch (e: IllegalStateException) {
                mPlayerEventListener?.onError()
                return
            }
        }
        markStartPositionApplied()
        mPlayerEventListener?.onPrepared()
        if (!isVideo()) {
            mPlayerEventListener?.onInfo(AbstractPlayer.MEDIA_INFO_RENDERING_START, 0)
        }
    }

    private fun isVideo(): Boolean {
        val trackInfo = mMediaPlayer?.getTrackInfo() ?: return false
        for (info in trackInfo) {
            if (info.getTrackType() == ITrackInfo.MEDIA_TRACK_TYPE_VIDEO) {
                return true
            }
        }
        return false
    }

    override fun onVideoSizeChanged(mp: IMediaPlayer, width: Int, height: Int, sar_num: Int, sar_den: Int) {
        val videoWidth = mp.getVideoWidth()
        val videoHeight = mp.getVideoHeight()
        if (videoWidth != 0 && videoHeight != 0) {
            mPlayerEventListener?.onVideoSizeChanged(videoWidth, videoHeight)
        }
    }

    override fun onNativeInvoke(what: Int, args: Bundle?): Boolean {
        return true
    }
}
