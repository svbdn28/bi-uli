package dev.hamper.uli.data

import android.util.AtomicFile
import dev.hamper.uli.engine.ProgressData
import kotlinx.serialization.json.Json
import java.io.File

interface ProgressStore {
    fun load(): ProgressData
    fun save(data: ProgressData)
}

/** Progress as one small JSON file in filesDir, replaced atomically on every write. */
class FileProgressStore(dir: File) : ProgressStore {
    private val file = AtomicFile(File(dir, "progress.json"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun load(): ProgressData = runCatching {
        json.decodeFromString<ProgressData>(file.readFully().decodeToString())
    }.getOrDefault(ProgressData())

    @Synchronized
    override fun save(data: ProgressData) {
        val out = file.startWrite()
        try {
            out.write(json.encodeToString(data).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}

/** Screenshot sessions run on seeded data and never touch the real progress file. */
class MemoryProgressStore(private var data: ProgressData = ProgressData()) : ProgressStore {
    override fun load() = data
    override fun save(data: ProgressData) {
        this.data = data
    }
}
