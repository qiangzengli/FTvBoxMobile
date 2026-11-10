package tv.danmaku.ijk.media.player.misc

import androidx.annotation.Keep
import java.io.IOException

@Keep
interface IMediaDataSource {
    @Throws(IOException::class)
    fun readAt(position: Long, buffer: ByteArray?, offset: Int, size: Int): Int

    @Throws(IOException::class)
    fun getSize(): Long

    @Throws(IOException::class)
    fun close()
}
