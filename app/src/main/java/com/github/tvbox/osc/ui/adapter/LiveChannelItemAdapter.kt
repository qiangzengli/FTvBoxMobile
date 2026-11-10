package com.github.tvbox.osc.ui.adapter

import android.graphics.Color
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.LiveChannelItem
import java.util.ArrayList

class LiveChannelItemAdapter : BaseQuickAdapter<LiveChannelItem, BaseViewHolder>(R.layout.item_live_channel, ArrayList()) {
    private var selectedChannelIndex = -1
    private var focusedChannelIndex = -1

    override fun convert(holder: BaseViewHolder, item: LiveChannelItem?) {
        if (item == null) return
        val tvChannelNum = holder.getView<TextView>(R.id.tvChannelNum)
        val tvChannel = holder.getView<TextView>(R.id.tvChannelName)
        tvChannelNum.text = item.getChannelNum().toString()
        tvChannel.text = item.getChannelName()
        tvChannelNum.isSelected = true
        tvChannel.isSelected = true
        val channelIndex = item.getChannelIndex()
        holder.itemView.isSelected = channelIndex == selectedChannelIndex
        if (channelIndex == selectedChannelIndex && channelIndex != focusedChannelIndex) {
            tvChannelNum.setTextColor(mContext.resources.getColor(R.color.color_1890FF))
            tvChannel.setTextColor(mContext.resources.getColor(R.color.color_1890FF))
        } else {
            tvChannelNum.setTextColor(Color.WHITE)
            tvChannel.setTextColor(Color.WHITE)
        }
    }

    fun setSelectedChannelIndex(selectedChannelIndex: Int) {
        if (selectedChannelIndex == this.selectedChannelIndex) return
        val preSelectedChannelIndex = this.selectedChannelIndex
        this.selectedChannelIndex = selectedChannelIndex
        if (preSelectedChannelIndex != -1) {
            notifyItemChanged(preSelectedChannelIndex)
        }
        if (this.selectedChannelIndex != -1) {
            notifyItemChanged(this.selectedChannelIndex)
        }
    }

    fun setFocusedChannelIndex(focusedChannelIndex: Int) {
        val preFocusedChannelIndex = this.focusedChannelIndex
        this.focusedChannelIndex = focusedChannelIndex
        if (preFocusedChannelIndex != -1) {
            notifyItemChanged(preFocusedChannelIndex)
        }
        if (this.focusedChannelIndex != -1) {
            notifyItemChanged(this.focusedChannelIndex)
        } else if (this.selectedChannelIndex != -1) {
            notifyItemChanged(this.selectedChannelIndex)
        }
    }
}
