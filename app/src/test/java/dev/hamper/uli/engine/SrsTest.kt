package dev.hamper.uli.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsTest {
    private val min = 60_000L
    private val hour = 60 * min
    private val day = 24 * hour
    private val now = 1_000_000_000L

    @Test
    fun rightAnswerClimbsOneBoxAndCapsAtFive() {
        var s = CardState()
        // Each answer waits until the card is due again.
        val boxes = (1..7).map { s = Srs.answer(s, correct = true, now = maxOf(now, s.dueAt)); s.box }
        assertEquals(listOf(1, 2, 3, 4, 5, 5, 5), boxes)
        assertEquals(7, s.timesRight)
        assertEquals(0, s.timesWrong)
    }

    @Test
    fun wrongAnswerDropsToBoxOneAndCounts() {
        val s = Srs.answer(CardState(box = 4, timesWrong = 2), correct = false, now = now)
        assertEquals(1, s.box)
        assertEquals(3, s.timesWrong)
        assertEquals(now + 10 * min, s.dueAt)
        assertEquals(now, s.lastSeenAt)
    }

    @Test
    fun intervalsMatchTheExamCalendar() {
        assertEquals(10 * min, Srs.interval(1))
        assertEquals(4 * hour, Srs.interval(2))
        assertEquals(day, Srs.interval(3))
        assertEquals(3 * day, Srs.interval(4))
        assertEquals(7 * day, Srs.interval(5))
        val s = Srs.answer(CardState(box = 2), correct = true, now = now)
        assertEquals(now + day, s.dueAt)
    }

    @Test
    fun readinessAveragesProbabilityByBox() {
        val ids = listOf(1, 2, 3, 4, 5, 6)
        val p = ProgressData(cards = (0..5).associate { (it + 1) to CardState(box = it, timesRight = it) })
        // Box 3 with no record of how long its run lasted is assumed to be the fastest climb: not settled yet.
        val expected = (0.25 + 0.6 + 0.75 + 0.75 + 0.93 + 0.97) / 6
        assertEquals(expected, Srs.readiness(ids, p), 1e-9)
        assertEquals(0.25, Srs.readiness(ids, ProgressData()), 1e-9)
        assertEquals(3, Srs.masteredCount(ids, p))
    }

    // The exam estimate is honest: misses never raise it, hits do.

    /** One state of every kind the engine can produce, old saves (no lastWrong) included. */
    private val zoo: List<CardState> = buildList {
        add(CardState())
        for (box in 1..5) {
            add(CardState(box = box, timesRight = box, dueAt = now - 1, lastWrong = false))
            add(CardState(box = box, timesRight = box, dueAt = now + day, lastWrong = false))
            add(CardState(box = box, timesRight = box, timesWrong = 2, dueAt = now - 1))
        }
        add(CardState(box = 1, timesWrong = 1, dueAt = now + 5 * min, lastWrong = true))
        add(CardState(box = 1, timesWrong = 1, dueAt = now + 5 * min, lastWrong = false))
        add(CardState(box = 1, timesWrong = 3, timesRight = 1, dueAt = now + 5 * min)) // old save
    }

    @Test
    fun aWrongAnswerNeverRaisesACardAndARightOneNeverLowersIt() {
        for (s in zoo) {
            val before = Srs.pCorrect(s)
            assertTrue("wrong raised $s", Srs.pCorrect(Srs.answer(s, false, now)) <= before)
            assertTrue("right lowered $s", Srs.pCorrect(Srs.answer(s, true, now)) >= before)
        }
    }

    @Test
    fun aJustMissedCardCountsBelowAnUnseenGuess() {
        val missed = Srs.answer(CardState(box = 4, timesRight = 4), false, now)
        assertTrue(Srs.pCorrect(missed) < Srs.pCorrect(CardState()))
        val rightOnce = Srs.answer(CardState(), true, now)
        assertTrue(Srs.pCorrect(rightOnce) > Srs.pCorrect(CardState()))
        // Fixed again right after the miss: better than the miss, not yet as good as a clean hit.
        val fixed = Srs.answer(missed, true, now + min)
        assertTrue(Srs.pCorrect(fixed) > Srs.pCorrect(missed))
        assertTrue(Srs.pCorrect(fixed) < Srs.pCorrect(rightOnce))
    }

    private val pool = (1..70).toList()

    /** Fresh, mid-way (some mastered, some missed) and nearly done, all at [now]. */
    private val starts: List<ProgressData> = listOf(
        ProgressData(),
        ProgressData(cards = pool.take(40).associateWith { id ->
            when (id % 4) {
                0 -> CardState(box = 4, timesRight = 4, dueAt = now - 1)
                1 -> CardState(box = 1, timesWrong = 1, dueAt = now - 1, lastWrong = true)
                2 -> CardState(box = 2, timesRight = 2, dueAt = now + hour)
                else -> CardState(box = 1, timesRight = 1, dueAt = now - 1)
            }
        }),
        ProgressData(cards = pool.associateWith { CardState(box = 5, timesRight = 5, dueAt = now + day) }),
    )

    private fun play(start: ProgressData, ids: List<Int>, correct: (Int) -> Boolean): ProgressData =
        start.copy(cards = start.cards + ids.associateWith { Srs.answer(start.card(it), correct(it), now) })

    @Test
    fun anAllWrongRoundNeverIncreasesTheEstimate() {
        for (start in starts) {
            val round = Srs.buildRound(pool, start, now)
            val before = Srs.readiness(pool, start)
            val after = Srs.readiness(pool, play(start, round) { false })
            assertTrue("all wrong raised $before to $after", after < before)
        }
    }

    @Test
    fun anAllRightRoundIncreasesTheEstimate() {
        // The last start (everything mastered, nothing due) has nothing to gain from an early repeat.
        for (start in starts.dropLast(1)) {
            val round = Srs.buildRound(pool, start, now)
            val before = Srs.readiness(pool, start)
            val after = Srs.readiness(pool, play(start, round) { true })
            assertTrue("all right left $before at $after", after > before)
        }
        val done = starts.last()
        assertEquals(Srs.readiness(pool, done), Srs.readiness(pool, play(done, Srs.buildRound(pool, done, now)) { true }), 1e-9)
    }

    @Test
    fun aMockExamOf41Of50LandsWhereItsAnswersPutIt() {
        val asked = pool.shuffled(kotlin.random.Random(1)).take(50)
        val missed = asked.take(9).toSet()
        fun mock(wrong: Set<Int>) = play(ProgressData(), asked) { it !in wrong }
        val after = mock(missed)
        // The 41 hits and the 9 misses each count at their own weight; the 20 unseen stay a guess.
        val expected = (41 * 0.6 + 9 * Srs.P_MISSED + 20 * Srs.P_GUESS) / 70
        assertEquals(expected, Srs.readiness(pool, after), 1e-9)
        // On the 50 questions asked, the estimate follows the score: hits up, misses down.
        val onAsked = Srs.readiness(asked, after)
        assertTrue(onAsked > Srs.readiness(asked, mock(asked.take(20).toSet())))
        assertTrue(onAsked < Srs.readiness(asked, mock(emptySet())))
        // The misses alone are below where they started (a guess), the hits above it.
        assertTrue(Srs.readiness(missed, after) < Srs.P_GUESS)
        assertTrue(Srs.readiness(asked - missed, after) > Srs.P_GUESS)
    }

    @Test
    fun oldSavesWithoutLastWrongInferIt() {
        assertTrue(CardState(box = 1, timesWrong = 2).justMissed)
        assertFalse(CardState(box = 1, timesRight = 1).justMissed)
        assertFalse(CardState(box = 3, timesWrong = 2).justMissed)
        assertFalse(CardState(box = 1, timesWrong = 2, lastWrong = false).justMissed)
    }

    @Test
    fun roundTakesDueFirstLowestBoxFirstThenNewInContentOrder() {
        val order = (1..20).toList()
        val cards = mapOf(
            5 to CardState(box = 3, dueAt = now - 1),
            9 to CardState(box = 1, dueAt = now - 1),
            12 to CardState(box = 2, dueAt = now - 5),
            3 to CardState(box = 2, dueAt = now + day), // not due yet
            1 to CardState(box = 4, dueAt = now + day),
        )
        val round = Srs.buildRound(order, ProgressData(cards = cards), now)
        assertEquals(10, round.size)
        assertEquals(listOf(9, 12, 5), round.take(3))
        assertEquals(listOf(2, 4, 6, 7, 8, 10, 11), round.drop(3))
    }

    @Test
    fun roundIsNeverEmptyWhenEverythingIsLearnedAndNothingDue() {
        val order = (1..12).toList()
        val cards = order.associateWith { CardState(box = if (it % 2 == 0) 4 else 2, dueAt = now + day, lastSeenAt = it.toLong()) }
        val round = Srs.buildRound(order, ProgressData(cards = cards), now)
        assertEquals(10, round.size)
        // Low boxes first, least recently seen first.
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 2, 4, 6, 8), round)
        assertEquals(round.size, round.toSet().size)
    }

    @Test
    fun mistakesAreWrongCardsBelowMasteryDueFirst() {
        val order = (1..6).toList()
        val cards = mapOf(
            1 to CardState(box = 1, timesWrong = 1, dueAt = now - 1),
            2 to CardState(box = 3, timesWrong = 2), // climbed back
            3 to CardState(box = 2, timesWrong = 4, dueAt = now + hour), // not due yet: last
            4 to CardState(box = 1, timesWrong = 3, dueAt = now - 5),
            5 to CardState(box = 2, timesWrong = 0),
        )
        val p = ProgressData(cards = cards)
        assertEquals(listOf(4, 1, 3), Srs.mistakes(order, p, now))
        assertEquals(listOf(4, 1), Srs.mistakes(order, p, now, size = 2))
        assertTrue(Srs.mistakes(order, ProgressData(), now).isEmpty())
    }

    @Test
    fun rightAnswerBeforeDueOnlyMarksSeen() {
        val early = CardState(box = 2, lastSeenAt = now - hour, dueAt = now + hour, timesRight = 1)
        val s = Srs.answer(early, correct = true, now = now)
        assertEquals(2, s.box)
        assertEquals(now + hour, s.dueAt)
        assertEquals(now, s.lastSeenAt)
        assertEquals(1, s.timesRight)
    }

    @Test
    fun rightAnswerWhenDueOrNewClimbs() {
        assertEquals(3, Srs.answer(CardState(box = 2, dueAt = now), correct = true, now = now).box)
        assertEquals(1, Srs.answer(CardState(), correct = true, now = now).box)
    }

    @Test
    fun wrongAnswerDemotesEvenBeforeDue() {
        val s = Srs.answer(CardState(box = 5, dueAt = now + 7 * day), correct = false, now = now)
        assertEquals(1, s.box)
        assertEquals(now + 10 * min, s.dueAt)
    }

    @Test
    fun drillingTheSameCardsAgainAndAgainDoesNotMasterThem() {
        // The round-1 bug: Chyby rounds and mock exams back to back pushed cards to mastered in minutes.
        var s = Srs.answer(CardState(box = 2, timesWrong = 1, dueAt = now), correct = false, now = now)
        for (minute in 1..6) s = Srs.answer(s, correct = true, now = now + minute * min)
        assertEquals(1, s.box)
        assertTrue(Srs.isMistake(s))
        // Once due (10 min later) a right answer climbs one box, and the next one has to wait 4 hours.
        s = Srs.answer(s, correct = true, now = now + 10 * min)
        assertEquals(2, s.box)
        s = Srs.answer(s, correct = true, now = now + 11 * min)
        assertEquals(2, s.box)
        s = Srs.answer(s, correct = true, now = now + 10 * min + 4 * hour)
        assertEquals(3, s.box)
        assertFalse(Srs.isMistake(s))
    }

    @Test
    fun dueNeedsASeenCard() {
        assertFalse(Srs.isDue(CardState(), now))
        assertTrue(Srs.isDue(CardState(box = 1, dueAt = now), now))
    }

    // Box 3 counts as 0.85 only once the answer has held for about a day.

    @Test
    fun boxThreeReachedInAnAfternoonIsCappedUntilItHolds() {
        var s = Srs.answer(CardState(), true, now) // box 1
        s = Srs.answer(s, true, now + 10 * min) // box 2
        s = Srs.answer(s, true, now + 10 * min + 4 * hour) // box 3, four hours after the first right answer
        assertEquals(3, s.box)
        assertEquals(now, s.rightSince)
        assertFalse(Srs.isSettled(s))
        assertEquals(0.75, Srs.pCorrect(s), 1e-9)
        // Answered right again 20 hours after the first right answer: now it has held.
        val later = Srs.answer(s, true, now + 20 * hour)
        assertEquals(3, later.box)
        assertTrue(Srs.isSettled(later))
        assertEquals(0.85, Srs.pCorrect(later), 1e-9)
        // Once due it climbs to box 4 as before.
        assertEquals(4, Srs.answer(later, true, s.dueAt).box)
    }

    @Test
    fun aMissRestartsTheRun() {
        var s = CardState(box = 3, lastSeenAt = now, rightSince = now - 2 * day, dueAt = now + day)
        assertEquals(0.85, Srs.pCorrect(s), 1e-9)
        s = Srs.answer(s, false, now)
        assertEquals(null, s.rightSince)
        s = Srs.answer(s, true, now + 10 * min)
        assertEquals(now + 10 * min, s.rightSince)
    }

    @Test
    fun oldSavesAssumeTheShortestPossibleRun() {
        // No rightSince: a box-3 card may have climbed in 4 h 10 min, so it stays capped; box 4 needs over a day.
        assertEquals(0.75, Srs.pCorrect(CardState(box = 3, timesRight = 3, lastSeenAt = now)), 1e-9)
        assertEquals(0.93, Srs.pCorrect(CardState(box = 4, timesRight = 4, lastSeenAt = now)), 1e-9)
        assertEquals(10 * min + 4 * hour, Srs.minSpan(3))
    }

    // Pass chance: 40 of 50 drawn from 70.

    @Test
    fun passNeedsFortyOfFifty() {
        assertEquals(40, Srs.PASS_CORRECT)
    }

    @Test
    fun aMeanOfEightyPercentIsACoinFlipNotAPass() {
        val chance = Srs.passChance(List(70) { 0.8 })
        // 50 cards at 0.8: mean 40, sd 2.83, so P(X >= 40) is a little over a half.
        assertTrue("got $chance", chance in 0.53..0.60)
        assertTrue(chance < Srs.LIKELY_PASS)
    }

    @Test
    fun passChanceFollowsTheCards() {
        assertTrue(Srs.passChance(List(70) { 0.25 }) < 1e-6)
        assertTrue(Srs.passChance(List(70) { 0.97 }) > 0.999)
        // Uniform 0.85: mean 42.5, sd 2.52, z = -1.19.
        assertEquals(0.883, Srs.passChance(List(70) { 0.85 }), 0.01)
        assertEquals(0.0, Srs.passChance(emptyList()), 0.0)
        assertEquals(0.0, Srs.passChance(List(30) { 1.0 }), 0.0) // fewer cards than needed
    }

    @Test
    fun passChanceMatchesASimulatedExam() {
        val rnd = kotlin.random.Random(11)
        // A mixed pool: some mastered, some shaky, some new.
        val p = List(70) { i -> listOf(0.97, 0.93, 0.85, 0.75, 0.6, 0.25)[i % 6] }.map { it.coerceAtMost(1.0) }
        val boosted = p.map { (it + 0.1).coerceAtMost(0.99) }
        for (pool in listOf(p, boosted)) {
            val trials = 40_000
            var passes = 0
            repeat(trials) {
                val drawn = pool.indices.shuffled(rnd).take(50)
                if (drawn.count { rnd.nextDouble() < pool[it] } >= 40) passes++
            }
            val simulated = passes.toDouble() / trials
            assertEquals("pool mean ${pool.average()}", simulated, Srs.passChance(pool), 0.04)
        }
    }

    @Test
    fun normalCdfIsAccurate() {
        assertEquals(0.5, Srs.normalCdf(0.0), 1e-7)
        assertEquals(0.841345, Srs.normalCdf(1.0), 1e-5)
        assertEquals(0.022750, Srs.normalCdf(-2.0), 1e-5)
    }
}
