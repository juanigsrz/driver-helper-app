package com.juanigsrz.driverhelper

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { HomeScreen() }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("idle") }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* user can retry */ }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* user can retry */ }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val svc = Intent(ctx, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ctx.startForegroundService(svc)
            status = "capture running"
        } else {
            status = "projection denied"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("driver-helper", style = MaterialTheme.typography.headlineMedium)
        Text("Status: $status")
        Text(
            "Backend: ${BuildConfig.BACKEND_URL}",
            style = MaterialTheme.typography.bodySmall,
        )

        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }) { Text("1. Grant notifications") }

        Button(onClick = {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }) { Text("2. Grant location") }

        Button(onClick = {
            ctx.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }) { Text("3. Grant usage access (Settings)") }

        Button(onClick = {
            val mpm = ctx.getSystemService<MediaProjectionManager>() ?: return@Button
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }) { Text("4. Start capture") }

        OutlinedButton(onClick = {
            ctx.stopService(Intent(ctx, CaptureService::class.java))
            status = "stopped"
        }) { Text("Stop capture") }
    }
}

@Suppress("unused")
private fun hasUsageStats(ctx: Context): Boolean {
    val aom = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = aom.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        ctx.packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}
