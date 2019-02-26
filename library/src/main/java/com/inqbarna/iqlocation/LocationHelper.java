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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.LocationSource;
import com.inqbarna.iqlocation.util.ErrorHandler;
import com.inqbarna.iqlocation.util.GeocoderError;

import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by David García <david.garcia@inqbarna.com> on 26/11/14.
 */
public class LocationHelper implements GoogleApiClient.ConnectionCallbacks {

    private static final String TAG                     = "IQLocation";
    public static final  long   LONGER_INTERVAL_MILLIS  = 60 * 60 * 1000; // 60 minutes in millis
    public static final  long   FASTEST_INTERVAL_MILLIS = 60 * 1000; // 1 minute in millis
    public static final int CHECK_INTERVAL_SECS = 5;

    private static boolean DEBUG = false;

    private GoogleApiClient apiClient;
    private Context         appContext;
    @Nullable
    private final String googleApiKey;

    private       ArrayList<ObservableEmitter<? super Location>> emitters;
    private       Observable<Location>                           observable;
    private final LocationRequest                                locationRequest;

    private LocationListener locationListener = this::dispatchNewLocation;

    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> sheduledTask;
    private Scheduler rxScheduler = Schedulers.from(executorService);
    private ErrorHandler globalErrorWatch;


    private static void debugPrint(String fmt, Object ...args) {
        if (DEBUG) {
            LoggerFactory.getLogger(LocationHelper.class).debug(fmt, args);
        }
    }

    public static class Builder {
        private Context context;
        private LocationRequest request;
        private String googleAPIKey;

        public Builder(Context context) {
            this.context = context;
            this.request = LocationRequest.create().setFastestInterval(FASTEST_INTERVAL_MILLIS).setInterval(LONGER_INTERVAL_MILLIS)
                                          .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        }

        public Builder setPriority(int priority) {
            request.setPriority(priority);
            return this;
        }

        public Builder setExpirationDuration(long millis) {
            request.setExpirationDuration(millis);
            return this;
        }

        public Builder setExpirationTime(long millis) {
            request.setExpirationTime(millis);
            return this;
        }

        public Builder setFastestInterval(long millis) {
            request.setFastestInterval(millis);
            return this;
        }

        public Builder setInterval(long millis) {
            request.setInterval(millis);
            return this;
        }

        public Builder setGoogleAPIKey(@NonNull String apiKey) {
            this.googleAPIKey = apiKey;
            return this;
        }

        public Builder setNumUpdates(int numUpdates) {
            request.setNumUpdates(numUpdates);
            return this;
        }

        public Builder setSmallestDisplacement(float smallestDisplacementMeters) {
            request.setSmallestDisplacement(smallestDisplacementMeters);
            return this;
        }

        public LocationHelper build() {
            return new LocationHelper(context, request, googleAPIKey);
        }
    }

    public static Builder builder(Context ctxt) {
        return new Builder(ctxt);
    }


    public static void setDebug(boolean enable) {
        DEBUG = enable;
    }

    private synchronized boolean dispatchNewLocation(Location location) {
        int count = 0;
        Iterator<ObservableEmitter<? super Location>> iterator = emitters.iterator();
        while (iterator.hasNext()) {
            final ObservableEmitter<? super Location> subscriber = iterator.next();
            if (!subscriber.isDisposed()) {
                count++;
                if (null != location) {
                    subscriber.onNext(location);
                }
            } else {
                iterator.remove();
            }
        }

        debugPrint("Location " + location + " delivered to " + count + " subscribers");

        if (emitters.isEmpty()) {
            debugPrint("No more subscribers, LocationHelper will disconnect");
            endClient();
        }
        return !emitters.isEmpty();
    }

    private synchronized void dispatchCompleted() {
        Iterator<ObservableEmitter<? super Location>> iterator = emitters.iterator();
        while (iterator.hasNext()) {
            final ObservableEmitter<? super Location> subscriber = iterator.next();
            if (!subscriber.isDisposed()) {
                subscriber.onComplete();
            }
            iterator.remove();
        }
    }

    private synchronized void dispatchError(Throwable error) {
        Iterator<ObservableEmitter<? super Location>> iterator = emitters.iterator();
        while (iterator.hasNext()) {
            final ObservableEmitter<? super Location> subscriber = iterator.next();
            if (!subscriber.isDisposed()) {
                subscriber.onError(error);
            }
            iterator.remove();
        }
    }

    private void endClient() {
        if (apiClient.isConnecting()) {
            debugPrint("Tried disconnect while still connecting... ignore");
            return;
        }
        apiClient.disconnect();
        synchronized (this) {
            if (null != sheduledTask) {
                sheduledTask.cancel(false);
                sheduledTask = null;
            }
        }
    }

    public static final int ENABLED       = 0;
    public static final int NO_PERMISSION = 1;
    public static final int DISABLED      = 2;

    public void setGlobalErrorWatch(ErrorHandler errorWatch) {
        this.globalErrorWatch = errorWatch;
    }

    public int isLocationEnabled() {
        return isLocationEnabled(false);
    }

    public int isLocationEnabled(boolean highAccuracyRequired) {
        return checkForLocationAvailability(highAccuracyRequired);
    }

    protected int checkForLocationAvailability(boolean highAccuracyRequired) {
        return checkForLocationAvailability(appContext, highAccuracyRequired, true);
    }

    public static int checkForLocationAvailability(Context context, boolean highAccuracyRequired, boolean includeSettings) {
        ContentResolver resolver = context.getContentResolver();

        // Check permission first
        if (highAccuracyRequired) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return NO_PERMISSION;
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return NO_PERMISSION;
            }
        }

        if (!includeSettings) {
            // TODO: 20/9/16 What's the best neutral result?
            return ENABLED;
        }

        boolean enabled = false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            String allowed = Settings.Secure.getString(resolver, Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            if (!TextUtils.isEmpty(allowed)) {
                if (highAccuracyRequired) {
                    if (allowed.contains(LocationManager.GPS_PROVIDER) && allowed.contains(LocationManager.NETWORK_PROVIDER)) {
                        enabled = true;
                    }
                } else {
                    if (allowed.contains(LocationManager.GPS_PROVIDER) || allowed.contains(LocationManager.NETWORK_PROVIDER)) {
                        enabled = true;
                    }
                }
            }
        } else {
            int mode = Settings.Secure.getInt(resolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
            if (highAccuracyRequired) {
                enabled = mode == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
            } else {
                enabled = mode != Settings.Secure.LOCATION_MODE_OFF;
            }
        }

        return enabled ? ENABLED : DISABLED;
    }

    public Observable<List<Address>> getAddressesAtMyLocation(final int maxResults) {
        if (!isGeocoderEnabled()) {
            return Observable.error(new LocationHelperError("Missing Google API Key, cannot perform reverse geolocation"));
        }
        return observable.observeOn(rxScheduler).map(getLocationToAddressesConverter(maxResults));
    }

    public Observable<List<Address>> getAddressesAtLocation(Location location, final int maxResults) {
        if (!isGeocoderEnabled()) {
            return Observable.error(new LocationHelperError("Missing Google API Key, cannot perform reverse geolocation"));
        }
        return Observable.just(location).observeOn(rxScheduler).map(getLocationToAddressesConverter(maxResults));
    }

    public Observable<Geocoder.LocationInfo> getReverseLocationInfo(String placeName) {
        if (!isGeocoderEnabled()) {
            return Observable.error(new LocationHelperError("Missing Google API Key, cannot perform reverse geolocation"));
        }
        return Observable.just(placeName).observeOn(rxScheduler).map(getAddressToInfoConverter());
    }

    public boolean isGeocoderEnabled() {
        return googleApiKey != null;
    }

    private Function<? super String, ? extends Geocoder.LocationInfo> getAddressToInfoConverter() {
        // PREREQUISITE: We've checked that geocoder key is set `isGeocoderEnabled()`
        //noinspection ConstantConditions
        return (Function<String, Geocoder.LocationInfo>) locationName -> Geocoder.getLatLngBoundsFromAddress(locationName, Locale.getDefault().getLanguage(), googleApiKey);
    }

    private Function<Location, List<Address>> getLocationToAddressesConverter(final int maxResults) {
        return location -> {
            List<Address> addresses = null;
            try {
                // PREREQUISITE: We've checked that geocoder key is set `isGeocoderEnabled()`
                //noinspection ConstantConditions
                addresses = Geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), maxResults, Locale.getDefault().getLanguage(), googleApiKey);
            } catch (GeocoderError error) {
                if (null != globalErrorWatch) {
                    if (!globalErrorWatch.chanceToInterceptGeocoderError(error)) {
                        throw error;
                    }
                } else {
                    throw error;
                }
            }

            if (null == addresses) {
                return Collections.emptyList();
            } else {
                return addresses;
            }

        };
    }

    public LocationHelper(Context context) {
        this(context, LONGER_INTERVAL_MILLIS, FASTEST_INTERVAL_MILLIS, null);
    }

    public LocationHelper(Context context, long longerIntervalMillis, long fastestIntervalMillis, @Nullable String googleApiKey) {
        this.appContext = context.getApplicationContext();
        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(appContext);
        builder.addApi(LocationServices.API).addConnectionCallbacks(this);

        apiClient = builder.build();

        emitters = new ArrayList<>();
        createObservable();

        this.googleApiKey = googleApiKey;

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                       .setInterval(longerIntervalMillis)
                       .setFastestInterval(fastestIntervalMillis);
    }

    private LocationHelper(Context context, LocationRequest request, @Nullable String googleApiKey) {
        this.appContext = context.getApplicationContext();
        this.googleApiKey = googleApiKey;
        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(appContext);
        builder.addApi(LocationServices.API).addConnectionCallbacks(this);

        apiClient = builder.build();

        emitters = new ArrayList<>();
        createObservable();
        this.locationRequest = request;
    }

    public final LocationRequest getLocationRequest() {
        return locationRequest;
    }

    /**
     * Start listenin for location updates, and receive them when subscribed
     *
     *
     * @return the observable that will emit locations as known
     */
    public Observable<Location> getLocation() {
        return observable;
    }

    @SuppressLint("MissingPermission")
    private void createObservable() {

        observable = Observable.create(anEmitter -> {
            ObservableEmitter<Location> emitter = anEmitter.serialize();

            final int locationEnabled = isLocationEnabled();
            if (locationEnabled == NO_PERMISSION) {
                emitter.onError(new LocationHelperError("You don't have required permissions, make sure to request them first"));
            } else if (locationEnabled != ENABLED) {
                debugPrint("Trying to subscribe to location, but it's disabled... finishing");
                anEmitter.onError(new LocationHelperError("You need to enable GPS to get location"));
                return;
            } else {
                debugPrint("Subscribed to getLocation");
            }

            synchronized (LocationHelper.this) {
                emitters.add(emitter);
            }
            if (!apiClient.isConnected() && !apiClient.isConnecting()) {
                debugPrint("Will connect to Google Api Client");
                connectApiClient();
            } else {
                dispatchNewLocation(LocationServices.FusedLocationApi.getLastLocation(apiClient));
            }
        });
    }

    private void connectApiClient() {
        apiClient.connect();
        synchronized (this) {
            if (null == sheduledTask) {
                sheduledTask = executorService.scheduleAtFixedRate(() -> {
                    if (!checkEmittersAlive()) {
                        endClient();
                    }
                }, CHECK_INTERVAL_SECS, CHECK_INTERVAL_SECS, TimeUnit.SECONDS);
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onConnected(Bundle bundle) {
        if (!checkEmittersAlive()) {
            debugPrint("Connected but no one subscribed, will disconnect");

            endClient();
        } else {
            if (!apiClient.isConnected()) {
                // we may have issued disconnect while connecting, because unsubscriptions maybe...
                debugPrint("onConnected called, but client disconnected");
                dispatchCompleted(); // probably not needed?
                return;
            }

            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                dispatchError(new LocationHelperError("You don't have required permissions, removed while connecting"));
            } else {
                //noinspection MissingPermission
                boolean alive = dispatchNewLocation(LocationServices.FusedLocationApi.getLastLocation(apiClient));
                if (alive) {
                    // maybe we get disconnected inside dispatch
                    //noinspection MissingPermission
                    LocationServices.FusedLocationApi.requestLocationUpdates(apiClient, locationRequest, locationListener);
                }
            }
        }
    }

    private synchronized boolean checkEmittersAlive() {
        Iterator<ObservableEmitter<? super Location>> iterator = emitters.iterator();
        while (iterator.hasNext()) {
            ObservableEmitter<? super Location> subscriber = iterator.next();
            if (subscriber.isDisposed()) {
                iterator.remove();
            }
        }
        return !emitters.isEmpty();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    public LocationSource newLocationSource() {
        return newLocationSource(false);
    }

    public LocationSource newLocationSource(boolean retryAlways) {
        return new MapLocationSource(this, retryAlways);
    }

    private static class OnLocationHolder {
        private LocationSource.OnLocationChangedListener                nonWeakListener;
        private WeakReference<LocationSource.OnLocationChangedListener> locationChangedListener;

        public OnLocationHolder(LocationSource.OnLocationChangedListener listener, boolean asWeak) {
            if (asWeak) {
                locationChangedListener = new WeakReference<>(listener);
            } else {
                nonWeakListener = listener;
            }
        }

        public boolean isWeak() {
            return null != locationChangedListener;
        }

        public boolean isValid() {
            if (null != locationChangedListener) {
                return locationChangedListener.get() != null;
            } else {
                return nonWeakListener != null;
            }
        }

        public void onLocationChanged(Location location) {
            if (isWeak()) {
                LocationSource.OnLocationChangedListener l = locationChangedListener.get();
                if (null != l) {
                    debugPrint("Delivering new location: " + location);
                    l.onLocationChanged(location);
                } else {
                    debugPrint("Cannot deliver location because weak reference has vanished");
                }
            } else if (null != nonWeakListener) {
                debugPrint("Delivering new location: " + location);
                nonWeakListener.onLocationChanged(location);
            }
        }
    }


    private static class MapLocationSource implements LocationSource {
        private LocationHelper helper;

        private Disposable       disposable;
        private boolean          activated;
        private int              count;
        private OnLocationHolder holder;
        private boolean          alwaysRetry;


        private final long MAX_MS = 15000;

        private MapLocationSource(LocationHelper helper, boolean retryAlways) {
            this.helper = helper;
            this.alwaysRetry = retryAlways;
        }

        @Override
        public synchronized void activate(OnLocationChangedListener onLocationChangedListener) {
            if (!activated) {
                count = 0;
                activated = true;
                debugPrint("Activating " + this + " with " + onLocationChangedListener);
                holder = new OnLocationHolder(onLocationChangedListener, false);
                beginGettingUpdates();
            } else {
                Log.w(TAG, "Tried to activate twice... ");
            }
        }

        private void beginGettingUpdates() {
            disposable = helper.getLocation().subscribe(
                    this::deliverLocation,
                    throwable -> {
                        Log.e(TAG, "Error getting location update", throwable);
                        finishSubscription(false);
                        if (throwable instanceof LocationHelperError && !alwaysRetry) {
                            // do not retry in this case
                        } else {
                            scheduleRetry();
                        }
                    },
                    () -> {
                        finishSubscription(false);
                        scheduleRetry();
                    }
            );
        }

        private void scheduleRetry() {
            if (isActivated()) {
                if (count != Integer.MAX_VALUE) {
                    count++;
                }

                long val = (2 * (count - 1)) * 100;
                if (val >= MAX_MS) {
                    val = MAX_MS;
                }

                debugPrint("Will retry in " + val + " ms");

                helper.executorService.schedule(
                        () -> {
                            if (isActivated()) {
                                debugPrint("Retrying to connect the location source");
                                beginGettingUpdates();
                            }
                        },
                        val,
                        TimeUnit.MILLISECONDS
                );
            }
        }

        private synchronized boolean isActivated() {
            return activated;
        }

        private void finishSubscription(boolean unsubscribe) {
            if (null != disposable) {
                if (unsubscribe) {
                    disposable.dispose();
                }
                disposable = null;
            }
        }

        private void deliverLocation(Location location) {
            if (null != holder) {
                if (holder.isValid()) {
                    holder.onLocationChanged(location);
                } else {
                    deactivate();
                }
            }
        }

        @Override
        public synchronized void deactivate() {
            debugPrint("Deactivating... " + this);
            finishSubscription(true);
            holder = null;
            activated = false;
        }
    }

    /**
     * @author David García <david.garcia@inqbarna.com>
     * @version 1.0 17/12/15
     */
    public static class LocationHelperError extends Error {
        public LocationHelperError(String detailMessage) {
            super(detailMessage);
        }
    }
}
