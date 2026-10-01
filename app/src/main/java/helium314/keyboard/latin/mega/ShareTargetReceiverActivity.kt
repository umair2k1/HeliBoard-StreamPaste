package helium314.keyboard.latin.mega

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import helium314.keyboard.latin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.io.OutputStreamWriter

class ShareTargetReceiverActivity : Activity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedUris = intent.sharedUris()
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        if (sharedUris.isEmpty() && text == null) {
            reportFailure()
            return
        }
        activityScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    MegaSharedMemoryManager(this@ShareTargetReceiverActivity).stage { output ->
                        if (sharedUris.isNotEmpty()) writeUris(sharedUris, output)
                        else writeText(text!!, output)
                    }
                }
            }
            if (result.isFailure) reportFailure()
            finish()
        }
    }

    private fun writeUris(uris: List<Uri>, output: OutputStream) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        uris.forEachIndexed { index, uri ->
            if (index > 0) output.write(NEWLINE)
            val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open shared item")
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

    private fun reportFailure() {
        Toast.makeText(this, R.string.stream_paste_import_error, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private fun Intent.sharedUris(): List<Uri> = if (action == Intent.ACTION_SEND_MULTIPLE) {
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    } else {
        getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::listOf).orEmpty()
    }

    private companion object {
        const val COPY_BUFFER_SIZE = 32 * 1024
        const val TEXT_BUFFER_SIZE = 8 * 1024
        val NEWLINE = byteArrayOf('\n'.code.toByte())
    }
}
