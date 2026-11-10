package com.fbox.mobile.auth

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

object AuthLinks {

    @JvmStatic
    fun decode(provider: ConfigAuthProvider, payload: String?): String {
        var url = payload ?: ""
        if (payload != null && payload.startsWith("data:image/") && payload.length < 1000000) {
            var bitmap: Bitmap? = null
            try {
                val comma = payload.indexOf(',')
                if (comma < 0 || !payload.substring(0, comma).endsWith(";base64")) return ""
                val data = Base64.decode(payload.substring(comma + 1), Base64.DEFAULT)
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(data, 0, data.size, options)
                if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth > 2048 || options.outHeight > 2048) {
                    return ""
                }
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (bitmap == null) return ""
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                url = MultiFormatReader().decode(
                    BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
                ).text
            } catch (e: Exception) {
                return ""
            } finally {
                bitmap?.recycle()
            }
        }
        return if (provider.allows(url)) url else ""
    }

    @JvmStatic
    fun intent(provider: ConfigAuthProvider, url: String?, packageName: String?): Intent? {
        if (!provider.allows(url)) return null
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        if (packageName != null) {
            var allowed = false
            for (candidate in provider.packages) {
                if (candidate == packageName) allowed = true
            }
            if (!allowed) return null
            intent.setPackage(packageName)
        }
        return intent
    }

    @JvmStatic
    fun openApp(context: Context, provider: ConfigAuthProvider, url: String?): Boolean {
        for (packageName in provider.packages) {
            val intent = intent(provider, url, packageName)
            if (intent == null || intent.resolveActivity(context.packageManager) == null) continue
            try {
                context.startActivity(intent)
                return true
            } catch (ignored: RuntimeException) {
            }
        }
        return false
    }

    @JvmStatic
    fun openWeb(context: Context, provider: ConfigAuthProvider, url: String?): Boolean {
        val intent = intent(provider, url, null) ?: return false
        return try {
            context.startActivity(intent)
            true
        } catch (ignored: RuntimeException) {
            false
        }
    }
}
