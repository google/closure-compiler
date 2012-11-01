/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * The class for results of the Function.bind operation
 * EcmaScript 5 spec, 15.3.4.5
 */
public class BoundFunction extends BaseFunction {

  static final long serialVersionUID = 2118137342826470729L;

  private final Callable targetFunction;
  private final Scriptable boundThis;
  private final Object[] boundArgs;
  private final int length;

  public BoundFunction(Context cx, Scriptable scope, Callable targetFunction, Scriptable boundThis,
                       Object[] boundArgs)
  {
    this.targetFunction = targetFunction;
    this.boundThis = boundThis;
    this.boundArgs = boundArgs;
    if (targetFunction instanceof BaseFunction) {
      length = Math.max(0, ((BaseFunction) targetFunction).getLength() - boundArgs.length);
    } else {
      length = 0;
    }

    ScriptRuntime.setFunctionProtoAndParent(this, scope);

    Function thrower = ScriptRuntime.typeErrorThrower();
    NativeObject throwing = new NativeObject();
    throwing.put("get", throwing, thrower);
    throwing.put("set", throwing, thrower);
    throwing.put("enumerable", throwing, false);
    throwing.put("configurable", throwing, false);
    throwing.preventExtensions();

    this.defineOwnProperty(cx, "caller", throwing, false);
    this.defineOwnProperty(cx, "arguments", throwing, false);
  }

  @Override
  public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] extraArgs)
  {
    Scriptable callThis = boundThis != null ? boundThis : ScriptRuntime.getTopCallScope(cx);
    return targetFunction.call(cx, scope, callThis, concat(boundArgs, extraArgs));
  }

  @Override
  public Scriptable construct(Context cx, Scriptable scope, Object[] extraArgs) {
    if (targetFunction instanceof Function) {
      return ((Function) targetFunction).construct(cx, scope, concat(boundArgs, extraArgs));
    }
    throw ScriptRuntime.typeError0("msg.not.ctor");
  }

  @Override
  public boolean hasInstance(Scriptable instance) {
    if (targetFunction instanceof Function) {
      return ((Function) targetFunction).hasInstance(instance);
    }
    throw ScriptRuntime.typeError0("msg.not.ctor");
  }

  @Override
  public int getLength() {
    return length;
  }

  private Object[] concat(Object[] first, Object[] second) {
    Object[] args = new Object[first.length + second.length];
    System.arraycopy(first, 0, args, 0, first.length);
    System.arraycopy(second, 0, args, first.length, second.length);
    return args;
  }
}
