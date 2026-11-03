package dev.hamper.uli.data

import dev.hamper.uli.ExamState
import dev.hamper.uli.QuizState
import dev.hamper.uli.RoundKind
import dev.hamper.uli.engine.Exam
import dev.hamper.uli.engine.Round
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedSessionTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun openRoundAndExamSurviveAJsonRoundTrip() {
        val round = Round(listOf(4, 8, 15, 16)).answer(true).next().answer(false).next()
        val quiz = QuizState(RoundKind.Mistakes, round, order = listOf(2, 0, 1), selected = setOf(0), answered = true, correct = false)
        val exam = Exam(ids = listOf(1, 2, 3), startedAt = 1_000L).answer(setOf(1, 2), now = 2_000L)
        val saved = SavedSession(quiz = quiz, roundStartBoxes = mapOf(4 to 0, 8 to 2), exam = ExamState(exam, listOf(1, 0), setOf(0)))

        val back = json.decodeFromString<SavedSession>(json.encodeToString(saved))

        assertEquals(saved, back)
        assertEquals(listOf(4, 8, 15, 16, 8), back.quiz!!.round.queue)
        assertEquals(2, back.quiz!!.round.index)
        assertEquals(1_000L, back.exam!!.exam.startedAt)
        assertEquals(setOf(1, 2), back.exam!!.exam.answers[1])
    }

    @Test
    fun emptySessionDecodesToNothingOpen() {
        val back = json.decodeFromString<SavedSession>("{}")
        assertNull(back.quiz)
        assertNull(back.exam)
    }
}
