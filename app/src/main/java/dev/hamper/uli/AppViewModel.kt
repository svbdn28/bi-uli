package dev.hamper.uli

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hamper.uli.data.FileProgressStore
import dev.hamper.uli.data.FileSessionStore
import dev.hamper.uli.data.MemorySessionStore
import dev.hamper.uli.data.SavedSession
import dev.hamper.uli.data.SessionStore
import dev.hamper.uli.data.ProgressStore
import dev.hamper.uli.engine.Content
import dev.hamper.uli.engine.Exam
import dev.hamper.uli.engine.ExamResult
import dev.hamper.uli.engine.Kind
import dev.hamper.uli.engine.ProgressData
import dev.hamper.uli.engine.Question
import dev.hamper.uli.engine.Round
import dev.hamper.uli.engine.Srs
import dev.hamper.uli.engine.Topic
import dev.hamper.uli.engine.isCorrect
import dev.hamper.uli.engine.result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.random.Random

sealed interface Screen {
    data object Home : Screen
    data object Quiz : Screen
    data object RoundEnd : Screen
    data object ExamRun : Screen
    data object ExamEnd : Screen
    data class Browse(val topic: Topic? = null, val expanded: Int? = null) : Screen
}

@Serializable
enum class RoundKind { Smart, Mistakes }

@Serializable
data class QuizState(
    val kind: RoundKind,
    val round: Round,
    /** Display order of the current question's options (indices into the original list). */
    val order: List<Int>,
    val selected: Set<Int> = emptySet(),
    val answered: Boolean = false,
    val correct: Boolean = false,
)

@Serializable
data class ExamState(
    val exam: Exam,
    val order: List<Int>,
    val selected: Set<Int> = emptySet(),
)

data class RoundSummary(
    val kind: RoundKind,
    val score: Int,
    val total: Int,
    val mistakes: List<Int>,
    val newlyMastered: Int,
)

data class ExamSummary(val result: ExamResult, val exam: Exam) {
    val durationMs: Long get() = (exam.finishedAt ?: exam.startedAt) - exam.startedAt
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val questions: List<Question> = app.assets.open("questions.json").use { Content.parse(it.readBytes().decodeToString()) }
    val byId: Map<Int, Question> = questions.associateBy { it.id }
    val ids: List<Int> = questions.map { it.id }

    private var store: ProgressStore = FileProgressStore(app.filesDir)
    private var sessionStore: SessionStore = FileSessionStore(app.filesDir)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private var random: Random = Random.Default
    private var seededShuffle = false
    var clock: () -> Long = System::currentTimeMillis
        private set

    var progress by mutableStateOf(store.load())
        private set
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var quiz by mutableStateOf<QuizState?>(null)
        private set
    var exam by mutableStateOf<ExamState?>(null)
        private set
    var roundSummary by mutableStateOf<RoundSummary?>(null)
        private set
    var examSummary by mutableStateOf<ExamSummary?>(null)
        private set

    /** Screenshot session: one-shot effects render a fixed frame so captures are repeatable. */
    var shotMode by mutableStateOf(false)
        private set

    /** Boxes at the start of the round, to count what got newly mastered. */
    private var roundStart: Map<Int, Int> = emptyMap()

    init {
        // An open round or exam survives a killed process; Domov offers to continue it.
        val saved = sessionStore.load()
        fun known(list: List<Int>) = list.isNotEmpty() && list.all { it in byId }
        quiz = saved.quiz?.takeIf { known(it.round.queue) && !it.round.isFinished }
        roundStart = if (quiz != null) saved.roundStartBoxes else emptyMap()
        exam = saved.exam?.takeIf { known(it.exam.ids) && !it.exam.isFinished }
    }

    private fun saveSession() {
        if (shotMode) return
        val data = SavedSession(quiz = quiz, roundStartBoxes = roundStart, exam = exam)
        val s = sessionStore
        viewModelScope.launch(io) { s.save(data) }
    }

    private fun persist(data: ProgressData) {
        progress = data
        val s = store
        viewModelScope.launch(io) { s.save(data) }
    }

    private fun shuffledOrder(q: Question): List<Int> {
        val r = if (seededShuffle) Random(q.id * 31 + 7) else random
        return q.options.indices.shuffled(r)
    }

    // Home

    /** What a Chyby round drills right now (up to a round's worth). */
    fun mistakeIds(): List<Int> = Srs.mistakes(ids, progress, clock())

    /** Nothing answered yet and nothing open: the main button says "Začať". */
    val isFresh: Boolean get() = progress.cards.isEmpty() && quiz == null

    fun nextRoundPreview(): List<Int> = Srs.buildRound(ids, progress, clock())

    fun toggleSk() = persist(progress.copy(showSk = !progress.showSk))

    fun resetProgress() {
        persist(ProgressData(showSk = progress.showSk))
        quiz = null
        exam = null
        roundStart = emptyMap()
        saveSession()
    }

    // Learning rounds

    fun startRound() = startRound(RoundKind.Smart, Srs.buildRound(ids, progress, clock()))

    /** Domov's main button: back into the open round, or a fresh smart round. */
    fun continueOrStart() {
        if (quiz != null) screen = Screen.Quiz else startRound()
    }

    fun startMistakes() {
        val m = mistakeIds()
        if (m.isNotEmpty()) startRound(RoundKind.Mistakes, m)
    }

    private fun startRound(kind: RoundKind, roundIds: List<Int>) {
        roundStart = roundIds.associateWith { progress.card(it).box }
        val round = Round(roundIds)
        quiz = QuizState(kind = kind, round = round, order = shuffledOrder(byId.getValue(round.current!!)))
        saveSession()
        screen = Screen.Quiz
    }

    fun current(): Question? = quiz?.round?.current?.let(byId::get)

    /** single and truefalse: one tap answers. */
    fun pick(index: Int) {
        val s = quiz ?: return
        if (s.answered) return
        val q = current() ?: return
        if (q.kind == Kind.Multi) {
            val sel = if (index in s.selected) s.selected - index else if (s.selected.size < q.choose) s.selected + index else s.selected
            quiz = s.copy(selected = sel)
            saveSession()
        } else {
            quiz = s.copy(selected = setOf(index))
            submit()
        }
    }

    fun submit() {
        val s = quiz ?: return
        val q = current() ?: return
        if (s.answered) return
        if (q.kind == Kind.Multi && s.selected.size != q.choose) return
        val ok = q.isCorrect(s.selected)
        val firstAttempt = !s.round.isRetry
        quiz = s.copy(round = s.round.answer(ok), answered = true, correct = ok)
        val now = clock()
        val card = progress.card(q.id)
        // Only the first attempt in a round moves the box; corrections just mark the card as seen.
        val updated = if (firstAttempt) Srs.answer(card, ok, now) else card.copy(lastSeenAt = now)
        persist(progress.copy(cards = progress.cards + (q.id to updated)))
        saveSession()
    }

    fun next() {
        val s = quiz ?: return
        if (!s.answered) return
        val round = s.round.next()
        if (round.isFinished) {
            val mastered = round.ids.count { (roundStart[it] ?: 0) < Srs.MASTERED_BOX && progress.card(it).isMastered }
            roundSummary = RoundSummary(s.kind, round.score, round.total, round.mistakes, mastered)
            quiz = null
            roundStart = emptyMap()
            screen = Screen.RoundEnd
        } else {
            quiz = QuizState(kind = s.kind, round = round, order = shuffledOrder(byId.getValue(round.current!!)))
        }
        saveSession()
    }

    // Mock exam

    fun startExam() {
        val e = Exam.start(ids, clock(), random)
        exam = ExamState(e, shuffledOrder(byId.getValue(e.current!!)))
        saveSession()
        screen = Screen.ExamRun
    }

    fun resumeExam() {
        if (exam != null) screen = Screen.ExamRun
    }

    fun discardExam() {
        exam = null
        saveSession()
    }

    fun examPick(index: Int) {
        val s = exam ?: return
        val q = s.exam.current?.let(byId::get) ?: return
        val sel = when {
            q.kind != Kind.Multi -> setOf(index)
            index in s.selected -> s.selected - index
            s.selected.size < q.choose -> s.selected + index
            else -> s.selected
        }
        exam = s.copy(selected = sel)
        saveSession()
    }

    fun examNext() {
        val s = exam ?: return
        val q = s.exam.current?.let(byId::get) ?: return
        if (s.selected.size != q.choose) return
        val e = s.exam.answer(s.selected, clock())
        if (e.isFinished) {
            finishExam(e)
        } else {
            exam = ExamState(e, shuffledOrder(byId.getValue(e.current!!)))
            saveSession()
        }
    }

    private fun finishExam(e: Exam) {
        val res = e.result(byId)
        val now = clock()
        // The mock exam is real practice: every answer moves its card like in a round.
        val cards = progress.cards.toMutableMap()
        for (id in e.ids) cards[id] = Srs.answer(progress.card(id), id !in res.wrong, now)
        persist(progress.copy(cards = cards))
        examSummary = ExamSummary(res, e)
        exam = null
        saveSession()
        screen = Screen.ExamEnd
    }

    // Navigation

    fun openBrowse(topic: Topic? = null) {
        screen = Screen.Browse(topic)
    }

    /** Leaving mid-round or mid-exam keeps it open (and saved); Domov offers to continue. */
    fun home() {
        screen = Screen.Home
    }

    // Debug screenshots

    fun applyShot(name: String) {
        Shots.apply(this, name)
    }

    internal fun installShot(
        data: ProgressData,
        now: Long,
        screen: Screen,
        quiz: QuizState? = null,
        exam: ExamState? = null,
        roundSummary: RoundSummary? = null,
        examSummary: ExamSummary? = null,
    ) {
        store = dev.hamper.uli.data.MemoryProgressStore(data)
        sessionStore = MemorySessionStore()
        seededShuffle = true
        shotMode = true
        random = Random(42)
        clock = { now }
        progress = data
        this.quiz = quiz
        this.exam = exam
        this.roundSummary = roundSummary
        this.examSummary = examSummary
        this.screen = screen
    }

    internal fun orderFor(q: Question): List<Int> = shuffledOrder(q)

    internal fun enableSeededShuffle() {
        seededShuffle = true
    }
}
