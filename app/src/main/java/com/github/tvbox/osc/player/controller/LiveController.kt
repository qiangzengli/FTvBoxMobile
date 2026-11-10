package com.github.tvbox.osc.player.controller

import android.content.Context
import android.view.MotionEvent
import android.widget.ProgressBar
import com.github.tvbox.osc.R

/**
 * 直播控制器
 */
class LiveController(context: Context) : BaseController(context) {
    protected var mLoading: ProgressBar? = null
    private var minFlingDistance = 100 // 最小识别距离
    private var minFlingVelocity = 10 // 最小识别速度

    override fun getLayoutId(): Int {
        return R.layout.player_live_control_view
    }

    override fun initView() {
        super.initView()
        mLoading = findViewById(R.id.loading)
    }

    interface LiveControlListener {
        fun singleTap(): Boolean
        fun longPress()
        fun playStateChanged(playState: Int)
        fun changeSource(direction: Int)
    }

    private var listener: LiveControlListener? = null

    fun setListener(listener: LiveControlListener?) {
        this.listener = listener
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (listener?.singleTap() == true) return true
        return super.onSingleTapConfirmed(e)
    }

    override fun onLongPress(e: MotionEvent) {
        listener?.longPress()
        super.onLongPress(e)
    }

    override fun onPlayStateChanged(playState: Int) {
        super.onPlayStateChanged(playState)
        listener?.playStateChanged(playState)
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (e1 != null) {
            if (e1.x - e2.x > minFlingDistance && Math.abs(velocityX) > minFlingVelocity) {
                listener?.changeSource(-1) // 左滑
            } else if (e2.x - e1.x > minFlingDistance && Math.abs(velocityX) > minFlingVelocity) {
                listener?.changeSource(1) // 右滑
            }
        }
        return false
    }
}
