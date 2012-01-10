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
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Norris Boyd
 *   Frank Mitchell
 *   Mike Shaver
 *   Kurt Westerfeld
 *   Kemal Bayram
 *   Ulrike Mueller <umueller@demandware.com>
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

package org.mozilla.javascript;

import java.lang.reflect.*;
import java.util.Map;

/**
 * This class reflects Java classes into the JavaScript environment, mainly
 * for constructors and static members.  We lazily reflect properties,
 * and currently do not guarantee that a single j.l.Class is only
 * reflected once into the JS environment, although we should.
 * The only known case where multiple reflections
 * are possible occurs when a j.l.Class is wrapped as part of a
 * method return or property access, rather than by walking the
 * Packages/java tree.
 *
 * @see NativeJavaArray
 * @see NativeJavaObject
 * @see NativeJavaPackage
 */

public class NativeJavaClass extends NativeJavaObject implements Function
{
    static final long serialVersionUID = -6460763940409461664L;

    // Special property for getting the underlying Java class object.
    static final String javaClassPropertyName = "__javaObject__";

    public NativeJavaClass() {
    }

    public NativeJavaClass(Scriptable scope, Class<?> cl) {
        this.parent = scope;
        this.javaObject = cl;
        initMembers();
    }

    @Override
    protected void initMembers() {
        Class<?> cl = (Class<?>)javaObject;
        members = JavaMembers.lookupClass(parent, cl, cl, false);
        staticFieldAndMethods
            = members.getFieldAndMethodsObjects(this, cl, true);
    }

    @Override
    public String getClassName() {
        return "JavaClass";
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return members.has(name, true) || javaClassPropertyName.equals(name);
    }

    @Override
    public Object get(String name, Scriptable start) {
        // When used as a constructor, ScriptRuntime.newObject() asks
        // for our prototype to create an object of the correct type.
        // We don't really care what the object is, since we're returning
        // one constructed out of whole cloth, so we return null.
        if (name.equals("prototype"))
            return null;

         if (staticFieldAndMethods != null) {
            Object result = staticFieldAndMethods.get(name);
            if (result != null)
                return result;
        }

        if (members.has(name, true)) {
            return members.get(this, name, javaObject, true);
        }

        Context cx = Context.getContext();
        Scriptable scope = ScriptableObject.getTopLevelScope(start);
        WrapFactory wrapFactory = cx.getWrapFactory();

        if (javaClassPropertyName.equals(name)) {
            return wrapFactory.wrap(cx, scope, javaObject,
                                    ScriptRuntime.ClassClass);
        }

        // experimental:  look for nested classes by appending $name to
        // current class' name.
        Class<?> nestedClass = findNestedClass(getClassObject(), name);
        if (nestedClass != null) {
            Scriptable nestedValue = wrapFactory.wrapJavaClass(cx, scope,
                    nestedClass);
            nestedValue.setParentScope(this);
            return nestedValue;
        }

        throw members.reportMemberNotFound(name);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        members.put(this, name, javaObject, value, true);
    }

    @Override
    public Object[] getIds() {
        return members.getIds(true);
    }

    public Class<?> getClassObject() {
        return (Class<?>) super.unwrap();
    }

    @Override
    public Object getDefaultValue(Class<?> hint) {
        if (hint == null || hint == ScriptRuntime.StringClass)
            return this.toString();
        if (hint == ScriptRuntime.BooleanClass)
            return Boolean.TRUE;
        if (hint == ScriptRuntime.NumberClass)
            return ScriptRuntime.NaNobj;
        return this;
    }

    public Object call(Context cx, Scriptable scope, Scriptable thisObj,
                       Object[] args)
    {
        // If it looks like a "cast" of an object to this class type,
        // walk the prototype chain to see if there's a wrapper of a
        // object that's an instanceof this class.
        if (args.length == 1 && args[0] instanceof Scriptable) {
            Class<?> c = getClassObject();
            Scriptable p = (Scriptable) args[0];
            do {
                if (p instanceof Wrapper) {
                    Object o = ((Wrapper) p).unwrap();
                    if (c.isInstance(o))
                        return p;
                }
                p = p.getPrototype();
            } while (p != null);
        }
        return construct(cx, scope, args);
    }

    public Scriptable construct(Context cx, Scriptable scope, Object[] args)
    {
        Class<?> classObject = getClassObject();
        int modifiers = classObject.getModifiers();
        if (! (Modifier.isInterface(modifiers) ||
               Modifier.isAbstract(modifiers)))
        {
            NativeJavaMethod ctors = members.ctors;
            int index = ctors.findCachedFunction(cx, args);
            if (index < 0) {
                String sig = NativeJavaMethod.scriptSignature(args);
                throw Context.reportRuntimeError2(
                    "msg.no.java.ctor", classObject.getName(), sig);
            }

            // Found the constructor, so try invoking it.
            return constructSpecific(cx, scope, args, ctors.methods[index]);
        } else {
            Scriptable topLevel = ScriptableObject.getTopLevelScope(this);
            String msg = "";
            try {
                // trying to construct an interface; use JavaAdapter to
                // construct a new class on the fly that implements this
                // interface.
                Object v = topLevel.get("JavaAdapter", topLevel);
                if (v != NOT_FOUND) {
                    Function f = (Function) v;
                    // Args are (interface, js object)
                    Object[] adapterArgs = { this, args[0] };
                    return f.construct(cx, topLevel, adapterArgs);
                }
            } catch (Exception ex) {
                // fall through to error
                String m = ex.getMessage();
                if (m != null)
                    msg = m;
            }
            throw Context.reportRuntimeError2(
                "msg.cant.instantiate", msg, classObject.getName());
        }
    }

    static Scriptable constructSpecific(Context cx, Scriptable scope,
                                        Object[] args, MemberBox ctor)
    {
        Scriptable topLevel = ScriptableObject.getTopLevelScope(scope);
        Class<?>[] argTypes = ctor.argTypes;

        if (ctor.vararg) {
            // marshall the explicit parameter
            Object[] newArgs = new Object[argTypes.length];
            for (int i = 0; i < argTypes.length-1; i++) {
                newArgs[i] = Context.jsToJava(args[i], argTypes[i]);
            }

            Object varArgs;

            // Handle special situation where a single variable parameter
            // is given and it is a Java or ECMA array.
            if (args.length == argTypes.length &&
                (args[args.length-1] == null ||
                 args[args.length-1] instanceof NativeArray ||
                 args[args.length-1] instanceof NativeJavaArray))
            {
                // convert the ECMA array into a native array
                varArgs = Context.jsToJava(args[args.length-1],
                                           argTypes[argTypes.length - 1]);
            } else {
                // marshall the variable parameter
                Class<?> componentType = argTypes[argTypes.length - 1].
                                        getComponentType();
                varArgs = Array.newInstance(componentType,
                                            args.length - argTypes.length + 1);
                for (int i=0; i < Array.getLength(varArgs); i++) {
                    Object value = Context.jsToJava(args[argTypes.length-1 + i],
                                                    componentType);
                    Array.set(varArgs, i, value);
                }
            }

            // add varargs
            newArgs[argTypes.length-1] = varArgs;
            // replace the original args with the new one
            args = newArgs;
        } else {
            Object[] origArgs = args;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                Object x = Context.jsToJava(arg, argTypes[i]);
                if (x != arg) {
                    if (args == origArgs) {
                        args = origArgs.clone();
                    }
                    args[i] = x;
                }
            }
        }

        Object instance = ctor.newInstance(args);
        // we need to force this to be wrapped, because construct _has_
        // to return a scriptable
        return cx.getWrapFactory().wrapNewObject(cx, topLevel, instance);
    }

    @Override
    public String toString() {
        return "[JavaClass " + getClassObject().getName() + "]";
    }

    /**
     * Determines if prototype is a wrapped Java object and performs
     * a Java "instanceof".
     * Exception: if value is an instance of NativeJavaClass, it isn't
     * considered an instance of the Java class; this forestalls any
     * name conflicts between java.lang.Class's methods and the
     * static methods exposed by a JavaNativeClass.
     */
    @Override
    public boolean hasInstance(Scriptable value) {

        if (value instanceof Wrapper &&
            !(value instanceof NativeJavaClass)) {
            Object instance = ((Wrapper)value).unwrap();

            return getClassObject().isInstance(instance);
        }

        // value wasn't something we understand
        return false;
    }

    private static Class<?> findNestedClass(Class<?> parentClass, String name) {
        String nestedClassName = parentClass.getName() + '$' + name;
        ClassLoader loader = parentClass.getClassLoader();
        if (loader == null) {
            // ALERT: if loader is null, nested class should be loaded
            // via system class loader which can be different from the
            // loader that brought Rhino classes that Class.forName() would
            // use, but ClassLoader.getSystemClassLoader() is Java 2 only
            return Kit.classOrNull(nestedClassName);
        } else {
            return Kit.classOrNull(loader, nestedClassName);
        }
    }

    private Map<String,FieldAndMethods> staticFieldAndMethods;
}
