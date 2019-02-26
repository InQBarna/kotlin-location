package com.inqbarna.iqlocation.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

/**
 * @author David García <david.garcia@inqbarna.com>
 * @version 1.0 30/11/16
 */
@Qualifier
@Retention(RetentionPolicy.CLASS)
public @interface BatteryConservativeLocation {
}
