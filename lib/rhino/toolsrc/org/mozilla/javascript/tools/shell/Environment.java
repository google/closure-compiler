/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
        Environment.java

        Wraps java.lang.System properties.

        by Patrick C. Beard <beard@netscape.com>
 */

package org.mozilla.javascript.tools.shell;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.ScriptableObject;

import java.util.Map;

/**
 * Environment, intended to be instantiated at global scope, provides
 * a natural way to access System properties from JavaScript.
 *
 */
public class Environment extends ScriptableObject
{
    static final long serialVersionUID = -430727378460177065L;

    private Environment thePrototypeInstance = null;

    public static void defineClass(ScriptableObject scope) {
        try {
            ScriptableObject.defineClass(scope, Environment.class);
        } catch (Exception e) {
            throw new Error(e.getMessage());
        }
    }

    @Override
    public String getClassName() {
        return "Environment";
    }

    public Environment() {
        if (thePrototypeInstance == null)
            thePrototypeInstance = this;
    }

    public Environment(ScriptableObject scope) {
        setParentScope(scope);
        Object ctor = ScriptRuntime.getTopLevelProp(scope, "Environment");
        if (ctor != null && ctor instanceof Scriptable) {
            Scriptable s = (Scriptable) ctor;
            setPrototype((Scriptable) s.get("prototype", s));
        }
    }

    @Override
    public boolean has(String name, Scriptable start) {
        if (this == thePrototypeInstance)
            return super.has(name, start);

        return (System.getProperty(name) != null);
    }

    @Override
    public Object get(String name, Scriptable start) {
        if (this == thePrototypeInstance)
            return super.get(name, start);

        String result = System.getProperty(name);
        if (result != null)
            return ScriptRuntime.toObject(getParentScope(), result);
        else
            return Scriptable.NOT_FOUND;
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        if (this == thePrototypeInstance)
            super.put(name, start, value);
        else
            System.getProperties().put(name, ScriptRuntime.toString(value));
    }

    private Object[] collectIds() {
        Map<Object,Object> props = System.getProperties();
        return props.keySet().toArray();
    }

    @Override
    public Object[] getIds() {
        if (this == thePrototypeInstance)
            return super.getIds();
        return collectIds();
    }

    @Override
    public Object[] getAllIds() {
        if (this == thePrototypeInstance)
            return super.getAllIds();
        return collectIds();
    }
}
