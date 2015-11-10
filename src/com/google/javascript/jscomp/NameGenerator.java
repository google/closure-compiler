/*
 * Copyright 2015 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;

import java.util.Set;

import javax.annotation.Nullable;

/**
 * A class that generates unique JavaScript variable/property names.
 */
interface NameGenerator {
  /**
   * Reconfigures this NameGenerator, and resets it to the initial state.
   *
   * @param reservedNames set of names that are reserved; generated names will
   *   not include these names. This set is referenced rather than copied,
   *   so changes to the set will be reflected in how names are generated.
   * @param prefix all generated names begin with this prefix.
   * @param reservedCharacters If specified these characters won't be used in
   *   generated names
   */
  void reset(
      Set<String> reservedNames,
      String prefix,
      @Nullable char[] reservedCharacters);

  /**
   * Returns a clone of this NameGenerator, reconfigured and reset.
   */
  NameGenerator clone(
      Set<String> reservedNames,
      String prefix,
      @Nullable char[] reservedCharacters);

  /**
   * Generates the next name.
   */
  String generateNextName();
}
