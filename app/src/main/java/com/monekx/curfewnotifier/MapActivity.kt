package com.monekx.curfewnotifier

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon // Импортируем Polygon
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import com.monekx.curfewnotifier.databinding.ActivityMapOsmdroidBinding

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var binding: ActivityMapOsmdroidBinding
    private var selectedLocation: GeoPoint? = null
    private var homeMarker: Marker? = null
    private var homeCircle: Polygon? = null // Добавляем переменную для круга

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ctx: Context = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        binding = ActivityMapOsmdroidBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val scaleBarOverlay = ScaleBarOverlay(mapView)
        scaleBarOverlay.setCentred(true)
        scaleBarOverlay.setScaleBarOffset(resources.displayMetrics.widthPixels / 2, 10)
        mapView.overlays.add(scaleBarOverlay)

        val compassOverlay = CompassOverlay(ctx, InternalCompassOrientationProvider(ctx), mapView)
        compassOverlay.enableCompass()
        mapView.overlays.add(compassOverlay)

        mapView.controller.setZoom(5.0)
        val ukraineCenter = GeoPoint(48.3794, 31.1656)
        mapView.controller.setCenter(ukraineCenter)

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
                updateHomeMarkerAndCircle(selectedLocation!!) // Обновляем и маркер, и круг
                mapView.controller.setCenter(selectedLocation)
                mapView.controller.setZoom(15.0)
            }
        }

        mapView.overlays.add(object : Overlay() {
            override fun onLongPress(e: android.view.MotionEvent?, mapView: MapView?): Boolean {
                if (mapView != null && e != null) {
                    val geoPoint = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                    selectedLocation = geoPoint
                    updateHomeMarkerAndCircle(geoPoint) // Обновляем и маркер, и круг
                    return true
                }
                return false
            }
        })

        binding.saveLocationButton.setOnClickListener {
            selectedLocation?.let {
                val resultIntent = Intent().apply {
                    putExtra("lat", it.latitude)
                    putExtra("lon", it.longitude)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } ?: run {
                // В реальном приложении здесь можно показать сообщение пользователю
            }
        }
    }

    // Объединенная функция для обновления маркера и круга
    private fun updateHomeMarkerAndCircle(geoPoint: GeoPoint) {
        // Удаляем старые маркер и круг, если они существуют
        homeMarker?.let { mapView.overlays.remove(it) }
        homeCircle?.let { mapView.overlays.remove(it) }

        // Создаем новый маркер
        homeMarker = Marker(mapView).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Выбранный дом"
        }
        mapView.overlays.add(homeMarker)

        // --- НОВЫЙ КОД: Создаем и добавляем круг ---
        homeCircle = Polygon().apply {
            // Генерируем точки для круга. 50 метров радиус.
            // GeoPoint.get=GeoPoint.fromDegrees() позволяет создать круг из центральной точки и радиуса.
            points = Polygon.pointsAsCircle(geoPoint, 50.0) // 50 метров радиус
            fillColor = Color.argb(60, 255, 0, 0) // Прозрачный красный цвет для заполнения
            strokeColor = Color.RED // Красный цвет для границы
            strokeWidth = 2.0f // Ширина границы
        }
        mapView.overlays.add(homeCircle)
        // --- КОНЕЦ НОВОГО КОДА ---

        mapView.invalidate() // Обновить карту для отображения изменений
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}