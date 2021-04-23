/*
 * Copyright 2021 InQBarna Kenkyuu Jo SL
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
package com.inqbarna.iqlocation

import android.content.DialogInterface

/**
 * Callbacks to be implemented by Permission Request delegates
 *
 * @author David García (david.garcia>@inqbarna.com)
 * @version 1.0 5/12/16
 */
interface PermissionDelegateCallbacks {

    /**
     * This method will be called when rationale should be shown to the user, according to permission system.
     *
     * @param accept callback to be used when user accepts
     * @param deny callback for user negative answer
     */
    fun showRequestPermissionsDialog(
        accept: DialogInterface.OnClickListener,
        deny: DialogInterface.OnClickListener
    )

    /**
     * Will be called when permission is granted (in the end of request flow). You should perform your stuff
     * depending on the requested permission here.
     *
     * @param alreadyGranted will be true if permission was already granted in advance
     */
    fun onPermissionGranted(alreadyGranted: Boolean)

    /**
     * Will be called when permission is denied (in the end of the flow). Do alternate behaviour in this callback method
     */
    fun onPermissionDenied()
}
