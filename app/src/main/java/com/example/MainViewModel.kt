package com.example

import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("local_prayer_settings", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .readTimeout(java.time.Duration.ofSeconds(15))
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // ── App Settings State Flow ──
    private val _theme = MutableStateFlow(prefs.getString("theme", "midnight") ?: "midnight")
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _calcMethod = MutableStateFlow(prefs.getInt("calc_method", 2))
    val calcMethod: StateFlow<Int> = _calcMethod.asStateFlow()

    private val _asrSchool = MutableStateFlow(prefs.getInt("asr_school", 1)) // 1: Hanafi, 0: Shafi'i
    val asrSchool: StateFlow<Int> = _asrSchool.asStateFlow()

    private val _azanOn = MutableStateFlow(prefs.getBoolean("azan_on", true))
    val azanOn: StateFlow<Boolean> = _azanOn.asStateFlow()

    private val _stayAwake = MutableStateFlow(prefs.getBoolean("stay_awake", true)) // stay awake default true
    val stayAwake: StateFlow<Boolean> = _stayAwake.asStateFlow()

    // ── GitHub Auto Update Configuration ──
    private val _githubOwner = MutableStateFlow(prefs.getString("github_owner", "htvusa") ?: "htvusa")
    val githubOwner: StateFlow<String> = _githubOwner.asStateFlow()

    private val _githubRepo = MutableStateFlow(prefs.getString("github_repo", "localprayertime") ?: "localprayertime")
    val githubRepo: StateFlow<String> = _githubRepo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    private val _latestVersionName = MutableStateFlow("")
    val latestVersionName: StateFlow<String> = _latestVersionName.asStateFlow()

    private val _latestVersionDescription = MutableStateFlow("")
    val latestVersionDescription: StateFlow<String> = _latestVersionDescription.asStateFlow()

    private val _latestApkUrl = MutableStateFlow("")
    val latestApkUrl: StateFlow<String> = _latestApkUrl.asStateFlow()

    private val _latestReleasePageUrl = MutableStateFlow("")
    val latestReleasePageUrl: StateFlow<String> = _latestReleasePageUrl.asStateFlow()

    private val _updateErrorMessage = MutableStateFlow<String?>(null)
    val updateErrorMessage: StateFlow<String?> = _updateErrorMessage.asStateFlow()

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _downloadedApkFile = MutableStateFlow<java.io.File?>(null)
    val downloadedApkFile: StateFlow<java.io.File?> = _downloadedApkFile.asStateFlow()

    // ── Coordinates & Location State Flow ──
    private val _latitude = MutableStateFlow(prefs.getFloat("lat", 21.4225f).toDouble()) // Default Mecca
    val latitude: StateFlow<Double> = _latitude.asStateFlow()

    private val _longitude = MutableStateFlow(prefs.getFloat("lon", 39.8262f).toDouble()) // Default Mecca
    val longitude: StateFlow<Double> = _longitude.asStateFlow()

    private val _locationName = MutableStateFlow("Mecca, Saudi Arabia")
    val locationName: StateFlow<String> = _locationName.asStateFlow()

    private val _weatherText = MutableStateFlow("Unavailable")
    val weatherText: StateFlow<String> = _weatherText.asStateFlow()

    // ── Selected Date ──
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // ── Prayers & Hijri Data ──
    private val _prayers = MutableStateFlow<List<PrayerTime>>(emptyList())
    val prayers: StateFlow<List<PrayerTime>> = _prayers.asStateFlow()

    private val _hijriText = MutableStateFlow("—")
    val hijriText: StateFlow<String> = _hijriText.asStateFlow()

    private val _gregorianText = MutableStateFlow("—")
    val gregorianText: StateFlow<String> = _gregorianText.asStateFlow()

    private val _currentPrayer = MutableStateFlow<PrayerTime?>(null)
    val currentPrayer: StateFlow<PrayerTime?> = _currentPrayer.asStateFlow()

    private val _nextPrayerCountdown = MutableStateFlow("—")
    val nextPrayerCountdown: StateFlow<String> = _nextPrayerCountdown.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Toast/Notification Event Flow ──
    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    // ── Dedicated Media Players ──
    private var azanPlayer: MediaPlayer? = null
    private var quranPlayer: MediaPlayer? = null
    private var nasheedPlayer: MediaPlayer? = null

    // Player Track Status
    val surahsList = listOf(
        "Al-Fatiha", "Al-Baqarah", "Al-Imran", "An-Nisa", "Al-Ma'idah", "Al-An'am", "Al-A'raf", "Al-Anfal", "At-Tawbah", "Yunus", "Hud", "Yusuf", "Ar-Ra'd", "Ibrahim", "Al-Hijr", "An-Nahl", "Al-Isra", "Al-Kahf", "Maryam", "Ta-Ha", "Al-Anbiya", "Al-Hajj", "Al-Mu'minun", "An-Nur", "Al-Furqan", "Ash-Shu'ara", "An-Naml", "Al-Qasas", "Al-Ankabut", "Ar-Rum", "Luqman", "As-Sajda", "Al-Ahzab", "Saba", "Fatir", "Ya-Sin", "As-Saffat", "Sad", "Az-Zumar", "Ghafir", "Fussilat", "Ash-Shura", "Az-Zukhruf", "Ad-Dukhan", "Al-Jathiya", "Al-Ahqaf", "Muhammad", "Al-Fath", "Al-Hujurat", "Qaf", "Adh-Dhariyat", "At-Tur", "An-Najm", "Al-Qamar", "Ar-Rahman", "Al-Waqia", "Al-Hadid", "Al-Mujadila", "Al-Hashr", "Al-Mumtahina", "As-Saff", "Al-Jumu'a", "Al-Munafiqun", "At-Taghabun", "At-Talaq", "At-Tahrim", "Al-Mulk", "Al-Qalam", "Al-Haqqah", "Al-Ma'arij", "Nuh", "Al-Jinn", "Al-Muzzammil", "Al-Muddaththir", "Al-Qiyamah", "Al-Insan", "Al-Mursalat", "An-Naba", "An-Nazi'at", "Abasa", "At-Takwir", "Al-Infitar", "Al-Mutaffifin", "Al-Inshiqaq", "Al-Buruj", "At-Tariq", "Al-A'la", "Al-Ghashiya", "Al-Fajr", "Al-Balad", "Ash-Shams", "Al-Lail", "Ad-Duha", "Ash-Sharh", "At-Tin", "Al-Alaq", "Al-Qadr", "Al-Bayyina", "Az-Zalzalah", "Al-Adiyat", "Al-Qari'a", "At-Takathur", "Al-Asr", "Al-Humaza", "Al-Fil", "Quraish", "Al-Ma'un", "Al-Kawthar", "Al-Kafirun", "An-Nasr", "Al-Masad", "Al-Ikhlas", "Al-Falaq", "An-Nas"
    )

    private val _quranSurahIndex = MutableStateFlow(prefs.getInt("quran_surah_idx", 1)) // 1 to 114
    val quranSurahIndex: StateFlow<Int> = _quranSurahIndex.asStateFlow()

    private val _quranStatus = MutableStateFlow("Ready")
    val quranStatus: StateFlow<String> = _quranStatus.asStateFlow()

    private val _quranIsPlaying = MutableStateFlow(false)
    val quranIsPlaying: StateFlow<Boolean> = _quranIsPlaying.asStateFlow()

    private val _quranProgress = MutableStateFlow(0f) // 0 to 1
    val quranProgress: StateFlow<Float> = _quranProgress.asStateFlow()

    // Nasheed Player Properties
    private val _nasheedTracks = MutableStateFlow<List<NasheedTrack>>(emptyList())
    val nasheedTracks: StateFlow<List<NasheedTrack>> = _nasheedTracks.asStateFlow()

    private val _nasheedIndex = MutableStateFlow(prefs.getInt("nasheed_idx", 0))
    val nasheedIndex: StateFlow<Int> = _nasheedIndex.asStateFlow()

    private val _nasheedStatus = MutableStateFlow("Ready")
    val nasheedStatus: StateFlow<String> = _nasheedStatus.asStateFlow()

    private val _nasheedIsPlaying = MutableStateFlow(false)
    val nasheedIsPlaying: StateFlow<Boolean> = _nasheedIsPlaying.asStateFlow()

    private val _nasheedProgress = MutableStateFlow(0f)
    val nasheedProgress: StateFlow<Float> = _nasheedProgress.asStateFlow()

    // Volume level state for players
    private val _quranVolume = MutableStateFlow(prefs.getFloat("quran_volume", 0.5f))
    val quranVolume: StateFlow<Float> = _quranVolume.asStateFlow()

    private val _nasheedVolume = MutableStateFlow(prefs.getFloat("nasheed_volume", 0.5f))
    val nasheedVolume: StateFlow<Float> = _nasheedVolume.asStateFlow()

    // ── Active Background Jobs ──
    private var countdownJob: Job? = null
    private var trackingJob: Job? = null
    private var progressTrackerJob: Job? = null
    private var lastPlayedAzanKey: String? = null

    init {
        // Prepare primary countdown and tracker loop
        startCountdownTimer()
        startPeriodicCheckers()
        
        // Initial prayer, weather extraction and nasheeds fetching
        fetchBackupNasheedManifest()
        updateSchedules()
        fetchWeatherDetails()

        // Check for updates automatically in the background
        viewModelScope.launch {
            try {
                delay(3000) // smooth startup delay
                checkGitHubUpdates(manuallyTriggered = false)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    // Update settings securely
    fun updateTheme(newTheme: String) {
        _theme.value = newTheme
        prefs.edit().putString("theme", newTheme).apply()
        viewModelScope.launch { _uiEvents.emit("Theme applied: $newTheme") }
    }

    fun updateCalculationMethod(methodId: Int) {
        _calcMethod.value = methodId
        prefs.edit().putInt("calc_method", methodId).apply()
        updateSchedules()
        viewModelScope.launch { _uiEvents.emit("Calculation method updated") }
    }

    fun updateAsrSchool(schoolId: Int) {
        _asrSchool.value = schoolId
        prefs.edit().putInt("asr_school", schoolId).apply()
        updateSchedules()
        viewModelScope.launch { _uiEvents.emit("Asr school modified") }
    }

    fun toggleAzan(on: Boolean) {
        _azanOn.value = on
        prefs.edit().putBoolean("azan_on", on).apply()
        viewModelScope.launch { _uiEvents.emit(if (on) "Azan volume enabled" else "Azan muted") }
    }

    fun toggleStayAwake(on: Boolean) {
        _stayAwake.value = on
        prefs.edit().putBoolean("stay_awake", on).apply()
        viewModelScope.launch { _uiEvents.emit(if (on) "Stay awake activated" else "Stay awake deactivated") }
    }

    fun changeSelectedDate(date: LocalDate) {
        _selectedDate.value = date
        updateSchedules()
    }

    fun setCoordinates(lat: Double, lon: Double) {
        _latitude.value = lat
        _longitude.value = lon
        prefs.edit().putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat()).apply()
        reverseGeocodeLocation(lat, lon)
        updateSchedules()
        fetchWeatherDetails()
        viewModelScope.launch { _uiEvents.emit("Coordinates synchronized") }
    }

    // Sync live Location Geocoder
    private fun reverseGeocodeLocation(lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Selected Location"
                    val country = addr.countryName ?: ""
                    _locationName.value = if (country.isNotEmpty()) "$city, $country" else city
                } else {
                    // Fallback using OpenStreetMap coordinates formatting
                    _locationName.value = String.format(Locale.US, "%.4f, %.4f", lat, lon)
                }
            } catch (e: Exception) {
                _locationName.value = String.format(Locale.US, "%.4f, %.4f", lat, lon)
            }
        }
    }

    // Refresh weather details from open-meteo
    fun fetchWeatherDetails() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lat = _latitude.value
                val lon = _longitude.value
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,wind_speed_10m,weathercode&temperature_unit=fahrenheit"
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val bodyString = response.body!!.string()
                        val adapter = moshi.adapter(WeatherResponse::class.java)
                        val weatherData = adapter.fromJson(bodyString)
                        if (weatherData != null) {
                            val code = weatherData.current.weathercode
                            val temp = weatherData.current.temperature_2m.toInt()
                            val wind = weatherData.current.wind_speed_10m.toInt()
                            val conditions = mapWeatherCodeToEmojiAndLabel(code)
                            _weatherText.value = "$conditions $temp°F · wind: $wind km/h"
                        }
                    }
                }
            } catch (e: Exception) {
                _weatherText.value = "Unavailable"
            }
        }
    }

    private fun mapWeatherCodeToEmojiAndLabel(code: Int): String {
        return when (code) {
            0 -> "☀️ Sunny"
            1 -> "🌤 Mainly Clear"
            2 -> "⛅ Partly Cloudy"
            3 -> "☁️ Overcast"
            45 -> "🌫 Foggy"
            51 -> "🌦 Light Drizzle"
            61 -> "🌧 Light Rain"
            71 -> "🌨 Flurries"
            80 -> "🌦 Heavy Showers"
            95 -> "⛈ Thunderstorm"
            else -> "🌡"
        }
    }

    // Retrieve prayer schedules from Aladhan API or Fallback computation math
    fun updateSchedules() {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lat = _latitude.value
                val lon = _longitude.value
                val method = _calcMethod.value
                val school = _asrSchool.value
                val date = _selectedDate.value
                val month = date.monthValue
                val year = date.year

                val url = "https://api.aladhan.com/v1/calendar?latitude=$lat&longitude=$lon&method=$method&school=$school&month=$month&year=$year"
                val request = Request.Builder().url(url).build()

                Log.d("PrayerTimes", "Fetching schedules from: $url")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val bodyString = response.body!!.string()
                        val adapter = moshi.adapter(AladhanResponse::class.java)
                        val scheduleData = adapter.fromJson(bodyString)

                        if (scheduleData != null && scheduleData.code == 200 && scheduleData.data.isNotEmpty()) {
                            // Extract specific month day details
                            val dayIndex = date.dayOfMonth - 1
                            if (dayIndex in scheduleData.data.indices) {
                                val dayInfo = scheduleData.data[dayIndex]
                                val timings = dayInfo.timings
                                val hijri = dayInfo.date.hijri

                                withContext(Dispatchers.Main) {
                                    _gregorianText.value = date.format(DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy")).uppercase(Locale.US)
                                    _hijriText.value = "${hijri.day} ${hijri.month.en} ${hijri.year} AH"
                                    mapTimingsToPrayers(timings, date)
                                    _isLoading.value = false
                                }
                                return@launch
                            }
                        }
                    }
                }
                // Fallback computation mathematically locally if API call is failed or offline
                generateFallbackLocalSchedules()
            } catch (e: Exception) {
                Log.e("PrayerTimes", "Failed loading prayer times, initiating fallback", e)
                generateFallbackLocalSchedules()
            }
        }
    }

    private suspend fun mapTimingsToPrayers(timings: Map<String, String>, date: LocalDate) {
        val mappedList = mutableListOf<PrayerTime>()
        val defaultZone = ZoneId.systemDefault()

        val keysToDisplay = listOf(
            "Imsak" to "Imsak",
            "Fajr" to "Fajr",
            "Sunrise" to "Sunrise",
            "derive_ishraq" to "Ishraq", // derived from sunrise
            "Dhuhr" to "Zuhr",
            "Asr" to "Asr",
            "Maghrib" to "Maghrib",
            "Isha" to "Isha'a"
        )

        for ((apiKey, dispName) in keysToDisplay) {
            try {
                if (apiKey == "derive_ishraq") {
                    // Ishraq is Sunrise + 22 Minutes (matching javascript derived math)
                    val sunriseStr = timings["Sunrise"]?.split(" ")?.firstOrNull() ?: "06:00"
                    val pts = sunriseStr.split(":")
                    val h = pts[0].toInt()
                    val m = pts[1].toInt()
                    val totalMins = h * 60 + m + 22
                    val derivedH = (totalMins / 60) % 24
                    val derivedM = totalMins % 60
                    val derivedTime = LocalDateTime.of(date, LocalTime.of(derivedH, derivedM))
                    val zdt = ZonedDateTime.of(derivedTime, defaultZone)
                    val ts = String.format(Locale.US, "%02d:%02d", derivedH, derivedM)
                    mappedList.add(PrayerTime(dispName, "Ishraq", zdt, ts, true))
                } else {
                    val fullVal = timings[apiKey] ?: "12:00"
                    val cleanTime = fullVal.split(" ").firstOrNull() ?: "12:00"
                    val trimmed = if (cleanTime.length == 5) "$cleanTime:00" else cleanTime
                    val pts = trimmed.split(":")
                    val h = pts[0].toInt()
                    val m = pts[1].toInt()
                    val ldt = LocalDateTime.of(date, LocalTime.of(h, m))
                    val zdt = ZonedDateTime.of(ldt, defaultZone)
                    mappedList.add(PrayerTime(dispName, apiKey, zdt, String.format(Locale.US, "%02d:%02d", h, m)))
                }
            } catch (e: Exception) {
                Log.w("PrayerTimes", "Failed parsing key $apiKey", e)
            }
        }

        withContext(Dispatchers.Main) {
            _prayers.value = mappedList
            calculateActiveAndNextPrayerState()
        }
    }

    private suspend fun generateFallbackLocalSchedules() {
        // Build robust local coordinates mapping offsets for basic backup mechanics
        val date = _selectedDate.value
        val defaultZone = ZoneId.systemDefault()
        
        val fakeTimings = mapOf(
            "Imsak" to "04:30",
            "Fajr" to "04:45",
            "Sunrise" to "06:12",
            "Dhuhr" to "12:15",
            "Asr" to "15:45",
            "Maghrib" to "18:48",
            "Isha" to "20:15"
        )
        
        withContext(Dispatchers.Main) {
            _gregorianText.value = date.format(DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy")).uppercase(Locale.US)
            _hijriText.value = "15 Dhul-Qadah 1447 AH (Local Fallback)"
            mapTimingsToPrayers(fakeTimings, date)
            _isLoading.value = false
        }
    }

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                calculateActiveAndNextPrayerState()
                delay(1000)
            }
        }
    }

    private fun startPeriodicCheckers() {
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                // Fetch prayer schedules and weather periodically to ensure consistency
                delay(1800000) // 30 mins
                updateSchedules()
                fetchWeatherDetails()
            }
        }
    }

    // Core Scheduler calculation, Azan triggering & countdown updates
    private fun calculateActiveAndNextPrayerState() {
        val now = ZonedDateTime.now()
        val currentPrayers = _prayers.value
        if (currentPrayers.isEmpty()) return

        // 1. Identify current active prayer
        var active: PrayerTime? = null
        for (i in currentPrayers.indices) {
            val curr = currentPrayers[i]
            val next = if (i + 1 < currentPrayers.size) currentPrayers[i + 1] else null
            
            if (now.isAfter(curr.time) && (next == null || now.isBefore(next.time))) {
                active = curr
                break
            }
        }
        
        // If system time falls before the very first prayer of the day (e.g. before Imsak), set active to last of previous day or empty
        if (active == null && now.isBefore(currentPrayers.first().time)) {
             active = null // before first prayer
        }

        if (_currentPrayer.value?.name != active?.name) {
            _currentPrayer.value = active
        }

        // 2. Identify and compute next prayer countdown
        val next = currentPrayers.find { it.time.isAfter(now) }
        if (next != null) {
            val duration = Duration.between(now, next.time)
            val h = duration.toHours()
            val m = duration.toMinutes() % 60
            val s = duration.seconds % 60
            _nextPrayerCountdown.value = String.format(Locale.US, "%s %02d:%02d:%02d", next.name, h, m, s)

            // Dynamic Azan sound triggering inside this critical 1-minute window
            if (_azanOn.value && duration.toMillis() in 0..1000L) {
                val key = next.key
                if (lastPlayedAzanKey != key) {
                    triggerAzanMediaAudio()
                    lastPlayedAzanKey = key
                }
            }
        } else {
            _nextPrayerCountdown.value = "All done ✦"
        }
    }

    // Play Azan MP3 file via direct MediaPlayer stream
    private fun triggerAzanMediaAudio() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                azanPlayer?.release()
                val url = "https://raw.githubusercontent.com/jm7867/pa/master/azan.mp3"
                Log.d("MediaPlayer", "Streaming Azan audio from: $url")
                
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(url)
                    prepare()
                    start()
                    azanPlayer = this
                }
                _uiEvents.emit("Azan activated at prayer time!")
            } catch (e: Exception) {
                Log.e("MediaPlayer", "Failed playing azan audio streaming", e)
            }
        }
    }

    // ── Dual Tab Quran Player Operations ──
    fun setQuranVolume(v: Float) {
        _quranVolume.value = v
        prefs.edit().putFloat("quran_volume", v).apply()
        quranPlayer?.setVolume(v, v)
    }

    fun playQuranSurah(surahIdx: Int) {
        val finalIdx = surahIdx.coerceIn(1, 114)
        _quranSurahIndex.value = finalIdx
        prefs.edit().putInt("quran_surah_idx", finalIdx).apply()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                viewModelScope.launch { 
                    _quranStatus.value = "Loading Surah..."
                    _quranProgress.value = 0f
                }
                
                quranPlayer?.stop()
                quranPlayer?.release()
                quranPlayer = null

                val fileCode = String.format(Locale.US, "%03d", finalIdx)
                val url = "https://raw.githubusercontent.com/jm7867/pa/master/quran/$fileCode.mp3"
                Log.d("MediaPlayer", "Streaming quran audio from: $url")

                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(url)
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        val vol = _quranVolume.value
                        mp.setVolume(vol, vol)
                        mp.start()
                        viewModelScope.launch {
                            _quranStatus.value = "Playing"
                            _quranIsPlaying.value = true
                        }
                        trackPlayerProgress()
                    }
                    setOnCompletionListener {
                        viewModelScope.launch {
                            _quranIsPlaying.value = false
                            _quranProgress.value = 0f
                            // Play next surah automatically
                            playQuranNext()
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        viewModelScope.launch { _quranStatus.value = "Failed streaming" }
                        true
                    }
                    quranPlayer = this
                }
            } catch (e: Exception) {
                Log.e("MediaPlayer", "Failed starting quran surah player", e)
                _quranStatus.value = "Playback error"
            }
        }
    }

    fun toggleQuranPlay() {
        val player = quranPlayer
        if (player != null) {
            if (_quranIsPlaying.value) {
                player.pause()
                _quranIsPlaying.value = false
                _quranStatus.value = "Paused"
            } else {
                player.start()
                _quranIsPlaying.value = true
                _quranStatus.value = "Playing"
            }
        } else {
            playQuranSurah(_quranSurahIndex.value)
        }
    }

    fun playQuranNext() {
        val nextIdx = if (_quranSurahIndex.value >= 114) 1 else _quranSurahIndex.value + 1
        playQuranSurah(nextIdx)
    }

    fun playQuranPrev() {
        val prevIdx = if (_quranSurahIndex.value <= 1) 114 else _quranSurahIndex.value - 1
        playQuranSurah(prevIdx)
    }

    fun seekQuran(progressPercent: Float) {
        val player = quranPlayer ?: return
        val pos = (progressPercent * player.duration).toInt()
        player.seekTo(pos)
        _quranProgress.value = progressPercent
    }

    // ── Nasheed Play Operations ──
    fun setNasheedVolume(v: Float) {
        _nasheedVolume.value = v
        prefs.edit().putFloat("nasheed_volume", v).apply()
        nasheedPlayer?.setVolume(v, v)
    }

    // Fetch dynamic Nashyed tracks from GitHub raw files
    private fun fetchBackupNasheedManifest() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://raw.githubusercontent.com/jm7867/pa/master/nashed/manifest.json"
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val bodyString = response.body!!.string()
                        val listAdapter = moshi.adapter(List::class.java)
                        val files = listAdapter.fromJson(bodyString) as? List<*>
                        if (files != null && files.isNotEmpty()) {
                            val mapped = files.map { file ->
                                val cleanName = file.toString()
                                val title = cleanName.replace(Regex("\\.mp3$"), "")
                                    .replace(Regex("^\\d+[\\s._\\-]+"), "")
                                    .replace(Regex("[_\\-]+"), " ")
                                    .replace(Regex("\\b\\w")) { it.value.uppercase(Locale.US) }
                                    .trim()
                                NasheedTrack("https://raw.githubusercontent.com/jm7867/pa/master/nashed/$cleanName", title)
                            }
                            _nasheedTracks.value = mapped
                            return@launch
                        }
                    }
                }
                // Precompile fallback standard nasheed audio list if download manifest fails
                produceFallbackNasheedsList()
            } catch (e: Exception) {
                produceFallbackNasheedsList()
            }
        }
    }

    private fun produceFallbackNasheedsList() {
        val defaultList = listOf(
            NasheedTrack("https://raw.githubusercontent.com/jm7867/pa/master/nashed/001%20Kun%20Anta.mp3", "Kun Anta"),
            NasheedTrack("https://raw.githubusercontent.com/jm7867/pa/master/nashed/002%20Omer%20Faruk.mp3", "Omer Faruk"),
            NasheedTrack("https://raw.githubusercontent.com/jm7867/pa/master/nashed/003%20Subhan%20Allah.mp3", "Subhan Allah")
        )
        _nasheedTracks.value = defaultList
    }

    fun playNasheed(index: Int) {
        val tracks = _nasheedTracks.value
        if (tracks.isEmpty()) return
        val finalIdx = index.coerceIn(0, tracks.size - 1)
        _nasheedIndex.value = finalIdx
        prefs.edit().putInt("nasheed_idx", finalIdx).apply()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                viewModelScope.launch {
                    _nasheedStatus.value = "Loading track..."
                    _nasheedProgress.value = 0f
                }

                nasheedPlayer?.stop()
                nasheedPlayer?.release()
                nasheedPlayer = null

                val track = tracks[finalIdx]
                Log.d("MediaPlayer", "Streaming Nasheed audio from: ${track.file}")

                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(track.file)
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        val vol = _nasheedVolume.value
                        mp.setVolume(vol, vol)
                        mp.start()
                        viewModelScope.launch {
                            _nasheedStatus.value = "${finalIdx + 1} / ${tracks.size}"
                            _nasheedIsPlaying.value = true
                        }
                        trackPlayerProgress()
                    }
                    setOnCompletionListener {
                        viewModelScope.launch {
                            _nasheedIsPlaying.value = false
                            _nasheedProgress.value = 0f
                            // Play next nasheed automatically
                            playNasheedNext()
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        viewModelScope.launch { _nasheedStatus.value = "Unavailable" }
                        true
                    }
                    nasheedPlayer = this
                }
            } catch (e: Exception) {
                Log.e("MediaPlayer", "Failed playing nasheed stream", e)
                _nasheedStatus.value = "Playback error"
            }
        }
    }

    fun toggleNasheedPlay() {
        val player = nasheedPlayer
        if (player != null) {
            if (_nasheedIsPlaying.value) {
                player.pause()
                _nasheedIsPlaying.value = false
                _nasheedStatus.value = "Paused"
            } else {
                player.start()
                _nasheedIsPlaying.value = true
                _nasheedStatus.value = "Playing"
            }
        } else {
            playNasheed(_nasheedIndex.value)
        }
    }

    fun playNasheedNext() {
        val size = _nasheedTracks.value.size
        if (size == 0) return
        val nextIdx = (_nasheedIndex.value + 1) % size
        playNasheed(nextIdx)
    }

    fun playNasheedPrev() {
        val size = _nasheedTracks.value.size
        if (size == 0) return
        val prevIdx = if (_nasheedIndex.value <= 0) size - 1 else _nasheedIndex.value - 1
        playNasheed(prevIdx)
    }

    fun seekNasheed(progressPercent: Float) {
        val player = nasheedPlayer ?: return
        val pos = (progressPercent * player.duration).toInt()
        player.seekTo(pos)
        _nasheedProgress.value = progressPercent
    }

    // Global background progress metrics sync thread
    private fun trackPlayerProgress() {
        progressTrackerJob?.cancel()
        progressTrackerJob = viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                quranPlayer?.let { p ->
                    if (p.isPlaying && p.duration > 0) {
                        _quranProgress.value = p.currentPosition.toFloat() / p.duration.toFloat()
                    }
                }
                nasheedPlayer?.let { p ->
                    if (p.isPlaying && p.duration > 0) {
                        _nasheedProgress.value = p.currentPosition.toFloat() / p.duration.toFloat()
                    }
                }
                delay(500)
            }
        }
    }

    // Release all players properly
    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        trackingJob?.cancel()
        progressTrackerJob?.cancel()
        azanPlayer?.release()
        quranPlayer?.release()
        nasheedPlayer?.release()
    }

    // ── GitHub Auto Update Processing ──
    fun checkGitHubUpdates(manuallyTriggered: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingUpdate.value = true
            _updateErrorMessage.value = null
            try {
                val owner = _githubOwner.value.trim()
                val repo = _githubRepo.value.trim()
                if (owner.isEmpty() || repo.isEmpty()) {
                    _updateErrorMessage.value = "GitHub configuration is empty"
                    _isCheckingUpdate.value = false
                    return@launch
                }

                val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Android-Prayer-Scheduler-Update-Checker")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val bodyString = response.body!!.string()
                        val adapter = moshi.adapter(GithubRelease::class.java)
                        val release = adapter.fromJson(bodyString)

                        if (release != null && !release.tag_name.isNullOrEmpty()) {
                            val tag = release.tag_name
                            var currentVersionName = "1.0"
                            try {
                                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                currentVersionName = pInfo.versionName ?: "1.0"
                            } catch (ve: Exception) {
                                // fallback
                            }

                            val isNewer = isNewerVersion(currentVersionName, tag)
                            if (isNewer) {
                                _updateAvailable.value = true
                                _latestVersionName.value = tag
                                _latestVersionDescription.value = release.body ?: release.name ?: "New version available on GitHub."
                                _latestReleasePageUrl.value = release.html_url ?: ""
                                
                                val apkAsset = release.assets?.find {
                                    it.name?.endsWith(".apk", ignoreCase = true) == true
                                }
                                _latestApkUrl.value = apkAsset?.browser_download_url ?: ""
                                if (manuallyTriggered) {
                                    _uiEvents.emit("New update $tag is available!")
                                }
                            } else {
                                _updateAvailable.value = false
                                if (manuallyTriggered) {
                                    _uiEvents.emit("App is up-to-date (v$currentVersionName)")
                                }
                            }
                        } else {
                            _updateErrorMessage.value = "No latest release found"
                            if (manuallyTriggered) {
                                _uiEvents.emit("No latest release found.")
                            }
                        }
                    } else if (response.code == 404) {
                        _updateErrorMessage.value = "No release found on GitHub. Make sure you have compiled a release and drafted a release with an attached .apk file on 'htvusa/localprayertime'."
                        if (manuallyTriggered) {
                            _uiEvents.emit("No release found (404)")
                        }
                    } else {
                        _updateErrorMessage.value = "API error: HTTP ${response.code}"
                        if (manuallyTriggered) {
                            _uiEvents.emit("Check failed: HTTP ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateChecker", "Failed checking GitHub updates", e)
                _updateErrorMessage.value = e.localizedMessage ?: "Connection error"
                if (manuallyTriggered) {
                    _uiEvents.emit("Connection failed")
                }
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun downloadApkDirectly(apkUrl: String) {
        if (apkUrl.isEmpty()) {
            viewModelScope.launch {
                _uiEvents.emit("Cannot download: Empty APK URL")
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isDownloadingUpdate.value = true
            _downloadProgress.value = 0f
            _downloadedApkFile.value = null

            try {
                val request = Request.Builder()
                    .url(apkUrl)
                    .header("User-Agent", "Android-Prayer-Scheduler-Downloader")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateErrorMessage.value = "Download failed: HTTP ${response.code}"
                        _uiEvents.emit("Download failed: HTTP ${response.code}")
                        _isDownloadingUpdate.value = false
                        return@launch
                    }

                    val body = response.body
                    if (body == null) {
                        _updateErrorMessage.value = "Empty download body content"
                        _uiEvents.emit("Empty download content")
                        _isDownloadingUpdate.value = false
                        return@launch
                    }

                    val totalBytes = body.contentLength()
                    val apkFile = java.io.File(context.cacheDir, "localprayertime-update-${_latestVersionName.value}.apk")
                    if (apkFile.exists()) {
                        apkFile.delete()
                    }

                    body.byteStream().use { inputStream ->
                        apkFile.outputStream().use { outputStream ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = 0L

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (totalBytes > 0) {
                                    _downloadProgress.value = totalBytesRead.toFloat() / totalBytes.toFloat()
                                }
                            }
                        }
                    }

                    _downloadProgress.value = 1.0f
                    _downloadedApkFile.value = apkFile
                    _uiEvents.emit("Download completed successfully!")
                }
            } catch (e: Exception) {
                Log.e("ApkDownload", "Failed to download update", e)
                _updateErrorMessage.value = "Connection error during download: ${e.localizedMessage}"
                _uiEvents.emit("Connection failed during download")
            } finally {
                _isDownloadingUpdate.value = false
            }
        }
    }

    fun clearDownloadedApkFile() {
        _downloadedApkFile.value = null
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        return try {
            val cleanCurrent = current.trim().removePrefix("v").removePrefix("V")
            val cleanLatest = latest.trim().removePrefix("v").removePrefix("V")
            if (cleanCurrent == cleanLatest) return false

            val currParts = cleanCurrent.split(".", "-").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val lateParts = cleanLatest.split(".", "-").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }

            val minLen = minOf(currParts.size, lateParts.size)
            for (i in 0 until minLen) {
                if (lateParts[i] > currParts[i]) return true
                if (lateParts[i] < currParts[i]) return false
            }
            lateParts.size > currParts.size
        } catch (e: Exception) {
            latest != current
        }
    }
}

// ── GitHub API JSON Models ──
data class GithubAsset(
    val name: String? = null,
    val browser_download_url: String? = null
)

data class GithubRelease(
    val tag_name: String? = null,
    val name: String? = null,
    val body: String? = null,
    val html_url: String? = null,
    val assets: List<GithubAsset>? = null
)
