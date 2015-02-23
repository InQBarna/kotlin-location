package com.inqbarna.iqlocation;

/**
 * Created by oriolfarrus on 13/02/15.
 */
import android.location.Address;
import android.os.Bundle;
import android.util.Log;

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

public class Geocoder {

    private static final String TAG = "IQGeocoder";

    private static boolean DEBUG_PRINT = false;

    public static void setDebug(boolean enable) {
        DEBUG_PRINT = enable;
    }

    public static List<Address> getFromLocation(double lat, double lng, int maxResult, String languageCode) {

        String address = String.format(
                Locale.ENGLISH, "http://maps.googleapis.com/maps/api/geocode/json?latlng=%1$f,%2$f&sensor=false&language=" + languageCode,
                lat, lng);
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

            if (DEBUG_PRINT)
                Log.d(TAG,json);

            JSONObject jsonObject = new JSONObject(json);

            retList = new ArrayList<Address>();

            if ("OK".equalsIgnoreCase(jsonObject.getString("status"))) {
                JSONArray results = jsonObject.getJSONArray("results");
                if (results.length() > 0) {
                    for (int i = 0; i < results.length() && i < maxResult; i++) {
                        JSONObject result = results.getJSONObject(i);

                        if (DEBUG_PRINT)
                            Log.d(TAG, result.toString());

                        Address addr = new Address(Locale.getDefault());
                        // addr.setAddressLine(0, result.getString("formatted_address"));

                        if(result.has("geometry")) {
                            JSONObject geometry = result.getJSONObject("geometry");
                            if(geometry.has("location_type")) {
                                String locationType = geometry.getString("location_type");
                                Bundle bundle = new Bundle();
                                bundle.putString("location_type", locationType);
                                addr.setExtras(bundle);
                            }
                        }

                        JSONArray components = result.getJSONArray("address_components");
                        String streetNumber = "";
                        String route = "";
                        String iso = "";
                        String adminArea2 = "";
                        String city = "";
                        String adminArea1 = "";
                        for (int a = 0; a < components.length(); a++) {

                            JSONObject component = components.getJSONObject(a);
                            JSONArray types = component.getJSONArray("types");
                            for (int j = 0; j < types.length(); j++) {
                                String type = types.getString(j);
                                if (type.equals("locality")) {
                                    city = component.getString("long_name");
                                    addr.setLocality(component.getString("long_name"));
                                } else if (type.equals("street_number")) {
                                    streetNumber = component.getString("long_name");
                                } else if (type.equals("route")) {
                                    route = component.getString("long_name");
                                } else if (type.equals("country")) {
                                    iso = component.getString("short_name");
                                } else if (type.equals("administrative_area_level_2")) {
                                    adminArea2 = component.getString("long_name");
                                } else if (type.equals("administrative_area_level_1")) {
                                    adminArea1 = component.getString("long_name");
                                    addr.setAdminArea(adminArea1);
                                }
                            }
                        }

                        addr.setLocality(city);
                        if(!adminArea2.matches("")){
                            addr.setSubAdminArea(adminArea2);
                        }else{
                            addr.setSubAdminArea(adminArea1);
                        }

                        addr.setAddressLine(0, route + " " + streetNumber);
                        addr.setCountryCode(iso);
                        addr.setLatitude(result.getJSONObject("geometry").getJSONObject("location").getDouble("lat"));
                        addr.setLongitude(result.getJSONObject("geometry").getJSONObject("location").getDouble("lng"));
                        retList.add(addr);
                    }
                }
            }


        } catch (ClientProtocolException e) {
            Log.e(TAG, "Error calling Google geocode webservice.", e);
        } catch (IOException e) {
            Log.e(TAG, "Error calling Google geocode webservice.", e);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing Google geocode webservice response.", e);
        }

        return retList;
    }

    public static LocationInfo getLatLngBoundsFromCountryName(String countryName) {
        try {
            countryName = URLEncoder.encode(countryName, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String address = "http://maps.googleapis.com/maps/api/geocode/json?address="+countryName+"&sensor=false&language=" + Locale.getDefault().getLanguage();
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

            if (DEBUG_PRINT)
                Log.d(TAG, json);

            JSONObject jsonObject = new JSONObject(json);

            int maxResult = 1;
            Double neLat = 0.0, neLng = 0.0, swLat = 0.0, swLng = 0.0, lat = 0.0, lng = 0.0;
            boolean found = false;


            if ("OK".equalsIgnoreCase(jsonObject.getString("status"))) {
                JSONArray results = jsonObject.getJSONArray("results");
                if (results.length() > 0) {
                    for (int i = 0; i < results.length() && i < maxResult; i++) {
                        JSONObject result = results.getJSONObject(i);

                        if(result.has("address_components")){

                            JSONArray address_components = result.getJSONArray("address_components");
                            for(int x = 0 ; x < address_components.length() ; x++){
                                JSONObject element = address_components.getJSONObject(x);
                                if(element.has("types")){
                                    JSONArray array = element.getJSONArray("types");
                                    for(int y = 0 ; y < array.length() ; y++){
                                        String type = array.getString(y);
                                        if("country".equals(type)){
                                            found = true;
                                            Log.d("inqgeocoder","found!");
                                        }
                                    }
                                }


                            }

                        }


                        if(result.has("geometry")){
                            JSONObject geometry = result.getJSONObject("geometry");

                            if(geometry.has("bounds")){
                                JSONObject bounds = geometry.getJSONObject("bounds");

                                if(bounds.has("northeast")){
                                    JSONObject northeast = bounds.getJSONObject("northeast");
                                    neLat = northeast.getDouble("lat");
                                    neLng = northeast.getDouble("lng");
                                }

                                if(bounds.has("southwest")){
                                    JSONObject southwest = bounds.getJSONObject("southwest");
                                    swLat = southwest.getDouble("lat");
                                    swLng = southwest.getDouble("lng");
                                }


                            }

                            if(geometry.has("location")){
                                JSONObject location = geometry.getJSONObject("location");
                                lat = location.getDouble("lat");
                                lng = location.getDouble("lng");
                            }

                        }

                        if(found)
                            break;

                    }
                }

                LatLngBounds latLngBounds = null;

                if(neLat != 0.0 && neLng != 0.0 && swLat != 0.0 && swLng != 0.0)
                    latLngBounds = new LatLngBounds(new LatLng(swLat,swLng),new LatLng(neLat,lng));

                locationInfo = new LocationInfo(latLngBounds, new LatLng(lat,lng));
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
        LatLng latLng;

        public LocationInfo(LatLngBounds latLngBounds, LatLng latLng){
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
