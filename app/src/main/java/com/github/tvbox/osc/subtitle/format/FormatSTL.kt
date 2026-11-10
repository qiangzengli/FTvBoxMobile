package com.github.tvbox.osc.subtitle.format

import com.github.tvbox.osc.subtitle.exception.FatalParsingException
import com.github.tvbox.osc.subtitle.model.Style
import com.github.tvbox.osc.subtitle.model.Subtitle
import com.github.tvbox.osc.subtitle.model.Time
import com.github.tvbox.osc.subtitle.model.TimedTextObject
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date

class FormatSTL : TimedTextFileFormat {

    @Throws(IOException::class, FatalParsingException::class)
    override fun parseFile(fileName: String, `is`: InputStream): TimedTextObject {
        val tto = TimedTextObject()
        tto.fileName = fileName

        val gsiBlock = ByteArray(1024)
        val ttiBlock = ByteArray(128)

        try {
            createSTLStyles(tto)

            val bytesRead = `is`.read(gsiBlock)
            if (bytesRead < 1024) {
                throw FatalParsingException("The file must contain at least a GSI block")
            }

            val dfc = byteArrayOf(gsiBlock[6], gsiBlock[7])
            val fps = String(dfc).toInt()

            val cct = byteArrayOf(gsiBlock[12], gsiBlock[13])
            val table = String(cct).toInt()

            val opt = ByteArray(32)
            System.arraycopy(gsiBlock, 16, opt, 0, 32)
            val title = String(opt)

            val oet = ByteArray(32)
            System.arraycopy(gsiBlock, 48, oet, 0, 32)
            val episodeTitle = String(oet)

            val tnb = byteArrayOf(gsiBlock[238], gsiBlock[239], gsiBlock[240], gsiBlock[241], gsiBlock[242])
            val numberOfTTIBlocks = String(tnb).toInt()

            val tns = byteArrayOf(gsiBlock[243], gsiBlock[244], gsiBlock[245], gsiBlock[246], gsiBlock[247])
            val numberOfSubtitles = String(tns).toInt()

            tto.title = ("${title.trim()} ${episodeTitle.trim()}").trim()

            if (table > 4 || table < 0) {
                tto.warnings += "Invalid Character Code table number, corrupt data? will try to parse anyways assuming it is latin.\n\n"
            } else if (table != 0) {
                tto.warnings += "Only latin alphabet supported for import from STL, other languages may produce unexpected results.\n\n"
            }

            var subtitleNumber = 0
            var additionalText = false
            var currentCaption: Subtitle? = null

            for (i in 0 until numberOfTTIBlocks) {
                val ttiRead = `is`.read(ttiBlock)
                if (ttiRead < 128) {
                    tto.warnings += "Unexpected end of file, $i blocks read, expecting $numberOfTTIBlocks blocks in total.\n\n"
                    break
                }

                if (!additionalText) {
                    currentCaption = Subtitle()
                }

                val currentSubNumber = (ttiBlock[1].toInt() and 0xff) + 256 * (ttiBlock[2].toInt() and 0xff)
                if (currentSubNumber != subtitleNumber) {
                    tto.warnings += "Unexpected subtitle number at TTI block $i. Parsing proceeds...\n\n"
                }

                val ebn = ttiBlock[3].toInt()
                additionalText = ebn != -1

                val startTime = "${ttiBlock[5].toInt() and 0xff}:${ttiBlock[6].toInt() and 0xff}:${ttiBlock[7].toInt() and 0xff}:${ttiBlock[8].toInt() and 0xff}"
                val endTime = "${ttiBlock[9].toInt() and 0xff}:${ttiBlock[10].toInt() and 0xff}:${ttiBlock[11].toInt() and 0xff}:${ttiBlock[12].toInt() and 0xff}"
                val justification = ttiBlock[14].toInt()

                if (ttiBlock[15].toInt() == 0) {
                    val textField = ByteArray(112)
                    System.arraycopy(ttiBlock, 16, textField, 0, 112)

                    if (additionalText) {
                        currentCaption?.let { parseTextForSTL(it, textField, justification, tto) }
                    } else {
                        currentCaption?.start = Time("h:m:s:f/fps", "$startTime/$fps")
                        currentCaption?.end = Time("h:m:s:f/fps", "$endTime/$fps")
                        currentCaption?.let { parseTextForSTL(it, textField, justification, tto) }
                    }
                }

                if (!additionalText) {
                    subtitleNumber++
                }
            }

            if (subtitleNumber != numberOfSubtitles) {
                tto.warnings += "Number of parsed subtitles ($subtitleNumber) different from expected number of subtitles ($numberOfSubtitles).\n\n"
            }

            `is`.close()
            tto.cleanUnusedStyles()
        } catch (e: Exception) {
            e.printStackTrace()
            throw FatalParsingException("Format error in the file, migth be due to corrupt data.\n" + e.message)
        }

        tto.built = true
        return tto
    }

    override fun toFile(tto: TimedTextObject): ByteArray? {
        if (!tto.built) return null

        val gsiBlock = ByteArray(1024)
        var ttiBlock = ByteArray(128)
        val file = ByteArray(1024 + 128 * tto.captions.size)

        var extra = "850STL25.0110000".toByteArray()
        System.arraycopy(extra, 0, gsiBlock, 0, extra.size)

        extra = if (tto.title != null) tto.title.toByteArray() else tto.fileName.toByteArray()
        for (i in 0 until (224 - 16)) {
            if (i < extra.size) {
                gsiBlock[i + 16] = extra[i]
            } else {
                gsiBlock[i + 16] = 32
            }
        }

        val dateFormat = SimpleDateFormat("yyMMdd")
        val date = Date()
        var aux = dateFormat.format(date)
        aux += aux + "00"
        var aux2 = "" + tto.captions.size
        while (aux2.length < 5) aux2 = "0$aux2"
        aux += aux2 + aux2 + "0013216100000000"

        aux += tto.captions[tto.captions.firstKey()]!!.start.getTime("hhmmssff/25")
        aux += "11OOO"
        extra = aux.toByteArray()
        System.arraycopy(extra, 0, gsiBlock, 224, extra.size)

        for (i in 277 until 1024) {
            gsiBlock[i] = 32
        }

        System.arraycopy(gsiBlock, 0, file, 0, gsiBlock.size)

        val itrC = tto.captions.values.iterator()
        var subtitleNumber = 0
        while (itrC.hasNext()) {
            val currentC = itrC.next()
            ttiBlock[0] = 0
            ttiBlock[1] = (subtitleNumber % 256).toByte()
            ttiBlock[2] = (subtitleNumber / 256).toByte()
            ttiBlock[3] = 0xff.toByte()
            ttiBlock[4] = 0

            var timeCode = currentC.start.getTime("h:m:s:f/25").split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            ttiBlock[5] = timeCode[0].toByte()
            ttiBlock[6] = timeCode[1].toByte()
            ttiBlock[7] = timeCode[2].toByte()
            ttiBlock[8] = timeCode[3].toByte()

            timeCode = currentC.end.getTime("h:m:s:f/25").split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            ttiBlock[9] = timeCode[0].toByte()
            ttiBlock[10] = timeCode[1].toByte()
            ttiBlock[11] = timeCode[2].toByte()
            ttiBlock[12] = timeCode[3].toByte()

            ttiBlock[13] = 18

            val style = currentC.style
            if (style != null) {
                if (style.textAlign.contains("left")) {
                    ttiBlock[14] = 1
                } else if (style.textAlign.contains("right")) {
                    ttiBlock[14] = 3
                } else {
                    ttiBlock[14] = 2
                }
            } else {
                ttiBlock[14] = 2
            }

            ttiBlock[15] = 0

            val lines = currentC.content.split("<br />".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var pos = 16
            for (i in lines.indices) {
                lines[i] = lines[i].replace("<.*?>".toRegex(), "")
            }

            if (style != null) {
                if (style.italic) {
                    ttiBlock[pos++] = 0x80.toByte()
                } else {
                    ttiBlock[pos++] = 0x81.toByte()
                }
                if (style.underline) {
                    ttiBlock[pos++] = 0x82.toByte()
                } else {
                    ttiBlock[pos++] = 0x83.toByte()
                }

                val color = style.color?.substring(0, 6) ?: ""
                if (color.equals("000000", ignoreCase = true)) {
                    ttiBlock[pos++] = 0x00.toByte()
                } else if (color.equals("0000ff", ignoreCase = true)) {
                    ttiBlock[pos++] = 0x04.toByte()
                } else if (color.equals("00ffff", ignoreCase = true)) {
                    ttiBlock[pos++] = 0x06.toByte()
                } else if (color.equals("00ff00", ignoreCase = true)) {
                    ttiBlock[pos++] = 0x02.toByte()
                } else if (color.equals("ff0000", ignoreCase = true)) {
                    ttiBlock[pos++] = 0x01.toByte()
                } else if (color.equals("ffff00", ignoreCase = true)) {
                    ttiBlock[pos++] = 0x03.toByte()
                } else if (color.equals("ff00ff", ignoreCase = true)) {
                    ttiBlock[pos++] = 0x05.toByte()
                } else {
                    ttiBlock[pos++] = 0x07.toByte()
                }
            }

            for (i in lines.indices) {
                val chars = lines[i].toCharArray()
                for (j in chars.indices) {
                    if (pos > 126) break
                    if (chars[j] >= 0x20.toChar() && chars[j] <= 0x7f.toChar()) {
                        ttiBlock[pos++] = chars[j].code.toByte()
                    }
                }
                if (i + 1 < lines.size) {
                    ttiBlock[pos++] = 0x8A.toByte()
                }
            }

            while (pos < 128) {
                ttiBlock[pos++] = 0x8F.toByte()
            }

            System.arraycopy(ttiBlock, 0, file, 1024 + subtitleNumber * 128, ttiBlock.size)
            ttiBlock = ByteArray(128)
            subtitleNumber++
        }

        return file
    }

    private fun parseTextForSTL(currentCaption: Subtitle, textField: ByteArray, justification: Int, tto: TimedTextObject) {
        var italics = false
        var underline = false
        var color = "white"
        var style: Style?
        var text = ""

        var i = 0
        while (i < textField.size) {
            val b = textField[i].toInt()
            if (b < 0) {
                if (b <= -113) {
                    if (i + 1 < textField.size && textField[i] == textField[i + 1]) {
                        i++
                    }
                    when (b) {
                        -128 -> italics = true
                        -127 -> italics = false
                        -126 -> underline = true
                        -125 -> underline = false
                        -118 -> {
                            currentCaption.content += "$text<br />"
                            text = ""
                        }
                        -113 -> {
                            currentCaption.content += text
                            text = ""
                            if (underline) color += "U"
                            if (italics) color += "I"
                            style = tto.styling[color]

                            if (justification == 1) {
                                color += "L"
                                if (tto.styling[color] == null) {
                                    style = Style(color, style)
                                    style.textAlign = "bottom-left"
                                    tto.styling[color] = style
                                } else {
                                    style = tto.styling[color]
                                }
                            } else if (justification == 3) {
                                color += "R"
                                if (tto.styling[color] == null) {
                                    style = Style(color, style)
                                    style.textAlign = "bottom-rigth"
                                    tto.styling[color] = style
                                } else {
                                    style = tto.styling[color]
                                }
                            }

                            currentCaption.style = style
                            var key = currentCaption.start.mseconds
                            while (tto.captions.containsKey(key)) key++
                            tto.captions[key] = currentCaption
                            i = textField.size
                        }
                    }
                }
            } else if (b < 32) {
                if (i + 1 < textField.size && textField[i] == textField[i + 1]) {
                    i++
                }
                when (b) {
                    7 -> color = "white"
                    2 -> color = "green"
                    4 -> color = "blue"
                    6 -> color = "cyan"
                    1 -> color = "red"
                    3 -> color = "yellow"
                    5 -> color = "magenta"
                    0 -> color = "black"
                }
            } else {
                val x = byteArrayOf(textField[i])
                text += String(x)
            }
            i++
        }
    }

    private fun createSTLStyles(tto: TimedTextObject) {
        val colors = arrayOf("white", "green", "blue", "cyan", "red", "yellow", "magenta", "black")
        for (col in colors) {
            var s = Style(col)
            s.color = Style.getRGBValue("name", col)
            tto.styling[s.iD] = s

            s = Style(col + "U", s)
            s.underline = true
            tto.styling[s.iD] = s

            s = Style(col + "UI", s)
            s.italic = true
            tto.styling[s.iD] = s

            s = Style(col + "I", s)
            s.underline = false
            tto.styling[s.iD] = s
        }
    }
}
