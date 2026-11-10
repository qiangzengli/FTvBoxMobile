package com.github.tvbox.osc.ui.adapter

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.tvbox.osc.R
import java.util.ArrayList

class ButtonAdapter<T : Any>(
    private val dialogInterface: SelectDialogInterface<T>,
    diffCallback: DiffUtil.ItemCallback<T>
) : ListAdapter<T, ButtonAdapter.SelectViewHolder>(diffCallback) {

    interface SelectDialogInterface<T> {
        fun click(value: T, pos: Int)
        fun getDisplay(value: T): String
    }

    class SelectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private val data = ArrayList<T>()
    private var select = 0

    fun setData(newData: List<T>, defaultSelect: Int) {
        data.clear()
        data.addAll(newData)
        select = Math.max(0, Math.min(defaultSelect, data.size - 1))
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectViewHolder {
        return SelectViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_button, parent, false)
        )
    }

    override fun onBindViewHolder(holder: SelectViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val value = data[position]
        val item = holder.itemView.findViewById<TextView>(R.id.tvName)
        item.text = dialogInterface.getDisplay(value)
        if (position == select) {
            item.setTextColor(-0xfd071f) // 0xff02f8e1
            item.typeface = Typeface.defaultFromStyle(Typeface.BOLD)
        } else {
            item.setTextColor(Color.WHITE)
            item.typeface = Typeface.defaultFromStyle(Typeface.NORMAL)
        }
        holder.itemView.setOnClickListener {
            if (position == select) return@setOnClickListener
            val oldSelect = select
            select = position
            notifyItemChanged(oldSelect)
            notifyItemChanged(select)
            dialogInterface.click(value, position)
        }
    }
}
