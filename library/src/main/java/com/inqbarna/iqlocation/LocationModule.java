/*
 * Copyright 2014 InQBarna Kenkyuu Jo SL
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


package com.inqbarna.iqlocation;

import android.content.Context;

import com.google.android.gms.location.LocationRequest;
import com.inqbarna.iqlocation.annotation.BatteryConservativeLocation;
import com.inqbarna.iqlocation.annotation.FastLocation;
import com.inqbarna.iqlocation.annotation.IntermediateLocation;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * @author David García <david.garcia@inqbarna.com>
 * @version 1.0 30/11/16
 */
@Module
public class LocationModule {

    private static final long QUICK_FASTEST_INTERVAL = 5 * 1000; // 5 seconds in millis
    private static final long MEDIUM_INTERVAL = 15 * 1000; // 15 seconds in millis
    private Context mContext;
    private final String googleAPIKey;

    public LocationModule(Context context) {
        this(context, null);
    }

    public LocationModule(Context context, String googleAPIKey) {
        mContext = context.getApplicationContext();
        this.googleAPIKey = googleAPIKey;
    }

    @Singleton
    @Provides
    @FastLocation
    LocationHelper provideFastLocation() {
        return LocationHelper.builder(mContext)
                             .setFastestInterval(QUICK_FASTEST_INTERVAL)
                             .setInterval(QUICK_FASTEST_INTERVAL)
                             .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                             .setGoogleAPIKey(googleAPIKey)
                             .build();
    }

    @Singleton
    @Provides
    @BatteryConservativeLocation
    LocationHelper provideBatterySaverLocation() {
        return LocationHelper.builder(mContext).setGoogleAPIKey(googleAPIKey).build();
    }

    @Singleton
    @Provides
    @IntermediateLocation
    LocationHelper provideIntermediateRequestLocation() {
        return LocationHelper.builder(mContext)
                             .setInterval(MEDIUM_INTERVAL)
                             .setFastestInterval(QUICK_FASTEST_INTERVAL)
                             .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                             .setGoogleAPIKey(googleAPIKey)
                             .build();
    }

}
