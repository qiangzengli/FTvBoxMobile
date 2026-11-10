package com.github.tvbox.osc.ui.activity

import android.content.Intent
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
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.cache.VodCollect
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.adapter.CollectAdapter
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

class CollectActivity : BaseActivity() {

    companion object {
        @JvmField
        var collectAdapter: CollectAdapter? = null
    }

    private var tvDelete: ImageView? = null
    private var tvClear: ImageView? = null
    private var tvDelTip: TextView? = null
    private var mGridView: TvRecyclerView? = null
    private var delMode = false

    override fun getLayoutResID(): Int {
        return R.layout.activity_collect
    }

    override fun init() {
        HawkConfig.hotVodDelete = false
        initView()
        initData()
    }

    private fun toggleDelMode() {
        HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete
        collectAdapter?.notifyDataSetChanged()
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
        collectAdapter = CollectAdapter()
        mGridView?.adapter = collectAdapter
        val empty = TextView(this).apply {
            text = "暂无收藏，在影片详情页点击加入收藏"
            setTextColor(-0x524a3b)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(24, 64, 24, 64)
        }
        collectAdapter?.emptyView = empty
        tvDelete?.setOnClickListener {
            toggleDelMode()
        }
        tvClear?.setOnClickListener {
            val dialog = ConfirmClearDialog(this, "Collect")
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
        collectAdapter?.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            val vodInfo = collectAdapter?.data?.getOrNull(position) ?: return@OnItemClickListener
            if (delMode) {
                collectAdapter?.remove(position)
                RoomDataManger.deleteVodCollect(vodInfo.id)
            } else {
                if (ApiConfig.get().getSource(vodInfo.sourceKey) != null) {
                    val bundle = Bundle().apply {
                        putString("id", vodInfo.vodId)
                        putString("sourceKey", vodInfo.sourceKey)
                        putString("title", vodInfo.name)
                        putString("picture", vodInfo.pic)
                        putBoolean("collect", true)
                    }
                    jumpActivity(DetailActivity::class.java, bundle)
                } else {
                    val newIntent = Intent(mContext, SearchActivity::class.java).apply {
                        putExtra("title", vodInfo.name)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(newIntent)
                }
            }
        }
        collectAdapter?.onItemLongClickListener = BaseQuickAdapter.OnItemLongClickListener { _, _, position ->
            val vodInfo = collectAdapter?.data?.getOrNull(position) ?: return@OnItemLongClickListener false
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
        val allVodRecord = RoomDataManger.getAllVodCollect()
        val vodInfoList = ArrayList<VodCollect>()
        for (vodInfo in allVodRecord) {
            vodInfoList.add(vodInfo)
        }
        collectAdapter?.setNewData(vodInfoList)
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
