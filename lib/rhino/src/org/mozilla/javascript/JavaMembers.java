/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.lang.reflect.*;
import java.util.*;

import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;

/**
 *
 * @see NativeJavaObject
 * @see NativeJavaClass
 */
class JavaMembers
{
    JavaMembers(Scriptable scope, Class<?> cl)
    {
        this(scope, cl, false);
    }

    JavaMembers(Scriptable scope, Class<?> cl, boolean includeProtected)
    {
        try {
            Context cx = ContextFactory.getGlobal().enterContext();
            ClassShutter shutter = cx.getClassShutter();
            if (shutter != null && !shutter.visibleToScripts(cl.getName())) {
                throw Context.reportRuntimeError1("msg.access.prohibited",
                                                  cl.getName());
            }
            this.members = new HashMap<String,Object>();
            this.staticMembers = new HashMap<String,Object>();
            this.cl = cl;
            boolean includePrivate = cx.hasFeature(
                    Context.FEATURE_ENHANCED_JAVA_ACCESS);
            reflect(scope, includeProtected, includePrivate);
        } finally {
            Context.exit();
        }
    }

    boolean has(String name, boolean isStatic)
    {
        Map<String,Object> ht = isStatic ? staticMembers : members;
        Object obj = ht.get(name);
        if (obj != null) {
            return true;
        }
        return findExplicitFunction(name, isStatic) != null;
    }

    Object get(Scriptable scope, String name, Object javaObject,
               boolean isStatic)
    {
        Map<String,Object> ht = isStatic ? staticMembers : members;
        Object member = ht.get(name);
        if (!isStatic && member == null) {
            // Try to get static member from instance (LC3)
            member = staticMembers.get(name);
        }
        if (member == null) {
            member = this.getExplicitFunction(scope, name,
                                              javaObject, isStatic);
            if (member == null)
                return Scriptable.NOT_FOUND;
        }
        if (member instanceof Scriptable) {
            return member;
        }
        Context cx = Context.getContext();
        Object rval;
        Class<?> type;
        try {
            if (member instanceof BeanProperty) {
                BeanProperty bp = (BeanProperty) member;
                if (bp.getter == null)
                    return Scriptable.NOT_FOUND;
                rval = bp.getter.invoke(javaObject, Context.emptyArgs);
                type = bp.getter.method().getReturnType();
            } else {
                Field field = (Field) member;
                rval = field.get(isStatic ? null : javaObject);
                type = field.getType();
            }
        } catch (Exception ex) {
            throw Context.throwAsScriptRuntimeEx(ex);
        }
        // Need to wrap the object before we return it.
        scope = ScriptableObject.getTopLevelScope(scope);
        return cx.getWrapFactory().wrap(cx, scope, rval, type);
    }

    void put(Scriptable scope, String name, Object javaObject,
             Object value, boolean isStatic)
    {
        Map<String,Object> ht = isStatic ? staticMembers : members;
        Object member = ht.get(name);
        if (!isStatic && member == null) {
            // Try to get static member from instance (LC3)
            member = staticMembers.get(name);
        }
        if (member == null)
            throw reportMemberNotFound(name);
        if (member instanceof FieldAndMethods) {
            FieldAndMethods fam = (FieldAndMethods) ht.get(name);
            member = fam.field;
        }

        // Is this a bean property "set"?
        if (member instanceof BeanProperty) {
            BeanProperty bp = (BeanProperty)member;
            if (bp.setter == null) {
                throw reportMemberNotFound(name);
            }
            // If there's only one setter or if the value is null, use the
            // main setter. Otherwise, let the NativeJavaMethod decide which
            // setter to use:
            if (bp.setters == null || value == null) {
                Class<?> setType = bp.setter.argTypes[0];
                Object[] args = { Context.jsToJava(value, setType) };
                try {
                    bp.setter.invoke(javaObject, args);
                } catch (Exception ex) {
                  throw Context.throwAsScriptRuntimeEx(ex);
                }
            } else {
                Object[] args = { value };
                bp.setters.call(Context.getContext(),
                                ScriptableObject.getTopLevelScope(scope),
                                scope, args);
            }
        }
        else {
            if (!(member instanceof Field)) {
                String str = (member == null) ? "msg.java.internal.private"
                                              : "msg.java.method.assign";
                throw Context.reportRuntimeError1(str, name);
            }
            Field field = (Field)member;
            Object javaValue = Context.jsToJava(value, field.getType());
            try {
                field.set(javaObject, javaValue);
            } catch (IllegalAccessException accessEx) {
                if ((field.getModifiers() & Modifier.FINAL) != 0) {
                    // treat Java final the same as JavaScript [[READONLY]]
                    return;
                }
                throw Context.throwAsScriptRuntimeEx(accessEx);
            } catch (IllegalArgumentException argEx) {
                throw Context.reportRuntimeError3(
                    "msg.java.internal.field.type",
                    value.getClass().getName(), field,
                    javaObject.getClass().getName());
            }
        }
    }

    Object[] getIds(boolean isStatic)
    {
        Map<String,Object> map = isStatic ? staticMembers : members;
        return map.keySet().toArray(new Object[map.size()]);
    }

    static String javaSignature(Class<?> type)
    {
        if (!type.isArray()) {
            return type.getName();
        } else {
            int arrayDimension = 0;
            do {
                ++arrayDimension;
                type = type.getComponentType();
            } while (type.isArray());
            String name = type.getName();
            String suffix = "[]";
            if (arrayDimension == 1) {
                return name.concat(suffix);
            } else {
                int length = name.length() + arrayDimension * suffix.length();
                StringBuilder sb = new StringBuilder(length);
                sb.append(name);
                while (arrayDimension != 0) {
                    --arrayDimension;
                    sb.append(suffix);
                }
                return sb.toString();
            }
        }
    }

    static String liveConnectSignature(Class<?>[] argTypes)
    {
        int N = argTypes.length;
        if (N == 0) { return "()"; }
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (int i = 0; i != N; ++i) {
            if (i != 0) {
                sb.append(',');
            }
            sb.append(javaSignature(argTypes[i]));
        }
        sb.append(')');
        return sb.toString();
    }

    private MemberBox findExplicitFunction(String name, boolean isStatic)
    {
        int sigStart = name.indexOf('(');
        if (sigStart < 0) { return null; }

        Map<String,Object> ht = isStatic ? staticMembers : members;
        MemberBox[] methodsOrCtors = null;
        boolean isCtor = (isStatic && sigStart == 0);

        if (isCtor) {
            // Explicit request for an overloaded constructor
            methodsOrCtors = ctors.methods;
        } else {
            // Explicit request for an overloaded method
            String trueName = name.substring(0,sigStart);
            Object obj = ht.get(trueName);
            if (!isStatic && obj == null) {
                // Try to get static member from instance (LC3)
                obj = staticMembers.get(trueName);
            }
            if (obj instanceof NativeJavaMethod) {
                NativeJavaMethod njm = (NativeJavaMethod)obj;
                methodsOrCtors = njm.methods;
            }
        }

        if (methodsOrCtors != null) {
            for (MemberBox methodsOrCtor : methodsOrCtors) {
                Class<?>[] type = methodsOrCtor.argTypes;
                String sig = liveConnectSignature(type);
                if (sigStart + sig.length() == name.length()
                        && name.regionMatches(sigStart, sig, 0, sig.length()))
                {
                    return methodsOrCtor;
                }
            }
        }

        return null;
    }

    private Object getExplicitFunction(Scriptable scope, String name,
                                       Object javaObject, boolean isStatic)
    {
        Map<String,Object> ht = isStatic ? staticMembers : members;
        Object member = null;
        MemberBox methodOrCtor = findExplicitFunction(name, isStatic);

        if (methodOrCtor != null) {
            Scriptable prototype =
                ScriptableObject.getFunctionPrototype(scope);

            if (methodOrCtor.isCtor()) {
                NativeJavaConstructor fun =
                    new NativeJavaConstructor(methodOrCtor);
                fun.setPrototype(prototype);
                member = fun;
                ht.put(name, fun);
            } else {
                String trueName = methodOrCtor.getName();
                member = ht.get(trueName);

                if (member instanceof NativeJavaMethod &&
                    ((NativeJavaMethod)member).methods.length > 1 ) {
                    NativeJavaMethod fun =
                        new NativeJavaMethod(methodOrCtor, name);
                    fun.setPrototype(prototype);
                    ht.put(name, fun);
                    member = fun;
                }
            }
        }

        return member;
    }

    /**
     * Retrieves mapping of methods to accessible methods for a class.
     * In case the class is not public, retrieves methods with same
     * signature as its public methods from public superclasses and
     * interfaces (if they exist). Basically upcasts every method to the
     * nearest accessible method.
     */
    private static Method[] discoverAccessibleMethods(Class<?> clazz,
                                                      boolean includeProtected,
                                                      boolean includePrivate)
    {
        Map<MethodSignature,Method> map = new HashMap<MethodSignature,Method>();
        discoverAccessibleMethods(clazz, map, includeProtected, includePrivate);
        return map.values().toArray(new Method[map.size()]);
    }

    private static void discoverAccessibleMethods(Class<?> clazz,
            Map<MethodSignature,Method> map, boolean includeProtected,
            boolean includePrivate)
    {
        if (isPublic(clazz.getModifiers()) || includePrivate) {
            try {
                if (includeProtected || includePrivate) {
                    while (clazz != null) {
                        try {
                            Method[] methods = clazz.getDeclaredMethods();
                            for (Method method : methods) {
                                int mods = method.getModifiers();

                                if (isPublic(mods)
                                        || isProtected(mods)
                                        || includePrivate) {
                                    MethodSignature sig = new MethodSignature(method);
                                    if (!map.containsKey(sig)) {
                                        if (includePrivate && !method.isAccessible())
                                            method.setAccessible(true);
                                        map.put(sig, method);
                                    }
                                }
                            }
                            clazz = clazz.getSuperclass();
                        } catch (SecurityException e) {
                            // Some security settings (i.e., applets) disallow
                            // access to Class.getDeclaredMethods. Fall back to
                            // Class.getMethods.
                            Method[] methods = clazz.getMethods();
                            for (Method method : methods) {
                                MethodSignature sig = new MethodSignature(method);
                                if (!map.containsKey(sig))
                                    map.put(sig, method);
                            }
                            break; // getMethods gets superclass methods, no
                                   // need to loop any more
                        }
                    }
                } else {
                    Method[] methods = clazz.getMethods();
                    for (Method method : methods) {
                        MethodSignature sig = new MethodSignature(method);
                        // Array may contain methods with same signature but different return value!
                        if (!map.containsKey(sig))
                            map.put(sig, method);
                    }
                }
                return;
            } catch (SecurityException e) {
                Context.reportWarning(
                        "Could not discover accessible methods of class " +
                            clazz.getName() + " due to lack of privileges, " +
                            "attemping superclasses/interfaces.");
                // Fall through and attempt to discover superclass/interface
                // methods
            }
        }

        Class<?>[] interfaces = clazz.getInterfaces();
        for (Class<?> intface : interfaces) {
            discoverAccessibleMethods(intface, map, includeProtected,
                    includePrivate);
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            discoverAccessibleMethods(superclass, map, includeProtected,
                    includePrivate);
        }
    }

    private static final class MethodSignature
    {
        private final String name;
        private final Class<?>[] args;

        private MethodSignature(String name, Class<?>[] args)
        {
            this.name = name;
            this.args = args;
        }

        MethodSignature(Method method)
        {
            this(method.getName(), method.getParameterTypes());
        }

        @Override
        public boolean equals(Object o)
        {
            if(o instanceof MethodSignature)
            {
                MethodSignature ms = (MethodSignature)o;
                return ms.name.equals(name) && Arrays.equals(args, ms.args);
            }
            return false;
        }

        @Override
        public int hashCode()
        {
            return name.hashCode() ^ args.length;
        }
    }

    private void reflect(Scriptable scope,
                         boolean includeProtected,
                         boolean includePrivate)
    {
        // We reflect methods first, because we want overloaded field/method
        // names to be allocated to the NativeJavaMethod before the field
        // gets in the way.

        Method[] methods = discoverAccessibleMethods(cl, includeProtected,
                                                     includePrivate);
        for (Method method : methods) {
            int mods = method.getModifiers();
            boolean isStatic = Modifier.isStatic(mods);
            Map<String,Object> ht = isStatic ? staticMembers : members;
            String name = method.getName();
            Object value = ht.get(name);
            if (value == null) {
                ht.put(name, method);
            } else {
                ObjArray overloadedMethods;
                if (value instanceof ObjArray) {
                    overloadedMethods = (ObjArray)value;
                } else {
                    if (!(value instanceof Method)) Kit.codeBug();
                    // value should be instance of Method as at this stage
                    // staticMembers and members can only contain methods
                    overloadedMethods = new ObjArray();
                    overloadedMethods.add(value);
                    ht.put(name, overloadedMethods);
                }
                overloadedMethods.add(method);
            }
        }

        // replace Method instances by wrapped NativeJavaMethod objects
        // first in staticMembers and then in members
        for (int tableCursor = 0; tableCursor != 2; ++tableCursor) {
            boolean isStatic = (tableCursor == 0);
            Map<String,Object> ht = isStatic ? staticMembers : members;
            for (Map.Entry<String, Object> entry: ht.entrySet()) {
                MemberBox[] methodBoxes;
                Object value = entry.getValue();
                if (value instanceof Method) {
                    methodBoxes = new MemberBox[1];
                    methodBoxes[0] = new MemberBox((Method)value);
                } else {
                    ObjArray overloadedMethods = (ObjArray)value;
                    int N = overloadedMethods.size();
                    if (N < 2) Kit.codeBug();
                    methodBoxes = new MemberBox[N];
                    for (int i = 0; i != N; ++i) {
                        Method method = (Method)overloadedMethods.get(i);
                        methodBoxes[i] = new MemberBox(method);
                    }
                }
                NativeJavaMethod fun = new NativeJavaMethod(methodBoxes);
                if (scope != null) {
                    ScriptRuntime.setFunctionProtoAndParent(fun, scope);
                }
                ht.put(entry.getKey(), fun);
            }
        }

        // Reflect fields.
        Field[] fields = getAccessibleFields(includeProtected, includePrivate);
        for (Field field : fields) {
            String name = field.getName();
            int mods = field.getModifiers();
            try {
                boolean isStatic = Modifier.isStatic(mods);
                Map<String,Object> ht = isStatic ? staticMembers : members;
                Object member = ht.get(name);
                if (member == null) {
                    ht.put(name, field);
                } else if (member instanceof NativeJavaMethod) {
                    NativeJavaMethod method = (NativeJavaMethod) member;
                    FieldAndMethods fam
                        = new FieldAndMethods(scope, method.methods, field);
                    Map<String,FieldAndMethods> fmht = isStatic ? staticFieldAndMethods
                                              : fieldAndMethods;
                    if (fmht == null) {
                        fmht = new HashMap<String,FieldAndMethods>();
                        if (isStatic) {
                            staticFieldAndMethods = fmht;
                        } else {
                            fieldAndMethods = fmht;
                        }
                    }
                    fmht.put(name, fam);
                    ht.put(name, fam);
                } else if (member instanceof Field) {
                    Field oldField = (Field) member;
                    // If this newly reflected field shadows an inherited field,
                    // then replace it. Otherwise, since access to the field
                    // would be ambiguous from Java, no field should be
                    // reflected.
                    // For now, the first field found wins, unless another field
                    // explicitly shadows it.
                    if (oldField.getDeclaringClass().
                            isAssignableFrom(field.getDeclaringClass()))
                    {
                        ht.put(name, field);
                    }
                } else {
                    // "unknown member type"
                    Kit.codeBug();
                }
            } catch (SecurityException e) {
                // skip this field
                Context.reportWarning("Could not access field "
                        + name + " of class " + cl.getName() +
                        " due to lack of privileges.");
            }
        }

        // Create bean properties from corresponding get/set methods first for
        // static members and then for instance members
        for (int tableCursor = 0; tableCursor != 2; ++tableCursor) {
            boolean isStatic = (tableCursor == 0);
            Map<String,Object> ht = isStatic ? staticMembers : members;

            Map<String,BeanProperty> toAdd = new HashMap<String,BeanProperty>();

            // Now, For each member, make "bean" properties.
            for (String name: ht.keySet()) {
                // Is this a getter?
                boolean memberIsGetMethod = name.startsWith("get");
                boolean memberIsSetMethod = name.startsWith("set");
                boolean memberIsIsMethod = name.startsWith("is");
                if (memberIsGetMethod || memberIsIsMethod
                        || memberIsSetMethod) {
                    // Double check name component.
                    String nameComponent
                        = name.substring(memberIsIsMethod ? 2 : 3);
                    if (nameComponent.length() == 0)
                        continue;

                    // Make the bean property name.
                    String beanPropertyName = nameComponent;
                    char ch0 = nameComponent.charAt(0);
                    if (Character.isUpperCase(ch0)) {
                        if (nameComponent.length() == 1) {
                            beanPropertyName = nameComponent.toLowerCase();
                        } else {
                            char ch1 = nameComponent.charAt(1);
                            if (!Character.isUpperCase(ch1)) {
                                beanPropertyName = Character.toLowerCase(ch0)
                                                   +nameComponent.substring(1);
                            }
                        }
                    }

                    // If we already have a member by this name, don't do this
                    // property.
                    if (toAdd.containsKey(beanPropertyName))
                        continue;
                    Object v = ht.get(beanPropertyName);
                    if (v != null) {
                        // A private field shouldn't mask a public getter/setter
                        if (!includePrivate || !(v instanceof Member) ||
                            !Modifier.isPrivate(((Member)v).getModifiers()))

                        {
                            continue;
                        }
                    }

                    // Find the getter method, or if there is none, the is-
                    // method.
                    MemberBox getter = null;
                    getter = findGetter(isStatic, ht, "get", nameComponent);
                    // If there was no valid getter, check for an is- method.
                    if (getter == null) {
                        getter = findGetter(isStatic, ht, "is", nameComponent);
                    }

                    // setter
                    MemberBox setter = null;
                    NativeJavaMethod setters = null;
                    String setterName = "set".concat(nameComponent);

                    if (ht.containsKey(setterName)) {
                        // Is this value a method?
                        Object member = ht.get(setterName);
                        if (member instanceof NativeJavaMethod) {
                            NativeJavaMethod njmSet = (NativeJavaMethod)member;
                            if (getter != null) {
                                // We have a getter. Now, do we have a matching
                                // setter?
                                Class<?> type = getter.method().getReturnType();
                                setter = extractSetMethod(type, njmSet.methods,
                                                            isStatic);
                            } else {
                                // No getter, find any set method
                                setter = extractSetMethod(njmSet.methods,
                                                            isStatic);
                            }
                            if (njmSet.methods.length > 1) {
                                setters = njmSet;
                            }
                        }
                    }
                    // Make the property.
                    BeanProperty bp = new BeanProperty(getter, setter,
                                                       setters);
                    toAdd.put(beanPropertyName, bp);
                }
            }

            // Add the new bean properties.
            for (String key: toAdd.keySet()) {
                Object value = toAdd.get(key);
                ht.put(key, value);
            }
        }

        // Reflect constructors
        Constructor<?>[] constructors = getAccessibleConstructors(includePrivate);
        MemberBox[] ctorMembers = new MemberBox[constructors.length];
        for (int i = 0; i != constructors.length; ++i) {
            ctorMembers[i] = new MemberBox(constructors[i]);
        }
        ctors = new NativeJavaMethod(ctorMembers, cl.getSimpleName());
    }

    private Constructor<?>[] getAccessibleConstructors(boolean includePrivate)
    {
      // The JVM currently doesn't allow changing access on java.lang.Class
      // constructors, so don't try
      if (includePrivate && cl != ScriptRuntime.ClassClass) {
          try {
              Constructor<?>[] cons = cl.getDeclaredConstructors();
              AccessibleObject.setAccessible(cons, true);

              return cons;
          } catch (SecurityException e) {
              // Fall through to !includePrivate case
              Context.reportWarning("Could not access constructor " +
                    " of class " + cl.getName() +
                    " due to lack of privileges.");
          }
      }
      return cl.getConstructors();
    }

    private Field[] getAccessibleFields(boolean includeProtected,
                                        boolean includePrivate) {
        if (includePrivate || includeProtected) {
            try {
                List<Field> fieldsList = new ArrayList<Field>();
                Class<?> currentClass = cl;

                while (currentClass != null) {
                    // get all declared fields in this class, make them
                    // accessible, and save
                    Field[] declared = currentClass.getDeclaredFields();
                    for (Field field : declared) {
                        int mod = field.getModifiers();
                        if (includePrivate || isPublic(mod) || isProtected(mod)) {
                            if (!field.isAccessible())
                                field.setAccessible(true);
                            fieldsList.add(field);
                        }
                    }
                    // walk up superclass chain.  no need to deal specially with
                    // interfaces, since they can't have fields
                    currentClass = currentClass.getSuperclass();
                }

                return fieldsList.toArray(new Field[fieldsList.size()]);
            } catch (SecurityException e) {
                // fall through to !includePrivate case
            }
        }
        return cl.getFields();
    }

    private MemberBox findGetter(boolean isStatic, Map<String,Object> ht, String prefix,
                                 String propertyName)
    {
        String getterName = prefix.concat(propertyName);
        if (ht.containsKey(getterName)) {
            // Check that the getter is a method.
            Object member = ht.get(getterName);
            if (member instanceof NativeJavaMethod) {
                NativeJavaMethod njmGet = (NativeJavaMethod) member;
                return extractGetMethod(njmGet.methods, isStatic);
            }
        }
        return null;
    }

    private static MemberBox extractGetMethod(MemberBox[] methods,
                                              boolean isStatic)
    {
        // Inspect the list of all MemberBox for the only one having no
        // parameters
        for (MemberBox method : methods) {
            // Does getter method have an empty parameter list with a return
            // value (eg. a getSomething() or isSomething())?
            if (method.argTypes.length == 0 && (!isStatic || method.isStatic())) {
                Class<?> type = method.method().getReturnType();
                if (type != Void.TYPE) {
                    return method;
                }
                break;
            }
        }
        return null;
    }

    private static MemberBox extractSetMethod(Class<?> type, MemberBox[] methods,
                                              boolean isStatic)
    {
        //
        // Note: it may be preferable to allow NativeJavaMethod.findFunction()
        //       to find the appropriate setter; unfortunately, it requires an
        //       instance of the target arg to determine that.
        //

        // Make two passes: one to find a method with direct type assignment,
        // and one to find a widening conversion.
        for (int pass = 1; pass <= 2; ++pass) {
            for (MemberBox method : methods) {
                if (!isStatic || method.isStatic()) {
                    Class<?>[] params = method.argTypes;
                    if (params.length == 1) {
                        if (pass == 1) {
                            if (params[0] == type) {
                                return method;
                            }
                        } else {
                            if (pass != 2) Kit.codeBug();
                            if (params[0].isAssignableFrom(type)) {
                                return method;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static MemberBox extractSetMethod(MemberBox[] methods,
                                              boolean isStatic)
    {

        for (MemberBox method : methods) {
            if (!isStatic || method.isStatic()) {
                if (method.method().getReturnType() == Void.TYPE) {
                    if (method.argTypes.length == 1) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    Map<String,FieldAndMethods> getFieldAndMethodsObjects(Scriptable scope,
            Object javaObject, boolean isStatic)
    {
        Map<String,FieldAndMethods> ht = isStatic ? staticFieldAndMethods : fieldAndMethods;
        if (ht == null)
            return null;
        int len = ht.size();
        Map<String,FieldAndMethods> result = new HashMap<String,FieldAndMethods>(len);
        for (FieldAndMethods fam: ht.values()) {
            FieldAndMethods famNew = new FieldAndMethods(scope, fam.methods,
                                                         fam.field);
            famNew.javaObject = javaObject;
            result.put(fam.field.getName(), famNew);
        }
        return result;
    }

    static JavaMembers lookupClass(Scriptable scope, Class<?> dynamicType,
                                   Class<?> staticType, boolean includeProtected)
    {
        JavaMembers members;
        ClassCache cache = ClassCache.get(scope);
        Map<Class<?>,JavaMembers> ct = cache.getClassCacheMap();

        Class<?> cl = dynamicType;
        for (;;) {
            members = ct.get(cl);
            if (members != null) {
                if (cl != dynamicType) {
                    // member lookup for the original class failed because of
                    // missing privileges, cache the result so we don't try again
                    ct.put(dynamicType, members);
                }
                return members;
            }
            try {
                members = new JavaMembers(cache.getAssociatedScope(), cl,
                        includeProtected);
                break;
            } catch (SecurityException e) {
                // Reflection may fail for objects that are in a restricted
                // access package (e.g. sun.*).  If we get a security
                // exception, try again with the static type if it is interface.
                // Otherwise, try superclass
                if (staticType != null && staticType.isInterface()) {
                    cl = staticType;
                    staticType = null; // try staticType only once
                } else {
                    Class<?> parent = cl.getSuperclass();
                    if (parent == null) {
                        if (cl.isInterface()) {
                            // last resort after failed staticType interface
                            parent = ScriptRuntime.ObjectClass;
                        } else {
                            throw e;
                        }
                    }
                    cl = parent;
                }
            }
        }

        if (cache.isCachingEnabled()) {
            ct.put(cl, members);
            if (cl != dynamicType) {
                // member lookup for the original class failed because of
                // missing privileges, cache the result so we don't try again
                ct.put(dynamicType, members);
            }
        }
        return members;
    }

    RuntimeException reportMemberNotFound(String memberName)
    {
        return Context.reportRuntimeError2(
            "msg.java.member.not.found", cl.getName(), memberName);
    }

    private Class<?> cl;
    private Map<String,Object> members;
    private Map<String,FieldAndMethods> fieldAndMethods;
    private Map<String,Object> staticMembers;
    private Map<String,FieldAndMethods> staticFieldAndMethods;
    NativeJavaMethod ctors; // we use NativeJavaMethod for ctor overload resolution
}

class BeanProperty
{
    BeanProperty(MemberBox getter, MemberBox setter, NativeJavaMethod setters)
    {
        this.getter = getter;
        this.setter = setter;
        this.setters = setters;
    }

    MemberBox getter;
    MemberBox setter;
    NativeJavaMethod setters;
}

class FieldAndMethods extends NativeJavaMethod
{
    static final long serialVersionUID = -9222428244284796755L;

    FieldAndMethods(Scriptable scope, MemberBox[] methods, Field field)
    {
        super(methods);
        this.field = field;
        setParentScope(scope);
        setPrototype(ScriptableObject.getFunctionPrototype(scope));
    }

    @Override
    public Object getDefaultValue(Class<?> hint)
    {
        if (hint == ScriptRuntime.FunctionClass)
            return this;
        Object rval;
        Class<?> type;
        try {
            rval = field.get(javaObject);
            type = field.getType();
        } catch (IllegalAccessException accEx) {
            throw Context.reportRuntimeError1(
                "msg.java.internal.private", field.getName());
        }
        Context cx  = Context.getContext();
        rval = cx.getWrapFactory().wrap(cx, this, rval, type);
        if (rval instanceof Scriptable) {
            rval = ((Scriptable) rval).getDefaultValue(hint);
        }
        return rval;
    }

    Field field;
    Object javaObject;
}
