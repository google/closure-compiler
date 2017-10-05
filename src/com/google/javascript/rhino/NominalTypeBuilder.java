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

  /** An individual object type that can have properties declared on it. */
  interface ObjectBuilder {
    /** Declares a property on this object type. */
    void declareProperty(String name, TypeI type, Node defSite);

    /**
     * Returns a TypeI referring to this object, if possible. This will succeed as long as the
     * final object is just a pointer to a mutable type (i.e. constructors and instances). For
     * prototypes, however, the final object is immutable and is only constructed at the last
     * possible moment and is therefore not available any earlier. If this is a prototype object
     * then toTypeI() will throw an IllegalStateException.
     *
     * Note also that the returned TypeI may not be frozen, and so certain functionality is very
     * likely to be missing (e.g. getSuperClassConstructor does not work, among many others).
     */
    TypeI toTypeI();
  }

  /**
   * Ensures that the given task will run before freezing this nominal type, but after any
   * prerequisites have been frozen. Any types passed as prerequisites are safe to access
   * via {@link ObjectBuilder#toTypeI} within the body of the runnable. The task will never
   * run immediately, but may be run very soon after the coding convention call returns.
   */
  void beforeFreeze(Runnable task, NominalTypeBuilder... prerequisites);

  /** Returns a nominal type builder for the super class. */
  NominalTypeBuilder superClass();

  /** Returns the object builder for declaring properties on the constructor function. */
  ObjectBuilder constructor();

  /** Returns the object builder for declaring properties on the instance type. */
  ObjectBuilder instance();

  /**
   * Returns the object builder for declaring properties on the prototype object. The returned
   * ObjectBuilder cannot be converted to a TypeI, since no TypeI is available yet.
   */
  ObjectBuilder prototype();
}
