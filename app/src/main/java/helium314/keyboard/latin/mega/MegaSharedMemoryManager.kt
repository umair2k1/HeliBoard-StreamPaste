package helium314.keyboard.latin.mega

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.util.AtomicFile
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.WeakHashMap
/** Stores the latest completed share without retaining its payload in memory. */
class MegaSharedMemoryManager @JvmOverloads internal constructor(
    context: Context,
    private val maximumBytes: Long = MAXIMUM_BYTES,
    private val availableBytes: (File) -> Long = { directory -> StatFs(directory.path).availableBytes },
) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.cacheDir, DIRECTORY_NAME)
    private val payloadFile = File(directory, PAYLOAD_NAME)
    private val atomicFile = AtomicFile(payloadFile)
    private val authority = "${appContext.packageName}.megapasteprovider"

    @Throws(IOException::class)
    fun stage(write: (OutputStream) -> Unit): Uri = synchronized(OPERATION_LOCK) {
        stageLocked(write)
    }

    @Throws(IOException::class)
    fun stageText(text: CharSequence): Uri = stage { output ->
        writeText(text, output)
    }

    @Throws(IOException::class)
    fun stageUris(uris: List<Uri>): Uri = stage { output ->
        writeUris(uris, output)
    }

    @Throws(IOException::class)
    fun stageUri(uri: Uri): Uri = stageUris(listOf(uri))

    fun stageFromClipboard(): Boolean {
        val clipboardManager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clipData = clipboardManager.primaryClip ?: return false
        if (clipData.itemCount == 0) return false
        val item = clipData.getItemAt(0) ?: return false

        val uri = item.uri
        if (uri != null) {
            val isTextUri = isTextCompatibleUri(uri, clipData.description)
            if (isTextUri) {
                return runCatching { stageUri(uri) }.isSuccess
            }
            return false
        }
        val text = item.text?.takeIf { it.isNotEmpty() }
            ?: item.coerceToText(appContext)?.takeIf { it.isNotEmpty() }
        if (text != null) {
            return runCatching { stageText(text) }.isSuccess
        }
        return false
    }

    private fun isTextCompatibleUri(uri: Uri, description: ClipDescription?): Boolean {
        if (description != null) {
            for (i in 0 until description.mimeTypeCount) {
                val mime = description.getMimeType(i)
                if (isTextMimeType(mime)) return true
            }
        }
        val type = runCatching { appContext.contentResolver.getType(uri) }.getOrNull()
        if (type != null && isTextMimeType(type)) return true
        if (uri.scheme == "file") return true
        return false
    }

    private fun isTextMimeType(mime: String?): Boolean {
        if (mime == null) return false
        val lower = mime.lowercase()
        return lower.startsWith("text/")
                || lower == "application/json"
                || lower == "application/xml"
                || lower == "application/javascript"
                || lower == "application/x-javascript"
                || lower == "application/x-yaml"
                || lower.endsWith("+json")
                || lower.endsWith("+xml")
    }

    private fun writeUris(uris: List<Uri>, output: OutputStream) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        uris.forEachIndexed { index, uri ->
            if (index > 0) output.write(NEWLINE)
            val input = appContext.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open shared item")
            input.use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun writeText(text: CharSequence, output: OutputStream) {
        val writer = OutputStreamWriter(output, Charsets.UTF_8)
        val buffer = CharArray(TEXT_BUFFER_SIZE)
        var offset = 0
        while (offset < text.length) {
            var count = minOf(buffer.size, text.length - offset)
            if (offset + count < text.length && count > 0 && Character.isHighSurrogate(text[offset + count - 1]) &&
                Character.isLowSurrogate(text[offset + count])) count--
            for (i in 0 until count) buffer[i] = text[offset + i]
            writer.write(buffer, 0, count)
            offset += count
        }
        writer.flush()
    }

    private fun stageLocked(write: (OutputStream) -> Unit): Uri {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create staging directory")
        var count = 0L
        val fileOutput = atomicFile.startWrite()
        val output = object : OutputStream() {
            override fun write(value: Int) {
                reserve(1)
                fileOutput.write(value)
            }

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                if (length < 0 || offset < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
                reserve(length)
                fileOutput.write(bytes, offset, length)
            }

            override fun flush() = fileOutput.flush()

            override fun close() = flush()

            private fun reserve(length: Int) {
                if (length.toLong() > maximumBytes - count) throw IOException("Shared text exceeds size limit")
                if (availableBytes(directory) - length < MINIMUM_FREE_BYTES) throw IOException("Insufficient cache space")
                count += length
            }
        }
        try {
            write(output)
            output.flush()
            atomicFile.finishWrite(fileOutput)
            pendingIdentityGeneration++
        } catch (failure: Throwable) {
            atomicFile.failWrite(fileOutput)
            throw failure
        }
        return FileProvider.getUriForFile(appContext, authority, payloadFile)
    }

    fun pendingUri(): Uri? = synchronized(OPERATION_LOCK) {
        if (hasPendingPayload()) FileProvider.getUriForFile(appContext, authority, payloadFile) else null
    }
    fun openPending(): ParcelFileDescriptor? = synchronized(OPERATION_LOCK) {
        if (!hasPendingPayload()) return@synchronized null
        val descriptor = appContext.contentResolver.openFileDescriptor(
            FileProvider.getUriForFile(appContext, authority, payloadFile), "r"
        )
        if (descriptor != null) openedDescriptorIdentities[descriptor] = pendingIdentityGeneration
        descriptor
    }

    /** Captures which staged share was opened so a later import cannot be deleted on completion. */
    fun pendingIdentity(descriptor: ParcelFileDescriptor): Long = synchronized(OPERATION_LOCK) {
        openedDescriptorIdentities.remove(descriptor)
            ?: throw IllegalArgumentException("Descriptor was not opened from the pending share")
    }

    fun deletePendingIfIdentity(identity: Long): Boolean = synchronized(OPERATION_LOCK) {
        if (identity != pendingIdentityGeneration || !hasPendingPayload()) return@synchronized false
        atomicFile.delete()
        pendingIdentityGeneration++
        true
    }



    fun deletePending() = synchronized(OPERATION_LOCK) {
        atomicFile.delete()
    }

    fun hasPendingPayload(): Boolean = synchronized(OPERATION_LOCK) {
        try {
            atomicFile.openRead().use { }
            payloadFile.isFile
        } catch (_: IOException) {
            false
        }
    }

    fun pendingLastModified(): Long = synchronized(OPERATION_LOCK) {
        if (hasPendingPayload()) payloadFile.lastModified() else 0L
    }

    companion object {
        const val MAXIMUM_BYTES = 256L * 1024 * 1024
        const val MINIMUM_FREE_BYTES = 64L * 1024 * 1024
        private const val DIRECTORY_NAME = "mega_paste"
        private val OPERATION_LOCK = Any()
        private val openedDescriptorIdentities = WeakHashMap<ParcelFileDescriptor, Long>()
        private var pendingIdentityGeneration = 0L
        private const val PAYLOAD_NAME = "pending"
        private const val COPY_BUFFER_SIZE = 32 * 1024
        private const val TEXT_BUFFER_SIZE = 8 * 1024
        private val NEWLINE = byteArrayOf('\n'.code.toByte())
    }
}
