package com.github.tvbox.osc.picasso

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.IntDef
import com.squareup.picasso.Transformation

/**
 * 描述
 *
 * @author pj567
 * @since 2020/12/22
 */
class RoundTransformation(private var key: String = "") : Transformation {
    private var viewWidth = 0
    private var viewHeight = 0
    private var bottomShapeHeight = 0

    @RoundType
    private var mRoundType = RoundType.NONE
    private var diameter = 0
    private var radius = 0
    private var isCenterCorp = true // 垂直方向不是中间裁剪，就是顶部

    fun override(width: Int, height: Int): RoundTransformation {
        this.viewWidth = width
        this.viewHeight = height
        return this
    }

    fun centerCorp(centerCorp: Boolean): RoundTransformation {
        this.isCenterCorp = centerCorp
        return this
    }

    fun bottomShapeHeight(shapeHeight: Int): RoundTransformation {
        this.bottomShapeHeight = shapeHeight
        return this
    }

    fun roundRadius(radius: Int, @RoundType mRoundType: Int): RoundTransformation {
        this.radius = radius
        this.diameter = radius * 2
        this.mRoundType = mRoundType
        return this
    }

    override fun transform(source: Bitmap): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height
        if (viewWidth == 0 || viewHeight == 0) {
            viewWidth = sourceWidth
            viewHeight = sourceHeight
        }
        val scale: Float
        val targetWidth: Int
        val targetHeight: Int
        if (sourceWidth != viewWidth || sourceHeight != viewHeight) {
            if (sourceWidth * 1f / viewWidth > sourceHeight * 1f / viewHeight) {
                scale = viewHeight.toFloat() / sourceHeight
                targetWidth = (sourceWidth * scale).toInt()
                targetHeight = viewHeight
            } else {
                scale = viewWidth.toFloat() / sourceWidth
                targetWidth = viewWidth
                targetHeight = (sourceHeight * scale).toInt()
            }
        } else {
            scale = 1f
            targetWidth = sourceWidth
            targetHeight = sourceHeight
        }
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        if (scale != 1f) {
            val matrix = Matrix()
            matrix.setScale(scale, scale)
            shader.setLocalMatrix(matrix)
        }
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        bitmap.setHasAlpha(true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = shader
        val canvas = Canvas(bitmap)
        val rect = RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat())
        canvas.drawRoundRect(rect, radius.toFloat(), radius.toFloat(), paint)

        source.recycle()
        return bitmap
    }

    private fun drawBottomLabel(mCanvas: Canvas, mPaint: Paint, left: Float, top: Float, right: Float, bottom: Float) {
        if (bottomShapeHeight <= 0) return
        mPaint.shader = null
        mPaint.color = -0x66000000 // 0x99000000
        mCanvas.drawPath(RoundedRect(left, bottom - bottomShapeHeight * 2, right, bottom, radius.toFloat(), radius.toFloat(), false, false, true, true), mPaint)
    }

    private fun drawRoundRect(mCanvas: Canvas, mPaint: Paint, width: Float, height: Float) {
        when (mRoundType) {
            RoundType.NONE -> {
                if (viewWidth.toFloat() == width && viewHeight.toFloat() == height) {
                    mCanvas.drawRect(RectF(0f, 0f, width, height), mPaint)
                } else {
                    if (viewWidth.toFloat() == width && viewHeight.toFloat() != height) {
                        val dis = (height - viewHeight) / 2f
                        if (isCenterCorp) {
                            mCanvas.translate(0f, -dis)
                            mCanvas.drawRect(RectF(0f, dis, viewWidth.toFloat(), viewHeight + dis), mPaint)
                        } else {
                            mCanvas.drawRect(RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat()), mPaint)
                        }
                    } else {
                        val dis = (width - viewWidth) / 2f
                        mCanvas.translate(-dis, 0f)
                        mCanvas.drawRect(RectF(dis, 0f, viewWidth + dis, viewHeight.toFloat()), mPaint)
                    }
                }
            }
            RoundType.ALL -> {
                if (viewWidth.toFloat() == width && viewHeight.toFloat() == height) {
                    mCanvas.drawRoundRect(RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    drawBottomLabel(mCanvas, mPaint, 0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
                } else if (viewWidth.toFloat() == width && viewHeight.toFloat() != height) {
                    val dis = (height - viewHeight) / 2f
                    if (isCenterCorp) {
                        mCanvas.translate(0f, -dis)
                        mCanvas.drawRoundRect(RectF(0f, dis, viewWidth.toFloat(), viewHeight + dis), radius.toFloat(), radius.toFloat(), mPaint)
                        drawBottomLabel(mCanvas, mPaint, 0f, dis, viewWidth.toFloat(), viewHeight + dis)
                    } else {
                        mCanvas.drawRoundRect(RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                        drawBottomLabel(mCanvas, mPaint, 0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
                    }
                } else {
                    val dis = (width - viewWidth) / 2f
                    mCanvas.translate(-dis, 0f)
                    mCanvas.drawRoundRect(RectF(dis, 0f, viewWidth + dis, viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    drawBottomLabel(mCanvas, mPaint, dis, 0f, viewWidth + dis, viewHeight.toFloat())
                }
            }
            RoundType.TOP -> {
                if (viewWidth.toFloat() == width && viewHeight.toFloat() == height) {
                    mCanvas.drawRoundRect(RectF(0f, 0f, viewWidth.toFloat(), diameter.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(0f, radius.toFloat(), viewWidth.toFloat(), viewHeight.toFloat()), mPaint)
                } else if (viewWidth.toFloat() == width && viewHeight.toFloat() != height) {
                    val dis = (height - viewHeight) / 2f
                    if (isCenterCorp) {
                        mCanvas.translate(0f, -dis)
                        mCanvas.drawRoundRect(RectF(0f, dis, viewWidth.toFloat(), diameter + dis), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(0f, dis + radius, viewWidth.toFloat(), viewHeight + dis), mPaint)
                    } else {
                        mCanvas.drawRoundRect(RectF(0f, 0f, viewWidth.toFloat(), diameter.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(0f, radius.toFloat(), viewWidth.toFloat(), viewHeight.toFloat()), mPaint)
                    }
                } else {
                    val dis = (width - viewWidth) / 2f
                    mCanvas.translate(-dis, 0f)
                    mCanvas.drawRoundRect(RectF(dis, 0f, viewWidth + dis, diameter.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(dis, radius.toFloat(), viewWidth + dis, viewHeight.toFloat()), mPaint)
                }
            }
            RoundType.RIGHT -> {
                if (viewWidth.toFloat() == width && viewHeight.toFloat() == height) {
                    mCanvas.drawRoundRect(RectF(viewWidth - diameter.toFloat(), 0f, viewWidth.toFloat(), viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(0f, 0f, viewWidth - radius.toFloat(), viewHeight.toFloat()), mPaint)
                } else if (viewWidth.toFloat() == width && viewHeight.toFloat() != height) {
                    val dis = (height - viewHeight) / 2f
                    if (isCenterCorp) {
                        mCanvas.translate(0f, -dis)
                        mCanvas.drawRoundRect(RectF(viewWidth - diameter.toFloat(), dis, viewWidth.toFloat(), viewHeight + dis), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(0f, dis, viewWidth - radius.toFloat(), viewHeight + dis), mPaint)
                    } else {
                        mCanvas.drawRoundRect(RectF(viewWidth - diameter.toFloat(), 0f, viewWidth.toFloat(), viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(0f, 0f, viewWidth - radius.toFloat(), viewHeight.toFloat()), mPaint)
                    }
                } else {
                    val dis = (width - viewWidth) / 2f
                    mCanvas.translate(-dis, 0f)
                    mCanvas.drawRoundRect(RectF(viewWidth - diameter + dis, 0f, viewWidth + dis, viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(dis, 0f, viewWidth - radius + dis, viewHeight.toFloat()), mPaint)
                }
            }
            RoundType.BOTTOM -> {
                if (viewWidth.toFloat() == width && viewHeight.toFloat() == height) {
                    mCanvas.drawRoundRect(RectF(0f, viewHeight - diameter.toFloat(), viewWidth.toFloat(), viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(0f, 0f, viewWidth.toFloat(), viewHeight - radius.toFloat()), mPaint)
                } else if (viewWidth.toFloat() == width && viewHeight.toFloat() != height) {
                    val dis = (height - viewHeight) / 2f
                    if (isCenterCorp) {
                        mCanvas.translate(0f, -dis)
                        mCanvas.drawRoundRect(RectF(0f, viewHeight - diameter + dis, viewWidth.toFloat(), viewHeight + dis), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(0f, dis, viewWidth.toFloat(), viewHeight - radius + dis), mPaint)
                    } else {
                        mCanvas.drawRoundRect(RectF(0f, viewHeight - diameter.toFloat(), viewWidth.toFloat(), viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(0f, 0f, viewWidth.toFloat(), viewHeight - radius.toFloat()), mPaint)
                    }
                } else {
                    val dis = (width - viewWidth) / 2f
                    mCanvas.translate(-dis, 0f)
                    mCanvas.drawRoundRect(RectF(dis, viewHeight - diameter.toFloat(), viewWidth + dis, viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(dis, 0f, viewWidth + dis, viewHeight - radius.toFloat()), mPaint)
                }
            }
            RoundType.LEFT -> {
                if (viewWidth.toFloat() == width && viewHeight.toFloat() == height) {
                    mCanvas.drawRoundRect(RectF(0f, 0f, diameter.toFloat(), viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(radius.toFloat(), 0f, viewWidth.toFloat(), viewHeight.toFloat()), mPaint)
                } else if (viewWidth.toFloat() == width && viewHeight.toFloat() != height) {
                    val dis = (height - viewHeight) / 2f
                    if (isCenterCorp) {
                        mCanvas.translate(0f, -dis)
                        mCanvas.drawRoundRect(RectF(0f, dis, diameter.toFloat(), viewHeight + dis), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(radius.toFloat(), dis, viewWidth.toFloat(), viewHeight + dis), mPaint)
                    } else {
                        mCanvas.drawRoundRect(RectF(0f, 0f, diameter.toFloat(), viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(radius.toFloat(), 0f, viewWidth.toFloat(), viewHeight.toFloat()), mPaint)
                    }
                } else {
                    val dis = (width - viewWidth) / 2f
                    mCanvas.translate(-dis, 0f)
                    mCanvas.drawRoundRect(RectF(dis, 0f, diameter + dis, viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(radius + dis, 0f, viewWidth + dis, viewHeight.toFloat()), mPaint)
                }
            }
            RoundType.LEFT_TOP -> {
                if (viewWidth.toFloat() == width && viewHeight.toFloat() == height) {
                    mCanvas.drawRoundRect(RectF(0f, 0f, diameter.toFloat(), diameter.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(radius.toFloat(), 0f, viewWidth.toFloat(), radius.toFloat()), mPaint)
                    mCanvas.drawRect(RectF(0f, radius.toFloat(), viewWidth.toFloat(), viewHeight.toFloat()), mPaint)
                } else if (viewWidth.toFloat() == width && viewHeight.toFloat() != height) {
                    val dis = (height - viewHeight) / 2f
                    if (isCenterCorp) {
                        mCanvas.translate(0f, -dis)
                        mCanvas.drawRoundRect(RectF(0f, dis, diameter.toFloat(), diameter + dis), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(radius.toFloat(), dis, viewWidth.toFloat(), radius + dis), mPaint)
                        mCanvas.drawRect(RectF(0f, radius + dis, viewWidth.toFloat(), viewHeight + dis), mPaint)
                    } else {
                        mCanvas.drawRoundRect(RectF(0f, 0f, diameter.toFloat(), diameter.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(radius.toFloat(), 0f, viewWidth.toFloat(), radius.toFloat()), mPaint)
                        mCanvas.drawRect(RectF(0f, radius.toFloat(), viewWidth.toFloat(), viewHeight.toFloat()), mPaint)
                    }
                } else {
                    val dis = (width - viewWidth) / 2f
                    mCanvas.translate(-dis, 0f)
                    mCanvas.drawRoundRect(RectF(dis, 0f, diameter + dis, diameter.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(radius + dis, 0f, viewWidth + dis, radius.toFloat()), mPaint)
                    mCanvas.drawRect(RectF(dis, radius.toFloat(), viewWidth + dis, viewHeight.toFloat()), mPaint)
                }
            }
            RoundType.LEFT_BOTTOM -> {
                if (viewWidth.toFloat() == width && viewHeight.toFloat() == height) {
                    mCanvas.drawRoundRect(RectF(0f, viewHeight - diameter.toFloat(), diameter.toFloat(), viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(0f, 0f, viewWidth.toFloat(), viewHeight - radius.toFloat()), mPaint)
                    mCanvas.drawRect(RectF(radius.toFloat(), viewHeight - radius.toFloat(), viewWidth.toFloat(), viewHeight.toFloat()), mPaint)
                } else if (viewWidth.toFloat() == width && viewHeight.toFloat() != height) {
                    val dis = (height - viewHeight) / 2f
                    if (isCenterCorp) {
                        mCanvas.translate(0f, -dis)
                        mCanvas.drawRoundRect(RectF(0f, viewHeight - diameter + dis, diameter.toFloat(), viewHeight + dis), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(0f, dis, viewWidth.toFloat(), viewHeight - radius + dis), mPaint)
                        mCanvas.drawRect(RectF(radius.toFloat(), viewHeight - radius + dis, viewWidth.toFloat(), viewHeight + dis), mPaint)
                    } else {
                        mCanvas.drawRoundRect(RectF(0f, viewHeight - diameter.toFloat(), diameter.toFloat(), viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(0f, 0f, viewWidth.toFloat(), viewHeight - radius.toFloat()), mPaint)
                        mCanvas.drawRect(RectF(radius.toFloat(), viewHeight - radius.toFloat(), viewWidth.toFloat(), viewHeight.toFloat()), mPaint)
                    }
                } else {
                    val dis = (width - viewWidth) / 2f
                    mCanvas.translate(-dis, 0f)
                    mCanvas.drawRoundRect(RectF(dis, viewHeight - diameter.toFloat(), diameter + dis, viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(dis, 0f, viewWidth + dis, viewHeight - radius.toFloat()), mPaint)
                    mCanvas.drawRect(RectF(radius + dis, viewHeight - radius.toFloat(), viewWidth + dis, viewHeight.toFloat()), mPaint)
                }
            }
            RoundType.RIGHT_TOP -> {
                if (viewWidth.toFloat() == width && viewHeight.toFloat() == height) {
                    mCanvas.drawRoundRect(RectF(viewWidth - diameter.toFloat(), 0f, viewWidth.toFloat(), diameter.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(0f, 0f, viewWidth - radius.toFloat(), radius.toFloat()), mPaint)
                    mCanvas.drawRect(RectF(0f, radius.toFloat(), viewWidth.toFloat(), viewHeight.toFloat()), mPaint)
                } else if (viewWidth.toFloat() == width && viewHeight.toFloat() != height) {
                    val dis = (height - viewHeight) / 2f
                    if (isCenterCorp) {
                        mCanvas.translate(0f, -dis)
                        mCanvas.drawRoundRect(RectF(viewWidth - diameter.toFloat(), dis, viewWidth.toFloat(), diameter + dis), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(0f, dis, viewWidth - radius.toFloat(), radius + dis), mPaint)
                        mCanvas.drawRect(RectF(0f, radius + dis, viewWidth.toFloat(), viewHeight + dis), mPaint)
                    } else {
                        mCanvas.drawRoundRect(RectF(viewWidth - diameter.toFloat(), 0f, viewWidth.toFloat(), diameter.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(0f, 0f, viewWidth - radius.toFloat(), radius.toFloat()), mPaint)
                        mCanvas.drawRect(RectF(0f, radius.toFloat(), viewWidth.toFloat(), viewHeight.toFloat()), mPaint)
                    }
                } else {
                    val dis = (width - viewWidth) / 2f
                    mCanvas.translate(-dis, 0f)
                    mCanvas.drawRoundRect(RectF(viewWidth - diameter + dis, 0f, viewWidth + dis, diameter.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(dis, 0f, viewWidth - radius + dis, radius.toFloat()), mPaint)
                    mCanvas.drawRect(RectF(dis, radius.toFloat(), viewWidth + dis, viewHeight.toFloat()), mPaint)
                }
            }
            RoundType.RIGHT_BOTTOM -> {
                if (viewWidth.toFloat() == width && viewHeight.toFloat() == height) {
                    mCanvas.drawRoundRect(RectF(viewWidth - diameter.toFloat(), viewHeight - diameter.toFloat(), viewWidth.toFloat(), viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(0f, 0f, viewWidth.toFloat(), viewHeight - radius.toFloat()), mPaint)
                    mCanvas.drawRect(RectF(0f, viewHeight - radius.toFloat(), viewWidth - radius.toFloat(), viewHeight.toFloat()), mPaint)
                } else if (viewWidth.toFloat() == width && viewHeight.toFloat() != height) {
                    val dis = (height - viewHeight) / 2f
                    if (isCenterCorp) {
                        mCanvas.translate(0f, -dis)
                        mCanvas.drawRoundRect(RectF(viewWidth - diameter.toFloat(), viewHeight - diameter + dis, viewWidth.toFloat(), viewHeight + dis), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(0f, dis, viewWidth.toFloat(), viewHeight - radius + dis), mPaint)
                        mCanvas.drawRect(RectF(0f, viewHeight - radius + dis, viewWidth - radius.toFloat(), viewHeight + dis), mPaint)
                    } else {
                        mCanvas.drawRoundRect(RectF(viewWidth - diameter.toFloat(), viewHeight - diameter.toFloat(), viewWidth.toFloat(), viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                        mCanvas.drawRect(RectF(0f, 0f, viewWidth.toFloat(), viewHeight - radius.toFloat()), mPaint)
                        mCanvas.drawRect(RectF(0f, viewHeight - radius.toFloat(), viewWidth - radius.toFloat(), viewHeight.toFloat()), mPaint)
                    }
                } else {
                    val dis = (width - viewWidth) / 2f
                    mCanvas.translate(-dis, 0f)
                    mCanvas.drawRoundRect(RectF(viewWidth - diameter + dis, viewHeight - diameter.toFloat(), viewWidth + dis, viewHeight.toFloat()), radius.toFloat(), radius.toFloat(), mPaint)
                    mCanvas.drawRect(RectF(dis, 0f, viewWidth + dis, viewHeight - radius.toFloat()), mPaint)
                    mCanvas.drawRect(RectF(dis, viewHeight - radius.toFloat(), viewWidth - radius + dis, viewHeight.toFloat()), mPaint)
                }
            }
        }
    }

    override fun key(): String {
        return key
    }

    @IntDef(RoundType.ALL, RoundType.TOP, RoundType.RIGHT, RoundType.BOTTOM, RoundType.LEFT, RoundType.LEFT_TOP, RoundType.LEFT_BOTTOM, RoundType.RIGHT_TOP, RoundType.RIGHT_BOTTOM, RoundType.NONE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class RoundType {
        companion object {
            const val ALL = 0
            const val TOP = 1
            const val RIGHT = 2
            const val BOTTOM = 3
            const val LEFT = 4
            const val LEFT_TOP = 5
            const val LEFT_BOTTOM = 6
            const val RIGHT_TOP = 7
            const val RIGHT_BOTTOM = 8
            const val NONE = 9
        }
    }

    companion object {
        @JvmStatic
        fun RoundedRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, tl: Boolean, tr: Boolean, br: Boolean, bl: Boolean): Path {
            var rX = rx
            var rY = ry
            val path = Path()
            if (rX < 0) rX = 0f
            if (rY < 0) rY = 0f
            val width = right - left
            val height = bottom - top
            if (rX > width / 2) rX = width / 2
            if (rY > height / 2) rY = height / 2
            val widthMinusCorners = width - 2 * rX
            val heightMinusCorners = height - 2 * rY

            path.moveTo(right, top + rY)
            if (tr) {
                path.rQuadTo(0f, -rY, -rX, -rY)
            } else {
                path.rLineTo(0f, -rY)
                path.rLineTo(-rX, 0f)
            }
            path.rLineTo(-widthMinusCorners, 0f)
            if (tl) {
                path.rQuadTo(-rX, 0f, -rX, rY)
            } else {
                path.rLineTo(-rX, 0f)
                path.rLineTo(0f, rY)
            }
            path.rLineTo(0f, heightMinusCorners)

            if (bl) {
                path.rQuadTo(0f, rY, rX, rY)
            } else {
                path.rLineTo(0f, rY)
                path.rLineTo(rX, 0f)
            }

            path.rLineTo(widthMinusCorners, 0f)
            if (br) {
                path.rQuadTo(rX, 0f, rX, -rY)
            } else {
                path.rLineTo(rX, 0f)
                path.rLineTo(0f, -rY)
            }

            path.rLineTo(0f, -heightMinusCorners)
            path.close()
            return path
        }
    }
}
