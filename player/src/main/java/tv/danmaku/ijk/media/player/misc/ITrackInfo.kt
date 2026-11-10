package tv.danmaku.ijk.media.player.misc

interface ITrackInfo {
    fun getFormat(): IMediaFormat?
    fun getLanguage(): String?
    fun getTrackType(): Int
    fun getInfoInline(): String?

    companion object {
        const val MEDIA_TRACK_TYPE_AUDIO = 2
        const val MEDIA_TRACK_TYPE_METADATA = 5
        const val MEDIA_TRACK_TYPE_SUBTITLE = 4
        const val MEDIA_TRACK_TYPE_TIMEDTEXT = 3
        const val MEDIA_TRACK_TYPE_UNKNOWN = 0
        const val MEDIA_TRACK_TYPE_VIDEO = 1
    }
}
