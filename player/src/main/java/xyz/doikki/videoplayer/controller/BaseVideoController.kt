package xyz.doikki.videoplayer.controller

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import xyz.doikki.videoplayer.player.VideoView
import xyz.doikki.videoplayer.player.VideoViewManager
import xyz.doikki.videoplayer.util.CutoutUtil
import xyz.doikki.videoplayer.util.L
import xyz.doikki.videoplayer.util.PlayerUtils
import java.util.LinkedHashMap

/**
 * 控制器基类
 * Created by Doikki on 2017/4/12.
 */
abstract class BaseVideoController @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
    IVideoController,
    OrientationHelper.OnOrientationChangeListener {

    // 播放器包装类，集合了MediaPlayerControl的api和IVideoController的api
    protected lateinit var mControlWrapper: ControlWrapper

    protected fun hasControlWrapper(): Boolean = ::mControlWrapper.isInitialized

    @JvmField
    protected var mActivity: Activity? = null

    // 控制器是否处于显示状态
    @JvmField
    protected var mShowing = false

    // 是否处于锁定状态
    @JvmField
    protected var mIsLocked = false

    // 播放视图隐藏超时
    @JvmField
    protected var mDefaultTimeout = 4000

    // 是否开启根据屏幕方向进入/退出全屏
    private var mEnableOrientation = false

    // 屏幕方向监听辅助类
    @JvmField
    protected var mOrientationHelper: OrientationHelper? = null

    // 用户设置是否适配刘海屏
    private var mAdaptCutout = false

    // 是否有刘海
    private var mHasCutout: Boolean? = null

    // 刘海的高度
    private var mCutoutHeight = 0

    // 是否开始刷新进度
    private var mIsStartProgress = false

    // 保存了所有的控制组件
    @JvmField
    protected var mControlComponents = LinkedHashMap<IControlComponent, Boolean>()

    private var mShowAnim: Animation? = null
    private var mHideAnim: Animation? = null
    private var mOrientation = 0

    init {
        initView()
    }

    protected open fun initView() {
        if (getLayoutId() != 0) {
            LayoutInflater.from(context).inflate(getLayoutId(), this, true)
        }
        mOrientationHelper = OrientationHelper(context.applicationContext)
        mEnableOrientation = VideoViewManager.getConfig().mEnableOrientation
        mAdaptCutout = VideoViewManager.getConfig().mAdaptCutout

        mShowAnim = AlphaAnimation(0f, 1f).apply { duration = 300 }
        mHideAnim = AlphaAnimation(1f, 0f).apply { duration = 300 }

        mActivity = PlayerUtils.scanForActivity(context)
    }

    /**
     * 设置控制器布局文件，子类必须实现
     */
    protected abstract fun getLayoutId(): Int

    /**
     * 重要：此方法用于将VideoView 和控制器绑定
     */
    @CallSuper
    open fun setMediaPlayer(mediaPlayer: MediaPlayerControl) {
        mControlWrapper = ControlWrapper(mediaPlayer, this)
        // 绑定ControlComponent和Controller
        for ((component) in mControlComponents) {
            component.attach(mControlWrapper)
        }
        // 开始监听设备方向
        mOrientationHelper?.setOnOrientationChangeListener(this)
    }

    /**
     * 添加控制组件，最后面添加的在最下面，合理组织添加顺序，可让ControlComponent位于不同的层级
     */
    open fun addControlComponent(vararg component: IControlComponent) {
        for (item in component) {
            addControlComponent(item, false)
        }
    }

    /**
     * 添加控制组件，最后面添加的在最下面，合理组织添加顺序，可让ControlComponent位于不同的层级
     */
    open fun addControlComponent(component: IControlComponent, isDissociate: Boolean) {
        mControlComponents[component] = isDissociate
        if (::mControlWrapper.isInitialized) {
            component.attach(mControlWrapper)
        }
        val view = component.getView()
        if (view != null && !isDissociate) {
            addView(view, 0)
        }
    }

    /**
     * 移除某个控制组件
     */
    open fun removeControlComponent(component: IControlComponent) {
        removeView(component.getView())
        mControlComponents.remove(component)
    }

    /**
     * 移除所有控制组件
     */
    open fun removeAllControlComponent() {
        for ((component) in mControlComponents) {
            removeView(component.getView())
        }
        mControlComponents.clear()
    }

    /**
     * 移除所有的游离控制组件
     */
    open fun removeAllDissociateComponents() {
        val it = mControlComponents.entries.iterator()
        while (it.hasNext()) {
            val next = it.next()
            if (next.value) {
                it.remove()
            }
        }
    }

    /**
     * VideoView调用此方法向控制器设置播放状态
     */
    @CallSuper
    open fun setPlayState(playState: Int) {
        handlePlayStateChanged(playState)
    }

    /**
     * VideoView调用此方法向控制器设置播放器状态
     */
    @CallSuper
    open fun setPlayerState(playerState: Int) {
        handlePlayerStateChanged(playerState)
    }

    /**
     * 设置播放视图自动隐藏超时
     */
    open fun setDismissTimeout(timeout: Int) {
        if (timeout > 0) {
            mDefaultTimeout = timeout
        }
    }

    override fun hide() {
        if (mShowing) {
            stopFadeOut()
            handleVisibilityChanged(false, mHideAnim)
            mShowing = false
        }
    }

    override fun show() {
        if (!mShowing) {
            handleVisibilityChanged(true, mShowAnim)
            startFadeOut()
            mShowing = true
        }
    }

    override fun isShowing(): Boolean = mShowing

    override fun startFadeOut() {
        stopFadeOut()
        postDelayed(mFadeOut, mDefaultTimeout.toLong())
    }

    override fun stopFadeOut() {
        removeCallbacks(mFadeOut)
    }

    protected val mFadeOut = Runnable { hide() }

    override fun setLocked(locked: Boolean) {
        mIsLocked = locked
        handleLockStateChanged(locked)
    }

    override fun isLocked(): Boolean = mIsLocked

    override fun startProgress() {
        if (mIsStartProgress) return
        post(mShowProgress)
        mIsStartProgress = true
    }

    override fun stopProgress() {
        if (!mIsStartProgress) return
        removeCallbacks(mShowProgress)
        mIsStartProgress = false
    }

    protected var mShowProgress: Runnable = object : Runnable {
        override fun run() {
            val pos = setProgress()
            if (::mControlWrapper.isInitialized && mControlWrapper.isPlaying()) {
                val delay = ((1000 - pos % 1000) / mControlWrapper.getSpeed()).toLong()
                postDelayed(this, delay)
            } else {
                mIsStartProgress = false
            }
        }
    }

    private fun setProgress(): Int {
        if (!::mControlWrapper.isInitialized) return 0
        val position = PlayerUtils.safeTimeMs(mControlWrapper.getCurrentPosition())
        val duration = PlayerUtils.safeTimeMs(mControlWrapper.getDuration())
        handleSetProgress(duration, position)
        return position
    }

    open fun setAdaptCutout(adaptCutout: Boolean) {
        mAdaptCutout = adaptCutout
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        checkCutout()
    }

    private fun checkCutout() {
        if (!mAdaptCutout) return
        val activity = mActivity
        if (activity != null && mHasCutout == null) {
            mHasCutout = CutoutUtil.allowDisplayToCutout(activity)
            if (mHasCutout == true) {
                mCutoutHeight = PlayerUtils.getStatusBarHeightPortrait(activity).toInt()
            }
        }
        L.d("hasCutout: $mHasCutout cutout height: $mCutoutHeight")
    }

    override fun hasCutout(): Boolean = mHasCutout == true

    override fun getCutoutHeight(): Int = mCutoutHeight

    open fun showNetWarning(): Boolean {
        return PlayerUtils.getNetworkType(context) == PlayerUtils.NETWORK_MOBILE &&
                !VideoViewManager.instance().playOnMobileNetwork()
    }

    protected open fun togglePlay() {
        if (::mControlWrapper.isInitialized) {
            mControlWrapper.togglePlay()
        }
    }

    protected open fun toggleFullScreen() {
        if (::mControlWrapper.isInitialized) {
            mControlWrapper.toggleFullScreen(mActivity)
        }
    }

    protected open fun startFullScreen(): Boolean {
        val activity = mActivity
        if (activity == null || activity.isFinishing) return false
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (::mControlWrapper.isInitialized) {
            mControlWrapper.startFullScreen()
        }
        return true
    }

    protected open fun stopFullScreen(): Boolean {
        val activity = mActivity
        if (activity == null || activity.isFinishing) return false
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (::mControlWrapper.isInitialized) {
            mControlWrapper.stopFullScreen()
        }
        return true
    }

    open fun onBackPressed(): Boolean = false

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (::mControlWrapper.isInitialized &&
            mControlWrapper.isPlaying() &&
            (mEnableOrientation || mControlWrapper.isFullScreen())
        ) {
            if (hasWindowFocus) {
                postDelayed({ mOrientationHelper?.enable() }, 800)
            } else {
                mOrientationHelper?.disable()
            }
        }
    }

    open fun setEnableOrientation(enableOrientation: Boolean) {
        mEnableOrientation = enableOrientation
    }

    @CallSuper
    override fun onOrientationChanged(orientation: Int) {
        val activity = mActivity
        if (activity == null || activity.isFinishing) return

        val lastOrientation = mOrientation

        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            mOrientation = -1
            return
        }

        if (orientation > 350 || orientation < 10) {
            val o = activity.requestedOrientation
            if (o == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE && lastOrientation == 0) return
            if (mOrientation == 0) return
            mOrientation = 0
            onOrientationPortrait(activity)
        } else if (orientation in 81..99) {
            val o = activity.requestedOrientation
            if (o == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT && lastOrientation == 90) return
            if (mOrientation == 90) return
            mOrientation = 90
            onOrientationReverseLandscape(activity)
        } else if (orientation in 261..279) {
            val o = activity.requestedOrientation
            if (o == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT && lastOrientation == 270) return
            if (mOrientation == 270) return
            mOrientation = 270
            onOrientationLandscape(activity)
        }
    }

    protected open fun onOrientationPortrait(activity: Activity) {
        if (mIsLocked) return
        if (!mEnableOrientation) return

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (::mControlWrapper.isInitialized) {
            mControlWrapper.stopFullScreen()
        }
    }

    protected open fun onOrientationLandscape(activity: Activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (::mControlWrapper.isInitialized) {
            if (mControlWrapper.isFullScreen()) {
                handlePlayerStateChanged(VideoView.PLAYER_FULL_SCREEN)
            } else {
                mControlWrapper.startFullScreen()
            }
        }
    }

    protected open fun onOrientationReverseLandscape(activity: Activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        if (::mControlWrapper.isInitialized) {
            if (mControlWrapper.isFullScreen()) {
                handlePlayerStateChanged(VideoView.PLAYER_FULL_SCREEN)
            } else {
                mControlWrapper.startFullScreen()
            }
        }
    }

    private fun handleVisibilityChanged(isVisible: Boolean, anim: Animation?) {
        if (!mIsLocked) {
            for ((component) in mControlComponents) {
                component.onVisibilityChanged(isVisible, anim)
            }
        }
        onVisibilityChanged(isVisible, anim)
    }

    protected open fun onVisibilityChanged(isVisible: Boolean, anim: Animation?) {}

    private fun handlePlayStateChanged(playState: Int) {
        for ((component) in mControlComponents) {
            component.onPlayStateChanged(playState)
        }
        onPlayStateChanged(playState)
    }

    @CallSuper
    protected open fun onPlayStateChanged(playState: Int) {
        when (playState) {
            VideoView.STATE_IDLE -> {
                mOrientationHelper?.disable()
                mOrientation = 0
                mIsLocked = false
                mShowing = false
                removeAllDissociateComponents()
            }
            VideoView.STATE_PLAYBACK_COMPLETED -> {
                mIsLocked = false
                mShowing = false
            }
            VideoView.STATE_ERROR -> {
                mShowing = false
            }
        }
    }

    private fun handlePlayerStateChanged(playerState: Int) {
        for ((component) in mControlComponents) {
            component.onPlayerStateChanged(playerState)
        }
        onPlayerStateChanged(playerState)
    }

    @CallSuper
    protected open fun onPlayerStateChanged(playerState: Int) {
        when (playerState) {
            VideoView.PLAYER_NORMAL -> {
                if (mEnableOrientation) {
                    mOrientationHelper?.enable()
                } else {
                    mOrientationHelper?.disable()
                }
                if (hasCutout()) {
                    CutoutUtil.adaptCutoutAboveAndroidP(context, true)
                }
            }
            VideoView.PLAYER_FULL_SCREEN -> {
                mOrientationHelper?.enable()
                if (hasCutout()) {
                    CutoutUtil.adaptCutoutAboveAndroidP(context, true)
                }
            }
            VideoView.PLAYER_TINY_SCREEN -> {
                mOrientationHelper?.disable()
            }
        }
    }

    private fun handleSetProgress(duration: Int, position: Int) {
        for ((component) in mControlComponents) {
            component.setProgress(duration, position)
        }
        setProgress(duration, position)
    }

    protected open fun setProgress(duration: Int, position: Int) {}

    private fun handleLockStateChanged(isLocked: Boolean) {
        for ((component) in mControlComponents) {
            component.onLockStateChanged(isLocked)
        }
        onLockStateChanged(isLocked)
    }

    protected open fun onLockStateChanged(isLocked: Boolean) {}
}
