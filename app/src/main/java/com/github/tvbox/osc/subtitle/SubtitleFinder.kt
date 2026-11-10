package com.github.tvbox.osc.subtitle

import com.github.tvbox.osc.subtitle.model.Subtitle

object SubtitleFinder {

    @JvmStatic
    fun find(position: Long, subtitles: List<Subtitle>?): Subtitle? {
        if (subtitles.isNullOrEmpty()) {
            return null
        }
        var start = 0
        var end = subtitles.size - 1
        while (start <= end) {
            val middle = (start + end) / 2
            val middleSubtitle = subtitles[middle]
            if (position < middleSubtitle.start.mseconds) {
                if (position > middleSubtitle.end.mseconds) {
                    return middleSubtitle
                }
                end = middle - 1
            } else if (position > middleSubtitle.end.mseconds) {
                if (position < middleSubtitle.start.mseconds) {
                    return middleSubtitle
                }
                start = middle + 1
            } else if (position >= middleSubtitle.start.mseconds &&
                position <= middleSubtitle.end.mseconds
            ) {
                return middleSubtitle
            }
        }
        return null
    }
}
