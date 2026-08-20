@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.primaloptima.scribe.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * InputTransformation providing smart prose typing conveniences:
 * - Smart typographical curly quotes: "..." → “...” and '...' → ‘...’
 * - Automatic pairing of brackets and delimiters: (), [], {}, "", '', **, __
 */
@OptIn(ExperimentalFoundationApi::class)
object ScribeInputTransformation : InputTransformation {

    override val keyboardOptions: KeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        autoCorrectEnabled = true,
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Default
    )

    override fun TextFieldBuffer.transformInput() {
        val currentChanges = changes
        if (currentChanges.changeCount == 0) return

        // Process single character insertions for smart quotes and auto-pair
        for (i in 0 until currentChanges.changeCount) {
            val insertedLength = currentChanges.getRange(i).length
            val changeStart = currentChanges.getRange(i).min

            if (insertedLength == 1 && changeStart < length) {
                val insertedChar = charAt(changeStart)
                val prevChar = if (changeStart > 0) charAt(changeStart - 1) else null

                // 1. Smart Double Quotes: " -> “ or ”
                if (insertedChar == '"') {
                    val smartQuote = if (prevChar == null || prevChar.isWhitespace() || prevChar in "([{«“‘") {
                        '“'
                    } else {
                        '”'
                    }
                    replace(changeStart, changeStart + 1, smartQuote.toString())
                }
                // 2. Smart Single Quotes: ' -> ‘ or ’
                else if (insertedChar == '\'') {
                    val smartSingleQuote = if (prevChar == null || prevChar.isWhitespace() || prevChar in "([{«“‘") {
                        '‘'
                    } else {
                        '’'
                    }
                    replace(changeStart, changeStart + 1, smartSingleQuote.toString())
                }
                // 3. Auto-pair brackets
                else if (insertedChar == '(') {
                    insert(changeStart + 1, ")")
                } else if (insertedChar == '[') {
                    insert(changeStart + 1, "]")
                } else if (insertedChar == '{') {
                    insert(changeStart + 1, "}")
                } else if (insertedChar == '«') {
                    insert(changeStart + 1, "»")
                }
            }
        }
    }
}
