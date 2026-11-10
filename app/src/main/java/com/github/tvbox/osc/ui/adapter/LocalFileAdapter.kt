package com.github.tvbox.osc.ui.adapter

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import java.io.File
import java.text.DecimalFormat
import java.util.ArrayList

class LocalFileAdapter : BaseQuickAdapter<File, BaseViewHolder>(R.layout.item_local_file, ArrayList()) {
    companion object {
        private val SIZE_FORMAT = DecimalFormat("0.#")
    }

    private var parentDir: File? = null

    override fun convert(helper: BaseViewHolder, item: File?) {
        if (item == null) return
        val isParent = parentDir != null && parentDir == item
        helper.setText(R.id.tvType, if (item.isDirectory) "目录" else "文件")
        helper.setText(R.id.tvName, if (isParent) ".." else item.name)
        helper.setText(R.id.tvInfo, if (item.isDirectory) "进入" else formatSize(item.length()))
    }

    fun setParentDir(parentDir: File?) {
        this.parentDir = parentDir
    }

    private fun formatSize(size: Long): String {
        if (size < 1024) return "$size B"
        val kb = size / 1024.0
        if (kb < 1024) return SIZE_FORMAT.format(kb) + " KB"
        val mb = kb / 1024.0
        if (mb < 1024) return SIZE_FORMAT.format(mb) + " MB"
        return SIZE_FORMAT.format(mb / 1024.0) + " GB"
    }
}
