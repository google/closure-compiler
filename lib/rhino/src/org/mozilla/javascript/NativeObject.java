/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * This class implements the Object native object.
 * See ECMA 15.2.
 */
public class NativeObject extends IdScriptableObject implements Map
{
    static final long serialVersionUID = -6345305608474346996L;

    private static final Object OBJECT_TAG = "Object";

    static void init(Scriptable scope, boolean sealed)
    {
        NativeObject obj = new NativeObject();
        obj.exportAsJSClass(MAX_PROTOTYPE_ID, scope, sealed);
    }

    @Override
    public String getClassName()
    {
        return "Object";
    }

    @Override
    public String toString()
    {
        return ScriptRuntime.defaultObjectToString(this);
    }

    @Override
    protected void fillConstructorProperties(IdFunctionObject ctor)
    {
        addIdFunctionProperty(ctor, OBJECT_TAG, ConstructorId_getPrototypeOf,
                "getPrototypeOf", 1);
        addIdFunctionProperty(ctor, OBJECT_TAG, ConstructorId_keys,
                "keys", 1);
        addIdFunctionProperty(ctor, OBJECT_TAG, ConstructorId_getOwnPropertyNames,
                "getOwnPropertyNames", 1);
        addIdFunctionProperty(ctor, OBJECT_TAG, ConstructorId_getOwnPropertyDescriptor,
                "getOwnPropertyDescriptor", 2);
        addIdFunctionProperty(ctor, OBJECT_TAG, ConstructorId_defineProperty,
                "defineProperty", 3);
        addIdFunctionProperty(ctor, OBJECT_TAG, ConstructorId_isExtensible,
                "isExtensible", 1);
        addIdFunctionProperty(ctor, OBJECT_TAG, ConstructorId_preventExtensions,
                "preventExtensions", 1);
        addIdFunctionProperty(ctor, OBJECT_TAG, ConstructorId_defineProperties,
                "defineProperties", 2);
        addIdFunctionProperty(ctor, OBJECT_TAG, ConstructorId_create,
                "create", 2);
        addIdFunctionProperty(ctor, OBJECT_TAG, ConstructorId_isSealed,
                "isSealed", 1);
        addIdFunctionProperty(ctor, OBJECT_TAG, ConstructorId_isFrozen,
                "isFrozen", 1);
        addIdFunctionProperty(ctor, OBJECT_TAG, ConstructorId_seal,
                "seal", 1);
        addIdFunctionProperty(ctor, OBJECT_TAG, ConstructorId_freeze,
                "freeze", 1);
        super.fillConstructorProperties(ctor);
    }

    @Override
    protected void initPrototypeId(int id)
    {
        String s;
        int arity;
        switch (id) {
          case Id_constructor:    arity=1; s="constructor";    break;
          case Id_toString:       arity=0; s="toString";       break;
          case Id_toLocaleString: arity=0; s="toLocaleString"; break;
          case Id_valueOf:        arity=0; s="valueOf";        break;
          case Id_hasOwnProperty: arity=1; s="hasOwnProperty"; break;
          case Id_propertyIsEnumerable:
            arity=1; s="propertyIsEnumerable"; break;
          case Id_isPrototypeOf:  arity=1; s="isPrototypeOf";  break;
          case Id_toSource:       arity=0; s="toSource";       break;
          case Id___defineGetter__:
            arity=2; s="__defineGetter__";     break;
          case Id___defineSetter__:
            arity=2; s="__defineSetter__";     break;
          case Id___lookupGetter__:
            arity=1; s="__lookupGetter__";     break;
          case Id___lookupSetter__:
            arity=1; s="__lookupSetter__";     break;
          default: throw new IllegalArgumentException(String.valueOf(id));
        }
        initPrototypeMethod(OBJECT_TAG, id, s, arity);
    }

    @Override
    public Object execIdCall(IdFunctionObject f, Context cx, Scriptable scope,
                             Scriptable thisObj, Object[] args)
    {
        if (!f.hasTag(OBJECT_TAG)) {
            return super.execIdCall(f, cx, scope, thisObj, args);
        }
        int id = f.methodId();
        switch (id) {
          case Id_constructor: {
            if (thisObj != null) {
                // BaseFunction.construct will set up parent, proto
                return f.construct(cx, scope, args);
            }
            if (args.length == 0 || args[0] == null
                || args[0] == Undefined.instance)
            {
                return new NativeObject();
            }
            return ScriptRuntime.toObject(cx, scope, args[0]);
          }

          case Id_toLocaleString: // For now just alias toString
          case Id_toString: {
            if (cx.hasFeature(Context.FEATURE_TO_STRING_AS_SOURCE)) {
                String s = ScriptRuntime.defaultObjectToSource(cx, scope,
                                                               thisObj, args);
                int L = s.length();
                if (L != 0 && s.charAt(0) == '(' && s.charAt(L - 1) == ')') {
                    // Strip () that surrounds toSource
                    s = s.substring(1, L - 1);
                }
                return s;
            }
            return ScriptRuntime.defaultObjectToString(thisObj);
          }

          case Id_valueOf:
            return thisObj;

          case Id_hasOwnProperty: {
            boolean result;
            if (args.length == 0) {
                result = false;
            } else {
                String s = ScriptRuntime.toStringIdOrIndex(cx, args[0]);
                if (s == null) {
                    int index = ScriptRuntime.lastIndexResult(cx);
                    result = thisObj.has(index, thisObj);
                } else {
                    result = thisObj.has(s, thisObj);
                }
            }
            return ScriptRuntime.wrapBoolean(result);
          }

          case Id_propertyIsEnumerable: {
            boolean result;
            if (args.length == 0) {
                result = false;
            } else {
                String s = ScriptRuntime.toStringIdOrIndex(cx, args[0]);
                if (s == null) {
                    int index = ScriptRuntime.lastIndexResult(cx);
                    result = thisObj.has(index, thisObj);
                    if (result && thisObj instanceof ScriptableObject) {
                        ScriptableObject so = (ScriptableObject)thisObj;
                        int attrs = so.getAttributes(index);
                        result = ((attrs & ScriptableObject.DONTENUM) == 0);
                    }
                } else {
                    result = thisObj.has(s, thisObj);
                    if (result && thisObj instanceof ScriptableObject) {
                        ScriptableObject so = (ScriptableObject)thisObj;
                        int attrs = so.getAttributes(s);
                        result = ((attrs & ScriptableObject.DONTENUM) == 0);
                    }
                }
            }
            return ScriptRuntime.wrapBoolean(result);
          }

          case Id_isPrototypeOf: {
            boolean result = false;
            if (args.length != 0 && args[0] instanceof Scriptable) {
                Scriptable v = (Scriptable) args[0];
                do {
                    v = v.getPrototype();
                    if (v == thisObj) {
                        result = true;
                        break;
                    }
                } while (v != null);
            }
            return ScriptRuntime.wrapBoolean(result);
          }

          case Id_toSource:
            return ScriptRuntime.defaultObjectToSource(cx, scope, thisObj,
                                                       args);
          case Id___defineGetter__:
          case Id___defineSetter__:
            {
                if (args.length < 2 || !(args[1] instanceof Callable)) {
                    Object badArg = (args.length >= 2 ? args[1]
                                     : Undefined.instance);
                    throw ScriptRuntime.notFunctionError(badArg);
                }
                if (!(thisObj instanceof ScriptableObject)) {
                    throw Context.reportRuntimeError2(
                        "msg.extend.scriptable",
                        thisObj.getClass().getName(),
                        String.valueOf(args[0]));
                }
                ScriptableObject so = (ScriptableObject)thisObj;
                String name = ScriptRuntime.toStringIdOrIndex(cx, args[0]);
                int index = (name != null ? 0
                             : ScriptRuntime.lastIndexResult(cx));
                Callable getterOrSetter = (Callable)args[1];
                boolean isSetter = (id == Id___defineSetter__);
                so.setGetterOrSetter(name, index, getterOrSetter, isSetter);
                if (so instanceof NativeArray)
                    ((NativeArray)so).setDenseOnly(false);
            }
            return Undefined.instance;

            case Id___lookupGetter__:
            case Id___lookupSetter__:
              {
                  if (args.length < 1 ||
                      !(thisObj instanceof ScriptableObject))
                      return Undefined.instance;

                  ScriptableObject so = (ScriptableObject)thisObj;
                  String name = ScriptRuntime.toStringIdOrIndex(cx, args[0]);
                  int index = (name != null ? 0
                               : ScriptRuntime.lastIndexResult(cx));
                  boolean isSetter = (id == Id___lookupSetter__);
                  Object gs;
                  for (;;) {
                      gs = so.getGetterOrSetter(name, index, isSetter);
                      if (gs != null)
                          break;
                      // If there is no getter or setter for the object itself,
                      // how about the prototype?
                      Scriptable v = so.getPrototype();
                      if (v == null)
                          break;
                      if (v instanceof ScriptableObject)
                          so = (ScriptableObject)v;
                      else
                          break;
                  }
                  if (gs != null)
                      return gs;
              }
              return Undefined.instance;

          case ConstructorId_getPrototypeOf:
              {
                Object arg = args.length < 1 ? Undefined.instance : args[0];
                Scriptable obj = ensureScriptable(arg);
                return obj.getPrototype();
              }
          case ConstructorId_keys:
              {
                Object arg = args.length < 1 ? Undefined.instance : args[0];
                Scriptable obj = ensureScriptable(arg);
                Object[] ids = obj.getIds();
                for (int i = 0; i < ids.length; i++) {
                  ids[i] = ScriptRuntime.toString(ids[i]);
                }
                return cx.newArray(scope, ids);
              }
          case ConstructorId_getOwnPropertyNames:
              {
                Object arg = args.length < 1 ? Undefined.instance : args[0];
                ScriptableObject obj = ensureScriptableObject(arg);
                Object[] ids = obj.getAllIds();
                for (int i = 0; i < ids.length; i++) {
                  ids[i] = ScriptRuntime.toString(ids[i]);
                }
                return cx.newArray(scope, ids);
              }
          case ConstructorId_getOwnPropertyDescriptor:
              {
                Object arg = args.length < 1 ? Undefined.instance : args[0];
                // TODO(norris): There's a deeper issue here if
                // arg instanceof Scriptable. Should we create a new
                // interface to admit the new ECMAScript 5 operations?
                ScriptableObject obj = ensureScriptableObject(arg);
                Object nameArg = args.length < 2 ? Undefined.instance : args[1];
                String name = ScriptRuntime.toString(nameArg);
                Scriptable desc = obj.getOwnPropertyDescriptor(cx, name);
                return desc == null ? Undefined.instance : desc;
              }
          case ConstructorId_defineProperty:
              {
                Object arg = args.length < 1 ? Undefined.instance : args[0];
                ScriptableObject obj = ensureScriptableObject(arg);
                Object name = args.length < 2 ? Undefined.instance : args[1];
                Object descArg = args.length < 3 ? Undefined.instance : args[2];
                ScriptableObject desc = ensureScriptableObject(descArg);
                obj.defineOwnProperty(cx, name, desc);
                return obj;
              }
          case ConstructorId_isExtensible:
              {
                Object arg = args.length < 1 ? Undefined.instance : args[0];
                ScriptableObject obj = ensureScriptableObject(arg);
                return Boolean.valueOf(obj.isExtensible());
              }
          case ConstructorId_preventExtensions:
              {
                Object arg = args.length < 1 ? Undefined.instance : args[0];
                ScriptableObject obj = ensureScriptableObject(arg);
                obj.preventExtensions();
                return obj;
              }
          case ConstructorId_defineProperties:
              {
                Object arg = args.length < 1 ? Undefined.instance : args[0];
                ScriptableObject obj = ensureScriptableObject(arg);
                Object propsObj = args.length < 2 ? Undefined.instance : args[1];
                Scriptable props = Context.toObject(propsObj, getParentScope());
                obj.defineOwnProperties(cx, ensureScriptableObject(props));
                return obj;
        }
          case ConstructorId_create:
              {
                Object arg = args.length < 1 ? Undefined.instance : args[0];
                Scriptable obj = (arg == null) ? null : ensureScriptable(arg);

                ScriptableObject newObject = new NativeObject();
                newObject.setParentScope(this.getParentScope());
                newObject.setPrototype(obj);

                if (args.length > 1 && args[1] != Undefined.instance) {
                  Scriptable props = Context.toObject(args[1], getParentScope());
                  newObject.defineOwnProperties(cx, ensureScriptableObject(props));
                }

                return newObject;
              }

          case ConstructorId_isSealed:
              {
                Object arg = args.length < 1 ? Undefined.instance : args[0];
                ScriptableObject obj = ensureScriptableObject(arg);

                if (obj.isExtensible()) return Boolean.FALSE;

                for (Object name: obj.getAllIds()) {
                  Object configurable = obj.getOwnPropertyDescriptor(cx, name).get("configurable");
                  if (Boolean.TRUE.equals(configurable))
                    return Boolean.FALSE;
                }

                return Boolean.TRUE;
              }
          case ConstructorId_isFrozen:
              {
                Object arg = args.length < 1 ? Undefined.instance : args[0];
                ScriptableObject obj = ensureScriptableObject(arg);

                if (obj.isExtensible()) return Boolean.FALSE;

                for (Object name: obj.getAllIds()) {
                  ScriptableObject desc = obj.getOwnPropertyDescriptor(cx, name);
                  if (Boolean.TRUE.equals(desc.get("configurable")))
                    return Boolean.FALSE;
                  if (isDataDescriptor(desc) && Boolean.TRUE.equals(desc.get("writable")))
                    return Boolean.FALSE;
                }

                return Boolean.TRUE;
              }
          case ConstructorId_seal:
              {
                Object arg = args.length < 1 ? Undefined.instance : args[0];
                ScriptableObject obj = ensureScriptableObject(arg);

                for (Object name: obj.getAllIds()) {
                  ScriptableObject desc = obj.getOwnPropertyDescriptor(cx, name);
                  if (Boolean.TRUE.equals(desc.get("configurable"))) {
                    desc.put("configurable", desc, Boolean.FALSE);
                    obj.defineOwnProperty(cx, name, desc, false);
                  }
                }
                obj.preventExtensions();

                return obj;
              }
          case ConstructorId_freeze:
              {
                Object arg = args.length < 1 ? Undefined.instance : args[0];
                ScriptableObject obj = ensureScriptableObject(arg);

                for (Object name: obj.getAllIds()) {
                  ScriptableObject desc = obj.getOwnPropertyDescriptor(cx, name);
                  if (isDataDescriptor(desc) && Boolean.TRUE.equals(desc.get("writable")))
                    desc.put("writable", desc, Boolean.FALSE);
                  if (Boolean.TRUE.equals(desc.get("configurable")))
                    desc.put("configurable", desc, Boolean.FALSE);
                  obj.defineOwnProperty(cx, name, desc, false);
                }
                obj.preventExtensions();

                return obj;
              }


          default:
            throw new IllegalArgumentException(String.valueOf(id));
        }
    }

    // methods implementing java.util.Map

    public boolean containsKey(Object key) {
        if (key instanceof String) {
            return has((String) key, this);
        } else if (key instanceof Number) {
            return has(((Number) key).intValue(), this);
        }
        return false;
    }

    public boolean containsValue(Object value) {
        for (Object obj : values()) {
            if (value == obj ||
                    value != null && value.equals(obj)) {
                return true;
            }
        }
        return false;
    }

    public Object remove(Object key) {
        Object value = get(key);
        if (key instanceof String) {
            delete((String) key);
        } else if (key instanceof Number) {
            delete(((Number) key).intValue());
        }
        return value;
    }


    public Set<Object> keySet() {
        return new KeySet();
    }

    public Collection<Object> values() {
        return new ValueCollection();
    }

    public Set<Map.Entry<Object, Object>> entrySet() {
        return new EntrySet();
    }

    public Object put(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    public void putAll(Map m) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        throw new UnsupportedOperationException();
    }


    class EntrySet extends AbstractSet<Entry<Object, Object>> {
        @Override
        public Iterator<Entry<Object, Object>> iterator() {
            return new Iterator<Map.Entry<Object, Object>>() {
                Object[] ids = getIds();
                Object key = null;
                int index = 0;

                public boolean hasNext() {
                    return index < ids.length;
                }

                public Map.Entry<Object, Object> next() {
                    final Object ekey = key = ids[index++];
                    final Object value = get(key);
                    return new Map.Entry<Object, Object>() {
                        public Object getKey() {
                            return ekey;
                        }

                        public Object getValue() {
                            return value;
                        }

                        public Object setValue(Object value) {
                            throw new UnsupportedOperationException();
                        }

                        public boolean equals(Object other) {
                            if (!(other instanceof Map.Entry)) {
                                return false;
                            }
                            Map.Entry e = (Map.Entry) other;
                            return (ekey == null ? e.getKey() == null : ekey.equals(e.getKey()))
                                && (value == null ? e.getValue() == null : value.equals(e.getValue()));
                        }

                        public int hashCode() {
                            return (ekey == null ? 0 : ekey.hashCode()) ^
                                   (value == null ? 0 : value.hashCode());
                        }

                        public String toString() {
                            return ekey + "=" + value;
                        }
                    };
                }

                public void remove() {
                    if (key == null) {
                        throw new IllegalStateException();
                    }
                    NativeObject.this.remove(key);
                    key = null;
                }
            };
        }

        @Override
        public int size() {
            return NativeObject.this.size();
        }
    }

    class KeySet extends AbstractSet<Object> {

        @Override
        public boolean contains(Object key) {
            return containsKey(key);
        }

        @Override
        public Iterator<Object> iterator() {
            return new Iterator<Object>() {
                Object[] ids = getIds();
                Object key;
                int index = 0;

                public boolean hasNext() {
                    return index < ids.length;
                }

                public Object next() {
                    try {
                        return (key = ids[index++]);
                    } catch(ArrayIndexOutOfBoundsException e) {
                        key = null;
                        throw new NoSuchElementException();
                    }
                }

                public void remove() {
                    if (key == null) {
                        throw new IllegalStateException();
                    }
                    NativeObject.this.remove(key);
                    key = null;
                }
           };
        }

        @Override
        public int size() {
            return NativeObject.this.size();
        }
    }

    class ValueCollection extends AbstractCollection<Object> {

        @Override
        public Iterator<Object> iterator() {
            return new Iterator<Object>() {
                Object[] ids = getIds();
                Object key;
                int index = 0;

                public boolean hasNext() {
                    return index < ids.length;
                }

                public Object next() {
                    return get((key = ids[index++]));
                }

                public void remove() {
                    if (key == null) {
                        throw new IllegalStateException();
                    }
                    NativeObject.this.remove(key);
                    key = null;
                }
            };
        }

        @Override
        public int size() {
            return NativeObject.this.size();
        }
    }


// #string_id_map#

    @Override
    protected int findPrototypeId(String s)
    {
        int id;
// #generated# Last update: 2007-05-09 08:15:55 EDT
        L0: { id = 0; String X = null; int c;
            L: switch (s.length()) {
            case 7: X="valueOf";id=Id_valueOf; break L;
            case 8: c=s.charAt(3);
                if (c=='o') { X="toSource";id=Id_toSource; }
                else if (c=='t') { X="toString";id=Id_toString; }
                break L;
            case 11: X="constructor";id=Id_constructor; break L;
            case 13: X="isPrototypeOf";id=Id_isPrototypeOf; break L;
            case 14: c=s.charAt(0);
                if (c=='h') { X="hasOwnProperty";id=Id_hasOwnProperty; }
                else if (c=='t') { X="toLocaleString";id=Id_toLocaleString; }
                break L;
            case 16: c=s.charAt(2);
                if (c=='d') {
                    c=s.charAt(8);
                    if (c=='G') { X="__defineGetter__";id=Id___defineGetter__; }
                    else if (c=='S') { X="__defineSetter__";id=Id___defineSetter__; }
                }
                else if (c=='l') {
                    c=s.charAt(8);
                    if (c=='G') { X="__lookupGetter__";id=Id___lookupGetter__; }
                    else if (c=='S') { X="__lookupSetter__";id=Id___lookupSetter__; }
                }
                break L;
            case 20: X="propertyIsEnumerable";id=Id_propertyIsEnumerable; break L;
            }
            if (X!=null && X!=s && !X.equals(s)) id = 0;
            break L0;
        }
// #/generated#
        return id;
    }

    private static final int
        ConstructorId_getPrototypeOf = -1,
        ConstructorId_keys = -2,
        ConstructorId_getOwnPropertyNames = -3,
        ConstructorId_getOwnPropertyDescriptor = -4,
        ConstructorId_defineProperty = -5,
        ConstructorId_isExtensible = -6,
        ConstructorId_preventExtensions = -7,
        ConstructorId_defineProperties= -8,
        ConstructorId_create = -9,
        ConstructorId_isSealed = -10,
        ConstructorId_isFrozen = -11,
        ConstructorId_seal = -12,
        ConstructorId_freeze = -13,

        Id_constructor           = 1,
        Id_toString              = 2,
        Id_toLocaleString        = 3,
        Id_valueOf               = 4,
        Id_hasOwnProperty        = 5,
        Id_propertyIsEnumerable  = 6,
        Id_isPrototypeOf         = 7,
        Id_toSource              = 8,
        Id___defineGetter__      = 9,
        Id___defineSetter__      = 10,
        Id___lookupGetter__      = 11,
        Id___lookupSetter__      = 12,
        MAX_PROTOTYPE_ID         = 12;

// #/string_id_map#
}
