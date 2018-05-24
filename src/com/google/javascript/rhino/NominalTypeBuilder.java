/*
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
 *   Ben Lickly
 *   Dimitris Vardoulakis
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

package com.google.javascript.rhino;

import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;

/**
 * Builder interface for declaring properties on class-like (nominal) types. Nominal types consist
 * primarily of three separate object types, which may each have their own properties declared on
 * them separately: (1) the constructor function, (2) the instance type, and (3) the prototype
 * object.
 *
 * This interface is used during the first part of type checking, while the type checker builds up
 * the set of known types. At this stage, not all types actually exist in a usable form:
 * specifically, NTI cannot express the type of a prototype object until immediately before the
 * owner type is frozen. Thus, this interface serves as a placeholder to allow operating on (i.e.
 * both assigning properties to, and referencing the yet-to-exist type) these not-yet-available
 * types.
 */
public interface NominalTypeBuilder {

  /** Declares a static property on the nominal type's constructor. */
  void declareConstructorProperty(String name, JSType type, Node defSite);
  /** Declares an instance property on the nominal type. */
  void declareInstanceProperty(String name, JSType type, Node defSite);
  /** Declares a property on the nominal type's prototype. */
  void declarePrototypeProperty(String name, JSType type, Node defSite);

  /** Returns a NominalTypeBuilder for this type's superclass. */
  NominalTypeBuilder superClass();

  /** Returns the constructor as a JSType. */
  FunctionType constructor();
  /** Returns the instance type as a JSType. */
  ObjectType instance();

  // TODO(sdh): See if we can just delete this entirely and use instance() instead?
  /** Returns the type of the prototype object (OTI) or instance (NTI). */
  ObjectType prototypeOrInstance();
}
