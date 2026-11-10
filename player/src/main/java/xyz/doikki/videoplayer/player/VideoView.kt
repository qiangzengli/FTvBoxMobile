package xyz.doikki.videoplayer.player

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.github.tvbox.osc.player.R
import xyz.doikki.videoplayer.controller.BaseVideoController
import xyz.doikki.videoplayer.controller.MediaPlayerControl
import xyz.doikki.videoplayer.render.IRenderView
import xyz.doikki.videoplayer.render.RenderViewFactory
import xyz.doikki.videoplayer.util.L
import xyz.doikki.videoplayer.util.PlayerUtils
import java.io.IOException
import java.util.ArrayList

/**
 * 播放器
 * Created by Doikki on 2017/4/7.
 */
open class VideoView<P : AbstractPlayer> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
    MediaPlayerControl,
    AbstractPlayer.PlayerEventListener {

    companion object {
        const val SCREEN_SCALE_DEFAULT = 0
        const val SCREEN_SCALE_16_9 = 1
        const val SCREEN_SCALE_4_3 = 2
        const val SCREEN_SCALE_MATCH_PARENT = 3
        const val SCREEN_SCALE_ORIGINAL = 4
        const val SCREEN_SCALE_CENTER_CROP = 5

        // 播放器的各种状态
        const val STATE_ERROR = -1
        const val STATE_IDLE = 0
        const val STATE_PREPARING = 1
        const val STATE_PREPARED = 2
        const val STATE_PLAYING = 3
        const val STATE_PAUSED = 4
        const val STATE_PLAYBACK_COMPLETED = 5
        const val STATE_BUFFERING = 6
        const val STATE_BUFFERED = 7
        const val STATE_START_ABORT = 8 // 开始播放中止

        const val PLAYER_NORMAL = 10 // 普通播放器
        const val PLAYER_FULL_SCREEN = 11 // 全屏播放器
        const val PLAYER_TINY_SCREEN = 12 // 小屏播放器
    }

    @JvmField
    protected var mMediaPlayer: P? = null

    @JvmField
    @Suppress("UNCHECKED_CAST")
    protected var mPlayerFactory: PlayerFactory<P>? = null

    @JvmField
    protected var mVideoController: BaseVideoController? = null

    /**
     * 真正承载播放器视图的容器
     */
    protected lateinit var mPlayerContainer: FrameLayout

    @JvmField
    protected var mRenderView: IRenderView? = null

    @JvmField
    protected var mRenderViewFactory: RenderViewFactory? = null

    @JvmField
    protected var mCurrentScreenScaleType: Int = SCREEN_SCALE_DEFAULT

    @JvmField
    protected var mVideoSize: IntArray = intArrayOf(0, 0)

    @JvmField
    protected var mIsMute: Boolean = false

    // --------- data sources ---------//
    @JvmField
    protected var mUrl: String? = null

    @JvmField
    protected var mProgressKey: String? = null

    @JvmField
    protected var mHeaders: Map<String, String>? = null

    @JvmField
    protected var mAssetFileDescriptor: AssetFileDescriptor? = null

    @JvmField
    protected var mCurrentPosition: Long = 0

    @JvmField
    protected var mCurrentPlayState: Int = STATE_IDLE

    @JvmField
    protected var mCurrentPlayerState: Int = PLAYER_NORMAL

    @JvmField
    protected var mIsFullScreen: Boolean = false

    @JvmField
    protected var mIsTinyScreen: Boolean = false

    @JvmField
    protected var mTinyScreenSize: IntArray = intArrayOf(0, 0)

    @JvmField
    protected var mEnableAudioFocus: Boolean = true

    @JvmField
    protected var mAudioFocusHelper: AudioFocusHelper? = null

    @JvmField
    protected var mOnStateChangeListeners: MutableList<OnStateChangeListener>? = null

    @JvmField
    protected var mProgressManager: ProgressManager? = null

    @JvmField
    protected var mIsLooping: Boolean = false

    private var mPlayerBackgroundColor: Int = Color.BLACK

    init {
        val config = VideoViewManager.getConfig()
        mEnableAudioFocus = config.mEnableAudioFocus
        mProgressManager = config.mProgressManager
        @Suppress("UNCHECKED_CAST")
        mPlayerFactory = config.mPlayerFactory as? PlayerFactory<P>
        mCurrentScreenScaleType = config.mScreenScaleType
        mRenderViewFactory = config.mRenderViewFactory

        val a = context.obtainStyledAttributes(attrs, R.styleable.VideoView)
        mEnableAudioFocus = a.getBoolean(R.styleable.VideoView_enableAudioFocus, mEnableAudioFocus)
        mIsLooping = a.getBoolean(R.styleable.VideoView_looping, false)
        mCurrentScreenScaleType = a.getInt(R.styleable.VideoView_screenScaleType, mCurrentScreenScaleType)
        mPlayerBackgroundColor = a.getColor(R.styleable.VideoView_playerBackgroundColor, Color.BLACK)
        a.recycle()

        initView()
    }

    protected open fun initView() {
        mPlayerContainer = FrameLayout(context)
        mPlayerContainer.setBackgroundColor(mPlayerBackgroundColor)
        val params = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        this.addView(mPlayerContainer, params)
    }

    open fun setPlayerBackgroundColor(color: Int) {
        mPlayerBackgroundColor = color
        mPlayerContainer.setBackgroundColor(color)
    }

    override fun start() {
        if (isInIdleState || isInStartAbortState) {
            startPlay()
        } else if (isInPlaybackState) {
            startInPlaybackState()
        }
    }

    protected open fun startPlay(): Boolean {
        if (showNetWarning()) {
            setPlayState(STATE_START_ABORT)
            return false
        }
        if (mEnableAudioFocus) {
            mAudioFocusHelper = AudioFocusHelper(this)
        }
        val progressManager = mProgressManager
        if (progressManager != null) {
            val key = mProgressKey ?: (mUrl ?: "")
            mCurrentPosition = progressManager.getSavedProgress(key)
        }
        initPlayer()
        addDisplay()
        startPrepare(false)
        return true
    }

    protected open fun showNetWarning(): Boolean {
        if (isLocalDataSource) return false
        return mVideoController?.showNetWarning() ?: false
    }

    protected open val isLocalDataSource: Boolean
        get() {
            if (mAssetFileDescriptor != null) {
                return true
            } else if (!TextUtils.isEmpty(mUrl)) {
                val uri = Uri.parse(mUrl)
                return ContentResolver.SCHEME_ANDROID_RESOURCE == uri.scheme ||
                        ContentResolver.SCHEME_FILE == uri.scheme ||
                        "rawresource" == uri.scheme
            }
            return false
        }

    protected open fun initPlayer() {
        val factory = mPlayerFactory ?: throw IllegalStateException("PlayerFactory is null")
        val player = factory.createPlayer(context)
        mMediaPlayer = player
        player.setPlayerEventListener(this)
        setInitOptions()
        player.initPlayer()
        setOptions()
    }

    protected open fun setInitOptions() {}

    protected open fun setOptions() {
        mMediaPlayer?.let { player ->
            player.setLooping(mIsLooping)
            val volume = if (mIsMute) 0.0f else 1.0f
            player.setVolume(volume, volume)
        }
    }

    protected open fun addDisplay() {
        mRenderView?.let { renderView ->
            mPlayerContainer.removeView(renderView.getView())
            renderView.release()
        }
        val factory = mRenderViewFactory ?: return
        val player = mMediaPlayer ?: return
        val render = factory.createRenderView(context)
        mRenderView = render
        render.attachToPlayer(player)
        val params = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        )
        mPlayerContainer.addView(render.getView(), 0, params)
    }

    @JvmOverloads
    protected open fun startPrepare(reset: Boolean, rebindRenderView: Boolean = false) {
        val player = mMediaPlayer ?: return
        if (reset) {
            player.reset()
            setOptions()
            if (rebindRenderView) {
                mRenderView?.attachToPlayer(player)
            }
        }
        if (prepareDataSource()) {
            player.setStartPosition(mCurrentPosition)
            player.prepareAsync()
            setPlayState(STATE_PREPARING)
            setPlayerState(if (isFullScreen()) PLAYER_FULL_SCREEN else if (isTinyScreen()) PLAYER_TINY_SCREEN else PLAYER_NORMAL)
        }
    }

    protected open fun prepareDataSource(): Boolean {
        val player = mMediaPlayer ?: return false
        val fd = mAssetFileDescriptor
        if (fd != null) {
            player.setDataSource(fd)
            return true
        } else if (!TextUtils.isEmpty(mUrl)) {
            player.setDataSource(mUrl!!, mHeaders)
            return true
        }
        return false
    }

    protected open fun startInPlaybackState() {
        mMediaPlayer?.start()
        setPlayState(STATE_PLAYING)
        if (mAudioFocusHelper != null && !isMute()) {
            mAudioFocusHelper?.requestFocus()
        }
        mPlayerContainer.keepScreenOn = true
    }

    override fun pause() {
        val player = mMediaPlayer
        if (isInPlaybackState && player != null && player.isPlaying()) {
            player.pause()
            setPlayState(STATE_PAUSED)
            if (mAudioFocusHelper != null && !isMute()) {
                mAudioFocusHelper?.abandonFocus()
            }
            mPlayerContainer.keepScreenOn = false
        }
    }

    open fun resume() {
        val player = mMediaPlayer
        if (isInPlaybackState && player != null && !player.isPlaying()) {
            val renderView = mRenderView?.getView()
            if (renderView is SurfaceView) {
                val holder = renderView.holder
                val surface = holder.surface
                if (surface != null && surface.isValid) {
                    player.setDisplay(holder)
                    resumePlay()
                } else {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            mRenderView?.apply {
                                setScaleType(mCurrentScreenScaleType)
                                setVideoSize(mVideoSize[0], mVideoSize[1])
                            }
                            player.setDisplay(h)
                            resumePlay()
                            h.removeCallback(this)
                        }

                        override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {}
                        override fun surfaceDestroyed(h: SurfaceHolder) {}
                    })
                }
            } else {
                resumePlay()
            }
        }
    }

    private fun resumePlay() {
        mMediaPlayer?.start()
        setPlayState(STATE_PLAYING)
        if (mAudioFocusHelper != null && !isMute()) {
            mAudioFocusHelper?.requestFocus()
        }
        mPlayerContainer.keepScreenOn = true
    }

    open fun release() {
        if (!isInIdleState) {
            mMediaPlayer?.let {
                it.release()
                mMediaPlayer = null
            }
            mRenderView?.let {
                mPlayerContainer.removeView(it.getView())
                it.release()
                mRenderView = null
            }
            mAssetFileDescriptor?.let {
                try {
                    it.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            mAudioFocusHelper?.let {
                it.abandonFocus()
                mAudioFocusHelper = null
            }
            mPlayerContainer.keepScreenOn = false
            saveProgress()
            mCurrentPosition = 0
            setPlayState(STATE_IDLE)
        }
        mVideoSize[0] = 0
        mVideoSize[1] = 0
    }

    protected open fun saveProgress() {
        val progressManager = mProgressManager
        if (progressManager != null && mCurrentPosition > 0) {
            L.d("saveProgress: $mCurrentPosition")
            val key = mProgressKey ?: (mUrl ?: "")
            progressManager.saveProgress(key, mCurrentPosition)
        }
    }

    protected open val isInPlaybackState: Boolean
        get() = mMediaPlayer != null &&
                mCurrentPlayState != STATE_ERROR &&
                mCurrentPlayState != STATE_IDLE &&
                mCurrentPlayState != STATE_PREPARING &&
                mCurrentPlayState != STATE_START_ABORT &&
                mCurrentPlayState != STATE_PLAYBACK_COMPLETED

    protected open val isInIdleState: Boolean
        get() = mCurrentPlayState == STATE_IDLE

    private val isInStartAbortState: Boolean
        get() = mCurrentPlayState == STATE_START_ABORT

    override fun replay(resetPosition: Boolean) {
        if (resetPosition) {
            mCurrentPosition = 0
        }
        val player = mMediaPlayer
        if (player == null) {
            start()
            return
        }
        if (player.keepRenderViewOnReset()) {
            player.reset()
            setOptions()
            player.setOptions()
            startPrepare(false)
        } else {
            startPrepare(true, true)
        }
    }

    override fun getDuration(): Long {
        return if (isInPlaybackState) mMediaPlayer?.getDuration() ?: 0 else 0
    }

    override fun getCurrentPosition(): Long {
        if (isInPlaybackState) {
            mCurrentPosition = mMediaPlayer?.getCurrentPosition() ?: 0
            return mCurrentPosition
        }
        return 0
    }

    override fun seekTo(pos: Long) {
        if (isInPlaybackState) {
            mMediaPlayer?.seekTo(pos)
        }
    }

    override fun isPlaying(): Boolean {
        return isInPlaybackState && (mMediaPlayer?.isPlaying() ?: false)
    }

    override fun getBufferedPercentage(): Int {
        return mMediaPlayer?.getBufferedPercentage() ?: 0
    }

    override fun setMute(isMute: Boolean) {
        mIsMute = isMute
        mMediaPlayer?.let {
            val volume = if (isMute) 0.0f else 1.0f
            it.setVolume(volume, volume)
        }
    }

    override fun isMute(): Boolean = mIsMute

    override fun onPrepared() {
        val player = mMediaPlayer
        if (mCurrentPosition > 0 && player != null && !player.isStartPositionApplied()) {
            player.seekTo(mCurrentPosition)
        }
        setPlayState(STATE_PREPARED)
        if (!isMute() && mAudioFocusHelper != null) {
            mAudioFocusHelper?.requestFocus()
        }
    }

    override fun onInfo(what: Int, extra: Int) {
        when (what) {
            AbstractPlayer.MEDIA_INFO_BUFFERING_START -> setPlayState(STATE_BUFFERING)
            AbstractPlayer.MEDIA_INFO_BUFFERING_END -> setPlayState(STATE_BUFFERED)
            AbstractPlayer.MEDIA_INFO_RENDERING_START -> {
                setPlayState(STATE_PLAYING)
                mPlayerContainer.keepScreenOn = true
            }
            AbstractPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED -> mRenderView?.setVideoRotation(extra)
        }
    }

    override fun onError() {
        mPlayerContainer.keepScreenOn = false
        setPlayState(STATE_ERROR)
    }

    override fun onCompletion() {
        mPlayerContainer.keepScreenOn = false
        mCurrentPosition = 0
        mProgressManager?.let {
            val key = mProgressKey ?: (mUrl ?: "")
            it.saveProgress(key, 0)
        }
        setPlayState(STATE_PLAYBACK_COMPLETED)
    }

    open fun getCurrentPlayerState(): Int = mCurrentPlayerState

    open fun getCurrentPlayState(): Int = mCurrentPlayState

    override fun getTcpSpeed(): Long {
        return mMediaPlayer?.getTcpSpeed() ?: 0
    }

    override fun setSpeed(speed: Float) {
        if (isInPlaybackState) {
            mMediaPlayer?.setSpeed(speed)
        }
    }

    override fun getSpeed(): Float {
        return if (isInPlaybackState) mMediaPlayer?.getSpeed() ?: 1f else 1f
    }

    open fun setUrl(url: String?) {
        setUrl(url, null)
    }

    open fun setUrl(url: String?, headers: Map<String, String>?) {
        mAssetFileDescriptor = null
        mUrl = url
        mHeaders = headers
        mVideoSize[0] = 0
        mVideoSize[1] = 0
    }

    open fun setAssetFileDescriptor(fd: AssetFileDescriptor?) {
        mUrl = null
        mAssetFileDescriptor = fd
    }

    open fun setProgressKey(key: String?) {
        mProgressKey = key
    }

    open fun skipPositionWhenPlay(position: Int) {
        mCurrentPosition = position.toLong()
    }

    open fun setVolume(v1: Float, v2: Float) {
        mMediaPlayer?.setVolume(v1, v2)
    }

    open fun setProgressManager(progressManager: ProgressManager?) {
        mProgressManager = progressManager
    }

    open fun setLooping(looping: Boolean) {
        mIsLooping = looping
        mMediaPlayer?.setLooping(looping)
    }

    open fun setEnableAudioFocus(enableAudioFocus: Boolean) {
        mEnableAudioFocus = enableAudioFocus
    }

    open fun setPlayerFactory(playerFactory: PlayerFactory<P>?) {
        mPlayerFactory = playerFactory ?: throw IllegalArgumentException("PlayerFactory can not be null!")
    }

    open fun setRenderViewFactory(renderViewFactory: RenderViewFactory?) {
        mRenderViewFactory = renderViewFactory ?: throw IllegalArgumentException("RenderViewFactory can not be null!")
    }

    override fun startFullScreen() {
        if (mIsFullScreen) return
        val decorView = getDecorView() ?: return

        mIsFullScreen = true
        hideSysBar(decorView)

        this.removeView(mPlayerContainer)
        decorView.addView(mPlayerContainer)
        setPlayerState(PLAYER_FULL_SCREEN)
    }

    @Suppress("DEPRECATION")
    private fun hideSysBar(decorView: ViewGroup) {
        var uiOptions = decorView.systemUiVisibility
        uiOptions = uiOptions or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        decorView.systemUiVisibility = uiOptions
        getActivity()?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && mIsFullScreen) {
            getDecorView()?.let { hideSysBar(it) }
        }
    }

    override fun stopFullScreen() {
        if (!mIsFullScreen) return
        val decorView = getDecorView() ?: return

        mIsFullScreen = false
        showSysBar(decorView)

        decorView.removeView(mPlayerContainer)
        this.addView(mPlayerContainer)
        setPlayerState(PLAYER_NORMAL)
    }

    @Suppress("DEPRECATION")
    private fun showSysBar(decorView: ViewGroup) {
        var uiOptions = decorView.systemUiVisibility
        uiOptions = uiOptions and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv()
        decorView.systemUiVisibility = uiOptions
        getActivity()?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    protected open fun getDecorView(): ViewGroup? {
        val activity = getActivity() ?: return null
        return activity.window.decorView as? ViewGroup
    }

    protected open fun getContentView(): ViewGroup? {
        val activity = getActivity() ?: return null
        return activity.findViewById(android.R.id.content)
    }

    protected open fun getActivity(): Activity? {
        val controller = mVideoController
        return if (controller != null) {
            PlayerUtils.scanForActivity(controller.context) ?: PlayerUtils.scanForActivity(context)
        } else {
            PlayerUtils.scanForActivity(context)
        }
    }

    override fun isFullScreen(): Boolean = mIsFullScreen

    override fun startTinyScreen() {
        if (mIsTinyScreen) return
        val contentView = getContentView() ?: return

        this.removeView(mPlayerContainer)
        var width = mTinyScreenSize[0]
        if (width <= 0) {
            width = PlayerUtils.getScreenWidth(context, false) / 2
        }

        var height = mTinyScreenSize[1]
        if (height <= 0) {
            height = width * 9 / 16
        }

        val params = LayoutParams(width, height).apply {
            gravity = Gravity.BOTTOM or Gravity.END
        }
        contentView.addView(mPlayerContainer, params)
        mIsTinyScreen = true
        setPlayerState(PLAYER_TINY_SCREEN)
    }

    override fun stopTinyScreen() {
        if (!mIsTinyScreen) return
        val contentView = getContentView() ?: return
        contentView.removeView(mPlayerContainer)
        val params = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        this.addView(mPlayerContainer, params)
        mIsTinyScreen = false
        setPlayerState(PLAYER_NORMAL)
    }

    override fun isTinyScreen(): Boolean = mIsTinyScreen

    override fun onVideoSizeChanged(videoWidth: Int, videoHeight: Int) {
        mVideoSize[0] = videoWidth
        mVideoSize[1] = videoHeight

        mRenderView?.apply {
            setScaleType(mCurrentScreenScaleType)
            setVideoSize(videoWidth, videoHeight)
        }
    }

    open fun setVideoController(mediaController: BaseVideoController?) {
        mPlayerContainer.removeView(mVideoController)
        mVideoController = mediaController
        if (mediaController != null) {
            mediaController.setMediaPlayer(this)
            val params = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            mPlayerContainer.addView(mVideoController, params)
        }
    }

    override fun setScreenScaleType(screenScaleType: Int) {
        mCurrentScreenScaleType = screenScaleType
        mRenderView?.setScaleType(screenScaleType)
    }

    override fun setMirrorRotation(enable: Boolean) {
        mRenderView?.getView()?.scaleX = if (enable) -1f else 1f
    }

    override fun doScreenShot(): Bitmap? {
        return mRenderView?.doScreenShot()
    }

    override fun getVideoSize(): IntArray = mVideoSize

    override fun setRotation(rotation: Float) {
        mRenderView?.setVideoRotation(rotation.toInt())
    }

    open fun setTinyScreenSize(tinyScreenSize: IntArray) {
        mTinyScreenSize = tinyScreenSize
    }

    protected open fun setPlayState(playState: Int) {
        mCurrentPlayState = playState
        mVideoController?.setPlayState(playState)
        mOnStateChangeListeners?.let { listeners ->
            for (l in PlayerUtils.getSnapshot(listeners)) {
                l.onPlayStateChanged(playState)
            }
        }
    }

    protected open fun setPlayerState(playerState: Int) {
        mCurrentPlayerState = playerState
        mVideoController?.setPlayerState(playerState)
        mOnStateChangeListeners?.let { listeners ->
            for (l in PlayerUtils.getSnapshot(listeners)) {
                l.onPlayerStateChanged(playerState)
            }
        }
    }

    interface OnStateChangeListener {
        fun onPlayerStateChanged(playerState: Int)
        fun onPlayStateChanged(playState: Int)
    }

    open class SimpleOnStateChangeListener : OnStateChangeListener {
        override fun onPlayerStateChanged(playerState: Int) {}
        override fun onPlayStateChanged(playState: Int) {}
    }

    open fun addOnStateChangeListener(listener: OnStateChangeListener) {
        if (mOnStateChangeListeners == null) {
            mOnStateChangeListeners = ArrayList()
        }
        mOnStateChangeListeners?.add(listener)
    }

    open fun removeOnStateChangeListener(listener: OnStateChangeListener) {
        mOnStateChangeListeners?.remove(listener)
    }

    open fun setOnStateChangeListener(listener: OnStateChangeListener) {
        if (mOnStateChangeListeners == null) {
            mOnStateChangeListeners = ArrayList()
        } else {
            mOnStateChangeListeners?.clear()
        }
        mOnStateChangeListeners?.add(listener)
    }

    open fun clearOnStateChangeListeners() {
        mOnStateChangeListeners?.clear()
    }

    open fun onBackPressed(): Boolean {
        return mVideoController?.onBackPressed() ?: false
    }

    override fun onSaveInstanceState(): Parcelable? {
        L.d("onSaveInstanceState: $mCurrentPosition")
        saveProgress()
        return super.onSaveInstanceState()
    }
}
