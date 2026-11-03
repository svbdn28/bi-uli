package dev.hamper.uli.engine

import kotlinx.serialization.Serializable

@Serializable
data class CardState(
    val box: Int = 0,
    val lastSeenAt: Long = 0,
    val dueAt: Long = 0,
    val timesWrong: Int = 0,
    val timesRight: Int = 0,
    /** The latest answer was wrong. Null in progress saved before this field existed: then inferred. */
    val lastWrong: Boolean? = null,
) {
    val isNew: Boolean get() = box == 0
    val isMastered: Boolean get() = box >= Srs.MASTERED_BOX

    /**
     * The latest answer was a miss. Old saves have no [lastWrong]; there a box-1 card with a miss on
     * record was almost always sent there by that miss (a right answer when due moves it to box 2).
     */
    val justMissed: Boolean get() = lastWrong ?: (box == 1 && timesWrong > 0)
}

@Serializable
data class ProgressData(
    val version: Int = 1,
    val cards: Map<Int, CardState> = emptyMap(),
    val showSk: Boolean = false,
) {
    fun card(id: Int): CardState = cards[id] ?: CardState()
}

/** Leitner boxes 0 (new) to 5 with short intervals: the exam is days away, not months. */
object Srs {
    const val MAX_BOX = 5
    const val MASTERED_BOX = 3
    const val ROUND_SIZE = 10

    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    fun interval(box: Int): Long = when (box) {
        0 -> 0
        1 -> 10 * MIN
        2 -> 4 * HOUR
        3 -> DAY
        4 -> 3 * DAY
        else -> 7 * DAY
    }

    /** An unseen card: a blind guess among four options. */
    const val P_GUESS = 0.25

    /** Just answered wrong: a wrong belief is worse than a blind guess until it is fixed. */
    const val P_MISSED = 0.2

    /** Missed, then answered right before it came due: fixed for now, but not yet spaced. */
    const val P_FIXED = 0.45

    /**
     * Chance of answering this card right on the exam, from its latest evidence. Averaged into the
     * exam estimate, so a wrong answer can never raise it: a miss always lands at [P_MISSED], the
     * lowest value, and a right answer always lands at or above where the card was.
     */
    fun pCorrect(state: CardState): Double = when {
        state.isNew -> P_GUESS
        state.justMissed -> P_MISSED
        state.box == 1 && state.timesWrong > 0 -> P_FIXED
        else -> when (state.box) {
            1 -> 0.6
            2 -> 0.75
            3 -> 0.85
            4 -> 0.93
            else -> 0.97
        }
    }

    /**
     * One answer in any mode (round, Chyby, mock exam). A wrong answer always drops the card to box 1.
     * A right answer climbs a box only when the card is new or due; answering a card again before its
     * interval is up proves nothing about memory, so it only marks the card as seen.
     */
    fun answer(state: CardState, correct: Boolean, now: Long): CardState = when {
        !correct -> state.copy(
            box = 1,
            lastSeenAt = now,
            dueAt = now + interval(1),
            timesWrong = state.timesWrong + 1,
            lastWrong = true,
        )
        state.isNew || state.dueAt <= now -> {
            val box = (state.box + 1).coerceAtMost(MAX_BOX)
            state.copy(box = box, lastSeenAt = now, dueAt = now + interval(box), timesRight = state.timesRight + 1, lastWrong = false)
        }
        // Early: no box climb, but a missed card answered right again is no longer "just missed".
        else -> state.copy(lastSeenAt = now, lastWrong = false)
    }

    /** Every answer of a finished mock exam moves its card exactly like an answer in a round. */
    fun applyExam(progress: ProgressData, ids: List<Int>, wrong: Collection<Int>, now: Long): ProgressData {
        val cards = progress.cards.toMutableMap()
        for (id in ids) cards[id] = answer(progress.card(id), id !in wrong, now)
        return progress.copy(cards = cards)
    }

    /** Expected share of correct answers on the exam (questions are drawn uniformly from the pool). */
    fun readiness(ids: Collection<Int>, progress: ProgressData): Double =
        if (ids.isEmpty()) 0.0 else ids.sumOf { pCorrect(progress.card(it)) } / ids.size

    fun masteredCount(ids: Collection<Int>, progress: ProgressData): Int =
        ids.count { progress.card(it).isMastered }

    fun isDue(state: CardState, now: Long): Boolean = state.box > 0 && state.dueAt <= now

    /**
     * Smart round: due cards first (lowest box first), then new ones in content order,
     * then the least recently seen low-box cards so a round is never empty.
     */
    fun buildRound(contentOrder: List<Int>, progress: ProgressData, now: Long, size: Int = ROUND_SIZE): List<Int> {
        val picked = LinkedHashSet<Int>()
        contentOrder
            .filter { isDue(progress.card(it), now) }
            .sortedWith(compareBy({ progress.card(it).box }, { progress.card(it).dueAt }))
            .forEach { if (picked.size < size) picked += it }
        contentOrder
            .filter { progress.card(it).isNew }
            .forEach { if (picked.size < size) picked += it }
        contentOrder
            .filter { it !in picked }
            .sortedWith(compareBy({ progress.card(it).box }, { progress.card(it).lastSeenAt }))
            .forEach { if (picked.size < size) picked += it }
        return picked.toList()
    }

    /** A "Chyby" card: answered wrong at least once and not yet back at [MASTERED_BOX]. */
    fun isMistake(state: CardState): Boolean = state.timesWrong > 0 && state.box < MASTERED_BOX

    /** Every Chyby card, due ones first (lowest box first), then the rest by how soon they come due. */
    fun allMistakes(contentOrder: List<Int>, progress: ProgressData, now: Long): List<Int> =
        contentOrder
            .filter { isMistake(progress.card(it)) }
            .sortedWith(
                compareBy<Int>({ !isDue(progress.card(it), now) }, { progress.card(it).box }, { progress.card(it).dueAt }, { -progress.card(it).timesWrong }),
            )

    /** What a Chyby round drills: the first [size] of [allMistakes]. */
    fun mistakes(contentOrder: List<Int>, progress: ProgressData, now: Long, size: Int = ROUND_SIZE): List<Int> =
        allMistakes(contentOrder, progress, now).take(size)

    fun dueCount(ids: Collection<Int>, progress: ProgressData, now: Long): Int = ids.count { isDue(progress.card(it), now) }
}
