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
import com.google.gwt.core.client.JavaScriptObject;
import com.google.javascript.jscomp.BasicErrorManager;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.WarningLevel;

import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runner for the GWT-compiled JSCompiler as a single exported method.
 */
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

  private GwtRunner() {}

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

    @JsProperty
    File[] getJsCode();

    @JsProperty
    File[] getExterns();
  }

  @JsType(namespace = JsPackage.GLOBAL, name = "Object", isNative = true)
  private interface File {
    @JsProperty String getName();
    @JsProperty String getSource();
  }

  @JsType(namespace = JsPackage.GLOBAL, name = "Object", isNative = true)
  private static class ModuleOutput {
    @JsProperty String compiledCode;
    @JsProperty JavaScriptObject[] errors;
    @JsProperty JavaScriptObject[] warnings;
  }

  private static native JavaScriptObject createError(String file, String description, String type,
        int lineNo, int charNo) /*-{
    return {file: file, description: description, type: type, lineNo: lineNo, charNo: charNo};
  }-*/;

  /**
   * Convert a list of {@link JSError} instances to a JS array containing plain objects.
   */
  private static JavaScriptObject[] toNativeErrorArray(List<JSError> errors) {
    JavaScriptObject out[] = new JavaScriptObject[errors.size()];
    for (int i = 0; i < errors.size(); ++i) {
      JSError error = errors.get(i);
      DiagnosticType type = error.getType();
      out[i] = createError(error.sourceName, error.description, type != null ? type.key : null,
          error.lineNumber, error.getCharno());
    }
    return out;
  }

  private static void applyDefaultOptions(CompilerOptions options) {
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    WarningLevel.DEFAULT.setOptionsForWarningLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setPrettyPrint(true);
  }

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

  private static List<SourceFile> fromFileArray(File[] src, String unknownPrefix) {
    List<SourceFile> out = new ArrayList<>();
    if (src != null) {
      for (int i = 0; i < src.length; ++i) {
        File file = src[i];
        String name = file.getName();
        if (name == null) {
          name = unknownPrefix + i;
        }
        String source = file.getSource();
        if (source == null) {
          source = "";
        }
        out.add(SourceFile.fromCode(name, source));
      }
    }
    return ImmutableList.copyOf(out);
  }

  /**
   * Public compiler call. Exposed in {@link #exportCompile}.
   */
  public static ModuleOutput compile(Flags flags) {
    CompilerOptions options = new CompilerOptions();
    applyDefaultOptions(options);
    applyOptionsFromFlags(options, flags);
    disableUnsupportedOptions(options);

    NodeErrorManager errorManager = new NodeErrorManager();
    Compiler compiler = new Compiler();
    compiler.setErrorManager(errorManager);

    List<SourceFile> externs = fromFileArray(flags.getExterns(), "Extern_");
    List<SourceFile> jsCode = fromFileArray(flags.getJsCode(), "Input_");
    compiler.compile(externs, jsCode, options);

    ModuleOutput output = new ModuleOutput();
    output.compiledCode = compiler.toSource();
    output.errors = toNativeErrorArray(errorManager.errors);
    output.warnings = toNativeErrorArray(errorManager.warnings);
    return output;
  }

  /**
   * Exports the {@link #compile} method via JSNI.
   *
   * This will be placed on {@code module.exports} or {@code this}.
   */
  public native void exportCompile() /*-{
    var fn = $entry(@com.google.javascript.jscomp.gwt.client.GwtRunner::compile(*));
    if (typeof module !== 'undefined' && module.exports) {
      module.exports = fn;
    } else {
      this.compile = fn;
    }
  }-*/;

  @Override
  public void onModuleLoad() {
    exportCompile();
  }

  /**
   * Custom {@link BasicErrorManager} to record {@link JSError} instances.
   */
  private static class NodeErrorManager extends BasicErrorManager {
    final List<JSError> errors = new ArrayList<>();
    final List<JSError> warnings = new ArrayList<>();

    @Override
    public void println(CheckLevel level, JSError error) {
      if (level == CheckLevel.ERROR) {
        errors.add(error);
      } else if (level == CheckLevel.WARNING) {
        warnings.add(error);
      }
    }

    @Override
    public void printSummary() {}
  }
}
