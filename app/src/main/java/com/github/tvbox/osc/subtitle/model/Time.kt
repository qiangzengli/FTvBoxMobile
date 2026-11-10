package com.github.tvbox.osc.subtitle.model

class Time {
    @JvmField
    var mseconds: Int = 0

    constructor(format: String, value: String) {
        if (format.equals("hh:mm:ss,ms", ignoreCase = true)) {
            val h = value.substring(0, 2).toInt()
            val m = value.substring(3, 5).toInt()
            val s = value.substring(6, 8).toInt()
            val ms = value.substring(9, 12).toInt()
            mseconds = ms + s * 1000 + m * 60000 + h * 3600000
        } else if (format.equals("h:mm:ss.cs", ignoreCase = true)) {
            val h = value.substring(0, 1).toInt()
            val m = value.substring(2, 4).toInt()
            val s = value.substring(5, 7).toInt()
            val cs = value.substring(8, 10).toInt()
            mseconds = cs * 10 + s * 1000 + m * 60000 + h * 3600000
        } else if (format.equals("h:m:s:f/fps", ignoreCase = true)) {
            var args = value.split("/".toRegex()).toTypedArray()
            val fps = args[1].toFloat()
            args = args[0].split(":".toRegex()).toTypedArray()
            val h = args[0].toInt()
            val m = args[1].toInt()
            val s = args[2].toInt()
            val f = args[3].toInt()
            mseconds = (f * 1000 / fps).toInt() + s * 1000 + m * 60000 + h * 3600000
        }
    }

    fun getTime(format: String): String {
        val time = StringBuilder()
        var aux: String
        if (format.equals("hh:mm:ss,ms", ignoreCase = true)) {
            val h = mseconds / 3600000
            aux = h.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux).append(':')
            val m = (mseconds / 60000) % 60
            aux = m.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux).append(':')
            val s = (mseconds / 1000) % 60
            aux = s.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux).append(',')
            val ms = mseconds % 1000
            aux = ms.toString()
            if (aux.length == 1) time.append("00")
            else if (aux.length == 2) time.append('0')
            time.append(aux)
        } else if (format.equals("h:mm:ss.cs", ignoreCase = true)) {
            val h = mseconds / 3600000
            aux = h.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux).append(':')
            val m = (mseconds / 60000) % 60
            aux = m.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux).append(':')
            val s = (mseconds / 1000) % 60
            aux = s.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux).append('.')
            val cs = (mseconds / 10) % 100
            aux = cs.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux)
        } else if (format.startsWith("hhmmssff/")) {
            val args = format.split("/".toRegex()).toTypedArray()
            val fps = args[1].toFloat()
            val h = mseconds / 3600000
            aux = h.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux)
            val m = (mseconds / 60000) % 60
            aux = m.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux)
            val s = (mseconds / 1000) % 60
            aux = s.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux)
            val f = (mseconds % 1000) * fps.toInt() / 1000
            aux = f.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux)
        } else if (format.startsWith("h:m:s:f/")) {
            val args = format.split("/".toRegex()).toTypedArray()
            val fps = args[1].toFloat()
            val h = mseconds / 3600000
            aux = h.toString()
            time.append(aux).append(':')
            val m = (mseconds / 60000) % 60
            aux = m.toString()
            time.append(aux).append(':')
            val s = (mseconds / 1000) % 60
            aux = s.toString()
            time.append(aux).append(':')
            val f = (mseconds % 1000) * fps.toInt() / 1000
            aux = f.toString()
            time.append(aux)
        } else if (format.startsWith("hh:mm:ss:ff/")) {
            val args = format.split("/".toRegex()).toTypedArray()
            val fps = args[1].toFloat()
            val h = mseconds / 3600000
            aux = h.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux).append(':')
            val m = (mseconds / 60000) % 60
            aux = m.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux).append(':')
            val s = (mseconds / 1000) % 60
            aux = s.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux).append(':')
            val f = (mseconds % 1000) * fps.toInt() / 1000
            aux = f.toString()
            if (aux.length == 1) time.append('0')
            time.append(aux)
        }
        return time.toString()
    }
}
