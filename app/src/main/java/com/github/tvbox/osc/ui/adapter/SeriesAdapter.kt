package com.github.tvbox.osc.ui.adapter

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.VodInfo
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import java.util.ArrayList

class SeriesAdapter(private val mGridLayoutManager: V7GridLayoutManager) :
    BaseQuickAdapter<VodInfo.VodSeries, BaseViewHolder>(R.layout.item_series, ArrayList()) {

    override fun convert(helper: BaseViewHolder, item: VodInfo.VodSeries?) {
        if (item == null) return
        val tvSeries = helper.getView<TextView>(R.id.tvSeries)
        if (item.selected) {
            tvSeries.setTextColor(mContext.resources.getColor(R.color.color_02F8E1))
        } else {
            tvSeries.setTextColor(Color.WHITE)
        }
        helper.setText(R.id.tvSeries, item.name ?: "")

        if (data.size == 1 && helper.layoutPosition == 0) {
            helper.itemView.nextFocusUpId = R.id.mGridViewFlag
        }

        val activity = helper.itemView.context as? Activity
        val mSeriesGroupTv = activity?.findViewById<View>(R.id.mSeriesGroupTv)
        if (data.size > 1 && mSeriesGroupTv != null && mSeriesGroupTv.visibility == View.VISIBLE) {
            val spanCount = mGridLayoutManager.spanCount
            val position = helper.layoutPosition
            if (position < spanCount) {
                helper.itemView.nextFocusUpId = R.id.mSeriesSortTv
            }
        }
        val mGridViewQuality = activity?.findViewById<View>(R.id.mGridViewQuality)
        if (mGridViewQuality != null && mGridViewQuality.visibility == View.VISIBLE && helper.layoutPosition < mGridLayoutManager.spanCount) {
            helper.itemView.nextFocusUpId = R.id.mGridViewQuality
        }
    }
}
