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
 *   Igor Bukanov
 *   Daniel Gredler
 *   Bob Jervis
 *   Roger Lawrence
 *   Cameron McCormack
 *   Steve Weiss
 *   Hannes Wallnoefer
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

// API class

package org.mozilla.javascript;

import java.lang.reflect.*;
import java.lang.annotation.Annotation;
import java.util.*;
import java.io.*;
import org.mozilla.javascript.debug.DebuggableObject;
import org.mozilla.javascript.annotations.*;

/**
 * This is the default implementation of the Scriptable interface. This
 * class provides convenient default behavior that makes it easier to
 * define host objects.
 * <p>
 * Various properties and methods of JavaScript objects can be conveniently
 * defined using methods of ScriptableObject.
 * <p>
 * Classes extending ScriptableObject must define the getClassName method.
 *
 * @see org.mozilla.javascript.Scriptable
 */

public abstract class ScriptableObject implements Scriptable, Serializable,
                                                  DebuggableObject,
                                                  ConstProperties, Map
{

    /**
     * The empty property attribute.
     *
     * Used by getAttributes() and setAttributes().
     *
     * @see org.mozilla.javascript.ScriptableObject#getAttributes(String)
     * @see org.mozilla.javascript.ScriptableObject#setAttributes(String, int)
     */
    public static final int EMPTY =     0x00;

    /**
     * Property attribute indicating assignment to this property is ignored.
     *
     * @see org.mozilla.javascript.ScriptableObject
     *      #put(String, Scriptable, Object)
     * @see org.mozilla.javascript.ScriptableObject#getAttributes(String)
     * @see org.mozilla.javascript.ScriptableObject#setAttributes(String, int)
     */
    public static final int READONLY =  0x01;

    /**
     * Property attribute indicating property is not enumerated.
     *
     * Only enumerated properties will be returned by getIds().
     *
     * @see org.mozilla.javascript.ScriptableObject#getIds()
     * @see org.mozilla.javascript.ScriptableObject#getAttributes(String)
     * @see org.mozilla.javascript.ScriptableObject#setAttributes(String, int)
     */
    public static final int DONTENUM =  0x02;

    /**
     * Property attribute indicating property cannot be deleted.
     *
     * @see org.mozilla.javascript.ScriptableObject#delete(String)
     * @see org.mozilla.javascript.ScriptableObject#getAttributes(String)
     * @see org.mozilla.javascript.ScriptableObject#setAttributes(String, int)
     */
    public static final int PERMANENT = 0x04;

    /**
     * Property attribute indicating that this is a const property that has not
     * been assigned yet.  The first 'const' assignment to the property will
     * clear this bit.
     */
    public static final int UNINITIALIZED_CONST = 0x08;

    public static final int CONST = PERMANENT|READONLY|UNINITIALIZED_CONST;
    /**
     * The prototype of this object.
     */
    private Scriptable prototypeObject;

    /**
     * The parent scope of this object.
     */
    private Scriptable parentScopeObject;

    private static final Slot REMOVED = new Slot(null, 0, READONLY);

    static {
        REMOVED.wasDeleted = true;
    }

    private transient Slot[] slots;
    // If count >= 0, it gives number of keys or if count < 0,
    // it indicates sealed object where ~count gives number of keys
    private int count;

    // gateways into the definition-order linked list of slots
    private transient Slot firstAdded;
    private transient Slot lastAdded;

    // cache; may be removed for smaller memory footprint
    private transient Slot lastAccess = REMOVED;

    private volatile Map<Object,Object> associatedValues;

    private static final int SLOT_QUERY = 1;
    private static final int SLOT_MODIFY = 2;
    private static final int SLOT_REMOVE = 3;
    private static final int SLOT_MODIFY_GETTER_SETTER = 4;
    private static final int SLOT_MODIFY_CONST = 5;
    private static final int SLOT_CONVERT_ACCESSOR_TO_DATA = 6;

    private boolean isExtensible = true;

    private static class Slot implements Serializable
    {
        private static final long serialVersionUID = -6090581677123995491L;
        String name; // This can change due to caching
        int indexOrHash;
        private volatile short attributes;
        transient volatile boolean wasDeleted;
        volatile Object value;
        transient volatile Slot next; // next in hash table bucket
        transient volatile Slot orderedNext; // next in linked list

        Slot(String name, int indexOrHash, int attributes)
        {
            this.name = name;
            this.indexOrHash = indexOrHash;
            this.attributes = (short)attributes;
        }

        private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException
        {
            in.defaultReadObject();
            if (name != null) {
                indexOrHash = name.hashCode();
            }
        }

        final int getAttributes()
        {
            return attributes;
        }

        final synchronized void setAttributes(int value)
        {
            checkValidAttributes(value);
            attributes = (short)value;
        }

        final void checkNotReadonly()
        {
            if ((attributes & READONLY) != 0) {
                String str = (name != null ? name
                              : Integer.toString(indexOrHash));
                throw Context.reportRuntimeError1("msg.modify.readonly", str);
            }
        }

        ScriptableObject getPropertyDescriptor(Context cx, Scriptable scope) {
            return buildDataDescriptor(
                scope,
                (value == null ? Undefined.instance : value),
                attributes);
        }

    }

    protected static ScriptableObject buildDataDescriptor(Scriptable scope, Object value, int attributes) {
      ScriptableObject desc = new NativeObject();
      ScriptRuntime.setObjectProtoAndParent(desc, scope);

      desc.defineProperty("value",        value, EMPTY);
      desc.defineProperty("writable",     (attributes & READONLY) == 0, EMPTY);
      desc.defineProperty("enumerable",   (attributes & DONTENUM) == 0, EMPTY);
      desc.defineProperty("configurable", (attributes & PERMANENT) == 0, EMPTY);
      return desc;
    }

    private static final class GetterSlot extends Slot
    {
        static final long serialVersionUID = -4900574849788797588L;

        Object getter;
        Object setter;

        GetterSlot(String name, int indexOrHash, int attributes)
        {
            super(name, indexOrHash, attributes);
        }

        @Override
        ScriptableObject getPropertyDescriptor(Context cx, Scriptable parent) {
          ScriptableObject desc = super.getPropertyDescriptor(cx, parent);
          desc.delete("value");
          desc.delete("writable");
          if (getter != null) desc.defineProperty("get", getter, EMPTY);
          if (setter != null) desc.defineProperty("set", setter, EMPTY);
          return desc;
        }
    }

    static void checkValidAttributes(int attributes)
    {
        final int mask = READONLY | DONTENUM | PERMANENT | UNINITIALIZED_CONST;
        if ((attributes & ~mask) != 0) {
            throw new IllegalArgumentException(String.valueOf(attributes));
        }
    }

    public ScriptableObject()
    {
    }

    public ScriptableObject(Scriptable scope, Scriptable prototype)
    {
        if (scope == null)
            throw new IllegalArgumentException();

        parentScopeObject = scope;
        prototypeObject = prototype;
    }

    /**
     * Gets the value that will be returned by calling the typeof operator on this object.
     * @return default is "object" unless {@link #avoidObjectDetection()} is <code>true</code> in which
     * case it returns "undefined"
     */
    public String getTypeOf() {
    	return avoidObjectDetection() ? "undefined" : "object";
    }

    /**
     * Return the name of the class.
     *
     * This is typically the same name as the constructor.
     * Classes extending ScriptableObject must implement this abstract
     * method.
     */
    public abstract String getClassName();

    /**
     * Returns true if the named property is defined.
     *
     * @param name the name of the property
     * @param start the object in which the lookup began
     * @return true if and only if the property was found in the object
     */
    public boolean has(String name, Scriptable start)
    {
        return null != getSlot(name, 0, SLOT_QUERY);
    }

    /**
     * Returns true if the property index is defined.
     *
     * @param index the numeric index for the property
     * @param start the object in which the lookup began
     * @return true if and only if the property was found in the object
     */
    public boolean has(int index, Scriptable start)
    {
        return null != getSlot(null, index, SLOT_QUERY);
    }

    /**
     * Returns the value of the named property or NOT_FOUND.
     *
     * If the property was created using defineProperty, the
     * appropriate getter method is called.
     *
     * @param name the name of the property
     * @param start the object in which the lookup began
     * @return the value of the property (may be null), or NOT_FOUND
     */
    public Object get(String name, Scriptable start)
    {
        return getImpl(name, 0, start);
    }

    /**
     * Returns the value of the indexed property or NOT_FOUND.
     *
     * @param index the numeric index for the property
     * @param start the object in which the lookup began
     * @return the value of the property (may be null), or NOT_FOUND
     */
    public Object get(int index, Scriptable start)
    {
        return getImpl(null, index, start);
    }

    /**
     * Sets the value of the named property, creating it if need be.
     *
     * If the property was created using defineProperty, the
     * appropriate setter method is called. <p>
     *
     * If the property's attributes include READONLY, no action is
     * taken.
     * This method will actually set the property in the start
     * object.
     *
     * @param name the name of the property
     * @param start the object whose property is being set
     * @param value value to set the property to
     */
    public void put(String name, Scriptable start, Object value)
    {
        if (putImpl(name, 0, start, value, EMPTY))
            return;

        if (start == this) throw Kit.codeBug();
        start.put(name, start, value);
    }

    /**
     * Sets the value of the indexed property, creating it if need be.
     *
     * @param index the numeric index for the property
     * @param start the object whose property is being set
     * @param value value to set the property to
     */
    public void put(int index, Scriptable start, Object value)
    {
        if (putImpl(null, index, start, value, EMPTY))
            return;

        if (start == this) throw Kit.codeBug();
        start.put(index, start, value);
    }

    /**
     * Removes a named property from the object.
     *
     * If the property is not found, or it has the PERMANENT attribute,
     * no action is taken.
     *
     * @param name the name of the property
     */
    public void delete(String name)
    {
        checkNotSealed(name, 0);
        accessSlot(name, 0, SLOT_REMOVE);
    }

    /**
     * Removes the indexed property from the object.
     *
     * If the property is not found, or it has the PERMANENT attribute,
     * no action is taken.
     *
     * @param index the numeric index for the property
     */
    public void delete(int index)
    {
        checkNotSealed(null, index);
        accessSlot(null, index, SLOT_REMOVE);
    }

    /**
     * Sets the value of the named const property, creating it if need be.
     *
     * If the property was created using defineProperty, the
     * appropriate setter method is called. <p>
     *
     * If the property's attributes include READONLY, no action is
     * taken.
     * This method will actually set the property in the start
     * object.
     *
     * @param name the name of the property
     * @param start the object whose property is being set
     * @param value value to set the property to
     */
    public void putConst(String name, Scriptable start, Object value)
    {
        if (putImpl(name, 0, start, value, READONLY))
            return;

        if (start == this) throw Kit.codeBug();
        if (start instanceof ConstProperties)
            ((ConstProperties)start).putConst(name, start, value);
        else
            start.put(name, start, value);
    }

    public void defineConst(String name, Scriptable start)
    {
        if (putImpl(name, 0, start, Undefined.instance, UNINITIALIZED_CONST))
            return;

        if (start == this) throw Kit.codeBug();
        if (start instanceof ConstProperties)
            ((ConstProperties)start).defineConst(name, start);
    }
    /**
     * Returns true if the named property is defined as a const on this object.
     * @param name
     * @return true if the named property is defined as a const, false
     * otherwise.
     */
    public boolean isConst(String name)
    {
        Slot slot = getSlot(name, 0, SLOT_QUERY);
        if (slot == null) {
            return false;
        }
        return (slot.getAttributes() & (PERMANENT|READONLY)) ==
                                       (PERMANENT|READONLY);

    }
    /**
     * @deprecated Use {@link #getAttributes(String name)}. The engine always
     * ignored the start argument.
     */
    public final int getAttributes(String name, Scriptable start)
    {
        return getAttributes(name);
    }

    /**
     * @deprecated Use {@link #getAttributes(int index)}. The engine always
     * ignored the start argument.
     */
    public final int getAttributes(int index, Scriptable start)
    {
        return getAttributes(index);
    }

    /**
     * @deprecated Use {@link #setAttributes(String name, int attributes)}.
     * The engine always ignored the start argument.
     */
    public final void setAttributes(String name, Scriptable start,
                                    int attributes)
    {
        setAttributes(name, attributes);
    }

    /**
     * @deprecated Use {@link #setAttributes(int index, int attributes)}.
     * The engine always ignored the start argument.
     */
    public void setAttributes(int index, Scriptable start,
                              int attributes)
    {
        setAttributes(index, attributes);
    }

    /**
     * Get the attributes of a named property.
     *
     * The property is specified by <code>name</code>
     * as defined for <code>has</code>.<p>
     *
     * @param name the identifier for the property
     * @return the bitset of attributes
     * @exception EvaluatorException if the named property is not found
     * @see org.mozilla.javascript.ScriptableObject#has(String, Scriptable)
     * @see org.mozilla.javascript.ScriptableObject#READONLY
     * @see org.mozilla.javascript.ScriptableObject#DONTENUM
     * @see org.mozilla.javascript.ScriptableObject#PERMANENT
     * @see org.mozilla.javascript.ScriptableObject#EMPTY
     */
    public int getAttributes(String name)
    {
        return findAttributeSlot(name, 0, SLOT_QUERY).getAttributes();
    }

    /**
     * Get the attributes of an indexed property.
     *
     * @param index the numeric index for the property
     * @exception EvaluatorException if the named property is not found
     *            is not found
     * @return the bitset of attributes
     * @see org.mozilla.javascript.ScriptableObject#has(String, Scriptable)
     * @see org.mozilla.javascript.ScriptableObject#READONLY
     * @see org.mozilla.javascript.ScriptableObject#DONTENUM
     * @see org.mozilla.javascript.ScriptableObject#PERMANENT
     * @see org.mozilla.javascript.ScriptableObject#EMPTY
     */
    public int getAttributes(int index)
    {
        return findAttributeSlot(null, index, SLOT_QUERY).getAttributes();
    }

    /**
     * Set the attributes of a named property.
     *
     * The property is specified by <code>name</code>
     * as defined for <code>has</code>.<p>
     *
     * The possible attributes are READONLY, DONTENUM,
     * and PERMANENT. Combinations of attributes
     * are expressed by the bitwise OR of attributes.
     * EMPTY is the state of no attributes set. Any unused
     * bits are reserved for future use.
     *
     * @param name the name of the property
     * @param attributes the bitset of attributes
     * @exception EvaluatorException if the named property is not found
     * @see org.mozilla.javascript.Scriptable#has(String, Scriptable)
     * @see org.mozilla.javascript.ScriptableObject#READONLY
     * @see org.mozilla.javascript.ScriptableObject#DONTENUM
     * @see org.mozilla.javascript.ScriptableObject#PERMANENT
     * @see org.mozilla.javascript.ScriptableObject#EMPTY
     */
    public void setAttributes(String name, int attributes)
    {
        checkNotSealed(name, 0);
        findAttributeSlot(name, 0, SLOT_MODIFY).setAttributes(attributes);
    }

    /**
     * Set the attributes of an indexed property.
     *
     * @param index the numeric index for the property
     * @param attributes the bitset of attributes
     * @exception EvaluatorException if the named property is not found
     * @see org.mozilla.javascript.Scriptable#has(String, Scriptable)
     * @see org.mozilla.javascript.ScriptableObject#READONLY
     * @see org.mozilla.javascript.ScriptableObject#DONTENUM
     * @see org.mozilla.javascript.ScriptableObject#PERMANENT
     * @see org.mozilla.javascript.ScriptableObject#EMPTY
     */
    public void setAttributes(int index, int attributes)
    {
        checkNotSealed(null, index);
        findAttributeSlot(null, index, SLOT_MODIFY).setAttributes(attributes);
    }

    /**
     * XXX: write docs.
     */
    public void setGetterOrSetter(String name, int index,
                                  Callable getterOrSetter, boolean isSetter)
    {
        setGetterOrSetter(name, index, getterOrSetter, isSetter, false);
    }

    private void setGetterOrSetter(String name, int index, Callable getterOrSetter, boolean isSetter, boolean force)
    {
        if (name != null && index != 0)
            throw new IllegalArgumentException(name);

        if (!force) {
          checkNotSealed(name, index);
        }

        final GetterSlot gslot;
        if (isExtensible()) {
          gslot = (GetterSlot)getSlot(name, index, SLOT_MODIFY_GETTER_SETTER);
        } else {
          gslot = (GetterSlot)getSlot(name, index, SLOT_QUERY);
          if (gslot == null)
            return;
        }

        if (!force) {
          gslot.checkNotReadonly();
        }
        if (isSetter) {
            gslot.setter = getterOrSetter;
        } else {
            gslot.getter = getterOrSetter;
        }
        gslot.value = Undefined.instance;
    }

    /**
     * Get the getter or setter for a given property. Used by __lookupGetter__
     * and __lookupSetter__.
     *
     * @param name Name of the object. If nonnull, index must be 0.
     * @param index Index of the object. If nonzero, name must be null.
     * @param isSetter If true, return the setter, otherwise return the getter.
     * @exception IllegalArgumentException if both name and index are nonnull
     *            and nonzero respectively.
     * @return Null if the property does not exist. Otherwise returns either
     *         the getter or the setter for the property, depending on
     *         the value of isSetter (may be undefined if unset).
     */
    public Object getGetterOrSetter(String name, int index, boolean isSetter)
    {
        if (name != null && index != 0)
            throw new IllegalArgumentException(name);
        Slot slot = getSlot(name, index, SLOT_QUERY);
        if (slot == null)
            return null;
        if (slot instanceof GetterSlot) {
            GetterSlot gslot = (GetterSlot)slot;
            Object result = isSetter ? gslot.setter : gslot.getter;
            return result != null ? result : Undefined.instance;
        } else
            return Undefined.instance;
    }

    /**
     * Returns whether a property is a getter or a setter
     * @param name property name
     * @param index property index
     * @param setter true to check for a setter, false for a getter
     * @return whether the property is a getter or a setter
     */
    protected boolean isGetterOrSetter(String name, int index, boolean setter) {
        Slot slot = getSlot(name, index, SLOT_QUERY);
        if (slot instanceof GetterSlot) {
            if (setter && ((GetterSlot)slot).setter != null) return true;
            if (!setter && ((GetterSlot)slot).getter != null) return true;
        }
        return false;
    }

    void addLazilyInitializedValue(String name, int index,
                                   LazilyLoadedCtor init, int attributes)
    {
        if (name != null && index != 0)
            throw new IllegalArgumentException(name);
        checkNotSealed(name, index);
        GetterSlot gslot = (GetterSlot)getSlot(name, index,
                                               SLOT_MODIFY_GETTER_SETTER);
        gslot.setAttributes(attributes);
        gslot.getter = null;
        gslot.setter = null;
        gslot.value = init;
    }

    /**
     * Returns the prototype of the object.
     */
    public Scriptable getPrototype()
    {
        return prototypeObject;
    }

    /**
     * Sets the prototype of the object.
     */
    public void setPrototype(Scriptable m)
    {
        prototypeObject = m;
    }

    /**
     * Returns the parent (enclosing) scope of the object.
     */
    public Scriptable getParentScope()
    {
        return parentScopeObject;
    }

    /**
     * Sets the parent (enclosing) scope of the object.
     */
    public void setParentScope(Scriptable m)
    {
        parentScopeObject = m;
    }

    /**
     * Returns an array of ids for the properties of the object.
     *
     * <p>Any properties with the attribute DONTENUM are not listed. <p>
     *
     * @return an array of java.lang.Objects with an entry for every
     * listed property. Properties accessed via an integer index will
     * have a corresponding
     * Integer entry in the returned array. Properties accessed by
     * a String will have a String entry in the returned array.
     */
    public Object[] getIds() {
        return getIds(false);
    }

    /**
     * Returns an array of ids for the properties of the object.
     *
     * <p>All properties, even those with attribute DONTENUM, are listed. <p>
     *
     * @return an array of java.lang.Objects with an entry for every
     * listed property. Properties accessed via an integer index will
     * have a corresponding
     * Integer entry in the returned array. Properties accessed by
     * a String will have a String entry in the returned array.
     */
    public Object[] getAllIds() {
        return getIds(true);
    }

    /**
     * Implements the [[DefaultValue]] internal method.
     *
     * <p>Note that the toPrimitive conversion is a no-op for
     * every type other than Object, for which [[DefaultValue]]
     * is called. See ECMA 9.1.<p>
     *
     * A <code>hint</code> of null means "no hint".
     *
     * @param typeHint the type hint
     * @return the default value for the object
     *
     * See ECMA 8.6.2.6.
     */
    public Object getDefaultValue(Class<?> typeHint)
    {
        return getDefaultValue(this, typeHint);
    }

    public static Object getDefaultValue(Scriptable object, Class<?> typeHint)
    {
        Context cx = null;
        for (int i=0; i < 2; i++) {
            boolean tryToString;
            if (typeHint == ScriptRuntime.StringClass) {
                tryToString = (i == 0);
            } else {
                tryToString = (i == 1);
            }

            String methodName;
            Object[] args;
            if (tryToString) {
                methodName = "toString";
                args = ScriptRuntime.emptyArgs;
            } else {
                methodName = "valueOf";
                args = new Object[1];
                String hint;
                if (typeHint == null) {
                    hint = "undefined";
                } else if (typeHint == ScriptRuntime.StringClass) {
                    hint = "string";
                } else if (typeHint == ScriptRuntime.ScriptableClass) {
                    hint = "object";
                } else if (typeHint == ScriptRuntime.FunctionClass) {
                    hint = "function";
                } else if (typeHint == ScriptRuntime.BooleanClass
                           || typeHint == Boolean.TYPE)
                {
                    hint = "boolean";
                } else if (typeHint == ScriptRuntime.NumberClass ||
                         typeHint == ScriptRuntime.ByteClass ||
                         typeHint == Byte.TYPE ||
                         typeHint == ScriptRuntime.ShortClass ||
                         typeHint == Short.TYPE ||
                         typeHint == ScriptRuntime.IntegerClass ||
                         typeHint == Integer.TYPE ||
                         typeHint == ScriptRuntime.FloatClass ||
                         typeHint == Float.TYPE ||
                         typeHint == ScriptRuntime.DoubleClass ||
                         typeHint == Double.TYPE)
                {
                    hint = "number";
                } else {
                    throw Context.reportRuntimeError1(
                        "msg.invalid.type", typeHint.toString());
                }
                args[0] = hint;
            }
            Object v = getProperty(object, methodName);
            if (!(v instanceof Function))
                continue;
            Function fun = (Function) v;
            if (cx == null)
                cx = Context.getContext();
            v = fun.call(cx, fun.getParentScope(), object, args);
            if (v != null) {
                if (!(v instanceof Scriptable)) {
                    return v;
                }
                if (typeHint == ScriptRuntime.ScriptableClass
                    || typeHint == ScriptRuntime.FunctionClass)
                {
                    return v;
                }
                if (tryToString && v instanceof Wrapper) {
                    // Let a wrapped java.lang.String pass for a primitive
                    // string.
                    Object u = ((Wrapper)v).unwrap();
                    if (u instanceof String)
                        return u;
                }
            }
        }
        // fall through to error
        String arg = (typeHint == null) ? "undefined" : typeHint.getName();
        throw ScriptRuntime.typeError1("msg.default.value", arg);
    }

    /**
     * Implements the instanceof operator.
     *
     * <p>This operator has been proposed to ECMA.
     *
     * @param instance The value that appeared on the LHS of the instanceof
     *              operator
     * @return true if "this" appears in value's prototype chain
     *
     */
    public boolean hasInstance(Scriptable instance) {
        // Default for JS objects (other than Function) is to do prototype
        // chasing.  This will be overridden in NativeFunction and non-JS
        // objects.

        return ScriptRuntime.jsDelegatesTo(instance, this);
    }

    /**
     * Emulate the SpiderMonkey (and Firefox) feature of allowing
     * custom objects to avoid detection by normal "object detection"
     * code patterns. This is used to implement document.all.
     * See https://bugzilla.mozilla.org/show_bug.cgi?id=412247.
     * This is an analog to JOF_DETECTING from SpiderMonkey; see
     * https://bugzilla.mozilla.org/show_bug.cgi?id=248549.
     * Other than this special case, embeddings should return false.
     * @return true if this object should avoid object detection
     * @since 1.7R1
     */
    public boolean avoidObjectDetection() {
        return false;
    }

    /**
     * Custom <tt>==</tt> operator.
     * Must return {@link Scriptable#NOT_FOUND} if this object does not
     * have custom equality operator for the given value,
     * <tt>Boolean.TRUE</tt> if this object is equivalent to <tt>value</tt>,
     * <tt>Boolean.FALSE</tt> if this object is not equivalent to
     * <tt>value</tt>.
     * <p>
     * The default implementation returns Boolean.TRUE
     * if <tt>this == value</tt> or {@link Scriptable#NOT_FOUND} otherwise.
     * It indicates that by default custom equality is available only if
     * <tt>value</tt> is <tt>this</tt> in which case true is returned.
     */
    protected Object equivalentValues(Object value)
    {
        return (this == value) ? Boolean.TRUE : Scriptable.NOT_FOUND;
    }

    /**
     * Defines JavaScript objects from a Java class that implements Scriptable.
     *
     * If the given class has a method
     * <pre>
     * static void init(Context cx, Scriptable scope, boolean sealed);</pre>
     *
     * or its compatibility form
     * <pre>
     * static void init(Scriptable scope);</pre>
     *
     * then it is invoked and no further initialization is done.<p>
     *
     * However, if no such a method is found, then the class's constructors and
     * methods are used to initialize a class in the following manner.<p>
     *
     * First, the zero-parameter constructor of the class is called to
     * create the prototype. If no such constructor exists,
     * a {@link EvaluatorException} is thrown. <p>
     *
     * Next, all methods are scanned for special prefixes that indicate that they
     * have special meaning for defining JavaScript objects.
     * These special prefixes are
     * <ul>
     * <li><code>jsFunction_</code> for a JavaScript function
     * <li><code>jsStaticFunction_</code> for a JavaScript function that
     *           is a property of the constructor
     * <li><code>jsGet_</code> for a getter of a JavaScript property
     * <li><code>jsSet_</code> for a setter of a JavaScript property
     * <li><code>jsConstructor</code> for a JavaScript function that
     *           is the constructor
     * </ul><p>
     *
     * If the method's name begins with "jsFunction_", a JavaScript function
     * is created with a name formed from the rest of the Java method name
     * following "jsFunction_". So a Java method named "jsFunction_foo" will
     * define a JavaScript method "foo". Calling this JavaScript function
     * will cause the Java method to be called. The parameters of the method
     * must be of number and types as defined by the FunctionObject class.
     * The JavaScript function is then added as a property
     * of the prototype. <p>
     *
     * If the method's name begins with "jsStaticFunction_", it is handled
     * similarly except that the resulting JavaScript function is added as a
     * property of the constructor object. The Java method must be static.
     *
     * If the method's name begins with "jsGet_" or "jsSet_", the method is
     * considered to define a property. Accesses to the defined property
     * will result in calls to these getter and setter methods. If no
     * setter is defined, the property is defined as READONLY.<p>
     *
     * If the method's name is "jsConstructor", the method is
     * considered to define the body of the constructor. Only one
     * method of this name may be defined. You may use the varargs forms
     * for constructors documented in {@link FunctionObject#FunctionObject(String, Member, Scriptable)}
     *
     * If no method is found that can serve as constructor, a Java
     * constructor will be selected to serve as the JavaScript
     * constructor in the following manner. If the class has only one
     * Java constructor, that constructor is used to define
     * the JavaScript constructor. If the the class has two constructors,
     * one must be the zero-argument constructor (otherwise an
     * {@link EvaluatorException} would have already been thrown
     * when the prototype was to be created). In this case
     * the Java constructor with one or more parameters will be used
     * to define the JavaScript constructor. If the class has three
     * or more constructors, an {@link EvaluatorException}
     * will be thrown.<p>
     *
     * Finally, if there is a method
     * <pre>
     * static void finishInit(Scriptable scope, FunctionObject constructor,
     *                        Scriptable prototype)</pre>
     *
     * it will be called to finish any initialization. The <code>scope</code>
     * argument will be passed, along with the newly created constructor and
     * the newly created prototype.<p>
     *
     * @param scope The scope in which to define the constructor.
     * @param clazz The Java class to use to define the JavaScript objects
     *              and properties.
     * @exception IllegalAccessException if access is not available
     *            to a reflected class member
     * @exception InstantiationException if unable to instantiate
     *            the named class
     * @exception InvocationTargetException if an exception is thrown
     *            during execution of methods of the named class
     * @see org.mozilla.javascript.Function
     * @see org.mozilla.javascript.FunctionObject
     * @see org.mozilla.javascript.ScriptableObject#READONLY
     * @see org.mozilla.javascript.ScriptableObject
     *      #defineProperty(String, Class, int)
     */
    public static <T extends Scriptable> void defineClass(
            Scriptable scope, Class<T> clazz)
        throws IllegalAccessException, InstantiationException,
               InvocationTargetException
    {
        defineClass(scope, clazz, false, false);
    }

    /**
     * Defines JavaScript objects from a Java class, optionally
     * allowing sealing.
     *
     * Similar to <code>defineClass(Scriptable scope, Class clazz)</code>
     * except that sealing is allowed. An object that is sealed cannot have
     * properties added or removed. Note that sealing is not allowed in
     * the current ECMA/ISO language specification, but is likely for
     * the next version.
     *
     * @param scope The scope in which to define the constructor.
     * @param clazz The Java class to use to define the JavaScript objects
     *              and properties. The class must implement Scriptable.
     * @param sealed Whether or not to create sealed standard objects that
     *               cannot be modified.
     * @exception IllegalAccessException if access is not available
     *            to a reflected class member
     * @exception InstantiationException if unable to instantiate
     *            the named class
     * @exception InvocationTargetException if an exception is thrown
     *            during execution of methods of the named class
     * @since 1.4R3
     */
    public static <T extends Scriptable> void defineClass(
            Scriptable scope, Class<T> clazz, boolean sealed)
        throws IllegalAccessException, InstantiationException,
               InvocationTargetException
    {
        defineClass(scope, clazz, sealed, false);
    }

    /**
     * Defines JavaScript objects from a Java class, optionally
     * allowing sealing and mapping of Java inheritance to JavaScript
     * prototype-based inheritance.
     *
     * Similar to <code>defineClass(Scriptable scope, Class clazz)</code>
     * except that sealing and inheritance mapping are allowed. An object
     * that is sealed cannot have properties added or removed. Note that
     * sealing is not allowed in the current ECMA/ISO language specification,
     * but is likely for the next version.
     *
     * @param scope The scope in which to define the constructor.
     * @param clazz The Java class to use to define the JavaScript objects
     *              and properties. The class must implement Scriptable.
     * @param sealed Whether or not to create sealed standard objects that
     *               cannot be modified.
     * @param mapInheritance Whether or not to map Java inheritance to
     *                       JavaScript prototype-based inheritance.
     * @return the class name for the prototype of the specified class
     * @exception IllegalAccessException if access is not available
     *            to a reflected class member
     * @exception InstantiationException if unable to instantiate
     *            the named class
     * @exception InvocationTargetException if an exception is thrown
     *            during execution of methods of the named class
     * @since 1.6R2
     */
    public static <T extends Scriptable> String defineClass(
            Scriptable scope, Class<T> clazz, boolean sealed,
            boolean mapInheritance)
        throws IllegalAccessException, InstantiationException,
               InvocationTargetException
    {
        BaseFunction ctor = buildClassCtor(scope, clazz, sealed,
                                           mapInheritance);
        if (ctor == null)
            return null;
        String name = ctor.getClassPrototype().getClassName();
        defineProperty(scope, name, ctor, ScriptableObject.DONTENUM);
        return name;
    }

    static <T extends Scriptable> BaseFunction buildClassCtor(
            Scriptable scope, Class<T> clazz,
            boolean sealed,
            boolean mapInheritance)
        throws IllegalAccessException, InstantiationException,
               InvocationTargetException
    {
        Method[] methods = FunctionObject.getMethodList(clazz);
        for (int i=0; i < methods.length; i++) {
            Method method = methods[i];
            if (!method.getName().equals("init"))
                continue;
            Class<?>[] parmTypes = method.getParameterTypes();
            if (parmTypes.length == 3 &&
                parmTypes[0] == ScriptRuntime.ContextClass &&
                parmTypes[1] == ScriptRuntime.ScriptableClass &&
                parmTypes[2] == Boolean.TYPE &&
                Modifier.isStatic(method.getModifiers()))
            {
                Object args[] = { Context.getContext(), scope,
                                  sealed ? Boolean.TRUE : Boolean.FALSE };
                method.invoke(null, args);
                return null;
            }
            if (parmTypes.length == 1 &&
                parmTypes[0] == ScriptRuntime.ScriptableClass &&
                Modifier.isStatic(method.getModifiers()))
            {
                Object args[] = { scope };
                method.invoke(null, args);
                return null;
            }

        }

        // If we got here, there isn't an "init" method with the right
        // parameter types.

        Constructor<?>[] ctors = clazz.getConstructors();
        Constructor<?> protoCtor = null;
        for (int i=0; i < ctors.length; i++) {
            if (ctors[i].getParameterTypes().length == 0) {
                protoCtor = ctors[i];
                break;
            }
        }
        if (protoCtor == null) {
            throw Context.reportRuntimeError1(
                      "msg.zero.arg.ctor", clazz.getName());
        }

        Scriptable proto = (Scriptable) protoCtor.newInstance(ScriptRuntime.emptyArgs);
        String className = proto.getClassName();

        // Set the prototype's prototype, trying to map Java inheritance to JS
        // prototype-based inheritance if requested to do so.
        Scriptable superProto = null;
        if (mapInheritance) {
            Class<? super T> superClass = clazz.getSuperclass();
            if (ScriptRuntime.ScriptableClass.isAssignableFrom(superClass) &&
                !Modifier.isAbstract(superClass.getModifiers()))
            {
                Class<? extends Scriptable> superScriptable =
                    extendsScriptable(superClass);
                String name = ScriptableObject.defineClass(scope,
                        superScriptable, sealed, mapInheritance);
                if (name != null) {
                    superProto = ScriptableObject.getClassPrototype(scope, name);
                }
            }
        }
        if (superProto == null) {
            superProto = ScriptableObject.getObjectPrototype(scope);
        }
        proto.setPrototype(superProto);

        // Find out whether there are any methods that begin with
        // "js". If so, then only methods that begin with special
        // prefixes will be defined as JavaScript entities.
        final String functionPrefix = "jsFunction_";
        final String staticFunctionPrefix = "jsStaticFunction_";
        final String getterPrefix = "jsGet_";
        final String setterPrefix = "jsSet_";
        final String ctorName = "jsConstructor";

        Member ctorMember = findAnnotatedMember(methods, JSConstructor.class);
        if (ctorMember == null) {
            ctorMember = findAnnotatedMember(ctors, JSConstructor.class);
        }
        if (ctorMember == null) {
            ctorMember = FunctionObject.findSingleMethod(methods, ctorName);
        }
        if (ctorMember == null) {
            if (ctors.length == 1) {
                ctorMember = ctors[0];
            } else if (ctors.length == 2) {
                if (ctors[0].getParameterTypes().length == 0)
                    ctorMember = ctors[1];
                else if (ctors[1].getParameterTypes().length == 0)
                    ctorMember = ctors[0];
            }
            if (ctorMember == null) {
                throw Context.reportRuntimeError1(
                          "msg.ctor.multiple.parms", clazz.getName());
            }
        }

        FunctionObject ctor = new FunctionObject(className, ctorMember, scope);
        if (ctor.isVarArgsMethod()) {
            throw Context.reportRuntimeError1
                ("msg.varargs.ctor", ctorMember.getName());
        }
        ctor.initAsConstructor(scope, proto);

        Method finishInit = null;
        HashSet<String> names = new HashSet<String>(methods.length);
        for (Method method : methods) {
            if (method == ctorMember) {
                continue;
            }
            String name = method.getName();
            if (name.equals("finishInit")) {
                Class<?>[] parmTypes = method.getParameterTypes();
                if (parmTypes.length == 3 &&
                    parmTypes[0] == ScriptRuntime.ScriptableClass &&
                    parmTypes[1] == FunctionObject.class &&
                    parmTypes[2] == ScriptRuntime.ScriptableClass &&
                    Modifier.isStatic(method.getModifiers()))
                {
                    finishInit = method;
                    continue;
                }
            }
            // ignore any compiler generated methods.
            if (name.indexOf('$') != -1)
                continue;
            if (name.equals(ctorName))
                continue;

            Annotation annotation = null;
            String prefix = null;
            if (method.isAnnotationPresent(JSFunction.class)) {
                annotation = method.getAnnotation(JSFunction.class);
            } else if (method.isAnnotationPresent(JSStaticFunction.class)) {
                annotation = method.getAnnotation(JSStaticFunction.class);
            } else if (method.isAnnotationPresent(JSGetter.class)) {
                annotation = method.getAnnotation(JSGetter.class);
            } else if (method.isAnnotationPresent(JSSetter.class)) {
                continue;
            }

            if (annotation == null) {
                if (name.startsWith(functionPrefix)) {
                    prefix = functionPrefix;
                } else if (name.startsWith(staticFunctionPrefix)) {
                    prefix = staticFunctionPrefix;
                } else if (name.startsWith(getterPrefix)) {
                    prefix = getterPrefix;
                } else if (annotation == null) {
                    // note that setterPrefix is among the unhandled names here -
                    // we deal with that when we see the getter
                    continue;
                }
            }
            String propName = getPropertyName(name, prefix, annotation);
            if (names.contains(propName)) {
                throw Context.reportRuntimeError2("duplicate.defineClass.name",
                        name, propName);
            }
            names.add(propName);
            name = propName;
            if (annotation instanceof JSGetter || prefix == getterPrefix) {
                if (!(proto instanceof ScriptableObject)) {
                    throw Context.reportRuntimeError2(
                        "msg.extend.scriptable",
                        proto.getClass().toString(), name);
                }
                Method setter = findSetterMethod(methods, name, setterPrefix);
                int attr = ScriptableObject.PERMANENT |
                           ScriptableObject.DONTENUM  |
                           (setter != null ? 0
                                           : ScriptableObject.READONLY);
                ((ScriptableObject) proto).defineProperty(name, null,
                                                          method, setter,
                                                          attr);
                continue;
            }

            boolean isStatic = annotation instanceof JSStaticFunction
                    || prefix == staticFunctionPrefix;
            if (isStatic && !Modifier.isStatic(method.getModifiers())) {
                throw Context.reportRuntimeError(
                        "jsStaticFunction must be used with static method.");
            }

            FunctionObject f = new FunctionObject(name, method, proto);
            if (f.isVarArgsConstructor()) {
                throw Context.reportRuntimeError1
                    ("msg.varargs.fun", ctorMember.getName());
            }
            defineProperty(isStatic ? ctor : proto, name, f, DONTENUM);
            if (sealed) {
                f.sealObject();
            }
        }

        // Call user code to complete initialization if necessary.
        if (finishInit != null) {
            Object[] finishArgs = { scope, ctor, proto };
            finishInit.invoke(null, finishArgs);
        }

        // Seal the object if necessary.
        if (sealed) {
            ctor.sealObject();
            if (proto instanceof ScriptableObject) {
                ((ScriptableObject) proto).sealObject();
            }
        }

        return ctor;
    }

    private static Member findAnnotatedMember(AccessibleObject[] members,
                                              Class<? extends Annotation> annotation) {
        for (AccessibleObject member : members) {
            if (member.isAnnotationPresent(annotation)) {
                return (Member) member;
            }
        }
        return null;
    }

    private static Method findSetterMethod(Method[] methods,
                                           String name,
                                           String prefix) {
        String newStyleName = "set"
                + Character.toUpperCase(name.charAt(0))
                + name.substring(1);
        for (Method method : methods) {
            JSSetter annotation = method.getAnnotation(JSSetter.class);
            if (annotation != null) {
                if (name.equals(annotation.value()) ||
                        ("".equals(annotation.value()) && newStyleName.equals(method.getName()))) {
                    return method;
                }
            }
        }
        String oldStyleName = prefix + name;
        for (Method method : methods) {
            if (oldStyleName.equals(method.getName())) {
                return method;
            }
        }
        return null;
    }

    private static String getPropertyName(String methodName,
                                          String prefix,
                                          Annotation annotation) {
        if (prefix != null) {
            return methodName.substring(prefix.length());
        }
        String propName = null;
        if (annotation instanceof JSGetter) {
            propName = ((JSGetter) annotation).value();
            if (propName == null || propName.length() == 0) {
                if (methodName.length() > 3 && methodName.startsWith("get")) {
                    propName = methodName.substring(3);
                    if (Character.isUpperCase(propName.charAt(0))) {
                        if (propName.length() == 1) {
                            propName = propName.toLowerCase();
                        } else if (!Character.isUpperCase(propName.charAt(1))){
                            propName = Character.toLowerCase(propName.charAt(0))
                                    + propName.substring(1);
                        }
                    }
                }
            }
        } else if (annotation instanceof JSFunction) {
            propName = ((JSFunction) annotation).value();
        } else if (annotation instanceof JSStaticFunction) {
            propName = ((JSStaticFunction) annotation).value();
        }
        if (propName == null || propName.length() == 0) {
            propName = methodName;
        }
        return propName;
    }

    @SuppressWarnings({"unchecked"})
    private static <T extends Scriptable> Class<T> extendsScriptable(Class<?> c)
    {
        if (ScriptRuntime.ScriptableClass.isAssignableFrom(c))
            return (Class<T>) c;
        return null;
    }

    /**
     * Define a JavaScript property.
     *
     * Creates the property with an initial value and sets its attributes.
     *
     * @param propertyName the name of the property to define.
     * @param value the initial value of the property
     * @param attributes the attributes of the JavaScript property
     * @see org.mozilla.javascript.Scriptable#put(String, Scriptable, Object)
     */
    public void defineProperty(String propertyName, Object value,
                               int attributes)
    {
        checkNotSealed(propertyName, 0);
        put(propertyName, this, value);
        setAttributes(propertyName, attributes);
    }

    /**
     * Utility method to add properties to arbitrary Scriptable object.
     * If destination is instance of ScriptableObject, calls
     * defineProperty there, otherwise calls put in destination
     * ignoring attributes
     */
    public static void defineProperty(Scriptable destination,
                                      String propertyName, Object value,
                                      int attributes)
    {
        if (!(destination instanceof ScriptableObject)) {
            destination.put(propertyName, destination, value);
            return;
        }
        ScriptableObject so = (ScriptableObject)destination;
        so.defineProperty(propertyName, value, attributes);
    }

    /**
     * Utility method to add properties to arbitrary Scriptable object.
     * If destination is instance of ScriptableObject, calls
     * defineProperty there, otherwise calls put in destination
     * ignoring attributes
     */
    public static void defineConstProperty(Scriptable destination,
                                           String propertyName)
    {
        if (destination instanceof ConstProperties) {
            ConstProperties cp = (ConstProperties)destination;
            cp.defineConst(propertyName, destination);
        } else
            defineProperty(destination, propertyName, Undefined.instance, CONST);
    }

    /**
     * Define a JavaScript property with getter and setter side effects.
     *
     * If the setter is not found, the attribute READONLY is added to
     * the given attributes. <p>
     *
     * The getter must be a method with zero parameters, and the setter, if
     * found, must be a method with one parameter.<p>
     *
     * @param propertyName the name of the property to define. This name
     *                    also affects the name of the setter and getter
     *                    to search for. If the propertyId is "foo", then
     *                    <code>clazz</code> will be searched for "getFoo"
     *                    and "setFoo" methods.
     * @param clazz the Java class to search for the getter and setter
     * @param attributes the attributes of the JavaScript property
     * @see org.mozilla.javascript.Scriptable#put(String, Scriptable, Object)
     */
    public void defineProperty(String propertyName, Class<?> clazz,
                               int attributes)
    {
        int length = propertyName.length();
        if (length == 0) throw new IllegalArgumentException();
        char[] buf = new char[3 + length];
        propertyName.getChars(0, length, buf, 3);
        buf[3] = Character.toUpperCase(buf[3]);
        buf[0] = 'g';
        buf[1] = 'e';
        buf[2] = 't';
        String getterName = new String(buf);
        buf[0] = 's';
        String setterName = new String(buf);

        Method[] methods = FunctionObject.getMethodList(clazz);
        Method getter = FunctionObject.findSingleMethod(methods, getterName);
        Method setter = FunctionObject.findSingleMethod(methods, setterName);
        if (setter == null)
            attributes |= ScriptableObject.READONLY;
        defineProperty(propertyName, null, getter,
                       setter == null ? null : setter, attributes);
    }

    /**
     * Define a JavaScript property.
     *
     * Use this method only if you wish to define getters and setters for
     * a given property in a ScriptableObject. To create a property without
     * special getter or setter side effects, use
     * <code>defineProperty(String,int)</code>.
     *
     * If <code>setter</code> is null, the attribute READONLY is added to
     * the given attributes.<p>
     *
     * Several forms of getters or setters are allowed. In all cases the
     * type of the value parameter can be any one of the following types:
     * Object, String, boolean, Scriptable, byte, short, int, long, float,
     * or double. The runtime will perform appropriate conversions based
     * upon the type of the parameter (see description in FunctionObject).
     * The first forms are nonstatic methods of the class referred to
     * by 'this':
     * <pre>
     * Object getFoo();
     * void setFoo(SomeType value);</pre>
     * Next are static methods that may be of any class; the object whose
     * property is being accessed is passed in as an extra argument:
     * <pre>
     * static Object getFoo(Scriptable obj);
     * static void setFoo(Scriptable obj, SomeType value);</pre>
     * Finally, it is possible to delegate to another object entirely using
     * the <code>delegateTo</code> parameter. In this case the methods are
     * nonstatic methods of the class delegated to, and the object whose
     * property is being accessed is passed in as an extra argument:
     * <pre>
     * Object getFoo(Scriptable obj);
     * void setFoo(Scriptable obj, SomeType value);</pre>
     *
     * @param propertyName the name of the property to define.
     * @param delegateTo an object to call the getter and setter methods on,
     *                   or null, depending on the form used above.
     * @param getter the method to invoke to get the value of the property
     * @param setter the method to invoke to set the value of the property
     * @param attributes the attributes of the JavaScript property
     */
    public void defineProperty(String propertyName, Object delegateTo,
                               Method getter, Method setter, int attributes)
    {
        MemberBox getterBox = null;
        if (getter != null) {
            getterBox = new MemberBox(getter);

            boolean delegatedForm;
            if (!Modifier.isStatic(getter.getModifiers())) {
                delegatedForm = (delegateTo != null);
                getterBox.delegateTo = delegateTo;
            } else {
                delegatedForm = true;
                // Ignore delegateTo for static getter but store
                // non-null delegateTo indicator.
                getterBox.delegateTo = Void.TYPE;
            }

            String errorId = null;
            Class<?>[] parmTypes = getter.getParameterTypes();
            if (parmTypes.length == 0) {
                if (delegatedForm) {
                    errorId = "msg.obj.getter.parms";
                }
            } else if (parmTypes.length == 1) {
                Object argType = parmTypes[0];
                // Allow ScriptableObject for compatibility
                if (!(argType == ScriptRuntime.ScriptableClass ||
                      argType == ScriptRuntime.ScriptableObjectClass))
                {
                    errorId = "msg.bad.getter.parms";
                } else if (!delegatedForm) {
                    errorId = "msg.bad.getter.parms";
                }
            } else {
                errorId = "msg.bad.getter.parms";
            }
            if (errorId != null) {
                throw Context.reportRuntimeError1(errorId, getter.toString());
            }
        }

        MemberBox setterBox = null;
        if (setter != null) {
            if (setter.getReturnType() != Void.TYPE)
                throw Context.reportRuntimeError1("msg.setter.return",
                                                  setter.toString());

            setterBox = new MemberBox(setter);

            boolean delegatedForm;
            if (!Modifier.isStatic(setter.getModifiers())) {
                delegatedForm = (delegateTo != null);
                setterBox.delegateTo = delegateTo;
            } else {
                delegatedForm = true;
                // Ignore delegateTo for static setter but store
                // non-null delegateTo indicator.
                setterBox.delegateTo = Void.TYPE;
            }

            String errorId = null;
            Class<?>[] parmTypes = setter.getParameterTypes();
            if (parmTypes.length == 1) {
                if (delegatedForm) {
                    errorId = "msg.setter2.expected";
                }
            } else if (parmTypes.length == 2) {
                Object argType = parmTypes[0];
                // Allow ScriptableObject for compatibility
                if (!(argType == ScriptRuntime.ScriptableClass ||
                      argType == ScriptRuntime.ScriptableObjectClass))
                {
                    errorId = "msg.setter2.parms";
                } else if (!delegatedForm) {
                    errorId = "msg.setter1.parms";
                }
            } else {
                errorId = "msg.setter.parms";
            }
            if (errorId != null) {
                throw Context.reportRuntimeError1(errorId, setter.toString());
            }
        }

        GetterSlot gslot = (GetterSlot)getSlot(propertyName, 0,
                                               SLOT_MODIFY_GETTER_SETTER);
        gslot.setAttributes(attributes);
        gslot.getter = getterBox;
        gslot.setter = setterBox;
    }

    public void defineOwnProperties(Context cx, ScriptableObject props) {
        Object[] ids = props.getIds();
        for (Object id : ids) {
            String name = ScriptRuntime.toString(id);
            Object descObj = props.get(id);
            ScriptableObject desc = ensureScriptableObject(descObj);
            checkValidPropertyDefinition(getSlot(name, 0, SLOT_QUERY), desc);
        }
        for (Object id : ids) {
            String name = ScriptRuntime.toString(id);
            ScriptableObject desc = (ScriptableObject) props.get(id);
            defineOwnProperty(cx, name, desc, false);
        }
    }

    /**
     * Defines a property on an object
     *
     * Based on [[DefineOwnProperty]] from 8.12.10 of the spec
     *
     * @param cx the current Context
     * @param id the name/index of the property
     * @param desc the new property descriptor, as described in 8.6.1
     */
    public void defineOwnProperty(Context cx, Object id, ScriptableObject desc) {
      defineOwnProperty(cx, id, desc, true);
    }

    private void defineOwnProperty(Context cx, Object id, ScriptableObject desc, boolean checkValid) {
      Slot slot = getSlot(cx, id, SLOT_QUERY);

      if (checkValid)
        checkValidPropertyDefinition(slot, desc);

      final int attributes;
      if (slot == null) { // new slot
        slot = getSlot(cx, id, SLOT_MODIFY);
        attributes = applyDescriptorToAttributeBitset(DONTENUM|READONLY|PERMANENT, desc);
      } else {
        attributes = applyDescriptorToAttributeBitset(slot.getAttributes(), desc);
      }

      defineOwnProperty(cx, slot, desc, attributes);
    }

    private void defineOwnProperty(Context cx, Slot slot, ScriptableObject desc, int attributes) {
      if (isAccessorDescriptor(desc)) {
        if ( !(slot instanceof GetterSlot) )
          slot = getSlot(cx, slot.name, SLOT_MODIFY_GETTER_SETTER);

        GetterSlot gslot = (GetterSlot) slot;

        Object getter = getProperty(desc, "get");
        if (getter != NOT_FOUND) {
            gslot.getter = getter;
        }
        Object setter = getProperty(desc, "set");
        if (setter != NOT_FOUND) {
            gslot.setter = setter;
        }

        gslot.value = Undefined.instance;
        gslot.setAttributes(attributes);
      } else {
        if (slot instanceof GetterSlot && isDataDescriptor(desc)) {
            slot = getSlot(cx, slot.name, SLOT_CONVERT_ACCESSOR_TO_DATA);
        }

        Object value = getProperty(desc, "value");
        if (value != NOT_FOUND) {
            slot.value = value;
        }
        slot.setAttributes(attributes);
      }
    }

    private void checkValidPropertyDefinition(Slot slot, ScriptableObject desc) {
      Object getter = getProperty(desc, "get");
      if (getter != NOT_FOUND && getter != Undefined.instance && !(getter instanceof Callable)) {
        throw ScriptRuntime.notFunctionError(getter);
      }
      Object setter = getProperty(desc, "set");
      if (setter != NOT_FOUND && setter != Undefined.instance && !(setter instanceof Callable)) {
        throw ScriptRuntime.notFunctionError(setter);
      }
      if (isDataDescriptor(desc) && isAccessorDescriptor(desc)) {
         throw ScriptRuntime.typeError0("msg.both.data.and.accessor.desc");
      }

      if (slot == null) { // new property
        if (!isExtensible()) throw ScriptRuntime.typeError("msg.not.extensible");
      } else {
        String name = slot.name;
        ScriptableObject current = getOwnPropertyDescriptor(Context.getContext(), name);
        if (isFalse(current.get("configurable", current))) {
          if (isTrue(getProperty(desc, "configurable")))
            throw ScriptRuntime.typeError1("msg.change.configurable.false.to.true", name);
          if (isTrue(current.get("enumerable", current)) != isTrue(getProperty(desc, "enumerable")))
            throw ScriptRuntime.typeError1("msg.change.enumerable.with.configurable.false", name);

          if (isGenericDescriptor(desc)) {
            // no further validation required
          } else if (isDataDescriptor(desc) && isDataDescriptor(current)) {
            if (isFalse(current.get("writable", current))) {
              if (isTrue(getProperty(desc, "writable")))
                throw ScriptRuntime.typeError1("msg.change.writable.false.to.true.with.configurable.false", name);

              if (changes(current.get("value", current), getProperty(desc, "value")))
                throw ScriptRuntime.typeError1("msg.change.value.with.writable.false", name);
            }
          } else if (isAccessorDescriptor(desc) && isAccessorDescriptor(current)) {
              if (changes(current.get("set", current), setter))
                throw ScriptRuntime.typeError1("msg.change.setter.with.configurable.false", name);

              if (changes(current.get("get", current), getter))
                throw ScriptRuntime.typeError1("msg.change.getter.with.configurable.false", name);
          } else {
            if (isDataDescriptor(current))
              throw ScriptRuntime.typeError1("msg.change.property.data.to.accessor.with.configurable.false", name);
            else
              throw ScriptRuntime.typeError1("msg.change.property.accessor.to.data.with.configurable.false", name);
          }
        }
      }
    }

    private static boolean isTrue(Object value) {
      return (value == NOT_FOUND) ? false : ScriptRuntime.toBoolean(value);
    }

    private static boolean isFalse(Object value) {
      return !isTrue(value);
    }

    private boolean changes(Object currentValue, Object newValue) {
      if (newValue == NOT_FOUND) return false;
      if (currentValue == NOT_FOUND) {
        currentValue = Undefined.instance;
      }
      return !ScriptRuntime.shallowEq(currentValue, newValue);
    }

    protected int applyDescriptorToAttributeBitset(int attributes,
                                                 ScriptableObject desc)
    {
      Object enumerable = getProperty(desc, "enumerable");
      if (enumerable != NOT_FOUND) {
        attributes = ScriptRuntime.toBoolean(enumerable)
            ? attributes & ~DONTENUM : attributes | DONTENUM;
      }

      Object writable = getProperty(desc, "writable");
      if (writable != NOT_FOUND) {
        attributes = ScriptRuntime.toBoolean(writable)
            ? attributes & ~READONLY : attributes | READONLY;
      }

      Object configurable = getProperty(desc, "configurable");
      if (configurable != NOT_FOUND) {
        attributes = ScriptRuntime.toBoolean(configurable)
            ? attributes & ~PERMANENT : attributes | PERMANENT;
      }

      return attributes;
    }

    protected boolean isDataDescriptor(ScriptableObject desc) {
      return hasProperty(desc, "value") || hasProperty(desc, "writable");
    }

    protected boolean isAccessorDescriptor(ScriptableObject desc) {
      return hasProperty(desc, "get") || hasProperty(desc, "set");
    }

    protected boolean isGenericDescriptor(ScriptableObject desc) {
      return !isDataDescriptor(desc) && !isAccessorDescriptor(desc);
    }

    protected Scriptable ensureScriptable(Object arg) {
      if ( !(arg instanceof Scriptable) )
        throw ScriptRuntime.typeError1("msg.arg.not.object", ScriptRuntime.typeof(arg));
      return (Scriptable) arg;
    }

    protected ScriptableObject ensureScriptableObject(Object arg) {
      if ( !(arg instanceof ScriptableObject) )
        throw ScriptRuntime.typeError1("msg.arg.not.object", ScriptRuntime.typeof(arg));
      return (ScriptableObject) arg;
    }

    /**
     * Search for names in a class, adding the resulting methods
     * as properties.
     *
     * <p> Uses reflection to find the methods of the given names. Then
     * FunctionObjects are constructed from the methods found, and
     * are added to this object as properties with the given names.
     *
     * @param names the names of the Methods to add as function properties
     * @param clazz the class to search for the Methods
     * @param attributes the attributes of the new properties
     * @see org.mozilla.javascript.FunctionObject
     */
    public void defineFunctionProperties(String[] names, Class<?> clazz,
                                         int attributes)
    {
        Method[] methods = FunctionObject.getMethodList(clazz);
        for (int i=0; i < names.length; i++) {
            String name = names[i];
            Method m = FunctionObject.findSingleMethod(methods, name);
            if (m == null) {
                throw Context.reportRuntimeError2(
                    "msg.method.not.found", name, clazz.getName());
            }
            FunctionObject f = new FunctionObject(name, m, this);
            defineProperty(name, f, attributes);
        }
    }

    /**
     * Get the Object.prototype property.
     * See ECMA 15.2.4.
     */
    public static Scriptable getObjectPrototype(Scriptable scope) {
        return getClassPrototype(scope, "Object");
    }

    /**
     * Get the Function.prototype property.
     * See ECMA 15.3.4.
     */
    public static Scriptable getFunctionPrototype(Scriptable scope) {
        return getClassPrototype(scope, "Function");
    }

    /**
     * Get the prototype for the named class.
     *
     * For example, <code>getClassPrototype(s, "Date")</code> will first
     * walk up the parent chain to find the outermost scope, then will
     * search that scope for the Date constructor, and then will
     * return Date.prototype. If any of the lookups fail, or
     * the prototype is not a JavaScript object, then null will
     * be returned.
     *
     * @param scope an object in the scope chain
     * @param className the name of the constructor
     * @return the prototype for the named class, or null if it
     *         cannot be found.
     */
    public static Scriptable getClassPrototype(Scriptable scope,
                                               String className)
    {
        scope = getTopLevelScope(scope);
        Object ctor = getProperty(scope, className);
        Object proto;
        if (ctor instanceof BaseFunction) {
            proto = ((BaseFunction)ctor).getPrototypeProperty();
        } else if (ctor instanceof Scriptable) {
            Scriptable ctorObj = (Scriptable)ctor;
            proto = ctorObj.get("prototype", ctorObj);
        } else {
            return null;
        }
        if (proto instanceof Scriptable) {
            return (Scriptable)proto;
        }
        return null;
    }

    /**
     * Get the global scope.
     *
     * <p>Walks the parent scope chain to find an object with a null
     * parent scope (the global object).
     *
     * @param obj a JavaScript object
     * @return the corresponding global scope
     */
    public static Scriptable getTopLevelScope(Scriptable obj)
    {
        for (;;) {
            Scriptable parent = obj.getParentScope();
            if (parent == null) {
                return obj;
            }
            obj = parent;
        }
    }

    public boolean isExtensible() {
      return isExtensible;
    }

    public void preventExtensions() {
      isExtensible = false;
    }

    /**
     * Seal this object.
     *
     * It is an error to add properties to or delete properties from
     * a sealed object. It is possible to change the value of an
     * existing property. Once an object is sealed it may not be unsealed.
     *
     * @since 1.4R3
     */
    public synchronized void sealObject() {
        if (count >= 0) {
            // Make sure all LazilyLoadedCtors are initialized before sealing.
            Slot slot = firstAdded;
            while (slot != null) {
                if (slot.value instanceof LazilyLoadedCtor) {
                    LazilyLoadedCtor initializer = (LazilyLoadedCtor) slot.value;
                    try {
                        initializer.init();
                    } finally {
                        slot.value = initializer.getValue();
                    }
                }
                slot = slot.orderedNext;
            }
            count = ~count;
        }
    }

    /**
     * Return true if this object is sealed.
     *
     * @return true if sealed, false otherwise.
     * @since 1.4R3
     * @see #sealObject()
     */
    public final boolean isSealed() {
        return count < 0;
    }

    private void checkNotSealed(String name, int index)
    {
        if (!isSealed())
            return;

        String str = (name != null) ? name : Integer.toString(index);
        throw Context.reportRuntimeError1("msg.modify.sealed", str);
    }

    /**
     * Gets a named property from an object or any object in its prototype chain.
     * <p>
     * Searches the prototype chain for a property named <code>name</code>.
     * <p>
     * @param obj a JavaScript object
     * @param name a property name
     * @return the value of a property with name <code>name</code> found in
     *         <code>obj</code> or any object in its prototype chain, or
     *         <code>Scriptable.NOT_FOUND</code> if not found
     * @since 1.5R2
     */
    public static Object getProperty(Scriptable obj, String name)
    {
        Scriptable start = obj;
        Object result;
        do {
            result = obj.get(name, start);
            if (result != Scriptable.NOT_FOUND)
                break;
            obj = obj.getPrototype();
        } while (obj != null);
        return result;
    }

    /**
     * Gets an indexed property from an object or any object in its prototype chain.
     * <p>
     * Searches the prototype chain for a property with integral index
     * <code>index</code>. Note that if you wish to look for properties with numerical
     * but non-integral indicies, you should use getProperty(Scriptable,String) with
     * the string value of the index.
     * <p>
     * @param obj a JavaScript object
     * @param index an integral index
     * @return the value of a property with index <code>index</code> found in
     *         <code>obj</code> or any object in its prototype chain, or
     *         <code>Scriptable.NOT_FOUND</code> if not found
     * @since 1.5R2
     */
    public static Object getProperty(Scriptable obj, int index)
    {
        Scriptable start = obj;
        Object result;
        do {
            result = obj.get(index, start);
            if (result != Scriptable.NOT_FOUND)
                break;
            obj = obj.getPrototype();
        } while (obj != null);
        return result;
    }

    /**
     * Returns whether a named property is defined in an object or any object
     * in its prototype chain.
     * <p>
     * Searches the prototype chain for a property named <code>name</code>.
     * <p>
     * @param obj a JavaScript object
     * @param name a property name
     * @return the true if property was found
     * @since 1.5R2
     */
    public static boolean hasProperty(Scriptable obj, String name)
    {
        return null != getBase(obj, name);
    }

    /**
     * If hasProperty(obj, name) would return true, then if the property that
     * was found is compatible with the new property, this method just returns.
     * If the property is not compatible, then an exception is thrown.
     *
     * A property redefinition is incompatible if the first definition was a
     * const declaration or if this one is.  They are compatible only if neither
     * was const.
     */
    public static void redefineProperty(Scriptable obj, String name,
                                        boolean isConst)
    {
        Scriptable base = getBase(obj, name);
        if (base == null)
            return;
        if (base instanceof ConstProperties) {
            ConstProperties cp = (ConstProperties)base;

            if (cp.isConst(name))
                throw Context.reportRuntimeError1("msg.const.redecl", name);
        }
        if (isConst)
            throw Context.reportRuntimeError1("msg.var.redecl", name);
    }
    /**
     * Returns whether an indexed property is defined in an object or any object
     * in its prototype chain.
     * <p>
     * Searches the prototype chain for a property with index <code>index</code>.
     * <p>
     * @param obj a JavaScript object
     * @param index a property index
     * @return the true if property was found
     * @since 1.5R2
     */
    public static boolean hasProperty(Scriptable obj, int index)
    {
        return null != getBase(obj, index);
    }

    /**
     * Puts a named property in an object or in an object in its prototype chain.
     * <p>
     * Searches for the named property in the prototype chain. If it is found,
     * the value of the property in <code>obj</code> is changed through a call
     * to {@link Scriptable#put(String, Scriptable, Object)} on the
     * prototype passing <code>obj</code> as the <code>start</code> argument.
     * This allows the prototype to veto the property setting in case the
     * prototype defines the property with [[ReadOnly]] attribute. If the
     * property is not found, it is added in <code>obj</code>.
     * @param obj a JavaScript object
     * @param name a property name
     * @param value any JavaScript value accepted by Scriptable.put
     * @since 1.5R2
     */
    public static void putProperty(Scriptable obj, String name, Object value)
    {
        Scriptable base = getBase(obj, name);
        if (base == null)
            base = obj;
        base.put(name, obj, value);
    }

    /**
     * Puts a named property in an object or in an object in its prototype chain.
     * <p>
     * Searches for the named property in the prototype chain. If it is found,
     * the value of the property in <code>obj</code> is changed through a call
     * to {@link Scriptable#put(String, Scriptable, Object)} on the
     * prototype passing <code>obj</code> as the <code>start</code> argument.
     * This allows the prototype to veto the property setting in case the
     * prototype defines the property with [[ReadOnly]] attribute. If the
     * property is not found, it is added in <code>obj</code>.
     * @param obj a JavaScript object
     * @param name a property name
     * @param value any JavaScript value accepted by Scriptable.put
     * @since 1.5R2
     */
    public static void putConstProperty(Scriptable obj, String name, Object value)
    {
        Scriptable base = getBase(obj, name);
        if (base == null)
            base = obj;
        if (base instanceof ConstProperties)
            ((ConstProperties)base).putConst(name, obj, value);
    }

    /**
     * Puts an indexed property in an object or in an object in its prototype chain.
     * <p>
     * Searches for the indexed property in the prototype chain. If it is found,
     * the value of the property in <code>obj</code> is changed through a call
     * to {@link Scriptable#put(int, Scriptable, Object)} on the prototype
     * passing <code>obj</code> as the <code>start</code> argument. This allows
     * the prototype to veto the property setting in case the prototype defines
     * the property with [[ReadOnly]] attribute. If the property is not found,
     * it is added in <code>obj</code>.
     * @param obj a JavaScript object
     * @param index a property index
     * @param value any JavaScript value accepted by Scriptable.put
     * @since 1.5R2
     */
    public static void putProperty(Scriptable obj, int index, Object value)
    {
        Scriptable base = getBase(obj, index);
        if (base == null)
            base = obj;
        base.put(index, obj, value);
    }

    /**
     * Removes the property from an object or its prototype chain.
     * <p>
     * Searches for a property with <code>name</code> in obj or
     * its prototype chain. If it is found, the object's delete
     * method is called.
     * @param obj a JavaScript object
     * @param name a property name
     * @return true if the property doesn't exist or was successfully removed
     * @since 1.5R2
     */
    public static boolean deleteProperty(Scriptable obj, String name)
    {
        Scriptable base = getBase(obj, name);
        if (base == null)
            return true;
        base.delete(name);
        return !base.has(name, obj);
    }

    /**
     * Removes the property from an object or its prototype chain.
     * <p>
     * Searches for a property with <code>index</code> in obj or
     * its prototype chain. If it is found, the object's delete
     * method is called.
     * @param obj a JavaScript object
     * @param index a property index
     * @return true if the property doesn't exist or was successfully removed
     * @since 1.5R2
     */
    public static boolean deleteProperty(Scriptable obj, int index)
    {
        Scriptable base = getBase(obj, index);
        if (base == null)
            return true;
        base.delete(index);
        return !base.has(index, obj);
    }

    /**
     * Returns an array of all ids from an object and its prototypes.
     * <p>
     * @param obj a JavaScript object
     * @return an array of all ids from all object in the prototype chain.
     *         If a given id occurs multiple times in the prototype chain,
     *         it will occur only once in this list.
     * @since 1.5R2
     */
    public static Object[] getPropertyIds(Scriptable obj)
    {
        if (obj == null) {
            return ScriptRuntime.emptyArgs;
        }
        Object[] result = obj.getIds();
        ObjToIntMap map = null;
        for (;;) {
            obj = obj.getPrototype();
            if (obj == null) {
                break;
            }
            Object[] ids = obj.getIds();
            if (ids.length == 0) {
                continue;
            }
            if (map == null) {
                if (result.length == 0) {
                    result = ids;
                    continue;
                }
                map = new ObjToIntMap(result.length + ids.length);
                for (int i = 0; i != result.length; ++i) {
                    map.intern(result[i]);
                }
                result = null; // Allow to GC the result
            }
            for (int i = 0; i != ids.length; ++i) {
                map.intern(ids[i]);
            }
        }
        if (map != null) {
            result = map.getKeys();
        }
        return result;
    }

    /**
     * Call a method of an object.
     * @param obj the JavaScript object
     * @param methodName the name of the function property
     * @param args the arguments for the call
     *
     * @see Context#getCurrentContext()
     */
    public static Object callMethod(Scriptable obj, String methodName,
                                    Object[] args)
    {
        return callMethod(null, obj, methodName, args);
    }

    /**
     * Call a method of an object.
     * @param cx the Context object associated with the current thread.
     * @param obj the JavaScript object
     * @param methodName the name of the function property
     * @param args the arguments for the call
     */
    public static Object callMethod(Context cx, Scriptable obj,
                                    String methodName,
                                    Object[] args)
    {
        Object funObj = getProperty(obj, methodName);
        if (!(funObj instanceof Function)) {
            throw ScriptRuntime.notFunctionError(obj, methodName);
        }
        Function fun = (Function)funObj;
        // XXX: What should be the scope when calling funObj?
        // The following favor scope stored in the object on the assumption
        // that is more useful especially under dynamic scope setup.
        // An alternative is to check for dynamic scope flag
        // and use ScriptableObject.getTopLevelScope(fun) if the flag is not
        // set. But that require access to Context and messy code
        // so for now it is not checked.
        Scriptable scope = ScriptableObject.getTopLevelScope(obj);
        if (cx != null) {
            return fun.call(cx, scope, obj, args);
        } else {
            return Context.call(null, fun, scope, obj, args);
        }
    }

    private static Scriptable getBase(Scriptable obj, String name)
    {
        do {
            if (obj.has(name, obj))
                break;
            obj = obj.getPrototype();
        } while(obj != null);
        return obj;
    }

    private static Scriptable getBase(Scriptable obj, int index)
    {
        do {
            if (obj.has(index, obj))
                break;
            obj = obj.getPrototype();
        } while(obj != null);
        return obj;
    }

    /**
     * Get arbitrary application-specific value associated with this object.
     * @param key key object to select particular value.
     * @see #associateValue(Object key, Object value)
     */
    public final Object getAssociatedValue(Object key)
    {
        Map<Object,Object> h = associatedValues;
        if (h == null)
            return null;
        return h.get(key);
    }

    /**
     * Get arbitrary application-specific value associated with the top scope
     * of the given scope.
     * The method first calls {@link #getTopLevelScope(Scriptable scope)}
     * and then searches the prototype chain of the top scope for the first
     * object containing the associated value with the given key.
     *
     * @param scope the starting scope.
     * @param key key object to select particular value.
     * @see #getAssociatedValue(Object key)
     */
    public static Object getTopScopeValue(Scriptable scope, Object key)
    {
        scope = ScriptableObject.getTopLevelScope(scope);
        for (;;) {
            if (scope instanceof ScriptableObject) {
                ScriptableObject so = (ScriptableObject)scope;
                Object value = so.getAssociatedValue(key);
                if (value != null) {
                    return value;
                }
            }
            scope = scope.getPrototype();
            if (scope == null) {
                return null;
            }
        }
    }

    /**
     * Associate arbitrary application-specific value with this object.
     * Value can only be associated with the given object and key only once.
     * The method ignores any subsequent attempts to change the already
     * associated value.
     * <p> The associated values are not serialized.
     * @param key key object to select particular value.
     * @param value the value to associate
     * @return the passed value if the method is called first time for the
     * given key or old value for any subsequent calls.
     * @see #getAssociatedValue(Object key)
     */
    public synchronized final Object associateValue(Object key, Object value)
    {
        if (value == null) throw new IllegalArgumentException();
        Map<Object,Object> h = associatedValues;
        if (h == null) {
            h = new HashMap<Object,Object>();
            associatedValues = h;
        }
        return Kit.initHash(h, key, value);
    }

    private Object getImpl(String name, int index, Scriptable start)
    {
        Slot slot = getSlot(name, index, SLOT_QUERY);
        if (slot == null) {
            return Scriptable.NOT_FOUND;
        }
        if (!(slot instanceof GetterSlot)) {
            return slot.value;
        }
        Object getterObj = ((GetterSlot)slot).getter;
        if (getterObj != null) {
            if (getterObj instanceof MemberBox) {
                MemberBox nativeGetter = (MemberBox)getterObj;
                Object getterThis;
                Object[] args;
                if (nativeGetter.delegateTo == null) {
                    getterThis = start;
                    args = ScriptRuntime.emptyArgs;
                } else {
                    getterThis = nativeGetter.delegateTo;
                    args = new Object[] { start };
                }
                return nativeGetter.invoke(getterThis, args);
            } else {
                Function f = (Function)getterObj;
                Context cx = Context.getContext();
                return f.call(cx, f.getParentScope(), start,
                              ScriptRuntime.emptyArgs);
            }
        }
        Object value = slot.value;
        if (value instanceof LazilyLoadedCtor) {
            LazilyLoadedCtor initializer = (LazilyLoadedCtor)value;
            try {
                initializer.init();
            } finally {
                value = initializer.getValue();
                slot.value = value;
            }
        }
        return value;
    }

    /**
     *
     * @param name
     * @param index
     * @param start
     * @param value
     * @param constFlag EMPTY means normal put.  UNINITIALIZED_CONST means
     * defineConstProperty.  READONLY means const initialization expression.
     * @return false if this != start and no slot was found.  true if this == start
     * or this != start and a READONLY slot was found.
     */
    private boolean putImpl(String name, int index, Scriptable start,
                            Object value, int constFlag)
    {
        Slot slot;
        if (this != start) {
            slot = getSlot(name, index, SLOT_QUERY);
            if (slot == null) {
                return false;
            }
        } else if (!isExtensible()) {
            slot = getSlot(name, index, SLOT_QUERY);
            if (slot == null) {
                return true;
            }
        } else {
            checkNotSealed(name, index);
            // either const hoisted declaration or initialization
            if (constFlag != EMPTY) {
                slot = getSlot(name, index, SLOT_MODIFY_CONST);
                int attr = slot.getAttributes();
                if ((attr & READONLY) == 0)
                    throw Context.reportRuntimeError1("msg.var.redecl", name);
                if ((attr & UNINITIALIZED_CONST) != 0) {
                    slot.value = value;
                    // clear the bit on const initialization
                    if (constFlag != UNINITIALIZED_CONST)
                        slot.setAttributes(attr & ~UNINITIALIZED_CONST);
                }
                return true;
            }
            slot = getSlot(name, index, SLOT_MODIFY);
        }
        if (slot instanceof GetterSlot) {
            Object setterObj = ((GetterSlot)slot).setter;
            if (setterObj == null) {
                if (((GetterSlot)slot).getter != null) {
                  // Based on TC39 ES3.1 Draft of 9-Feb-2009, 8.12.4, step 2,
                  // we should throw a TypeError in this case.
                  throw ScriptRuntime.typeError1("msg.set.prop.no.setter", name);
                }
            } else {
                Context cx = Context.getContext();
                if (setterObj instanceof MemberBox) {
                    MemberBox nativeSetter = (MemberBox)setterObj;
                    Class<?> pTypes[] = nativeSetter.argTypes;
                    // XXX: cache tag since it is already calculated in
                    // defineProperty ?
                    Class<?> valueType = pTypes[pTypes.length - 1];
                    int tag = FunctionObject.getTypeTag(valueType);
                    Object actualArg = FunctionObject.convertArg(cx, start,
                                                                 value, tag);
                    Object setterThis;
                    Object[] args;
                    if (nativeSetter.delegateTo == null) {
                        setterThis = start;
                        args = new Object[] { actualArg };
                    } else {
                        setterThis = nativeSetter.delegateTo;
                        args = new Object[] { start, actualArg };
                    }
                    nativeSetter.invoke(setterThis, args);
                } else {
                    Function f = (Function)setterObj;
                    f.call(cx, f.getParentScope(), start,
                           new Object[] { value });
                }
                return true;
            }
        } else if ((slot.getAttributes() & READONLY) != 0) {
            return true;
        }
        if (this == start) {
            slot.value = value;
            return true;
        } else {
            return false;
        }
    }

    private Slot findAttributeSlot(String name, int index, int accessType)
    {
        Slot slot = getSlot(name, index, accessType);
        if (slot == null) {
            String str = (name != null ? name : Integer.toString(index));
            throw Context.reportRuntimeError1("msg.prop.not.found", str);
        }
        return slot;
    }

    /**
     * Locate the slot with given name or index.
     *
     * @param name property name or null if slot holds spare array index.
     * @param index index or 0 if slot holds property name.
     */
    private Slot getSlot(String name, int index, int accessType)
    {
        Slot slot;

        // Query last access cache and check that it was not deleted.
      lastAccessCheck:
        {
            slot = lastAccess;
            if (name != null) {
                if (name != slot.name)
                    break lastAccessCheck;
                // No String.equals here as successful slot search update
                // name object with fresh reference of the same string.
            } else {
                if (slot.name != null || index != slot.indexOrHash)
                    break lastAccessCheck;
            }

            if (slot.wasDeleted)
                break lastAccessCheck;

            if (accessType == SLOT_MODIFY_GETTER_SETTER &&
                !(slot instanceof GetterSlot))
                break lastAccessCheck;

            if (accessType == SLOT_CONVERT_ACCESSOR_TO_DATA &&
                (slot instanceof GetterSlot))
                break lastAccessCheck;

            return slot;
        }

        slot = accessSlot(name, index, accessType);
        if (slot != null) {
            // Update the cache
            lastAccess = slot;
        }
        return slot;
    }

    private Slot accessSlot(String name, int index, int accessType)
    {
        int indexOrHash = (name != null ? name.hashCode() : index);

        if (accessType == SLOT_QUERY ||
            accessType == SLOT_MODIFY ||
            accessType == SLOT_MODIFY_CONST ||
            accessType == SLOT_MODIFY_GETTER_SETTER ||
            accessType == SLOT_CONVERT_ACCESSOR_TO_DATA)
        {
            // Check the hashtable without using synchronization

            Slot[] slotsLocalRef = slots; // Get stable local reference
            if (slotsLocalRef == null) {
                if (accessType == SLOT_QUERY)
                    return null;
            } else {
                int tableSize = slotsLocalRef.length;
                int slotIndex = getSlotIndex(tableSize, indexOrHash);
                Slot slot = slotsLocalRef[slotIndex];
                while (slot != null) {
                    String sname = slot.name;
                    if (sname != null) {
                        if (sname == name)
                            break;
                        if (name != null && indexOrHash == slot.indexOrHash) {
                            if (name.equals(sname)) {
                                // This will avoid calling String.equals when
                                // slot is accessed with same string object
                                // next time.
                                slot.name = name;
                                break;
                            }
                        }
                    } else if (name == null &&
                               indexOrHash == slot.indexOrHash) {
                        break;
                    }
                    slot = slot.next;
                }
                if (accessType == SLOT_QUERY) {
                    return slot;
                } else if (accessType == SLOT_MODIFY) {
                    if (slot != null)
                        return slot;
                } else if (accessType == SLOT_MODIFY_GETTER_SETTER) {
                    if (slot instanceof GetterSlot)
                        return slot;
                } else if (accessType == SLOT_MODIFY_CONST) {
                    if (slot != null)
                        return slot;
                } else if (accessType == SLOT_CONVERT_ACCESSOR_TO_DATA) {
                    if ( !(slot instanceof GetterSlot) )
                        return slot;
                }
            }

            // A new slot has to be inserted or the old has to be replaced
            // by GetterSlot. Time to synchronize.

            synchronized (this) {
                // Refresh local ref if another thread triggered grow
                slotsLocalRef = slots;
                int insertPos;
                if (count == 0) {
                    // Always throw away old slots if any on empty insert
                    slotsLocalRef = new Slot[5];
                    slots = slotsLocalRef;
                    insertPos = getSlotIndex(slotsLocalRef.length, indexOrHash);
                } else {
                    int tableSize = slotsLocalRef.length;
                    insertPos = getSlotIndex(tableSize, indexOrHash);
                    Slot prev = slotsLocalRef[insertPos];
                    Slot slot = prev;
                    while (slot != null) {
                        if (slot.indexOrHash == indexOrHash &&
                            (slot.name == name ||
                             (name != null && name.equals(slot.name))))
                        {
                            break;
                        }
                        prev = slot;
                        slot = slot.next;
                    }

                    if (slot != null) {
                        // Another thread just added a slot with same
                        // name/index before this one entered synchronized
                        // block. This is a race in application code and
                        // probably indicates bug there. But for the hashtable
                        // implementation it is harmless with the only
                        // complication is the need to replace the added slot
                        // if we need GetterSlot and the old one is not.

                        Slot newSlot;

                        if (accessType == SLOT_MODIFY_GETTER_SETTER && !(slot instanceof GetterSlot)) {
                            newSlot = new GetterSlot(name, indexOrHash, slot.getAttributes());
                        } else if (accessType == SLOT_CONVERT_ACCESSOR_TO_DATA && (slot instanceof GetterSlot)) {
                            newSlot = new Slot(name, indexOrHash, slot.getAttributes());
                        } else if (accessType == SLOT_MODIFY_CONST) {
                          return null;
                        } else {
                          return slot;
                        }

                        newSlot.value = slot.value;
                        newSlot.next = slot.next;
                        // add new slot to linked list
                        if (lastAdded != null)
                            lastAdded.orderedNext = newSlot;
                        if (firstAdded == null)
                            firstAdded = newSlot;
                        lastAdded = newSlot;
                        // add new slot to hash table
                        if (prev == slot) {
                            slotsLocalRef[insertPos] = newSlot;
                        } else {
                            prev.next = newSlot;
                        }
                        // other housekeeping
                        slot.wasDeleted = true;
                        slot.value = null;
                        slot.name = null;
                        if (slot == lastAccess) {
                            lastAccess = REMOVED;
                        }
                        return newSlot;
                    } else {
                      // Check if the table is not too full before inserting.
                      if (4 * (count + 1) > 3 * slotsLocalRef.length) {
                          slotsLocalRef = new Slot[slotsLocalRef.length * 2 + 1];
                          copyTable(slots, slotsLocalRef, count);
                          slots = slotsLocalRef;
                          insertPos = getSlotIndex(slotsLocalRef.length,
                                  indexOrHash);
                      }
                    }
                }
                Slot newSlot = (accessType == SLOT_MODIFY_GETTER_SETTER
                                ? new GetterSlot(name, indexOrHash, 0)
                                : new Slot(name, indexOrHash, 0));
                if (accessType == SLOT_MODIFY_CONST)
                    newSlot.setAttributes(CONST);
                ++count;
                // add new slot to linked list
                if (lastAdded != null)
                    lastAdded.orderedNext = newSlot;
                if (firstAdded == null)
                    firstAdded = newSlot;
                lastAdded = newSlot;
                // add new slot to hash table, return it
                addKnownAbsentSlot(slotsLocalRef, newSlot, insertPos);
                return newSlot;
            }

        } else if (accessType == SLOT_REMOVE) {
            synchronized (this) {
                Slot[] slotsLocalRef = slots;
                if (count != 0) {
                    int tableSize = slots.length;
                    int slotIndex = getSlotIndex(tableSize, indexOrHash);
                    Slot prev = slotsLocalRef[slotIndex];
                    Slot slot = prev;
                    while (slot != null) {
                        if (slot.indexOrHash == indexOrHash &&
                            (slot.name == name ||
                             (name != null && name.equals(slot.name))))
                        {
                            break;
                        }
                        prev = slot;
                        slot = slot.next;
                    }
                    if (slot != null && (slot.getAttributes() & PERMANENT) == 0) {
                        count--;
                        // remove slot from hash table
                        if (prev == slot) {
                            slotsLocalRef[slotIndex] = slot.next;
                        } else {
                            prev.next = slot.next;
                        }
                        // Mark the slot as removed. It is still referenced
                        // from the order-added linked list, but will be
                        // cleaned up later
                        slot.wasDeleted = true;
                        slot.value = null;
                        slot.name = null;
                        if (slot == lastAccess) {
                            lastAccess = REMOVED;
                        }
                    }
                }
            }
            return null;

        } else {
            throw Kit.codeBug();
        }
    }

    private static int getSlotIndex(int tableSize, int indexOrHash)
    {
        return (indexOrHash & 0x7fffffff) % tableSize;
    }

    // Must be inside synchronized (this)
    private static void copyTable(Slot[] slots, Slot[] newSlots, int count)
    {
        if (count == 0) throw Kit.codeBug();

        int tableSize = newSlots.length;
        int i = slots.length;
        for (;;) {
            --i;
            Slot slot = slots[i];
            while (slot != null) {
                int insertPos = getSlotIndex(tableSize, slot.indexOrHash);
                Slot next = slot.next;
                addKnownAbsentSlot(newSlots, slot, insertPos);
                slot.next = null;
                slot = next;
                if (--count == 0)
                    return;
            }
        }
    }

    /**
     * Add slot with keys that are known to absent from the table.
     * This is an optimization to use when inserting into empty table,
     * after table growth or during deserialization.
     */
    private static void addKnownAbsentSlot(Slot[] slots, Slot slot,
                                           int insertPos)
    {
        if (slots[insertPos] == null) {
            slots[insertPos] = slot;
        } else {
            Slot prev = slots[insertPos];
            while (prev.next != null) {
                prev = prev.next;
            }
            prev.next = slot;
        }
    }

    Object[] getIds(boolean getAll) {
        Slot[] s = slots;
        Object[] a = ScriptRuntime.emptyArgs;
        if (s == null)
            return a;
        int c = 0;
        Slot slot = firstAdded;
        while (slot != null && slot.wasDeleted) {
            // as long as we're traversing the order-added linked list,
            // remove deleted slots
            slot = slot.orderedNext;
        }
        firstAdded = slot;
        if (slot != null) {
            for (;;) {
                if (getAll || (slot.getAttributes() & DONTENUM) == 0) {
                    if (c == 0)
                        a = new Object[s.length];
                    a[c++] = slot.name != null
                                 ? (Object) slot.name
                                 : Integer.valueOf(slot.indexOrHash);
                }
                Slot next = slot.orderedNext;
                while (next != null && next.wasDeleted) {
                    // remove deleted slots
                    next = next.orderedNext;
                }
                slot.orderedNext = next;
                if (next == null) {
                    break;
                }
                slot = next;
            }
        }
        lastAdded = slot;
        if (c == a.length)
            return a;
        Object[] result = new Object[c];
        System.arraycopy(a, 0, result, 0, c);
        return result;
    }

    private synchronized void writeObject(ObjectOutputStream out)
        throws IOException
    {
        out.defaultWriteObject();
        int objectsCount = count;
        if (objectsCount < 0) {
            // "this" was sealed
            objectsCount = ~objectsCount;
        }
        if (objectsCount == 0) {
            out.writeInt(0);
        } else {
            out.writeInt(slots.length);
            Slot slot = firstAdded;
            while (slot != null && slot.wasDeleted) {
                // as long as we're traversing the order-added linked list,
                // remove deleted slots
                slot = slot.orderedNext;
            }
            firstAdded = slot;
            while (slot != null) {
                out.writeObject(slot);
                Slot next = slot.orderedNext;
                while (next != null && next.wasDeleted) {
                    // remove deleted slots
                    next = next.orderedNext;
                }
                slot.orderedNext = next;
                slot = next;
            }
        }
    }

    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();
        lastAccess = REMOVED;

        int tableSize = in.readInt();
        if (tableSize != 0) {
            slots = new Slot[tableSize];
            int objectsCount = count;
            if (objectsCount < 0) {
                // "this" was sealed
                objectsCount = ~objectsCount;
            }
            Slot prev = null;
            for (int i=0; i != objectsCount; ++i) {
                lastAdded = (Slot)in.readObject();
                if (i==0) {
                    firstAdded = lastAdded;
                } else {
                    prev.orderedNext = lastAdded;
                }
                int slotIndex = getSlotIndex(tableSize, lastAdded.indexOrHash);
                addKnownAbsentSlot(slots, lastAdded, slotIndex);
                prev = lastAdded;
            }
        }
    }

    protected ScriptableObject getOwnPropertyDescriptor(Context cx, Object id) {
      Slot slot = getSlot(cx, id, SLOT_QUERY);
      if (slot == null) return null;
      Scriptable scope = getParentScope();
      return slot.getPropertyDescriptor(cx, (scope == null ? this : scope));
    }

    protected Slot getSlot(Context cx, Object id, int accessType) {
      final Slot slot;
      String name = ScriptRuntime.toStringIdOrIndex(cx, id);
      if (name == null) {
        int index = ScriptRuntime.lastIndexResult(cx);
        slot = getSlot(null, index, accessType);
      } else {
        slot = getSlot(name, 0, accessType);
      }
      return slot;
    }

    // Methods and classes to implement java.util.Map interface

    public int size() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

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

    public Object get(Object key) {
        Object value = null;
        if (key instanceof String) {
            value = get((String) key, this);
        } else if (key instanceof Number) {
            value = get(((Number) key).intValue(), this);
        }
        if (value == Scriptable.NOT_FOUND || value == Undefined.instance) {
            return null;
        } else if (value instanceof Wrapper) {
            return ((Wrapper) value).unwrap();
        } else {
            return value;
        }
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


    class EntrySet extends AbstractSet<Map.Entry<Object, Object>> {
        @Override
        public Iterator<Map.Entry<Object, Object>> iterator() {
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
                    ScriptableObject.this.remove(key);
                    key = null;
                }
            };
        }

        @Override
        public int size() {
            return count;
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
                    return (key = ids[index++]);
                }

                public void remove() {
                    if (key == null) {
                        throw new IllegalStateException();
                    }
                    ScriptableObject.this.remove(key);
                    key = null;
                }
           };
        }

        @Override
        public int size() {
            return count;
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
                    ScriptableObject.this.remove(key);
                    key = null;
                }
            };
        }

        @Override
        public int size() {
            return count;
        }
    }

}
