package dev.hamper.uli

import dev.hamper.uli.engine.CardState
import dev.hamper.uli.engine.Exam
import dev.hamper.uli.engine.ProgressData
import dev.hamper.uli.engine.Round
import dev.hamper.uli.engine.result
import kotlin.random.Random

/**
 * Deterministic states for screenshots, opened with
 * `adb shell am start -n dev.hamper.uli.debug/dev.hamper.uli.MainActivity --es shot <name>`.
 * Only reachable from debug builds (MainActivity checks BuildConfig.DEBUG).
 */
object Shots {
    val names = listOf("home", "q-single", "q-wrong", "q-right", "q-multi", "round-end", "exam-result", "browse")

    private const val NOW = 1_793_707_200_000L // 2026-11-03 12:00 UTC
    private const val MIN = 60_000L

    /** ~25 mastered, ~15 learning, the rest new: the first 40 questions in course order have been seen. */
    fun seededProgress(ids: List<Int>): ProgressData {
        val seen = ids.take(40).shuffled(Random(5))
        val cards = mutableMapOf<Int, CardState>()
        seen.forEachIndexed { i, id ->
            cards[id] = if (i < 25) {
                val box = 3 + i % 3
                CardState(box = box, lastSeenAt = NOW - (i + 2) * 60 * MIN, dueAt = NOW + (i - 3) * 60 * MIN, timesRight = box, timesWrong = i % 4 / 3)
            } else {
                val box = 1 + i % 2
                CardState(box = box, lastSeenAt = NOW - (i % 5 + 1) * 30 * MIN, dueAt = NOW - i * MIN, timesRight = box - 1, timesWrong = 1 + i % 3)
            }
        }
        return ProgressData(cards = cards, showSk = false)
    }

    private val roundIds = listOf(12, 30, 44, 67, 41, 2, 55, 61, 70, 35)

    fun apply(vm: AppViewModel, name: String) {
        val data = seededProgress(vm.ids)
        vm.enableSeededShuffle()
        when (name) {
            "q-single", "q-wrong" -> {
                val r = Round(roundIds).answer(true).next().answer(false).next().answer(true).next()
                val q = vm.byId.getValue(67)
                var state = QuizState(RoundKind.Smart, r, vm.orderFor(q))
                if (name == "q-wrong") {
                    val pick = q.options.indexOfFirst { it.text.contains("2755") }
                    state = state.copy(round = r.answer(false), selected = setOf(pick), answered = true, correct = false)
                }
                vm.installShot(data, NOW, Screen.Quiz, quiz = state)
            }
            "q-right" -> {
                val ids = listOf(3, 18, 21, 33, 52, 27, 60, 64, 14, 49)
                var r = Round(ids)
                // One early miss whose correction lands after Q27, so Q27 is a first attempt.
                repeat(5) { r = r.answer(it != 4).next() }
                val q = vm.byId.getValue(27)
                val state = QuizState(RoundKind.Smart, r.answer(true), vm.orderFor(q), selected = q.correctIndices, answered = true, correct = true)
                vm.installShot(data, NOW, Screen.Quiz, quiz = state)
            }
            "q-multi" -> {
                val ids = listOf(8, 5, 22, 29, 36, 47, 53, 58, 62, 69)
                val r = Round(ids).answer(true).next()
                val q = vm.byId.getValue(5)
                val state = QuizState(RoundKind.Smart, r, vm.orderFor(q), selected = q.correctIndices.take(2).toSet())
                vm.installShot(data, NOW, Screen.Quiz, quiz = state)
            }
            "round-end" -> vm.installShot(
                data, NOW, Screen.RoundEnd,
                roundSummary = RoundSummary(RoundKind.Smart, score = 8, total = 10, mistakes = listOf(67, 33), newlyMastered = 3),
            )
            "exam-result" -> {
                val rnd = Random(3)
                var e = Exam.start(vm.ids, NOW - (23 * 60 + 41) * 1000L, rnd)
                val wrong = e.ids.shuffled(Random(9)).take(8).toSet()
                for (id in e.ids) {
                    val q = vm.byId.getValue(id)
                    val pick = if (id in wrong) setOf(q.options.indexOfFirst { !it.correct }).let { w ->
                        if (q.choose > 1) w + q.correctIndices.drop(1) else w
                    } else q.correctIndices
                    e = e.answer(pick, NOW)
                }
                vm.installShot(data, NOW, Screen.ExamEnd, examSummary = ExamSummary(e.result(vm.byId), e))
            }
            "browse" -> vm.installShot(data, NOW, Screen.Browse(expanded = 2))
            else -> vm.installShot(data, NOW, Screen.Home)
        }
    }
}
