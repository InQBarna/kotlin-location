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
import android.app.Activity
import android.app.PendingIntent
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStates
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.SettingsClient
import org.slf4j.LoggerFactory
import java.util.*

open class LocationPermissionRequestDelegate private constructor(context: Activity,
                                        callbacks: PermissionDelegateCallbacks,
                                        fragment: Fragment?,
                                        options: Options,
                                        state: Bundle?) : PermissionRequestDelegate(context, null, fragment, options, state) {
    var requestCodeResolveSettings = DEFAULT_RC_RESOLVE_SETTINGS
    private val settingsClient: SettingsClient?
    private lateinit var interceptedCallbacks: PermissionDelegateCallbacks


    private var resolvingError = false

    private val upstreamCallbacks: PermissionDelegateCallbacks = object : PermissionDelegateCallbacks {
        override fun showRequestPermissionsDialog(accept: DialogInterface.OnClickListener, deny: DialogInterface.OnClickListener) {
            interceptedCallbacks.showRequestPermissionsDialog(accept, deny)
        }

        override fun onPermissionGranted(alreadyGranted: Boolean) {
            if (!getOptions().checkSettings) {
                interceptedCallbacks.onPermissionGranted(alreadyGranted)
            } else {
                beginSettingsCheck(alreadyGranted)
            }
        }

        override fun onPermissionDenied() {
            interceptedCallbacks.onPermissionDenied()
        }
    }

    init {
        this.callbacks = upstreamCallbacks
        interceptedCallbacks = callbacks
        settingsClient = if (options.checkSettings) {
            LocationServices.getSettingsClient(context)
        } else {
            null
        }
    }

    @JvmOverloads
    constructor(context: Activity, callbacks: PermissionDelegateCallbacks, state: Bundle? = null, options: Options = defaultOptions()) : this(context, callbacks, null, options, state)
    @JvmOverloads
    constructor(fragment: Fragment, callbacks: PermissionDelegateCallbacks, state: Bundle? = null, options: Options = defaultOptions()) : this(fragment.requireActivity(), callbacks, fragment, options, state)

    @Throws(SendIntentException::class)
    protected fun doResolveResolvable(resolution: PendingIntent, requestCode: Int) {
        if (resolvingError) {
            return
        }
        resolvingError = true
        val intentSender = resolution.intentSender
        if (null != fragment) {
            fragment.startIntentSenderForResult(intentSender, requestCode, null, 0, 0, 0, null)
        } else {
            context.startIntentSenderForResult(intentSender, requestCode, null, 0, 0, 0)
        }
    }

    private fun getOptions(): Options {
        return options as Options
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return if (requestCode == requestCodeResolveSettings) {
            resolvingError = false
            if (resultCode == Activity.RESULT_OK && data != null) {
                processLocationSettings(LocationSettingsStates.fromIntent(data), false)
            } else {
                interceptedCallbacks.onPermissionDenied()
            }
            true
        } else {
            false
        }
    }

    class Options private constructor(
            internal val checkSettings: Boolean,
            internal val requests: List<LocationRequest>,
            baseOptions: PermissionRequestDelegate.Options
    ) : PermissionRequestDelegate.Options(baseOptions) {

        class Builder {
            private var checkSettings = false
            private var locationRequests: MutableList<LocationRequest> = ArrayList()

            private val baseBuilder = PermissionRequestDelegate.Options.Builder()
            fun satisfyRequests(requests: List<LocationRequest>): Builder = apply {
                checkSettings = true
                locationRequests.addAll(requests)
            }

            fun satisfyRequest(request: LocationRequest): Builder = apply {
                checkSettings = true
                locationRequests.add(request)
            }

            fun disableCheckAlways() = apply {
                baseBuilder.disableCheckAlways()
            }

            fun build(): Options {
                return Options(checkSettings, locationRequests, baseBuilder.build())
            }
        }
    }

    override fun onSaveState(state: Bundle) {
        super.onSaveState(state)
    }

    fun checkLocationPermission() {
        checkPermissionsAll(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun beginSettingsCheck(alreadyGranted: Boolean) {

        val client = LocationServices.getSettingsClient(context)
        val task = client.checkLocationSettings(
                LocationSettingsRequest.Builder()
                        .addAllLocationRequests(getOptions().requests)
                        .build()
        )

        task.addOnCompleteListener(context) {
            try {
                val res = it.getResult(ApiException::class.java)
                processLocationSettings(res.locationSettingsStates, alreadyGranted)
            } catch (e: ApiException) {
                when (e.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        val resolvableApiException = e as ResolvableApiException
                        try {
                            doResolveResolvable(resolvableApiException.resolution, requestCodeResolveSettings)
                        } catch (e: SendIntentException) {
                            LoggerFactory.getLogger(TAG).error("Couldn't resolve error: {}", e.stackTraceToString())
                            interceptedCallbacks.onPermissionDenied()
                        }
                    }
                    else -> interceptedCallbacks.onPermissionDenied()
                }
            }
        }
    }

    protected fun processLocationSettings(locationSettingsStates: LocationSettingsStates?, alreadyGranted: Boolean) {
        if (locationSettingsStates == null) {
            // This is happening on mocked location provider
            LoggerFactory.getLogger(TAG).error("Invalid location setting states, blindly grant permission!")
            interceptedCallbacks.onPermissionGranted(alreadyGranted)
            return
        }

        if (locationSettingsStates.isLocationPresent && locationSettingsStates.isLocationUsable) {
            interceptedCallbacks.onPermissionGranted(alreadyGranted)
        } else {
            interceptedCallbacks.onPermissionDenied()
        }
    }

    companion object {
        const val DEFAULT_RC_RESOLVE_SETTINGS = 2
        private const val TAG = "LPermissionDelegate"
        private fun defaultOptions(): Options = Options.Builder().build()
    }
}
