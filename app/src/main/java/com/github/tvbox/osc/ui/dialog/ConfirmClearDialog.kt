package com.github.tvbox.osc.ui.dialog

import android.content.Context
import android.widget.TextView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.cache.VodCollect
import com.github.tvbox.osc.ui.activity.CollectActivity
import com.github.tvbox.osc.ui.activity.HistoryActivity
import java.util.ArrayList

class ConfirmClearDialog(context: Context, type: String?) : BaseDialog(context) {

    init {
        setContentView(R.layout.dialog_confirm)
        setCanceledOnTouchOutside(true)
        val tvYes = findViewById<TextView>(R.id.btnConfirm)
        val tvNo = findViewById<TextView>(R.id.btnCancel)
        findViewById<TextView>(R.id.confirmation).text =
            if ("Collect" == type) "清空全部收藏？此操作无法撤销。" else "清空全部观看记录？此操作无法撤销。"

        tvYes.setOnClickListener {
            if ("Collect" == type) {
                val vodInfoList: List<VodCollect> = ArrayList()
                CollectActivity.collectAdapter?.setNewData(vodInfoList)
                CollectActivity.collectAdapter?.notifyDataSetChanged()
                RoomDataManger.deleteVodCollectAll()
            } else if ("History" == type) {
                val vodInfoList: List<VodInfo> = ArrayList()
                HistoryActivity.historyAdapter?.setNewData(vodInfoList)
                HistoryActivity.historyAdapter?.notifyDataSetChanged()
                RoomDataManger.deleteVodRecordAll()
            }
            dismiss()
        }
        tvNo.setOnClickListener {
            dismiss()
        }
    }
}
