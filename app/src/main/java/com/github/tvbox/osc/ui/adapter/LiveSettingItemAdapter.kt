package com.github.tvbox.osc.ui.adapter

import android.graphics.Color
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.LiveSettingItem
import java.util.ArrayList

class LiveSettingItemAdapter : BaseQuickAdapter<LiveSettingItem, BaseViewHolder>(R.layout.item_live_setting, ArrayList()) {
    private var focusedItemIndex = -1

    override fun convert(holder: BaseViewHolder, item: LiveSettingItem?) {
        if (item == null) return
        val tvItemName = holder.getView<TextView>(R.id.tvSettingItemName)
        tvItemName.text = item.getItemName()
        tvItemName.isSelected = true
        val itemIndex = item.getItemIndex()
        holder.itemView.isSelected = item.isItemSelected()
        if (item.isItemSelected() && itemIndex != focusedItemIndex) {
            tvItemName.setTextColor(mContext.resources.getColor(R.color.color_1890FF))
        } else {
            tvItemName.setTextColor(Color.WHITE)
        }
    }

    fun selectItem(selectedItemIndex: Int, select: Boolean, unselectPreItemIndex: Boolean) {
        if (unselectPreItemIndex) {
            val preSelectedItemIndex = getSelectedItemIndex()
            if (preSelectedItemIndex != -1) {
                data[preSelectedItemIndex].setItemSelected(false)
                notifyItemChanged(preSelectedItemIndex)
            }
        }
        if (selectedItemIndex != -1 && selectedItemIndex in data.indices) {
            data[selectedItemIndex].setItemSelected(select)
            notifyItemChanged(selectedItemIndex)
        }
    }

    fun setFocusedItemIndex(focusedItemIndex: Int) {
        val preFocusItemIndex = this.focusedItemIndex
        this.focusedItemIndex = focusedItemIndex
        if (preFocusItemIndex != -1) {
            notifyItemChanged(preFocusItemIndex)
        }
        if (this.focusedItemIndex != -1) {
            notifyItemChanged(this.focusedItemIndex)
        }
    }

    fun getSelectedItemIndex(): Int {
        for (item in data) {
            if (item.isItemSelected()) return item.getItemIndex()
        }
        return -1
    }
}
