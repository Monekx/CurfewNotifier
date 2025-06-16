package com.monekx.curfewnotifier

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.monekx.curfewnotifier.service.CurfewForegroundService
import com.monekx.curfewnotifier.ui.CurfewStatusScreen
import com.monekx.curfewnotifier.ui.HomeLocationScreen
import com.monekx.curfewnotifier.ui.NewsScreen
import com.monekx.curfewnotifier.ui.NotificationSettingsScreen
import com.monekx.curfewnotifier.ui.theme.CurfewNotifierTheme
import kotlinx.coroutines.launch

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

// Определяем маршруты для навигации
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object CurfewStatus : Screen("curfew_status", "Комендант", Icons.Default.Info)
    object Notifications : Screen("notifications", "Уведомления", Icons.Default.Notifications)
    object HomeLocation : Screen("home_location", "Дом", Icons.Default.Place)
    object News : Screen("news", "Новости", Icons.Default.Home) // Изменен значок на "Дом" для примера
}


class MainActivity : ComponentActivity() {

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
                MainAppScreen(mapLauncher = mapLauncher)
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
fun MainAppScreen(mapLauncher: ActivityResultLauncher<Intent>) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val bottomNavItems = listOf(
        Screen.CurfewStatus,
        Screen.Notifications,
        Screen.HomeLocation,
        Screen.News
    )

    Scaffold(
        bottomBar = {
            NavigationBar(modifier = Modifier.height(64.dp)) { // Увеличиваем высоту для лучшего вида
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                // Избегаем создания нескольких копий одного и того же элемента
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                // Избегаем нескольких копий одного и того же элемента в стеке
                                launchSingleTop = true
                                // Восстанавливаем состояние при повторном выборе элемента
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.CurfewStatus.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.CurfewStatus.route) {
                CurfewStatusScreen()
            }
            composable(Screen.Notifications.route) {
                NotificationSettingsScreen(context = context)
            }
            composable(Screen.HomeLocation.route) {
                HomeLocationScreen(context = context, mapLauncher = mapLauncher)
            }
            composable(Screen.News.route) {
                NewsScreen(context = context)
            }
        }
    }
}