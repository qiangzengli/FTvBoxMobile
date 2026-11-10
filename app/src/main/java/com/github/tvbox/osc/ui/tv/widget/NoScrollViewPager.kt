package com.github.tvbox.osc.ui.tv.widget

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

/**
 * @author acer
 * @date 2018/7/24
 */
class NoScrollViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewPager(context, attrs) {

    private var swipeEnabled = false

    fun setSwipeEnabled(enabled: Boolean) {
        swipeEnabled = enabled
    }

    /**
     * 禁止viewpager里面内容导致页面切换
     */
    override fun executeKeyEvent(event: KeyEvent): Boolean {
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return swipeEnabled && super.onTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return swipeEnabled && super.onInterceptTouchEvent(ev)
    }
}
