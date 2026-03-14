package com.harazone.feedback

interface FeedbackReporter {
    fun captureScreenshot(): ByteArray?
    fun launchEmail(screenshot: ByteArray?, description: String, deviceInfo: String, logs: String)
}
