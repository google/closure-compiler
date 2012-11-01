/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.xmlimpl;

import org.mozilla.javascript.*;
import org.mozilla.javascript.xml.*;

/**
 *  This abstract class describes what all XML objects (XML, XMLList) should
 *  have in common.
 *
 * @see XML
 */
abstract class XMLObjectImpl extends XMLObject {
    private static final Object XMLOBJECT_TAG = "XMLObject";
    private XMLLibImpl lib;
    private boolean prototypeFlag;

    protected XMLObjectImpl(XMLLibImpl lib, Scriptable scope,
                            XMLObject prototype)
    {
        initialize(lib, scope, prototype);
    }

    final void initialize(XMLLibImpl lib, Scriptable scope,
                          XMLObject prototype)
    {
        setParentScope(scope);
        setPrototype(prototype);
        prototypeFlag = (prototype == null);
        this.lib = lib;
    }

    final boolean isPrototype() {
        return prototypeFlag;
    }

    XMLLibImpl getLib() {
        return lib;
    }

    final XML newXML(XmlNode node) {
        return lib.newXML(node);
    }

    XML xmlFromNode(XmlNode node) {
        if (node.getXml() == null) {
            node.setXml( newXML(node) );
        }
        return node.getXml();
    }

    final XMLList newXMLList() {
        return lib.newXMLList();
    }

    final XMLList newXMLListFrom(Object o) {
        return lib.newXMLListFrom(o);
    }

    final XmlProcessor getProcessor() {
        return lib.getProcessor();
    }

    final QName newQName(String uri, String localName, String prefix) {
        return lib.newQName(uri, localName, prefix);
    }

    final QName newQName(XmlNode.QName name) {
        return lib.newQName(name);
    }

    final Namespace createNamespace(XmlNode.Namespace declaration) {
        if (declaration == null) return null;
        return lib.createNamespaces( new XmlNode.Namespace[] { declaration } )[0];
    }

    final Namespace[] createNamespaces(XmlNode.Namespace[] declarations) {
        return lib.createNamespaces(declarations);
    }


    @Override
    public final Scriptable getPrototype() {
        return super.getPrototype();
    }

    @Override
    public final void setPrototype(Scriptable prototype) {
        super.setPrototype(prototype);
    }

    @Override
    public final Scriptable getParentScope() {
        return super.getParentScope();
    }

    @Override
    public final void setParentScope(Scriptable parent) {
        super.setParentScope(parent);
    }

    @Override
    public final Object getDefaultValue(Class<?> hint) {
        return this.toString();
    }

    @Override
    public final boolean hasInstance(Scriptable scriptable) {
        return super.hasInstance(scriptable);
    }

    /**
     * ecmaHas(cx, id) calls this after resolving when id to XMLName
     * and checking it is not Uint32 index.
     */
    abstract boolean hasXMLProperty(XMLName name);

    /**
     * ecmaGet(cx, id) calls this after resolving when id to XMLName
     * and checking it is not Uint32 index.
     */
    abstract Object getXMLProperty(XMLName name);

    /**
     * ecmaPut(cx, id, value) calls this after resolving when id to XMLName
     * and checking it is not Uint32 index.
     */
    abstract void putXMLProperty(XMLName name, Object value);

    /**
     * ecmaDelete(cx, id) calls this after resolving when id to XMLName
     * and checking it is not Uint32 index.
     */
    abstract void deleteXMLProperty(XMLName name);

    /**
     * Test XML equality with target the target.
     */
    abstract boolean equivalentXml(Object target);

    abstract void addMatches(XMLList rv, XMLName name);

    private XMLList getMatches(XMLName name) {
        XMLList rv = newXMLList();
        addMatches(rv, name);
        return rv;
    }

    abstract XML getXML();

    // Methods from section 12.4.4 in the spec
    abstract XMLList child(int index);
    abstract XMLList child(XMLName xmlName);
    abstract XMLList children();
    abstract XMLList comments();
    abstract boolean contains(Object xml);
    abstract XMLObjectImpl copy();
    abstract XMLList elements(XMLName xmlName);
    abstract boolean hasOwnProperty(XMLName xmlName);
    abstract boolean hasComplexContent();
    abstract boolean hasSimpleContent();
    abstract int length();
    abstract void normalize();
    abstract Object parent();
    abstract XMLList processingInstructions(XMLName xmlName);
    abstract boolean propertyIsEnumerable(Object member);
    abstract XMLList text();
    @Override
    public abstract String toString();
    abstract String toSource(int indent);
    abstract String toXMLString();
    abstract Object valueOf();

    protected abstract Object jsConstructor(Context cx, boolean inNewExpr, Object[] args);

    //
    //
    // Methods overriding ScriptableObject
    //
    //

    /**
     * XMLObject always compare with any value and equivalentValues
     * never returns {@link Scriptable#NOT_FOUND} for them but rather
     * calls equivalentXml(value) and wrap the result as Boolean.
     */
    @Override
    protected final Object equivalentValues(Object value) {
        boolean result = equivalentXml(value);
        return result ? Boolean.TRUE : Boolean.FALSE;
    }

    //
    //
    // Methods overriding XMLObject
    //
    //

    /**
     * Implementation of ECMAScript [[Has]]
     */
    @Override
    public final boolean has(Context cx, Object id) {
        if (cx == null) cx = Context.getCurrentContext();
        XMLName xmlName = lib.toXMLNameOrIndex(cx, id);
        if (xmlName == null) {
            long index = ScriptRuntime.lastUint32Result(cx);
            // XXX Fix this cast
            return has((int)index, this);
        }
        return hasXMLProperty(xmlName);
    }

    @Override
    public boolean has(String name, Scriptable start) {
        Context cx = Context.getCurrentContext();
        return hasXMLProperty(lib.toXMLNameFromString(cx, name));
    }
    /**
     * Implementation of ECMAScript [[Get]]
     */
    @Override
    public final Object get(Context cx, Object id) {
        if (cx == null) cx = Context.getCurrentContext();
        XMLName xmlName = lib.toXMLNameOrIndex(cx, id);
        if (xmlName == null) {
            long index = ScriptRuntime.lastUint32Result(cx);
            // XXX Fix this cast
            Object result = get((int)index, this);
            if (result == Scriptable.NOT_FOUND) {
                result = Undefined.instance;
            }
            return result;
        }
        return getXMLProperty(xmlName);
    }

    @Override
    public Object get(String name, Scriptable start) {
        Context cx = Context.getCurrentContext();
        return getXMLProperty(lib.toXMLNameFromString(cx, name));
    }
    /**
     * Implementation of ECMAScript [[Put]]
     */
    @Override
    public final void put(Context cx, Object id, Object value) {
        if (cx == null) cx = Context.getCurrentContext();
        XMLName xmlName = lib.toXMLNameOrIndex(cx, id);
        if (xmlName == null) {
            long index = ScriptRuntime.lastUint32Result(cx);
            // XXX Fix this cast
            put((int)index, this, value);
            return;
        }
        putXMLProperty(xmlName, value);
    }

   @Override
    public void put(String name, Scriptable start, Object value) {
        Context cx = Context.getCurrentContext();
        putXMLProperty(lib.toXMLNameFromString(cx, name), value);
    }
    /**
     * Implementation of ECMAScript [[Delete]].
     */
    @Override
    public final boolean delete(Context cx, Object id) {
        if (cx == null) cx = Context.getCurrentContext();
        XMLName xmlName = lib.toXMLNameOrIndex(cx, id);
        if (xmlName == null) {
            long index = ScriptRuntime.lastUint32Result(cx);
            // XXX Fix this
            delete((int)index);
            return true;
        }
        deleteXMLProperty(xmlName);
        return true;
    }


    @Override
    public void delete(String name) {
        Context cx = Context.getCurrentContext();
        deleteXMLProperty(lib.toXMLNameFromString(cx, name));
    }

    @Override
    public Object getFunctionProperty(Context cx, int id) {
        if (isPrototype()) {
            return super.get(id, this);
        } else {
            Scriptable proto = getPrototype();
            if (proto instanceof XMLObject) {
                return ((XMLObject)proto).getFunctionProperty(cx, id);
            }
        }
        return NOT_FOUND;
    }

    @Override
    public Object getFunctionProperty(Context cx, String name) {
        if (isPrototype()) {
            return super.get(name, this);
        } else {
            Scriptable proto = getPrototype();
            if (proto instanceof XMLObject) {
                return ((XMLObject)proto).getFunctionProperty(cx, name);
            }
        }
        return NOT_FOUND;
    }

    //    TODO    Can this be made more strongly typed?
    @Override
    public Ref memberRef(Context cx, Object elem, int memberTypeFlags) {
        boolean attribute = (memberTypeFlags & Node.ATTRIBUTE_FLAG) != 0;
        boolean descendants = (memberTypeFlags & Node.DESCENDANTS_FLAG) != 0;
        if (!attribute && !descendants) {
            // Code generation would use ecma(Get|Has|Delete|Set) for
            // normal name identifiers so one ATTRIBUTE_FLAG
            // or DESCENDANTS_FLAG has to be set
            throw Kit.codeBug();
        }
        XmlNode.QName qname = lib.toNodeQName(cx, elem, attribute);
        XMLName rv = XMLName.create(qname, attribute, descendants);
        rv.initXMLObject(this);
        return rv;
    }

    /**
     * Generic reference to implement x::ns, x.@ns::y, x..@ns::y etc.
     */
    @Override
    public Ref memberRef(Context cx, Object namespace, Object elem,
            int memberTypeFlags)
    {
        boolean attribute = (memberTypeFlags & Node.ATTRIBUTE_FLAG) != 0;
        boolean descendants = (memberTypeFlags & Node.DESCENDANTS_FLAG) != 0;
        XMLName rv = XMLName.create(lib.toNodeQName(cx, namespace, elem),
                attribute, descendants);
        rv.initXMLObject(this);
        return rv;
    }

    @Override
    public NativeWith enterWith(Scriptable scope) {
        return new XMLWithScope(lib, scope, this);
    }

    @Override
    public NativeWith enterDotQuery(Scriptable scope) {
        XMLWithScope xws = new XMLWithScope(lib, scope, this);
        xws.initAsDotQuery();
        return xws;
    }

    @Override
    public final Object addValues(Context cx, boolean thisIsLeft,
        Object value) {
        if (value instanceof XMLObject) {
            XMLObject v1, v2;
            if (thisIsLeft) {
                v1 = this;
                v2 = (XMLObject)value;
            } else {
                v1 = (XMLObject)value;
                v2 = this;
            }
            return lib.addXMLObjects(cx, v1, v2);
        }
        if (value == Undefined.instance) {
            // both "xml + undefined" and "undefined + xml" gives String(xml)
            return ScriptRuntime.toString(this);
        }

        return super.addValues(cx, thisIsLeft, value);
    }

    //
    //
    // IdScriptableObject machinery
    //
    //

    final void exportAsJSClass(boolean sealed) {
        prototypeFlag = true;
        exportAsJSClass(MAX_PROTOTYPE_ID, getParentScope(), sealed);
    }

// #string_id_map#
    private final static int
        Id_constructor             = 1,

        Id_addNamespace            = 2,
        Id_appendChild             = 3,
        Id_attribute               = 4,
        Id_attributes              = 5,
        Id_child                   = 6,
        Id_childIndex              = 7,
        Id_children                = 8,
        Id_comments                = 9,
        Id_contains                = 10,
        Id_copy                    = 11,
        Id_descendants             = 12,
        Id_elements                = 13,
        Id_inScopeNamespaces       = 14,
        Id_insertChildAfter        = 15,
        Id_insertChildBefore       = 16,
        Id_hasOwnProperty          = 17,
        Id_hasComplexContent       = 18,
        Id_hasSimpleContent        = 19,
        Id_length                  = 20,
        Id_localName               = 21,
        Id_name                    = 22,
        Id_namespace               = 23,
        Id_namespaceDeclarations   = 24,
        Id_nodeKind                = 25,
        Id_normalize               = 26,
        Id_parent                  = 27,
        Id_prependChild            = 28,
        Id_processingInstructions  = 29,
        Id_propertyIsEnumerable    = 30,
        Id_removeNamespace         = 31,
        Id_replace                 = 32,
        Id_setChildren             = 33,
        Id_setLocalName            = 34,
        Id_setName                 = 35,
        Id_setNamespace            = 36,
        Id_text                    = 37,
        Id_toString                = 38,
        Id_toSource                = 39,
        Id_toXMLString             = 40,
        Id_valueOf                 = 41,

        MAX_PROTOTYPE_ID           = 41;

    @Override
    protected int findPrototypeId(String s) {
        int id;
// #generated# Last update: 2008-10-21 12:32:31 MESZ
        L0: { id = 0; String X = null; int c;
            L: switch (s.length()) {
            case 4: c=s.charAt(0);
                if (c=='c') { X="copy";id=Id_copy; }
                else if (c=='n') { X="name";id=Id_name; }
                else if (c=='t') { X="text";id=Id_text; }
                break L;
            case 5: X="child";id=Id_child; break L;
            case 6: c=s.charAt(0);
                if (c=='l') { X="length";id=Id_length; }
                else if (c=='p') { X="parent";id=Id_parent; }
                break L;
            case 7: c=s.charAt(0);
                if (c=='r') { X="replace";id=Id_replace; }
                else if (c=='s') { X="setName";id=Id_setName; }
                else if (c=='v') { X="valueOf";id=Id_valueOf; }
                break L;
            case 8: switch (s.charAt(2)) {
                case 'S': c=s.charAt(7);
                    if (c=='e') { X="toSource";id=Id_toSource; }
                    else if (c=='g') { X="toString";id=Id_toString; }
                    break L;
                case 'd': X="nodeKind";id=Id_nodeKind; break L;
                case 'e': X="elements";id=Id_elements; break L;
                case 'i': X="children";id=Id_children; break L;
                case 'm': X="comments";id=Id_comments; break L;
                case 'n': X="contains";id=Id_contains; break L;
                } break L;
            case 9: switch (s.charAt(2)) {
                case 'c': X="localName";id=Id_localName; break L;
                case 'm': X="namespace";id=Id_namespace; break L;
                case 'r': X="normalize";id=Id_normalize; break L;
                case 't': X="attribute";id=Id_attribute; break L;
                } break L;
            case 10: c=s.charAt(0);
                if (c=='a') { X="attributes";id=Id_attributes; }
                else if (c=='c') { X="childIndex";id=Id_childIndex; }
                break L;
            case 11: switch (s.charAt(0)) {
                case 'a': X="appendChild";id=Id_appendChild; break L;
                case 'c': X="constructor";id=Id_constructor; break L;
                case 'd': X="descendants";id=Id_descendants; break L;
                case 's': X="setChildren";id=Id_setChildren; break L;
                case 't': X="toXMLString";id=Id_toXMLString; break L;
                } break L;
            case 12: c=s.charAt(0);
                if (c=='a') { X="addNamespace";id=Id_addNamespace; }
                else if (c=='p') { X="prependChild";id=Id_prependChild; }
                else if (c=='s') {
                    c=s.charAt(3);
                    if (c=='L') { X="setLocalName";id=Id_setLocalName; }
                    else if (c=='N') { X="setNamespace";id=Id_setNamespace; }
                }
                break L;
            case 14: X="hasOwnProperty";id=Id_hasOwnProperty; break L;
            case 15: X="removeNamespace";id=Id_removeNamespace; break L;
            case 16: c=s.charAt(0);
                if (c=='h') { X="hasSimpleContent";id=Id_hasSimpleContent; }
                else if (c=='i') { X="insertChildAfter";id=Id_insertChildAfter; }
                break L;
            case 17: c=s.charAt(3);
                if (c=='C') { X="hasComplexContent";id=Id_hasComplexContent; }
                else if (c=='c') { X="inScopeNamespaces";id=Id_inScopeNamespaces; }
                else if (c=='e') { X="insertChildBefore";id=Id_insertChildBefore; }
                break L;
            case 20: X="propertyIsEnumerable";id=Id_propertyIsEnumerable; break L;
            case 21: X="namespaceDeclarations";id=Id_namespaceDeclarations; break L;
            case 22: X="processingInstructions";id=Id_processingInstructions; break L;
            }
            if (X!=null && X!=s && !X.equals(s)) id = 0;
            break L0;
        }
// #/generated#
        return id;
    }
// #/string_id_map#

    @Override
    protected void initPrototypeId(int id) {
        String s;
        int arity;
        switch (id) {
            case Id_constructor: {
                IdFunctionObject ctor;
                if (this instanceof XML) {
                    ctor = new XMLCtor((XML)this, XMLOBJECT_TAG, id, 1);
                } else {
                    ctor = new IdFunctionObject(this, XMLOBJECT_TAG, id, 1);
                }
                initPrototypeConstructor(ctor);
                return;
            }

            case Id_addNamespace:      arity=1; s="addNamespace";      break;
            case Id_appendChild:       arity=1; s="appendChild";       break;
            case Id_attribute:         arity=1; s="attribute";         break;
            case Id_attributes:        arity=0; s="attributes";        break;
            case Id_child:             arity=1; s="child";             break;
            case Id_childIndex:        arity=0; s="childIndex";        break;
            case Id_children:          arity=0; s="children";          break;
            case Id_comments:          arity=0; s="comments";          break;
            case Id_contains:          arity=1; s="contains";          break;
            case Id_copy:              arity=0; s="copy";              break;
            case Id_descendants:       arity=1; s="descendants";       break;
            case Id_elements:          arity=1; s="elements";          break;
            case Id_hasComplexContent: arity=0; s="hasComplexContent"; break;
            case Id_hasOwnProperty:    arity=1; s="hasOwnProperty";    break;
            case Id_hasSimpleContent:  arity=0; s="hasSimpleContent";  break;
            case Id_inScopeNamespaces: arity=0; s="inScopeNamespaces"; break;
            case Id_insertChildAfter:  arity=2; s="insertChildAfter";  break;
            case Id_insertChildBefore: arity=2; s="insertChildBefore"; break;
            case Id_length:            arity=0; s="length";            break;
            case Id_localName:         arity=0; s="localName";         break;
            case Id_name:              arity=0; s="name";              break;
            case Id_namespace:         arity=1; s="namespace";         break;
            case Id_namespaceDeclarations:
                arity=0; s="namespaceDeclarations"; break;
            case Id_nodeKind:          arity=0; s="nodeKind";          break;
            case Id_normalize:         arity=0; s="normalize";         break;
            case Id_parent:            arity=0; s="parent";            break;
            case Id_prependChild:      arity=1; s="prependChild";      break;
            case Id_processingInstructions:
                arity=1; s="processingInstructions"; break;
            case Id_propertyIsEnumerable:
                arity=1; s="propertyIsEnumerable"; break;
            case Id_removeNamespace:   arity=1; s="removeNamespace";   break;
            case Id_replace:           arity=2; s="replace";           break;
            case Id_setChildren:       arity=1; s="setChildren";       break;
            case Id_setLocalName:      arity=1; s="setLocalName";      break;
            case Id_setName:           arity=1; s="setName";           break;
            case Id_setNamespace:      arity=1; s="setNamespace";      break;
            case Id_text:              arity=0; s="text";              break;
            case Id_toString:          arity=0; s="toString";          break;
            case Id_toSource:          arity=1; s="toSource";          break;
            case Id_toXMLString:       arity=1; s="toXMLString";       break;
            case Id_valueOf:           arity=0; s="valueOf";           break;

            default: throw new IllegalArgumentException(String.valueOf(id));
        }
        initPrototypeMethod(XMLOBJECT_TAG, id, s, arity);
    }

    private Object[] toObjectArray(Object[] typed) {
        Object[] rv = new Object[typed.length];
        for (int i=0; i<rv.length; i++) {
            rv[i] = typed[i];
        }
        return rv;
    }

    private void xmlMethodNotFound(Object object, String name) {
        throw ScriptRuntime.notFunctionError(object, name);
    }

    @Override
    public Object execIdCall(IdFunctionObject f, Context cx, Scriptable scope,
                             Scriptable thisObj, Object[] args)
    {
        if (!f.hasTag(XMLOBJECT_TAG)) {
            return super.execIdCall(f, cx, scope, thisObj, args);
        }
        int id = f.methodId();
        if (id == Id_constructor) {
            return jsConstructor(cx, thisObj == null, args);
        }

        // All (XML|XMLList).prototype methods require thisObj to be XML
        if (!(thisObj instanceof XMLObjectImpl))
            throw incompatibleCallError(f);
        XMLObjectImpl realThis = (XMLObjectImpl)thisObj;

        XML xml = realThis.getXML();
        switch (id) {
            case Id_appendChild: {
                if (xml == null) xmlMethodNotFound(realThis, "appendChild");
                return xml.appendChild(arg(args, 0));
            }
            case Id_addNamespace: {
                if (xml == null) xmlMethodNotFound(realThis, "addNamespace");
                Namespace ns = lib.castToNamespace(cx, arg(args, 0));
                return xml.addNamespace(ns);
            }
            case Id_childIndex: {
                if (xml == null) xmlMethodNotFound(realThis, "childIndex");
                return ScriptRuntime.wrapInt(xml.childIndex());
            }
            case Id_inScopeNamespaces: {
                if (xml == null) xmlMethodNotFound(realThis, "inScopeNamespaces");
                return cx.newArray(scope, toObjectArray(xml.inScopeNamespaces()));
            }
            case Id_insertChildAfter: {
                if (xml == null) xmlMethodNotFound(realThis, "insertChildAfter");
                Object arg0 = arg(args, 0);
                if (arg0 == null || arg0 instanceof XML) {
                    return xml.insertChildAfter((XML)arg0, arg(args, 1));
                }
                return Undefined.instance;
            }
            case Id_insertChildBefore: {
                if (xml == null) xmlMethodNotFound(realThis, "insertChildBefore");
                Object arg0 = arg(args, 0);
                if (arg0 == null || arg0 instanceof XML) {
                    return xml.insertChildBefore((XML)arg0, arg(args, 1));
                }
                return Undefined.instance;
            }
            case Id_localName: {
                if (xml == null) xmlMethodNotFound(realThis, "localName");
                return xml.localName();
            }
            case Id_name: {
                if (xml == null) xmlMethodNotFound(realThis, "name");
                return xml.name();
            }
            case Id_namespace: {
                if (xml == null) xmlMethodNotFound(realThis, "namespace");
                String prefix = (args.length > 0) ? ScriptRuntime.toString(args[0]) : null;
                Namespace rv = xml.namespace(prefix);
                if (rv == null) {
                    return Undefined.instance;
                } else {
                    return rv;
                }
            }
            case Id_namespaceDeclarations: {
                if (xml == null) xmlMethodNotFound(realThis, "namespaceDeclarations");
                Namespace[] array = xml.namespaceDeclarations();
                return cx.newArray(scope, toObjectArray(array));
            }
            case Id_nodeKind: {
                if (xml == null) xmlMethodNotFound(realThis, "nodeKind");
                return xml.nodeKind();
            }
            case Id_prependChild: {
                if (xml == null) xmlMethodNotFound(realThis, "prependChild");
                return xml.prependChild(arg(args, 0));
            }
            case Id_removeNamespace: {
                if (xml == null) xmlMethodNotFound(realThis, "removeNamespace");
                Namespace ns = lib.castToNamespace(cx, arg(args, 0));
                return xml.removeNamespace(ns);
            }
            case Id_replace: {
                if (xml == null) xmlMethodNotFound(realThis, "replace");
                XMLName xmlName = lib.toXMLNameOrIndex(cx, arg(args, 0));
                Object arg1 = arg(args, 1);
                if (xmlName == null) {
                    //    I refuse to believe that this number will exceed 2^31
                    int index = (int)ScriptRuntime.lastUint32Result(cx);
                    return xml.replace(index, arg1);
                } else {
                    return xml.replace(xmlName, arg1);
                }
            }
            case Id_setChildren: {
                if (xml == null) xmlMethodNotFound(realThis, "setChildren");
                return xml.setChildren(arg(args, 0));
            }
            case Id_setLocalName: {
                if (xml == null) xmlMethodNotFound(realThis, "setLocalName");
                String localName;
                Object arg = arg(args, 0);
                if (arg instanceof QName) {
                    localName = ((QName)arg).localName();
                } else {
                    localName = ScriptRuntime.toString(arg);
                }
                xml.setLocalName(localName);
                return Undefined.instance;
            }
            case Id_setName: {
                if (xml == null) xmlMethodNotFound(realThis, "setName");
                Object arg = (args.length != 0) ? args[0] : Undefined.instance;
                QName qname = lib.constructQName(cx, arg);
                xml.setName(qname);
                return Undefined.instance;
            }
            case Id_setNamespace: {
                if (xml == null) xmlMethodNotFound(realThis, "setNamespace");
                Namespace ns = lib.castToNamespace(cx, arg(args, 0));
                xml.setNamespace(ns);
                return Undefined.instance;
            }

            case Id_attribute: {
                XMLName xmlName = XMLName.create( lib.toNodeQName(cx, arg(args, 0), true), true, false );
                return realThis.getMatches(xmlName);
            }
            case Id_attributes:
                return realThis.getMatches(XMLName.create(XmlNode.QName.create(null, null), true, false));
            case Id_child: {
                XMLName xmlName = lib.toXMLNameOrIndex(cx, arg(args, 0));
                if (xmlName == null) {
                    //    Two billion or so is a fine upper limit, so we cast to int
                    int index = (int)ScriptRuntime.lastUint32Result(cx);
                    return realThis.child(index);
                } else {
                    return realThis.child(xmlName);
                }
            }
            case Id_children:
                return realThis.children();
            case Id_comments:
                return realThis.comments();
            case Id_contains:
                return ScriptRuntime.wrapBoolean(
                    realThis.contains(arg(args, 0)));
            case Id_copy:
                return realThis.copy();
            case Id_descendants: {
                XmlNode.QName qname = (args.length == 0) ? XmlNode.QName.create(null, null) : lib.toNodeQName(cx, args[0], false);
                return realThis.getMatches( XMLName.create(qname, false, true) );
            }
            case Id_elements: {
                XMLName xmlName = (args.length == 0)
                ? XMLName.formStar()
                : lib.toXMLName(cx, args[0]);
                return realThis.elements(xmlName);
            }
            case Id_hasOwnProperty: {
                XMLName xmlName = lib.toXMLName(cx, arg(args, 0));
                return ScriptRuntime.wrapBoolean(
                    realThis.hasOwnProperty(xmlName));
            }
            case Id_hasComplexContent:
                return ScriptRuntime.wrapBoolean(realThis.hasComplexContent());
            case Id_hasSimpleContent:
                return ScriptRuntime.wrapBoolean(realThis.hasSimpleContent());
            case Id_length:
                return ScriptRuntime.wrapInt(realThis.length());
            case Id_normalize:
                realThis.normalize();
                return Undefined.instance;
            case Id_parent:
                return realThis.parent();
            case Id_processingInstructions: {
                XMLName xmlName = (args.length > 0)
                ? lib.toXMLName(cx, args[0])
                : XMLName.formStar();
                return realThis.processingInstructions(xmlName);
            }
            case Id_propertyIsEnumerable: {
                return ScriptRuntime.wrapBoolean(
                    realThis.propertyIsEnumerable(arg(args, 0)));
            }
            case Id_text:
                return realThis.text();
            case Id_toString:
                return realThis.toString();
            case Id_toSource:
                int indent = ScriptRuntime.toInt32(args, 0);
                return realThis.toSource(indent);
            case Id_toXMLString: {
                return realThis.toXMLString();
            }
            case Id_valueOf:
                return realThis.valueOf();
        }
        throw new IllegalArgumentException(String.valueOf(id));
    }

    private static Object arg(Object[] args, int i) {
        return (i < args.length) ? args[i] : Undefined.instance;
    }

    final XML newTextElementXML(XmlNode reference, XmlNode.QName qname, String value) {
        return lib.newTextElementXML(reference, qname, value);
    }

    /* TODO: Hopefully this can be replaced with ecmaToXml below. */
    final XML newXMLFromJs(Object inputObject) {
        return lib.newXMLFromJs(inputObject);
    }

    final XML ecmaToXml(Object object) {
        return lib.ecmaToXml(object);
    }

    final String ecmaEscapeAttributeValue(String s) {
        //    TODO    Check this
        String quoted = lib.escapeAttributeValue(s);
        return quoted.substring(1, quoted.length() - 1);
    }

    final XML createEmptyXML() {
        return newXML(XmlNode.createEmpty(getProcessor()));
    }
}
