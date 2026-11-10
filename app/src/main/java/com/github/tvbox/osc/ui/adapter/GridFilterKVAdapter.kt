package com.github.tvbox.osc.ui.adapter

import android.graphics.Color
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import java.util.ArrayList

class GridFilterKVAdapter : BaseQuickAdapter<String, BaseViewHolder>(R.layout.item_grid_filter_value, ArrayList()) {
    private var selectedPosition = -1

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }

    override fun convert(helper: BaseViewHolder, item: String?) {
        helper.setText(R.id.filterValue, item ?: "")
        val selected = helper.layoutPosition == selectedPosition
        val label = helper.getView<TextView>(R.id.filterValue)
        label.isSelected = selected
        label.paint.isFakeBoldText = selected
        label.setTextColor(if (selected) -0xfd071f else Color.WHITE) // 0xFF02F8E1
    }
}
