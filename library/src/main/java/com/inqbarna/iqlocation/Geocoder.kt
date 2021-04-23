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
@file:JvmName("Geocoder")
package com.inqbarna.iqlocation

import android.location.Address
import android.os.Bundle
import android.util.SparseArray
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.inqbarna.iqlocation.util.GeocoderError
import com.inqbarna.iqlocation.util.await
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "IQGeocoder"
const val LOCATION_TYPE = "location_type"
const val LOCATION_ROOFTOP = "ROOFTOP"
const val LOCATION_APPROXIMATE = "APPROXIMATE"
private var DEBUG_PRINT = false
fun setDebug(enable: Boolean) {
    DEBUG_PRINT = enable
}

private fun debugPrint(fmt: String, vararg args: Any) {
    if (DEBUG_PRINT) {
        LoggerFactory.getLogger(TAG).debug(fmt, *args)
    }
}

private fun debugPrint(generator: MessageGenerator) {
    if (DEBUG_PRINT) {
        LoggerFactory.getLogger(TAG).debug(generator.generate())
    }
}

private suspend fun ResponseBody.awaitString(dispatcher: Dispatcher): String = suspendCancellableCoroutine { continuation ->
    val executorService = dispatcher.executorService()
    if (executorService.isShutdown) {
        continuation.resumeWithException(IllegalStateException("Worker thread shutdown"))
    } else {
        executorService.submit {
            val value = string()
            if (continuation.isActive) {
                continuation.resume(value)
            }
        }
    }
}

suspend fun getFromLocation(
    lat: Double,
    lng: Double,
    maxResult: Int,
    languageCode: String?,
    googleApiKey: String
): List<Address> {
    val address = String.format(
        Locale.ENGLISH,
        "https://maps.googleapis.com/maps/api/geocode/json?latlng=%1\$f,%2\$f&sensor=false&language=%3\$s&key=%4\$s",
        lat,
        lng,
        languageCode,
        googleApiKey
    )
    debugPrint("Geocoder request: $address")
    val client = OkHttpClient()
    val builder = Request.Builder()
    builder.get().url(HttpUrl.get(address))
    try {
        client.newCall(builder.build()).await().use { response ->
            if (!response.isSuccessful) {
                throw GeocoderError("Failed to execute call: ${response.message()}")
            }
            val jsonObject = JSONObject(requireNotNull(response.body()).awaitString(client.dispatcher()))
            val retList = mutableListOf<Address>()
            val status = jsonObject.getString("status")
            if ("OK".equals(status, ignoreCase = true)) {
                val results = jsonObject.getJSONArray("results")
                if (results.length() > 0) {
                    var i = 0
                    while (i < results.length() && i < maxResult) {
                        val result = results.getJSONObject(i)
                        debugPrint { result.toString() }
                        val addr = Address(Locale.getDefault())
                        if (result.has("geometry")) {
                            val geometry = result.getJSONObject("geometry")
                            if (geometry.has(LOCATION_TYPE)) {
                                val locationType = geometry.getString(LOCATION_TYPE)
                                val bundle = Bundle()
                                bundle.putString(LOCATION_TYPE, locationType)
                                addr.extras = bundle
                            }
                            if (geometry.has("location")) {
                                val location = geometry.getJSONObject("location")
                                addr.latitude = location.getDouble("lat")
                                addr.longitude = location.getDouble("lng")
                            }
                        }
                        val components = result.getJSONArray("address_components")
                        var streetNumber: String? = null
                        var route: String? = null
                        val adminAreas = SparseArray<String>()
                        for (a in 0 until components.length()) {
                            val component = components.getJSONObject(a)
                            val types = component.getJSONArray("types")
                            for (j in 0 until types.length()) {
                                val type = types.getString(j)
                                when (type) {
                                    "locality" -> addr.locality = component.getString("long_name")
                                    "sublocality" -> addr.subLocality =
                                        component.getString("long_name")
                                    "street_number" -> streetNumber =
                                        component.getString("long_name")
                                    "route", "street_name" -> route =
                                        component.getString("long_name")
                                    "country" -> {
                                        addr.countryCode = component.getString("short_name")
                                        addr.countryName = component.getString("long_name")
                                    }
                                    "administrative_area_level_2" -> adminAreas.put(
                                        1,
                                        component.getString("long_name")
                                    )
                                    "administrative_area_level_1" -> adminAreas.put(
                                        0,
                                        component.getString("long_name")
                                    )
                                    "administrative_area_level_3" -> adminAreas.put(
                                        2,
                                        component.getString("long_name")
                                    )
                                    "administrative_area_level_4" -> adminAreas.put(
                                        3,
                                        component.getString("long_name")
                                    )
                                    "postal_code" -> addr.postalCode =
                                        component.getString("long_name")
                                }
                            }
                        }
                        if (adminAreas.size() >= 1) {
                            addr.adminArea = adminAreas.valueAt(0)
                        }
                        if (adminAreas.size() >= 2) {
                            addr.subAdminArea = adminAreas.valueAt(1)
                        }
                        if (null != route && null != streetNumber) {
                            addr.setAddressLine(0, "$route $streetNumber")
                        }
                        retList.add(addr)
                        i++
                    }
                }
                return retList.toList()
            } else {
                throw GeocoderError(status)
            }
        }
    } catch (e: IOException) {
        LoggerFactory.getLogger(TAG).error("Error calling Google geocode webservice.", e)
        throw GeocoderError("Error calling Google geocode webservice " + e.message, e) // because somehow stacktrace not printing
    } catch (e: JSONException) {
        LoggerFactory.getLogger(TAG)
            .error("Error parsing Google geocode webservice response.", e)
        throw GeocoderError("Error parsing Google geocode webservice response.", e)
    } catch (e: Exception) {
        LoggerFactory.getLogger(TAG).error("Unknown error in geocoder", e)
        throw GeocoderError("Unknown error in geocoder" + e.message, e)
    }
}

suspend fun getLatLngBoundsFromAddress(
    addressName: String,
    languageCode: String,
    googleApiKey: String
): LocationInfo {
    val encodedAddressName: String = try {
        URLEncoder.encode(addressName, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        throw GeocoderError("Failed encoding place", e)
    }
    val address =
        "https://maps.googleapis.com/maps/api/geocode/json?address=$encodedAddressName&sensor=false&language=$languageCode&key=$googleApiKey"
    debugPrint("Will request: $address")
    val client = OkHttpClient()
    try {
            client.newCall(
                Request
                    .Builder()
                    .get()
                    .url(HttpUrl.get(address))
                    .build()
            ).await().use { response ->
            if (!response.isSuccessful) {
                throw GeocoderError("Failed to execute call: ${response.message()}")
            }
            val json = requireNotNull(response.body()).awaitString(client.dispatcher())
            if (DEBUG_PRINT) {
                debugPrint(json)
            }
            val jsonObject = JSONObject(json)
            var neLat = 0.0
            var neLng = 0.0
            var swLat = 0.0
            var swLng = 0.0
            var lat = 0.0
            var lng = 0.0
            var found = false
            var bounds: LatLngBounds? = null
            var viewport: LatLngBounds? = null
            if ("OK".equals(jsonObject.getString("status"), ignoreCase = true)) {
                val results = jsonObject.getJSONArray("results")
                if (results.length() > 0) {
                    for (i in 0 until results.length()) {
                        val result = results.getJSONObject(i)
                        if (result.has("geometry")) {
                            val geometry = result.getJSONObject("geometry")
                            if (geometry.has("viewport")) {
                                val viewPortJson = geometry.getJSONObject("viewport")
                                if (viewPortJson.has("northeast")) {
                                    val northeast = viewPortJson.getJSONObject("northeast")
                                    neLat = northeast.getDouble("lat")
                                    neLng = northeast.getDouble("lng")
                                }
                                if (viewPortJson.has("southwest")) {
                                    val southwest = viewPortJson.getJSONObject("southwest")
                                    swLat = southwest.getDouble("lat")
                                    swLng = southwest.getDouble("lng")
                                }
                                viewport = LatLngBounds(LatLng(swLat, swLng), LatLng(neLat, neLng))
                            }
                            if (geometry.has("location")) {
                                val location = geometry.getJSONObject("location")
                                lat = location.getDouble("lat")
                                lng = location.getDouble("lng")
                            }
                            if (geometry.has("bounds")) {
                                val regionJson = geometry.getJSONObject("bounds")
                                if (regionJson.has("northeast")) {
                                    val northeast = regionJson.getJSONObject("northeast")
                                    neLat = northeast.getDouble("lat")
                                    neLng = northeast.getDouble("lng")
                                }
                                if (regionJson.has("southwest")) {
                                    val southwest = regionJson.getJSONObject("southwest")
                                    swLat = southwest.getDouble("lat")
                                    swLng = southwest.getDouble("lng")
                                }
                                bounds = LatLngBounds(LatLng(swLat, swLng), LatLng(neLat, neLng))
                            }
                            found = true
                        }
                        if (found) {
                            break
                        }
                    }
                }
                if (null == viewport) {
                    viewport = bounds
                        ?: throw GeocoderError("Invalid geocoder response? Or did not process them all!")
                }
                return LocationInfo(viewport, LatLng(lat, lng), bounds)
            } else {
                throw GeocoderError(jsonObject.getString("status"))
            }
        }
    } catch (e: IOException) {
        LoggerFactory.getLogger(TAG).error("Error calling Google geocode webservice.", e)
        throw GeocoderError("Error calling Google geocode webservice. " + e.message, e)
    } catch (e: JSONException) {
        LoggerFactory.getLogger(TAG).error("Error parsing Google geocode webservice response.", e)
        throw GeocoderError("Error parsing Google geocode webservice response. " + e.message, e)
    }
}

private fun interface MessageGenerator {
    fun generate(): String?
}

class LocationInfo(
    viewPort: LatLngBounds,
    val latLng: LatLng,
    val latLngBounds: LatLngBounds?
) {
    val latLngViewPort: LatLngBounds? = null
}
