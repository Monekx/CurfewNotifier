package com.monekx.curfewnotifier.ui

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FmdBad
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.* // Убедитесь, что rememberCoroutineScope() импортирован
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import com.monekx.curfewnotifier.MapActivity
import com.monekx.curfewnotifier.R
import com.monekx.curfewnotifier.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.widget.Toast

import com.monekx.curfewnotifier.HOME_RADIUS_METERS_KEY

@Composable
fun HomeLocationScreen(context: Context, mapLauncher: ActivityResultLauncher<Intent>) {
    var homeLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val latKey = intPreferencesKey("home_lat_int")
    val lonKey = intPreferencesKey("home_lon_int")

    // Состояние для радиуса домашней зоны
    var homeRadiusInput by remember { mutableStateOf("50") }
    var homeRadiusError by remember { mutableStateOf(false) }
    var currentHomeRadius by remember { mutableStateOf(50) } // Фактический радиус, используемый в логике

    // ПОЛУЧАЕМ CoroutineScope В КОНТЕКСТЕ @Composable ФУНКЦИИ
    val scope = rememberCoroutineScope()

    val saveHomeRadius = {
        val radius = homeRadiusInput.toIntOrNull()
        if (radius != null && radius > 0) {
            currentHomeRadius = radius
            homeRadiusError = false
            Toast.makeText(context, "Радиус дома сохранен: $radius м", Toast.LENGTH_SHORT).show()
            // Используем уже полученный scope
            scope.launch {
                context.dataStore.edit { prefs ->
                    prefs[HOME_RADIUS_METERS_KEY] = radius
                }
            }
        } else {
            homeRadiusError = true
            Toast.makeText(context, "Некорректное значение радиуса", Toast.LENGTH_SHORT).show()
        }
    }


    LaunchedEffect(Unit) {
        val preferences = context.dataStore.data.first()
        val storedLatInt = preferences[latKey]
        val storedLonInt = preferences[lonKey]
        if (storedLatInt != null && storedLonInt != null) {
            homeLocation = Pair(storedLatInt / 1_000_000.0, storedLonInt / 1_000_000.0)
        }

        // Загружаем сохраненный радиус
        val storedRadius = preferences[HOME_RADIUS_METERS_KEY] ?: 50 // Дефолт 50
        homeRadiusInput = storedRadius.toString()
        currentHomeRadius = storedRadius
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Карточка с общей информацией и статусом домашней точки
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (homeLocation != null) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Home Location Set",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Ваша домашняя точка установлена",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Широта: %.6f".format(homeLocation!!.first),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Долгота: %.6f".format(homeLocation!!.second),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Радиус домашней зоны: $currentHomeRadius м",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.FmdBad,
                        contentDescription = "Home Location Not Set",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Домашняя точка не установлена",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Пожалуйста, установите её, чтобы получать точные уведомления о расстоянии до дома.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val intent = Intent(context, MapActivity::class.java)
                        mapLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Открыть карту и выбрать точку")
                }
            }
        }

        // НОВАЯ КАРТОЧКА: Настройка радиуса домашней зоны
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Настроить радиус домашней зоны",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = homeRadiusInput,
                    onValueChange = { newValue ->
                        homeRadiusInput = newValue
                        homeRadiusError = false
                    },
                    label = { Text("Радиус (метры)") },
                    isError = homeRadiusError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
                if (homeRadiusError) {
                    Text("Введите корректный радиус (число > 0).", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { saveHomeRadius() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Сохранить радиус")
                }
            }
        }


        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Здесь будет предпросмотр карты",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}