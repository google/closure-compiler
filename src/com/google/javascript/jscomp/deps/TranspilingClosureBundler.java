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

package com.google.javascript.jscomp.deps;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.CharSource;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * {@link ClosureBundler} that transpiles its sources.
 */
@NotThreadSafe
public final class TranspilingClosureBundler extends ClosureBundler {
  private static final HashFunction HASH_FUNCTION = Hashing.goodFastHash(64);
  private static final int DEFAULT_CACHE_SIZE = 100;
  /**
   * Cache recent transpilations, keyed by the hash code of the input
   * to avoid storing the whole input.
   */
  @VisibleForTesting final Cache<Long, String> cachedTranspilations;

  // TODO(sdh): Not all transpilation requires the runtime, only inject if actually needed.
  private final String es6Runtime;
  private boolean needToBundleEs6Runtime = true;
  // Whether to inline source map info directly into the output, in a "// #sourceMappingUrl"
  // comment. This bloats the size of the transpiled output, but it allows the server to avoid
  // serving the source map separately.
  private final boolean inlineSourceMap;
  // Map of source paths to generated source map paths.
  private final Map<String, String> sourceMapCache = new ConcurrentHashMap<>();

  public TranspilingClosureBundler() {
    this(getEs6Runtime());
  }

  /**
   * Creates a new bundler that transpile the sources from ES6 to ES5.
   *
   * @param transpilationCache The cache to use to store already transpiled files
   */
  public TranspilingClosureBundler(
      Cache<Long, String> transpilationCache, boolean inlineSourceMap) {
    this(getEs6Runtime(), transpilationCache, inlineSourceMap);
  }

  @VisibleForTesting
  TranspilingClosureBundler(String es6Runtime) {
    this(
        es6Runtime,
        CacheBuilder.newBuilder().maximumSize(DEFAULT_CACHE_SIZE).<Long, String>build(),
        true);
  }

  @VisibleForTesting
  TranspilingClosureBundler(
      String es6Runtime, Cache<Long, String> transpilationCache, boolean inlineSourceMap) {
    this.es6Runtime = es6Runtime;
    this.cachedTranspilations = transpilationCache;
    this.inlineSourceMap = inlineSourceMap;
  }

  @Override
  public void appendTo(Appendable out, DependencyInfo info, CharSource content) throws IOException {
    if (needToBundleEs6Runtime) {
      // Piggyback on the first call to transformInput to include the ES6 runtime as well.
      super.appendTo(out, SimpleDependencyInfo.EMPTY, CharSource.wrap(es6Runtime));
      needToBundleEs6Runtime = false;
    }
    super.appendTo(out, info, content);
  }

  private static CompilerOptions getOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT6_STRICT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    // Quoting keyword properties is only needed in ES3, so basically only in IE8.
    // But we set it explicitly here because the way the test bundler works, it invokes
    // the compiler without giving information about the browser, so we have to quote
    // every time to be safe :-/
    options.setQuoteKeywordProperties(true);
    options.setSkipNonTranspilationPasses(true);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
    options.setWrapGoogModulesForWhitespaceOnly(false);
    options.setPrettyPrint(true);
    options.setSourceMapOutputPath("/dev/null");
    options.setSourceMapIncludeSourcesContent(true);
    return options;
  }

  @Override
  protected String transformInput(final String js, final String path) {
    try {
      // Don't use built-in hashCode to decrease the likelihood of a collision.
      long hashCode = HASH_FUNCTION.hashString(js, StandardCharsets.UTF_8).asLong();
      return cachedTranspilations.get(
          hashCode,
          new Callable<String>() {
            @Override
            public String call() throws IOException {
              // Neither the compiler nor the options is thread safe, so they can't be
              // saved as instance state.
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              Compiler compiler = new Compiler(new PrintStream(baos));
              SourceFile sourceFile = SourceFile.fromCode(path, js);
              Result result =
                  compiler.<SourceFile, SourceFile>compile(
                      ImmutableList.<SourceFile>of(), ImmutableList.of(sourceFile), getOptions());
              if (compiler.getErrorManager().getErrorCount() > 0) {
                String message;
                try {
                  message = baos.toString(StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException e) {
                  throw new RuntimeException(e);
                }
                throw new IllegalStateException(message);
              }
              if (!result.transpiledFiles.contains(sourceFile)) {
                return js;
              }
              StringBuilder source = new StringBuilder().append(compiler.toSource());
              StringBuilder sourceMap = new StringBuilder();
              compiler.getSourceMap().appendTo(sourceMap, path);
              sourceMapCache.put(path, sourceMap.toString());
              if (inlineSourceMap) {
                source
                    .append("\n//# sourceMappingURL=data:,")
                    .append(URLEncoder.encode(sourceMap.toString(), "UTF-8").replace("+", "%20"));
              }
              return source.append("\n").toString();
            }
          });
    } catch (ExecutionException | UncheckedExecutionException e) {
      // IllegalStateExceptions thrown from the callable above will end up here as
      // UncheckedExecutionExceptions, per the contract of Cache#get. Throw the underlying
      // IllegalStateException so that the compiler error message is at the top of the stack trace.
      if (e.getCause() instanceof IllegalStateException) {
        throw (IllegalStateException) e.getCause();
      } else {
        throw Throwables.propagate(e);
      }
    }
  }

  @Override
  public String getSourceMap(final String path) {
    return sourceMapCache.get(path);
  }

  /** Generates the runtime by requesting the "es6_runtime" library from the compiler. */
  private static String getEs6Runtime() {
    CompilerOptions options = getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3); // change .delete to ['delete']
    options.setForceLibraryInjection(ImmutableList.of("es6_runtime"));
    Compiler compiler = new Compiler();
    SourceFile sourceFile = SourceFile.fromCode("source", "");
    compiler.<SourceFile, SourceFile>compile(
        ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(sourceFile), options);
    return compiler.toSource();
  }
}
