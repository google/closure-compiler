/*
 * Copyright 2013 The Closure Compiler Authors.
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

/** A registry capable of translating names into JSTypes. */
public interface DeclaredTypeRegistry {

  /**
   * Get the type of the function that the declared type registry represents.
   */
  DeclaredFunctionType getDeclaredFunctionType();

  /**
   * Returns the declaration of the given qualified name,
   * or null if the name is not defined.
   * If {@code includeTypes} is true, include definitions that are not in code,
   * such as @template parameters and forward declarations.
   */
  Declaration getDeclaration(QualifiedName qname, boolean includeTypes);

  /**
   * Returns the declared JSType of the given identifier,
   * or null if the identifier is not defined.
   */
  JSType getDeclaredTypeOf(String name);

  JSTypes getCommonTypes();
}
