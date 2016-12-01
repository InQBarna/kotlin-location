package com.inqbarna.iqlocation;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;

import java.util.ArrayList;
import java.util.List;

public class LocationPermissionRequestDelegate {
    private static final String TAG = "PermsDelegate";

    public static final int  DEFAULT_RC_RESOLVE_ERROR    = 1;
    public static final int  DEFAULT_RC_RESOLVE_SETTINGS = 2;
    public static final int DEFAULT_RC_LOCATION_PERMISSION = 123;

    public int  mRequestCodeLocationPermission = DEFAULT_RC_LOCATION_PERMISSION;
    private int mRequestCodeResolveError       = DEFAULT_RC_RESOLVE_ERROR;
    private int mRequestCodeResolveSettings    = DEFAULT_RC_RESOLVE_SETTINGS;

    private final LocationDelegateCallbacks mCallbacks;
    private final Activity mContext;
    private final Fragment mFragment;
    private Options mOptions;
    private GoogleApiClient mApiClient;
    private final GoogleApiClient.ConnectionCallbacks mClientCallback = new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(@Nullable Bundle bundle) {
            if (null != mOnConnected) {
                mOnConnected.run();
                mOnConnected = null;
            }
        }

        @Override
        public void onConnectionSuspended(int i) {
        }
    };

    private boolean mResolvingError;
    private final GoogleApiClient.OnConnectionFailedListener mConnectionFailedCallback = new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            if (mResolvingError) {
                // Already attempting to resolve an error.
                return;
            } else if (connectionResult.hasResolution()) {
                try {
                    doResolveResolvable(connectionResult.getResolution(), mRequestCodeResolveError);
                } catch (IntentSender.SendIntentException e) {
                    // There was an error with the resolution intent. Try again.
                    mApiClient.connect();
                }
            } else {
                // Show dialog using GoogleApiAvailability.getErrorDialog()
                final String errorString = GoogleApiAvailability.getInstance().getErrorString(connectionResult.getErrorCode());
                Log.e(TAG, "Error connecting to GoogleApiClient: " + errorString);
                failIfRequestedLocation();
                mResolvingError = true;
            }
        }
    };

    public void setRequestCodeLocationPermission(int requestCodeLocationPermission) {
        this.mRequestCodeLocationPermission = requestCodeLocationPermission;
    }

    public void setRequestCodeResolveError(int requestCodeResolveError) {
        this.mRequestCodeResolveError = requestCodeResolveError;
    }

    public void setRequestCodeResolveSettings(int requestCodeResolveSettings) {
        this.mRequestCodeResolveSettings = requestCodeResolveSettings;
    }

    protected void doResolveResolvable(PendingIntent resolution, int requestCode) throws IntentSender.SendIntentException {
        if (mResolvingError) {
            return;
        }
        mResolvingError = true;
        final IntentSender intentSender = resolution.getIntentSender();
        if (null != mFragment) {
            mFragment.startIntentSenderForResult(intentSender, requestCode, null, 0, 0, 0, null);
        } else {
            mContext.startIntentSenderForResult(intentSender, requestCode, null, 0, 0, 0);
        }
    }

    private void failIfRequestedLocation() {
        if (null != mOnConnected) {
            mCallbacks.onPermissionDenied();
            mOnConnected = null;
        }
    }

    private Runnable mOnConnected;

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == mRequestCodeResolveError) {
            mResolvingError = false;
            if (resultCode == Activity.RESULT_OK) {
                if (!mApiClient.isConnected() && !mApiClient.isConnecting()) {
                    mApiClient.connect();
                }
            }
            return true;
        } else if (requestCode == mRequestCodeResolveSettings) {
            if (resultCode == Activity.RESULT_OK) {
                processLocationSettings(LocationSettingsStates.fromIntent(data));
            } else {
                mCallbacks.onPermissionDenied();
            }
            return true;
        } else {
            return false;
        }
    }

    public interface LocationDelegateCallbacks {
        void showRequestPermissionsDialog(DialogInterface.OnClickListener accept, DialogInterface.OnClickListener deny);
        void onPermissionGranted();
        void onPermissionDenied();
    }

    public static class Options {
        final boolean checkAlways;
        final boolean checkSettings;
        final List<LocationRequest> mRequests;

        public Options(Builder builder) {
            checkAlways = builder.checkAllways;
            checkSettings = builder.checkSettings;
            mRequests = builder.locationRequests;
        }

        public static class Builder {

            boolean checkAllways  = true;
            boolean checkSettings = false;
            List<LocationRequest> locationRequests = new ArrayList<>();

            public Builder satisfyRequests(@NonNull List<? extends LocationRequest> requests) {
                this.checkSettings = true;
                locationRequests.addAll(requests);
                return this;
            }

            public Builder disableCheckAllways() {
                this.checkAllways = false;
                return this;
            }

            public Options build() {
                return new Options(this);
            }
        }
    }

    private static final String SHOWN = "com.inqbarna.iqlocation.LocationPermissionRequestDelegate.SHOWN";
    private static final Options DEFAULT_OPTIONS = new Options.Builder().build();

    private boolean mShown;


    public LocationPermissionRequestDelegate(Activity context, LocationDelegateCallbacks callbacks, @Nullable Bundle state) {
        this(context, callbacks, DEFAULT_OPTIONS, state);
    }

    public LocationPermissionRequestDelegate(Activity context, LocationDelegateCallbacks callbacks, @NonNull Options options, @Nullable Bundle state) {
        this(context, callbacks, null, options, state);
    }

    public LocationPermissionRequestDelegate(Fragment fragment, LocationDelegateCallbacks callbacks, @Nullable Bundle state) {
        this(fragment, callbacks, DEFAULT_OPTIONS, state);
    }

    public LocationPermissionRequestDelegate(Fragment fragment, LocationDelegateCallbacks callbacks, @NonNull Options options, @Nullable Bundle state) {
        this(fragment.getActivity(), callbacks, fragment, options, state);
    }

    public LocationPermissionRequestDelegate(Activity context, LocationDelegateCallbacks callbacks, Fragment fragment, @NonNull Options options, @Nullable Bundle state) {
        mCallbacks = callbacks;
        mContext = context;
        mFragment = fragment;
        mOptions = options;

        if (null != state) {
            mShown = state.getBoolean(SHOWN);
        }

        if (mOptions.checkSettings) {
            mApiClient = new GoogleApiClient.Builder(mContext, mClientCallback, mConnectionFailedCallback)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    public void onSaveState(Bundle state) {
        state.putBoolean(SHOWN, mShown);
    }

    public final void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (!mOptions.checkSettings) {
                mCallbacks.onPermissionGranted();
            } else {
                beginSettingsCheck();
            }
        } else if (mOptions.checkAlways || !mShown) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(mContext, Manifest.permission.ACCESS_FINE_LOCATION)) {
                mCallbacks.showRequestPermissionsDialog(
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                actualPermissionRequest();
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mShown = true;
                                onCancelPermission();
                            }
                        }
                );
            } else {
                actualPermissionRequest();
            }
        } else {
            mCallbacks.onPermissionDenied();
        }
    }

    protected void onCancelPermission() {
        mCallbacks.onPermissionDenied();
    }

    void actualPermissionRequest() {
        String[] toRequest = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        if (null != mFragment) {
            mFragment.requestPermissions(toRequest, mRequestCodeLocationPermission);
        } else {
            ActivityCompat.requestPermissions(mContext, toRequest, mRequestCodeLocationPermission);
        }
    }

    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == mRequestCodeLocationPermission) {
            mShown = true;
            boolean granted = false;
            for (int i = 0, grantResultsLength = grantResults.length; i < grantResultsLength; i++) {
                int gr = grantResults[i];
                granted |= gr == PackageManager.PERMISSION_GRANTED;
            }
            if (granted) {
                if (!mOptions.checkSettings) {
                    mCallbacks.onPermissionGranted();
                } else {
                    beginSettingsCheck();
                }
            } else {
                mCallbacks.onPermissionDenied();
            }
            return true;
        }
        return false;
    }

    private void beginSettingsCheck() {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
                builder.addAllLocationRequests(mOptions.mRequests);

                LocationServices.SettingsApi.checkLocationSettings(mApiClient, builder.build())
                        .setResultCallback(
                                new ResultCallback<LocationSettingsResult>() {
                                    @Override
                                    public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                                        final Status status = locationSettingsResult.getStatus();
                                        if (!status.isSuccess()) {
                                            if (status.hasResolution()) {
                                                final PendingIntent resolution = locationSettingsResult.getStatus().getResolution();
                                                try {
                                                    doResolveResolvable(resolution, mRequestCodeResolveSettings);
                                                } catch (IntentSender.SendIntentException e) {
                                                    Log.e(TAG, "Cannot resolve error", e);
                                                    mCallbacks.onPermissionDenied();
                                                }
                                            } else {
                                                mCallbacks.onPermissionDenied();
                                            }
                                        } else {
                                            processLocationSettings(locationSettingsResult.getLocationSettingsStates());
                                        }
                                    }
                                }
                        );
            }
        };

        if (mApiClient.isConnected()) {
            task.run();
        } else {
            mOnConnected = task;
        }
    }

    protected void processLocationSettings(LocationSettingsStates locationSettingsStates) {
        if (locationSettingsStates.isLocationPresent() && locationSettingsStates.isLocationUsable()) {
            mCallbacks.onPermissionGranted();
        } else {
            mCallbacks.onPermissionDenied();
        }
    }

    public void onStart() {
        if (null != mApiClient) {
            mApiClient.connect();
        }
    }

    public void onStop() {
        if (null != mApiClient) {
            mApiClient.disconnect();
        }
    }





    public static AlertDialog defaultLocationDialog(Context context, DialogInterface.OnClickListener accept, DialogInterface.OnClickListener decline) {
        final Resources resources = context.getResources();
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);

        // set title
        alertDialogBuilder.setTitle(resources.getString(R.string.request_location_title));

        // set dialog message
        alertDialogBuilder.setMessage(resources.getString(R.string.request_location_msg)).setCancelable(false);

        alertDialogBuilder.setPositiveButton(resources.getString(android.R.string.ok), accept).setNegativeButton(resources.getString(android.R.string.cancel), decline);

        // create alert dialog
        return alertDialogBuilder.create();
    }
}