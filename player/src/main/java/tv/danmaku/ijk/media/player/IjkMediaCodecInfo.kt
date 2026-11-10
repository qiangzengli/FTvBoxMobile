package tv.danmaku.ijk.media.player

import android.annotation.TargetApi
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.os.Build
import android.text.TextUtils
import android.util.Log
import java.util.Locale
import java.util.TreeMap

class IjkMediaCodecInfo {
    @JvmField
    var mCodecInfo: MediaCodecInfo? = null

    @JvmField
    var mRank = 0

    @JvmField
    var mMimeType: String? = null

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    fun dumpProfileLevels(mimeType: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return

        try {
            val caps = mCodecInfo?.getCapabilitiesForType(mimeType)
            var maxProfile = 0
            var maxLevel = 0
            if (caps?.profileLevels != null) {
                for (profileLevel in caps.profileLevels) {
                    if (profileLevel == null) continue
                    maxProfile = maxOf(maxProfile, profileLevel.profile)
                    maxLevel = maxOf(maxLevel, profileLevel.level)
                }
            }
            Log.i(TAG, String.format(Locale.US, "%s", getProfileLevelName(maxProfile, maxLevel)))
        } catch (e: Throwable) {
            Log.i(TAG, "profile-level: exception")
        }
    }

    companion object {
        private const val TAG = "IjkMediaCodecInfo"

        const val RANK_MAX = 1000
        const val RANK_TESTED = 800
        const val RANK_ACCEPTABLE = 700
        const val RANK_LAST_CHANCE = 600
        const val RANK_SECURE = 300
        const val RANK_SOFTWARE = 200
        const val RANK_NON_STANDARD = 100
        const val RANK_NO_SENSE = 0

        private var sKnownCodecList: MutableMap<String, Int>? = null

        @Synchronized
        private fun getKnownCodecList(): MutableMap<String, Int> {
            sKnownCodecList?.let { return it }

            val list = TreeMap<String, Int>(String.CASE_INSENSITIVE_ORDER)
            // ----- Nvidia -----
            list["OMX.Nvidia.h264.decode"] = RANK_TESTED
            list["OMX.Nvidia.h264.decode.secure"] = RANK_SECURE

            // ----- Intel -----
            list["OMX.Intel.hw_vd.h264"] = RANK_TESTED + 1
            list["OMX.Intel.VideoDecoder.AVC"] = RANK_TESTED

            // ----- Qualcomm -----
            list["OMX.qcom.video.decoder.avc"] = RANK_TESTED
            list["OMX.ittiam.video.decoder.avc"] = RANK_NO_SENSE

            // ----- Samsung -----
            list["OMX.SEC.avc.dec"] = RANK_TESTED
            list["OMX.SEC.AVC.Decoder"] = RANK_TESTED - 1
            list["OMX.SEC.avcdec"] = RANK_TESTED - 2
            list["OMX.SEC.avc.sw.dec"] = RANK_SOFTWARE
            list["OMX.Exynos.avc.dec"] = RANK_TESTED
            list["OMX.Exynos.AVC.Decoder"] = RANK_TESTED - 1

            // ------ Huawei hisilicon ------
            list["OMX.k3.video.decoder.avc"] = RANK_TESTED
            list["OMX.IMG.MSVDX.Decoder.AVC"] = RANK_TESTED

            // ----- TI -----
            list["OMX.TI.DUCATI1.VIDEO.DECODER"] = RANK_TESTED

            // ------ RockChip ------
            list["OMX.rk.video_decoder.avc"] = RANK_TESTED

            // ------ AMLogic -----
            list["OMX.amlogic.avc.decoder.awesome"] = RANK_TESTED

            // ------ Marvell ------
            list["OMX.MARVELL.VIDEO.HW.CODA7542DECODER"] = RANK_TESTED
            list["OMX.MARVELL.VIDEO.H264DECODER"] = RANK_SOFTWARE

            // ----- TODO: need test -----
            list.remove("OMX.Action.Video.Decoder")
            list.remove("OMX.allwinner.video.decoder.avc")
            list.remove("OMX.BRCM.vc4.decoder.avc")
            list.remove("OMX.brcm.video.h264.hw.decoder")
            list.remove("OMX.brcm.video.h264.decoder")
            list.remove("OMX.cosmo.video.decoder.avc")
            list.remove("OMX.duos.h264.decoder")
            list.remove("OMX.hantro.81x0.video.decoder")
            list.remove("OMX.hantro.G1.video.decoder")
            list.remove("OMX.hisi.video.decoder")
            list.remove("OMX.LG.decoder.video.avc")
            list.remove("OMX.MS.AVC.Decoder")
            list.remove("OMX.RENESAS.VIDEO.DECODER.H264")
            list.remove("OMX.RTK.video.decoder")
            list.remove("OMX.sprd.h264.decoder")
            list.remove("OMX.ST.VFM.H264Dec")
            list.remove("OMX.vpu.video_decoder.avc")
            list.remove("OMX.WMT.decoder.avc")
            list.remove("OMX.bluestacks.hw.decoder")

            // ---------------
            // Useless codec
            // ----- google -----
            list["OMX.google.h264.decoder"] = RANK_SOFTWARE
            list["OMX.google.h264.lc.decoder"] = RANK_SOFTWARE
            // ----- huawei k920 -----
            list["OMX.k3.ffmpeg.decoder"] = RANK_SOFTWARE
            list["OMX.ffmpeg.video.decoder"] = RANK_SOFTWARE
            // ----- unknown -----
            list["OMX.sprd.soft.h264.decoder"] = RANK_SOFTWARE

            sKnownCodecList = list
            return list
        }

        @JvmStatic
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        fun setupCandidate(codecInfo: MediaCodecInfo?, mimeType: String?): IjkMediaCodecInfo? {
            if (codecInfo == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return null

            var name = codecInfo.name
            if (TextUtils.isEmpty(name)) return null

            name = name.lowercase(Locale.US)
            var rank = RANK_NO_SENSE
            if (!name.startsWith("omx.")) {
                rank = RANK_NON_STANDARD
            } else if (name.startsWith("omx.pv")) {
                rank = RANK_SOFTWARE
            } else if (name.startsWith("omx.google.")) {
                rank = RANK_SOFTWARE
            } else if (name.startsWith("omx.ffmpeg.")) {
                rank = RANK_SOFTWARE
            } else if (name.startsWith("omx.k3.ffmpeg.")) {
                rank = RANK_SOFTWARE
            } else if (name.startsWith("omx.avcodec.")) {
                rank = RANK_SOFTWARE
            } else if (name.startsWith("omx.ittiam.")) {
                rank = RANK_NO_SENSE
            } else if (name.startsWith("omx.mtk.")) {
                rank = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    RANK_NO_SENSE
                } else {
                    RANK_TESTED
                }
            } else {
                val knownRank = getKnownCodecList()[name]
                if (knownRank != null) {
                    rank = knownRank
                } else {
                    rank = try {
                        val cap = codecInfo.getCapabilitiesForType(mimeType)
                        if (cap != null) RANK_ACCEPTABLE else RANK_LAST_CHANCE
                    } catch (e: Throwable) {
                        RANK_LAST_CHANCE
                    }
                }
            }

            val candidate = IjkMediaCodecInfo()
            candidate.mCodecInfo = codecInfo
            candidate.mRank = rank
            candidate.mMimeType = mimeType
            return candidate
        }

        @JvmStatic
        fun getProfileLevelName(profile: Int, level: Int): String {
            return String.format(Locale.US, " %s Profile Level %s (%d,%d)", getProfileName(profile), getLevelName(level), profile, level)
        }

        @JvmStatic
        fun getProfileName(profile: Int): String {
            return when (profile) {
                CodecProfileLevel.AVCProfileBaseline -> "Baseline"
                CodecProfileLevel.AVCProfileMain -> "Main"
                CodecProfileLevel.AVCProfileExtended -> "Extends"
                CodecProfileLevel.AVCProfileHigh -> "High"
                CodecProfileLevel.AVCProfileHigh10 -> "High10"
                CodecProfileLevel.AVCProfileHigh422 -> "High422"
                CodecProfileLevel.AVCProfileHigh444 -> "High444"
                else -> "Unknown"
            }
        }

        @JvmStatic
        fun getLevelName(level: Int): String {
            return when (level) {
                CodecProfileLevel.AVCLevel1 -> "1"
                CodecProfileLevel.AVCLevel1b -> "1b"
                CodecProfileLevel.AVCLevel11 -> "11"
                CodecProfileLevel.AVCLevel12 -> "12"
                CodecProfileLevel.AVCLevel13 -> "13"
                CodecProfileLevel.AVCLevel2 -> "2"
                CodecProfileLevel.AVCLevel21 -> "21"
                CodecProfileLevel.AVCLevel22 -> "22"
                CodecProfileLevel.AVCLevel3 -> "3"
                CodecProfileLevel.AVCLevel31 -> "31"
                CodecProfileLevel.AVCLevel32 -> "32"
                CodecProfileLevel.AVCLevel4 -> "4"
                CodecProfileLevel.AVCLevel41 -> "41"
                CodecProfileLevel.AVCLevel42 -> "42"
                CodecProfileLevel.AVCLevel5 -> "5"
                CodecProfileLevel.AVCLevel51 -> "51"
                65536 -> "52"
                else -> "0"
            }
        }
    }
}
