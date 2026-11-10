package com.github.tvbox.osc.ui.adapter

import android.graphics.Color
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.LiveEpgDate
import java.util.ArrayList

class LiveEpgDateAdapter : BaseQuickAdapter<LiveEpgDate, BaseViewHolder>(R.layout.item_live_channel_group, ArrayList()) {

    private var selectedIndex = -1
    private var focusedIndex = -1

    override fun convert(holder: BaseViewHolder, item: LiveEpgDate?) {
        if (item == null) return
        val tvGroupName = holder.getView<TextView>(R.id.tvChannelGroupName)
        tvGroupName.text = item.getDatePresented() ?: ""
        tvGroupName.setBackgroundColor(Color.TRANSPARENT)
        val itemIndex = item.getIndex()
        if (itemIndex == selectedIndex && itemIndex != focusedIndex) {
            tvGroupName.setTextColor(mContext.resources.getColor(R.color.color_1890FF))
        } else {
            tvGroupName.setTextColor(mContext.resources.getColor(R.color.color_CCFFFFFF))
        }
    }

    fun setSelectedIndex(selectedIndex: Int) {
        if (selectedIndex == this.selectedIndex) return
        val preSelectedIndex = this.selectedIndex
        this.selectedIndex = selectedIndex
        if (preSelectedIndex != -1) {
            notifyItemChanged(preSelectedIndex)
        }
        if (this.selectedIndex != -1) {
            notifyItemChanged(this.selectedIndex)
        }
    }

    fun getSelectedIndex(): Int {
        return selectedIndex
    }

    fun setFocusedIndex(focusedIndex: Int) {
        val preSelectedIndex = this.selectedIndex
        this.focusedIndex = focusedIndex
        if (preSelectedIndex != -1) {
            notifyItemChanged(preSelectedIndex)
        }
        if (this.focusedIndex != -1) {
            notifyItemChanged(this.focusedIndex)
        }
    }
}
