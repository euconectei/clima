package app.clima.data

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

data class Place(
    val name: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    /** Entrada criada pelo botão de localização atual; só existe uma por vez. */
    val isGps: Boolean = false,
) {
    val label get() = if (region.isBlank()) name else "$name, $region"

    /** Identifica o lugar pelas coordenadas (arredondadas), já que nome e região podem variar. */
    val key get() = "%.2f,%.2f".format(Locale.US, latitude, longitude)

    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("region", region)
        .put("lat", latitude)
        .put("lon", longitude)
        .put("gps", isGps)

    companion object {
        fun fromJson(o: JSONObject) =
            Place(o.getString("name"), o.optString("region"), o.getDouble("lat"), o.getDouble("lon"), o.optBoolean("gps"))
    }
}

data class Hour(
    val time: LocalDateTime,
    val temp: Int,
    val code: Int,
    val rainChance: Int,
    val isDay: Boolean,
)

data class Day(
    val date: LocalDate,
    val code: Int,
    val max: Int,
    val min: Int,
    val rainChance: Int,
    val rainMm: Double,
    val uvMax: Double,
)

data class Weather(
    val place: Place,
    val fetchedAt: Long,
    val temp: Int,
    val feelsLike: Int,
    val humidity: Int,
    val windKmh: Int,
    val windGustKmh: Int,
    val windDirection: Int,
    val pressureHpa: Int,
    val cloudCover: Int,
    val rainNowMm: Double,
    val code: Int,
    val isDay: Boolean,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val hourly: List<Hour>,
    val daily: List<Day>,
)
