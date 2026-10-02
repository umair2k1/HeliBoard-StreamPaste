package helium314.keyboard.latin.streampaste

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

enum class StreamPasteOutcome { COMPLETED, CANCELLED, FAILED }
class StreamPasteController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeJob: Job? = null
    private var activeDescriptor: ParcelFileDescriptor? = null
    @Volatile private var committedBytes: Long = 0

    val isRunning: Boolean get() = activeJob != null
    val committedUtf8Bytes: Long get() = committedBytes

    fun startSync(
        descriptor: ParcelFileDescriptor,
        totalBytes: Long,
        commitChunk: (CharSequence) -> Boolean,
        onProgress: (committedUtf8Bytes: Long, totalBytes: Long) -> Unit,
        onFinished: (StreamPasteOutcome) -> Unit,
    ): Job = start(descriptor, totalBytes, { text -> commitChunk(text) }, onProgress, onFinished)

    fun start(
        descriptor: ParcelFileDescriptor,
        totalBytes: Long,
        commitChunk: suspend (CharSequence) -> Boolean,
        onProgress: (committedUtf8Bytes: Long, totalBytes: Long) -> Unit,
        onFinished: (StreamPasteOutcome) -> Unit,
    ): Job {
        check(activeJob == null) { "A stream paste is already active" }
        activeDescriptor = descriptor
        committedBytes = 0
        val job = scope.launch {
            var outcome = StreamPasteOutcome.FAILED
            try {
                val chunks = Channel<Chunk>(capacity = 1)
                val producer = launch(Dispatchers.IO) {
                    try {
                        val decoder = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                        InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(descriptor), decoder).use { reader ->
                            val input = CharArray(READ_BUFFER_SIZE)
                            var chunk = StringBuilder()
                            var codePoints = 0
                            var pendingHighSurrogate: Char? = null
                            while (true) {
                                val count = reader.read(input)
                                if (count < 0) break
                                for (index in 0 until count) {
                                    val character = input[index]
                                    if (pendingHighSurrogate != null) {
                                        chunk.append(pendingHighSurrogate!!)
                                        chunk.append(character)
                                        pendingHighSurrogate = null
                                        codePoints++
                                    } else if (Character.isHighSurrogate(character)) {
                                        pendingHighSurrogate = character
                                        continue
                                    } else {
                                        chunk.append(character)
                                        codePoints++
                                    }
                                    if (codePoints == MAX_CODE_POINTS_PER_CHUNK) {
                                        chunks.send(Chunk(chunk.toString(), utf8Length(chunk)))
                                        chunk = StringBuilder()
                                        codePoints = 0
                                    }
                                }
                            }
                            if (pendingHighSurrogate != null) throw IllegalArgumentException("Invalid UTF-8 decoder output")
                            if (chunk.isNotEmpty()) chunks.send(Chunk(chunk.toString(), utf8Length(chunk)))
                        }
                        chunks.close()
                    } catch (failure: Throwable) {
                        val isCancelled = failure is CancellationException || activeJob?.isCancelled == true
                        chunks.close(if (isCancelled) null else failure)
                        if (failure is CancellationException) throw failure
                    }
                }
                try {
                    var hasCommitted = false
                    for (chunk in chunks) {
                        if (hasCommitted) delay(COMMIT_INTERVAL_MILLIS)
                        if (!commitChunk(chunk.text)) throw CommitRejectedException()
                        committedBytes += chunk.utf8Bytes
                        onProgress(committedBytes, totalBytes)
                        hasCommitted = true
                    }
                    producer.join()
                    outcome = StreamPasteOutcome.COMPLETED
                } finally {
                    if (!producer.isCompleted) producer.cancelAndJoin()
                }
            } catch (_: CancellationException) {
                outcome = StreamPasteOutcome.CANCELLED
            } catch (_: Throwable) {
                outcome = StreamPasteOutcome.FAILED
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    runCatching { activeDescriptor?.close() }
                    activeDescriptor = null
                    activeJob = null
                    onFinished(outcome)
                }
            }
        }
        activeJob = job
        return job
    }

    fun cancel() {
        activeJob?.cancel()
    }

    fun shutdown() {
        cancel()
        runCatching { activeDescriptor?.close() }
        scope.cancel()
    }

    private data class Chunk(val text: String, val utf8Bytes: Long)

    private class CommitRejectedException : Exception()

    companion object {
        const val MAX_CODE_POINTS_PER_CHUNK = 2_048
        private const val READ_BUFFER_SIZE = 4_096
        private const val COMMIT_INTERVAL_MILLIS = 15L

        private fun utf8Length(text: CharSequence): Long {
            var bytes = 0L
            var index = 0
            while (index < text.length) {
                val character = text[index]
                bytes += when {
                    Character.isHighSurrogate(character) -> { index++; 4L }
                    character.code < 0x80 -> 1L
                    character.code < 0x800 -> 2L
                    else -> 3L
                }
                index++
            }
            return bytes
        }
    }
}
