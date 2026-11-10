package com.github.tvbox.osc.ui.dialog

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.MovieSort
import com.github.tvbox.osc.ui.adapter.GridFilterKVAdapter
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import java.util.ArrayList

class GridFilterDialog(context: Context) : BaseDialog(context) {
    @JvmField
    var filterRoot: LinearLayout

    private var selectChange = false
    private var onChanged: Callback? = null

    init {
        setCanceledOnTouchOutside(false)
        setCancelable(true)
        setContentView(R.layout.dialog_grid_filter)
        filterRoot = findViewById(R.id.filterRoot)
    }

    interface Callback {
        fun change()
    }

    fun setOnDismiss(callback: Callback) {
        setOnDismissListener {
            if (selectChange) {
                callback.change()
            }
        }
    }

    fun setOnChanged(callback: Callback?) {
        onChanged = callback
    }

    fun setData(sortData: MovieSort.SortData) {
        filterRoot.removeAllViews()
        for (filter in sortData.filters) {
            val key = filter.key ?: continue
            val line = LayoutInflater.from(context).inflate(R.layout.item_grid_filter, filterRoot, false)
            line.findViewById<TextView>(R.id.filterName).text = filter.name
            val gridView = line.findViewById<TvRecyclerView>(R.id.mFilterKv)
            val widthDp = context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density
            val fontScale = context.resources.configuration.fontScale
            val columns = Math.max(1, Math.min(3, (widthDp / (112 * Math.max(1f, fontScale))).toInt()))
            gridView.setHasFixedSize(false)
            gridView.isNestedScrollingEnabled = false
            gridView.layoutManager = V7GridLayoutManager(context, columns)
            val adapter = GridFilterKVAdapter()
            val filterValues = filter.values
            val labels = if (filterValues != null) ArrayList(filterValues.keys) else ArrayList<String>()
            val values = if (filterValues != null) ArrayList(filterValues.values) else ArrayList<String>()
            val selectedVal = sortData.filterSelect?.get(key)
            adapter.setSelectedPosition(values.indexOf(selectedVal))
            gridView.adapter = adapter
            adapter.setOnItemClickListener { _, _, position ->
                val value = values[position]
                val currentSelected = sortData.filterSelect?.get(key)
                val deselect = value == currentSelected
                if (deselect) {
                    sortData.filterSelect?.remove(key)
                } else {
                    sortData.filterSelect?.put(key, value)
                }
                adapter.setSelectedPosition(if (deselect) -1 else position)
                selectChange = true
                onChanged?.change()
            }
            adapter.setNewData(labels)
            filterRoot.addView(line)
        }
    }

    override fun show() {
        selectChange = false
        super.show()
    }
}
