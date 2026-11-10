package xyz.doikki.videoplayer.exo

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.ext.rtmp.RtmpDataSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import okhttp3.OkHttpClient
import java.io.File
import java.util.HashMap
import java.util.Locale

class ExoMediaSourceHelper private constructor(context: Context) {
    private val mAppContext: Context = context.applicationContext
    private var mHttpDataSourceFactory: OkHttpDataSource.Factory? = null
    private var mCache: Cache? = null
    private var mClient: OkHttpClient? = null

    fun setOkClient(client: OkHttpClient?) {
        mClient = client
        mHttpDataSourceFactory = null
    }

    fun getMediaSource(uri: String): MediaSource {
        return getMediaSource(uri, null, false)
    }

    fun getMediaSource(uri: String, headers: Map<String, String>?): MediaSource {
        return getMediaSource(uri, headers, false)
    }

    fun getMediaSource(uri: String, isCache: Boolean): MediaSource {
        return getMediaSource(uri, null, isCache)
    }

    fun getMediaSource(uri: String, headers: Map<String, String>?, isCache: Boolean): MediaSource {
        return getMediaSource(uri, headers, isCache, inferContentType(uri, headers))
    }

    fun getHlsMediaSource(uri: String, headers: Map<String, String>?): MediaSource {
        return getMediaSource(uri, headers, false, C.TYPE_HLS)
    }

    private fun getMediaSource(uri: String, headers: Map<String, String>?, isCache: Boolean, contentType: Int): MediaSource {
        val contentUri = Uri.parse(uri)
        if ("rtmp" == contentUri.scheme) {
            return ProgressiveMediaSource.Factory(RtmpDataSourceFactory(null))
                .createMediaSource(MediaItem.fromUri(contentUri))
        } else if ("rtsp" == contentUri.scheme) {
            return RtspMediaSource.Factory().createMediaSource(MediaItem.fromUri(contentUri))
        }
        val factory: DataSource.Factory = if (isCache) {
            getCacheDataSourceFactory()
        } else {
            getDataSourceFactory()
        }
        applyHeaders(headers)
        return when (contentType) {
            C.TYPE_DASH -> DashMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(contentUri))
            C.TYPE_HLS -> HlsMediaSource.Factory(factory)
                .setLoadErrorHandlingPolicy(HlsErrorHandlingPolicy())
                .createMediaSource(MediaItem.fromUri(contentUri))
            else -> ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(contentUri))
        }
    }

    private fun inferContentType(fileName: String, headers: Map<String, String>?): Int {
        val formatType = inferFormatContentType(headers)
        if (formatType != C.TYPE_OTHER) {
            return formatType
        }
        val lowerFileName = fileName.lowercase(Locale.ROOT)
        return if (lowerFileName.contains(".mpd") || lowerFileName.contains("type=mpd") || lowerFileName.contains("type=dash") || lowerFileName.contains("format=mpd") || lowerFileName.contains("format=dash")) {
            C.TYPE_DASH
        } else if (isHlsUri(lowerFileName)) {
            C.TYPE_HLS
        } else {
            C.TYPE_OTHER
        }
    }

    private fun inferFormatContentType(headers: Map<String, String>?): Int {
        if (headers == null || !headers.containsKey(HEADER_FORMAT)) {
            return C.TYPE_OTHER
        }
        var format = headers[HEADER_FORMAT] ?: return C.TYPE_OTHER
        format = format.trim().lowercase(Locale.ROOT)
        if (format == "hls" || format.contains("mpegurl") || format.contains("m3u8")) {
            return C.TYPE_HLS
        }
        if (format == "dash" || format == "mpd" || format.contains("dash+xml")) {
            return C.TYPE_DASH
        }
        return C.TYPE_OTHER
    }

    private fun isHlsUri(uri: String): Boolean {
        if (isAudioUri(uri)) {
            return false
        }
        if (uri.contains("m3u8") || uri.contains("type=hls") || uri.contains("format=hls")) {
            return true
        }
        val parsedUri = Uri.parse(uri)
        var path = parsedUri.path ?: return false
        path = path.lowercase(Locale.ROOT)
        return path.endsWith("/live.php") || path.contains("/live/")
    }

    private fun isAudioUri(uri: String): Boolean {
        val parsedUri = Uri.parse(uri)
        var path = parsedUri.path ?: uri
        path = path.lowercase(Locale.ROOT)
        return path.endsWith(".mp3")
                || path.endsWith(".m4a")
                || path.endsWith(".aac")
                || path.endsWith(".flac")
                || path.endsWith(".wav")
                || path.endsWith(".ogg")
                || path.endsWith(".opus")
                || path.endsWith(".amr")
    }

    private fun getCacheDataSourceFactory(): DataSource.Factory {
        if (mCache == null) {
            mCache = newCache()
        }
        return CacheDataSource.Factory()
            .setCache(mCache!!)
            .setUpstreamDataSourceFactory(getDataSourceFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun newCache(): Cache {
        return SimpleCache(
            File(externalCacheDir(), "exo-video-cache"),
            LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024),
            ExoDatabaseProvider(mAppContext)
        )
    }

    private fun externalCacheDir(): File {
        return mAppContext.externalCacheDir ?: mAppContext.cacheDir
    }

    private fun getDataSourceFactory(): DataSource.Factory {
        return DefaultDataSourceFactory(mAppContext, getHttpDataSourceFactory())
    }

    private fun getHttpDataSourceFactory(): OkHttpDataSource.Factory {
        if (mHttpDataSourceFactory == null) {
            val client = mClient ?: OkHttpClient.Builder().build()
            mHttpDataSourceFactory = OkHttpDataSource.Factory(client)
        }
        return mHttpDataSourceFactory!!
    }

    private fun applyHeaders(headers: Map<String, String>?) {
        val requestHeaders = HashMap<String, String>()
        var userAgent: String? = null
        if (!headers.isNullOrEmpty()) {
            for ((key, value) in headers) {
                if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) {
                    continue
                }
                if (HEADER_FORMAT.equals(key, ignoreCase = true)) {
                    continue
                }
                if ("User-Agent".equals(key, ignoreCase = true)) {
                    userAgent = value.trim()
                } else {
                    requestHeaders[key] = value.trim()
                }
            }
        }
        val factory = getHttpDataSourceFactory()
        factory.setUserAgent(userAgent)
        factory.setDefaultRequestProperties(requestHeaders)
    }

    fun setCache(cache: Cache?) {
        this.mCache = cache
    }

    companion object {
        const val HEADER_FORMAT = "TVBox-Format"

        @Volatile
        private var sInstance: ExoMediaSourceHelper? = null

        @JvmStatic
        fun getInstance(context: Context): ExoMediaSourceHelper {
            if (sInstance == null) {
                synchronized(ExoMediaSourceHelper::class.java) {
                    if (sInstance == null) {
                        sInstance = ExoMediaSourceHelper(context)
                    }
                }
            }
            return sInstance!!
        }
    }
}
