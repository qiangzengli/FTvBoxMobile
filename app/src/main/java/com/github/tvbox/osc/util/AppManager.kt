package com.github.tvbox.osc.util

import android.app.Activity
import java.util.Stack

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
class AppManager private constructor() {
    private var activityStack: Stack<Activity>? = null

    private object SingleHolder {
        val instance = AppManager()
    }

    /**
     * 添加Activity到堆栈
     */
    fun addActivity(activity: Activity) {
        if (activityStack == null) {
            activityStack = Stack<Activity>()
        }
        activityStack!!.add(activity)
    }

    /**
     * 是否有activity
     */
    fun isActivity(): Boolean {
        return activityStack != null && !activityStack!!.isEmpty()
    }

    /**
     * 获取当前Activity（堆栈中最后一个压入的）
     */
    fun currentActivity(): Activity? {
        return activityStack?.lastElement()
    }

    /**
     * 结束当前Activity（堆栈中最后一个压入的）
     */
    fun finishActivity() {
        val activity = activityStack?.lastElement()
        if (activity != null && !activity.isFinishing) {
            activity.finish()
        }
    }

    fun finishActivity(activity: Activity?) {
        activityStack?.remove(activity)
    }

    /**
     * 结束指定类名的Activity
     */
    fun finishActivity(cls: Class<*>) {
        val stack = activityStack ?: return
        for (activity in stack) {
            if (activity.javaClass == cls) {
                if (!activity.isFinishing) {
                    activity.finish()
                }
                break
            }
        }
    }

    fun backActivity(cls: Class<*>) {
        val stack = activityStack ?: return
        while (!stack.empty()) {
            val activity = stack.pop()
            if (activity.javaClass == cls) {
                stack.push(activity)
                break
            } else {
                activity.finish()
            }
        }
    }

    /**
     * 结束所有Activity
     */
    fun finishAllActivity() {
        val stack = activityStack
        if (stack != null && stack.size > 0) {
            for (i in 0 until stack.size) {
                val activity = stack[i]
                if (activity != null && !activity.isFinishing) {
                    activity.finish()
                }
            }
            stack.clear()
        }
    }

    /**
     * 获取指定的Activity
     */
    fun getActivity(cls: Class<*>): Activity? {
        val stack = activityStack ?: return null
        for (activity in stack) {
            if (activity.javaClass == cls) {
                return activity
            }
        }
        return null
    }

    fun appExit(code: Int) {
        try {
            finishAllActivity()
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(code)
        } catch (e: Exception) {
            activityStack?.clear()
            e.printStackTrace()
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): AppManager {
            return SingleHolder.instance
        }
    }
}
