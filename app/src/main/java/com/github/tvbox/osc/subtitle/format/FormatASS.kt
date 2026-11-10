package com.github.tvbox.osc.subtitle.format

import com.github.tvbox.osc.subtitle.model.Style
import com.github.tvbox.osc.subtitle.model.Subtitle
import com.github.tvbox.osc.subtitle.model.Time
import com.github.tvbox.osc.subtitle.model.TimedTextObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.ArrayList

class FormatASS : TimedTextFileFormat {

    @Throws(IOException::class)
    override fun parseFile(fileName: String, `is`: InputStream): TimedTextObject {
        val tto = TimedTextObject()
        tto.fileName = fileName

        var caption: Subtitle
        var style: Style

        var timer = 100f
        var isASS = false

        var styleFormat: Array<String> = emptyArray()
        var dialogueFormat: Array<String> = emptyArray()

        val `in` = InputStreamReader(`is`)
        val br = BufferedReader(`in`)

        var line: String?
        var lineCounter = 0
        try {
            line = br.readLine()
            lineCounter++
            while (line != null) {
                line = line.trim()
                if (line.startsWith("[")) {
                    if (line.equals("[Script info]", ignoreCase = true)) {
                        lineCounter++
                        line = br.readLine()?.trim()
                        while (line != null && !line.startsWith("[")) {
                            if (line.startsWith("Title:")) {
                                val titleArr = line.split(":".toRegex()).toTypedArray()
                                tto.title = if (titleArr.size > 1) titleArr[1].trim() else ""
                            } else if (line.startsWith("Original Script:")) {
                                val authorArr = line.split(":".toRegex()).toTypedArray()
                                tto.author = if (authorArr.size > 1) authorArr[1].trim() else ""
                            } else if (line.startsWith("Script Type:")) {
                                val scriptTypeParts = line.split(":".toRegex())
                                if (scriptTypeParts.size > 1) {
                                    val scriptType = scriptTypeParts[1].trim()
                                    if (scriptType.equals("v4.00+", ignoreCase = true)) {
                                        isASS = true
                                    } else if (!scriptType.equals("v4.00", ignoreCase = true)) {
                                        tto.warnings += "Script version is older than 4.00, it may produce parsing errors."
                                    }
                                }
                            } else if (line.startsWith("Timer:")) {
                                val timerParts = line.split(":".toRegex())
                                if (timerParts.size > 1) {
                                    timer = timerParts[1].trim().replace(',', '.').toFloat()
                                }
                            }
                            lineCounter++
                            line = br.readLine()?.trim()
                        }
                    } else if (line.equals("[v4 Styles]", ignoreCase = true) ||
                        line.equals("[v4 Styles+]", ignoreCase = true) ||
                        line.equals("[v4+ Styles]", ignoreCase = true)
                    ) {
                        if (line.contains("+") && !isASS) {
                            isASS = true
                            tto.warnings += "ScriptType should be set to v4:00+ in the [Script Info] section.\n\n"
                        }
                        lineCounter++
                        line = br.readLine()
                        if (line != null && !line.startsWith("Format:")) {
                            tto.warnings += "Format: (format definition) expected at line $line for the styles section\n\n"
                            while (line != null && !line.startsWith("Format:")) {
                                lineCounter++
                                line = br.readLine()
                            }
                        }
                        if (line != null) {
                            val formatParts = line.split(":".toRegex())
                            if (formatParts.size > 1) {
                                styleFormat = formatParts[1].trim().split(",".toRegex()).toTypedArray()
                            }
                            lineCounter++
                            line = br.readLine()
                            while (line != null && !line.startsWith("Style:")) {
                                tto.warnings += "Style: (format definition) expected at line $line for the styles section\n\n"
                                lineCounter++
                                line = br.readLine()
                            }
                            if (line != null) {
                                val styleParts = line.split(":".toRegex())
                                if (styleParts.size > 1) {
                                    style = parseStyleForASS(styleParts[1].trim().split(",".toRegex()).toTypedArray(), styleFormat, lineCounter, isASS, tto)
                                    val styleId = style.iD
                                    tto.styling[styleId] = style
                                }
                            }
                        }
                    } else if (line.equals("[Events]", ignoreCase = true)) {
                        lineCounter++
                        line = br.readLine()
                        tto.warnings += "Only dialogue events are considered, all other events are ignored.\n\n"
                        if (line != null && !line.startsWith("Format:")) {
                            tto.warnings += "Format: (format definition) expected at line $line for the events section\n\n"
                            while (line != null && !line.startsWith("Format:")) {
                                lineCounter++
                                line = br.readLine()
                            }
                        }
                        if (line != null) {
                            val dialogueFormatParts = line.split(":".toRegex())
                            if (dialogueFormatParts.size > 1) {
                                dialogueFormat = dialogueFormatParts[1].trim().split(",".toRegex()).toTypedArray()
                            }
                            lineCounter++
                            line = br.readLine()
                            while (line != null && !line.startsWith("[")) {
                                if (line.startsWith("Dialogue:")) {
                                    val splitColon = line.split(":", limit = 2)
                                    if (splitColon.size > 1) {
                                        caption = parseDialogueForASS(splitColon[1].trim().split(",", limit = 10).toTypedArray(), dialogueFormat, timer, tto)
                                        var key = caption.start.mseconds
                                        while (tto.captions.containsKey(key)) key++
                                        tto.captions[key] = caption
                                    }
                                }
                                lineCounter++
                                line = br.readLine()
                            }
                        }
                    } else if (line.equals("[Fonts]", ignoreCase = true) || line.equals("[Graphics]", ignoreCase = true)) {
                        tto.warnings += "The section " + line + " is not supported for conversion, all information there will be lost.\n\n"
                    } else {
                        tto.warnings += "Unrecognized section: " + line + " all information there is ignored."
                    }
                }
                if (line != null && !line.startsWith("[")) {
                    line = br.readLine()
                    lineCounter++
                }
            }
            tto.cleanUnusedStyles()
        } catch (e: NullPointerException) {
            tto.warnings += "unexpected end of file, maybe last caption is not complete.\n\n"
        } finally {
            `is`.close()
        }

        tto.built = true
        return tto
    }

    override fun toFile(tto: TimedTextObject): Array<String>? {
        if (!tto.built) return null

        var index = 0
        val file = ArrayList<String>(30 + tto.styling.size + tto.captions.size)

        file.add(index++, "[Script Info]")
        var title = "Title: "
        title += if (tto.title.isEmpty()) tto.fileName else tto.title
        file.add(index++, title)

        var author = "Original Script: "
        author += if (tto.author.isEmpty()) "Unknown" else tto.author
        file.add(index++, author)

        if (tto.copyrigth.isNotEmpty()) file.add(index++, "; " + tto.copyrigth)
        if (tto.description.isNotEmpty()) file.add(index++, "; " + tto.description)
        file.add(index++, "; Converted by the Online Subtitle Converter developed by J. David Requejo")

        if (tto.useASSInsteadOfSSA) file.add(index++, "Script Type: V4.00+")
        else file.add(index++, "Script Type: V4.00")
        file.add(index++, "Collisions: Normal")
        file.add(index++, "Timer: 100,0000")
        if (tto.useASSInsteadOfSSA) file.add(index++, "WrapStyle: 1")
        file.add(index++, "")

        if (tto.useASSInsteadOfSSA) file.add(index++, "[V4+ Styles]")
        else file.add(index++, "[V4 Styles]")

        if (tto.useASSInsteadOfSSA) {
            file.add(index++, "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        } else {
            file.add(index++, "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, TertiaryColour, BackColour, Bold, Italic, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, AlphaLevel, Encoding")
        }

        val itrS = tto.styling.values.iterator()
        while (itrS.hasNext()) {
            var styleLine = "Style: "
            val current = itrS.next()
            styleLine += current.iD + ","
            styleLine += current.font + ","
            styleLine += current.fontSize + ","
            styleLine += getColorsForASS(tto.useASSInsteadOfSSA, current)
            styleLine += getOptionsForASS(tto.useASSInsteadOfSSA, current)
            styleLine += "1,2,2,"
            styleLine += getAlignForASS(tto.useASSInsteadOfSSA, current.textAlign)
            styleLine += ",0,0,0,"
            if (!tto.useASSInsteadOfSSA) styleLine += "0,"
            styleLine += "0"
            file.add(index++, styleLine)
        }
        file.add(index++, "")

        file.add(index++, "[Events]")
        if (tto.useASSInsteadOfSSA) {
            file.add(index++, "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        } else {
            file.add(index++, "Format: Marked, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        }

        val itrC = tto.captions.values.iterator()
        while (itrC.hasNext()) {
            var lineStr = "Dialogue: 0,"
            val current = itrC.next()
            if (tto.offset != 0) {
                current.start.mseconds += tto.offset
                current.end.mseconds += tto.offset
            }
            lineStr += current.start.getTime("h:mm:ss.cs") + ","
            lineStr += current.end.getTime("h:mm:ss.cs") + ","
            if (tto.offset != 0) {
                current.start.mseconds -= tto.offset
                current.end.mseconds -= tto.offset
            }
            if (current.style != null) lineStr += current.style!!.iD
            else lineStr += "Default"
            lineStr += ",,0000,0000,0000,,"
            lineStr += current.content.replace("<br />", "\\N").replace("\\<.*?\\>".toRegex(), "")
            file.add(index++, lineStr)
        }
        file.add(index++, "")

        return file.toTypedArray()
    }

    private fun parseStyleForASS(line: Array<String>, styleFormat: Array<String>, index: Int, isASS: Boolean, tto: TimedTextObject): Style {
        val newStyle = Style(Style.defaultID())
        if (line.size != styleFormat.size) {
            tto.warnings += "incorrectly formated line at $index\n\n"
        } else {
            for (i in styleFormat.indices) {
                val formatParam = styleFormat[i].trim()
                if (formatParam.equals("Name", ignoreCase = true)) {
                    newStyle.iD = line[i].trim()
                } else if (formatParam.equals("Fontname", ignoreCase = true)) {
                    newStyle.font = line[i].trim()
                } else if (formatParam.equals("Fontsize", ignoreCase = true)) {
                    newStyle.fontSize = line[i].trim()
                } else if (formatParam.equals("PrimaryColour", ignoreCase = true)) {
                    val color = line[i].trim()
                    if (isASS) {
                        newStyle.color = if (color.startsWith("&H")) Style.getRGBValue("&HAABBGGRR", color)
                        else Style.getRGBValue("decimalCodedAABBGGRR", color)
                    } else {
                        newStyle.color = if (color.startsWith("&H")) Style.getRGBValue("&HBBGGRR", color)
                        else Style.getRGBValue("decimalCodedBBGGRR", color)
                    }
                } else if (formatParam.equals("BackColour", ignoreCase = true)) {
                    val color = line[i].trim()
                    if (isASS) {
                        newStyle.backgroundColor = if (color.startsWith("&H")) Style.getRGBValue("&HAABBGGRR", color)
                        else Style.getRGBValue("decimalCodedAABBGGRR", color)
                    } else {
                        newStyle.backgroundColor = if (color.startsWith("&H")) Style.getRGBValue("&HBBGGRR", color)
                        else Style.getRGBValue("decimalCodedBBGGRR", color)
                    }
                } else if (formatParam.equals("Bold", ignoreCase = true)) {
                    newStyle.bold = line[i].trim().toBoolean()
                } else if (formatParam.equals("Italic", ignoreCase = true)) {
                    newStyle.italic = line[i].trim().toBoolean()
                } else if (formatParam.equals("Underline", ignoreCase = true)) {
                    newStyle.underline = line[i].trim().toBoolean()
                } else if (formatParam.equals("Alignment", ignoreCase = true)) {
                    val placement = line[i].trim().toInt()
                    if (isASS) {
                        when (placement) {
                            1 -> newStyle.textAlign = "bottom-left"
                            2 -> newStyle.textAlign = "bottom-center"
                            3 -> newStyle.textAlign = "bottom-right"
                            4 -> newStyle.textAlign = "mid-left"
                            5 -> newStyle.textAlign = "mid-center"
                            6 -> newStyle.textAlign = "mid-right"
                            7 -> newStyle.textAlign = "top-left"
                            8 -> newStyle.textAlign = "top-center"
                            9 -> newStyle.textAlign = "top-right"
                            else -> tto.warnings += "undefined alignment for style at line $index\n\n"
                        }
                    } else {
                        when (placement) {
                            9 -> newStyle.textAlign = "bottom-left"
                            10 -> newStyle.textAlign = "bottom-center"
                            11 -> newStyle.textAlign = "bottom-right"
                            1 -> newStyle.textAlign = "mid-left"
                            2 -> newStyle.textAlign = "mid-center"
                            3 -> newStyle.textAlign = "mid-right"
                            5 -> newStyle.textAlign = "top-left"
                            6 -> newStyle.textAlign = "top-center"
                            7 -> newStyle.textAlign = "top-right"
                            else -> tto.warnings += "undefined alignment for style at line $index\n\n"
                        }
                    }
                }
            }
        }
        return newStyle
    }

    private fun parseDialogueForASS(line: Array<String>, dialogueFormat: Array<String>, timer: Float, tto: TimedTextObject): Subtitle {
        val newCaption = Subtitle()
        val captionText = line[9]
        newCaption.content = captionText.replace("\\{.*?\\}".toRegex(), "").replace("\n", "<br />").replace("\\N", "<br />")

        for (i in dialogueFormat.indices) {
            val formatParam = dialogueFormat[i].trim()
            if (formatParam.equals("Style", ignoreCase = true)) {
                newCaption.lyricCurrent = "PLAYING_CENTER" == line[i].trim()
                val s = tto.styling[line[i].trim()]
                if (s != null) newCaption.style = s
                else tto.warnings += "undefined style: " + line[i].trim() + "\n\n"
            } else if (formatParam.equals("Start", ignoreCase = true)) {
                newCaption.start = Time("h:mm:ss.cs", line[i].trim())
            } else if (formatParam.equals("End", ignoreCase = true)) {
                newCaption.end = Time("h:mm:ss.cs", line[i].trim())
            }
        }

        if (timer != 100f) {
            newCaption.start.mseconds = (newCaption.start.mseconds / (timer / 100)).toInt()
            newCaption.end.mseconds = (newCaption.end.mseconds / (timer / 100)).toInt()
        }
        return newCaption
    }

    private fun getColorsForASS(useASSInsteadOfSSA: Boolean, style: Style): String {
        val color = style.color ?: "00000000"
        val bgColor = style.backgroundColor ?: "00000000"
        return if (useASSInsteadOfSSA) {
            val cHex = "00" + color.substring(4, 6) + color.substring(2, 4) + color.substring(0, 2)
            val bgHex = "80" + bgColor.substring(4, 6) + bgColor.substring(2, 4) + bgColor.substring(0, 2)
            Integer.parseInt(cHex, 16).toString() + ",16777215,0," + java.lang.Long.parseLong(bgHex, 16) + ","
        } else {
            val cHex = color.substring(4, 6) + color.substring(2, 4) + color.substring(0, 2)
            val bgHex = bgColor.substring(4, 6) + bgColor.substring(2, 4) + bgColor.substring(0, 2)
            java.lang.Long.parseLong(cHex, 16).toString() + ",16777215,0," + java.lang.Long.parseLong(bgHex, 16) + ","
        }
    }

    private fun getOptionsForASS(useASSInsteadOfSSA: Boolean, style: Style): String {
        var options = if (style.bold) "-1," else "0,"
        options += if (style.italic) "-1," else "0,"
        if (useASSInsteadOfSSA) {
            options += if (style.underline) "-1," else "0,"
            options += "0,100,100,0,0,"
        }
        return options
    }

    private fun getAlignForASS(useASSInsteadOfSSA: Boolean, align: String?): Int {
        return if (useASSInsteadOfSSA) {
            when (align) {
                "bottom-left" -> 1
                "bottom-center" -> 2
                "bottom-right" -> 3
                "mid-left" -> 4
                "mid-center" -> 5
                "mid-right" -> 6
                "top-left" -> 7
                "top-center" -> 8
                "top-right" -> 9
                else -> 2
            }
        } else {
            when (align) {
                "bottom-left" -> 9
                "bottom-center" -> 10
                "bottom-right" -> 11
                "mid-left" -> 1
                "mid-center" -> 2
                "mid-right" -> 3
                "top-left" -> 5
                "top-center" -> 6
                "top-right" -> 7
                else -> 10
            }
        }
    }
}
