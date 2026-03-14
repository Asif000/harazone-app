package com.harazone.feedback

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.widget.Toast
import androidx.core.content.FileProvider
import com.harazone.util.AppLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AndroidFeedbackReporter(private val context: Context) : FeedbackReporter {

    override fun captureScreenshot(): ByteArray? {
        val activity = context as? Activity
        if (activity == null) {
            AppLogger.w("captureScreenshot: context is not an Activity — skipping")
            return null
        }
        val window = activity.window ?: return null
        val decorView = window.decorView
        if (decorView.width == 0 || decorView.height == 0) return null

        val bmp = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var copyResult = PixelCopy.ERROR_UNKNOWN

        // PixelCopy callback must go to a thread OTHER than the calling thread
        // when the calling thread is blocked with a latch. Use a dedicated HandlerThread.
        val handlerThread = HandlerThread("PixelCopyThread").apply { start() }
        try {
            PixelCopy.request(window, bmp, { result ->
                copyResult = result
                latch.countDown()
            }, Handler(handlerThread.looper))
            latch.await(2, TimeUnit.SECONDS)
        } finally {
            handlerThread.quitSafely()
        }

        if (copyResult != PixelCopy.SUCCESS) {
            AppLogger.w("PixelCopy failed with result $copyResult")
            return null
        }
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 90, baos)
        return baos.toByteArray()
    }

    override fun launchEmail(screenshot: ByteArray?, description: String, deviceInfo: String, logs: String) {
        val body = "--- Device Info ---\n$deviceInfo\n\n--- Description ---\n${description.ifBlank { "(none)" }}\n\n--- Logs ---\n$logs"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("saturnplasma@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "AreaDiscovery Feedback")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        if (screenshot != null) {
            val file = File(context.cacheDir, "feedback_screenshot.png")
            file.writeBytes(screenshot)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.clipData = ClipData.newUri(context.contentResolver, "Screenshot", uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Send Feedback"))
        } catch (_: ActivityNotFoundException) {
            AppLogger.w("No email client available")
            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }
}
