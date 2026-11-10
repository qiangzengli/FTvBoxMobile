package com.github.tvbox.osc.ui.adapter

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import java.util.ArrayList

class BackupAdapter : BaseQuickAdapter<String, BaseViewHolder>(R.layout.item_dialog_backup, ArrayList()) {
    override fun convert(helper: BaseViewHolder, item: String?) {
        helper.setText(R.id.tvName, item ?: "")
        helper.addOnClickListener(R.id.tvName)
        helper.addOnClickListener(R.id.tvDel)
    }
}
