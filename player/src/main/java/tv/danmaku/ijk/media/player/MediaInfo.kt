package tv.danmaku.ijk.media.player

class MediaInfo {
    @JvmField
    var mMediaPlayerName: String? = null

    @JvmField
    var mVideoDecoder: String? = null

    @JvmField
    var mVideoDecoderImpl: String? = null

    @JvmField
    var mAudioDecoder: String? = null

    @JvmField
    var mAudioDecoderImpl: String? = null

    @JvmField
    var mMeta: IjkMediaMeta? = null
}
