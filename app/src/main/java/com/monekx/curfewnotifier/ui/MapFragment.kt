package com.monekx.curfewnotifier.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.monekx.curfewnotifier.R

class MapFragment : Fragment() {

    private lateinit var map: MapView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().load(requireContext(), requireContext().getSharedPreferences("osmdroid", 0))

        val view = inflater.inflate(R.layout.activity_map_osmdroid, container, false)
        map = view.findViewById(R.id.map_osm)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val homePoint = GeoPoint(50.4501, 30.5234) // Kyiv
        map.controller.setZoom(15.0)
        map.controller.setCenter(homePoint)

        val marker = Marker(map)
        marker.position = homePoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Дом"
        map.overlays.add(marker)

        return view
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
