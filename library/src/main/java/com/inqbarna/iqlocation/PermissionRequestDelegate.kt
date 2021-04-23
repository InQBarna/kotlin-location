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

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * @author David García <david.garcia></david.garcia>@inqbarna.com>
 * @version 1.0 5/12/16
 */
open class PermissionRequestDelegate protected constructor(protected val context: Activity,
                                                           protected var callbacks: PermissionDelegateCallbacks?,
                                                           protected val fragment: Fragment?,
                                                           protected val options: Options,
                                                           state: Bundle?) {
    var requestCodeLocationPermission = DEFAULT_RC_LOCATION_PERMISSION
    var isRationaleShown = false
        private set


    @JvmOverloads
    constructor(context: Activity, callbacks: PermissionDelegateCallbacks, state: Bundle? = null, options: Options = defaultOptions()) : this(context, callbacks, null, options, state)
    @JvmOverloads
    constructor(fragment: Fragment, callbacks: PermissionDelegateCallbacks, state: Bundle? = null, options: Options = defaultOptions()) : this(fragment.requireActivity(), callbacks, fragment, options, state)

    open fun onSaveState(state: Bundle) {
        state.putBoolean(SHOWN, isRationaleShown)
    }

    private fun shouldShowRationale(perms: Array<String>): Boolean {
        var show = false
        for (perm in perms) {
            show = show or ActivityCompat.shouldShowRequestPermissionRationale(context, perm)
        }
        return show
    }

    open fun checkPermissionsAll(permsissions: Array<String>) {
        var allGranted = true
        for (p in permsissions) {
            allGranted = allGranted and (ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED)
        }
        if (allGranted) {
            callbacks?.onPermissionGranted(true)
        } else if (options.checkAlways || !isRationaleShown) {
            if (shouldShowRationale(permsissions)) {
                callbacks?.showRequestPermissionsDialog(
                        { _, _ ->
                            actualPermissionRequest(permsissions)
                        }
                ) { _, _ ->
                    isRationaleShown = true
                    onCancelPermission()
                }
            } else {
                actualPermissionRequest(permsissions)
            }
        } else {
            callbacks?.onPermissionDenied()
        }
    }

    protected fun onCancelPermission() {
        callbacks?.onPermissionDenied()
    }

    fun actualPermissionRequest(toRequest: Array<String>) {
        if (null != fragment) {
            fragment.requestPermissions(toRequest, requestCodeLocationPermission)
        } else {
            ActivityCompat.requestPermissions(context, toRequest, requestCodeLocationPermission)
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        if (requestCode == requestCodeLocationPermission) {
            isRationaleShown = true
            var granted = false
            var i = 0
            val grantResultsLength = grantResults.size
            while (i < grantResultsLength) {
                val gr = grantResults[i]
                granted = granted or (gr == PackageManager.PERMISSION_GRANTED)
                i++
            }
            if (granted) {
                callbacks?.onPermissionGranted(false)
            } else {
                callbacks?.onPermissionDenied()
            }
            return true
        }
        return false
    }

    open class Options protected constructor(
            internal val checkAlways: Boolean
    ) {

        protected constructor(other: Options) : this(other.checkAlways)


        class Builder {
            var checkAllways = true
            fun disableCheckAlways() = apply {
                checkAllways = false
            }

            fun build() = Options(checkAllways)
        }

    }

    companion object {
        protected const val TAG = "PermsDelegate"
        private const val SHOWN = "com.inqbarna.iqlocation.PermissionRequestDelegate.SHOWN"
        private const val DEFAULT_RC_LOCATION_PERMISSION = 123
        private fun defaultOptions(): Options = Options.Builder().build()
    }

    init {
        if (null != state) {
            isRationaleShown = state.getBoolean(SHOWN)
        }
    }
}
