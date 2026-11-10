package com.github.tvbox.osc.subtitle.model

class Style {
    @JvmField
    var iD: String = ""

    @JvmField
    var font: String? = null

    @JvmField
    var fontSize: String? = null

    /** colors are stored as 8 chars long RGBA */
    @JvmField
    var color: String? = null

    @JvmField
    var backgroundColor: String? = null

    @JvmField
    var textAlign: String = ""

    @JvmField
    var italic: Boolean = false

    @JvmField
    var bold: Boolean = false

    @JvmField
    var underline: Boolean = false

    constructor(styleName: String) {
        this.iD = styleName
    }

    constructor(styleName: String, style: Style?) {
        this.iD = styleName
        if (style != null) {
            this.font = style.font
            this.fontSize = style.fontSize
            this.color = style.color
            this.backgroundColor = style.backgroundColor
            this.textAlign = style.textAlign
            this.italic = style.italic
            this.underline = style.underline
            this.bold = style.bold
        }
    }

    companion object {
        private var styleCounter = 0

        @JvmStatic
        fun getRGBValue(format: String, value: String): String? {
            var color: String? = null
            if (format.equals("name", ignoreCase = true)) {
                color = when (value) {
                    "transparent" -> "00000000"
                    "black" -> "000000ff"
                    "silver" -> "c0c0c0ff"
                    "gray" -> "808080ff"
                    "white" -> "ffffffff"
                    "maroon" -> "800000ff"
                    "red" -> "ff0000ff"
                    "purple" -> "800080ff"
                    "fuchsia" -> "ff00ffff"
                    "magenta" -> "ff00ffff "
                    "green" -> "008000ff"
                    "lime" -> "00ff00ff"
                    "olive" -> "808000ff"
                    "yellow" -> "ffff00ff"
                    "navy" -> "000080ff"
                    "blue" -> "0000ffff"
                    "teal" -> "008080ff"
                    "aqua" -> "00ffffff"
                    "cyan" -> "00ffffff "
                    else -> null
                }
            } else if (format.equals("&HBBGGRR", ignoreCase = true)) {
                val sb = StringBuilder()
                sb.append(value.substring(6))
                sb.append(value.substring(4, 5))
                sb.append(value.substring(2, 3))
                sb.append("ff")
                color = sb.toString()
            } else if (format.equals("&HAABBGGRR", ignoreCase = true)) {
                val sb = StringBuilder()
                sb.append(value.substring(8))
                sb.append(value.substring(6, 7))
                sb.append(value.substring(4, 5))
                sb.append(value.substring(2, 3))
                color = sb.toString()
            } else if (format.equals("decimalCodedBBGGRR", ignoreCase = true)) {
                var c = Integer.toHexString(value.toInt())
                while (c.length < 6) c = "0$c"
                color = c.substring(4) + c.substring(2, 4) + c.substring(0, 2) + "ff"
            } else if (format.equals("decimalCodedAABBGGRR", ignoreCase = true)) {
                var c = java.lang.Long.toHexString(value.toLong())
                while (c.length < 8) c = "0$c"
                color = c.substring(6) + c.substring(4, 6) + c.substring(2, 4) + c.substring(0, 2)
            }
            return color
        }

        @JvmStatic
        fun defaultID(): String {
            return "default" + styleCounter++
        }
    }
}
