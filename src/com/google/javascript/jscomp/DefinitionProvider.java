/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.rhino.Node;

import java.util.Collection;

/**
 * Maps variable uses sites to variable definition sites.
 *
 */
interface DefinitionProvider {
  /**
   * Returns a collection of definitions that characterize the
   * possible values of a variable or property.  If information is
   * unavailable or incomplete, return null.  This function should
   * never return an empty collection.
   *
   * @return non-empty definition collection, or null.
   */
  Collection<Definition> getDefinitionsReferencedAt(Node useSite);
}
