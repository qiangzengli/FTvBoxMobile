package tv.danmaku.ijk.media.player.misc

interface IMediaFormat {
    fun getString(name: String?): String?
    fun getInteger(name: String?): Int

    companion object {
        const val KEY_MIME = "mime"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
    }
}
