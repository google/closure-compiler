/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.xmlimpl;

import org.mozilla.javascript.*;

/**
 * Class QName
 *
 */
final class QName extends IdScriptableObject
{
    static final long serialVersionUID = 416745167693026750L;

    private static final Object QNAME_TAG = "QName";

    private XMLLibImpl lib;

    private QName prototype;

    private XmlNode.QName delegate;

    private QName() {
    }

    static QName create(XMLLibImpl lib, Scriptable scope, QName prototype, XmlNode.QName delegate) {
        QName rv = new QName();
        rv.lib = lib;
        rv.setParentScope(scope);
        rv.prototype = prototype;
        rv.setPrototype(prototype);
        rv.delegate = delegate;
        return rv;
    }

//    /** @deprecated */
//    static QName create(XMLLibImpl lib, XmlNode.QName nodeQname) {
//        return create(lib, lib.globalScope(), lib.qnamePrototype(), nodeQname);
//    }

    void exportAsJSClass(boolean sealed) {
        exportAsJSClass(MAX_PROTOTYPE_ID, getParentScope(), sealed);
    }

    @Override
    public String toString() {
        //    ECMA357 13.3.4.2
        if (delegate.getNamespace() == null) {
            return "*::" + localName();
        } else if (delegate.getNamespace().isGlobal()) {
            //    leave as empty
            return localName();
        } else {
            return uri() + "::" + localName();
        }
    }

    public String localName() {
        if (delegate.getLocalName() == null) return "*";
        return delegate.getLocalName();
    }

    /*
     * TODO This property is supposed to be invisible and I think we can
     *  make it private at some point, though Namespace might need it
     */
    String prefix() {
        if (delegate.getNamespace() == null) return null;
        return delegate.getNamespace().getPrefix();
    }

    String uri() {
        if (delegate.getNamespace() == null) return null;
        return delegate.getNamespace().getUri();
    }

    /** @deprecated */
    final XmlNode.QName toNodeQname() {
        return delegate;
    }

    final XmlNode.QName getDelegate() {
        return delegate;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof QName)) return false;
        return equals((QName)obj);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    protected Object equivalentValues(Object value)
    {
        if(!(value instanceof QName)) return Scriptable.NOT_FOUND;
        boolean result = equals((QName)value);
        return result ? Boolean.TRUE : Boolean.FALSE;
    }

    private boolean equals(QName q) {
        return this.delegate.equals(q.delegate);
    }

    @Override
    public String getClassName() {
        return "QName";
    }

    @Override
    public Object getDefaultValue(Class<?> hint) {
        return toString();
    }

// #string_id_map#
    private static final int
        Id_localName            = 1,
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
// #generated# Last update: 2007-08-20 08:21:41 EDT
        L0: { id = 0; String X = null;
            int s_length = s.length();
            if (s_length==3) { X="uri";id=Id_uri; }
            else if (s_length==9) { X="localName";id=Id_localName; }
            if (X!=null && X!=s && !X.equals(s)) id = 0;
            break L0;
        }
// #/generated#

        if (id == 0) return super.findInstanceIdInfo(s);

        int attr;
        switch (id) {
          case Id_localName:
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
          case Id_localName: return "localName";
          case Id_uri: return "uri";
        }
        return super.getInstanceIdName(id);
    }

    @Override
    protected Object getInstanceIdValue(int id)
    {
        switch (id - super.getMaxInstanceId()) {
          case Id_localName: return localName();
          case Id_uri: return uri();
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
// #generated# Last update: 2007-08-20 08:21:41 EDT
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
        initPrototypeMethod(QNAME_TAG, id, s, arity);
    }

    @Override
    public Object execIdCall(IdFunctionObject f,
                             Context cx,
                             Scriptable scope,
                             Scriptable thisObj,
                             Object[] args)
    {
        if (!f.hasTag(QNAME_TAG)) {
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

    private QName realThis(Scriptable thisObj, IdFunctionObject f)
    {
        if(!(thisObj instanceof QName))
            throw incompatibleCallError(f);
        return (QName)thisObj;
    }

    QName newQName(XMLLibImpl lib, String q_uri, String q_localName, String q_prefix) {
        QName prototype = this.prototype;
        if (prototype == null) {
            prototype = this;
        }
        XmlNode.Namespace ns = null;
        if (q_prefix != null) {
            ns = XmlNode.Namespace.create(q_prefix, q_uri);
        } else if (q_uri != null) {
            ns = XmlNode.Namespace.create(q_uri);
        } else {
            ns = null;
        }
        if (q_localName != null && q_localName.equals("*")) q_localName = null;
        return create(lib, this.getParentScope(), prototype, XmlNode.QName.create(ns, q_localName));
    }

    //    See ECMA357 13.3.2
    QName constructQName(XMLLibImpl lib, Context cx, Object namespace, Object name) {
        String nameString = null;
        if (name instanceof QName) {
            if (namespace == Undefined.instance) {
                return (QName)name;
            } else {
                nameString = ((QName)name).localName();
            }
        }
        if (name == Undefined.instance) {
            nameString = "";
        } else {
            nameString = ScriptRuntime.toString(name);
        }

        if (namespace == Undefined.instance) {
            if ("*".equals(nameString)) {
                namespace = null;
            } else {
                namespace = lib.getDefaultNamespace(cx);
            }
        }
        Namespace namespaceNamespace = null;
        if (namespace == null) {
            //    leave as null
        } else if (namespace instanceof Namespace) {
            namespaceNamespace = (Namespace)namespace;
        } else {
            namespaceNamespace = lib.newNamespace(ScriptRuntime.toString(namespace));
        }
        String q_localName = nameString;
        String q_uri;
        String q_prefix;
        if (namespace == null) {
            q_uri = null;
            q_prefix = null;    //    corresponds to undefined; see QName class
        } else {
            q_uri = namespaceNamespace.uri();
            q_prefix = namespaceNamespace.prefix();
        }
        return newQName(lib, q_uri, q_localName, q_prefix);
    }

    QName constructQName(XMLLibImpl lib, Context cx, Object nameValue) {
        return constructQName(lib, cx, Undefined.instance, nameValue);
    }

    QName castToQName(XMLLibImpl lib, Context cx, Object qnameValue) {
        if (qnameValue instanceof QName) {
            return (QName)qnameValue;
        }
        return constructQName(lib, cx, qnameValue);
    }

    private Object jsConstructor(Context cx, boolean inNewExpr, Object[] args) {
        //    See ECMA357 13.3.2
        if (!inNewExpr && args.length == 1) {
            return castToQName(lib, cx, args[0]);
        }
        if (args.length == 0) {
            return constructQName(lib, cx, Undefined.instance);
        } else if (args.length == 1) {
            return constructQName(lib, cx, args[0]);
        } else {
            return constructQName(lib, cx, args[0], args[1]);
        }
    }

    private String js_toSource() {
        StringBuffer sb = new StringBuffer();
        sb.append('(');
        toSourceImpl(uri(), localName(), prefix(), sb);
        sb.append(')');
        return sb.toString();
    }

    private static void toSourceImpl(String uri, String localName, String prefix, StringBuffer sb) {
        sb.append("new QName(");
        if (uri == null && prefix == null) {
            if (!"*".equals(localName)) {
                sb.append("null, ");
            }
        } else {
            Namespace.toSourceImpl(prefix, uri, sb);
            sb.append(", ");
        }
        sb.append('\'');
        sb.append(ScriptRuntime.escapeString(localName, '\''));
        sb.append("')");
    }
}
