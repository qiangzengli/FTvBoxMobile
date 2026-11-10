package com.github.tvbox.osc.ui.adapter

import android.graphics.Typeface
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import java.util.ArrayList

class FastListAdapter : BaseQuickAdapter<String, BaseViewHolder>(R.layout.item_mobile_source_chip, ArrayList()) {
    private var selectedName: String = "全部"

    override fun convert(helper: BaseViewHolder, item: String?) {
        val textView = helper.getView<TextView>(R.id.tvSearchWord)
        textView.text = item ?: ""
        updateSelection(textView, item)
    }

    fun setSelectedName(selectedName: String?) {
        this.selectedName = if (TextUtils.isEmpty(selectedName)) "全部" else selectedName!!
    }

    fun refreshVisibleSelection(parent: ViewGroup?) {
        if (parent == null) return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is TextView) {
                updateSelection(child, child.text.toString())
            }
        }
    }

    private fun updateSelection(textView: TextView, item: String?) {
        val selected = TextUtils.equals(item, selectedName)
        textView.isSelected = selected
        textView.setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }
}
