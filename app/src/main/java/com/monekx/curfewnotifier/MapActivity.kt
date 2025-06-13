package com.monekx.curfewnotifier

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import com.monekx.curfewnotifier.databinding.ActivityMapOsmdroidBinding

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var binding: ActivityMapOsmdroidBinding
    private var selectedLocation: GeoPoint? = null
    private var homeMarker: Marker? = null
    private var homeCircle: Polygon? = null
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация OSMdroid конфигурации
        val ctx: Context = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        binding = ActivityMapOsmdroidBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK) // Используем OpenStreetMap тайлы по умолчанию
        mapView.setMultiTouchControls(true) // Включить мультитач (зум, скролл)

        // Добавляем контролы:
        // Масштабная линейка
        val scaleBarOverlay = ScaleBarOverlay(mapView)
        scaleBarOverlay.setCentred(true)
        scaleBarOverlay.setScaleBarOffset(resources.displayMetrics.widthPixels / 2, 10)
        mapView.overlays.add(scaleBarOverlay)

        // Компас
        val compassOverlay = CompassOverlay(ctx, InternalCompassOrientationProvider(ctx), mapView)
        compassOverlay.enableCompass()
        mapView.overlays.add(compassOverlay)

        // Инициализация и добавление слоя с текущим местоположением
        // Проверяем разрешение на местоположение (должно быть уже получено в MainActivity)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            val locationProvider = GpsMyLocationProvider(ctx)
            myLocationOverlay = MyLocationNewOverlay(locationProvider, mapView)

            myLocationOverlay.enableMyLocation() // Начинает получать обновления местоположения
            myLocationOverlay.enableFollowLocation() // Карта будет центрироваться на текущем местоположении

            mapView.overlays.add(myLocationOverlay)

            // Центрируем карту на текущем местоположении, как только оно будет получено
            myLocationOverlay.runOnFirstFix {
                runOnUiThread {
                    myLocationOverlay.myLocation?.let {
                        mapView.controller.setCenter(it)
                        mapView.controller.setZoom(15.0) // Приближаем к местоположению
                    }
                }
            }
        } else {
            // Если разрешения нет, слой местоположения не будет работать.
            // MainActivity должна была запросить разрешение, поэтому здесь просто пассивное поведение.
        }

        // Начальный зум и центр Украины (устанавливается, если местоположение пользователя не удалось получить сразу)
        mapView.controller.setZoom(5.0)
        val ukraineCenter = GeoPoint(48.3794, 31.1656)
        mapView.controller.setCenter(ukraineCenter)


        // Загружаем сохраненную точку дома при старте активности
        runBlocking {
            val latKey = intPreferencesKey("home_lat_int")
            val lonKey = intPreferencesKey("home_lon_int")
            val preferences = applicationContext.dataStore.data.first()

            val storedLatInt = preferences[latKey]
            val storedLonInt = preferences[lonKey]

            if (storedLatInt != null && storedLonInt != null) {
                val storedLat = storedLatInt / 1_000_000.0
                val storedLon = storedLonInt / 1_000_000.0
                selectedLocation = GeoPoint(storedLat, storedLon)
                updateHomeMarkerAndCircle(selectedLocation!!)
                // Если есть сохраненная точка дома, центрируем на ней,
                // только если местоположение пользователя не активно или еще не получено.
                if (!myLocationOverlay.isMyLocationEnabled || myLocationOverlay.myLocation == null) {
                    mapView.controller.setCenter(selectedLocation)
                    mapView.controller.setZoom(15.0)
                }
            }
        }

        // Обработчик долгого нажатия для выбора точки дома
        mapView.overlays.add(object : Overlay() {
            override fun onLongPress(e: android.view.MotionEvent?, mapView: MapView?): Boolean {
                if (mapView != null && e != null) {
                    val geoPoint = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                    selectedLocation = geoPoint
                    updateHomeMarkerAndCircle(geoPoint)
                    return true
                }
                return false
            }
        })

        // Настраиваем кнопку "Сохранить"
        binding.saveLocationButton.setOnClickListener {
            selectedLocation?.let {
                // Если местоположение выбрано, возвращаем его в MainActivity
                val resultIntent = Intent().apply {
                    putExtra("lat", it.latitude)
                    putExtra("lon", it.longitude)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } ?: run {
                // Если местоположение не выбрано, можно показать сообщение пользователю
                // (в реальном приложении здесь можно использовать Snackbar или Toast)
            }
        }
    }

    // Обновляет/создает маркер дома и круг на карте
    private fun updateHomeMarkerAndCircle(geoPoint: GeoPoint) {
        // Удаляем старые маркер и круг, если они существуют
        homeMarker?.let { mapView.overlays.remove(it) }
        homeCircle?.let { mapView.overlays.remove(it) }

        // Создаем новый маркер
        homeMarker = Marker(mapView).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Выбранный дом"
            // Можно установить свою иконку маркера:
            // icon = ContextCompat.getDrawable(this@MapActivity, R.drawable.ic_home_marker)
        }
        mapView.overlays.add(homeMarker)

        // Создаем и добавляем круг радиусом 50 метров
        homeCircle = Polygon().apply {
            points = Polygon.pointsAsCircle(geoPoint, 50.0) // 50 метров радиус
            fillColor = Color.argb(60, 255, 0, 0) // Прозрачный красный цвет для заполнения
            strokeColor = Color.RED // Красный цвет для границы
            strokeWidth = 2.0f // Ширина границы
        }
        mapView.overlays.add(homeCircle)
        mapView.invalidate() // Обновить карту для отображения маркера и круга
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume() // Важно для OSMdroid
        // Включаем отслеживание местоположения и следование при возобновлении активности
        if (::myLocationOverlay.isInitialized) { // Проверяем инициализацию, чтобы избежать краша, если разрешение не было дано
            myLocationOverlay.enableMyLocation()
            myLocationOverlay.enableFollowLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause() // Важно для OSMdroid
        // Отключаем отслеживание местоположения и следование при паузе активности для экономии батареи
        if (::myLocationOverlay.isInitialized) {
            myLocationOverlay.disableMyLocation()
            myLocationOverlay.disableFollowLocation()
        }
    }
}