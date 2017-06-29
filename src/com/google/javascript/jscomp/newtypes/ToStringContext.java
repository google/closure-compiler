/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration options for {@link JSType#toString}.  This object is
 * passed around to all internal types.
 */
public class ToStringContext {

  /**
   * Returns a context that keeps track of printed type variables.
   * The first variable printed with each non-uniquified (simple)
   * name is left as-is. Any later variables are printed in full
   * disambiguated form. For example, if the same context is passed
   * to the "expected" and "found" types in a mismatch message where
   * the types only differ by similarly-named type variables, the
   * result might be {@code
   *   Expected: Foo<T#1>,
   *   Found: Foo<T#2>.
   * }
   */
  public static ToStringContext disambiguateTypeVars(JSType... types) {
    DisambiguateTypeVars ctx = new DisambiguateTypeVars();
    StringBuilder sb = new StringBuilder();
    for (JSType type : types) {
      type.appendTo(sb, ctx);
    }
    ctx.freeze();
    return ctx;
  }

  /** The default context for toString(). */
  static final ToStringContext TO_STRING = new ToStringContext();

  /** The default context for toAnnotationString(). */
  static final ToStringContext FOR_ANNOTATION =
      new ToStringContext() {
        @Override
        boolean forAnnotation() {
          return true;
        }
      };

  // Nobody else should be making instances.
  private ToStringContext() {}

  String formatTypeVar(String typeVar) {
    return UniqueNameGenerator.getOriginalName(typeVar);
  }

  boolean forAnnotation() {
    return false;
  }

  private static class DisambiguateTypeVars extends ToStringContext {
    // Map from typevar name prefix (a string without #) to all seen typevars that share the prefix
    final SetMultimap<String, String> simpleToUnique = HashMultimap.create();
    // Maps the actual internal name of a typevar to the display name chosen for this context
    final Map<String, String> renamings = new HashMap<>();

    @Override
    String formatTypeVar(String typeVar) {
      String result = renamings.get(typeVar);
      if (result != null) {
        return result;
      }
      String simple = super.formatTypeVar(typeVar);
      simpleToUnique.put(simple, typeVar);
      result = simple + "#" + simpleToUnique.get(simple).size();
      renamings.put(typeVar, result);
      return result;
    }

    /**
     * Removes unnecessary #-suffixes from the renamings map for all cases where the non-suffixed
     * name is not ambiguous.  This is called after the context has seen all the types that will
     * be printed, but before actually generating any messages.  Any new typevars seen after
     * freezing will always be suffixed, even if not necessary for disambiguation.
     */
    void freeze() {
      for (Map.Entry<String, Collection<String>> entry : simpleToUnique.asMap().entrySet()) {
        if (entry.getValue().size() == 1) {
          renamings.put(entry.getValue().iterator().next(), entry.getKey());
        }
      }
    }
  }
}
