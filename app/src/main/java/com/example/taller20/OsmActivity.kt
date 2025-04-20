package com.example.taller20

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import com.example.taller20.databinding.ActivityOsmBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.util.Locale

class OsmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOsmBinding
    lateinit var map: MapView
    private val bogota = GeoPoint(4.62, -74.07)
    private var currentLocation: GeoPoint? = null
    private var longPressedMarker: Marker? = null
    private var searchMarker: Marker? = null
    private lateinit var roadManager: RoadManager
    private var roadOverlay: Polyline? = null
    private var routeStartPoint: GeoPoint? = null
    private var routeEndPoint: GeoPoint? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOsmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Configuration.getInstance().load(this,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))

        map = binding.osmMap
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.overlays.add(createOverlayEvents())

        // Configurar ruta
        roadManager = OSRMRoadManager(this, "ANDROID")
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // Inicializar cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestCurrentLocation()

        // Escuchar pulsación larga para marcar y trazar ruta
        map.overlays.add(createOverlayEvents())

        binding.address.setOnEditorActionListener { textView, i, keyEvent ->
            if (i == EditorInfo.IME_ACTION_SEARCH) {
                val text = binding.address.text.toString()
                val geoPoint = findLocation(text)

                if (geoPoint != null) {
                    if (searchMarker != null) {
                        map.overlays.remove(searchMarker)
                    }
                    searchMarker = createMarker(geoPoint, text, "", R.drawable.baseline_add_location_24)
                    searchMarker?.let {map.overlays.add(it)}

                    map.controller.animateTo(geoPoint)
                    map.controller.setZoom(18.0)
                } else {
                    Toast.makeText(this, "No se pudo encontrar la ubicación", Toast.LENGTH_SHORT).show()
                }
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        map.controller.setZoom(18.0)
        map.controller.animateTo(bogota)
        val uims = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uims.nightMode == UiModeManager.MODE_NIGHT_YES) {
            map.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        }
        addMarker()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    private fun requestCurrentLocation() {

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                currentLocation = GeoPoint(location.latitude, location.longitude)
                val marker = createMarker(currentLocation!!, "Ubicación actual", "", R.drawable.baseline_add_location_24)
                map.overlays.add(marker)
                map.controller.animateTo(currentLocation)
                map.controller.setZoom(17.0)
            } else {
                Toast.makeText(this, "Ubicación no disponible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createOverlayEvents(): MapEventsOverlay {
        val overlayEvents = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    longPressOnMap(p)

                    if (routeStartPoint == null) {
                        routeStartPoint = p
                    } else {
                        routeEndPoint = p
                        drawRoute(routeStartPoint!!, routeEndPoint!!)
                        routeStartPoint = null
                        routeEndPoint = null
                    }
                }
                return true
            }
        })
        return overlayEvents
    }

    fun longPressOnMap(p:GeoPoint){
        if(longPressedMarker!=null)
            map.getOverlays().remove(longPressedMarker)
        val address = findAddress(p.latitude, p.longitude)
        val snippet : String
        if(address!=null) {
            snippet = address
        }else{
            snippet = ""
        }
        addMarker(p, snippet, true)
    }

    fun addMarker(p:GeoPoint, snippet : String, longPressed : Boolean){
        if(longPressed) {
            longPressedMarker =
                createMarker(p, "New Location", snippet, R.drawable.baseline_add_location_24)
            if (longPressedMarker != null) {
                map.getOverlays().add(longPressedMarker)
            }
        }else{
            searchMarker = createMarker(p, "Snippet", "", R.drawable.
            baseline_add_location_24)
            map.
            overlays.add(searchMarker)
        }
    }

    fun addMarker() {
        val markerPoint = GeoPoint(4.62, -74.07)
        val marker = Marker(map)
        marker.setTitle("Mi Marcador")
        val myIcon = ContextCompat.getDrawable(this, R.drawable.baseline_add_location_24)

        marker.setIcon(myIcon)
        marker.setPosition(markerPoint)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.getOverlays().add(marker)
    }

    fun createMarker(p:GeoPoint, title: String, desc: String, iconID : Int) : Marker? {
        var marker : Marker? = null;
        if(map != null) {
            marker = Marker(map);
            if (title != null) marker.setTitle(title);
            if (desc != null) marker.setSubDescription(desc);
            if (iconID != 0) {
                val myIcon = getResources().getDrawable(iconID, this.getTheme());
                marker.setIcon(myIcon);
            }
            marker.setPosition(p);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        }
        return marker
    }

    fun findAddress(lat: Double, lon: Double): String? {
        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        return addresses?.getOrNull(0)?.getAddressLine(0)
    }

    private fun findLocation(addressText: String): GeoPoint? {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(addressText, 1)

            if (!addresses.isNullOrEmpty() && addresses[0] != null) {
                val address = addresses[0]
                return GeoPoint(address.latitude, address.longitude)
            }
        } catch (e: Exception) {
            Log.e("OsmActivity", "Error finding location: ${e.message}")
        }
        return null
    }

    fun drawRoute(start : GeoPoint, finish : GeoPoint){
        if (start == null || finish == null) {
            Toast.makeText(this, "Ubicación incompleta para ruta", Toast.LENGTH_SHORT).show()
            return
        }

        var routePoints = ArrayList<GeoPoint>()
        routePoints.add(start)
        routePoints.add(finish)
        val road = roadManager.getRoad(routePoints)
        Log.i("MapsApp", "Route length: "+road.mLength+" klm")
        Log.i("MapsApp", "Duration: "+road.mDuration/60+" min")

        if(map != null){
            if(roadOverlay != null){
                map.getOverlays().remove(roadOverlay);
            }
            roadOverlay = RoadManager.buildRoadOverlay(road)
            roadOverlay!!.getOutlinePaint().setColor(Color.RED)
            roadOverlay!!.getOutlinePaint().setStrokeWidth(10F)
            map.getOverlays().add(roadOverlay)
        }
    }





}