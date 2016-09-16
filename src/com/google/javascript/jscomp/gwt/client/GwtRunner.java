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

import com.google.common.collect.ImmutableMap;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.javascript.jscomp.BasicErrorManager;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DefaultExterns;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.ResourceLoader;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.SourceMap;
import com.google.javascript.jscomp.SourceMapInput;
import com.google.javascript.jscomp.WarningLevel;
import com.google.javascript.jscomp.deps.SourceCodeEscapers;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

/**
 * Runner for the GWT-compiled JSCompiler as a single exported method.
 */
public final class GwtRunner implements EntryPoint {
  private static final CompilationLevel DEFAULT_COMPILATION_LEVEL =
      CompilationLevel.SIMPLE_OPTIMIZATIONS;

  private static final String OUTPUT_MARKER = "%output%";
  private static final String OUTPUT_MARKER_JS_STRING = "%output|jsstring%";

  private static final String EXTERNS_PREFIX = "externs/";

  private GwtRunner() {}

  @JsType(namespace = JsPackage.GLOBAL, name = "Object", isNative = true)
  private static class Flags {
    boolean angularPass;
    boolean applyInputSourceMaps;
    boolean assumeFunctionWrapper;
    String compilationLevel;
    boolean dartPass;
    JsMap defines;
    String env;
    boolean exportLocalPropertyDefinitions;
    boolean generateExports;
    String languageIn;
    String languageOut;
    boolean checksOnly;
    boolean newTypeInf;
    String outputWrapper;
    boolean polymerPass;
    boolean preserveTypeAnnotations;
    boolean processCommonJsModules;
    public String renamePrefixNamespace;
    boolean rewritePolyfills;
    String warningLevel;
    boolean useTypesForOptimization;

    // These flags do not match the Java compiler JAR.
    File[] jsCode;
    File[] externs;
    boolean createSourceMap;
  }

  /**
   * defaultFlags must have a value set for each field. Otherwise, GWT has no way to create the
   * fields inside Flags (as it's native). If Flags is not-native, GWT eats its field names
   * anyway.
   */
  private static final Flags defaultFlags = new Flags();
  static {
    defaultFlags.angularPass = false;
    defaultFlags.applyInputSourceMaps = true;
    defaultFlags.assumeFunctionWrapper = false;
    defaultFlags.checksOnly = false;
    defaultFlags.compilationLevel = "SIMPLE";
    defaultFlags.dartPass = false;
    defaultFlags.defines = null;
    defaultFlags.env = "BROWSER";
    defaultFlags.exportLocalPropertyDefinitions = false;
    defaultFlags.generateExports = false;
    defaultFlags.languageIn = "ECMASCRIPT6";
    defaultFlags.languageOut = "ECMASCRIPT5";
    defaultFlags.newTypeInf = false;
    defaultFlags.outputWrapper = null;
    defaultFlags.polymerPass = false;
    defaultFlags.preserveTypeAnnotations = false;
    defaultFlags.processCommonJsModules = false;
    defaultFlags.renamePrefixNamespace = null;
    defaultFlags.rewritePolyfills = true;
    defaultFlags.warningLevel = "DEFAULT";
    defaultFlags.useTypesForOptimization = true;
    defaultFlags.jsCode = null;
    defaultFlags.externs = null;
    defaultFlags.createSourceMap = false;
  }

  @JsType(namespace = JsPackage.GLOBAL, name = "Object", isNative = true)
  private static class File {
    @JsProperty String path;
    @JsProperty String src;
    @JsProperty String sourceMap;
  }

  @JsType(namespace = JsPackage.GLOBAL, name = "Object", isNative = true)
  private static class ModuleOutput {
    @JsProperty String compiledCode;
    @JsProperty String sourceMap;
    @JsProperty JavaScriptObject[] errors;
    @JsProperty JavaScriptObject[] warnings;
  }

  /**
   * Wraps a generic JS object used as a map.
   */
  private static final class JsMap extends JavaScriptObject {
    protected JsMap() {}

    /**
     * @return This {@code JsMap} as a {@link Map}.
     */
    Map<String, Object> asMap() {
      ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
      for (String key : keys(this)) {
        builder.put(key, get(key));
      }
      return builder.build();
    }

    /**
     * Validates that the values of this {@code JsMap} are primitives: either number, string or
     * boolean. Note that {@code typeof null} is object.
     */
    private native void validatePrimitiveTypes() /*-{
      var valid = {'number': '', 'string': '', 'boolean': ''};
      Object.keys(this).forEach(function(key) {
        var type = typeof this[key];
        if (!(type in valid)) {
          throw new TypeError('Type of define `' + key + '` unsupported: ' + type);
        }
      }, this);
    }-*/;

    private native Object get(String key) /*-{
      return this[key];
    }-*/;
  }

  @JsMethod(name = "keys", namespace = "Object")
  private static native String[] keys(Object o);

  private static native JavaScriptObject createError(String file, String description, String type,
        int lineNo, int charNo) /*-{
    return {file: file, description: description, type: type, lineNo: lineNo, charNo: charNo};
  }-*/;

  /**
   * Convert a list of {@link JSError} instances to a JS array containing plain objects.
   */
  private static JavaScriptObject[] toNativeErrorArray(List<JSError> errors) {
    JavaScriptObject[] out = new JavaScriptObject[errors.size()];
    for (int i = 0; i < errors.size(); ++i) {
      JSError error = errors.get(i);
      DiagnosticType type = error.getType();
      out[i] = createError(error.sourceName, error.description, type != null ? type.key : null,
          error.lineNumber, error.getCharno());
    }
    return out;
  }

  /**
   * Generates the output code, taking into account the passed {@code outputWrapper}.
   */
  private static String writeOutput(Compiler compiler, String outputWrapper) {
    String code = compiler.toSource();
    if (outputWrapper == null) {
      return code;
    }

    String marker;
    int pos = outputWrapper.indexOf(OUTPUT_MARKER_JS_STRING);
    if (pos != -1) {
      // With jsstring, run SourceCodeEscapers (as per AbstractCommandLineRunner).
      code = SourceCodeEscapers.javascriptEscaper().escape(code);
      marker = OUTPUT_MARKER_JS_STRING;
    } else {
      pos = outputWrapper.indexOf(OUTPUT_MARKER);
      if (pos == -1) {
        return code;  // neither marker could be found, just return code
      }
      marker = OUTPUT_MARKER;
    }

    String prefix = outputWrapper.substring(0, pos);
    SourceMap sourceMap = compiler.getSourceMap();
    if (sourceMap != null) {
      sourceMap.setWrapperPrefix(prefix);
    }
    return prefix + code + outputWrapper.substring(pos + marker.length());
  }

  private static List<SourceFile> createExterns(CompilerOptions.Environment environment) {
    String[] resources = ResourceLoader.resourceList(GwtRunner.class);
    Map<String, SourceFile> all = new HashMap<>();
    for (String res : resources) {
      if (res.startsWith(EXTERNS_PREFIX)) {
        String filename = res.substring(EXTERNS_PREFIX.length());
        all.put(filename, SourceFile.fromCode("externs.zip//" + res,
              ResourceLoader.loadTextResource(GwtRunner.class, res)));
      }
    }
    return DefaultExterns.prepareExterns(environment, all);
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

    CompilerOptions.Environment environment = CompilerOptions.Environment.BROWSER;
    if (flags.env != null) {
      environment = CompilerOptions.Environment.valueOf(flags.env.toUpperCase());
    }
    options.setEnvironment(environment);

    LanguageMode languageIn = LanguageMode.fromString(flags.languageIn);
    if (languageIn != null) {
      options.setLanguageIn(languageIn);
    }
    LanguageMode languageOut = LanguageMode.fromString(flags.languageOut);
    if (languageOut != null) {
      options.setLanguageOut(languageOut);
    }

    if (flags.createSourceMap) {
      options.setSourceMapOutputPath("%output%");
    }

    if (flags.defines != null) {
      // CompilerOptions also validates types, but uses Preconditions and therefore won't generate
      // a useful exception.
      flags.defines.validatePrimitiveTypes();
      options.setDefineReplacements(flags.defines.asMap());
    }

    options.setAngularPass(flags.angularPass);
    options.setApplyInputSourceMaps(flags.applyInputSourceMaps);
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
    return out;
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
   * Updates the destination flags (user input) with source flags (the defaults). Returns a list
   * of flags that are on the destination, but not on the source.
   */
  private static native String[] updateFlags(Flags dst, Flags src) /*-{
    for (var k in src) {
      if (!(k in dst)) {
        dst[k] = src[k];
      }
    }
    var unhandled = [];
    for (var k in dst) {
      if (!(k in src)) {
        unhandled.push(k);
      }
    }
    return unhandled;
  }-*/;

  /**
   * Public compiler call. Exposed in {@link #exportCompile}.
   */
  public static ModuleOutput compile(Flags flags) {
    String[] unhandled = updateFlags(flags, defaultFlags);
    if (unhandled.length > 0) {
      throw new RuntimeException("Unhandled flag: " + unhandled[0]);
    }

    List<SourceFile> jsCode = fromFileArray(flags.jsCode, "Input_");
    ImmutableMap<String, SourceMapInput> sourceMaps = buildSourceMaps(flags.jsCode, "Input_");

    CompilerOptions options = new CompilerOptions();
    applyDefaultOptions(options);
    applyOptionsFromFlags(options, flags);
    options.setInputSourceMaps(sourceMaps);
    disableUnsupportedOptions(options);

    List<SourceFile> externs = fromFileArray(flags.externs, "Extern_");
    externs.addAll(createExterns(options.getEnvironment()));

    NodeErrorManager errorManager = new NodeErrorManager();
    Compiler compiler = new Compiler(new NodePrintStream());
    compiler.setErrorManager(errorManager);
    compiler.compile(externs, jsCode, options);

    ModuleOutput output = new ModuleOutput();
    output.compiledCode = writeOutput(compiler, flags.outputWrapper);
    output.errors = toNativeErrorArray(errorManager.errors);
    output.warnings = toNativeErrorArray(errorManager.warnings);

    if (flags.createSourceMap) {
      StringBuilder b = new StringBuilder();
      try {
        compiler.getSourceMap().appendTo(b, "");
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
   * This will be placed on {@code module.exports}, {@code self.compile} or {@code window.compile}.
   */
  public native void exportCompile() /*-{
    var fn = $entry(@com.google.javascript.jscomp.gwt.client.GwtRunner::compile(*));
    if (typeof module !== 'undefined' && module.exports) {
      module.exports = fn;
    } else if (typeof self === 'object') {
      self.compile = fn;
    } else {
      window.compile = fn;
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

  // TODO(johnlenz): remove this once GWT has a proper PrintStream implementation
  private static class NodePrintStream extends PrintStream {
    private String line = "";

    NodePrintStream() {
      super((OutputStream) null);
    }

    @Override
    public void println(String s) {
      print(s + "\n");
    }

    @Override
    public void print(String s) {
      if (useStdErr()) {
        writeToStdErr(s);
      } else {
        writeFinishedLinesToConsole(s);
      }
    }

    private void writeFinishedLinesToConsole(String s) {
      line = line + s;
      int start = 0;
      int end = 0;
      while ((end = line.indexOf('\n', start)) != -1) {
        writeToConsole(line.substring(start, end));
        start = end + 1;
      }
      line = line.substring(start);
    }

    private static native boolean useStdErr() /*-{
      return !!(typeof process != "undefined" && process.stderr);
    }-*/;

    private native boolean writeToStdErr(String s) /*-{
      process.stderr.write(s);
    }-*/;


    // NOTE: console methods always add a newline following the text.
    private native void writeToConsole(String s) /*-{
       console.log(s);
    }-*/;

    @Override
    public void close() {
    }

    @Override
    public void flush() {
    }

    @Override
    public void write(byte[] buffer, int offset, int length) {
    }

    @Override
    public void write(int oneByte) {
    }
  }
}
