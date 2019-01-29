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
