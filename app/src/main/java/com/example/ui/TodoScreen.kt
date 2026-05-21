package com.example.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TodoItem
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.foundation.verticalScroll
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(viewModel: TodoViewModel) {
    val isLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

    MyApplicationTheme(darkTheme = isDarkMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLocked) {
                    LockScreen(viewModel = viewModel)
                } else {
                    TodoDashboard(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun LockScreen(viewModel: TodoViewModel) {
    val context = LocalContext.current
    val masterPin by viewModel.masterPin.collectAsStateWithLifecycle()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Biometric Launcher
    val biometricPromptLauncher = remember {
        val activity = context as? FragmentActivity
        if (activity != null) {
            val executor = ContextCompat.getMainExecutor(context)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    viewModel.unlockApp()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Fall back to pin nicely
                }
            }
            BiometricPrompt(activity, executor, callback)
        } else null
    }

    // Trigger authenticating immediately if enabled
    LaunchedEffect(isBiometricEnabled) {
        if (isBiometricEnabled && biometricPromptLauncher != null) {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock To-Do List")
                .setSubtitle("Confirm biological credentials to access private notes")
                .setNegativeButtonText("Use PIN Fallback")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            try {
                biometricPromptLauncher.authenticate(promptInfo)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Identity visual banner
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "App Locked",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "To-Do List Private Vault",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter secure PIN or verify biometrics",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Secure PIN input field
        OutlinedTextField(
            value = enteredPin,
            onValueChange = {
                if (it.length <= 4) {
                    enteredPin = it
                    pinError = false
                    if (it == masterPin) {
                        viewModel.unlockApp()
                    }
                }
            },
            label = { Text("Secure PIN (Default: 1234)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (enteredPin == masterPin) {
                    viewModel.unlockApp()
                } else {
                    pinError = true
                    enteredPin = ""
                }
            }),
            singleLine = true,
            isError = pinError,
            modifier = Modifier
                .width(260.dp)
                .testTag("pin_field"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                errorBorderColor = MaterialTheme.colorScheme.error
            )
        )

        if (pinError) {
            Text(
                text = "Invalid Passcode. Please try again.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Button(
                onClick = {
                    if (enteredPin == masterPin) {
                        viewModel.unlockApp()
                    } else {
                        pinError = enteredPin != masterPin
                        if (pinError) enteredPin = ""
                    }
                },
                modifier = Modifier.testTag("unlock_button")
            ) {
                Text("Verify Unlock")
            }

            if (isBiometricEnabled && biometricPromptLauncher != null) {
                IconButton(
                    onClick = {
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock To-Do List")
                            .setSubtitle("Confirm biological credentials")
                            .setNegativeButtonText("Cancel")
                            .build()
                        try {
                            biometricPromptLauncher.authenticate(promptInfo)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Biometric error", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Launch Biometric Prompt",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoDashboard(viewModel: TodoViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    // Observe lists
    val items by viewModel.todoItems.collectAsStateWithLifecycle()
    val categories = listOf("All", "Inbox", "Work", "Home", "Projects")
    val selectedCat by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

    // Dialog & bottom sheets toggle states
    var showAddDialog by remember { mutableStateOf(false) }
    var showSyncSheet by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Hold selected item for long press options card
    var longPressedItem by remember { mutableStateOf<TodoItem?>(null) }
    var editingItem by remember { mutableStateOf<TodoItem?>(null) }

    // Voice recording permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showVoiceDialog = true
            viewModel.startSpeechToText(context)
        } else {
            Toast.makeText(context, "Microphone access is required for voice transcription.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Sleek fingerprint accent badge
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Sleek biometric auth badge",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Tasks",
                                fontWeight = FontWeight.Medium,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Secure cross-device workspace",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                actions = {
                    // Lock app manually
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.lockApp()
                        },
                        modifier = Modifier.testTag("action_lock")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Secure Vault",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    // Secure Device Sync Setup
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSyncSheet = true
                        },
                        modifier = Modifier.testTag("action_sync")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "SecureSync Configurations",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    // Preferences
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSettingsDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Workspace preferences",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    editingItem = null
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .size(56.dp)
                    .testTag("add_task_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Task",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF211F26), RoundedCornerShape(32.dp))
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp))
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Item 1: Tasks (Active)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.selectCategory("All")
                            }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF4A4458))
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Tasks Section",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Tasks",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Item 2: Calendar Overview
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showSyncSheet = true
                            }
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Calendar Sync Panel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Sync Panel",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Item 3: Settings
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showSettingsDialog = true
                            }
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Preferences Tab",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Preferences",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search + Speak Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search task list or notes...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .testTag("search_bar"),
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )

                // Speak to Input Button (Voice-To-Text)
                FilledIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("voice_input_button"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input Speech-to-Text"
                    )
                }
            }

            // Category Horizontal Selection Cards/Chips
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Category list header row inside lazy column to prevent scroll conflict
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                            .testTag("category_filter_row"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = selectedCat.equals(cat, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.selectCategory(cat)
                                },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (!isSelected) {
                                            val dotColor = when (cat.lowercase()) {
                                                "work" -> com.example.ui.theme.CatWorkDot
                                                "projects" -> com.example.ui.theme.CatProjectsDot
                                                "home" -> com.example.ui.theme.CatHomeDot
                                                else -> null
                                            }
                                            if (dotColor != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(dotColor, CircleShape)
                                                )
                                            }
                                        }
                                        Text(cat, fontWeight = FontWeight.Bold)
                                    }
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Done,
                                            contentDescription = "Filter selected",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = null,
                                modifier = Modifier.testTag("chip_$cat")
                            )
                        }
                    }
                }

                if (items.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.FactCheck,
                                contentDescription = "List empty",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Zero items on your plate!",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Create customizable notes, synchronize secure devices, or try typing with standard Voice.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 24.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    itemsIndexed(items) { index, item ->
                        var isPressed by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.96f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "Press ripple"
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .scale(scale)
                                .combinedClickable(
                                    onClick = {
                                        viewModel.toggleCompleted(item)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        longPressedItem = item
                                    }
                                )
                                .testTag("task_item_$index"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.isCompleted) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (item.isCompleted) 0.dp else 4.dp
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Status checkbox matching the Sleek design instructions
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (item.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .then(
                                            if (!item.isCompleted) {
                                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                            } else Modifier
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.toggleCompleted(item)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.isCompleted) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Task complete",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Task detail texts
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Urgent indicator styling
                                        if (item.isUrgent) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.tertiary)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "URGENT",
                                                    color = MaterialTheme.colorScheme.onTertiary,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Category tag label
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Label,
                                                contentDescription = "Category",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = item.category,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Sync icon indicator
                                        if (item.syncStatus == "pending") {
                                            Icon(
                                                imageVector = Icons.Default.CloudQueue,
                                                contentDescription = "Pending Sync",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.CloudDone,
                                                contentDescription = "Synced Securely",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = item.title,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.isCompleted) {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (item.notes.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.notes,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Reminders timeline dates
                                    if (item.reminderTime != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsActive,
                                                contentDescription = "Active Alarm",
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            val df = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                            Text(
                                                text = "Reminder: " + df.format(Date(item.reminderTime)),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Interactive Quick Options Button
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        longPressedItem = item
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Open task menus",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG: 3D TOUCH LONG PRESS ACTIONS PANEL ---
    longPressedItem?.let { item ->
        AlertDialog(
            onDismissRequest = { longPressedItem = null },
            title = {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Notes: " + (if (item.notes.isEmpty()) "None" else item.notes),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    HorizontalDivider()

                    // Edit Action Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                longPressedItem = null
                                editingItem = item
                                showAddDialog = true
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditCalendar,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Edit Task Details", fontWeight = FontWeight.Bold)
                    }

                    // Toggle Complete Action Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.toggleCompleted(item)
                                longPressedItem = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.isCompleted) Icons.Default.RemoveCircleOutline else Icons.Default.CheckCircle,
                            contentDescription = "Check",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(if (item.isCompleted) "Reopen Task" else "Mark Complete", fontWeight = FontWeight.Bold)
                    }

                    // Delete Task
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.deleteTodo(item)
                                longPressedItem = null
                                Toast.makeText(context, "Task moved to bin", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Delete Task Securely", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { longPressedItem = null }) {
                    Text("Dismiss Panel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // --- DIALOG: VOICE TO TEXT SPEECH-TO-TEXT ---
    if (showVoiceDialog) {
        val listening by viewModel.isVoiceListening.collectAsStateWithLifecycle()
        val voiceStatus by viewModel.voiceStatus.collectAsStateWithLifecycle()
        val voiceBuffer by viewModel.voiceTextFieldBuffer.collectAsStateWithLifecycle()

        Dialog(onDismissRequest = {
            viewModel.stopSpeechToText()
            showVoiceDialog = false
        }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Voice-to-Text Input",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pulse Mic icon based on listening
                    val pulseScale by animateFloatAsState(
                        targetValue = if (listening) 1.2f else 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ), label = "mic pulse"
                    )

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                if (listening) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (listening) viewModel.stopSpeechToText()
                                else viewModel.startSpeechToText(context)
                            }
                        ) {
                            Icon(
                                imageVector = if (listening) Icons.Default.MicNone else Icons.Default.Mic,
                                contentDescription = "Recording mic",
                                tint = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = voiceStatus,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Rich Text box showing transcribed speech
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (voiceBuffer.isEmpty()) "Speak task name clearly..." else voiceBuffer,
                            fontSize = 15.sp,
                            color = if (voiceBuffer.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Demo controls to simulate on web view easily!
                    Text(
                        text = "Or tap a template below for testing on web preview:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.simulateVoiceInput("Discuss team sync deliverables") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Discuss team sync", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { viewModel.simulateVoiceInput("Buy immediate general groceries") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Buy grocery notes", fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.stopSpeechToText()
                                showVoiceDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (voiceBuffer.isNotEmpty()) {
                                    showVoiceDialog = false
                                    viewModel.stopSpeechToText()
                                    // Open Add Dialog directly with voice textual input!
                                    editingItem = null
                                    showAddDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = voiceBuffer.isNotEmpty()
                        ) {
                            Text("Use Text")
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG: ADD OR EDIT TASK PANEL ---
    if (showAddDialog) {
        val voiceBuffer by viewModel.voiceTextFieldBuffer.collectAsStateWithLifecycle()
        
        var tTitle by remember { mutableStateOf(editingItem?.title ?: voiceBuffer) }
        var tNotes by remember { mutableStateOf(editingItem?.notes ?: "") }
        var tCategory by remember { mutableStateOf(editingItem?.category ?: "Inbox") }
        var tUrgent by remember { mutableStateOf(editingItem?.isUrgent ?: false) }
        var tDueDate by remember { mutableStateOf<Long?>(editingItem?.dueDate) }
        
        val tempReminder by viewModel.tempReminderTime.collectAsStateWithLifecycle()
        var tReminder by remember { mutableStateOf<Long?>(editingItem?.reminderTime ?: tempReminder) }

        val calendar = Calendar.getInstance()

        // Clear speech buffer after usage in add fields
        LaunchedEffect(voiceBuffer) {
            if (voiceBuffer.isNotEmpty()) {
                tTitle = voiceBuffer
            }
        }

        AlertDialog(
            onDismissRequest = {
                viewModel.updateTempReminder(null)
                showAddDialog = false
            },
            title = {
                Text(
                    text = if (editingItem == null) "Create New Task" else "Update Task Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Task title input
                    TextField(
                        value = tTitle,
                        onValueChange = { tTitle = it },
                        label = { Text("Task Heading") },
                        placeholder = { Text("What needs to be done?") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_title_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    // Task Notes
                    TextField(
                        value = tNotes,
                        onValueChange = { tNotes = it },
                        label = { Text("Additional Notes (Optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_notes_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    // Category dropdown row
                    Column {
                        Text(
                            text = "Category Organiser Tag",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Inbox", "Work", "Home", "Projects").forEach { cat ->
                                val isChosen = tCategory == cat
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { tCategory = cat },
                                    label = { Text(cat, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // Urgent highlight switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PriorityHigh,
                                contentDescription = "Urgent Option",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Column {
                                Text("Immediate Attention", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Pin item as critical priority level", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = tUrgent,
                            onCheckedChange = { tUrgent = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                                checkedTrackColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            modifier = Modifier.testTag("urgent_switch")
                        )
                    }

                    // Date & Customizable Reminders clock pickers
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Customizable Alert Reminder",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Date Picker button
                            Button(
                                onClick = {
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            calendar.set(Calendar.YEAR, y)
                                            calendar.set(Calendar.MONTH, m)
                                            calendar.set(Calendar.DAY_OF_MONTH, d)
                                            
                                            // Trigger time dialog right after date picker!
                                            TimePickerDialog(
                                                context,
                                                { _, hr, min ->
                                                    calendar.set(Calendar.HOUR_OF_DAY, hr)
                                                    calendar.set(Calendar.MINUTE, min)
                                                    calendar.set(Calendar.SECOND, 0)
                                                    tReminder = calendar.timeInMillis
                                                    viewModel.updateTempReminder(calendar.timeInMillis)
                                                },
                                                calendar.get(Calendar.HOUR_OF_DAY),
                                                calendar.get(Calendar.MINUTE),
                                                false
                                            ).show()
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Pick Reminder Time", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Set Alert", fontSize = 12.sp)
                            }

                            if (tReminder != null) {
                                Button(
                                    onClick = {
                                        tReminder = null
                                        viewModel.updateTempReminder(null)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.NotificationsOff, contentDescription = "Clear Reminder", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear Alert", fontSize = 12.sp)
                                }
                            }
                        }

                        if (tReminder != null) {
                            val sdf = SimpleDateFormat("EEEE, MMM d, h:mm a", Locale.getDefault())
                            Text(
                                text = "Configured: " + sdf.format(Date(tReminder!!)),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "Offline exact notification alarm",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tTitle.isNotEmpty()) {
                            viewModel.saveTodo(
                                id = editingItem?.id,
                                title = tTitle,
                                notes = tNotes,
                                category = tCategory,
                                isUrgent = tUrgent,
                                dueDate = tDueDate,
                                reminderTime = tReminder
                            )
                            viewModel.simulateVoiceInput("") // Reset speech
                            showAddDialog = false
                            Toast.makeText(context, "Task saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Task name cannot be empty.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("save_task_button")
                ) {
                    Text(if (editingItem == null) "Create" else "Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    viewModel.updateTempReminder(null)
                }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    // --- SECURE SYNC CONFIGURATION PANEL ---
    if (showSyncSheet) {
        val syncingState by viewModel.syncingState.collectAsStateWithLifecycle()
        val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
        val syncUrl by viewModel.syncServerUrl.collectAsStateWithLifecycle()
        val syncPasscode by viewModel.syncPasscode.collectAsStateWithLifecycle()
        val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
        val simMode by viewModel.syncSimulationMode.collectAsStateWithLifecycle()

        var fUrl by remember { mutableStateOf(syncUrl) }
        var fPasscode by remember { mutableStateOf(syncPasscode) }
        var fPairing by remember { mutableStateOf(pairingCode) }
        var fSimMode by remember { mutableStateOf(simMode) }

        Dialog(onDismissRequest = { showSyncSheet = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .shadow(12.dp, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .heightIn(max = 520.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Secure Cross-Device Sync",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showSyncSheet = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close settings")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Quick Switch Simulation vs Real Server
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Secure Sync Sandbox Match", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Runs clean client-side encryption simulation with simulated updates", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = fSimMode,
                                onCheckedChange = { fSimMode = it },
                                modifier = Modifier.testTag("simulation_switch")
                            )
                        }

                        // Encryption Passcode
                        OutlinedTextField(
                            value = fPasscode,
                            onValueChange = { fPasscode = it },
                            label = { Text("AES-128 Group Secret Passcode") },
                            placeholder = { Text("Pre-shared encryption passcode") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("group_passcode_field"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Pairing Key
                        OutlinedTextField(
                            value = fPairing,
                            onValueChange = { fPairing = it },
                            label = { Text("Secure Device Group Pairing Key") },
                            placeholder = { Text("Must be identical across all devices") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pairing_key_field"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Real Base URL if Simulation is False
                        if (!fSimMode) {
                            OutlinedTextField(
                                value = fUrl,
                                onValueChange = { fUrl = it },
                                label = { Text("Custom HTTPS Sync Hub URL") },
                                placeholder = { Text("https://my-sync-server.com/api") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_url_field"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.saveSyncSettings(
                                    url = fUrl,
                                    passcode = fPasscode,
                                    pairing = fPairing,
                                    isSimulated = fSimMode
                                )
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.runSecureSync()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sync_trigger_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = "Synchronise Now")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pair & Sync Now")
                        }

                        // Log Activity viewer to verify AES base64 transmission packet packets!
                        if (syncLogs.isNotEmpty()) {
                            Text(
                                text = "Client Cryptography & Transport Logs:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .padding(8.dp)
                            ) {
                                LazyColumn(
                                    reverseLayout = true
                                ) {
                                    items(syncLogs.reversed()) { log ->
                                        Text(
                                            text = log,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = Color(0xFF00FF9D),
                                            lineHeight = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    when (val sState = syncingState) {
                        is SyncStatusState.Syncing -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Cryptographic matching in progress...", fontSize = 13.sp)
                            }
                        }
                        is SyncStatusState.Success -> {
                            Text(
                                text = "Status: " + sState.msg,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        is SyncStatusState.Failure -> {
                            Text(
                                text = "Status Error: " + sState.error,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    // --- DIALOG: GENERAL SETTINGS (DARK MODE & BIOMETRICS) ---
    if (showSettingsDialog) {
        val masterPin by viewModel.masterPin.collectAsStateWithLifecycle()
        val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
        val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

        var pinFieldVal by remember { mutableStateOf(masterPin) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Workspace Preferences", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Dark theme option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Aesthetic Dark Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Deep gray space visual style", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { viewModel.setDarkMode(it) }
                        )
                    }

                    HorizontalDivider()

                    // Biometric Authentication locker toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Biometric Security", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Prompt biometric credentials on launch", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { viewModel.setBiometricEnabled(it) }
                        )
                    }

                    // Master Pin code setting
                    OutlinedTextField(
                        value = pinFieldVal,
                        onValueChange = {
                            if (it.length <= 4) {
                                pinFieldVal = it
                                viewModel.setMasterPin(it)
                            }
                        },
                        label = { Text("Backup Master PIN Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_setup_field")
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("Apply Settings")
                }
            }
        )
    }
}
