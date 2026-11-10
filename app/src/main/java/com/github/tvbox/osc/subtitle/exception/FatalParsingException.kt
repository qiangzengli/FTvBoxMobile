package com.github.tvbox.osc.subtitle.exception

/**
 * This class represents problems that may arise during the parsing of a subtitle file.
 *
 * @author J. David
 */
class FatalParsingException(private val parsingError: String) : Exception(parsingError) {
    override val message: String
        get() = parsingError

    override fun getLocalizedMessage(): String {
        return parsingError
    }

    companion object {
        private const val serialVersionUID = 6798827566637277804L
    }
}
