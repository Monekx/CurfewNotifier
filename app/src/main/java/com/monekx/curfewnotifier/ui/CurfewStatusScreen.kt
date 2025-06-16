package com.monekx.curfewnotifier.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.monekx.curfewnotifier.dataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.LocalDate
import android.util.Log

@Composable
fun CurfewStatusScreen() {
    val context = LocalContext.current
    val curfewStart = LocalTime.of(23, 0)
    val curfewEnd = LocalTime.of(5, 0)

    var now by remember { mutableStateOf(LocalTime.now()) } // Это состояние, обновляемое LaunchedEffect
    var isAtHome by remember { mutableStateOf<Boolean?>(null) }
    val IS_AT_HOME_KEY = booleanPreferencesKey("is_at_home")

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now() // Обновляем состояние каждую секунду
            Log.d("CurfewStatusScreen", "Time updated by LaunchedEffect: ${now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}")
            val preferences = context.dataStore.data.first()
            isAtHome = preferences[IS_AT_HOME_KEY]
            delay(1000)
        }
    }

    // --- ИСПРАВЛЕНИЕ ЗДЕСЬ: Используем 'now' state для всех расчетов ---
    // Конвертируем 'now' (LocalTime) в LocalDateTime для расчетов с датой.
    // При каждой рекомпозиции из-за изменения 'now', эти переменные будут пересчитываться.
    val currentDateTimeForCalculation = LocalDate.now().atTime(now) // <-- КЛЮЧЕВОЕ ИЗМЕНЕНИЕ
    val curfewStartToday = LocalDate.now().atTime(curfewStart)
    val curfewEndToday = LocalDate.now().atTime(curfewEnd)
    val curfewStartTomorrow = LocalDate.now().plusDays(1).atTime(curfewStart)
    val curfewEndTomorrow = LocalDate.now().plusDays(1).atTime(curfewEnd)

    val inCurfew: Boolean
    val targetDateTime: LocalDateTime

    if (currentDateTimeForCalculation.isAfter(curfewStartToday) || currentDateTimeForCalculation.isBefore(curfewEndToday)) {
        // Если сейчас между 23:00 текущего дня и 05:00 следующего дня, значит, мы в комендантском часе.
        // Цель - конец комендантского часа сегодня (т.е. завтра утром)
        inCurfew = true
        targetDateTime = if (currentDateTimeForCalculation.isBefore(curfewEndToday)) curfewEndToday else curfewEndTomorrow
    } else {
        // Если сейчас между 05:00 и 23:00, мы не в комендантском часе.
        // Цель - начало комендантского часа сегодня (если оно еще не прошло) или завтра.
        inCurfew = false
        targetDateTime = if (currentDateTimeForCalculation.isBefore(curfewStartToday)) curfewStartToday else curfewStartTomorrow
    }

    val remainingDuration = Duration.between(currentDateTimeForCalculation, targetDateTime) // Используем currentDateTimeForCalculation
    val hours = remainingDuration.toHours()
    val minutes = remainingDuration.toMinutes() % 60
    val seconds = remainingDuration.seconds % 60

    // ... (остальной код UI, использующий hours, minutes, seconds и now для отображения текущего времени) ...

    val statusColor = when {
        inCurfew -> MaterialTheme.colorScheme.error
        remainingDuration.toMinutes() > 120 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    val curfewIcon = if (inCurfew) Icons.Default.NightsStay else Icons.Default.WbSunny
    val curfewIconTint = if (inCurfew) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary

    val homeStatusIcon = when (isAtHome) {
        true -> Icons.Default.Home
        false -> Icons.Default.LocationOff
        null -> Icons.Default.LocationOff
    }
    val homeStatusIconTint = when (isAtHome) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    val homeStatusText = when (isAtHome) {
        true -> "Вы дома"
        false -> "Вы не дома"
        null -> "Статус дома неизвестен (нет данных или разрешений)"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
                Icon(
                    imageVector = curfewIcon,
                    contentDescription = "Curfew Status Icon",
                    tint = curfewIconTint,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (inCurfew) "Сейчас комендантский час." else "До комендантского часа",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "%02d:%02d:%02d".format(hours, minutes, seconds), // Использует часы/минуты/секунды, рассчитанные на основе 'now' state
                    fontSize = 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = statusColor
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Текущее время: ${now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}", // Использует 'now' state для отображения текущего времени
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = "Комендантский час: ${curfewStart.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${curfewEnd.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = homeStatusIcon,
                    contentDescription = "Home Status Icon",
                    tint = homeStatusIconTint,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = homeStatusText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "ver. 0.4b by monekx",
            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}