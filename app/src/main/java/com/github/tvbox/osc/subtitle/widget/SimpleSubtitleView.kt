package com.github.tvbox.osc.subtitle.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import com.github.tvbox.osc.cache.CacheManager
import com.github.tvbox.osc.subtitle.DefaultSubtitleEngine
import com.github.tvbox.osc.subtitle.SubtitleEngine
import com.github.tvbox.osc.subtitle.model.Subtitle
import com.github.tvbox.osc.util.MD5
import xyz.doikki.videoplayer.player.AbstractPlayer

@SuppressLint("AppCompatCustomView")
class SimpleSubtitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr),
    SubtitleEngine,
    SubtitleEngine.OnSubtitleChangeListener,
    SubtitleEngine.OnSubtitlePreparedListener {

    private lateinit var mSubtitleEngine: SubtitleEngine

    @JvmField
    var isInternal: Boolean = false

    @JvmField
    var hasInternal: Boolean = false

    // TextView can invoke overridden methods before this subclass is initialized.
    private var backGroundText: TextView? = null
    private var lyricMode: Boolean = false

    init {
        backGroundText = TextView(context, attrs, defStyleAttr)
        mSubtitleEngine = DefaultSubtitleEngine()
        mSubtitleEngine.setOnSubtitlePreparedListener(this)
        mSubtitleEngine.setOnSubtitleChangeListener(this)
    }

    override fun onSubtitlePrepared(subtitles: List<Subtitle>?) {
        start()
    }

    override fun onSubtitleChanged(subtitle: Subtitle?) {
        if (subtitle == null) {
            text = EMPTY_TEXT
            return
        }
        val lines = subtitle.lines
        if (lyricMode && lines != null) {
            val builder = SpannableStringBuilder()
            for (i in lines.size - 1 downTo 0) {
                val line = lines[i]
                if (i > 0) builder.append('\n')
                val start = builder.length
                builder.append(Html.fromHtml(formatText(line.content)))
                val end = builder.length
                builder.setSpan(
                    ForegroundColorSpan(if (line.lyricCurrent) Color.GREEN else Color.YELLOW),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            text = builder
            invalidate()
            return
        }
        text = Html.fromHtml(formatText(subtitle.content))
    }

    private fun formatText(textParam: String?): String {
        if (textParam == null) return EMPTY_TEXT
        var formatted = textParam.replace("(?:\r\n)".toRegex(), "<br />")
        formatted = formatted.replace("(?:\r)".toRegex(), "<br />")
        formatted = formatted.replace("(?:\n)".toRegex(), "<br />")
        formatted = formatted.replace("\\\\N".toRegex(), "<br />")
        formatted = formatted.replace("\\{[\\s\\S]*?\\}".toRegex(), "")
        formatted = formatted.replace("^.*?,.*?,.*?,.*?,.*?,.*?,.*?,.*?,.*?,", "")
        return formatted
    }

    override fun setSubtitlePath(path: String) {
        isInternal = false
        mSubtitleEngine.setSubtitlePath(path)
    }

    override fun setSubtitleDelay(milliseconds: Int?) {
        mSubtitleEngine.setSubtitleDelay(milliseconds)
    }

    override fun setPlaySubtitleCacheKey(cacheKey: String) {
        mSubtitleEngine.setPlaySubtitleCacheKey(cacheKey)
    }

    fun setMergeSameTime(mergeSameTime: Boolean) {
        val engine = mSubtitleEngine
        if (engine is DefaultSubtitleEngine) {
            engine.setMergeSameTime(mergeSameTime)
        }
    }

    fun setLyricMode(lyricMode: Boolean) {
        this.lyricMode = lyricMode
    }

    override fun getPlaySubtitleCacheKey(): String? {
        return mSubtitleEngine.getPlaySubtitleCacheKey()
    }

    fun clearSubtitleCache() {
        val subtitleCacheKey = getPlaySubtitleCacheKey()
        if (!subtitleCacheKey.isNullOrEmpty()) {
            CacheManager.delete(MD5.string2MD5(subtitleCacheKey), "")
        }
    }

    override fun reset() {
        mSubtitleEngine.reset()
    }

    override fun start() {
        mSubtitleEngine.start()
    }

    override fun pause() {
        mSubtitleEngine.pause()
    }

    override fun resume() {
        mSubtitleEngine.resume()
    }

    override fun stop() {
        mSubtitleEngine.stop()
    }

    override fun destroy() {
        mSubtitleEngine.destroy()
    }

    override fun bindToMediaPlayer(mediaPlayer: AbstractPlayer) {
        mSubtitleEngine.bindToMediaPlayer(mediaPlayer)
    }

    override fun setOnSubtitlePreparedListener(listener: SubtitleEngine.OnSubtitlePreparedListener?) {
        mSubtitleEngine.setOnSubtitlePreparedListener(listener)
    }

    override fun setOnSubtitleChangeListener(listener: SubtitleEngine.OnSubtitleChangeListener?) {
        mSubtitleEngine.setOnSubtitleChangeListener(listener)
    }

    override fun onDetachedFromWindow() {
        destroy()
        super.onDetachedFromWindow()
    }

    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        backGroundText?.layoutParams = params
        super.setLayoutParams(params)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val background = backGroundText
        if (background == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val tt = background.text
        val currentText = if (lyricMode) text.toString() else text
        if (TextUtils.isEmpty(tt) || tt != currentText) {
            background.text = currentText
            postInvalidate()
        }
        background.measure(widthMeasureSpec, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun setTextSize(size: Float) {
        super.setTextSize(size)
        backGroundText?.setTextSize(size)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        backGroundText?.text = if (lyricMode && text != null) text.toString() else text
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        backGroundText?.layout(left, top, right, bottom)
        super.onLayout(changed, left, top, right, bottom)
    }

    override fun onDraw(canvas: Canvas) {
        backGroundText?.let { background ->
            drawBackGroundText(background)
            background.draw(canvas)
        }
        super.onDraw(canvas)
    }

    private fun drawBackGroundText(background: TextView) {
        val tp = background.paint
        tp.strokeWidth = 10f
        tp.style = Paint.Style.FILL_AND_STROKE
        background.setTextColor(Color.BLACK)
        background.gravity = gravity
    }

    companion object {
        private const val EMPTY_TEXT = ""
    }
}
