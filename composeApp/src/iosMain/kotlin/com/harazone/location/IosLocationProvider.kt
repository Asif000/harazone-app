package com.harazone.location

import com.harazone.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
                suspendCancellableCoroutine { cont ->
                    val manager = CLLocationManager()
                    manager.desiredAccuracy = kCLLocationAccuracyBest
                    activeManager = manager

                    // On iOS 14+, assigning manager.delegate triggers locationManagerDidChangeAuthorization
                    // synchronously if status is already determined. This flag prevents double startUpdatingLocation().
                    var locationUpdateStarted = false

                    val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {

                        // iOS 14+ callback — fires immediately on delegate assignment if status already determined
                        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                            if (cont.isCompleted) return
                            when (manager.authorizationStatus) {
                                kCLAuthorizationStatusAuthorizedWhenInUse,
                                kCLAuthorizationStatusAuthorizedAlways -> {
                                    if (!locationUpdateStarted) {
                                        locationUpdateStarted = true
                                        manager.startUpdatingLocation()
                                    }
                                }
                                kCLAuthorizationStatusDenied,
                                kCLAuthorizationStatusRestricted -> {
                                    activeDelegate = null
                                    activeManager = null
                                    cont.resume(Result.failure(Exception("Location permission denied")))
                                }
                                else -> { /* notDetermined — wait for user to respond to dialog */ }
                            }
                        }

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
                    // This assignment may trigger locationManagerDidChangeAuthorization synchronously
                    // (iOS 14+ fires it immediately when status is already determined).
                    manager.delegate = delegate

                    // Secondary gate — runs after delegate assignment.
                    // If locationManagerDidChangeAuthorization already ran synchronously (authorized case),
                    // locationUpdateStarted will be true and the authorized branch is skipped.
                    if (!locationUpdateStarted) {
                        when (manager.authorizationStatus) {
                            kCLAuthorizationStatusAuthorizedWhenInUse,
                            kCLAuthorizationStatusAuthorizedAlways -> {
                                locationUpdateStarted = true
                                manager.startUpdatingLocation()
                            }
                            kCLAuthorizationStatusNotDetermined -> {
                                // Show permission dialog. locationManagerDidChangeAuthorization will
                                // call startUpdatingLocation() once user responds.
                                manager.requestWhenInUseAuthorization()
                            }
                            else -> {
                                if (!cont.isCompleted) {
                                    activeDelegate = null
                                    activeManager = null
                                    cont.resume(Result.failure(Exception("Location permission denied")))
                                }
                            }
                        }
                    }

                    cont.invokeOnCancellation {
                        manager.stopUpdatingLocation()
                        activeDelegate = null
                        activeManager = null
                    }
                }
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
}
