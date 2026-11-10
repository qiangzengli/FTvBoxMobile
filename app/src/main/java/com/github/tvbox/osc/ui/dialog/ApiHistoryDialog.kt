package com.github.tvbox.osc.ui.dialog

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter
import com.owen.tvrecyclerview.widget.TvRecyclerView

class ApiHistoryDialog(context: Context) : BaseDialog(context, R.style.CustomDialogStyleDim) {

    init {
        setContentView(R.layout.dialog_api_history)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun setTip(tip: String?) {
        findViewById<TextView>(R.id.title)?.text = tip
    }

    fun setAdapter(
        sourceBeanSelectDialogInterface: ApiHistoryDialogAdapter.SelectDialogInterface?,
        data: List<String>?,
        select: Int
    ) {
        val adapter = ApiHistoryDialogAdapter(sourceBeanSelectDialogInterface)
        adapter.setData(data, select)
        val tvRecyclerView = findViewById<TvRecyclerView>(R.id.list)
        tvRecyclerView.adapter = adapter
        tvRecyclerView.selectedPosition = select
        tvRecyclerView.post {
            tvRecyclerView.scrollToPosition(select)
        }
    }
}
