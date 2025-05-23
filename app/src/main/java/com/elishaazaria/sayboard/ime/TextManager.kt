package com.elishaazaria.sayboard.ime

import android.util.Log
import com.elishaazaria.sayboard.recognition.ModelManager
import com.elishaazaria.sayboard.sayboardPreferenceModel

class TextManager(private val ime: IME, private val modelManager: ModelManager) {
    private val prefs by sayboardPreferenceModel()

    private var addSpace = false
    private var capitalize = true
    private var firstSinceResume = true

    private var composing = false

    fun onUpdateSelection(
        newSelStart: Int,
        newSelEnd: Int,
    ) {
        if (!composing) {
            if (newSelStart == newSelEnd) { // cursor moved
                checkAddSpaceAndCapitalize()
            }
        }
    }

    fun onText(text: String, mode: Mode) {
        if (text.isEmpty())  // no need to commit empty text
            return

        // --- MODIFICATION START ---
        // Trim leading/trailing spaces from the recognized text first.
        var processedText = text.trim()
        // --- MODIFICATION END ---


        Log.d(
            TAG,
            "onText. original text: $text, processed text: $processedText, mode: $mode, addSpace: $addSpace, firstSinceResume: $firstSinceResume"
        )

        if (processedText.isEmpty()) { // After trimming, if it's empty, no need to proceed
            return
        }

        // --- MODIFICATION START ---
        // Check if the processed text contains Chinese characters.
        val isChineseInput = containsChinese(processedText)
        if (isChineseInput) {
            Log.d(TAG, "Input text contains Chinese. Removing internal spaces.")
            // Remove all internal spaces from Chinese text
            processedText = processedText.replace(" ", "")
        }
        // --- MODIFICATION END ---


        if (firstSinceResume) {
            firstSinceResume = false
            checkAddSpaceAndCapitalize()
        }

        val ic = ime.currentInputConnection ?: return

        var spacedText = processedText // Start with the processed text

        // --- MODIFICATION START ---
        // Only apply capitalization and addSpace logic if the input is NOT Chinese.
        if (!isChineseInput) {
            if (prefs.logicAutoCapitalize.get() && capitalize) {
                if (spacedText.isNotEmpty()) { // Ensure spacedText is not empty before uppercasing
                     spacedText = spacedText[0].uppercase() + spacedText.substring(1)
                }
            }

            if (modelManager.currentRecognizerSourceAddSpaces && addSpace) {
                spacedText = " $spacedText"
            }
        }
        // --- MODIFICATION END ---

        when (mode) {
            Mode.FINAL, Mode.STANDARD -> {
                // add a space next time. Usually overridden by onUpdateSelection
                // --- MODIFICATION START ---
                // Only set addSpace based on the last character if the input is NOT Chinese.
                if (!isChineseInput) {
                    addSpace = addSpaceAfter(
                        spacedText[spacedText.length - 1] // last char
                    )
                } else {
                    // If the input was Chinese, the next input should not start with a space
                    addSpace = false
                }
                // --- MODIFICATION END ---

                capitalizeAfter(
                    spacedText
                )?.let {
                    capitalize = it
                }
                composing = false
                ic.commitText(spacedText, 1)
            }

            Mode.PARTIAL -> {
                composing = true
                ic.setComposingText(spacedText, 1)
            }

            Mode.INSERT -> {
                // Manual insert. Don't add a space.
                composing = false
                ic.commitText(processedText, 1) // Use processedText for INSERT mode as well
            }
        }
    }

    private fun checkAddSpaceAndCapitalize() {
        if (!modelManager.currentRecognizerSourceAddSpaces) {
            addSpace = false
            return
        }
        val cs = ime.currentInputConnection.getTextBeforeCursor(3, 0) // Get text before cursor
        if (cs != null) {
            // Check if the text before the cursor contains Chinese characters.
            if (containsChinese(cs)) {
                Log.d(TAG, "Text before cursor contains Chinese. Setting addSpace to false.")
                addSpace = false // If text before cursor is Chinese, do not add space
            } else {
                // Original logic: add space if the text before cursor is not empty and the last char is not a punctuation mark
                addSpace = cs.isNotEmpty() && addSpaceAfter(cs[cs.length - 1])
            }

            val value = capitalizeAfter(cs)
            value?.let {
                capitalize = it
            }
        }
    }

    private fun capitalizeAfter(string: CharSequence): Boolean? {
        for (char in string.reversed()) {
            if (char.isLetterOrDigit()) {
                return false
            }
            if (char in sentenceTerminator) {
                return true
            }
        }
        return null
    }

    private fun addSpaceAfter(char: Char): Boolean = when (char) {
        '"' -> false
        '*' -> false
        ' ' -> false
        '\n' -> false
        '\t' -> false
        else -> true
    }

    fun onResume() {
        firstSinceResume = true;
    }

    // Helper function to check if a string contains Chinese characters.
    private fun containsChinese(text: CharSequence): Boolean {
        for (i in 0 until text.length) {
            val char = text[i]
            // Common Unicode ranges for Chinese characters
            if (char.toInt() in 0x4E00..0x9FFF || // CJK Unified Ideographs
                char.toInt() in 0x3400..0x4DBF || // CJK Unified Ideographs Extension A
                char.toInt() in 0x20000..0x2A6DF || // CJK Unified Ideographs Extension B
                char.toInt() in 0x2A700..0x2B73F || // CJK Unified Ideographs Extension C
                char.toInt() in 0x2B740..0x2B81F || // CJK Unified Ideographs Extension D
                char.toInt() in 0x2B820..0x2CEAF || // CJK Unified Ideographs Extension E
                char.toInt() in 0xF900..0xFAFF || // CJK Compatibility Ideographs
                char.toInt() in 0xFE30..0xFE4F // CJK Compatibility Forms (some punctuation)
            ) {
                return true
            }
        }
        return false
    }

    enum class Mode {
        STANDARD, PARTIAL, FINAL, INSERT
    }

    companion object {
        private const val TAG = "TextManager"
        private val sentenceTerminator = charArrayOf('.', '\n', '!', '?')
    }
}
