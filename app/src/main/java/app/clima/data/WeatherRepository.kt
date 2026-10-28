package app.clima.data

import android.content.Context
import org.json.JSONObject

/** Guarda o lugar escolhido e a última resposta da API, para o app abrir na hora e os widgets funcionarem offline. */
class WeatherRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("clima", Context.MODE_PRIVATE)

    var place: Place
        get() = prefs.getString(KEY_PLACE, null)?.let { Place.fromJson(JSONObject(it)) } ?: DEFAULT_PLACE
        set(value) = prefs.edit().putString(KEY_PLACE, value.toJson().toString()).apply()

    fun cached(): Weather? {
        val raw = prefs.getString(KEY_RAW, null) ?: return null
        val cachedPlace = prefs.getString(KEY_RAW_PLACE, null) ?: return null
        return runCatching {
            OpenMeteo.parse(Place.fromJson(JSONObject(cachedPlace)), raw, prefs.getLong(KEY_RAW_AT, 0))
        }.getOrNull()
    }

    suspend fun refresh(): Weather {
        val target = place
        val raw = OpenMeteo.forecast(target)
        val now = System.currentTimeMillis()
        val weather = OpenMeteo.parse(target, raw, now)
        prefs.edit()
            .putString(KEY_RAW, raw)
            .putString(KEY_RAW_PLACE, target.toJson().toString())
            .putLong(KEY_RAW_AT, now)
            .apply()
        return weather
    }

    private companion object {
        const val KEY_PLACE = "place"
        const val KEY_RAW = "raw"
        const val KEY_RAW_PLACE = "raw_place"
        const val KEY_RAW_AT = "raw_at"
        val DEFAULT_PLACE = Place("São Paulo", "Brasil",-23.5475, -46.6361)
    }
}
