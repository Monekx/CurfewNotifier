package com.monekx.curfewnotifier.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.monekx.curfewnotifier.NotificationConfig
import com.monekx.curfewnotifier.dataStore
import com.monekx.curfewnotifier.NOTIFICATION_CONFIGS_KEY
import com.monekx.curfewnotifier.service.CurfewForegroundService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Импортируем новые ключи
import com.monekx.curfewnotifier.CURFEW_START_HOUR_KEY
import com.monekx.curfewnotifier.CURFEW_START_MINUTE_KEY
import com.monekx.curfewnotifier.CURFEW_END_HOUR_KEY
import com.monekx.curfewnotifier.CURFEW_END_MINUTE_KEY

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(context: Context) {
    val notificationConfigs = remember { mutableStateListOf<NotificationConfig>() }
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var inputMinutes by remember { mutableStateOf("") }
    var inputMessage by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<NotificationConfig?>(null) }
    var editInputMinutes by remember { mutableStateOf("") }
    var editInputMessage by remember { mutableStateOf("") }
    var editInputError by remember { mutableStateOf(false) }

    // Состояния для времени комендантского часа
    var curfewStartHour by remember { mutableStateOf("23") }
    var curfewStartMinute by remember { mutableStateOf("00") }
    var curfewEndHour by remember { mutableStateOf("05") }
    var curfewEndMinute by remember { mutableStateOf("00") }

    var curfewTimeInputError by remember { mutableStateOf(false) }


    val saveNotificationConfigs = {
        scope.launch {
            context.dataStore.edit { prefs ->
                val json = Gson().toJson(notificationConfigs.distinctBy { it.minutesBefore })
                prefs[NOTIFICATION_CONFIGS_KEY] = json
            }
        }
    }

    val saveCurfewTimes = {
        scope.launch {
            context.dataStore.edit { prefs ->
                val startHour = curfewStartHour.toIntOrNull()
                val startMinute = curfewStartMinute.toIntOrNull()
                val endHour = curfewEndHour.toIntOrNull()
                val endMinute = curfewEndMinute.toIntOrNull()

                if (startHour != null && startHour in 0..23 &&
                    startMinute != null && startMinute in 0..59 &&
                    endHour != null && endHour in 0..23 &&
                    endMinute != null && endMinute in 0..59
                ) {
                    prefs[CURFEW_START_HOUR_KEY] = startHour
                    prefs[CURFEW_START_MINUTE_KEY] = startMinute
                    prefs[CURFEW_END_HOUR_KEY] = endHour
                    prefs[CURFEW_END_MINUTE_KEY] = endMinute
                    curfewTimeInputError = false
                    Toast.makeText(context, "Время комендантского часа сохранено", Toast.LENGTH_SHORT).show()
                } else {
                    curfewTimeInputError = true
                    Toast.makeText(context, "Некорректное время комендантского часа", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    LaunchedEffect(Unit) {
        context.dataStore.data.first()[NOTIFICATION_CONFIGS_KEY]?.let { savedJson ->
            val type = object : TypeToken<List<NotificationConfig>>() {}.type
            val loadedConfigs: List<NotificationConfig> = Gson().fromJson(savedJson, type)
            notificationConfigs.addAll(loadedConfigs)
        }

        // Загрузка времени комендантского часа
        context.dataStore.data.first().let { prefs ->
            curfewStartHour = (prefs[CURFEW_START_HOUR_KEY] ?: 23).toString().padStart(2, '0')
            curfewStartMinute = (prefs[CURFEW_START_MINUTE_KEY] ?: 0).toString().padStart(2, '0')
            curfewEndHour = (prefs[CURFEW_END_HOUR_KEY] ?: 5).toString().padStart(2, '0')
            curfewEndMinute = (prefs[CURFEW_END_MINUTE_KEY] ?: 0).toString().padStart(2, '0')
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally // Центрируем содержимое по горизонтали
    ) {
        // Карточка для заголовка и кнопок управления
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) // Более контрастный фон для этой карточки
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Настройка уведомлений",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly, // Распределяем кнопки равномерно
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (notificationConfigs.isNotEmpty()) {
                                val randomConfig = notificationConfigs.random()
                                val serviceIntent = Intent(context, CurfewForegroundService::class.java).apply {
                                    action = CurfewForegroundService.ACTION_EMULATE_NOTIFICATION
                                    putExtra(CurfewForegroundService.EXTRA_EMULATE_MINUTES_VALUE, randomConfig.minutesBefore)
                                }
                                ContextCompat.startForegroundService(context, serviceIntent)
                                Log.d("NotificationSettingsScreen", "Отправлен запрос на эмуляцию уведомления за ${randomConfig.minutesBefore} минут.")
                            } else {
                                Log.d("NotificationSettingsScreen", "Список уведомлений пуст, невозможно эмулировать.")
                                Toast.makeText(context, "Список уведомлений пуст, добавьте новое.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary) // Изменим цвет кнопки
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = "Эмулировать уведомление", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Тест")
                    }
                    Button(
                        onClick = {
                            inputMinutes = ""
                            inputMessage = ""
                            inputError = false
                            showAddDialog = true
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) // Основной цвет для добавления
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить уведомление", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Добавить")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp)) // Отступ между карточками

        // НОВАЯ КАРТОЧКА: Настройка времени комендантского часа
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Время комендантского часа",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Начало:", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = curfewStartHour,
                        onValueChange = { newValue ->
                            if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                                curfewStartHour = newValue
                                curfewTimeInputError = false
                            }
                        },
                        label = { Text("Час") },
                        modifier = Modifier.width(70.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = curfewTimeInputError
                    )
                    Text(":", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = curfewStartMinute,
                        onValueChange = { newValue ->
                            if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                                curfewStartMinute = newValue
                                curfewTimeInputError = false
                            }
                        },
                        label = { Text("Мин") },
                        modifier = Modifier.width(70.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = curfewTimeInputError
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Конец:", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = curfewEndHour,
                        onValueChange = { newValue ->
                            if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                                curfewEndHour = newValue
                                curfewTimeInputError = false
                            }
                        },
                        label = { Text("Час") },
                        modifier = Modifier.width(70.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = curfewTimeInputError
                    )
                    Text(":", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = curfewEndMinute,
                        onValueChange = { newValue ->
                            if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                                curfewEndMinute = newValue
                                curfewTimeInputError = false
                            }
                        },
                        label = { Text("Мин") },
                        modifier = Modifier.width(70.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = curfewTimeInputError
                    )
                }
                if (curfewTimeInputError) {
                    Text("Введите корректное время (ЧЧ:ММ).", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { saveCurfewTimes() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Сохранить время комендантского часа")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp)) // Отступ между карточками

        // Карточка для списка уведомлений
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ваши настроенные уведомления:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (notificationConfigs.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = "No Notifications",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Список уведомлений пуст.\nНажмите 'Добавить', чтобы настроить напоминания.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) { // Увеличиваем max height
                        items(notificationConfigs.sortedByDescending { it.minutesBefore }) { config ->
                            Card( // Каждая запись уведомления в своей карточке
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), // Небольшая тень для каждого элемента
                                shape = MaterialTheme.shapes.small, // Меньшие скругления для элементов списка
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface) // Фон для каждого элемента
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) { // Занимает доступное пространство
                                        Text("За ${config.minutesBefore} минут", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        if (config.message.isNotBlank()) {
                                            Text(config.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = config.enabled,
                                            onCheckedChange = { isChecked ->
                                                val index = notificationConfigs.indexOfFirst { it.minutesBefore == config.minutesBefore }
                                                if (index != -1) {
                                                    notificationConfigs[index] = config.copy(enabled = isChecked)
                                                    saveNotificationConfigs()
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(onClick = {
                                            editingConfig = config
                                            editInputMinutes = config.minutesBefore.toString()
                                            editInputMessage = config.message
                                            editInputError = false
                                            showEditDialog = true
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = MaterialTheme.colorScheme.onSurfaceVariant) // Цвет иконки
                                        }
                                        IconButton(onClick = {
                                            notificationConfigs.remove(config)
                                            saveNotificationConfigs()
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Добавить уведомление", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputMinutes,
                        onValueChange = {
                            inputMinutes = it
                            inputError = false
                        },
                        label = { Text("Минут до комендантского часа") },
                        isError = inputError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )
                    if (inputError) {
                        Text("Введите корректное число (например, 10, 60, 120).", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputMessage,
                        onValueChange = { inputMessage = it },
                        label = { Text("Текст уведомления (необязательно)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val minutes = inputMinutes.toIntOrNull()
                        if (minutes != null && minutes > 0) {
                            if (notificationConfigs.none { it.minutesBefore == minutes }) {
                                notificationConfigs.add(NotificationConfig(minutes, inputMessage, true))
                                saveNotificationConfigs()
                            }
                            showAddDialog = false
                            inputMinutes = ""
                            inputMessage = ""
                            inputError = false
                        } else {
                            inputError = true
                        }
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showAddDialog = false
                        inputMinutes = ""
                        inputMessage = ""
                        inputError = false
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showEditDialog && editingConfig != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Редактировать уведомление", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editInputMinutes,
                        onValueChange = {
                            editInputMinutes = it
                            editInputError = false
                        },
                        label = { Text("Минут до комендантского часа") },
                        isError = editInputError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )
                    if (editInputError) {
                        Text("Введите корректное число (например, 10, 60, 120).", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editInputMessage,
                        onValueChange = { editInputMessage = it },
                        label = { Text("Текст уведомления (необязательно)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newMinutes = editInputMinutes.toIntOrNull()
                        if (newMinutes != null && newMinutes > 0) {
                            val existingConfig = notificationConfigs.find { it.minutesBefore == newMinutes }
                            if (newMinutes != editingConfig!!.minutesBefore && existingConfig != null) {
                                editInputError = true
                                return@Button
                            }

                            val index = notificationConfigs.indexOf(editingConfig)
                            if (index != -1) {
                                notificationConfigs[index] = editingConfig!!.copy(minutesBefore = newMinutes, message = editInputMessage)
                                saveNotificationConfigs()
                            }
                            showEditDialog = false
                            editingConfig = null
                            editInputMinutes = ""
                            editInputMessage = ""
                            editInputError = false
                        } else {
                            editInputError = true
                        }
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showEditDialog = false
                        editingConfig = null
                        editInputMinutes = ""
                        editInputMessage = ""
                        editInputError = false
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}