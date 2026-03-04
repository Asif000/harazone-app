package com.areadiscovery.location

import com.areadiscovery.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.darwin.NSObject
import kotlin.coroutines.resume

class IosLocationProvider : LocationProvider {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun getCurrentLocation(): Result<GpsCoordinates> =
        suspendCancellableCoroutine { cont ->
            val manager = CLLocationManager()
            manager.desiredAccuracy = kCLLocationAccuracyBest

            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(
                    manager: CLLocationManager,
                    didUpdateLocations: List<*>
                ) {
                    val location = didUpdateLocations.firstOrNull() as? CLLocation
                    if (location != null) {
                        manager.stopUpdatingLocation()
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
                    manager.stopUpdatingLocation()
                    AppLogger.e { "CLLocationManager failed: ${didFailWithError.localizedDescription}" }
                    cont.resume(Result.failure(Exception(didFailWithError.localizedDescription)))
                }
            }

            manager.delegate = delegate
            manager.requestWhenInUseAuthorization()
            manager.startUpdatingLocation()

            cont.invokeOnCancellation { manager.stopUpdatingLocation() }
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
