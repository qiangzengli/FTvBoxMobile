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

// Callers can pass value types; the fallback uses their normal equality semantics.
@SuppressLint("DiffUtilEquals")
class SelectDialogAdapter<T : Any>(
    private val dialogInterface: SelectDialogInterface<T>?,
    diffCallback: DiffUtil.ItemCallback<T>?
) : ListAdapter<T, SelectDialogAdapter.SelectViewHolder>(
    diffCallback ?: object : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = oldItem == newItem
    }
) {

    class SelectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    interface SelectDialogInterface<T> {
        fun click(value: T, pos: Int)
        fun getDisplay(valItem: T): String
    }

    companion object {
        @JvmField
        val stringDiff = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }
        }
    }

    private val data = ArrayList<T>()
    private var select = 0

    fun setData(newData: List<T>?, defaultSelect: Int) {
        data.clear()
        if (newData != null) {
            data.addAll(newData)
        }
        select = defaultSelect
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectViewHolder {
        return SelectViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_dialog_select, parent, false)
        )
    }

    override fun onBindViewHolder(holder: SelectViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val value = data[position]
        val name = dialogInterface?.getDisplay(value) ?: ""
        val view = holder.itemView.findViewById<TextView>(R.id.tvName)
        view.isSelected = position == select
        view.setCompoundDrawablesWithIntrinsicBounds(0, 0, if (position == select) R.drawable.ic_mobile_check else 0, 0)
        if (position == select) {
            view.setTextColor(-0x513901) // 0xffaec6ff
            view.typeface = Typeface.defaultFromStyle(Typeface.BOLD)
        } else {
            view.setTextColor(Color.WHITE)
            view.typeface = Typeface.defaultFromStyle(Typeface.NORMAL)
        }
        view.text = name
        holder.itemView.setOnClickListener {
            if (position == select) return@setOnClickListener
            notifyItemChanged(select)
            select = position
            notifyItemChanged(select)
            dialogInterface?.click(value, position)
        }
    }
}
