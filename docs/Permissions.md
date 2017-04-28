# Permission Helper Classes

### Generic permissions requests

For this task, one would use a plain `PermissionRequestDelegate` as follows

```java
    @Override
    public void onCreate() {
        mCamPermissions = new PermissionRequestDelegate(this, mPermCallbacks, options, savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mCamPermissions.onSaveState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (!mCamPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
```

and whenever you want to actually start flow of the permission request just start it with

```java
    mCamPermissions.checkPermissionsAll(new String[]{Manifest.permission.CAMERA});
```

    NOTE: this could just be done for any permission, not just for Manifest.permission.CAMERA

In order to create options you should do:

```java
    PermissionRequestDelegate.Options.Builder builder = new PermissionRequestDelegate.Options.Builder();
    PermissionRequestDelegate.Options options = builder.build();
```

### Location permission requests

For location permissions, there's an extension of the [PermissionRequestDelegate](#generic-permissions-requests), that is `LocationPermissionRequestDelegate`

It does basically the same, except that it also checks if GPS setting is enabled or not, to consider as a possible request to enable it. The usage would be exact, but
it has its own set of options. Which has the ability to inspect LocationRequests to see if it requires high or low accuracy *etc...*

```java
    final LocationPermissionRequestDelegate.Options options = new LocationPermissionRequestDelegate.Options.Builder()
                .satisfyRequests(Collections.singletonList(getLocationHelper().getLocationRequest()))
                .disableCheckAllways()
                .build();
```


### The callbacks

Both systems require you to give a `PermissionDelegateCallbacks` implementation.

|Method|Description|
|------|-----------|
|`showRequestPermissionsDialog`| This will be called in order to let the application show its **custom** permission request disclaimer dialog. One should end calling either the  `accept` or `deny` callback arguments|
|`onPermissionGranted`| This will be called when permission has been granted |
|`onPermissionDenied` | This will be called when permission has been denied |

