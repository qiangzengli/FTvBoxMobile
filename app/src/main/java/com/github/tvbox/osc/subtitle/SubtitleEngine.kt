package com.github.tvbox.osc.subtitle

import com.github.tvbox.osc.subtitle.model.Subtitle
import xyz.doikki.videoplayer.player.AbstractPlayer

interface SubtitleEngine {

    fun setSubtitlePath(path: String)

    fun setSubtitleDelay(milliseconds: Int?)

    fun setPlaySubtitleCacheKey(cacheKey: String)

    fun getPlaySubtitleCacheKey(): String?

    fun start()

    fun pause()

    fun resume()

    fun stop()

    fun reset()

    fun destroy()

    fun bindToMediaPlayer(mediaPlayer: AbstractPlayer)

    fun setOnSubtitlePreparedListener(listener: OnSubtitlePreparedListener?)

    fun setOnSubtitleChangeListener(listener: OnSubtitleChangeListener?)

    interface OnSubtitlePreparedListener {
        fun onSubtitlePrepared(subtitles: List<Subtitle>?)
    }

    interface OnSubtitleChangeListener {
        fun onSubtitleChanged(subtitle: Subtitle?)
    }
}
