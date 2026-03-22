package com.harazone.util

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openMapsNavigation(lat: Double, lng: Double, name: String): Boolean {
    val urlString = "http://maps.apple.com/?daddr=$lat,$lng&dirflg=d"
    val url = NSURL(string = urlString) ?: return false
    return UIApplication.sharedApplication.openURL(url)
}
