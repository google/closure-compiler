/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.xmlimpl;

import org.mozilla.javascript.*;
import org.mozilla.javascript.xml.*;
import java.util.ArrayList;

class XMLList extends XMLObjectImpl implements Function {
    static final long serialVersionUID = -4543618751670781135L;

    private XmlNode.InternalList _annos;
    private XMLObjectImpl targetObject = null;
    private XmlNode.QName targetProperty = null;

    XMLList(XMLLibImpl lib, Scriptable scope, XMLObject prototype) {
        super(lib, scope, prototype);
        _annos = new XmlNode.InternalList();
    }

    /* TODO Will probably end up unnecessary as we move things around */
    XmlNode.InternalList getNodeList() {
        return _annos;
    }

    //    TODO    Should be XMLObjectImpl, XMLName?
    void setTargets(XMLObjectImpl object, XmlNode.QName property) {
        targetObject = object;
        targetProperty = property;
    }

    /* TODO: original author marked this as deprecated */
    private XML getXmlFromAnnotation(int index) {
        return getXML(_annos, index);
    }

    @Override
    XML getXML() {
        if (length() == 1) return getXmlFromAnnotation(0);
        return null;
    }

    private void internalRemoveFromList(int index) {
        _annos.remove(index);
    }

    void replace(int index, XML xml) {
        if (index < length()) {
            XmlNode.InternalList newAnnoList = new XmlNode.InternalList();
            newAnnoList.add(_annos, 0, index);
            newAnnoList.add(xml);
            newAnnoList.add(_annos, index+1, length());
            _annos = newAnnoList;
        }
    }

    private void insert(int index, XML xml) {
        if (index < length()) {
            XmlNode.InternalList newAnnoList = new XmlNode.InternalList();
            newAnnoList.add(_annos, 0, index);
            newAnnoList.add(xml);
            newAnnoList.add(_annos, index, length());
            _annos = newAnnoList;
        }
    }

    //
    //
    //  methods overriding ScriptableObject
    //
    //

    @Override
    public String getClassName() {
        return "XMLList";
    }

    //
    //
    //  methods overriding IdScriptableObject
    //
    //

    @Override
    public Object get(int index, Scriptable start) {
        //Log("get index: " + index);

        if (index >= 0 && index < length()) {
            return getXmlFromAnnotation(index);
        } else {
            return Scriptable.NOT_FOUND;
        }
    }

    @Override
    boolean hasXMLProperty(XMLName xmlName) {
        // Has should return true if the property would have results > 0
        return getPropertyList(xmlName).length() > 0;
    }

    @Override
    public boolean has(int index, Scriptable start) {
        return 0 <= index && index < length();
    }

    @Override
    void putXMLProperty(XMLName xmlName, Object value) {
        //Log("put property: " + name);

        // Special-case checks for undefined and null
        if (value == null) {
            value = "null";
        } else if (value instanceof Undefined) {
            value = "undefined";
        }

        if (length() > 1) {
            throw ScriptRuntime.typeError(
               "Assignment to lists with more than one item is not supported");
        } else if (length() == 0) {
            // Secret sauce for super-expandos.
            // We set an element here, and then add ourselves to our target.
            if (targetObject != null && targetProperty != null &&
                targetProperty.getLocalName() != null &&
                targetProperty.getLocalName().length() > 0)
            {
                // Add an empty element with our targetProperty name and
                // then set it.
                XML xmlValue = newTextElementXML(null, targetProperty, null);
                addToList(xmlValue);

                if(xmlName.isAttributeName()) {
                    setAttribute(xmlName, value);
                } else {
                    XML xml = item(0);
                    xml.putXMLProperty(xmlName, value);

                    // Update the list with the new item at location 0.
                    replace(0, item(0));
                }

                // Now add us to our parent
                XMLName name2 = XMLName.formProperty(
                        targetProperty.getNamespace().getUri(),
                        targetProperty.getLocalName());
                targetObject.putXMLProperty(name2, this);
                replace(0, targetObject.getXML().getLastXmlChild());
            } else {
                throw ScriptRuntime.typeError(
                  "Assignment to empty XMLList without targets not supported");
            }
        } else if(xmlName.isAttributeName()) {
            setAttribute(xmlName, value);
        } else {
            XML xml = item(0);
            xml.putXMLProperty(xmlName, value);

            // Update the list with the new item at location 0.
            replace(0, item(0));
        }
    }

    @Override
    Object getXMLProperty(XMLName name) {
        return getPropertyList(name);
    }

    private void replaceNode(XML xml, XML with) {
        xml.replaceWith(with);
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        Object parent = Undefined.instance;
        // Convert text into XML if needed.
        XMLObject xmlValue;

        // Special-case checks for undefined and null
        if (value == null) {
            value = "null";
        } else if (value instanceof Undefined) {
            value = "undefined";
        }

        if (value instanceof XMLObject) {
            xmlValue = (XMLObject) value;
        } else {
            if (targetProperty == null) {
                xmlValue = newXMLFromJs(value.toString());
            } else {
                //    Note that later in the code, we will use this as an argument to replace(int,value)
                //    So we will be "replacing" this element with itself
                //    There may well be a better way to do this
                //    TODO    Find a way to refactor this whole method and simplify it
                xmlValue = item(index);
                if (xmlValue == null) {
                    XML x = item(0);
                    xmlValue = x == null
                        ? newTextElementXML(null,targetProperty,null)
                        : x.copy();
                }
                ((XML)xmlValue).setChildren(value);
            }
        }

        // Find the parent
        if (index < length()) {
          parent = item(index).parent();
        } else if (length() == 0) {
          parent = targetObject != null ? targetObject.getXML() : parent();
        } else {
          // Appending
          parent = parent();
        }

        if (parent instanceof XML) {
            // found parent, alter doc
            XML xmlParent = (XML) parent;

            if (index < length()) {
                // We're replacing the the node.
                XML xmlNode = getXmlFromAnnotation(index);

                if (xmlValue instanceof XML) {
                    replaceNode(xmlNode, (XML) xmlValue);
                    replace(index, xmlNode);
                } else if (xmlValue instanceof XMLList) {
                    // Replace the first one, and add the rest on the list.
                    XMLList list = (XMLList) xmlValue;

                    if (list.length() > 0) {
                        int lastIndexAdded = xmlNode.childIndex();
                        replaceNode(xmlNode, list.item(0));
                        replace(index, list.item(0));

                        for (int i = 1; i < list.length(); i++) {
                            xmlParent.insertChildAfter(xmlParent.getXmlChild(lastIndexAdded), list.item(i));
                            lastIndexAdded++;
                            insert(index + i, list.item(i));
                        }
                    }
                }
            } else {
                // Appending
                xmlParent.appendChild(xmlValue);
                addToList(xmlParent.getLastXmlChild());
            }
        } else {
            // Don't all have same parent, no underlying doc to alter
            if (index < length()) {
                XML xmlNode = getXML(_annos, index);

                if (xmlValue instanceof XML) {
                    replaceNode(xmlNode, (XML) xmlValue);
                    replace(index, xmlNode);
                } else if (xmlValue instanceof XMLList) {
                    // Replace the first one, and add the rest on the list.
                    XMLList list = (XMLList) xmlValue;

                    if (list.length() > 0) {
                        replaceNode(xmlNode, list.item(0));
                        replace(index, list.item(0));

                        for (int i = 1; i < list.length(); i++) {
                            insert(index + i, list.item(i));
                        }
                    }
                }
            } else {
                addToList(xmlValue);
            }
        }
    }

    private XML getXML(XmlNode.InternalList _annos, int index) {
        if (index >= 0 && index < length()) {
            return xmlFromNode(_annos.item(index));
        } else {
            return null;
        }
    }

    @Override
    void deleteXMLProperty(XMLName name) {
        for (int i = 0; i < length(); i++) {
            XML xml = getXmlFromAnnotation(i);

            if (xml.isElement()) {
                xml.deleteXMLProperty(name);
            }
        }
    }

    @Override
    public void delete(int index) {
        if (index >= 0 && index < length()) {
            XML xml = getXmlFromAnnotation(index);

            xml.remove();

            internalRemoveFromList(index);
        }
    }

    @Override
    public Object[] getIds() {
        Object enumObjs[];

        if (isPrototype()) {
            enumObjs = new Object[0];
        } else {
            enumObjs = new Object[length()];

            for (int i = 0; i < enumObjs.length; i++) {
                enumObjs[i] = Integer.valueOf(i);
            }
        }

        return enumObjs;
    }

    public Object[] getIdsForDebug() {
        return getIds();
    }


    // XMLList will remove will delete all items in the list (a set delete) this differs from the XMLList delete operator.
    void remove() {
        int nLen = length();
        for (int i = nLen - 1; i >= 0; i--) {
            XML xml = getXmlFromAnnotation(i);
            if (xml != null) {
                xml.remove();
                internalRemoveFromList(i);
            }
        }
    }

    XML item(int index) {
        return _annos != null
            ? getXmlFromAnnotation(index) : createEmptyXML();
    }

    private void setAttribute(XMLName xmlName, Object value) {
        for (int i = 0; i < length(); i++) {
            XML xml = getXmlFromAnnotation(i);
            xml.setAttribute(xmlName, value);
        }
    }

    void addToList(Object toAdd) {
        _annos.addToList(toAdd);
    }

    //
    //
    // Methods from section 12.4.4 in the spec
    //
    //

    @Override
    XMLList child(int index) {
        XMLList result = newXMLList();

        for (int i = 0; i < length(); i++) {
            result.addToList(getXmlFromAnnotation(i).child(index));
        }

        return result;
    }

    @Override
    XMLList child(XMLName xmlName) {
        XMLList result = newXMLList();

        for (int i = 0; i < length(); i++) {
            result.addToList(getXmlFromAnnotation(i).child(xmlName));
        }

        return result;
    }

    @Override
    void addMatches(XMLList rv, XMLName name) {
        for (int i=0; i<length(); i++) {
            getXmlFromAnnotation(i).addMatches(rv, name);
        }
    }

    @Override
    XMLList children() {
        ArrayList<XML> list = new ArrayList<XML>();

        for (int i = 0; i < length(); i++) {
            XML xml = getXmlFromAnnotation(i);

            if (xml != null) {
                XMLList childList = xml.children();

                int cChildren = childList.length();
                for (int j = 0; j < cChildren; j++) {
                    list.add(childList.item(j));
                }
            }
        }

        XMLList allChildren = newXMLList();
        int sz = list.size();

        for (int i = 0; i < sz; i++) {
            allChildren.addToList(list.get(i));
        }

        return allChildren;
    }

    @Override
    XMLList comments() {
        XMLList result = newXMLList();

        for (int i = 0; i < length(); i++) {
            XML xml = getXmlFromAnnotation(i);
            result.addToList(xml.comments());
        }

        return result;
    }

    @Override
    XMLList elements(XMLName name) {
        XMLList rv = newXMLList();
        for (int i=0; i<length(); i++) {
            XML xml = getXmlFromAnnotation(i);
            rv.addToList(xml.elements(name));
        }
        return rv;
    }

    @Override
    boolean contains(Object xml) {
        boolean result = false;

        for (int i = 0; i < length(); i++) {
            XML member = getXmlFromAnnotation(i);

            if (member.equivalentXml(xml)) {
                result = true;
                break;
            }
        }

        return result;
    }

    @Override
    XMLObjectImpl copy() {
        XMLList result = newXMLList();

        for (int i = 0; i < length(); i++) {
            XML xml = getXmlFromAnnotation(i);
            result.addToList(xml.copy());
        }

        return result;
    }

    @Override
    boolean hasOwnProperty(XMLName xmlName) {
        if (isPrototype()) {
            String property = xmlName.localName();
            return (findPrototypeId(property) != 0);
        } else {
            return (getPropertyList(xmlName).length() > 0);
        }
    }

    @Override
    boolean hasComplexContent() {
        boolean complexContent;
        int length = length();

        if (length == 0) {
            complexContent = false;
        } else if (length == 1) {
            complexContent = getXmlFromAnnotation(0).hasComplexContent();
        } else {
            complexContent = false;

            for (int i = 0; i < length; i++) {
                XML nextElement = getXmlFromAnnotation(i);
                if (nextElement.isElement()) {
                    complexContent = true;
                    break;
                }
            }
        }

        return complexContent;
    }

    @Override
    boolean hasSimpleContent() {
        if (length() == 0) {
            return true;
        } else if (length() == 1) {
            return getXmlFromAnnotation(0).hasSimpleContent();
        } else {
            for (int i=0; i<length(); i++) {
                XML nextElement = getXmlFromAnnotation(i);
                if (nextElement.isElement()) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    int length() {
        int result = 0;

        if (_annos != null) {
            result = _annos.length();
        }

        return result;
    }

    @Override
    void normalize() {
        for (int i = 0; i < length(); i++) {
            getXmlFromAnnotation(i).normalize();
        }
    }

    /**
     * If list is empty, return undefined, if elements have different parents return undefined,
     * If they all have the same parent, return that parent
     */
    @Override
    Object parent() {
        if (length() == 0) return Undefined.instance;

        XML candidateParent = null;

        for (int i = 0; i < length(); i++) {
            Object currParent = getXmlFromAnnotation(i).parent();
            if (!(currParent instanceof XML)) return Undefined.instance;
            XML xml = (XML)currParent;
            if (i == 0) {
                // Set the first for the rest to compare to.
                candidateParent = xml;
            } else {
                if (candidateParent.is(xml)) {
                    //    keep looking
                } else {
                    return Undefined.instance;
                }
            }
        }
        return candidateParent;
    }

    @Override
    XMLList processingInstructions(XMLName xmlName) {
        XMLList result = newXMLList();

        for (int i = 0; i < length(); i++) {
            XML xml = getXmlFromAnnotation(i);

            result.addToList(xml.processingInstructions(xmlName));
        }

        return result;
    }

    @Override
    boolean propertyIsEnumerable(Object name) {
        long index;
        if (name instanceof Integer) {
            index = ((Integer)name).intValue();
        } else if (name instanceof Number) {
            double x = ((Number)name).doubleValue();
            index = (long)x;
            if (index != x) {
                return false;
            }
            if (index == 0 && 1.0 / x < 0) {
                // Negative 0
                return false;
            }
        } else {
            String s = ScriptRuntime.toString(name);
            index = ScriptRuntime.testUint32String(s);
        }
        return (0 <= index && index < length());
    }

    @Override
    XMLList text() {
        XMLList result = newXMLList();

        for (int i = 0; i < length(); i++) {
            result.addToList(getXmlFromAnnotation(i).text());
        }

        return result;
    }

    @Override
    public String toString() {
        //    ECMA357 10.1.2
        if (hasSimpleContent()) {
            StringBuffer sb = new StringBuffer();

            for(int i = 0; i < length(); i++) {
                XML next = getXmlFromAnnotation(i);
                if (next.isComment() || next.isProcessingInstruction()) {
                    //    do nothing
                } else {
                    sb.append(next.toString());
                }
            }

            return sb.toString();
        } else {
            return toXMLString();
        }
    }

    @Override
    String toSource(int indent) {
        return toXMLString();
    }

    @Override
    String toXMLString() {
        //    See ECMA 10.2.1
        StringBuffer sb = new StringBuffer();

        for (int i=0; i<length(); i++) {
            if (getProcessor().isPrettyPrinting() && i != 0) {
                sb.append('\n');
            }
            sb.append(getXmlFromAnnotation(i).toXMLString());
        }
        return sb.toString();
    }

    @Override
    Object valueOf() {
        return this;
    }

    //
    // Other public Functions from XMLObject
    //

    @Override
    boolean equivalentXml(Object target) {
        boolean result = false;

        // Zero length list should equate to undefined
        if (target instanceof Undefined && length() == 0) {
            result = true;
        } else if (length() == 1) {
            result = getXmlFromAnnotation(0).equivalentXml(target);
        } else if (target instanceof XMLList) {
            XMLList otherList = (XMLList) target;

            if (otherList.length() == length()) {
                result = true;

                for (int i = 0; i < length(); i++) {
                    if (!getXmlFromAnnotation(i).equivalentXml(otherList.getXmlFromAnnotation(i))) {
                        result = false;
                        break;
                    }
                }
            }
        }

        return result;
    }

    private XMLList getPropertyList(XMLName name) {
        XMLList propertyList = newXMLList();
        XmlNode.QName qname = null;

        if (!name.isDescendants() && !name.isAttributeName()) {
            // Only set the targetProperty if this is a regular child get
            // and not a descendant or attribute get
            qname = name.toQname();
        }

        propertyList.setTargets(this, qname);

        for (int i = 0; i < length(); i++) {
            propertyList.addToList(
                getXmlFromAnnotation(i).getPropertyList(name));
        }

        return propertyList;
    }

    private Object applyOrCall(boolean isApply,
        Context cx, Scriptable scope,
        Scriptable thisObj, Object[] args) {
        String methodName = isApply ? "apply" : "call";
        if(!(thisObj instanceof XMLList) ||
            ((XMLList)thisObj).targetProperty == null)
            throw ScriptRuntime.typeError1("msg.isnt.function",
                methodName);

        return ScriptRuntime.applyOrCall(isApply, cx, scope, thisObj, args);
    }

    @Override
    protected Object jsConstructor(Context cx, boolean inNewExpr,
                                   Object[] args)
    {
        if (args.length == 0) {
            return newXMLList();
        } else {
            Object arg0 = args[0];
            if (!inNewExpr && arg0 instanceof XMLList) {
                // XMLList(XMLList) returns the same object.
                return arg0;
            }
            return newXMLListFrom(arg0);
        }
    }

    /**
     * See ECMA 357, 11_2_2_1, Semantics, 3_e.
     */
    @Override
    public Scriptable getExtraMethodSource(Context cx) {
        if (length() == 1) {
            return getXmlFromAnnotation(0);
        }
        return null;
    }

    public Object call(Context cx, Scriptable scope, Scriptable thisObj,
        Object[] args) {
        // This XMLList is being called as a Function.
        // Let's find the real Function object.
        if(targetProperty == null)
            throw ScriptRuntime.notFunctionError(this);

        String methodName = targetProperty.getLocalName();

        boolean isApply = methodName.equals("apply");
        if(isApply || methodName.equals("call"))
            return applyOrCall(isApply, cx, scope, thisObj, args);

        if (!(thisObj instanceof XMLObject)) {
            throw ScriptRuntime.typeError1("msg.incompat.call", methodName);
        }
        Object func = null;
        Scriptable sobj = thisObj;

        while (sobj instanceof XMLObject) {
            XMLObject xmlObject = (XMLObject) sobj;
            func = xmlObject.getFunctionProperty(cx, methodName);
            if (func != Scriptable.NOT_FOUND) {
                break;
            }
            sobj = xmlObject.getExtraMethodSource(cx);
            if (sobj != null) {
                thisObj = sobj;
                if (!(sobj instanceof XMLObject)) {
                    func = ScriptableObject.getProperty(sobj, methodName);
                }
            }
        }

        if (!(func instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(thisObj, func, methodName);
        }
        return ((Callable)func).call(cx, scope, thisObj, args);
    }

    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        throw ScriptRuntime.typeError1("msg.not.ctor", "XMLList");
    }
}


