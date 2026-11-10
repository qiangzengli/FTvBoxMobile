package com.github.tvbox.osc.ui.adapter

import android.graphics.Color
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.LiveSettingGroup
import java.util.ArrayList

class LiveSettingGroupAdapter : BaseQuickAdapter<LiveSettingGroup, BaseViewHolder>(R.layout.item_live_setting_group, ArrayList()) {
    private var selectedGroupIndex = -1
    private var focusedGroupIndex = -1

    override fun convert(holder: BaseViewHolder, group: LiveSettingGroup?) {
        if (group == null) return
        val tvGroupName = holder.getView<TextView>(R.id.tvSettingGroupName)
        tvGroupName.text = group.getGroupName()
        tvGroupName.isSelected = true
        val groupIndex = group.getGroupIndex()
        holder.itemView.isSelected = groupIndex == selectedGroupIndex
        if (groupIndex == selectedGroupIndex && groupIndex != focusedGroupIndex) {
            tvGroupName.setTextColor(mContext.resources.getColor(R.color.color_1890FF))
        } else {
            tvGroupName.setTextColor(Color.WHITE)
        }
    }

    fun setSelectedGroupIndex(selectedGroupIndex: Int) {
        val preSelectedGroupIndex = this.selectedGroupIndex
        this.selectedGroupIndex = if (findPositionByGroupIndex(selectedGroupIndex) != -1) selectedGroupIndex else -1
        val preSelectedPosition = findPositionByGroupIndex(preSelectedGroupIndex)
        if (preSelectedPosition != -1) {
            notifyItemChanged(preSelectedPosition)
        }
        val selectedPosition = findPositionByGroupIndex(this.selectedGroupIndex)
        if (selectedPosition != -1) {
            notifyItemChanged(selectedPosition)
        }
    }

    fun getSelectedGroupIndex(): Int {
        return selectedGroupIndex
    }

    fun setFocusedGroupIndex(focusedGroupIndex: Int) {
        this.focusedGroupIndex = if (findPositionByGroupIndex(focusedGroupIndex) != -1) focusedGroupIndex else -1
        val focusedPosition = findPositionByGroupIndex(this.focusedGroupIndex)
        if (focusedPosition != -1) {
            notifyItemChanged(focusedPosition)
        } else {
            val selectedPosition = findPositionByGroupIndex(this.selectedGroupIndex)
            if (selectedPosition != -1) {
                notifyItemChanged(selectedPosition)
            }
        }
    }

    fun findPositionByGroupIndex(groupIndex: Int): Int {
        for (i in data.indices) {
            if (data[i].getGroupIndex() == groupIndex) return i
        }
        return -1
    }
}
