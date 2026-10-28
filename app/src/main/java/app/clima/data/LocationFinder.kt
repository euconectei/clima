package app.clima.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

object LocationFinder {
    /** O clima é por cidade, então uma posição de até 15 minutos atrás serve e responde na hora. */
    private const val FRESH_MS = 15 * 60_000L

    /** getCurrentLocation espera 30 s por padrão e, em alguns aparelhos, termina sem posição. */
    private const val FIX_TIMEOUT_MS = 8_000L

    /** Localização atual aproximada; exige a permissão ACCESS_COARSE_LOCATION já concedida. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java)
        val enabled = lm.getProviders(true)
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { it in enabled }
        val provider = providers.firstOrNull() ?: return null
        return try {
            val last = providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.elapsedRealtimeNanos }
            if (last != null && last.ageMs() <= FRESH_MS) return last
            // Sem posição recente: pede uma nova, e se não vier a tempo usa a última conhecida, mesmo antiga.
            withTimeoutOrNull(FIX_TIMEOUT_MS) { fix(lm, provider, context) } ?: last
        } catch (_: SecurityException) {
            null
        }
    }

    private fun Location.ageMs() = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000

    @SuppressLint("MissingPermission")
    private suspend fun fix(lm: LocationManager, provider: String, context: Context): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
        }

    @Suppress("DEPRECATION")
    suspend fun toPlace(context: Context, location: Location): Place = withContext(Dispatchers.IO) {
        val address = runCatching {
            Geocoder(context, Locale.forLanguageTag("pt-BR"))
                .getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
        }.getOrNull()
        Place(
            name = address?.locality ?: address?.subAdminArea ?: "Minha localização",
            region = listOfNotNull(address?.adminArea, address?.countryName).joinToString(", "),
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }
}
