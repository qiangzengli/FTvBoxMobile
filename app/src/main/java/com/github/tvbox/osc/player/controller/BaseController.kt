package com.github.tvbox.osc.player.controller

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import xyz.doikki.videoplayer.controller.BaseVideoController
import xyz.doikki.videoplayer.controller.IControlComponent
import xyz.doikki.videoplayer.controller.IGestureComponent
import xyz.doikki.videoplayer.player.VideoView
import xyz.doikki.videoplayer.util.PlayerUtils

abstract class BaseController @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseVideoController(context, attrs, defStyleAttr),
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener,
    View.OnTouchListener {

    private var mGestureDetector: GestureDetector? = null
    private var mAudioManager: AudioManager? = null
    private var mIsGestureEnabled = true
    private var mStreamVolume = 0
    private var mBrightness = 0f
    private var mSeekPosition = -1
    private var mFirstTouch = false
    private var mChangePosition = false
    private var mChangeBrightness = false
    private var mChangeVolume = false
    private var mCanChangePosition = true
    private var mEnableInNormal = false
    private var mCanSlide = false
    private var mCurPlayState = 0

    @JvmField
    protected var mHandler: Handler

    @JvmField
    protected var mHandlerCallback: HandlerCallback? = null

    fun interface HandlerCallback {
        fun callback(msg: Message)
    }

    private var mIsDoubleTapTogglePlayEnabled = true

    init {
        mHandler = Handler(Looper.getMainLooper()) { msg ->
            when (msg.what) {
                100 -> { // 亮度+音量调整
                    mSlideInfo?.visibility = VISIBLE
                    mSlideInfo?.text = msg.obj?.toString() ?: ""
                }

                101 -> { // 亮度+音量调整 关闭
                    mSlideInfo?.visibility = GONE
                }

                else -> {
                    mHandlerCallback?.callback(msg)
                }
            }
            false
        }
    }

    private var mSlideInfo: TextView? = null
    private var mLoading: ProgressBar? = null
    private var mPauseRoot: ViewGroup? = null
    private var mPauseTime: TextView? = null

    override fun initView() {
        super.initView()
        mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        mGestureDetector = GestureDetector(context, this)
        setOnTouchListener(this)
        mSlideInfo = findViewWithTag("vod_control_slide_info")
        mLoading = findViewWithTag("vod_control_loading")
        mPauseRoot = findViewWithTag("vod_control_pause")
        mPauseTime = findViewWithTag("vod_control_pause_t")
    }

    override fun setProgress(duration: Int, position: Int) {
        super.setProgress(duration, position)
        mPauseTime?.text = PlayerUtils.stringForTime(position) + " / " + PlayerUtils.stringForTime(duration)
    }

    override fun onPlayStateChanged(playState: Int) {
        super.onPlayStateChanged(playState)
        when (playState) {
            VideoView.STATE_IDLE -> {
                mPauseRoot?.visibility = GONE
                mLoading?.visibility = GONE
            }

            VideoView.STATE_PLAYING -> {
                mPauseRoot?.visibility = GONE
                mLoading?.visibility = GONE
            }

            VideoView.STATE_PAUSED -> {
                mPauseRoot?.visibility = VISIBLE
                mLoading?.visibility = GONE
            }

            VideoView.STATE_PREPARED,
            VideoView.STATE_ERROR,
            VideoView.STATE_BUFFERED -> {
                mPauseRoot?.visibility = GONE
                mLoading?.visibility = GONE
            }

            VideoView.STATE_PREPARING,
            VideoView.STATE_BUFFERING -> {
                mPauseRoot?.visibility = GONE
                mLoading?.visibility = VISIBLE
            }

            VideoView.STATE_PLAYBACK_COMPLETED -> {
                mLoading?.visibility = GONE
                mPauseRoot?.visibility = GONE
            }
        }
    }

    /**
     * 设置是否可以滑动调节进度，默认可以
     */
    open fun setCanChangePosition(canChangePosition: Boolean) {
        mCanChangePosition = canChangePosition
    }

    /**
     * 是否在竖屏模式下开始手势控制，默认关闭
     */
    open fun setEnableInNormal(enableInNormal: Boolean) {
        mEnableInNormal = enableInNormal
    }

    /**
     * 是否开启手势控制，默认开启，关闭之后，手势调节进度，音量，亮度功能将关闭
     */
    open fun setGestureEnabled(gestureEnabled: Boolean) {
        mIsGestureEnabled = gestureEnabled
    }

    /**
     * 是否开启双击播放/暂停，默认开启
     */
    open fun setDoubleTapTogglePlayEnabled(enabled: Boolean) {
        mIsDoubleTapTogglePlayEnabled = enabled
    }

    open fun hidePauseRoot() {
        mPauseRoot?.visibility = GONE
    }

    open fun showPauseRoot() {
        mPauseRoot?.visibility = VISIBLE
    }

    override fun setPlayerState(playerState: Int) {
        super.setPlayerState(playerState)
        if (playerState == VideoView.PLAYER_NORMAL) {
            mCanSlide = mEnableInNormal
        } else if (playerState == VideoView.PLAYER_FULL_SCREEN) {
            mCanSlide = true
        }
    }

    override fun setPlayState(playState: Int) {
        super.setPlayState(playState)
        mCurPlayState = playState
    }

    protected open fun isInPlaybackState(): Boolean {
        return mControlWrapper != null
                && mCurPlayState != VideoView.STATE_ERROR
                && mCurPlayState != VideoView.STATE_IDLE
                && mCurPlayState != VideoView.STATE_PREPARING
                && mCurPlayState != VideoView.STATE_PREPARED
                && mCurPlayState != VideoView.STATE_START_ABORT
                && mCurPlayState != VideoView.STATE_PLAYBACK_COMPLETED
    }

    protected open fun canHandleGesture(event: MotionEvent?): Boolean {
        return isInPlaybackState()
                && mIsGestureEnabled
                && mCanSlide
                && !isLocked()
                && (event != null && !PlayerUtils.isEdge(context, event))
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        return mGestureDetector?.onTouchEvent(event!!) ?: false
    }

    /**
     * 手指按下的瞬间
     */
    override fun onDown(e: MotionEvent): Boolean {
        if (!isInPlaybackState() // 不处于播放状态
            || !mIsGestureEnabled // 关闭了手势
            || PlayerUtils.isEdge(context, e) // 处于屏幕边沿
        ) return true
        mStreamVolume = mAudioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val activity = PlayerUtils.scanForActivity(context)
        mBrightness = if (activity == null) {
            0f
        } else {
            activity.window.attributes.screenBrightness
        }
        mFirstTouch = true
        mChangePosition = false
        mChangeBrightness = false
        mChangeVolume = false
        return true
    }

    /**
     * 单击
     */
    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (isInPlaybackState()) {
            mControlWrapper.toggleShowState()
        }
        return true
    }

    /**
     * 双击
     */
    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (mIsDoubleTapTogglePlayEnabled && !isLocked() && isInPlaybackState()) togglePlay()
        return true
    }

    /**
     * 在屏幕上滑动
     */
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        if (!canHandleGesture(e1)) return true
        val deltaX = (e1?.x ?: 0f) - e2.x
        val deltaY = (e1?.y ?: 0f) - e2.y
        if (mFirstTouch) {
            mChangePosition = Math.abs(distanceX) >= Math.abs(distanceY)
            if (!mChangePosition) {
                // 半屏宽度
                val halfScreen = PlayerUtils.getScreenWidth(context, true) / 2
                if (e2.x > halfScreen) {
                    mChangeVolume = true
                } else {
                    mChangeBrightness = true
                }
            }

            if (mChangePosition) {
                // 根据用户设置是否可以滑动调节进度来决定最终是否可以滑动调节进度
                mChangePosition = mCanChangePosition
            }

            if (mChangePosition || mChangeBrightness || mChangeVolume) {
                for (next in mControlComponents.entries) {
                    val component = next.key
                    if (component is IGestureComponent) {
                        component.onStartSlide()
                    }
                }
            }
            mFirstTouch = false
        }
        if (mChangePosition) {
            slideToChangePosition(deltaX)
        } else if (mChangeBrightness) {
            slideToChangeBrightness(deltaY)
        } else if (mChangeVolume) {
            slideToChangeVolume(deltaY)
        }
        return true
    }

    protected open fun slideToChangePosition(deltaX: Float) {
        var dx = -deltaX
        val width = measuredWidth
        val duration = PlayerUtils.safeTimeMs(mControlWrapper.getDuration())
        val currentPosition = PlayerUtils.safeTimeMs(mControlWrapper.getCurrentPosition())
        var position = (dx / width * 120000 + currentPosition).toInt()
        if (position > duration) position = duration
        if (position < 0) position = 0
        for (next in mControlComponents.entries) {
            val component = next.key
            if (component is IGestureComponent) {
                component.onPositionChange(position, currentPosition, duration)
            }
        }
        updateSeekUI(currentPosition, position, duration)
        mSeekPosition = position
    }

    protected open fun updateSeekUI(curr: Int, seekTo: Int, duration: Int) {
    }

    protected open fun slideToChangeBrightness(deltaY: Float) {
        val activity = PlayerUtils.scanForActivity(context) ?: return
        val window = activity.window
        val attributes = window.attributes
        val height = measuredHeight
        if (mBrightness == -1.0f) mBrightness = 0.5f
        var brightness = deltaY * 2 / height * 1.0f + mBrightness
        if (brightness < 0) {
            brightness = 0f
        }
        if (brightness > 1.0f) brightness = 1.0f
        val percent = (brightness * 100).toInt()
        attributes.screenBrightness = brightness
        window.attributes = attributes
        for (next in mControlComponents.entries) {
            val component = next.key
            if (component is IGestureComponent) {
                component.onBrightnessChange(percent)
            }
        }
        val msg = Message.obtain()
        msg.what = 100
        msg.obj = "亮度$percent%"
        mHandler.sendMessage(msg)
        mHandler.removeMessages(101)
        mHandler.sendEmptyMessageDelayed(101, 1000)
    }

    protected open fun slideToChangeVolume(deltaY: Float) {
        val audioManager = mAudioManager ?: return
        val streamMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val height = measuredHeight
        val deltaV = deltaY * 2 / height * streamMaxVolume
        var index = mStreamVolume + deltaV
        if (index > streamMaxVolume) index = streamMaxVolume.toFloat()
        if (index < 0) index = 0f
        val percent = (index / streamMaxVolume * 100).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index.toInt(), 0)
        for (next in mControlComponents.entries) {
            val component = next.key
            if (component is IGestureComponent) {
                component.onVolumeChange(percent)
            }
        }
        val msg = Message.obtain()
        msg.what = 100
        msg.obj = "音量$percent%"
        mHandler.sendMessage(msg)
        mHandler.removeMessages(101)
        mHandler.sendEmptyMessageDelayed(101, 1000)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 滑动结束时事件处理
        val detector = mGestureDetector
        if (detector == null || !detector.onTouchEvent(event)) {
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    stopSlide()
                    if (mSeekPosition >= 0) {
                        mControlWrapper.seekTo(mSeekPosition.toLong())
                        mSeekPosition = -1
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    stopSlide()
                    mSeekPosition = -1
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun stopSlide() {
        for (next in mControlComponents.entries) {
            val component = next.key
            if (component is IGestureComponent) {
                component.onStopSlide()
            }
        }
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        return false
    }

    override fun onLongPress(e: MotionEvent) {
    }

    override fun onShowPress(e: MotionEvent) {
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        return false
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false
    }

    open fun onKeyEvent(event: KeyEvent): Boolean {
        return false
    }
}
