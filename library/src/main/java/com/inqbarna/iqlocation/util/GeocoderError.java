package com.inqbarna.iqlocation.util;

import androidx.annotation.NonNull;

/**
 * Created by David García <david.garcia@inqbarna.com> on 6/5/15.
 */
public class GeocoderError extends Error {


    private String serviceStatus;

    public GeocoderError(@NonNull String serviceStatus) {
        super("Google API error status: " + serviceStatus);
        this.serviceStatus = serviceStatus;
    }

    public GeocoderError(String message, Throwable throwable) {
        super(message, throwable);
    }

}
