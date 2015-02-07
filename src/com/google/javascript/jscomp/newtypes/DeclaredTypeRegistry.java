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

import com.google.javascript.rhino.TypeIRegistry;

/** A registry capable of translating names into JSTypes. */
public interface DeclaredTypeRegistry extends TypeIRegistry {

  /**
   * Returns the named type from a given potentially qualified type name,
   * or null if the name is not defined.
   */
  JSType lookupTypeByName(String name);

  /** Returns the instance of the typedef named {@code name} */
  Typedef getTypedef(String name);

  /** Returns the instance of the enum named {@code name} */
  EnumType getEnum(String name);

  /**
   * Returns the declared JSType of the given identifier,
   * or null if the identifier is not defined.
   */
  JSType getDeclaredTypeOf(String name);

  JSTypes getCommonTypes();
}
