package com.karakept.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import com.karakept.app.ui.icons.AppIcons
import isDevBuild
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.ui.components.OidcSignInPane
import com.karakept.app.ui.components.SsoSignInOption
import com.karakept.app.ui.components.einkModalBorder
import com.karakept.app.ui.components.rememberJsonFilePicker

private const val STEP_WELCOME = 0
private const val STEP_PERMISSIONS = 1
private const val STEP_BACKGROUND_SYNC = 2
private const val STEP_SERVER = 3
private const val TOTAL_STEPS = 4

@Serializable
class OnboardingScreen : NavKey {
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinViewModel<OnboardingScreenModel>()

        var currentStep by remember { mutableStateOf(STEP_WELCOME) }
        var permissionGranted by remember { mutableStateOf(false) }
        var restoreMessage by remember { mutableStateOf<String?>(null) }
        var backgroundSyncEnabled by remember { mutableStateOf(false) }
        var backgroundSyncFrequency by remember { mutableStateOf(60) }
        var ssoServerUrl by remember { mutableStateOf<String?>(null) }

        val onFinish = {
            screenModel.completeOnboarding(backgroundSyncEnabled, backgroundSyncFrequency) {
                navigator.replaceAll(MainScreen)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Step indicator
                    StepIndicator(
                        currentStep = currentStep,
                        totalSteps = TOTAL_STEPS,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    )

                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally { it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it } + fadeOut())
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { step ->
                        when (step) {
                            STEP_WELCOME -> WelcomeStep(
                                screenModel = screenModel,
                                onRestoreSuccess = { message, serversRestored ->
                                    if (serversRestored) {
                                        // Servers were restored from backup — no need to show the
                                        // server connection step, complete onboarding immediately.
                                        onFinish()
                                    } else {
                                        restoreMessage = message
                                        currentStep = STEP_SERVER
                                    }
                                }
                            )
                            STEP_PERMISSIONS -> PermissionsStep(
                                permissionGranted = permissionGranted,
                                onPermissionResult = { granted -> permissionGranted = granted }
                            )
                            STEP_BACKGROUND_SYNC -> BackgroundSyncStep(
                                enabled = backgroundSyncEnabled,
                                onEnabledChange = { backgroundSyncEnabled = it },
                                frequencyMinutes = backgroundSyncFrequency,
                                onFrequencyChange = { backgroundSyncFrequency = it }
                            )
                            STEP_SERVER -> ServerConnectionStep(
                                screenModel = screenModel,
                                restoreMessage = restoreMessage,
                                onConnected = { onFinish() },
                                onSignInWithSso = { ssoServerUrl = it }
                            )
                        }
                    }

                    // Navigation buttons
                    OnboardingNavigationBar(
                        currentStep = currentStep,
                        totalSteps = TOTAL_STEPS,
                        canProceed = when (currentStep) {
                            STEP_PERMISSIONS -> !platformNeedsNotificationPermission() || permissionGranted
                            else -> true
                        },
                        onNext = {
                            if (currentStep < TOTAL_STEPS - 1) {
                                currentStep++
                            }
                        },
                        onBack = {
                            if (currentStep > 0) currentStep--
                        },
                        onSkip = {
                            if (currentStep < TOTAL_STEPS - 1) {
                                currentStep++
                            }
                        },
                        showSkip = currentStep == STEP_PERMISSIONS || currentStep == STEP_BACKGROUND_SYNC,
                        showNext = currentStep < STEP_SERVER,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
            }
            // Over the wizard rather than instead of it, so cancelling returns to the URL as typed.
            ssoServerUrl?.let { serverUrl ->
                Surface(modifier = Modifier.fillMaxSize()) {
                    OidcSignInPane(
                        serverUrl = serverUrl,
                        onApiKey = { apiKey ->
                            screenModel.addServer(serverUrl, apiKey) { onFinish() }
                        },
                        onCancel = { ssoServerUrl = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val isPast = index < currentStep
            val color = if (isActive || isPast) activeColor else inactiveColor
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = color, size = size)
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    screenModel: OnboardingScreenModel,
    onRestoreSuccess: (message: String, serversRestored: Boolean) -> Unit
) {
    var isRestoring by remember { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var pendingBackupContent by remember { mutableStateOf<String?>(null) }

    // Show PIN dialog when a backup file has been picked
    val content = pendingBackupContent
    if (content != null) {
        BackupPinEntryDialog(
            onConfirm = { pin ->
                pendingBackupContent = null
                isRestoring = true
                restoreError = null
                screenModel.importSettings(content, pin) { result ->
                    isRestoring = false
                    result.fold(
                        onSuccess = { (message, serversRestored) -> onRestoreSuccess(message, serversRestored) },
                        onFailure = { e -> restoreError = e.message ?: "Restore failed" }
                    )
                }
            },
            onDismiss = { pendingBackupContent = null }
        )
    }

    val pickFile = rememberJsonFilePicker { content ->
        if (content != null) {
            restoreError = null
            pendingBackupContent = content
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Icon(
                imageVector = AppIcons.Default.Bookmark,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            if (isDevBuild) {
                Text(
                    text = "DEV",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 2.dp)
                        .background(
                            color = Color(0xFFD32F2F),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Welcome to Karakept",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your personal bookmark manager, powered by Karakeep.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        FeatureRow(
            icon = AppIcons.Default.Bookmark,
            title = "Save bookmarks",
            description = "Save links, articles, and notes from anywhere."
        )
        Spacer(modifier = Modifier.height(12.dp))
        FeatureRow(
            icon = AppIcons.Default.Cloud,
            title = "Sync across devices",
            description = "Your bookmarks are synced with your Karakeep server."
        )
        Spacer(modifier = Modifier.height(12.dp))
        FeatureRow(
            icon = AppIcons.Default.Notifications,
            title = "Get notified",
            description = "Receive notifications when your bookmarks are processed."
        )
        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Already have a backup?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { pickFile() },
            enabled = !isRestoring,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isRestoring) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restoring…")
            } else {
                Text("Restore from backup")
            }
        }
        restoreError?.let { errorText ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val ONBOARDING_FREQUENCY_OPTIONS = listOf(
    15 to "Every 15 minutes",
    30 to "Every 30 minutes",
    60 to "Every hour",
    120 to "Every 2 hours",
    240 to "Every 4 hours",
    480 to "Every 8 hours"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundSyncStep(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    frequencyMinutes: Int,
    onFrequencyChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = AppIcons.Default.Sync,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Background Sync",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Keep your bookmarks up to date automatically. Karakept can sync in the background so your reading list is always fresh.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This is off by default and can be changed at any time in Settings.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable background sync",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }

        if (enabled) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Sync frequency",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            var expanded by remember { mutableStateOf(false) }
            val selectedLabel = ONBOARDING_FREQUENCY_OPTIONS.find { it.first == frequencyMinutes }?.second
                ?: "${frequencyMinutes}m"

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ONBOARDING_FREQUENCY_OPTIONS.forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onFrequencyChange(minutes)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsStep(
    permissionGranted: Boolean,
    onPermissionResult: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (permissionGranted) AppIcons.Default.CheckCircle else AppIcons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = if (permissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Enable Notifications",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Karakept can notify you when a bookmark has been processed and is ready to read.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (permissionGranted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Notifications enabled!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else if (platformNeedsNotificationPermission()) {
            NotificationPermissionButton(onPermissionResult = onPermissionResult)
        } else {
            // Desktop — no permission needed, auto-skip
            Text(
                text = "Notifications are available on this platform.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ServerConnectionStep(
    screenModel: OnboardingScreenModel,
    restoreMessage: String?,
    onConnected: () -> Unit,
    onSignInWithSso: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<Boolean?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Icon(
            imageVector = AppIcons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Connect to Your Server",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Karakept requires a Karakeep server. Enter your server URL and an API key to get started.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You can create an API key in your Karakeep server settings under Settings → API Keys.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (restoreMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = AppIcons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = restoreMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Spacer(modifier = Modifier.height(24.dp))
        }

        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                connectionStatus = null
            },
            label = { Text("Server URL") },
            placeholder = { Text("https://your-server.example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                connectionStatus = null
            },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) AppIcons.Filled.Visibility else AppIcons.Filled.VisibilityOff,
                        contentDescription = if (isPasswordVisible) "Hide API key" else "Show API key"
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (connectionStatus != null) {
            Text(
                text = if (connectionStatus == true) "Connection successful!" else "Connection failed. Check the URL and API key.",
                color = if (connectionStatus == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    isTesting = true
                    connectionStatus = null
                    screenModel.testConnection(url, apiKey) { success ->
                        isTesting = false
                        connectionStatus = success
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isTesting && !isConnecting && url.isNotBlank() && apiKey.isNotBlank()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Test")
                }
            }

            Button(
                onClick = {
                    isConnecting = true
                    screenModel.addServer(url, apiKey) {
                        isConnecting = false
                        onConnected()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isTesting && !isConnecting && url.isNotBlank() && apiKey.isNotBlank()
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Connect")
                }
            }
        }

        SsoSignInOption(
            enabled = !isTesting && !isConnecting && url.isNotBlank(),
            onClick = { onSignInWithSso(url.trim()) }
        )
    }
}

@Composable
private fun OnboardingNavigationBar(
    currentStep: Int,
    totalSteps: Int,
    canProceed: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    showSkip: Boolean,
    showNext: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentStep > 0) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
        } else {
            Spacer(modifier = Modifier.width(80.dp))
        }

        if (showSkip) {
            TextButton(onClick = onSkip) {
                Text("Skip")
            }
        } else {
            Spacer(modifier = Modifier.width(80.dp))
        }

        if (showNext) {
            Button(
                onClick = onNext,
                enabled = canProceed
            ) {
                Text(if (currentStep == 0) "Get Started" else "Next")
            }
        } else {
            // Last step — connect button is inline in the step
            Spacer(modifier = Modifier.width(80.dp))
        }
    }
}

/**
 * PIN entry dialog shown after the user picks a backup file during onboarding.
 * All backups are encrypted, so a PIN is always required.
 */
@Composable
private fun BackupPinEntryDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = einkModalBorder(AlertDialogDefaults.shape),
        title = { Text("Enter Backup PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter the PIN used when this backup was exported.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin) },
                enabled = pin.length in 4..6
            ) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
