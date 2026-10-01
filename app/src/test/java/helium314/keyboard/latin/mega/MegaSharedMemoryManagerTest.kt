package helium314.keyboard.latin.mega

import android.content.Context
import helium314.keyboard.latin.mega.MegaSharedMemoryManager.Companion.MINIMUM_FREE_BYTES
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class MegaSharedMemoryManagerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun stagesAndReadsGeneratedPayloadLargerThanOneMegabyte() {
        val manager = MegaSharedMemoryManager(context, availableBytes = { Long.MAX_VALUE })
        manager.deletePending()
        val block = ByteArray(8 * 1024) { (it % 251).toByte() }
        val repetitions = 160
        val expectedSize = block.size * repetitions.toLong()
        val expectedDigest = MessageDigest.getInstance("SHA-256").apply {
            repeat(repetitions) { update(block) }
        }.digest()

        val stagedUri = manager.stage { output -> repeat(repetitions) { output.write(block) } }
        val pendingUri = assertNotNull(manager.pendingUri())
        assertEquals(stagedUri, pendingUri)
        val actualDigest = MessageDigest.getInstance("SHA-256")
        var actualSize = 0L
        context.contentResolver.openInputStream(pendingUri)!!.use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                actualDigest.update(buffer, 0, count)
                actualSize += count
            }
        }
        assertEquals(expectedSize, actualSize)
        assertEquals(expectedDigest.toList(), actualDigest.digest().toList())
        manager.deletePending()
    }

    @Test
    fun failedImportPreservesPreviousPayload() {
        val manager = MegaSharedMemoryManager(context, availableBytes = { Long.MAX_VALUE })
        manager.deletePending()
        manager.stage { it.write("earlier".toByteArray()) }

        assertFailsWith<IOException> {
            manager.stage {
                it.write("partial".toByteArray())
                throw IOException("source failed")
            }
        }

        assertEquals("earlier", readPending(manager))
        manager.deletePending()
    }

    @Test
    fun completingPasteDoesNotDeleteAStillNewerStagedShare() {
        val manager = MegaSharedMemoryManager(context, availableBytes = { Long.MAX_VALUE })
        manager.deletePending()
        manager.stage { it.write("consumed".toByteArray()) }
        val descriptor = assertNotNull(manager.openPending())
        val consumedIdentity = manager.pendingIdentity(descriptor)
        try {
            manager.stage { it.write("latest".toByteArray()) }
            assertFalse(manager.deletePendingIfIdentity(consumedIdentity))
            assertEquals("latest", readPending(manager))
        } finally {
            descriptor.close()
            manager.deletePending()
        }
    }

    @Test
    fun sizeAndHeadroomLimitsRejectWritesWithoutReplacingPendingPayload() {
        val sizeLimited = MegaSharedMemoryManager(context, maximumBytes = 4, availableBytes = { Long.MAX_VALUE })
        sizeLimited.deletePending()
        sizeLimited.stage { it.write("keep".toByteArray()) }
        assertFailsWith<IOException> { sizeLimited.stage { it.write("12345".toByteArray()) } }
        assertEquals("keep", readPending(sizeLimited))

        val headroomLimited = MegaSharedMemoryManager(
            context,
            maximumBytes = 16,
            availableBytes = { MINIMUM_FREE_BYTES },
        )
        assertFailsWith<IOException> { headroomLimited.stage { it.write(1) } }
        assertEquals("keep", readPending(headroomLimited))
        sizeLimited.deletePending()
    }

    private fun readPending(manager: MegaSharedMemoryManager): String {
        val uri = assertNotNull(manager.pendingUri())
        return context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    }
}
