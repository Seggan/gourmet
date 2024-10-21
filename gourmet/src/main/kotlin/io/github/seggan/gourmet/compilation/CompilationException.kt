package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.util.Location

class CompilationException(message: String) : Exception(message) {
    constructor(message: String, location: Location) : this(
        "Error at ${location.file}:${location.row}:${location.column}: $message"
    )
}