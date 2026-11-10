package com.fbox.mobile.auth

import org.json.JSONObject
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class AuthSession(
    private val backend: Backend,
    private val provider: String,
    private val worker: ScheduledExecutorService,
    private val ui: Executor,
    private val listener: Listener,
    private val pollMillis: Long = 1500,
    private val lifetimeMillis: Long = 300000
) : AutoCloseable {

    fun interface Backend {
        @Throws(Exception::class)
        fun request(action: String, fields: Map<String, String>): JSONObject
    }

    interface Listener {
        fun ready(url: String)
        fun waiting()
        fun success()
        fun failed(message: String)
    }

    @Volatile
    private var closed = false
    private var started = false
    private var task = ""
    private var startedAt: Long = 0
    private var failures = 0

    @Synchronized
    fun start() {
        if (started || closed) return
        started = true
        worker.execute {
            startedAt = System.nanoTime()
            try {
                val result = backend.request("loginStart", Collections.singletonMap("provider", provider))
                task = result.optString("taskId")
                if (closed) {
                    cancelTask()
                    return@execute
                }
                if (result.optInt("code", -1) != 0 || task.isEmpty() || result.optString("qrUrl").isEmpty()) {
                    fail("当前线路没有提供可用的手机授权会话，请使用其他登录方式")
                    return@execute
                }
                val url = result.getString("qrUrl")
                deliver { listener.ready(url) }
                schedulePoll()
            } catch (e: Exception) {
                fail("无法创建授权会话，请重试或使用其他登录方式")
            }
        }
    }

    private fun fields(): Map<String, String> {
        val fields = HashMap<String, String>()
        fields["provider"] = provider
        fields["taskId"] = task
        return fields
    }

    @Synchronized
    private fun schedulePoll() {
        if (!closed) worker.schedule({ poll() }, pollMillis, TimeUnit.MILLISECONDS)
    }

    private fun poll() {
        if (closed || task.isEmpty()) return
        if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) >= lifetimeMillis) {
            fail("授权已超时，请重新发起登录")
            return
        }
        try {
            val response = backend.request("loginPoll", fields())
            if (closed) return
            failures = 0
            if ("success" == response.optString("status")) {
                task = ""
                deliver { listener.success() }
                finishWorker()
                return
            }
            if (response.optBoolean("terminal") || response.optInt("code", 0) != 0) {
                fail("授权未完成或已失效，请重新发起登录")
                return
            }
            deliver { listener.waiting() }
        } catch (e: Exception) {
            if (++failures >= 3) {
                fail("授权状态读取失败，请检查网络后重试")
                return
            }
        }
        schedulePoll()
    }

    private fun deliver(callback: Runnable) {
        ui.execute { if (!closed) callback.run() }
    }

    private fun fail(message: String) {
        cancelTask()
        if (!closed) deliver { listener.failed(message) }
        finishWorker()
    }

    @Synchronized
    private fun finishWorker() {
        worker.shutdown()
    }

    private fun cancelTask() {
        if (task.isEmpty()) return
        try {
            backend.request("loginCancel", fields())
        } catch (ignored: Exception) {
        }
        task = ""
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        if (!worker.isShutdown) {
            worker.execute { cancelTask() }
            worker.shutdown()
        }
    }
}
