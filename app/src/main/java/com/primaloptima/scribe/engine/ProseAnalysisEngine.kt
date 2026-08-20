package com.primaloptima.scribe.engine

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@Immutable
data class WordFrequency(
    val word: String,
    val count: Int,
    val percentage: Float
)

@Immutable
data class PhraseFrequency(
    val phrase: String,
    val count: Int
)

@Immutable
data class DuplicateWordAlert(
    val word: String,
    val sentenceIndex: Int,
    val preview: String
)

@Immutable
data class MonotonyAlert(
    val startSentenceIndex: Int,
    val sentenceCount: Int,
    val avgLength: Int,
    val preview: String
)

@Immutable
data class ProseAnalysisResult(
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val sentenceCount: Int = 0,
    val paragraphCount: Int = 0,
    val syllableCount: Int = 0,
    val readingTimeMinutes: Float = 0f,
    val speakingTimeMinutes: Float = 0f,
    val avgSentenceLength: Float = 0f,
    val avgWordLength: Float = 0f,
    val avgSyllablesPerWord: Float = 0f,

    // Readability
    val fleschReadingEase: Float = 0f,
    val fleschReadingEaseLabel: String = "N/A",
    val fleschKincaidGrade: Float = 0f,
    val fleschKincaidGradeLabel: String = "N/A",
    val gunningFogIndex: Float = 0f,
    val colemanLiauIndex: Float = 0f,

    // Repetition & Vocabulary
    val overusedWords: List<WordFrequency> = emptyList(),
    val repeatedPhrases: List<PhraseFrequency> = emptyList(),
    val duplicateAdjacentWords: List<DuplicateWordAlert> = emptyList(),
    val uniqueWordCount: Int = 0,
    val lexicalDiversity: Float = 0f, // Type-Token Ratio (0.0 - 1.0)

    // Pacing & Sentence Variety
    val shortSentencesCount: Int = 0,      // < 10 words
    val mediumSentencesCount: Int = 0,     // 10 - 20 words
    val longSentencesCount: Int = 0,       // 21 - 35 words
    val veryLongSentencesCount: Int = 0,   // > 35 words
    val sentenceLengths: List<Int> = emptyList(),
    val monotonyWarnings: List<MonotonyAlert> = emptyList(),

    // Dialogue & Prose Balance
    val dialogueWordCount: Int = 0,
    val narrativeWordCount: Int = 0,
    val dialoguePercentage: Float = 0f,
    val narrativePercentage: Float = 0f
)

object ProseAnalysisEngine {

    private val STOP_WORDS = hashSetOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
        "aren't", "as", "at", "be", "because", "been", "before", "being", "below", "between", "both",
        "but", "by", "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't",
        "doing", "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't",
        "has", "hasn't", "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here",
        "here's", "hers", "herself", "him", "himself", "his", "how", "how's", "i", "i'd", "i'll",
        "i'm", "i've", "if", "in", "into", "is", "isn't", "it", "it's", "its", "itself", "let's",
        "me", "more", "most", "mustn't", "my", "myself", "no", "nor", "not", "of", "off", "on",
        "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out", "over", "own",
        "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so", "some",
        "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then",
        "there", "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this",
        "those", "through", "to", "too", "under", "until", "up", "very", "was", "wasn't", "we",
        "we'd", "we'll", "we're", "we've", "were", "weren't", "what", "what's", "when", "when's",
        "where", "where's", "which", "while", "who", "who's", "whom", "why", "why's", "with",
        "won't", "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've", "your",
        "yours", "yourself", "yourselves", "said", "asked", "replied", "looked"
    )

    suspend fun analyze(text: String): ProseAnalysisResult = withContext(Dispatchers.Default) {
        if (text.isBlank()) {
            return@withContext ProseAnalysisResult()
        }

        val totalLength = text.length
        val paragraphs = text.split(Regex("\n+")).filter { it.isNotBlank() }
        val paragraphCount = paragraphs.size.coerceAtLeast(1)

        // 1. Sentence splitting and tokenization
        val sentences = splitSentences(text)
        val sentenceCount = sentences.size.coerceAtLeast(1)

        val words = mutableListOf<String>()
        val wordsLower = mutableListOf<String>()
        val sentenceLengths = mutableListOf<Int>()
        var totalSyllables = 0
        var complexWordCount = 0 // for Gunning Fog (3+ syllables)
        var totalLetters = 0

        // Dialogue extraction: words enclosed in quotes (", ', “, ”, «, »)
        var dialogueWords = 0
        var inQuotes = false

        val duplicateAdjacent = mutableListOf<DuplicateWordAlert>()

        for (sIdx in sentences.indices) {
            val sentence = sentences[sIdx]
            val sWords = extractWords(sentence)
            sentenceLengths.add(sWords.size)

            var prevWordLower = ""

            for (w in sWords) {
                words.add(w)
                val lower = w.lowercase(Locale.ROOT)
                wordsLower.add(lower)
                totalLetters += w.count { it.isLetter() }

                val syl = countSyllables(lower)
                totalSyllables += syl
                if (syl >= 3 && !isCompoundOrProperNoun(w)) {
                    complexWordCount++
                }

                // Check for immediate repetition ("the the", "had had")
                if (lower == prevWordLower && lower.length > 1 && !isAllowedDoubleWord(lower)) {
                    duplicateAdjacent.add(
                        DuplicateWordAlert(
                            word = w,
                            sentenceIndex = sIdx,
                            preview = sentence.take(60).trim()
                        )
                    )
                }
                prevWordLower = lower
            }
        }

        // Dialogue vs Narrative word counter
        for (i in 0 until text.length) {
            val c = text[i]
            if (c == '"' || c == '“' || c == '”' || c == '«' || c == '»') {
                inQuotes = !inQuotes
            } else if (c.isLetterOrDigit()) {
                if (inQuotes) {
                    val prevIsSpace = (i == 0 || !text[i - 1].isLetterOrDigit())
                    if (prevIsSpace) dialogueWords++
                }
            }
        }

        val totalWords = words.size.coerceAtLeast(1)
        val narrativeWords = (totalWords - dialogueWords).coerceAtLeast(0)
        val dialoguePercent = (dialogueWords.toFloat() / totalWords * 100f).coerceIn(0f, 100f)
        val narrativePercent = (100f - dialoguePercent).coerceIn(0f, 100f)

        // Reading & Speaking times
        val readingTime = totalWords / 225f
        val speakingTime = totalWords / 150f

        // Averages
        val avgSentenceLen = totalWords.toFloat() / sentenceCount
        val avgWordLen = if (totalWords > 0) totalLetters.toFloat() / totalWords else 0f
        val avgSyllablesPerWord = if (totalWords > 0) totalSyllables.toFloat() / totalWords else 0f

        // Readability formulas
        // Flesch Reading Ease: 206.835 - 1.015 * (words/sentence) - 84.6 * (syllables/word)
        val fleschEase = (206.835f - (1.015f * avgSentenceLen) - (84.6f * avgSyllablesPerWord)).coerceIn(0f, 100f)
        val fleschEaseLabel = when {
            fleschEase >= 90f -> "Very Easy"
            fleschEase >= 80f -> "Easy"
            fleschEase >= 70f -> "Fairly Easy"
            fleschEase >= 60f -> "Standard"
            fleschEase >= 50f -> "Fairly Difficult"
            fleschEase >= 30f -> "Difficult"
            else -> "Very Confusing"
        }

        // Flesch-Kincaid Grade Level: 0.39 * (words/sentence) + 11.8 * (syllables/word) - 15.59
        val fkGrade = (0.39f * avgSentenceLen + 11.8f * avgSyllablesPerWord - 15.59f).coerceAtLeast(0f)
        val fkGradeLabel = when {
            fkGrade <= 5f -> "Elementary"
            fkGrade <= 8f -> "Middle School (${fkGrade.roundToInt()}th Grade)"
            fkGrade <= 12f -> "High School (${fkGrade.roundToInt()}th Grade)"
            fkGrade <= 16f -> "College Level"
            else -> "Graduate / Academic"
        }

        // Gunning Fog Index: 0.4 * ((words/sentence) + 100 * (complex words / words))
        val gunningFog = (0.4f * (avgSentenceLen + (100f * complexWordCount.toFloat() / totalWords))).coerceAtLeast(0f)

        // Coleman-Liau: 0.0588 * L - 0.296 * S - 15.8 (L = avg letters per 100 words, S = avg sentences per 100 words)
        val l = (totalLetters.toFloat() / totalWords) * 100f
        val s = (sentenceCount.toFloat() / totalWords) * 100f
        val colemanLiau = (0.0588f * l - 0.296f * s - 15.8f).coerceAtLeast(0f)

        // Word frequencies & repetition (excluding stopwords)
        val freqMap = mutableMapOf<String, Int>()
        for (w in wordsLower) {
            if (w.length > 2 && !STOP_WORDS.contains(w)) {
                freqMap[w] = (freqMap[w] ?: 0) + 1
            }
        }
        val overusedWords = freqMap.entries
            .filter { it.value >= 3 && (it.value.toFloat() / totalWords) > 0.005f }
            .sortedByDescending { it.value }
            .take(10)
            .map {
                WordFrequency(
                    word = it.key,
                    count = it.value,
                    percentage = (it.value.toFloat() / totalWords) * 100f
                )
            }

        // Repeated 2-word and 3-word n-gram phrases
        val repeatedPhrases = findRepeatedPhrases(wordsLower, totalWords)

        // Lexical Diversity (Type-Token Ratio)
        val uniqueWords = wordsLower.toHashSet().size
        val typeTokenRatio = if (totalWords > 0) uniqueWords.toFloat() / totalWords else 0f

        // Pacing & Sentence distribution
        var shortCount = 0
        var mediumCount = 0
        var longCount = 0
        var veryLongCount = 0

        for (len in sentenceLengths) {
            when {
                len < 10 -> shortCount++
                len <= 20 -> mediumCount++
                len <= 35 -> longCount++
                else -> veryLongCount++
            }
        }

        // Monotony detection: 4 or more consecutive sentences with nearly identical length (within 2 words)
        val monotonyAlerts = mutableListOf<MonotonyAlert>()
        var streakStart = 0
        var streakLen = 1

        for (i in 1 until sentenceLengths.size) {
            val diff = Math.abs(sentenceLengths[i] - sentenceLengths[i - 1])
            if (diff <= 2 && sentenceLengths[i] > 3) {
                streakLen++
                if (streakLen == 4) {
                    val sub = sentences.subList(streakStart, (streakStart + 4).coerceAtMost(sentences.size))
                    val preview = sub.joinToString(" ") { it.trim() }.take(90)
                    monotonyAlerts.add(
                        MonotonyAlert(
                            startSentenceIndex = streakStart,
                            sentenceCount = 4,
                            avgLength = sentenceLengths[i],
                            preview = "$preview..."
                        )
                    )
                }
            } else {
                streakStart = i
                streakLen = 1
            }
        }

        ProseAnalysisResult(
            wordCount = totalWords,
            charCount = totalLength,
            sentenceCount = sentenceCount,
            paragraphCount = paragraphCount,
            syllableCount = totalSyllables,
            readingTimeMinutes = readingTime,
            speakingTimeMinutes = speakingTime,
            avgSentenceLength = avgSentenceLen,
            avgWordLength = avgWordLen,
            avgSyllablesPerWord = avgSyllablesPerWord,
            fleschReadingEase = fleschEase,
            fleschReadingEaseLabel = fleschEaseLabel,
            fleschKincaidGrade = fkGrade,
            fleschKincaidGradeLabel = fkGradeLabel,
            gunningFogIndex = gunningFog,
            colemanLiauIndex = colemanLiau,
            overusedWords = overusedWords,
            repeatedPhrases = repeatedPhrases,
            duplicateAdjacentWords = duplicateAdjacent.take(6),
            uniqueWordCount = uniqueWords,
            lexicalDiversity = typeTokenRatio,
            shortSentencesCount = shortCount,
            mediumSentencesCount = mediumCount,
            longSentencesCount = longCount,
            veryLongSentencesCount = veryLongCount,
            sentenceLengths = sentenceLengths.take(50),
            monotonyWarnings = monotonyAlerts.take(4),
            dialogueWordCount = dialogueWords,
            narrativeWordCount = narrativeWords,
            dialoguePercentage = dialoguePercent,
            narrativePercentage = narrativePercent
        )
    }

    private fun splitSentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            sb.append(c)
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                // Check if it's an abbreviation (e.g. "Mr.", "Dr.", "e.g.", "1.5")
                val isAbbrev = (c == '.' && i > 0 && i + 1 < text.length && text[i + 1].isLetterOrDigit())
                if (!isAbbrev) {
                    val s = sb.toString().trim()
                    if (s.isNotEmpty()) {
                        result.add(s)
                    }
                    sb.clear()
                }
            }
            i++
        }
        val remaining = sb.toString().trim()
        if (remaining.isNotEmpty()) {
            result.add(remaining)
        }
        return if (result.isEmpty()) listOf(text) else result
    }

    private fun extractWords(sentence: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        for (i in 0 until sentence.length) {
            val c = sentence[i]
            if (c.isLetterOrDigit() || c == '\'' || c == '’' || c == '-') {
                sb.append(c)
            } else {
                if (sb.isNotEmpty()) {
                    val w = sb.toString().trim('\'', '’', '-')
                    if (w.isNotEmpty()) result.add(w)
                    sb.clear()
                }
            }
        }
        if (sb.isNotEmpty()) {
            val w = sb.toString().trim('\'', '’', '-')
            if (w.isNotEmpty()) result.add(w)
        }
        return result
    }

    fun countSyllables(word: String): Int {
        val clean = word.lowercase(Locale.ROOT).filter { it.isLetter() }
        if (clean.length <= 3) return 1

        var count = 0
        var prevIsVowel = false
        val vowels = "aeiouy"

        for (i in clean.indices) {
            val isVowel = vowels.contains(clean[i])
            if (isVowel && !prevIsVowel) {
                count++
            }
            prevIsVowel = isVowel
        }

        // Silent 'e' at the end
        if (clean.endsWith("e") && !clean.endsWith("le") && count > 1) {
            count--
        }
        // '-ed' ending usually not a syllable unless preceded by 't' or 'd'
        if (clean.endsWith("ed") && !clean.endsWith("ted") && !clean.endsWith("ded") && count > 1) {
            count--
        }

        return count.coerceAtLeast(1)
    }

    private fun isCompoundOrProperNoun(word: String): Boolean {
        return word.isNotEmpty() && word[0].isUpperCase()
    }

    private fun isAllowedDoubleWord(word: String): Boolean {
        return word == "that" || word == "had" // e.g. "He knew that that was true", "He had had enough"
    }

    private fun findRepeatedPhrases(words: List<String>, totalWords: Int): List<PhraseFrequency> {
        if (words.size < 6) return emptyList()
        val phraseCounts = mutableMapOf<String, Int>()

        // 3-word n-grams
        for (i in 0 until words.size - 2) {
            val w1 = words[i]
            val w2 = words[i + 1]
            val w3 = words[i + 2]
            // Require at least one non-stop word
            if (!STOP_WORDS.contains(w1) || !STOP_WORDS.contains(w2) || !STOP_WORDS.contains(w3)) {
                val phrase = "$w1 $w2 $w3"
                phraseCounts[phrase] = (phraseCounts[phrase] ?: 0) + 1
            }
        }

        return phraseCounts.entries
            .filter { it.value >= 3 }
            .sortedByDescending { it.value }
            .take(6)
            .map { PhraseFrequency(it.key, it.value) }
    }
}
