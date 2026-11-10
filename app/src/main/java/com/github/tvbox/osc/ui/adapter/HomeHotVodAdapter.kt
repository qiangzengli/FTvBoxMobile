package com.github.tvbox.osc.ui.adapter

import android.content.res.Configuration
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.fbox.mobile.MobileSheets
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.ImgUtil
import com.orhanobut.hawk.Hawk
import me.jessyan.autosize.utils.AutoSizeUtils
import java.util.ArrayList

class HomeHotVodAdapter(private val style: ImgUtil.Style?, private var tvRateValue: String?) :
    BaseQuickAdapter<Movie.Video, BaseViewHolder>(R.layout.item_user_hot_vod, ArrayList()) {

    private var defaultWidth: Int = 0
    private var phoneListMode = false

    init {
        if (style != null) {
            this.defaultWidth = ImgUtil.getStyleDefaultWidth(style)
        }
    }

    fun setPhoneListMode(value: Boolean) {
        if (phoneListMode == value) return
        phoneListMode = value
        notifyDataSetChanged()
    }

    override fun convert(helper: BaseViewHolder, item: Movie.Video?) {
        if (item == null) return
        val displayName = com.fbox.mobile.auth.MobileConfigAuth.title(item)
        val displayNote = com.fbox.mobile.auth.MobileConfigAuth.note(item)
        val tvDel = helper.getView<FrameLayout>(R.id.delFrameLayout)
        tvDel.visibility = if (HawkConfig.hotVodDelete) View.VISIBLE else View.GONE

        val tvRate = helper.getView<TextView>(R.id.tvRate)
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2) {
            val bean = ApiConfig.get().getSource(item.sourceKey)
            tvRateValue = bean?.name ?: ""
        }
        tvRate.text = tvRateValue ?: ""

        val tvNote = helper.getView<TextView>(R.id.tvNote)
        if (displayNote.isNullOrEmpty()) {
            tvNote.visibility = View.GONE
        } else {
            tvNote.text = displayNote
            tvNote.visibility = View.VISIBLE
        }
        helper.setText(R.id.tvName, displayName)
        val ivThumb = helper.getView<ImageView>(R.id.ivThumb)

        var newWidth = ImgUtil.defaultWidth
        var newHeight = ImgUtil.defaultHeight
        if (style != null) {
            newWidth = defaultWidth
            newHeight = (newWidth / style.ratio).toInt()
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

        val root = helper.itemView as LinearLayout
        val container = ivThumb.parent as ViewGroup
        val imageParams = container.layoutParams as LinearLayout.LayoutParams
        val title = helper.getView<TextView>(R.id.tvName)
        val titleParams = title.layoutParams as LinearLayout.LayoutParams
        root.orientation = if (phoneListMode) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_VERTICAL
        val padding = if (phoneListMode) MobileSheets.dp(mContext, 10f) else 0
        root.setPadding(padding, padding, padding, padding)
        imageParams.width = if (phoneListMode) MobileSheets.dp(mContext, 80f) else ViewGroup.LayoutParams.MATCH_PARENT
        imageParams.height = MobileSheets.dp(mContext, if (phoneListMode) 112f else 148f)
        container.layoutParams = imageParams
        titleParams.width = if (phoneListMode) 0 else ViewGroup.LayoutParams.MATCH_PARENT
        titleParams.height = if (phoneListMode) ViewGroup.LayoutParams.WRAP_CONTENT else MobileSheets.dp(mContext, 48f)
        titleParams.weight = if (phoneListMode) 1f else 0f
        titleParams.leftMargin = if (phoneListMode) MobileSheets.dp(mContext, 12f) else 0
        title.layoutParams = titleParams
        title.textSize = if (phoneListMode) 16f else 14f
        if (!phoneListMode) applyStyleToImage(ivThumb)
    }

    private fun applyStyleToImage(ivThumb: ImageView) {
        if (mContext.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            val container = ivThumb.parent as ViewGroup
            val params = container.layoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            container.layoutParams = params
            if (style != null) {
                container.post {
                    if (!phoneListMode && container.width > 0) {
                        val measured = container.layoutParams
                        measured.height = (container.width / Math.max(0.2f, style.ratio)).toInt()
                        container.layoutParams = measured
                    }
                }
            }
            return
        }
        if (style != null) {
            val container = ivThumb.parent as ViewGroup
            val width = defaultWidth
            val height = (width / style.ratio).toInt()
            val containerParams = container.layoutParams
            containerParams.height = AutoSizeUtils.mm2px(mContext, height.toFloat())
            containerParams.width = AutoSizeUtils.mm2px(mContext, width.toFloat())
            container.layoutParams = containerParams
        }
    }
}
