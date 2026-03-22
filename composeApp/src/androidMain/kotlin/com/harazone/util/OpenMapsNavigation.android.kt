package com.harazone.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.lang.ref.WeakReference

/**
 * Holds a weak reference to the application context for platform utility functions.
 * Set from MainActivity.onCreate().
 */
object AppContextHolder {
    private var contextRef: WeakReference<Context>? = null

    fun set(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    fun get(): Context? = contextRef?.get()
}

actual fun openMapsNavigation(lat: Double, lng: Double, name: String): Boolean {
    val context = AppContextHolder.get() ?: return false
    val encodedName = Uri.encode(name)
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedName)")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
