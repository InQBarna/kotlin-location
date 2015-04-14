package com.inqbarna.iqlocation;

/**
 * Created by oriolfarrus on 13/02/15.
 */
import android.location.Address;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.AllClientPNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import rx.Observable;

public class Geocoder {

    private static final String TAG                  = "IQGeocoder";
    public static final  String LOCATION_TYPE        = "location_type";
    public static final  String LOCATION_ROOFTOP     = "ROOFTOP";
    public static final  String LOCATION_APPROXIMATE = "APPROXIMATE";


    private static boolean DEBUG_PRINT = false;

    public static void setDebug(boolean enable) {
        DEBUG_PRINT = enable;
    }

    public static List<Address> getFromLocation(double lat, double lng, int maxResult, String languageCode) {

        if (Looper.getMainLooper().equals(Looper.myLooper())) {
            throw new IllegalStateException("Cannot run this method from UI thread");
        }

        String address = String.format(
                Locale.ENGLISH, "http://maps.googleapis.com/maps/api/geocode/json?latlng=%1$f,%2$f&sensor=false&language=" + languageCode,
                lat, lng);

        if (DEBUG_PRINT) {
            Log.d(TAG, "Geocoder request: " + address);
        }
        HttpGet httpGet = new HttpGet(address);
        HttpClient client = new DefaultHttpClient();
        client.getParams().setParameter(AllClientPNames.USER_AGENT, "Mozilla/5.0 (Java) Gecko/20081007 java-geocoder");
        client.getParams().setIntParameter(AllClientPNames.CONNECTION_TIMEOUT, 5 * 1000);
        client.getParams().setIntParameter(AllClientPNames.SO_TIMEOUT, 25 * 1000);
        HttpResponse response;

        List<Address> retList = null;

        try {
            response = client.execute(httpGet);
            HttpEntity entity = response.getEntity();
            String json = EntityUtils.toString(entity, "UTF-8");

            JSONObject jsonObject = new JSONObject(json);

            retList = new ArrayList<Address>();

            if ("OK".equalsIgnoreCase(jsonObject.getString("status"))) {
                JSONArray results = jsonObject.getJSONArray("results");
                if (results.length() > 0) {
                    for (int i = 0; i < results.length() && i < maxResult; i++) {
                        JSONObject result = results.getJSONObject(i);

                        if (DEBUG_PRINT) {
                            Log.d(TAG, result.toString());
                        }

                        Address addr = new Address(Locale.getDefault());
                        /*String formattedAddress = null;
                        if (result.has("formatted_address")) {
                            formattedAddress = result.getString("formatted_address");
                        }
                        */

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
                                if (type.equals("locality")) {
                                    addr.setLocality(component.getString("long_name"));
                                } else if (type.equals("sublocality")) {
                                    addr.setSubLocality(component.getString("long_name"));
                                } else if (type.equals("street_number")) {
                                    streetNumber = component.getString("long_name");
                                } else if (type.equals("route") || type.equals("street_name")) {
                                    route = component.getString("long_name");
                                } else if (type.equals("country")) {
                                    addr.setCountryCode(component.getString("short_name"));
                                    addr.setCountryName(component.getString("long_name"));
                                } else if (type.equals("administrative_area_level_2")) {
                                    adminAreas.put(1, component.getString("long_name"));
                                } else if (type.equals("administrative_area_level_1")) {
                                    adminAreas.put(0, component.getString("long_name"));
                                } else if (type.equals("administrative_area_level_3")) {
                                    adminAreas.put(2, component.getString("long_name"));
                                } else if (type.equals("administrative_area_level_4")) {
                                    adminAreas.put(3, component.getString("long_name"));
                                } else if (type.equals("postal_code")) {
                                    addr.setPostalCode(component.getString("long_name"));
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
            }


        } catch (IOException e) {
            Log.e(TAG, "Error calling Google geocode webservice.", e);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing Google geocode webservice response.", e);
        }

        return retList;
    }

    public static LocationInfo getLatLngBoundsFromCountryName(String countryName) {

        if (Looper.getMainLooper().equals(Looper.myLooper())) {
            throw new IllegalStateException("Cannot run this method from UI thread");
        }

        try {
            countryName = URLEncoder.encode(countryName, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String address = "http://maps.googleapis.com/maps/api/geocode/json?address=" + countryName + "&sensor=false&language=" +
                Locale.getDefault().getLanguage();
        HttpGet httpGet = new HttpGet(address);
        HttpClient client = new DefaultHttpClient();
        client.getParams().setParameter(AllClientPNames.USER_AGENT, "Mozilla/5.0 (Java) Gecko/20081007 java-geocoder");
        client.getParams().setIntParameter(AllClientPNames.CONNECTION_TIMEOUT, 5 * 1000);
        client.getParams().setIntParameter(AllClientPNames.SO_TIMEOUT, 25 * 1000);
        HttpResponse response;

        LocationInfo locationInfo = null;

        try {
            response = client.execute(httpGet);
            HttpEntity entity = response.getEntity();
            String json = EntityUtils.toString(entity, "UTF-8");

            if (DEBUG_PRINT) {
                Log.d(TAG, json);
            }

            JSONObject jsonObject = new JSONObject(json);

            int maxResult = 1;
            Double neLat = 0.0, neLng = 0.0, swLat = 0.0, swLng = 0.0, lat = 0.0, lng = 0.0;
            boolean found = false;


            if ("OK".equalsIgnoreCase(jsonObject.getString("status"))) {
                JSONArray results = jsonObject.getJSONArray("results");
                if (results.length() > 0) {
                    for (int i = 0; i < results.length() && i < maxResult; i++) {
                        JSONObject result = results.getJSONObject(i);

                        if (result.has("address_components")) {

                            JSONArray address_components = result.getJSONArray("address_components");
                            for (int x = 0; x < address_components.length(); x++) {
                                JSONObject element = address_components.getJSONObject(x);
                                if (element.has("types")) {
                                    JSONArray array = element.getJSONArray("types");
                                    for (int y = 0; y < array.length(); y++) {
                                        String type = array.getString(y);
                                        if ("country".equals(type)) {
                                            found = true;
                                            Log.d("inqgeocoder", "found!");
                                        }
                                    }
                                }


                            }

                        }


                        if (result.has("geometry")) {
                            JSONObject geometry = result.getJSONObject("geometry");

                            if (geometry.has("bounds")) {
                                JSONObject bounds = geometry.getJSONObject("bounds");

                                if (bounds.has("northeast")) {
                                    JSONObject northeast = bounds.getJSONObject("northeast");
                                    neLat = northeast.getDouble("lat");
                                    neLng = northeast.getDouble("lng");
                                }

                                if (bounds.has("southwest")) {
                                    JSONObject southwest = bounds.getJSONObject("southwest");
                                    swLat = southwest.getDouble("lat");
                                    swLng = southwest.getDouble("lng");
                                }


                            }

                            if (geometry.has("location")) {
                                JSONObject location = geometry.getJSONObject("location");
                                lat = location.getDouble("lat");
                                lng = location.getDouble("lng");
                            }

                        }

                        if (found) {
                            break;
                        }

                    }
                }

                LatLngBounds latLngBounds = null;

                if (neLat != 0.0 && neLng != 0.0 && swLat != 0.0 && swLng != 0.0) {
                    latLngBounds = new LatLngBounds(new LatLng(swLat, swLng), new LatLng(neLat, lng));
                }

                locationInfo = new LocationInfo(latLngBounds, new LatLng(lat, lng));
            }


        } catch (IOException e) {
            Log.e(TAG, "Error calling Google geocode webservice.", e);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing Google geocode webservice response.", e);
        }

        return locationInfo;
    }

    public static class LocationInfo {
        LatLngBounds latLngBounds;
        LatLng       latLng;

        public LocationInfo(LatLngBounds latLngBounds, LatLng latLng) {
            this.latLngBounds = latLngBounds;
            this.latLng = latLng;
        }

        public LatLngBounds getLatLngBounds() {
            return latLngBounds;
        }

        public LatLng getLatLng() {
            return latLng;
        }
    }
}
