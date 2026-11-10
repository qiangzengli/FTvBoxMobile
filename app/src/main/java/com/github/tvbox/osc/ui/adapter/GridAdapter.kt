package com.github.tvbox.osc.ui.adapter

import android.content.res.Configuration
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.util.ImgUtil
import me.jessyan.autosize.utils.AutoSizeUtils
import java.util.ArrayList

class GridAdapter(showList: Boolean, @JvmField var style: ImgUtil.Style?) :
    BaseQuickAdapter<Movie.Video, BaseViewHolder>(
        if (showList || (style != null && style?.type == "list")) R.layout.item_list else R.layout.item_grid,
        ArrayList()
    ) {
    private var mShowList: Boolean = showList
    private var defaultWidth: Int = 0

    init {
        val s = style
        if (s != null) {
            if (s.type == "list") this.mShowList = true
            this.defaultWidth = ImgUtil.getStyleDefaultWidth(s)
        }
    }

    override fun convert(helper: BaseViewHolder, item: Movie.Video?) {
        if (item == null) return
        val displayName = com.fbox.mobile.auth.MobileConfigAuth.title(item)
        val displayNote = com.fbox.mobile.auth.MobileConfigAuth.note(item)
        if (this.mShowList) {
            helper.setText(R.id.tvNote, displayNote)
            helper.setText(R.id.tvName, displayName)
            val ivThumb = helper.getView<ImageView>(R.id.ivThumb)
            val pic = item.pic?.trim() ?: ""
            if (!TextUtils.isEmpty(pic)) {
                if (ImgUtil.isBase64Image(pic)) {
                    ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic))
                } else {
                    ImgUtil.load(pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10f), AutoSizeUtils.mm2px(mContext, 240f), AutoSizeUtils.mm2px(mContext, 336f), displayName)
                }
            } else {
                ivThumb.setImageDrawable(ImgUtil.createTextDrawable(displayName))
            }
            return
        }

        val tvYear = helper.getView<TextView>(R.id.tvYear)
        if (item.year <= 0) {
            tvYear.visibility = View.GONE
        } else {
            tvYear.text = item.year.toString()
            tvYear.visibility = View.VISIBLE
        }
        val tvLang = helper.getView<TextView>(R.id.tvLang)
        tvLang.visibility = View.GONE
        val tvArea = helper.getView<TextView>(R.id.tvArea)
        tvArea.visibility = View.GONE
        if (TextUtils.isEmpty(displayNote)) {
            helper.setVisible(R.id.tvNote, false)
        } else {
            helper.setVisible(R.id.tvNote, true)
            helper.setText(R.id.tvNote, displayNote)
        }
        helper.setText(R.id.tvName, displayName)
        helper.setText(R.id.tvActor, item.actor ?: "")
        val ivThumb = helper.getView<ImageView>(R.id.ivThumb)
        var newWidth = ImgUtil.defaultWidth
        var newHeight = ImgUtil.defaultHeight
        val curStyle = style
        if (curStyle != null) {
            newWidth = defaultWidth
            newHeight = (newWidth / curStyle.ratio).toInt()
        }
        val pic = item.pic?.trim() ?: ""
        if (!TextUtils.isEmpty(pic)) {
            if (ImgUtil.isBase64Image(pic)) {
                ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic))
            } else {
                ImgUtil.load(pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10f), AutoSizeUtils.mm2px(mContext, newWidth.toFloat()), AutoSizeUtils.mm2px(mContext, newHeight.toFloat()), displayName)
            }
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(displayName))
        }
        applyStyleToImage(ivThumb)
    }

    private fun applyStyleToImage(ivThumb: ImageView) {
        val curStyle = style
        if (mContext.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            val container = ivThumb.parent as ViewGroup
            val params = container.layoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            container.layoutParams = params
            if (curStyle != null) {
                container.post {
                    if (container.width > 0) {
                        val measured = container.layoutParams
                        measured.height = (container.width / Math.max(0.2f, curStyle.ratio)).toInt()
                        container.layoutParams = measured
                    }
                }
            }
            return
        }
        if (curStyle != null) {
            val container = ivThumb.parent as ViewGroup
            val width = defaultWidth
            val height = (width / curStyle.ratio).toInt()
            val containerParams = container.layoutParams
            containerParams.height = AutoSizeUtils.mm2px(mContext, height.toFloat())
            containerParams.width = AutoSizeUtils.mm2px(mContext, width.toFloat())
            container.layoutParams = containerParams
        }
    }
}
