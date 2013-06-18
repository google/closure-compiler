/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.Serializable;

/**
 * Cache of generated classes and data structures to access Java runtime
 * from JavaScript.
 *
 *
 * @since Rhino 1.5 Release 5
 */
public class ClassCache implements Serializable
{
    private static final long serialVersionUID = -8866246036237312215L;
    private static final Object AKEY = "ClassCache";
    private volatile boolean cachingIsEnabled = true;
    private transient Map<Class<?>,JavaMembers> classTable;
    private transient Map<JavaAdapter.JavaAdapterSignature,Class<?>> classAdapterCache;
    private transient Map<Class<?>,Object> interfaceAdapterCache;
    private int generatedClassSerial;
    private Scriptable associatedScope;

    /**
     * Search for ClassCache object in the given scope.
     * The method first calls
     * {@link ScriptableObject#getTopLevelScope(Scriptable scope)}
     * to get the top most scope and then tries to locate associated
     * ClassCache object in the prototype chain of the top scope.
     *
     * @param scope scope to search for ClassCache object.
     * @return previously associated ClassCache object or a new instance of
     *         ClassCache if no ClassCache object was found.
     *
     * @see #associate(ScriptableObject topScope)
     */
    public static ClassCache get(Scriptable scope)
    {
        ClassCache cache = (ClassCache)
                ScriptableObject.getTopScopeValue(scope, AKEY);
        if (cache == null) {
            throw new RuntimeException("Can't find top level scope for " +
                    "ClassCache.get");
        }
        return cache;
    }

    /**
     * Associate ClassCache object with the given top-level scope.
     * The ClassCache object can only be associated with the given scope once.
     *
     * @param topScope scope to associate this ClassCache object with.
     * @return true if no previous ClassCache objects were embedded into
     *         the scope and this ClassCache were successfully associated
     *         or false otherwise.
     *
     * @see #get(Scriptable scope)
     */
    public boolean associate(ScriptableObject topScope)
    {
        if (topScope.getParentScope() != null) {
            // Can only associate cache with top level scope
            throw new IllegalArgumentException();
        }
        if (this == topScope.associateValue(AKEY, this)) {
            associatedScope = topScope;
            return true;
        }
        return false;
    }

    /**
     * Empty caches of generated Java classes and Java reflection information.
     */
    public synchronized void clearCaches()
    {
        classTable = null;
        classAdapterCache = null;
        interfaceAdapterCache = null;
    }

    /**
     * Check if generated Java classes and Java reflection information
     * is cached.
     */
    public final boolean isCachingEnabled()
    {
        return cachingIsEnabled;
    }

     /**
     * Set whether to cache some values.
     * <p>
     * By default, the engine will cache the results of
     * <tt>Class.getMethods()</tt> and similar calls.
     * This can speed execution dramatically, but increases the memory
     * footprint. Also, with caching enabled, references may be held to
     * objects past the lifetime of any real usage.
     * <p>
     * If caching is enabled and this method is called with a
     * <code>false</code> argument, the caches will be emptied.
     * <p>
     * Caching is enabled by default.
     *
     * @param enabled if true, caching is enabled
     *
     * @see #clearCaches()
     */
    public synchronized void setCachingEnabled(boolean enabled)
    {
        if (enabled == cachingIsEnabled)
            return;
        if (!enabled)
            clearCaches();
        cachingIsEnabled = enabled;
    }

    /**
     * @return a map from classes to associated JavaMembers objects
     */
    Map<Class<?>,JavaMembers> getClassCacheMap() {
        if (classTable == null) {
            // Use 1 as concurrency level here and for other concurrent hash maps
            // as we don't expect high levels of sustained concurrent writes.
            classTable = new ConcurrentHashMap<Class<?>,JavaMembers>(16, 0.75f, 1);
        }
        return classTable;
    }

    Map<JavaAdapter.JavaAdapterSignature,Class<?>> getInterfaceAdapterCacheMap()
    {
        if (classAdapterCache == null) {
            classAdapterCache = new ConcurrentHashMap<JavaAdapter.JavaAdapterSignature,Class<?>>(16, 0.75f, 1);
        }
        return classAdapterCache;
    }

    /**
     * @deprecated
     * The method always returns false.
     * @see #setInvokerOptimizationEnabled(boolean enabled)
     */
    public boolean isInvokerOptimizationEnabled()
    {
        return false;
    }

    /**
     * @deprecated
     * The method does nothing.
     * Invoker optimization is no longer used by Rhino.
     * On modern JDK like 1.4 or 1.5 the disadvantages of the optimization
     * like increased memory usage or longer initialization time overweight
     * small speed increase that can be gained using generated proxy class
     * to replace reflection.
     */
    public synchronized void setInvokerOptimizationEnabled(boolean enabled)
    {
    }

    /**
     * Internal engine method to return serial number for generated classes
     * to ensure name uniqueness.
     */
    public final synchronized int newClassSerialNumber()
    {
        return ++generatedClassSerial;
    }

    Object getInterfaceAdapter(Class<?> cl)
    {
        return interfaceAdapterCache == null
                    ? null
                    : interfaceAdapterCache.get(cl);
    }

    synchronized void cacheInterfaceAdapter(Class<?> cl, Object iadapter)
    {
        if (cachingIsEnabled) {
            if (interfaceAdapterCache == null) {
                interfaceAdapterCache = new ConcurrentHashMap<Class<?>,Object>(16, 0.75f, 1);
            }
            interfaceAdapterCache.put(cl, iadapter);
        }
    }

    Scriptable getAssociatedScope() {
        return associatedScope;
    }
}
