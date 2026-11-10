package tv.danmaku.ijk.media.player.ffmpeg

import androidx.annotation.Keep

@Keep
class FFmpegApi {
    companion object {
        @JvmStatic
        external fun av_base64_encode(input: ByteArray?): String?
    }
}
