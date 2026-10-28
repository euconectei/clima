package app.clima.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

object LocationFinder {
    /** Localização atual aproximada; exige a permissão ACCESS_COARSE_LOCATION já concedida. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java)
        val enabled = lm.getProviders(true)
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in enabled } ?: return null
        return try {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
            }
        } catch (_: SecurityException) {
            null
        }
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
