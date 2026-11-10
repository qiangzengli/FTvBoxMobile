package tv.danmaku.ijk.media.player

import android.os.Bundle
import android.text.TextUtils
import java.util.ArrayList
import java.util.Locale

@Suppress("SameParameterValue")
class IjkMediaMeta {
    @JvmField
    var mMediaMeta: Bundle? = null

    @JvmField
    var mFormat: String? = null

    @JvmField
    var mDurationUS: Long = 0

    @JvmField
    var mStartUS: Long = 0

    @JvmField
    var mBitrate: Long = 0

    @JvmField
    val mStreams: ArrayList<IjkStreamMeta> = ArrayList()

    @JvmField
    var mVideoStream: IjkStreamMeta? = null

    @JvmField
    var mAudioStream: IjkStreamMeta? = null

    fun getString(key: String?): String? {
        return mMediaMeta?.getString(key)
    }

    fun getInt(key: String?): Int {
        return getInt(key, 0)
    }

    fun getInt(key: String?, defaultValue: Int): Int {
        val value = getString(key)
        if (TextUtils.isEmpty(value)) return defaultValue
        return try {
            value!!.toInt()
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }

    fun getLong(key: String?): Long {
        return getLong(key, 0)
    }

    fun getLong(key: String?, defaultValue: Long): Long {
        val value = getString(key)
        if (TextUtils.isEmpty(value)) return defaultValue
        return try {
            value!!.toLong()
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }

    fun getParcelableArrayList(key: String?): ArrayList<Bundle>? {
        return mMediaMeta?.getParcelableArrayList(key)
    }

    fun getDurationInline(): String {
        val duration = mDurationUS + 5000
        var secs = duration / 1000000
        var mins = secs / 60
        secs %= 60
        val hours = mins / 60
        mins %= 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    }

    class IjkStreamMeta(val mIndex: Int) {
        @JvmField
        var mMeta: Bundle? = null

        @JvmField
        var mType: String? = null

        @JvmField
        var mLanguage: String? = null

        // common
        @JvmField
        var mCodecName: String? = null

        @JvmField
        var mCodecProfile: String? = null

        @JvmField
        var mCodecLongName: String? = null

        @JvmField
        var mBitrate: Long = 0

        // video
        @JvmField
        var mWidth = 0

        @JvmField
        var mHeight = 0

        @JvmField
        var mFpsNum = 0

        @JvmField
        var mFpsDen = 0

        @JvmField
        var mTbrNum = 0

        @JvmField
        var mTbrDen = 0

        @JvmField
        var mSarNum = 0

        @JvmField
        var mSarDen = 0

        // audio
        @JvmField
        var mSampleRate = 0

        @JvmField
        var mChannelLayout: Long = 0

        fun getString(key: String?): String? {
            return mMeta?.getString(key)
        }

        fun getInt(key: String?): Int {
            return getInt(key, 0)
        }

        fun getInt(key: String?, defaultValue: Int): Int {
            val value = getString(key)
            if (TextUtils.isEmpty(value)) return defaultValue
            return try {
                value!!.toInt()
            } catch (e: NumberFormatException) {
                defaultValue
            }
        }

        fun getLong(key: String?): Long {
            return getLong(key, 0)
        }

        fun getLong(key: String?, defaultValue: Long): Long {
            val value = getString(key)
            if (TextUtils.isEmpty(value)) return defaultValue
            return try {
                value!!.toLong()
            } catch (e: NumberFormatException) {
                defaultValue
            }
        }

        fun getCodecLongNameInline(): String {
            return if (!TextUtils.isEmpty(mCodecLongName)) {
                mCodecLongName!!
            } else if (!TextUtils.isEmpty(mCodecName)) {
                mCodecName!!
            } else {
                "N/A"
            }
        }

        fun getCodecShortNameInline(): String {
            return if (!TextUtils.isEmpty(mCodecName)) {
                mCodecName!!
            } else {
                "N/A"
            }
        }

        fun getResolutionInline(): String {
            return if (mWidth <= 0 || mHeight <= 0) {
                "N/A"
            } else if (mSarNum <= 0 || mSarDen <= 0) {
                String.format(Locale.US, "%d x %d", mWidth, mHeight)
            } else {
                String.format(Locale.US, "%d x %d [SAR %d:%d]", mWidth, mHeight, mSarNum, mSarDen)
            }
        }

        fun getFpsInline(): String {
            return if (mFpsNum <= 0 || mFpsDen <= 0) {
                "N/A"
            } else {
                (mFpsNum.toFloat() / mFpsDen).toString()
            }
        }

        fun getBitrateInline(): String {
            return if (mBitrate <= 0) {
                "N/A"
            } else if (mBitrate < 1000) {
                String.format(Locale.US, "%d bit/s", mBitrate)
            } else {
                String.format(Locale.US, "%d kb/s", mBitrate / 1000)
            }
        }

        fun getSampleRateInline(): String {
            return if (mSampleRate <= 0) {
                "N/A"
            } else {
                String.format(Locale.US, "%d Hz", mSampleRate)
            }
        }

        fun getChannelLayoutInline(): String {
            return if (mChannelLayout <= 0) {
                "N/A"
            } else {
                if (mChannelLayout == AV_CH_LAYOUT_MONO) {
                    "mono"
                } else if (mChannelLayout == AV_CH_LAYOUT_STEREO) {
                    "stereo"
                } else {
                    String.format(Locale.US, "%x", mChannelLayout)
                }
            }
        }
    }

    companion object {
        // media meta
        const val IJKM_KEY_FORMAT = "format"
        const val IJKM_KEY_DURATION_US = "duration_us"
        const val IJKM_KEY_START_US = "start_us"
        const val IJKM_KEY_BITRATE = "bitrate"
        const val IJKM_KEY_VIDEO_STREAM = "video"
        const val IJKM_KEY_AUDIO_STREAM = "audio"
        const val IJKM_KEY_TIMEDTEXT_STREAM = "timedtext"

        // stream meta
        const val IJKM_KEY_TYPE = "type"
        const val IJKM_VAL_TYPE__VIDEO = "video"
        const val IJKM_VAL_TYPE__AUDIO = "audio"
        const val IJKM_VAL_TYPE__TIMEDTEXT = "timedtext"
        const val IJKM_VAL_TYPE__UNKNOWN = "unknown"
        const val IJKM_KEY_LANGUAGE = "language"

        const val IJKM_KEY_CODEC_NAME = "codec_name"
        const val IJKM_KEY_CODEC_PROFILE = "codec_profile"
        const val IJKM_KEY_CODEC_LEVEL = "codec_level"
        const val IJKM_KEY_CODEC_LONG_NAME = "codec_long_name"
        const val IJKM_KEY_CODEC_PIXEL_FORMAT = "codec_pixel_format"
        const val IJKM_KEY_CODEC_PROFILE_ID = "codec_profile_id"

        // stream: video
        const val IJKM_KEY_WIDTH = "width"
        const val IJKM_KEY_HEIGHT = "height"
        const val IJKM_KEY_FPS_NUM = "fps_num"
        const val IJKM_KEY_FPS_DEN = "fps_den"
        const val IJKM_KEY_TBR_NUM = "tbr_num"
        const val IJKM_KEY_TBR_DEN = "tbr_den"
        const val IJKM_KEY_SAR_NUM = "sar_num"
        const val IJKM_KEY_SAR_DEN = "sar_den"

        // stream: audio
        const val IJKM_KEY_SAMPLE_RATE = "sample_rate"
        const val IJKM_KEY_CHANNEL_LAYOUT = "channel_layout"

        const val IJKM_KEY_STREAMS = "streams"

        const val AV_CH_FRONT_LEFT: Long = 0x00000001
        const val AV_CH_FRONT_RIGHT: Long = 0x00000002
        const val AV_CH_FRONT_CENTER: Long = 0x00000004
        const val AV_CH_LOW_FREQUENCY: Long = 0x00000008
        const val AV_CH_BACK_LEFT: Long = 0x00000010
        const val AV_CH_BACK_RIGHT: Long = 0x00000020
        const val AV_CH_FRONT_LEFT_OF_CENTER: Long = 0x00000040
        const val AV_CH_FRONT_RIGHT_OF_CENTER: Long = 0x00000080
        const val AV_CH_BACK_CENTER: Long = 0x00000100
        const val AV_CH_SIDE_LEFT: Long = 0x00000200
        const val AV_CH_SIDE_RIGHT: Long = 0x00000400
        const val AV_CH_TOP_CENTER: Long = 0x00000800
        const val AV_CH_TOP_FRONT_LEFT: Long = 0x00001000
        const val AV_CH_TOP_FRONT_CENTER: Long = 0x00002000
        const val AV_CH_TOP_FRONT_RIGHT: Long = 0x00004000
        const val AV_CH_TOP_BACK_LEFT: Long = 0x00008000
        const val AV_CH_TOP_BACK_CENTER: Long = 0x00010000
        const val AV_CH_TOP_BACK_RIGHT: Long = 0x00020000
        const val AV_CH_STEREO_LEFT: Long = 0x20000000
        const val AV_CH_STEREO_RIGHT: Long = 0x40000000
        const val AV_CH_WIDE_LEFT: Long = 0x0000000080000000L
        const val AV_CH_WIDE_RIGHT: Long = 0x0000000100000000L
        const val AV_CH_SURROUND_DIRECT_LEFT: Long = 0x0000000200000000L
        const val AV_CH_SURROUND_DIRECT_RIGHT: Long = 0x0000000400000000L
        const val AV_CH_LOW_FREQUENCY_2: Long = 0x0000000800000000L

        const val AV_CH_LAYOUT_MONO = AV_CH_FRONT_CENTER
        const val AV_CH_LAYOUT_STEREO = AV_CH_FRONT_LEFT or AV_CH_FRONT_RIGHT
        const val AV_CH_LAYOUT_2POINT1 = AV_CH_LAYOUT_STEREO or AV_CH_LOW_FREQUENCY
        const val AV_CH_LAYOUT_2_1 = AV_CH_LAYOUT_STEREO or AV_CH_BACK_CENTER
        const val AV_CH_LAYOUT_SURROUND = AV_CH_LAYOUT_STEREO or AV_CH_FRONT_CENTER
        const val AV_CH_LAYOUT_3POINT1 = AV_CH_LAYOUT_SURROUND or AV_CH_LOW_FREQUENCY
        const val AV_CH_LAYOUT_4POINT0 = AV_CH_LAYOUT_SURROUND or AV_CH_BACK_CENTER
        const val AV_CH_LAYOUT_4POINT1 = AV_CH_LAYOUT_4POINT0 or AV_CH_LOW_FREQUENCY
        const val AV_CH_LAYOUT_2_2 = AV_CH_LAYOUT_STEREO or AV_CH_SIDE_LEFT or AV_CH_SIDE_RIGHT
        const val AV_CH_LAYOUT_QUAD = AV_CH_LAYOUT_STEREO or AV_CH_BACK_LEFT or AV_CH_BACK_RIGHT
        const val AV_CH_LAYOUT_5POINT0 = AV_CH_LAYOUT_SURROUND or AV_CH_SIDE_LEFT or AV_CH_SIDE_RIGHT
        const val AV_CH_LAYOUT_5POINT1 = AV_CH_LAYOUT_5POINT0 or AV_CH_LOW_FREQUENCY
        const val AV_CH_LAYOUT_5POINT0_BACK = AV_CH_LAYOUT_SURROUND or AV_CH_BACK_LEFT or AV_CH_BACK_RIGHT
        const val AV_CH_LAYOUT_5POINT1_BACK = AV_CH_LAYOUT_5POINT0_BACK or AV_CH_LOW_FREQUENCY
        const val AV_CH_LAYOUT_6POINT0 = AV_CH_LAYOUT_5POINT0 or AV_CH_BACK_CENTER
        const val AV_CH_LAYOUT_6POINT0_FRONT = AV_CH_LAYOUT_2_2 or AV_CH_FRONT_LEFT_OF_CENTER or AV_CH_FRONT_RIGHT_OF_CENTER
        const val AV_CH_LAYOUT_HEXAGONAL = AV_CH_LAYOUT_5POINT0_BACK or AV_CH_BACK_CENTER
        const val AV_CH_LAYOUT_6POINT1 = AV_CH_LAYOUT_5POINT1 or AV_CH_BACK_CENTER
        const val AV_CH_LAYOUT_6POINT1_BACK = AV_CH_LAYOUT_5POINT1_BACK or AV_CH_BACK_CENTER
        const val AV_CH_LAYOUT_6POINT1_FRONT = AV_CH_LAYOUT_6POINT0_FRONT or AV_CH_LOW_FREQUENCY
        const val AV_CH_LAYOUT_7POINT0 = AV_CH_LAYOUT_5POINT0 or AV_CH_BACK_LEFT or AV_CH_BACK_RIGHT
        const val AV_CH_LAYOUT_7POINT0_FRONT = AV_CH_LAYOUT_5POINT0 or AV_CH_FRONT_LEFT_OF_CENTER or AV_CH_FRONT_RIGHT_OF_CENTER
        const val AV_CH_LAYOUT_7POINT1 = AV_CH_LAYOUT_5POINT1 or AV_CH_BACK_LEFT or AV_CH_BACK_RIGHT
        const val AV_CH_LAYOUT_7POINT1_WIDE = AV_CH_LAYOUT_5POINT1 or AV_CH_FRONT_LEFT_OF_CENTER or AV_CH_FRONT_RIGHT_OF_CENTER
        const val AV_CH_LAYOUT_7POINT1_WIDE_BACK = AV_CH_LAYOUT_5POINT1_BACK or AV_CH_FRONT_LEFT_OF_CENTER or AV_CH_FRONT_RIGHT_OF_CENTER
        const val AV_CH_LAYOUT_OCTAGONAL = AV_CH_LAYOUT_5POINT0 or AV_CH_BACK_LEFT or AV_CH_BACK_CENTER or AV_CH_BACK_RIGHT
        const val AV_CH_LAYOUT_STEREO_DOWNMIX = AV_CH_STEREO_LEFT or AV_CH_STEREO_RIGHT

        const val FF_PROFILE_H264_CONSTRAINED = (1 shl 9) // 8+1; constraint_set1_flag
        const val FF_PROFILE_H264_INTRA = (1 shl 11) // 8+3; constraint_set3_flag

        const val FF_PROFILE_H264_BASELINE = 66
        const val FF_PROFILE_H264_CONSTRAINED_BASELINE = (66 or FF_PROFILE_H264_CONSTRAINED)
        const val FF_PROFILE_H264_MAIN = 77
        const val FF_PROFILE_H264_EXTENDED = 88
        const val FF_PROFILE_H264_HIGH = 100
        const val FF_PROFILE_H264_HIGH_10 = 110
        const val FF_PROFILE_H264_HIGH_10_INTRA = (110 or FF_PROFILE_H264_INTRA)
        const val FF_PROFILE_H264_HIGH_422 = 122
        const val FF_PROFILE_H264_HIGH_422_INTRA = (122 or FF_PROFILE_H264_INTRA)
        const val FF_PROFILE_H264_HIGH_444 = 144
        const val FF_PROFILE_H264_HIGH_444_PREDICTIVE = 244
        const val FF_PROFILE_H264_HIGH_444_INTRA = (244 or FF_PROFILE_H264_INTRA)
        const val FF_PROFILE_H264_CAVLC_444 = 44

        @JvmStatic
        fun parse(mediaMeta: Bundle?): IjkMediaMeta? {
            if (mediaMeta == null) return null

            val meta = IjkMediaMeta()
            meta.mMediaMeta = mediaMeta
            meta.mFormat = meta.getString(IJKM_KEY_FORMAT)
            meta.mDurationUS = meta.getLong(IJKM_KEY_DURATION_US)
            meta.mStartUS = meta.getLong(IJKM_KEY_START_US)
            meta.mBitrate = meta.getLong(IJKM_KEY_BITRATE)

            val videoStreamIndex = meta.getInt(IJKM_KEY_VIDEO_STREAM, -1)
            val audioStreamIndex = meta.getInt(IJKM_KEY_AUDIO_STREAM, -1)

            val streams = meta.getParcelableArrayList(IJKM_KEY_STREAMS) ?: return meta

            var index = -1
            for (streamBundle in streams) {
                index++
                if (streamBundle == null) continue

                val streamMeta = IjkStreamMeta(index)
                streamMeta.mMeta = streamBundle
                streamMeta.mType = streamMeta.getString(IJKM_KEY_TYPE)
                streamMeta.mLanguage = streamMeta.getString(IJKM_KEY_LANGUAGE)
                if (TextUtils.isEmpty(streamMeta.mType)) continue

                streamMeta.mCodecName = streamMeta.getString(IJKM_KEY_CODEC_NAME)
                streamMeta.mCodecProfile = streamMeta.getString(IJKM_KEY_CODEC_PROFILE)
                streamMeta.mCodecLongName = streamMeta.getString(IJKM_KEY_CODEC_LONG_NAME)
                streamMeta.mBitrate = streamMeta.getInt(IJKM_KEY_BITRATE).toLong()

                if (IJKM_VAL_TYPE__VIDEO.equals(streamMeta.mType, ignoreCase = true)) {
                    streamMeta.mWidth = streamMeta.getInt(IJKM_KEY_WIDTH)
                    streamMeta.mHeight = streamMeta.getInt(IJKM_KEY_HEIGHT)
                    streamMeta.mFpsNum = streamMeta.getInt(IJKM_KEY_FPS_NUM)
                    streamMeta.mFpsDen = streamMeta.getInt(IJKM_KEY_FPS_DEN)
                    streamMeta.mTbrNum = streamMeta.getInt(IJKM_KEY_TBR_NUM)
                    streamMeta.mTbrDen = streamMeta.getInt(IJKM_KEY_TBR_DEN)
                    streamMeta.mSarNum = streamMeta.getInt(IJKM_KEY_SAR_NUM)
                    streamMeta.mSarDen = streamMeta.getInt(IJKM_KEY_SAR_DEN)

                    if (videoStreamIndex == index) {
                        meta.mVideoStream = streamMeta
                    }
                } else if (IJKM_VAL_TYPE__AUDIO.equals(streamMeta.mType, ignoreCase = true)) {
                    streamMeta.mSampleRate = streamMeta.getInt(IJKM_KEY_SAMPLE_RATE)
                    streamMeta.mChannelLayout = streamMeta.getLong(IJKM_KEY_CHANNEL_LAYOUT)

                    if (audioStreamIndex == index) {
                        meta.mAudioStream = streamMeta
                    }
                }
                meta.mStreams.add(streamMeta)
            }
            return meta
        }
    }
}
