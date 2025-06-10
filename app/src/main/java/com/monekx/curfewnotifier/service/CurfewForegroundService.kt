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
import com.monekx.curfewnotifier.dataStore // Импортируем dataStore
import com.monekx.curfewnotifier.gson // Импортируем gson
import com.monekx.curfewnotifier.NOTIFICATION_CONFIGS_KEY // Импортируем ключ
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    // --- НОВОЕ: Список конфигураций уведомлений ---
    private var notificationConfigs: List<NotificationConfig> = emptyList()
    // --- КОНЕЦ НОВОГО ---

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        startForeground(NOTIFICATION_ID, createBaseNotification("Сервис запущен").build())

        // Загружаем координаты дома и конфигурации уведомлений при старте сервиса
        serviceScope.launch {
            loadHomeLocation()
            loadNotificationConfigs()
            startCurfewMonitoring() // Запускаем мониторинг после загрузки данных
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Сервис будет перезапущен, если его убьет система
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // Отменяем все корутины сервиса
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback!!)
        }
        Log.d("CurfewNotifierService", "Сервис остановлен.")
    }

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
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Пример иконки
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Не позволяет пользователю смахнуть уведомление
            .setPriority(NotificationCompat.PRIORITY_HIGH)
    }

    private suspend fun loadHomeLocation() {
        val latKey = intPreferencesKey("home_lat_int")
        val lonKey = intPreferencesKey("home_lon_int")
        val preferences = applicationContext.dataStore.data.first()

        val storedLatInt = preferences[latKey]
        val storedLonInt = preferences[lonKey]

        if (storedLatInt != null && storedLonInt != null) {
            homeLat = storedLatInt / 1_000_000.0
            homeLon = storedLonInt / 1_000_000.0
            Log.d("CurfewNotifierService", "Загружены координаты дома: $homeLat, $homeLon")
        } else {
            Log.d("CurfewNotifierService", "Координаты дома не найдены.")
        }
    }

    // --- НОВАЯ ФУНКЦИЯ: Загрузка конфигураций уведомлений ---
    private suspend fun loadNotificationConfigs() {
        applicationContext.dataStore.data.first()[NOTIFICATION_CONFIGS_KEY]?.let { savedJson ->
            val type = object : TypeToken<List<NotificationConfig>>() {}.type
            notificationConfigs = gson.fromJson(savedJson, type)
            Log.d("CurfewNotifierService", "Загружены конфигурации уведомлений: ${notificationConfigs.size} штук.")
        } ?: run {
            notificationConfigs = emptyList()
            Log.d("CurfewNotifierService", "Конфигурации уведомлений не найдены.")
        }
    }
    // --- КОНЕЦ НОВОЙ ФУНКЦИИ ---

    private suspend fun startCurfewMonitoring() {
        val curfewStart = LocalTime.of(23, 0)
        val curfewEnd = LocalTime.of(5, 0)

        // Обновляем список конфигураций уведомлений при каждом запуске цикла
        // и при каждом изменении (хотя пока не реализовано, но это место для refresh)
        // Чтобы быть уверенными в актуальности, можно перечитывать его
        // или настроить Flow для реактивного обновления.
        // Пока оставим так, как будто он загружается один раз при старте сервиса.
        // Если понадобится динамическое обновление во время работы сервиса,
        // нужно будет подписываться на изменения DataStore.

        while (serviceJob.isActive) {
            val now = LocalTime.now()
            val inCurfew = now.isAfter(curfewStart) || now.isBefore(curfewEnd)
            val endCurfew = if (now.isBefore(curfewEnd)) curfewEnd else curfewEnd.plusHours(24) // Конец текущего комендантского часа
            val startCurfew = if (now.isAfter(curfewStart)) curfewStart.plusHours(24) else curfewStart // Начало следующего комендантского часа

            // Проверяем, когда пользователь дома
            if (homeLat != 0.0 && homeLon != 0.0) {
                // Если у нас есть разрешение на местоположение, получаем его
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // Обновляем каждые 5 секунд
                        .setMinUpdateDistanceMeters(10f)
                        .build()

                    locationCallback = object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult) {
                            locationResult.lastLocation?.let { location ->
                                val distance = calculateDistance(
                                    homeLat, homeLon,
                                    location.latitude, location.longitude
                                )
                                // Логируем текущее расстояние для отладки
                                Log.d("CurfewNotifierService", "Расстояние до дома: ${distance}м")

                                // --- НОВОЕ: Проверка нахождения дома с радиусом 50м ---
                                val isUserAtHome = distance < 50.0 // Радиус 50 метров
                                Log.d("CurfewNotifierService", "Пользователь дома: $isUserAtHome")
                                // --- КОНЕЦ НОВОГО ---

                                // Обновляем основное уведомление сервиса
                                val statusText = if (inCurfew) "Комендантский час." else "До комендантского часа."
                                val homeStatusText = if (isUserAtHome) "Вы дома." else "Вы не дома."
                                updateNotification(createBaseNotification("$statusText $homeStatusText"))
                            }
                        }
                    }
                    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, null)
                }
            }


            // --- НОВОЕ: Логика для планирования уведомлений с кастомным текстом ---
            if (!inCurfew) { // Уведомления только до начала комендантского часа
                val timeUntilCurfew = Duration.between(now, startCurfew)

                notificationConfigs.forEach { config ->
                    if (config.enabled) { // Проверяем, включено ли уведомление
                        val minutesBeforeCurfew = config.minutesBefore
                        val threshold = Duration.ofMinutes(minutesBeforeCurfew.toLong())

                        // Проверяем, находимся ли мы в окне уведомления
                        // и еще не отправили его (нужно добавить механизм отслеживания отправленных)
                        if (timeUntilCurfew <= threshold && timeUntilCurfew > Duration.ofMinutes(minutesBeforeCurfew.toLong() - 1)) {
                            // TODO: Здесь нужен механизм, чтобы уведомление отправлялось только один раз.
                            // Можно хранить Set<Int> уже отправленных уведомлений за текущий цикл (день).
                            // Для простоты примера, пока что будет отправляться каждый раз, когда условие истинно (каждую секунду).
                            // В реальном приложении нужно добавить логику для предотвращения спама.

                            val notificationMessage = config.message.ifBlank {
                                "До комендантского часа осталось ${minutesBeforeCurfew} минут!"
                            }
                            sendNotification(notificationMessage, minutesBeforeCurfew) // Передаем кастомный текст и уникальный ID
                        }
                    }
                }
            }
            // --- КОНЕЦ НОВОГО ---

            delay(1000) // Проверяем каждую секунду
        }
    }

    private fun updateNotification(notificationBuilder: NotificationCompat.Builder) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    // --- НОВАЯ ФУНКЦИЯ: Отправка уведомления с кастомным текстом ---
    private fun sendNotification(message: String, notificationUniqueId: Int) {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w("CurfewNotifierService", "Нет разрешения на POST_NOTIFICATIONS.")
            return
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Напоминание о комендантском часе")
            .setContentText(message) // Используем кастомный текст
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Уведомление исчезнет после нажатия
            .build()

        // Используем уникальный ID для каждого типа уведомления,
        // чтобы они не перезаписывали друг друга (например, 120, 60, 30 минут)
        // Прибавляем к NOTIFICATION_ID, чтобы не конфликтовать с основным уведомлением сервиса.
        notificationManager.notify(NOTIFICATION_ID + notificationUniqueId, notification)
        Log.d("CurfewNotifierService", "Отправлено уведомление: '$message' за $notificationUniqueId минут.")
    }
    // --- КОНЕЦ НОВОЙ ФУНКЦИИ ---

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