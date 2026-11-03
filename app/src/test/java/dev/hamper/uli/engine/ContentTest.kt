package dev.hamper.uli.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContentTest {
    private val questions: List<Question> by lazy {
        val file = listOf(File("../content/questions.json"), File("content/questions.json")).first { it.isFile }
        Content.parse(file.readText())
    }

    @Test
    fun seventyQuestionsLoadInOrder() {
        assertEquals(70, questions.size)
        assertEquals((1..70).toList(), questions.map { it.id })
        questions.forEach { Topic.of(it.topic) }
    }

    @Test
    fun chooseMatchesTheNumberOfCorrectOptions() {
        for (q in questions) {
            assertEquals("Q${q.id}", q.choose, q.options.count { it.correct })
            when (q.kind) {
                Kind.Multi -> assertTrue("Q${q.id}", q.choose > 1)
                else -> assertEquals("Q${q.id}", 1, q.choose)
            }
        }
    }

    @Test
    fun trueFalseHasTrueAndFalse() {
        val tf = questions.filter { it.kind == Kind.TrueFalse }
        assertTrue(tf.isNotEmpty())
        for (q in tf) assertEquals("Q${q.id}", listOf("True", "False"), q.options.map { it.text })
    }

    @Test
    fun terminalDemosFitFortyFourColumns() {
        for (q in questions) {
            for (line in q.demo?.lines().orEmpty()) {
                assertTrue("Q${q.id}: ${line.length} > 44: $line", line.length <= 44 && '\t' !in line)
            }
        }
    }

    @Test
    fun everyTopicHasQuestions() {
        assertEquals(Topic.entries.toSet(), questions.map { it.topicEnum }.toSet())
    }

    @Test
    fun commandOptionsAreDetected() {
        val byId = questions.associateBy { it.id }
        assertEquals(setOf(0, 1, 2), Code.codeOptions(byId.getValue(67)))
        assertEquals(4, Code.codeOptions(byId.getValue(36)).size)
        assertTrue(Code.codeOptions(byId.getValue(5)).isEmpty())
        assertTrue(Code.codeOptions(byId.getValue(32)).isEmpty())
        assertTrue(Code.codeOptions(byId.getValue(16)).isEmpty())
    }

    @Test
    fun inlineCodeInProse() {
        fun marked(text: String, english: Boolean) = Code.spans(text, english).map { text.substring(it.first, it.last + 1) }
        assertEquals(listOf("ls -S"), marked("The command ls -S will sort files:", true))
        assertEquals(listOf("cp"), marked("When using the cp command, you must provide both", true))
        assertEquals(listOf("-S", "-r", "ls -Sr"), marked("-S triedi podľa veľkosti. Opačné poradie dostaneš pridaním -r (ls -Sr).", false))
        assertFalse(marked("Free as in freedom, not free beer.", false).isNotEmpty())
    }

    @Test
    fun codeSpansDoNotBreakInsidePathsButLongCodeWrapsAtSpaces() {
        val wj = '\u2060'
        assertEquals("/${wj}usr/${wj}bin/${wj}program", Code.unbreakable("/usr/bin/program"))
        assertEquals("ls\u00A0-${wj}l", Code.unbreakable("ls -l"))
        val long = Code.unbreakable("sudo chmod 4755 /usr/bin/program")
        assertTrue(long.contains(' '))
        assertEquals("16384 Nov 3 /${wj}usr/${wj}bin", Code.gluePaths("16384 Nov 3 /usr/bin"))
    }

    @Test
    fun skLineHiddenWhenItEqualsTheQuestionAndDisputedNoteIsASentence() {
        val q6 = questions.first { it.id == 6 }
        assertFalse(q6.hasDistinctSk)
        assertTrue(questions.first { it.id == 67 }.hasDistinctSk)
        assertEquals(
            "V GNU tar funguje aj tar -tf foo.tar.gz (autodetekcia kompresie), kľúč NDG chce -tzf.",
            questions.first { it.id == 33 }.disputedNote,
        )
        assertEquals(null, questions.first { it.id == 67 }.disputedNote)
    }
}
