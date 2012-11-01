/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import org.mozilla.classfile.*;
import java.lang.reflect.*;
import java.io.*;
import java.security.*;
import java.util.*;

public final class JavaAdapter implements IdFunctionCall
{
    /**
     * Provides a key with which to distinguish previously generated
     * adapter classes stored in a hash table.
     */
    static class JavaAdapterSignature
    {
        Class<?> superClass;
        Class<?>[] interfaces;
        ObjToIntMap names;

        JavaAdapterSignature(Class<?> superClass, Class<?>[] interfaces,
                             ObjToIntMap names)
        {
            this.superClass = superClass;
            this.interfaces = interfaces;
            this.names = names;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof JavaAdapterSignature))
                return false;
            JavaAdapterSignature sig = (JavaAdapterSignature) obj;
            if (superClass != sig.superClass)
                return false;
            if (interfaces != sig.interfaces) {
                if (interfaces.length != sig.interfaces.length)
                    return false;
                for (int i=0; i < interfaces.length; i++)
                    if (interfaces[i] != sig.interfaces[i])
                        return false;
            }
            if (names.size() != sig.names.size())
                return false;
            ObjToIntMap.Iterator iter = new ObjToIntMap.Iterator(names);
            for (iter.start(); !iter.done(); iter.next()) {
                String name = (String)iter.getKey();
                int arity = iter.getValue();
                if (arity != sig.names.get(name, arity + 1))
                    return false;
            }
            return true;
        }

        @Override
        public int hashCode()
        {
            return (superClass.hashCode() + Arrays.hashCode(interfaces)) ^ names.size();
        }
    }

    public static void init(Context cx, Scriptable scope, boolean sealed)
    {
        JavaAdapter obj = new JavaAdapter();
        IdFunctionObject ctor = new IdFunctionObject(obj, FTAG, Id_JavaAdapter,
                                                     "JavaAdapter", 1, scope);
        ctor.markAsConstructor(null);
        if (sealed) {
            ctor.sealObject();
        }
        ctor.exportAsScopeProperty();
    }

    public Object execIdCall(IdFunctionObject f, Context cx, Scriptable scope,
                             Scriptable thisObj, Object[] args)
    {
        if (f.hasTag(FTAG)) {
            if (f.methodId() == Id_JavaAdapter) {
                return js_createAdapter(cx, scope, args);
            }
        }
        throw f.unknown();
    }

    public static Object convertResult(Object result, Class<?> c)
    {
        if (result == Undefined.instance &&
            (c != ScriptRuntime.ObjectClass &&
             c != ScriptRuntime.StringClass))
        {
            // Avoid an error for an undefined value; return null instead.
            return null;
        }
        return Context.jsToJava(result, c);
    }

    public static Scriptable createAdapterWrapper(Scriptable obj, Object adapter)
    {
        Scriptable scope = ScriptableObject.getTopLevelScope(obj);
        NativeJavaObject res = new NativeJavaObject(scope, adapter, null, true);
        res.setPrototype(obj);
        return res;
    }

    public static Object getAdapterSelf(Class<?> adapterClass, Object adapter)
        throws NoSuchFieldException, IllegalAccessException
    {
        Field self = adapterClass.getDeclaredField("self");
        return self.get(adapter);
    }

    static Object js_createAdapter(Context cx, Scriptable scope, Object[] args)
    {
        int N = args.length;
        if (N == 0) {
            throw ScriptRuntime.typeError0("msg.adapter.zero.args");
        }

        // Expected arguments:
        // Any number of NativeJavaClass objects representing the super-class
        // and/or interfaces to implement, followed by one NativeObject providing
        // the implementation, followed by any number of arguments to pass on
        // to the (super-class) constructor.

        int classCount;
        for (classCount = 0; classCount < N - 1; classCount++) {
            Object arg = args[classCount];
            // We explicitly test for NativeObject here since checking for
            // instanceof ScriptableObject or !(instanceof NativeJavaClass)
            // would fail for a Java class that isn't found in the class path
            // as NativeJavaPackage extends ScriptableObject.
            if (arg instanceof NativeObject) {
                break;
            }
            if (!(arg instanceof NativeJavaClass)) {
                throw ScriptRuntime.typeError2("msg.not.java.class.arg",
                                               String.valueOf(classCount),
                                               ScriptRuntime.toString(arg));
            }
        }
        Class<?> superClass = null;
        Class<?>[] intfs = new Class[classCount];
        int interfaceCount = 0;
        for (int i = 0; i < classCount; ++i) {
            Class<?> c = ((NativeJavaClass) args[i]).getClassObject();
            if (!c.isInterface()) {
                if (superClass != null) {
                    throw ScriptRuntime.typeError2("msg.only.one.super",
                              superClass.getName(), c.getName());
                }
                superClass = c;
            } else {
                intfs[interfaceCount++] = c;
            }
        }

        if (superClass == null) {
            superClass = ScriptRuntime.ObjectClass;
        }
        
        Class<?>[] interfaces = new Class[interfaceCount];
        System.arraycopy(intfs, 0, interfaces, 0, interfaceCount);
        // next argument is implementation, must be scriptable
        Scriptable obj = ScriptableObject.ensureScriptable(args[classCount]);

        Class<?> adapterClass = getAdapterClass(scope, superClass, interfaces, obj);
        Object adapter;
        
        int argsCount = N - classCount - 1;
        try {
            if (argsCount > 0) {
                // Arguments contain parameters for super-class constructor.
                // We use the generic Java method lookup logic to find and
                // invoke the right constructor.
                Object[] ctorArgs = new Object[argsCount + 2];
                ctorArgs[0] = obj;
                ctorArgs[1] = cx.getFactory();
                System.arraycopy(args, classCount + 1, ctorArgs, 2, argsCount);
                // TODO: cache class wrapper?
                NativeJavaClass classWrapper = new NativeJavaClass(scope,
                        adapterClass, true);
                NativeJavaMethod ctors = classWrapper.members.ctors;
                int index = ctors.findCachedFunction(cx, ctorArgs);
                if (index < 0) {
                    String sig = NativeJavaMethod.scriptSignature(args);
                    throw Context.reportRuntimeError2(
                            "msg.no.java.ctor", adapterClass.getName(), sig);
                }

                // Found the constructor, so try invoking it.
                adapter = NativeJavaClass.constructInternal(ctorArgs, ctors.methods[index]);
            } else {
                Class<?>[] ctorParms = {
                        ScriptRuntime.ScriptableClass,
                        ScriptRuntime.ContextFactoryClass
                };
                Object[] ctorArgs = { obj, cx.getFactory() };
                adapter = adapterClass.getConstructor(ctorParms).newInstance(ctorArgs);
            }

            Object self = getAdapterSelf(adapterClass, adapter);
            // Return unwrapped JavaAdapter if it implements Scriptable
            if (self instanceof Wrapper) {
                Object unwrapped = ((Wrapper) self).unwrap();
                if (unwrapped instanceof Scriptable) {
                    if (unwrapped instanceof ScriptableObject) {
                        ScriptRuntime.setObjectProtoAndParent(
                                (ScriptableObject)unwrapped, scope);
                    }
                    return unwrapped;
                }
            }
            return self;
        } catch (Exception ex) {
            throw Context.throwAsScriptRuntimeEx(ex);
        }
    }

    // Needed by NativeJavaObject serializer
    public static void writeAdapterObject(Object javaObject,
                                          ObjectOutputStream out)
        throws IOException
    {
        Class<?> cl = javaObject.getClass();
        out.writeObject(cl.getSuperclass().getName());

        Class<?>[] interfaces = cl.getInterfaces();
        String[] interfaceNames = new String[interfaces.length];

        for (int i=0; i < interfaces.length; i++)
            interfaceNames[i] = interfaces[i].getName();

        out.writeObject(interfaceNames);

        try {
            Object delegee = cl.getField("delegee").get(javaObject);
            out.writeObject(delegee);
            return;
        } catch (IllegalAccessException e) {
        } catch (NoSuchFieldException e) {
        }
        throw new IOException();
    }

    // Needed by NativeJavaObject de-serializer
    public static Object readAdapterObject(Scriptable self,
                                           ObjectInputStream in)
        throws IOException, ClassNotFoundException
    {
        ContextFactory factory;
        Context cx = Context.getCurrentContext();
        if (cx != null) {
            factory = cx.getFactory();
        } else {
            factory = null;
        }

        Class<?> superClass = Class.forName((String)in.readObject());

        String[] interfaceNames = (String[])in.readObject();
        Class<?>[] interfaces = new Class[interfaceNames.length];

        for (int i=0; i < interfaceNames.length; i++)
            interfaces[i] = Class.forName(interfaceNames[i]);

        Scriptable delegee = (Scriptable)in.readObject();

        Class<?> adapterClass = getAdapterClass(self, superClass, interfaces,
                                             delegee);

        Class<?>[] ctorParms = {
            ScriptRuntime.ContextFactoryClass,
            ScriptRuntime.ScriptableClass,
            ScriptRuntime.ScriptableClass
        };
        Object[] ctorArgs = { factory, delegee, self };
        try {
            return adapterClass.getConstructor(ctorParms).newInstance(ctorArgs);
        } catch(InstantiationException e) {
        } catch(IllegalAccessException e) {
        } catch(InvocationTargetException e) {
        } catch(NoSuchMethodException e) {
        }

        throw new ClassNotFoundException("adapter");
    }

    private static ObjToIntMap getObjectFunctionNames(Scriptable obj)
    {
        Object[] ids = ScriptableObject.getPropertyIds(obj);
        ObjToIntMap map = new ObjToIntMap(ids.length);
        for (int i = 0; i != ids.length; ++i) {
            if (!(ids[i] instanceof String))
                continue;
            String id = (String) ids[i];
            Object value = ScriptableObject.getProperty(obj, id);
            if (value instanceof Function) {
                Function f = (Function)value;
                int length = ScriptRuntime.toInt32(
                                 ScriptableObject.getProperty(f, "length"));
                if (length < 0) {
                    length = 0;
                }
                map.put(id, length);
            }
        }
        return map;
    }

    private static Class<?> getAdapterClass(Scriptable scope, Class<?> superClass,
                                            Class<?>[] interfaces, Scriptable obj)
    {
        ClassCache cache = ClassCache.get(scope);
        Map<JavaAdapterSignature,Class<?>> generated
            = cache.getInterfaceAdapterCacheMap();

        ObjToIntMap names = getObjectFunctionNames(obj);
        JavaAdapterSignature sig;
        sig = new JavaAdapterSignature(superClass, interfaces, names);
        Class<?> adapterClass = generated.get(sig);
        if (adapterClass == null) {
            String adapterName = "adapter" + cache.newClassSerialNumber();
            byte[] code = createAdapterCode(names, adapterName,
                                            superClass, interfaces, null);

            adapterClass = loadAdapterClass(adapterName, code);
            if (cache.isCachingEnabled()) {
                generated.put(sig, adapterClass);
            }
        }
        return adapterClass;
    }

    public static byte[] createAdapterCode(ObjToIntMap functionNames,
                                           String adapterName,
                                           Class<?> superClass,
                                           Class<?>[] interfaces,
                                           String scriptClassName)
    {
        ClassFileWriter cfw = new ClassFileWriter(adapterName,
                                                  superClass.getName(),
                                                  "<adapter>");
        cfw.addField("factory", "Lorg/mozilla/javascript/ContextFactory;",
                     (short) (ClassFileWriter.ACC_PUBLIC |
                              ClassFileWriter.ACC_FINAL));
        cfw.addField("delegee", "Lorg/mozilla/javascript/Scriptable;",
                     (short) (ClassFileWriter.ACC_PUBLIC |
                              ClassFileWriter.ACC_FINAL));
        cfw.addField("self", "Lorg/mozilla/javascript/Scriptable;",
                     (short) (ClassFileWriter.ACC_PUBLIC |
                              ClassFileWriter.ACC_FINAL));
        int interfacesCount = interfaces == null ? 0 : interfaces.length;
        for (int i=0; i < interfacesCount; i++) {
            if (interfaces[i] != null)
                cfw.addInterface(interfaces[i].getName());
        }

        String superName = superClass.getName().replace('.', '/');
        Constructor<?>[] ctors = superClass.getDeclaredConstructors();
        for (Constructor<?> ctor : ctors) {
            int mod = ctor.getModifiers();
            if (Modifier.isPublic(mod) || Modifier.isProtected(mod)) {
                generateCtor(cfw, adapterName, superName, ctor);
            }
        }
        generateSerialCtor(cfw, adapterName, superName);
        if (scriptClassName != null) {
            generateEmptyCtor(cfw, adapterName, superName, scriptClassName);
        }

        ObjToIntMap generatedOverrides = new ObjToIntMap();
        ObjToIntMap generatedMethods = new ObjToIntMap();

        // generate methods to satisfy all specified interfaces.
        for (int i = 0; i < interfacesCount; i++) {
            Method[] methods = interfaces[i].getMethods();
            for (int j = 0; j < methods.length; j++) {
                Method method = methods[j];
                int mods = method.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
                    continue;
                }
                String methodName = method.getName();
                Class<?>[] argTypes = method.getParameterTypes();
                if (!functionNames.has(methodName)) {
                    try {
                        superClass.getMethod(methodName, argTypes);
                        // The class we're extending implements this method and
                        // the JavaScript object doesn't have an override. See
                        // bug 61226.
                        continue;
                    } catch (NoSuchMethodException e) {
                        // Not implemented by superclass; fall through
                    }
                }
                // make sure to generate only one instance of a particular
                // method/signature.
                String methodSignature = getMethodSignature(method, argTypes);
                String methodKey = methodName + methodSignature;
                if (! generatedOverrides.has(methodKey)) {
                    generateMethod(cfw, adapterName, methodName, argTypes,
                                   method.getReturnType(), true);
                    generatedOverrides.put(methodKey, 0);
                    generatedMethods.put(methodName, 0);
                }
            }
        }

        // Now, go through the superclass's methods, checking for abstract
        // methods or additional methods to override.

        // generate any additional overrides that the object might contain.
        Method[] methods = getOverridableMethods(superClass);
        for (int j = 0; j < methods.length; j++) {
            Method method = methods[j];
            int mods = method.getModifiers();
            // if a method is marked abstract, must implement it or the
            // resulting class won't be instantiable. otherwise, if the object
            // has a property of the same name, then an override is intended.
            boolean isAbstractMethod = Modifier.isAbstract(mods);
            String methodName = method.getName();
            if (isAbstractMethod || functionNames.has(methodName)) {
                // make sure to generate only one instance of a particular
                // method/signature.
                Class<?>[] argTypes = method.getParameterTypes();
                String methodSignature = getMethodSignature(method, argTypes);
                String methodKey = methodName + methodSignature;
                if (! generatedOverrides.has(methodKey)) {
                    generateMethod(cfw, adapterName, methodName, argTypes,
                                   method.getReturnType(), true);
                    generatedOverrides.put(methodKey, 0);
                    generatedMethods.put(methodName, 0);

                    // if a method was overridden, generate a "super$method"
                    // which lets the delegate call the superclass' version.
                    if (!isAbstractMethod) {
                        generateSuper(cfw, adapterName, superName,
                                      methodName, methodSignature,
                                      argTypes, method.getReturnType());
                    }
                }
            }
        }

        // Generate Java methods for remaining properties that are not
        // overrides.
        ObjToIntMap.Iterator iter = new ObjToIntMap.Iterator(functionNames);
        for (iter.start(); !iter.done(); iter.next()) {
            String functionName = (String)iter.getKey();
            if (generatedMethods.has(functionName))
                continue;
            int length = iter.getValue();
            Class<?>[] parms = new Class[length];
            for (int k=0; k < length; k++)
                parms[k] = ScriptRuntime.ObjectClass;
            generateMethod(cfw, adapterName, functionName, parms,
                           ScriptRuntime.ObjectClass, false);
        }
        return cfw.toByteArray();
    }

    static Method[] getOverridableMethods(Class<?> clazz)
    {
        ArrayList<Method> list = new ArrayList<Method>();
        HashSet<String> skip = new HashSet<String>();
        // Check superclasses before interfaces so we always choose
        // implemented methods over abstract ones, even if a subclass
        // re-implements an interface already implemented in a superclass
        // (e.g. java.util.ArrayList)
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            appendOverridableMethods(c, list, skip);
        }
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Class<?> intf: c.getInterfaces())
                appendOverridableMethods(intf, list, skip);
        }
        return list.toArray(new Method[list.size()]);
    }

    private static void appendOverridableMethods(Class<?> c,
            ArrayList<Method> list, HashSet<String> skip)
    {
        Method[] methods = c.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            String methodKey = methods[i].getName() +
                getMethodSignature(methods[i],
                        methods[i].getParameterTypes());
            if (skip.contains(methodKey))
                continue; // skip this method
            int mods = methods[i].getModifiers();
            if (Modifier.isStatic(mods))
                continue;
            if (Modifier.isFinal(mods)) {
                // Make sure we don't add a final method to the list
                // of overridable methods.
                skip.add(methodKey);
                continue;
            }
            if (Modifier.isPublic(mods) || Modifier.isProtected(mods)) {
                list.add(methods[i]);
                skip.add(methodKey);
            }
        }
    }

    static Class<?> loadAdapterClass(String className, byte[] classBytes)
    {
        Object staticDomain;
        Class<?> domainClass = SecurityController.getStaticSecurityDomainClass();
        if(domainClass == CodeSource.class || domainClass == ProtectionDomain.class) {
            // use the calling script's security domain if available
            ProtectionDomain protectionDomain = SecurityUtilities.getScriptProtectionDomain();
            if (protectionDomain == null) {
                protectionDomain = JavaAdapter.class.getProtectionDomain();
            }
            if(domainClass == CodeSource.class) {
                staticDomain = protectionDomain == null ? null : protectionDomain.getCodeSource();
            }
            else {
                staticDomain = protectionDomain;
            }
        }
        else {
            staticDomain = null;
        }
        GeneratedClassLoader loader = SecurityController.createLoader(null,
                staticDomain);
        Class<?> result = loader.defineClass(className, classBytes);
        loader.linkClass(result);
        return result;
    }

    public static Function getFunction(Scriptable obj, String functionName)
    {
        Object x = ScriptableObject.getProperty(obj, functionName);
        if (x == Scriptable.NOT_FOUND) {
            // This method used to swallow the exception from calling
            // an undefined method. People have come to depend on this
            // somewhat dubious behavior. It allows people to avoid
            // implementing listener methods that they don't care about,
            // for instance.
            return null;
        }
        if (!(x instanceof Function))
            throw ScriptRuntime.notFunctionError(x, functionName);

        return (Function)x;
    }

    /**
     * Utility method which dynamically binds a Context to the current thread,
     * if none already exists.
     */
    public static Object callMethod(ContextFactory factory,
                                    final Scriptable thisObj,
                                    final Function f, final Object[] args,
                                    final long argsToWrap)
    {
        if (f == null) {
            // See comments in getFunction
            return null;
        }
        if (factory == null) {
            factory = ContextFactory.getGlobal();
        }

        final Scriptable scope = f.getParentScope();
        if (argsToWrap == 0) {
            return Context.call(factory, f, scope, thisObj, args);
        }

        Context cx = Context.getCurrentContext();
        if (cx != null) {
            return doCall(cx, scope, thisObj, f, args, argsToWrap);
        } else {
            return factory.call(new ContextAction() {
                public Object run(Context cx)
                {
                    return doCall(cx, scope, thisObj, f, args, argsToWrap);
                }
            });
        }
    }

    private static Object doCall(Context cx, Scriptable scope,
                                 Scriptable thisObj, Function f,
                                 Object[] args, long argsToWrap)
    {
        // Wrap the rest of objects
        for (int i = 0; i != args.length; ++i) {
            if (0 != (argsToWrap & (1 << i))) {
                Object arg = args[i];
                if (!(arg instanceof Scriptable)) {
                    args[i] = cx.getWrapFactory().wrap(cx, scope, arg,
                                                       null);
                }
            }
        }
        return f.call(cx, scope, thisObj, args);
    }

    public static Scriptable runScript(final Script script)
    {
        return (Scriptable)ContextFactory.getGlobal().call(
            new ContextAction() {
                public Object run(Context cx)
                {
                    ScriptableObject global = ScriptRuntime.getGlobal(cx);
                    script.exec(cx, global);
                    return global;
                }
            });
    }

    private static void generateCtor(ClassFileWriter cfw, String adapterName,
                                     String superName, Constructor<?> superCtor)
    {
        short locals = 3; // this + factory + delegee
        Class<?>[] parameters = superCtor.getParameterTypes();

        // Note that we swapped arguments in app-facing constructors to avoid
        // conflicting signatures with serial constructor defined below.
        if (parameters.length == 0) {
            cfw.startMethod("<init>",
                        "(Lorg/mozilla/javascript/Scriptable;"
                        +"Lorg/mozilla/javascript/ContextFactory;)V",
                        ClassFileWriter.ACC_PUBLIC);

            // Invoke base class constructor
            cfw.add(ByteCode.ALOAD_0);  // this
            cfw.addInvoke(ByteCode.INVOKESPECIAL, superName, "<init>", "()V");
        } else {
            StringBuilder sig = new StringBuilder(
                    "(Lorg/mozilla/javascript/Scriptable;"
                    +"Lorg/mozilla/javascript/ContextFactory;");
            int marker = sig.length(); // lets us reuse buffer for super signature
            for (Class<?> c : parameters) {
                appendTypeString(sig, c);
            }
            sig.append(")V");
            cfw.startMethod("<init>", sig.toString(), ClassFileWriter.ACC_PUBLIC);

            // Invoke base class constructor
            cfw.add(ByteCode.ALOAD_0);  // this
            short paramOffset = 3;
            for (Class<?> parameter : parameters) {
                paramOffset += generatePushParam(cfw, paramOffset, parameter);
            }
            locals = paramOffset;
            sig.delete(1, marker);
            cfw.addInvoke(ByteCode.INVOKESPECIAL, superName, "<init>", sig.toString());
        }

        // Save parameter in instance variable "delegee"
        cfw.add(ByteCode.ALOAD_0);  // this
        cfw.add(ByteCode.ALOAD_1);  // first arg: Scriptable delegee
        cfw.add(ByteCode.PUTFIELD, adapterName, "delegee",
                "Lorg/mozilla/javascript/Scriptable;");

        // Save parameter in instance variable "factory"
        cfw.add(ByteCode.ALOAD_0);  // this
        cfw.add(ByteCode.ALOAD_2);  // second arg: ContextFactory instance
        cfw.add(ByteCode.PUTFIELD, adapterName, "factory",
                "Lorg/mozilla/javascript/ContextFactory;");

        cfw.add(ByteCode.ALOAD_0);  // this for the following PUTFIELD for self
        // create a wrapper object to be used as "this" in method calls
        cfw.add(ByteCode.ALOAD_1);  // the Scriptable delegee
        cfw.add(ByteCode.ALOAD_0);  // this
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "org/mozilla/javascript/JavaAdapter",
                      "createAdapterWrapper",
                      "(Lorg/mozilla/javascript/Scriptable;"
                      +"Ljava/lang/Object;"
                      +")Lorg/mozilla/javascript/Scriptable;");
        cfw.add(ByteCode.PUTFIELD, adapterName, "self",
                "Lorg/mozilla/javascript/Scriptable;");

        cfw.add(ByteCode.RETURN);
        cfw.stopMethod(locals);
    }

    private static void generateSerialCtor(ClassFileWriter cfw,
                                           String adapterName,
                                           String superName)
    {
        cfw.startMethod("<init>",
                        "(Lorg/mozilla/javascript/ContextFactory;"
                        +"Lorg/mozilla/javascript/Scriptable;"
                        +"Lorg/mozilla/javascript/Scriptable;"
                        +")V",
                        ClassFileWriter.ACC_PUBLIC);

        // Invoke base class constructor
        cfw.add(ByteCode.ALOAD_0);  // this
        cfw.addInvoke(ByteCode.INVOKESPECIAL, superName, "<init>", "()V");

        // Save parameter in instance variable "factory"
        cfw.add(ByteCode.ALOAD_0);  // this
        cfw.add(ByteCode.ALOAD_1);  // first arg: ContextFactory instance
        cfw.add(ByteCode.PUTFIELD, adapterName, "factory",
                "Lorg/mozilla/javascript/ContextFactory;");

        // Save parameter in instance variable "delegee"
        cfw.add(ByteCode.ALOAD_0);  // this
        cfw.add(ByteCode.ALOAD_2);  // second arg: Scriptable delegee
        cfw.add(ByteCode.PUTFIELD, adapterName, "delegee",
                "Lorg/mozilla/javascript/Scriptable;");
        // save self
        cfw.add(ByteCode.ALOAD_0);  // this
        cfw.add(ByteCode.ALOAD_3);  // third arg: Scriptable self
        cfw.add(ByteCode.PUTFIELD, adapterName, "self",
                "Lorg/mozilla/javascript/Scriptable;");

        cfw.add(ByteCode.RETURN);
        cfw.stopMethod((short)4); // 4: this + factory + delegee + self
    }

    private static void generateEmptyCtor(ClassFileWriter cfw,
                                          String adapterName,
                                          String superName,
                                          String scriptClassName)
    {
        cfw.startMethod("<init>", "()V", ClassFileWriter.ACC_PUBLIC);

        // Invoke base class constructor
        cfw.add(ByteCode.ALOAD_0);  // this
        cfw.addInvoke(ByteCode.INVOKESPECIAL, superName, "<init>", "()V");

        // Set factory to null to use current global when necessary
        cfw.add(ByteCode.ALOAD_0);
        cfw.add(ByteCode.ACONST_NULL);
        cfw.add(ByteCode.PUTFIELD, adapterName, "factory",
                "Lorg/mozilla/javascript/ContextFactory;");

        // Load script class
        cfw.add(ByteCode.NEW, scriptClassName);
        cfw.add(ByteCode.DUP);
        cfw.addInvoke(ByteCode.INVOKESPECIAL, scriptClassName, "<init>", "()V");

        // Run script and save resulting scope
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "org/mozilla/javascript/JavaAdapter",
                      "runScript",
                      "(Lorg/mozilla/javascript/Script;"
                      +")Lorg/mozilla/javascript/Scriptable;");
        cfw.add(ByteCode.ASTORE_1);

        // Save the Scriptable in instance variable "delegee"
        cfw.add(ByteCode.ALOAD_0);  // this
        cfw.add(ByteCode.ALOAD_1);  // the Scriptable
        cfw.add(ByteCode.PUTFIELD, adapterName, "delegee",
                "Lorg/mozilla/javascript/Scriptable;");

        cfw.add(ByteCode.ALOAD_0);  // this for the following PUTFIELD for self
        // create a wrapper object to be used as "this" in method calls
        cfw.add(ByteCode.ALOAD_1);  // the Scriptable
        cfw.add(ByteCode.ALOAD_0);  // this
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "org/mozilla/javascript/JavaAdapter",
                      "createAdapterWrapper",
                      "(Lorg/mozilla/javascript/Scriptable;"
                      +"Ljava/lang/Object;"
                      +")Lorg/mozilla/javascript/Scriptable;");
        cfw.add(ByteCode.PUTFIELD, adapterName, "self",
                "Lorg/mozilla/javascript/Scriptable;");

        cfw.add(ByteCode.RETURN);
        cfw.stopMethod((short)2); // this + delegee
    }

    /**
     * Generates code to wrap Java arguments into Object[].
     * Non-primitive Java types are left as-is pending conversion
     * in the helper method. Leaves the array object on the top of the stack.
     */
    static void generatePushWrappedArgs(ClassFileWriter cfw,
                                        Class<?>[] argTypes,
                                        int arrayLength)
    {
        // push arguments
        cfw.addPush(arrayLength);
        cfw.add(ByteCode.ANEWARRAY, "java/lang/Object");
        int paramOffset = 1;
        for (int i = 0; i != argTypes.length; ++i) {
            cfw.add(ByteCode.DUP); // duplicate array reference
            cfw.addPush(i);
            paramOffset += generateWrapArg(cfw, paramOffset, argTypes[i]);
            cfw.add(ByteCode.AASTORE);
        }
    }

    /**
     * Generates code to wrap Java argument into Object.
     * Non-primitive Java types are left unconverted pending conversion
     * in the helper method. Leaves the wrapper object on the top of the stack.
     */
    private static int generateWrapArg(ClassFileWriter cfw, int paramOffset,
                                       Class<?> argType)
    {
        int size = 1;
        if (!argType.isPrimitive()) {
            cfw.add(ByteCode.ALOAD, paramOffset);

        } else if (argType == Boolean.TYPE) {
            // wrap boolean values with java.lang.Boolean.
            cfw.add(ByteCode.NEW, "java/lang/Boolean");
            cfw.add(ByteCode.DUP);
            cfw.add(ByteCode.ILOAD, paramOffset);
            cfw.addInvoke(ByteCode.INVOKESPECIAL, "java/lang/Boolean",
                          "<init>", "(Z)V");

        } else if (argType == Character.TYPE) {
            // Create a string of length 1 using the character parameter.
            cfw.add(ByteCode.ILOAD, paramOffset);
            cfw.addInvoke(ByteCode.INVOKESTATIC, "java/lang/String",
                          "valueOf", "(C)Ljava/lang/String;");

        } else {
            // convert all numeric values to java.lang.Double.
            cfw.add(ByteCode.NEW, "java/lang/Double");
            cfw.add(ByteCode.DUP);
            String typeName = argType.getName();
            switch (typeName.charAt(0)) {
            case 'b':
            case 's':
            case 'i':
                // load an int value, convert to double.
                cfw.add(ByteCode.ILOAD, paramOffset);
                cfw.add(ByteCode.I2D);
                break;
            case 'l':
                // load a long, convert to double.
                cfw.add(ByteCode.LLOAD, paramOffset);
                cfw.add(ByteCode.L2D);
                size = 2;
                break;
            case 'f':
                // load a float, convert to double.
                cfw.add(ByteCode.FLOAD, paramOffset);
                cfw.add(ByteCode.F2D);
                break;
            case 'd':
                cfw.add(ByteCode.DLOAD, paramOffset);
                size = 2;
                break;
            }
            cfw.addInvoke(ByteCode.INVOKESPECIAL, "java/lang/Double",
                          "<init>", "(D)V");
        }
        return size;
    }

    /**
     * Generates code to convert a wrapped value type to a primitive type.
     * Handles unwrapping java.lang.Boolean, and java.lang.Number types.
     * Generates the appropriate RETURN bytecode.
     */
    static void generateReturnResult(ClassFileWriter cfw, Class<?> retType,
                                     boolean callConvertResult)
    {
        // wrap boolean values with java.lang.Boolean, convert all other
        // primitive values to java.lang.Double.
        if (retType == Void.TYPE) {
            cfw.add(ByteCode.POP);
            cfw.add(ByteCode.RETURN);

        } else if (retType == Boolean.TYPE) {
            cfw.addInvoke(ByteCode.INVOKESTATIC,
                          "org/mozilla/javascript/Context",
                          "toBoolean", "(Ljava/lang/Object;)Z");
            cfw.add(ByteCode.IRETURN);

        } else if (retType == Character.TYPE) {
            // characters are represented as strings in JavaScript.
            // return the first character.
            // first convert the value to a string if possible.
            cfw.addInvoke(ByteCode.INVOKESTATIC,
                          "org/mozilla/javascript/Context",
                          "toString",
                          "(Ljava/lang/Object;)Ljava/lang/String;");
            cfw.add(ByteCode.ICONST_0);
            cfw.addInvoke(ByteCode.INVOKEVIRTUAL, "java/lang/String",
                          "charAt", "(I)C");
            cfw.add(ByteCode.IRETURN);

        } else if (retType.isPrimitive()) {
            cfw.addInvoke(ByteCode.INVOKESTATIC,
                          "org/mozilla/javascript/Context",
                          "toNumber", "(Ljava/lang/Object;)D");
            String typeName = retType.getName();
            switch (typeName.charAt(0)) {
            case 'b':
            case 's':
            case 'i':
                cfw.add(ByteCode.D2I);
                cfw.add(ByteCode.IRETURN);
                break;
            case 'l':
                cfw.add(ByteCode.D2L);
                cfw.add(ByteCode.LRETURN);
                break;
            case 'f':
                cfw.add(ByteCode.D2F);
                cfw.add(ByteCode.FRETURN);
                break;
            case 'd':
                cfw.add(ByteCode.DRETURN);
                break;
            default:
                throw new RuntimeException("Unexpected return type " +
                                           retType.toString());
            }

        } else {
            String retTypeStr = retType.getName();
            if (callConvertResult) {
                cfw.addLoadConstant(retTypeStr);
                cfw.addInvoke(ByteCode.INVOKESTATIC,
                              "java/lang/Class",
                              "forName",
                              "(Ljava/lang/String;)Ljava/lang/Class;");

                cfw.addInvoke(ByteCode.INVOKESTATIC,
                              "org/mozilla/javascript/JavaAdapter",
                              "convertResult",
                              "(Ljava/lang/Object;"
                              +"Ljava/lang/Class;"
                              +")Ljava/lang/Object;");
            }
            // Now cast to return type
            cfw.add(ByteCode.CHECKCAST, retTypeStr);
            cfw.add(ByteCode.ARETURN);
        }
    }

    private static void generateMethod(ClassFileWriter cfw, String genName,
                                       String methodName, Class<?>[] parms,
                                       Class<?> returnType, boolean convertResult)
    {
        StringBuilder sb = new StringBuilder();
        int paramsEnd = appendMethodSignature(parms, returnType, sb);
        String methodSignature = sb.toString();
        cfw.startMethod(methodName, methodSignature,
                        ClassFileWriter.ACC_PUBLIC);

        // Prepare stack to call method

        // push factory
        cfw.add(ByteCode.ALOAD_0);
        cfw.add(ByteCode.GETFIELD, genName, "factory",
                "Lorg/mozilla/javascript/ContextFactory;");

        // push self
        cfw.add(ByteCode.ALOAD_0);
        cfw.add(ByteCode.GETFIELD, genName, "self",
                "Lorg/mozilla/javascript/Scriptable;");

        // push function
        cfw.add(ByteCode.ALOAD_0);
        cfw.add(ByteCode.GETFIELD, genName, "delegee",
                "Lorg/mozilla/javascript/Scriptable;");
        cfw.addPush(methodName);
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "org/mozilla/javascript/JavaAdapter",
                      "getFunction",
                      "(Lorg/mozilla/javascript/Scriptable;"
                      +"Ljava/lang/String;"
                      +")Lorg/mozilla/javascript/Function;");

        // push arguments
        generatePushWrappedArgs(cfw, parms, parms.length);

        // push bits to indicate which parameters should be wrapped
        if (parms.length > 64) {
            // If it will be an issue, then passing a static boolean array
            // can be an option, but for now using simple bitmask
            throw Context.reportRuntimeError0(
                "JavaAdapter can not subclass methods with more then"
                +" 64 arguments.");
        }
        long convertionMask = 0;
        for (int i = 0; i != parms.length; ++i) {
            if (!parms[i].isPrimitive()) {
                convertionMask |= (1 << i);
            }
        }
        cfw.addPush(convertionMask);

        // go through utility method, which creates a Context to run the
        // method in.
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "org/mozilla/javascript/JavaAdapter",
                      "callMethod",
                      "(Lorg/mozilla/javascript/ContextFactory;"
                      +"Lorg/mozilla/javascript/Scriptable;"
                      +"Lorg/mozilla/javascript/Function;"
                      +"[Ljava/lang/Object;"
                      +"J"
                      +")Ljava/lang/Object;");

        generateReturnResult(cfw, returnType, convertResult);

        cfw.stopMethod((short)paramsEnd);
    }

    /**
     * Generates code to push typed parameters onto the operand stack
     * prior to a direct Java method call.
     */
    private static int generatePushParam(ClassFileWriter cfw, int paramOffset,
                                         Class<?> paramType)
    {
        if (!paramType.isPrimitive()) {
            cfw.addALoad(paramOffset);
            return 1;
        }
        String typeName = paramType.getName();
        switch (typeName.charAt(0)) {
        case 'z':
        case 'b':
        case 'c':
        case 's':
        case 'i':
            // load an int value, convert to double.
            cfw.addILoad(paramOffset);
            return 1;
        case 'l':
            // load a long, convert to double.
            cfw.addLLoad(paramOffset);
            return 2;
        case 'f':
            // load a float, convert to double.
            cfw.addFLoad(paramOffset);
            return 1;
        case 'd':
            cfw.addDLoad(paramOffset);
            return 2;
        }
        throw Kit.codeBug();
    }

    /**
     * Generates code to return a Java type, after calling a Java method
     * that returns the same type.
     * Generates the appropriate RETURN bytecode.
     */
    private static void generatePopResult(ClassFileWriter cfw,
                                          Class<?> retType)
    {
        if (retType.isPrimitive()) {
            String typeName = retType.getName();
            switch (typeName.charAt(0)) {
            case 'b':
            case 'c':
            case 's':
            case 'i':
            case 'z':
                cfw.add(ByteCode.IRETURN);
                break;
            case 'l':
                cfw.add(ByteCode.LRETURN);
                break;
            case 'f':
                cfw.add(ByteCode.FRETURN);
                break;
            case 'd':
                cfw.add(ByteCode.DRETURN);
                break;
            }
        } else {
            cfw.add(ByteCode.ARETURN);
        }
    }

    /**
     * Generates a method called "super$methodName()" which can be called
     * from JavaScript that is equivalent to calling "super.methodName()"
     * from Java. Eventually, this may be supported directly in JavaScript.
     */
    private static void generateSuper(ClassFileWriter cfw,
                                      String genName, String superName,
                                      String methodName, String methodSignature,
                                      Class<?>[] parms, Class<?> returnType)
    {
        cfw.startMethod("super$" + methodName, methodSignature,
                        ClassFileWriter.ACC_PUBLIC);

        // push "this"
        cfw.add(ByteCode.ALOAD, 0);

        // push the rest of the parameters.
        int paramOffset = 1;
        for (Class<?> parm : parms) {
            paramOffset += generatePushParam(cfw, paramOffset, parm);
        }

        // call the superclass implementation of the method.
        cfw.addInvoke(ByteCode.INVOKESPECIAL,
                      superName,
                      methodName,
                      methodSignature);

        // now, handle the return type appropriately.
        Class<?> retType = returnType;
        if (!retType.equals(Void.TYPE)) {
            generatePopResult(cfw, retType);
        } else {
            cfw.add(ByteCode.RETURN);
        }
        cfw.stopMethod((short)(paramOffset + 1));
    }

    /**
     * Returns a fully qualified method name concatenated with its signature.
     */
    private static String getMethodSignature(Method method, Class<?>[] argTypes)
    {
        StringBuilder sb = new StringBuilder();
        appendMethodSignature(argTypes, method.getReturnType(), sb);
        return sb.toString();
    }

    static int appendMethodSignature(Class<?>[] argTypes,
                                     Class<?> returnType,
                                     StringBuilder sb)
    {
        sb.append('(');
        int firstLocal = 1 + argTypes.length; // includes this.
        for (Class<?> type : argTypes) {
            appendTypeString(sb, type);
            if (type == Long.TYPE || type == Double.TYPE) {
                // adjust for double slot
                ++firstLocal;
            }
        }
        sb.append(')');
        appendTypeString(sb, returnType);
        return firstLocal;
    }

    private static StringBuilder appendTypeString(StringBuilder sb, Class<?> type)
    {
        while (type.isArray()) {
            sb.append('[');
            type = type.getComponentType();
        }
        if (type.isPrimitive()) {
            char typeLetter;
            if (type == Boolean.TYPE) {
                typeLetter = 'Z';
            } else if (type == Long.TYPE) {
                typeLetter = 'J';
            } else {
                String typeName = type.getName();
                typeLetter = Character.toUpperCase(typeName.charAt(0));
            }
            sb.append(typeLetter);
        } else {
            sb.append('L');
            sb.append(type.getName().replace('.', '/'));
            sb.append(';');
        }
        return sb;
    }

    static int[] getArgsToConvert(Class<?>[] argTypes)
    {
        int count = 0;
        for (int i = 0; i != argTypes.length; ++i) {
            if (!argTypes[i].isPrimitive())
                ++count;
        }
        if (count == 0)
            return null;
        int[] array = new int[count];
        count = 0;
        for (int i = 0; i != argTypes.length; ++i) {
            if (!argTypes[i].isPrimitive())
                array[count++] = i;
        }
        return array;
    }

    private static final Object FTAG = "JavaAdapter";
    private static final int Id_JavaAdapter = 1;
}
