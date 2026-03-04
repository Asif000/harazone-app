package com.areadiscovery.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.areadiscovery.util.AppLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidLocationProvider(private val context: Context) : LocationProvider {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @Throws(SecurityException::class)
    override suspend fun getCurrentLocation(): Result<GpsCoordinates> =
        suspendCancellableCoroutine { cont ->
            try {
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            cont.resume(Result.success(GpsCoordinates(location.latitude, location.longitude)))
                        } else {
                            cont.resume(Result.failure(Exception("Location unavailable — null result from FusedLocationProvider")))
                        }
                    }
                    .addOnFailureListener { e ->
                        AppLogger.e(e) { "FusedLocationProvider failed" }
                        cont.resume(Result.failure(e))
                    }
            } catch (e: SecurityException) {
                AppLogger.e(e) { "Location permission not granted" }
                cont.resume(Result.failure(e))
            }
        }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
        try {
            if (!Geocoder.isPresent()) {
                return Result.failure(Exception("Geocoder service unavailable on this device"))
            }
            val geocoder = Geocoder(context)
            val areaName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        cont.resume(extractAreaName(addresses))
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    extractAreaName(addresses ?: emptyList())
                }
            }
            Result.success(areaName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun extractAreaName(addresses: List<android.location.Address>): String {
        val address = addresses.firstOrNull()
            ?: return "Unknown area"
        return listOfNotNull(
            address.subLocality,
            address.locality,
            address.adminArea
        ).joinToString(", ").ifBlank { "Unknown area" }
    }
}
