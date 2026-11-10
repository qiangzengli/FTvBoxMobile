package com.github.tvbox.osc.subtitle.format

import com.github.tvbox.osc.subtitle.model.Subtitle
import com.github.tvbox.osc.subtitle.model.Time
import com.github.tvbox.osc.subtitle.model.TimedTextObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.ArrayList

class FormatSRT : TimedTextFileFormat {

    @Throws(IOException::class)
    override fun parseFile(fileName: String, `is`: InputStream): TimedTextObject {
        val tto = TimedTextObject()
        var caption = Subtitle()
        var captionNumber = 1
        var allGood: Boolean

        val `in` = InputStreamReader(`is`)
        val br = BufferedReader(`in`)
        tto.fileName = fileName

        var line = br.readLine()
        var lineCounter = 0
        try {
            while (line != null) {
                line = line.trim()
                lineCounter++
                if (line.isNotEmpty()) {
                    allGood = false
                    try {
                        val num = line.toInt()
                        if (num != captionNumber) {
                            throw Exception()
                        } else {
                            captionNumber++
                            allGood = true
                        }
                    } catch (e: Exception) {
                        tto.warnings += "$captionNumber expected at line $lineCounter\n skipping to next line\n\n"
                    }
                    if (allGood) {
                        try {
                            lineCounter++
                            line = br.readLine().trim()
                            val start = line.substring(0, 12)
                            val end = line.substring(line.length - 12)
                            caption.start = Time("hh:mm:ss,ms", start)
                            caption.end = Time("hh:mm:ss,ms", end)
                        } catch (e: Exception) {
                            tto.warnings += "incorrect time format at line $lineCounter"
                            allGood = false
                        }
                    }
                    if (allGood) {
                        lineCounter++
                        line = br.readLine().trim()
                        var text = ""
                        while (line.isNotEmpty()) {
                            text += "$line<br />"
                            line = br.readLine().trim()
                            lineCounter++
                        }
                        caption.content = text
                        var key = caption.start.mseconds
                        while (tto.captions.containsKey(key)) key++
                        if (key != caption.start.mseconds) {
                            tto.warnings += "caption with same start time found...\n\n"
                        }
                        tto.captions[key] = caption
                    }
                    while (line.isNotEmpty()) {
                        line = br.readLine().trim()
                        lineCounter++
                    }
                    caption = Subtitle()
                }
                line = br.readLine()
            }
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
        val file = ArrayList<String>(5 * tto.captions.size)
        val c = tto.captions.values
        val itr = c.iterator()
        var captionNumber = 1

        while (itr.hasNext()) {
            val current = itr.next()
            file.add(index++, "" + captionNumber++)
            if (tto.offset != 0) {
                current.start.mseconds += tto.offset
                current.end.mseconds += tto.offset
            }
            file.add(
                index++,
                current.start.getTime("hh:mm:ss,ms") + " --> " + current.end.getTime("hh:mm:ss,ms")
            )
            if (tto.offset != 0) {
                current.start.mseconds -= tto.offset
                current.end.mseconds -= tto.offset
            }
            val lines = cleanTextForSRT(current)
            var i = 0
            while (i < lines.size) file.add(index++, "" + lines[i++])
            file.add(index++, "")
        }

        return file.toTypedArray()
    }

    private fun cleanTextForSRT(current: Subtitle): Array<String> {
        val text = current.content
        val lines = text.split("<br />".toRegex()).toTypedArray()
        for (i in lines.indices) {
            lines[i] = lines[i].replace("\\<.*?\\>".toRegex(), "")
        }
        return lines
    }
}
