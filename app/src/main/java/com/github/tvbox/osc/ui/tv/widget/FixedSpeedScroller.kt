package com.github.tvbox.osc.ui.tv.widget

import android.content.Context
import android.view.animation.Interpolator
import android.widget.Scroller

/**
 * @author acer
 * @date 2018/7/24
 */
class FixedSpeedScroller : Scroller {
    private var mDuration = 0

    fun setmDuration(mDuration: Int) {
        this.mDuration = mDuration
    }

    constructor(context: Context?) : super(context)

    constructor(context: Context?, interpolator: Interpolator?) : super(context, interpolator)

    constructor(context: Context?, interpolator: Interpolator?, flywheel: Boolean) : super(
        context,
        interpolator,
        flywheel
    )

    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
        super.startScroll(startX, startY, dx, dy, mDuration)
    }

    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int) {
        super.startScroll(startX, startY, dx, dy, mDuration)
    }
}
