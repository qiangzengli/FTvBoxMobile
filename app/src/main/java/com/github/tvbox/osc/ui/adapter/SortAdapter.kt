package com.github.tvbox.osc.ui.adapter

import android.view.View
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.MovieSort
import java.util.ArrayList

class SortAdapter : BaseQuickAdapter<MovieSort.SortData, BaseViewHolder>(R.layout.item_home_sort, ArrayList()) {
    private var selectedPosition = 0

    fun setSelectedPosition(position: Int) {
        val previous = selectedPosition
        selectedPosition = position
        notifyItemChanged(previous)
        notifyItemChanged(position)
    }

    override fun convert(helper: BaseViewHolder, item: MovieSort.SortData?) {
        if (item == null) return
        helper.setText(R.id.tvTitle, item.name ?: "")
        val selected = helper.adapterPosition == selectedPosition
        helper.itemView.isSelected = selected
        val hasFilters = item.filters != null && item.filters.isNotEmpty()
        helper.getView<View>(R.id.tvFilter).visibility = if (selected && hasFilters) View.VISIBLE else View.GONE
    }
}
