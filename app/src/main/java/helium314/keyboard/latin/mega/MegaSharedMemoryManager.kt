package helium314.keyboard.latin.mega

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.util.AtomicFile
import android.system.Os
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.OutputStream

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
        if (hasPendingPayload()) appContext.contentResolver.openFileDescriptor(
            FileProvider.getUriForFile(appContext, authority, payloadFile), "r"
        ) else null
    }

    fun deletePending() = synchronized(OPERATION_LOCK) {
        atomicFile.delete()
    }

    /** Identifies the opened file so completion of an older paste cannot delete a newer share. */
    fun pendingIdentity(descriptor: ParcelFileDescriptor): Long =
        Os.fstat(descriptor.fileDescriptor).st_ino

    fun deletePendingIfIdentity(identity: Long): Boolean = synchronized(OPERATION_LOCK) {
        if (!hasPendingPayload()) return@synchronized false
        val currentIdentity = runCatching { Os.stat(payloadFile.path).st_ino }.getOrNull()
        if (currentIdentity != identity) return@synchronized false
        atomicFile.delete()
        true
    }

    private fun hasPendingPayload(): Boolean {
        return try {
            atomicFile.openRead().use { }
            payloadFile.isFile
        } catch (_: IOException) {
            false
        }
    }

    companion object {
        const val MAXIMUM_BYTES = 256L * 1024 * 1024
        const val MINIMUM_FREE_BYTES = 64L * 1024 * 1024
        private const val DIRECTORY_NAME = "mega_paste"
        private val OPERATION_LOCK = Any()
        private const val PAYLOAD_NAME = "pending"
    }
}
