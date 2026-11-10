package xyz.doikki.videoplayer.ijk

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import tv.danmaku.ijk.media.player.misc.IMediaDataSource
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class RawDataSourceProvider(private var mDescriptor: AssetFileDescriptor?) : IMediaDataSource {

    private var mMediaBytes: ByteArray? = null

    override fun readAt(position: Long, buffer: ByteArray?, offset: Int, size: Int): Int {
        val mediaBytes = mMediaBytes ?: return -1
        if (buffer == null || position + 1 >= mediaBytes.size) {
            return -1
        }

        var length: Int
        if (position + size < mediaBytes.size) {
            length = size
        } else {
            length = (mediaBytes.size - position).toInt()
            if (length > buffer.size) {
                length = buffer.size
            }
            length--
        }
        System.arraycopy(mediaBytes, position.toInt(), buffer, offset, length)
        return length
    }

    @Throws(IOException::class)
    override fun getSize(): Long {
        val descriptor = mDescriptor ?: return 0
        val length = descriptor.length
        if (mMediaBytes == null) {
            val inputStream = descriptor.createInputStream()
            mMediaBytes = readBytes(inputStream)
        }
        return length
    }

    @Throws(IOException::class)
    override fun close() {
        mDescriptor?.close()
        mDescriptor = null
        mMediaBytes = null
    }

    @Throws(IOException::class)
    private fun readBytes(inputStream: InputStream): ByteArray {
        val byteBuffer = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var len: Int
        while (inputStream.read(buffer).also { len = it } != -1) {
            byteBuffer.write(buffer, 0, len)
        }
        return byteBuffer.toByteArray()
    }

    companion object {
        @JvmStatic
        fun create(context: Context, uri: Uri): RawDataSourceProvider? {
            return try {
                val fileDescriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
                RawDataSourceProvider(fileDescriptor)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                null
            }
        }
    }
}
