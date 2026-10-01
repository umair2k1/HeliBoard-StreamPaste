package helium314.keyboard.latin.mega

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.util.AtomicFile
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.OutputStream
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
        private val openedDescriptorIdentities = WeakHashMap<ParcelFileDescriptor, Long>()
        private var pendingIdentityGeneration = 0L
        private const val PAYLOAD_NAME = "pending"
    }
}
