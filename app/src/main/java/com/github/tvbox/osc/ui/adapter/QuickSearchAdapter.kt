package com.github.tvbox.osc.ui.adapter

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.Movie
import java.util.ArrayList

class QuickSearchAdapter : BaseQuickAdapter<Movie.Video, BaseViewHolder>(R.layout.item_quick_search_lite, ArrayList()) {
    override fun convert(helper: BaseViewHolder, item: Movie.Video?) {
        if (item == null) return
        val source = ApiConfig.get().getSource(item.sourceKey)
        val sourceName = source?.name ?: ""
        helper.setText(
            R.id.tvName,
            String.format("%s  %s %s %s", sourceName, item.name ?: "", item.type ?: "", item.note ?: "")
        )
    }
}
