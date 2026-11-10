package com.github.tvbox.osc.ui.adapter

import android.app.Activity
import android.view.View
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.VodInfo
import java.util.ArrayList

class SeriesFlagAdapter : BaseQuickAdapter<VodInfo.VodSeriesFlag, BaseViewHolder>(R.layout.item_series_flag, ArrayList()) {

    override fun convert(helper: BaseViewHolder, item: VodInfo.VodSeriesFlag?) {
        if (item == null) return
        val select = helper.getView<View>(R.id.tvSeriesFlagSelect)
        select.visibility = if (item.selected) View.VISIBLE else View.GONE
        helper.setText(R.id.tvSeriesFlag, item.name ?: "")

        val activity = helper.itemView.context as? Activity
        val mSeriesGroupTv = activity?.findViewById<View>(R.id.mSeriesGroupTv)
        val mGridViewQuality = activity?.findViewById<View>(R.id.mGridViewQuality)

        if (mSeriesGroupTv != null && mSeriesGroupTv.visibility == View.VISIBLE) {
            helper.itemView.nextFocusDownId = R.id.mSeriesSortTv
        } else if (mGridViewQuality != null && mGridViewQuality.visibility == View.VISIBLE) {
            helper.itemView.nextFocusDownId = R.id.mGridViewQuality
        } else {
            helper.itemView.nextFocusDownId = R.id.mGridView
        }

        if (helper.layoutPosition == data.size - 1) {
            helper.itemView.id = View.generateViewId()
            helper.itemView.nextFocusRightId = helper.itemView.id
        } else {
            helper.itemView.nextFocusRightId = View.NO_ID
        }
    }
}
