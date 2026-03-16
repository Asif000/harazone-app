package com.harazone.util

actual fun generateUuid(): String = java.util.UUID.randomUUID().toString()
