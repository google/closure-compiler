/*
 * Copyright 2016 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.gwt.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gwt.core.client.EntryPoint;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.WarningLevel;

import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

import java.util.Map;

/**
 * Runner for the GWT-compiled JSCompiler
 *
 * @author moz@google.com (Michael Zhou)
 */
@JsType(namespace = JsPackage.GLOBAL, name = "JSCompiler")
public final class GwtRunner implements EntryPoint {

  private static final Map<String, CompilationLevel> COMPILATION_LEVEL_MAP =
      ImmutableMap.of(
          "WHITESPACE_ONLY",
          CompilationLevel.WHITESPACE_ONLY,
          "SIMPLE",
          CompilationLevel.SIMPLE_OPTIMIZATIONS,
          "SIMPLE_OPTIMIZATIONS",
          CompilationLevel.SIMPLE_OPTIMIZATIONS,
          "ADVANCED",
          CompilationLevel.ADVANCED_OPTIMIZATIONS,
          "ADVANCED_OPTIMIZATIONS",
          CompilationLevel.ADVANCED_OPTIMIZATIONS);

  private static final Map<String, WarningLevel> WARNING_LEVEL_MAP =
      ImmutableMap.of(
          "QUIET",
          WarningLevel.QUIET,
          "DEFAULT",
          WarningLevel.DEFAULT,
          "VERBOSE",
          WarningLevel.VERBOSE);

  private final Compiler compiler;

  public GwtRunner() {
    compiler = new Compiler();
    compiler.disableThreads();
  }

  @JsType(namespace = JsPackage.GLOBAL, name = "Object", isNative = true)
  private interface Flags {
    @JsProperty
    String getCompilationLevel();

    @JsProperty
    String getWarningLevel();

    @JsProperty
    String getLanguageIn();

    @JsProperty
    String getLanguageOut();

    @JsProperty
    boolean getChecksOnly();

    @JsProperty
    boolean getNewTypeInf();

    @JsProperty
    boolean getPreserveTypeAnnotations();

    @JsProperty
    boolean getRewritePolyfills();
  }

  private static void applyDefaultOptions(CompilerOptions options) {
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    WarningLevel.DEFAULT.setOptionsForWarningLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setPrettyPrint(true);
  }

  // TODO(moz): Handle most compiler flags and report errors on invalid flags / values.
  private static void applyOptionsFromFlags(CompilerOptions options, Flags flags) {
    if (flags == null) {
      return;
    }

    if (flags.getCompilationLevel() != null) {
      CompilationLevel level =
          COMPILATION_LEVEL_MAP.get(flags.getCompilationLevel().toUpperCase());
      if (level != null) {
        level.setOptionsForCompilationLevel(options);
      }
    }

    if (flags.getWarningLevel() != null) {
      WarningLevel level = WARNING_LEVEL_MAP.get(flags.getWarningLevel().toUpperCase());
      if (level != null) {
        level.setOptionsForWarningLevel(options);
      }
    }

    if (flags.getLanguageIn() != null) {
      LanguageMode languageIn = LanguageMode.fromString(flags.getLanguageIn());
      if (languageIn != null) {
        options.setLanguageIn(languageIn);
      }
    }

    if (flags.getLanguageOut() != null) {
      LanguageMode languageOut = LanguageMode.fromString(flags.getLanguageOut());
      if (languageOut != null) {
        options.setLanguageOut(languageOut);
      }
    }

    options.setChecksOnly(flags.getChecksOnly());
    options.setNewTypeInference(flags.getNewTypeInf());
    options.setPreserveTypeAnnotations(flags.getPreserveTypeAnnotations());
    options.setRewritePolyfills(flags.getRewritePolyfills());
  }

  private static void disableUnsupportedOptions(CompilerOptions options) {
    options.getDependencyOptions().setDependencySorting(false);
  }

  public String compile(String js, Flags flags) {
    CompilerOptions options = new CompilerOptions();
    applyDefaultOptions(options);
    applyOptionsFromFlags(options, flags);
    disableUnsupportedOptions(options);
    SourceFile src = SourceFile.fromCode("src.js", js);
    SourceFile externs = SourceFile.fromCode("externs.js", "var window;");
    compiler.compile(ImmutableList.of(externs), ImmutableList.of(src), options);
    return compiler.toSource();
  }

  @Override
  public void onModuleLoad() {}
}
