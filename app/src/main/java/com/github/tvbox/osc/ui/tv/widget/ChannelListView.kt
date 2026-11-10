package com.github.tvbox.osc.ui.tv.widget

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.ListView
import com.github.tvbox.osc.ui.activity.LivePlayActivity

class ChannelListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ListView(context, attrs, defStyleAttr) {

    @JvmField
    var dataChangedListener: DataChangedListener? = null

    @JvmField
    var pos: Int = LivePlayActivity.currentChannelGroupIndex
    private var y: Int = 0

    fun setSelect(position: Int, y: Int) {
        super.setSelection(position)
        pos = position
        this.y = y
    }

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) {
            setSelectionFromTop(pos, y)
        }
    }

    override fun handleDataChanged() {
        super.handleDataChanged()
        dataChangedListener?.onSuccess()
    }

    fun setDataChangedListener(listener: DataChangedListener?) {
        this.dataChangedListener = listener
    }

    fun interface DataChangedListener {
        fun onSuccess()
    }
}
