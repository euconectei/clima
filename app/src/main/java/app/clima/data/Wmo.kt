package app.clima.data

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val ptBR: Locale = Locale.forLanguageTag("pt-BR")

/** Códigos de tempo WMO usados pela Open-Meteo. Os ícones são emojis (Noto Color Emoji, a fonte do Android). */
object Wmo {
    fun label(code: Int): String = when (code) {
        0 -> "Céu limpo"
        1 -> "Predomínio de sol"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina"
        51, 53, 55 -> "Garoa"
        56, 57 -> "Garoa congelante"
        61 -> "Chuva fraca"
        63 -> "Chuva"
        65 -> "Chuva forte"
        66, 67 -> "Chuva congelante"
        71, 73, 75, 77 -> "Neve"
        80 -> "Pancadas fracas"
        81 -> "Pancadas de chuva"
        82 -> "Pancadas fortes"
        85, 86 -> "Pancadas de neve"
        95 -> "Tempestade"
        96, 99 -> "Tempestade com granizo"
        else -> "—"
    }

    fun icon(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1 -> if (isDay) "🌤️" else "🌙"
        2 -> if (isDay) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }
}

fun LocalDate.shortDay(isToday: Boolean): String =
    if (isToday) "Hoje"
    else dayOfWeek.getDisplayName(TextStyle.SHORT, ptBR).trimEnd('.').replaceFirstChar { it.uppercase() }

/** Direção do vento em graus → ponto cardeal (L = leste, O = oeste). */
fun compass(degrees: Int): String =
    arrayOf("N", "NE", "L", "SE", "S", "SO", "O", "NO")[(((degrees % 360) + 22.5) / 45).toInt() % 8]

fun uvLevel(uv: Double): String = when {
    uv < 3 -> "Baixo"
    uv < 6 -> "Moderado"
    uv < 8 -> "Alto"
    uv < 11 -> "Muito alto"
    else -> "Extremo"
}
