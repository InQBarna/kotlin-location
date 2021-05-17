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
@file:JvmName("LocationFactories")
package com.inqbarna.iqlocation

import android.content.Context
import com.google.android.gms.location.LocationRequest

/**
 * @author David García <david.garcia></david.garcia>@inqbarna.com>
 * @version 1.0 30/11/16
 */
private class LocationHelperDefaultFactory(
    context: Context,
    private val googleAPIKey: String? = null
) : LocationHelperFactory {
    private val context: Context = context.applicationContext

    override fun createIntermediateLocationHelper(): LocationHelper = with (LocationHelper.builder(context)) {
        setInterval(MEDIUM_INTERVAL)
        setFastestInterval(QUICK_FASTEST_INTERVAL)
        setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
        googleAPIKey?.let { setGoogleAPIKey(it) }
        build()
    }

    override fun createBatterySaverLocationHelper(): LocationHelper = with (LocationHelper.builder(context)) {
        googleAPIKey?.let { setGoogleAPIKey(it) }
        build()
    }

    override fun createFastLocationHelper(): LocationHelper = with(LocationHelper.builder(context)) {
        setFastestInterval(QUICK_FASTEST_INTERVAL)
        setInterval(QUICK_FASTEST_INTERVAL)
        setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        googleAPIKey?.let { setGoogleAPIKey(it) }
        build()
    }

    companion object {
        private const val QUICK_FASTEST_INTERVAL = (5 * 1000L) // 5 seconds in millis
        private const val MEDIUM_INTERVAL = (15 * 1000L) // 15 seconds in millis
    }
}

@JvmOverloads
fun defaultLocationFactory(context: Context, googleAPIKey: String? = null): LocationHelperFactory = LocationHelperDefaultFactory(context, googleAPIKey)

interface LocationHelperFactory {
    /**
     * Creates a [LocationHelper] with tradeoff values between quick updates, and
     * battery saving
     */
    fun createIntermediateLocationHelper(): LocationHelper

    /**
     * Creates a [LocationHelper] with lowest battery consumption attribution for the app, but also very slow updates.
     *
     * (Will only get updates if some other app uses FusedApi to get location)
     */
    fun createBatterySaverLocationHelper(): LocationHelper

    /**
     * Location helper with default setup for the fastests location updates. This is the most battery
     * consuming configuration
     */
    fun createFastLocationHelper(): LocationHelper
}
