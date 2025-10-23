package io.github.seggan.gourmet.compilation.optimization

class BlockInliner private constructor(code: String) {

    private val preCode = code.substringBefore(MAIN_LOOP_HEADER)
    private val entryBlock = entryPointRegex.find(preCode)!!.groupValues[1].toInt()
    private var code = code.substringAfter(MAIN_LOOP_HEADER).substringBeforeLast("};")
    private var blockNum = 1

    private fun inlineBlocks() {
        while (true) {
            val block = getBlock() ?: break
            if ("push $blockNum;//[noinline]" in code || blockNum == entryBlock) {
                blockNum++
                continue
            }
            val regex = """push $blockNum;//\[inline]\n\s+""".toRegex()
            val matches = regex.findAll(code).toList()
            if (matches.size == 1) {
                code = code.replaceRange(matches[0].range, block.code)
                if (matches[0].range.first < block.start) {
                    // Adjust block start and end positions if inlining occurred before the block
                    val lengthDiff = block.code.length - matches[0].value.length
                    val newBlockStart = block.start + lengthDiff
                    val newBlockEnd = block.end + lengthDiff
                    code = code.removeRange(newBlockStart, newBlockEnd)
                } else {
                    code = code.removeRange(block.start, block.end)
                }
            }
            blockNum++
        }
    }

    private fun getBlock(): BlockInfo? {
        val regex = $$"""push \$state; eq $$blockNum; if \{\n""".toRegex()
        val match = regex.find(code) ?: return null
        var index = match.range.last + 1
        var braceCount = 1
        while (index < code.length) {
            if (code[index] == '{') {
                braceCount++
            } else if (code[index] == '}') {
                braceCount--
            }
            if (braceCount == 0) {
                break
            }
            index++
        }
        var blockCode = code.substring(match.range.last + 1, index).trim().replace(popStateRegex, "")
        if (blockCode.endsWith($$"@returns.pop $state;")) {
            blockCode += $$"push $state;"
        }
        return BlockInfo(match.range.first, index + 2, blockCode)
    }

    private data class BlockInfo(val start: Int, val end: Int, val code: String)

    companion object {
        private const val MAIN_LOOP_HEADER = $$"while $state {"
        private val entryPointRegex = $$"""def \$state (\d+);""".toRegex()
        private val popStateRegex = $$"""(?<!\.)pop \$state;$""".toRegex()

        fun inline(code: String): String {
            val inliner = BlockInliner(code)
            inliner.inlineBlocks()
            return "${inliner.preCode}$MAIN_LOOP_HEADER\n${inliner.code.trimEnd()}\n};"
        }
    }
}