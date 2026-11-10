package tv.danmaku.ijk.media.player.misc

import androidx.annotation.Keep
import java.io.IOException

@Keep
interface IAndroidIO {
    @Throws(IOException::class)
    fun open(url: String?): Int

    @Throws(IOException::class)
    fun read(buffer: ByteArray?, size: Int): Int

    @Throws(IOException::class)
    fun seek(offset: Long, whence: Int): Long

    @Throws(IOException::class)
    fun close(): Int
}
