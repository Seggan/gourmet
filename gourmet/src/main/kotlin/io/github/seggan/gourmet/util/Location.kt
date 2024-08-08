package io.github.seggan.gourmet.util

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval

data class Location(val file: String, val row: Int, val column: Int, val text: String) {

    override fun toString(): String {
        return "line $row, column $column, statement '$text'"
    }

    companion object {
        internal var currentFile: String? = null
    }
}

val ParserRuleContext.location: Location
    get() = Location(
        Location.currentFile ?: "unknown",
        start.line,
        start.charPositionInLine,
        start.inputStream.getText(Interval(start.startIndex, stop.stopIndex))
    )
