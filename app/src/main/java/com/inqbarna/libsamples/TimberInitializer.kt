package com.inqbarna.libsamples

import android.content.Context
import androidx.startup.Initializer
import timber.log.Timber

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 22/04/2021
 */
class TimberInitializer : Initializer<Any> {
    override fun create(context: Context): Any {
        Timber.plant(Timber.DebugTree())
        Timber.tag("Init").d("Initialization of timber successful")
        return Unit
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
