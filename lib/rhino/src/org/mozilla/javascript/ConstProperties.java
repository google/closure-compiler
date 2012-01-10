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
 *   Bob Jervis
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

// API class

package org.mozilla.javascript;

public interface ConstProperties {
    /**
     * Sets a named const property in this object.
     * <p>
     * The property is specified by a string name
     * as defined for <code>Scriptable.get</code>.
     * <p>
     * The possible values that may be passed in are as defined for
     * <code>Scriptable.get</code>. A class that implements this method may choose
     * to ignore calls to set certain properties, in which case those
     * properties are effectively read-only.<p>
     * For properties defined in a prototype chain,
     * use <code>putProperty</code> in ScriptableObject. <p>
     * Note that if a property <i>a</i> is defined in the prototype <i>p</i>
     * of an object <i>o</i>, then evaluating <code>o.a = 23</code> will cause
     * <code>set</code> to be called on the prototype <i>p</i> with
     * <i>o</i> as the  <i>start</i> parameter.
     * To preserve JavaScript semantics, it is the Scriptable
     * object's responsibility to modify <i>o</i>. <p>
     * This design allows properties to be defined in prototypes and implemented
     * in terms of getters and setters of Java values without consuming slots
     * in each instance.<p>
     * <p>
     * The values that may be set are limited to the following:
     * <UL>
     * <LI>java.lang.Boolean objects</LI>
     * <LI>java.lang.String objects</LI>
     * <LI>java.lang.Number objects</LI>
     * <LI>org.mozilla.javascript.Scriptable objects</LI>
     * <LI>null</LI>
     * <LI>The value returned by Context.getUndefinedValue()</LI>
     * </UL><p>
     * Arbitrary Java objects may be wrapped in a Scriptable by first calling
     * <code>Context.toObject</code>. This allows the property of a JavaScript
     * object to contain an arbitrary Java object as a value.<p>
     * Note that <code>has</code> will be called by the runtime first before
     * <code>set</code> is called to determine in which object the
     * property is defined.
     * Note that this method is not expected to traverse the prototype chain,
     * which is different from the ECMA [[Put]] operation.
     * @param name the name of the property
     * @param start the object whose property is being set
     * @param value value to set the property to
     * @see org.mozilla.javascript.Scriptable#has(String, Scriptable)
     * @see org.mozilla.javascript.Scriptable#get(String, Scriptable)
     * @see org.mozilla.javascript.ScriptableObject#putProperty(Scriptable, String, Object)
     * @see org.mozilla.javascript.Context#toObject(Object, Scriptable)
     */
    public void putConst(String name, Scriptable start, Object value);

    /**
     * Reserves a definition spot for a const.  This will set up a definition
     * of the const property, but set its value to undefined.  The semantics of
     * the start parameter is the same as for putConst.
     * @param name The name of the property.
     * @param start The object whose property is being reserved.
     */
    public void defineConst(String name, Scriptable start);

    /**
     * Returns true if the named property is defined as a const on this object.
     * @param name
     * @return true if the named property is defined as a const, false
     * otherwise.
     */
    public boolean isConst(String name);
}
