package com.example

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
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
            val isTablet = maxWidth > 720.dp

            if (isTablet) {
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
                    
                    // Simple inline status dates and location rows
                    HijriDateBanner(viewModel = viewModel, currentTheme = currentTheme)
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Active Display clock
                    MobileClockContainer(viewModel = viewModel, currentTheme = currentTheme)

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
    val stayAwakeEnabled by viewModel.stayAwake.collectAsStateWithLifecycle()

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
                        text = "🌡 Weather",
                        fontSize = 11.sp,
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

        // Stay Awake Feature Controller directly placed on Tablet Sidebar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(currentTheme.primary.copy(alpha = 0.08f))
                .clickable { viewModel.toggleStayAwake(!stayAwakeEnabled) }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = "Stay Awake icon",
                    tint = if (stayAwakeEnabled) currentTheme.primary else currentTheme.textSub,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Stay Awake",
                        fontSize = 13.sp,
                        color = currentTheme.textOnBg,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (stayAwakeEnabled) "Screen remains ON" else "Screen standard timeout",
                        fontSize = 10.sp,
                        color = currentTheme.textSub
                    )
                }
            }
            Switch(
                checked = stayAwakeEnabled,
                onCheckedChange = { viewModel.toggleStayAwake(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = currentTheme.primary,
                    uncheckedThumbColor = currentTheme.textSub,
                    uncheckedTrackColor = currentTheme.background
                ),
                modifier = Modifier.scale(0.85f).testTag("sidebar_stay_awake_switch")
            )
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
        // Today schedule heading & calendar week strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Today's Schedules & Week Grid",
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = currentTheme.primaryVariant,
                fontStyle = FontStyle.Italic
            )
            
            // Stay awake visual indicator directly on right content top row
            val awake by viewModel.stayAwake.collectAsStateWithLifecycle()
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (awake) currentTheme.primary.copy(alpha = 0.15f) else currentTheme.surface.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.clickable { viewModel.toggleStayAwake(!awake) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (awake) currentTheme.primary else currentTheme.textMuted, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (awake) "Screen: Keep Awake" else "Screen: Default",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (awake) currentTheme.primary else currentTheme.textSub
                    )
                }
            }
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
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Stay awake mobile inline toggle switch for visual convenience!
            val awakeEnabled by viewModel.stayAwake.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(currentTheme.primary.copy(alpha = 0.05f))
                    .clickable { viewModel.toggleStayAwake(!awakeEnabled) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = if (awakeEnabled) currentTheme.primary else currentTheme.textSub,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (awakeEnabled) "Stay Awake (Active)" else "Stay Awake (Idle)",
                    fontSize = 11.sp,
                    color = if (awakeEnabled) currentTheme.primary else currentTheme.textOnBg,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = awakeEnabled,
                    onCheckedChange = { viewModel.toggleStayAwake(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = currentTheme.primary
                    ),
                    modifier = Modifier.scale(0.7f).testTag("mobile_stay_awake_switch")
                )
            }

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
                modifier = Modifier.fillMaxHeight()
            )
        }
    }
}

@Composable
fun MobileSchedulesVertical(viewModel: MainViewModel, currentTheme: PrayerTheme) {
    val prayersList by viewModel.prayers.collectAsStateWithLifecycle()

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

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        prayersList.forEach { prayer ->
            val idx = prayersList.indexOf(prayer)
            val next = if (idx + 1 < prayersList.size) prayersList[idx + 1] else null
            val isNow = now.isAfter(prayer.time) && (next == null || now.isBefore(next.time))
            val isDone = !isNow && now.isAfter(prayer.time)

            PrayerCardItem(
                prayer = prayer,
                isNow = isNow,
                isDone = isDone,
                currentTheme = currentTheme,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PrayerCardItem(
    prayer: PrayerTime,
    isNow: Boolean,
    isDone: Boolean,
    currentTheme: PrayerTheme,
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

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%d:%s", dispH, mString),
                    fontFamily = FontFamily.Serif,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isNow) currentTheme.primaryVariant else currentTheme.textOnBg
                )
                Text(
                    text = " $ap",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    color = currentTheme.textSub,
                    modifier = Modifier.padding(bottom = 6.dp)
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
    val method by viewModel.calcMethod.collectAsStateWithLifecycle()
    val school by viewModel.asrSchool.collectAsStateWithLifecycle()
    val azanOn by viewModel.azanOn.collectAsStateWithLifecycle()
    val stayAwakeEnabled by viewModel.stayAwake.collectAsStateWithLifecycle()

    // Preview state theme key
    var previewThemeKey by remember { mutableStateOf(currentTheme.id) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = currentTheme.surface),
            border = BorderStroke(1.dp, currentTheme.primary.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
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

                // Scrollable bodies
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Stay Awake feature (PROMINENT TOGGLE AS REQUESTED)
                    Text(
                        text = "🔔 DISPLAY CONVENIENCE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primary,
                        letterSpacing = 1.5.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(currentTheme.primary.copy(alpha = 0.08f))
                            .clickable { viewModel.toggleStayAwake(!stayAwakeEnabled) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                            Text(
                                text = "Keep Screen Awake",
                                fontSize = 13.sp,
                                color = currentTheme.textOnBg,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Screen will never turn off while this prayer schedule app is open.",
                                fontSize = 10.sp,
                                color = currentTheme.textSub
                            )
                        }
                        Switch(
                            checked = stayAwakeEnabled,
                            onCheckedChange = { viewModel.toggleStayAwake(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = currentTheme.primary
                            ),
                            modifier = Modifier.testTag("dialog_stay_awake_switch")
                        )
                    }

                    Divider(color = currentTheme.primary.copy(alpha = 0.1f))

                    // 1. Dynamic visual themes swatches
                    Text(
                        text = "🎨 VISUAL THEMES",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primary,
                        letterSpacing = 1.5.sp
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PrayerTheme.values().forEach { theme ->
                            val isSel = theme.id == previewThemeKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(currentTheme.primary.copy(alpha = if (isSel) 0.15f else 0.04f))
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isSel) currentTheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        previewThemeKey = theme.id
                                        viewModel.updateTheme(theme.id)
                                    }
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(theme.background, CircleShape)
                                            .border(1.dp, theme.primary, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = theme.displayName.split(" ").first(),
                                        fontSize = 8.sp,
                                        color = currentTheme.textOnBg,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = currentTheme.primary.copy(alpha = 0.1f))

                    // 2. Calculation method list selector
                    Text(
                        text = "🕌 CALCULATION METHOD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primary,
                        letterSpacing = 1.5.sp
                    )

                    var methodOpen by remember { mutableStateOf(false) }
                    val currentMethodObj = CALCULATION_METHODS.find { it.id == method } ?: CALCULATION_METHODS.first()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(currentTheme.surface)
                            .border(1.dp, currentTheme.primary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .clickable { methodOpen = !methodOpen }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = currentMethodObj.name, fontSize = 13.sp, color = currentTheme.textOnBg)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = currentTheme.primary)
                        }
                    }

                    if (methodOpen) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = currentTheme.surface.copy(alpha = 0.95f)),
                            border = BorderStroke(1.dp, currentTheme.primary.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.height(200.dp).verticalScroll(rememberScrollState())) {
                                CALCULATION_METHODS.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.updateCalculationMethod(item.id)
                                                methodOpen = false
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.name,
                                            fontSize = 13.sp,
                                            color = if (item.id == method) currentTheme.primary else currentTheme.textOnBg,
                                            fontWeight = if (item.id == method) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = currentTheme.primary.copy(alpha = 0.1f))

                    // 3. School toggler selection Shafi/Hanafi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Asr Calculation School",
                                fontSize = 13.sp,
                                color = currentTheme.textOnBg,
                                fontWeight = FontWeight.Bold
                            )
                            Text(text = "Hanafi school offsets sunset prayers by standard hour", fontSize = 10.sp, color = currentTheme.textSub)
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(currentTheme.primary.copy(alpha = 0.08f))
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (school == 0) currentTheme.primary else Color.Transparent)
                                    .clickable { viewModel.updateAsrSchool(0) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Shafi'i",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (school == 0) Color.White else currentTheme.textSub
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (school == 1) currentTheme.primary else Color.Transparent)
                                    .clickable { viewModel.updateAsrSchool(1) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Hanafi",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (school == 1) Color.White else currentTheme.textSub
                                )
                            }
                        }
                    }

                    Divider(color = currentTheme.primary.copy(alpha = 0.1f))

                    // 4. Azan audio toggler
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Play Azan Voice Notification",
                                fontSize = 13.sp,
                                color = currentTheme.textOnBg,
                                fontWeight = FontWeight.Bold
                            )
                            Text(text = "Triggers holy Azan audio at prayer minutes.", fontSize = 10.sp, color = currentTheme.textSub)
                        }

                        Switch(
                            checked = azanOn,
                            onCheckedChange = { viewModel.toggleAzan(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = currentTheme.primary
                            )
                        )
                    }
                }

                // Bottom CTA Save
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary)
                ) {
                    Text(
                        text = "✦ Save Settings",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
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
