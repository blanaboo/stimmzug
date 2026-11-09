package io.github.blanaboo.stimmzug.data.log

import android.content.Context
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The session log (#37) on the device: one [LoggedSession] per line of [file] (JSON Lines). Appending never
 * rewrites what is there, and a damaged line costs only that session.
 */
class SessionLogRepository(private val file: File, private val io: CoroutineDispatcher = Dispatchers.IO) {

    constructor(context: Context) : this(File(context.applicationContext.filesDir, FILE_NAME))

    private val writes = CoroutineScope(SupervisorJob() + io)

    /** Appends [session]; [record] does it without waiting, e.g. while the screen closes. */
    suspend fun append(session: LoggedSession) = withContext(io) {
        lock.withLock { file.appendText(json.encodeToString(LoggedSession.serializer(), session) + "\n") }
    }

    fun record(session: LoggedSession): Job = writes.launch { append(session) }

    suspend fun readAll(): List<LoggedSession> = withContext(io) {
        lock.withLock {
            if (!file.exists()) return@withLock emptyList()
            file.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
                runCatching { json.decodeFromString(LoggedSession.serializer(), line) }.getOrNull()
            }
        }
    }

    /** Writes the log as CSV into [dir] for sharing; `null` while the log is empty. */
    suspend fun exportCsv(dir: File): File? {
        val sessions = readAll()
        if (sessions.isEmpty()) return null
        return withContext(io) {
            dir.mkdirs()
            File(dir, "stimmzug-uebungsprotokoll-${LocalDate.now()}.csv").apply {
                writeText(SessionLogCsv.write(sessions), Charsets.UTF_8)
            }
        }
    }

    companion object {
        const val FILE_NAME = "session_log.jsonl"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        // One lock for all instances, as they share the file.
        private val lock = Mutex()
    }
}
