package com.github.tvbox.osc.callback

import com.github.tvbox.osc.R
import com.kingja.loadsir.callback.Callback

class LoadingCallback : Callback() {
    override fun onCreateView(): Int {
        return R.layout.loadsir_loading_layout
    }
}
