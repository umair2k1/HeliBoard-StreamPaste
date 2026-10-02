package helium314.keyboard.latin.streampaste

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

class ShareTargetReceiverActivity : Activity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedUris = intent.sharedUris()
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        if (sharedUris.isEmpty() && text == null) {
            reportFailure()
            return
        }
        activityScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val manager = StreamPasteMemoryManager(this@ShareTargetReceiverActivity)
                    if (sharedUris.isNotEmpty()) manager.stageUris(sharedUris)
                    else manager.stageText(text!!)
                }
            }
            if (result.isFailure) {
                reportFailure()
            } else {
                Toast.makeText(this@ShareTargetReceiverActivity, R.string.stream_paste_staged_success, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
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

}
