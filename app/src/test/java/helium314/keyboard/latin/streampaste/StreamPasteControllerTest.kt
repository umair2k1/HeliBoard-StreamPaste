package helium314.keyboard.latin.streampaste

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class StreamPasteControllerTest {
    @Test
    fun streamsLargeUtf8TextInBoundedCodePointChunksWithCompleteProgress() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = buildString {
            repeat(350_000) { append("A😀é\n") }
        }
        val bytes = fixture.toByteArray(Charsets.UTF_8)
        val file = payloadFile(bytes)
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val controller = StreamPasteController()
        val reconstructed = StringBuilder()
        val progress = mutableListOf<Pair<Long, Long>>()
        var outcome: StreamPasteOutcome? = null
        var finishCalls = 0
        try {
            val job = controller.start(
                descriptor,
                bytes.size.toLong(),
                commitChunk = { chunk ->
                    reconstructed.append(chunk)
                    assertTrue(Character.codePointCount(chunk, 0, chunk.length) <= StreamPasteController.MAX_CODE_POINTS_PER_CHUNK)
                    true
                },
                onProgress = { committed, total -> progress += committed to total },
                onFinished = { result -> outcome = result; finishCalls++ },
            )
            job.join()

            assertEquals(fixture, reconstructed.toString())
            assertTrue(reconstructed.contains("😀"))
            assertEquals(StreamPasteOutcome.COMPLETED, outcome)
            assertEquals(1, finishCalls)
            assertEquals(bytes.size.toLong() to bytes.size.toLong(), progress.last())
            assertFalse(descriptor.fileDescriptor.valid())
        } finally {
            controller.shutdown()
            file.delete()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cancellationAfterFirstCommitStopsFurtherDeliveryAndClosesDescriptor() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val bytes = "stream😀".repeat(400_000).toByteArray(Charsets.UTF_8)
        val file = payloadFile(bytes)
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val controller = StreamPasteController()
        var commitCount = 0
        var outcome: StreamPasteOutcome? = null
        var finishCalls = 0
        try {
            val job = controller.start(
                descriptor,
                bytes.size.toLong(),
                commitChunk = {
                    commitCount++
                    true
                },
                onProgress = { committed, _ -> if (committed > 0) controller.cancel() },
                onFinished = { result -> outcome = result; finishCalls++ },
            )
            job.join()

            assertEquals(StreamPasteOutcome.CANCELLED, outcome)
            assertEquals(1, commitCount)
            assertEquals(1, finishCalls)
            assertFalse(descriptor.fileDescriptor.valid())
        } finally {
            controller.shutdown()
            file.delete()
            Dispatchers.resetMain()
        }
    }

    private fun payloadFile(bytes: ByteArray): File = File.createTempFile("mega-paste-test", ".txt").apply {
        writeBytes(bytes)
    }
}
