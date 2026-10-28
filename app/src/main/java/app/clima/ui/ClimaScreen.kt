package app.clima.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clima.data.Day
import app.clima.data.MAX_SAVED_PLACES
import app.clima.data.Place
import app.clima.data.Weather
import app.clima.data.Wmo
import app.clima.data.compass
import app.clima.data.uvLevel
import app.clima.data.shortDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClimaScreen(vm: WeatherViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.useMyLocation() else vm.showError("Permissão de localização negada.")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(state.place.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = { askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                        Icon(Icons.Default.Place, contentDescription = "Usar minha localização")
                    }
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Adicionar cidade")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SavedPlaces(state.saved, state.place, state.temps, onPick = vm::switchTo, onRemove = vm::remove)
                val weather = state.weather
                if (weather == null) {
                    EmptyState(error = state.error, loading = state.loading, onRetry = vm::refresh)
                } else {
                    Hero(weather)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    TodayCard(weather)
                    HourlyCard(weather)
                    NextDaysCard(weather)
                    Text(
                        "Atualizado às ${weather.fetchedAt.toLocalTime()} · Open-Meteo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (searching) {
        SearchSheet(
            // Com o limite de cidades salvas atingido, só dá para adicionar outra depois de remover uma.
            full = state.saved.count { !it.isGps } >= MAX_SAVED_PLACES,
            results = state.results,
            onQuery = vm::search,
            onPick = { vm.select(it); searching = false },
            onDismiss = { searching = false },
        )
    }
}

private fun Long.toLocalTime(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

/** Locais salvos (localização atual + até 3): um toque troca de cidade, o X remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedPlaces(
    saved: List<Place>,
    selected: Place,
    temps: Map<String, Int>,
    onPick: (Place) -> Unit,
    onRemove: (Place) -> Unit,
) {
    if (saved.size < 2) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(saved, key = { it.key }) { place ->
            InputChip(
                selected = place.key == selected.key,
                onClick = { onPick(place) },
                label = { Text(listOfNotNull(place.name, temps[place.key]?.let { "$it°" }).joinToString(" · "), maxLines = 1) },
                leadingIcon = { Icon(if (place.isGps) Icons.Default.LocationOn else Icons.Default.Place, contentDescription = null) },
                // A localização atual não é removida: ela é recriada pelo botão de pin.
                trailingIcon = if (place.isGps) null else {
                    {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remover ${place.name}",
                            modifier = Modifier.clickable { onRemove(place) },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptyState(error: String?, loading: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (error != null && !loading) {
            Text("☁️", fontSize = 56.sp)
            Text(error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onRetry) { Text("Tentar de novo") }
        } else {
            Text("Carregando previsão…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Hero(w: Weather) {
    val today = w.daily.first()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Wmo.icon(w.code, w.isDay), fontSize = 72.sp)
        Text("${w.temp}°", fontSize = 96.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface)
        Text(Wmo.label(w.code, w.isDay), style = MaterialTheme.typography.titleLarge)
        Text(
            "Máx ${today.max}° · Mín ${today.min}° · Sensação ${w.feelsLike}°",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun HourlyCard(w: Weather) {
    SectionCard("Próximas horas") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(w.hourly) { i, h ->
                Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (i == 0) "Agora" else "${h.time.hour}h", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Wmo.icon(h.code, h.isDay), fontSize = 24.sp)
                    Text("${h.temp}°", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (h.rainChance >= 20) "${h.rainChance}%" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NextDaysCard(w: Weather) {
    val weekMin = w.daily.minOf { it.min }
    val weekMax = w.daily.maxOf { it.max }
    SectionCard("Próximos dias") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            w.daily.drop(1).forEach { d -> DayRow(d, weekMin, weekMax) }
        }
    }
}

/** Linha compacta: o dia atual já tem seu próprio destaque, então os demais ficam pequenos. */
@Composable
private fun DayRow(d: Day, weekMin: Int, weekMax: Int) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(d.date.shortDay(isToday = false), Modifier.width(44.dp), style = MaterialTheme.typography.bodyMedium)
            Text(Wmo.icon(d.code), fontSize = 18.sp)
            Text("${d.min}°", Modifier.width(28.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RangeBar(d.min, d.max, weekMin, weekMax, Modifier.weight(1f))
            Text("${d.max}°", Modifier.width(28.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Text(
            listOfNotNull(
                "Chuva ${d.rainChance}%".takeIf { d.rainChance > 0 },
                "%.1f mm".format(PT_BR, d.rainMm).takeIf { d.rainMm >= 0.1 },
                "UV ${d.uvMax.toInt()}".takeIf { d.uvMax >= 1 },
            ).joinToString(" · "),
            Modifier.padding(start = 70.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Faixa mínima–máxima do dia dentro da faixa da semana. */
@Composable
private fun RangeBar(lo: Int, hi: Int, weekMin: Int, weekMax: Int, modifier: Modifier) {
    val before = (lo - weekMin).toFloat()
    val span = (hi - lo).coerceAtLeast(1).toFloat()
    val after = (weekMax - hi).toFloat()
    Row(modifier.height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (before > 0f) Spacer(Modifier.weight(before))
        Box(Modifier.weight(span).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        if (after > 0f) Spacer(Modifier.weight(after))
    }
}

/** Destaque do dia atual: todos os indicadores em tiles grandes. */
@Composable
private fun TodayCard(w: Weather) {
    val today = w.daily.first()
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Hoje em detalhe", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("🌡️", "Sensação", "${w.feelsLike}°", "Máx ${today.max}° · Mín ${today.min}°", Modifier.weight(1f))
                Tile("💧", "Umidade", "${w.humidity}%", null, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("💨", "Vento", "${w.windKmh} km/h", "${compass(w.windDirection)} · rajadas ${w.windGustKmh} km/h", Modifier.weight(1f))
                Tile("☀️", "Índice UV", "${today.uvMax.toInt()}", uvLevel(today.uvMax), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("🌧️", "Chuva", "${today.rainChance}%", "%.1f mm previstos".format(PT_BR, today.rainMm), Modifier.weight(1f))
                Tile("☁️", "Nuvens", "${w.cloudCover}%", null, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("🧭", "Pressão", "${w.pressureHpa} hPa", null, Modifier.weight(1f))
                Tile("🌅", "Sol", w.sunrise?.format(fmt) ?: "—", "Pôr do sol ${w.sunset?.format(fmt) ?: "—"}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Tile(icon: String, title: String, value: String, sub: String?, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$icon  $title", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(sub ?: " ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(
    full: Boolean,
    results: List<Place>,
    onQuery: (String) -> Unit,
    onPick: (Place) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onQuery(it) },
                label = { Text(if (full) "Limite de $MAX_SAVED_PLACES cidades atingido" else "Adicionar cidade") },
                enabled = !full,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(results) { place ->
                    ListItem(
                        headlineContent = { Text(place.name) },
                        supportingContent = { Text(place.region) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPick(place) },
                    )
                }
            }
        }
    }
}
