package com.github.tvbox.osc.subtitle

import com.github.tvbox.osc.subtitle.model.Subtitle
import com.github.tvbox.osc.subtitle.runtime.AppTaskExecutor

class UIRenderTask(private val mOnSubtitleChangeListener: SubtitleEngine.OnSubtitleChangeListener?) : Runnable {

    private var mSubtitle: Subtitle? = null

    override fun run() {
        mOnSubtitleChangeListener?.onSubtitleChanged(mSubtitle)
    }

    fun execute(subtitle: Subtitle?) {
        mSubtitle = subtitle
        AppTaskExecutor.mainThread().execute(this)
    }
}
