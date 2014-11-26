package com.inqbarna.iqlocation;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;

/**
 * Created by David García <david.garcia@inqbarna.com> on 26/11/14.
 */
public class LocationHelper implements GoogleApiClient.ConnectionCallbacks {

    private static final long LONGER_INTERVAL_MILLIS = 60 * 60 * 1000; // 60 minutes in millis
    private static final long FASTEST_INTERVAL_MILLIS = 60 * 1000; // 1 minute in millis
    private GoogleApiClient apiClient;
    private Context appContext;

    private ArrayList<Subscriber<? super Location>> subscribers;
    private Observable<Location> observable;
    private final LocationRequest locationRequest;

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            dispatchNewLocation(location);
        }
    };

    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> sheduledTask;

    private synchronized void dispatchNewLocation(Location location) {
        Iterator<Subscriber<? super Location>> iterator = subscribers.iterator();
        while (iterator.hasNext()) {
            final Subscriber<? super Location> subscriber = iterator.next();
            if (!subscriber.isUnsubscribed()) {
                subscriber.onNext(location);
            } else {
                iterator.remove();
            }
        }

        if (subscribers.isEmpty()) {
            endClient();
        }
    }

    private void endClient() {
        apiClient.disconnect();
        synchronized (this) {
            if (null != sheduledTask) {
                sheduledTask.cancel(false);
                sheduledTask = null;
            }
        }
    }

    public LocationHelper(Context context) {
        this.appContext = context.getApplicationContext();
        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(appContext);
        builder.addApi(LocationServices.API).addConnectionCallbacks(this);

        apiClient = builder.build();

        subscribers = new ArrayList<>();
        createObservable();

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setInterval(LONGER_INTERVAL_MILLIS)
                .setFastestInterval(FASTEST_INTERVAL_MILLIS);
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
                synchronized (LocationHelper.this) {
                    subscribers.add(subscriber);
                }
                if (!apiClient.isConnected() && !apiClient.isConnecting()) {
                    connectApiClient();
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
                }, 1, 1, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (!checkSubscriversAlive()) {
            endClient();
        } else {
            dispatchNewLocation(LocationServices.FusedLocationApi.getLastLocation(apiClient));
            LocationServices.FusedLocationApi.requestLocationUpdates(apiClient, locationRequest, locationListener);
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
}
