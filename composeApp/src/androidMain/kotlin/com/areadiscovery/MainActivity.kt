package com.areadiscovery

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import org.koin.android.ext.koin.androidContext

class MainActivity : ComponentActivity() {

    private var permissionResolved by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionResolved = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = !isDebug

        val alreadyGranted = LOCATION_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (alreadyGranted) {
            permissionResolved = true
        } else {
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }

        setContent {
            if (permissionResolved) {
                App(
                    platformConfig = { androidContext(this@MainActivity) },
                    onNavigateToMaps = { lat, lon, name ->
                        val uri = Uri.parse("geo:$lat,$lon?q=${Uri.encode(name)}")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                            true
                        } else {
                            false
                        }
                    },
                )
            }
        }
    }

    companion object {
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(platformConfig = {})
}