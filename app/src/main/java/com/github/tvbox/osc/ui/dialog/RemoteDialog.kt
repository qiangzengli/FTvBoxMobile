package com.github.tvbox.osc.ui.dialog

import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.ui.tv.QRCodeGen
import me.jessyan.autosize.utils.AutoSizeUtils

class RemoteDialog(context: Context) : BaseDialog(context) {
    private val ivQRCode: ImageView
    private val tvAddress: TextView

    init {
        setContentView(R.layout.dialog_remote)
        setCanceledOnTouchOutside(false)
        ivQRCode = findViewById(R.id.ivQRCode)
        tvAddress = findViewById(R.id.tvAddress)
        refreshQRCode()
    }

    private fun refreshQRCode() {
        val address = ControlManager.get().getAddress(false)
        tvAddress.text = String.format("手机/电脑扫描上方二维码或者直接浏览器访问地址\n%s", address)
        ivQRCode.setImageBitmap(
            QRCodeGen.generateBitmap(
                address,
                AutoSizeUtils.mm2px(context, 240f),
                AutoSizeUtils.mm2px(context, 240f)
            )
        )
    }
}
