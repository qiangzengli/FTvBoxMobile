package okhttp3.dnsoverhttps

import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.Util
import okhttp3.internal.platform.Platform
import okhttp3.internal.publicsuffix.PublicSuffixDatabase
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.ArrayList
import java.util.Arrays
import java.util.concurrent.CountDownLatch

class DnsOverHttps internal constructor(builder: Builder) : Dns {
    private val client: OkHttpClient
    private var url: HttpUrl?
    private val includeIPv6: Boolean
    private val post: Boolean
    private val resolvePrivateAddresses: Boolean
    private val resolvePublicAddresses: Boolean

    init {
        if (builder.client == null) {
            throw NullPointerException("client not set")
        }
        this.url = builder.url
        this.includeIPv6 = builder.includeIPv6
        this.post = builder.post
        this.resolvePrivateAddresses = builder.resolvePrivateAddresses
        this.resolvePublicAddresses = builder.resolvePublicAddresses
        this.client = builder.client!!.newBuilder().dns(buildBootstrapClient(builder)).build()
    }

    fun setUrl(newUrl: HttpUrl?) {
        this.url = newUrl
    }

    fun url(): HttpUrl? = url

    fun post(): Boolean = post

    fun includeIPv6(): Boolean = includeIPv6

    fun client(): OkHttpClient = client

    fun resolvePrivateAddresses(): Boolean = resolvePrivateAddresses

    fun resolvePublicAddresses(): Boolean = resolvePublicAddresses

    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        val currentUrl = this.url ?: return Dns.SYSTEM.lookup(hostname)
        if (!resolvePrivateAddresses || !resolvePublicAddresses) {
            val privateHost = isPrivateHost(hostname)
            if (privateHost && !resolvePrivateAddresses) {
                throw UnknownHostException("private hosts not resolved")
            }
            if (!privateHost && !resolvePublicAddresses) {
                throw UnknownHostException("public hosts not resolved")
            }
        }
        return lookupHttps(hostname)
    }

    @Throws(Throwable::class)
    fun lookupHttpsForwardSync(hostname: String): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        byteArrayOutputStream.write(executeRequestsSync(hostname, DnsRecordCodec.TYPE_A))
        byteArrayOutputStream.write(executeRequestsSync(hostname, DnsRecordCodec.TYPE_AAAA))
        return byteArrayOutputStream.toByteArray()
    }

    @Throws(UnknownHostException::class)
    private fun lookupHttps(hostname: String): List<InetAddress> {
        val networkRequests = ArrayList<Call>(2)
        val failures = ArrayList<Exception>(2)
        val results = ArrayList<InetAddress>(5)

        buildRequest(hostname, networkRequests, results, failures, DnsRecordCodec.TYPE_A)

        if (includeIPv6) {
            buildRequest(hostname, networkRequests, results, failures, DnsRecordCodec.TYPE_AAAA)
        }

        executeRequests(hostname, networkRequests, results, failures)

        if (results.isNotEmpty()) {
            return results
        }
        return Dns.SYSTEM.lookup(hostname)
    }

    private fun buildRequest(
        hostname: String,
        networkRequests: MutableList<Call>,
        results: MutableList<InetAddress>,
        failures: MutableList<Exception>,
        type: Int
    ) {
        val request = buildRequest(hostname, type)
        val response = getCacheOnlyResponse(request)

        if (response != null) {
            processResponse(response, hostname, results, failures)
        } else {
            networkRequests.add(client.newCall(request))
        }
    }

    @Throws(IOException::class)
    private fun executeRequestsSync(hostname: String, type: Int): ByteArray {
        val request = buildRequest(hostname, type)
        var response = getCacheOnlyResponse(request)

        if (response == null) {
            response = client.newCall(request).execute()
        }
        return response.body()!!.bytes()
    }

    private fun executeRequests(
        hostname: String,
        networkRequests: List<Call>,
        responses: MutableList<InetAddress>,
        failures: MutableList<Exception>
    ) {
        val latch = CountDownLatch(networkRequests.size)

        for (call in networkRequests) {
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    synchronized(failures) {
                        failures.add(e)
                    }
                    latch.countDown()
                }

                override fun onResponse(call: Call, response: Response) {
                    processResponse(response, hostname, responses, failures)
                    latch.countDown()
                }
            })
        }

        try {
            latch.await()
        } catch (e: InterruptedException) {
            failures.add(e)
        }
    }

    private fun processResponse(
        response: Response,
        hostname: String,
        results: MutableList<InetAddress>,
        failures: MutableList<Exception>
    ) {
        try {
            val addresses = readResponse(hostname, response)
            synchronized(results) {
                results.addAll(addresses)
            }
        } catch (e: Exception) {
            synchronized(failures) {
                failures.add(e)
            }
        }
    }

    @Throws(UnknownHostException::class)
    private fun throwBestFailure(hostname: String, failures: List<Exception>): List<InetAddress> {
        if (failures.isEmpty()) {
            throw UnknownHostException(hostname)
        }

        val failure = failures[0]
        if (failure is UnknownHostException) {
            throw failure
        }

        val unknownHostException = UnknownHostException(hostname)
        unknownHostException.initCause(failure)

        for (i in 1 until failures.size) {
            Util.addSuppressedIfPossible(unknownHostException, failures[i])
        }

        throw unknownHostException
    }

    private fun getCacheOnlyResponse(request: Request): Response? {
        if (!post && client.cache() != null) {
            try {
                val cacheRequest = request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()
                val cacheResponse = client.newCall(cacheRequest).execute()
                if (cacheResponse.code() != 504) {
                    return cacheResponse
                }
            } catch (ignored: IOException) {
            }
        }
        return null
    }

    @Throws(Exception::class)
    private fun readResponse(hostname: String, response: Response): List<InetAddress> {
        if (response.cacheResponse() == null && response.protocol() != Protocol.HTTP_2) {
            Platform.get().log(Platform.WARN, "Incorrect protocol: " + response.protocol(), null)
        }

        try {
            if (!response.isSuccessful) {
                throw IOException("response: " + response.code() + " " + response.message())
            }

            val body: ResponseBody = response.body() ?: throw IOException("null response body")

            if (body.contentLength() > MAX_RESPONSE_SIZE) {
                throw IOException(
                    "response size exceeds limit (" +
                            MAX_RESPONSE_SIZE +
                            " bytes): " +
                            body.contentLength() +
                            " bytes"
                )
            }

            val responseBytes = body.source().readByteString()
            return DnsRecordCodec.decodeAnswers(hostname, responseBytes)
        } finally {
            response.close()
        }
    }

    private fun buildRequest(hostname: String, type: Int): Request {
        var requestBuilder = Request.Builder().header("Accept", DNS_MESSAGE.toString())
        val query = DnsRecordCodec.encodeQuery(hostname, type)

        val targetUrl = url ?: throw IllegalStateException("url not set")
        if (post) {
            requestBuilder = requestBuilder.url(targetUrl).post(RequestBody.create(DNS_MESSAGE, query))
        } else {
            val encoded = query.base64Url().replace("=", "")
            val requestUrl = targetUrl.newBuilder().addQueryParameter("dns", encoded).build()
            requestBuilder = requestBuilder.url(requestUrl)
        }

        return requestBuilder.build()
    }

    class Builder {
        internal var client: OkHttpClient? = null
        internal var url: HttpUrl? = null
        internal var includeIPv6 = true
        internal var post = false
        internal var systemDns = Dns.SYSTEM
        internal var bootstrapDnsHosts: List<InetAddress>? = null
        internal var resolvePrivateAddresses = false
        internal var resolvePublicAddresses = true

        fun build(): DnsOverHttps = DnsOverHttps(this)

        fun client(client: OkHttpClient?): Builder {
            this.client = client
            return this
        }

        fun url(url: HttpUrl?): Builder {
            this.url = url
            return this
        }

        fun includeIPv6(includeIPv6: Boolean): Builder {
            this.includeIPv6 = includeIPv6
            return this
        }

        fun post(post: Boolean): Builder {
            this.post = post
            return this
        }

        fun resolvePrivateAddresses(resolvePrivateAddresses: Boolean): Builder {
            this.resolvePrivateAddresses = resolvePrivateAddresses
            return this
        }

        fun resolvePublicAddresses(resolvePublicAddresses: Boolean): Builder {
            this.resolvePublicAddresses = resolvePublicAddresses
            return this
        }

        fun bootstrapDnsHosts(bootstrapDnsHosts: List<InetAddress>?): Builder {
            this.bootstrapDnsHosts = bootstrapDnsHosts
            return this
        }

        fun bootstrapDnsHosts(vararg bootstrapDnsHosts: InetAddress): Builder {
            return bootstrapDnsHosts(Arrays.asList(*bootstrapDnsHosts))
        }

        fun systemDns(systemDns: Dns): Builder {
            this.systemDns = systemDns
            return this
        }
    }

    companion object {
        @JvmField
        val DNS_MESSAGE = MediaType.get("application/dns-message")
        const val MAX_RESPONSE_SIZE = 64 * 1024

        @JvmStatic
        private fun buildBootstrapClient(builder: Builder): Dns {
            val hosts = builder.bootstrapDnsHosts
            return if (hosts != null && builder.url != null) {
                BootstrapDns(builder.url!!.host(), hosts)
            } else {
                builder.systemDns
            }
        }

        @JvmStatic
        fun isPrivateHost(host: String): Boolean {
            return PublicSuffixDatabase.get().getEffectiveTldPlusOne(host) == null
        }
    }
}
