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

    private val _prayerTimeTextSize = MutableStateFlow(prefs.getString("prayer_time_text_size", "large") ?: "large")
    val prayerTimeTextSize: StateFlow<String> = _prayerTimeTextSize.asStateFlow()

    private val _appOrientation = MutableStateFlow(prefs.getString("app_orientation", "portrait") ?: "portrait")
    val appOrientation: StateFlow<String> = _appOrientation.asStateFlow()

    // Local caution offsets (minutes) for Fajr, Sunrise, and Maghrib
    private val _fajrOffset = MutableStateFlow(prefs.getInt("adj_fajr", 0))
    val fajrOffset: StateFlow<Int> = _fajrOffset.asStateFlow()

    private val _sunriseOffset = MutableStateFlow(prefs.getInt("adj_sunrise", 0))
    val sunriseOffset: StateFlow<Int> = _sunriseOffset.asStateFlow()

    private val _maghribOffset = MutableStateFlow(prefs.getInt("adj_maghrib", 0))
    val maghribOffset: StateFlow<Int> = _maghribOffset.asStateFlow()

    // ── GitHub Auto Update Configuration ──
    private val _githubOwner = MutableStateFlow(prefs.getString("github_owner", "htvusa") ?: "htvusa")
    val githubOwner: StateFlow<String> = _githubOwner.asStateFlow()

    private val _githubRepo = MutableStateFlow(prefs.getString("github_repo", "localprayertime") ?: "localprayertime")
    val githubRepo: StateFlow<String> = _githubRepo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    private val _updatePromptTrigger = MutableStateFlow(0)
    val updatePromptTrigger: StateFlow<Int> = _updatePromptTrigger.asStateFlow()

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
    private var wazPlayer: MediaPlayer? = null

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

    private val _azanAudioOptions = MutableStateFlow<List<String>>(emptyList())
    val azanAudioOptions: StateFlow<List<String>> = _azanAudioOptions.asStateFlow()

    private val _quranQaris = MutableStateFlow<List<String>>(emptyList())
    val quranQaris: StateFlow<List<String>> = _quranQaris.asStateFlow()

    private val _selectedQari = MutableStateFlow(prefs.getString("selected_qari", "mishari_rashid_al_afasy") ?: "mishari_rashid_al_afasy")
    val selectedQari: StateFlow<String> = _selectedQari.asStateFlow()

    private val _settingsRevision = MutableStateFlow(0)
    val settingsRevision: StateFlow<Int> = _settingsRevision.asStateFlow()

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

    // Waz Player Properties
    private val _wazTracks = MutableStateFlow<List<WazTrack>>(emptyList())
    val wazTracks: StateFlow<List<WazTrack>> = _wazTracks.asStateFlow()

    private val _wazIndex = MutableStateFlow(prefs.getInt("waz_idx", 0))
    val wazIndex: StateFlow<Int> = _wazIndex.asStateFlow()

    private val _wazStatus = MutableStateFlow("Ready")
    val wazStatus: StateFlow<String> = _wazStatus.asStateFlow()

    private val _wazIsPlaying = MutableStateFlow(false)
    val wazIsPlaying: StateFlow<Boolean> = _wazIsPlaying.asStateFlow()

    private val _wazProgress = MutableStateFlow(0f)
    val wazProgress: StateFlow<Float> = _wazProgress.asStateFlow()

    // Volume level state for players
    private val _quranVolume = MutableStateFlow(prefs.getFloat("quran_volume", 0.5f))
    val quranVolume: StateFlow<Float> = _quranVolume.asStateFlow()

    private val _nasheedVolume = MutableStateFlow(prefs.getFloat("nasheed_volume", 0.5f))
    val nasheedVolume: StateFlow<Float> = _nasheedVolume.asStateFlow()

    private val _wazVolume = MutableStateFlow(prefs.getFloat("waz_volume", 0.5f))
    val wazVolume: StateFlow<Float> = _wazVolume.asStateFlow()

    // ── Subscribe Masjid State ──
    private val _subscribedUser = MutableStateFlow(prefs.getString("sub_masjid_user", "") ?: "")
    val subscribedUser: StateFlow<String> = _subscribedUser.asStateFlow()

    private val _subscribedName = MutableStateFlow(prefs.getString("sub_masjid_name", "") ?: "")
    val subscribedName: StateFlow<String> = _subscribedName.asStateFlow()

    private val _subscribedData = MutableStateFlow<Map<String, String>>(emptyMap())
    val subscribedData: StateFlow<Map<String, String>> = _subscribedData.asStateFlow()

    private val _subscribedHistory = MutableStateFlow<List<String>>(
        prefs.getStringSet("sub_masjid_history", emptySet())?.toList() ?: emptyList()
    )
    val subscribedHistory: StateFlow<List<String>> = _subscribedHistory.asStateFlow()

    private val _masjidUsers = MutableStateFlow<List<MasjidUser>>(emptyList())
    val masjidUsers: StateFlow<List<MasjidUser>> = _masjidUsers.asStateFlow()

    private val _slidesList = MutableStateFlow<List<String>>(emptyList())
    val slidesList: StateFlow<List<String>> = _slidesList.asStateFlow()

    private val _slidesLoading = MutableStateFlow(false)
    val slidesLoading: StateFlow<Boolean> = _slidesLoading.asStateFlow()

    private val _masjidLoading = MutableStateFlow(false)
    val masjidLoading: StateFlow<Boolean> = _masjidLoading.asStateFlow()

    private val _masjidStatusMessage = MutableStateFlow("")
    val masjidStatusMessage: StateFlow<String> = _masjidStatusMessage.asStateFlow()

    private val _masjidStatusType = MutableStateFlow("info") // "ok", "err", "info"
    val masjidStatusType: StateFlow<String> = _masjidStatusType.asStateFlow()

    fun refreshSubscribedMasjidData() {
        val map = mutableMapOf<String, String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("sub_masjid_field_") && value is String) {
                map[key.removePrefix("sub_masjid_field_")] = value
            }
        }
        _subscribedData.value = map
    }


    // ── Active Background Jobs ──
    private var countdownJob: Job? = null
    private var trackingJob: Job? = null
    private var progressTrackerJob: Job? = null
    private var refresh30SecondsJob: Job? = null
    private var lastPlayedAzanKey: String? = null

    init {
        // Initial load of subscribed masjid data
        refreshSubscribedMasjidData()
        syncSlides()

        // Prepare primary countdown and tracker loop
        startCountdownTimer()
        startPeriodicCheckers()
        start30SecondsRefreshTimer()
        
        // Initial prayer, weather extraction and nasheeds fetching
        fetchBackupNasheedManifest()
        fetchWazManifest()
        fetchAzanAudioOptions()
        fetchQuranQaris()
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

    fun updatePrayerTimeTextSize(newSize: String) {
        _prayerTimeTextSize.value = newSize
        prefs.edit().putString("prayer_time_text_size", newSize).apply()
        viewModelScope.launch { _uiEvents.emit("Prayer time font size updated to $newSize") }
    }

    fun updateFajrOffset(minutes: Int) {
        _fajrOffset.value = minutes
        prefs.edit().putInt("adj_fajr", minutes).apply()
        updateSchedules()
        viewModelScope.launch { _uiEvents.emit("Fajr adjustment updated to $minutes min") }
    }

    fun updateSunriseOffset(minutes: Int) {
        _sunriseOffset.value = minutes
        prefs.edit().putInt("adj_sunrise", minutes).apply()
        updateSchedules()
        viewModelScope.launch { _uiEvents.emit("Sunrise adjustment updated to $minutes min") }
    }

    fun updateMaghribOffset(minutes: Int) {
        _maghribOffset.value = minutes
        prefs.edit().putInt("adj_maghrib", minutes).apply()
        updateSchedules()
        viewModelScope.launch { _uiEvents.emit("Maghrib adjustment updated to $minutes min") }
    }

    fun updateAppOrientation(newOrientation: String) {
        _appOrientation.value = newOrientation
        prefs.edit().putString("app_orientation", newOrientation).apply()
        viewModelScope.launch { _uiEvents.emit("Screen mode set to $newOrientation") }
    }

    private var dateResetJob: Job? = null

    fun changeSelectedDate(date: LocalDate) {
        _selectedDate.value = date
        updateSchedules()

        dateResetJob?.cancel()
        if (date != LocalDate.now()) {
            dateResetJob = viewModelScope.launch {
                delay(30000)
                _selectedDate.value = LocalDate.now()
                updateSchedules()
            }
        }
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
                    val baseMins = h * 60 + m + _sunriseOffset.value // Use adjusted Sunrise base so Ishraq is updated as well
                    val totalMins = baseMins + 22
                    val derivedH = ((totalMins / 60) % 24 + 24) % 24
                    val derivedM = (totalMins % 60 + 60) % 60
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
                    
                    var totalMins = h * 60 + m
                    if (apiKey == "Fajr") {
                        totalMins += _fajrOffset.value
                    } else if (apiKey == "Sunrise") {
                        totalMins += _sunriseOffset.value
                    } else if (apiKey == "Maghrib") {
                        totalMins += _maghribOffset.value
                    }

                    val finalMins = (totalMins % 1440 + 1440) % 1440
                    val finalH = finalMins / 60
                    val finalM = finalMins % 60

                    val ldt = LocalDateTime.of(date, LocalTime.of(finalH, finalM))
                    val zdt = ZonedDateTime.of(ldt, defaultZone)
                    mappedList.add(PrayerTime(dispName, apiKey, zdt, String.format(Locale.US, "%02d:%02d", finalH, finalM)))
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

                // Smooth daily reload at/after 12:01 AM so the user doesn't need to reopen the app
                val systemDate = LocalDate.now()
                val systemTime = LocalTime.now()
                val isResetJobActive = dateResetJob?.isActive == true
                if (!isResetJobActive && systemDate != _selectedDate.value && !systemTime.isBefore(LocalTime.of(0, 1))) {
                    Log.d("DailyReload", "Smooth daily reload initiated at 12:01 AM or later")
                    _selectedDate.value = systemDate
                    updateSchedules()
                    fetchWeatherDetails()
                    // Fetch latest masjid jamat times at 12:01 AM rollover as well
                    val currentUser = _subscribedUser.value
                    if (currentUser.isNotEmpty()) {
                        connectMasjidUsername(currentUser, silent = true)
                    }
                }

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
                try {
                    checkGitHubUpdates(manuallyTriggered = false)
                } catch (e: Exception) {
                    // Ignore background check errors
                }
            }
        }
    }

    private fun start30SecondsRefreshTimer() {
        refresh30SecondsJob?.cancel()
        refresh30SecondsJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(30000) // Refresh precisely every 30 seconds
                try {
                    // Refresh both base schedules and subscribed masjid jamat times
                    updateSchedules()
                    
                    val currentUser = _subscribedUser.value
                    if (currentUser.isNotEmpty()) {
                        connectMasjidUsername(currentUser, silent = true)
                        syncSlides()
                    }
                } catch (e: Exception) {
                    Log.e("30sRefresher", "Error checking for updates in background: ${e.message}")
                }
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
                    if (isPrayerAzanEnabled(key)) {
                        triggerAzanMediaAudio(key)
                        lastPlayedAzanKey = key
                    }
                }
            }
        } else {
            _nextPrayerCountdown.value = "All done ✦"
        }
    }

    // Play Azan MP3 file via direct MediaPlayer stream
    private fun triggerAzanMediaAudio(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            azanPlayer?.release()
            azanPlayer = null

            val preferredFile = getPrayerAzanFile(key)
            val urls = mutableListOf<String>()
            if (preferredFile.isNotEmpty()) {
                urls.add("https://raw.githubusercontent.com/htvusa/pa/master/azan/$preferredFile")
            }
            if (key.equals("Fajr", ignoreCase = true)) {
                urls.add("https://raw.githubusercontent.com/htvusa/pa/master/fajrAzan.mp3")
                urls.add("https://raw.githubusercontent.com/htvusa/pa/master/azan.mp3")
            } else {
                urls.add("https://raw.githubusercontent.com/htvusa/pa/master/azan.mp3")
            }
            urls.add("https://www.islamcan.com/audio/azan/rema.mp3")
            urls.add("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")

            for (url in urls) {
                try {
                    Log.d("MediaPlayer", "Streaming Azan audio from: $url")
                    val player = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(url)
                        prepare()
                        start()
                    }
                    azanPlayer = player
                    if (key.equals("Fajr", ignoreCase = true) && url.contains("fajrAzan.mp3")) {
                        _uiEvents.emit("Fajr Azan activated at prayer time!")
                    } else {
                        _uiEvents.emit("$key Azan activated: $preferredFile")
                    }
                    return@launch
                } catch (e: Exception) {
                    Log.e("MediaPlayer", "Failed playing azan audio streaming from $url, trying next...", e)
                }
            }
            Log.e("MediaPlayer", "All Azan streaming URLs failed.")
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
            viewModelScope.launch { 
                _quranStatus.value = "Loading Surah..."
                _quranProgress.value = 0f
            }
            
            quranPlayer?.stop()
            quranPlayer?.release()
            quranPlayer = null
            
            nasheedPlayer?.stop()
            nasheedPlayer?.release()
            nasheedPlayer = null
            _nasheedIsPlaying.value = false
            _nasheedStatus.value = "Ready"

            wazPlayer?.stop()
            wazPlayer?.release()
            wazPlayer = null
            _wazIsPlaying.value = false
            _wazStatus.value = "Ready"

            val fileCode = String.format(Locale.US, "%03d", finalIdx)
            val qariVal = _selectedQari.value
            val urls = mutableListOf<String>()
            if (qariVal.isNotEmpty()) {
                urls.add("https://raw.githubusercontent.com/htvusa/pa/master/quran/$qariVal/$fileCode.mp3")
            }
            urls.add("https://raw.githubusercontent.com/htvusa/pa/master/quran/$fileCode.mp3")
            urls.add("https://download.quranicaudio.com/quran/mishari_rashid_al_afasy/$fileCode.mp3")

            for (url in urls) {
                try {
                    Log.d("MediaPlayer", "Streaming quran audio from: $url")
                    val player = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(url)
                        prepare()
                    }
                    
                    val vol = _quranVolume.value
                    player.setVolume(vol, vol)
                    player.start()
                    
                    quranPlayer = player
                    viewModelScope.launch {
                        _quranStatus.value = "Playing"
                        _quranIsPlaying.value = true
                    }
                    
                    player.setOnCompletionListener {
                        viewModelScope.launch {
                            _quranIsPlaying.value = false
                            _quranProgress.value = 0f
                            playQuranNext()
                        }
                    }
                    player.setOnErrorListener { _, _, _ ->
                        viewModelScope.launch { _quranStatus.value = "Playback failure" }
                        true
                    }
                    
                    trackPlayerProgress()
                    return@launch
                } catch (e: Exception) {
                    Log.e("MediaPlayer", "Failed starting quran surah player from $url, trying next...", e)
                }
            }
            viewModelScope.launch {
                _quranStatus.value = "Failed streaming"
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
                val url = "https://raw.githubusercontent.com/htvusa/pa/master/nashed/manifest.json"
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
                                NasheedTrack("https://raw.githubusercontent.com/htvusa/pa/master/nashed/$cleanName", title)
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
            NasheedTrack("https://download.quranicaudio.com/quran/mishari_rashid_al_afasy/001.mp3", "Surah Al-Fatihah"),
            NasheedTrack("https://download.quranicaudio.com/quran/mishari_rashid_al_afasy/112.mp3", "Surah Al-Ikhlas"),
            NasheedTrack("https://download.quranicaudio.com/quran/mishari_rashid_al_afasy/113.mp3", "Surah Al-Falaq"),
            NasheedTrack("https://download.quranicaudio.com/quran/mishari_rashid_al_afasy/114.mp3", "Surah An-Nas")
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
            viewModelScope.launch {
                _nasheedStatus.value = "Loading track..."
                _nasheedProgress.value = 0f
            }

            nasheedPlayer?.stop()
            nasheedPlayer?.release()
            nasheedPlayer = null

            quranPlayer?.stop()
            quranPlayer?.release()
            quranPlayer = null
            _quranIsPlaying.value = false
            _quranStatus.value = "Ready"

            wazPlayer?.stop()
            wazPlayer?.release()
            wazPlayer = null
            _wazIsPlaying.value = false
            _wazStatus.value = "Ready"

            val track = tracks[finalIdx]
            Log.d("MediaPlayer", "Streaming Nasheed audio from: ${track.file}")

            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(track.file)
                    prepare()
                }

                val vol = _nasheedVolume.value
                player.setVolume(vol, vol)
                player.start()

                nasheedPlayer = player
                viewModelScope.launch {
                    _nasheedStatus.value = "${finalIdx + 1} / ${tracks.size}"
                    _nasheedIsPlaying.value = true
                }

                player.setOnCompletionListener {
                    viewModelScope.launch {
                        _nasheedIsPlaying.value = false
                        _nasheedProgress.value = 0f
                        playNasheedNext()
                    }
                }
                player.setOnErrorListener { _, _, _ ->
                    viewModelScope.launch { _nasheedStatus.value = "Unavailable" }
                    true
                }

                trackPlayerProgress()
            } catch (e: Exception) {
                Log.e("MediaPlayer", "Failed playing nasheed stream for ${track.file}", e)
                viewModelScope.launch {
                    _nasheedStatus.value = "Playback error"
                }
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

    // ── Waz Play Operations ──
    fun setWazVolume(v: Float) {
        _wazVolume.value = v
        prefs.edit().putFloat("waz_volume", v).apply()
        wazPlayer?.setVolume(v, v)
    }

    private fun fetchWazManifest() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://raw.githubusercontent.com/htvusa/pa/master/waz/manifest.json"
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
                                WazTrack("https://raw.githubusercontent.com/htvusa/pa/master/waz/$cleanName", title)
                            }
                            _wazTracks.value = mapped
                            return@launch
                        }
                    }
                }
                produceFallbackWazList()
            } catch (e: Exception) {
                produceFallbackWazList()
            }
        }
    }

    private fun produceFallbackWazList() {
        val defaultList = listOf(
            WazTrack("https://download.quranicaudio.com/quran/mishari_rashid_al_afasy/001.mp3", "Allama Fultali - Waqt & Amal")
        )
        _wazTracks.value = defaultList
    }

    fun playWaz(index: Int) {
        val tracks = _wazTracks.value
        if (tracks.isEmpty()) return
        val finalIdx = index.coerceIn(0, tracks.size - 1)
        _wazIndex.value = finalIdx
        prefs.edit().putInt("waz_idx", finalIdx).apply()

        viewModelScope.launch(Dispatchers.IO) {
            viewModelScope.launch {
                _wazStatus.value = "Loading track..."
                _wazProgress.value = 0f
            }

            wazPlayer?.stop()
            wazPlayer?.release()
            wazPlayer = null

            quranPlayer?.stop()
            quranPlayer?.release()
            quranPlayer = null
            _quranIsPlaying.value = false
            _quranStatus.value = "Ready"

            nasheedPlayer?.stop()
            nasheedPlayer?.release()
            nasheedPlayer = null
            _nasheedIsPlaying.value = false
            _nasheedStatus.value = "Ready"

            val track = tracks[finalIdx]
            Log.d("MediaPlayer", "Streaming Waz audio from: ${track.file}")

            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(track.file)
                    prepare()
                }

                val vol = _wazVolume.value
                player.setVolume(vol, vol)
                player.start()

                wazPlayer = player
                viewModelScope.launch {
                    _wazStatus.value = "${finalIdx + 1} / ${tracks.size}"
                    _wazIsPlaying.value = true
                }

                player.setOnCompletionListener {
                    viewModelScope.launch {
                        _wazIsPlaying.value = false
                        _wazProgress.value = 0f
                        playWazNext()
                    }
                }
                player.setOnErrorListener { _, _, _ ->
                    viewModelScope.launch { _wazStatus.value = "Unavailable" }
                    true
                }

                trackPlayerProgress()
            } catch (e: Exception) {
                Log.e("MediaPlayer", "Failed playing waz stream for ${track.file}", e)
                viewModelScope.launch {
                    _wazStatus.value = "Playback error"
                }
            }
        }
    }

    fun toggleWazPlay() {
        val player = wazPlayer
        if (player != null) {
            if (_wazIsPlaying.value) {
                player.pause()
                _wazIsPlaying.value = false
                _wazStatus.value = "Paused"
            } else {
                player.start()
                _wazIsPlaying.value = true
                _wazStatus.value = "Playing"
            }
        } else {
            playWaz(_wazIndex.value)
        }
    }

    fun playWazNext() {
        val size = _wazTracks.value.size
        if (size == 0) return
        val nextIdx = (_wazIndex.value + 1) % size
        playWaz(nextIdx)
    }

    fun playWazPrev() {
        val size = _wazTracks.value.size
        if (size == 0) return
        val prevIdx = if (_wazIndex.value <= 0) size - 1 else _wazIndex.value - 1
        playWaz(prevIdx)
    }

    fun seekWaz(progressPercent: Float) {
        val player = wazPlayer ?: return
        val pos = (progressPercent * player.duration).toInt()
        player.seekTo(pos)
        _wazProgress.value = progressPercent
    }

    fun fetchMasjidUsers() {
        viewModelScope.launch(Dispatchers.IO) {
            _masjidLoading.value = true
            _masjidStatusMessage.value = "Loading users..."
            _masjidStatusType.value = "info"
            try {
                val url = "https://daarulhikmahny.org/lapp/users.php?api_key=dhny-display-2025"
                val request = Request.Builder()
                    .url(url)
                    .header("X-API-Key", "dhny-display-2025")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val bodyString = response.body!!.string()
                        val adapter = moshi.adapter(UsersResponse::class.java)
                        val res = adapter.fromJson(bodyString)
                        if (res != null && res.success && res.users != null) {
                            _masjidUsers.value = res.users
                            _masjidStatusMessage.value = ""
                        } else {
                            _masjidStatusMessage.value = "Failed to load users"
                            _masjidStatusType.value = "err"
                        }
                    } else {
                        _masjidStatusMessage.value = "HTTP error: ${response.code}"
                        _masjidStatusType.value = "err"
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error fetching masjid users", e)
                _masjidStatusMessage.value = "Connection failed"
                _masjidStatusType.value = "err"
            } finally {
                _masjidLoading.value = false
            }
        }
    }

    fun connectMasjidUsername(newUser: String, silent: Boolean = false) {
        val cleanUser = newUser.trim()
        if (cleanUser.isEmpty()) {
            if (!silent) {
                _masjidStatusMessage.value = "Please enter a username"
                _masjidStatusType.value = "err"
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (!silent) {
                _masjidLoading.value = true
                _masjidStatusMessage.value = "Connecting..."
                _masjidStatusType.value = "info"
            }
            try {
                val url = "https://daarulhikmahny.org/lapp/api.php?api_key=dhny-display-2025&username=$cleanUser"
                val request = Request.Builder()
                    .url(url)
                    .header("X-API-Key", "dhny-display-2025")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val bodyString = response.body!!.string()
                        val adapter = moshi.adapter(MosqueApiResponse::class.java)
                        val res = adapter.fromJson(bodyString)
                        if (res != null && res.success && res.data != null) {
                            val data = res.data
                            val masjidName = data["name"] ?: cleanUser
                            
                            _subscribedUser.value = cleanUser
                            _subscribedName.value = masjidName
                            
                            prefs.edit()
                                .putString("sub_masjid_user", cleanUser)
                                .putString("sub_masjid_name", masjidName)
                                .putString("sub_masjid_histname_$cleanUser", masjidName)
                                .apply()

                            // Store individual data items in shared preferences as requested in HTML layout
                            val editor = prefs.edit()
                            data.forEach { (key, value) ->
                                editor.putString("sub_masjid_field_$key", value)
                            }
                            editor.apply()

                            // Add to history list as requested
                            val currentHistoryList = _subscribedHistory.value.toMutableList()
                            currentHistoryList.remove(cleanUser)
                            currentHistoryList.add(0, cleanUser)
                            if (currentHistoryList.size > 8) {
                                currentHistoryList.removeAt(currentHistoryList.size - 1)
                            }
                            _subscribedHistory.value = currentHistoryList
                            prefs.edit().putStringSet("sub_masjid_history", currentHistoryList.toSet()).apply()

                            _masjidStatusMessage.value = "Connected as $cleanUser"
                            _masjidStatusType.value = "ok"
                            refreshSubscribedMasjidData()
                            syncSlides()
                        } else {
                            _masjidStatusMessage.value = "User not found or API error"
                            _masjidStatusType.value = "err"
                        }
                    } else {
                        _masjidStatusMessage.value = "HTTP error: ${response.code}"
                        _masjidStatusType.value = "err"
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error connecting masjid", e)
                _masjidStatusMessage.value = "Connection failed"
                _masjidStatusType.value = "err"
            } finally {
                _masjidLoading.value = false
            }
        }
    }

    fun syncSlides() {
        val username = _subscribedUser.value
        if (username.isEmpty()) {
            _slidesList.value = emptyList()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _slidesLoading.value = true
            try {
                val url = "https://daarulhikmahny.org/lapp/slides.php?api_key=dhny-display-2025&username=$username&_=${System.currentTimeMillis()}"
                val request = Request.Builder()
                    .url(url)
                    .header("X-API-Key", "dhny-display-2025")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val bodyString = response.body!!.string()
                        Log.d("syncSlides", "Response string: $bodyString")
                        
                        val list = mutableListOf<String>()
                        try {
                            val type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                            val adapter = moshi.adapter<Map<String, Any>>(type)
                            val resMap = adapter.fromJson(bodyString)
                            if (resMap != null) {
                                val rawSlides = resMap["slides"] ?: resMap["data"] ?: resMap["images"]
                                if (rawSlides is List<*>) {
                                    for (item in rawSlides) {
                                        if (item is String) {
                                            list.add(item)
                                        } else if (item is Map<*, *>) {
                                            val urlVal = (item["url"] ?: item["image"] ?: item["file"] ?: item["src"])?.toString()
                                            if (!urlVal.isNullOrEmpty()) {
                                                list.add(urlVal)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("syncSlides", "Error parsing map: ${e.message}")
                        }

                        if (list.isEmpty()) {
                            try {
                                val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, java.lang.Object::class.java)
                                val listAdapter = moshi.adapter<List<Any>>(listType)
                                val parsedList = listAdapter.fromJson(bodyString)
                                if (parsedList != null) {
                                    for (item in parsedList) {
                                        if (item is String) {
                                            list.add(item)
                                        } else if (item is Map<*, *>) {
                                            val urlVal = (item["url"] ?: item["image"] ?: item["file"] ?: item["src"])?.toString()
                                            if (!urlVal.isNullOrEmpty()) {
                                                list.add(urlVal)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("syncSlides", "Error parsing list: ${e.message}")
                            }
                        }

                        if (list.isEmpty() && !bodyString.trim().startsWith("{") && !bodyString.trim().startsWith("[")) {
                            bodyString.split("\n").map { it.trim() }.filter { it.isNotEmpty() && (it.startsWith("http") || it.contains(".")) }.forEach {
                                list.add(it)
                            }
                        }

                        // Robust fallback regex to parse any string containing image paths from the raw endpoint output
                        if (list.isEmpty()) {
                            val regexHttp = """https?://[^\s"']+\.(?:png|jpg|jpeg|gif|webp|bmp)""".toRegex(RegexOption.IGNORE_CASE)
                            regexHttp.findAll(bodyString).forEach {
                                list.add(it.value)
                            }
                        }
                        if (list.isEmpty()) {
                            val regexRelative = """(?:uploads|slides)/[^\s"']+\.(?:png|jpg|jpeg|gif|webp|bmp)""".toRegex(RegexOption.IGNORE_CASE)
                            regexRelative.findAll(bodyString).forEach {
                                list.add(it.value)
                            }
                        }

                        val cleanList = list.distinct().map { rawUrl ->
                            val trimmed = rawUrl.trim()
                            val imageFilename = trimmed.substringAfterLast("/")
                            val slideUrl = "https://daarulhikmahny.org/lapp/usernames/$username/$imageFilename"
                            Log.d("syncSlides", "Formatted slide URL: $slideUrl")
                            slideUrl
                        }
                        
                        withContext(Dispatchers.Main) {
                            _slidesList.value = cleanList
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("syncSlides", "Error fetching slides: ${e.message}", e)
            } finally {
                _slidesLoading.value = false
            }
        }
    }

    fun clearSubscribedMasjidFields() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("sub_masjid_field_") }.forEach {
            editor.remove(it)
        }
        editor.apply()
        _subscribedData.value = emptyMap()
        _slidesList.value = emptyList()
    }

    fun removeMasjidFromHistory(user: String) {
        val currentHistoryList = _subscribedHistory.value.toMutableList()
        currentHistoryList.remove(user)
        _subscribedHistory.value = currentHistoryList
        prefs.edit().putStringSet("sub_masjid_history", currentHistoryList.toSet()).apply()

        if (_subscribedUser.value == user) {
            val nextUser = currentHistoryList.firstOrNull() ?: ""
            if (nextUser.isNotEmpty()) {
                connectMasjidUsername(nextUser, silent = true)
            } else {
                _subscribedUser.value = ""
                _subscribedName.value = ""
                clearSubscribedMasjidFields()
                prefs.edit().remove("sub_masjid_user").remove("sub_masjid_name").apply()
            }
        }
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
                wazPlayer?.let { p ->
                    if (p.isPlaying && p.duration > 0) {
                        _wazProgress.value = p.currentPosition.toFloat() / p.duration.toFloat()
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
        wazPlayer?.release()
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
                                _updatePromptTrigger.value = _updatePromptTrigger.value + 1
                                _latestVersionName.value = tag
                                _latestVersionDescription.value = release.body ?: release.name ?: "New version available on GitHub."
                                _latestReleasePageUrl.value = release.html_url ?: ""
                                
                                // Direct raw APK download URL requested by user
                                _latestApkUrl.value = "https://raw.githubusercontent.com/htvusa/localprayertime/main/.build-outputs/app-debug.apk"
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
            val cleanCurrent = current.trim().removePrefix("v").removePrefix("V")
            val cleanLatest = latest.trim().removePrefix("v").removePrefix("V")
            cleanLatest != cleanCurrent
        }
    }

    private fun fetchAzanAudioOptions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://raw.githubusercontent.com/htvusa/pa/master/azan/manifest.json"
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val bodyString = response.body!!.string()
                        val listAdapter = moshi.adapter(List::class.java)
                        val items = listAdapter.fromJson(bodyString) as? List<*>
                        if (items != null) {
                            val list = items.mapNotNull { it?.toString() }
                            if (list.isNotEmpty()) {
                                _azanAudioOptions.value = list
                                return@launch
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AzanOptions", "Failed loading azan options manifest", e)
            }
            _azanAudioOptions.value = listOf("fajrAzan.mp3", "azan.mp3")
        }
    }

    private fun fetchQuranQaris() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://raw.githubusercontent.com/htvusa/pa/master/quran/manifest.json"
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val bodyString = response.body!!.string()
                        try {
                            val listAdapter = moshi.adapter(List::class.java)
                            val items = listAdapter.fromJson(bodyString) as? List<*>
                            if (items != null) {
                                val list = items.mapNotNull { it?.toString() }
                                if (list.isNotEmpty()) {
                                    _quranQaris.value = list
                                    if (_selectedQari.value.isEmpty() || !list.contains(_selectedQari.value)) {
                                        _selectedQari.value = list[0]
                                    }
                                    return@launch
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("QuranQari", "Failed parsing manifest as list of strings, trying list of maps", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("QuranQari", "Failed loading quran manifest", e)
            }
            val fallback = listOf("mishari_rashid_al_afasy", "abdul_basit_murattal", "saad_al_ghamdi", "maher_al_muaiqly")
            _quranQaris.value = fallback
            if (_selectedQari.value.isEmpty() || !fallback.contains(_selectedQari.value)) {
                _selectedQari.value = fallback[0]
            }
        }
    }

    fun updateSelectedQari(qari: String) {
        _selectedQari.value = qari
        prefs.edit().putString("selected_qari", qari).apply()
        _settingsRevision.value += 1
        viewModelScope.launch { _uiEvents.emit("Qari reciter updated to $qari") }
    }

    fun isPrayerAzanEnabled(key: String): Boolean {
        _settingsRevision.value
        return prefs.getBoolean("azan_enabled_$key", true)
    }

    fun setPrayerAzanEnabled(key: String, enabled: Boolean) {
        prefs.edit().putBoolean("azan_enabled_$key", enabled).apply()
        _settingsRevision.value += 1
        viewModelScope.launch {
            _uiEvents.emit("${if (enabled) "Enabled" else "Disabled"} notifications for $key")
        }
    }

    fun getPrayerAzanFile(key: String): String {
        _settingsRevision.value
        val isZuhrToIsha = key in listOf("Dhuhr", "Asr", "Maghrib", "Isha")
        return if (isZuhrToIsha) {
            prefs.getString("azan_select_shared_zuhr_isha", "azan.mp3") ?: "azan.mp3"
        } else {
            val defaultVal = if (key == "Fajr") "fajrAzan.mp3" else "azan.mp3"
            prefs.getString("azan_select_$key", defaultVal) ?: defaultVal
        }
    }

    fun setPrayerAzanFile(key: String, file: String) {
        val isZuhrToIsha = key in listOf("Dhuhr", "Asr", "Maghrib", "Isha")
        if (isZuhrToIsha) {
            prefs.edit().putString("azan_select_shared_zuhr_isha", file).apply()
        } else {
            prefs.edit().putString("azan_select_$key", file).apply()
        }
        _settingsRevision.value += 1
        viewModelScope.launch {
            _uiEvents.emit("Updated azan option: $file")
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
