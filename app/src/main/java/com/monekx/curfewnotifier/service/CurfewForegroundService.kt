package com.monekx.curfewnotifier.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
import java.time.LocalDateTime
import java.time.LocalDate

import com.monekx.curfewnotifier.CURFEW_START_HOUR_KEY
import com.monekx.curfewnotifier.CURFEW_START_MINUTE_KEY
import com.monekx.curfewnotifier.CURFEW_END_HOUR_KEY
import com.monekx.curfewnotifier.CURFEW_END_MINUTE_KEY
import com.monekx.curfewnotifier.HOME_RADIUS_METERS_KEY


class CurfewForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var homeLat: Double = 0.0
    private var homeLon: Double = 0.0
    @Volatile
    private var homeRadiusMeters: Int = 50
    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val CHANNEL_ID = "CurfewNotifierChannel"
    private val NOTIFICATION_ID = 101

    private var notificationConfigs: List<NotificationConfig> = emptyList()

    private val sentNotificationsThisCycle = mutableSetOf<Int>()
    private var lastCurfewStatus: Boolean? = null

    private val IS_AT_HOME_KEY = booleanPreferencesKey("is_at_home")

    @Volatile
    private var curfewStartHour: Int = 23
    @Volatile
    private var curfewStartMinute: Int = 0
    @Volatile
    private var curfewEndHour: Int = 5
    @Volatile
    private var curfewEndMinute: Int = 0

    companion object {
        const val ACTION_EMULATE_NOTIFICATION = "com.monekx.curfewnotifier.EMULATE_NOTIFICATION"
        const val EXTRA_EMULATE_MINUTES_VALUE = "extra_emulate_minutes_value"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        startForeground(NOTIFICATION_ID, createBaseNotification(
            "Сервис запущен",
            "Загрузка времени...",
            "Определение местоположения..."
        ).build())

        serviceScope.launch {
            applicationContext.dataStore.data.first().let { prefs ->
                val storedLatInt = prefs[intPreferencesKey("home_lat_int")]
                val storedLonInt = prefs[intPreferencesKey("home_lon_int")]
                if (storedLatInt != null && storedLonInt != null) {
                    homeLat = storedLatInt / 1_000_000.0
                    homeLon = storedLonInt / 1_000_000.0
                }
                homeRadiusMeters = prefs[HOME_RADIUS_METERS_KEY] ?: 50
                curfewStartHour = prefs[CURFEW_START_HOUR_KEY] ?: 23
                curfewStartMinute = prefs[CURFEW_START_MINUTE_KEY] ?: 0
                curfewEndHour = prefs[CURFEW_END_HOUR_KEY] ?: 5
                curfewEndMinute = prefs[CURFEW_END_MINUTE_KEY] ?: 0
                Log.d("CurfewService", "Initial settings loaded: Home(${homeLat}, ${homeLon}) Radius: ${homeRadiusMeters}m, Curfew: ${curfewStartHour}:${curfewStartMinute} - ${curfewEndHour}:${curfewEndMinute}")
            }

            launch {
                applicationContext.dataStore.data.collect { prefs ->
                    val storedLatInt = prefs[intPreferencesKey("home_lat_int")]
                    val storedLonInt = prefs[intPreferencesKey("home_lon_int")]
                    if (storedLatInt != null && storedLonInt != null) {
                        homeLat = storedLatInt / 1_000_000.0
                        homeLon = storedLonInt / 1_000_000.0
                    }
                    homeRadiusMeters = prefs[HOME_RADIUS_METERS_KEY] ?: 50
                    curfewStartHour = prefs[CURFEW_START_HOUR_KEY] ?: 23
                    curfewStartMinute = prefs[CURFEW_START_MINUTE_KEY] ?: 0
                    curfewEndHour = prefs[CURFEW_END_HOUR_KEY] ?: 5
                    curfewEndMinute = prefs[CURFEW_END_MINUTE_KEY] ?: 0
                    Log.d("CurfewService", "Settings updated from DataStore via collect: Home(${homeLat}, ${homeLon}) Radius: ${homeRadiusMeters}m, Curfew: ${curfewStartHour}:${curfewStartMinute} - ${curfewEndHour}:${curfewEndMinute}")
                }
            }

            loadNotificationConfigs()

            startCurfewMonitoring()
            startLocationUpdates()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("CurfewService", "onStartCommand вызван. Action: ${intent?.action}, Extras: ${intent?.extras}")

        if (intent?.action == ACTION_EMULATE_NOTIFICATION) {
            val emulateMinutes = intent.getIntExtra(EXTRA_EMULATE_MINUTES_VALUE, -1)
            if (emulateMinutes != -1) {
                serviceScope.launch {
                    if (notificationConfigs.isEmpty()) {
                        loadNotificationConfigs()
                    }

                    val configToEmulate = notificationConfigs.find { it.minutesBefore == emulateMinutes }
                    if (configToEmulate != null && configToEmulate.enabled) {
                        val message = configToEmulate.message.ifBlank {
                            "Эмулированное уведомление за ${configToEmulate.minutesBefore} минут!"
                        }
                        sendNotification(message, configToEmulate.minutesBefore)
                    } else {
                        Log.w("CurfewService", "Не удалось эмулировать: конфигурация не найдена или отключена для $emulateMinutes минут. Текущие конфиги: ${notificationConfigs.map { it.minutesBefore }}.")
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback!!)
            Log.d("CurfewService", "Обновления местоположения остановлены.")
        }
        Log.d("CurfewService", "Сервис остановлен.")
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

    private fun createBaseNotification(
        curfewStatusText: String,
        timeRemainingText: String,
        homeStatusText: String
    ): NotificationCompat.Builder {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Curfew Notifier")
            .setContentText("$curfewStatusText. $timeRemainingText. $homeStatusText")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$curfewStatusText\n$timeRemainingText\n$homeStatusText"))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private suspend fun loadHomeLocation() {
        // Локации теперь загружаются в onCreate через collect
    }

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

    private suspend fun startCurfewMonitoring() {
        while (serviceJob.isActive) {
            val curfewStart = LocalTime.of(curfewStartHour, curfewStartMinute)
            val curfewEnd = LocalTime.of(curfewEndHour, curfewEndMinute)

            val nowTime = LocalTime.now()
            val currentDateTime = LocalDateTime.now()

            val curfewStartToday = LocalDate.now().atTime(curfewStart)
            val curfewEndToday = LocalDate.now().atTime(curfewEnd)
            val curfewStartTomorrow = LocalDate.now().plusDays(1).atTime(curfewStart)
            val curfewEndTomorrow = LocalDate.now().plusDays(1).atTime(curfewEnd)

            val inCurfew: Boolean
            val targetDateTime: LocalDateTime

            if (curfewStart.isBefore(curfewEnd)) {
                inCurfew = currentDateTime.isAfter(curfewStartToday) && currentDateTime.isBefore(curfewEndToday)
                targetDateTime = if (inCurfew) {
                    curfewEndToday
                } else if (currentDateTime.isBefore(curfewStartToday)) {
                    curfewStartToday
                } else {
                    curfewStartTomorrow
                }
            } else {
                inCurfew = currentDateTime.isAfter(curfewStartToday) || currentDateTime.isBefore(curfewEndToday)
                targetDateTime = if (inCurfew) {
                    if (currentDateTime.isAfter(curfewStartToday)) {
                        curfewEndTomorrow
                    } else {
                        curfewEndToday
                    }
                } else {
                    curfewStartToday
                }
            }

            val remainingDuration = Duration.between(currentDateTime, targetDateTime)
            val hours = remainingDuration.toHours()
            val minutes = remainingDuration.toMinutes() % 60
            val seconds = remainingDuration.seconds % 60

            val curfewStatusText = if (inCurfew) "Комендантский час активен" else "До комендантского часа"
            val timeRemainingText = "Осталось: %02d:%02d:%02d".format(hours, minutes, seconds)


            var homeStatusText = "Статус дома неизвестен"
            // Используем data.first() для получения текущего значения из Flow
            val isAtHome = applicationContext.dataStore.data.first()[IS_AT_HOME_KEY]
            if (isAtHome != null) {
                homeStatusText = if (isAtHome) "Вы дома" else "Вы не дома"
            }

            updateNotification(createBaseNotification(curfewStatusText, timeRemainingText, homeStatusText))

            if (lastCurfewStatus == true && !inCurfew) {
                sentNotificationsThisCycle.clear()
                Log.d("CurfewService", "Сет отправленных уведомлений очищен (начало нового цикла).")
            }
            lastCurfewStatus = inCurfew

            if (!inCurfew) {
                val timeUntilCurfew = Duration.between(currentDateTime, targetDateTime)

                notificationConfigs.forEach { config ->
                    if (config.enabled) {
                        val minutesBeforeCurfew = config.minutesBefore
                        val threshold = Duration.ofMinutes(minutesBeforeCurfew.toLong())

                        if (timeUntilCurfew <= threshold && timeUntilCurfew > Duration.ofMinutes(minutesBeforeCurfew.toLong() - 1)) {
                            if (!sentNotificationsThisCycle.contains(minutesBeforeCurfew)) {
                                val notificationMessage = config.message.ifBlank {
                                    "До комендантского часа осталось ${minutesBeforeCurfew} минут!"
                                }
                                sendNotification(notificationMessage, minutesBeforeCurfew)
                                sentNotificationsThisCycle.add(minutesBeforeCurfew)
                                Log.d("CurfewService", "Уведомление '${minutesBeforeCurfew} мин.' отправлено и добавлено в сет.")
                            } else {
                                Log.d("CurfewService", "Уведомление '${minutesBeforeCurfew} мин.' уже было отправлено в этом цикле. Пропускаем.")
                            }
                        }
                    }
                }
            }

            delay(1000)
        }
    }

    private fun updateNotification(notificationBuilder: NotificationCompat.Builder) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun sendNotification(message: String, notificationUniqueId: Int) {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w("CurfewService", "Нет разрешения на POST_NOTIFICATIONS, уведомление не отправлено.")
            return
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Напоминание о комендантском часе")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + notificationUniqueId, notification)
        Log.d("CurfewService", "Отправлено уведомление: '$message' (ID: ${NOTIFICATION_ID + notificationUniqueId}).")
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("CurfewService", "Нет разрешения на местоположение, обновления не будут запущены.")
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            // .setWaitForActivityUpdates(false) // УДАЛЕНА эта строка
            .setMinUpdateIntervalMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    Log.d("CurfewService", "Location update: ${location.latitude}, ${location.longitude}")
                    if (homeLat != 0.0 || homeLon != 0.0) {
                        val distance = calculateDistance(
                            homeLat, homeLon,
                            location.latitude, location.longitude
                        )
                        val isUserAtHome = distance <= homeRadiusMeters
                        Log.d("CurfewService", "Distance to home: %.2f meters. At home: $isUserAtHome".format(distance))
                        serviceScope.launch {
                            applicationContext.dataStore.edit { prefs ->
                                prefs[IS_AT_HOME_KEY] = isUserAtHome
                            }
                        }
                    } else {
                        Log.w("CurfewService", "Home location not set, cannot calculate distance.")
                        serviceScope.launch {
                            applicationContext.dataStore.edit { prefs ->
                                // ИСПРАВЛЕНИЕ: Используем remove() для "сброса" nullable Boolean
                                prefs.remove(IS_AT_HOME_KEY)
                            }
                        }
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, mainLooper)
        Log.d("CurfewService", "Запрошены обновления местоположения.")
    }


    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3
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