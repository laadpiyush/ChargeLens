package com.chargelens.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargelens.app.BuildConfig
import com.chargelens.app.data.settings.AppSettings
import com.chargelens.app.data.settings.ChargingScreenMode
import com.chargelens.app.domain.model.CurrentSignMode
import com.chargelens.app.domain.model.CurrentUnit
import com.chargelens.app.domain.model.TemperatureUnit
import com.chargelens.app.ui.charging.ChargingActivity
import com.chargelens.app.ui.components.LabeledRow
import com.chargelens.app.ui.components.NavigationRow
import com.chargelens.app.ui.components.ScreenTitle
import com.chargelens.app.ui.components.SectionCard
import com.chargelens.app.ui.components.SwitchRow
import com.chargelens.app.ui.theme.LocalExtras
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onOpenDesign: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    // Re-check permission state whenever we come back from a system settings screen.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val notifGranted = remember(resumeTick) { Permissions.notificationsGranted(context) }
    val fsiGranted = remember(resumeTick) { Permissions.fullScreenIntentGranted(context) }
    val batteryExempt = remember(resumeTick) { Permissions.ignoringBatteryOptimizations(context) }
    val overlayGranted = remember(resumeTick) { Permissions.canDrawOverlays(context) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::export)
    }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    fun edit(t: (AppSettings) -> AppSettings) = viewModel.update(t)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { ScreenTitle("Settings", Modifier.padding(start = 4.dp)) }

            item {
                SectionCard("Appearance") {
                    NavigationRow("Design", "Accent colour, gauge, font, AMOLED black, tiles", onOpenDesign)
                }
            }

            item {
                SectionCard("Monitoring") {
                    SwitchRow("Background monitoring", s.monitoringEnabled, viewModel::setMonitoring,
                        "Ongoing notification, charge history and the charging screen trigger")
                    SwitchRow("Only while charging", s.monitorOnlyWhileCharging, { v -> edit { it.copy(monitorOnlyWhileCharging = v) } },
                        "No sampling or history on battery. Turn off to track battery drain in Usage.")
                    SwitchRow(
                        "Wait for charger in background",
                        s.waitForChargerInBackground,
                        { v -> edit { it.copy(waitForChargerInBackground = v) } },
                        if (s.waitForChargerInBackground) "A dormant listener runs on battery so plugging in opens the charging screen and always-on gauge by itself."
                        else "Off: nothing runs on battery. Open ChargeLens after plugging in to start monitoring for that charge; it stops when you unplug.",
                        enabled = s.monitorOnlyWhileCharging,
                    )
                    LabeledRow("Sampling interval", "How often values refresh while the screen is on")
                    ChipRow(listOf(1000L to "1 s", 2000L to "2 s", 5000L to "5 s"), s.sampleIntervalMs) { v -> edit { it.copy(sampleIntervalMs = v) } }
                }
            }

            item {
                SectionCard("Units") {
                    LabeledRow("Temperature")
                    Segmented(
                        listOf(TemperatureUnit.CELSIUS to "°C", TemperatureUnit.FAHRENHEIT to "°F"),
                        s.temperatureUnit,
                    ) { v -> edit { it.copy(temperatureUnit = v) } }
                }
            }

            item {
                SectionCard("Current reading") {
                    LabeledRow("Current sign", "Use \"Inverted\" if charging shows as negative on your phone")
                    RadioGroup(CurrentSignMode.entries.map { it to it.label }, s.currentSignMode) { v -> edit { it.copy(currentSignMode = v) } }
                    HorizontalDivider(color = LocalExtras.current.track, modifier = Modifier.padding(vertical = 4.dp))
                    LabeledRow("Current unit", "Some phones report mA instead of µA")
                    RadioGroup(CurrentUnit.entries.map { it to it.label }, s.currentUnit) { v -> edit { it.copy(currentUnit = v) } }
                }
            }

            item {
                SectionCard("Charge speed thresholds") {
                    val t = s.thresholds
                    ThresholdSlider("Slow below", t.slowBelowW, 1f..15f) { v -> edit { it.copy(thresholds = t.copy(slowBelowW = v, fastFromW = maxOf(t.fastFromW, v), superFastFromW = maxOf(t.superFastFromW, v))) } }
                    ThresholdSlider("Fast from", t.fastFromW, 5f..40f) { v -> edit { it.copy(thresholds = t.copy(fastFromW = v, slowBelowW = minOf(t.slowBelowW, v), superFastFromW = maxOf(t.superFastFromW, v))) } }
                    ThresholdSlider("Super fast from", t.superFastFromW, 15f..120f) { v -> edit { it.copy(thresholds = t.copy(superFastFromW = v, fastFromW = minOf(t.fastFromW, v), slowBelowW = minOf(t.slowBelowW, v))) } }
                }
            }

            item {
                SectionCard("Battery") {
                    var text by rememberSaveable(s.designCapacityOverrideMah) { mutableStateOf(s.designCapacityOverrideMah?.toString().orEmpty()) }
                    val detected = viewModel.detectedDesignCapacity
                    LabeledRow(
                        "Design capacity",
                        if (detected != null) "Detected from system: $detected mAh. Leave empty to use it." else "Not reported by this phone — enter it from the spec sheet.",
                    )
                    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { v -> text = v.filter(Char::isDigit).take(5) },
                            label = { Text("mAh") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { edit { it.copy(designCapacityOverrideMah = text.toIntOrNull()?.takeIf { v -> v in 500..20000 }) } }) {
                            Text("Save")
                        }
                    }
                    LabeledRow(
                        "Learned full capacity",
                        s.estimatedCapacityMah?.let { "${it.roundToInt()} mAh" } ?: "Still learning",
                    ) {
                        TextButton(onClick = viewModel::resetCapacity) { Text("Reset") }
                    }
                }
            }

            item {
                SectionCard("Charging screen") {
                    val cs = s.chargingScreen
                    fun editCs(t: (com.chargelens.app.data.settings.ChargingScreenSettings) -> com.chargelens.app.data.settings.ChargingScreenSettings) =
                        edit { it.copy(chargingScreen = t(it.chargingScreen)) }
                    SwitchRow("Show screen when charging", cs.enabled, { v -> editCs { it.copy(enabled = v) } },
                        "Opens the Details screen full-screen when a charger is connected")
                    SwitchRow("Always-on gauge while charging", cs.alwaysOnWhileCharging, { v -> editCs { it.copy(alwaysOnWhileCharging = v) } },
                        "When the screen turns off on the charger, show the clock and battery gauge, very dim, on black. Tap for the lock screen; press power to turn it off. Needs Full-screen alerts.")
                    LabeledRow("Mode")
                    ChargingScreenMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = cs.mode == mode, role = Role.RadioButton) { editCs { it.copy(mode = mode) } }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = cs.mode == mode, onClick = null, modifier = Modifier.padding(horizontal = 10.dp))
                            Column {
                                Text(mode.label)
                                Text(mode.description, style = MaterialTheme.typography.bodySmall, color = LocalExtras.current.muted)
                            }
                        }
                    }
                    if (cs.mode == ChargingScreenMode.SCREENSAVER) {
                        LabeledRow("Select ChargeLens as the screen saver", "Settings → Display → Screen saver, then \"When to start: while charging\"") {
                            TextButton(onClick = { Permissions.open(context, Permissions.screenSaverSettings()) }) { Text("Open") }
                        }
                    }
                    if (cs.mode == ChargingScreenMode.OVERLAY && !overlayGranted) {
                        LabeledRow("Overlay permission needed", "Allows ChargeLens to draw the charging screen over other apps. Without it, the charging display is used instead.") {
                            TextButton(onClick = { Permissions.open(context, Permissions.overlaySettings(context)) }) { Text("Grant") }
                        }
                    }
                    SwitchRow("Unlock when opened", cs.dismissKeyguard, { v -> editCs { it.copy(dismissKeyguard = v) } },
                        "Asks to dismiss the lock screen (PIN/biometric still required)")
                    SwitchRow("AMOLED mode", cs.amoled, { v -> editCs { it.copy(amoled = v) } }, "Pure black, lower brightness")
                    SwitchRow("Pixel shift", cs.pixelShift, { v -> editCs { it.copy(pixelShift = v) } }, "Moves content a few pixels every minute to avoid burn-in")
                    LabeledRow("Auto-dim", "Dims after inactivity; tap to wake")
                    ChipRow(listOf(0 to "Off", 15 to "15 s", 30 to "30 s", 60 to "1 min", 300 to "5 min"), cs.autoDimSeconds) { v -> editCs { it.copy(autoDimSeconds = v) } }
                    LabeledRow("Preview") {
                        TextButton(onClick = { context.startActivity(ChargingActivity.intent(context, preview = true)) }) { Text("Open") }
                    }
                }
            }

            item {
                SectionCard("Notification") {
                    val n = s.notification
                    SwitchRow("Show wattage", n.showWattage, { v -> edit { it.copy(notification = n.copy(showWattage = v)) } })
                    SwitchRow("Show temperature", n.showTemperature, { v -> edit { it.copy(notification = n.copy(showTemperature = v)) } })
                    LabeledRow("Update every")
                    ChipRow(listOf(2 to "2 s", 5 to "5 s", 10 to "10 s", 30 to "30 s"), n.updateIntervalSeconds) { v -> edit { it.copy(notification = n.copy(updateIntervalSeconds = v)) } }
                }
            }

            item {
                SectionCard("Permissions") {
                    PermissionRow("Notifications", "Needed for the live notification and charging screen", notifGranted) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else Permissions.open(context, Permissions.appNotificationSettings(context))
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        PermissionRow("Full-screen alerts", "Lets the charging screen open over the lock screen", fsiGranted) {
                            Permissions.open(context, Permissions.fullScreenIntentSettings(context))
                        }
                    }
                    PermissionRow("Unrestricted battery", "Stops Android from killing monitoring in the background", batteryExempt) {
                        Permissions.open(context, Permissions.requestIgnoreBatteryOptimizations(context))
                    }
                    PermissionRow("Display over other apps", "Optional: opens the charging screen while you use another app, and enables overlay mode", overlayGranted) {
                        Permissions.open(context, Permissions.overlaySettings(context))
                    }
                }
            }

            item {
                SectionCard("Data") {
                    LabeledRow("Export history", "Sessions and samples as CSV") {
                        TextButton(onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            exportLauncher.launch("chargelens-$stamp.csv")
                        }) { Text("Export") }
                    }
                    LabeledRow("Clear history") {
                        TextButton(onClick = { confirmClear = true }) { Text("Clear", color = LocalExtras.current.hot) }
                    }
                }
            }

            item {
                SectionCard("About") {
                    LabeledRow("ChargeLens ${BuildConfig.VERSION_NAME}", "Live battery and charging monitor. Values come from Android's BatteryManager; capacity and health are estimates.")
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("All charge and discharge sessions will be deleted.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clearHistory() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(
        Modifier.padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun <T> RadioGroup(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    options.forEach { (value, label) ->
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = value == selected, onClick = null, modifier = Modifier.padding(10.dp))
            Text(label)
        }
    }
}

@Composable
private fun ThresholdSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    LabeledRow(label, "${local.roundToInt()} W")
    Slider(
        value = local,
        onValueChange = { local = it.roundToInt().toFloat() },
        onValueChangeFinished = { onChange(local) },
        valueRange = range,
        modifier = Modifier.padding(horizontal = 18.dp),
    )
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean, onGrant: () -> Unit) {
    LabeledRow(title, subtitle) {
        if (granted) Text("Allowed", color = LocalExtras.current.accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp))
        else TextButton(onClick = onGrant) { Text("Grant") }
    }
}
