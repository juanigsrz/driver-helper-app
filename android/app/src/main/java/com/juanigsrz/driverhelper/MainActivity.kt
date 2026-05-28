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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
    val def = ScoreConfig.DEFAULT
    var status by remember { mutableStateOf("idle") }
    var costPerKm by remember {
        mutableStateOf(AppSettings.costPerKm(ctx)?.let { "%.0f".format(it) } ?: "")
    }
    var commissionPct by remember {
        mutableStateOf(
            AppSettings.platformCommission(ctx)?.let { "%.0f".format(it * 100) } ?: ""
        )
    }
    var minArsPerKm by remember {
        mutableStateOf(AppSettings.minArsPerKm(ctx)?.let { "%.0f".format(it) } ?: "")
    }
    var minArsPerHr by remember {
        mutableStateOf(AppSettings.minArsPerHr(ctx)?.let { "%.0f".format(it) } ?: "")
    }
    var deadheadPct by remember {
        mutableStateOf(
            AppSettings.maxDeadheadRatio(ctx)?.let { "%.0f".format(it * 100) } ?: ""
        )
    }
    var savedMsg by remember { mutableStateOf("") }

    val notifPermission = rememberLauncherForActivityResult(
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

    fun defLabel(base: String, default: Double, fmt: String): String =
        "$base (default: ${fmt.format(default)})"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("driver-helper", style = MaterialTheme.typography.headlineMedium)
        Text("Status: $status")
        Text(
            "Scoring runs on-device — no backend needed.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = costPerKm,
            onValueChange = { costPerKm = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text(defLabel("Cost per km (ARS)", def.costPerKm, "%.0f")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = commissionPct,
            onValueChange = { commissionPct = it.filter { c -> c.isDigit() || c == '.' } },
            label = {
                Text(defLabel("Platform commission %", def.platformCommission * 100, "%.0f"))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = minArsPerKm,
            onValueChange = { minArsPerKm = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text(defLabel("Min profit ARS/km", def.minArsPerKm, "%.0f")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = minArsPerHr,
            onValueChange = { minArsPerHr = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text(defLabel("Min profit ARS/hr", def.minArsPerHr, "%.0f")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = deadheadPct,
            onValueChange = { deadheadPct = it.filter { c -> c.isDigit() || c == '.' } },
            label = {
                Text(defLabel("Max deadhead %", def.maxDeadheadRatio * 100, "%.0f"))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            AppSettings.setCostPerKm(ctx, costPerKm.toDoubleOrNull())
            AppSettings.setPlatformCommission(ctx, commissionPct.toDoubleOrNull()?.div(100.0))
            AppSettings.setMinArsPerKm(ctx, minArsPerKm.toDoubleOrNull())
            AppSettings.setMinArsPerHr(ctx, minArsPerHr.toDoubleOrNull())
            AppSettings.setMaxDeadheadRatio(ctx, deadheadPct.toDoubleOrNull()?.div(100.0))
            savedMsg = "Saved"
        }) { Text("Save settings") }
        if (savedMsg.isNotEmpty()) {
            Text(savedMsg, style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider()

        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }) { Text("1. Grant notifications") }

        Button(onClick = {
            ctx.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }) { Text("2. Grant usage access (Settings)") }

        Button(onClick = {
            val mpm = ctx.getSystemService<MediaProjectionManager>() ?: return@Button
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }) { Text("3. Start capture") }

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
