@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.harazone.feedback

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.MessageUI.MFMailComposeResult
import platform.MessageUI.MFMailComposeViewController
import platform.MessageUI.MFMailComposeViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.posix.memcpy

class IosFeedbackReporter : FeedbackReporter {
    private var activeMailDelegate: MailDelegate? = null

    override fun captureScreenshot(): ByteArray? {
        val keyWindow = getKeyWindow() ?: return null
        val renderer = UIGraphicsImageRenderer(bounds = keyWindow.bounds)
        val image = renderer.imageWithActions { ctx ->
            keyWindow.layer.renderInContext(ctx!!.CGContext)
        }
        val nsData = UIImageJPEGRepresentation(image, 0.8) ?: return null
        return nsData.toByteArray()
    }

    override fun launchEmail(screenshot: ByteArray?, description: String, deviceInfo: String, logs: String) {
        val body = "--- Device Info ---\n$deviceInfo\n\n--- Description ---\n${description.ifBlank { "(none)" }}\n\n--- Logs ---\n$logs"

        if (!MFMailComposeViewController.canSendMail()) {
            val truncatedBody = body.take(2000)
            val url = NSURL(string = "mailto:saturnplasma@gmail.com?subject=AreaDiscovery%20Feedback&body=${
                truncatedBody.replace(" ", "%20").replace("\n", "%0A")
            }")
            if (url != null) {
                UIApplication.sharedApplication.openURL(url)
            }
            return
        }

        val vc = MFMailComposeViewController()
        val delegate = MailDelegate { activeMailDelegate = null }
        activeMailDelegate = delegate
        vc.mailComposeDelegate = delegate
        vc.setToRecipients(listOf("saturnplasma@gmail.com"))
        vc.setSubject("AreaDiscovery Feedback")
        vc.setMessageBody(body, isHTML = false)

        if (screenshot != null) {
            val nsData = screenshot.toNSData()
            vc.addAttachmentData(nsData, mimeType = "image/jpeg", fileName = "screenshot.jpg")
        }

        val rootVc = getKeyWindow()?.rootViewController ?: return
        var topVc: UIViewController = rootVc
        while (topVc.presentedViewController != null) {
            topVc = topVc.presentedViewController!!
        }
        topVc.presentViewController(vc, animated = true, completion = null)
    }

    private fun getKeyWindow(): UIWindow? {
        return UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.map { it as UIWindow } }
            .firstOrNull { it.isKeyWindow() }
    }
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return bytes
}

private fun ByteArray.toNSData(): NSData {
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

private class MailDelegate(
    private val onDone: () -> Unit,
) : NSObject(), MFMailComposeViewControllerDelegateProtocol {
    override fun mailComposeController(
        controller: MFMailComposeViewController,
        didFinishWithResult: MFMailComposeResult,
        error: platform.Foundation.NSError?,
    ) {
        controller.dismissViewControllerAnimated(true, completion = null)
        onDone()
    }
}
