package app.clima.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject

/** Quantas cidades definidas o usuário pode salvar, além da localização atual. */
const val MAX_SAVED_PLACES = 3

/**
 * Guarda os locais salvos, o lugar selecionado e a última resposta da API de cada lugar,
 * para o app abrir na hora e os widgets funcionarem offline.
 */
class WeatherRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("clima", Context.MODE_PRIVATE)

    /** Lugar selecionado: o que o app e os widgets 2x2, 4x2 e 4x4 mostram. */
    var place: Place
        get() = prefs.getString(KEY_PLACE, null)?.let { Place.fromJson(JSONObject(it)) } ?: DEFAULT_PLACE
        set(value) = prefs.edit().putString(KEY_PLACE, value.toJson().toString()).apply()

    /** Locais salvos: a localização atual (se houver) e até [MAX_SAVED_PLACES] definidos. Quem já usava o app começa só com o lugar que tinha selecionado. */
    val places: List<Place>
        get() {
            val raw = prefs.getString(KEY_PLACES, null)
            val saved = raw?.let {
                runCatching {
                    val array = JSONArray(it)
                    (0 until array.length()).map { i -> Place.fromJson(array.getJSONObject(i)) }
                }.getOrNull()
            }
            return if (saved.isNullOrEmpty()) listOf(place) else saved
        }

    /** Salva (se ainda não estiver na lista) e seleciona o lugar. Devolve false se a lista já está cheia. */
    fun select(target: Place): Boolean {
        val saved = places
        val existing = saved.firstOrNull { it.key == target.key }
        if (existing == null) {
            if (saved.count { !it.isGps } >= MAX_SAVED_PLACES) return false
            savePlaces(saved + target)
        }
        place = existing ?: target
        return true
    }

    /** Remove o lugar; o app sempre mantém pelo menos um. Devolve false quando não removeu. */
    fun remove(target: Place): Boolean {
        val saved = places
        if (saved.size <= 1) return false
        val remaining = saved.filterNot { it.key == target.key }
        savePlaces(remaining)
        prefs.edit().remove(rawKey(target)).remove(atKey(target)).apply()
        if (place.key == target.key) place = remaining.first()
        return true
    }

    /** A localização atual substitui a anterior, vai para o topo da lista e fica selecionada. */
    fun setMyLocation(target: Place) {
        val gps = target.copy(isGps = true)
        savePlaces(listOf(gps) + places.filterNot { it.isGps || it.key == gps.key })
        place = gps
    }

    /** Escolha de um widget individual: a chave de um local salvo, ou nada para "localização atual". */
    fun widgetChoice(widgetId: Int): String? = prefs.getString(widgetKey(widgetId), null)

    fun setWidgetChoice(widgetId: Int, placeKey: String?) {
        prefs.edit().apply { if (placeKey == null) remove(widgetKey(widgetId)) else putString(widgetKey(widgetId), placeKey) }.apply()
    }

    fun forgetWidget(widgetId: Int) = prefs.edit().remove(widgetKey(widgetId)).apply()

    /**
     * Local que o widget mostra: o que ele escolheu; se não escolheu (ou o local foi removido), a localização atual;
     * se ainda não há localização atual, o local selecionado no app.
     */
    fun placeFor(widgetId: Int): Place {
        val saved = places
        val choice = widgetChoice(widgetId)
        return saved.firstOrNull { choice != null && it.key == choice } ?: saved.firstOrNull { it.isGps } ?: place
    }

    fun cachedFor(widgetId: Int): Weather? = cached(placeFor(widgetId))

    fun cached(target: Place = place): Weather? {
        var raw = prefs.getString(rawKey(target), null)
        var at = prefs.getLong(atKey(target), 0)
        if (raw == null) {
            // Cache do formato antigo (um único lugar), válido só para o lugar que ele guardou.
            val legacyPlace = prefs.getString(KEY_RAW_PLACE, null)?.let { runCatching { Place.fromJson(JSONObject(it)) }.getOrNull() }
            if (legacyPlace?.key == target.key) {
                raw = prefs.getString(KEY_RAW, null)
                at = prefs.getLong(KEY_RAW_AT, 0)
            }
        }
        raw ?: return null
        return runCatching { OpenMeteo.parse(target, raw, at) }.getOrNull()
    }

    suspend fun refresh(target: Place = place): Weather {
        val raw = OpenMeteo.forecast(target)
        val now = System.currentTimeMillis()
        val weather = OpenMeteo.parse(target, raw, now)
        prefs.edit().putString(rawKey(target), raw).putLong(atKey(target), now).apply()
        return weather
    }

    /** Atualiza os demais locais salvos; um que falhe não impede os outros. */
    suspend fun refreshOthers() = coroutineScope {
        val selectedKey = place.key
        places.filter { it.key != selectedKey }.map { async { runCatching { refresh(it) } } }.awaitAll()
    }

    /** Atualiza todos os locais salvos; só falha se nenhum for atualizado. */
    suspend fun refreshAll() = coroutineScope {
        val results = places.map { async { runCatching { refresh(it) } } }.awaitAll()
        if (results.all { it.isFailure }) results.first().getOrThrow()
    }

    /** Emite sempre que algo é salvo (lugar, lista ou previsão), para os widgets se redesenharem com o cache novo. */
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun savePlaces(list: List<Place>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PLACES, array.toString()).apply()
    }

    private fun widgetKey(id: Int) = "widget:$id"
    private fun rawKey(p: Place) = "raw:${p.key}"
    private fun atKey(p: Place) = "raw_at:${p.key}"

    private companion object {
        const val KEY_PLACE = "place"
        const val KEY_PLACES = "places"
        const val KEY_RAW = "raw"
        const val KEY_RAW_PLACE = "raw_place"
        const val KEY_RAW_AT = "raw_at"
        val DEFAULT_PLACE = Place("São Paulo", "Brasil",-23.5475, -46.6361)
    }
}
