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

/**
 * A type that can contain properties,
 * such as an ObjectType, NominalType, or a Namespace.
 */
interface TypeWithProperties {
  /** Get the inferred type of the given property */
  JSType getProp(QualifiedName qname);

  /** Get the declared type of the given property */
  JSType getDeclaredProp(QualifiedName qname);

  /** Return whether this type contains any form of property */
  boolean mayHaveProp(QualifiedName qname);

  /** Return whether this type contains a required property */
  boolean hasProp(QualifiedName qname);

  /** Return whether this type contains a constant property */
  boolean hasConstantProp(QualifiedName qname);
}
