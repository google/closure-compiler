/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * This class reflects Java packages into the JavaScript environment.  We
 * lazily reflect classes and subpackages, and use a caching/sharing
 * system to ensure that members reflected into one JavaPackage appear
 * in all other references to the same package (as with Packages.java.lang
 * and java.lang).
 *
 * @see NativeJavaArray
 * @see NativeJavaObject
 * @see NativeJavaClass
 */

public class NativeJavaTopPackage
    extends NativeJavaPackage implements Function, IdFunctionCall
{
    static final long serialVersionUID = -1455787259477709999L;

    // we know these are packages so we can skip the class check
    // note that this is ok even if the package isn't present.
    private static final String[][] commonPackages = {
            {"java", "lang", "reflect"},
            {"java", "io"},
            {"java", "math"},
            {"java", "net"},
            {"java", "util", "zip"},
            {"java", "text", "resources"},
            {"java", "applet"},
            {"javax", "swing"}
    };

    NativeJavaTopPackage(ClassLoader loader)
    {
        super(true, "", loader);
    }

    public Object call(Context cx, Scriptable scope, Scriptable thisObj,
                       Object[] args)
    {
        return construct(cx, scope, args);
    }

    public Scriptable construct(Context cx, Scriptable scope, Object[] args)
    {
        ClassLoader loader = null;
        if (args.length != 0) {
            Object arg = args[0];
            if (arg instanceof Wrapper) {
                arg = ((Wrapper)arg).unwrap();
            }
            if (arg instanceof ClassLoader) {
                loader = (ClassLoader)arg;
            }
        }
        if (loader == null) {
            Context.reportRuntimeError0("msg.not.classloader");
            return null;
        }
        NativeJavaPackage pkg = new NativeJavaPackage(true, "", loader);
        ScriptRuntime.setObjectProtoAndParent(pkg, scope);
        return pkg;
    }

    public static void init(Context cx, Scriptable scope, boolean sealed)
    {
        ClassLoader loader = cx.getApplicationClassLoader();
        final NativeJavaTopPackage top = new NativeJavaTopPackage(loader);
        top.setPrototype(getObjectPrototype(scope));
        top.setParentScope(scope);

        for (int i = 0; i != commonPackages.length; i++) {
            NativeJavaPackage parent = top;
            for (int j = 0; j != commonPackages[i].length; j++) {
                parent = parent.forcePackage(commonPackages[i][j], scope);
            }
        }

        // getClass implementation
        IdFunctionObject getClass = new IdFunctionObject(top, FTAG, Id_getClass,
                                                         "getClass", 1, scope);

        // We want to get a real alias, and not a distinct JavaPackage
        // with the same packageName, so that we share classes and top
        // that are underneath.
        String[] topNames = ScriptRuntime.getTopPackageNames();
        NativeJavaPackage[] topPackages = new NativeJavaPackage[topNames.length];
        for (int i=0; i < topNames.length; i++) {
            topPackages[i] = (NativeJavaPackage)top.get(topNames[i], top);
        }

        // It's safe to downcast here since initStandardObjects takes
        // a ScriptableObject.
        ScriptableObject global = (ScriptableObject) scope;

        if (sealed) {
            getClass.sealObject();
        }
        getClass.exportAsScopeProperty();
        global.defineProperty("Packages", top, ScriptableObject.DONTENUM);
        for (int i=0; i < topNames.length; i++) {
            global.defineProperty(topNames[i], topPackages[i],
                                  ScriptableObject.DONTENUM);
        }
    }

    public Object execIdCall(IdFunctionObject f, Context cx, Scriptable scope,
                             Scriptable thisObj, Object[] args)
    {
        if (f.hasTag(FTAG)) {
            if (f.methodId() == Id_getClass) {
                return js_getClass(cx, scope, args);
            }
        }
        throw f.unknown();
    }

    private Scriptable js_getClass(Context cx, Scriptable scope, Object[] args)
    {
        if (args.length > 0  && args[0] instanceof Wrapper) {
            Scriptable result = this;
            Class<?> cl = ((Wrapper) args[0]).unwrap().getClass();
            // Evaluate the class name by getting successive properties of
            // the string to find the appropriate NativeJavaClass object
            String name = cl.getName();
            int offset = 0;
            for (;;) {
                int index = name.indexOf('.', offset);
                String propName = index == -1
                                  ? name.substring(offset)
                                  : name.substring(offset, index);
                Object prop = result.get(propName, result);
                if (!(prop instanceof Scriptable))
                    break;  // fall through to error
                result = (Scriptable) prop;
                if (index == -1)
                    return result;
                offset = index+1;
            }
        }
        throw Context.reportRuntimeError0("msg.not.java.obj");
    }

    private static final Object FTAG = "JavaTopPackage";
    private static final int Id_getClass = 1;
}
