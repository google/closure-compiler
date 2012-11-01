/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.xml.impl.xmlbeans;

import java.util.*;
import org.apache.xmlbeans.XmlCursor;

import org.mozilla.javascript.*;

class NamespaceHelper
{
    private XMLLibImpl lib;
    private final Map prefixToURI = new HashMap();
    private final Map uriToPrefix = new HashMap();
    // A set of URIs that are used without explicit namespace declaration in scope.
    private final Set undeclared = new HashSet();

    private NamespaceHelper(XMLLibImpl lib)
    {
        this.lib = lib;
        // Insert the default namespace
        prefixToURI.put("", "");
        Set prefixes = new HashSet();
        prefixes.add("");
        uriToPrefix.put("", prefixes);
    }

    /**
     * Declared a new namespace
     *
     * @param prefix
     * @param uri
     * @param declarations
     */
    private void declareNamespace(String prefix, String uri, ObjArray declarations)
    {
        Set prefixes = (Set)uriToPrefix.get(uri);
        if(prefixes == null)
        {
            prefixes = new HashSet();
            uriToPrefix.put(uri, prefixes);
        }

        if(!prefixes.contains(prefix))
        {
            String oldURI = (String)prefixToURI.get(prefix);

            // Add the new mapping
            prefixes.add(prefix);
            prefixToURI.put(prefix, uri);
            if(declarations != null)
                declarations.add(new Namespace(lib, prefix, uri));

            if(oldURI != null)
            {
                // Update the existing mapping
                prefixes = (Set)uriToPrefix.get(oldURI);
                prefixes.remove(prefix);
            }
        }
    }

    /**
     * Updates the internal state of this NamespaceHelper to reflect the
     * existance of the XML token pointed to by the cursor.
     */
    private void processName(XmlCursor cursor, ObjArray declarations)
    {
        javax.xml.namespace.QName qname = cursor.getName();
        String uri = qname.getNamespaceURI();
        Set prefixes = (Set)uriToPrefix.get(uri);
        if(prefixes == null || prefixes.size() == 0)
        {
            undeclared.add(uri);
            if(declarations != null)
                declarations.add(new Namespace(lib, uri));
        }
    }

    /**
     * Updates the internal state of this NamespaceHelper with the
     * namespace information of the element pointed to by the cursor.
     */
    private void update(XmlCursor cursor, ObjArray declarations)
    {
        // Process the Namespace declarations
        cursor.push();
        while(cursor.toNextToken().isAnyAttr())
        {
            if(cursor.isNamespace())
            {
                javax.xml.namespace.QName name = cursor.getName();
                String prefix = name.getLocalPart();
                String uri = name.getNamespaceURI();

                declareNamespace(prefix, uri, declarations);
            }
        }
        cursor.pop();

        // Process the element
        processName(cursor, declarations);

        // Process the attributes
        cursor.push();
        boolean hasNext = cursor.toFirstAttribute();
        while(hasNext)
        {
            processName(cursor, declarations);
            hasNext = cursor.toNextAttribute();
        }
        cursor.pop();
    }

    /**
     * @return Object[] array of Namespace objects in scope at the cursor.
     */
    public static Object[] inScopeNamespaces(XMLLibImpl lib, XmlCursor cursor)
    {
        ObjArray namespaces = new ObjArray();
        NamespaceHelper helper = new NamespaceHelper(lib);

        cursor.push();

        int depth = 0;
        while(cursor.hasPrevToken())
        {
            if(cursor.isContainer())
            {
                cursor.push();
                depth++;
            }

            cursor.toParent();
        }

        for(int i = 0; i < depth; i++)
        {
            cursor.pop();
            helper.update(cursor, null);
        }

        Iterator i = helper.prefixToURI.entrySet().iterator();
        while(i.hasNext())
        {
            Map.Entry entry = (Map.Entry)i.next();
            Namespace ns = new Namespace(lib, (String)entry.getKey(),
                                            (String)entry.getValue());
            namespaces.add(ns);
        }

        i = helper.undeclared.iterator();
        while(i.hasNext())
        {
            Namespace ns = new Namespace(lib, (String)i.next());
            namespaces.add(ns);
        }

        cursor.pop();

        return namespaces.toArray();
    }

    static Namespace getNamespace(XMLLibImpl lib, XmlCursor cursor,
                                  Object[] inScopeNamespaces)
    {
        String uri;
        String prefix;

        if (cursor.isProcinst()) {
            uri = "";
            prefix = "";
        } else {
            javax.xml.namespace.QName qname = cursor.getName();
            uri = qname.getNamespaceURI();
            prefix = qname.getPrefix();
        }

        if (inScopeNamespaces == null)
            return new Namespace(lib, prefix, uri);

        Namespace result = null;
        for (int i = 0; i != inScopeNamespaces.length; ++i) {
            Namespace ns = (Namespace)inScopeNamespaces[i];
            if(ns == null) continue;

            String nsURI = ns.uri();
            if(nsURI.equals(uri))
            {
                if(prefix.equals(ns.prefix()))
                {
                    result = ns;
                    break;
                }

                if(result == null ||
                   (result.prefix() == null &&
                    ns.prefix() != null))
                    result = ns;
            }
        }

        if(result == null)
            result = new Namespace(lib, prefix, uri);

        return result;
    }

    /**
     * @return List of Namespace objects that are declared in the container pointed to by the cursor.
     */
    public static Object[] namespaceDeclarations(XMLLibImpl lib, XmlCursor cursor)
    {
        ObjArray declarations = new ObjArray();
        NamespaceHelper helper = new NamespaceHelper(lib);

        cursor.push();

        int depth = 0;
        while(cursor.hasPrevToken())
        {
            if(cursor.isContainer())
            {
                cursor.push();
                depth++;
            }

            cursor.toParent();
        }

        for(int i = 0; i < depth - 1; i++)
        {
            cursor.pop();
            helper.update(cursor, null);
        }

        if(depth > 0)
        {
            cursor.pop();
            helper.update(cursor, declarations);
        }

        cursor.pop();

        return declarations.toArray();
    }

    /**
     * @return Prefix to URI map of all namespaces in scope at the cursor.
     */
    public static Map getAllNamespaces(XMLLibImpl lib, XmlCursor cursor)
    {
        NamespaceHelper helper = new NamespaceHelper(lib);

        cursor.push();

        int depth = 0;
        while(cursor.hasPrevToken())
        {
            if(cursor.isContainer())
            {
                cursor.push();
                depth++;
            }

            cursor.toParent();
        }

        for(int i = 0; i < depth; i++)
        {
            cursor.pop();
            helper.update(cursor, null);
        }

        cursor.pop();

        return helper.prefixToURI;
    }

    public static void getNamespaces(XmlCursor cursor, Map prefixToURI)
    {
        cursor.push();
        while(cursor.toNextToken().isAnyAttr())
        {
            if(cursor.isNamespace())
            {
                javax.xml.namespace.QName name = cursor.getName();
                String prefix = name.getLocalPart();
                String uri = name.getNamespaceURI();

                prefixToURI.put(prefix, uri);
            }
        }
        cursor.pop();
    }

    public static void removeNamespace(XmlCursor cursor, String prefix)
    {
        cursor.push();
        while(cursor.toNextToken().isAnyAttr())
        {
            if(cursor.isNamespace())
            {
                javax.xml.namespace.QName name = cursor.getName();
                if(name.getLocalPart().equals(prefix))
                {
                    cursor.removeXml();
                    break;
                }
            }
        }
        cursor.pop();
    }
}