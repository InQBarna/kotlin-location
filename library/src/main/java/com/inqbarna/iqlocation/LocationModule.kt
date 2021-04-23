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

import android.content.Context
import com.google.android.gms.location.LocationRequest
import com.inqbarna.iqlocation.annotation.BatteryConservativeLocation
import com.inqbarna.iqlocation.annotation.FastLocation
import com.inqbarna.iqlocation.annotation.IntermediateLocation
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * @author David García <david.garcia></david.garcia>@inqbarna.com>
 * @version 1.0 30/11/16
 */
@Module
class LocationModule @JvmOverloads constructor(
    context: Context,
    private val googleAPIKey: String? = null
) {
    private val context: Context = context.applicationContext

    @Singleton
    @Provides
    @FastLocation
    fun provideFastLocation(): LocationHelper = with(LocationHelper.builder(context)) {
        setFastestInterval(QUICK_FASTEST_INTERVAL)
        setInterval(QUICK_FASTEST_INTERVAL)
        setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        googleAPIKey?.let { setGoogleAPIKey(it) }
        build()
    }

    @Singleton
    @Provides
    @BatteryConservativeLocation
    fun provideBatterySaverLocation(): LocationHelper = with (LocationHelper.builder(context)) {
        googleAPIKey?.let { setGoogleAPIKey(it) }
        build()
    }

    @Singleton
    @Provides
    @IntermediateLocation
    fun provideIntermediateRequestLocation(): LocationHelper = with (LocationHelper.builder(context)) {
        setInterval(MEDIUM_INTERVAL)
        setFastestInterval(QUICK_FASTEST_INTERVAL)
        setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
        googleAPIKey?.let { setGoogleAPIKey(it) }
        build()
    }

    companion object {
        private const val QUICK_FASTEST_INTERVAL = (5 * 1000L) // 5 seconds in millis
        private const val MEDIUM_INTERVAL = (15 * 1000L) // 15 seconds in millis
    }
}
