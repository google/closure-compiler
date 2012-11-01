/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.xml.impl.xmlbeans;

import org.apache.xmlbeans.XmlCursor;

import java.util.*;


public class LogicalEquality
{
    public static boolean nodesEqual(XmlCursor xmlOne, XmlCursor xmlTwo)
    {
        boolean result = false;

        if (xmlOne.isStartdoc())
        {
            xmlOne.toFirstContentToken();
        }

        if (xmlTwo.isStartdoc())
        {
            xmlTwo.toFirstContentToken();
        }

        if (xmlOne.currentTokenType() == xmlTwo.currentTokenType())
        {
            if (xmlOne.isEnddoc())
            {
                // Both empty
                result = true;
            }
            else if (xmlOne.isAttr())
            {
                result = attributesEqual(xmlOne, xmlTwo);
            }
            else if (xmlOne.isText())
            {
                result = textNodesEqual(xmlOne, xmlTwo);
            }
            else if (xmlOne.isComment())
            {
                result = commentsEqual(xmlOne, xmlTwo);
            }
            else if (xmlOne.isProcinst())
            {
                result = processingInstructionsEqual(xmlOne, xmlTwo);
            }
            else if (xmlOne.isStart())
            {
                // Compare root elements
                result = elementsEqual(xmlOne, xmlTwo);
            }
        }

        return result;
    }

    private static boolean elementsEqual(XmlCursor xmlOne, XmlCursor xmlTwo)
    {
        boolean result = true;

        if (!qnamesEqual(xmlOne.getName(), xmlTwo.getName()))
        {
            result = false;
        }
        else
        {
            // These filter out empty text nodes.
            nextToken(xmlOne);
            nextToken(xmlTwo);

            do
            {
                if (xmlOne.currentTokenType() != xmlTwo.currentTokenType())
                {
                    // Not same token
                    result = false;
                    break;
                }
                else if (xmlOne.isEnd())
                {
                    // Done with this element, step over end
                    break;
                }
                else if (xmlOne.isEnddoc())
                {
                    // Shouldn't get here
                    break;
                }
                else if (xmlOne.isAttr())
                {
                    // This one will move us to the first non-attr token.
                    result = attributeListsEqual(xmlOne, xmlTwo);
                }
                else
                {
                    if (xmlOne.isText())
                    {
                        result = textNodesEqual(xmlOne, xmlTwo);
                    }
                    else if (xmlOne.isComment())
                    {
                        result = commentsEqual(xmlOne, xmlTwo);
                    }
                    else if (xmlOne.isProcinst())
                    {
                        result = processingInstructionsEqual(xmlOne, xmlTwo);
                    }
                    else if (xmlOne.isStart())
                    {
                        result = elementsEqual(xmlOne, xmlTwo);
                    }
                    else
                    {
                        //XML.log("Unknown token type" + xmlOne.currentTokenType());
                    }

                    // These filter out empty text nodes.
                    nextToken(xmlOne);
                    nextToken(xmlTwo);
                }
            }
            while(result);
        }

        return result;
    }

    /**
     *
     * @param xmlOne
     * @param xmlTwo
     * @return
     */
    private static boolean attributeListsEqual(XmlCursor xmlOne, XmlCursor xmlTwo)
    {
        boolean result = true;
        TreeMap mapOne = loadAttributeMap(xmlOne);
        TreeMap mapTwo = loadAttributeMap(xmlTwo);

        if (mapOne.size() != mapTwo.size())
        {
            result = false;
        }
        else
        {
            Set keysOne = mapOne.keySet();
            Set keysTwo = mapTwo.keySet();
            Iterator itOne = keysOne.iterator();
            Iterator itTwo = keysTwo.iterator();

            while (result && itOne.hasNext())
            {
                String valueOne = (String) itOne.next();
                String valueTwo = (String) itTwo.next();

                if (!valueOne.equals(valueTwo))
                {
                    result = false;
                }
                else
                {
                    javax.xml.namespace.QName qnameOne = (javax.xml.namespace.QName) mapOne.get(valueOne);
                    javax.xml.namespace.QName qnameTwo = (javax.xml.namespace.QName) mapTwo.get(valueTwo);

                    if (!qnamesEqual(qnameOne, qnameTwo))
                    {
                        result = false;
                    }
                }
            }
        }

        return result;
    }

    /**
     *
     * @param xml
     * @return
     */
    private static TreeMap loadAttributeMap(XmlCursor xml)
    {
        TreeMap result = new TreeMap();

        while (xml.isAttr())
        {
            result.put(xml.getTextValue(), xml.getName());
            nextToken(xml);
        }

        return result;
    }

    /**
     *
     * @param xmlOne
     * @param xmlTwo
     * @return
     */
    private static boolean attributesEqual(XmlCursor xmlOne, XmlCursor xmlTwo)
    {
        boolean result = false;

        if (xmlOne.isAttr() && xmlTwo.isAttr())
        {
            if (qnamesEqual(xmlOne.getName(), xmlTwo.getName()))
            {
                if (xmlOne.getTextValue().equals(xmlTwo.getTextValue()))
                {
                    result = true;
                }
            }
        }

        return result;
    }

    /**
     *
     * @param xmlOne
     * @param xmlTwo
     * @return
     */
    private static boolean textNodesEqual(XmlCursor xmlOne, XmlCursor xmlTwo)
    {
        boolean result = false;

        if (xmlOne.isText() && xmlTwo.isText())
        {
            if (xmlOne.getChars().equals(xmlTwo.getChars()))
            {
                result = true;
            }
        }

        return result;
    }

    /**
     *
     * @param xmlOne
     * @param xmlTwo
     * @return
     */
    private static boolean commentsEqual(XmlCursor xmlOne, XmlCursor xmlTwo)
    {
        boolean result = false;

        if (xmlOne.isComment() && xmlTwo.isComment())
        {
            if (xmlOne.getTextValue().equals(xmlTwo.getTextValue()))
            {
                result = true;
            }
        }

        return result;
    }

    /**
     *
     * @param xmlOne
     * @param xmlTwo
     * @return
     */
    private static boolean processingInstructionsEqual(XmlCursor xmlOne, XmlCursor xmlTwo)
    {
        boolean result = false;

        if (xmlOne.isProcinst() && xmlTwo.isProcinst())
        {
            if (qnamesEqual(xmlOne.getName(), xmlTwo.getName()))
            {
                if (xmlOne.getTextValue().equals(xmlTwo.getTextValue()))
                {
                    result = true;
                }
            }
        }

        return result;
    }

    /**
     *
     * @param qnameOne
     * @param qnameTwo
     * @return
     */
    private static boolean qnamesEqual(javax.xml.namespace.QName qnameOne, javax.xml.namespace.QName qnameTwo)
    {
        boolean result = false;

        if (qnameOne.getNamespaceURI().equals(qnameTwo.getNamespaceURI()))
        {
            if (qnameOne.getLocalPart().equals(qnameTwo.getLocalPart()))
            {
                return true;
            }
        }

        return result;
    }

    /**
     * filter out empty textNodes here
     *
     * @param xml
     */
    private static void nextToken(XmlCursor xml)
    {
        do
        {
            xml.toNextToken();

            if (!xml.isText())
            {
                // Not a text node
                break;
            }
            else if (xml.getChars().trim().length() > 0)
            {
                // Text node is not empty
                break;
            }
        }
        while (true);
    }
}
