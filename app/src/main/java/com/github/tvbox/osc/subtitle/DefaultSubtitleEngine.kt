package com.github.tvbox.osc.subtitle

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.cache.CacheManager
import com.github.tvbox.osc.subtitle.model.Subtitle
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.MD5
import com.github.tvbox.osc.util.SubtitleHelper
import xyz.doikki.videoplayer.player.AbstractPlayer
import java.io.File
import java.util.ArrayList
import java.util.TreeMap

class DefaultSubtitleEngine : SubtitleEngine {
    private var mWorkHandler: Handler? = null
    private var mSubtitles: List<Subtitle>? = null
    private var mUIRenderTask: UIRenderTask? = null
    private var mMediaPlayer: AbstractPlayer? = null
    private var mOnSubtitlePreparedListener: SubtitleEngine.OnSubtitlePreparedListener? = null
    private var mOnSubtitleChangeListener: SubtitleEngine.OnSubtitleChangeListener? = null
    private var mergeSameTime = false
    private var playSubtitleCacheKey: String? = null

    override fun bindToMediaPlayer(mediaPlayer: AbstractPlayer) {
        mMediaPlayer = mediaPlayer
    }

    override fun setSubtitlePath(path: String) {
        initWorkThread()
        reset()
        if (TextUtils.isEmpty(path)) {
            Log.w(TAG, "loadSubtitleFromRemote: path is null.")
            return
        }

        SubtitleLoader.loadSubtitle(path, object : SubtitleLoader.Callback {
            override fun onSuccess(subtitleLoadSuccessResult: SubtitleLoadSuccessResult) {
                if (subtitleLoadSuccessResult.timedTextObject == null) {
                    Log.d(TAG, "onSuccess: timedTextObject is null.")
                    return
                }
                val captions = subtitleLoadSuccessResult.timedTextObject?.captions
                if (captions == null) {
                    Log.d(TAG, "onSuccess: captions is null.")
                    return
                }
                mSubtitles = buildSubtitles(captions)
                setSubtitleDelay(SubtitleHelper.getTimeDelay())
                notifyPrepared()

                val subtitlePath = subtitleLoadSuccessResult.subtitlePath ?: ""
                if (subtitlePath.startsWith("http://") || subtitlePath.startsWith("https://")) {
                    val subtitleFileCacheDir = App.getInstance().cacheDir.absolutePath + "/zimu/"
                    val cacheDir = File(subtitleFileCacheDir)
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs()
                    }
                    val subtitleFile = subtitleFileCacheDir + subtitleLoadSuccessResult.fileName
                    val cacheSubtitleFile = File(subtitleFile)
                    val contentBytes = subtitleLoadSuccessResult.content?.toByteArray() ?: ByteArray(0)
                    val writeResult = FileUtils.writeSimple(contentBytes, cacheSubtitleFile)
                    if (writeResult && playSubtitleCacheKey != null) {
                        CacheManager.save(MD5.string2MD5(getPlaySubtitleCacheKey()), subtitleFile)
                    }
                } else {
                    CacheManager.save(MD5.string2MD5(getPlaySubtitleCacheKey()), path)
                }
            }

            override fun onError(exception: Exception) {
                Log.e(TAG, "onError: " + exception.message)
            }
        })
    }

    fun setMergeSameTime(mergeSameTime: Boolean) {
        this.mergeSameTime = mergeSameTime
    }

    private fun buildSubtitles(captions: TreeMap<Int, Subtitle>): List<Subtitle> {
        val subtitles = ArrayList<Subtitle>()
        for (subtitle in captions.values) {
            if (mergeSameTime && subtitles.isNotEmpty()) {
                val previous = subtitles[subtitles.size - 1]
                if (previous.start.mseconds == subtitle.start.mseconds &&
                    previous.end.mseconds == subtitle.end.mseconds
                ) {
                    val prevLines = previous.lines ?: ArrayList<Subtitle>().also {
                        it.add(previous)
                        previous.lines = it
                    }
                    prevLines.add(subtitle)
                    if (!TextUtils.isEmpty(subtitle.content)) {
                        if (!TextUtils.isEmpty(previous.content)) previous.content += "\n"
                        previous.content += subtitle.content
                    }
                    continue
                }
            }
            if (mergeSameTime) {
                val lines = ArrayList<Subtitle>()
                lines.add(subtitle)
                subtitle.lines = lines
            }
            subtitles.add(subtitle)
        }
        return subtitles
    }

    override fun setSubtitleDelay(milliseconds: Int?) {
        val delay = milliseconds ?: 0
        if (delay == 0) {
            return
        }
        val currentSubtitles = mSubtitles
        if (currentSubtitles.isNullOrEmpty()) {
            return
        }
        mSubtitles = null
        for (i in currentSubtitles.indices) {
            val subtitle = currentSubtitles[i]
            val start = subtitle.start
            val end = subtitle.end
            start.mseconds += delay
            end.mseconds += delay
            if (start.mseconds <= 0) {
                start.mseconds = 0
            }
            if (end.mseconds <= 0) {
                end.mseconds = 0
            }
        }
        mSubtitles = currentSubtitles
    }

    override fun setPlaySubtitleCacheKey(cacheKey: String) {
        playSubtitleCacheKey = cacheKey
    }

    override fun getPlaySubtitleCacheKey(): String? {
        return playSubtitleCacheKey
    }

    override fun reset() {
        stop()
        mSubtitles = null
        mUIRenderTask = null
    }

    override fun start() {
        Log.d(TAG, "start: ")
        if (mMediaPlayer == null) {
            Log.w(
                TAG, "MediaPlayer is not bind, You must bind MediaPlayer to " +
                        SubtitleEngine::class.java.simpleName +
                        " before start() method be called, you can do this by call bindToMediaPlayer(MediaPlayer mediaPlayer) method."
            )
            return
        }
        stop()
        mWorkHandler?.sendEmptyMessageDelayed(MSG_REFRESH, REFRESH_INTERVAL.toLong())
    }

    override fun pause() {
        stop()
    }

    override fun resume() {
        start()
    }

    override fun stop() {
        mWorkHandler?.removeMessages(MSG_REFRESH)
    }

    override fun destroy() {
        Log.d(TAG, "destroy: ")
        stopWorkThread()
        reset()
    }

    private fun initWorkThread() {
        stopWorkThread()
        mWorkHandler = Handler(Looper.getMainLooper()) { msg ->
            try {
                var delay = REFRESH_INTERVAL.toLong()
                val player = mMediaPlayer
                if (player != null && player.isPlaying()) {
                    val position = player.getCurrentPosition()
                    val subtitle = SubtitleFinder.find(position, mSubtitles)
                    notifyRefreshUI(subtitle)
                    if (subtitle != null) {
                        val remaining = subtitle.end.mseconds.toLong() - position
                        delay = if (remaining > 0) remaining else 0L
                    }
                }
                mWorkHandler?.sendEmptyMessageDelayed(MSG_REFRESH, delay)
            } catch (e: Exception) {
                // ignored
            }
            true
        }
    }

    private fun stopWorkThread() {
        mWorkHandler?.let {
            it.removeCallbacksAndMessages(null)
            mWorkHandler = null
        }
    }

    private fun notifyRefreshUI(subtitle: Subtitle?) {
        if (mUIRenderTask == null) {
            mUIRenderTask = UIRenderTask(mOnSubtitleChangeListener)
        }
        mUIRenderTask?.execute(subtitle)
    }

    private fun notifyPrepared() {
        mOnSubtitlePreparedListener?.onSubtitlePrepared(mSubtitles)
    }

    override fun setOnSubtitlePreparedListener(listener: SubtitleEngine.OnSubtitlePreparedListener?) {
        mOnSubtitlePreparedListener = listener
    }

    override fun setOnSubtitleChangeListener(listener: SubtitleEngine.OnSubtitleChangeListener?) {
        mOnSubtitleChangeListener = listener
    }

    companion object {
        private val TAG = DefaultSubtitleEngine::class.java.simpleName
        private const val MSG_REFRESH = 0x888
        private const val REFRESH_INTERVAL = 100
    }
}
