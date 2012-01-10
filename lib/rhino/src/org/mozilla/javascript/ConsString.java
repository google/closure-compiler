/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Hannes Wallnoefer
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.mozilla.javascript;

/**
 * <p>This class represents a string composed of two components, each of which
 * may be a <code>java.lang.String</code> or another ConsString.</p>
 *
 * <p>This string representation is optimized for concatenation using the "+"
 * operator. Instead of immediately copying both components to a new character
 * array, ConsString keeps references to the original components and only
 * converts them to a String if either toString() is called or a certain depth
 * level is reached.</p>
 *
 * <p>Note that instances of this class are only immutable if both parts are
 * immutable, i.e. either Strings or ConsStrings that are ultimately composed
 * of Strings.</p>
 *
 * <p>Both the name and the concept are borrowed from V8.</p>
 */
public class ConsString implements CharSequence {

    private CharSequence s1, s2;
    private final int length;
    private int depth;

    public ConsString(CharSequence str1, CharSequence str2) {
        s1 = str1;
        s2 = str2;
        length = str1.length() + str2.length();
        depth = 1;
        if (str1 instanceof ConsString) {
            depth += ((ConsString)str1).depth;
        }
        if (str2 instanceof ConsString) {
            depth += ((ConsString)str2).depth;
        }
        // Don't let it grow too deep, can cause stack overflows
        if (depth > 2000) {
            flatten();
        }
    }

    public String toString() {
        return depth == 0 ? (String)s1 : flatten();
    }

    private synchronized String flatten() {
        if (depth > 0) {
            StringBuilder b = new StringBuilder(length);
            appendTo(b);
            s1 = b.toString();
            s2 = "";
            depth = 0;
        }
        return (String)s1;
    }

    private synchronized void appendTo(StringBuilder b) {
        appendFragment(s1, b);
        appendFragment(s2, b);
    }

    private static void appendFragment(CharSequence s, StringBuilder b) {
        if (s instanceof ConsString) {
            ((ConsString)s).appendTo(b);
        } else {
            b.append(s);
        }
    }

    public int length() {
        return length;
    }

    public char charAt(int index) {
        String str = depth == 0 ? (String)s1 : flatten();
        return str.charAt(index);
    }

    public CharSequence subSequence(int start, int end) {
        String str = depth == 0 ? (String)s1 : flatten();
        return str.substring(start, end);
    }

}
