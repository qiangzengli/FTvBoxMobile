package com.github.tvbox.osc.bean

import java.util.Date

class LiveEpgDate {

    private var index: Int = 0
    private var datePresented: String? = null
    private var dateParamVal: Date? = null

    fun getIndex(): Int {
        return index
    }

    fun setIndex(index: Int) {
        this.index = index
    }

    fun getDatePresented(): String? {
        return datePresented
    }

    fun setDatePresented(datePresented: String?) {
        this.datePresented = datePresented
    }

    fun getDateParamVal(): Date? {
        return dateParamVal
    }

    fun setDateParamVal(dateParamVal: Date?) {
        this.dateParamVal = dateParamVal
    }
}
