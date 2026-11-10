package com.github.tvbox.osc.subtitle.runtime

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultTaskExecutor : TaskExecutor() {

    @Volatile
    private var mMainHandler: Handler? = null
    private val mLock = Any()
    private val mDeskIO: ExecutorService = Executors.newFixedThreadPool(3)

    override fun executeOnDeskIO(task: Runnable) {
        mDeskIO.execute(task)
    }

    override fun postToMainThread(task: Runnable) {
        if (mMainHandler == null) {
            synchronized(mLock) {
                if (mMainHandler == null) {
                    mMainHandler = Handler(Looper.getMainLooper())
                }
            }
        }
        mMainHandler?.post(task)
    }

    override fun isMainThread(): Boolean {
        return Thread.currentThread() == Looper.getMainLooper().thread
    }
}
