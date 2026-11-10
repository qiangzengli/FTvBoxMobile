package tv.danmaku.ijk.media.player

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Rect
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import androidx.annotation.Keep
import tv.danmaku.ijk.media.player.annotations.AccessedByNative
import tv.danmaku.ijk.media.player.annotations.CalledByNative
import tv.danmaku.ijk.media.player.misc.IAndroidIO
import tv.danmaku.ijk.media.player.misc.IMediaDataSource
import tv.danmaku.ijk.media.player.misc.ITrackInfo
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo
import tv.danmaku.ijk.media.player.pragma.DebugLog
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.ref.WeakReference
import java.security.InvalidParameterException
import java.util.ArrayList
import java.util.Locale

@Keep
class IjkMediaPlayer : AbstractMediaPlayer {

    @Keep
    @AccessedByNative
    @JvmField
    var mNativeMediaPlayer: Long = 0

    @Keep
    @AccessedByNative
    @JvmField
    var mNativeMediaDataSource: Long = 0

    @Keep
    @AccessedByNative
    @JvmField
    var mNativeAndroidIO: Long = 0

    @Keep
    @AccessedByNative
    @JvmField
    var mNativeSurfaceTexture: Int = 0

    @Keep
    @AccessedByNative
    @JvmField
    var mListenerContext: Int = 0

    private var mSurfaceHolder: SurfaceHolder? = null
    private var mEventHandler: EventHandler? = null
    private var mWakeLock: PowerManager.WakeLock? = null
    private var mScreenOnWhilePlaying = false
    private var mStayAwake = false

    private var mVideoWidth = 0
    private var mVideoHeight = 0
    private var mVideoSarNum = 0
    private var mVideoSarDen = 0

    private var mDataSource: String? = null

    private var mOnControlMessageListener: OnControlMessageListener? = null
    private var mOnNativeInvokeListener: OnNativeInvokeListener? = null
    private var mOnMediaCodecSelectListener: OnMediaCodecSelectListener? = null

    constructor() : this(sLocalLibLoader)

    constructor(libLoader: IjkLibLoader?) {
        initPlayer(libLoader)
    }

    private fun initPlayer(libLoader: IjkLibLoader?) {
        loadLibrariesOnce(libLoader)
        initNativeOnce()

        val looper: Looper? = Looper.myLooper() ?: Looper.getMainLooper()
        mEventHandler = if (looper != null) EventHandler(this, looper) else null

        native_setup(WeakReference(this))
    }

    private external fun _setFrameAtTime(
        imgCachePath: String?,
        startTime: Long,
        endTime: Long,
        num: Int,
        imgDefinition: Int
    )

    private external fun _setVideoSurface(surface: Surface?)

    override fun setDisplay(sh: SurfaceHolder?) {
        mSurfaceHolder = sh
        val surface = sh?.surface
        _setVideoSurface(surface)
        updateSurfaceScreenOn()
    }

    override fun setSurface(surface: Surface?) {
        if (mScreenOnWhilePlaying && surface != null) {
            DebugLog.w(TAG, "setScreenOnWhilePlaying(true) is ineffective for Surface")
        }
        mSurfaceHolder = null
        _setVideoSurface(surface)
        updateSurfaceScreenOn()
    }

    @Throws(IOException::class, IllegalArgumentException::class, SecurityException::class, IllegalStateException::class)
    override fun setDataSource(context: Context, uri: Uri) {
        setDataSource(context, uri, null)
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Throws(IOException::class, IllegalArgumentException::class, SecurityException::class, IllegalStateException::class)
    override fun setDataSource(context: Context, uri: Uri, headers: Map<String, String>?) {
        var resolvedUri: Uri = uri
        val scheme = resolvedUri.scheme
        if (ContentResolver.SCHEME_FILE == scheme) {
            val path = resolvedUri.path
            if (path != null) {
                setDataSource(path)
            }
            return
        } else if (ContentResolver.SCHEME_CONTENT == scheme && Settings.AUTHORITY == resolvedUri.authority) {
            val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.getDefaultType(resolvedUri))
                ?: throw FileNotFoundException("Failed to resolve default ringtone")
            resolvedUri = ringtoneUri
        }

        var fd: AssetFileDescriptor? = null
        try {
            val resolver = context.contentResolver
            fd = resolver.openAssetFileDescriptor(resolvedUri, "r")
            if (fd == null) return

            if (fd.declaredLength < 0) {
                setDataSource(fd.fileDescriptor)
            } else {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.declaredLength)
            }
            return
        } catch (ignored: SecurityException) {
        } catch (ignored: IOException) {
        } finally {
            fd?.close()
        }

        Log.d(TAG, "Couldn't open file on client side, trying server side")
        setDataSource(resolvedUri.toString(), headers)
    }

    @Throws(IOException::class, IllegalArgumentException::class, SecurityException::class, IllegalStateException::class)
    override fun setDataSource(path: String) {
        mDataSource = path
        _setDataSource(path, null, null)
    }

    @Throws(IOException::class, IllegalArgumentException::class, SecurityException::class, IllegalStateException::class)
    fun setDataSource(path: String, headers: Map<String, String>?) {
        if (headers != null && headers.isNotEmpty()) {
            val sb = java.lang.StringBuilder()
            for ((key, value) in headers) {
                sb.append(key)
                sb.append(":")
                if (!TextUtils.isEmpty(value)) {
                    sb.append(value)
                }
                sb.append("\r\n")
                setOption(OPT_CATEGORY_FORMAT, "headers", sb.toString())
                setOption(
                    OPT_CATEGORY_FORMAT,
                    "protocol_whitelist",
                    "async,cache,crypto,file,dash,http,https,ijkhttphook,ijkinject,ijklivehook,ijklongurl,ijksegment,ijktcphook,pipe,rtp,tcp,tls,udp,ijkurlhook,data"
                )
            }
        }
        setDataSource(path)
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    @Throws(IOException::class, IllegalArgumentException::class, IllegalStateException::class)
    override fun setDataSource(fd: FileDescriptor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
            val nativeFd: Int
            try {
                val f = fd.javaClass.getDeclaredField("descriptor")
                f.isAccessible = true
                nativeFd = f.getInt(fd)
            } catch (e: NoSuchFieldException) {
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            }
            _setDataSourceFd(nativeFd)
        } else {
            val pfd = ParcelFileDescriptor.dup(fd)
            try {
                _setDataSourceFd(pfd.fd)
            } finally {
                pfd.close()
            }
        }
    }

    @Throws(IOException::class, IllegalArgumentException::class, IllegalStateException::class)
    private fun setDataSource(fd: FileDescriptor, offset: Long, length: Long) {
        setDataSource(fd)
    }

    @Throws(IllegalArgumentException::class, SecurityException::class, IllegalStateException::class)
    override fun setDataSource(mediaDataSource: IMediaDataSource) {
        _setDataSource(mediaDataSource)
    }

    @Throws(IllegalArgumentException::class, SecurityException::class, IllegalStateException::class)
    fun setAndroidIOCallback(androidIO: IAndroidIO) {
        _setAndroidIOCallback(androidIO)
    }

    private external fun _setDataSource(path: String?, keys: Array<String>?, values: Array<String>?)
    private external fun _setDataSourceFd(fd: Int)
    private external fun _setDataSource(mediaDataSource: IMediaDataSource?)
    private external fun _setAndroidIOCallback(androidIO: IAndroidIO?)

    override fun getDataSource(): String? = mDataSource

    @Throws(IllegalStateException::class)
    override fun prepareAsync() {
        _prepareAsync()
    }

    external fun _prepareAsync()

    @Throws(IllegalStateException::class)
    override fun start() {
        stayAwake(true)
        _start()
    }

    private external fun _start()

    @Throws(IllegalStateException::class)
    override fun stop() {
        stayAwake(false)
        _stop()
    }

    private external fun _stop()

    @Throws(IllegalStateException::class)
    override fun pause() {
        stayAwake(false)
        _pause()
    }

    private external fun _pause()

    @SuppressLint("Wakelock")
    override fun setWakeMode(context: Context, mode: Int) {
        var washeld = false
        if (mWakeLock != null) {
            if (mWakeLock?.isHeld == true) {
                washeld = true
                mWakeLock?.release()
            }
            mWakeLock = null
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = pm.newWakeLock(mode or PowerManager.ON_AFTER_RELEASE, IjkMediaPlayer::class.java.name)
        mWakeLock?.setReferenceCounted(false)
        if (washeld) {
            mWakeLock?.acquire()
        }
    }

    override fun setScreenOnWhilePlaying(screenOn: Boolean) {
        if (mScreenOnWhilePlaying != screenOn) {
            if (screenOn && mSurfaceHolder == null) {
                DebugLog.w(TAG, "setScreenOnWhilePlaying(true) is ineffective without a SurfaceHolder")
            }
            mScreenOnWhilePlaying = screenOn
            updateSurfaceScreenOn()
        }
    }

    @SuppressLint("Wakelock")
    private fun stayAwake(awake: Boolean) {
        if (mWakeLock != null) {
            if (awake && mWakeLock?.isHeld != true) {
                mWakeLock?.acquire()
            } else if (!awake && mWakeLock?.isHeld == true) {
                mWakeLock?.release()
            }
        }
        mStayAwake = awake
        updateSurfaceScreenOn()
    }

    private fun updateSurfaceScreenOn() {
        mSurfaceHolder?.setKeepScreenOn(mScreenOnWhilePlaying && mStayAwake)
    }

    override fun getTrackInfo(): Array<IjkTrackInfo>? {
        val bundle = getMediaMeta() ?: return null
        val mediaMeta = IjkMediaMeta.parse(bundle) ?: return null
        if (mediaMeta.mStreams.isEmpty()) return null

        val trackInfos = ArrayList<IjkTrackInfo>()
        for (streamMeta in mediaMeta.mStreams) {
            val trackInfo = IjkTrackInfo(streamMeta)
            if (IjkMediaMeta.IJKM_VAL_TYPE__VIDEO.equals(streamMeta.mType, ignoreCase = true)) {
                trackInfo.setTrackType(ITrackInfo.MEDIA_TRACK_TYPE_VIDEO)
            } else if (IjkMediaMeta.IJKM_VAL_TYPE__AUDIO.equals(streamMeta.mType, ignoreCase = true)) {
                trackInfo.setTrackType(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO)
            } else if (IjkMediaMeta.IJKM_VAL_TYPE__TIMEDTEXT.equals(streamMeta.mType, ignoreCase = true)) {
                trackInfo.setTrackType(ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT)
            }
            trackInfos.add(trackInfo)
        }
        return trackInfos.toTypedArray()
    }

    fun getSelectedTrack(trackType: Int): Int {
        return when (trackType) {
            ITrackInfo.MEDIA_TRACK_TYPE_VIDEO -> _getPropertyLong(FFP_PROP_INT64_SELECTED_VIDEO_STREAM, -1).toInt()
            ITrackInfo.MEDIA_TRACK_TYPE_AUDIO -> _getPropertyLong(FFP_PROP_INT64_SELECTED_AUDIO_STREAM, -1).toInt()
            ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT -> _getPropertyLong(FFP_PROP_INT64_SELECTED_TIMEDTEXT_STREAM, -1).toInt()
            else -> -1
        }
    }

    fun selectTrack(track: Int) {
        _setStreamSelected(track, true)
    }

    fun deselectTrack(track: Int) {
        _setStreamSelected(track, false)
    }

    private external fun _setStreamSelected(stream: Int, select: Boolean)

    override fun getVideoWidth(): Int = mVideoWidth

    override fun getVideoHeight(): Int = mVideoHeight

    override fun getVideoSarNum(): Int = mVideoSarNum

    override fun getVideoSarDen(): Int = mVideoSarDen

    override external fun isPlaying(): Boolean

    @Throws(IllegalStateException::class)
    override external fun seekTo(msec: Long)

    override external fun getCurrentPosition(): Long

    override external fun getDuration(): Long

    override fun release() {
        stayAwake(false)
        updateSurfaceScreenOn()
        resetListeners()
        _release()
    }

    private external fun _release()

    override fun reset() {
        stayAwake(false)
        _reset()
        mEventHandler?.removeCallbacksAndMessages(null)
        mVideoWidth = 0
        mVideoHeight = 0
    }

    private external fun _reset()

    override fun setLooping(looping: Boolean) {
        val loopCount = if (looping) 0 else 1
        setOption(OPT_CATEGORY_PLAYER, "loop", loopCount.toLong())
        _setLoopCount(loopCount)
    }

    private external fun _setLoopCount(loopCount: Int)

    override fun isLooping(): Boolean {
        val loopCount = _getLoopCount()
        return loopCount != 1
    }

    private external fun _getLoopCount(): Int

    fun setSpeed(speed: Float) {
        _setPropertyFloat(FFP_PROP_FLOAT_PLAYBACK_RATE, speed)
    }

    fun getSpeed(speed: Float): Float {
        return _getPropertyFloat(FFP_PROP_FLOAT_PLAYBACK_RATE, 0.0f)
    }

    val videoDecoder: Int
        get() = _getPropertyLong(FFP_PROP_INT64_VIDEO_DECODER, FFP_PROPV_DECODER_UNKNOWN.toLong()).toInt()

    val videoOutputFramesPerSecond: Float
        get() = _getPropertyFloat(PROP_FLOAT_VIDEO_OUTPUT_FRAMES_PER_SECOND, 0.0f)

    val videoDecodeFramesPerSecond: Float
        get() = _getPropertyFloat(PROP_FLOAT_VIDEO_DECODE_FRAMES_PER_SECOND, 0.0f)

    val videoCachedDuration: Long
        get() = _getPropertyLong(FFP_PROP_INT64_VIDEO_CACHED_DURATION, 0)

    val audioCachedDuration: Long
        get() = _getPropertyLong(FFP_PROP_INT64_AUDIO_CACHED_DURATION, 0)

    val videoCachedBytes: Long
        get() = _getPropertyLong(FFP_PROP_INT64_VIDEO_CACHED_BYTES, 0)

    val audioCachedBytes: Long
        get() = _getPropertyLong(FFP_PROP_INT64_AUDIO_CACHED_BYTES, 0)

    val videoCachedPackets: Long
        get() = _getPropertyLong(FFP_PROP_INT64_VIDEO_CACHED_PACKETS, 0)

    val audioCachedPackets: Long
        get() = _getPropertyLong(FFP_PROP_INT64_AUDIO_CACHED_PACKETS, 0)

    val asyncStatisticBufBackwards: Long
        get() = _getPropertyLong(FFP_PROP_INT64_ASYNC_STATISTIC_BUF_BACKWARDS, 0)

    val asyncStatisticBufForwards: Long
        get() = _getPropertyLong(FFP_PROP_INT64_ASYNC_STATISTIC_BUF_FORWARDS, 0)

    val asyncStatisticBufCapacity: Long
        get() = _getPropertyLong(FFP_PROP_INT64_ASYNC_STATISTIC_BUF_CAPACITY, 0)

    val trafficStatisticByteCount: Long
        get() = _getPropertyLong(FFP_PROP_INT64_TRAFFIC_STATISTIC_BYTE_COUNT, 0)

    val cacheStatisticPhysicalPos: Long
        get() = _getPropertyLong(FFP_PROP_INT64_CACHE_STATISTIC_PHYSICAL_POS, 0)

    val cacheStatisticFileForwards: Long
        get() = _getPropertyLong(FFP_PROP_INT64_CACHE_STATISTIC_FILE_FORWARDS, 0)

    val cacheStatisticFilePos: Long
        get() = _getPropertyLong(FFP_PROP_INT64_CACHE_STATISTIC_FILE_POS, 0)

    val cacheStatisticCountBytes: Long
        get() = _getPropertyLong(FFP_PROP_INT64_CACHE_STATISTIC_COUNT_BYTES, 0)

    val fileSize: Long
        get() = _getPropertyLong(FFP_PROP_INT64_LOGICAL_FILE_SIZE, 0)

    val bitRate: Long
        get() = _getPropertyLong(FFP_PROP_INT64_BIT_RATE, 0)

    val tcpSpeed: Long
        get() = _getPropertyLong(FFP_PROP_INT64_TCP_SPEED, 0)

    val seekLoadDuration: Long
        get() = _getPropertyLong(FFP_PROP_INT64_LATEST_SEEK_LOAD_DURATION, 0)

    val dropFrameRate: Float
        get() = _getPropertyFloat(FFP_PROP_FLOAT_DROP_FRAME_RATE, 0.0f)

    private external fun _getPropertyFloat(property: Int, defaultValue: Float): Float
    private external fun _setPropertyFloat(property: Int, value: Float)
    private external fun _getPropertyLong(property: Int, defaultValue: Long): Long
    private external fun _setPropertyLong(property: Int, value: Long)

    override external fun setVolume(leftVolume: Float, rightVolume: Float)
    override external fun getAudioSessionId(): Int

    override fun getMediaInfo(): MediaInfo {
        val mediaInfo = MediaInfo()
        mediaInfo.mMediaPlayerName = "ijkplayer"

        val videoCodecInfo = _getVideoCodecInfo()
        if (!TextUtils.isEmpty(videoCodecInfo)) {
            val nodes = videoCodecInfo!!.split(",").toTypedArray()
            if (nodes.size >= 2) {
                mediaInfo.mVideoDecoder = nodes[0]
                mediaInfo.mVideoDecoderImpl = nodes[1]
            } else if (nodes.size >= 1) {
                mediaInfo.mVideoDecoder = nodes[0]
                mediaInfo.mVideoDecoderImpl = ""
            }
        }

        val audioCodecInfo = _getAudioCodecInfo()
        if (!TextUtils.isEmpty(audioCodecInfo)) {
            val nodes = audioCodecInfo!!.split(",").toTypedArray()
            if (nodes.size >= 2) {
                mediaInfo.mAudioDecoder = nodes[0]
                mediaInfo.mAudioDecoderImpl = nodes[1]
            } else if (nodes.size >= 1) {
                mediaInfo.mAudioDecoder = nodes[0]
                mediaInfo.mAudioDecoderImpl = ""
            }
        }

        try {
            mediaInfo.mMeta = IjkMediaMeta.parse(_getMediaMeta())
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return mediaInfo
    }

    @Deprecated("Deprecated in Java")
    override fun setLogEnabled(enable: Boolean) {
    }

    @Deprecated("Deprecated in Java")
    override fun isPlayable(): Boolean = true

    private external fun _getVideoCodecInfo(): String?
    private external fun _getAudioCodecInfo(): String?

    fun setOption(category: Int, name: String?, value: String?) {
        _setOption(category, name, value)
    }

    fun setOption(category: Int, name: String?, value: Long) {
        _setOption(category, name, value)
    }

    private external fun _setOption(category: Int, name: String?, value: String?)
    private external fun _setOption(category: Int, name: String?, value: Long)

    fun getMediaMeta(): Bundle? = _getMediaMeta()

    private external fun _getMediaMeta(): Bundle?

    override fun setAudioStreamType(streamtype: Int) {}

    @Deprecated("Deprecated in Java")
    override fun setKeepInBackground(keepInBackground: Boolean) {}

    private external fun native_setup(IjkMediaPlayer_this: Any?)
    private external fun native_finalize()
    private external fun native_message_loop(IjkMediaPlayer_this: Any?)

    protected fun finalize() {
        native_finalize()
    }

    fun httphookReconnect() {
        _setPropertyLong(FFP_PROP_INT64_IMMEDIATE_RECONNECT, 1)
    }

    fun setCacheShare(share: Int) {
        _setPropertyLong(FFP_PROP_INT64_SHARE_CACHE_DATA, share.toLong())
    }

    fun setOnControlMessageListener(listener: OnControlMessageListener?) {
        mOnControlMessageListener = listener
    }

    fun interface OnControlMessageListener {
        fun onControlResolveSegmentUrl(segment: Int): String?
    }

    fun setOnNativeInvokeListener(listener: OnNativeInvokeListener?) {
        mOnNativeInvokeListener = listener
    }

    interface OnNativeInvokeListener {
        fun onNativeInvoke(what: Int, args: Bundle?): Boolean

        companion object {
            const val CTRL_WILL_TCP_OPEN = 0x20001
            const val CTRL_DID_TCP_OPEN = 0x20002
            const val CTRL_WILL_HTTP_OPEN = 0x20003
            const val CTRL_WILL_LIVE_OPEN = 0x20005
            const val CTRL_WILL_CONCAT_RESOLVE_SEGMENT = 0x20007

            const val EVENT_WILL_HTTP_OPEN = 0x1
            const val EVENT_DID_HTTP_OPEN = 0x2
            const val EVENT_WILL_HTTP_SEEK = 0x3
            const val EVENT_DID_HTTP_SEEK = 0x4

            const val ARG_URL = "url"
            const val ARG_SEGMENT_INDEX = "segment_index"
            const val ARG_RETRY_COUNTER = "retry_counter"

            const val ARG_ERROR = "error"
            const val ARG_FAMILIY = "family"
            const val ARG_IP = "ip"
            const val ARG_PORT = "port"
            const val ARG_FD = "fd"

            const val ARG_OFFSET = "offset"
            const val ARG_HTTP_CODE = "http_code"
            const val ARG_FILE_SIZE = "file_size"
        }
    }

    fun interface OnMediaCodecSelectListener {
        fun onMediaCodecSelect(mp: IMediaPlayer, mimeType: String?, profile: Int, level: Int): String?
    }

    fun setOnMediaCodecSelectListener(listener: OnMediaCodecSelectListener?) {
        mOnMediaCodecSelectListener = listener
    }

    override fun resetListeners() {
        super.resetListeners()
        mOnMediaCodecSelectListener = null
    }

    private class EventHandler(mp: IjkMediaPlayer, looper: Looper) : Handler(looper) {
        private val mWeakPlayer: WeakReference<IjkMediaPlayer> = WeakReference(mp)

        override fun handleMessage(msg: Message) {
            val player = mWeakPlayer.get()
            if (player == null || player.mNativeMediaPlayer == 0L) {
                DebugLog.w(TAG, "IjkMediaPlayer went away with unhandled events")
                return
            }

            when (msg.what) {
                MEDIA_PREPARED -> {
                    player.notifyOnPrepared()
                }
                MEDIA_PLAYBACK_COMPLETE -> {
                    player.stayAwake(false)
                    player.notifyOnCompletion()
                }
                MEDIA_BUFFERING_UPDATE -> {
                    var bufferPosition = msg.arg1.toLong()
                    if (bufferPosition < 0) {
                        bufferPosition = 0
                    }

                    var percent: Long = 0
                    val duration = player.getDuration()
                    if (duration > 0) {
                        percent = bufferPosition * 100 / duration
                    }
                    if (percent >= 100) {
                        percent = 100
                    }

                    player.notifyOnBufferingUpdate(percent.toInt())
                }
                MEDIA_SEEK_COMPLETE -> {
                    player.notifyOnSeekComplete()
                }
                MEDIA_SET_VIDEO_SIZE -> {
                    player.mVideoWidth = msg.arg1
                    player.mVideoHeight = msg.arg2
                    player.notifyOnVideoSizeChanged(
                        player.mVideoWidth, player.mVideoHeight,
                        player.mVideoSarNum, player.mVideoSarDen
                    )
                }
                MEDIA_ERROR -> {
                    DebugLog.e(TAG, "Error (${msg.arg1},${msg.arg2})")
                    if (!player.notifyOnError(msg.arg1, msg.arg2)) {
                        player.notifyOnCompletion()
                    }
                    player.stayAwake(false)
                }
                MEDIA_INFO -> {
                    if (msg.arg1 == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        DebugLog.i(TAG, "Info: MEDIA_INFO_VIDEO_RENDERING_START\n")
                    }
                    player.notifyOnInfo(msg.arg1, msg.arg2)
                }
                MEDIA_TIMED_TEXT -> {
                    if (msg.obj == null) {
                        player.notifyOnTimedText(null)
                    } else {
                        val msgText = msg.obj as? String
                        if (msgText != null && msgText.contains("{\\fad")) {
                            player.notifyOnTimedText(null)
                            return
                        }
                        if (msg.arg1 == 0) { // normal
                            val text = IjkTimedText(Rect(0, 0, 1, 1), msgText)
                            player.notifyOnTimedText(text)
                        } else if (msg.arg1 == 1) { // ass
                            val text = IjkTimedText(Rect(0, 0, 1, 1), msgText)
                            player.notifyOnTimedText(text)
                        } else if (msg.arg1 == 2) { // bitmap
                            val text: IjkTimedText = if (msg.arg2 > 0 && (msg.obj as? IntArray)?.size == msg.arg2) {
                                IjkTimedText(msg.obj as IntArray)
                            } else {
                                IjkTimedText(null, "")
                            }
                            player.notifyOnTimedText(text)
                        }
                    }
                }
                MEDIA_NOP -> {
                }
                MEDIA_SET_VIDEO_SAR -> {
                    player.mVideoSarNum = msg.arg1
                    player.mVideoSarDen = msg.arg2
                    player.notifyOnVideoSizeChanged(
                        player.mVideoWidth, player.mVideoHeight,
                        player.mVideoSarNum, player.mVideoSarDen
                    )
                }
                else -> {
                    DebugLog.e(TAG, "Unknown message type ${msg.what}")
                }
            }
        }
    }

    class DefaultMediaCodecSelector : OnMediaCodecSelectListener {
        @Suppress("deprecation")
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        override fun onMediaCodecSelect(mp: IMediaPlayer, mimeType: String?, profile: Int, level: Int): String? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return null
            if (TextUtils.isEmpty(mimeType)) return null

            Log.i(TAG, String.format(Locale.US, "onSelectCodec: mime=%s, profile=%d, level=%d", mimeType, profile, level))
            val candidateCodecList = ArrayList<IjkMediaCodecInfo>()
            val numCodecs = MediaCodecList.getCodecCount()
            for (i in 0 until numCodecs) {
                val codecInfo = MediaCodecList.getCodecInfoAt(i)
                Log.d(TAG, String.format(Locale.US, "  found codec: %s", codecInfo.name))
                if (codecInfo.isEncoder) continue

                val types = codecInfo.supportedTypes ?: continue
                for (type in types) {
                    if (TextUtils.isEmpty(type)) continue
                    Log.d(TAG, String.format(Locale.US, "    mime: %s", type))
                    if (!type.equals(mimeType, ignoreCase = true)) continue

                    val candidate = IjkMediaCodecInfo.setupCandidate(codecInfo, mimeType) ?: continue
                    candidateCodecList.add(candidate)
                    Log.i(TAG, String.format(Locale.US, "candidate codec: %s rank=%d", codecInfo.name, candidate.mRank))
                    candidate.dumpProfileLevels(mimeType)
                }
            }

            if (candidateCodecList.isEmpty()) return null

            var bestCodec = candidateCodecList[0]
            for (codec in candidateCodecList) {
                if (codec.mRank > bestCodec.mRank) {
                    bestCodec = codec
                }
            }

            if (bestCodec.mRank < IjkMediaCodecInfo.RANK_LAST_CHANCE) {
                Log.w(TAG, String.format(Locale.US, "unaccetable codec: %s", bestCodec.mCodecInfo?.name))
                return null
            }

            Log.i(TAG, String.format(Locale.US, "selected codec: %s rank=%d", bestCodec.mCodecInfo?.name, bestCodec.mRank))
            return bestCodec.mCodecInfo?.name
        }

        companion object {
            @JvmField
            val sInstance = DefaultMediaCodecSelector()
        }
    }

    companion object {
        private val TAG = IjkMediaPlayer::class.java.name

        private const val MEDIA_NOP = 0
        private const val MEDIA_PREPARED = 1
        private const val MEDIA_PLAYBACK_COMPLETE = 2
        private const val MEDIA_BUFFERING_UPDATE = 3
        private const val MEDIA_SEEK_COMPLETE = 4
        private const val MEDIA_SET_VIDEO_SIZE = 5
        private const val MEDIA_TIMED_TEXT = 99
        private const val MEDIA_ERROR = 100
        private const val MEDIA_INFO = 200

        protected const val MEDIA_SET_VIDEO_SAR = 10001

        // options
        const val IJK_LOG_UNKNOWN = 0
        const val IJK_LOG_DEFAULT = 1
        const val IJK_LOG_VERBOSE = 2
        const val IJK_LOG_DEBUG = 3
        const val IJK_LOG_INFO = 4
        const val IJK_LOG_WARN = 5
        const val IJK_LOG_ERROR = 6
        const val IJK_LOG_FATAL = 7
        const val IJK_LOG_SILENT = 8

        const val OPT_CATEGORY_FORMAT = 1
        const val OPT_CATEGORY_CODEC = 2
        const val OPT_CATEGORY_SWS = 3
        const val OPT_CATEGORY_PLAYER = 4

        const val SDL_FCC_YV12 = 0x32315659
        const val SDL_FCC_RV16 = 0x36315652
        const val SDL_FCC_RV32 = 0x32335652

        // properties
        const val PROP_FLOAT_VIDEO_DECODE_FRAMES_PER_SECOND = 10001
        const val PROP_FLOAT_VIDEO_OUTPUT_FRAMES_PER_SECOND = 10002
        const val FFP_PROP_FLOAT_PLAYBACK_RATE = 10003
        const val FFP_PROP_FLOAT_DROP_FRAME_RATE = 10007

        const val FFP_PROP_INT64_SELECTED_VIDEO_STREAM = 20001
        const val FFP_PROP_INT64_SELECTED_AUDIO_STREAM = 20002
        const val FFP_PROP_INT64_SELECTED_TIMEDTEXT_STREAM = 20011

        const val FFP_PROP_INT64_VIDEO_DECODER = 20003
        const val FFP_PROP_INT64_AUDIO_DECODER = 20004
        const val FFP_PROPV_DECODER_UNKNOWN = 0
        const val FFP_PROPV_DECODER_AVCODEC = 1
        const val FFP_PROPV_DECODER_MEDIACODEC = 2
        const val FFP_PROPV_DECODER_VIDEOTOOLBOX = 3
        const val FFP_PROP_INT64_VIDEO_CACHED_DURATION = 20005
        const val FFP_PROP_INT64_AUDIO_CACHED_DURATION = 20006
        const val FFP_PROP_INT64_VIDEO_CACHED_BYTES = 20007
        const val FFP_PROP_INT64_AUDIO_CACHED_BYTES = 20008
        const val FFP_PROP_INT64_VIDEO_CACHED_PACKETS = 20009
        const val FFP_PROP_INT64_AUDIO_CACHED_PACKETS = 20010
        const val FFP_PROP_INT64_ASYNC_STATISTIC_BUF_BACKWARDS = 20201
        const val FFP_PROP_INT64_ASYNC_STATISTIC_BUF_FORWARDS = 20202
        const val FFP_PROP_INT64_ASYNC_STATISTIC_BUF_CAPACITY = 20203
        const val FFP_PROP_INT64_TRAFFIC_STATISTIC_BYTE_COUNT = 20204
        const val FFP_PROP_INT64_CACHE_STATISTIC_PHYSICAL_POS = 20205
        const val FFP_PROP_INT64_CACHE_STATISTIC_FILE_FORWARDS = 20206
        const val FFP_PROP_INT64_CACHE_STATISTIC_FILE_POS = 20207
        const val FFP_PROP_INT64_CACHE_STATISTIC_COUNT_BYTES = 20208
        const val FFP_PROP_INT64_LOGICAL_FILE_SIZE = 20209
        const val FFP_PROP_INT64_SHARE_CACHE_DATA = 20210
        const val FFP_PROP_INT64_BIT_RATE = 20100
        const val FFP_PROP_INT64_TCP_SPEED = 20200
        const val FFP_PROP_INT64_LATEST_SEEK_LOAD_DURATION = 20300
        const val FFP_PROP_INT64_IMMEDIATE_RECONNECT = 20211

        private val sLocalLibLoader = object : IjkLibLoader {
            override fun loadLibrary(libName: String?) {
                System.loadLibrary(libName)
            }
        }

        @Volatile
        private var mIsLibLoaded = false

        @JvmStatic
        fun loadLibrariesOnce(libLoader: IjkLibLoader?) {
            synchronized(IjkMediaPlayer::class.java) {
                if (!mIsLibLoaded) {
                    val loader = libLoader ?: sLocalLibLoader
                    try {
                        loader.loadLibrary("ijkffmpeg")
                        loader.loadLibrary("ijksdl")
                        loader.loadLibrary("player")
                    } catch (ignored: Throwable) {
                    }
                    mIsLibLoaded = true
                }
            }
        }

        @Volatile
        private var mIsNativeInitialized = false

        @JvmStatic
        private fun initNativeOnce() {
            synchronized(IjkMediaPlayer::class.java) {
                if (!mIsNativeInitialized) {
                    native_init()
                    native_setDot(if (dotOpen) dotPort else 0)
                    mIsNativeInitialized = true
                }
            }
        }

        @JvmStatic
        fun getColorFormatName(mediaCodecColorFormat: Int): String? {
            return _getColorFormatName(mediaCodecColorFormat)
        }

        @JvmStatic
        private external fun _getColorFormatName(mediaCodecColorFormat: Int): String?

        @JvmStatic
        private external fun native_init()

        @Keep
        @CalledByNative
        @JvmStatic
        fun postEventFromNative(weakThiz: Any?, what: Int, arg1: Int, arg2: Int, obj: Any?) {
            if (weakThiz == null) return

            val mp = (weakThiz as? WeakReference<*>)?.get() as? IjkMediaPlayer ?: return

            if (what == MEDIA_INFO && arg1 == IMediaPlayer.MEDIA_INFO_STARTED_AS_NEXT) {
                mp.start()
            }
            if (mp.mEventHandler != null) {
                val m = mp.mEventHandler!!.obtainMessage(what, arg1, arg2, obj)
                mp.mEventHandler!!.sendMessage(m)
            }
        }

        @Keep
        @CalledByNative
        @JvmStatic
        fun onNativeInvoke(weakThiz: Any?, what: Int, args: Bundle?): Boolean {
            DebugLog.ifmt(TAG, "onNativeInvoke %d", what)
            if (weakThiz == null || weakThiz !is WeakReference<*>) {
                throw IllegalStateException("<null weakThiz>.onNativeInvoke()")
            }

            @Suppress("UNCHECKED_CAST")
            val weakPlayer = weakThiz as WeakReference<IjkMediaPlayer>
            val player = weakPlayer.get() ?: throw IllegalStateException("<null weakPlayer>.onNativeInvoke()")

            val listener = player.mOnNativeInvokeListener
            if (listener != null && listener.onNativeInvoke(what, args)) return true

            when (what) {
                OnNativeInvokeListener.CTRL_WILL_CONCAT_RESOLVE_SEGMENT -> {
                    val onControlMessageListener = player.mOnControlMessageListener ?: return false

                    val segmentIndex = args?.getInt(OnNativeInvokeListener.ARG_SEGMENT_INDEX, -1) ?: -1
                    if (segmentIndex < 0) {
                        throw InvalidParameterException("onNativeInvoke(invalid segment index)")
                    }

                    val newUrl = onControlMessageListener.onControlResolveSegmentUrl(segmentIndex)
                        ?: throw RuntimeException(IOException("onNativeInvoke() = <NULL newUrl>"))

                    args?.putString(OnNativeInvokeListener.ARG_URL, newUrl)
                    return true
                }
                else -> return false
            }
        }

        @Keep
        @CalledByNative
        @JvmStatic
        fun onSelectCodec(weakThiz: Any?, mimeType: String?, profile: Int, level: Int): String? {
            if (weakThiz == null || weakThiz !is WeakReference<*>) return null

            @Suppress("UNCHECKED_CAST")
            val weakPlayer = weakThiz as WeakReference<IjkMediaPlayer>
            val player = weakPlayer.get() ?: return null

            val listener = player.mOnMediaCodecSelectListener ?: DefaultMediaCodecSelector.sInstance
            return listener.onMediaCodecSelect(player, mimeType, profile, level)
        }

        @Volatile
        private var dotPort = 0

        @Volatile
        private var dotOpen = false

        @JvmStatic
        fun setDotPort(open: Boolean, p: Int) {
            dotOpen = open
            dotPort = p
        }

        @JvmStatic
        fun toggleDotPort(open: Boolean) {
            dotOpen = open
            if (mIsNativeInitialized) {
                native_setDot(if (dotOpen) dotPort else 0)
            }
        }

        @JvmStatic
        external fun native_profileBegin(libName: String?)

        @JvmStatic
        external fun native_profileEnd()

        @JvmStatic
        external fun native_setLogLevel(level: Int)

        @JvmStatic
        external fun native_setReqLevel(level: Int)

        @JvmStatic
        external fun native_setDot(port: Int)
    }
}
