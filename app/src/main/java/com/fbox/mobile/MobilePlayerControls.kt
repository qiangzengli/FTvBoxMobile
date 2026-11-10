package com.fbox.mobile

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.ui.dialog.BaseDialog
import java.util.ArrayList

object MobilePlayerControls {
    @JvmStatic
    fun install(root: View) {
        val group = root.findViewById<LinearLayout>(R.id.play_btn_group) ?: return
        val primary = listOf(R.id.play_label, R.id.play_pre, R.id.play_next, R.id.play_speed)
        val advanced = ArrayList<TextView>()
        val hidden = LinearLayout(root.context)
        hidden.visibility = View.GONE
        (root as? ViewGroup)?.addView(hidden)
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (child is TextView && !primary.contains(child.id)) {
                group.removeViewAt(i)
                hidden.addView(child)
                advanced.add(0, child)
            }
        }
        val episodes = MobileSheets.action(root.context, "选集")
        episodes.setOnClickListener {
            root.findViewById<View>(R.id.play_next)?.performLongClick()
        }
        group.addView(episodes)
        val more = MobileSheets.action(root.context, "更多")
        more.contentDescription = "播放设置、字幕、弹幕和投屏"
        group.addView(more)
        more.setOnClickListener {
            val dialog = BaseDialog(root.context)
            dialog.setTitle("播放设置")
            val list = LinearLayout(root.context)
            list.orientation = LinearLayout.VERTICAL
            for (source in advanced) {
                if (source.visibility != View.VISIBLE) continue
                val row = LinearLayout(root.context)
                val action = MobileSheets.action(root.context, source.text.toString())
                action.setOnClickListener {
                    dialog.dismiss()
                    source.performClick()
                }
                row.addView(action, LinearLayout.LayoutParams(0, -2, 1f))
                if (source.isLongClickable) {
                    val options = MobileSheets.action(root.context, "选项")
                    options.contentDescription = "${source.text}更多选项"
                    options.setOnClickListener {
                        dialog.dismiss()
                        source.performLongClick()
                    }
                    row.addView(options)
                }
                list.addView(row)
            }
            dialog.setContentView(list)
            dialog.show()
        }
    }
}
