package dev.hamper.uli.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SessionsTest {
    private fun q(id: Int, kind: Kind, correct: List<Boolean>) = Question(
        id = id, topic = "intro", kind = kind, choose = correct.count { it }, question = "q$id", questionSk = "",
        options = correct.mapIndexed { i, c -> Option("o$i", c) }, explanation = "", source = "ok",
    )

    @Test
    fun wrongCardComesBackThreePositionsLaterUntilRight() {
        var r = Round(ids = (1..5).toList())
        r = r.answer(false) // 1 wrong
        assertEquals(listOf(1, 2, 3, 1, 4, 5), r.queue)
        r = r.next()
        r = r.answer(true).next() // 2
        r = r.answer(true).next() // 3
        assertEquals(1, r.current)
        assertTrue(r.isRetry)
        r = r.answer(false) // 1 wrong again: comes back again
        assertEquals(listOf(1, 2, 3, 1, 4, 5, 1), r.queue)
        r = r.next().answer(true).next().answer(true).next()
        assertEquals(1, r.current)
        r = r.answer(true).next()
        assertTrue(r.isFinished)
        // First attempt counts once: 4 of 5.
        assertEquals(4, r.score)
        assertEquals(5, r.total)
        assertEquals(listOf(1), r.mistakes)
    }

    @Test
    fun reinsertionClampsToTheEndOfTheQueue() {
        var r = Round(ids = listOf(1, 2))
        r = r.answer(true).next()
        r = r.answer(false)
        assertEquals(listOf(1, 2, 2), r.queue)
        r = r.next()
        assertEquals(2, r.current)
        assertFalse(r.isFinished)
    }

    @Test
    fun displayNumberDoesNotCountRetries() {
        var r = Round(ids = listOf(1, 2, 3, 4))
        assertEquals(1, r.displayNumber)
        r = r.answer(false)
        assertEquals(1, r.displayNumber)
        r = r.next().answer(true).next().answer(true).next()
        assertEquals(1, r.current)
        assertEquals(3, r.displayNumber)
        assertTrue(r.isRetry)
    }

    @Test
    fun multiNeedsTheExactSet() {
        val m = q(1, Kind.Multi, listOf(true, true, false, true))
        assertTrue(m.isCorrect(setOf(0, 1, 3)))
        assertFalse(m.isCorrect(setOf(0, 1)))
        assertFalse(m.isCorrect(setOf(0, 1, 2, 3)))
        assertFalse(m.isCorrect(setOf(0, 1, 2)))
        val s = q(2, Kind.Single, listOf(false, true))
        assertTrue(s.isCorrect(setOf(1)))
        assertFalse(s.isCorrect(emptySet()))
    }

    @Test
    fun examScoresPointsAndPassLine() {
        val qs = (1..70).associateWith { q(it, Kind.Single, listOf(true, false)) }
        var e = Exam.start(qs.keys.toList(), now = 0, random = Random(1))
        assertEquals(50, e.ids.size)
        assertEquals(50, e.ids.toSet().size)
        e.ids.forEachIndexed { i, _ -> e = e.answer(if (i < 40) setOf(0) else setOf(1), now = 99) }
        assertTrue(e.isFinished)
        assertEquals(99L, e.finishedAt)
        val res = e.result(qs)
        assertEquals(40, res.correct)
        assertEquals(64.0, res.points, 1e-9)
        assertTrue(res.passed)
        assertFalse(ExamResult(39, 50, emptyList()).passed)
        assertEquals(67.2, ExamResult(42, 50, emptyList()).points, 1e-9)
    }
}
