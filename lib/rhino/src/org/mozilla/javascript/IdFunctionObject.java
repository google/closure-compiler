/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// API class

package org.mozilla.javascript;

public class IdFunctionObject extends BaseFunction
{

    static final long serialVersionUID = -5332312783643935019L;

    public IdFunctionObject(IdFunctionCall idcall, Object tag, int id, int arity)
    {
        if (arity < 0)
            throw new IllegalArgumentException();

        this.idcall = idcall;
        this.tag = tag;
        this.methodId = id;
        this.arity = arity;
        if (arity < 0) throw new IllegalArgumentException();
    }

    public IdFunctionObject(IdFunctionCall idcall, Object tag, int id,
                            String name, int arity, Scriptable scope)
    {
        super(scope, null);

        if (arity < 0)
            throw new IllegalArgumentException();
        if (name == null)
            throw new IllegalArgumentException();

        this.idcall = idcall;
        this.tag = tag;
        this.methodId = id;
        this.arity = arity;
        this.functionName = name;
    }

    public void initFunction(String name, Scriptable scope)
    {
        if (name == null) throw new IllegalArgumentException();
        if (scope == null) throw new IllegalArgumentException();
        this.functionName = name;
        setParentScope(scope);
    }

    public final boolean hasTag(Object tag)
    {
        return tag == null ? this.tag == null : tag.equals(this.tag);
    }

    public final int methodId()
    {
        return methodId;
    }

    public final void markAsConstructor(Scriptable prototypeProperty)
    {
        useCallAsConstructor = true;
        setImmunePrototypeProperty(prototypeProperty);
    }

    public final void addAsProperty(Scriptable target)
    {
        ScriptableObject.defineProperty(target, functionName, this,
                                        ScriptableObject.DONTENUM);
    }

    public void exportAsScopeProperty()
    {
        addAsProperty(getParentScope());
    }

    @Override
    public Scriptable getPrototype()
    {
        // Lazy initialization of prototype: for native functions this
        // may not be called at all
        Scriptable proto = super.getPrototype();
        if (proto == null) {
            proto = getFunctionPrototype(getParentScope());
            setPrototype(proto);
        }
        return proto;
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj,
                       Object[] args)
    {
        return idcall.execIdCall(this, cx, scope, thisObj, args);
    }

    @Override
    public Scriptable createObject(Context cx, Scriptable scope)
    {
        if (useCallAsConstructor) {
            return null;
        }
        // Throw error if not explicitly coded to be used as constructor,
        // to satisfy ECMAScript standard (see bugzilla 202019).
        // To follow current (2003-05-01) SpiderMonkey behavior, change it to:
        // return super.createObject(cx, scope);
        throw ScriptRuntime.typeError1("msg.not.ctor", functionName);
    }

    @Override
    String decompile(int indent, int flags)
    {
        StringBuffer sb = new StringBuffer();
        boolean justbody = (0 != (flags & Decompiler.ONLY_BODY_FLAG));
        if (!justbody) {
            sb.append("function ");
            sb.append(getFunctionName());
            sb.append("() { ");
        }
        sb.append("[native code for ");
        if (idcall instanceof Scriptable) {
            Scriptable sobj = (Scriptable)idcall;
            sb.append(sobj.getClassName());
            sb.append('.');
        }
        sb.append(getFunctionName());
        sb.append(", arity=");
        sb.append(getArity());
        sb.append(justbody ? "]\n" : "] }\n");
        return sb.toString();
    }

    @Override
    public int getArity()
    {
        return arity;
    }

    @Override
    public int getLength() { return getArity(); }

    @Override
    public String getFunctionName()
    {
        return (functionName == null) ? "" : functionName;
    }

    public final RuntimeException unknown()
    {
        // It is program error to call id-like methods for unknown function
        return new IllegalArgumentException(
            "BAD FUNCTION ID="+methodId+" MASTER="+idcall);
    }

    private final IdFunctionCall idcall;
    private final Object tag;
    private final int methodId;
    private int arity;
    private boolean useCallAsConstructor;
    private String functionName;
}
