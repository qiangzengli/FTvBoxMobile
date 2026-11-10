package com.github.tvbox.osc.ui.activity

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.BounceInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.adapter.HistoryAdapter
import com.github.tvbox.osc.ui.dialog.ConfirmClearDialog
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.ArrayList

/**
 * @author pj567
 * @date :2021/1/7
 * @description:
 */
class HistoryActivity : BaseActivity() {

    companion object {
        @JvmField
        var historyAdapter: HistoryAdapter? = null
    }

    private var tvDelete: ImageView? = null
    private var tvClear: ImageView? = null
    private var tvDelTip: TextView? = null
    private var mGridView: TvRecyclerView? = null
    private var delMode = false

    override fun getLayoutResID(): Int {
        return R.layout.activity_history
    }

    override fun shouldRefreshAutoSize(): Boolean {
        return true
    }

    override fun init() {
        HawkConfig.hotVodDelete = false
        initView()
        initData()
    }

    private fun toggleDelMode() {
        HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete
        historyAdapter?.notifyDataSetChanged()
        delMode = !delMode
        tvDelTip?.visibility = if (delMode) View.VISIBLE else View.GONE
    }

    private fun initView() {
        EventBus.getDefault().register(this)
        tvDelete = findViewById(R.id.tvDelete)
        tvClear = findViewById(R.id.tvClear)
        tvDelTip = findViewById(R.id.tvDelTip)
        mGridView = findViewById(R.id.mGridView)
        mGridView?.setHasFixedSize(true)
        mGridView?.layoutManager = V7GridLayoutManager(this.mContext, if (isPhonePortrait()) 1 else if (isBaseOnWidth()) 5 else 6)
        historyAdapter = HistoryAdapter()
        mGridView?.adapter = historyAdapter
        val empty = TextView(this).apply {
            text = "暂无观看记录，播放影片后会显示在这里"
            setTextColor(-0x524a3b)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(24, 64, 24, 64)
        }
        historyAdapter?.emptyView = empty
        tvDelete?.setOnClickListener {
            toggleDelMode()
        }
        tvClear?.setOnClickListener {
            val dialog = ConfirmClearDialog(this, "History")
            dialog.show()
        }
        mGridView?.setOnInBorderKeyEventListener { direction, _ ->
            if (direction == View.FOCUS_UP) {
                tvDelete?.isFocusable = true
                tvClear?.isFocusable = true
                tvDelete?.requestFocus()
            }
            false
        }
        mGridView?.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(BounceInterpolator()).start()
            }

            override fun onItemSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(BounceInterpolator()).start()
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View, position: Int) {}
        })
        historyAdapter?.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            if (position == -1) return@OnItemClickListener
            val vodInfo = historyAdapter?.data?.getOrNull(position) ?: return@OnItemClickListener
            if (delMode) {
                historyAdapter?.remove(position)
                RoomDataManger.deleteVodRecord(vodInfo.sourceKey, vodInfo)
            } else {
                val bundle = Bundle().apply {
                    putString("id", vodInfo.id)
                    putString("sourceKey", vodInfo.sourceKey)
                }
                val sourceBean = ApiConfig.get().getSource(vodInfo.sourceKey)
                if (sourceBean != null) {
                    bundle.putString("title", vodInfo.name)
                    bundle.putString("picture", vodInfo.pic)
                    jumpActivity(DetailActivity::class.java, bundle)
                } else {
                    bundle.putString("title", vodInfo.name)
                    if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
                        jumpActivity(FastSearchActivity::class.java, bundle)
                    } else {
                        jumpActivity(SearchActivity::class.java, bundle)
                    }
                }
            }
        }
        historyAdapter?.onItemLongClickListener = BaseQuickAdapter.OnItemLongClickListener { _, _, position ->
            val vodInfo = historyAdapter?.data?.getOrNull(position) ?: return@OnItemLongClickListener false
            val bundle = Bundle().apply {
                putString("title", vodInfo.name)
            }
            if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
                jumpActivity(FastSearchActivity::class.java, bundle)
            } else {
                jumpActivity(SearchActivity::class.java, bundle)
            }
            true
        }
    }

    private fun initData() {
        val allVodRecord = RoomDataManger.getAllVodRecord(100)
        val vodInfoList = ArrayList<VodInfo>()
        for (vodInfo in allVodRecord) {
            if (!vodInfo.playNote.isNullOrEmpty()) {
                vodInfo.note = "上次看到" + vodInfo.playNote
            }
            vodInfoList.add(vodInfo)
        }
        historyAdapter?.setNewData(vodInfoList)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH) {
            initData()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    override fun onBackPressed() {
        if (delMode) {
            toggleDelMode()
            return
        }
        super.onBackPressed()
    }
}
