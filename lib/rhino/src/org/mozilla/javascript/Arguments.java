/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * This class implements the "arguments" object.
 *
 * See ECMA 10.1.8
 *
 * @see org.mozilla.javascript.NativeCall
 */
final class Arguments extends IdScriptableObject
{
    static final long serialVersionUID = 4275508002492040609L;

    private static final String FTAG = "Arguments";

    public Arguments(NativeCall activation)
    {
        this.activation = activation;

        Scriptable parent = activation.getParentScope();
        setParentScope(parent);
        setPrototype(ScriptableObject.getObjectPrototype(parent));

        args = activation.originalArgs;
        lengthObj = Integer.valueOf(args.length);

        NativeFunction f = activation.function;
        calleeObj = f;

        Scriptable topLevel = getTopLevelScope(parent);
        constructor = getProperty(topLevel, "Object");

        int version = f.getLanguageVersion();
        if (version <= Context.VERSION_1_3
            && version != Context.VERSION_DEFAULT)
        {
            callerObj = null;
        } else {
            callerObj = NOT_FOUND;
        }
    }

    @Override
    public String getClassName()
    {
        return FTAG;
    }

    private Object arg(int index) {
      if (index < 0 || args.length <= index) return NOT_FOUND;
      return args[index];
    }

    // the following helper methods assume that 0 < index < args.length

    private void putIntoActivation(int index, Object value) {
        String argName = activation.function.getParamOrVarName(index);
        activation.put(argName, activation, value);
    }

    private Object getFromActivation(int index) {
        String argName = activation.function.getParamOrVarName(index);
        return activation.get(argName, activation);
    }

    private void replaceArg(int index, Object value) {
      if (sharedWithActivation(index)) {
        putIntoActivation(index, value);
      }
      synchronized (this) {
        if (args == activation.originalArgs) {
          args = args.clone();
        }
        args[index] = value;
      }
    }

    private void removeArg(int index) {
      synchronized (this) {
        if (args[index] != NOT_FOUND) {
          if (args == activation.originalArgs) {
            args = args.clone();
          }
          args[index] = NOT_FOUND;
        }
      }
    }

    // end helpers

    @Override
    public boolean has(int index, Scriptable start)
    {
        if (arg(index) != NOT_FOUND) {
          return true;
        }
        return super.has(index, start);
    }

    @Override
    public Object get(int index, Scriptable start)
    {
      final Object value = arg(index);
      if (value == NOT_FOUND) {
        return super.get(index, start);
      } else {
        if (sharedWithActivation(index)) {
          return getFromActivation(index);
        } else {
          return value;
        }
      }
    }

    private boolean sharedWithActivation(int index)
    {
        NativeFunction f = activation.function;
        int definedCount = f.getParamCount();
        if (index < definedCount) {
            // Check if argument is not hidden by later argument with the same
            // name as hidden arguments are not shared with activation
            if (index < definedCount - 1) {
                String argName = f.getParamOrVarName(index);
                for (int i = index + 1; i < definedCount; i++) {
                    if (argName.equals(f.getParamOrVarName(i))) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void put(int index, Scriptable start, Object value)
    {
        if (arg(index) == NOT_FOUND) {
          super.put(index, start, value);
        } else {
          replaceArg(index, value);
        }
    }

    @Override
    public void delete(int index)
    {
        if (0 <= index && index < args.length) {
          removeArg(index);
        }
        super.delete(index);
    }

// #string_id_map#

    private static final int
        Id_callee           = 1,
        Id_length           = 2,
        Id_caller           = 3,
        Id_constructor      = 4,

        MAX_INSTANCE_ID     = Id_constructor;

    @Override
    protected int getMaxInstanceId()
    {
        return MAX_INSTANCE_ID;
    }

    @Override
    protected int findInstanceIdInfo(String s)
    {
        int id;
// #generated# Last update: 2010-01-06 05:48:21 ARST
        L0: { id = 0; String X = null; int c;
            int s_length = s.length();
            if (s_length==6) {
                c=s.charAt(5);
                if (c=='e') { X="callee";id=Id_callee; }
                else if (c=='h') { X="length";id=Id_length; }
                else if (c=='r') { X="caller";id=Id_caller; }
            }
            else if (s_length==11) { X="constructor";id=Id_constructor; }
            if (X!=null && X!=s && !X.equals(s)) id = 0;
            break L0;
        }
// #/generated#

        if (id == 0) return super.findInstanceIdInfo(s);

        int attr;
        switch (id) {
          case Id_callee:
          case Id_caller:
          case Id_length:
          case Id_constructor:
            attr = DONTENUM;
            break;
          default: throw new IllegalStateException();
        }
        return instanceIdInfo(attr, id);
    }

// #/string_id_map#

    @Override
    protected String getInstanceIdName(int id)
    {
        switch (id) {
            case Id_callee: return "callee";
            case Id_length: return "length";
            case Id_caller: return "caller";
            case Id_constructor: return "constructor";
        }
        return null;
    }

    @Override
    protected Object getInstanceIdValue(int id)
    {
        switch (id) {
            case Id_callee: return calleeObj;
            case Id_length: return lengthObj;
            case Id_caller: {
                Object value = callerObj;
                if (value == UniqueTag.NULL_VALUE) { value = null; }
                else if (value == null) {
                    NativeCall caller = activation.parentActivationCall;
                    if (caller != null) {
                        value = caller.get("arguments", caller);
                    }
                }
                return value;
            }
            case Id_constructor:
                return constructor;
        }
        return super.getInstanceIdValue(id);
    }

    @Override
    protected void setInstanceIdValue(int id, Object value)
    {
        switch (id) {
            case Id_callee: calleeObj = value; return;
            case Id_length: lengthObj = value; return;
            case Id_caller:
                callerObj = (value != null) ? value : UniqueTag.NULL_VALUE;
                return;
            case Id_constructor: constructor = value; return;
        }
        super.setInstanceIdValue(id, value);
    }

    @Override
    Object[] getIds(boolean getAll)
    {
        Object[] ids = super.getIds(getAll);
        if (args.length != 0) {
            boolean[] present = new boolean[args.length];
            int extraCount = args.length;
            for (int i = 0; i != ids.length; ++i) {
                Object id = ids[i];
                if (id instanceof Integer) {
                    int index = ((Integer)id).intValue();
                    if (0 <= index && index < args.length) {
                        if (!present[index]) {
                            present[index] = true;
                            extraCount--;
                        }
                    }
                }
            }
            if (!getAll) { // avoid adding args which were redefined to non-enumerable
              for (int i = 0; i < present.length; i++) {
                if (!present[i] && super.has(i, this)) {
                  present[i] = true;
                  extraCount--;
                }
              }
            }
            if (extraCount != 0) {
                Object[] tmp = new Object[extraCount + ids.length];
                System.arraycopy(ids, 0, tmp, extraCount, ids.length);
                ids = tmp;
                int offset = 0;
                for (int i = 0; i != args.length; ++i) {
                    if (present == null || !present[i]) {
                        ids[offset] = Integer.valueOf(i);
                        ++offset;
                    }
                }
                if (offset != extraCount) Kit.codeBug();
            }
        }
        return ids;
    }

    @Override
    protected ScriptableObject getOwnPropertyDescriptor(Context cx, Object id) {
      double d = ScriptRuntime.toNumber(id);
      int index = (int) d;
      if (d != index) {
        return super.getOwnPropertyDescriptor(cx, id);
      }
      Object value = arg(index);
      if (value == NOT_FOUND) {
        return super.getOwnPropertyDescriptor(cx, id);
      }
      if (sharedWithActivation(index)) {
        value = getFromActivation(index);
      }
      if (super.has(index, this)) { // the descriptor has been redefined
        ScriptableObject desc = super.getOwnPropertyDescriptor(cx, id);
        desc.put("value", desc, value);
        return desc;
      } else {
        Scriptable scope = getParentScope();
        if (scope == null) scope = this;
        return buildDataDescriptor(scope, value, EMPTY);
      }
    }

    @Override
    protected void defineOwnProperty(Context cx, Object id,
                                     ScriptableObject desc,
                                     boolean checkValid) {
      super.defineOwnProperty(cx, id, desc, checkValid);

      double d = ScriptRuntime.toNumber(id);
      int index = (int) d;
      if (d != index) return;

      Object value = arg(index);
      if (value == NOT_FOUND) return;

      if (isAccessorDescriptor(desc)) {
        removeArg(index);
        return;
      }

      Object newValue = getProperty(desc, "value");
      if (newValue == NOT_FOUND) return;

      replaceArg(index, newValue);

      if (isFalse(getProperty(desc, "writable"))) {
        removeArg(index);
      }
    }

// Fields to hold caller, callee and length properties,
// where NOT_FOUND value tags deleted properties.
// In addition if callerObj == NULL_VALUE, it tags null for scripts, as
// initial callerObj == null means access to caller arguments available
// only in JS <= 1.3 scripts
    private Object callerObj;
    private Object calleeObj;
    private Object lengthObj;
    private Object constructor;

    private NativeCall activation;

// Initially args holds activation.getOriginalArgs(), but any modification
// of its elements triggers creation of a copy. If its element holds NOT_FOUND,
// it indicates deleted index, in which case super class is queried.
    private Object[] args;
}
