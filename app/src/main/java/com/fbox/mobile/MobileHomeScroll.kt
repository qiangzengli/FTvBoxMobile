package com.fbox.mobile

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

class MobileHomeScroll(context: Context, categoryList: View, private val pager: View) :
    ViewGroup(context), NestedScrollingParent3 {

    private val nested = NestedScrollingParentHelper(this)
    private val header: LinearLayout
    private val categories: FrameLayout
    private val categoryHeight: Int = MobileSheets.dp(context, 56f)
    private val actionsHeight: Int = MobileSheets.dp(context, 84f)
    private var actions: View? = null
    private var scrollingTarget: View? = null
    private var offset = 0
    private var homePage = true

    init {
        clipChildren = true
        header = LinearLayout(context)
        header.orientation = LinearLayout.VERTICAL
        categories = FrameLayout(context)
        categories.addView(categoryList, FrameLayout.LayoutParams(-1, categoryHeight))
        header.addView(categories, LinearLayout.LayoutParams(-1, categoryHeight))
        addView(header, LayoutParams(-1, -2))
        addView(pager, LayoutParams(-1, -1))
    }

    fun bindActions(clip: View?) {
        if (clip == null || actions === clip) return
        if (actions != null) header.removeView(actions)
        if (clip.parent is ViewGroup) (clip.parent as ViewGroup).removeView(clip)
        actions = clip
        val params = LinearLayout.LayoutParams(-1, actionsHeight)
        val margin = MobileSheets.dp(context, 8f)
        params.leftMargin = margin
        params.rightMargin = margin
        header.addView(actions, params)
        actions?.visibility = if (homePage) VISIBLE else GONE
        requestLayout()
    }

    fun selectPage(position: Int) {
        val target = scrollingTarget
        if (target is RecyclerView) {
            target.stopScroll()
        }
        scrollingTarget = null
        homePage = position == 0
        actions?.visibility = if (homePage) VISIBLE else GONE
        expand()
        requestLayout()
    }

    fun expand() {
        offset = 0
        applyOffset()
    }

    private fun range(): Int = categoryHeight + if (homePage && actions != null) actionsHeight else 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        header.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(range(), MeasureSpec.EXACTLY)
        )
        pager.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        offset = max(0, min(range(), offset))
        header.layout(0, -offset, width, range() - offset)
        pager.layout(0, range() - offset, width, range() - offset + height)
        updateAccessibility()
    }

    private fun move(dy: Int): Int {
        val previous = offset
        offset = max(0, min(range(), offset + dy))
        if (offset != previous) applyOffset()
        return offset - previous
    }

    private fun applyOffset() {
        ViewCompat.offsetTopAndBottom(header, -offset - header.top)
        ViewCompat.offsetTopAndBottom(pager, range() - offset - pager.top)
        updateAccessibility()
    }

    private fun updateAccessibility() {
        categories.importantForAccessibility = if (offset >= categoryHeight) {
            IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        actions?.importantForAccessibility = if (!homePage || offset >= range()) {
            IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
    }

    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
        val visible = Rect(rectangle)
        visible.offset(child.left, child.top)
        if (child === pager && visible.bottom > height && offset < range()) {
            move(range() - offset)
            return true
        }
        return super.requestChildRectangleOnScreen(child, rectangle, immediate)
    }

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        return (axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        scrollingTarget = target
        nested.onNestedScrollAccepted(child, target, axes, type)
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        nested.onStopNestedScroll(target, type)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        consumed[1] += move(dy)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        consumed[1] += move(dyUnconsumed)
    }

    override fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, type: Int) {
        move(dyUnconsumed)
    }

    override fun getNestedScrollAxes(): Int = nested.nestedScrollAxes

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean = false

    override fun onNestedFling(target: View, velocityX: Float, velocityY: Float, consumed: Boolean): Boolean = false

    override fun onStartNestedScroll(child: View, target: View, axes: Int): Boolean {
        return onStartNestedScroll(child, target, axes, ViewCompat.TYPE_TOUCH)
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int) {
        onNestedScrollAccepted(child, target, axes, ViewCompat.TYPE_TOUCH)
    }

    override fun onStopNestedScroll(target: View) {
        onStopNestedScroll(target, ViewCompat.TYPE_TOUCH)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH)
    }

    override fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, ViewCompat.TYPE_TOUCH)
    }
}
