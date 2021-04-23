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
 * Error originated at Geocoder
 */
class GeocoderError private constructor(val serviceStatus: String?, message: String?, throwable: Throwable?) : Error(message, throwable) {

    constructor(serviceStatus: String) : this(serviceStatus, "Google API error status: $serviceStatus", null)
    constructor(msg: String, throwable: Throwable?) : this(null, msg, throwable)
}
