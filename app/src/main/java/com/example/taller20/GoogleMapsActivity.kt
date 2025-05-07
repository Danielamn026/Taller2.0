package com.example.taller20

import ApiService
import RouteResponse
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.taller20.databinding.ActivityGoogleMapsBinding
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
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class GoogleMapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    val markerPoints = mutableListOf<LatLng>()
    private lateinit var binding: ActivityGoogleMapsBinding
    private var start : String = ""
    private var end : String = ""
    var poly : Polyline? = null
    private var mostrandoRuta: Boolean = false

    //Sensores de luz
    private lateinit var sensorManager : SensorManager
    private var ligthSensor: Sensor? = null
    private lateinit var sensorEventListener: SensorEventListener

    //Direcciones a coordenadas, viceversa
    private lateinit var geocoder: Geocoder

    //Location
    private lateinit var locationClient : FusedLocationProviderClient
    private lateinit var locationRequest : LocationRequest
    private lateinit var locationCallback : LocationCallback
    private var lastLocation: Location? = null


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
        binding = ActivityGoogleMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        ligthSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        sensorEventListener = createSensorEventListener()

        geocoder = Geocoder(baseContext)

        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //Usuario realiza busqueda de direccion, se obtiene ubicacion y la muestra
        binding.address.setOnEditorActionListener{ textView, i, keyEvent ->
            if(i == EditorInfo.IME_ACTION_SEARCH){
                val address = binding.address.text.toString()
                val latlong = findLocation(address)
                if(this@GoogleMapsActivity::mMap.isInitialized) {
                    poly?.remove()
                    poly = null
                    mostrandoRuta = false
                    mMap.clear()

                    //Marcador ubicacion actual
                    val currentLatLng = LatLng(lastLocation!!.latitude, lastLocation!!.longitude)
                    mMap.addMarker(MarkerOptions().position(currentLatLng).title("Ubicación actual"))
                    //Marcador direccion buscada
                    mMap.addMarker(MarkerOptions().position(latlong!!).title(address))
                    start = "${currentLatLng.longitude},${currentLatLng.latitude}"
                    end = "${latlong.longitude},${latlong.latitude}"
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(latlong))
                    mMap.moveCamera(CameraUpdateFactory.zoomTo(15f))
                    createRoute()
                }
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(sensorEventListener, ligthSensor, SensorManager.SENSOR_DELAY_NORMAL)
        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorEventListener)
        stopLocationUpdates()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        // Add a marker in Bogota and move the camera
        val bogota = LatLng(4.63, -74.10)
        mMap.addMarker(MarkerOptions().position(bogota).title("Marker en Bogota"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(bogota))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bogota, 15f))
        mMap.uiSettings.isZoomControlsEnabled = true

        start = ""
        end = ""

        // Listener de long click, borra marcadores, pone uno en la ubicacion clickeada
        mMap.setOnMapLongClickListener {
            poly?.remove()
            poly = null
            mostrandoRuta = false
            mMap.clear()

            //Marcador ubicacion actual
            val currentLatLng = LatLng(lastLocation!!.latitude, lastLocation!!.longitude)
            mMap.addMarker(MarkerOptions().position(currentLatLng).title("Ubicación actual"))
            //Marcador ubicacion clickeada
            val address = findAddress(it)
            mMap.addMarker(MarkerOptions().position(it).title(address))

            start = "${currentLatLng.longitude},${currentLatLng.latitude}"
            end = "${it.longitude},${it.latitude}"
            createRoute()
        }
    }

    private fun getRetrifit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openrouteservice.org/")
            //Parsea respuesta a JSON
            .addConverterFactory(GsonConverterFactory.create())
            //retorna un objeto de tipo retrofit
            .build()
    }

    //Metodo que llama interfaz de retrofit
    private fun createRoute(){
        mostrandoRuta = true
        //Todoo lo que se haga, estara en otro hilo, no en el principal
        CoroutineScope(Dispatchers.IO).launch {
            val call = getRetrifit().create(ApiService::class.java)
                .getRoute("5b3ce3597851110001cf624850245a96538a4909acf578056744f5eb", start, end)
            if(call.isSuccessful) {
                drawRoute(call.body())
                call.body()
            } else {
                Log.i("aris", "KO")
            }
        }
    }

    private fun drawRoute(routeResponse: RouteResponse?){
        val polyLineOptions = PolylineOptions()
        //Se rellena con la coordenadas que dio el servicio
        routeResponse?.features?.first()?.geometry?.coordinates?.forEach {
            polyLineOptions.add(LatLng(it[1], it[0]))
        }
        //Volver a hilo principal para pintar ruta
        runOnUiThread {
            poly = mMap.addPolyline(polyLineOptions)
        }
    }

    private fun createSensorEventListener():SensorEventListener{
        val sel = object : SensorEventListener{
            override fun onSensorChanged(event: SensorEvent?) {
                if(this@GoogleMapsActivity::mMap.isInitialized){
                    if(event!=null){
                        if(event.values[0]>5000){
                            val mapStyle = mMap.setMapStyle(
                                MapStyleOptions.loadRawResourceStyle(baseContext, R.raw.light)
                            )
                        }else{
                            mMap.setMapStyle(
                                MapStyleOptions.loadRawResourceStyle(baseContext, R.raw.dark)
                            )
                        }
                    }

                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

            }
        }
        return sel
    }

    // LOCATION
    private fun createLocationRequest(): LocationRequest {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(5000)
            .build()
        return request
    }

    private fun createLocationCallback(): LocationCallback {
        val callback = object : LocationCallback() {
            // Recibe actualizaciones de ubicacion
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val newLocation = result.lastLocation

                if (newLocation != null) {
                    lastLocation = newLocation
                    if (!mostrandoRuta) {
                        val latLng = LatLng(newLocation.latitude, newLocation.longitude)
                        mMap.clear()
                        mMap.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title("Ubicación actual")
                        )
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                    }
                }
            }
        }
        return callback
    }

    fun stopLocationUpdates(){
        locationClient.removeLocationUpdates(locationCallback)
    }

    fun startLocationUpdates(){
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)== PackageManager.PERMISSION_GRANTED){
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
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

    fun findAddress (location : LatLng):String?{
        // Obtiene la direccion a partir de coordenadas
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 2)
        if(addresses != null && !addresses.isEmpty()){
            val addr = addresses.get(0)
            val locname = addr.getAddressLine(0)
            return locname
        }
        return null
    }

    fun findLocation(address : String):LatLng?{
        // Obtiene coordenadas a partir de la direccion
        val addresses = geocoder.getFromLocationName(address, 2)
        if(addresses != null && !addresses.isEmpty()){
            val addr = addresses.get(0)
            val location = LatLng(addr.latitude, addr.longitude)
            return location
        }
        return null
    }

    fun drawMarker(location : LatLng, description : String?, icon: Int){
        val addressMarker = mMap.addMarker(MarkerOptions().position(location).icon(bitmapDescriptorFromVector(this,
            icon)))!!
        if(description!=null){
            addressMarker.title=description
        }

        mMap.moveCamera(CameraUpdateFactory.newLatLng(location))
        mMap.moveCamera(CameraUpdateFactory.zoomTo(15f))
    }

    fun bitmapDescriptorFromVector(context : Context, vectorResId : Int) : BitmapDescriptor {
        val vectorDrawable : Drawable = ContextCompat.getDrawable(context, vectorResId)!!
        vectorDrawable.setBounds(0, 0, vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight());
        val bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(),
            Bitmap.Config.ARGB_8888);
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

}
