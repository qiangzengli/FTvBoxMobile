package xyz.doikki.videoplayer.render

import android.graphics.Bitmap
import android.view.View
import xyz.doikki.videoplayer.player.AbstractPlayer

interface IRenderView {

    /**
     * 关联AbstractPlayer
     */
    fun attachToPlayer(player: AbstractPlayer)

    /**
     * 设置视频宽高
     * @param videoWidth 宽
     * @param videoHeight 高
     */
    fun setVideoSize(videoWidth: Int, videoHeight: Int)

    /**
     * 设置视频旋转角度
     * @param degree 角度值
     */
    fun setVideoRotation(degree: Int)

    /**
     * 设置screen scale type
     * @param scaleType 类型
     */
    fun setScaleType(scaleType: Int)

    /**
     * 获取真实的RenderView
     */
    fun getView(): View

    /**
     * 截图
     */
    fun doScreenShot(): Bitmap?

    /**
     * 释放资源
     */
    fun release()
}
