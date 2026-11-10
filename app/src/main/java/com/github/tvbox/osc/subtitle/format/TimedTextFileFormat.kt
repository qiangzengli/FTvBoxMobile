package com.github.tvbox.osc.subtitle.format

import com.github.tvbox.osc.subtitle.exception.FatalParsingException
import com.github.tvbox.osc.subtitle.model.TimedTextObject
import java.io.IOException
import java.io.InputStream

interface TimedTextFileFormat {
    /**
     * This methods receives the path to a file, parses it, and returns a TimedTextObject
     *
     * @return TimedTextObject representing the parsed file
     * @throws IOException when having trouble reading the file from the given path
     */
    @Throws(IOException::class, FatalParsingException::class)
    fun parseFile(fileName: String, `is`: InputStream): TimedTextObject

    /**
     * This method transforms a given TimedTextObject into a formatted subtitle file
     *
     * @param tto the object to transform into a file
     * @return NULL if the given TimedTextObject has not been built first,
     * or String[] where each String is at least a line, if size is 2, then the file has at least two lines.
     * or byte[] in case the file is a binary (as is the case of STL format)
     */
    fun toFile(tto: TimedTextObject): Any?
}
