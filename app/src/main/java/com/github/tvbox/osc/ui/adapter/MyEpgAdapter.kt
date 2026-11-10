package com.github.tvbox.osc.ui.adapter

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.Epginfo
import com.github.tvbox.osc.ui.tv.widget.AudioWaveView

class MyEpgAdapter(private val data: List<Epginfo>, private val context: Context, i: Int) : BaseAdapter() {
    private var defaultSelection = i
    private var defaultShiyiSelection = 0

    companion object {
        @JvmField
        var fontSize: Float = 20f
    }

    fun setSelection(i: Int) {
        this.defaultSelection = i
        notifyDataSetChanged()
    }

    fun setShiyiSelection(i: Int) {
        this.defaultShiyiSelection = i
        notifyDataSetChanged()
    }

    fun setFontSize(f: Float) {
        fontSize = f
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return data.size
    }

    override fun getItem(i: Int): Any? {
        return null
    }

    override fun getItemId(i: Int): Long {
        return i.toLong()
    }

    override fun getView(i: Int, convertView: View?, viewGroup: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.epglist_item, viewGroup, false)
        val textview = view.findViewById<TextView>(R.id.tv_epg_name)
        val timeview = view.findViewById<TextView>(R.id.tv_epg_time)
        val wqddgAudioWaveView = view.findViewById<AudioWaveView>(R.id.wqddg_AudioWaveView)
        wqddgAudioWaveView.visibility = View.GONE
        if (i < data.size) {
            val item = data[i]
            textview.text = item.title ?: ""
            timeview.text = "${item.start}--${item.end}"
            textview.setTextColor(Color.WHITE)
            timeview.setTextColor(Color.WHITE)
            Log.e("roinlong", "getView: $i")
            if (i == this.defaultSelection) {
                wqddgAudioWaveView.visibility = View.VISIBLE
                textview.setTextColor(Color.rgb(0, 153, 255))
                timeview.setTextColor(Color.rgb(0, 153, 255))
                textview.freezesText = true
                timeview.freezesText = true
            } else {
                wqddgAudioWaveView.visibility = View.GONE
            }
        }
        return view
    }
}
