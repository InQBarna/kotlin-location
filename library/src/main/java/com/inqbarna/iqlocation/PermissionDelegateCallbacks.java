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

import android.content.DialogInterface;

/**
 * @author David García <david.garcia@inqbarna.com>
 * @version 1.0 5/12/16
 */
public interface PermissionDelegateCallbacks {
    void showRequestPermissionsDialog(DialogInterface.OnClickListener accept, DialogInterface.OnClickListener deny);
    void onPermissionGranted(boolean alreadyGranted);
    void onPermissionDenied();
}
