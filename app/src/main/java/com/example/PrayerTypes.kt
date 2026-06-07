package com.example

import com.squareup.moshi.JsonClass
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

// Standard prayer names as represented in the UI
data class PrayerTime(
    val name: String,
    val key: String,
    val time: ZonedDateTime,
    val ts: String, // format "HH:mm"
    val isDerived: Boolean = false
)

enum class Madhab(val id: Int, val displayName: String) {
    SHAFI(0, "Shafi'i (Standard)"),
    HANAFI(1, "Hanafi")
}

data class CalculationMethod(val id: Int, val name: String)

val CALCULATION_METHODS = listOf(
    CalculationMethod(2, "ISNA — North America"),
    CalculationMethod(0, "Karachi — South Asia"),
    CalculationMethod(1, "MWL — Muslim World League"),
    CalculationMethod(3, "ISNA — Alternate"),
    CalculationMethod(4, "Umm al-Qura, Makkah"),
    CalculationMethod(5, "Egyptian General Authority"),
    CalculationMethod(7, "UOIF — France"),
    CalculationMethod(8, "Gulf Region"),
    CalculationMethod(9, "Kuwait"),
    CalculationMethod(12, "Qatar"),
    CalculationMethod(13, "Singapore"),
    CalculationMethod(99, "Custom")
)

// Retrofit/Moshi API Models
@JsonClass(generateAdapter = true)
data class AladhanResponse(
    val code: Int,
    val status: String,
    val data: List<AladhanDayData>
)

@JsonClass(generateAdapter = true)
data class AladhanDayData(
    val timings: Map<String, String>,
    val date: AladhanDate
)

@JsonClass(generateAdapter = true)
data class AladhanDate(
    val readable: String,
    val hijri: AladhanHijri
)

@JsonClass(generateAdapter = true)
data class AladhanHijri(
    val day: String,
    val month: AladhanHijriMonth,
    val year: String
)

@JsonClass(generateAdapter = true)
data class AladhanHijriMonth(
    val number: Int,
    val en: String,
    val ar: String? = null
)

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    val current: WeatherCurrent
)

@JsonClass(generateAdapter = true)
data class WeatherCurrent(
    val temperature_2m: Double,
    val wind_speed_10m: Double,
    val weathercode: Int
)

data class NasheedTrack(
    val file: String,
    val title: String
)

data class WazTrack(
    val file: String,
    val title: String
)

@JsonClass(generateAdapter = true)
data class MasjidUser(
    val username: String,
    val name: String?,
    val city: String?,
    val state: String?
)

@JsonClass(generateAdapter = true)
data class UsersResponse(
    val success: Boolean,
    val users: List<MasjidUser>?
)

@JsonClass(generateAdapter = true)
data class MosqueApiResponse(
    val success: Boolean,
    val data: Map<String, String>?
)

