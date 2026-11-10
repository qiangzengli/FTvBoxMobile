package com.github.tvbox.osc.ui.adapter

import android.graphics.Color
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.LiveChannelGroup
import java.util.ArrayList

class LiveChannelGroupAdapter : BaseQuickAdapter<LiveChannelGroup, BaseViewHolder>(R.layout.item_live_channel_group, ArrayList()) {
    private var selectedGroupIndex = -1
    private var focusedGroupIndex = -1

    override fun convert(holder: BaseViewHolder, item: LiveChannelGroup?) {
        if (item == null) return
        val tvGroupName = holder.getView<TextView>(R.id.tvChannelGroupName)
        tvGroupName.text = item.getGroupName()
        tvGroupName.isSelected = true
        val groupIndex = item.getGroupIndex()
        holder.itemView.isSelected = groupIndex == selectedGroupIndex
        if (groupIndex == selectedGroupIndex && groupIndex != focusedGroupIndex) {
            tvGroupName.setTextColor(mContext.resources.getColor(R.color.color_1890FF))
        } else {
            tvGroupName.setTextColor(Color.WHITE)
        }
    }

    fun setSelectedGroupIndex(selectedGroupIndex: Int) {
        if (selectedGroupIndex == this.selectedGroupIndex) return
        val preSelectedGroupIndex = this.selectedGroupIndex
        this.selectedGroupIndex = selectedGroupIndex
        notifyGroupChanged(preSelectedGroupIndex)
        notifyGroupChanged(this.selectedGroupIndex)
    }

    fun getSelectedGroupIndex(): Int {
        return selectedGroupIndex
    }

    fun setFocusedGroupIndex(focusedGroupIndex: Int) {
        this.focusedGroupIndex = focusedGroupIndex
        if (this.focusedGroupIndex != -1) {
            notifyGroupChanged(this.focusedGroupIndex)
        } else if (this.selectedGroupIndex != -1) {
            notifyGroupChanged(this.selectedGroupIndex)
        }
    }

    fun clearGroupState() {
        selectedGroupIndex = -1
        focusedGroupIndex = -1
    }

    private fun notifyGroupChanged(position: Int) {
        if (position in 0 until itemCount) {
            notifyItemChanged(position)
        }
    }
}
