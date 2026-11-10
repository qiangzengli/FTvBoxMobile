package com.fbox.mobile

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.ui.dialog.BaseDialog
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object MobileSheets {
    @JvmStatic
    fun dp(context: Context, value: Float): Int {
        return (value * context.resources.displayMetrics.density).roundToInt()
    }

    @JvmStatic
    fun install(dialog: BaseDialog) {
        val context = dialog.context
        val window = dialog.window ?: return
        val host = dialog.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (host.childCount == 0) return
        val body = host.getChildAt(0)

        val sheetTitle = dialog.sheetTitle
        val input = body.findViewById<EditText>(R.id.input)
        val submit = body.findViewById<View>(R.id.inputSubmit)
        if (input != null && submit != null && body.findViewById<View>(R.id.mobileApiActions) == null) {
            input.setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_GO) {
                    hideKeyboard(input)
                    submit.performClick()
                    true
                } else {
                    false
                }
            }
        }
        val footer = body.findViewById<View>(R.id.mobileApiActions)
        if (footer != null) {
            (footer.parent as? ViewGroup)?.removeView(footer)
        }
        host.removeView(body)

        val sheet = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                var available = (resources.displayMetrics.heightPixels * 0.85f).toInt()
                if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    available = min(available, MeasureSpec.getSize(heightMeasureSpec))
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST))
            }
        }
        sheet.orientation = LinearLayout.VERTICAL
        sheet.setPadding(dp(context, 16f), dp(context, 8f), dp(context, 16f), dp(context, 16f))
        sheet.setBackgroundResource(R.drawable.mobile_sheet)

        val handleArea = FrameLayout(context)
        handleArea.contentDescription = "下滑关闭弹窗"
        val handle = View(context)
        val handleShape = GradientDrawable()
        handleShape.setColor(0xFF687080.toInt())
        handleShape.cornerRadius = dp(context, 2f).toFloat()
        handle.background = handleShape
        handleArea.addView(handle, FrameLayout.LayoutParams(dp(context, 36f), dp(context, 4f), Gravity.CENTER))
        sheet.addView(handleArea, LinearLayout.LayoutParams(-1, dp(context, 20f)))

        val header = LinearLayout(context)
        header.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(context)
        title.text = sheetTitle
        title.textSize = 18f
        title.setTextColor(Color.WHITE)
        title.maxLines = 2
        title.ellipsize = TextUtils.TruncateAt.END
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

        val close = TextView(context)
        close.text = "关闭"
        close.textSize = 14f
        close.setTextColor(0xFFAEC6FF.toInt())
        close.gravity = Gravity.CENTER
        close.contentDescription = "关闭弹窗"
        close.setBackgroundResource(R.drawable.mobile_surface)
        close.setOnClickListener { dialog.cancel() }
        header.addView(close, LinearLayout.LayoutParams(dp(context, 56f), dp(context, 48f)))
        sheet.addView(header, LinearLayout.LayoutParams(-1, -2))

        installDragDismiss(handleArea, sheet, dialog)
        installDragDismiss(header, sheet, dialog)

        val scroll = NestedScrollView(context)
        scroll.isFillViewport = false
        scroll.clipToPadding = false
        body.setBackgroundColor(Color.TRANSPARENT)
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        val bodyParams = LinearLayout.LayoutParams(-1, -2, 1f)
        bodyParams.topMargin = dp(context, 12f)
        sheet.addView(scroll, bodyParams)

        if (footer != null) {
            val footerParams = LinearLayout.LayoutParams(-1, -2)
            footerParams.topMargin = dp(context, 12f)
            sheet.addView(footer, footerParams)
        }
        host.addView(sheet, ViewGroup.LayoutParams(-1, -2))

        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val attrs = window.attributes
        attrs.dimAmount = 0.48f
        window.attributes = attrs
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setCanceledOnTouchOutside(true)
    }

    private fun installDragDismiss(touchArea: View, sheet: View, dialog: BaseDialog) {
        touchArea.setOnTouchListener(object : View.OnTouchListener {
            var startY = 0f
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    startY = event.rawY
                    return true
                }
                val distance = max(0f, event.rawY - startY)
                if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    sheet.translationY = distance
                    return true
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    if (distance >= dp(view.context, 64f)) {
                        dialog.cancel()
                    } else {
                        sheet.animate().translationY(0f).setDuration(150).start()
                    }
                    return true
                }
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    sheet.translationY = 0f
                }
                return true
            }
        })
    }

    @JvmStatic
    fun action(context: Context, label: String): TextView {
        val button = TextView(context)
        button.text = label
        button.setTextColor(Color.WHITE)
        button.textSize = 14f
        button.gravity = Gravity.CENTER_VERTICAL
        button.setPadding(dp(context, 16f), dp(context, 8f), dp(context, 16f), dp(context, 8f))
        button.minHeight = dp(context, 48f)
        button.setBackgroundResource(R.drawable.mobile_surface)
        return button
    }

    @JvmStatic
    fun hideKeyboard(input: View) {
        val keyboard = input.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        keyboard?.hideSoftInputFromWindow(input.windowToken, 0)
    }
}
