package com.inqbarna.iqlocation.util;

/**
 * Created by David García <david.garcia@inqbarna.com> on 6/5/15.
 */
public interface ErrorHandler {
    /**
     * This method will be called with an instance of {@link com.inqbarna.iqlocation.util.GeocoderError},
     * if returning true error won't be notyfied further, but empty Address list will be given instead, returning false
     * the error will be delivered to the end of the chain.
     *
     * @param error the error object
     * @return true to stop propagation of error
     */
    boolean chanceToInterceptGeocoderError(GeocoderError error);
}
