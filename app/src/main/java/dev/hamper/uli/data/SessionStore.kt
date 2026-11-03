package dev.hamper.uli.data

import android.util.AtomicFile
import dev.hamper.uli.ExamState
import dev.hamper.uli.QuizState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What is open right now: a learning round (with the boxes its cards had when it started, for the
 * "newly mastered" count) and/or a mock exam. Saved on every step so a killed process loses nothing.
 */
@Serializable
data class SavedSession(
    val version: Int = 1,
    val quiz: QuizState? = null,
    val roundStartBoxes: Map<Int, Int> = emptyMap(),
    val exam: ExamState? = null,
)

interface SessionStore {
    fun load(): SavedSession
    fun save(data: SavedSession)
}

/** session.json next to progress.json, replaced atomically like the progress file. */
class FileSessionStore(dir: File) : SessionStore {
    private val file = AtomicFile(File(dir, "session.json"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun load(): SavedSession = runCatching {
        json.decodeFromString<SavedSession>(file.readFully().decodeToString())
    }.getOrDefault(SavedSession())

    @Synchronized
    override fun save(data: SavedSession) {
        if (data.quiz == null && data.exam == null) {
            file.delete()
            return
        }
        val out = file.startWrite()
        try {
            out.write(json.encodeToString(data).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}

class MemorySessionStore(private var data: SavedSession = SavedSession()) : SessionStore {
    override fun load() = data
    override fun save(data: SavedSession) {
        this.data = data
    }
}
