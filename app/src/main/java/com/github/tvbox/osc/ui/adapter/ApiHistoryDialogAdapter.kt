package com.github.tvbox.osc.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.util.HistoryHelper
import java.util.ArrayList

class ApiHistoryDialogAdapter(private val dialogInterface: SelectDialogInterface?) :
    ListAdapter<String, ApiHistoryDialogAdapter.SelectViewHolder>(object : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }) {

    class SelectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    interface SelectDialogInterface {
        fun click(value: String)
        fun del(value: String, data: ArrayList<String>)
    }

    private val data = ArrayList<String>()
    private var select = ""

    fun setData(newData: List<String>?, defaultSelect: Int) {
        data.clear()
        if (newData != null) {
            data.addAll(newData)
        }
        select = if (defaultSelect in data.indices) data[defaultSelect] else ""
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectViewHolder {
        return SelectViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_dialog_api_history, parent, false)
        )
    }

    override fun onBindViewHolder(holder: SelectViewHolder, position: Int) {
        val value = data[position]
        var name = value
        if (HistoryHelper.isApiLineSource(value)) {
            name = "[仓] $name"
        }
        if (select == value) {
            name = "√ $name"
        }
        holder.itemView.findViewById<TextView>(R.id.tvName).text = name
        holder.itemView.findViewById<View>(R.id.tvName).setOnClickListener {
            if (select == value) return@setOnClickListener
            notifyItemChanged(data.indexOf(select))
            select = value
            notifyItemChanged(data.indexOf(value))
            dialogInterface?.click(value)
        }
        holder.itemView.findViewById<View>(R.id.tvDel).setOnClickListener {
            if (select == value) return@setOnClickListener
            val removeIndex = data.indexOf(value)
            notifyItemRemoved(removeIndex)
            data.remove(value)
            dialogInterface?.del(value, data)
        }
    }
}
