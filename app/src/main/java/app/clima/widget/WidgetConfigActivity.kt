package app.clima.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import app.clima.R
import app.clima.data.Place
import app.clima.data.WeatherRepository
import app.clima.ui.ClimaTheme
import kotlinx.coroutines.launch

/** Aberta ao adicionar (e ao reconfigurar) um widget individual: escolhe a localização atual ou uma cidade salva. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sem RESULT_OK o launcher descarta o widget; só confirmamos depois da escolha.
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repo = WeatherRepository(this)
        enableEdgeToEdge()
        setContent {
            ClimaTheme {
                ChoiceScreen(
                    places = repo.places,
                    choice = repo.widgetChoice(widgetId),
                    onPick = { key ->
                        repo.setWidgetChoice(widgetId, key)
                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        lifecycleScope.launch {
                            ClimaWidgets.updateAll(applicationContext)
                            finish()
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceScreen(places: List<Place>, choice: String?, onPick: (String?) -> Unit) {
    var picked by remember { mutableStateOf(choice) }
    val gps = places.firstOrNull { it.isGps }
    // Sem escolha (ou com a cidade já removida) o widget mostra a localização atual.
    val effective = places.firstOrNull { !it.isGps && it.key == picked }?.key

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_config_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Option(
                    title = stringResource(R.string.widget_config_current),
                    subtitle = gps?.label ?: stringResource(R.string.widget_config_current_empty),
                    selected = effective == null,
                    onClick = { picked = null; onPick(null) },
                )
            }
            places.filter { !it.isGps }.forEach { place ->
                item(key = place.key) {
                    Option(
                        title = place.name,
                        subtitle = place.region,
                        selected = effective == place.key,
                        onClick = { picked = place.key; onPick(place.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Option(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle.isBlank()) null else ({ Text(subtitle) }),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
