/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.base.Strings;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.javascript.jscomp.transpile.BaseTranspiler;
import com.google.javascript.jscomp.transpile.TranspileResult;
import com.google.javascript.jscomp.transpile.Transpiler;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// TODO(user): Convert this class to a builder/autovalue.
/** A utility class to assist in creating JS bundle files. */
public final class ClosureBundler {

  private final Transpiler transpiler;
  private final Transpiler es6ModuleTranspiler;

  private final EvalMode mode;
  private final String sourceUrl;
  private final String path;
  private final boolean embedSourcemap;

  // TODO(sdh): This cache should be moved out into a higher level, but is
  // currently required due to the API that source maps must be accessible
  // via just a path (and not the file contents).
  private final Map<String, String> sourceMapCache;
  private final Object minifier;

  public ClosureBundler() {
    this(Transpiler.NULL);
  }

  public ClosureBundler(Transpiler transpiler) {
    this(transpiler, BaseTranspiler.LATEST_TRANSPILER);
  }

  public ClosureBundler(Transpiler transpiler, Transpiler es6ModuleTranspiler) {
    this(
        transpiler,
        es6ModuleTranspiler,
        EvalMode.NORMAL,
        /* sourceUrl= */ null,
        /* path= */ "unknown_source",
        null,
        new ConcurrentHashMap<>(),
        /* embedSourcemap= */ false);
  }

  private ClosureBundler(
      Transpiler transpiler,
      Transpiler es6ModuleTranspiler,
      EvalMode mode,
      String sourceUrl,
      String path,
      Object minifier,
      Map<String, String> sourceMapCache,
      boolean embedSourcemap) {
    this.transpiler = transpiler;
    this.mode = mode;
    this.sourceUrl = sourceUrl;
    this.path = path;
    this.sourceMapCache = sourceMapCache;
    this.es6ModuleTranspiler = es6ModuleTranspiler;
    this.minifier = minifier;
    this.embedSourcemap = embedSourcemap;
  }

  public ClosureBundler withTranspilers(
      Transpiler newTranspiler, Transpiler newEs6ModuleTranspiler) {
    return new ClosureBundler(
        newTranspiler,
        newEs6ModuleTranspiler,
        mode,
        sourceUrl,
        path,
        minifier,
        sourceMapCache,
        embedSourcemap);
  }

  public ClosureBundler withTranspiler(Transpiler newTranspiler) {
    return withTranspilers(newTranspiler, es6ModuleTranspiler);
  }

  public ClosureBundler withEs6ModuleTranspiler(Transpiler newEs6ModuleTranspiler) {
    return withTranspilers(transpiler, newEs6ModuleTranspiler);
  }

  public ClosureBundler disableJ2clMinifier() {
    return new ClosureBundler(
        transpiler,
        es6ModuleTranspiler,
        mode,
        sourceUrl,
        path,
        /* minifier= */ null,
        sourceMapCache,
        embedSourcemap);
  }

  public final ClosureBundler useEval(boolean useEval) {
    EvalMode newMode = useEval ? EvalMode.EVAL : EvalMode.NORMAL;
    return new ClosureBundler(
        transpiler,
        es6ModuleTranspiler,
        newMode,
        sourceUrl,
        path,
        minifier,
        sourceMapCache,
        embedSourcemap);
  }

  public final ClosureBundler withSourceUrl(String newSourceUrl) {
    return new ClosureBundler(
        transpiler,
        es6ModuleTranspiler,
        mode,
        newSourceUrl,
        path,
        minifier,
        sourceMapCache,
        embedSourcemap);
  }

  public final ClosureBundler withPath(String newPath) {
    return new ClosureBundler(
        transpiler,
        es6ModuleTranspiler,
        mode,
        sourceUrl,
        newPath,
        minifier,
        sourceMapCache,
        embedSourcemap);
  }

  public final ClosureBundler embedSourcemap() {
    return new ClosureBundler(
        transpiler,
        es6ModuleTranspiler,
        mode,
        sourceUrl,
        path,
        minifier,
        sourceMapCache,
        /* embedSourcemap= */ true);
  }

  /** Append the contents of the string to the supplied appendable. */
  public static void appendInput(Appendable out, DependencyInfo info, String contents)
      throws IOException {
    new ClosureBundler().appendTo(out, info, contents);
  }

  /** Append the contents of the string to the supplied appendable. */
  public void appendTo(Appendable out, DependencyInfo info, String content) throws IOException {
    appendTo(out, info, CharSource.wrap(content));
  }

  /** Append the contents of the file to the supplied appendable. */
  public void appendTo(Appendable out, DependencyInfo info, File content, Charset contentCharset)
      throws IOException {
    appendTo(out, info, Files.asCharSource(content, contentCharset));
  }

  /** Append the contents of the CharSource to the supplied appendable. */
  public void appendTo(Appendable out, DependencyInfo info, CharSource content) throws IOException {
    String code = content.read();
    if (info.isModule()) {
      mode.appendGoogModule(transpile(code), out, sourceUrl);
    } else if ("es6".equals(info.getLoadFlags().get("module")) && transpiler == Transpiler.NULL) {
      // TODO(johnplaisted): Make the default transpiler the ES_MODULE_TO_CJS_TRANSPILER. Currently
      // some code is passing in unicode identifiers in non-ES6 modules the compiler fails to parse.
      // Once this compiler bug is fixed we can always transpile.
      mode.appendTraditional(transpileEs6Module(code), out, sourceUrl);
    } else {
      mode.appendTraditional(transpile(code), out, sourceUrl);
    }
  }

  public void appendRuntimeTo(Appendable out) throws IOException {
    String runtime = transpiler.runtime();
    if (!runtime.isEmpty()) {
      mode.appendTraditional(runtime, out, null);
    }
    if (transpiler == Transpiler.NULL) {
      mode.appendTraditional(es6ModuleTranspiler.runtime(), out, null);
    }
    mode.appendTraditional("this.CLOSURE_EVAL_PREFILTER = function(s) { return s; };", out, null);
    mode.appendTraditional("(function(thisValue){", out, null);
    // Check for Chrome <87 which does not eval properly in workers.
    mode.appendTraditional(
        "var isChrome87 = false; try {isChrome87 =  eval(trustedTypes.emptyScript) !=="
            + " trustedTypes.emptyScript } catch (e) {} if (typeof trustedTypes !=="
            + " 'undefined' && trustedTypes.createPolicy &&isChrome87 ) {",
        out,
        null);
    mode.appendTraditional(
        "  var policy = trustedTypes.createPolicy('goog#devserver',{ createScript: function(s){"
            + " return s; }});",
        out,
        null);
    mode.appendTraditional(
        "  thisValue.CLOSURE_EVAL_PREFILTER = policy.createScript.bind(policy);", out, null);
    mode.appendTraditional("}", out, null);
    mode.appendTraditional("})(this);", out, null);
  }

  /**
   * Subclasses that need to provide a source map for any transformed input can return it with this
   * method.
   */
  public String getSourceMap(String path) {
    return Strings.nullToEmpty(sourceMapCache.get(path));
  }

  private String transpile(String s, Transpiler t) {
    TranspileResult result;
    try {
      result = t.transpile(new URI(path), s);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    sourceMapCache.put(path, result.sourceMap());
    return embedSourcemap ? result.embedSourcemapBase64().transpiled() : result.transpiled();
  }

  private String transpile(String s) {
    return transpile(s, transpiler);
  }

  private String transpileEs6Module(String s) {
    return transpile(s, es6ModuleTranspiler);
  }

  private enum EvalMode {
    EVAL {
      @Override
      void appendTraditional(String s, Appendable out, String sourceUrl) throws IOException {
        out.append("eval(this.CLOSURE_EVAL_PREFILTER(\"");
        EscapeMode.ESCAPED.append(s, out);
        appendSourceUrl(out, EscapeMode.ESCAPED, sourceUrl);
        out.append("\"));\n");
      }

      @Override
      void appendGoogModule(String s, Appendable out, String sourceUrl) throws IOException {
        out.append("goog.loadModule(\"");
        EscapeMode.ESCAPED.append(s, out);
        appendSourceUrl(out, EscapeMode.ESCAPED, sourceUrl);
        out.append("\");\n");
      }
    },
    NORMAL {
      @Override
      void appendTraditional(String s, Appendable out, String sourceUrl) throws IOException {
        EscapeMode.NORMAL.append(s, out);
        appendSourceUrl(out, EscapeMode.NORMAL, sourceUrl);
      }

      @Override
      void appendGoogModule(String s, Appendable out, String sourceUrl) throws IOException {
        // add the prefix on the first line so the line numbers aren't affected.
        out.append("goog.loadModule(function(exports) {" + "'use strict';");
        EscapeMode.NORMAL.append(s, out);
        out.append(
            "\n" // terminate any trailing single line comment.
                + ";" // terminate any trailing expression.
                + "return exports;});\n");
        appendSourceUrl(out, EscapeMode.NORMAL, sourceUrl);
      }
    };

    abstract void appendTraditional(String s, Appendable out, String sourceUrl) throws IOException;

    abstract void appendGoogModule(String s, Appendable out, String sourceUrl) throws IOException;
  }

  private enum EscapeMode {
    ESCAPED {
      @Override
      void append(String s, Appendable out) throws IOException {
        SourceCodeEscapers.appendWithJavascriptEscaper(s, out);
      }
    },
    NORMAL {
      @Override
      void append(String s, Appendable out) throws IOException {
        out.append(s);
      }
    };

    abstract void append(String s, Appendable out) throws IOException;
  }

  private static void appendSourceUrl(Appendable out, EscapeMode mode, String sourceUrl)
      throws IOException {
    if (sourceUrl == null) {
      return;
    }
    String toAppend = "\n//# sourceURL=" + sourceUrl + "\n";
    // Don't go through #append. That method relies on #transformInput,
    // but source URLs generally aren't valid JS inputs.
    mode.append(toAppend, out);
  }
}
