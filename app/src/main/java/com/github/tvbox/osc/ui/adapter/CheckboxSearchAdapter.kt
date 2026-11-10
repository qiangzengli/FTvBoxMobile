package com.github.tvbox.osc.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.util.SearchHelper
import java.util.ArrayList
import java.util.HashMap

class CheckboxSearchAdapter(diffCallback: DiffUtil.ItemCallback<SourceBean>) :
    ListAdapter<SourceBean, CheckboxSearchAdapter.ViewHolder>(diffCallback) {

    private val data = ArrayList<SourceBean>()
    @JvmField
    var mCheckedSources: HashMap<String, String>? = HashMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_dialog_checkbox_search, parent, false)
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newData: List<SourceBean>, checkedSources: HashMap<String, String>?) {
        data.clear()
        data.addAll(newData)
        mCheckedSources = checkedSources
        notifyDataSetChanged()
    }

    fun setMCheckedSources() {
        val checked = mCheckedSources ?: HashMap()
        SearchHelper.putCheckedSources(checked, data.size == checked.size)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pos = holder.adapterPosition
        if (pos !in data.indices) return
        val sourceBean = data[pos]
        holder.oneSearchSource.text = sourceBean.name
        holder.oneSearchSource.setOnCheckedChangeListener(null)
        val checked = mCheckedSources
        if (checked != null) {
            holder.oneSearchSource.isChecked = checked.containsKey(sourceBean.key)
        }
        holder.oneSearchSource.tag = sourceBean
        holder.oneSearchSource.setOnCheckedChangeListener { _, isChecked ->
            val curChecked = mCheckedSources ?: HashMap<String, String>().also { mCheckedSources = it }
            val key = sourceBean.key
            if (key != null) {
                if (isChecked) {
                    curChecked[key] = "1"
                } else {
                    curChecked.remove(key)
                }
            }
            notifyItemChanged(pos)
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val oneSearchSource: CheckBox = view.findViewById(R.id.oneSearchSource)
    }
}
