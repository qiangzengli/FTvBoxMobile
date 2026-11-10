package com.github.tvbox.osc.ui.dialog

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import java.util.ArrayList

class EpisodeDialog(
    context: Context,
    title: String?,
    episodes: List<VodInfo.VodSeries>?,
    selectedPosition: Int,
    listener: EpisodeSelectListener?
) : BaseDialog(context) {

    interface EpisodeSelectListener {
        fun selectEpisode(position: Int)
    }

    private val episodes: List<VodInfo.VodSeries>
    private val selectedPosition: Int
    private val listener: EpisodeSelectListener?

    init {
        if (context is Activity) setOwnerActivity(context)
        this.episodes = if (episodes == null) ArrayList() else ArrayList(episodes)
        this.selectedPosition = Math.max(0, Math.min(selectedPosition, Math.max(0, this.episodes.size - 1)))
        this.listener = listener
        setContentView(R.layout.dialog_episode)
        init(title)
    }

    private fun init(title: String?) {
        val episodeTitle = findViewById<TextView>(R.id.episode_title)
        episodeTitle.text = title ?: ""
        findViewById<View>(R.id.episode_dismiss).setOnClickListener {
            dismiss()
        }

        val episodeList = findViewById<TvRecyclerView>(R.id.episode_list)
        val adapter = EpisodeAdapter(selectedPosition)
        episodeList.setHasFixedSize(true)
        val layoutManager = V7GridLayoutManager(context, 1)
        episodeList.layoutManager = layoutManager
        episodeList.adapter = adapter
        adapter.setNewData(episodes)
        val initialGridWidth = context.resources.displayMetrics.widthPixels / 2 -
                context.resources.getDimensionPixelSize(R.dimen.vs_30) * 2
        if (initialGridWidth > 0) {
            layoutManager.spanCount = getSpanCount(initialGridWidth)
            scrollToSelectedPosition(layoutManager)
            episodeList.setSelectedPosition(selectedPosition)
        }
        adapter.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            dismiss()
            listener?.selectEpisode(position)
        }
        episodeList.post(object : Runnable {
            override fun run() {
                if (episodeList.width <= 0) {
                    episodeList.postDelayed(this, 50)
                    return
                }
                val spanCount = getSpanCount(episodeList.width)
                if (layoutManager.spanCount != spanCount) {
                    layoutManager.spanCount = spanCount
                    scrollToSelectedPosition(layoutManager)
                }
                episodeList.setSelectedPosition(selectedPosition)
                requestSelectedEpisodeFocus(episodeList, layoutManager, 0)
            }
        })
    }

    private fun scrollToSelectedPosition(layoutManager: V7GridLayoutManager) {
        val rowsBeforeSelected = 2
        val startPosition = Math.max(0, selectedPosition - layoutManager.spanCount * rowsBeforeSelected)
        layoutManager.scrollToPositionWithOffset(startPosition, 0)
    }

    private fun requestSelectedEpisodeFocus(
        episodeList: TvRecyclerView,
        layoutManager: V7GridLayoutManager,
        retryCount: Int
    ) {
        episodeList.postDelayed({
            val selectedView = layoutManager.findViewByPosition(selectedPosition)
            if (selectedView != null) {
                selectedView.requestRectangleOnScreen(
                    Rect(0, 0, selectedView.width, selectedView.height),
                    true
                )
            } else if (retryCount < 3) {
                scrollToSelectedPosition(layoutManager)
                requestSelectedEpisodeFocus(episodeList, layoutManager, retryCount + 1)
            }
        }, 50)
    }

    private fun getSpanCount(gridWidth: Int): Int {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = 14 * context.resources.displayMetrics.scaledDensity
        val bounds = Rect()
        var maxTextWidth = 1
        for (episode in episodes) {
            val name = episode.name ?: ""
            paint.getTextBounds(name, 0, name.length, bounds)
            maxTextWidth = Math.max(maxTextWidth, bounds.width())
        }
        val itemPadding = com.fbox.mobile.MobileSheets.dp(context, 24f)
        val itemMargin = com.fbox.mobile.MobileSheets.dp(context, 8f)
        val itemWidth = maxTextWidth + itemPadding + itemMargin
        return Math.max(1, Math.min(4, gridWidth / Math.max(1, itemWidth)))
    }

    private class EpisodeAdapter(private val selectedPosition: Int) :
        BaseQuickAdapter<VodInfo.VodSeries, BaseViewHolder>(R.layout.item_series, ArrayList()) {

        override fun convert(helper: BaseViewHolder, item: VodInfo.VodSeries?) {
            val series = helper.getView<TextView>(R.id.tvSeries)
            helper.itemView.isSelected = helper.layoutPosition == selectedPosition
            series.text = item?.name ?: ""
            series.setTextColor(
                if (helper.layoutPosition == selectedPosition)
                    mContext.resources.getColor(R.color.color_02F8E1)
                else
                    Color.WHITE
            )
        }
    }
}
