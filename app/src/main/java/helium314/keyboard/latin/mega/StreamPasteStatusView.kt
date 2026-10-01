package helium314.keyboard.latin.mega

import android.content.Context
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import helium314.keyboard.latin.R
import kotlin.math.min

class StreamPasteStatusView(context: Context, onCancel: () -> Unit) : LinearLayout(context) {
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val status = TextView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padding = (8 * resources.displayMetrics.density).toInt()
        setPadding(padding, 0, padding, 0)
        progress.max = 100
        addView(progress, LayoutParams((72 * resources.displayMetrics.density).toInt(), LayoutParams.WRAP_CONTENT))
        status.setSingleLine()
        addView(status, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = padding
        })
        addView(Button(context).apply {
            setText(R.string.stream_paste_cancel)
            setOnClickListener { onCancel() }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
            marginStart = padding
        })
        updateProgress(0, 0)
    }

    fun updateProgress(committedUtf8Bytes: Long, totalBytes: Long) {
        val percent = if (totalBytes <= 0L) 100 else min(100L, committedUtf8Bytes * 100 / totalBytes).toInt()
        progress.progress = percent
        status.text = context.getString(
            R.string.stream_paste_progress,
            percent,
            committedUtf8Bytes / BYTES_PER_KILOBYTE,
            totalBytes / BYTES_PER_KILOBYTE,
        )
    }

    private companion object {
        const val BYTES_PER_KILOBYTE = 1024L
    }
}
