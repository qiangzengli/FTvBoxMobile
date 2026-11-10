package xyz.doikki.videoplayer.exo

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import java.io.IOException

class HlsErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (isChunkError(loadErrorInfo)) {
            return RETRY_DELAY_MS
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
        if (dataType == C.DATA_TYPE_MEDIA) {
            return MAX_RETRIES
        }
        return super.getMinimumLoadableRetryCount(dataType)
    }

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
    ): LoadErrorHandlingPolicy.FallbackSelection? {
        if (isChunkError(loadErrorInfo)) {
            return null
        }
        return super.getFallbackSelectionFor(fallbackOptions, loadErrorInfo)
    }

    private fun isChunkError(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Boolean {
        return loadErrorInfo.exception is IOException
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 500L
    }
}
