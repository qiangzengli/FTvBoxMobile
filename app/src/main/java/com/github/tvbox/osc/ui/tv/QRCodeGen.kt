package com.github.tvbox.osc.ui.tv

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.util.HashMap

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
object QRCodeGen {

    @JvmStatic
    @JvmOverloads
    fun generateBitmap(content: String?, width: Int, height: Int, padding: Int = 0): Bitmap? {
        if (content == null) return null
        val qrCodeWriter = QRCodeWriter()
        val hints: MutableMap<EncodeHintType, String> = HashMap()
        hints[EncodeHintType.CHARACTER_SET] = "utf-8"
        hints[EncodeHintType.MARGIN] = padding.toString()
        try {
            val encode: BitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints)
            val pixels = IntArray(width * height)
            for (i in 0 until height) {
                for (j in 0 until width) {
                    if (encode.get(j, i)) {
                        pixels[i * width + j] = 0x00000000
                    } else {
                        pixels[i * width + j] = -0x1 // 0xffffffff
                    }
                }
            }
            return Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.RGB_565)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
        return null
    }
}
