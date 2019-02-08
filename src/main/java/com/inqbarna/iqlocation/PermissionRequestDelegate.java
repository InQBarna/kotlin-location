package com.inqbarna.iqlocation;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

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
    private final Fragment mFragment;
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

    protected void setCallbacks(@NonNull PermissionDelegateCallbacks callbacks) {
        mCallbacks = callbacks;
    }

    @NonNull
    protected PermissionDelegateCallbacks getCallbacks() {
        return mCallbacks;
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
            mCallbacks.onPermissionGranted(true);
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

    public boolean isRationaleShown() {
        return mShown;
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
                mCallbacks.onPermissionGranted(false);
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

        protected Options(PermissionRequestDelegate.Options.Builder builder) {
            checkAlways = builder.checkAllways;
        }

        public abstract static class BaseBuilder<O extends Options> {
            public abstract O build();
        }

        public static class Builder<T extends Builder> extends BaseBuilder<Options> {

            boolean checkAllways  = true;

            public T disableCheckAllways() {
                this.checkAllways = false;
                return (T) this;
            }

            @Override
            public Options build() {
                return new Options(this);
            }
        }
    }
}
