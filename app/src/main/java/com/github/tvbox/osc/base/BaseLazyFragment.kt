package com.github.tvbox.osc.base

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import com.github.tvbox.osc.callback.EmptyCallback
import com.github.tvbox.osc.callback.LoadingCallback
import com.kingja.loadsir.core.LoadService
import com.kingja.loadsir.core.LoadSir
import me.jessyan.autosize.AutoSize
import me.jessyan.autosize.internal.CustomAdapt

abstract class BaseLazyFragment : Fragment(), CustomAdapt {

    @JvmField
    protected var rootView: View? = null

    @JvmField
    protected var isViewCreated: Boolean = false

    @JvmField
    protected var currentVisibleState: Boolean = false

    @JvmField
    protected var mIsFirstVisible: Boolean = true

    @JvmField
    protected var mContext: Context? = null

    @JvmField
    protected var mActivity: Activity? = null

    private var mLoadService: LoadService<*>? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
        mActivity = context as? Activity
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val act = activity
        if (act != null) {
            AutoSize.autoConvertDensity(act, sizeInDp, isBaseOnWidth)
        }
        if (rootView == null) {
            rootView = inflater.inflate(getLayoutResID(), container, false)
        }
        isViewCreated = true
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        @Suppress("DEPRECATION")
        if (!isHidden && userVisibleHint) {
            dispatchUserVisibleHint(true)
        }
    }

    @Suppress("DEPRECATION")
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isViewCreated) {
            if (isVisibleToUser && !currentVisibleState) {
                dispatchUserVisibleHint(true)
            } else if (!isVisibleToUser && currentVisibleState) {
                dispatchUserVisibleHint(false)
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            dispatchUserVisibleHint(false)
        } else {
            dispatchUserVisibleHint(true)
        }
    }

    private fun dispatchUserVisibleHint(isVisible: Boolean) {
        if (isVisible && isParentInvisible()) {
            return
        }
        if (currentVisibleState == isVisible) {
            return
        }
        currentVisibleState = isVisible
        if (isVisible) {
            if (mIsFirstVisible) {
                mIsFirstVisible = false
                init()
            }
            onFragmentResume()
            dispatchChildVisibleState(true)
        } else {
            onFragmentPause()
            dispatchChildVisibleState(false)
        }
    }

    @Suppress("DEPRECATION")
    private fun dispatchChildVisibleState(visible: Boolean) {
        val fragmentManager = childFragmentManager
        val fragments = fragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is BaseLazyFragment && !fragment.isHidden && fragment.userVisibleHint) {
                fragment.dispatchUserVisibleHint(visible)
            }
        }
    }

    protected open fun onFragmentPause() {}

    protected open fun onFragmentResume() {}

    private fun isParentInvisible(): Boolean {
        val parentFragment = parentFragment
        if (parentFragment is BaseLazyFragment) {
            return !parentFragment.isSupportVisible()
        }
        return false
    }

    private fun isSupportVisible(): Boolean {
        return currentVisibleState
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        val act = activity
        if (act != null) {
            AutoSize.autoConvertDensity(act, sizeInDp, isBaseOnWidth)
        }
        super.onResume()
        if (!mIsFirstVisible) {
            if (!isHidden && !currentVisibleState && userVisibleHint) {
                dispatchUserVisibleHint(true)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onPause() {
        super.onPause()
        if (currentVisibleState && userVisibleHint) {
            dispatchUserVisibleHint(false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isViewCreated = false
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : View> findViewById(@IdRes viewId: Int): T? {
        var view: View? = null
        if (rootView != null) {
            view = rootView!!.findViewById(viewId)
        }
        return view as? T
    }

    protected abstract fun getLayoutResID(): Int

    protected abstract fun init()

    protected fun setLoadSir(view: View) {
        if (mLoadService == null) {
            mLoadService = LoadSir.getDefault().register(view) { }
        }
    }

    protected fun setLoadSir2(view: View) {
        mLoadService = LoadSir.getDefault().register(view) { }
    }

    protected open fun showLoading() {
        mLoadService?.showCallback(LoadingCallback::class.java)
    }

    protected open fun showEmpty() {
        mLoadService?.showCallback(EmptyCallback::class.java)
    }

    protected open fun showSuccess() {
        mLoadService?.showSuccess()
    }

    fun jumpActivity(clazz: Class<out BaseActivity>) {
        val intent = Intent(mContext, clazz)
        startActivity(intent)
    }

    fun jumpActivity(clazz: Class<out BaseActivity>, bundle: Bundle?) {
        val intent = Intent(mContext, clazz)
        if (bundle != null) {
            intent.putExtras(bundle)
        }
        startActivity(intent)
    }

    override fun getSizeInDp(): Float {
        val act = activity
        if (act != null && act is CustomAdapt) {
            return act.sizeInDp
        }
        return 0f
    }

    override fun isBaseOnWidth(): Boolean {
        val act = activity
        if (act != null && act is CustomAdapt) {
            return act.isBaseOnWidth
        }
        return true
    }
}
