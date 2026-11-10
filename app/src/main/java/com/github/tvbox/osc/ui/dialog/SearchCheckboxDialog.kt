package com.github.tvbox.osc.ui.dialog

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.ui.adapter.CheckboxSearchAdapter
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import java.util.HashMap

class SearchCheckboxDialog(
    context: Context,
    @JvmField var mSourceList: MutableList<SourceBean>,
    @JvmField var mCheckSourcees: HashMap<String, String>?
) : BaseDialog(context) {

    private lateinit var mGridView: TvRecyclerView
    private lateinit var checkboxSearchAdapter: CheckboxSearchAdapter
    private lateinit var checkAll: TextView
    private lateinit var clearAll: TextView

    init {
        if (context is Activity) {
            setOwnerActivity(context)
        }
        setCanceledOnTouchOutside(false)
        setCancelable(true)
        setContentView(R.layout.dialog_checkbox_search)
        initView(context)
    }

    override fun dismiss() {
        checkboxSearchAdapter.setMCheckedSources()
        super.dismiss()
    }

    protected fun initView(context: Context) {
        mGridView = findViewById(R.id.mGridView)
        checkAll = findViewById(R.id.checkAll)
        clearAll = findViewById(R.id.clearAll)
        checkboxSearchAdapter = CheckboxSearchAdapter(object : DiffUtil.ItemCallback<SourceBean>() {
            override fun areItemsTheSame(oldItem: SourceBean, newItem: SourceBean): Boolean {
                return oldItem.getKey() == newItem.getKey()
            }

            override fun areContentsTheSame(oldItem: SourceBean, newItem: SourceBean): Boolean {
                return oldItem.getName() == newItem.getName()
            }
        })
        mGridView.setHasFixedSize(true)

        val size = mSourceList.size
        var spanCount = Math.floor((size / 10).toDouble()).toInt()
        if (spanCount <= 0) spanCount = 1
        if (spanCount > 2) spanCount = 2
        mGridView.layoutManager = V7GridLayoutManager(context, spanCount)
        val root = findViewById<View>(R.id.root)
        val clp = root.layoutParams
        clp.width = ViewGroup.LayoutParams.MATCH_PARENT

        mGridView.adapter = checkboxSearchAdapter
        checkboxSearchAdapter.setData(mSourceList, mCheckSourcees)

        checkAll.setOnClickListener { view ->
            FastClickCheckUtil.check(view)
            val checkMap = HashMap<String, String>()
            for (sourceBean in mSourceList) {
                checkMap[sourceBean.getKey()] = "1"
            }
            mCheckSourcees = checkMap
            checkboxSearchAdapter.setData(mSourceList, mCheckSourcees)
        }
        clearAll.setOnClickListener { view ->
            FastClickCheckUtil.check(view)
            mCheckSourcees = HashMap()
            checkboxSearchAdapter.setData(mSourceList, mCheckSourcees)
        }
    }

    fun setMSourceList(sourceBeanList: MutableList<SourceBean>) {
        mSourceList = sourceBeanList
        checkboxSearchAdapter.setData(mSourceList, mCheckSourcees)
    }
}
