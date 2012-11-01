/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.jdk15;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import org.mozilla.javascript.*;

public class VMBridge_jdk15 extends org.mozilla.javascript.jdk13.VMBridge_jdk13
{
    public VMBridge_jdk15() throws SecurityException, InstantiationException {
        try {
            // Just try and see if we can access the isVarArgs method.
            // We want to fail loading if the method does not exist
            // so that we can load a bridge to an older JDK instead.
            Method.class.getMethod("isVarArgs", (Class[]) null);
        } catch (NoSuchMethodException e) {
            // Throw a fitting exception that is handled by
            // org.mozilla.javascript.Kit.newInstanceOrNull:
            throw new InstantiationException(e.getMessage());
        }
    }

    @Override
    public boolean isVarArgs(Member member) {
        if (member instanceof Method)
            return ((Method) member).isVarArgs();
        else if (member instanceof Constructor)
            return ((Constructor<?>) member).isVarArgs();
        else
            return false;
    }

    /**
     * If "obj" is a java.util.Iterator or a java.lang.Iterable, return a
     * wrapping as a JavaScript Iterator. Otherwise, return null.
     * This method is in VMBridge since Iterable is a JDK 1.5 addition.
     */
    @Override
    public Iterator<?> getJavaIterator(Context cx, Scriptable scope, Object obj) {
        if (obj instanceof Wrapper) {
            Object unwrapped = ((Wrapper) obj).unwrap();
            Iterator<?> iterator = null;
            if (unwrapped instanceof Iterator)
                iterator = (Iterator<?>) unwrapped;
            if (unwrapped instanceof Iterable)
                iterator = ((Iterable<?>)unwrapped).iterator();
            return iterator;
        }
        return null;
    }
}
