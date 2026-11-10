package com.github.tvbox.osc.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.App
import com.google.gson.Gson
import com.google.gson.JsonObject
import me.jessyan.autosize.utils.AutoSizeUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.HashMap
import java.util.Random

object ImgUtil {
    private val drawableCache: MutableMap<String, Drawable> = HashMap()
    @JvmField
    var defaultWidth = 244
    @JvmField
    var defaultHeight = 320

    class Style(
        @JvmField var ratio: Float,
        @JvmField var type: String?
    )

    @JvmStatic
    fun isBase64Image(picUrl: String?): Boolean {
        return picUrl != null && picUrl.startsWith("data:image")
    }

    @JvmStatic
    fun initStyle(): Style? {
        val bStyle = ApiConfig.get().getHomeSourceBean().getStyle()
        if (bStyle.isNotEmpty()) {
            try {
                val jsonObject = JSONObject(bStyle)
                return Style(jsonObject.getDouble("ratio").toFloat(), jsonObject.getString("type"))
            } catch (ignored: JSONException) {
            }
        }
        return null
    }

    @JvmStatic
    fun spanCountByStyle(style: Style?, defaultCount: Int): Int {
        if (style == null) return defaultCount
        var spanCount = defaultCount
        if ("rect" == style.type) {
            if (style.ratio >= 1.7) {
                spanCount = 3
            } else if (style.ratio >= 1.3) {
                spanCount = 4
            }
        } else if ("list" == style.type) {
            spanCount = 1
        }
        return spanCount
    }

    @JvmStatic
    fun getStyleDefaultWidth(style: Style?): Int {
        if (style == null) return 280
        var styleDefaultWidth = 280
        if (style.ratio < 1) styleDefaultWidth = 214
        if (style.ratio > 1.7) styleDefaultWidth = 380
        return styleDefaultWidth
    }

    @JvmStatic
    fun decodeBase64ToBitmap(base64Str: String): Bitmap {
        val base64Data = base64Str.substring(base64Str.indexOf(",") + 1)
        val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }

    @JvmStatic
    fun load(url: String?, view: ImageView, roundingRadius: Int) {
        load(url, view, roundingRadius, 0, 0, null)
    }

    @JvmStatic
    fun load(url: String?, view: ImageView, roundingRadius: Int, newWidth: Int, newHeight: Int) {
        load(url, view, roundingRadius, newWidth, newHeight, null)
    }

    @JvmStatic
    fun load(url: String?, view: ImageView, roundingRadius: Int, newWidth: Int, newHeight: Int, label: String?, scaleType: ImageView.ScaleType) {
        var radius = roundingRadius
        view.scaleType = scaleType
        if (radius <= 0) radius = 1
        val fallback = createTextDrawable(if (TextUtils.isEmpty(label)) "TVBox" else label!!, newWidth, newHeight, radius.toFloat())
        val placeholder = createImagePlaceholderDrawable(newWidth, newHeight, radius.toFloat())
        if (isInvalidImageUrl(url)) {
            view.setImageDrawable(fallback)
            return
        }
        var options = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .dontAnimate()
            .transform(FitCenter(), RoundedCorners(radius))
        if (newWidth > 0 && newHeight > 0) {
            options = options.override(newWidth, newHeight)
        }
        Glide.with(App.getInstance())
            .asBitmap()
            .load(getUrl(url!!))
            .placeholder(placeholder)
            .error(fallback)
            .listener(getListener(view, scaleType, fallback))
            .apply(options)
            .into(view)
    }

    @JvmStatic
    fun load(url: String?, view: ImageView, roundingRadius: Int, newWidth: Int, newHeight: Int, label: String?) {
        var radius = roundingRadius
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        if (radius <= 0) radius = 1
        val fallback = createTextDrawable(if (TextUtils.isEmpty(label)) "TVBox" else label!!, newWidth, newHeight, radius.toFloat())
        val placeholder = createImagePlaceholderDrawable(newWidth, newHeight, radius.toFloat())
        if (isInvalidImageUrl(url)) {
            view.setImageDrawable(fallback)
            return
        }
        var options = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .dontAnimate()
            .transform(CenterCrop(), RoundedCorners(radius))
        if (newWidth > 0 && newHeight > 0) {
            options = options.override(newWidth, newHeight)
        }
        Glide.with(App.getInstance())
            .asBitmap()
            .load(getUrl(url!!))
            .placeholder(placeholder)
            .error(fallback)
            .listener(getListener(view, ImageView.ScaleType.CENTER_CROP, fallback))
            .apply(options)
            .into(view)
    }

    @JvmStatic
    fun loadUrl(url: String?, view: ImageView) {
        load(url, view, 10)
    }

    @JvmStatic
    fun loadVideoScreenshot(uri: String?, imageView: ImageView, frameTimeMicros: Long) {
        val requestOptions = RequestOptions.frameOf(frameTimeMicros * 1000)
            .transform(CenterCrop(), RoundedCorners(10))
        Glide.with(App.getInstance())
            .load(uri)
            .skipMemoryCache(true)
            .apply(requestOptions)
            .into(imageView)
    }

    @JvmStatic
    fun getRandomColor(): Int {
        val random = Random()
        return Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256))
    }

    @JvmStatic
    fun createTextDrawable(text: String?): Drawable {
        return createTextDrawable(text, 0, 0, AutoSizeUtils.mm2px(App.getInstance(), 10f).toFloat())
    }

    private fun createTextDrawable(textParam: String?, widthParam: Int, heightParam: Int, cornerRadiusParam: Float): Drawable {
        var text = textParam
        var width = widthParam
        var height = heightParam
        var cornerRadius = cornerRadiusParam
        if (TextUtils.isEmpty(text)) text = "TVBox"
        if (width <= 0) width = 180
        if (height <= 0) height = 240
        if (cornerRadius <= 0) cornerRadius = 1f
        val key = text + "_" + width + "x" + height + "_" + cornerRadius.toInt()
        text = text!!.substring(0, 1)
        if (drawableCache.containsKey(key)) return drawableCache[key]!!
        val randomColor = getRandomColor()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = randomColor
        paint.style = Paint.Style.FILL
        val rectF = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        paint.color = Color.WHITE
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        val fontMetrics = paint.fontMetrics
        val x = width / 2f
        val y = (height - fontMetrics.bottom - fontMetrics.top) / 2f
        canvas.drawText(text, x, y, paint)
        val drawable: Drawable = BitmapDrawable(App.getInstance().resources, bitmap)
        drawableCache[key] = drawable
        return drawable
    }

    private fun createImagePlaceholderDrawable(widthParam: Int, heightParam: Int, cornerRadiusParam: Float): Drawable {
        var width = widthParam
        var height = heightParam
        var cornerRadius = cornerRadiusParam
        if (width <= 0) width = 180
        if (height <= 0) height = 240
        if (cornerRadius <= 0) cornerRadius = 1f
        val key = "placeholder_" + width + "x" + height + "_" + cornerRadius.toInt()
        if (drawableCache.containsKey(key)) return drawableCache[key]!!

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val icon = BitmapFactory.decodeResource(App.getInstance().resources, R.drawable.icon_img_placeholder)
        if (icon != null) {
            val left = (width - icon.width) / 2f
            val top = (height - icon.height) / 2f
            canvas.drawBitmap(icon, left, top, null)
        }

        val drawable: Drawable = BitmapDrawable(App.getInstance().resources, bitmap)
        drawableCache[key] = drawable
        return drawable
    }

    @JvmStatic
    fun clearCache() {
        drawableCache.clear()
    }

    @JvmStatic
    fun clearMemoryCache() {
        clearCache()
        try {
            Glide.get(App.getInstance()).clearMemory()
            LOG.i("echo-img-clear-memory-cache")
        } catch (th: Throwable) {
            LOG.i("echo-img-clear-memory-cache-error:" + th.message)
        }
    }

    private fun getUrl(urlParam: String): Any? {
        var url = urlParam
        if (url.startsWith("data:")) return url
        var header: String? = null
        var referer: String? = null
        var ua: String? = null
        var cookie: String? = null
        if (url.contains("@Headers=")) {
            header = url.split("@Headers=")[1].split("@")[0]
            try {
                header = URLDecoder.decode(header, "UTF-8")
            } catch (ignored: UnsupportedEncodingException) {
            }
        }
        if (url.contains("@Cookie=")) cookie = url.split("@Cookie=")[1].split("@")[0]
        if (url.contains("@User-Agent=")) ua = url.split("@User-Agent=")[1].split("@")[0]
        if (url.contains("@Referer=")) referer = url.split("@Referer=")[1].split("@")[0]
        url = url.split("@")[0]
        if (TextUtils.isEmpty(url)) return null

        val builder = LazyHeaders.Builder()
        val headers: MutableMap<String, String> = HashMap()
        if (!TextUtils.isEmpty(header)) {
            try {
                val jsonInfo = Gson().fromJson(header, JsonObject::class.java)
                for (key in jsonInfo.keySet()) {
                    putHeader(headers, key, jsonInfo.get(key).asString)
                }
            } catch (ignored: Throwable) {
            }
        }
        putHeader(headers, "Cookie", cookie)
        if (!TextUtils.isEmpty(ua)) putHeader(headers, "User-Agent", ua)
        if (!TextUtils.isEmpty(referer)) putHeader(headers, "Referer", referer)
        for ((key, value) in headers) builder.setHeader(key, value)
        return GlideUrl(url, builder.build())
    }

    @JvmStatic
    fun getImageModel(url: String): Any? {
        return getUrl(url)
    }

    private fun isInvalidImageUrl(urlParam: String?): Boolean {
        var url = urlParam
        if (TextUtils.isEmpty(url)) return true
        url = url!!.trim()
        if (TextUtils.isEmpty(url)) return true
        return hasEmptyProxyParam(url, "img")
    }

    private fun hasEmptyProxyParam(url: String, key: String): Boolean {
        if (!url.startsWith("proxy://") && !url.contains("/proxy?")) return false
        val queryIndex = url.indexOf('?')
        val query = if (queryIndex >= 0) url.substring(queryIndex + 1) else url.substring("proxy://".length)
        val pairs = query.split("&".toRegex()).toTypedArray()
        for (pair in pairs) {
            val eqIndex = pair.indexOf('=')
            if (eqIndex < 0) continue
            if (key == pair.substring(0, eqIndex) && TextUtils.isEmpty(pair.substring(eqIndex + 1))) {
                return true
            }
        }
        return false
    }

    private fun putHeader(headers: MutableMap<String, String>, key: String?, value: String?) {
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) return
        headers[key!!] = value!!.trim()
    }

    private fun getListener(view: ImageView, scaleType: ImageView.ScaleType, fallback: Drawable): RequestListener<Bitmap> {
        return object : RequestListener<Bitmap> {
            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean): Boolean {
                view.scaleType = scaleType
                view.setImageDrawable(fallback)
                return true
            }

            override fun onResourceReady(resource: Bitmap, model: Any, target: Target<Bitmap>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                view.scaleType = scaleType
                return false
            }
        }
    }
}
