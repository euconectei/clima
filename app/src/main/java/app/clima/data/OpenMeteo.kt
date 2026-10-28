package app.clima.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

/** Cliente da Open-Meteo (https://open-meteo.com): gratuita, sem chave de API. */
object OpenMeteo {
    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODING = "https://geocoding-api.open-meteo.com/v1/search"

    suspend fun forecast(place: Place): String = get(
        "$FORECAST?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_gusts_10m,wind_direction_10m,pressure_msl,cloud_cover,precipitation,is_day" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,uv_index_max,sunrise,sunset" +
            "&timezone=auto&forecast_days=7",
    )

    suspend fun search(query: String): List<Place> {
        val raw = get("$GEOCODING?name=${URLEncoder.encode(query, "UTF-8")}&count=8&language=pt&format=json")
        val results = JSONObject(raw).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            Place(
                name = r.getString("name"),
                region = listOf(r.optString("admin1"), r.optString("country"))
                    .filter { it.isNotBlank() }
                    .joinToString(", "),
                latitude = r.getDouble("latitude"),
                longitude = r.getDouble("longitude"),
            )
        }
    }

    fun parse(place: Place, raw: String, fetchedAt: Long): Weather {
        val root = JSONObject(raw)
        val cur = root.getJSONObject("current")
        val hourly = root.getJSONObject("hourly")
        val daily = root.getJSONObject("daily")

        // Os horários vêm no fuso local do lugar ("2026-10-27T14:00"), então comparar como texto funciona.
        val times = hourly.getJSONArray("time")
        val nowKey = cur.getString("time").substring(0, 13) + ":00"
        val start = (0 until times.length()).firstOrNull { times.getString(it) >= nowKey } ?: 0
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val rain = hourly.getJSONArray("precipitation_probability")
        val isDay = hourly.getJSONArray("is_day")
        val hours = (start until minOf(start + 24, times.length())).map { i ->
            Hour(
                time = LocalDateTime.parse(times.getString(i)),
                temp = temps.optDouble(i).roundToInt(),
                code = codes.optInt(i),
                rainChance = rain.optInt(i),
                isDay = isDay.optInt(i) == 1,
            )
        }

        val days = daily.getJSONArray("time")
        val dCodes = daily.getJSONArray("weather_code")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dRain = daily.getJSONArray("precipitation_probability_max")
        val dMm = daily.optJSONArray("precipitation_sum")
        val dUv = daily.optJSONArray("uv_index_max")
        val dayList = (0 until days.length()).map { i ->
            Day(
                date = LocalDate.parse(days.getString(i)),
                code = dCodes.optInt(i),
                max = dMax.optDouble(i).roundToInt(),
                min = dMin.optDouble(i).roundToInt(),
                rainChance = dRain.optInt(i),
                rainMm = dMm?.optDouble(i, 0.0) ?: 0.0,
                uvMax = dUv?.optDouble(i, 0.0) ?: 0.0,
            )
        }

        return Weather(
            place = place,
            fetchedAt = fetchedAt,
            temp = cur.getDouble("temperature_2m").roundToInt(),
            feelsLike = cur.getDouble("apparent_temperature").roundToInt(),
            humidity = cur.getInt("relative_humidity_2m"),
            windKmh = cur.getDouble("wind_speed_10m").roundToInt(),
            windGustKmh = cur.optDouble("wind_gusts_10m", 0.0).roundToInt(),
            windDirection = cur.optInt("wind_direction_10m"),
            pressureHpa = cur.optDouble("pressure_msl", 0.0).roundToInt(),
            cloudCover = cur.optInt("cloud_cover"),
            rainNowMm = cur.optDouble("precipitation", 0.0),
            code = cur.getInt("weather_code"),
            isDay = cur.getInt("is_day") == 1,
            sunrise = daily.timeAt("sunrise", 0),
            sunset = daily.timeAt("sunset", 0),
            hourly = hours,
            daily = dayList,
        )
    }

    private fun JSONObject.timeAt(key: String, index: Int): LocalTime? {
        val arr: JSONArray = optJSONArray(key) ?: return null
        val value = arr.optString(index)
        return if (value.length >= 16) LocalTime.parse(value.substring(11, 16)) else null
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "Clima-Android/1.0")
            check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
