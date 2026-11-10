package com.github.tvbox.osc.ui.adapter

import android.text.TextUtils
import android.widget.ImageView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.ImgUtil
import com.orhanobut.hawk.Hawk
import me.jessyan.autosize.utils.AutoSizeUtils
import java.util.ArrayList

open class SearchAdapter : BaseQuickAdapter<Movie.Video, BaseViewHolder>(
    if (Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0) R.layout.item_search_lite else R.layout.item_search_normal,
    ArrayList()
) {
    companion object {
        private const val SEARCH_POSTER_WIDTH = 180
        private const val SEARCH_POSTER_HEIGHT = 240
    }

    override fun convert(helper: BaseViewHolder, item: Movie.Video?) {
        if (item == null) return
        val source = ApiConfig.get().getSource(item.sourceKey)
        val sourceName = source?.name ?: ""
        if (Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0) {
            val text = String.format("%s\n%s · %s %s", item.name ?: "", sourceName, item.type ?: "", item.note ?: "")
            helper.setText(R.id.tvName, text)
        } else {
            helper.setText(R.id.tvName, item.name ?: "")
            helper.setText(R.id.tvSite, sourceName)
            val hasNote = !item.note.isNullOrEmpty()
            helper.setVisible(R.id.tvNote, hasNote)
            if (hasNote) {
                helper.setText(R.id.tvNote, item.note)
            }
            val ivThumb = helper.getView<ImageView>(R.id.ivThumb)
            val pic = item.pic?.trim()
            if (!pic.isNullOrEmpty()) {
                ImgUtil.load(
                    pic,
                    ivThumb,
                    AutoSizeUtils.mm2px(mContext, 10f),
                    AutoSizeUtils.mm2px(mContext, SEARCH_POSTER_WIDTH.toFloat()),
                    AutoSizeUtils.mm2px(mContext, SEARCH_POSTER_HEIGHT.toFloat()),
                    item.name
                )
            } else {
                ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name))
            }
        }
    }
}
