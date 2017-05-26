/*
 * Copyright 2014 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.newtypes;

import java.io.Serializable;
import java.util.Collection;

/**
 * A type that can contain properties, such as an ObjectType, and
 * EnumType (a separate, special case, of ObjectType).
 */
interface TypeWithProperties extends Serializable {
  /**
   * Get the inferred type of the given property. Returns the undefined
   * type if the named property is not found.
   */
  JSType getProp(QualifiedName qname);

  /**
   * Get the declared type of the given property, or null if the named
   * property is not declared.
   */
  JSType getDeclaredProp(QualifiedName qname);

  /**
   * Return true if this type contains any form of the given property
   * (this may include declared, inferred, required, or optional properties).
   */
  boolean mayHaveProp(QualifiedName qname);

  /**
   * Return true if this type contains the given required property.
   * The qname must be an identifier, not a general qualified name.
   */
  boolean hasProp(QualifiedName qname);

  /**
   * Return true if this type contains the given property and it is constant.
   * The qname must be an identifier, not a general qualified name.
   */
  boolean hasConstantProp(QualifiedName qname);

  /**
   * Return all topmost subtypes of this type that have the given property.
   * If the type itself has the property then only this type is included in the result.
   */
  Collection<JSType> getSubtypesWithProperty(QualifiedName qname);
}
