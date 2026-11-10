package com.github.tvbox.osc.ui.tv.widget

import android.view.View
import android.view.ViewGroup

/**
 * 描述
 *
 * @author pj567
 * @since 2020/7/28
 */
class ViewObj(
    private val view: View,
    private val params: ViewGroup.MarginLayoutParams
) {
    fun setMarginLeft(left: Int) {
        params.leftMargin = left
        view.layoutParams = params
    }

    fun setMarginTop(top: Int) {
        params.topMargin = top
        view.layoutParams = params
    }

    fun setMarginRight(right: Int) {
        params.rightMargin = right
        view.layoutParams = params
    }

    fun setMarginBottom(bottom: Int) {
        params.bottomMargin = bottom
        view.layoutParams = params
    }

    fun setWidth(width: Int) {
        params.width = width
        view.layoutParams = params
    }

    fun setHeight(height: Int) {
        params.height = height
        view.layoutParams = params
    }
}
