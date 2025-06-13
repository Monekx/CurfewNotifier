package com.monekx.curfewnotifier.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.android.gms.location.*
import com.google.gson.reflect.TypeToken
import com.monekx.curfewnotifier.MainActivity
import com.monekx.curfewnotifier.NotificationConfig
import com.monekx.curfewnotifier.dataStore
import com.monekx.curfewnotifier.gson
import com.monekx.curfewnotifier.NOTIFICATION_CONFIGS_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CurfewForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var homeLat: Double = 0.0
    private var homeLon: Double = 0.0
    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val CHANNEL_ID = "CurfewNotifierChannel"
    private val NOTIFICATION_ID = 101

    private var notificationConfigs: List<NotificationConfig> = emptyList()

    private val sentNotificationsThisCycle = mutableSetOf<Int>()
    private var lastCurfewStatus: Boolean? = null // Отслеживаем изменение статуса комендантского часа

    // Константы для Intent Action и Extra для эмуляции уведомлений
    companion object {
        const val ACTION_EMULATE_NOTIFICATION = "com.monekx.curfewnotifier.EMULATE_NOTIFICATION"
        const val EXTRA_EMULATE_MINUTES_VALUE = "extra_emulate_minutes_value" // Обновлено
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        startForeground(NOTIFICATION_ID, createBaseNotification("Сервис запущен").build())

        serviceScope.launch {
            loadHomeLocation()
            loadNotificationConfigs() // Загружаем конфиги асинхронно
            startCurfewMonitoring()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("CurfewService", "onStartCommand вызван. Action: ${intent?.action}, Extras: ${intent?.extras}")

        // Обработка Intent для эмуляции уведомления
        if (intent?.action == ACTION_EMULATE_NOTIFICATION) {
            val emulateMinutes = intent.getIntExtra(EXTRA_EMULATE_MINUTES_VALUE, -1) // Обновлено
            if (emulateMinutes != -1) {
                serviceScope.launch { // Запускаем корутину для асинхронной обработки
                    Log.d("CurfewService", "Получен ACTION_EMULATE_NOTIFICATION. EmulateMinutes: $emulateMinutes. Ждем загрузки конфигов (если еще нет)...")
                    // Гарантируем, что конфиги загружены, если вдруг этот Intent пришел очень рано
                    if (notificationConfigs.isEmpty()) {
                        loadNotificationConfigs() // Перезагружаем/убеждаемся, что они загружены
                        Log.d("CurfewService", "Конфигурации уведомлений были загружены по запросу эмуляции.")
                    }

                    // Теперь, когда конфиги гарантированно загружены, пытаемся эмулировать
                    val configToEmulate = notificationConfigs.find { it.minutesBefore == emulateMinutes }
                    if (configToEmulate != null && configToEmulate.enabled) {
                        val message = configToEmulate.message.ifBlank {
                            "Эмулированное уведомление за ${configToEmulate.minutesBefore} минут!"
                        }
                        sendNotification(message, configToEmulate.minutesBefore)
                        Log.d("CurfewService", "Эмулированное уведомление отправлено: '$message'.")
                    } else {
                        Log.w("CurfewService", "Не удалось эмулировать: конфигурация не найдена или отключена для $emulateMinutes минут. Текущие конфиги: ${notificationConfigs.map { it.minutesBefore }}.")
                    }
                }
            }
        }

        return START_STICKY // Сервис будет перезапущен, если его убьет система
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // Отменяем все корутины сервиса
        // Удаляем обновления местоположения при уничтожении сервиса
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback!!)
            Log.d("CurfewService", "Обновления местоположения остановлены.")
        }
        Log.d("CurfewService", "Сервис остановлен.")
    }

    // Создание канала уведомлений (для Android 8.0+)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Curfew Notifier"
            val descriptionText = "Уведомления о комендантском часе"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Создание базового уведомления для Foreground Service
    private fun createBaseNotification(contentText: String): NotificationCompat.Builder {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Curfew Notifier")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Пример иконки, замените на свою
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Не позволяет пользователю смахнуть уведомление
            .setPriority(NotificationCompat.PRIORITY_HIGH)
    }

    // Загрузка сохраненных координат дома из DataStore
    private suspend fun loadHomeLocation() {
        val latKey = intPreferencesKey("home_lat_int")
        val lonKey = intPreferencesKey("home_lon_int")
        val preferences = applicationContext.dataStore.data.first()

        val storedLatInt = preferences[latKey]
        val storedLonInt = preferences[lonKey]

        if (storedLatInt != null && storedLonInt != null) {
            homeLat = storedLatInt / 1_000_000.0
            homeLon = storedLonInt / 1_000_000.0
            Log.d("CurfewService", "Загружены координаты дома: $homeLat, $homeLon")
        } else {
            Log.w("CurfewService", "Координаты дома не найдены.")
        }
    }

    // Загрузка сохраненных конфигураций уведомлений из DataStore
    private suspend fun loadNotificationConfigs() {
        applicationContext.dataStore.data.first()[NOTIFICATION_CONFIGS_KEY]?.let { savedJson ->
            val type = object : TypeToken<List<NotificationConfig>>() {}.type
            notificationConfigs = gson.fromJson(savedJson, type)
            Log.d("CurfewService", "Загружены конфигурации уведомлений: ${notificationConfigs.size} штук.")
        } ?: run {
            notificationConfigs = emptyList()
            Log.w("CurfewService", "Конфигурации уведомлений не найдены.")
        }
    }

    // Основная логика мониторинга комендантского часа и местоположения
    private suspend fun startCurfewMonitoring() {
        val curfewStart = LocalTime.of(23, 0) // Начало комендантского часа
        val curfewEnd = LocalTime.of(5, 0)    // Конец комендантского часа

        while (serviceJob.isActive) {
            val now = LocalTime.now()
            val inCurfew = now.isAfter(curfewStart) || now.isBefore(curfewEnd)

            // Определяем ближайшее время комендантского часа для ОТОБРАЖЕНИЯ в основном уведомлении
            val nearestCurfewTime: LocalTime
            val curfewEventType: String // "Начало" или "Конец"

            if (inCurfew) {
                // Если сейчас комендантский час, то ближайшее событие - его конец
                nearestCurfewTime = curfewEnd
                curfewEventType = "Конец"
            } else {
                // Если комендантского часа нет, то ближайшее событие - его начало
                nearestCurfewTime = curfewStart
                curfewEventType = "Начало"
            }
            // Форматируем время для отображения (например, "23:00" или "05:00")
            val formattedCurfewTime = nearestCurfewTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

            // Правильное вычисление nextCurfewStartTime для логики пользовательских уведомлений "за X минут"
            // Эта переменная всегда указывает на *начало* следующего комендантского часа.
            val nextCurfewStartTime = if (now.isAfter(curfewStart)) curfewStart.plusHours(24) else curfewStart


            // Логика сброса sentNotificationsThisCycle:
            // Сбрасываем сет, когда статус меняется с "комендантский час" на "не комендантский час".
            // Это сигнализирует о начале нового цикла ожидания до комендантского часа.
            if (lastCurfewStatus == true && !inCurfew) {
                sentNotificationsThisCycle.clear()
                Log.d("CurfewService", "Сет отправленных уведомлений очищен (начало нового цикла).")
            }
            lastCurfewStatus = inCurfew

            // Проверяем, когда пользователь дома, и запрашиваем обновления местоположения
            if (homeLat != 0.0 && homeLon != 0.0) { // Проверяем, что координаты дома установлены
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // Обновляем каждые 5 секунд
                        .setMinUpdateDistanceMeters(10f)
                        .build()

                    // Убедимся, что locationCallback создаётся и запрашивается только один раз
                    // Если он уже зарегистрирован, не пытаемся регистрировать снова
                    if (locationCallback == null) {
                        locationCallback = object : LocationCallback() {
                            override fun onLocationResult(locationResult: LocationResult) {
                                locationResult.lastLocation?.let { location ->
                                    val distance = calculateDistance(
                                        homeLat, homeLon,
                                        location.latitude, location.longitude
                                    )
                                    Log.d("CurfewService", "Расстояние до дома: ${String.format("%.2f", distance)}м")

                                    val isUserAtHome = distance < 50.0
                                    Log.d("CurfewService", "Пользователь дома: $isUserAtHome")

                                    val homeStatusText = if (isUserAtHome) "Вы дома." else "Вы не дома."
                                    val fullStatusText = "$homeStatusText $curfewEventType коменд.часа: $formattedCurfewTime"
                                    updateNotification(createBaseNotification(fullStatusText))
                                } ?: Log.w("CurfewService", "onLocationResult: lastLocation is null.")
                            }
                            override fun onLocationAvailability(p0: LocationAvailability) {
                                Log.d("CurfewService", "Location availability: ${p0.isLocationAvailable}")
                            }
                        }
                        // Вызов requestLocationUpdates должен быть в главном потоке (MainThread/Looper)
                        withContext(Dispatchers.Main) {
                            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, null)
                            Log.d("CurfewService", "Запрошены обновления местоположения в Main потоке.")
                        }
                    }
                } else {
                    Log.w("CurfewService", "Отсутствует разрешение ACCESS_FINE_LOCATION для запроса местоположения.")
                }
            } else {
                Log.d("CurfewService", "Координаты дома не установлены, местоположение не отслеживается.")
            }

            // Логика для планирования уведомлений с кастомным текстом
            if (!inCurfew) { // Уведомления только до начала комендантского часа
                val timeUntilCurfew = Duration.between(now, nextCurfewStartTime)

                notificationConfigs.forEach { config ->
                    if (config.enabled) { // Проверяем, включено ли уведомление
                        val minutesBeforeCurfew = config.minutesBefore
                        val threshold = Duration.ofMinutes(minutesBeforeCurfew.toLong())

                        // Проверяем, находимся ли мы в окне уведомления (минута срабатывания)
                        // и не было ли оно уже отправлено в этом цикле.
                        if (timeUntilCurfew <= threshold && timeUntilCurfew > Duration.ofMinutes(minutesBeforeCurfew.toLong() - 1)) {
                            if (!sentNotificationsThisCycle.contains(minutesBeforeCurfew)) {
                                val notificationMessage = config.message.ifBlank {
                                    "До комендантского часа осталось ${minutesBeforeCurfew} минут!"
                                }
                                sendNotification(notificationMessage, minutesBeforeCurfew)
                                sentNotificationsThisCycle.add(minutesBeforeCurfew) // Добавляем в сет, чтобы не спамить
                                Log.d("CurfewService", "Уведомление '${minutesBeforeCurfew} мин.' отправлено и добавлено в сет.")
                            } else {
                                Log.d("CurfewService", "Уведомление '${minutesBeforeCurfew} мин.' уже было отправлено в этом цикле. Пропускаем.")
                            }
                        }
                    }
                }
            }

            delay(1000) // Проверяем каждую секунду
        }
    }

    // Обновление основного уведомления Foreground Service
    private fun updateNotification(notificationBuilder: NotificationCompat.Builder) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    // Отправка индивидуального уведомления с кастомным текстом
    private fun sendNotification(message: String, notificationUniqueId: Int) {
        // Проверяем разрешение на уведомления перед отправкой
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w("CurfewService", "Нет разрешения на POST_NOTIFICATIONS, уведомление не отправлено.")
            return
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Напоминание о комендантском часе")
            .setContentText(message) // Используем кастомный текст
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Пример иконки, замените на свою
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Уведомление исчезнет после нажатия
            .build()

        // Используем уникальный ID для каждого типа уведомления
        notificationManager.notify(NOTIFICATION_ID + notificationUniqueId, notification)
        Log.d("CurfewService", "Отправлено уведомление: '$message' (ID: ${NOTIFICATION_ID + notificationUniqueId}).")
    }

    // Функция для расчета расстояния между двумя географическими точками (в метрах)
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // Радиус Земли в метрах
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }
}