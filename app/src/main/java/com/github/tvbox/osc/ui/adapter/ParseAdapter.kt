package com.github.tvbox.osc.ui.adapter

import android.graphics.Color
import android.view.View
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.ParseBean
import java.util.ArrayList

class ParseAdapter : BaseQuickAdapter<ParseBean, BaseViewHolder>(R.layout.item_play_parse, ArrayList()) {
    override fun convert(helper: BaseViewHolder, item: ParseBean?) {
        if (item == null) return
        val tvParse = helper.getView<TextView>(R.id.tvParse)
        tvParse.visibility = View.VISIBLE
        if (item.isDefault()) {
            tvParse.setTextColor(mContext.resources.getColor(R.color.color_02F8E1))
        } else {
            tvParse.setTextColor(Color.WHITE)
        }
        tvParse.text = item.getName() ?: ""
        if (helper.layoutPosition == 0) {
            helper.itemView.nextFocusLeftId = R.id.screen_display
        }
    }
}
