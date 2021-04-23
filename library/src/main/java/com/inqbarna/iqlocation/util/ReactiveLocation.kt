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
@file:JvmName("ReactiveLocation")
package com.inqbarna.iqlocation.util

import android.location.Address
import android.location.Location
import com.inqbarna.iqlocation.LocationHelper
import com.inqbarna.iqlocation.LocationInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.reactive.publish
import org.reactivestreams.Publisher

@JvmOverloads
fun LocationHelper.publishLocations(dispatcher: CoroutineDispatcher = Dispatchers.Main): Publisher<Location> {
    return location.asPublisher(dispatcher)
}

@JvmOverloads
fun LocationHelper.addressesAtMyLocation(numResults: Int, dispatcher: CoroutineDispatcher = Dispatchers.Main): Publisher<List<Address>> {
    return getAddressesAtMyLocation(numResults).asPublisher(dispatcher)
}

@OptIn(ExperimentalCoroutinesApi::class)
@JvmOverloads
fun LocationHelper.addressesAtGivenLocation(numResults: Int, location: Location, dispatcher: CoroutineDispatcher = Dispatchers.Main): Publisher<List<Address>> {
    return publish(dispatcher) {
        send(getAddressesAtLocation(location, numResults))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@JvmOverloads
fun LocationHelper.lookupReverseNameInfo(name: String, dispatcher: CoroutineDispatcher = Dispatchers.Main): Publisher<LocationInfo> {
    return publish(dispatcher) {
        send(getReverseLocationInfo(name))
    }
}
