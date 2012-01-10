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
 *   Raphael Speyer
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

/**
 * The class for results of the Function.bind operation
 * EcmaScript 5 spec, 15.3.4.5
 */
public class BoundFunction extends BaseFunction {
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
