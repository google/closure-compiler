/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import org.mozilla.javascript.debug.DebuggableScript;

final class InterpretedFunction extends NativeFunction implements Script
{
    static final long serialVersionUID = 541475680333911468L;

    InterpreterData idata;
    SecurityController securityController;
    Object securityDomain;

    private InterpretedFunction(InterpreterData idata,
                                Object staticSecurityDomain)
    {
        this.idata = idata;

        // Always get Context from the current thread to
        // avoid security breaches via passing mangled Context instances
        // with bogus SecurityController
        Context cx = Context.getContext();
        SecurityController sc = cx.getSecurityController();
        Object dynamicDomain;
        if (sc != null) {
            dynamicDomain = sc.getDynamicSecurityDomain(staticSecurityDomain);
        } else {
            if (staticSecurityDomain != null) {
                throw new IllegalArgumentException();
            }
            dynamicDomain = null;
        }

        this.securityController = sc;
        this.securityDomain = dynamicDomain;
    }

    private InterpretedFunction(InterpretedFunction parent, int index)
    {
        this.idata = parent.idata.itsNestedFunctions[index];
        this.securityController = parent.securityController;
        this.securityDomain = parent.securityDomain;
    }

    /**
     * Create script from compiled bytecode.
     */
    static InterpretedFunction createScript(InterpreterData idata,
                                            Object staticSecurityDomain)
    {
        InterpretedFunction f;
        f = new InterpretedFunction(idata, staticSecurityDomain);
        return f;
    }

    /**
     * Create function compiled from Function(...) constructor.
     */
    static InterpretedFunction createFunction(Context cx,Scriptable scope,
                                              InterpreterData idata,
                                              Object staticSecurityDomain)
    {
        InterpretedFunction f;
        f = new InterpretedFunction(idata, staticSecurityDomain);
        f.initScriptFunction(cx, scope);
        return f;
    }

    /**
     * Create function embedded in script or another function.
     */
    static InterpretedFunction createFunction(Context cx, Scriptable scope,
                                              InterpretedFunction  parent,
                                              int index)
    {
        InterpretedFunction f = new InterpretedFunction(parent, index);
        f.initScriptFunction(cx, scope);
        return f;
    }


    @Override
    public String getFunctionName()
    {
        return (idata.itsName == null) ? "" : idata.itsName;
    }

    /**
     * Calls the function.
     * @param cx the current context
     * @param scope the scope used for the call
     * @param thisObj the value of "this"
     * @param args function arguments. Must not be null. You can use
     * {@link ScriptRuntime#emptyArgs} to pass empty arguments.
     * @return the result of the function call.
     */
    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj,
                       Object[] args)
    {
        if (!ScriptRuntime.hasTopCall(cx)) {
            return ScriptRuntime.doTopCall(this, cx, scope, thisObj, args);
        }
        return Interpreter.interpret(this, cx, scope, thisObj, args);
    }

    public Object exec(Context cx, Scriptable scope)
    {
        if (!isScript()) {
            // Can only be applied to scripts
            throw new IllegalStateException();
        }
        if (!ScriptRuntime.hasTopCall(cx)) {
            // It will go through "call" path. but they are equivalent
            return ScriptRuntime.doTopCall(
                this, cx, scope, scope, ScriptRuntime.emptyArgs);
        }
        return Interpreter.interpret(
            this, cx, scope, scope, ScriptRuntime.emptyArgs);
    }

    public boolean isScript() {
        return idata.itsFunctionType == 0;
    }

    @Override
    public String getEncodedSource()
    {
        return Interpreter.getEncodedSource(idata);
    }

    @Override
    public DebuggableScript getDebuggableView()
    {
        return idata;
    }

    @Override
    public Object resumeGenerator(Context cx, Scriptable scope, int operation,
                                  Object state, Object value)
    {
        return Interpreter.resumeGenerator(cx, scope, operation, state, value);
    }

    @Override
    protected int getLanguageVersion()
    {
        return idata.languageVersion;
    }

    @Override
    protected int getParamCount()
    {
        return idata.argCount;
    }

    @Override
    protected int getParamAndVarCount()
    {
        return idata.argNames.length;
    }

    @Override
    protected String getParamOrVarName(int index)
    {
        return idata.argNames[index];
    }

    @Override
    protected boolean getParamOrVarConst(int index)
    {
        return idata.argIsConst[index];
    }
}

