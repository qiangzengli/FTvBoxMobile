package com.github.tvbox.osc.callback

import com.github.tvbox.osc.R
import com.kingja.loadsir.callback.Callback

class EmptyCallback : Callback() {
    override fun onCreateView(): Int {
        return R.layout.loadsir_empty_layout
    }
}
