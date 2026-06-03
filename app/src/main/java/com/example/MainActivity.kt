package com.example

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import java.util.TimeZone
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.location.LocationServices
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        // ── Automatic Relaunch on Crash ──
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CrashHandler", "Crash detected in thread ${thread.name}. Automatically relaunching app...", throwable)
            try {
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                applicationContext.startActivity(intent)
            } catch (e: Exception) {
                Log.e("CrashHandler", "Failed to relaunch automatically", e)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            java.lang.System.exit(10)
        }

        // ── Keep Screen Awake Configuration controller loop ──
        lifecycle.currentStateFlow.let {
            // Collect stayAwake setting dynamically and apply window flag
            runOnUiThread {
                try {
                    val isAwake = viewModel.stayAwake.value
                    if (isAwake) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                } catch (e: Exception) {
                    // Ignore window errors
                }
            }
        }

        setContent {
            val stayAwakeEnabled by viewModel.stayAwake.collectAsStateWithLifecycle()
            
            // Sync current stay awake status to native window flags dynamically
            LaunchedEffect(stayAwakeEnabled) {
                try {
                    if (stayAwakeEnabled) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                } catch (e: Exception) {
                    // Gracefully ignore frame issues
                }
            }

            val themeKey by viewModel.theme.collectAsStateWithLifecycle()
            val currentTheme = PrayerTheme.fromId(themeKey)

            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = currentTheme.primary,
                    secondary = currentTheme.secondary,
                    background = currentTheme.background,
                    surface = currentTheme.surface
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = currentTheme.background
                ) {
                    MainAppLayout(viewModel = viewModel, currentTheme = currentTheme, activity = this)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        try {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } catch (e: Exception) {
            // Ignore system UI errors
        }
    }
}

// Sparkle Star layout parameter specifications
data class StarSpec(val x: Float, val y: Float, val size: Float)

@Composable
fun AmbientStarsBackground(currentTheme: PrayerTheme) {
    if (!currentTheme.hasAmbientStars) return

    val stars = remember {
        List(55) {
            StarSpec(
                x = (1..99).random() / 100f,
                y = (1..99).random() / 100f,
                size = (12..35).random() / 10f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "StarsTwinkle")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3500
                0.15f at 0
                0.9f at 1750
                0.15f at 3500
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "AlphaTwinkle"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEach { star ->
            // Use specific variance factor for organic spatial depth
            val starAlpha = (alphaAnim + (star.x * 0.15f)).coerceIn(0.1f, 1.0f)
            drawCircle(
                color = Color.White.copy(alpha = starAlpha),
                radius = star.size,
                center = Offset(
                    x = star.x * size.width,
                    y = star.y * size.height
                )
            )
        }
    }
}

@Composable
fun MainAppLayout(
    viewModel: MainViewModel,
    currentTheme: PrayerTheme,
    activity: Activity
) {
    val context = LocalContext.current
    var isSettingsOpen by remember { mutableStateOf(false) }
    val appOrientation by viewModel.appOrientation.collectAsStateWithLifecycle()

    LaunchedEffect(appOrientation) {
        when (appOrientation) {
            "landscape" -> {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            "portrait" -> {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            else -> {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Request Location Permission dynamic hook on startup
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            fetchAndSetDeviceLocation(activity, viewModel)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            fetchAndSetDeviceLocation(activity, viewModel)
        } else {
            // Request permissions dynamically
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // ── Twinkle Background and Ambient Layers ──
    Box(modifier = Modifier.fillMaxSize()) {
        // Star particle systems
        AmbientStarsBackground(currentTheme = currentTheme)

        // Radial glowing pattern of dynamic color palettes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val brush = Brush.radialGradient(
                        colors = listOf(
                            currentTheme.primary.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.2f, size.height * 0.2f),
                        radius = size.width * 0.7f
                    )
                    drawRect(brush = brush)
                    
                    val brushBottom = Brush.radialGradient(
                        colors = listOf(
                            currentTheme.secondary.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.8f, size.height * 0.8f),
                        radius = size.width * 0.7f
                    )
                    drawRect(brush = brushBottom)
                }
        )

        // ── Responsive Layout Framework ──
        BoxWithConstraints(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val isLandscape = when (appOrientation) {
                "landscape" -> true
                "portrait" -> false
                else -> maxWidth > maxHeight
            }

            if (isLandscape) {
                // Two-column Split Landscape layout for Tablets
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Left Column: Interactive Sidebar control panel
                    SidebarColumn(
                        viewModel = viewModel,
                        currentTheme = currentTheme,
                        onOpenSettings = { isSettingsOpen = true },
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .border(width = 1.dp, color = currentTheme.primary.copy(alpha = 0.15f))
                    )

                    // Right Column: Active Prayer Grids and Media systems
                    MainContentColumn(
                        viewModel = viewModel,
                        currentTheme = currentTheme,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(20.dp)
                    )
                }
            } else {
                // Stacked Scroll Layout for Mobile Portrait
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Top header block with brand banner
                    BrandHeader(viewModel = viewModel, currentTheme = currentTheme, onOpenSettings = { isSettingsOpen = true })
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Time & Date section on the left side, Current Weather section on the right side
                    TimeAndWeatherRow(viewModel = viewModel, currentTheme = currentTheme)

                    Spacer(modifier = Modifier.height(14.dp))

                    // Prayer Schedules view
                    Text(
                        text = "Today's Schedules",
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 20.sp,
                        color = currentTheme.primaryVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    MobileSchedulesVertical(viewModel = viewModel, currentTheme = currentTheme)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Media systems
                    Text(
                        text = "Dynamic Worship Companion",
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 18.sp,
                        color = currentTheme.primaryVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    InlineAudioCompanionWidget(viewModel = viewModel, currentTheme = currentTheme)
                }
            }
        }

        // ── Custom Popup Settings Panel Dialog ──
        if (isSettingsOpen) {
            AmbientSettingsPopup(
                viewModel = viewModel,
                currentTheme = currentTheme,
                onDismiss = { isSettingsOpen = false }
            )
        }

        // ── Optional Auto Update Alert Dialog ──
        val updateAvailable by viewModel.updateAvailable.collectAsStateWithLifecycle()
        val latestVersionName by viewModel.latestVersionName.collectAsStateWithLifecycle()
        val latestVersionDescription by viewModel.latestVersionDescription.collectAsStateWithLifecycle()
        val latestApkUrl by viewModel.latestApkUrl.collectAsStateWithLifecycle()
        val latestReleasePageUrl by viewModel.latestReleasePageUrl.collectAsStateWithLifecycle()

        val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsStateWithLifecycle()
        val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
        val downloadedApkFile by viewModel.downloadedApkFile.collectAsStateWithLifecycle()

        LaunchedEffect(downloadedApkFile) {
            val file = downloadedApkFile
            if (file != null) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        if (!context.packageManager.canRequestPackageInstalls()) {
                            val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(settingsIntent)
                            android.widget.Toast.makeText(context, "Please allow 'Install unknown apps' and download again.", android.widget.Toast.LENGTH_LONG).show()
                            viewModel.clearDownloadedApkFile()
                            return@LaunchedEffect
                        }
                    }

                    val authority = "${context.packageName}.fileprovider"
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(installIntent)
                } catch (e: Exception) {
                    Log.e("DirectInstall", "Failed to start package installer", e)
                    android.widget.Toast.makeText(context, "Failed to start installer: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    viewModel.clearDownloadedApkFile()
                }
            }
        }

        var dismissUpdatePrompt by remember { mutableStateOf(false) }

        if (updateAvailable && !dismissUpdatePrompt) {
            Dialog(
                onDismissRequest = { if (!isDownloadingUpdate) dismissUpdatePrompt = true },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = currentTheme.surface),
                    border = BorderStroke(1.dp, currentTheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(currentTheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDownloadingUpdate) Icons.Default.Refresh else Icons.Default.Info,
                                contentDescription = null,
                                tint = currentTheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = if (isDownloadingUpdate) "Downloading Update..." else "New Update Available!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentTheme.textOnBg
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        if (isDownloadingUpdate) {
                            val progressPercent = (downloadProgress * 100).toInt()
                            Text(
                                text = "Downloading update from GitHub directly. The installer will open automatically.\n\nProgress: $progressPercent%",
                                fontSize = 12.sp,
                                color = currentTheme.textSub,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            LinearProgressIndicator(
                                progress = downloadProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = currentTheme.primary,
                                trackColor = currentTheme.primary.copy(alpha = 0.15f)
                            )
                        } else {
                            Text(
                                text = "A new version ($latestVersionName) was detected on GitHub.\n\nUpdate notes:\n$latestVersionDescription",
                                fontSize = 12.sp,
                                color = currentTheme.textSub,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { dismissUpdatePrompt = true },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, currentTheme.primary.copy(alpha = 0.3f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = currentTheme.primary)
                                ) {
                                    Text("Later", fontSize = 12.sp)
                                }
                                
                                Button(
                                    onClick = {
                                        val apkUrl = latestApkUrl
                                        if (apkUrl.isNotEmpty()) {
                                            viewModel.downloadApkDirectly(apkUrl)
                                        } else {
                                            val downloadUrl = latestReleasePageUrl
                                            if (downloadUrl.isNotEmpty()) {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    // ignore fallback failures
                                                }
                                                dismissUpdatePrompt = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = currentTheme.primary,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(
                                        text = if (latestApkUrl.isNotEmpty()) "Download & Install" else "Update Page", 
                                        fontSize = 12.sp, 
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Tablet Landscape elements ──

@Composable
fun SidebarColumn(
    viewModel: MainViewModel,
    currentTheme: PrayerTheme,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locationText by viewModel.locationName.collectAsStateWithLifecycle()
    val weatherText by viewModel.weatherText.collectAsStateWithLifecycle()
    val azanOn by viewModel.azanOn.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .background(currentTheme.surface.copy(alpha = 0.85f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Identity Brand Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "☽ ",
                fontSize = 22.sp,
                color = currentTheme.primaryVariant,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Local Prayer Times",
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                color = currentTheme.textOnBg,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Big Digital Clock Clock with dynamic ticker
        LiveAwesomeClockWidget(currentTheme = currentTheme)

        Spacer(modifier = Modifier.height(20.dp))

        // Muslim Hijri & Gregorian Calendar Box
        HijriDateBanner(viewModel = viewModel, currentTheme = currentTheme)

        Spacer(modifier = Modifier.height(20.dp))

        // Active highlighted current prayer widget
        ActivePrayerSection(viewModel = viewModel, currentTheme = currentTheme)

        Spacer(modifier = Modifier.weight(1f))

        // Climate indicator block
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, currentTheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = currentTheme.surface.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Coords",
                        tint = currentTheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = locationText,
                        fontSize = 13.sp,
                        color = currentTheme.textOnBg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🌡",
                        fontSize = 13.sp,
                        color = currentTheme.textSub,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = weatherText,
                        fontSize = 12.sp,
                        color = currentTheme.textOnBg,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Sidebar custom control triggers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { viewModel.toggleAzan(!azanOn) },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(currentTheme.surface)
                    .border(width = 0.8.dp, color = currentTheme.primary.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = if (azanOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                    contentDescription = "Muted trigger",
                    tint = if (azanOn) currentTheme.primary else Color(0xFFFF5252)
                )
            }

            IconButton(
                onClick = { 
                    viewModel.updateSchedules()
                    viewModel.fetchWeatherDetails()
                },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(currentTheme.surface)
                    .border(width = 0.8.dp, color = currentTheme.primary.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync details",
                    tint = currentTheme.textOnBg
                )
            }

            IconButton(
                onClick = { onOpenSettings() },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(currentTheme.surface)
                    .border(width = 0.8.dp, color = currentTheme.primary.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Config details",
                    tint = currentTheme.primary
                )
            }
        }
    }
}

@Composable
fun MainContentColumn(
    viewModel: MainViewModel,
    currentTheme: PrayerTheme,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // Today schedule heading
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Schedules",
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = currentTheme.primaryVariant,
                fontStyle = FontStyle.Italic
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Week Calendar strip selector
        CalendarWeekStripWidget(viewModel = viewModel, currentTheme = currentTheme)

        Spacer(modifier = Modifier.height(16.dp))

        // Main Prayers grids (4 Column, 2 row split)
        Box(modifier = Modifier.weight(1f)) {
            PrayersLandscapeGridView(viewModel = viewModel, currentTheme = currentTheme)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large audio control media shell bar
        InlineAudioCompanionWidget(viewModel = viewModel, currentTheme = currentTheme)
    }
}

// ── Layout Element Helper Composables ──

@Composable
fun BrandHeader(
    viewModel: MainViewModel,
    currentTheme: PrayerTheme,
    onOpenSettings: () -> Unit
) {
    val azanOn by viewModel.azanOn.collectAsStateWithLifecycle()
    val locationText by viewModel.locationName.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "☽ ",
                    fontSize = 24.sp,
                    color = currentTheme.primaryVariant,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Local Prayer Times",
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = currentTheme.textOnBg
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = currentTheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = locationText,
                    fontSize = 12.sp,
                    color = currentTheme.textSub
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(
                onClick = { viewModel.toggleAzan(!azanOn) },
                colors = IconButtonDefaults.iconButtonColors(containerColor = currentTheme.surface)
            ) {
                Icon(
                    imageVector = if (azanOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                    contentDescription = null,
                    tint = if (azanOn) currentTheme.primary else Color(0xFFFF5252)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                colors = IconButtonDefaults.iconButtonColors(containerColor = currentTheme.surface)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = currentTheme.primary
                )
            }
        }
    }
}

@Composable
fun HijriDateBanner(viewModel: MainViewModel, currentTheme: PrayerTheme) {
    val gregText by viewModel.gregorianText.collectAsStateWithLifecycle()
    val hijriText by viewModel.hijriText.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = currentTheme.primary.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, currentTheme.primary.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = gregText,
                fontSize = 12.sp,
                color = currentTheme.textSub,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hijriText,
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
                color = currentTheme.textOnBg,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
fun TimeAndWeatherRow(viewModel: MainViewModel, currentTheme: PrayerTheme) {
    val gregText by viewModel.gregorianText.collectAsStateWithLifecycle()
    val hijriText by viewModel.hijriText.collectAsStateWithLifecycle()
    val weatherText by viewModel.weatherText.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Card: Time & Date
        Card(
            modifier = Modifier.weight(1.1f),
            colors = CardDefaults.cardColors(containerColor = currentTheme.surface.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, currentTheme.primary.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                // Live ticking digital clock widget
                LiveAwesomeClockWidgetLeft(currentTheme = currentTheme)
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = gregText,
                    fontSize = 11.sp,
                    color = currentTheme.textSub,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = hijriText,
                    fontFamily = FontFamily.Serif,
                    fontSize = 13.sp,
                    color = currentTheme.textOnBg,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Right Card: Weather section
        Card(
            modifier = Modifier.weight(0.9f),
            colors = CardDefaults.cardColors(containerColor = currentTheme.surface.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, currentTheme.primary.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(currentTheme.primary.copy(alpha = 0.08f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🌤 Weather",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = currentTheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = weatherText,
                    fontSize = 12.sp,
                    color = currentTheme.textOnBg,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun LiveAwesomeClockWidgetLeft(currentTheme: PrayerTheme) {
    var time by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            time = LocalTime.now()
        }
    }

    val hr = time.format(DateTimeFormatter.ofPattern("hh"))
    val min = time.format(DateTimeFormatter.ofPattern("mm"))
    val sec = time.format(DateTimeFormatter.ofPattern("ss"))
    val ap = time.format(DateTimeFormatter.ofPattern("a")).uppercase(Locale.US)

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = "$hr:$min",
            fontFamily = FontFamily.Serif,
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = currentTheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(
                text = ap,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = currentTheme.textOnBg
            )
            Text(
                text = ":$sec",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = currentTheme.textSub
            )
        }
    }
}

@Composable
fun MobileClockContainer(viewModel: MainViewModel, currentTheme: PrayerTheme) {
    val weatherText by viewModel.weatherText.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = currentTheme.surface.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LiveAwesomeClockWidget(currentTheme = currentTheme)
            
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = weatherText,
                fontSize = 13.sp,
                color = currentTheme.primaryVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// Visual Clock tick scheduler
@Composable
fun LiveAwesomeClockWidget(currentTheme: PrayerTheme) {
    var time by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            time = LocalTime.now()
        }
    }

    val hr = time.format(DateTimeFormatter.ofPattern("hh"))
    val min = time.format(DateTimeFormatter.ofPattern("mm"))
    val sec = time.format(DateTimeFormatter.ofPattern("ss"))
    val ap = time.format(DateTimeFormatter.ofPattern("a")).uppercase(Locale.US)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            // Main Hour-Minute Digits
            Text(
                text = "$hr:$min",
                fontFamily = FontFamily.Serif,
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                color = currentTheme.primaryVariant,
                letterSpacing = 1.sp
            )
            // Seconds indicator
            Text(
                text = sec,
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                color = currentTheme.textOnBg.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            // AM / PM
            Text(
                text = ap,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = currentTheme.textOnBg,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Text(
            text = "Time Zone: " + TimeZone.getDefault().id.split("/").last().replace("_", " "),
            fontSize = 11.sp,
            color = currentTheme.textSub,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun ActivePrayerSection(viewModel: MainViewModel, currentTheme: PrayerTheme) {
    val active by viewModel.currentPrayer.collectAsStateWithLifecycle()
    val countdown by viewModel.nextPrayerCountdown.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = currentTheme.primary.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, currentTheme.primary.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Dynamic flashing dot
                var dotVisible by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(700)
                        dotVisible = !dotVisible
                    }
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(if (dotVisible) 1.0f else 0.2f)
                        .background(currentTheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CURRENT PRAYER",
                    fontSize = 10.sp,
                    color = currentTheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = active?.name ?: "Sunrise / Off-schedule",
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                color = currentTheme.textOnBg,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(1.dp))

            Text(
                text = active?.let { formatLocalDateTimeTo12H(it.time) } ?: "—",
                fontSize = 13.sp,
                color = currentTheme.textSub
            )

            Divider(color = currentTheme.primary.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 10.dp))

            Text(
                text = "NEXT COUNTDOWN",
                fontSize = 9.sp,
                color = currentTheme.textMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = countdown,
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                color = currentTheme.secondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CalendarWeekStripWidget(viewModel: MainViewModel, currentTheme: PrayerTheme) {
    val selected by viewModel.selectedDate.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    // Generate specific start/end around Sunday to Saturday
    val daysList = remember {
        val start = today.minusDays(today.dayOfWeek.value.toLong() % 7)
        List(7) { idx -> start.plusDays(idx.toLong()) }
    }

    Row(
        modifier = Modifier.fillMaxWidth().background(currentTheme.surface.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        daysList.forEach { date ->
            val isSel = date == selected
            val isToday = date == today
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isSel -> currentTheme.primary.copy(alpha = 0.18f)
                            isToday -> currentTheme.surface
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSel) currentTheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { viewModel.changeSelectedDate(date) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEE")).uppercase(Locale.US),
                        fontSize = 9.sp,
                        color = if (isSel) currentTheme.textOnBg else currentTheme.textMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        color = if (isSel) currentTheme.primary else currentTheme.textSub,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
fun PrayersLandscapeGridView(viewModel: MainViewModel, currentTheme: PrayerTheme) {
    val prayersList by viewModel.prayers.collectAsStateWithLifecycle()
    val textSizePreference by viewModel.prayerTimeTextSize.collectAsStateWithLifecycle()

    if (prayersList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = currentTheme.primary)
        }
        return
    }

    val now = ZonedDateTime.now()

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(prayersList) { prayer ->
            // Calculate Active / Now highlights matching Javascript rules
            val idx = prayersList.indexOf(prayer)
            val next = if (idx + 1 < prayersList.size) prayersList[idx + 1] else null
            val isNow = now.isAfter(prayer.time) && (next == null || now.isBefore(next.time))
            val isDone = !isNow && now.isAfter(prayer.time)

            PrayerCardItem(
                prayer = prayer,
                isNow = isNow,
                isDone = isDone,
                currentTheme = currentTheme,
                textSizePreference = textSizePreference,
                modifier = Modifier.fillMaxHeight()
            )
        }
    }
}

@Composable
fun MobileSchedulesVertical(viewModel: MainViewModel, currentTheme: PrayerTheme) {
    val prayersList by viewModel.prayers.collectAsStateWithLifecycle()
    val textSizePreference by viewModel.prayerTimeTextSize.collectAsStateWithLifecycle()

    if (prayersList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = currentTheme.primary)
        }
        return
    }

    val now = ZonedDateTime.now()
    val targetKeys = listOf("Imsak", "Fajr", "Sunrise", "Ishraq", "Dhuhr", "Asr", "Maghrib", "Isha")
    val finalPrayers = prayersList.filter { it.key in targetKeys }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Render 2-column layout (4 rows of 2 columns for Imsak, Fajr, Sunrise, Ishraq, Dhuhr, Asr, Maghrib, Isha)
        for (rowIndex in 0 until 4) {
            val firstIdx = rowIndex * 2
            val secondIdx = rowIndex * 2 + 1
            if (firstIdx < finalPrayers.size) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        val p = finalPrayers[firstIdx]
                        val idx = prayersList.indexOf(p)
                        val next = if (idx + 1 < prayersList.size) prayersList[idx + 1] else null
                        val isNow = now.isAfter(p.time) && (next == null || now.isBefore(next.time))
                        val isDone = !isNow && now.isAfter(p.time)
                        PrayerCardItem(
                            prayer = p,
                            isNow = isNow,
                            isDone = isDone,
                            currentTheme = currentTheme,
                            textSizePreference = textSizePreference,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (secondIdx < finalPrayers.size) {
                            val p = finalPrayers[secondIdx]
                            val idx = prayersList.indexOf(p)
                            val next = if (idx + 1 < prayersList.size) prayersList[idx + 1] else null
                            val isNow = now.isAfter(p.time) && (next == null || now.isBefore(next.time))
                            val isDone = !isNow && now.isAfter(p.time)
                            PrayerCardItem(
                                prayer = p,
                                isNow = isNow,
                                isDone = isDone,
                                currentTheme = currentTheme,
                                textSizePreference = textSizePreference,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Spacer(modifier = Modifier)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrayerCardItem(
    prayer: PrayerTime,
    isNow: Boolean,
    isDone: Boolean,
    currentTheme: PrayerTheme,
    textSizePreference: String = "large",
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    isNow -> currentTheme.primary.copy(alpha = 0.12f)
                    else -> currentTheme.surface.copy(alpha = if (isDone) 0.35f else 0.7f)
                }
            )
            .border(
                width = 1.dp,
                color = when {
                    isNow -> currentTheme.primary.copy(alpha = 0.5f)
                    else -> currentTheme.primary.copy(alpha = 0.08f)
                },
                shape = shape
            )
            .padding(14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = prayer.name,
                    fontSize = 11.sp,
                    color = if (isNow) currentTheme.primary else currentTheme.textMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                // Inline beautiful badges
                if (isNow) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(currentTheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                } else if (isDone) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = currentTheme.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time block
            val parts = prayer.ts.split(":")
            val rawH = parts[0].toInt()
            val mString = parts[1]
            val dispH = if (rawH % 12 == 0) 12 else rawH % 12
            val ap = if (rawH >= 12) "PM" else "AM"

            val initialSize = when (textSizePreference) {
                "normal" -> 32.sp
                "large" -> 42.sp
                "extra_large" -> 52.sp
                "huge" -> 64.sp
                else -> 42.sp
            }
            val amPmSize = when (textSizePreference) {
                "normal" -> 10.sp
                "large" -> 12.sp
                "extra_large" -> 14.sp
                "huge" -> 16.sp
                else -> 12.sp
            }

            var sizeState by remember(textSizePreference) { mutableStateOf(initialSize) }
            var readyToDraw by remember(textSizePreference) { mutableStateOf(false) }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%d:%s", dispH, mString),
                    fontFamily = FontFamily.Serif,
                    fontSize = sizeState,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    onTextLayout = { textLayoutResult ->
                        if (textLayoutResult.hasVisualOverflow && sizeState.value > 16f) {
                            sizeState = (sizeState.value * 0.9f).sp
                        } else {
                            readyToDraw = true
                        }
                    },
                    modifier = Modifier.drawWithContent {
                        if (readyToDraw) drawContent()
                    },
                    color = if (isNow) currentTheme.primaryVariant else currentTheme.textOnBg
                )
                Text(
                    text = " $ap",
                    fontSize = amPmSize,
                    fontWeight = FontWeight.Bold,
                    color = if (isNow) currentTheme.primary else currentTheme.textSub,
                    modifier = Modifier.padding(bottom = (sizeState.value * 0.15f).coerceAtMost(10f).dp)
                )
            }
        }
    }
}

// ── Immersive Dual Tab Media Player Widget Panel ──

@Composable
fun InlineAudioCompanionWidget(
    viewModel: MainViewModel,
    currentTheme: PrayerTheme
) {
    var selectedTab by remember { mutableStateOf("quran") } // "quran" or "nasheed"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = currentTheme.surface.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, currentTheme.primary.copy(alpha = 0.12f))
    ) {
        Column {
            // Media selector tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(currentTheme.primary.copy(alpha = 0.05f))
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selectedTab == "quran") currentTheme.surface else Color.Transparent)
                        .clickable { selectedTab = "quran" }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = if (selectedTab == "quran") currentTheme.primary else currentTheme.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Holy Quran",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == "quran") currentTheme.textOnBg else currentTheme.textSub
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selectedTab == "nasheed") currentTheme.surface else Color.Transparent)
                        .clickable { selectedTab = "nasheed" }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (selectedTab == "nasheed") currentTheme.secondary else currentTheme.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Nasheeds",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == "nasheed") currentTheme.textOnBg else currentTheme.textSub
                        )
                    }
                }
            }

            // Tab contents
            Crossfade(targetState = selectedTab, label = "TabSwitch") { tab ->
                if (tab == "quran") {
                    QuranPlayerView(viewModel = viewModel, currentTheme = currentTheme)
                } else {
                    NasheedPlayerView(viewModel = viewModel, currentTheme = currentTheme)
                }
            }
        }
    }
}

@Composable
fun QuranPlayerView(viewModel: MainViewModel, currentTheme: PrayerTheme) {
    val surahs = viewModel.surahsList
    val selectedSurahIdx by viewModel.quranSurahIndex.collectAsStateWithLifecycle()
    val playing by viewModel.quranIsPlaying.collectAsStateWithLifecycle()
    val status by viewModel.quranStatus.collectAsStateWithLifecycle()
    val progress by viewModel.quranProgress.collectAsStateWithLifecycle()
    val volume by viewModel.quranVolume.collectAsStateWithLifecycle()

    var dropdownOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Surah icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(currentTheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📖", fontSize = 16.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Text Info / Selector dialog spinner
            Column(
                modifier = Modifier.weight(1f).clickable { dropdownOpen = true }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Surah " + surahs.getOrElse(selectedSurahIdx - 1) { "Al-Fatiha" },
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.textOnBg
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Drop",
                        tint = currentTheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "Mishari Al-Afasi · $status",
                    fontSize = 10.sp,
                    color = currentTheme.textSub
                )
            }

            // Controllers
            IconButton(onClick = { viewModel.playQuranPrev() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = currentTheme.textOnBg)
            }

            IconButton(
                onClick = { viewModel.toggleQuranPlay() },
                colors = IconButtonDefaults.iconButtonColors(containerColor = currentTheme.primary)
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White
                )
            }

            IconButton(onClick = { viewModel.playQuranNext() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = currentTheme.textOnBg)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress scrubbing and Volume sliders
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "⏳", fontSize = 11.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Slider(
                value = progress,
                onValueChange = { viewModel.seekQuran(it) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    activeTrackColor = currentTheme.primary,
                    inactiveTrackColor = currentTheme.primary.copy(alpha = 0.15f),
                    thumbColor = currentTheme.primary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "🔊", fontSize = 11.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Slider(
                value = volume,
                onValueChange = { viewModel.setQuranVolume(it) },
                modifier = Modifier.width(80.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = currentTheme.textOnBg,
                    thumbColor = currentTheme.textOnBg
                )
            )
        }

        // Surah selection selector Dropdown Menu
        if (dropdownOpen) {
            Dialog(onDismissRequest = { dropdownOpen = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f),
                    colors = CardColors(containerColor = currentTheme.surface, contentColor = currentTheme.textOnBg, disabledContainerColor = currentTheme.surface, disabledContentColor = currentTheme.textSub)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Select Holy Surah",
                            fontFamily = FontFamily.Serif,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentTheme.primaryVariant,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Divider(color = currentTheme.primary.copy(alpha = 0.15f))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(surahs) { surah ->
                                val sIndex = surahs.indexOf(surah) + 1
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (sIndex == selectedSurahIdx) currentTheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                        .clickable {
                                            viewModel.playQuranSurah(sIndex)
                                            dropdownOpen = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%03d", sIndex),
                                        fontSize = 12.sp,
                                        color = currentTheme.primary,
                                        modifier = Modifier.width(36.dp)
                                    )
                                    Text(
                                        text = surah,
                                        fontSize = 14.sp,
                                        color = currentTheme.textOnBg,
                                        fontWeight = if (sIndex == selectedSurahIdx) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NasheedPlayerView(viewModel: MainViewModel, currentTheme: PrayerTheme) {
    val tracks by viewModel.nasheedTracks.collectAsStateWithLifecycle()
    val activeIdx by viewModel.nasheedIndex.collectAsStateWithLifecycle()
    val playing by viewModel.nasheedIsPlaying.collectAsStateWithLifecycle()
    val status by viewModel.nasheedStatus.collectAsStateWithLifecycle()
    val progress by viewModel.nasheedProgress.collectAsStateWithLifecycle()
    val volume by viewModel.nasheedVolume.collectAsStateWithLifecycle()

    val currentTrack = tracks.getOrNull(activeIdx)

    Column(
        modifier = Modifier.padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(currentTheme.secondary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🎵", fontSize = 16.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = currentTrack?.title ?: "No Track Selected",
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = currentTheme.textOnBg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Acapella Vocals · $status",
                    fontSize = 10.sp,
                    color = currentTheme.textSub
                )
            }

            IconButton(onClick = { viewModel.playNasheedPrev() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = currentTheme.textOnBg)
            }

            IconButton(
                onClick = { viewModel.toggleNasheedPlay() },
                colors = IconButtonDefaults.iconButtonColors(containerColor = currentTheme.secondary)
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White
                )
            }

            IconButton(onClick = { viewModel.playNasheedNext() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = currentTheme.textOnBg)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "⏳", fontSize = 11.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Slider(
                value = progress,
                onValueChange = { viewModel.seekNasheed(it) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    activeTrackColor = currentTheme.secondary,
                    inactiveTrackColor = currentTheme.secondary.copy(alpha = 0.15f),
                    thumbColor = currentTheme.secondary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "🔊", fontSize = 11.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Slider(
                value = volume,
                onValueChange = { viewModel.setNasheedVolume(it) },
                modifier = Modifier.width(80.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = currentTheme.textOnBg,
                    thumbColor = currentTheme.textOnBg
                )
            )
        }
    }
}

// ── Settings dialog Popups ──

@Composable
fun AmbientSettingsPopup(
    viewModel: MainViewModel,
    currentTheme: PrayerTheme,
    onDismiss: () -> Unit
) {
    val currentOrientation by viewModel.appOrientation.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val updateAvailableVal by viewModel.updateAvailable.collectAsStateWithLifecycle()
    val latestVersionNameVal by viewModel.latestVersionName.collectAsStateWithLifecycle()
    val updateErrorMessage by viewModel.updateErrorMessage.collectAsStateWithLifecycle()

    val themeKey by viewModel.theme.collectAsStateWithLifecycle()
    val textSizePreference by viewModel.prayerTimeTextSize.collectAsStateWithLifecycle()
    val calcMethodVal by viewModel.calcMethod.collectAsStateWithLifecycle()
    val asrSchoolVal by viewModel.asrSchool.collectAsStateWithLifecycle()
    val azanOnVal by viewModel.azanOn.collectAsStateWithLifecycle()
    val stayAwakeVal by viewModel.stayAwake.collectAsStateWithLifecycle()

    var calcExpanded by remember { mutableStateOf(false) }
    val selectedMethodName = CALCULATION_METHODS.find { it.id == calcMethodVal }?.name ?: "ISNA — North America"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            try {
                val window = (view.parent as? DialogWindowProvider)?.window
                if (window != null) {
                    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                }
            } catch (e: Exception) {
                // Ignore immersive error
            }
            onDispose {}
        }

        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = currentTheme.surface),
            border = BorderStroke(1.dp, currentTheme.primary.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(currentTheme.primary.copy(alpha = 0.08f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "⚙ Settings",
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primaryVariant
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = currentTheme.textOnBg)
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Theme Selector
                    Text(
                        text = "🎨 VISUAL THEMES",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primary,
                        letterSpacing = 1.5.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrayerTheme.values().forEach { theme ->
                            val isSelected = theme.id == themeKey
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) theme.primary.copy(alpha = 0.2f) else theme.surface.copy(alpha = 0.4f))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) theme.primary else currentTheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.updateTheme(theme.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(theme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = theme.displayName,
                                        fontSize = 12.sp,
                                        color = if (isSelected) theme.primary else currentTheme.textOnBg,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = currentTheme.primary.copy(alpha = 0.1f))

                    // 2. Display Orientation Selectors
                    Text(
                        text = "📱 DISPLAY ORIENTATION",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primary,
                        letterSpacing = 1.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Auto Option
                        val sAuto = currentOrientation == "auto"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sAuto) currentTheme.primary.copy(alpha = 0.15f) else currentTheme.primary.copy(alpha = 0.04f))
                                .border(
                                    width = if (sAuto) 1.5.dp else 1.dp,
                                    color = if (sAuto) currentTheme.primary else currentTheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.updateAppOrientation("auto") }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "🔄", fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Auto Mode",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sAuto) currentTheme.primary else currentTheme.textOnBg
                                )
                            }
                        }

                        // Portrait Option
                        val sPortrait = currentOrientation == "portrait"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sPortrait) currentTheme.primary.copy(alpha = 0.15f) else currentTheme.primary.copy(alpha = 0.04f))
                                .border(
                                    width = if (sPortrait) 1.5.dp else 1.dp,
                                    color = if (sPortrait) currentTheme.primary else currentTheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.updateAppOrientation("portrait") }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "📱", fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Portrait Mode",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sPortrait) currentTheme.primary else currentTheme.textOnBg
                                )
                            }
                        }

                        // Landscape Option
                        val sLandscape = currentOrientation == "landscape"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sLandscape) currentTheme.primary.copy(alpha = 0.15f) else currentTheme.primary.copy(alpha = 0.04f))
                                .border(
                                    width = if (sLandscape) 1.5.dp else 1.dp,
                                    color = if (sLandscape) currentTheme.primary else currentTheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.updateAppOrientation("landscape") }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "📐", fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Landscape Mode",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sLandscape) currentTheme.primary else currentTheme.textOnBg
                                )
                            }
                        }
                    }

                    Divider(color = currentTheme.primary.copy(alpha = 0.1f))

                    // 3. Font Size Selectors
                    Text(
                        text = "📏 PRAYER TIME FONT SIZE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primary,
                        letterSpacing = 1.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sizes = listOf("small" to "Small", "medium" to "Medium", "large" to "Large")
                        sizes.forEach { (sizeId, name) ->
                            val isSelected = sizeId == textSizePreference
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) currentTheme.primary.copy(alpha = 0.15f) else currentTheme.primary.copy(alpha = 0.04f))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) currentTheme.primary else currentTheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.updatePrayerTimeTextSize(sizeId) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) currentTheme.primary else currentTheme.textOnBg
                                )
                            }
                        }
                    }

                    Divider(color = currentTheme.primary.copy(alpha = 0.1f))

                    // 4. Calculation Method Selection
                    Text(
                        text = "🕊 CALCULATION METHOD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primary,
                        letterSpacing = 1.5.sp
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(currentTheme.primary.copy(alpha = 0.04f))
                                .border(1.dp, currentTheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .clickable { calcExpanded = !calcExpanded }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedMethodName,
                                fontSize = 12.sp,
                                color = currentTheme.textOnBg,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (calcExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Dropdown Indicator",
                                tint = currentTheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = calcExpanded,
                            onDismissRequest = { calcExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .background(currentTheme.surface)
                                .border(1.dp, currentTheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        ) {
                            CALCULATION_METHODS.forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(text = method.name, fontSize = 12.sp, color = currentTheme.textOnBg) },
                                    onClick = {
                                        viewModel.updateCalculationMethod(method.id)
                                        calcExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Divider(color = currentTheme.primary.copy(alpha = 0.1f))

                    // 5. Asr Calculation School Selection
                    Text(
                        text = "🕋 ASR SCHOOL (MADHAB)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primary,
                        letterSpacing = 1.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val schools = listOf(0 to "Shafi'i (Standard)", 1 to "Hanafi")
                        schools.forEach { (schoolId, name) ->
                            val isSelected = schoolId == asrSchoolVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) currentTheme.primary.copy(alpha = 0.15f) else currentTheme.primary.copy(alpha = 0.04f))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) currentTheme.primary else currentTheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.updateAsrSchool(schoolId) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) currentTheme.primary else currentTheme.textOnBg
                                )
                            }
                        }
                    }

                    Divider(color = currentTheme.primary.copy(alpha = 0.1f))

                    // 6. Audio notification & Screen Alert Options
                    Text(
                        text = "🔔 PREFERENCES",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primary,
                        letterSpacing = 1.5.sp
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(currentTheme.primary.copy(alpha = 0.04f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Keep Screen Awake
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Keep Screen Awake",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = currentTheme.textOnBg
                                )
                                Text(
                                    text = "Prevent screen from turning off.",
                                    fontSize = 9.sp,
                                    color = currentTheme.textSub
                                )
                            }
                            Switch(
                                checked = stayAwakeVal,
                                onCheckedChange = { viewModel.toggleStayAwake(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = currentTheme.primary,
                                    checkedTrackColor = currentTheme.primary.copy(alpha = 0.4f),
                                    uncheckedThumbColor = currentTheme.textSub,
                                    uncheckedTrackColor = currentTheme.primary.copy(alpha = 0.1f)
                                )
                            )
                        }

                        Divider(color = currentTheme.primary.copy(alpha = 0.06f))

                        // Azan Alert Player
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Azan Voice Alert",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = currentTheme.textOnBg
                                )
                                Text(
                                    text = "Play audio when it's prayer time.",
                                    fontSize = 9.sp,
                                    color = currentTheme.textSub
                                )
                            }
                            Switch(
                                checked = azanOnVal,
                                onCheckedChange = { viewModel.toggleAzan(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = currentTheme.primary,
                                    checkedTrackColor = currentTheme.primary.copy(alpha = 0.4f),
                                    uncheckedThumbColor = currentTheme.textSub,
                                    uncheckedTrackColor = currentTheme.primary.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }

                    Divider(color = currentTheme.primary.copy(alpha = 0.1f))

                    // 7. System Updates
                    Text(
                        text = "🚀 SYSTEM SERVICE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primary,
                        letterSpacing = 1.5.sp
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(currentTheme.primary.copy(alpha = 0.04f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Check for Updates",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = currentTheme.textOnBg
                                )
                                Text(
                                    text = "Verify if newer application builds are available.",
                                    fontSize = 9.sp,
                                    color = currentTheme.textSub
                                )
                            }

                            if (isCheckingUpdate) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = currentTheme.primary,
                                        strokeWidth = 1.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Checking...", fontSize = 10.sp, color = currentTheme.textSub)
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.checkGitHubUpdates(manuallyTriggered = true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Text("Check Now", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        // Status notification card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (updateAvailableVal) currentTheme.primary.copy(alpha = 0.12f)
                                else currentTheme.surface.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (updateAvailableVal) currentTheme.primary.copy(alpha = 0.3f)
                                else currentTheme.primary.copy(alpha = 0.08f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                if (updateAvailableVal) {
                                    Text(
                                        text = "📢 Update Available: v$latestVersionNameVal",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = currentTheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Please tap download on the screen prompt or restart the app to upgrade.",
                                        fontSize = 9.sp,
                                        color = currentTheme.textSub
                                    )
                                } else if (updateErrorMessage != null) {
                                    Text(
                                        text = "⚠️ " + updateErrorMessage!!,
                                        fontSize = 10.sp,
                                        color = Color.Red,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = "✓ App is up-to-date (v1.3)",
                                        fontSize = 10.sp,
                                        color = currentTheme.textSub
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Close CTA
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Close Settings", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

// ── Native Location Helper Functions ──

private fun fetchAndSetDeviceLocation(activity: Activity, viewModel: MainViewModel) {
    if (androidx.core.app.ActivityCompat.checkSelfPermission(
            activity,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
        androidx.core.app.ActivityCompat.checkSelfPermission(
            activity,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    try {
        val client = LocationServices.getFusedLocationProviderClient(activity)
        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                viewModel.setCoordinates(loc.latitude, loc.longitude)
            } else {
                // Fetch fresh high accuracy GPS
                client.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { fresh ->
                        if (fresh != null) {
                            viewModel.setCoordinates(fresh.latitude, fresh.longitude)
                        }
                    }
            }
        }.addOnFailureListener {
            // Ignore failure
        }
    } catch (e: Exception) {
        // Safe check
    }
}

private fun formatLocalDateTimeTo12H(zdt: ZonedDateTime): String {
    return try {
        zdt.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
    } catch (e: Exception) {
        ""
    }
}

// Standard scale modifier is imported from androidx.compose.ui.draw.scale in imports block
