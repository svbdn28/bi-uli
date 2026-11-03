package dev.hamper.uli

import dev.hamper.uli.data.SavedSession
import dev.hamper.uli.engine.Content
import dev.hamper.uli.engine.Exam
import dev.hamper.uli.engine.LastAnswer
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
        assertNull(Flow.pick(s, q12, questionId = 30, index = 0, now))
        assertNotNull(Flow.pick(s, q12, questionId = 12, index = 0, now))
        // Indices outside the question are never stored.
        assertNull(Flow.pick(s, q12, 12, q12.options.size, now))
        assertNull(Flow.pick(s, q12, 12, -1, now))
        // Answered: no more picks.
        assertNull(Flow.pick(s.copy(answered = true), q12, 12, 0, now))
    }

    @Test
    fun aLateExamTapNeverLandsOnTheNextQuestion() {
        val e = Exam(ids = listOf(41, 5, 12), startedAt = now).answer(setOf(1), now)
        val s = ExamState(e, order = byId.getValue(5).options.indices.toList())
        val q5 = byId.getValue(5)
        // The tap still names question 41, which slid out: ignored.
        assertNull(Flow.examPick(s, q5, questionId = 41, index = 3, now))
        assertNull(Flow.examPick(s, q5, questionId = 5, index = 99, now))
        assertEquals(setOf(2), Flow.examPick(s, q5, questionId = 5, index = 2, now)!!.selected)
    }

    @Test
    fun aDoubleTapOnNextDoesNotAnswerTheIncomingQuestion() {
        val q12 = byId.getValue(12)
        val shown = 5_000_000L
        val s = quizOn(12, 30).copy(shownAt = shown)
        // The second tap of a double tap on "Ďalej" lands on the new card's option ~100 ms after it came up.
        assertNull(Flow.pick(s, q12, 12, 0, shown + 100))
        assertNull(Flow.pick(s, q12, 12, 0, shown + Flow.SETTLE_MS - 1))
        assertNotNull(Flow.pick(s, q12, 12, 0, shown + Flow.SETTLE_MS))
        // Unknown show time (0) or a clock that went backwards never locks the question.
        assertNotNull(Flow.pick(s.copy(shownAt = 0), q12, 12, 0, 50))
        assertNotNull(Flow.pick(s, q12, 12, 0, shown - 1))

        val e = Exam(ids = listOf(41, 5), startedAt = now).answer(setOf(1), now)
        val q5 = byId.getValue(5)
        val es = ExamState(e, order = q5.options.indices.toList(), shownAt = shown)
        assertNull(Flow.examPick(es, q5, 5, 0, shown + 100))
        assertNotNull(Flow.examPick(es, q5, 5, 0, shown + Flow.SETTLE_MS))
    }

    @Test
    fun aRestoredQuestionIsNotTappableRightAway() {
        val r = Flow.restore(SavedSession(quiz = quizOn(12, 30)), ProgressData(), byId, shownAt = 9_000L)
        assertEquals(9_000L, r.quiz!!.shownAt)
        assertNull(Flow.pick(r.quiz!!, byId.getValue(12), 12, 0, 9_100L))
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
    fun aRoundSavedJustBeforeAnAnswerComesBackAnsweredTheWayItWasCounted() {
        val q = byId.getValue(12)
        val wrong = q.options.indices.first { it !in q.correctIndices }
        val before = quizOn(12, 30, seq = 4).copy(selected = setOf(wrong))
        val (_, counted) = Flow.submit(before, q, ProgressData(seq = 4), now)
        assertEquals(LastAnswer(12, setOf(wrong), false), counted.last)
        // The app died after progress.json, before session.json: the session still has question 12 open.
        val saved = before.copy(selected = emptySet())
        val r = Flow.restore(SavedSession(quiz = saved), counted, byId).quiz!!
        assertTrue(r.answered)
        assertFalse(r.correct)
        assertEquals(setOf(wrong), r.selected)
        // The miss is in the round too: question 12 comes back later, as after a live miss.
        assertEquals(before.round.answer(false), r.round)
        // A different answer can no longer be given.
        assertNull(Flow.pick(r, q, 12, q.correctIndices.first(), now))
        assertEquals(counted, Flow.restore(SavedSession(quiz = saved), counted, byId).progress)
    }

    @Test
    fun aCountedAnswerForAnotherQuestionDoesNotSwallowTheNextOne() {
        val q = byId.getValue(12)
        // The count is ahead but its answer was for question 30 (or an old save recorded none).
        for (last in listOf(LastAnswer(30, setOf(0), true), null)) {
            val progress = ProgressData(seq = 6, last = last)
            val r = Flow.restore(SavedSession(quiz = quizOn(12, 30, seq = 4)), progress, byId).quiz!!
            assertFalse(r.answered)
            assertEquals(6, r.seq)
            val (_, p) = Flow.submit(r.copy(selected = q.correctIndices), q, progress, now)
            assertEquals(7, p.seq)
            assertEquals(1, p.card(12).box)
        }
    }

    @Test
    fun retryingTheDamagedFileMovesTheOpenRoundOntoIt() {
        val q = byId.getValue(12)
        // The round started on the empty in-memory progress (seq 0) after progress.json failed to load.
        val open = quizOn(12, 30, seq = 0).copy(shownAt = 77L)
        val loaded = ProgressData(seq = 40, cards = mapOf(12 to ProgressData().card(12).copy(box = 2), 30 to ProgressData().card(30).copy(box = 4)))
        val b = Flow.rebase(SavedSession(quiz = open, roundStartBoxes = mapOf(12 to 0, 30 to 0)), loaded, byId)
        val quiz = b.quiz!!
        assertEquals(40, quiz.seq)
        assertEquals(77L, quiz.shownAt)
        assertEquals(mapOf(12 to 2, 30 to 4), b.roundStart)
        // The next answer counts instead of looking already applied.
        val (_, p) = Flow.submit(quiz.copy(selected = q.correctIndices), q, b.progress, now)
        assertEquals(41, p.seq)
        assertEquals(3, p.card(12).box)
    }

    @Test
    fun retryingTheDamagedFileCountsAnExamFinishedMeanwhile() {
        var e = Exam(ids = listOf(1, 2, 3), startedAt = now)
        for (id in e.ids) e = e.answer(byId.getValue(id).correctIndices, now + 1000)
        val loaded = ProgressData(seq = 9)
        val b = Flow.rebase(SavedSession(lastExam = e), loaded, byId)
        assertTrue(b.progressChanged)
        assertEquals(now, b.progress.examApplied)
        assertEquals(9, b.progress.seq)
        // Already counted in the loaded file: left alone.
        assertFalse(Flow.rebase(SavedSession(lastExam = e), b.progress, byId).progressChanged)
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
