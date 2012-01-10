/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1998.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Norris Boyd
 *   Matthieu Riou
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.mozilla.javascript.tools.shell;

import java.io.InputStream;
import java.util.List;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;

import org.mozilla.javascript.Kit;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Function;

/**
 * Provides a specialized input stream for consoles to handle line
 * editing, history and completion. Relies on the JLine library (see
 * <http://jline.sourceforge.net>).
 */
public class ShellLine {

    public static InputStream getStream(Scriptable scope) {
        // We don't want a compile-time dependency on the JLine jar, so use
        // reflection to load and reference the JLine classes.
        ClassLoader classLoader = ShellLine.class.getClassLoader();
        if (classLoader == null) {
            // If the attempt to get a class specific class loader above failed
            // then fallback to the system class loader.
            classLoader = ClassLoader.getSystemClassLoader();
        }
        if (classLoader == null) {
            // If for some reason we still don't have a handle to a class
            // loader then give up (avoid a NullPointerException).
            return null;
        }
        Class<?> readerClass = Kit.classOrNull(classLoader, "jline.ConsoleReader");
        if (readerClass == null)
            return null;
        try {
            // ConsoleReader reader = new ConsoleReader();
            Constructor<?> c = readerClass.getConstructor();
            Object reader = c.newInstance();

            // reader.setBellEnabled(false);
            Method m = readerClass.getMethod("setBellEnabled", Boolean.TYPE);
            m.invoke(reader, Boolean.FALSE);

            // reader.addCompletor(new FlexibleCompletor(prefixes));
            Class<?> completorClass = Kit.classOrNull(classLoader,
                "jline.Completor");
            m = readerClass.getMethod("addCompletor", completorClass);
            Object completor = Proxy.newProxyInstance(classLoader,
                    new Class[] { completorClass },
                    new FlexibleCompletor(completorClass, scope));
            m.invoke(reader, completor);

            // return new ConsoleReaderInputStream(reader);
            Class<?> inputStreamClass = Kit.classOrNull(classLoader,
                "jline.ConsoleReaderInputStream");
            c = inputStreamClass.getConstructor(readerClass);
            return (InputStream) c.newInstance(reader);
        } catch (NoSuchMethodException e) {
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }
        return null;
    }
}

/**
 * The completors provided with JLine are pretty uptight, they only
 * complete on a line that it can fully recognize (only composed of
 * completed strings). This one completes whatever came before.
 */
class FlexibleCompletor implements java.lang.reflect.InvocationHandler {
    private Method completeMethod;
    private Scriptable global;

    FlexibleCompletor(Class<?> completorClass, Scriptable global)
        throws NoSuchMethodException
    {
        this.global = global;
        this.completeMethod = completorClass.getMethod("complete", String.class,
                Integer.TYPE, List.class);
    }

    @SuppressWarnings({"unchecked"})
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (method.equals(this.completeMethod)) {
            int result = complete((String)args[0], ((Integer) args[1]).intValue(),
                    (List<String>) args[2]);
            return Integer.valueOf(result);
        }
        throw new NoSuchMethodError(method.toString());
    }

    public int complete(String buffer, int cursor, List<String> candidates) {
        // Starting from "cursor" at the end of the buffer, look backward
        // and collect a list of identifiers separated by (possibly zero)
        // dots. Then look up each identifier in turn until getting to the
        // last, presumably incomplete fragment. Then enumerate all the
        // properties of the last object and find any that have the
        // fragment as a prefix and return those for autocompletion.
        int m = cursor - 1;
        while (m >= 0) {
            char c = buffer.charAt(m);
            if (!Character.isJavaIdentifierPart(c) && c != '.')
                break;
            m--;
        }
        String namesAndDots = buffer.substring(m+1, cursor);
        String[] names = namesAndDots.split("\\.", -1);
        Scriptable obj = this.global;
        for (int i=0; i < names.length - 1; i++) {
            Object val = obj.get(names[i], global);
            if (val instanceof Scriptable)
                obj = (Scriptable) val;
            else {
                return buffer.length(); // no matches
            }
        }
        Object[] ids = (obj instanceof ScriptableObject)
                       ? ((ScriptableObject)obj).getAllIds()
                       : obj.getIds();
        String lastPart = names[names.length-1];
        for (int i=0; i < ids.length; i++) {
            if (!(ids[i] instanceof String))
                continue;
            String id = (String)ids[i];
            if (id.startsWith(lastPart)) {
                if (obj.get(id, obj) instanceof Function)
                    id += "(";
                candidates.add(id);
            }
        }
        return buffer.length() - lastPart.length();
    }
}
