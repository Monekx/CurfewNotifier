package com.monekx.curfewnotifier

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.monekx.curfewnotifier.service.CurfewForegroundService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalTime
import androidx.compose.ui.text.input.KeyboardType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent // Добавляем PendingIntent
import android.util.Log
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.app.NotificationCompat // Добавляем NotificationCompat
import com.monekx.curfewnotifier.ui.theme.CurfewNotifierTheme


// Добавляем класс для представления данных уведомления
data class NotificationConfig(
    val minutesBefore: Int,
    val message: String,
    var enabled: Boolean = true // Добавляем состояние включения/выключения
)

// Используем dataStore, как и раньше
val Context.dataStore by preferencesDataStore(name = "settings")

// Ключ для хранения набора конфигураций уведомлений (будем хранить как JSON строку)
val NOTIFICATION_CONFIGS_KEY = stringPreferencesKey("notification_configs")

// Инициализируем Gson для сериализации/десериализации JSON
val gson = Gson()

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_CODE = 1001
        private const val TEST_NOTIFICATION_ID = 9999 // ID для тестовых уведомлений
    }

    private lateinit var mapLauncher: ActivityResultLauncher<Intent>

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Создаем канал уведомлений (если он еще не создан)
        createNotificationChannel()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = "package:$packageName".toUri()
            startActivity(intent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
            }
        }

        val serviceIntent = Intent(this, CurfewForegroundService::class.java)
        startForegroundService(serviceIntent)

        mapLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val lat = data.getDoubleExtra("lat", 0.0)
                    val lon = data.getDoubleExtra("lon", 0.0)
                    lifecycleScope.launch {
                        applicationContext.dataStore.edit { prefs ->
                            prefs[intPreferencesKey("home_lat_int")] = (lat * 1_000_000).toInt()
                            prefs[intPreferencesKey("home_lon_int")] = (lon * 1_000_000).toInt()
                        }
                    }
                }
            }
        }

        setContent {
            CurfewNotifierTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(applicationContext, mapLauncher)
                }
            }
        }
    }

    // --- НОВАЯ ФУНКЦИЯ: Создание канала уведомлений (если еще не создан) ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Curfew Notifier"
            val descriptionText = "Уведомления о комендантском часе"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("CurfewNotifierChannel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    // --- КОНЕЦ НОВОЙ ФУНКЦИИ ---

    // --- НОВАЯ ФУНКЦИЯ: Отправка тестового уведомления ---
    fun sendTestNotification(context: Context, minutesBefore: Int, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w("MainActivity", "Нет разрешения на POST_NOTIFICATIONS для тестового уведомления.")
                // Можно запросить разрешение здесь, но лучше сделать это при старте приложения
                return
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "CurfewNotifierChannel")
            .setContentTitle("ТЕСТ: Напоминание о комендантском часе")
            .setContentText(message.ifBlank { "Тестовое уведомление за $minutesBefore минут." })
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // Добавляем ContentIntent
            .build()

        // Используем уникальный ID для тестовых уведомлений, чтобы не конфликтовать с сервисными
        // и чтобы каждое новое тестовое уведомление отображалось отдельно.
        notificationManager.notify(TEST_NOTIFICATION_ID + minutesBefore, notification)
        Log.d("MainActivity", "Отправлено тестовое уведомление: '$message' за $minutesBefore минут.")
    }
    // --- КОНЕЦ НОВОЙ ФУНКЦИИ ---
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(context: Context, mapLauncher: ActivityResultLauncher<Intent>) {
    val curfewStart = LocalTime.of(23, 0)
    val curfewEnd = LocalTime.of(5, 0)

    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1000)
        }
    }

    val inCurfew = now.isAfter(curfewStart) || now.isBefore(curfewEnd)
    val end = if (inCurfew) {
        if (now.isBefore(curfewEnd)) curfewEnd else curfewEnd.plusHours(24)
    } else curfewStart

    val remainingDuration = Duration.between(now, end)
    val hours = remainingDuration.toHours()
    val minutes = remainingDuration.toMinutes() % 60
    val seconds = remainingDuration.seconds % 60

    val statusColor = when {
        inCurfew -> Color(0xFFF44336)
        remainingDuration.toMinutes() > 120 -> Color(0xFF4CAF50)
        else -> Color(0xFFFFC107)
    }

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

    val saveNotificationConfigs = {
        scope.launch {
            context.dataStore.edit { prefs ->
                val json = gson.toJson(notificationConfigs.distinctBy { it.minutesBefore })
                prefs[NOTIFICATION_CONFIGS_KEY] = json
            }
        }
    }

    LaunchedEffect(Unit) {
        context.dataStore.data.first()[NOTIFICATION_CONFIGS_KEY]?.let { savedJson ->
            val type = object : TypeToken<List<NotificationConfig>>() {}.type
            val loadedConfigs: List<NotificationConfig> = gson.fromJson(savedJson, type)
            notificationConfigs.addAll(loadedConfigs)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (inCurfew)
                "Сейчас комендантский час. Осталось %02d:%02d:%02d".format(hours, minutes, seconds)
            else
                "До комендантского часа %02d:%02d:%02d".format(hours, minutes, seconds),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )

        Button(onClick = {
            val intent = Intent(context, MapActivity::class.java)
            mapLauncher.launch(intent)
        }) {
            Text("Установить / изменить точку дома")
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Уведомления:", fontWeight = FontWeight.SemiBold)
                // --- НОВАЯ КНОПКА: Триггер случайного уведомления ---
                Button(onClick = {
                    if (notificationConfigs.isNotEmpty()) {
                        val randomConfig = notificationConfigs.random() // Выбираем случайную конфигурацию
                        // Вызываем функцию отправки тестового уведомления из MainActivity
                        (context as? MainActivity)?.sendTestNotification(context, randomConfig.minutesBefore, randomConfig.message)
                    } else {
                        // Опционально: показать сообщение, если список уведомлений пуст
                        Log.d("MainScreen", "Список уведомлений пуст, невозможно отправить тестовое.")
                    }
                }) {
                    Text("Тест увед.")
                }
                // --- КОНЕЦ НОВОЙ КНОПКИ ---

                Button(onClick = {
                    inputMinutes = ""
                    inputMessage = ""
                    inputError = false
                    showAddDialog = true
                }) {
                    Text("Добавить")
                }
            }

            if (notificationConfigs.isEmpty()) {
                Text("Нажмите 'Добавить', чтобы установить время и текст уведомлений.", color = Color.Gray)
            } else {
                notificationConfigs.sortedByDescending { it.minutesBefore }.forEach { config ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("за ${config.minutesBefore} минут")
                            if (config.message.isNotBlank()) {
                                Text(config.message, fontSize = 12.sp, color = Color.Gray)
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
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                editingConfig = config
                                editInputMinutes = config.minutesBefore.toString()
                                editInputMessage = config.message
                                editInputError = false
                                showEditDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                            }
                            IconButton(onClick = {
                                notificationConfigs.remove(config)
                                saveNotificationConfigs()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        ClickableText(
            text = AnnotatedString("ver. 0.1 by monekx"),
            onClick = {},
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp),
            style = LocalTextStyle.current.copy(color = Color.Gray, fontSize = 12.sp)
        )
    }

    // --- Диалоговое окно для ДОБАВЛЕНИЯ нового времени и сообщения ---
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Добавить уведомление") },
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
                        singleLine = true
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
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
                }) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showAddDialog = false
                    inputMinutes = ""
                    inputMessage = ""
                    inputError = false
                }) {
                    Text("Отмена")
                }
            }
        )
    }

    // --- Диалоговое окно для РЕДАКТИРОВАНИЯ существующего уведомления ---
    if (showEditDialog && editingConfig != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Редактировать уведомление") },
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
                        singleLine = true
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
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
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showEditDialog = false
                    editingConfig = null
                    editInputMinutes = ""
                    editInputMessage = ""
                    editInputError = false
                }) {
                    Text("Отмена")
                }
            }
        )
    }
}