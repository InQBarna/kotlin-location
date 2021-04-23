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
package com.inqbarna.iqlocation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.LocationSource
import com.google.android.gms.maps.LocationSource.OnLocationChangedListener
import com.inqbarna.iqlocation.util.ErrorHandler
import com.inqbarna.iqlocation.util.GeocoderError
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.slf4j.LoggerFactory
import java.lang.ref.WeakReference
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

private sealed class LocationValue {
    class Value(val location: Location) : LocationValue()
    class Error(val error: LocationHelper.LocationHelperError) : LocationValue()
}

/**
 * Created by David García <david.garcia></david.garcia>@inqbarna.com> on 26/11/14.
 */
class LocationHelper private constructor(private val appContext: Context, val locationRequest: LocationRequest, private val googleApiKey: String?) {

    private val _location: MutableSharedFlow<LocationValue> = MutableSharedFlow(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /**
     * Start listening for location updates, and receive them when subscribed
     *
     *
     * @return the observable that will emit locations as known
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val location: Flow<Location>
        get() {
            return _location
                    .asSharedFlow()
                    .onStart {
                        tryStartListening()
                    }
                    .map {
                        when (it) {
                            is LocationValue.Value -> it.location
                            is LocationValue.Error -> {
                                _location.resetReplayCache()
                                throw it.error
                            }
                        }
                    }
                    .onCompletion {
                        if (_location.subscriptionCount.value == 0) {
                            shutdownTask()
                        } else {
                            debugPrint("Gathering completed, but subscriptions are still on")
                        }
                    }
        }

    private val handlerDispatcher = Handler(looperThread.looper).asCoroutineDispatcher()
    private val locationCoroutineScope = CoroutineScope(handlerDispatcher + CoroutineName("IQLocationScope"))
    private var globalErrorWatch: ErrorHandler? = null
    private val fusedApiClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext)

    @JvmOverloads
    constructor(
        context: Context,
        longerIntervalMillis: Long = LONGER_INTERVAL_MILLIS,
        fastestIntervalMillis: Long = FASTEST_INTERVAL_MILLIS,
        googleApiKey: String? = null
    ) : this(context, createLocationRequest(longerIntervalMillis, fastestIntervalMillis), googleApiKey)

    private var gettingUpdates: Boolean = false
    private val mutex = Mutex()

    private val callbacks: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(p0: LocationResult) {
            locationCoroutineScope.launch {

                @Suppress("UNNECESSARY_SAFE_CALL")
                p0.lastLocation?.let {
                    _location.emit(LocationValue.Value(it))
                }
            }
        }

        override fun onLocationAvailability(p0: LocationAvailability) {
            debugPrint("Got location availability: {}", p0.isLocationAvailable)
        }
    }

    private suspend fun shutdownTask() {
        mutex.withLock {
            if (gettingUpdates) {
                fusedApiClient.removeLocationUpdates(callbacks)
                debugPrint("Did shutdown location updates")
                gettingUpdates = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryStartListening() {
        mutex.withLock {
            if (!gettingUpdates) {
                val locationEnabled = isLocationEnabled
                when {
                    locationEnabled == NO_PERMISSION -> {
                        throw LocationHelperError("You don't have required permissions, make sure to request them first")
                    }
                    locationEnabled != ENABLED -> {
                        debugPrint("Trying to subscribe to location, but it's disabled... finishing")
                        throw LocationHelperError("You need to enable GPS to get location")
                    }
                    else -> {
                        debugPrint("Subscribed to getLocation")
                    }
                }

                val task = fusedApiClient.requestLocationUpdates(locationRequest, callbacks, looperThread.looper).also {
                    it.await()
                }
                gettingUpdates = task.isSuccessful.also {
                    val lastLocation = fusedApiClient.lastLocation.await()
                    if (null != lastLocation) {
                        _location.emit(LocationValue.Value(lastLocation))
                    }
                    if (it) {
                        debugPrint("Success starting location updates")
                    } else {
                        debugPrint("Failed to setup location updates")
                    }
                }
            }
        }
    }

    class Builder(private val context: Context) {
        private val request: LocationRequest =
            LocationRequest
                .create()
                .setFastestInterval(FASTEST_INTERVAL_MILLIS)
                .setInterval(LONGER_INTERVAL_MILLIS)
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)

        private var googleAPIKey: String? = null
        fun setPriority(priority: Int): Builder {
            request.priority = priority
            return this
        }

        fun setExpirationDuration(millis: Long): Builder {
            request.setExpirationDuration(millis)
            return this
        }

        fun setExpirationTime(millis: Long): Builder {
            request.expirationTime = millis
            return this
        }

        fun setFastestInterval(millis: Long): Builder {
            request.fastestInterval = millis
            return this
        }

        fun setInterval(millis: Long): Builder {
            request.interval = millis
            return this
        }

        fun setGoogleAPIKey(apiKey: String): Builder {
            googleAPIKey = apiKey
            return this
        }

        fun setNumUpdates(numUpdates: Int): Builder {
            request.numUpdates = numUpdates
            return this
        }

        fun setSmallestDisplacement(smallestDisplacementMeters: Float): Builder {
            request.smallestDisplacement = smallestDisplacementMeters
            return this
        }

        fun build(): LocationHelper {
            return LocationHelper(context, request, googleAPIKey)
        }
    }

    fun setGlobalErrorWatch(errorWatch: ErrorHandler?) {
        globalErrorWatch = errorWatch
    }

    val isLocationEnabled: Int
        get() = isLocationEnabled(false)

    fun isLocationEnabled(highAccuracyRequired: Boolean): Int {
        return checkForLocationAvailability(highAccuracyRequired)
    }

    private fun checkForLocationAvailability(highAccuracyRequired: Boolean): Int {
        return checkForLocationAvailability(appContext, highAccuracyRequired, true)
    }

    fun getAddressesAtMyLocation(maxResults: Int): Flow<List<Address>> {
        val mapper = getLocationToAddressesConverter(maxResults)
        return location.onStart {
            if (!isGeocoderEnabled) {
                throw LocationHelperError("Missing Google API Key, cannot perform reverse geolocation")
            }
        }.map { mapper(it) }
    }

    suspend fun getAddressesAtLocation(location: Location, maxResults: Int): List<Address> {
        return if (!isGeocoderEnabled) {
            throw LocationHelperError("Missing Google API Key, cannot perform reverse geolocation")
        } else {
            val locationToAddressesConverter = getLocationToAddressesConverter(maxResults)
            locationToAddressesConverter(location)
        }
    }

    suspend fun getReverseLocationInfo(placeName: String): LocationInfo {
        return if (!isGeocoderEnabled) {
            throw LocationHelperError("Missing Google API Key, cannot perform reverse geolocation")
        } else {
            addressToInfoConverter(placeName)
        }
    }

    val isGeocoderEnabled: Boolean
        get() = googleApiKey != null

    // PREREQUISITE: We've checked that geocoder key is set `isGeocoderEnabled()`
    private suspend fun addressToInfoConverter(locationName: String): LocationInfo {
        return getLatLngBoundsFromAddress(locationName, Locale.getDefault().language, requireNotNull(googleApiKey))
    }

    private fun getLocationToAddressesConverter(maxResults: Int): suspend (Location) -> List<Address> {
        return { location ->
            try {
                // PREREQUISITE: We've checked that geocoder key is set `isGeocoderEnabled()`
                getFromLocation(
                    location.latitude,
                    location.longitude,
                    maxResults,
                    Locale.getDefault().language,
                    requireNotNull(googleApiKey) { "You need to check isGeocoderEnabled" }
                )
            } catch (error: GeocoderError) {
                if (null != globalErrorWatch) {
                    if (!globalErrorWatch!!.chanceToInterceptGeocoderError(error)) {
                        throw error
                    } else {
                        emptyList()
                    }
                } else {
                    throw error
                }
            }
        }
    }

    @JvmOverloads
    fun newLocationSource(retryAlways: Boolean = false): LocationSource {
        return MapLocationSource(this, retryAlways)
    }

    private class OnLocationHolder(listener: OnLocationChangedListener, asWeak: Boolean) {
        private var nonWeakListener: OnLocationChangedListener? = null
        private var locationChangedListener: WeakReference<OnLocationChangedListener>? = null
        val isValid: Boolean
            get() = (locationChangedListener?.get() ?: nonWeakListener) != null

        fun onLocationChanged(location: Location) {
            val locChangedListener = locationChangedListener?.get()
                ?: nonWeakListener

            locChangedListener?.onLocationChanged(location)
        }

        init {
            if (asWeak) {
                locationChangedListener = WeakReference(listener)
            } else {
                nonWeakListener = listener
            }
        }
    }

    private class MapLocationSource(
        private val helper: LocationHelper,
        private val alwaysRetry: Boolean
    ) : LocationSource {

        companion object {
            private const val MAX_MS: Long = 15000
        }

        private var currentScope: CoroutineScope? = null

        @Synchronized
        override fun activate(onLocationChangedListener: OnLocationChangedListener) {
            if (currentScope == null) {
                debugPrint("Activating $this with $onLocationChangedListener")
                currentScope = CoroutineScope(Dispatchers.Main).also {
                    it.beginGettingUpdates(OnLocationHolder(onLocationChangedListener, false))
                }
            } else {
                LoggerFactory.getLogger(TAG).warn("Tried to activate twice this location source")
            }
        }

        @OptIn(ExperimentalTime::class)
        private fun CoroutineScope.beginGettingUpdates(holder: OnLocationHolder) {

            coroutineContext[Job]?.invokeOnCompletion {
                synchronized(this@MapLocationSource) {
                    currentScope = null
                }
            }

            launch {
                helper.location.retryWhen { cause, attempt ->
                    if (cause is LocationHelperError && !alwaysRetry) {
                        false
                    } else {
                        val expectedDelay = ((2 * (attempt + 1)) * 100).coerceAtMost(MAX_MS)
                        debugPrint("Still no location, will delay $expectedDelay ms: {}", cause.stackTraceToString())
                        delay(expectedDelay)
                        true
                    }
                }.collect {
                    holder.onLocationChanged(it)
                }
            }

            launch {
                while (true) {
                    delay(5.seconds)
                    ensureActive()
                    if (!holder.isValid) {
                        this@beginGettingUpdates.cancel()
                    }
                }
            }
        }

        @Synchronized
        override fun deactivate() {
            currentScope?.cancel()
            currentScope = null
            debugPrint("Deactivating... $this")
        }
    }

    /**
     * Represents an error in location helper
     */
    class LocationHelperError(detailMessage: String?) : Error(detailMessage)
    companion object {
        private const val TAG = "IQLocation"
        const val LONGER_INTERVAL_MILLIS = (60 * 60 * 1000L) // 60 minutes in millis
        const val FASTEST_INTERVAL_MILLIS = (60 * 1000L) // 1 minute in millis
        const val CHECK_INTERVAL_SECS = 5
        private var DEBUG = false
        private val looperThread = HandlerThread("IQLocationThread").also {
            it.start()
        }
        private fun debugPrint(fmt: String, vararg args: Any) {
            if (DEBUG) {
                LoggerFactory.getLogger(LocationHelper::class.java).debug(fmt, *args)
            }
        }

        fun builder(ctxt: Context): Builder {
            return Builder(ctxt)
        }

        fun setDebug(enable: Boolean) {
            DEBUG = enable
        }

        private fun createLocationRequest(longerIntervalMillis: Long, fastestIntervalMillis: Long): LocationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            interval = longerIntervalMillis
            fastestInterval = fastestIntervalMillis
        }

        const val ENABLED = 0
        const val NO_PERMISSION = 1
        const val DISABLED = 2

        /**
         * Immediate check of status of location services, through permissions check, and settings check
         *
         *
         * @param highAccuracyRequired if true, then we will request for FINE Location, otherwise COARSE will be used
         * @param includeSettings if true, GPS needs to also be enabled, otherwise only permission is checked
         *
         * @return
         * - 0 in case location services are available.
         * - 1 in case permission is not granted (thus no location available)
         * - 2 when settings are disconnecting location services
         */
        @JvmStatic
        fun checkForLocationAvailability(
            context: Context,
            highAccuracyRequired: Boolean,
            includeSettings: Boolean
        ): Int {
            val resolver = context.contentResolver

            // Check permission first
            if (highAccuracyRequired) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return NO_PERMISSION
                }
            } else {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return NO_PERMISSION
                }
            }
            if (!includeSettings) {
                // TODO: 20/9/16 What's the best neutral result?
                return ENABLED
            }
            var enabled = false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                val allowed =
                    Settings.Secure.getString(resolver, Settings.Secure.LOCATION_PROVIDERS_ALLOWED)
                if (!TextUtils.isEmpty(allowed)) {
                    if (highAccuracyRequired) {
                        if (allowed.contains(LocationManager.GPS_PROVIDER) && allowed.contains(
                                LocationManager.NETWORK_PROVIDER
                            )
                        ) {
                            enabled = true
                        }
                    } else {
                        if (allowed.contains(LocationManager.GPS_PROVIDER) || allowed.contains(
                                LocationManager.NETWORK_PROVIDER
                            )
                        ) {
                            enabled = true
                        }
                    }
                }
            } else {
                val mode = Settings.Secure.getInt(
                    resolver,
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF
                )
                enabled = if (highAccuracyRequired) {
                    mode == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                } else {
                    mode != Settings.Secure.LOCATION_MODE_OFF
                }
            }
            return if (enabled) ENABLED else DISABLED
        }
    }
}
