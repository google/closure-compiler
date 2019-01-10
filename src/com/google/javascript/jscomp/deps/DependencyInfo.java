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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.errorprone.annotations.Immutable;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A data structure for JS dependency information for a single .js file.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
public interface DependencyInfo extends Serializable {

  /** A dependency link between two files, e.g. goog.require('namespace'), import 'file'; */
  @AutoValue
  @Immutable
  abstract class Require implements Serializable {
    public static final Require BASE = googRequireSymbol("goog");

    public enum Type {
      /** Standard goog.require call for a symbol from a goog.provide or goog.module. */
      GOOG_REQUIRE_SYMBOL,
      /** ES6 import statement. */
      ES6_IMPORT,
      /** Parsed from an existing Closure dependency file. */
      PARSED_FROM_DEPS,
      /** CommonJS require() call. */
      COMMON_JS,
      /** Compiler module dependencies. */
      COMPILER_MODULE
    }

    public static ImmutableList<String> asSymbolList(Iterable<Require> requires) {
      return Streams.stream(requires).map(Require::getSymbol).collect(toImmutableList());
    }

    public static Require googRequireSymbol(String symbol) {
      return builder()
          .setRawText(symbol)
          .setSymbol(symbol)
          .setType(Type.GOOG_REQUIRE_SYMBOL)
          .build();
    }

    public static Require es6Import(String symbol, String rawPath) {
      return builder().setRawText(rawPath).setSymbol(symbol).setType(Type.ES6_IMPORT).build();
    }

    public static Require commonJs(String symbol, String rawPath) {
      return builder().setRawText(rawPath).setSymbol(symbol).setType(Type.COMMON_JS).build();
    }

    public static Require compilerModule(String symbol) {
      return builder().setRawText(symbol).setSymbol(symbol).setType(Type.COMPILER_MODULE).build();
    }

    public static Require parsedFromDeps(String symbol) {
      return builder().setRawText(symbol).setSymbol(symbol).setType(Type.PARSED_FROM_DEPS).build();
    }

    private static Builder builder() {
      return new AutoValue_DependencyInfo_Require.Builder();
    }

    protected abstract Builder toBuilder();

    public Require withSymbol(String symbol) {
      return toBuilder().setSymbol(symbol).build();
    }

    /**
     * @return symbol the symbol provided by another {@link DependencyInfo}'s {@link
     *     DependencyInfo#getProvides()}
     */
    public abstract String getSymbol();

    /**
     * @return the raw text of the import string as it appears in the file. Used mostly for error
     *     reporting.
     */
    public abstract String getRawText();

    public abstract Type getType();

    @AutoValue.Builder
    abstract static class Builder {
      public abstract Builder setType(Type value);

      public abstract Builder setRawText(String rawText);

      public abstract Builder setSymbol(String value);

      public abstract Require build();
    }
  }

  /** Gets the unique name / path of this file. */
  String getName();

  /** Gets the path of this file relative to Closure's base.js file. */
  String getPathRelativeToClosureBase();

  /** Gets the symbols provided by this file. */
  ImmutableList<String> getProvides();

  /** Gets the symbols required by this file. */
  ImmutableList<Require> getRequires();

  ImmutableList<String> getRequiredSymbols();

  /** Gets the symbols type-required by this file (i.e. for typechecking only). */
  ImmutableList<String> getTypeRequires();

  /** Gets the loading information for this file. */
  ImmutableMap<String, String> getLoadFlags();

  /** Whether the symbol is provided by a module */
  boolean isModule();

  /**
   * Abstract base implementation that defines derived accessors such
   * as {@link #isModule}.
   */
  abstract class Base implements DependencyInfo {
    @Override public boolean isModule() {
      return "goog".equals(getLoadFlags().get("module"));
    }

    @Override
    public ImmutableList<String> getRequiredSymbols() {
      return Require.asSymbolList(getRequires());
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
      writeJsArray(out, Require.asSymbolList(info.getRequires()));
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
          Iterables.transform(values, arg -> "'" + arg.replace("'", "\\'") + "'");
      out.append("[");
      out.append(Joiner.on(", ").join(quoted));
      out.append("]");
    }
  }
}
