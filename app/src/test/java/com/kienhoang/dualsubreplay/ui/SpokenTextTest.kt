package com.kienhoang.dualsubreplay.ui

import com.atilika.kuromoji.ipadic.Tokenizer
import com.kienhoang.dualsubreplay.data.AnalyzedToken
import com.kienhoang.dualsubreplay.data.PartOfSpeech
import com.kienhoang.dualsubreplay.data.japaneseLearnerWords
import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenTextTest {
    private val line = "昔は毎朝ヨーグルトとバナナを"
    private val words by lazy { japaneseLearnerWords(Tokenizer(), line) }

    private fun word(text: String) = words.first { it.text == text }

    @Test
    fun kanjiWordIsSpokenFromItsReading() {
        val morning = word("毎朝")
        assertEquals("まいあさ", spokenText(morning.text, morning.startIndex, listOf(morning)))
    }

    @Test
    fun phraseReplacesOnlyWordsWithReadings() {
        val parts = words.subList(words.indexOf(word("毎朝")), words.indexOf(word("バナナ")) + 1)
        val start = parts.first().startIndex
        val text = line.substring(start, parts.last().endIndex)
        assertEquals("まいあさヨーグルトとバナナ", spokenText(text, start, parts))
    }

    @Test
    fun wordWithoutReadingIsSpokenAsWritten() {
        val yogurt = word("ヨーグルト")
        assertEquals("ヨーグルト", spokenText(yogurt.text, yogurt.startIndex, listOf(yogurt)))
        val english = AnalyzedToken("morning", 4, 11, PartOfSpeech.NOUN)
        assertEquals("morning", spokenText("morning", 4, listOf(english)))
    }

    @Test
    fun offsetsThatDoNotMatchTheTextKeepTheTextAsWritten() {
        val shifted = AnalyzedToken("毎朝", 9, 11, PartOfSpeech.NOUN, reading = "まいあさ")
        assertEquals("毎朝", spokenText("毎朝", 0, listOf(shifted)))
    }

    @Test
    fun savedWordUsesOnlyAKanaReading() {
        assertEquals("まいあさ", spokenWord("毎朝", " まいあさ "))
        assertEquals("むかし まいあさ", spokenWord("昔は毎朝", "むかし まいあさ"))
        assertEquals("毎朝", spokenWord("毎朝", "maiasa"))
        assertEquals("你好", spokenWord("你好", "nǐ hǎo"))
        assertEquals("毎朝", spokenWord("毎朝", null))
        assertEquals("毎朝", spokenWord("毎朝", " "))
    }
}
