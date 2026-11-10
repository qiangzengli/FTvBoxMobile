package com.github.tvbox.osc.subtitle.runtime

abstract class TaskExecutor {

    abstract fun executeOnDeskIO(task: Runnable)

    open fun executeOnMainThread(task: Runnable) {
        if (isMainThread()) {
            task.run()
        } else {
            postToMainThread(task)
        }
    }

    abstract fun postToMainThread(task: Runnable)

    abstract fun isMainThread(): Boolean
}
