package com.github.catvod.crawler

import android.util.Log
import com.github.catvod.crawler.python.IPyLoader

class pyLoader : IPyLoader {
    override fun clear() {
        Log.i("PyLoader", "java flavor: clear() called, Python is not supported.")
    }

    override fun setConfig(jsonStr: String?) {
        Log.i("PyLoader", "java flavor: setConfig() called, Python is not supported.")
    }

    override fun setRecentPyKey(key: String?) {
        Log.i("PyLoader", "java flavor: setRecentPyKey() called, Python is not supported.")
    }

    override fun getSpider(key: String?, cls: String?, ext: String?): Spider? {
        Log.i("PyLoader", "java flavor: getSpider() called, Python is not supported.")
        return SpiderNull()
    }

    override fun proxyInvoke(params: Map<String, String>?): Array<Any?>? {
        Log.i("PyLoader", "java flavor: proxyInvoke(params) called, Python is not supported.")
        return null
    }

    override fun proxyInvoke(params: Map<String, String>?, key: String?): Array<Any?>? {
        Log.i("PyLoader", "java flavor: proxyInvoke(params, key) called, Python is not supported.")
        return null
    }
}
