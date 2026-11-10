package com.github.tvbox.osc.ui.dialog

import android.content.Context
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import com.github.tvbox.osc.R
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.adapter.ButtonAdapter
import com.github.tvbox.osc.util.DanmuHelper
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.atomic.AtomicInteger

class DanmuSettingDialog(context: Context) : BaseDialog(context) {
    private var danmuSearchListener: DanmuSearchListener? = null

    init {
        setContentView(R.layout.dialog_danmu_setting)
        initDanmuSearch()
        initColor()
        initSpeed()
        initSize()
        initLine()
        initAlpha()
    }

    private fun initDanmuSearch() {
        val danmuSearch = findViewById<TextView>(R.id.danmuSearch)
        danmuSearch.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            val listener = danmuSearchListener ?: return@setOnClickListener
            dismiss()
            listener.openSearchDanmuDialog()
        }
        danmuSearch.post { danmuSearch.requestFocus() }
    }

    private fun initColor() {
        val data = listOf(false, true)
        setButtonAdapter(R.id.trv_color, data, if (DanmuHelper.useRandomColor()) 1 else 0, object : ButtonAdapter.SelectDialogInterface<Boolean> {
            override fun click(value: Boolean, pos: Int) {
                DanmuHelper.setRandomColor(value)
                EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, true))
            }

            override fun getDisplay(value: Boolean): String {
                return if (value) "随机" else "默认"
            }
        })
    }

    private fun initSpeed() {
        val speeds = listOf(2.4f, 1.8f, 1.5f, 1.0f)
        val defaultPos = Math.max(0, speeds.indexOf(DanmuHelper.getSpeed()))
        setButtonAdapter(R.id.speed, speeds, defaultPos, object : ButtonAdapter.SelectDialogInterface<Float> {
            override fun click(value: Float, pos: Int) {
                DanmuHelper.setSpeed(value)
                EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false))
            }

            override fun getDisplay(value: Float): String {
                val idx = speeds.indexOf(value)
                return if (idx < 0) "适中" else arrayOf("超慢", "慢", "适中", "快")[idx]
            }
        })
    }

    private fun initSize() {
        val sizeText = findViewById<TextView>(R.id.size)
        val sizeSub = findViewById<TextView>(R.id.sizeSub)
        val sizeAdd = findViewById<TextView>(R.id.sizeAdd)
        val size = AtomicInteger(Math.round(DanmuHelper.getSizeScale() * 10))
        renderSize(sizeText, size.get())
        sizeSub.setOnClickListener {
            if (size.get() <= 6) return@setOnClickListener
            size.decrementAndGet()
            DanmuHelper.setSizeScale(size.get() / 10f)
            renderSize(sizeText, size.get())
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false))
        }
        sizeAdd.setOnClickListener {
            if (size.get() >= 20) return@setOnClickListener
            size.incrementAndGet()
            DanmuHelper.setSizeScale(size.get() / 10f)
            renderSize(sizeText, size.get())
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false))
        }
    }

    private fun initLine() {
        val lineText = findViewById<TextView>(R.id.line)
        val lineSub = findViewById<TextView>(R.id.lineSub)
        val lineAdd = findViewById<TextView>(R.id.lineAdd)
        val line = AtomicInteger(DanmuHelper.getMaxLine())
        renderLine(lineText, line.get())
        lineSub.setOnClickListener {
            if (line.get() <= 1) return@setOnClickListener
            line.decrementAndGet()
            DanmuHelper.setMaxLine(line.get())
            renderLine(lineText, line.get())
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false))
        }
        lineAdd.setOnClickListener {
            if (line.get() >= 15) return@setOnClickListener
            line.incrementAndGet()
            DanmuHelper.setMaxLine(line.get())
            renderLine(lineText, line.get())
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false))
        }
    }

    private fun initAlpha() {
        val alphaText = findViewById<TextView>(R.id.alpha)
        val alphaSub = findViewById<TextView>(R.id.alphaSub)
        val alphaAdd = findViewById<TextView>(R.id.alphaAdd)
        val alpha = AtomicInteger(Math.round(DanmuHelper.getAlpha() * 100))
        renderAlpha(alphaText, alpha.get())
        alphaSub.setOnClickListener {
            if (alpha.get() <= 10) return@setOnClickListener
            alpha.addAndGet(-10)
            DanmuHelper.setAlpha(alpha.get() / 100f)
            renderAlpha(alphaText, alpha.get())
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false))
        }
        alphaAdd.setOnClickListener {
            if (alpha.get() >= 100) return@setOnClickListener
            alpha.addAndGet(10)
            DanmuHelper.setAlpha(alpha.get() / 100f)
            renderAlpha(alphaText, alpha.get())
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false))
        }
    }

    private fun renderSize(view: TextView, value: Int) {
        view.text = "${value}档"
    }

    private fun renderLine(view: TextView, value: Int) {
        view.text = "${value}行"
    }

    private fun renderAlpha(view: TextView, value: Int) {
        view.text = "$value%"
    }

    private fun <T : Any> setButtonAdapter(rid: Int, data: List<T>, select: Int, dialogInterface: ButtonAdapter.SelectDialogInterface<T>) {
        val adapter = ButtonAdapter(dialogInterface, object : DiffUtil.ItemCallback<T>() {
            override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
                return oldItem == newItem
            }
        })
        adapter.setData(data, select)
        val tvRecyclerView = findViewById<TvRecyclerView>(rid)
        tvRecyclerView.layoutManager = V7LinearLayoutManager(context, 0, false)
        tvRecyclerView.adapter = adapter
        tvRecyclerView.setSelectedPosition(select)
    }

    fun setDanmuSearchListener(danmuSearchListener: DanmuSearchListener?) {
        this.danmuSearchListener = danmuSearchListener
    }

    interface DanmuSearchListener {
        fun openSearchDanmuDialog()
    }
}
