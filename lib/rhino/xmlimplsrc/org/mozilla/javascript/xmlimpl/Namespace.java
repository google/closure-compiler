/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.xmlimpl;

import org.mozilla.javascript.*;

/**
 * Class Namespace
 *
 */
class Namespace extends IdScriptableObject
{
    static final long serialVersionUID = -5765755238131301744L;

    private static final Object NAMESPACE_TAG = "Namespace";

    private Namespace prototype;
    private XmlNode.Namespace ns;

    private Namespace() {
    }

    static Namespace create(Scriptable scope, Namespace prototype, XmlNode.Namespace namespace) {
        Namespace rv = new Namespace();
        rv.setParentScope(scope);
        rv.prototype = prototype;
        rv.setPrototype(prototype);
        rv.ns = namespace;
        return rv;
    }

    final XmlNode.Namespace getDelegate() {
        return ns;
    }

    public void exportAsJSClass(boolean sealed) {
        exportAsJSClass(MAX_PROTOTYPE_ID, this.getParentScope(), sealed);
    }

    public String uri() {
        return ns.getUri();
    }

    public String prefix() {
        return ns.getPrefix();
    }

    @Override
    public String toString() {
        return uri();
    }

    public String toLocaleString() {
        return toString();
    }

    private boolean equals(Namespace n) {
        return uri().equals(n.uri());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Namespace)) return false;
        return equals((Namespace)obj);
    }

    @Override
    public int hashCode() {
        return uri().hashCode();
    }

    @Override
    protected Object equivalentValues(Object value) {
        if (!(value instanceof Namespace)) return Scriptable.NOT_FOUND;
        boolean result = equals((Namespace)value);
        return result ? Boolean.TRUE : Boolean.FALSE;
    }

    @Override
    public String getClassName() {
        return "Namespace";
    }

    @Override
    public Object getDefaultValue(Class<?> hint) {
        return uri();
    }

// #string_id_map#
    private static final int
        Id_prefix               = 1,
        Id_uri                  = 2,
        MAX_INSTANCE_ID         = 2;

    @Override
    protected int getMaxInstanceId()
    {
        return super.getMaxInstanceId() + MAX_INSTANCE_ID;
    }

    @Override
    protected int findInstanceIdInfo(String s)
    {
        int id;
// #generated# Last update: 2007-08-20 08:23:22 EDT
        L0: { id = 0; String X = null;
            int s_length = s.length();
            if (s_length==3) { X="uri";id=Id_uri; }
            else if (s_length==6) { X="prefix";id=Id_prefix; }
            if (X!=null && X!=s && !X.equals(s)) id = 0;
            break L0;
        }
// #/generated#

        if (id == 0) return super.findInstanceIdInfo(s);

        int attr;
        switch (id) {
          case Id_prefix:
          case Id_uri:
            attr = PERMANENT | READONLY;
            break;
          default: throw new IllegalStateException();
        }
        return instanceIdInfo(attr, super.getMaxInstanceId() + id);
    }
// #/string_id_map#

    @Override
    protected String getInstanceIdName(int id)
    {
        switch (id - super.getMaxInstanceId()) {
          case Id_prefix: return "prefix";
          case Id_uri: return "uri";
        }
        return super.getInstanceIdName(id);
    }

    @Override
    protected Object getInstanceIdValue(int id)
    {
        switch (id - super.getMaxInstanceId()) {
          case Id_prefix:
            if (ns.getPrefix() == null) return Undefined.instance;
            return ns.getPrefix();
          case Id_uri:
            return ns.getUri();
        }
        return super.getInstanceIdValue(id);
    }


// #string_id_map#
    private static final int
        Id_constructor          = 1,
        Id_toString             = 2,
        Id_toSource             = 3,
        MAX_PROTOTYPE_ID        = 3;

    @Override
    protected int findPrototypeId(String s)
    {
        int id;
// #generated# Last update: 2007-08-20 08:23:22 EDT
        L0: { id = 0; String X = null; int c;
            int s_length = s.length();
            if (s_length==8) {
                c=s.charAt(3);
                if (c=='o') { X="toSource";id=Id_toSource; }
                else if (c=='t') { X="toString";id=Id_toString; }
            }
            else if (s_length==11) { X="constructor";id=Id_constructor; }
            if (X!=null && X!=s && !X.equals(s)) id = 0;
            break L0;
        }
// #/generated#
        return id;
    }
// #/string_id_map#

    @Override
    protected void initPrototypeId(int id)
    {
        String s;
        int arity;
        switch (id) {
          case Id_constructor: arity=2; s="constructor"; break;
          case Id_toString:    arity=0; s="toString";    break;
          case Id_toSource:    arity=0; s="toSource";    break;
          default: throw new IllegalArgumentException(String.valueOf(id));
        }
        initPrototypeMethod(NAMESPACE_TAG, id, s, arity);
    }

    @Override
    public Object execIdCall(IdFunctionObject f,
                             Context cx,
                             Scriptable scope,
                             Scriptable thisObj,
                             Object[] args)
    {
        if (!f.hasTag(NAMESPACE_TAG)) {
            return super.execIdCall(f, cx, scope, thisObj, args);
        }
        int id = f.methodId();
        switch (id) {
          case Id_constructor:
            return jsConstructor(cx, (thisObj == null), args);
          case Id_toString:
            return realThis(thisObj, f).toString();
          case Id_toSource:
            return realThis(thisObj, f).js_toSource();
        }
        throw new IllegalArgumentException(String.valueOf(id));
    }

    private Namespace realThis(Scriptable thisObj, IdFunctionObject f) {
        if(!(thisObj instanceof Namespace))
            throw incompatibleCallError(f);
        return (Namespace)thisObj;
    }

    Namespace newNamespace(String uri) {
        Namespace prototype = (this.prototype == null) ? this : this.prototype;
        return create( this.getParentScope(), prototype, XmlNode.Namespace.create(uri) );
    }

    Namespace newNamespace(String prefix, String uri) {
        if (prefix == null) return newNamespace(uri);
        Namespace prototype = (this.prototype == null) ? this : this.prototype;
        return create( this.getParentScope(), prototype, XmlNode.Namespace.create(prefix, uri) );
    }

    Namespace constructNamespace(Object uriValue) {
        String prefix;
        String uri;

        if (uriValue instanceof Namespace) {
            Namespace ns = (Namespace)uriValue;
            prefix = ns.prefix();
            uri = ns.uri();
        } else if (uriValue instanceof QName) {
            QName qname = (QName)uriValue;
            uri = qname.uri();
            if (uri != null) {
                //    TODO    Is there a way to push this back into QName so that we can make prefix() private?
                prefix = qname.prefix();
            } else {
                uri = qname.toString();
                prefix = null;
            }
        } else {
            uri = ScriptRuntime.toString(uriValue);
            prefix = (uri.length() == 0) ? "" : null;
        }

        return newNamespace(prefix, uri);
    }

    Namespace castToNamespace(Object namespaceObj) {
        if (namespaceObj instanceof Namespace) {
            return (Namespace)namespaceObj;
        }
        return constructNamespace(namespaceObj);
    }

    private Namespace constructNamespace(Object prefixValue, Object uriValue) {
        String prefix;
        String uri;

        if (uriValue instanceof QName) {
            QName qname = (QName)uriValue;
            uri = qname.uri();
            if (uri == null) {
                uri = qname.toString();
            }
        } else {
            uri = ScriptRuntime.toString(uriValue);
        }

        if (uri.length() == 0) {
            if (prefixValue == Undefined.instance) {
                prefix = "";
            } else {
                prefix = ScriptRuntime.toString(prefixValue);
                if (prefix.length() != 0) {
                    throw ScriptRuntime.typeError(
                        "Illegal prefix '"+prefix+"' for 'no namespace'.");
                }
            }
        } else if (prefixValue == Undefined.instance) {
            prefix = "";
        } else if (!XMLName.accept(prefixValue)) {
            prefix = "";
        } else {
            prefix = ScriptRuntime.toString(prefixValue);
        }

        return newNamespace(prefix, uri);
    }

    private Namespace constructNamespace() {
        return newNamespace("", "");
    }

    private Object jsConstructor(Context cx, boolean inNewExpr, Object[] args)
    {
        if (!inNewExpr && args.length == 1) {
            return castToNamespace(args[0]);
        }

        if (args.length == 0) {
            return constructNamespace();
        } else if (args.length == 1) {
            return constructNamespace(args[0]);
        } else {
            return constructNamespace(args[0], args[1]);
        }
    }

    private String js_toSource()
    {
        StringBuffer sb = new StringBuffer();
        sb.append('(');
        toSourceImpl(ns.getPrefix(), ns.getUri(), sb);
        sb.append(')');
        return sb.toString();
    }

    static void toSourceImpl(String prefix, String uri, StringBuffer sb)
    {
        sb.append("new Namespace(");
        if (uri.length() == 0) {
            if (!"".equals(prefix)) throw new IllegalArgumentException(prefix);
        } else {
            sb.append('\'');
            if (prefix != null) {
                sb.append(ScriptRuntime.escapeString(prefix, '\''));
                sb.append("', '");
            }
            sb.append(ScriptRuntime.escapeString(uri, '\''));
            sb.append('\'');
        }
        sb.append(')');
    }
}
