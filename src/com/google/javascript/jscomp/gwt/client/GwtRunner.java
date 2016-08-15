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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;

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
import com.google.javascript.jscomp.SourceMapInput;
import com.google.javascript.jscomp.WarningLevel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

/**
 * Runner for the GWT-compiled JSCompiler as a single exported method.
 */
public final class GwtRunner implements EntryPoint {
  private static final CompilationLevel DEFAULT_COMPILATION_LEVEL =
      CompilationLevel.SIMPLE_OPTIMIZATIONS;

  private GwtRunner() {}

  /**
   * Specifies flags and their defaults.
   *
   * You must specify defaults in the constructor (as of August 2016). Defaults specified
   * alongside fields will cause falsey values to be optimized and inlined. Fix here-
   *    https://gwt-review.googlesource.com/#/c/16600/
   */
  @JsType(namespace = JsPackage.GLOBAL, name = "Flags")
  public static class Flags {
    public boolean angularPass;
    public boolean assumeFunctionWrapper;
    public String compilationLevel;
    public boolean dartPass;
    public boolean exportLocalPropertyDefinitions;
    public boolean generateExports;
    public String languageIn;
    public String languageOut;
    public boolean checksOnly;
    public boolean newTypeInf;
    public boolean polymerPass;
    public boolean preserveTypeAnnotations;
    public boolean processCommonJsModules;
    public String renamePrefixNamespace;
    public boolean rewritePolyfills;
    public String warningLevel;
    public boolean useTypesForOptimization;

    // These flags do not match the Java jar release.
    public File[] jsCode;
    public File[] externs;
    public boolean createSourceMap;

    /**
     * Flags constructor. Defaults must be specified here for every field.
     */
    public Flags() {
      this.angularPass = false;
      this.assumeFunctionWrapper = false;
      this.compilationLevel = "SIMPLE";
      this.dartPass = false;
      this.exportLocalPropertyDefinitions = false;
      this.generateExports = false;
      this.languageIn = "ES6";
      this.languageOut = "ES5";
      this.checksOnly = false;
      this.newTypeInf = false;
      this.polymerPass = false;
      this.preserveTypeAnnotations = false;
      this.processCommonJsModules = false;
      this.renamePrefixNamespace = null;
      this.rewritePolyfills = true;
      this.warningLevel = "DEFAULT";
      this.useTypesForOptimization = true;
      this.jsCode = null;
      this.externs = null;
      this.createSourceMap = false;
    }

    /**
     * Updates this {@link Flags} with a raw {@link JavaScriptObject}.
     *
     * @param raw The raw flags passed to this program.
     * @return A list of invalid/unhandled flags.
     */
    public native String[] update(JavaScriptObject raw) /*-{
      var unhandled = [];
      for (var k in raw) {
        if (k in this) {
          this[k] = raw[k];
        } else {
          unhandled.push(k);
        }
      }
      return unhandled;
    }-*/;
  }

  /**
   * File object matching {@code AbstractCommandLineRunner.JsonFileSpec}. This is marked as the
   * native {@code Object} type as it's not instantiated anywhere.
   */
  @JsType(namespace = JsPackage.GLOBAL, name = "Object", isNative = true)
  public static class File {
    @JsProperty String path;
    @JsProperty String src;
    @JsProperty String sourceMap;
  }

  /**
   * Output type returned to caller.
   */
  @JsType(namespace = JsPackage.GLOBAL, name = "ModuleOutput")
  public static class ModuleOutput {
    @JsProperty String compiledCode;
    @JsProperty String sourceMap;
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
    options.setLanguageIn(LanguageMode.ECMASCRIPT6);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
  }

  private static void applyOptionsFromFlags(CompilerOptions options, Flags flags) {
    CompilationLevel level = DEFAULT_COMPILATION_LEVEL;
    if (flags.compilationLevel != null) {
      level = CompilationLevel.fromString(flags.compilationLevel.toUpperCase());
      if (level == null) {
        throw new RuntimeException(
            "Bad value for compilationLevel: " + flags.compilationLevel);
      }
    }
    level.setOptionsForCompilationLevel(options);
    if (flags.assumeFunctionWrapper) {
      level.setWrappedOutputOptimizations(options);
    }
    if (flags.useTypesForOptimization) {
      level.setTypeBasedOptimizationOptions(options);
    }

    WarningLevel warningLevel = WarningLevel.DEFAULT;
    if (flags.warningLevel != null) {
      warningLevel = WarningLevel.valueOf(flags.warningLevel);
    }
    warningLevel.setOptionsForWarningLevel(options);

    LanguageMode languageIn = LanguageMode.fromString(flags.languageIn);
    if (languageIn == null) {
      throw new RuntimeException("Bad value for languageIn: " + flags.languageIn);
    }
    options.setLanguageIn(languageIn);

    LanguageMode languageOut = LanguageMode.fromString(flags.languageOut);
    if (languageOut == null) {
      throw new RuntimeException("Bad value for languageOut: " + flags.languageOut);
    }
    options.setLanguageOut(languageOut);

    if (flags.createSourceMap) {
      options.setSourceMapOutputPath("%output%");
    }

    options.setAngularPass(flags.angularPass);
    options.setChecksOnly(flags.checksOnly);
    options.setDartPass(flags.dartPass);
    options.setExportLocalPropertyDefinitions(flags.exportLocalPropertyDefinitions);
    options.setGenerateExports(flags.generateExports);
    options.setNewTypeInference(flags.newTypeInf);
    options.setPolymerPass(flags.polymerPass);
    options.setPreserveTypeAnnotations(flags.preserveTypeAnnotations);
    options.setProcessCommonJSModules(flags.processCommonJsModules);
    options.setRenamePrefixNamespace(flags.renamePrefixNamespace);
    options.setRewritePolyfills(flags.rewritePolyfills);
  }

  private static void disableUnsupportedOptions(CompilerOptions options) {
    options.getDependencyOptions().setDependencySorting(false);
  }

  private static List<SourceFile> fromFileArray(File[] src, String unknownPrefix) {
    List<SourceFile> out = new ArrayList<>();
    if (src != null) {
      for (int i = 0; i < src.length; ++i) {
        File file = src[i];
        String path = file.path;
        if (path == null) {
          path = unknownPrefix + i;
        }
        out.add(SourceFile.fromCode(path, nullToEmpty(file.src)));
      }
    }
    return ImmutableList.copyOf(out);
  }

  private static ImmutableMap<String, SourceMapInput> buildSourceMaps(
      File[] src, String unknownPrefix) {
    ImmutableMap.Builder<String, SourceMapInput> inputSourceMaps = new ImmutableMap.Builder<>();
    if (src != null) {
      for (int i = 0; i < src.length; ++i) {
        File file = src[i];
        if (isNullOrEmpty(file.sourceMap)) {
          continue;
        }
        String path = file.path;
        if (path == null) {
          path = unknownPrefix + i;
        }
        path += ".map";
        SourceFile sf = SourceFile.fromCode(path, file.sourceMap);
        inputSourceMaps.put(path, new SourceMapInput(sf));
      }
    }
    return inputSourceMaps.build();
  }

  /**
   * Public compiler call. Exposed in {@link #exportCompile}.
   *
   * @param raw The passed raw flags from the user.
   * @return The output from the compile.
   */
  public static ModuleOutput compile(JavaScriptObject raw) {
    Flags flags = new Flags();
    String[] unhandled = flags.update(raw);
    if (unhandled.length > 0) {
      throw new RuntimeException("Unhandled flag: " + unhandled[0]);
    }

    List<SourceFile> externs = fromFileArray(flags.externs, "Extern_");
    List<SourceFile> jsCode = fromFileArray(flags.jsCode, "Input_");
    ImmutableMap<String, SourceMapInput> sourceMaps = buildSourceMaps(flags.jsCode, "Input_");

    CompilerOptions options = new CompilerOptions();
    applyDefaultOptions(options);
    applyOptionsFromFlags(options, flags);
    options.setInputSourceMaps(sourceMaps);
    disableUnsupportedOptions(options);

    NodeErrorManager errorManager = new NodeErrorManager();
    Compiler compiler = new Compiler();
    compiler.setErrorManager(errorManager);
    compiler.compile(externs, jsCode, options);

    ModuleOutput output = new ModuleOutput();
    output.compiledCode = compiler.toSource();
    output.errors = toNativeErrorArray(errorManager.errors);
    output.warnings = toNativeErrorArray(errorManager.warnings);

    if (flags.createSourceMap) {
      StringBuilder b = new StringBuilder();
      try {
        compiler.getSourceMap().appendTo(b, "IGNORED");
      } catch (IOException e) {
        // ignore
      }
      output.sourceMap = b.toString();
    }

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
