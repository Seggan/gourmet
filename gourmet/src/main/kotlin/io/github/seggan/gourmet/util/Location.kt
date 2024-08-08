package io.github.seggan.gourmet.util

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval

data class Location(val row: Int, val column: Int, val text: String) {

    override fun toString(): String {
        return "line $row, column $column, statement '$text'"
    }
}

val ParserRuleContext.location: Location
    get() = Location(
        start.line,
        start.charPositionInLine,
        start.inputStream.getText(Interval(start.startIndex, stop.stopIndex))
    )
