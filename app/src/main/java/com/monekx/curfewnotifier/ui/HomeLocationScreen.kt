package com.monekx.curfewnotifier.ui

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FmdBad
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource // Добавлен для использования изображений из ресурсов
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import com.monekx.curfewnotifier.MapActivity
import com.monekx.curfewnotifier.R // Добавлен для доступа к ресурсам
import com.monekx.curfewnotifier.dataStore
import kotlinx.coroutines.flow.first

@Composable
fun HomeLocationScreen(context: Context, mapLauncher: ActivityResultLauncher<Intent>) {
    var homeLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val latKey = intPreferencesKey("home_lat_int")
    val lonKey = intPreferencesKey("home_lon_int")

    LaunchedEffect(Unit) {
        val preferences = context.dataStore.data.first()
        val storedLatInt = preferences[latKey]
        val storedLonInt = preferences[lonKey]
        if (storedLatInt != null && storedLonInt != null) {
            homeLocation = Pair(storedLatInt / 1_000_000.0, storedLonInt / 1_000_000.0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp) // Добавлен отступ между элементами
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
                        modifier = Modifier.size(64.dp) // Крупная иконка
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
                        text = "Широта: %.6f".format(homeLocation!!.first), // Увеличена точность
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Долгота: %.6f".format(homeLocation!!.second), // Увеличена точность
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.FmdBad, // Иконка для отсутствующей точки
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

        // Добавим простую "заглушку" карты для визуального разнообразия
        // В реальном приложении здесь можно встроить неинтерактивный фрагмент карты
        // или скриншот, но для примера используем простой фон.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp), // Фиксированная высота
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Если у вас есть изображение-заглушка карты в drawable
                // Image(
                //     painter = painterResource(id = R.drawable.map_placeholder), // Замените на ваш ресурс
                //     contentDescription = "Map Placeholder",
                //     modifier = Modifier.fillMaxSize(),
                //     contentScale = ContentScale.Crop
                // )
                Text(
                    text = "Здесь будет предпросмотр карты",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}