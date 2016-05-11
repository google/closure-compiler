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

package com.google.javascript.jscomp.deps;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A data structure for JS dependency information for a single .js file.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
public interface DependencyInfo {

  /** Gets the unique name / path of this file. */
  public String getName();

  /** Gets the path of this file relative to Closure's base.js file. */
  public String getPathRelativeToClosureBase();

  /** Gets the symbols provided by this file. */
  public Collection<String> getProvides();

  /** Gets the symbols required by this file. */
  public Collection<String> getRequires();

  /** Gets the loading information for this file. */
  public ImmutableMap<String, String> getLoadFlags();

  /** Whether the symbol is provided by a module */
  public boolean isModule();

  /**
   * Abstract base implementation that defines derived accessors such
   * as {@link #isModule}.
   */
  public abstract class Base implements DependencyInfo {
    @Override public boolean isModule() {
      return "goog".equals(getLoadFlags().get("module"));
    }
  }

  /** Utility methods. */
  class Util {
    private Util() {}

    // TODO(sdh): This would be better as a defender method once Java 8 is allowed (b/28382956):
    //     void DependencyInfo#writeAddDependency(Appendable);
    /** Prints a goog.addDependency call for a single DependencyInfo. */
    public static void writeAddDependency(Appendable out, DependencyInfo info) throws IOException {
      out.append("goog.addDependency('")
          .append(info.getPathRelativeToClosureBase())
          .append("', ");
      writeJsArray(out, info.getProvides());
      out.append(", ");
      writeJsArray(out, info.getRequires());
      Map<String, String> loadFlags = info.getLoadFlags();
      if (!loadFlags.isEmpty()) {
        out.append(", ");
        writeJsObject(out, loadFlags);
      }
      out.append(");\n");
    }

    /** Prints a map as a JS object literal. */
    private static void writeJsObject(Appendable out, Map<String, String> map) throws IOException {
      List<String> entries = new ArrayList<>();
      for (Map.Entry<String, String> entry : map.entrySet()) {
        String key = entry.getKey().replace("'", "\\'");
        String value = entry.getValue().replace("'", "\\'");
        entries.add("'" + key + "': '" + value + "'");
      }
      out.append("{");
      out.append(Joiner.on(", ").join(entries));
      out.append("}");
    }

    /** Prints a list of strings formatted as a JavaScript array of string literals. */
    private static void writeJsArray(Appendable out, Collection<String> values) throws IOException {
      Iterable<String> quoted =
          Iterables.transform(
              values,
              new Function<String, String>() {
                @Override public String apply(String arg) {
                  return "'" + arg.replace("'", "\\'") + "'";
                }
              });
      out.append("[");
      out.append(Joiner.on(", ").join(quoted));
      out.append("]");
    }
  }
}
