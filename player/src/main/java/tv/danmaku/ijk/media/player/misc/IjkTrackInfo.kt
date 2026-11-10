package tv.danmaku.ijk.media.player.misc

import android.text.TextUtils
import tv.danmaku.ijk.media.player.IjkMediaMeta

class IjkTrackInfo(private var mStreamMeta: IjkMediaMeta.IjkStreamMeta?) : ITrackInfo {
    private var mTrackType = ITrackInfo.MEDIA_TRACK_TYPE_UNKNOWN

    fun setMediaMeta(streamMeta: IjkMediaMeta.IjkStreamMeta?) {
        mStreamMeta = streamMeta
    }

    override fun getFormat(): IMediaFormat {
        return IjkMediaFormat(mStreamMeta)
    }

    override fun getLanguage(): String {
        return if (mStreamMeta == null || TextUtils.isEmpty(mStreamMeta?.mLanguage)) {
            "und"
        } else {
            mStreamMeta?.mLanguage ?: "und"
        }
    }

    override fun getTrackType(): Int {
        return mTrackType
    }

    fun setTrackType(trackType: Int) {
        mTrackType = trackType
    }

    override fun toString(): String {
        return javaClass.simpleName + '{' + getInfoInline() + "}"
    }

    override fun getInfoInline(): String {
        val out = java.lang.StringBuilder(128)
        when (mTrackType) {
            ITrackInfo.MEDIA_TRACK_TYPE_VIDEO -> {
                out.append("VIDEO")
                out.append(", ")
                out.append(mStreamMeta?.getCodecShortNameInline())
                out.append(", ")
                out.append(mStreamMeta?.getBitrateInline())
                out.append(", ")
                out.append(mStreamMeta?.getResolutionInline())
            }
            ITrackInfo.MEDIA_TRACK_TYPE_AUDIO -> {
                out.append("AUDIO")
                out.append(", ")
                out.append(mStreamMeta?.getCodecShortNameInline())
                out.append(", ")
                out.append(mStreamMeta?.getBitrateInline())
                out.append(", ")
                out.append(mStreamMeta?.getSampleRateInline())
            }
            ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT -> {
                out.append("TIMEDTEXT")
                out.append(", ")
                out.append(mStreamMeta?.mLanguage)
            }
            ITrackInfo.MEDIA_TRACK_TYPE_SUBTITLE -> {
                out.append("SUBTITLE")
            }
            else -> {
                out.append("UNKNOWN")
            }
        }
        return out.toString()
    }
}
