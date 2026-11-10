package com.github.tvbox.osc.ui.adapter

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.Subtitle
import java.util.ArrayList

class SearchSubtitleAdapter : BaseQuickAdapter<Subtitle, BaseViewHolder>(R.layout.item_search_subtitle_result, ArrayList()) {
    override fun convert(helper: BaseViewHolder, item: Subtitle?) {
        if (item == null) return
        helper.setText(R.id.subtitleName, item.getName() ?: "")
        helper.setText(R.id.subtitleNameInfo, if (item.getIsZip()) "压缩包" else "文件")
    }
}
