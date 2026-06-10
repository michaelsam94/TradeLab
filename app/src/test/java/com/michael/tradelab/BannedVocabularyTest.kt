package com.michael.tradelab

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Compliance gate: financial-app banned vocabulary must never appear in
 * user-facing string resources (Play policy + simulator positioning).
 */
class BannedVocabularyTest {

    private val banned = listOf(
        "prediction", "guaranteed", "profit", "no-loss", "make money",
        "buy now", "sure thing", "sure win", "win big",
    )

    @Test
    fun `strings xml contains no banned vocabulary`() {
        val candidates = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
        )
        val stringsFile = candidates.firstOrNull { it.exists() }
            ?: error("strings.xml not found from ${File(".").absolutePath}")
        // The spec-mandated compliance phrase "not a prediction" is the one allowed
        // (negated) use of the word; strip it before scanning.
        val content = stringsFile.readText().lowercase().replace("not a prediction", "")
        for (word in banned) {
            assertTrue("Banned vocabulary \"$word\" found in strings.xml", !content.contains(word))
        }
    }
}
