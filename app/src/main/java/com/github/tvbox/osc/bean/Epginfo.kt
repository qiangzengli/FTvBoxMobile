package com.github.tvbox.osc.bean

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class Epginfo(
    Date: Date,
    str: String?,
    date: Date,
    str1: String,
    str2: String,
    pos: Int
) {
    @JvmField
    var startdateTime: Date? = null
    @JvmField
    var enddateTime: Date? = null
    @JvmField
    var datestart: Int = 0
    @JvmField
    var dateend: Int = 0
    @JvmField
    var title: String? = null
    @JvmField
    var originStart: String? = null
    @JvmField
    var originEnd: String? = null
    @JvmField
    var start: String? = null
    @JvmField
    var end: String? = null
    @JvmField
    var index: Int = 0
    @JvmField
    var epgDate: Date? = null
    @JvmField
    var currentEpgDate: String? = null

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd")

    init {
        epgDate = Date
        currentEpgDate = timeFormat.format(epgDate!!)
        title = str
        originStart = str1
        originEnd = str2
        index = pos
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd")
        simpleDateFormat.timeZone = TimeZone.getTimeZone("GMT+8:00")
        val userSimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
        userSimpleDateFormat.timeZone = TimeZone.getDefault()
        startdateTime = userSimpleDateFormat.parse(simpleDateFormat.format(date) + " " + str1 + ":00 GMT+8:00", ParsePosition(0))
        enddateTime = userSimpleDateFormat.parse(simpleDateFormat.format(date) + " " + str2 + ":00 GMT+8:00", ParsePosition(0))
        if (startdateTime != null && enddateTime != null && !enddateTime!!.after(startdateTime)) {
            val calendar = Calendar.getInstance()
            calendar.time = enddateTime!!
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            enddateTime = calendar.time
        }
        val zoneFormat = SimpleDateFormat("HH:mm")
        start = if (startdateTime != null) zoneFormat.format(startdateTime!!) else ""
        end = if (enddateTime != null) zoneFormat.format(enddateTime!!) else ""
        datestart = try {
            start!!.replace(":", "").toInt()
        } catch (e: Exception) {
            0
        }
        dateend = try {
            end!!.replace(":", "").toInt()
        } catch (e: Exception) {
            0
        }
    }
}
