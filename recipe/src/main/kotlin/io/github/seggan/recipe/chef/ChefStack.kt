package io.github.seggan.recipe.chef

data class ChefStack(val num: Int) {

    init {
        require(num > 0) { "Stack number must be positive" }
    }

    override fun toString(): String {
        val strNum = num.toString()
        val ord = when {
            strNum == "1" -> ""
            strNum.endsWith("1") -> "${strNum}st "
            strNum.endsWith("2") -> "${strNum}nd "
            strNum.endsWith("3") -> "${strNum}rd "
            else -> "${strNum}th "
        }
        return "${ord}mixing bowl"
    }
}