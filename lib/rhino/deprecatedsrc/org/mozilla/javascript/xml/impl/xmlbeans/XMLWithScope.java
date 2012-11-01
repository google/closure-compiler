/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.xml.impl.xmlbeans;

import org.mozilla.javascript.*;
import org.mozilla.javascript.xml.*;

final class XMLWithScope extends NativeWith
{
    private static final long serialVersionUID = -696429282095170887L;

    private XMLLibImpl lib;
    private int         _currIndex;
    private XMLList     _xmlList;
    private XMLObject   _dqPrototype;

    XMLWithScope(XMLLibImpl lib, Scriptable parent, XMLObject prototype)
    {
        super(parent, prototype);
        this.lib = lib;
    }

    void initAsDotQuery()
    {
        XMLObject prototype = (XMLObject)getPrototype();
        // XMLWithScope also handles the .(xxx) DotQuery for XML
        // basically DotQuery is a for/in/with statement and in
        // the following 3 statements we setup to signal it's
        // DotQuery,
        // the index and the object being looped over.  The
        // xws.setPrototype is the scope of the object which is
        // is a element of the lhs (XMLList).
        _currIndex = 0;
        _dqPrototype = prototype;
        if (prototype instanceof XMLList) {
            XMLList xl = (XMLList)prototype;
            if (xl.length() > 0) {
                setPrototype((Scriptable)(xl.get(0, null)));
            }
        }
        // Always return the outer-most type of XML lValue of
        // XML to left of dotQuery.
        _xmlList = new XMLList(lib);
    }

    protected Object updateDotQuery(boolean value)
    {
        // Return null to continue looping

        XMLObject seed = _dqPrototype;
        XMLList xmlL = _xmlList;

        if (seed instanceof XMLList) {
            // We're a list so keep testing each element of the list if the
            // result on the top of stack is true then that element is added
            // to our result list.  If false, we try the next element.
            XMLList orgXmlL = (XMLList)seed;

            int idx = _currIndex;

            if (value) {
                xmlL.addToList(orgXmlL.get(idx, null));
            }

            // More elements to test?
            if (++idx < orgXmlL.length()) {
                // Yes, set our new index, get the next element and
                // reset the expression to run with this object as
                // the WITH selector.
                _currIndex = idx;
                setPrototype((Scriptable)(orgXmlL.get(idx, null)));

                // continue looping
                return null;
            }
        } else {
            // If we're not a XMLList then there's no looping
            // just return DQPrototype if the result is true.
            if (value) {
              xmlL.addToList(seed);
            }
        }

        return xmlL;
    }
}
