package tv.danmaku.ijk.media.player

import android.annotation.TargetApi
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import tv.danmaku.ijk.media.player.misc.IMediaDataSource
import tv.danmaku.ijk.media.player.misc.ITrackInfo
import java.io.FileDescriptor
import java.io.IOException

interface IMediaPlayer {
    fun setDisplay(sh: SurfaceHolder?)

    @Throws(IOException::class, IllegalArgumentException::class, SecurityException::class, IllegalStateException::class)
    fun setDataSource(context: Context, uri: Uri)

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Throws(IOException::class, IllegalArgumentException::class, SecurityException::class, IllegalStateException::class)
    fun setDataSource(context: Context, uri: Uri, headers: Map<String, String>?)

    @Throws(IOException::class, IllegalArgumentException::class, IllegalStateException::class)
    fun setDataSource(fd: FileDescriptor)

    @Throws(IOException::class, IllegalArgumentException::class, SecurityException::class, IllegalStateException::class)
    fun setDataSource(path: String)

    fun getDataSource(): String?

    @Throws(IllegalStateException::class)
    fun prepareAsync()

    @Throws(IllegalStateException::class)
    fun start()

    @Throws(IllegalStateException::class)
    fun stop()

    @Throws(IllegalStateException::class)
    fun pause()

    fun setScreenOnWhilePlaying(screenOn: Boolean)

    fun getVideoWidth(): Int

    fun getVideoHeight(): Int

    fun isPlaying(): Boolean

    @Throws(IllegalStateException::class)
    fun seekTo(msec: Long)

    fun getCurrentPosition(): Long

    fun getDuration(): Long

    fun release()

    fun reset()

    fun setVolume(leftVolume: Float, rightVolume: Float)

    fun getAudioSessionId(): Int

    fun getMediaInfo(): MediaInfo?

    @Deprecated("Deprecated in Java")
    fun setLogEnabled(enable: Boolean)

    @Deprecated("Deprecated in Java")
    fun isPlayable(): Boolean

    fun setOnPreparedListener(listener: OnPreparedListener?)

    fun setOnCompletionListener(listener: OnCompletionListener?)

    fun setOnBufferingUpdateListener(listener: OnBufferingUpdateListener?)

    fun setOnSeekCompleteListener(listener: OnSeekCompleteListener?)

    fun setOnVideoSizeChangedListener(listener: OnVideoSizeChangedListener?)

    fun setOnErrorListener(listener: OnErrorListener?)

    fun setOnInfoListener(listener: OnInfoListener?)

    fun setOnTimedTextListener(listener: OnTimedTextListener?)

    fun setAudioStreamType(streamtype: Int)

    @Deprecated("Deprecated in Java")
    fun setKeepInBackground(keepInBackground: Boolean)

    fun getVideoSarNum(): Int

    fun getVideoSarDen(): Int

    @Deprecated("Deprecated in Java")
    fun setWakeMode(context: Context, mode: Int)

    fun setLooping(looping: Boolean)

    fun isLooping(): Boolean

    fun getTrackInfo(): Array<out ITrackInfo>?

    fun setSurface(surface: Surface?)

    fun setDataSource(mediaDataSource: IMediaDataSource)

    fun interface OnPreparedListener {
        fun onPrepared(mp: IMediaPlayer)
    }

    fun interface OnCompletionListener {
        fun onCompletion(mp: IMediaPlayer)
    }

    fun interface OnBufferingUpdateListener {
        fun onBufferingUpdate(mp: IMediaPlayer, percent: Int)
    }

    fun interface OnSeekCompleteListener {
        fun onSeekComplete(mp: IMediaPlayer)
    }

    fun interface OnVideoSizeChangedListener {
        fun onVideoSizeChanged(mp: IMediaPlayer, width: Int, height: Int, sar_num: Int, sar_den: Int)
    }

    fun interface OnErrorListener {
        fun onError(mp: IMediaPlayer, what: Int, extra: Int): Boolean
    }

    fun interface OnInfoListener {
        fun onInfo(mp: IMediaPlayer, what: Int, extra: Int): Boolean
    }

    fun interface OnTimedTextListener {
        fun onTimedText(mp: IMediaPlayer, text: IjkTimedText?)
    }

    companion object {
        const val MEDIA_INFO_UNKNOWN = 1
        const val MEDIA_INFO_STARTED_AS_NEXT = 2
        const val MEDIA_INFO_VIDEO_RENDERING_START = 3
        const val MEDIA_INFO_VIDEO_TRACK_LAGGING = 700
        const val MEDIA_INFO_BUFFERING_START = 701
        const val MEDIA_INFO_BUFFERING_END = 702
        const val MEDIA_INFO_NETWORK_BANDWIDTH = 703
        const val MEDIA_INFO_BAD_INTERLEAVING = 800
        const val MEDIA_INFO_NOT_SEEKABLE = 801
        const val MEDIA_INFO_METADATA_UPDATE = 802
        const val MEDIA_INFO_TIMED_TEXT_ERROR = 900
        const val MEDIA_INFO_UNSUPPORTED_SUBTITLE = 901
        const val MEDIA_INFO_SUBTITLE_TIMED_OUT = 902

        const val MEDIA_INFO_VIDEO_ROTATION_CHANGED = 10001
        const val MEDIA_INFO_AUDIO_RENDERING_START = 10002
        const val MEDIA_INFO_AUDIO_DECODED_START = 10003
        const val MEDIA_INFO_VIDEO_DECODED_START = 10004
        const val MEDIA_INFO_OPEN_INPUT = 10005
        const val MEDIA_INFO_FIND_STREAM_INFO = 10006
        const val MEDIA_INFO_COMPONENT_OPEN = 10007
        const val MEDIA_INFO_VIDEO_SEEK_RENDERING_START = 10008
        const val MEDIA_INFO_AUDIO_SEEK_RENDERING_START = 10009
        const val MEDIA_INFO_MEDIA_ACCURATE_SEEK_COMPLETE = 10100

        const val MEDIA_ERROR_UNKNOWN = 1
        const val MEDIA_ERROR_SERVER_DIED = 100
        const val MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = 200
        const val MEDIA_ERROR_IO = -1004
        const val MEDIA_ERROR_MALFORMED = -1007
        const val MEDIA_ERROR_UNSUPPORTED = -1010
        const val MEDIA_ERROR_TIMED_OUT = -110
    }
}
