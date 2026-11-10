package com.github.tvbox.osc.ui.adapter

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.Epginfo
import com.github.tvbox.osc.ui.tv.widget.AudioWaveView
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date

class LiveEpgAdapter : BaseQuickAdapter<Epginfo, BaseViewHolder>(R.layout.epglist_item, ArrayList()) {
    private var selectedEpgIndex = -1
    private var focusedEpgIndex = -1
    private var shiyiSelection = false
    private var shiyiDate: String? = null
    private var sourceIncludeBack = false

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd")

    companion object {
        @JvmField
        var fontSize: Float = 20f
    }

    fun CanBack(source_include_back: Boolean?) {
        this.sourceIncludeBack = source_include_back ?: false
    }

    @SuppressLint("SetTextI18n")
    override fun convert(holder: BaseViewHolder, value: Epginfo?) {
        if (value == null) return
        val textview = holder.getView<TextView>(R.id.tv_epg_name)
        val timeview = holder.getView<TextView>(R.id.tv_epg_time)
        val shiyi = holder.getView<TextView>(R.id.shiyi)
        val wqddgAudioWaveView = holder.getView<AudioWaveView>(R.id.wqddg_AudioWaveView)
        holder.itemView.isSelected = value.index == selectedEpgIndex || value.index == focusedEpgIndex
        textview.isSelected = true
        timeview.isSelected = true
        wqddgAudioWaveView.visibility = View.GONE

        val todayDate = timeFormat.format(Date())
        if (value.index == selectedEpgIndex && value.index != focusedEpgIndex && (value.currentEpgDate == shiyiDate || value.currentEpgDate == todayDate)) {
            textview.setTextColor(mContext.resources.getColor(R.color.color_1890FF))
            timeview.setTextColor(mContext.resources.getColor(R.color.color_1890FF))
        } else {
            textview.setTextColor(Color.WHITE)
            timeview.setTextColor(Color.WHITE)
        }

        val now = Date()
        val start = value.startdateTime
        val end = value.enddateTime
        if (start != null && end != null && now >= start && now <= end) {
            shiyi.visibility = View.VISIBLE
            shiyi.setBackgroundColor(Color.YELLOW)
            shiyi.text = "直播中"
            shiyi.setTextColor(Color.RED)
        } else if (end != null && now > end && sourceIncludeBack) {
            shiyi.visibility = View.VISIBLE
            shiyi.setBackgroundColor(Color.BLUE)
            shiyi.setTextColor(Color.WHITE)
            shiyi.text = "回看"
        } else {
            shiyi.visibility = View.GONE
        }

        textview.text = value.title ?: ""
        timeview.text = "${value.start}--${value.end}"

        if (!shiyiSelection) {
            if (start != null && end != null && now >= start && now <= end) {
                wqddgAudioWaveView.visibility = View.VISIBLE
                textview.freezesText = true
                timeview.freezesText = true
            } else {
                wqddgAudioWaveView.visibility = View.GONE
            }
        } else {
            if (value.index == this.selectedEpgIndex && value.currentEpgDate == shiyiDate) {
                wqddgAudioWaveView.visibility = View.VISIBLE
                textview.freezesText = true
                timeview.freezesText = true
                shiyi.text = "回看中"
                shiyi.setTextColor(Color.RED)
                shiyi.setBackgroundColor(Color.rgb(12, 255, 0))
                if (start != null && end != null && now >= start && now <= end) {
                    shiyi.visibility = View.VISIBLE
                    shiyi.setBackgroundColor(Color.YELLOW)
                    shiyi.text = "直播中"
                    shiyi.setTextColor(Color.RED)
                }
            } else {
                wqddgAudioWaveView.visibility = View.GONE
            }
        }
    }

    fun setShiyiSelection(i: Int, t: Boolean, currentEpgDate: String?) {
        this.selectedEpgIndex = i
        this.shiyiDate = if (t) currentEpgDate else null
        shiyiSelection = t
        notifyItemChanged(this.selectedEpgIndex)
    }

    fun getSelectedIndex(): Int {
        return selectedEpgIndex
    }

    fun setSelectedEpgIndex(selectedEpgIndex: Int) {
        if (selectedEpgIndex == this.selectedEpgIndex) {
            if (this.selectedEpgIndex != -1) notifyItemChanged(this.selectedEpgIndex)
            return
        }
        val preSelectedEpgIndex = this.selectedEpgIndex
        this.selectedEpgIndex = selectedEpgIndex
        if (preSelectedEpgIndex != -1) {
            notifyItemChanged(preSelectedEpgIndex)
        }
        if (this.selectedEpgIndex != -1) {
            notifyItemChanged(this.selectedEpgIndex)
        }
    }

    fun getFocusedEpgIndex(): Int {
        return focusedEpgIndex
    }

    fun setFocusedEpgIndex(focusedEpgIndex: Int) {
        this.focusedEpgIndex = focusedEpgIndex
    }
}
