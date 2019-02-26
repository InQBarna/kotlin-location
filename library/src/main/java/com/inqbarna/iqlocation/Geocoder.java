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

/**
 * Created by oriolfarrus on 13/02/15.
 */

import android.location.Address;
import android.os.Bundle;
import android.os.Looper;
import android.util.SparseArray;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.inqbarna.iqlocation.util.GeocoderError;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Geocoder {

    private static final String TAG                  = "IQGeocoder";
    public static final  String LOCATION_TYPE        = "location_type";
    public static final  String LOCATION_ROOFTOP     = "ROOFTOP";
    public static final  String LOCATION_APPROXIMATE = "APPROXIMATE";


    private static boolean DEBUG_PRINT = false;

    public static void setDebug(boolean enable) {
        DEBUG_PRINT = enable;
    }

    private interface MessageGenerator {
        String generate();
    }
    private static void debugPrint(String fmt, Object ...args) {
        if (DEBUG_PRINT) {
            LoggerFactory.getLogger(Geocoder.class).debug(fmt, args);
        }
    }

    private static void debugPrint(MessageGenerator generator) {
        if (DEBUG_PRINT) {
            LoggerFactory.getLogger(Geocoder.class).debug(generator.generate());
        }
    }

    public static List<Address> getFromLocation(double lat, double lng, int maxResult, String languageCode, @NonNull String googleApiKey) {

        if (Looper.getMainLooper().equals(Looper.myLooper())) {
            throw new IllegalStateException("Cannot run this method from UI thread");
        }

        String address = String.format(
                Locale.ENGLISH, "https://maps.googleapis.com/maps/api/geocode/json?latlng=%1$f,%2$f&sensor=false&language=%3$s&key=%4$s",
                lat, lng, languageCode, googleApiKey);

        debugPrint("Geocoder request: " + address);

        OkHttpClient client = new OkHttpClient();

        Request.Builder builder = new Request.Builder();

        builder.get().url(HttpUrl.parse(address));
        final Call call = client.newCall(builder.build());

        List<Address> retList = null;

        try {
            final Response response = call.execute();
            JSONObject jsonObject = new JSONObject(response.body().string());

            retList = new ArrayList<Address>();

            final String status = jsonObject.getString("status");
            if ("OK".equalsIgnoreCase(status)) {
                JSONArray results = jsonObject.getJSONArray("results");
                if (results.length() > 0) {
                    for (int i = 0; i < results.length() && i < maxResult; i++) {
                        JSONObject result = results.getJSONObject(i);

                        debugPrint(result::toString);

                        Address addr = new Address(Locale.getDefault());

                        if (result.has("geometry")) {
                            JSONObject geometry = result.getJSONObject("geometry");
                            if (geometry.has(LOCATION_TYPE)) {
                                String locationType = geometry.getString(LOCATION_TYPE);
                                Bundle bundle = new Bundle();
                                bundle.putString(LOCATION_TYPE, locationType);
                                addr.setExtras(bundle);
                            }

                            if (geometry.has("location")) {
                                final JSONObject location = geometry.getJSONObject("location");
                                addr.setLatitude(location.getDouble("lat"));
                                addr.setLongitude(location.getDouble("lng"));
                            }
                        }

                        JSONArray components = result.getJSONArray("address_components");
                        String streetNumber = null;
                        String route = null;

                        SparseArray<String> adminAreas = new SparseArray<>();

                        for (int a = 0; a < components.length(); a++) {

                            JSONObject component = components.getJSONObject(a);
                            JSONArray types = component.getJSONArray("types");
                            for (int j = 0; j < types.length(); j++) {
                                String type = types.getString(j);
                                switch (type) {
                                    case "locality":
                                        addr.setLocality(component.getString("long_name"));
                                        break;
                                    case "sublocality":
                                        addr.setSubLocality(component.getString("long_name"));
                                        break;
                                    case "street_number":
                                        streetNumber = component.getString("long_name");
                                        break;
                                    case "route":
                                    case "street_name":
                                        route = component.getString("long_name");
                                        break;
                                    case "country":
                                        addr.setCountryCode(component.getString("short_name"));
                                        addr.setCountryName(component.getString("long_name"));
                                        break;
                                    case "administrative_area_level_2":
                                        adminAreas.put(1, component.getString("long_name"));
                                        break;
                                    case "administrative_area_level_1":
                                        adminAreas.put(0, component.getString("long_name"));
                                        break;
                                    case "administrative_area_level_3":
                                        adminAreas.put(2, component.getString("long_name"));
                                        break;
                                    case "administrative_area_level_4":
                                        adminAreas.put(3, component.getString("long_name"));
                                        break;
                                    case "postal_code":
                                        addr.setPostalCode(component.getString("long_name"));
                                        break;
                                }
                            }
                        }


                        if (adminAreas.size() >= 1) {
                            addr.setAdminArea(adminAreas.valueAt(0));
                        }

                        if (adminAreas.size() >= 2) {
                            addr.setSubAdminArea(adminAreas.valueAt(1));
                        }

                        if (null != route && null != streetNumber) {
                            addr.setAddressLine(0, route + " " + streetNumber);
                        }

                        retList.add(addr);
                    }
                }
            } else {
                throw new GeocoderError(status);
            }

        } catch (IOException e) {
            LoggerFactory.getLogger(Geocoder.class).error("Error calling Google geocode webservice.", e);
            throw new GeocoderError("Error calling Google geocode webservice " + e.getMessage(), e); // because somehow stacktrace not printing
        } catch (JSONException e) {
            LoggerFactory.getLogger(Geocoder.class).error("Error parsing Google geocode webservice response.", e);
            throw new GeocoderError("Error parsing Google geocode webservice response.", e);
        } catch (Exception e) {
            LoggerFactory.getLogger(Geocoder.class).error("Unknown error in geocoder", e);
            throw new GeocoderError("Unknown error in geocoder" + e.getMessage(), e);
        }

        return retList;
    }

    public static LocationInfo getLatLngBoundsFromAddress(String addressName, String languageCode, @NonNull String googleApiKey) {

        if (Looper.getMainLooper().equals(Looper.myLooper())) {
            throw new IllegalStateException("Cannot run this method from UI thread");
        }

        try {
            addressName = URLEncoder.encode(addressName, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new GeocoderError("Failed encoding place", e);
        }

        String address = "https://maps.googleapis.com/maps/api/geocode/json?address=" + addressName + "&sensor=false&language=" + languageCode + "&key=" + googleApiKey;
        debugPrint("Will request: " + address);

        OkHttpClient client = new OkHttpClient();
        final Call call = client.newCall(new Request.Builder().get().url(HttpUrl.parse(address)).build());

        LocationInfo locationInfo = null;

        try {
            final Response response = call.execute();
            String json = response.body().string();

            if (DEBUG_PRINT) {
                debugPrint(json);
            }

            JSONObject jsonObject = new JSONObject(json);

            double neLat = 0.0, neLng = 0.0, swLat = 0.0, swLng = 0.0, lat = 0.0, lng = 0.0;
            boolean found = false;
            LatLngBounds bounds = null;
            LatLngBounds viewport = null;

            if ("OK".equalsIgnoreCase(jsonObject.getString("status"))) {
                JSONArray results = jsonObject.getJSONArray("results");
                if (results.length() > 0) {
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject result = results.getJSONObject(i);

                        if (result.has("geometry")) {
                            JSONObject geometry = result.getJSONObject("geometry");

                            if (geometry.has("viewport")) {
                                JSONObject viewPortJson = geometry.getJSONObject("viewport");

                                if (viewPortJson.has("northeast")) {
                                    JSONObject northeast = viewPortJson.getJSONObject("northeast");
                                    neLat = northeast.getDouble("lat");
                                    neLng = northeast.getDouble("lng");
                                }

                                if (viewPortJson.has("southwest")) {
                                    JSONObject southwest = viewPortJson.getJSONObject("southwest");
                                    swLat = southwest.getDouble("lat");
                                    swLng = southwest.getDouble("lng");
                                }

                                viewport = new LatLngBounds(new LatLng(swLat, swLng), new LatLng(neLat, neLng));
                            }

                            if (geometry.has("location")) {
                                JSONObject location = geometry.getJSONObject("location");
                                lat = location.getDouble("lat");
                                lng = location.getDouble("lng");
                            }

                            if (geometry.has("bounds")) {
                                JSONObject regionJson = geometry.getJSONObject("bounds");

                                if (regionJson.has("northeast")) {
                                    JSONObject northeast = regionJson.getJSONObject("northeast");
                                    neLat = northeast.getDouble("lat");
                                    neLng = northeast.getDouble("lng");
                                }

                                if (regionJson.has("southwest")) {
                                    JSONObject southwest = regionJson.getJSONObject("southwest");
                                    swLat = southwest.getDouble("lat");
                                    swLng = southwest.getDouble("lng");
                                }
                                bounds = new LatLngBounds(new LatLng(swLat, swLng), new LatLng(neLat, neLng));
                            }
                            found = true;
                        }

                        if (found) {
                            break;
                        }
                    }
                }

                if (null == viewport) {
                    if (null != bounds) {
                        viewport = bounds;
                    } else {
                        throw new GeocoderError("Invalid geocoder response? Or did not process them all!");
                    }
                }

                locationInfo = new LocationInfo(viewport, new LatLng(lat, lng), bounds);
            }

        } catch (IOException e) {
            LoggerFactory.getLogger(Geocoder.class).error("Error calling Google geocode webservice.", e);
            throw new GeocoderError("Error calling Google geocode webservice. " + e.getMessage(), e);
        } catch (JSONException e) {
            LoggerFactory.getLogger(Geocoder.class).error("Error parsing Google geocode webservice response.", e);
            throw new GeocoderError("Error parsing Google geocode webservice response. " + e.getMessage(), e);
        }

        return locationInfo;
    }

    public static class LocationInfo {
        private LatLngBounds latLngBounds;
        private LatLngBounds latLngViewPort;
        private LatLng       latLng;

        public LocationInfo(@NonNull LatLngBounds viewPort, LatLng latLng, @Nullable LatLngBounds bounds) {
            this.latLngBounds = bounds;
            this.latLng = latLng;
        }

        @Nullable
        public LatLngBounds getLatLngBounds() {
            return latLngBounds;
        }

        @NonNull
        public LatLngBounds getLatLngViewPort() {
            return latLngViewPort;
        }

        public LatLng getLatLng() {
            return latLng;
        }
    }
}
