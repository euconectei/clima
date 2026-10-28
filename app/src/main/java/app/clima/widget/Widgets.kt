package app.clima.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as dayNight
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.clima.MainActivity
import app.clima.data.Day
import app.clima.data.Hour
import app.clima.data.Place
import app.clima.data.Weather
import app.clima.data.WeatherRepository
import app.clima.data.Wmo
import app.clima.data.compass
import app.clima.data.uvLevel
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

/** Pequeno (2x2): temperatura em destaque. */
class SmallWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = WeatherRepository(context)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val weather = repo.observe { cachedFor(widgetId) }
            GlanceTheme { WidgetFrame(weather) { SmallContent(it) } }
        }
    }
}

/** Médio (4x2): agora, próximas horas e métricas principais. */
class MediumWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = WeatherRepository(context)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val weather = repo.observe { cachedFor(widgetId) }
            GlanceTheme { WidgetFrame(weather) { MediumContent(it) } }
        }
    }
}

/** Grande (4x4): visão completa com métricas, horas e próximos 4 dias. */
class LargeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = WeatherRepository(context)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val weather = repo.observe { cachedFor(widgetId) }
            GlanceTheme { WidgetFrame(weather) { LargeContent(it) } }
        }
    }
}

private class Overview(val selected: Weather?, val cities: List<Pair<Place, Weather?>>, val selectedKey: String)

/**
 * Relê o cache sempre que ele muda. Com a sessão do Glance viva (~45 s) um updateAll só recompõe e não roda o
 * provideGlance de novo, então dados lidos antes do provideContent ficariam velhos (o widget ficava sem previsão).
 */
@Composable
private fun <T> WeatherRepository.observe(read: WeatherRepository.() -> T): T {
    val value by remember { changes().map { read() } }.collectAsState(initial = read())
    return value
}

/** Visão geral (4x2): um cartão vertical por cidade salva (localização atual + até 3). */
class MultiCityWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = WeatherRepository(context)
        provideContent {
            val overview = repo.observe { Overview(cached(), places.map { it to cached(it) }, place.key) }
            GlanceTheme {
                WidgetFrame(overview.selected, padding = 8.dp) { MultiCityContent(overview.cities, overview.selectedKey) }
            }
        }
    }
}

class SmallWidgetReceiver : ClimaWidgetReceiver() {
    override val glanceAppWidget = SmallWidget()
}

class MediumWidgetReceiver : ClimaWidgetReceiver() {
    override val glanceAppWidget = MediumWidget()
}

class LargeWidgetReceiver : ClimaWidgetReceiver() {
    override val glanceAppWidget = LargeWidget()
}

class MultiCityWidgetReceiver : ClimaWidgetReceiver() {
    override val glanceAppWidget = MultiCityWidget()
}

/** Tocar num cartão do carrossel seleciona a cidade no app (e o destaque do carrossel); os widgets individuais seguem a própria escolha. */
class SelectCityCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = WeatherRepository(context)
        val key = parameters[CityKey] ?: return
        repo.places.firstOrNull { it.key == key }?.let { repo.place = it }
        ClimaWidgets.updateAll(context)
    }

    companion object {
        val CityKey = ActionParameters.Key<String>("city")
    }
}

abstract class ClimaWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val repo = WeatherRepository(context)
        appWidgetIds.forEach(repo::forgetWidget)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.runNow(context)
    }
}

object ClimaWidgets {
    suspend fun updateAll(context: Context) {
        SmallWidget().updateAll(context)
        MediumWidget().updateAll(context)
        LargeWidget().updateAll(context)
        MultiCityWidget().updateAll(context)
    }
}

// --- Paleta ---------------------------------------------------------------------------------

/** Cores do widget. */
private class Palette(
    val background: ColorProvider,
    val pill: ColorProvider,
    val onSurface: ColorProvider,
    val onVariant: ColorProvider,
    val highlight: ColorProvider,
    val onHighlight: ColorProvider,
    val accent: ColorProvider,
)

private val LocalPalette = staticCompositionLocalOf<Palette> { error("Palette não fornecida") }

private class Accent(val highlight: ColorProvider, val onHighlight: ColorProvider, val bar: ColorProvider)

private fun accent(
    highlight: Pair<Long, Long>, onHighlight: Pair<Long, Long>, bar: Pair<Long, Long>,
) = Accent(
    highlight = dayNight(Color(highlight.first), Color(highlight.second)),
    onHighlight = dayNight(Color(onHighlight.first), Color(onHighlight.second)),
    bar = dayNight(Color(bar.first), Color(bar.second)),
)

/** Condições com paleta própria de acento; as demais (nublado, neve, neblina, noite) ficam com o Material You puro. */
private fun accentFor(code: Int, isDay: Boolean): Accent? = when {
    (code == 0 || code == 1) && isDay -> accent(0xFFFFE088 to 0xFF574500, 0xFF241A00 to 0xFFFFE08B, 0xFFD97706 to 0xFFFBBF24)
    code in 51..67 || code in 80..82 -> accent(0xFF7DD3FC to 0xFF0369A1, 0xFF082F49 to 0xFFE0F2FE, 0xFF0284C7 to 0xFF38BDF8)
    code >= 95 -> accent(0xFF94A3B8 to 0xFF334155, 0xFF020617 to 0xFFE2E2F0, 0xFF475569 to 0xFF94A3B8)
    else -> null
}

private fun isWet(code: Int) = code in 51..67 || code in 80..82 || code >= 95

/** Fundo, pílulas e textos vêm sempre do Material You (papel de parede, claro/escuro); só o acento reage ao clima. */
@Composable
private fun paletteFor(code: Int, isDay: Boolean): Palette {
    val c = GlanceTheme.colors
    val accent = accentFor(code, isDay)
    return Palette(
        background = c.widgetBackground,
        pill = c.surfaceVariant,
        onSurface = c.onSurface,
        onVariant = c.onSurfaceVariant,
        highlight = accent?.highlight ?: c.primaryContainer,
        onHighlight = accent?.onHighlight ?: c.onPrimaryContainer,
        accent = accent?.bar ?: c.primary,
    )
}

private enum class Tone { Primary, Variant, OnHighlight }

// --- Estrutura ------------------------------------------------------------------------------

@Composable
private fun WidgetFrame(weather: Weather?, padding: Dp = 14.dp, content: @Composable (Weather) -> Unit) {
    val palette = paletteFor(weather?.code ?: -1, weather?.isDay ?: true)
    CompositionLocalProvider(LocalPalette provides palette) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(palette.background)
                .cornerRadius(android.R.dimen.system_app_widget_background_radius)
                .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java)))
                .padding(padding),
        ) {
            if (weather == null) {
                Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    GText("Clima", 16, FontWeight.Bold)
                    GText("Toque para carregar a previsão", 12, tone = Tone.Variant, maxLines = 2)
                }
            } else {
                content(weather)
            }
        }
    }
}

// --- Pequeno --------------------------------------------------------------------------------

@Composable
private fun SmallContent(w: Weather) {
    val today = w.daily.first()
    val p = LocalPalette.current
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(GlanceModifier.defaultWeight()) {
                GText(w.place.name, 13, FontWeight.Medium)
                GText("Sensação ${w.feelsLike}°", 10, tone = Tone.Variant)
            }
            Box(GlanceModifier.background(p.highlight).cornerRadius(14.dp).padding(6.dp)) {
                GText(Wmo.icon(w.code, w.isDay), 22, tone = Tone.OnHighlight)
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GText("${w.temp}°", 30, FontWeight.Bold)
            Spacer(GlanceModifier.width(5.dp))
            Box(GlanceModifier.width(1.dp).height(28.dp).background(p.pill)) {}
            Spacer(GlanceModifier.width(5.dp))
            Column {
                GText("↑${today.max}°", 11, FontWeight.Bold)
                GText("↓${today.min}°", 11, FontWeight.Medium, tone = Tone.Variant)
            }
            Spacer(GlanceModifier.width(5.dp))
            Box(GlanceModifier.width(1.dp).height(28.dp).background(p.pill)) {}
            Spacer(GlanceModifier.width(5.dp))
            Column {
                // Com chuva ou tempestade mostra o volume (mm); nos demais casos, a probabilidade.
                val rain = if (isWet(w.code)) "🌧️${"%.1f".format(PT_BR, today.rainMm)}mm" else "🌂${today.rainChance}%"
                GText(rain, 11, FontWeight.Bold)
                GText("💨${w.windKmh} km/h", 9, tone = Tone.Variant)
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(p.pill)) {}
        Spacer(GlanceModifier.height(6.dp))
        GText(Wmo.label(w.code, w.isDay), 12, FontWeight.Medium, tone = Tone.Variant)
    }
}

// --- Médio ----------------------------------------------------------------------------------

@Composable
private fun MediumContent(w: Weather) {
    val today = w.daily.first()
    val p = LocalPalette.current
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.background(p.highlight).cornerRadius(16.dp).padding(8.dp)) {
                GText(Wmo.icon(w.code, w.isDay), 26, tone = Tone.OnHighlight)
            }
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                GText(w.place.name, 15, FontWeight.Bold)
                GText("${Wmo.label(w.code, w.isDay)} · sens. ${w.feelsLike}° · ${today.min}°–${today.max}°", 10, tone = Tone.Variant)
            }
            GText("${w.temp}°", 38, FontWeight.Bold)
        }
        Spacer(GlanceModifier.defaultWeight())
        PillRow(w.hourly.take(5)) { i, h -> HourPill(h, label = if (i == 0) "Agora" else "${h.time.hour}h", highlight = i == 0, showRain = false) }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
            MetricPill("🌧️ Chuva", "${today.rainChance}%", "%.1f mm".format(PT_BR, today.rainMm))
            Spacer(GlanceModifier.width(6.dp))
            MetricPill("💧 Umidade", "${w.humidity}%", "relativa")
            Spacer(GlanceModifier.width(6.dp))
            MetricPill("💨 Vento", "${w.windKmh} km/h", compass(w.windDirection))
            Spacer(GlanceModifier.width(6.dp))
            MetricPill("☀️ UV", "${today.uvMax.toInt()}", uvLevel(today.uvMax))
        }
    }
}

// --- Várias cidades -------------------------------------------------------------------------

/** O layout se adapta à quantidade: 1 cartão horizontal; 2 a 4 cartões verticais, mais compactos quanto mais cidades. */
@Composable
private fun MultiCityContent(cities: List<Pair<Place, Weather?>>, selectedKey: String) {
    val single = cities.singleOrNull()
    if (single != null) {
        SingleCityCard(single.first, single.second)
        return
    }
    Row(GlanceModifier.fillMaxSize()) {
        cities.forEachIndexed { i, (place, weather) ->
            if (i > 0) Spacer(GlanceModifier.width(4.dp))
            CityCard(place, weather, selected = place.key == selectedKey, roomy = cities.size <= 2)
        }
    }
}

private fun cityClick(place: Place) =
    actionRunCallback<SelectCityCallback>(actionParametersOf(SelectCityCallback.CityKey to place.key))

/** Subtítulo do cartão: a região do lugar. */
private fun Place.subtitle() = region

/** Cartão vertical estreito: com 3 ou 4 cidades o nome é truncado e o pin só marca a localização atual. */
@Composable
private fun RowScope.CityCard(place: Place, w: Weather?, selected: Boolean, roomy: Boolean) {
    val p = LocalPalette.current
    val tone = if (selected) Tone.OnHighlight else Tone.Primary
    val variant = if (selected) Tone.OnHighlight else Tone.Variant
    val today = w?.daily?.firstOrNull()
    Column(
        GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .background(if (selected) p.highlight else p.pill)
            .cornerRadius(18.dp)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .clickable(cityClick(place)),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                GText(if (place.isGps) "📍${place.name}" else place.name, if (roomy) 12 else 10, FontWeight.Bold, tone = tone)
                if (roomy && place.subtitle().isNotBlank()) GText(place.subtitle(), 9, tone = variant)
            }
            if (w != null) GText(Wmo.icon(w.code, w.isDay), if (roomy) 18 else 14, tone = tone)
        }
        Spacer(GlanceModifier.defaultWeight())
        GText(if (w != null) "${w.temp}°" else "—", if (roomy) 26 else 22, FontWeight.Bold, tone = tone)
        GText(if (w != null) Wmo.label(w.code, w.isDay) else "Sem dados", if (roomy) 10 else 8, tone = variant)
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
            GText("↓${today?.min ?: "—"}°", if (roomy) 11 else 9, FontWeight.Medium, tone = variant)
            Spacer(GlanceModifier.defaultWeight())
            GText("↑${today?.max ?: "—"}°", if (roomy) 11 else 9, FontWeight.Medium, tone = variant)
        }
    }
}

/** Só uma cidade (a localização atual): um cartão horizontal, com nome, temperatura e mín/máx lado a lado. */
@Composable
private fun SingleCityCard(place: Place, w: Weather?) {
    val p = LocalPalette.current
    val today = w?.daily?.firstOrNull()
    Row(
        GlanceModifier
            .fillMaxSize()
            .background(p.highlight)
            .cornerRadius(22.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clickable(cityClick(place)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.defaultWeight()) {
            GText(if (place.isGps) "📍${place.name}" else place.name, 14, FontWeight.Bold, tone = Tone.OnHighlight)
            if (place.subtitle().isNotBlank()) GText(place.subtitle(), 10, tone = Tone.OnHighlight)
        }
        if (w != null) GText(Wmo.icon(w.code, w.isDay), 30, tone = Tone.OnHighlight)
        Spacer(GlanceModifier.width(10.dp))
        Box(GlanceModifier.width(1.dp).height(40.dp).background(p.pill)) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GText(if (w != null) "${w.temp}°" else "—", 34, FontWeight.Bold, tone = Tone.OnHighlight)
            GText(if (w != null) Wmo.label(w.code, w.isDay) else "Sem dados", 10, tone = Tone.OnHighlight)
        }
        Spacer(GlanceModifier.width(10.dp))
        Box(GlanceModifier.width(1.dp).height(40.dp).background(p.pill)) {}
        Spacer(GlanceModifier.width(10.dp))
        Column {
            GText("↑${today?.max ?: "—"}°", 12, FontWeight.Bold, tone = Tone.OnHighlight)
            GText("↓${today?.min ?: "—"}°", 12, FontWeight.Medium, tone = Tone.OnHighlight)
        }
    }
}

// --- Grande ---------------------------------------------------------------------------------

@Composable
private fun LargeContent(w: Weather) {
    val today = w.daily.first()
    val p = LocalPalette.current
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    Column(GlanceModifier.fillMaxSize()) {
        // Os blocos são agrupados porque o Glance limita a 10 filhos por Column.
        Column(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(GlanceModifier.defaultWeight()) {
                    GText("📍 ${w.place.name}", 16, FontWeight.Bold)
                    GText(today.date.header(), 10, tone = Tone.Variant)
                    GText(Wmo.label(w.code, w.isDay), 12, FontWeight.Medium, tone = Tone.Variant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    GText("${w.temp}°", 46, FontWeight.Bold)
                    GText("Sensação ${w.feelsLike}°", 10, tone = Tone.Variant)
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                MetricPill("🌧️ Chuva", "${today.rainChance}%", "%.1f mm".format(PT_BR, today.rainMm), highlight = true)
                Spacer(GlanceModifier.width(6.dp))
                MetricPill("💧 Umidade", "${w.humidity}%", "relativa")
                Spacer(GlanceModifier.width(6.dp))
                MetricPill("💨 Vento", "${w.windKmh} km/h", compass(w.windDirection))
                Spacer(GlanceModifier.width(6.dp))
                MetricPill("🧭 Pressão", "${w.pressureHpa}", "hPa")
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Column(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GText("PRÓXIMAS HORAS", 10, FontWeight.Medium, tone = Tone.Variant)
                Spacer(GlanceModifier.defaultWeight())
                GText("🌇 Pôr do sol às ${w.sunset?.format(fmt) ?: "—"}", 10, tone = Tone.Variant)
            }
            Spacer(GlanceModifier.height(4.dp))
            PillRow(w.hourly.take(5)) { i, h -> HourPill(h, label = if (i == 0) "Agora" else "${h.time.hour}h", highlight = i == 0, showRain = true) }
        }
        Spacer(GlanceModifier.defaultWeight())
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(p.pill)) {}
        Spacer(GlanceModifier.height(6.dp))
        Column(GlanceModifier.fillMaxWidth()) {
            GText("PRÓXIMOS 4 DIAS", 10, FontWeight.Medium, tone = Tone.Variant)
            Spacer(GlanceModifier.height(2.dp))
            val weekMin = w.daily.minOf { it.min }
            val weekMax = w.daily.maxOf { it.max }
            w.daily.drop(1).take(4).forEach { d -> DayRow(d, weekMin, weekMax) }
        }
    }
}

@Composable
private fun DayRow(d: Day, weekMin: Int, weekMax: Int) {
    val p = LocalPalette.current
    // Quanto mais quente o máximo do dia dentro da semana, mais cheia a barra.
    val progress = ((d.max - weekMin + 1f) / (weekMax - weekMin + 1f)).coerceIn(0.15f, 1f)
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        GText(dayName(d.date), 12, FontWeight.Medium, modifier = GlanceModifier.width(58.dp))
        GText(Wmo.icon(d.code), 15)
        Spacer(GlanceModifier.width(4.dp))
        GText("${d.rainChance}%", 10, tone = Tone.Variant, modifier = GlanceModifier.width(32.dp))
        GText("${d.min}°", 11, tone = Tone.Variant, modifier = GlanceModifier.width(26.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.defaultWeight().height(6.dp),
            color = p.accent,
            backgroundColor = p.pill,
        )
        Spacer(GlanceModifier.width(6.dp))
        GText("${d.max}°", 11, FontWeight.Bold, modifier = GlanceModifier.width(26.dp))
    }
}

// --- Componentes ----------------------------------------------------------------------------

/** Linha de pílulas de largura igual, com 6dp entre elas. */
@Composable
private fun <T> PillRow(items: List<T>, pill: @Composable RowScope.(Int, T) -> Unit) {
    Row(GlanceModifier.fillMaxWidth()) {
        items.forEachIndexed { i, item ->
            if (i > 0) Spacer(GlanceModifier.width(6.dp))
            pill(i, item)
        }
    }
}

@Composable
private fun RowScope.HourPill(h: Hour, label: String, highlight: Boolean, showRain: Boolean) {
    val tone = if (highlight) Tone.OnHighlight else Tone.Primary
    PillColumn(highlight, GlanceModifier.defaultWeight()) {
        GText(label, 10, FontWeight.Medium, tone = tone)
        GText(Wmo.icon(h.code, h.isDay), 18)
        GText("${h.temp}°", 13, FontWeight.Bold, tone = tone)
        if (showRain) GText("💧${h.rainChance}%", 9, tone = if (highlight) Tone.OnHighlight else Tone.Variant)
    }
}

@Composable
private fun RowScope.MetricPill(
    label: String,
    value: String,
    sub: String,
    highlight: Boolean = false,
) {
    val tone = if (highlight) Tone.OnHighlight else Tone.Primary
    val variant = if (highlight) Tone.OnHighlight else Tone.Variant
    PillColumn(highlight, GlanceModifier.defaultWeight(), Alignment.Start) {
        GText(label, 9, FontWeight.Medium, tone = variant)
        GText(value, 12, FontWeight.Bold, tone = tone)
        GText(sub, 9, tone = variant)
    }
}

@Composable
private fun PillColumn(
    highlight: Boolean,
    modifier: GlanceModifier,
    horizontal: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier = modifier.background(if (highlight) p.highlight else p.pill).cornerRadius(16.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = horizontal,
        content = content,
    )
}

@Composable
private fun GText(
    text: String,
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    tone: Tone = Tone.Primary,
    maxLines: Int = 1,
    modifier: GlanceModifier = GlanceModifier,
) {
    val p = LocalPalette.current
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = when (tone) {
                Tone.Primary -> p.onSurface
                Tone.Variant -> p.onVariant
                Tone.OnHighlight -> p.onHighlight
            },
            fontSize = size.sp,
            fontWeight = weight,
        ),
    )
}

private fun dayName(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(JTextStyle.FULL, PT_BR).substringBefore("-feira").replaceFirstChar { it.uppercase() }

private fun LocalDate.header(): String =
    "${dayName(this)}, $dayOfMonth ${month.getDisplayName(JTextStyle.SHORT, PT_BR).trimEnd('.').replaceFirstChar { it.uppercase() }}"
