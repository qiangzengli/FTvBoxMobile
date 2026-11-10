package xyz.doikki.videoplayer.render

import android.view.View
import xyz.doikki.videoplayer.player.VideoView

class MeasureHelper {

    private var mVideoWidth = 0
    private var mVideoHeight = 0
    private var mCurrentScreenScale = 0
    private var mVideoRotationDegree = 0

    fun setVideoRotation(videoRotationDegree: Int) {
        mVideoRotationDegree = videoRotationDegree
    }

    fun setVideoSize(width: Int, height: Int) {
        mVideoWidth = width
        mVideoHeight = height
    }

    fun setScreenScale(screenScale: Int) {
        mCurrentScreenScale = screenScale
    }

    /**
     * 注意：VideoView的宽高一定要定死，否则以下算法不成立
     */
    fun doMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int): IntArray {
        var wSpec = widthMeasureSpec
        var hSpec = heightMeasureSpec
        if (mVideoRotationDegree == 90 || mVideoRotationDegree == 270) {
            // 软解码时处理旋转信息，交换宽高
            val temp = wSpec
            wSpec = hSpec
            hSpec = temp
        }

        var width = View.MeasureSpec.getSize(wSpec)
        var height = View.MeasureSpec.getSize(hSpec)

        if (mVideoHeight == 0 || mVideoWidth == 0) {
            return intArrayOf(width, height)
        }

        when (mCurrentScreenScale) {
            VideoView.SCREEN_SCALE_ORIGINAL -> {
                width = mVideoWidth
                height = mVideoHeight
            }
            VideoView.SCREEN_SCALE_16_9 -> {
                if (height > width / 16 * 9) {
                    height = width / 16 * 9
                } else {
                    width = height / 9 * 16
                }
            }
            VideoView.SCREEN_SCALE_4_3 -> {
                if (height > width / 4 * 3) {
                    height = width / 4 * 3
                } else {
                    width = height / 3 * 4
                }
            }
            VideoView.SCREEN_SCALE_MATCH_PARENT -> {
                width = wSpec
                height = hSpec
            }
            VideoView.SCREEN_SCALE_CENTER_CROP -> {
                if (mVideoWidth * height > width * mVideoHeight) {
                    width = height * mVideoWidth / mVideoHeight
                } else {
                    height = width * mVideoHeight / mVideoWidth
                }
            }
            VideoView.SCREEN_SCALE_DEFAULT -> {
                if (mVideoWidth * height < width * mVideoHeight) {
                    width = height * mVideoWidth / mVideoHeight
                } else if (mVideoWidth * height > width * mVideoHeight) {
                    height = width * mVideoHeight / mVideoWidth
                }
            }
            else -> {
                if (mVideoWidth * height < width * mVideoHeight) {
                    width = height * mVideoWidth / mVideoHeight
                } else if (mVideoWidth * height > width * mVideoHeight) {
                    height = width * mVideoHeight / mVideoWidth
                }
            }
        }
        return intArrayOf(width, height)
    }
}
