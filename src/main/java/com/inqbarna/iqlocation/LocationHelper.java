package com.inqbarna.iqlocation;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.LocationSource;
import com.inqbarna.iqlocation.util.ErrorHandler;
import com.inqbarna.iqlocation.util.ExecutorServiceScheduler;
import com.inqbarna.iqlocation.util.GeocoderError;

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

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

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

    private       ArrayList<Subscriber<? super Location>> subscribers;
    private       Observable<Location>                    observable;
    private final LocationRequest                         locationRequest;

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            dispatchNewLocation(location);
        }
    };

    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> sheduledTask;
    private ExecutorServiceScheduler rxScheduler = new ExecutorServiceScheduler(executorService);
    private ErrorHandler globalErrorWatch;

    public static Func1<? super Location, Boolean> NOT_NULL = new Func1<Location, Boolean>() {
        @Override
        public Boolean call(Location location) {
            return location != null;
        }
    };

    public static class Builder {
        private Context context;
        private LocationRequest request;

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

        public Builder setNumUpdates(int numUpdates) {
            request.setNumUpdates(numUpdates);
            return this;
        }

        public Builder setSmallestDisplacement(float smallestDisplacementMeters) {
            request.setSmallestDisplacement(smallestDisplacementMeters);
            return this;
        }

        public LocationHelper build() {
            return new LocationHelper(context, request);
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
        Iterator<Subscriber<? super Location>> iterator = subscribers.iterator();
        while (iterator.hasNext()) {
            final Subscriber<? super Location> subscriber = iterator.next();
            if (!subscriber.isUnsubscribed()) {
                count++;
                subscriber.onNext(location);
            } else {
                iterator.remove();
            }
        }

        if (DEBUG) {
            Log.d(TAG, "Location " + location + " delivered to " + count + " subscribers");
        }

        if (subscribers.isEmpty()) {
            if (DEBUG)
                Log.d(TAG, "No more subscribers, LocationHelper will disconnect");
            endClient();
        }
        return !subscribers.isEmpty();
    }

    private synchronized void dispatchCompleted() {
        Iterator<Subscriber<? super Location>> iterator = subscribers.iterator();
        while (iterator.hasNext()) {
            final Subscriber<? super Location> subscriber = iterator.next();
            if (!subscriber.isUnsubscribed()) {
                subscriber.onCompleted();
            }
            iterator.remove();
        }
    }

    private void endClient() {
        if (apiClient.isConnecting()) {
            if (DEBUG) Log.d(TAG, "Tried disconnect while still connecting... ignore");
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

        ContentResolver resolver = appContext.getContentResolver();

        // Check permission first
        if (highAccuracyRequired) {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return NO_PERMISSION;
            }
        } else {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return NO_PERMISSION;
            }
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
        return observable.first(NOT_NULL).observeOn(rxScheduler).map(
                getLocationToAddressesConverter(maxResults));
    }

    public Observable<List<Address>> getAddressesAtLocation(Location location, final int maxResults) {
        return Observable.just(location).observeOn(rxScheduler).map(
                getLocationToAddressesConverter(maxResults)
        );
    }

    private Func1<Location, List<Address>> getLocationToAddressesConverter(final int maxResults) {
        return new Func1<Location, List<Address>>() {
            @Override
            public List<Address> call(Location location) {

                List<Address> addresses = null;
                try {
                    addresses = Geocoder.getFromLocation(
                            location.getLatitude(), location.getLongitude(), maxResults, Locale.getDefault().getLanguage());
                } catch (GeocoderError error) {
                    if (null != globalErrorWatch) {
                        if (!globalErrorWatch.chanceToInterceptGeocoderError(error)) {
                            throw error;
                        }
                    }
                }

                if (null == addresses) {
                    return Collections.emptyList();
                } else {
                    return addresses;
                }

            }
        };
    }

    public LocationHelper(Context context) {
        this(context, LONGER_INTERVAL_MILLIS, FASTEST_INTERVAL_MILLIS);
    }

    public LocationHelper(Context context, long longerIntervalMillis, long fastestIntervalMillis) {
        this.appContext = context.getApplicationContext();
        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(appContext);
        builder.addApi(LocationServices.API).addConnectionCallbacks(this);

        apiClient = builder.build();

        subscribers = new ArrayList<>();
        createObservable();

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                       .setInterval(longerIntervalMillis)
                       .setFastestInterval(fastestIntervalMillis);
    }

    private LocationHelper(Context context, LocationRequest request) {
        this.appContext = context.getApplicationContext();
        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(appContext);
        builder.addApi(LocationServices.API).addConnectionCallbacks(this);

        apiClient = builder.build();

        subscribers = new ArrayList<>();
        createObservable();
        this.locationRequest = request;
    }


    /**
     * The returned observable is not guaranteed to deliver results in main thread, if you want to ensure that
     * use the {@link rx.Observable#observeOn(rx.Scheduler)} giving the {@link rx.android.schedulers.AndroidSchedulers#mainThread()} as argument
     *
     * If interested in just the first not null location consider the operator
     * {@link rx.Observable#first(rx.functions.Func1)}
     *
     *
     *
     * @return the observable that will emit locations as known
     */
    public Observable<Location> getLocation() {
        return observable;
    }

    private void createObservable() {
        observable = Observable.create(new Observable.OnSubscribe<Location>() {
            @Override
            public void call(Subscriber<? super Location> subscriber) {


                final int locationEnabled = isLocationEnabled();
                if (locationEnabled == NO_PERMISSION) {
                    subscriber.onError(new NoPermissionError("You don't have required permissions, make sure to request them first"));
                } else if (locationEnabled != ENABLED) {
                    if (DEBUG) Log.d(TAG, "Trying to subscribe to location, but it's disabled... finishing");
                    subscriber.onCompleted();
                    return;
                } else if (DEBUG) {
                    Log.d(TAG, "Subscribed to getLocation");
                }

                synchronized (LocationHelper.this) {
                    subscribers.add(subscriber);
                }
                if (!apiClient.isConnected() && !apiClient.isConnecting()) {
                    if (DEBUG) Log.d(TAG, "Will connect to Google Api Client");
                    connectApiClient();
                } else {
                    dispatchNewLocation(LocationServices.FusedLocationApi.getLastLocation(apiClient));
                }
            }
        });
    }

    private void connectApiClient() {
        apiClient.connect();
        synchronized (this) {
            if (null == sheduledTask) {
                sheduledTask = executorService.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        if (!checkSubscriversAlive()) {
                            endClient();
                        }
                    }
                }, CHECK_INTERVAL_SECS, CHECK_INTERVAL_SECS, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (!checkSubscriversAlive()) {
            if (DEBUG) Log.d(TAG, "Connected but no one subscribed, will disconnect");

            endClient();
        } else {
            if (!apiClient.isConnected()) {
                // we may have issued disconnect while connecting, because unsubscriptions maybe...
                if (DEBUG) Log.d(TAG, "onConnected called, but client disconnected");
                dispatchCompleted(); // probably not needed?
                return;
            }
            boolean alive = dispatchNewLocation(LocationServices.FusedLocationApi.getLastLocation(apiClient));
            if (alive) {
                // maybe we get disconnected inside dispatch
                LocationServices.FusedLocationApi.requestLocationUpdates(apiClient, locationRequest, locationListener);
            }
        }
    }

    private synchronized boolean checkSubscriversAlive() {
        Iterator<Subscriber<? super Location>> iterator = subscribers.iterator();
        while (iterator.hasNext()) {
            Subscriber<? super Location> subscriber = iterator.next();
            if (subscriber.isUnsubscribed()) {
                iterator.remove();
            }
        }
        return !subscribers.isEmpty();
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
                locationChangedListener = new WeakReference<LocationSource.OnLocationChangedListener>(listener);
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
                    if (DEBUG) Log.d(TAG, "Delivering new location: " + location);
                    l.onLocationChanged(location);
                } else if (DEBUG) {
                    Log.d(TAG, "Cannot deliver location because weak reference has vanished");
                }
            } else if (null != nonWeakListener) {
                if (DEBUG) Log.d(TAG, "Delivering new location: " + location);
                nonWeakListener.onLocationChanged(location);
            }
        }
    }


    private static class MapLocationSource implements LocationSource {
        private LocationHelper helper;

        private Subscription     suscription;
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
                if (DEBUG) Log.d(TAG, "Activating " + this + " with " + onLocationChangedListener);
                holder = new OnLocationHolder(onLocationChangedListener, false);
                beginGettingUpdates();
            } else if (DEBUG) {
                Log.w(TAG, "Tried to activate twice... ");
            }
        }

        private void beginGettingUpdates() {
            suscription = helper.getLocation().filter(
                    new Func1<Location, Boolean>() {
                        @Override
                        public Boolean call(Location location) {
                            return null != location;
                        }
                    }
            ).subscribe(
                    new Action1<Location>() {
                        @Override
                        public void call(Location location) {
                            deliverLocation(location);
                        }
                    },
                    new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            Log.e(TAG, "Error getting location update", throwable);
                            finishSubscription(false);
                            if (throwable instanceof NoPermissionError && !alwaysRetry) {
                                // do not retry in this case
                            } else {
                                scheduleRetry();
                            }
                        }
                    },
                    new Action0() {
                        @Override
                        public void call() {
                            finishSubscription(false);
                            scheduleRetry();
                        }
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

                if (DEBUG)
                    Log.d(TAG, "Will retry in " + val + " ms");

                helper.executorService.schedule(
                        new Runnable() {
                            @Override
                            public void run() {
                                if (isActivated()) {
                                    if (DEBUG)
                                        Log.d(TAG, "Retrying to connect the location source");
                                    beginGettingUpdates();
                                }
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
            if (null != suscription) {
                if (unsubscribe) {
                    suscription.unsubscribe();
                }
                suscription = null;
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
            if (DEBUG)
                Log.d(TAG, "Deactivating... " + this);
            finishSubscription(true);
            holder = null;
            activated = false;
        }
    }

    /**
     * @author David García <david.garcia@inqbarna.com>
     * @version 1.0 17/12/15
     */
    public static class NoPermissionError extends Error {
        public NoPermissionError(String detailMessage) {
            super(detailMessage);
        }
    }
}
