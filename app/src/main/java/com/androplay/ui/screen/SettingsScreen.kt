package com.androplay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androplay.service.ReceiverSettings
import androidx.compose.ui.res.stringResource
import com.androplay.R
import com.androplay.ui.mirroringLabel
import com.androplay.ui.settingValueLabel
import com.androplay.ui.AppBackground
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.androplay.viewmodel.AirPlayViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AirPlayViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    // Starts on the row below the device name: focusing the text field opens the keyboard.
    val firstChoice = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstChoice.requestFocus() } }
    // Same look as the home screen: no app bar, a title with a button beside it.
    AppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    HomeButton(stringResource(R.string.action_back), onClick = onBack)
                }
            }
            // Connection settings change what senders see, so the receiver restarts for them;
            // playback settings apply live and are also in the quick menu during playback.
            item {
                SectionHeader(stringResource(R.string.section_connection), stringResource(R.string.section_connection_note))
                DeviceNameSetting(value = settings.deviceName) { name ->
                    viewModel.updateSettings { it.copy(deviceName = name) }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
            ChoiceSetting(
                stringResource(R.string.setting_resolution), settings.resolution, ReceiverSettings.RESOLUTIONS,
                modifier = Modifier.focusRequester(firstChoice)
            ) { viewModel.updateSettings { current -> current.copy(resolution = it) } }
        }
            item { ChoiceSetting(stringResource(R.string.setting_frame_rate), settings.frameRate, ReceiverSettings.FRAME_RATES) { viewModel.updateSettings { current -> current.copy(frameRate = it) } } }
            item {
                ChoiceSetting(stringResource(R.string.setting_codec), settings.videoCodec, ReceiverSettings.VIDEO_CODECS) {
                    viewModel.updateSettings { current -> current.copy(videoCodec = it) }
                }
            }
            item {
                // Auto only offers H.265 for 4K mirroring on a 4K screen with a hardware HEVC decoder.
                Text(
                    stringResource(R.string.setting_this_tv, mirroringLabel(viewModel.mirroringProfile(settings))),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            item {
                SwitchSetting(stringResource(R.string.setting_dlna), settings.dlnaEnabled) { enabled ->
                    viewModel.updateSettings { it.copy(dlnaEnabled = enabled) }
                }
            }
            item { AccessSetting(settings, viewModel) }
            item {
                ChoiceSetting(
                    stringResource(R.string.setting_takeover),
                    if (settings.allowTakeover) TAKEOVER_ALLOW else TAKEOVER_REFUSE,
                    listOf(TAKEOVER_REFUSE, TAKEOVER_ALLOW),
                    display = { stringResource(if (it == TAKEOVER_ALLOW) R.string.setting_takeover_allow else R.string.setting_takeover_refuse) }
                ) { choice -> viewModel.updateSettings { it.copy(allowTakeover = choice == TAKEOVER_ALLOW) } }
            }
            item {
                SectionHeader(stringResource(R.string.section_playback), stringResource(R.string.section_playback_note))
                SwitchSetting(stringResource(R.string.setting_stats), settings.showStats) { enabled ->
                    viewModel.updateSettings { it.copy(showStats = enabled) }
                }
            }
            item {
                SwitchSetting(stringResource(R.string.setting_lyrics), settings.showLyrics) { enabled ->
                    viewModel.updateSettings { it.copy(showLyrics = enabled) }
                }
                Text(stringResource(R.string.setting_lyrics_note), color = Color.Gray, fontSize = 14.sp)
            }
            item {
                ChoiceSetting(stringResource(R.string.setting_picture), settings.pictureMode, ReceiverSettings.PICTURE_MODES) {
                    viewModel.updateSettings { current -> current.copy(pictureMode = it) }
                }
            }
            item {
                SectionHeader(stringResource(R.string.section_system), null)
                SwitchSetting(stringResource(R.string.setting_start_on_boot), settings.startOnBoot) { enabled ->
                    viewModel.updateSettings { it.copy(startOnBoot = enabled) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, note: String?) {
    Spacer(modifier = Modifier.height(24.dp))
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
    note?.let { Text(it, color = Color.Gray, fontSize = 14.sp) }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun ChoiceSetting(
    label: String,
    value: String,
    choices: List<String>,
    /** How a stored value is shown, in the TV's language. */
    display: @Composable (String) -> String = { settingValueLabel(it) },
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    SettingLine(label) {
        // The menu is anchored to the button, so it opens beside the value on the right.
        Box {
            HomeButton("${display(value)}  ▾", modifier) { expanded = true }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(text = { Text(display(choice), fontSize = 18.sp) }, onClick = { onSelected(choice); expanded = false })
                }
            }
        }
    }
}

@Composable
fun DeviceNameSetting(label: String = stringResource(R.string.setting_device_name), value: String, isPassword: Boolean = false, onSaved: (String) -> Unit) {
    var editing by remember(value) { mutableStateOf(value) }
    OutlinedTextField(value = editing, onValueChange = { editing = it }, label = { Text(label) }, singleLine = true,
        visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (isPassword) androidx.compose.ui.text.input.KeyboardType.NumberPassword else androidx.compose.ui.text.input.KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = { TextButton(onClick = { onSaved(editing) }) { Text(stringResource(R.string.action_save)) } })
}

private const val ACCESS_OPEN = "Not required"
private const val ACCESS_PASSWORD = "Required"
private const val TAKEOVER_REFUSE = "Refuse it"
private const val TAKEOVER_ALLOW = "Let it take over"

/**
 * Casting with or without a password. "Required" only takes effect once a valid password
 * is saved, so choosing it can't lock everyone out by accident.
 */
@Composable
fun AccessSetting(settings: ReceiverSettings, viewModel: AirPlayViewModel) {
    var choosingPassword by remember { mutableStateOf(false) }
    ChoiceSetting(
        stringResource(R.string.setting_password),
        if (settings.requirePassword) ACCESS_PASSWORD else ACCESS_OPEN,
        listOf(ACCESS_OPEN, ACCESS_PASSWORD),
        display = { stringResource(if (it == ACCESS_PASSWORD) R.string.setting_password_on else R.string.setting_password_off) }
    ) { choice ->
        if (choice == ACCESS_OPEN) {
            choosingPassword = false
            viewModel.updateSettings { it.copy(requirePassword = false) }
        } else if (ReceiverSettings.isValidPin(settings.pin)) {
            viewModel.updateSettings { it.copy(requirePassword = true) }
        } else {
            choosingPassword = true  // enabled when a valid password is saved below
        }
    }
    if (settings.requirePassword || choosingPassword) {
        PinSetting(settings.pin) { pin ->
            choosingPassword = false
            viewModel.updateSettings { it.copy(pin = pin, requirePassword = true) }
        }
    }
}

/** The password senders must enter: at least [ReceiverSettings.MIN_PIN_LENGTH] digits. */
@Composable
fun PinSetting(value: String, onSaved: (String) -> Unit) {
    var editing by remember(value) { mutableStateOf(value) }
    val invalid = !ReceiverSettings.isValidPin(editing)
    OutlinedTextField(
        value = editing,
        onValueChange = { editing = it.filter(Char::isDigit) },
        label = { Text(stringResource(R.string.setting_password_field)) },
        supportingText = {
            Text(
                if (invalid) stringResource(R.string.setting_password_too_short, ReceiverSettings.MIN_PIN_LENGTH)
                else stringResource(R.string.setting_password_help)
            )
        },
        isError = invalid && editing.isNotEmpty(),
        singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
        ),
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = { TextButton(onClick = { onSaved(editing) }, enabled = !invalid) { Text(stringResource(R.string.action_save)) } }
    )
}

@Composable
fun SwitchSetting(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    SettingLine(label) {
        HomeButton(stringResource(if (checked) R.string.on else R.string.off), muted = !checked) { onChanged(!checked) }
    }
}

/** One setting: its name on the left, its control (a home-style button) on the right. */
@Composable
private fun SettingLine(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
        control()
    }
}
