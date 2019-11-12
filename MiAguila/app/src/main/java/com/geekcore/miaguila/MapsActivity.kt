package com.geekcore.miaguila

import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.*

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*

import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.Polyline
import com.google.maps.android.PolyUtil
import org.json.JSONObject
import com.google.android.gms.maps.model.Marker
import kotlin.math.*
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.math.BigDecimal
import kotlin.collections.ArrayList

private const val LOCATION_PERMISSION_REQUEST_CODE = 1

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener,
    LocationListener {


    // region Variables

    private val interval: Long = 5000
    private lateinit var mLocationRequest: LocationRequest
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var lastLocation: Location
    private lateinit var mMap: GoogleMap
    private var destination = LatLng(4.672655, -74.054071)
    private var polyline: ArrayList<Polyline> = ArrayList()
    private var destinationChanged: Boolean? = null

    // endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        mLocationRequest = LocationRequest()
        mLocationRequest.interval = interval

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback,
            Looper.myLooper())
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnMarkerClickListener(this)
        mMap.uiSettings.isZoomControlsEnabled = true

        mMap.addMarker(
            MarkerOptions()
                .position(destination)
                .title("Destino")
                .draggable(true)
        )
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(destination))

        setUpMap()
    }

    private fun setUpMap(){
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }

        mMap.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener (this) { location ->
            if (location != null){
                lastLocation = location
                val currentLatLng = LatLng(location.latitude, location.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
            }
        }

        mMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) { }
            override fun onMarkerDrag(marker: Marker) { }
            override fun onMarkerDragEnd(marker: Marker) {
                destination = LatLng(marker.position.latitude, marker.position.longitude)
                destinationChanged = true
            }
        })
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = LatLng(lastLocation.latitude, lastLocation.longitude)
            locationResult.lastLocation

            val distance = distanceInMeters(locationResult.lastLocation.latitude,
                locationResult.lastLocation.longitude, location.latitude, location.longitude)

            if (distance > 5 || destinationChanged == true || destinationChanged == null){
                mMap.addMarker(
                    MarkerOptions()
                        .position(location)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )

                val url = getURL(location, destination)
                makeRequest(url)
                onLocationChanged(locationResult.lastLocation)
                destinationChanged = false
            }

            val speed = locationResult.lastLocation.speed.toDouble()
            val currentSpeed = round(speed, 3, BigDecimal.ROUND_HALF_UP)
            val speedKmPerHour = (round((currentSpeed*3.6), 3, BigDecimal.ROUND_HALF_UP)).toInt()

            val s = "$speedKmPerHour km/h"

            val extendedFloatingActionButton =
                findViewById<ExtendedFloatingActionButton>(R.id.fab)
            extendedFloatingActionButton.text = s
        }
    }

    override fun onLocationChanged(location: Location) {
        lastLocation = location
    }

    // region LocationListener override methods

    override fun onProviderDisabled(provider: String?) {}
    override fun onProviderEnabled(provider: String?) {}
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onMarkerClick(p0: Marker?): Boolean = false

    // endregion

    // region Utils

    private fun getURL(from : LatLng, to : LatLng) : String {
        val origin = "origin=" + from.latitude + "," + from.longitude
        val dest = "destination=" + to.latitude + "," + to.longitude
        val mode = "mode=driving"
        val params = "$origin&$dest&$mode"

        return "https://maps.googleapis.com/maps/api/directions/json?$params" + "&key=" +
                getString(R.string.google_maps_key)
    }

    private fun distanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6373.0

        val lat1InRadians = Math.toRadians(lat1)
        val lat2InRadians = Math.toRadians(lat2)
        val lon1InRadians = Math.toRadians(lon1)
        val lon2InRadians = Math.toRadians(lon2)

        val dLon = lon2InRadians - lon1InRadians
        val dLat = lat2InRadians - lat1InRadians

        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadiusKm * c * 1000
    }

    private fun round(unrounded: Double, precision: Int, roundingMode: Int): Double{
        val bd = BigDecimal(unrounded)
        val rounded: BigDecimal = bd.setScale(precision, roundingMode)

        return rounded.toDouble()
    }

    private fun makeRequest(url: String) {
        val path: MutableList<List<LatLng>> = ArrayList()
        val directionsRequest = object : StringRequest(Method.GET, url, Response.Listener<String> {
                response ->
            val jsonResponse = JSONObject(response)
            // Get routes
            val routes = jsonResponse.getJSONArray("routes")
            val legs = routes.getJSONObject(0).getJSONArray("legs")
            val steps = legs.getJSONObject(0).getJSONArray("steps")
            for (i in 0 until steps.length()) {
                val points = steps.getJSONObject(i).getJSONObject("polyline").getString("points")
                path.add(PolyUtil.decode(points))
            }

            for(line in polyline){
                line.remove()
            }
            polyline.clear()

            for (i in 0 until path.size) {
                polyline.add(mMap.addPolyline(PolylineOptions().addAll(path[i]).color(Color.RED)))
            }

        }, Response.ErrorListener {
        }){}
        val requestQueue = Volley.newRequestQueue(this)
        requestQueue.add(directionsRequest)
    }

    // endregion
}

