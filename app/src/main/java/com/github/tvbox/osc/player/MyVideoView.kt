package com.github.tvbox.osc.player

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.github.tvbox.osc.util.ImgUtil
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.ui.widget.DanmakuView
import xyz.doikki.videoplayer.player.AbstractPlayer
import xyz.doikki.videoplayer.player.VideoView

class MyVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VideoView<AbstractPlayer>(context, attrs, defStyleAttr), DrawHandler.Callback {

    fun interface DanmuSeekListener {
        fun onDanmuSeek(position: Long)
    }

    private var danmuView: DanmakuView? = null
    private var artworkView: ImageView? = null
    private var danmuSeekListener: DanmuSeekListener? = null

    fun getMediaPlayer(): AbstractPlayer? {
        return mMediaPlayer
    }

    fun setArtwork(url: String?) {
        if (TextUtils.isEmpty(url)) {
            clearArtwork()
            return
        }
        if (artworkView == null) {
            artworkView = ImageView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                scaleType = ImageView.ScaleType.FIT_CENTER
                isClickable = false
                isFocusable = false
            }
            val index = if (mRenderView == null) 0 else Math.min(1, mPlayerContainer.childCount)
            mPlayerContainer.addView(
                artworkView,
                index,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }
        artworkView?.visibility = View.VISIBLE
        artworkView?.let { ImgUtil.load(url, it, 0, 0, 0, "", ImageView.ScaleType.FIT_CENTER) }
    }

    fun clearArtwork() {
        artworkView?.visibility = View.GONE
        artworkView?.setImageDrawable(null)
    }

    fun getPlaybackPosition(): Long {
        return mCurrentPosition
    }

    override fun seekTo(pos: Long) {
        super.seekTo(pos)
        val listener = danmuSeekListener
        if (listener != null) {
            listener.onDanmuSeek(pos)
        } else if (haveDanmu()) {
            danmuView?.seekTo(pos)
        }
    }

    override fun resume() {
        super.resume()
        if (haveDanmu()) danmuView?.resume()
    }

    override fun start() {
        super.start()
        if (haveDanmu()) danmuView?.resume()
    }

    override fun pause() {
        super.pause()
        if (haveDanmu()) danmuView?.pause()
    }

    override fun release() {
        super.release()
        if (haveDanmu()) danmuView?.release()
    }

    private fun haveDanmu(): Boolean {
        return danmuView != null && danmuView!!.isPrepared
    }

    fun setDanmuView(view: DanmakuView?) {
        danmuView = view
        if (danmuView != null) danmuView!!.setCallback(this)
    }

    fun getDanmuView(): DanmakuView? {
        return danmuView
    }

    fun setDanmuSeekListener(listener: DanmuSeekListener?) {
        danmuSeekListener = listener
    }

    override fun prepared() {
        post {
            val dView = danmuView ?: return@post
            if (isPlaying() && dView.isPrepared) {
                dView.start(getCurrentPosition())
            }
        }
    }

    override fun updateTimer(timer: DanmakuTimer?) {
    }

    override fun danmakuShown(danmaku: BaseDanmaku?) {
    }

    override fun drawingFinished() {
    }
}
