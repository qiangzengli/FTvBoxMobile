package com.github.tvbox.osc.player

import java.util.ArrayList

class TrackInfo {
    private val audio: MutableList<TrackInfoBean> = ArrayList()
    private val video: MutableList<TrackInfoBean> = ArrayList()
    private val subtitle: MutableList<TrackInfoBean> = ArrayList()

    fun getAudio(): MutableList<TrackInfoBean> {
        return audio
    }

    fun getAudioSelected(track: Boolean): Int {
        return getSelected(audio, track)
    }

    fun getSubtitleSelected(track: Boolean): Int {
        return getSelected(subtitle, track)
    }

    fun getSelected(list: List<TrackInfoBean>, track: Boolean): Int {
        var i = 0
        for (trackInfoBean in list) {
            if (trackInfoBean.selected) return if (track) trackInfoBean.trackId else i
            i++
        }
        return 99999
    }

    fun addAudio(audio: TrackInfoBean) {
        this.audio.add(audio)
    }

    fun getVideo(): MutableList<TrackInfoBean> {
        return video
    }

    fun getVideoSelected(track: Boolean): Int {
        return getSelected(video, track)
    }

    fun addVideo(video: TrackInfoBean) {
        this.video.add(video)
    }

    fun getSubtitle(): MutableList<TrackInfoBean> {
        return subtitle
    }

    fun addSubtitle(subtitle: TrackInfoBean) {
        this.subtitle.add(subtitle)
    }
}
