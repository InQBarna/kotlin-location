/*
 * Copyright 2021 InQBarna Kenkyuu Jo SL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* 
 * Copyright 2021 InQBarna Kenkyuu Jo SL 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */
package com.inqbarna.libsamples

import android.content.DialogInterface
import android.content.Intent
import android.location.Location
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.LocationSource
import com.inqbarna.iqlocation.LocationHelper
import com.inqbarna.iqlocation.LocationModule
import com.inqbarna.iqlocation.LocationPermissionRequestDelegate
import com.inqbarna.iqlocation.PermissionDelegateCallbacks
import com.inqbarna.iqlocation.util.narrowedLifecycle
import com.inqbarna.libsamples.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import timber.log.Timber

private val currentThreadName: String
    get() = Thread.currentThread().name

private fun Location.printString(): String = "Lat: $latitude, Lon: $longitude"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val locationHelper by lazy {
        LocationHelper.setDebug(true)
        LocationModule(this).provideFastLocation()
    }

    private val narrowLifecycle by narrowedLifecycle()

    private val callbacks = object : PermissionDelegateCallbacks {
        override fun showRequestPermissionsDialog(accept: DialogInterface.OnClickListener, deny: DialogInterface.OnClickListener) {
            AlertDialog.Builder(this@MainActivity)
                    .setTitle("We need permission")
                    .setMessage("Hey dude, give us permission to locate you!")
                    .setPositiveButton("Ok!", accept)
                    .setNegativeButton("No Way", deny)
                    .show()
        }

        override fun onPermissionGranted(alreadyGranted: Boolean) {
            narrowLifecycle.coroutineScope.launchWhenStarted {

                val firstLocation = locationHelper.location.first()

                Timber.d("[%s] Got first location: %s", currentThreadName, firstLocation.printString())

                delay(2000)

                locationHelper.location.collect { location ->
                    Timber.d("[%s] Received location(1) %s", currentThreadName, location.printString())
                }
            }

            narrowLifecycle.coroutineScope.launchWhenStarted {
                delay(4000)
                try {
                    locationHelper.getAddressesAtMyLocation(3).collect { addresses ->
                        for (address in addresses) {
                            Timber.d("$address")
                        }
                    }
                } catch (e: LocationHelper.LocationHelperError) {
                    Timber.e(e, "Failed to get reverse location")
                }
            }

            narrowLifecycle.coroutineScope.launchWhenStarted {
                try {
                    val info = locationHelper.getReverseLocationInfo("barcelona")
                    Timber.d("LatLng for barcelona: ${info.latLng}")
                } catch (e: LocationHelper.LocationHelperError) {
                    Timber.e(e, "Failed to get reverse location")
                }
            }
        }

        override fun onPermissionDenied() {
            Timber.e("User denied permission")
            binding.btnRequestLocation.isEnabled = true
        }
    }

    private lateinit var locationPermissionRequestDelegate: LocationPermissionRequestDelegate
    private lateinit var locationSource: LocationSource


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationPermissionRequestDelegate = LocationPermissionRequestDelegate(this,
                callbacks,
                savedInstanceState,
                LocationPermissionRequestDelegate
                        .Options
                        .Builder()
                        .satisfyRequest(locationHelper.locationRequest)
                        .disableCheckAlways()
                        .build()
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRequestLocation.setOnClickListener {
            beginLocationUpdates()
        }

        binding.checkSource.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                doActivateLocationSource()
            } else {
                deactivateLocationSource()
            }
        }

        locationSource = locationHelper.newLocationSource(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        locationPermissionRequestDelegate.onSaveState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (!locationPermissionRequestDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!locationPermissionRequestDelegate.onActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onStart() {
        super.onStart()
        beginLocationUpdates()
        if (binding.checkSource.isChecked) {
            doActivateLocationSource()
        }
    }

    private var locationSourceActivated = false
    private fun doActivateLocationSource() {
        if (!locationSourceActivated) {
            locationSourceActivated = true
            locationSource.activate { location -> Timber.d("[%s] Got location on source: Lat: %f, Long: %f", Thread.currentThread().name, location.latitude, location.longitude) }
        }
    }

    override fun onStop() {
        super.onStop()
        deactivateLocationSource()
    }

    private fun deactivateLocationSource() {
        if (locationSourceActivated) {
            locationSource.deactivate()
            locationSourceActivated = false
        }
    }

    private fun beginLocationUpdates() {
        locationPermissionRequestDelegate.checkLocationPermission()
    }
}
