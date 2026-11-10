package com.github.tvbox.osc.base

import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import com.fbox.mobile.MobileFrame
import com.github.tvbox.osc.R
import com.github.tvbox.osc.callback.EmptyCallback
import com.github.tvbox.osc.callback.LoadingCallback
import com.github.tvbox.osc.util.AppManager
import com.kingja.loadsir.core.LoadService
import com.kingja.loadsir.core.LoadSir
import me.jessyan.autosize.AutoSizeCompat
import me.jessyan.autosize.AutoSizeConfig
import me.jessyan.autosize.internal.CustomAdapt
import xyz.doikki.videoplayer.util.CutoutUtil
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

abstract class BaseActivity : AppCompatActivity(), CustomAdapt {

    private var mobileFrame: MobileFrame? = null
    private var phoneFullscreen: Boolean = false

    fun isPhonePortrait(): Boolean {
        return super.getResources().configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    fun setPhoneFullscreen(fullscreen: Boolean) {
        phoneFullscreen = fullscreen
        mobileFrame?.setFullscreen(fullscreen)
        hideSysBar()
    }

    @JvmField
    protected var mContext: Context? = null
    private var mLoadService: LoadService<*>? = null

    private val refreshAutoSizeRunnable = Runnable {
        if (shouldRefreshAutoSize()) {
            refreshAutoSize()
        }
    }

    private val hideSysBarRunnable = Runnable {
        hideSysBar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            if (screenRatio < 0) {
                val dm = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(dm)
                updateScreenRatio(dm)
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        super.onCreate(savedInstanceState)
        setContentView(getLayoutResID())
        mContext = this
        initSystemUiListener()
        CutoutUtil.adaptCutoutAboveAndroidP(mContext, true)
        AppManager.getInstance().addActivity(this)
        init()
        phoneFullscreen = this is com.github.tvbox.osc.ui.activity.LivePlayActivity
        val host: ViewGroup = findViewById(android.R.id.content)
        val content = host.getChildAt(0)
        host.removeView(content)
        mobileFrame = MobileFrame(this, content)
        setContentView(mobileFrame!!.view)
        mobileFrame!!.attach()
        hideSysBar()
    }

    override fun onResume() {
        super.onResume()
        hideSysBar()
        changeWallpaper(false)
        if (shouldRefreshAutoSize()) {
            refreshAutoSize()
            scheduleRefreshAutoSize()
        }
    }

    open fun hideSysBar() {
        if (!phoneFullscreen) {
            CutoutUtil.adaptCutoutAboveAndroidP(mContext, false)
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            window.statusBarColor = -0xefecdf // 0xFF10131A
            window.navigationBarColor = -0xefecdf
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            CutoutUtil.adaptCutoutAboveAndroidP(mContext, true)
            var uiOptions = window.decorView.systemUiVisibility
            uiOptions = uiOptions or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            uiOptions = uiOptions or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            uiOptions = uiOptions or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            uiOptions = uiOptions or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            uiOptions = uiOptions or View.SYSTEM_UI_FLAG_FULLSCREEN
            uiOptions = uiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            window.decorView.systemUiVisibility = uiOptions
        }
    }

    private fun initSystemUiListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val decorView = window.decorView
            decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                val hiddenBars = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                if (phoneFullscreen && (visibility and hiddenBars) != hiddenBars) {
                    decorView.removeCallbacks(hideSysBarRunnable)
                    decorView.postDelayed(hideSysBarRunnable, 300)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        window.decorView.removeCallbacks(refreshAutoSizeRunnable)
        window.decorView.removeCallbacks(hideSysBarRunnable)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSysBar()
            if (shouldRefreshAutoSize()) {
                scheduleRefreshAutoSize()
            }
        }
    }

    protected open fun shouldRefreshAutoSize(): Boolean {
        return false
    }

    private fun scheduleRefreshAutoSize() {
        val decorView = window.decorView
        decorView.removeCallbacks(refreshAutoSizeRunnable)
        decorView.postDelayed(refreshAutoSizeRunnable, 300)
    }

    private fun refreshAutoSize() {
        try {
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(dm)
            if (dm.widthPixels <= 0 || dm.heightPixels <= 0) {
                return
            }
            updateScreenRatio(dm)
            AutoSizeConfig.getInstance().screenWidth = dm.widthPixels
            AutoSizeConfig.getInstance().screenHeight = dm.heightPixels
            AutoSizeCompat.autoConvertDensityOfCustomAdapt(super.getResources(), this)
            window.decorView.requestLayout()
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    private fun updateScreenRatio(dm: DisplayMetrics) {
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val min = Math.min(screenWidth, screenHeight)
        if (min > 0) {
            screenRatio = Math.max(screenWidth, screenHeight).toFloat() / min.toFloat()
        }
    }

    override fun getResources(): Resources {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            AutoSizeCompat.autoConvertDensityOfCustomAdapt(super.getResources(), this)
        }
        return super.getResources()
    }

    fun hasPermission(permission: String): Boolean {
        var has = true
        try {
            has = PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return has
    }

    protected abstract fun getLayoutResID(): Int

    protected abstract fun init()

    protected fun setLoadSir(view: View) {
        if (mLoadService == null) {
            mLoadService = LoadSir.getDefault().register(view) { }
        }
    }

    protected open fun showLoading() {
        mLoadService?.showCallback(LoadingCallback::class.java)
    }

    protected open fun isLoading(): Boolean {
        val current = mLoadService?.currentCallback
        return current != null && current == LoadingCallback::class.java
    }

    protected open fun showEmpty() {
        mLoadService?.showCallback(EmptyCallback::class.java)
    }

    protected open fun showSuccess() {
        mLoadService?.showSuccess()
    }

    override fun onDestroy() {
        super.onDestroy()
        AppManager.getInstance().finishActivity(this)
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

    protected fun getAssetText(fileName: String): String {
        val stringBuilder = StringBuilder()
        try {
            val assets: AssetManager = assets
            val bf = BufferedReader(InputStreamReader(assets.open(fileName)))
            var line: String?
            while (bf.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
            return stringBuilder.toString()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return ""
    }

    override fun getSizeInDp(): Float {
        if (isPhonePortrait()) return 392f
        return if (isBaseOnWidth) 1280f else 720f
    }

    override fun isBaseOnWidth(): Boolean {
        if (isPhonePortrait()) return true
        return !(screenRatio >= 4.0f)
    }

    open fun changeWallpaper(force: Boolean) {
        if (!force && globalWp != null) {
            window.setBackgroundDrawable(globalWp)
        }
        try {
            val wp = File(filesDir.absolutePath + "/wp")
            if (wp.exists()) {
                val opts = BitmapFactory.Options()
                opts.inJustDecodeBounds = true
                BitmapFactory.decodeFile(wp.absolutePath, opts)
                val imageHeight = opts.outHeight
                val imageWidth = opts.outWidth
                val picHeight = 720
                val picWidth = 1080
                val scaleX = imageWidth / picWidth
                val scaleY = imageHeight / picHeight
                var scale = 1
                if (scaleX > scaleY && scaleY >= 1) {
                    scale = scaleX
                }
                if (scaleX < scaleY && scaleX >= 1) {
                    scale = scaleY
                }
                opts.inJustDecodeBounds = false
                opts.inSampleSize = scale
                globalWp = BitmapDrawable(resources, BitmapFactory.decodeFile(wp.absolutePath, opts))
            } else {
                globalWp = null
            }
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            globalWp = null
        }
        if (globalWp != null) {
            window.setBackgroundDrawable(globalWp)
        } else {
            window.setBackgroundDrawableResource(R.drawable.app_bg)
        }
    }

    companion object {
        private var screenRatio = -100.0f

        @JvmField
        protected var globalWp: BitmapDrawable? = null
    }
}
