package com.github.tvbox.osc.ui.adapter

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import java.util.ArrayList

class SettingMenuAdapter : BaseQuickAdapter<String, BaseViewHolder>(R.layout.item_setting_menu, ArrayList()) {
    override fun convert(helper: BaseViewHolder, item: String?) {
        helper.setText(R.id.tvName, item ?: "")
        helper.addOnClickListener(R.id.tvName)
    }
}
