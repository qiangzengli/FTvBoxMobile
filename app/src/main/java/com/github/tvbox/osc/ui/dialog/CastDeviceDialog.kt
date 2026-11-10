package com.github.tvbox.osc.ui.dialog

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.dlna.CastDevice
import com.github.tvbox.osc.dlna.CastVideo
import com.github.tvbox.osc.dlna.DLNACastManager
import com.github.tvbox.osc.player.thirdparty.RemoteTVBox
import com.owen.tvrecyclerview.widget.TvRecyclerView
import okhttp3.Call
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap

class CastDeviceDialog(context: Context, private val video: CastVideo) : BaseDialog(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val devices: MutableMap<String, CastDevice> = LinkedHashMap()
    private val adapter = DeviceAdapter()
    private var onCastListener: OnCastListener? = null
    private var title: TextView? = null
    private var progressBar: ProgressBar? = null
    private var tvScanning: TextView? = null
    private var tvEmpty: TextView? = null
    private var searchFinished = false

    init {
        setContentView(R.layout.dialog_cast)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = findViewById(R.id.title)
        progressBar = findViewById(R.id.progressBar)
        tvScanning = findViewById(R.id.tvScanning)
        tvEmpty = findViewById(R.id.tvEmpty)
        val btnRefresh = findViewById<TextView>(R.id.btnRefresh)
        val btnCancel = findViewById<TextView>(R.id.btnCancel)
        val list = findViewById<TvRecyclerView>(R.id.list)
        list.adapter = adapter
        title?.text = "投屏到设备"
        btnRefresh.setOnClickListener {
            refreshDevices()
        }
        btnCancel.setOnClickListener {
            dismiss()
        }
        searchDevices()
    }

    override fun dismiss() {
        DLNACastManager.get().setDeviceListener(null)
        DLNACastManager.get().release(context)
        super.dismiss()
    }

    fun setOnCastListener(listener: OnCastListener?) {
        this.onCastListener = listener
    }

    private fun searchDevices() {
        searchFinished = false
        updateState()
        searchTvBoxDevices()
        searchDlnaDevices()
        handler.postDelayed({
            searchFinished = true
            updateState()
        }, 15000)
    }

    private fun refreshDevices() {
        handler.removeCallbacksAndMessages(null)
        DLNACastManager.get().setDeviceListener(null)
        DLNACastManager.get().release(context)
        devices.clear()
        adapter.setData(ArrayList())
        searchDevices()
    }

    private fun searchTvBoxDevices() {
        Thread {
            RemoteTVBox.searchAvalible(object : RemoteTVBox.Callback() {
                override fun found(viewHost: String, end: Boolean) {
                    handler.post {
                        addDevice(CastDevice.tvbox(viewHost))
                    }
                }

                override fun fail(all: Boolean, end: Boolean) {
                    if (end) {
                        handler.post {
                            updateState()
                        }
                    }
                }
            })
        }.start()
    }

    private fun searchDlnaDevices() {
        DLNACastManager.get().setDeviceListener(object : DLNACastManager.DeviceListener {
            override fun onDeviceChanged() {
                for (device in DLNACastManager.get().getDevices()) {
                    addDevice(device)
                }
            }
        })
        DLNACastManager.get().init(context)
        handler.postDelayed({
            DLNACastManager.get().search()
        }, 1000)
    }

    private fun addDevice(device: CastDevice) {
        devices[device.type.toString() + ":" + device.id] = device
        adapter.setData(ArrayList(devices.values))
        updateState()
    }

    private fun updateState() {
        val t = title ?: return
        t.text = "投屏到设备"
        val hasDevice = devices.isNotEmpty()
        progressBar?.visibility = if (!hasDevice && !searchFinished) View.VISIBLE else View.GONE
        tvScanning?.visibility = if (!hasDevice && !searchFinished) View.VISIBLE else View.GONE
        tvEmpty?.visibility = if (!hasDevice && searchFinished) View.VISIBLE else View.GONE
    }

    private fun castToDevice(device: CastDevice) {
        if (device.type == CastDevice.TYPE_TVBOX) {
            castToTvBox(device)
            return
        }
        DLNACastManager.get().cast(device, video, object : DLNACastManager.CastCallback {
            override fun onResult(success: Boolean, msg: String) {
                handleCastResult(success, msg)
            }
        })
    }

    private fun castToTvBox(device: CastDevice) {
        try {
            val url = buildTvBoxUrl(video.url, video.headers)
            val params: MutableMap<String, String> = HashMap()
            params["do"] = "push"
            params["url"] = url
            RemoteTVBox.post("http://" + device.id + "/action", params, object : okhttp3.Callback {
                override fun onFailure(call: Call, e: IOException) {
                    handler.post {
                        handleCastResult(false, "TVBox投屏失败")
                    }
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    val ok: Boolean
                    try {
                        val body = response.body()
                        ok = body != null && "ok" == body.string()
                    } finally {
                        response.close()
                    }
                    handler.post {
                        handleCastResult(ok, if (ok) "" else "TVBox投屏失败")
                    }
                }
            })
        } catch (e: Exception) {
            handleCastResult(false, "TVBox投屏失败")
        }
    }

    @Throws(Exception::class)
    private fun buildTvBoxUrl(url: String, headers: HashMap<String, String>?): String {
        if (headers == null || headers.isEmpty()) return url
        val json = JSONObject(headers as Map<*, *>).toString()
        return url + "@Headers=" + URLEncoder.encode(json, "UTF-8") + "@"
    }

    private fun handleCastResult(success: Boolean, msg: String?) {
        if (success) {
            Toast.makeText(context, "投屏成功", Toast.LENGTH_SHORT).show()
            onCastListener?.onCastSuccess()
            dismiss()
        } else {
            Toast.makeText(context, if (msg.isNullOrEmpty()) "投屏失败" else msg, Toast.LENGTH_SHORT).show()
            onCastListener?.onCastFailed()
        }
    }

    interface OnCastListener {
        fun onCastSuccess()
        fun onCastFailed()
    }

    private inner class DeviceAdapter : RecyclerView.Adapter<DeviceAdapter.DeviceHolder>() {
        private val data: MutableList<CastDevice> = ArrayList()

        fun setData(devices: List<CastDevice>) {
            data.clear()
            data.addAll(devices)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceHolder {
            return DeviceHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_cast_device, parent, false))
        }

        override fun onBindViewHolder(holder: DeviceHolder, position: Int) {
            val device = data[position]
            val name = holder.itemView.findViewById<TextView>(R.id.tvDeviceName)
            val ip = holder.itemView.findViewById<TextView>(R.id.tvDeviceIp)
            name.text = device.name
            ip.text = (if (device.type == CastDevice.TYPE_DLNA) "DLNA  " else "TVBox  ") + device.id
            holder.itemView.setOnClickListener {
                castToDevice(device)
            }
        }

        override fun getItemCount(): Int {
            return data.size
        }

        inner class DeviceHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    }
}
