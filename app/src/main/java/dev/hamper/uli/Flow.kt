package dev.hamper.uli

import dev.hamper.uli.data.SavedSession
import dev.hamper.uli.engine.Exam
import dev.hamper.uli.engine.Kind
import dev.hamper.uli.engine.Options
import dev.hamper.uli.engine.ProgressData
import dev.hamper.uli.engine.Question
import dev.hamper.uli.engine.Srs
import dev.hamper.uli.engine.isCorrect
import dev.hamper.uli.engine.result

/**
 * The round and exam steps as plain functions of their state, so the guards (stale taps, double applies,
 * damaged saves) are tested without Android.
 */
object Flow {
    // Taps

    /**
     * A tap on option [index] of question [questionId] in a round. Null when the tap must be ignored: it came
     * from a card that is no longer current (a ghost tap during the slide-out), the index is not an option of
     * this question, or the question is already answered.
     */
    fun pick(s: QuizState, q: Question, questionId: Int, index: Int): QuizState? {
        if (s.answered || q.id != questionId || s.round.current != questionId || index !in q.options.indices) return null
        return s.copy(selected = toggle(q, s.selected, index))
    }

    /** The same for the mock exam. */
    fun examPick(s: ExamState, q: Question, questionId: Int, index: Int): ExamState? {
        if (s.exam.current != questionId || q.id != questionId || index !in q.options.indices) return null
        return s.copy(selected = toggle(q, s.selected, index))
    }

    private fun toggle(q: Question, selected: Set<Int>, index: Int): Set<Int> = when {
        q.kind != Kind.Multi -> setOf(index)
        index in selected -> selected - index
        selected.size < q.choose -> selected + index
        else -> selected
    }

    // Applying answers

    /**
     * Answers the current round card. Returns the new round state and the progress to save. When the progress
     * already counts this answer (the app died after saving progress but before saving the round), the card
     * is not moved a second time.
     */
    fun submit(s: QuizState, q: Question, progress: ProgressData, now: Long): Pair<QuizState, ProgressData> {
        val ok = q.isCorrect(s.selected)
        val answered = s.copy(round = s.round.answer(ok), answered = true, correct = ok)
        if (progress.seq > s.seq) return answered to progress
        val card = progress.card(q.id)
        // Only the first attempt in a round moves the box; corrections just mark the card as seen.
        val updated = if (!s.round.isRetry) Srs.answer(card, ok, now) else card.copy(lastSeenAt = now)
        return answered to progress.copy(cards = progress.cards + (q.id to updated), seq = progress.seq + 1)
    }

    /** A finished mock exam moves every card it asked, exactly once (keyed by its start time). */
    fun applyExam(progress: ProgressData, e: Exam, byId: Map<Int, Question>, now: Long): ProgressData {
        if (progress.examApplied == e.startedAt) return progress
        val res = e.result(byId)
        return Srs.applyExam(progress, e.ids, res.wrong, now).copy(examApplied = e.startedAt)
    }

    // Restoring after a killed process

    data class Restored(
        val quiz: QuizState?,
        val roundStart: Map<Int, Int>,
        val exam: ExamState?,
        val lastExam: Exam?,
        val progress: ProgressData,
        /** The progress had to catch up with a finished exam whose answers never reached it. */
        val progressChanged: Boolean,
    )

    /**
     * What of a saved session can be continued against the current question bank. A round or exam is dropped
     * when any id is unknown or a saved option order, pick or answer does not fit its question (the bank
     * changed, or the file is damaged). A finished exam that is saved but not yet counted in [progress] is
     * counted now ([catchUp]; off when the progress itself could not be read).
     */
    fun restore(saved: SavedSession, progress: ProgressData, byId: Map<Int, Question>, catchUp: Boolean = true): Restored {
        fun fits(q: Question?, order: List<Int>, selected: Set<Int>) =
            q != null && Options.isPermutation(order, q) && selected.all { it in q.options.indices }

        fun answersFit(e: Exam) = e.ids.isNotEmpty() && e.ids.all { it in byId } && e.ids.toSet().size == e.ids.size &&
            e.answers.all { (id, picks) -> id in e.ids && picks.all { it in byId.getValue(id).options.indices } }

        val quiz = saved.quiz?.takeIf { s ->
            val r = s.round
            r.queue.isNotEmpty() && r.ids.all { it in byId } && r.queue.all { it in byId } && !r.isFinished &&
                fits(r.current?.let(byId::get), s.order, s.selected)
        }
        val exam = saved.exam?.takeIf { s ->
            !s.exam.isFinished && answersFit(s.exam) && s.exam.startedAt != progress.examApplied &&
                fits(s.exam.current?.let(byId::get), s.order, s.selected)
        }
        val last = saved.lastExam?.takeIf { it.isFinished && answersFit(it) }
        var p = progress
        var changed = false
        if (last != null && catchUp && progress.examApplied != last.startedAt) {
            p = applyExam(progress, last, byId, last.finishedAt ?: last.startedAt)
            changed = true
        } else if (last != null && !catchUp) {
            // Progress starting over after a damaged file: this exam must not be replayed into it later.
            p = progress.copy(examApplied = last.startedAt)
        }
        return Restored(
            quiz = quiz,
            roundStart = if (quiz != null) saved.roundStartBoxes else emptyMap(),
            exam = exam,
            lastExam = last,
            progress = p,
            progressChanged = changed,
        )
    }
}
