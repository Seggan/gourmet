package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.util.Location

class SyntaxException(message: String, row: Int, column: Int) : Exception(
    "Syntax error at ${Location.currentFile}:$row:$column: $message"
)