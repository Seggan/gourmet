package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.util.Location

class CompilationException(message: String, location: Location) : Exception(
    "Error at ${location.file}:${location.row}:${location.column}: $message"
)