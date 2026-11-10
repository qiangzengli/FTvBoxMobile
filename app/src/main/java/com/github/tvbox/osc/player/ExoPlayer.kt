package com.github.tvbox.osc.player

import android.content.Context
import android.os.Looper
import android.util.Pair
import com.github.tvbox.osc.util.AudioTrackMemory
import com.github.tvbox.osc.util.LOG
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Renderer
import com.google.android.exoplayer2.RenderersFactory
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.text.TextOutput
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.MappingTrackSelector
import com.google.android.exoplayer2.util.MimeTypes
import xyz.doikki.videoplayer.exo.ExoMediaPlayer
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.ArrayList
import java.util.HashMap

class ExoPlayer(context: Context) : ExoMediaPlayer(context) {

    @Volatile
    private var internalSubtitleDelayUs: Long = 0
    private var onCuesListener: OnCuesListener? = null
    private var defaultSubtitleTrackSelected = false
    private var defaultSubtitleTrackSelectionClosed = false

    init {
        setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                .build()
        )
        setRenderersFactory(buildRenderersFactory(context))
        LOG.i("echo-exo-low-memory-load-control")
        memory = AudioTrackMemory.getInstance(context)
    }

    override fun initPlayer() {
        super.initPlayer()
        mInternalPlayer?.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                loadDefaultSubtitleTrackBeforeReady()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    defaultSubtitleTrackSelectionClosed = true
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                onCuesListener?.onCues(cueGroup.cues)
            }
        })
        LOG.i("echo-exo-cues-listener-ready")
    }

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        defaultSubtitleTrackSelected = false
        defaultSubtitleTrackSelectionClosed = false
        super.setDataSource(path, headers)
    }

    private fun buildRenderersFactory(context: Context): RenderersFactory {
        val factory = SubtitleOffsetRenderersFactory(context, object : SubtitleDelayProvider {
            override fun getDelayUs(): Long {
                return internalSubtitleDelayUs
            }
        })
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        try {
            val method = DefaultRenderersFactory::class.java.getMethod("forceDisableMediaCodecAsynchronousQueueing")
            method.invoke(factory)
            LOG.i("echo-exo-disable-async-codec-queue")
        } catch (th: Throwable) {
            LOG.i("echo-exo-disable-async-codec-queue-skip:" + th.javaClass.simpleName)
        }
        return factory
    }

    fun getTrackInfo(): TrackInfo {
        val data = TrackInfo()
        val selector = trackSelector ?: return data
        val mappedInfo = selector.currentMappedTrackInfo ?: return data

        for (rendererIndex in 0 until mappedInfo.rendererCount) {
            val type = mappedInfo.getRendererType(rendererIndex)
            if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_VIDEO && type != C.TRACK_TYPE_TEXT) continue

            val groups = mappedInfo.getTrackGroups(rendererIndex)
            for (groupIndex in 0 until groups.length) {
                val group = groups.get(groupIndex)
                for (trackIndex in 0 until group.length) {
                    val fmt = group.getFormat(trackIndex)
                    if (type == C.TRACK_TYPE_TEXT && isUndeclaredClosedCaptionTrack(fmt)) continue
                    val language = getLanguage(fmt)
                    val detail = if (type == C.TRACK_TYPE_VIDEO) getVideoName(fmt) else getName(fmt)
                    val bean = TrackInfoBean().apply {
                        this.language = language
                        this.name = buildDisplayName(
                            if (type == C.TRACK_TYPE_AUDIO) "音轨" else if (type == C.TRACK_TYPE_VIDEO) "视轨" else "字幕",
                            if (type == C.TRACK_TYPE_AUDIO) data.getAudio().size + 1 else if (type == C.TRACK_TYPE_VIDEO) data.getVideo().size + 1 else data.getSubtitle().size + 1,
                            language, detail
                        )
                        this.renderId = rendererIndex
                        this.trackGroupId = groupIndex
                        this.trackId = trackIndex
                        this.groupIndex = groupIndex
                        this.index = trackIndex
                        this.selected = isCurrentTrackSelected(fmt, type)
                        this.bitmapSubtitle = type == C.TRACK_TYPE_TEXT && isBitmapSubtitle(fmt)
                    }

                    if (type == C.TRACK_TYPE_AUDIO) {
                        data.addAudio(bean)
                    } else if (type == C.TRACK_TYPE_VIDEO) {
                        data.addVideo(bean)
                    } else {
                        data.addSubtitle(bean)
                    }
                }
            }
        }
        return data
    }

    fun setTrack(groupIndex: Int, trackIndex: Int, playKey: String) {
        val selector = trackSelector ?: return
        val mappedInfo = selector.currentMappedTrackInfo
        setTrack(findAudioRendererIndex(mappedInfo), groupIndex, trackIndex, playKey)
    }

    fun setTrack(track: TrackInfoBean?, playKey: String) {
        if (track == null) return
        setTrack(track.renderId, track.trackGroupId, track.trackId, playKey)
    }

    private fun setTrack(rendererIndex: Int, groupIndex: Int, trackIndex: Int, playKey: String) {
        try {
            val selector = trackSelector ?: return
            val mappedInfo = selector.currentMappedTrackInfo
            if (mappedInfo == null) {
                LOG.i("echo-setTrack: MappedTrackInfo is null")
                return
            }
            if (rendererIndex == C.INDEX_UNSET || rendererIndex < 0 || rendererIndex >= mappedInfo.rendererCount) {
                LOG.i("echo-setTrack: No renderer found")
                return
            }

            val groups = mappedInfo.getTrackGroups(rendererIndex)
            if (!isTrackIndexValid(groups, groupIndex, trackIndex)) {
                LOG.i("echo-setTrack: Invalid track index - group:$groupIndex, track:$trackIndex")
                return
            }
            val override = DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex)
            val builder = selector.buildUponParameters()
            builder.setRendererDisabled(rendererIndex, false)
            builder.clearSelectionOverrides(rendererIndex)
            builder.setSelectionOverride(rendererIndex, groups, override)
            selector.setParameters(builder.build())

            if (mappedInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_AUDIO && playKey.isNotEmpty()) {
                memory?.save(playKey, groupIndex, trackIndex)
            }
        } catch (e: Exception) {
            LOG.i("echo-setTrack error: " + e.message)
        }
    }

    fun loadDefaultTrack(playKey: String) {
        val pair: Pair<Int, Int>? = memory?.exoLoad(playKey)
        if (pair == null) return

        val selector = trackSelector ?: return
        val mappedInfo = selector.currentMappedTrackInfo ?: return

        val audioRendererIndex = findAudioRendererIndex(mappedInfo)
        if (audioRendererIndex == C.INDEX_UNSET) return

        setTrack(audioRendererIndex, pair.first, pair.second, "")
    }

    fun loadDefaultSubtitleTrack() {
        if (defaultSubtitleTrackSelected) return
        val trackInfo = getTrackInfo()
        val subtitles = trackInfo.getSubtitle()
        if (subtitles.isEmpty()) return

        defaultSubtitleTrackSelected = true
        var target: TrackInfoBean = subtitles[0]
        for (subtitle in subtitles) {
            if ("国语" == subtitle.language) {
                target = subtitle
                break
            }
        }
        setTrack(target, "")
    }

    private fun loadDefaultSubtitleTrackBeforeReady() {
        if (defaultSubtitleTrackSelectionClosed) return
        loadDefaultSubtitleTrack()
    }

    fun setOnCuesListener(listener: OnCuesListener?) {
        onCuesListener = listener
    }

    fun setInternalSubtitleDelay(milliseconds: Int) {
        internalSubtitleDelayUs = milliseconds * 1000L
    }

    private fun findAudioRendererIndex(mappedInfo: MappingTrackSelector.MappedTrackInfo?): Int {
        if (mappedInfo == null) return C.INDEX_UNSET
        for (i in 0 until mappedInfo.rendererCount) {
            if (mappedInfo.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                return i
            }
        }
        return C.INDEX_UNSET
    }

    private fun isTrackIndexValid(groups: TrackGroupArray, groupIndex: Int, trackIndex: Int): Boolean {
        if (groupIndex < 0 || groupIndex >= groups.length) return false
        val group = groups.get(groupIndex)
        return trackIndex >= 0 && trackIndex < group.length
    }

    private fun isBitmapSubtitle(format: Format?): Boolean {
        if (format == null || format.sampleMimeType == null) return false
        val mimeType = format.sampleMimeType!!.lowercase()
        return mimeType.contains("pgs") || mimeType.contains("dvb") || mimeType.contains("vobsub")
    }

    private fun isUndeclaredClosedCaptionTrack(format: Format?): Boolean {
        if (format == null || format.accessibilityChannel != Format.NO_VALUE) return false
        return MimeTypes.APPLICATION_CEA608 == format.sampleMimeType ||
                MimeTypes.APPLICATION_CEA708 == format.sampleMimeType
    }

    private fun isCurrentTrackSelected(format: Format, trackType: Int): Boolean {
        val player = mInternalPlayer ?: return false
        val tracks = player.currentTracks
        for (group in tracks.groups) {
            if (group.type != trackType || !group.isSelected) continue
            for (i in 0 until group.length) {
                if (group.isTrackSelected(i) && isSameFormat(format, group.getTrackFormat(i))) {
                    return true
                }
            }
        }
        return false
    }

    private fun isSameFormat(a: Format?, b: Format?): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false
        if (a.id != null && b.id != null && a.id == b.id) return true
        return a == b
    }

    private fun getLanguage(fmt: Format): String {
        val language = matchLanguage(fmt.language)
        if (language.isNotEmpty()) {
            return language
        }
        return matchLanguage(
            (if (fmt.label == null) "" else fmt.label) + " " +
                    (if (fmt.id == null) "" else fmt.id) + " " +
                    if (fmt.codecs == null) "" else fmt.codecs
        )
    }

    private fun matchLanguage(text: String?): String {
        if (text == null) return ""
        val value = text.lowercase()
        val mapped = LANG_MAP[value]
        if (mapped != null) return mapped
        if (value.contains("yue") || value.contains("cantonese") || value.contains("粤") || value.contains("广东")) {
            return "粤语"
        }
        if (value.contains("zh") || value.contains("chi") || value.contains("zho") || value.contains("chs")
            || value.contains("cht") || value.contains("cmn") || value.contains("中")
            || value.contains("国语") || value.contains("普通话")
        ) {
            return "国语"
        }
        if (value.contains("en") || value.contains("eng") || value.contains("english") || value.contains("英")) {
            return "英语"
        }
        if (value.contains("ja") || value.contains("jpn") || value.contains("japanese") || value.contains("日")) {
            return "日语"
        }
        if (value.contains("ko") || value.contains("kor") || value.contains("korean") || value.contains("韩")) {
            return "韩语"
        }
        if (value.contains("tha") || value.contains("thai") || value.contains("th")) {
            return "泰语"
        }
        return ""
    }

    private fun getName(fmt: Format): String {
        val channelLabel: String = if (fmt.channelCount <= 0) {
            ""
        } else if (fmt.channelCount == 1) {
            "单声道"
        } else if (fmt.channelCount == 2) {
            "立体声"
        } else {
            fmt.channelCount.toString() + " 声道"
        }

        var codec = ""
        if (fmt.codecs != null && fmt.codecs!!.isNotEmpty()) {
            codec = fmt.codecs!!.uppercase()
        }
        if (fmt.sampleMimeType != null && fmt.sampleMimeType!!.contains("/")) {
            val mime = fmt.sampleMimeType!!.substring(fmt.sampleMimeType!!.indexOf('/') + 1)
            if (codec.isEmpty()) {
                codec = mime.uppercase()
            }
        }
        val builder = StringBuilder()
        appendPart(builder, fmt.label)
        appendPart(builder, codec)
        appendPart(builder, channelLabel)
        return builder.toString()
    }

    private fun getVideoName(fmt: Format): String {
        val builder = StringBuilder()
        appendPart(builder, fmt.label)
        if (fmt.width > 0 && fmt.height > 0) {
            appendPart(builder, fmt.width.toString() + "x" + fmt.height)
        }
        if (fmt.codecs != null && fmt.codecs!!.isNotEmpty()) {
            appendPart(builder, fmt.codecs!!.uppercase())
        } else if (fmt.sampleMimeType != null && fmt.sampleMimeType!!.contains("/")) {
            appendPart(builder, fmt.sampleMimeType!!.substring(fmt.sampleMimeType!!.indexOf('/') + 1).uppercase())
        }
        return builder.toString()
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

    private fun appendPart(builder: StringBuilder, value: String?) {
        if (value == null) return
        val part = value.trim()
        if (part.isEmpty() || "und".equals(part, ignoreCase = true) || "未知" == part) return
        if (builder.isNotEmpty()) {
            builder.append(" / ")
        }
        builder.append(part)
    }

    fun interface OnCuesListener {
        fun onCues(cues: List<Cue>)
    }

    private fun interface SubtitleDelayProvider {
        fun getDelayUs(): Long
    }

    private class SubtitleOffsetRenderersFactory(
        context: Context,
        private val subtitleDelayProvider: SubtitleDelayProvider
    ) : DefaultRenderersFactory(context) {

        override fun buildTextRenderers(
            context: Context,
            output: TextOutput,
            outputLooper: Looper,
            extensionRendererMode: Int,
            out: ArrayList<Renderer>
        ) {
            val firstRendererIndex = out.size
            super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out)
            for (i in firstRendererIndex until out.size) {
                val renderer = out[i]
                out[i] = Proxy.newProxyInstance(
                    Renderer::class.java.classLoader,
                    arrayOf<Class<*>>(Renderer::class.java),
                    SubtitleOffsetRendererHandler(renderer, subtitleDelayProvider)
                ) as Renderer
            }
        }
    }

    private class SubtitleOffsetRendererHandler(
        private val renderer: Renderer,
        private val subtitleDelayProvider: SubtitleDelayProvider
    ) : InvocationHandler {

        @Throws(Throwable::class)
        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            var invokeArgs = args
            if ("render" == method.name && args != null && args.isNotEmpty() && args[0] is Long) {
                invokeArgs = args.clone()
                val positionUs = invokeArgs[0] as Long
                invokeArgs[0] = Math.max(0, positionUs - subtitleDelayProvider.getDelayUs())
            }
            return try {
                method.invoke(renderer, *(invokeArgs ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw (e.cause ?: e)
            }
        }
    }

    companion object {
        private var memory: AudioTrackMemory? = null
        private val LANG_MAP: MutableMap<String, String> = HashMap()

        init {
            LANG_MAP["zh"] = "国语"
            LANG_MAP["zh-cn"] = "国语"
            LANG_MAP["cmn"] = "国语"
            LANG_MAP["chi"] = "国语"
            LANG_MAP["zho"] = "国语"
            LANG_MAP["chs"] = "国语"
            LANG_MAP["yue"] = "粤语"
            LANG_MAP["zh-hk"] = "粤语"
            LANG_MAP["zh-yue"] = "粤语"
            LANG_MAP["en"] = "英语"
            LANG_MAP["en-us"] = "英语"
            LANG_MAP["eng"] = "英语"
            LANG_MAP["ja"] = "日语"
            LANG_MAP["jpn"] = "日语"
            LANG_MAP["ko"] = "韩语"
            LANG_MAP["kor"] = "韩语"
            LANG_MAP["th"] = "泰语"
            LANG_MAP["tha"] = "泰语"
        }
    }
}
