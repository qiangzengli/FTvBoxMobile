package com.github.tvbox.osc.subtitle.model

import com.github.tvbox.osc.subtitle.format.FormatASS
import com.github.tvbox.osc.subtitle.format.FormatSCC
import com.github.tvbox.osc.subtitle.format.FormatSRT
import com.github.tvbox.osc.subtitle.format.FormatSTL
import com.github.tvbox.osc.subtitle.format.FormatTTML
import java.util.Hashtable
import java.util.TreeMap

class TimedTextObject {
    @JvmField
    var title: String = ""

    @JvmField
    var description: String = ""

    @JvmField
    var copyrigth: String = ""

    @JvmField
    var author: String = ""

    @JvmField
    var fileName: String = ""

    @JvmField
    var language: String = ""

    @JvmField
    var styling: Hashtable<String, Style>

    @JvmField
    var layout: Hashtable<String, Region>

    @JvmField
    var captions: TreeMap<Int, Subtitle>

    @JvmField
    var warnings: String

    @JvmField
    var useASSInsteadOfSSA: Boolean = true

    @JvmField
    var offset: Int = 0

    @JvmField
    var built: Boolean = false

    init {
        styling = Hashtable()
        layout = Hashtable()
        captions = TreeMap()
        warnings = "List of non fatal errors produced during parsing:\n\n"
    }

    fun toSRT(): Array<String>? {
        return FormatSRT().toFile(this) as? Array<String>
    }

    fun toASS(): Array<String>? {
        return FormatASS().toFile(this) as? Array<String>
    }

    fun toSTL(): ByteArray? {
        return FormatSTL().toFile(this) as? ByteArray
    }

    fun toSCC(): Array<String>? {
        return FormatSCC().toFile(this) as? Array<String>
    }

    fun toTTML(): Array<String>? {
        return FormatTTML().toFile(this) as? Array<String>
    }

    fun cleanUnusedStyles() {
        val usedStyles = Hashtable<String, Style>()
        val itrC = captions.values.iterator()
        while (itrC.hasNext()) {
            val current = itrC.next()
            if (current.style != null) {
                val iD = current.style!!.iD
                if (iD != null && !usedStyles.containsKey(iD)) {
                    usedStyles[iD] = current.style!!
                }
            }
        }
        this.styling = usedStyles
    }
}
