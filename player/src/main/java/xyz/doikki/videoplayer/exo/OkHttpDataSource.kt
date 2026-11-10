package xyz.doikki.videoplayer.exo

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.upstream.BaseDataSource
import com.google.android.exoplayer2.upstream.DataSourceException
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidContentTypeException
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException
import com.google.android.exoplayer2.upstream.HttpDataSource.RequestProperties
import com.google.android.exoplayer2.upstream.HttpUtil
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util
import com.google.common.base.Predicate
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.CountDownLatch
import kotlin.math.min

@Deprecated("ExoPlayer HTTP data source backed by okhttp3")
class OkHttpDataSource private constructor(
    private val callFactory: Call.Factory,
    private val userAgent: String?,
    private val cacheControl: CacheControl?,
    private val defaultRequestProperties: RequestProperties?,
    private var contentTypePredicate: Predicate<String>?
) : BaseDataSource(true), HttpDataSource {

    class Factory(private val callFactory: Call.Factory) : HttpDataSource.BaseFactory() {
        private var userAgent: String? = null
        private var transferListener: TransferListener? = null
        private var cacheControl: CacheControl? = null
        private var contentTypePredicate: Predicate<String>? = null

        fun setUserAgent(userAgent: String?): Factory {
            this.userAgent = userAgent
            return this
        }

        fun setCacheControl(cacheControl: CacheControl?): Factory {
            this.cacheControl = cacheControl
            return this
        }

        fun setContentTypePredicate(contentTypePredicate: Predicate<String>?): Factory {
            this.contentTypePredicate = contentTypePredicate
            return this
        }

        fun setTransferListener(transferListener: TransferListener?): Factory {
            this.transferListener = transferListener
            return this
        }

        override fun createDataSourceInternal(defaultRequestProperties: RequestProperties): HttpDataSource {
            val dataSource = OkHttpDataSource(callFactory, userAgent, cacheControl, defaultRequestProperties, contentTypePredicate)
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener!!)
            }
            return dataSource
        }
    }

    private val requestProperties = RequestProperties()
    private var dataSpec: DataSpec? = null
    private var response: Response? = null
    private var responseByteStream: InputStream? = null
    private var opened = false
    private var bytesToRead: Long = 0
    private var bytesRead: Long = 0

    constructor(callFactory: Call.Factory) : this(callFactory, null)

    constructor(callFactory: Call.Factory, userAgent: String?) : this(callFactory, userAgent, null, null, null)

    constructor(callFactory: Call.Factory, userAgent: String?, cacheControl: CacheControl?, defaultRequestProperties: RequestProperties?) : this(
        callFactory, userAgent, cacheControl, defaultRequestProperties, null
    )

    @Deprecated("Deprecated in Java")
    fun setContentTypePredicate(contentTypePredicate: Predicate<String>?) {
        this.contentTypePredicate = contentTypePredicate
    }

    override fun getUri(): Uri? {
        val currentResponse = response ?: return null
        return Uri.parse(currentResponse.request().url().toString())
    }

    override fun getResponseCode(): Int {
        val currentResponse = response ?: return -1
        return currentResponse.code()
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        val currentResponse = response ?: return Collections.emptyMap()
        return currentResponse.headers().toMultimap()
    }

    override fun setRequestProperty(name: String, value: String) {
        Assertions.checkNotNull(name)
        Assertions.checkNotNull(value)
        requestProperties.set(name, value)
    }

    override fun clearRequestProperty(name: String) {
        Assertions.checkNotNull(name)
        requestProperties.remove(name)
    }

    override fun clearAllRequestProperties() {
        requestProperties.clear()
    }

    @Throws(HttpDataSourceException::class)
    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        bytesRead = 0
        bytesToRead = 0
        transferInitializing(dataSpec)

        val request = makeRequest(dataSpec)
        val resp: Response
        val responseBody: ResponseBody
        val call = callFactory.newCall(request)
        try {
            resp = executeCall(call)
            this.response = resp
            responseBody = Assertions.checkNotNull(resp.body())
            responseByteStream = responseBody.byteStream()
        } catch (e: IOException) {
            throw HttpDataSourceException.createForIOException(e, dataSpec, HttpDataSourceException.TYPE_OPEN)
        }

        val responseCode = resp.code()
        if (!resp.isSuccessful) {
            if (responseCode == 416) {
                val documentSize = HttpUtil.getDocumentSize(resp.headers()["Content-Range"])
                if (dataSpec.position == documentSize) {
                    opened = true
                    transferStarted(dataSpec)
                    return if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else 0
                }
            }

            val errorResponseBody: ByteArray = try {
                Util.toByteArray(Assertions.checkNotNull(responseByteStream))
            } catch (e: IOException) {
                Util.EMPTY_BYTE_ARRAY
            }
            val headers = resp.headers().toMultimap()
            closeConnectionQuietly()
            val cause: IOException? = if (responseCode == 416) {
                DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            } else {
                null
            }
            throw InvalidResponseCodeException(responseCode, resp.message(), cause, headers, dataSpec, errorResponseBody)
        }

        val mediaType = responseBody.contentType()
        val contentType = mediaType?.toString() ?: ""
        if (contentTypePredicate != null && contentTypePredicate?.apply(contentType) != true) {
            closeConnectionQuietly()
            throw InvalidContentTypeException(contentType, dataSpec)
        }

        val bytesToSkip = if (responseCode == 200 && dataSpec.position != 0L) dataSpec.position else 0L
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            bytesToRead = dataSpec.length
        } else {
            val contentLength = responseBody.contentLength()
            bytesToRead = if (contentLength != -1L) (contentLength - bytesToSkip) else C.LENGTH_UNSET.toLong()
        }

        opened = true
        transferStarted(dataSpec)

        try {
            skipFully(bytesToSkip, dataSpec)
        } catch (e: HttpDataSourceException) {
            closeConnectionQuietly()
            throw e
        }
        return bytesToRead
    }

    @Throws(HttpDataSourceException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return try {
            readInternal(buffer, offset, length)
        } catch (e: IOException) {
            throw HttpDataSourceException.createForIOException(e, Assertions.checkNotNull(dataSpec), HttpDataSourceException.TYPE_READ)
        }
    }

    @Throws(HttpDataSourceException::class)
    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
            closeConnectionQuietly()
        }
    }

    @Throws(HttpDataSourceException::class)
    private fun makeRequest(dataSpec: DataSpec): Request {
        val position = dataSpec.position
        val length = dataSpec.length

        val url = HttpUrl.parse(dataSpec.uri.toString())
            ?: throw HttpDataSourceException("Malformed URL", dataSpec, PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK, HttpDataSourceException.TYPE_OPEN)

        val builder = Request.Builder().url(url)
        if (cacheControl != null) {
            builder.cacheControl(cacheControl)
        }

        val headers = HashMap<String, String>()
        if (defaultRequestProperties != null) {
            headers.putAll(defaultRequestProperties.snapshot)
        }
        headers.putAll(requestProperties.snapshot)
        headers.putAll(dataSpec.httpRequestHeaders)
        for ((key, value) in headers) {
            builder.header(key, value)
        }

        val rangeHeader = HttpUtil.buildRangeRequestHeader(position, length)
        if (rangeHeader != null) {
            builder.addHeader("Range", rangeHeader)
        }
        if (userAgent != null) {
            builder.addHeader("User-Agent", userAgent)
        }
        if (!dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP)) {
            builder.addHeader("Accept-Encoding", "identity")
        }

        var requestBody: RequestBody? = null
        if (dataSpec.httpBody != null) {
            requestBody = RequestBody.create(null, dataSpec.httpBody!!)
        } else if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST) {
            requestBody = RequestBody.create(null, Util.EMPTY_BYTE_ARRAY)
        }
        builder.method(dataSpec.httpMethodString, requestBody)
        return builder.build()
    }

    @Throws(IOException::class)
    private fun executeCall(call: Call): Response {
        val responseHolder = arrayOfNulls<Response>(1)
        val errorHolder = arrayOfNulls<IOException>(1)
        val latch = CountDownLatch(1)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorHolder[0] = e
                latch.countDown()
            }

            override fun onResponse(call: Call, response: Response) {
                responseHolder[0] = response
                latch.countDown()
            }
        })

        var interrupted = false
        while (true) {
            try {
                latch.await()
                break
            } catch (e: InterruptedException) {
                interrupted = true
                call.cancel()
            }
        }
        if (interrupted) {
            throw InterruptedIOException()
        }
        if (errorHolder[0] != null) {
            throw errorHolder[0]!!
        }
        return Assertions.checkNotNull(responseHolder[0])
    }

    @Throws(HttpDataSourceException::class)
    private fun skipFully(bytesToSkip: Long, dataSpec: DataSpec) {
        if (bytesToSkip == 0L) return

        var remaining = bytesToSkip
        val skipBuffer = ByteArray(4096)
        try {
            while (remaining > 0) {
                val readLength = min(remaining, skipBuffer.size.toLong()).toInt()
                val read = Assertions.checkNotNull(responseByteStream).read(skipBuffer, 0, readLength)
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedIOException()
                }
                if (read == -1) {
                    throw HttpDataSourceException(dataSpec, PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE, HttpDataSourceException.TYPE_OPEN)
                }
                remaining -= read
                bytesTransferred(read)
            }
        } catch (e: IOException) {
            if (e is HttpDataSourceException) {
                throw e
            }
            throw HttpDataSourceException(dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, HttpDataSourceException.TYPE_OPEN)
        }
    }

    @Throws(IOException::class)
    private fun readInternal(buffer: ByteArray, offset: Int, length: Int): Int {
        var readLength = length
        if (readLength == 0) return 0

        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val bytesRemaining = bytesToRead - bytesRead
            if (bytesRemaining == 0L) {
                return C.RESULT_END_OF_INPUT
            }
            readLength = min(readLength.toLong(), bytesRemaining).toInt()
        }

        val read = Assertions.checkNotNull(responseByteStream).read(buffer, offset, readLength)
        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }

        bytesRead += read
        bytesTransferred(read)
        return read
    }

    private fun closeConnectionQuietly() {
        response?.body()?.close()
        response = null
        responseByteStream = null
    }
}
