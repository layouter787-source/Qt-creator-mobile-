package com.qtcreatormobile.aiclient

/** Pedaco de uma resposta: texto comum ou bloco de codigo cercado por ```. */
sealed interface MessageBlock {
    data class Text(val text: String) : MessageBlock
    data class Code(val language: String, val code: String) : MessageBlock
}

object MarkdownBlocks {
    /** Divide [text] em texto e blocos de codigo. Um bloco ainda aberto (streaming) vira codigo ate o fim. */
    fun split(text: String): List<MessageBlock> {
        val blocks = mutableListOf<MessageBlock>()
        var rest = text
        while (true) {
            val open = rest.indexOf("```")
            if (open < 0) {
                if (rest.isNotBlank()) blocks += MessageBlock.Text(rest.trim('\n'))
                break
            }
            val before = rest.substring(0, open)
            if (before.isNotBlank()) blocks += MessageBlock.Text(before.trim('\n'))
            val afterOpen = rest.substring(open + 3)
            val newline = afterOpen.indexOf('\n')
            val language = if (newline >= 0) afterOpen.substring(0, newline).trim() else afterOpen.trim()
            val bodyStart = if (newline >= 0) newline + 1 else afterOpen.length
            val body = afterOpen.substring(bodyStart)
            val close = body.indexOf("```")
            if (close < 0) {
                blocks += MessageBlock.Code(language, body.trimEnd('\n'))
                break
            }
            blocks += MessageBlock.Code(language, body.substring(0, close).trimEnd('\n'))
            rest = body.substring(close + 3)
        }
        return blocks
    }
}
