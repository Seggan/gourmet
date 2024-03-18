package io.github.seggan.gourmet.chef

@JvmInline
value class ChefStack(private val num: Int) {

    init {
        require(num > 0) { "Stack number must be positive" }
    }

    override fun toString(): String {
        val strNum = num.toString()
        val ord = when {
            strNum.endsWith("1") -> "${strNum}st"
            strNum.endsWith("2") -> "${strNum}nd"
            strNum.endsWith("3") -> "${strNum}rd"
            else -> "${strNum}th"
        }
        return "$ord mixing bowl"
    }
}