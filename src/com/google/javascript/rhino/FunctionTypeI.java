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

import java.util.Collection;
import java.util.List;

/**
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public interface FunctionTypeI extends ObjectTypeI {
  /**
   * Creates a new function type B based on the original function type A.
   * Takes the receiver type (this type) of A and makes it the first
   * argument of B; B has no receiver type.
   * Used by the DevirtualizePrototypeMethods pass.
   */
  TypeI convertMethodToFunction();

  /** Returns whether {@code this} type represents a constructor. */
  boolean hasInstanceType();

  /**
   * Returns a type representing an instance of {@code this} constructor,
   * or null if {@code this} is not a constructor.
   */
  ObjectTypeI getInstanceType();

  TypeI getReturnType();

  /**
   * For a constructor function, returns the name of the instances.
   * For other functions, returns null.
   */
  String getReferenceName();

  /** Gets the AST Node where this function was defined. */
  Node getSource();

  /**
   * Returns an iterable of direct types that are subtypes of this type.
   * This is only valid for constructors and interfaces, and will not be
   * null. This allows a downward traversal of the subtype graph.
   */
  Iterable<FunctionTypeI> getDirectSubTypes();

  /** Gets the type of {@code this} in this function. */
  TypeI getTypeOfThis();

  /** Whether this function type has any properties (not counting "prototype"). */
  boolean hasProperties();

  void setSource(Node n);

  /** Checks if a call to this function with the given list of arguments is valid. */
  boolean acceptsArguments(List<? extends TypeI> argumentTypes);

  Collection<ObjectTypeI> getAncestorInterfaces();

  ObjectTypeI getPrototypeProperty();

  Iterable<TypeI> getParameterTypes();

  /** Returns the number of required arguments. */
  int getMinArity();

  /** Returns the maximum number of allowed arguments, or Integer.MAX_VALUE if variadic. */
  int getMaxArity();

  /** Returns a Builder instance initialized to this function. */
  Builder toBuilder();

  /** Interface for building FunctionTypeI instances. */
  interface Builder {
    /** Returns a builder with unknown return type. */
    Builder withUnknownReturnType();

    /** Returns a builder with the given return type. */
    Builder withReturnType(TypeI returnType);

    /** Returns a builder with an empty parameter list. */
    Builder withNoParameters();

    /** Builds a new FunctionTypeI. */
    FunctionTypeI build();
  }
}
