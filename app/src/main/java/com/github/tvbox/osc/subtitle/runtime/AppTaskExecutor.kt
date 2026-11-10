package com.github.tvbox.osc.subtitle.runtime

import java.util.concurrent.Executor

class AppTaskExecutor private constructor() : TaskExecutor() {

    private val mDefaultTaskExecutor: TaskExecutor = DefaultTaskExecutor()
    private var mDelegate: TaskExecutor = mDefaultTaskExecutor

    fun setDelegate(taskExecutor: TaskExecutor?) {
        mDelegate = taskExecutor ?: mDefaultTaskExecutor
    }

    override fun executeOnDeskIO(task: Runnable) {
        mDelegate.executeOnDeskIO(task)
    }

    override fun executeOnMainThread(task: Runnable) {
        mDelegate.executeOnMainThread(task)
    }

    override fun postToMainThread(task: Runnable) {
        mDelegate.postToMainThread(task)
    }

    override fun isMainThread(): Boolean {
        return mDelegate.isMainThread()
    }

    companion object {
        @Volatile
        private var sInstance: AppTaskExecutor? = null

        @JvmStatic
        fun getInstance(): TaskExecutor {
            if (sInstance == null) {
                synchronized(AppTaskExecutor::class.java) {
                    if (sInstance == null) {
                        sInstance = AppTaskExecutor()
                    }
                }
            }
            return sInstance!!
        }

        private val sDeskIO = Executor { command ->
            getInstance().executeOnDeskIO(command)
        }

        private val sMainThread = Executor { command ->
            getInstance().executeOnMainThread(command)
        }

        @JvmStatic
        fun deskIO(): Executor {
            return sDeskIO
        }

        @JvmStatic
        fun mainThread(): Executor {
            return sMainThread
        }
    }
}
