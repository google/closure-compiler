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
import com.google.javascript.jscomp.ResourceLoader;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.WarningLevel;

import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runner for the GWT-compiled JSCompiler
 *
 * @author moz@google.com (Michael Zhou)
 */
@JsType(namespace = JsPackage.GLOBAL, name = "JSCompiler")
public final class GwtRunner implements EntryPoint {

  // The core language externs in sorted order.
  private static final List<String> BUILTIN_EXTERNS_LANG = ImmutableList.of(
      "es3.js",
      "es5.js",
      "es6.js",
      "es6_collections.js");

  // The browser externs in sorted order.
  private static final List<String> BUILTIN_EXTERNS_BROWSER_DEP_ORDER = ImmutableList.of(
      "browser/intl.js",
      "browser/w3c_event.js",
      "browser/w3c_event3.js",
      "browser/gecko_event.js",
      "browser/ie_event.js",
      "browser/webkit_event.js",
      "browser/w3c_device_sensor_event.js",
      "browser/w3c_dom1.js",
      "browser/w3c_dom2.js",
      "browser/w3c_dom3.js",
      "browser/gecko_dom.js",
      "browser/ie_dom.js",
      "browser/webkit_dom.js",
      "browser/w3c_css.js",
      "browser/gecko_css.js",
      "browser/ie_css.js",
      "browser/webkit_css.js",
      "browser/w3c_touch_event.js");

  // Extra browser externs.
  private static final List<String> BUILTIN_EXTERNS_BROWSER_EXTRA = ImmutableList.of(
      "browser/fileapi.js",
      "browser/html5.js",
      "browser/page_visibility.js",
      "browser/w3c_batterystatus.js",
      "browser/w3c_range.js",
      "browser/w3c_xml.js");

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
  private List<SourceFile> builtInExterns;

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
    options.setLanguageIn(LanguageMode.ECMASCRIPT6);
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
    options.getDependencyOptions().setEs6ModuleOrder(false);
  }

  // TODO(moz): Handle custom environment with CompilerOptions.Environment.
  private static List<SourceFile> loadBuiltInExterns() {
    List<SourceFile> externs = new ArrayList<>();
    String pathPrefix = "externs/";
    for (String key : BUILTIN_EXTERNS_LANG) {
      String path = pathPrefix + key;
      externs.add(
          SourceFile.fromCode(path, ResourceLoader.loadTextResource(GwtRunner.class, path)));
    }

    for (String key : BUILTIN_EXTERNS_BROWSER_DEP_ORDER) {
      String path = pathPrefix + key;
      externs.add(
          SourceFile.fromCode(path, ResourceLoader.loadTextResource(GwtRunner.class, path)));
    }

    for (String key : BUILTIN_EXTERNS_BROWSER_EXTRA) {
      String path = pathPrefix + key;
      externs.add(
          SourceFile.fromCode(path, ResourceLoader.loadTextResource(GwtRunner.class, path)));
    }
    return externs;
  }

  public String compile(String js, Flags flags) {
    CompilerOptions options = new CompilerOptions();
    applyDefaultOptions(options);
    applyOptionsFromFlags(options, flags);
    disableUnsupportedOptions(options);
    if (builtInExterns == null) {
      builtInExterns = loadBuiltInExterns();
    }
    SourceFile src = SourceFile.fromCode("src.js", js);
    compiler.compile(builtInExterns, ImmutableList.of(src), options);
    return compiler.toSource();
  }

  @Override
  public void onModuleLoad() {}
}
