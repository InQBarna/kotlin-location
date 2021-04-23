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
package com.inqbarna.iqlocation.util

/**
 * Error management policy customizations can be done providing with custom
 * implementation of this interface at [com.inqbarna.iqlocation.LocationHelper.setGlobalErrorWatch]
 */
interface ErrorHandler {
    /**
     * This method will be called with an instance of [com.inqbarna.iqlocation.util.GeocoderError],
     * if returning true error won't be notyfied further, but empty Address list will be given instead, returning false
     * the error will be delivered to the end of the chain.
     *
     * @param error the error object
     * @return true to stop propagation of error
     */
    fun chanceToInterceptGeocoderError(error: GeocoderError): Boolean
}
