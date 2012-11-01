/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Avoid loading classes unless they are used.
 *
 * <p> This improves startup time and average memory usage.
 */
public final class LazilyLoadedCtor implements java.io.Serializable {
	private static final long serialVersionUID = 1L;

    private static final int STATE_BEFORE_INIT = 0;
    private static final int STATE_INITIALIZING = 1;
    private static final int STATE_WITH_VALUE = 2;

    private final ScriptableObject scope;
    private final String propertyName;
    private final String className;
    private final boolean sealed;
    private final boolean privileged;
    private Object initializedValue;
    private int state;

    public LazilyLoadedCtor(ScriptableObject scope, String propertyName,
            String className, boolean sealed)
    {
        this(scope, propertyName, className, sealed, false);
    }

    LazilyLoadedCtor(ScriptableObject scope, String propertyName,
            String className, boolean sealed, boolean privileged)
    {

        this.scope = scope;
        this.propertyName = propertyName;
        this.className = className;
        this.sealed = sealed;
        this.privileged = privileged;
        this.state = STATE_BEFORE_INIT;

        scope.addLazilyInitializedValue(propertyName, 0, this,
                ScriptableObject.DONTENUM);
    }

    void init()
    {
        synchronized (this) {
            if (state == STATE_INITIALIZING)
                throw new IllegalStateException(
                    "Recursive initialization for "+propertyName);
            if (state == STATE_BEFORE_INIT) {
                state = STATE_INITIALIZING;
                // Set value now to have something to set in finally block if
                // buildValue throws.
                Object value = Scriptable.NOT_FOUND;
                try {
                    value = buildValue();
                } finally {
                    initializedValue = value;
                    state = STATE_WITH_VALUE;
                }
            }
        }
    }

    Object getValue()
    {
        if (state != STATE_WITH_VALUE)
            throw new IllegalStateException(propertyName);
        return initializedValue;
    }

    private Object buildValue()
    {
        if(privileged)
        {
            return AccessController.doPrivileged(new PrivilegedAction<Object>()
            {
                public Object run()
                {
                    return buildValue0();
                }
            });
        }
        else
        {
            return buildValue0();
        }
    }

    private Object buildValue0()
    {
        Class<? extends Scriptable> cl = cast(Kit.classOrNull(className));
        if (cl != null) {
            try {
                Object value = ScriptableObject.buildClassCtor(scope, cl,
                                                               sealed, false);
                if (value != null) {
                    return value;
                }
                else {
                    // cl has own static initializer which is expected
                    // to set the property on its own.
                    value = scope.get(propertyName, scope);
                    if (value != Scriptable.NOT_FOUND)
                        return value;
                }
            } catch (InvocationTargetException ex) {
                Throwable target = ex.getTargetException();
                if (target instanceof RuntimeException) {
                    throw (RuntimeException)target;
                }
            } catch (RhinoException ex) {
            } catch (InstantiationException ex) {
            } catch (IllegalAccessException ex) {
            } catch (SecurityException ex) {
            }
        }
        return Scriptable.NOT_FOUND;
    }

    @SuppressWarnings({"unchecked"})
    private Class<? extends Scriptable> cast(Class<?> cl) {
        return (Class<? extends Scriptable>)cl;
    }

}
