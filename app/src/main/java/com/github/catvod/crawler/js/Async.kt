package com.github.catvod.crawler.js

import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.JSObject
import java.util.concurrent.CountDownLatch

class Async private constructor() {

    private val future: Result = Result()

    private val success = JSCallFunction { args ->
        future.complete(if (args != null && args.isNotEmpty()) args[0] else null)
        null
    }

    private val error = JSCallFunction { args ->
        val msg = if (args != null && args.isNotEmpty() && args[0] != null) args[0].toString() else ""
        future.completeExceptionally(Exception(msg))
        null
    }

    private fun call(jsObject: JSObject, name: String, args: Array<out Any?>?): Result {
        val function = jsObject.getJSFunction(name) ?: return empty()
        try {
            val result = if (args != null) function.call(*args) else function.call()
            if (result is JSObject) then(result)
            else future.complete(result)
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        } finally {
            function.release()
        }
        return future
    }

    private fun empty(): Result {
        future.complete(null)
        return future
    }

    private fun then(promise: JSObject) {
        val then = promise.getJSFunction("then")
        if (then == null) {
            future.complete(promise)
        } else {
            consume(then, success)
            consume(promise.getJSFunction("catch"), error)
        }
    }

    private fun consume(function: JSFunction?, callback: JSCallFunction) {
        if (function == null) return
        try {
            function.call(callback)
        } finally {
            function.release()
        }
    }

    class Result {
        private val latch = CountDownLatch(1)
        private var value: Any? = null
        private var error: Throwable? = null

        fun complete(value: Any?) {
            this.value = value
            latch.countDown()
        }

        fun completeExceptionally(error: Throwable) {
            this.error = error
            latch.countDown()
        }

        @Throws(Exception::class)
        fun get(): Any? {
            latch.await()
            if (error != null) {
                if (error is Exception) throw (error as Exception)
                throw Exception(error)
            }
            return value
        }
    }

    companion object {
        @JvmStatic
        fun run(jsObject: JSObject, name: String, args: Array<out Any?>?): Result {
            return Async().call(jsObject, name, args)
        }
    }
}
