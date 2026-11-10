package tv.danmaku.ijk.media.player.misc

import android.annotation.TargetApi
import android.os.Build
import android.text.TextUtils
import tv.danmaku.ijk.media.player.IjkMediaMeta
import java.util.HashMap
import java.util.Locale

class IjkMediaFormat(val mMediaFormat: IjkMediaMeta.IjkStreamMeta?) : IMediaFormat {

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    override fun getInteger(name: String?): Int {
        if (mMediaFormat == null || name == null) return 0
        return mMediaFormat.getInt(name)
    }

    override fun getString(name: String?): String? {
        if (mMediaFormat == null || name == null) return null

        if (sFormatterMap.containsKey(name)) {
            val formatter = sFormatterMap[name]
            return formatter?.format(this)
        }

        return mMediaFormat.getString(name)
    }

    private abstract class Formatter {
        fun format(mediaFormat: IjkMediaFormat): String {
            val value = doFormat(mediaFormat)
            return if (TextUtils.isEmpty(value)) defaultString else value!!
        }

        protected abstract fun doFormat(mediaFormat: IjkMediaFormat): String?

        protected open val defaultString: String
            get() = "N/A"
    }

    companion object {
        const val KEY_IJK_CODEC_LONG_NAME_UI = "ijk-codec-long-name-ui"
        const val KEY_IJK_CODEC_NAME_UI = "ijk-codec-name-ui"
        const val KEY_IJK_BIT_RATE_UI = "ijk-bit-rate-ui"
        const val KEY_IJK_CODEC_PROFILE_LEVEL_UI = "ijk-profile-level-ui"
        const val KEY_IJK_CODEC_PIXEL_FORMAT_UI = "ijk-pixel-format-ui"
        const val KEY_IJK_RESOLUTION_UI = "ijk-resolution-ui"
        const val KEY_IJK_FRAME_RATE_UI = "ijk-frame-rate-ui"
        const val KEY_IJK_SAMPLE_RATE_UI = "ijk-sample-rate-ui"
        const val KEY_IJK_CHANNEL_UI = "ijk-channel-ui"
        const val CODEC_NAME_H264 = "h264"

        private val sFormatterMap: MutableMap<String, Formatter> = HashMap()

        init {
            sFormatterMap[KEY_IJK_CODEC_LONG_NAME_UI] = object : Formatter() {
                override fun doFormat(mediaFormat: IjkMediaFormat): String? {
                    return mediaFormat.mMediaFormat?.getString(IjkMediaMeta.IJKM_KEY_CODEC_LONG_NAME)
                }
            }
            sFormatterMap[KEY_IJK_CODEC_NAME_UI] = object : Formatter() {
                override fun doFormat(mediaFormat: IjkMediaFormat): String? {
                    return mediaFormat.mMediaFormat?.getString(IjkMediaMeta.IJKM_KEY_CODEC_NAME)
                }
            }
            sFormatterMap[KEY_IJK_BIT_RATE_UI] = object : Formatter() {
                override fun doFormat(mediaFormat: IjkMediaFormat): String? {
                    val bitRate = mediaFormat.getInteger(IjkMediaMeta.IJKM_KEY_BITRATE)
                    return when {
                        bitRate <= 0 -> null
                        bitRate < 1000 -> String.format(Locale.US, "%d bit/s", bitRate)
                        else -> String.format(Locale.US, "%d kb/s", bitRate / 1000)
                    }
                }
            }
            sFormatterMap[KEY_IJK_CODEC_PROFILE_LEVEL_UI] = object : Formatter() {
                override fun doFormat(mediaFormat: IjkMediaFormat): String? {
                    val profileIndex = mediaFormat.getInteger(IjkMediaMeta.IJKM_KEY_CODEC_PROFILE_ID)
                    val profile = when (profileIndex) {
                        IjkMediaMeta.FF_PROFILE_H264_BASELINE -> "Baseline"
                        IjkMediaMeta.FF_PROFILE_H264_CONSTRAINED_BASELINE -> "Constrained Baseline"
                        IjkMediaMeta.FF_PROFILE_H264_MAIN -> "Main"
                        IjkMediaMeta.FF_PROFILE_H264_EXTENDED -> "Extended"
                        IjkMediaMeta.FF_PROFILE_H264_HIGH -> "High"
                        IjkMediaMeta.FF_PROFILE_H264_HIGH_10 -> "High 10"
                        IjkMediaMeta.FF_PROFILE_H264_HIGH_10_INTRA -> "High 10 Intra"
                        IjkMediaMeta.FF_PROFILE_H264_HIGH_422 -> "High 4:2:2"
                        IjkMediaMeta.FF_PROFILE_H264_HIGH_422_INTRA -> "High 4:2:2 Intra"
                        IjkMediaMeta.FF_PROFILE_H264_HIGH_444 -> "High 4:4:4"
                        IjkMediaMeta.FF_PROFILE_H264_HIGH_444_PREDICTIVE -> "High 4:4:4 Predictive"
                        IjkMediaMeta.FF_PROFILE_H264_HIGH_444_INTRA -> "High 4:4:4 Intra"
                        IjkMediaMeta.FF_PROFILE_H264_CAVLC_444 -> "CAVLC 4:4:4"
                        else -> return null
                    }

                    val sb = StringBuilder()
                    sb.append(profile)

                    val codecName = mediaFormat.getString(IjkMediaMeta.IJKM_KEY_CODEC_NAME)
                    if (!TextUtils.isEmpty(codecName) && CODEC_NAME_H264.equals(codecName, ignoreCase = true)) {
                        val level = mediaFormat.getInteger(IjkMediaMeta.IJKM_KEY_CODEC_LEVEL)
                        if (level < 10) return sb.toString()

                        sb.append(" Profile Level ")
                        sb.append((level / 10) % 10)
                        if ((level % 10) != 0) {
                            sb.append(".")
                            sb.append(level % 10)
                        }
                    }

                    return sb.toString()
                }
            }
            sFormatterMap[KEY_IJK_CODEC_PIXEL_FORMAT_UI] = object : Formatter() {
                override fun doFormat(mediaFormat: IjkMediaFormat): String? {
                    return mediaFormat.getString(IjkMediaMeta.IJKM_KEY_CODEC_PIXEL_FORMAT)
                }
            }
            sFormatterMap[KEY_IJK_RESOLUTION_UI] = object : Formatter() {
                override fun doFormat(mediaFormat: IjkMediaFormat): String? {
                    val width = mediaFormat.getInteger(IMediaFormat.KEY_WIDTH)
                    val height = mediaFormat.getInteger(IMediaFormat.KEY_HEIGHT)
                    val sarNum = mediaFormat.getInteger(IjkMediaMeta.IJKM_KEY_SAR_NUM)
                    val sarDen = mediaFormat.getInteger(IjkMediaMeta.IJKM_KEY_SAR_DEN)

                    return when {
                        width <= 0 || height <= 0 -> null
                        sarNum <= 0 || sarDen <= 0 -> String.format(Locale.US, "%d x %d", width, height)
                        else -> String.format(Locale.US, "%d x %d [SAR %d:%d]", width, height, sarNum, sarDen)
                    }
                }
            }
            sFormatterMap[KEY_IJK_FRAME_RATE_UI] = object : Formatter() {
                override fun doFormat(mediaFormat: IjkMediaFormat): String? {
                    val fpsNum = mediaFormat.getInteger(IjkMediaMeta.IJKM_KEY_FPS_NUM)
                    val fpsDen = mediaFormat.getInteger(IjkMediaMeta.IJKM_KEY_FPS_DEN)
                    return if (fpsNum <= 0 || fpsDen <= 0) {
                        null
                    } else {
                        (fpsNum.toFloat() / fpsDen).toString()
                    }
                }
            }
            sFormatterMap[KEY_IJK_SAMPLE_RATE_UI] = object : Formatter() {
                override fun doFormat(mediaFormat: IjkMediaFormat): String? {
                    val sampleRate = mediaFormat.getInteger(IjkMediaMeta.IJKM_KEY_SAMPLE_RATE)
                    return if (sampleRate <= 0) {
                        null
                    } else {
                        String.format(Locale.US, "%d Hz", sampleRate)
                    }
                }
            }
            sFormatterMap[KEY_IJK_CHANNEL_UI] = object : Formatter() {
                override fun doFormat(mediaFormat: IjkMediaFormat): String? {
                    val channelLayout = mediaFormat.getInteger(IjkMediaMeta.IJKM_KEY_CHANNEL_LAYOUT)
                    return when {
                        channelLayout <= 0 -> null
                        channelLayout.toLong() == IjkMediaMeta.AV_CH_LAYOUT_MONO -> "mono"
                        channelLayout.toLong() == IjkMediaMeta.AV_CH_LAYOUT_STEREO -> "stereo"
                        else -> String.format(Locale.US, "%x", channelLayout)
                    }
                }
            }
        }
    }
}
