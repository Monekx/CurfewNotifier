package com.monekx.curfewnotifier

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
// УДАЛЕНЫ импорты Accompanist:
// import com.google.accompanist.swiperefresh.SwipeRefresh
// import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
// import com.google.accompanist.swiperefresh.SwipeRefreshIndicator

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.compose.foundation.text.KeyboardOptions
import com.monekx.curfewnotifier.data.RssItem
import com.monekx.curfewnotifier.repository.NewsRepository
import com.monekx.curfewnotifier.ui.theme.CurfewNotifierTheme

// Класс для представления данных уведомления
data class NotificationConfig(
    val minutesBefore: Int,
    val message: String,
    var enabled: Boolean = true
)

// DataStore для настроек
val Context.dataStore by preferencesDataStore(name = "settings")

// Ключ для хранения набора конфигураций уведомлений
val NOTIFICATION_CONFIGS_KEY = stringPreferencesKey("notification_configs")

// Инициализируем Gson
val gson = Gson()

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_CODE_NOTIFICATIONS = 1001
    }

    private lateinit var mapLauncher: ActivityResultLauncher<Intent>
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = "package:$packageName".toUri()
            startActivity(intent)
        }

        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineLocationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted || coarseLocationGranted) {
                Log.d("MainActivity", "Разрешение на местоположение получено.")
                startLocationService()
            } else {
                Log.w("MainActivity", "Разрешение на местоположение отклонено. Сервис не будет запущен.")
            }
        }

        notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity", "Разрешение на POST_NOTIFICATIONS получено.")
            } else {
                Log.w("MainActivity", "Разрешение на POST_NOTIFICATIONS отклонено. Уведомления могут не отображаться.")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        checkAndRequestLocationPermissions()

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

    private fun checkAndRequestLocationPermissions() {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationPermission || coarseLocationPermission) {
            Log.d("MainActivity", "Разрешения на местоположение уже есть.")
            startLocationService()
        } else {
            Log.d("MainActivity", "Запрашиваем разрешения на местоположение.")
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startLocationService() {
        Log.d("MainActivity", "Попытка запуска CurfewForegroundService.")
        val serviceIntent = Intent(this, CurfewForegroundService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
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

    // Состояние для новостей
    val newsItems = remember { mutableStateListOf<RssItem>() }
    val newsRepository = remember { NewsRepository() }
    var isLoadingNews by remember { mutableStateOf(false) }
    var newsError by remember { mutableStateOf<String?>(null) }

    // --- УДАЛЕНЫ состояния Pull-to-Refresh: ---
    // val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isLoadingNews)
    // --- КОНЕЦ УДАЛЕНИЯ ---

    val fetchNews: suspend () -> Unit = {
        isLoadingNews = true // Показываем индикатор загрузки
        newsError = null
        val loadedNews = newsRepository.getCurfewNews()
        newsItems.clear()
        if (loadedNews.isNotEmpty()) {
            newsItems.addAll(loadedNews)
        } else {
            newsError = "Не удалось загрузить новости. Проверьте подключение к Интернету или источник новостей."
            Toast.makeText(context, newsError, Toast.LENGTH_SHORT).show()
        }
        isLoadingNews = false // Скрываем индикатор загрузки
    }

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

        fetchNews() // Инициальная загрузка новостей
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
                Text(
                    text = "Уведомления:",
                    style = MaterialTheme.typography.headlineSmall
                )
                Button(onClick = {
                    if (notificationConfigs.isNotEmpty()) {
                        val randomConfig = notificationConfigs.random()
                        val serviceIntent = Intent(context, CurfewForegroundService::class.java).apply {
                            action = CurfewForegroundService.ACTION_EMULATE_NOTIFICATION
                            putExtra(CurfewForegroundService.EXTRA_EMULATE_MINUTES_VALUE, randomConfig.minutesBefore)
                        }
                        ContextCompat.startForegroundService(context, serviceIntent)
                        Log.d("MainScreen", "Отправлен запрос на эмуляцию уведомления за ${randomConfig.minutesBefore} минут.")
                    } else {
                        Log.d("MainScreen", "Список уведомлений пуст, невозможно эмулировать.")
                        Toast.makeText(context, "Список уведомлений пуст, добавьте новое.", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Эмул. увед.")
                }

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
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(notificationConfigs.sortedByDescending { it.minutesBefore }) { config ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("за ${config.minutesBefore} минут", style = MaterialTheme.typography.bodyLarge)
                                if (config.message.isNotBlank()) {
                                    Text(config.message, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
                        HorizontalDivider()
                    }
                }
            }
        }

        // Раздел новостей
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Новости:",
            style = MaterialTheme.typography.headlineSmall
        )

        // --- ИСПРАВЛЕНИЕ: Добавление кнопки "Обновить новости" ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End // Выравниваем кнопку по правому краю
        ) {
            Button(onClick = { scope.launch { fetchNews() } }) {
                Text("Обновить новости")
            }
        }
        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---


        // Отображение состояния загрузки/ошибки/пустого списка
        when {
            isLoadingNews -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            newsError != null -> Text(newsError!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
            newsItems.isEmpty() -> Text("Нет новостей для отображения.", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(newsItems) { news ->
                        NewsItem(news = news, context = context)
                        HorizontalDivider()
                    }
                }
            }
        }


        // Текст версии приложения (находится вне LazyColumn)
        Text( // Используем обычный Text
            text = "ver. 0.1 by monekx",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp),
            style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
        )
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

// Composable для отображения отдельной новости
@Composable
fun NewsItem(news: RssItem, context: Context) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .clickable {
                    news.link?.let { url ->
                        Log.d("NewsItem", "Клик по новости. Открытие в WebView: $url")
                        try {
                            if (url.isNotBlank()) {
                                val intent = Intent(context, WebViewActivity::class.java).apply {
                                    putExtra(WebViewActivity.EXTRA_URL, url)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ContextCompat.startActivity(context, intent, null)
                            } else {
                                Log.e("NewsItem", "Некорректный или пустой URL новости: '$url'")
                                Toast.makeText(context, "Некорректная ссылка на новость.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("NewsItem", "Ошибка при открытии ссылки в WebView: $url", e)
                            Toast.makeText(context, "Не удалось открыть новость.", Toast.LENGTH_SHORT).show()
                        }
                    } ?: Log.e("NewsItem", "Ссылка новости равна null.")
                }
        ) {
            Text(
                text = news.title ?: "Без заголовка",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            news.description?.let { desc ->
                Text(
                    text = desc.take(150) + if (desc.length > 150) "..." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            news.pubDate?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.End)
                )
            }
            news.link?.let { url ->
                val annotatedText = buildAnnotatedString {
                    append("Читать далее")
                    addStyle(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.tertiary,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = 0,
                        end = length
                    )
                }
                ClickableText(
                    text = annotatedText,
                    onClick = {
                        Log.d("NewsItem", "Клик по 'Читать далее'. Открытие в WebView: $url")
                        try {
                            if (url.isNotBlank()) {
                                val intent = Intent(context, WebViewActivity::class.java).apply {
                                    putExtra(WebViewActivity.EXTRA_URL, url)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ContextCompat.startActivity(context, intent, null)
                            } else {
                                Log.e("NewsItem", "Некорректный или пустой URL для 'Читать далее': '$url'")
                                Toast.makeText(context, "Некорректная ссылка на новость.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("NewsItem", "Ошибка при открытии ссылки 'Читать далее' в WebView: $url", e)
                            Toast.makeText(context, "Не удалось открыть новость.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.tertiary)
                )
            }
        }
    }
}