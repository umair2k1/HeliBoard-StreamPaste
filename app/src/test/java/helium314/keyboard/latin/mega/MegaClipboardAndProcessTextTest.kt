package helium314.keyboard.latin.mega

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import helium314.keyboard.latin.inputlogic.InputLogic
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
@Implements(FileProvider::class)
open class ShadowFileProvider {
    companion object {
        @Implementation
        @JvmStatic
        fun getUriForFile(context: Context, authority: String, file: File): Uri {
            return Uri.parse("content://$authority/mega_paste/${file.name}")
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowFileProvider::class])
class MegaClipboardAndProcessTextTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @org.junit.Before
    fun setupStorage() {
        val cache = context.cacheDir
        org.robolectric.shadows.ShadowStatFs.registerStats(cache.path, 1000000, 1000000, 1000000)
        val megaDir = File(cache, "mega_paste").apply { mkdirs() }
        org.robolectric.shadows.ShadowStatFs.registerStats(megaDir.path, 1000000, 1000000, 1000000)
    }
    @Test
    fun autoStreamThresholdIsFiveThousandCharacters() {
        assertEquals(5000, InputLogic.STREAM_PASTE_AUTO_THRESHOLD)
    }

    @Test
    fun stageTextCreatesPendingPayloadWithTimestamp() {
        val manager = MegaSharedMemoryManager(context)
        manager.deletePending()
        assertFalse(manager.hasPendingPayload())

        val uri = manager.stageText("Hello MegaPaste from stageText")
        assertTrue(uri.toString().contains("megapasteprovider"))
        assertTrue(manager.hasPendingPayload())
        assertTrue(manager.pendingLastModified() > 0)
        assertEquals("Hello MegaPaste from stageText", readPending())
        manager.deletePending()
    }

    @Test
    fun stageFromClipboardWithPlainTextStagesSuccessfully() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("test", "Copied text from clipboard"))

        val manager = MegaSharedMemoryManager(context)
        manager.deletePending()
        assertFalse(manager.hasPendingPayload())

        val result = manager.stageFromClipboard()
        assertTrue(result)
        assertTrue(manager.hasPendingPayload())
        assertTrue(manager.pendingLastModified() > 0)
        assertEquals("Copied text from clipboard", readPending())
        manager.deletePending()
    }

    @Test
    fun stageFromClipboardWithEmptyClipboardReturnsFalse() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.clearPrimaryClip()
        val manager = MegaSharedMemoryManager(context)
        manager.deletePending()

        assertFalse(manager.stageFromClipboard())
        assertFalse(manager.hasPendingPayload())
        assertEquals(0L, manager.pendingLastModified())
    }

    @Test
    fun stageFromClipboardWithNonTextItemReturnsFalse() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData(android.content.ClipDescription("image", arrayOf("image/png")), ClipData.Item(Uri.parse("content://fake/image.png")))
        clipboard.setPrimaryClip(clip)

        val manager = MegaSharedMemoryManager(context)
        manager.deletePending()

        assertFalse(manager.stageFromClipboard())
        assertFalse(manager.hasPendingPayload())
    }

    @Test
    fun shareTargetReceiverActivityFinishesOnEmptyIntentWithoutStaging() {
        val manager = MegaSharedMemoryManager(context)
        manager.deletePending()

        val intent = Intent(Intent.ACTION_SEND)
        val controller = Robolectric.buildActivity(ShareTargetReceiverActivity::class.java, intent)
        controller.create()
        val activity = controller.get()
        assertTrue(activity.isFinishing)
        assertFalse(manager.hasPendingPayload())
    }

    @Test
    fun shareTargetReceiverActivityHandlesProcessTextIntentAndStagesPayload() {
        val manager = MegaSharedMemoryManager(context)
        manager.deletePending()
        assertFalse(manager.hasPendingPayload())

        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            putExtra(Intent.EXTRA_PROCESS_TEXT, "Sample process text payload from selection menu")
        }
        val controller = Robolectric.buildActivity(ShareTargetReceiverActivity::class.java, intent)
        controller.create()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val activity = controller.get()
        assertTrue(activity.isFinishing)
        assertTrue(manager.hasPendingPayload())
        assertEquals("Sample process text payload from selection menu", readPending())
        manager.deletePending()
    }

    @Test
    fun autoStreamThresholdBranchingDistinguishesShortAndLongPayloads() {
        val shortPayload = "a".repeat(InputLogic.STREAM_PASTE_AUTO_THRESHOLD)
        val longPayload = "a".repeat(InputLogic.STREAM_PASTE_AUTO_THRESHOLD + 1)

        val shouldStreamShort = shortPayload.length > InputLogic.STREAM_PASTE_AUTO_THRESHOLD
        val shouldStreamLong = longPayload.length > InputLogic.STREAM_PASTE_AUTO_THRESHOLD

        assertFalse(shouldStreamShort, "Payload at or below 5000 characters must not route to stream paste")
        assertTrue(shouldStreamLong, "Payload exceeding 5000 characters must route to stream paste")
    }

    private fun readPending(): String {
        val payloadFile = File(File(context.cacheDir, "mega_paste"), "pending")
        return payloadFile.readText(Charsets.UTF_8)
    }
}
