package dev.hamper.uli

import dev.hamper.uli.data.SavedSession
import dev.hamper.uli.engine.Content
import dev.hamper.uli.engine.Exam
import dev.hamper.uli.engine.ProgressData
import dev.hamper.uli.engine.Question
import dev.hamper.uli.engine.Round
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FlowTest {
    private val questions: List<Question> by lazy {
        val file = listOf(File("../content/questions.json"), File("content/questions.json")).first { it.isFile }
        Content.parse(file.readText())
    }
    private val byId by lazy { questions.associateBy { it.id } }
    private val now = 1_800_000_000_000L

    private fun quizOn(vararg ids: Int, seq: Long = 0): QuizState {
        val q = byId.getValue(ids.first())
        return QuizState(RoundKind.Smart, Round(ids.toList()), order = q.options.indices.toList(), seq = seq)
    }

    // Ghost taps

    @Test
    fun aTapFromTheOutgoingQuestionIsIgnored() {
        val s = quizOn(12, 30)
        val q12 = byId.getValue(12)
        // A tap naming question 30 while 12 is current (or the other way round) does nothing.
        assertNull(Flow.pick(s, q12, questionId = 30, index = 0))
        assertNotNull(Flow.pick(s, q12, questionId = 12, index = 0))
        // Indices outside the question are never stored.
        assertNull(Flow.pick(s, q12, 12, q12.options.size))
        assertNull(Flow.pick(s, q12, 12, -1))
        // Answered: no more picks.
        assertNull(Flow.pick(s.copy(answered = true), q12, 12, 0))
    }

    @Test
    fun aLateExamTapNeverLandsOnTheNextQuestion() {
        val e = Exam(ids = listOf(41, 5, 12), startedAt = now).answer(setOf(1), now)
        val s = ExamState(e, order = byId.getValue(5).options.indices.toList())
        val q5 = byId.getValue(5)
        // The tap still names question 41, which slid out: ignored.
        assertNull(Flow.examPick(s, q5, questionId = 41, index = 3))
        assertNull(Flow.examPick(s, q5, questionId = 5, index = 99))
        assertEquals(setOf(2), Flow.examPick(s, q5, questionId = 5, index = 2)!!.selected)
    }

    // Answers applied once

    @Test
    fun anAnswerAlreadyInProgressIsNotAppliedAgain() {
        val q = byId.getValue(12)
        val s = quizOn(12, 30, seq = 4).copy(selected = q.correctIndices)
        val start = ProgressData(seq = 4)
        val (answered, p1) = Flow.submit(s, q, start, now)
        assertTrue(answered.answered)
        assertEquals(5, p1.seq)
        assertEquals(1, p1.card(12).box)
        assertEquals(1, p1.card(12).timesRight)
        // The app died after saving progress, before saving the round: the restored round answers again.
        val (_, p2) = Flow.submit(s, q, p1, now + 60_000)
        assertEquals(p1, p2)
    }

    @Test
    fun aFinishedExamMovesItsCardsOnce() {
        var e = Exam(ids = listOf(1, 2, 3), startedAt = now)
        for (id in e.ids) e = e.answer(byId.getValue(id).correctIndices, now)
        val once = Flow.applyExam(ProgressData(), e, byId, now)
        assertEquals(now, once.examApplied)
        assertEquals(1, once.card(1).box)
        assertEquals(once, Flow.applyExam(once, e, byId, now + 1))
    }

    // Restore

    @Test
    fun aSavedOrderThatDoesNotFitTheQuestionDropsTheRound() {
        val good = quizOn(12, 30)
        assertNotNull(Flow.restore(SavedSession(quiz = good), ProgressData(), byId).quiz)
        val n = byId.getValue(12).options.size
        for (bad in listOf(List(n) { 0 }, (0 until n + 1).toList(), (0 until n - 1).toList(), listOf(5, 6, 7, 8).take(n))) {
            assertNull("order $bad", Flow.restore(SavedSession(quiz = good.copy(order = bad)), ProgressData(), byId).quiz)
        }
        assertNull(Flow.restore(SavedSession(quiz = good.copy(selected = setOf(9))), ProgressData(), byId).quiz)
        assertNull(Flow.restore(SavedSession(quiz = QuizState(RoundKind.Smart, Round(listOf(999)), listOf(0, 1))), ProgressData(), byId).quiz)
    }

    @Test
    fun anExamWithAnUnknownIdOrBadAnswerIsDropped() {
        val ok = Exam(ids = listOf(1, 2, 3), startedAt = now).answer(setOf(0), now)
        val order = byId.getValue(2).options.indices.toList()
        assertNotNull(Flow.restore(SavedSession(exam = ExamState(ok, order)), ProgressData(), byId).exam)
        assertNull(Flow.restore(SavedSession(exam = ExamState(ok.copy(ids = listOf(1, 2, 999)), order)), ProgressData(), byId).exam)
        assertNull(Flow.restore(SavedSession(exam = ExamState(ok.copy(answers = mapOf(1 to setOf(7))), order)), ProgressData(), byId).exam)
        assertNull(Flow.restore(SavedSession(exam = ExamState(ok, order.reversed().drop(1))), ProgressData(), byId).exam)
    }

    @Test
    fun aFinishedExamSavedButNotCountedIsCountedOnRestore() {
        var e = Exam(ids = listOf(1, 2, 3), startedAt = now)
        for (id in e.ids) e = e.answer(byId.getValue(id).correctIndices, now + 1000)
        // The session got the finished exam, the app died before progress was written.
        val r = Flow.restore(SavedSession(lastExam = e), ProgressData(), byId)
        assertTrue(r.progressChanged)
        assertEquals(now, r.progress.examApplied)
        assertEquals(1, r.progress.card(3).box)
        assertEquals(e, r.lastExam)
        // Next start: already counted, nothing changes.
        val again = Flow.restore(SavedSession(lastExam = e), r.progress, byId)
        assertFalse(again.progressChanged)
        assertEquals(r.progress, again.progress)
        // A damaged progress file never gets an old exam replayed into the fresh start.
        val fresh = Flow.restore(SavedSession(lastExam = e), ProgressData(), byId, catchUp = false)
        assertFalse(fresh.progressChanged)
        assertTrue(fresh.progress.cards.isEmpty())
        assertEquals(now, fresh.progress.examApplied)
    }

    @Test
    fun anOpenExamAlreadyCountedIsNotOfferedAgain() {
        val e = Exam(ids = listOf(1, 2, 3), startedAt = now).answer(setOf(0), now)
        val r = Flow.restore(SavedSession(exam = ExamState(e, byId.getValue(2).options.indices.toList())), ProgressData(examApplied = now), byId)
        assertNull(r.exam)
    }
}
