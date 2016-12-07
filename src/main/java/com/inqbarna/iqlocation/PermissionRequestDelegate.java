package com.inqbarna.iqlocation;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;

/**
 * @author David García <david.garcia@inqbarna.com>
 * @version 1.0 5/12/16
 */
public class PermissionRequestDelegate {
    protected static final String TAG   = "PermsDelegate";
    protected static final String SHOWN = "com.inqbarna.iqlocation.PermissionRequestDelegate.SHOWN";

    private static final Options DEFAULT_OPTIONS = new Options.Builder().build();

    public static final int DEFAULT_RC_LOCATION_PERMISSION = 123;
    public              int mRequestCodeLocationPermission = DEFAULT_RC_LOCATION_PERMISSION;

    private       boolean                     mShown;
    private       PermissionDelegateCallbacks mCallbacks;
    private final Activity                    mContext;
    private final Fragment                    mFragment;
    private       Options                     mOptions;

    public void setRequestCodeLocationPermission(int requestCodeLocationPermission) {
        this.mRequestCodeLocationPermission = requestCodeLocationPermission;
    }

    public PermissionRequestDelegate(Activity context, PermissionDelegateCallbacks callbacks, @Nullable Bundle state) {
        this(context, callbacks, DEFAULT_OPTIONS, state);
    }

    public PermissionRequestDelegate(Activity context, PermissionDelegateCallbacks callbacks, @NonNull PermissionRequestDelegate.Options options, @Nullable Bundle state) {
        this(context, callbacks, null, options, state);
    }

    public PermissionRequestDelegate(Fragment fragment, PermissionDelegateCallbacks callbacks, @Nullable Bundle state) {
        this(fragment, callbacks, DEFAULT_OPTIONS, state);
    }

    public PermissionRequestDelegate(Fragment fragment, PermissionDelegateCallbacks callbacks, @NonNull PermissionRequestDelegate.Options options, @Nullable Bundle state) {
        this(fragment.getActivity(), callbacks, fragment, options, state);
    }

    public PermissionRequestDelegate(Activity context, PermissionDelegateCallbacks callbacks, Fragment fragment, @NonNull PermissionRequestDelegate.Options options, @Nullable Bundle state) {
        mCallbacks = callbacks;
        mContext = context;
        mFragment = fragment;
        mOptions = options;

        if (null != state) {
            mShown = state.getBoolean(SHOWN);
        }
    }

    protected Activity getContext() {
        return mContext;
    }

    protected Fragment getFragment() {
        return mFragment;
    }

    protected void setCallbacks(PermissionDelegateCallbacks callbacks) {
        mCallbacks = callbacks;
    }

    public void onSaveState(Bundle state) {
        state.putBoolean(LocationPermissionRequestDelegate.SHOWN, mShown);
    }

    private boolean shouldShowRationale(String []perms) {
        boolean show = false;
        for (String perm : perms) {
            show |= ActivityCompat.shouldShowRequestPermissionRationale(getContext(), perm);
        }
        return show;
    }

    public void checkPermissionsAll(final String []permsissions) {
        boolean allGranted = true;
        for (String p : permsissions) {
            allGranted &= ContextCompat.checkSelfPermission(getContext(), p) == PackageManager.PERMISSION_GRANTED;
        }

        if (allGranted) {
            mCallbacks.onPermissionGranted();
        } else if (mOptions.checkAlways || !mShown) {
            if (shouldShowRationale(permsissions)) {
                mCallbacks.showRequestPermissionsDialog(
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                actualPermissionRequest(permsissions);
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
                actualPermissionRequest(permsissions);
            }
        } else {
            mCallbacks.onPermissionDenied();
        }
    }

    protected void onCancelPermission() {
        mCallbacks.onPermissionDenied();
    }

    void actualPermissionRequest(String []toRequest) {
        if (null != getFragment()) {
            getFragment().requestPermissions(toRequest, mRequestCodeLocationPermission);
        } else {
            ActivityCompat.requestPermissions(getContext(), toRequest, mRequestCodeLocationPermission);
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
                mCallbacks.onPermissionGranted();
            } else {
                mCallbacks.onPermissionDenied();
            }
            return true;
        }
        return false;
    }

    protected Options getOptions() {
        return mOptions;
    }

    public static class Options {
        final boolean               checkAlways;

        public Options(PermissionRequestDelegate.Options.Builder builder) {
            checkAlways = builder.checkAllways;
        }

        public static class Builder<T extends Options> {

            boolean checkAllways  = true;

            public PermissionRequestDelegate.Options.Builder<T> disableCheckAllways() {
                this.checkAllways = false;
                return this;
            }

            public T build() {
                return (T) new Options(this);
            }
        }
    }
}
