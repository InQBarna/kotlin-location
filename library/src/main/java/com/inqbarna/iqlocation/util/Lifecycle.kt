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
@file:JvmName("NarrowLifecycle")
package com.inqbarna.iqlocation.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 22/04/2021
 */
@JvmName("narrowLifecycleOwner")
fun LifecycleOwner.asNarrowedLifecycleOwner(): LifecycleOwner {
    return NarrowingLifecycleOwnerImpl(this)
}

fun narrowedLifecycle(): ReadOnlyProperty<LifecycleOwner, Lifecycle> = NarrowingLifecycleDelegate()

private class NarrowingLifecycleDelegate : ReadOnlyProperty<LifecycleOwner, Lifecycle> {
    private var _currentNarrowedLifecycle: LifecycleOwner? = null
    override fun getValue(thisRef: LifecycleOwner, property: KProperty<*>): Lifecycle {
        return _currentNarrowedLifecycle?.takeUnless { it.lifecycle.currentState <= Lifecycle.State.DESTROYED }?.lifecycle
                ?: thisRef.asNarrowedLifecycleOwner().also {
                    _currentNarrowedLifecycle = it
                }.lifecycle
    }
}

private class NarrowingLifecycleOwnerImpl(private val outerOwner: LifecycleOwner): LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    private val outerStateObserver = object : DefaultLifecycleObserver, LifecycleEventObserver {
        override fun onStop(owner: LifecycleOwner) {
            registry.markState(Lifecycle.State.DESTROYED)
            owner.lifecycle.removeObserver(this)
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (registry.currentState > Lifecycle.State.DESTROYED) {
                registry.handleLifecycleEvent(event)
            }
        }
    }
    init {
        outerOwner.lifecycle.addObserver(outerStateObserver)
    }

    override fun getLifecycle(): Lifecycle {
        return registry
    }
}

