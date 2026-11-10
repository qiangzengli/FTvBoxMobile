package com.github.tvbox.osc.ui.dialog

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import com.github.tvbox.osc.R
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter
import com.owen.tvrecyclerview.widget.TvRecyclerView

class SelectDialog<T> : BaseDialog {

    @JvmOverloads
    constructor(context: Context, resId: Int = R.layout.dialog_select) : super(context) {
        setContentView(resId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun setTip(tip: String?) {
        findViewById<TextView>(R.id.title)?.text = tip
    }

    fun setAdapter(
        sourceBeanSelectDialogInterface: SelectDialogAdapter.SelectDialogInterface<T>?,
        sourceBeanItemCallback: DiffUtil.ItemCallback<T>?,
        data: List<T>?,
        select: Int
    ) {
        val selectIdx = select
        val adapter = SelectDialogAdapter<T>(sourceBeanSelectDialogInterface, sourceBeanItemCallback)
        adapter.setData(data, select)
        val tvRecyclerView = findViewById<TvRecyclerView>(R.id.list)
        tvRecyclerView.adapter = adapter
        tvRecyclerView.selectedPosition = select
        if (select < 10) {
            tvRecyclerView.setSelection(select)
        }
        tvRecyclerView.post {
            if (selectIdx >= 10) {
                tvRecyclerView.smoothScrollToPosition(selectIdx)
                tvRecyclerView.setSelectionWithSmooth(selectIdx)
            }
        }
    }
}
