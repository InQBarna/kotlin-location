package com.inqbarna.iqlocation;

import android.content.DialogInterface;

/**
 * @author David García <david.garcia@inqbarna.com>
 * @version 1.0 5/12/16
 */
public interface PermissionDelegateCallbacks {
    void showRequestPermissionsDialog(DialogInterface.OnClickListener accept, DialogInterface.OnClickListener deny);
    void onPermissionGranted();
    void onPermissionDenied();
}
