package xyz.doikki.videoplayer.controller

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import xyz.doikki.videoplayer.player.VideoView
import xyz.doikki.videoplayer.util.PlayerUtils
import kotlin.math.abs

/**
 * 包含手势操作的VideoController
 * Created by Doikki on 2018/1/6.
 */
abstract class GestureVideoController @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseVideoController(context, attrs, defStyleAttr),
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener,
    View.OnTouchListener {

    private lateinit var mGestureDetector: GestureDetector
    private lateinit var mAudioManager: AudioManager
    private var mIsGestureEnabled = true
    private var mStreamVolume = 0
    private var mBrightness = 0f
    private var mSeekPosition = 0
    private var mFirstTouch = false
    private var mChangePosition = false
    private var mChangeBrightness = false
    private var mChangeVolume = false

    private var mCanChangePosition = true
    private var mEnableInNormal = false
    private var mCanSlide = false
    private var mCurPlayState = 0
    private var mIsDoubleTapTogglePlayEnabled = true

    override fun initView() {
        super.initView()
        mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mGestureDetector = GestureDetector(context, this)
        setOnTouchListener(this)
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

    private fun isInPlaybackState(): Boolean {
        return hasControlWrapper() &&
                mCurPlayState != VideoView.STATE_ERROR &&
                mCurPlayState != VideoView.STATE_IDLE &&
                mCurPlayState != VideoView.STATE_PREPARING &&
                mCurPlayState != VideoView.STATE_PREPARED &&
                mCurPlayState != VideoView.STATE_START_ABORT &&
                mCurPlayState != VideoView.STATE_PLAYBACK_COMPLETED
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return mGestureDetector.onTouchEvent(event)
    }

    /**
     * 手指按下的瞬间
     */
    override fun onDown(e: MotionEvent): Boolean {
        if (!isInPlaybackState() || !mIsGestureEnabled || PlayerUtils.isEdge(context, e)) {
            return true
        }
        mStreamVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val activity = PlayerUtils.scanForActivity(context)
        mBrightness = activity?.window?.attributes?.screenBrightness ?: 0f
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
        if (mIsDoubleTapTogglePlayEnabled && !isLocked() && isInPlaybackState()) {
            togglePlay()
        }
        return true
    }

    /**
     * 在屏幕上滑动
     */
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        if (e1 == null) return true
        if (!isInPlaybackState() || !mIsGestureEnabled || !mCanSlide || isLocked() || PlayerUtils.isEdge(context, e1)) {
            return true
        }
        val deltaX = e1.x - e2.x
        val deltaY = e1.y - e2.y
        if (mFirstTouch) {
            mChangePosition = abs(distanceX) >= abs(distanceY)
            if (!mChangePosition) {
                val halfScreen = PlayerUtils.getScreenWidth(context, true) / 2
                if (e2.x > halfScreen) {
                    mChangeVolume = true
                } else {
                    mChangeBrightness = true
                }
            }

            if (mChangePosition) {
                mChangePosition = mCanChangePosition
            }

            if (mChangePosition || mChangeBrightness || mChangeVolume) {
                for ((component) in mControlComponents) {
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
        val invDeltaX = -deltaX
        val width = measuredWidth
        val duration = PlayerUtils.safeTimeMs(mControlWrapper.getDuration())
        val currentPosition = PlayerUtils.safeTimeMs(mControlWrapper.getCurrentPosition())
        var position = (invDeltaX / width * 120000 + currentPosition).toInt()
        if (position > duration) position = duration
        if (position < 0) position = 0
        for ((component) in mControlComponents) {
            if (component is IGestureComponent) {
                component.onPositionChange(position, currentPosition, duration)
            }
        }
        mSeekPosition = position
    }

    protected open fun slideToChangeBrightness(deltaY: Float) {
        val activity = PlayerUtils.scanForActivity(context) ?: return
        val window = activity.window
        val attributes: WindowManager.LayoutParams = window.attributes
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
        for ((component) in mControlComponents) {
            if (component is IGestureComponent) {
                component.onBrightnessChange(percent)
            }
        }
    }

    protected open fun slideToChangeVolume(deltaY: Float) {
        val streamMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val height = measuredHeight
        val deltaV = deltaY * 2 / height * streamMaxVolume
        var index = mStreamVolume + deltaV
        if (index > streamMaxVolume) index = streamMaxVolume.toFloat()
        if (index < 0) index = 0f
        val percent = (index / streamMaxVolume * 100).toInt()
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index.toInt(), 0)
        for ((component) in mControlComponents) {
            if (component is IGestureComponent) {
                component.onVolumeChange(percent)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!mGestureDetector.onTouchEvent(event)) {
            val action = event.action
            when (action) {
                MotionEvent.ACTION_UP -> {
                    stopSlide()
                    if (mSeekPosition > 0) {
                        mControlWrapper.seekTo(mSeekPosition.toLong())
                        mSeekPosition = 0
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    stopSlide()
                    mSeekPosition = 0
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun stopSlide() {
        for ((component) in mControlComponents) {
            if (component is IGestureComponent) {
                component.onStopSlide()
            }
        }
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean = false

    override fun onLongPress(e: MotionEvent) {}

    override fun onShowPress(e: MotionEvent) {}

    override fun onDoubleTapEvent(e: MotionEvent): Boolean = false

    override fun onSingleTapUp(e: MotionEvent): Boolean = false
}
