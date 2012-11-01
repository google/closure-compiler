/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.jdk13;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Member;
import java.lang.reflect.Proxy;

import org.mozilla.javascript.*;

public class VMBridge_jdk13 extends VMBridge
{
    private ThreadLocal<Object[]> contextLocal = new ThreadLocal<Object[]>();

    @Override
    protected Object getThreadContextHelper()
    {
        // To make subsequent batch calls to getContext/setContext faster
        // associate permanently one element array with contextLocal
        // so getContext/setContext would need just to read/write the first
        // array element.
        // Note that it is necessary to use Object[], not Context[] to allow
        // garbage collection of Rhino classes. For details see comments
        // by Attila Szegedi in
        // https://bugzilla.mozilla.org/show_bug.cgi?id=281067#c5

        Object[] storage = contextLocal.get();
        if (storage == null) {
            storage = new Object[1];
            contextLocal.set(storage);
        }
        return storage;
    }

    @Override
    protected Context getContext(Object contextHelper)
    {
        Object[] storage = (Object[])contextHelper;
        return (Context)storage[0];
    }

    @Override
    protected void setContext(Object contextHelper, Context cx)
    {
        Object[] storage = (Object[])contextHelper;
        storage[0] = cx;
    }

    @Override
    protected ClassLoader getCurrentThreadClassLoader()
    {
        return Thread.currentThread().getContextClassLoader();
    }

    @Override
    protected boolean tryToMakeAccessible(Object accessibleObject)
    {
        if (!(accessibleObject instanceof AccessibleObject)) {
            return false;
        }
        AccessibleObject accessible = (AccessibleObject)accessibleObject;
        if (accessible.isAccessible()) {
            return true;
        }
        try {
            accessible.setAccessible(true);
        } catch (Exception ex) { }

        return accessible.isAccessible();
    }

    @Override
    protected Object getInterfaceProxyHelper(ContextFactory cf,
                                             Class<?>[] interfaces)
    {
        // XXX: How to handle interfaces array withclasses from different
        // class loaders? Using cf.getApplicationClassLoader() ?
        ClassLoader loader = interfaces[0].getClassLoader();
        Class<?> cl = Proxy.getProxyClass(loader, interfaces);
        Constructor<?> c;
        try {
            c = cl.getConstructor(new Class[] { InvocationHandler.class });
        } catch (NoSuchMethodException ex) {
            // Should not happen
            throw Kit.initCause(new IllegalStateException(), ex);
        }
        return c;
    }

    @Override
    protected Object newInterfaceProxy(Object proxyHelper,
                                       final ContextFactory cf,
                                       final InterfaceAdapter adapter,
                                       final Object target,
                                       final Scriptable topScope)
    {
        Constructor<?> c = (Constructor<?>)proxyHelper;

        InvocationHandler handler = new InvocationHandler() {
                public Object invoke(Object proxy,
                                     Method method,
                                     Object[] args)
                {
                    // In addition to methods declared in the interface, proxies
                    // also route some java.lang.Object methods through the
                    // invocation handler.
                    if (method.getDeclaringClass() == Object.class) {
                        String methodName = method.getName();
                        if (methodName.equals("equals")) {
                            Object other = args[0];
                            // Note: we could compare a proxy and its wrapped function
                            // as equal here but that would break symmetry of equal().
                            // The reason == suffices here is that proxies are cached
                            // in ScriptableObject (see NativeJavaObject.coerceType())
                            return Boolean.valueOf(proxy == other);
                        }
                        if (methodName.equals("hashCode")) {
                            return Integer.valueOf(target.hashCode());
                        }
                        if (methodName.equals("toString")) {
                            return "Proxy[" + target.toString() + "]";
                        }
                    }
                    return adapter.invoke(cf, target, topScope, proxy, method, args);
                }
            };
        Object proxy;
        try {
            proxy = c.newInstance(handler);
        } catch (InvocationTargetException ex) {
            throw Context.throwAsScriptRuntimeEx(ex);
        } catch (IllegalAccessException ex) {
            // Should not happen
            throw Kit.initCause(new IllegalStateException(), ex);
        } catch (InstantiationException ex) {
            // Should not happen
            throw Kit.initCause(new IllegalStateException(), ex);
        }
        return proxy;
    }

    @Override
    protected boolean isVarArgs(Member member) {
      return false;
    }
}
