package com.github.tvbox.osc.subtitle.format

import com.github.tvbox.osc.subtitle.exception.FatalParsingException
import com.github.tvbox.osc.subtitle.model.Style
import com.github.tvbox.osc.subtitle.model.Subtitle
import com.github.tvbox.osc.subtitle.model.Time
import com.github.tvbox.osc.subtitle.model.TimedTextObject
import org.w3c.dom.Document
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import javax.xml.parsers.DocumentBuilderFactory

class FormatTTML : TimedTextFileFormat {

    @Throws(IOException::class, FatalParsingException::class)
    override fun parseFile(fileName: String, `is`: InputStream): TimedTextObject {
        val tto = TimedTextObject()
        tto.fileName = fileName

        val dbFactory = DocumentBuilderFactory.newInstance()
        try {
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(`is`)
            doc.documentElement.normalize()

            // recover metadata
            var node = doc.getElementsByTagName("ttm:title").item(0)
            if (node != null) tto.title = node.textContent
            node = doc.getElementsByTagName("ttm:copyright").item(0)
            if (node != null) tto.copyrigth = node.textContent
            node = doc.getElementsByTagName("ttm:desc").item(0)
            if (node != null) tto.description = node.textContent

            // recover styles and captions
            val styleN = doc.getElementsByTagName("style")
            val captionsN = doc.getElementsByTagName("p")

            tto.warnings += "Styling attributes are only recognized inside a style definition, to be referenced later in the captions.\n\n"

            for (i in 0 until styleN.length) {
                var style = Style(Style.defaultID())
                node = styleN.item(i)
                val attr = node.attributes

                var currentAtr = attr.getNamedItem("id")
                if (currentAtr != null) {
                    style.iD = currentAtr.nodeValue ?: ""
                }
                currentAtr = attr.getNamedItem("xml:id")
                if (currentAtr != null) {
                    style.iD = currentAtr.nodeValue ?: ""
                }

                currentAtr = attr.getNamedItem("style")
                if (currentAtr != null) {
                    val baseStyle = tto.styling[currentAtr.nodeValue]
                    if (baseStyle != null) {
                        style = Style(style.iD, baseStyle)
                    }
                }

                currentAtr = attr.getNamedItem("tts:backgroundColor")
                if (currentAtr != null) {
                    style.backgroundColor = parseColor(currentAtr.nodeValue, tto)
                }

                currentAtr = attr.getNamedItem("tts:color")
                if (currentAtr != null) {
                    style.color = parseColor(currentAtr.nodeValue, tto)
                }

                currentAtr = attr.getNamedItem("tts:fontFamily")
                if (currentAtr != null) {
                    style.font = currentAtr.nodeValue
                }

                currentAtr = attr.getNamedItem("tts:fontSize")
                if (currentAtr != null) {
                    style.fontSize = currentAtr.nodeValue
                }

                currentAtr = attr.getNamedItem("tts:fontStyle")
                if (currentAtr != null) {
                    if (currentAtr.nodeValue.equals("italic", ignoreCase = true) ||
                        currentAtr.nodeValue.equals("oblique", ignoreCase = true)
                    ) {
                        style.italic = true
                    } else if (currentAtr.nodeValue.equals("normal", ignoreCase = true)) {
                        style.italic = false
                    }
                }

                currentAtr = attr.getNamedItem("tts:fontWeight")
                if (currentAtr != null) {
                    if (currentAtr.nodeValue.equals("bold", ignoreCase = true)) {
                        style.bold = true
                    } else if (currentAtr.nodeValue.equals("normal", ignoreCase = true)) {
                        style.bold = false
                    }
                }

                currentAtr = attr.getNamedItem("tts:opacity")
                if (currentAtr != null) {
                    try {
                        var alpha = currentAtr.nodeValue.toFloat()
                        if (alpha > 1) alpha = 1f else if (alpha < 0) alpha = 0f
                        var aa = Integer.toHexString((alpha * 255).toInt())
                        if (aa.length < 2) aa = "0$aa"
                        val c = style.color
                        if (c != null && c.length >= 6) {
                            style.color = c.substring(0, 6) + aa
                        }
                        val bg = style.backgroundColor
                        if (bg != null && bg.length >= 6) {
                            style.backgroundColor = bg.substring(0, 6) + aa
                        }
                    } catch (e: NumberFormatException) {
                        // ignore
                    }
                }

                currentAtr = attr.getNamedItem("tts:textAlign")
                if (currentAtr != null) {
                    if (currentAtr.nodeValue.equals("left", ignoreCase = true) ||
                        currentAtr.nodeValue.equals("start", ignoreCase = true)
                    ) {
                        style.textAlign = "bottom-left"
                    } else if (currentAtr.nodeValue.equals("right", ignoreCase = true) ||
                        currentAtr.nodeValue.equals("end", ignoreCase = true)
                    ) {
                        style.textAlign = "bottom-right"
                    }
                }

                currentAtr = attr.getNamedItem("tts:textDecoration")
                if (currentAtr != null) {
                    if (currentAtr.nodeValue.equals("underline", ignoreCase = true)) {
                        style.underline = true
                    } else if (currentAtr.nodeValue.equals("noUnderline", ignoreCase = true)) {
                        style.underline = false
                    }
                }

                tto.styling[style.iD] = style
            }

            for (i in 0 until captionsN.length) {
                val caption = Subtitle()
                caption.content = ""
                var validCaption = true
                node = captionsN.item(i)
                val attr = node.attributes

                var currentAtr = attr.getNamedItem("begin")
                caption.start = Time("", "")
                caption.end = Time("", "")
                if (currentAtr != null) {
                    caption.start.mseconds = parseTimeExpression(currentAtr.nodeValue, tto, doc)
                }

                currentAtr = attr.getNamedItem("end")
                if (currentAtr != null) {
                    caption.end.mseconds = parseTimeExpression(currentAtr.nodeValue, tto, doc)
                } else {
                    currentAtr = attr.getNamedItem("dur")
                    if (currentAtr != null) {
                        caption.end.mseconds = caption.start.mseconds + parseTimeExpression(currentAtr.nodeValue, tto, doc)
                    } else {
                        validCaption = false
                    }
                }

                currentAtr = attr.getNamedItem("style")
                if (currentAtr != null) {
                    val style = tto.styling[currentAtr.nodeValue]
                    if (style != null) {
                        caption.style = style
                    } else {
                        tto.warnings += "unrecoginzed style referenced: ${currentAtr.nodeValue}\n\n"
                    }
                }

                val textN = node.childNodes
                val contentBuilder = StringBuilder()
                for (j in 0 until textN.length) {
                    if (textN.item(j).nodeName == "#text") {
                        contentBuilder.append(textN.item(j).textContent.trim())
                    } else if (textN.item(j).nodeName == "br") {
                        contentBuilder.append("<br />")
                    }
                }
                caption.content = contentBuilder.toString()

                if (caption.content.replace("<br />", "").trim().isEmpty()) {
                    validCaption = false
                }

                if (validCaption) {
                    var key = caption.start.mseconds
                    while (tto.captions.containsKey(key)) key++
                    tto.captions[key] = caption
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw FatalParsingException("Error during parsing: " + e.message)
        }

        tto.built = true
        return tto
    }

    override fun toFile(tto: TimedTextObject): Array<String>? {
        if (!tto.built) return null

        val file = ArrayList<String>(30 + tto.styling.size + tto.captions.size)
        file.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        file.add("<tt xml:lang=\"${tto.language}\" xmlns=\"http://www.w3.org/ns/ttml\" xmlns:tts=\"http://www.w3.org/ns/ttml#styling\">")
        file.add("\t<head>")
        file.add("\t\t<metadata xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\">")

        val title = if (tto.title.isNullOrEmpty()) tto.fileName else tto.title
        file.add("\t\t\t<ttm:title>$title</ttm:title>")
        if (!tto.copyrigth.isNullOrEmpty()) {
            file.add("\t\t\t<ttm:copyright>${tto.copyrigth}</ttm:copyright>")
        }
        var desc = "Converted by the Online Subtitle Converter developed by J. David Requejo\n"
        if (!tto.author.isNullOrEmpty()) {
            desc += "\n Original file by: ${tto.author}\n"
        }
        if (!tto.description.isNullOrEmpty()) {
            desc += "${tto.description}\n"
        }
        file.add("\t\t\t<ttm:desc>$desc\t\t\t</ttm:desc>")
        file.add("\t\t</metadata>")
        file.add("\t\t<styling>")

        for (style in tto.styling.values) {
            var line = "\t\t\t<style xml:id=\"${style.iD}\""
            if (style.color != null) line += " tts:color=\"#${style.color}\""
            if (style.backgroundColor != null) line += " tts:backgroundColor=\"#${style.backgroundColor}\""
            if (style.font != null) line += " tts:fontFamily=\"${style.font}\""
            if (style.fontSize != null) line += " tts:fontSize=\"${style.fontSize}\""
            if (style.italic) line += " tts:fontStyle=\"italic\""
            if (style.bold) line += " tts:fontWeight=\"bold\""
            line += " tts:textAlign=\""
            line += if (style.textAlign.contains("left")) {
                "left\""
            } else if (style.textAlign.contains("right")) {
                "rigth\""
            } else {
                "center\""
            }
            if (style.underline) line += " tts:textDecoration=\"underline\""
            line += " />"
            file.add(line)
        }

        file.add("\t\t</styling>")
        file.add("\t</head>")
        file.add("\t<body>")
        file.add("\t\t<div>")

        for (caption in tto.captions.values) {
            var line = "\t\t\t<p begin=\"${caption.start.getTime("hh:mm:ss,ms").replace(',', '.')}\""
            line += " end=\"${caption.end.getTime("hh:mm:ss,ms").replace(',', '.')}\""
            val style = caption.style
            if (style != null) line += " style=\"${style.iD}\""
            line += " >${caption.content}</p>\n"
            file.add(line)
        }

        file.add("\t\t</div>")
        file.add("\t</body>")
        file.add("</tt>")
        file.add("")

        return file.toTypedArray()
    }

    private fun parseColor(color: String, tto: TimedTextObject): String {
        var value: String
        if (color.startsWith("#")) {
            value = if (color.length == 7) {
                color.substring(1) + "ff"
            } else if (color.length == 9) {
                color.substring(1)
            } else {
                tto.warnings += "Unrecoginzed format: $color\n\n"
                "ffffffff"
            }
        } else if (color.startsWith("rgb")) {
            val alpha = color.startsWith("rgba")
            try {
                val values = color.split("\\(".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val r = values[0].trim().toInt()
                val g = values[1].trim().toInt()
                val b = values[2].trim().substring(0, 2).toInt()
                val a = if (alpha) values[3].trim().substring(0, 2).toInt() else 255

                val hexR = Integer.toHexString(r).padStart(2, '0')
                val hexG = Integer.toHexString(g).padStart(2, '0')
                val hexB = Integer.toHexString(b).padStart(2, '0')
                val hexA = if (alpha) Integer.toHexString(a).padStart(2, '0') else "ff"

                value = hexR + hexG + hexB + hexA
            } catch (e: Exception) {
                value = "ffffffff"
                tto.warnings += "Unrecoginzed color: $color\n\n"
            }
        } else {
            val rgbVal = Style.getRGBValue("name", color)
            if (rgbVal.isNullOrEmpty()) {
                value = "ffffffff"
                tto.warnings += "Unrecoginzed color: $color\n\n"
            } else {
                value = rgbVal
            }
        }
        return value
    }

    private fun parseTimeExpression(timeExpressionParam: String, tto: TimedTextObject, doc: Document): Int {
        var timeExpression = timeExpressionParam
        var mSeconds = 0
        if (timeExpression.contains(":")) {
            val parts = timeExpression.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size == 3) {
                val h = parts[0].toInt()
                val m = parts[1].toInt()
                val s = parts[2].toFloat()
                mSeconds = h * 3600000 + m * 60000 + (s * 1000).toInt()
            } else if (parts.size == 4) {
                val h = parts[0].toInt()
                val m = parts[1].toInt()
                val s = parts[2].toInt()
                val f = parts[3].toFloat()
                var frameRate = 25
                val n = doc.getElementsByTagName("ttp:frameRate").item(0)
                if (n != null) {
                    try {
                        frameRate = n.nodeValue.toInt()
                    } catch (e: NumberFormatException) {
                        // use default
                    }
                }
                mSeconds = h * 3600000 + m * 60000 + s * 1000 + (f * 1000 / frameRate).toInt()
            }
        } else {
            val metric = timeExpression.substring(timeExpression.length - 1)
            timeExpression = timeExpression.substring(0, timeExpression.length - 1).replace(',', '.').trim()
            try {
                val time = timeExpression.toDouble()
                if (metric.equals("h", ignoreCase = true)) {
                    mSeconds = (time * 3600000).toInt()
                } else if (metric.equals("m", ignoreCase = true)) {
                    mSeconds = (time * 60000).toInt()
                } else if (metric.equals("s", ignoreCase = true)) {
                    mSeconds = (time * 1000).toInt()
                } else if (metric.equals("ms", ignoreCase = true)) {
                    mSeconds = time.toInt()
                } else if (metric.equals("f", ignoreCase = true)) {
                    var frameRate = 25
                    val n = doc.getElementsByTagName("ttp:frameRate").item(0)
                    if (n != null) {
                        try {
                            frameRate = n.nodeValue.toInt()
                        } catch (e: Exception) {}
                    }
                    mSeconds = (time * 1000 / frameRate).toInt()
                } else if (metric.equals("t", ignoreCase = true)) {
                    var tickRate = 1
                    val n = doc.getElementsByTagName("ttp:tickRate").item(0)
                    if (n != null) {
                        try {
                            tickRate = n.nodeValue.toInt()
                        } catch (e: Exception) {}
                    }
                    mSeconds = (time * 1000 / tickRate).toInt()
                }
            } catch (e: NumberFormatException) {
                // ignore
            }
        }
        return mSeconds
    }
}
