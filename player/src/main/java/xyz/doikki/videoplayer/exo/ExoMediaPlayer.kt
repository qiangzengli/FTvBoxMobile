package xyz.doikki.videoplayer.exo

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.LoadControl
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.RenderersFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.DefaultAnalyticsCollector
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.MappingTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.util.Clock
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.video.VideoSize
import xyz.doikki.videoplayer.player.AbstractPlayer
import xyz.doikki.videoplayer.player.VideoViewManager
import xyz.doikki.videoplayer.util.PlayerUtils
import java.util.HashMap

open class ExoMediaPlayer(context: Context) : AbstractPlayer(), Player.Listener {

    @JvmField
    protected var mAppContext: Context = context.applicationContext

    @JvmField
    protected var mInternalPlayer: SimpleExoPlayer? = null

    @JvmField
    protected var mMediaSource: MediaSource? = null

    @JvmField
    protected var mMediaSourceHelper: ExoMediaSourceHelper = ExoMediaSourceHelper.getInstance(context)

    private var mSpeedPlaybackParameters: PlaybackParameters? = null
    private var mIsPreparing = false
    private var mLoadControl: LoadControl? = null
    private var mRenderersFactory: RenderersFactory? = null

    @JvmField
    protected var mTrackSelector: TrackSelector? = null

    @JvmField
    var trackSelector: DefaultTrackSelector? = null

    @JvmField
    protected var currentPlayPath: String? = null

    @JvmField
    protected var currentHeaders: Map<String, String>? = null

    private var mRetriedAsHls = false

    override fun initPlayer() {
        if (mLoadControl == null) {
            mLoadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 30_000, 1_000, 3_000)
                .setTargetBufferBytes(32 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .setBackBuffer(0, false)
                .build()
        }
        val renderersFactory = mRenderersFactory ?: DefaultRenderersFactory(mAppContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .also { mRenderersFactory = it }

        val activeTrackSelector = mTrackSelector ?: DefaultTrackSelector(mAppContext).also { mTrackSelector = it }

        mInternalPlayer = SimpleExoPlayer.Builder(
            mAppContext,
            renderersFactory,
            activeTrackSelector,
            DefaultMediaSourceFactory(mAppContext),
            mLoadControl!!,
            DefaultBandwidthMeter.getSingletonInstance(mAppContext),
            DefaultAnalyticsCollector(Clock.DEFAULT)
        ).build()

        setOptions()

        if (VideoViewManager.getConfig().mIsEnableLog && mTrackSelector is MappingTrackSelector) {
            mInternalPlayer?.addAnalyticsListener(EventLogger(mTrackSelector as MappingTrackSelector, "ExoPlayer"))
        }
        if (trackSelector == null && mTrackSelector is DefaultTrackSelector) {
            trackSelector = mTrackSelector as DefaultTrackSelector
        }
        mInternalPlayer?.addListener(this)
    }

    fun setTrackSelector(trackSelector: TrackSelector) {
        mTrackSelector = trackSelector
    }

    fun setRenderersFactory(renderersFactory: RenderersFactory) {
        mRenderersFactory = renderersFactory
    }

    fun setLoadControl(loadControl: LoadControl) {
        mLoadControl = loadControl
    }

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        Log.i("Tvbox-runtime", "echo-setDataSource:$path")
        currentPlayPath = path
        currentHeaders = copyHeaders(headers)
        mRetriedAsHls = false
        mMediaSource = mMediaSourceHelper.getMediaSource(path, copyHeaders(currentHeaders))
    }

    override fun setDataSource(fd: AssetFileDescriptor) {
        // 不支持AssetFileDescriptor方式
    }

    override fun start() {
        mInternalPlayer?.playWhenReady = true
    }

    override fun pause() {
        mInternalPlayer?.playWhenReady = false
    }

    override fun stop() {
        mInternalPlayer?.stop()
    }

    override fun prepareAsync() {
        val player = mInternalPlayer ?: return
        val mediaSource = mMediaSource ?: return
        if (mSpeedPlaybackParameters != null) {
            player.playbackParameters = mSpeedPlaybackParameters!!
        }
        mIsPreparing = true
        player.setMediaSource(mediaSource, getStartPosition())
        markStartPositionApplied()
        player.prepare()
    }

    override fun reset() {
        mInternalPlayer?.let {
            it.stop()
            it.clearMediaItems()
            mIsPreparing = false
            mRetriedAsHls = false
        }
    }

    override fun keepRenderViewOnReset(): Boolean = true

    override fun isPlaying(): Boolean {
        val player = mInternalPlayer ?: return false
        return when (player.playbackState) {
            Player.STATE_BUFFERING, Player.STATE_READY -> player.playWhenReady
            Player.STATE_IDLE, Player.STATE_ENDED -> false
            else -> false
        }
    }

    override fun seekTo(time: Long) {
        mInternalPlayer?.seekTo(time)
    }

    override fun release() {
        mInternalPlayer?.let {
            it.removeListener(this)
            it.release()
            mInternalPlayer = null
        }
        mIsPreparing = false
        mSpeedPlaybackParameters = null
    }

    override fun getCurrentPosition(): Long = mInternalPlayer?.currentPosition ?: 0L

    override fun getDuration(): Long = mInternalPlayer?.duration ?: 0L

    override fun getBufferedPercentage(): Int = mInternalPlayer?.bufferedPercentage ?: 0

    override fun setSurface(surface: Surface?) {
        mInternalPlayer?.setVideoSurface(surface)
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        if (holder == null) {
            setSurface(null)
        } else {
            setSurface(holder.surface)
        }
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        mInternalPlayer?.volume = (leftVolume + rightVolume) / 2
    }

    override fun setLooping(isLooping: Boolean) {
        mInternalPlayer?.repeatMode = if (isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    override fun setOptions() {
        mInternalPlayer?.playWhenReady = true
    }

    override fun setSpeed(speed: Float) {
        val playbackParameters = PlaybackParameters(speed)
        mSpeedPlaybackParameters = playbackParameters
        mInternalPlayer?.playbackParameters = playbackParameters
    }

    override fun getSpeed(): Float {
        return mSpeedPlaybackParameters?.speed ?: 1f
    }

    override fun getTcpSpeed(): Long {
        return PlayerUtils.getNetSpeed(mAppContext)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val listener = mPlayerEventListener ?: return
        if (mIsPreparing) {
            if (playbackState == Player.STATE_READY) {
                listener.onPrepared()
                listener.onInfo(AbstractPlayer.MEDIA_INFO_RENDERING_START, 0)
                mIsPreparing = false
            }
            return
        }
        when (playbackState) {
            Player.STATE_BUFFERING -> listener.onInfo(AbstractPlayer.MEDIA_INFO_BUFFERING_START, getBufferedPercentage())
            Player.STATE_READY -> listener.onInfo(AbstractPlayer.MEDIA_INFO_BUFFERING_END, getBufferedPercentage())
            Player.STATE_ENDED -> listener.onCompletion()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e("Tvbox-runtime", "echo-Exo player error: $currentPlayPath", error)
        if (retryAsHls(error)) {
            return
        }
        mPlayerEventListener?.onError()
    }

    private fun retryAsHls(error: PlaybackException): Boolean {
        if (mRetriedAsHls || mInternalPlayer == null || currentPlayPath == null) {
            return false
        }
        if (!isParsingError(error)) {
            return false
        }
        mRetriedAsHls = true
        Log.i("Tvbox-runtime", "echo-Exo retry as HLS: $currentPlayPath")
        mMediaSource = mMediaSourceHelper.getHlsMediaSource(currentPlayPath!!, copyHeaders(currentHeaders))
        mIsPreparing = true
        mInternalPlayer?.setMediaSource(mMediaSource!!, getStartPosition())
        markStartPositionApplied()
        mInternalPlayer?.prepare()
        mInternalPlayer?.playWhenReady = true
        return true
    }

    private fun isParsingError(error: PlaybackException): Boolean {
        val errorCode = error.errorCode
        return errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                || errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
                || errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
    }

    private fun copyHeaders(headers: Map<String, String>?): Map<String, String>? {
        return if (headers == null) null else HashMap(headers)
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        val listener = mPlayerEventListener ?: return
        listener.onVideoSizeChanged(videoSize.width, videoSize.height)
        if (videoSize.unappliedRotationDegrees > 0) {
            listener.onInfo(AbstractPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED, videoSize.unappliedRotationDegrees)
        }
    }
}
