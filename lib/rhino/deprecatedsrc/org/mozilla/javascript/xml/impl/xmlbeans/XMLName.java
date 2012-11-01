/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.xml.impl.xmlbeans;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Kit;
import org.mozilla.javascript.Ref;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Undefined;

class XMLName extends Ref
{
    static final long serialVersionUID = 3832176310755686977L;

    private String uri;
    private String localName;
    private boolean isAttributeName;
    private boolean isDescendants;
    private XMLObjectImpl xmlObject;

    private XMLName(String uri, String localName)
    {
        this.uri = uri;
        this.localName = localName;
    }

    static XMLName formStar()
    {
        return new XMLName(null, "*");
    }

    static XMLName formProperty(String uri, String localName)
    {
        return new XMLName(uri, localName);
    }

    void initXMLObject(XMLObjectImpl xmlObject)
    {
        if (xmlObject == null) throw new IllegalArgumentException();
        if (this.xmlObject != null) throw new IllegalStateException();
        this.xmlObject = xmlObject;
    }

    String uri()
    {
        return uri;
    }

    String localName()
    {
        return localName;
    }

    boolean isAttributeName()
    {
        return isAttributeName;
    }

    void setAttributeName()
    {
        if (isAttributeName) throw new IllegalStateException();
        isAttributeName = true;
    }

    boolean isDescendants()
    {
        return isDescendants;
    }

    void setIsDescendants()
    {
        if (isDescendants) throw new IllegalStateException();
        isDescendants = true;
    }

    public boolean has(Context cx)
    {
        if (xmlObject == null) {
            return false;
        }
        return xmlObject.hasXMLProperty(this);
    }

    public Object get(Context cx)
    {
        if (xmlObject == null) {
            throw ScriptRuntime.undefReadError(Undefined.instance,
                                               toString());
        }
        return xmlObject.getXMLProperty(this);
    }

    public Object set(Context cx, Object value)
    {
        if (xmlObject == null) {
            throw ScriptRuntime.undefWriteError(Undefined.instance,
                                                toString(),
                                                value);
        }
        // Assignment to descendants causes parse error on bad reference
        // and this should not be called
        if (isDescendants) throw Kit.codeBug();
        xmlObject.putXMLProperty(this, value);
        return value;
    }

    public boolean delete(Context cx)
    {
        if (xmlObject == null) {
            return true;
        }
        xmlObject.deleteXMLProperty(this);
        return !xmlObject.hasXMLProperty(this);
    }

    public String toString()
    {
        //return qname.localName();
        StringBuffer buff = new StringBuffer();
        if (isDescendants) buff.append("..");
        if (isAttributeName) buff.append('@');
        if (uri == null) {
            buff.append('*');
            if(localName().equals("*")) {
                return buff.toString();
            }
        } else {
            buff.append('"').append(uri()).append('"');
        }
        buff.append(':').append(localName());
        return buff.toString();
    }

}
