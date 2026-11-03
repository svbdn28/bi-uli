package dev.hamper.uli.data

import androidx.core.util.AtomicFile
import dev.hamper.uli.engine.ProgressData
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

/** What reading a saved file found. Only a missing file means "nothing yet"; anything else is kept safe. */
sealed interface Loaded<out T> {
    data class Ok<T>(val data: T) : Loaded<T>
    data object Missing : Loaded<Nothing>

    /** The file is there but unreadable. [backup] is a copy of it (null if even copying failed). */
    data class Corrupt(val backup: File?, val error: Throwable) : Loaded<Nothing>
}

interface ProgressStore {
    fun load(): Loaded<ProgressData>

    /** Throws when the write fails; the caller decides how to tell the user. */
    fun save(data: ProgressData)
}

internal val storeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Reads [name] in [dir]. A missing file is [Loaded.Missing]; a file that cannot be read or parsed is copied to
 * `<stem>.corrupt-<timestamp>.json` first and reported as [Loaded.Corrupt], never silently treated as empty.
 */
internal fun <T> readJson(dir: File, name: String, now: () -> Long, decode: (String) -> T): Loaded<T> {
    val base = File(dir, name)
    val file = AtomicFile(base)
    val bytes = try {
        file.readFully()
    } catch (e: FileNotFoundException) {
        return Loaded.Missing
    } catch (e: Exception) {
        return Loaded.Corrupt(backup(dir, base, now()), e)
    }
    return try {
        Loaded.Ok(decode(bytes.decodeToString()))
    } catch (e: Exception) {
        Loaded.Corrupt(backup(dir, base, now()), e)
    }
}

private fun backup(dir: File, base: File, now: Long): File? = runCatching {
    // The same damaged file read on every start is kept once, not once per start.
    val bytes = base.readBytes()
    dir.listFiles { f -> f.name.startsWith("${base.nameWithoutExtension}.corrupt-") }
        ?.firstOrNull { it.readBytes().contentEquals(bytes) }
        ?.let { return@runCatching it }
    val copy = File(dir, "${base.nameWithoutExtension}.corrupt-$now.json")
    base.copyTo(copy, overwrite = true)
}.getOrNull()

internal fun writeJson(dir: File, name: String, text: String) {
    val file = AtomicFile(File(dir, name))
    val out = file.startWrite()
    try {
        out.write(text.encodeToByteArray())
        file.finishWrite(out)
    } catch (e: Exception) {
        file.failWrite(out)
        throw e
    }
}

/** Progress as one small JSON file in filesDir, replaced atomically on every write. */
class FileProgressStore(private val dir: File, private val now: () -> Long = System::currentTimeMillis) : ProgressStore {
    override fun load(): Loaded<ProgressData> =
        readJson(dir, NAME, now) { storeJson.decodeFromString<ProgressData>(it) }

    @Synchronized
    override fun save(data: ProgressData) = writeJson(dir, NAME, storeJson.encodeToString(data))

    companion object {
        const val NAME = "progress.json"
    }
}

/** Screenshot sessions run on seeded data and never touch the real progress file. */
class MemoryProgressStore(private var data: ProgressData = ProgressData()) : ProgressStore {
    override fun load(): Loaded<ProgressData> = Loaded.Ok(data)
    override fun save(data: ProgressData) {
        this.data = data
    }
}
