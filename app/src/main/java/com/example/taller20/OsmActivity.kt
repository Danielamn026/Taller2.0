package com.example.taller20

import android.app.UiModeManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Looper
import android.os.StrictMode
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import com.example.taller20.databinding.ActivityOsmBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.gson.Gson
import models.Ubication
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OsmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOsmBinding
    lateinit var map: MapView
    private val bogota = GeoPoint(4.62, -74.07)

    private var longPressedMarker: Marker? = null
    private var searchMarker: Marker? = null
    private var roadOverlay: Polyline? = null
    private var routeStartPoint: GeoPoint? = null
    private var routeEndPoint: GeoPoint? = null

    private val visitedPoints = mutableListOf<GeoPoint>()
    private lateinit var roadManager: RoadManager
    private val gson = Gson()
    private val fileName = "ubications.json"

    //Location
    private lateinit var locationClient : FusedLocationProviderClient
    private lateinit var locationRequest : LocationRequest
    private lateinit var locationCallback : LocationCallback
    private var currentLocation: GeoPoint? = null

    // Registra resultado para manejar activacion de GPS, si usuario acepta,se inician updates
    val locationSettings = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ActivityResultCallback {
            if(it.resultCode == RESULT_OK){
                startLocationUpdates()
            }else{
                Toast.makeText(this, "The GPS is turned off", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // Solicita permiso de ubicacion, si concede, verifica configuracion GPS
    val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback {
            if(it){
                locationSettings()
            }else{
                Toast.makeText(this, "There is no permission to access the GPS", Toast.LENGTH_SHORT).show()
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOsmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))

        map = binding.osmMap
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.overlays.add(createOverlayEvents())

        // Configurar ruta
        roadManager = OSRMRoadManager(this, "ANDROID")
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // Inicializar cliente de ubicación
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()


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

                    routeStartPoint = currentLocation
                    routeEndPoint = geoPoint

                    if (routeStartPoint != null && routeEndPoint != null) {
                        drawRoute(routeStartPoint!!, routeEndPoint!!)
                    }

                    map.controller.animateTo(geoPoint)
                    map.controller.setZoom(18.0)
                    val distance = currentLocation?.distanceToAsDouble(geoPoint) ?: 0.0
                    Toast.makeText(this, "Distancia: %.2f metros".format(distance), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No se pudo encontrar la ubicación", Toast.LENGTH_SHORT).show()
                }
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        binding.routeButton.setOnClickListener {
            drawHistoryRoute()
        }
    }

    override fun onResume() {
        super.onResume()
        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)

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
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()

        val file = File(filesDir, fileName)
        if (file.exists()) {
            file.delete()
            Log.i("OsmActivity", "Archivo ubications.json eliminado en onDestroy()")
        }
    }

    private fun createLocationRequest(): LocationRequest {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(5000)
            .build()
        return request
    }

    private fun createLocationCallback(): LocationCallback {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val newLocation = result.lastLocation

                if (newLocation == null) {
                    Toast.makeText(this@OsmActivity, "Ubicación no detectada", Toast.LENGTH_SHORT).show()
                    return
                }

                val newGeo = GeoPoint(newLocation.latitude, newLocation.longitude)
                val distance = currentLocation?.distanceToAsDouble(newGeo) ?: 0.0

                //Si la ubicacion cambia mas de 30m, actualiza marcador con nueva ubicacion
                if (currentLocation == null || distance > 30.0) {
                    currentLocation = newGeo
                    visitedPoints.add(newGeo)
                    saveUbication(newGeo)

                    val marker = createMarker(newGeo, "Ubicación actual", "", R.drawable.baseline_add_location_24)
                    map.overlays.add(marker)
                    map.controller.animateTo(newGeo)
                    map.controller.setZoom(17.0)
                }
            }
        }
        return callback
    }

    fun startLocationUpdates(){
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)== PackageManager.PERMISSION_GRANTED){
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    fun stopLocationUpdates(){
        locationClient.removeLocationUpdates(locationCallback)
    }

    fun locationSettings(){
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener { locationSettingsResponse ->
            // All location settings are satisfied. The client can initialize
            // location requests here. // ...
            startLocationUpdates()
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException){
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    val isr : IntentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettings.launch(isr)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Toast.makeText(this, "There is no GPS hardware", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveUbication(p: GeoPoint) {
        val file = File(filesDir, fileName)
        // Archivo ya existe en almacenamiento interno
        val ubicationsList: MutableList<Ubication> = if (file.exists()) {
            val content = file.readText()
            // Si el archivo no está vacío
            if (content.isNotBlank()){
                // Define tipo generico MutableList<Ubication> para que Gson sepa como deserializar el Json a una lista
                val tipo = object : TypeToken<MutableList<Ubication>>() {}.type
                // Convertir Json a una lista de tipo Ubicacion
                gson.fromJson(content, tipo)
            } else mutableListOf()
        } else mutableListOf()

        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        ubicationsList.add(Ubication(p.latitude, p.longitude, date))
        file.writeText(gson.toJson(ubicationsList))
    }

    fun findAddress(lat: Double, lon: Double): String? {
        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        return addresses?.getOrNull(0)?.getAddressLine(0)
    }

    fun findLocation(addressText: String): GeoPoint? {
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

    private fun createOverlayEvents(): MapEventsOverlay {
        val overlayEvents = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    longPressOnMap(p)
                        routeStartPoint = currentLocation
                        routeEndPoint = p
                        drawRoute(routeStartPoint!!, routeEndPoint!!)
                        routeStartPoint = null
                        routeEndPoint = null
                }
                return true
            }
        })
        return overlayEvents
    }

    fun longPressOnMap(p:GeoPoint){
        if(longPressedMarker!=null) {
            map.getOverlays().remove(longPressedMarker)
        }
        val address = findAddress(p.latitude, p.longitude)
        val snippet : String
        if(address != null) {
            snippet = address
        }else{
            snippet = ""
        }
        longPressedMarker = createMarker(p, "Punto tocado", snippet, R.drawable.baseline_add_location_24)
        map.overlays.add(longPressedMarker)
        val distance = currentLocation?.distanceToAsDouble(p) ?: 0.0
        Toast.makeText(applicationContext, "Distancia: %.2f metros".format(distance), Toast.LENGTH_SHORT).show()
    }

    fun addMarker(p:GeoPoint, snippet : String, longPressed : Boolean){
        if(longPressed) {
            longPressedMarker =
                createMarker(p, "New Location", snippet, R.drawable.baseline_add_location_24)
            if (longPressedMarker != null) {
                map.getOverlays().add(longPressedMarker)
            }
        }else{
            searchMarker = createMarker(p, "Snippet", "", R.drawable.baseline_add_location_24)
            map.overlays.add(searchMarker)
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

    fun drawRoute(start : GeoPoint, finish : GeoPoint){
        if (start == null || finish == null) {
            Toast.makeText(this, "Ubicación incompleta para ruta", Toast.LENGTH_SHORT).show()
            return
        }
        val road = roadManager.getRoad(arrayListOf(start, finish))
        Log.i("MapsApp", "Route length: " + road.mLength + " klm")
        Log.i("MapsApp", "Duration: " + road.mDuration/60 + " min")

        if(map != null){
            if(roadOverlay != null){
                map.getOverlays().remove(roadOverlay);
            }
            roadOverlay = RoadManager.buildRoadOverlay(road)
            roadOverlay!!.getOutlinePaint().setColor(Color.RED)
            roadOverlay!!.getOutlinePaint().setStrokeWidth(10F)
            map.getOverlays().add(roadOverlay)
            //Mostrar distancia
            Toast.makeText(this, "Distancia por ruta: %.2f km".format(road.mLength), Toast.LENGTH_LONG).show()
        }
    }

    private fun drawHistoryRoute() {
        val file = File(filesDir, fileName)
        if (!file.exists()) {
            Toast.makeText(this, "No hay historial para mostrar", Toast.LENGTH_SHORT).show()
            return
        }

        val tipo = object : TypeToken<List<Ubication>>() {}.type
        val ubicationsList: List<Ubication> = gson.fromJson(file.readText(), tipo)

        if (ubicationsList.size < 2) {
            Toast.makeText(this, "Se necesitan al menos dos ubicaciones para trazar la ruta", Toast.LENGTH_SHORT).show()
            return
        }
        val geoPoints = ubicationsList.map { GeoPoint(it.latitude, it.longitude) }

        val road = roadManager.getRoad(ArrayList(geoPoints))
        val overlay = RoadManager.buildRoadOverlay(road)
        overlay.outlinePaint.color = Color.BLUE
        overlay.outlinePaint.strokeWidth = 10f
        map.overlays.add(overlay)
        map.invalidate()

        // Centrar en la última ubicación
        map.controller.animateTo(geoPoints.last())

        Toast.makeText(this, "Ruta de historial dibujada: ${road.mLength} km", Toast.LENGTH_SHORT).show()
    }


}