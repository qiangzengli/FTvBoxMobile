package okhttp3.dnsoverhttps

import okio.Buffer
import okio.ByteString
import okio.utf8Size
import java.io.EOFException
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.ArrayList

internal object DnsRecordCodec {
    private const val SERVFAIL: Byte = 2
    private const val NXDOMAIN: Byte = 3
    const val TYPE_A = 0x0001
    const val TYPE_AAAA = 0x001c
    private const val TYPE_PTR = 0x000c
    private val ASCII = Charset.forName("ASCII")

    @JvmStatic
    fun encodeQuery(host: String, type: Int): ByteString {
        val buf = Buffer()

        buf.writeShort(0) // query id
        buf.writeShort(256) // flags with recursion
        buf.writeShort(1) // question count
        buf.writeShort(0) // answerCount
        buf.writeShort(0) // authorityResourceCount
        buf.writeShort(0) // additional

        val nameBuf = Buffer()
        val labels = host.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (label in labels) {
            val utf8ByteCount = label.utf8Size()
            if (utf8ByteCount != label.length.toLong()) {
                throw IllegalArgumentException("non-ascii hostname: $host")
            }
            nameBuf.writeByte(utf8ByteCount.toInt())
            nameBuf.writeUtf8(label)
        }
        nameBuf.writeByte(0) // end

        nameBuf.copyTo(buf, 0, nameBuf.size)
        buf.writeShort(type)
        buf.writeShort(1) // CLASS_IN

        return buf.readByteString()
    }

    @JvmStatic
    @Throws(Exception::class)
    fun decodeAnswers(hostname: String, byteString: ByteString): List<InetAddress> {
        val result = ArrayList<InetAddress>()

        val buf = Buffer()
        buf.write(byteString)
        buf.readShort() // query id

        val flags = buf.readShort().toInt() and 0xffff
        if (flags shr 15 == 0) {
            throw IllegalArgumentException("not a response")
        }

        val responseCode = (flags and 0xf).toByte()

        if (responseCode == NXDOMAIN) {
            throw UnknownHostException("$hostname: NXDOMAIN")
        } else if (responseCode == SERVFAIL) {
            throw UnknownHostException("$hostname: SERVFAIL")
        }

        val questionCount = buf.readShort().toInt() and 0xffff
        val answerCount = buf.readShort().toInt() and 0xffff
        buf.readShort() // authority record count
        buf.readShort() // additional record count

        for (i in 0 until questionCount) {
            skipName(buf) // name
            buf.readShort() // type
            buf.readShort() // class
        }

        for (i in 0 until answerCount) {
            skipName(buf) // name

            val type = buf.readShort().toInt() and 0xffff
            buf.readShort() // class
            val ttl = buf.readInt().toLong() and 0xffffffffL // ttl
            val length = buf.readShort().toInt() and 0xffff

            if (type == TYPE_A || type == TYPE_AAAA) {
                val bytes = ByteArray(length)
                buf.read(bytes)
                result.add(InetAddress.getByAddress(bytes))
            } else {
                buf.skip(length.toLong())
            }
        }

        return result
    }

    @Throws(EOFException::class)
    private fun skipName(`in`: Buffer) {
        var length = `in`.readByte().toInt()

        if (length < 0) {
            `in`.skip(1)
        } else {
            while (length > 0) {
                `in`.skip(length.toLong())
                length = `in`.readByte().toInt()
            }
        }
    }
}
