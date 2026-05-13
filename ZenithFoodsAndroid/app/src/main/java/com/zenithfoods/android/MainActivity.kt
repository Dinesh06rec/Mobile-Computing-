package com.zenithfoods.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val vm: ZenithViewModel by viewModels()

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        vm.onGoogleActivityResult(result.data)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        vm.onLocationPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.loadCatalogIfNeeded()

        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.isSignedIn) {
                if (!state.isSignedIn) return@LaunchedEffect
                val fine = Manifest.permission.ACCESS_FINE_LOCATION
                when (ContextCompat.checkSelfPermission(this@MainActivity, fine)) {
                    PackageManager.PERMISSION_GRANTED -> vm.ensureDeliverySocketAndLocation()
                    else -> locationPermissionLauncher.launch(fine)
                }
            }

            MaterialTheme(colorScheme = zenithColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ZenithRoot(
                        state = state,
                        vm = vm,
                        onGoogleSignIn = {
                            vm.beginGoogleSignIn()
                            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN,
                            )
                                .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                                .requestEmail()
                                .build()
                            val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(
                                this@MainActivity,
                                gso,
                            )
                            googleLauncher.launch(client.signInIntent)
                        },
                    )
                }
            }
        }
    }
}
