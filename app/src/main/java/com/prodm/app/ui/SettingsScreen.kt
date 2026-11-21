package com.prodm.app.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.prodm.app.R
import com.prodm.app.data.AppSettings
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(s: AppSettings, vm: MainViewModel, modifier: Modifier = Modifier) {
    val lang = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
    var showTime by remember { mutableStateOf(false) }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard(R.string.sec_appearance, Icons.Rounded.Palette) {
            Label(R.string.theme)
            ChipRow(
                listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark).map { stringResource(it) },
                s.theme
            ) { i -> vm.updateSettings { it.copy(theme = i) } }
            SwitchRow(R.string.amoled, R.string.amoled_sub, s.amoled) { v -> vm.updateSettings { it.copy(amoled = v) } }
            SwitchRow(R.string.dynamic, R.string.dynamic_sub, s.dynamic) { v -> vm.updateSettings { it.copy(dynamic = v) } }
            Label(R.string.language)
            ChipRow(
                listOf(R.string.lang_system, R.string.lang_en, R.string.lang_ar).map { stringResource(it) },
                when (lang) { "en" -> 1; "ar" -> 2; else -> 0 }
            ) { i -> vm.setLanguage(when (i) { 1 -> "en"; 2 -> "ar"; else -> "" }) }
        }

        SectionCard(R.string.sec_downloads, Icons.Rounded.CloudDownload) {
            SliderRow(R.string.max_concurrent, null, s.maxConcurrent, 1, 6) { v -> vm.updateSettings { it.copy(maxConcurrent = v) } }
            SliderRow(R.string.connections, R.string.connections_sub, s.connections, 1, 16) { v -> vm.updateSettings { it.copy(connections = v) } }
            Label(R.string.speed_limit)
            val limits = listOf(0, 256, 512, 1024, 2048, 5120)
            val unlimited = stringResource(R.string.unlimited)
            ChipRow(
                limits.map { if (it == 0) unlimited else if (it >= 1024) "${it / 1024} MB/s" else "$it KB/s" },
                limits.indexOf(s.speedLimitKb).coerceAtLeast(0)
            ) { i -> vm.updateSettings { it.copy(speedLimitKb = limits[i]) } }
            SwitchRow(R.string.wifi_only, R.string.wifi_only_sub, s.wifiOnly) { v -> vm.updateSettings { it.copy(wifiOnly = v) } }
            SwitchRow(R.string.clipboard_watch, R.string.clipboard_watch_sub, s.clipboard) { v -> vm.updateSettings { it.copy(clipboard = v) } }
        }

        SectionCard(R.string.sec_schedule, Icons.Rounded.Schedule) {
            SwitchRow(R.string.schedule_enable, R.string.schedule_enable_sub, s.scheduleOn) { v -> vm.updateSettings { it.copy(scheduleOn = v) } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.schedule_time), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { showTime = true }) {
                    Text(
                        String.format(Locale.US, "%02d:%02d", s.schedHour, s.schedMinute),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        SectionCard(R.string.sec_about, Icons.Rounded.Info) {
            Text(
                stringResource(R.string.about_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showTime) {
        val st = rememberTimePickerState(s.schedHour, s.schedMinute, is24Hour = false)
        Dialog(onDismissRequest = { showTime = false }) {
            Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = st)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = {
                            vm.updateSettings { it.copy(schedHour = st.hour, schedMinute = st.minute) }
                            showTime = false
                        }) { Text(stringResource(R.string.ok)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: Int, icon: ImageVector, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun Label(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SwitchRow(title: Int, sub: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (sub != null) {
                Text(
                    stringResource(sub), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(title: Int, sub: Int?, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    var tmp by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${tmp.roundToInt()}", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
            )
        }
        if (sub != null) {
            Text(
                stringResource(sub), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = tmp,
            onValueChange = { tmp = it },
            onValueChangeFinished = { onChange(tmp.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = max - min - 1
        )
        Spacer(Modifier.height(2.dp))
    }
}
