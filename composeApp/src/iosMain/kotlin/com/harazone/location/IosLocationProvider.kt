package com.harazone.location

import com.harazone.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.darwin.NSObject
import kotlin.coroutines.resume

class IosLocationProvider : LocationProvider {

    // Strong references to prevent GC — CLLocationManager holds only a weak ref to its delegate
    private var activeDelegate: NSObject? = null
    private var activeManager: CLLocationManager? = null
    private val locationMutex = Mutex()

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun getCurrentLocation(): Result<GpsCoordinates> =
        locationMutex.withLock {
            withContext(Dispatchers.Main) {
                // Phase 1: Ensure permission — no timeout. On first launch the user sees the iOS
                // dialog; we wait however long that takes before starting the GPS fix.
                val authorized = ensurePermission()
                if (!authorized) return@withContext Result.failure(Exception("Location permission denied"))

                // Phase 2: Fetch location with its own timeout, independent of the permission wait.
                withTimeoutOrNull(LOCATION_FIX_TIMEOUT_MS) {
                    fetchLocation()
                } ?: Result.failure(Exception("Location fix timed out"))
            }
        }

    /**
     * Suspends until location permission is resolved. Returns true if granted, false if denied.
     * Never times out — the iOS permission dialog can stay on screen as long as the user needs.
     */
    private suspend fun ensurePermission(): Boolean =
        suspendCancellableCoroutine { cont ->
            val manager = CLLocationManager()
            activeManager = manager

            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                    if (cont.isCompleted) return
                    when (manager.authorizationStatus) {
                        kCLAuthorizationStatusAuthorizedWhenInUse,
                        kCLAuthorizationStatusAuthorizedAlways -> {
                            activeDelegate = null
                            activeManager = null
                            cont.resume(true)
                        }
                        kCLAuthorizationStatusDenied,
                        kCLAuthorizationStatusRestricted -> {
                            activeDelegate = null
                            activeManager = null
                            cont.resume(false)
                        }
                        else -> { /* notDetermined — wait for user response */ }
                    }
                }
            }

            activeDelegate = delegate
            // Assigning delegate triggers locationManagerDidChangeAuthorization synchronously on
            // iOS 14+ if status is already determined, resolving the coroutine immediately.
            manager.delegate = delegate

            // Secondary gate — in case the synchronous callback already resolved the coroutine.
            if (!cont.isCompleted) {
                when (manager.authorizationStatus) {
                    kCLAuthorizationStatusAuthorizedWhenInUse,
                    kCLAuthorizationStatusAuthorizedAlways -> {
                        activeDelegate = null
                        activeManager = null
                        cont.resume(true)
                    }
                    kCLAuthorizationStatusNotDetermined -> {
                        // Show permission dialog. locationManagerDidChangeAuthorization will fire
                        // once the user responds.
                        manager.requestWhenInUseAuthorization()
                    }
                    else -> {
                        activeDelegate = null
                        activeManager = null
                        cont.resume(false)
                    }
                }
            }

            cont.invokeOnCancellation {
                activeDelegate = null
                activeManager = null
            }
        }

    /**
     * Fetches a single GPS fix. Assumes permission is already granted.
     * Caller is responsible for applying a timeout.
     */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun fetchLocation(): Result<GpsCoordinates> =
        suspendCancellableCoroutine { cont ->
            val manager = CLLocationManager()
            manager.desiredAccuracy = kCLLocationAccuracyBest
            activeManager = manager

            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(
                    manager: CLLocationManager,
                    didUpdateLocations: List<*>
                ) {
                    if (cont.isCompleted) return
                    val location = didUpdateLocations.firstOrNull() as? CLLocation
                    if (location != null) {
                        manager.stopUpdatingLocation()
                        activeDelegate = null
                        activeManager = null
                        cont.resume(Result.success(
                            GpsCoordinates(
                                latitude = location.coordinate.useContents { latitude },
                                longitude = location.coordinate.useContents { longitude }
                            )
                        ))
                    }
                }

                override fun locationManager(
                    manager: CLLocationManager,
                    didFailWithError: platform.Foundation.NSError
                ) {
                    if (cont.isCompleted) return
                    manager.stopUpdatingLocation()
                    activeDelegate = null
                    activeManager = null
                    AppLogger.e { "CLLocationManager failed: ${didFailWithError.localizedDescription}" }
                    cont.resume(Result.failure(Exception(didFailWithError.localizedDescription)))
                }
            }

            activeDelegate = delegate
            manager.delegate = delegate
            manager.startUpdatingLocation()

            cont.invokeOnCancellation {
                manager.stopUpdatingLocation()
                activeDelegate = null
                activeManager = null
            }
        }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
        suspendCancellableCoroutine { cont ->
            val geocoder = CLGeocoder()
            val location = CLLocation(latitude, longitude)
            geocoder.reverseGeocodeLocation(location) { placemarks, error ->
                if (error != null) {
                    AppLogger.e { "CLGeocoder failed: ${error.localizedDescription}" }
                    cont.resume(Result.failure(Exception(error.localizedDescription)))
                } else {
                    val placemark = (placemarks?.firstOrNull() as? platform.CoreLocation.CLPlacemark)
                    val areaName = listOfNotNull(
                        placemark?.subLocality,
                        placemark?.locality,
                        placemark?.administrativeArea
                    ).joinToString(", ").ifBlank { "Unknown area" }
                    cont.resume(Result.success(areaName))
                }
            }
        }

    companion object {
        // Timeout applied only to the GPS fix phase, after permission is already granted.
        private const val LOCATION_FIX_TIMEOUT_MS = 10_000L
    }
}
