package com.github.tvbox.osc.ui.tv.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.View
import java.util.Random

class AudioWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private lateinit var paint: Paint
    private lateinit var rectF1: RectF
    private lateinit var rectF2: RectF
    private lateinit var rectF3: RectF
    private lateinit var rectF4: RectF
    private lateinit var rectF5: RectF
    private var viewWidth = 0
    private var viewHeight = 0
    /** 每个条的宽度 */
    private var rectWidth = 0
    /** 条数 */
    private val columnCount = 7
    /** 条间距 */
    private val space = 8
    /** 条随机高度 */
    private var randomHeight = 0
    private lateinit var random: Random
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            invalidate()
        }
    }

    init {
        init()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        viewHeight = MeasureSpec.getSize(heightMeasureSpec)
        rectWidth = (viewWidth - space * (columnCount - 1)) / columnCount
    }

    private fun init() {
        paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
        }
        random = Random()
        initRect()
    }

    private fun initRect() {
        rectF1 = RectF()
        rectF2 = RectF()
        rectF3 = RectF()
        rectF4 = RectF()
        rectF5 = RectF()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = rectWidth + space

        if (viewHeight <= 0) return

        randomHeight = random.nextInt(viewHeight)
        rectF1.set((left * 0).toFloat(), randomHeight.toFloat(), (left * 0 + rectWidth).toFloat(), viewHeight.toFloat())
        randomHeight = random.nextInt(viewHeight)
        rectF2.set((left * 1).toFloat(), randomHeight.toFloat(), (left * 1 + rectWidth).toFloat(), viewHeight.toFloat())
        randomHeight = random.nextInt(viewHeight)
        rectF3.set((left * 2).toFloat(), randomHeight.toFloat(), (left * 2 + rectWidth).toFloat(), viewHeight.toFloat())
        randomHeight = random.nextInt(viewHeight)
        rectF4.set((left * 3).toFloat(), randomHeight.toFloat(), (left * 3 + rectWidth).toFloat(), viewHeight.toFloat())
        randomHeight = random.nextInt(viewHeight)
        rectF5.set((left * 4).toFloat(), randomHeight.toFloat(), (left * 4 + rectWidth).toFloat(), viewHeight.toFloat())

        canvas.drawRect(rectF1, paint)
        canvas.drawRect(rectF2, paint)
        canvas.drawRect(rectF3, paint)
        canvas.drawRect(rectF4, paint)
        canvas.drawRect(rectF5, paint)

        handler.sendEmptyMessageDelayed(0, 200)
    }
}
