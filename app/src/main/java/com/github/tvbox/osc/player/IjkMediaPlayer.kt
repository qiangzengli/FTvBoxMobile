package com.github.tvbox.osc.player

import android.content.Context
import android.text.TextUtils
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.IJKCode
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.util.AudioTrackMemory
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.MD5
import com.orhanobut.hawk.Hawk
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaMeta
import tv.danmaku.ijk.media.player.misc.ITrackInfo
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo
import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper
import xyz.doikki.videoplayer.ijk.IjkPlayer
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.LinkedHashMap
import tv.danmaku.ijk.media.player.IjkMediaPlayer as RawIjkMediaPlayer

class IjkMediaPlayer(context: Context, private var codec: IJKCode? = null) : IjkPlayer(context) {

    protected var currentPlayPath: String? = null

    init {
        memory = AudioTrackMemory.getInstance(context)
    }

    override fun setOptions() {
        super.setOptions()
        val player = mMediaPlayer ?: return
        val codecTmp = if (this.codec == null) ApiConfig.get().getCurrentIJKCode() else this.codec
        val options = codecTmp?.getOption()
        if (options != null) {
            for (key in options.keys) {
                val value = options[key]
                val opt = key.split("\\|".toRegex()).toTypedArray()
                val category = opt[0].trim().toInt()
                val name = opt[1].trim()
                try {
                    val valLong = value!!.toLong()
                    player.setOption(category, name, valLong)
                } catch (e: Exception) {
                    player.setOption(category, name, value)
                }
            }
        }
        player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps", 30)

        // 开启内置字幕
        player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_PLAYER, "subtitle", 1)
        player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1)
        player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", -1)
        player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "safe", 0)

        if (Hawk.get(HawkConfig.PLAYER_IS_LIVE, false)) {
            LOG.i("echo-type-直播")
            player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 300)
            player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1)
            player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 1)
            player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_CODEC, "threads", "1")
        } else {
            LOG.i("echo-type-点播")
            // 降低延迟
            player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 3000)
            player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "infbuf", 0)
            player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_CODEC, "threads", "2")
        }
    }

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        val player = mMediaPlayer
        var finalPath = path
        try {
            when (getStreamType(finalPath)) {
                RTSP_UDP_RTP -> {
                    player?.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "infbuf", 1)
                    player?.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp")
                    player?.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_flags", "prefer_tcp")
                    player?.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", (512 * 1000).toLong())
                    player?.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", (2 * 1000 * 1000).toLong())
                }

                CACHE_VIDEO -> {
                    if (Hawk.get(HawkConfig.IJK_CACHE_PLAY, false)) {
                        val cachePath = FileUtils.getCachePath() + "/ijkcaches/"
                        val cacheFile = File(cachePath)
                        if (!cacheFile.exists()) cacheFile.mkdirs()
                        val tmpMd5 = MD5.string2MD5(finalPath)
                        val cacheFilePath = cachePath + tmpMd5 + ".file"
                        val cacheMapPath = cachePath + tmpMd5 + ".map"

                        player?.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "cache_file_path", cacheFilePath)
                        player?.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "cache_map_path", cacheMapPath)
                        player?.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "parse_cache_map", 1)
                        player?.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "auto_save_map", 1)
                        player?.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "cache_max_capacity", (60 * 1024 * 1024).toLong())
                        finalPath = "ijkio:cache:ffio:$finalPath"
                    }
                }

                M3U8 -> {
                    // 直播且是ijk的时候自动走代理解决DNS
                    if (Hawk.get(HawkConfig.PLAYER_IS_LIVE, false)) {
                        val uri = URI(finalPath)
                        val host = uri.host
                        if (ITV_TARGET_DOMAIN.equals(host, ignoreCase = true)) {
                            finalPath = ControlManager.get().getAddress(true) + "proxy?go=live&type=m3u8&url=" + URLEncoder.encode(finalPath, "UTF-8")
                        }
                    }
                }

                else -> {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setDataSourceHeader(headers)
        player?.setOption(
            RawIjkMediaPlayer.OPT_CATEGORY_FORMAT,
            "protocol_whitelist",
            "ijkio,ffio,async,cache,crypto,file,dash,http,https,ijkhttphook,ijkinject,ijklivehook,ijklongurl,ijksegment,ijktcphook,pipe,rtp,tcp,tls,udp,ijkurlhook,data"
        )
        currentPlayPath = finalPath
        super.setDataSource(finalPath, null)
    }

    private fun getStreamType(path: String?): Int {
        if (TextUtils.isEmpty(path)) {
            return OTHER
        }
        val lowerPath = path!!.lowercase()
        if (lowerPath.startsWith("rtsp://") || lowerPath.startsWith("udp://") || lowerPath.startsWith("rtp://")) {
            return RTSP_UDP_RTP
        }
        val cleanUrl = path.split("\\?".toRegex()).toTypedArray()[0]
        if (cleanUrl.endsWith(".m3u8")) {
            return M3U8
        }
        if (cleanUrl.endsWith(".mp4") || cleanUrl.endsWith(".mkv") || cleanUrl.endsWith(".avi")) {
            return CACHE_VIDEO
        }
        return OTHER
    }

    private fun setDataSourceHeader(headers: Map<String, String>?) {
        val player = mMediaPlayer ?: return
        val playHeaders = LinkedHashMap<String, String>()
        var userAgent: String? = null
        var hasAccept = false
        if (headers != null && headers.isNotEmpty()) {
            for ((key, value) in headers) {
                if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) {
                    continue
                }
                if (ExoMediaSourceHelper.HEADER_FORMAT.equals(key, ignoreCase = true)) {
                    continue
                }
                if ("User-Agent".equals(key, ignoreCase = true)) {
                    userAgent = value.trim()
                } else {
                    if ("Accept".equals(key, ignoreCase = true)) {
                        hasAccept = true
                    }
                    playHeaders[key] = value.trim()
                }
            }
        }
        if (TextUtils.isEmpty(userAgent)) {
            userAgent = DEFAULT_USER_AGENT
        }
        if (!hasAccept) {
            playHeaders["Accept"] = DEFAULT_ACCEPT
        }
        if (!TextUtils.isEmpty(userAgent)) {
            player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", userAgent)
        }
        if (playHeaders.isNotEmpty()) {
            val sb = StringBuilder()
            for ((k, v) in playHeaders) {
                sb.append(k)
                sb.append(": ")
                sb.append(v)
                sb.append("\r\n")
            }
            player.setOption(RawIjkMediaPlayer.OPT_CATEGORY_FORMAT, "headers", sb.toString())
        }
    }

    fun getTrackInfo(): TrackInfo? {
        val player = mMediaPlayer ?: return null
        val trackInfo = player.getTrackInfo() ?: return null
        val data = TrackInfo()
        val subtitleSelected = player.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT)
        val audioSelected = player.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO)
        val videoSelected = player.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_VIDEO)
        var index = 0
        for (info in trackInfo) {
            if (info.getTrackType() == ITrackInfo.MEDIA_TRACK_TYPE_VIDEO) {
                if (isAttachedPicture(info)) {
                    LOG.i("echo-ijk-skip-attached-picture:" + info.getInfoInline())
                    index++
                    continue
                }
                val v = TrackInfoBean()
                val name = processVideoName(info.getInfoInline())
                val language = getFriendlyLanguage(info.getLanguage(), info.getInfoInline())
                v.language = language
                v.name = buildDisplayName("视轨", data.getVideo().size + 1, language, name)
                v.trackId = index
                v.index = index
                v.selected = index == videoSelected
                data.addVideo(v)
            } else if (info.getTrackType() == ITrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                val a = TrackInfoBean()
                val name = processAudioName(info.getInfoInline())
                var lang = info.getLanguage()
                if (name.startsWith("aac")) lang = "中文"
                a.language = lang
                a.name = name
                val language = getFriendlyLanguage(a.language, info.getInfoInline())
                a.language = language
                a.name = buildDisplayName("音轨", data.getAudio().size + 1, language, name)
                a.trackId = index
                a.index = index
                a.selected = index == audioSelected
                data.addAudio(a)
            } else if (info.getTrackType() == ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {
                if (!isTextSubtitle(info.getInfoInline())) {
                    LOG.i("echo-ijk-skip-bitmap-subtitle:" + info.getInfoInline())
                    index++
                    continue
                }
                val t = TrackInfoBean()
                t.name = info.getInfoInline()
                t.language = info.getLanguage()
                val language = getFriendlyLanguage(t.language, t.name)
                t.language = language
                t.name = buildDisplayName("字幕", data.getSubtitle().size + 1, language, "")
                t.trackId = index
                t.index = index
                t.selected = index == subtitleSelected
                data.addSubtitle(t)
            }
            index++
        }
        return data
    }

    private fun processAudioName(rawName: String?): String {
        if (rawName == null) return ""
        return rawName.replace("AUDIO,", "")
            .replace("N/A,", "")
            .replace(" ", "")
            .replace("^,+|,+$".toRegex(), "")
            .replace(",", " / ")
    }

    private fun processVideoName(rawName: String?): String {
        if (rawName == null) return ""
        return rawName.replace("VIDEO,", "")
            .replace("N/A,", "")
            .replace(" ", "")
            .replace("^,+|,+$".toRegex(), "")
            .replace(",", " / ")
    }

    private fun isAttachedPicture(info: IjkTrackInfo): Boolean {
        val format = info.getFormat() ?: return false
        val codecName = format.getString(IjkMediaMeta.IJKM_KEY_CODEC_NAME)
        return "mjpeg".equals(codecName, ignoreCase = true)
                && format.getInteger(IjkMediaMeta.IJKM_KEY_BITRATE) <= 0
                && format.getInteger(IjkMediaMeta.IJKM_KEY_FPS_NUM) <= 0
    }

    private fun isTextSubtitle(rawName: String?): Boolean {
        val value = rawName?.lowercase() ?: ""
        return !value.contains("pgs")
                && !value.contains("hdmv")
                && !value.contains("dvd subtitle")
                && !value.contains("dvd_subtitle")
                && !value.contains("dvb subtitle")
                && !value.contains("dvb_subtitle")
                && !value.contains("xsub")
                && !value.contains("vobsub")
                && !value.contains("bitmap")
    }

    private fun getFriendlyLanguage(language: String?, rawInfo: String?): String {
        val text = ((if (language == null) "" else language) + " " + if (rawInfo == null) "" else rawInfo).lowercase()
        if (text.contains("yue") || text.contains("cantonese") || text.contains("粤") || text.contains("广东")) {
            return "粤语"
        }
        if (text.contains("zh") || text.contains("chi") || text.contains("zho") || text.contains("chs")
            || text.contains("cht") || text.contains("cmn") || text.contains("中")
            || text.contains("国语") || text.contains("普通话")
        ) {
            return "国语"
        }
        if (text.contains("en") || text.contains("eng") || text.contains("english") || text.contains("英")) {
            return "英语"
        }
        if (text.contains("ja") || text.contains("jpn") || text.contains("japanese") || text.contains("日")) {
            return "日语"
        }
        if (text.contains("ko") || text.contains("kor") || text.contains("korean") || text.contains("韩")) {
            return "韩语"
        }
        if (text.contains("tha") || text.contains("thai") || text.contains("th")) {
            return "泰语"
        }
        return ""
    }

    private fun buildDisplayName(prefix: String, number: Int, language: String?, detail: String?): String {
        val builder = StringBuilder(prefix).append(number)
        if (!language.isNullOrEmpty()) {
            builder.append(" - ").append(language)
        }
        if (!detail.isNullOrEmpty()) {
            builder.append(" ").append(detail)
        }
        return builder.toString()
    }

    fun setTrack(trackIndex: Int) {
        val player = mMediaPlayer ?: return
        val audioSelected = player.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO)
        val subtitleSelected = player.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT)
        if (trackIndex != audioSelected && trackIndex != subtitleSelected) {
            player.selectTrack(trackIndex)
        }
    }

    fun setTrack(trackIndex: Int, playKey: String) {
        val player = mMediaPlayer ?: return
        val audioSelected = player.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO)
        if (trackIndex != audioSelected) {
            if (playKey.isNotEmpty()) {
                memory?.save(playKey, trackIndex)
            }
            player.selectTrack(trackIndex)
        }
    }

    fun setOnTimedTextListener(listener: IMediaPlayer.OnTimedTextListener?) {
        mMediaPlayer?.setOnTimedTextListener(listener)
    }

    fun loadDefaultTrack(trackInfo: TrackInfo?, playKey: String) {
        if (trackInfo != null && trackInfo.getAudio().size > 1) {
            val trackIndex = memory?.ijkLoad(playKey) ?: -1
            if (trackIndex == -1) {
                val firstIndex = trackInfo.getAudio()[0].index
                setTrack(firstIndex)
                return
            }
            setTrack(trackIndex)
        }
    }

    companion object {
        private var memory: AudioTrackMemory? = null
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
        private const val DEFAULT_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/json;q=0.9"
        private const val ITV_TARGET_DOMAIN = "gslbserv.itv.cmvideo.cn"

        private const val RTSP_UDP_RTP = 1
        private const val CACHE_VIDEO = 2
        private const val M3U8 = 3
        private const val OTHER = 0
    }
}
