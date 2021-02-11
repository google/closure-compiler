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

package com.google.javascript.jscomp.bundle;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.ErrorFormat;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.MessageFormatter;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A source transformer base class. May also include a runtime that needs to
 * be shipped with the final bundle.
 */
@GwtIncompatible
@Immutable
public abstract class CompilerBasedTransformer implements Source.Transformer {

  private final CompilerSupplier compilerSupplier;

  public CompilerBasedTransformer(CompilerSupplier compilerSupplier) {
    this.compilerSupplier = checkNotNull(compilerSupplier);
  }

  public abstract Optional<String> getRuntime();
  public abstract String getTranformationName();

  @Override
  public Source transform(Source input) {
    CompileResult result = compilerSupplier.compile(input.path(), input.code());
    if (!result.errors.isEmpty()) {
      // TODO(sdh): how to handle this?  Currently we throw an ISE with the message,
      // but this may not be the most appropriate option.  It might make sense to
      // add console.log() statements to any JS that comes out, particularly for
      // warnings.
      MessageFormatter formatter = ErrorFormat.SOURCELESS.toFormatter(null, false);
      StringBuilder message =
          new StringBuilder().append(getTranformationName()).append(" failed.\n");
      for (JSError error : result.errors) {
        message.append(formatter.formatError(error));
      }
      throw new IllegalStateException(message.toString());
    }
    if (!result.transformed) {
      return input;
    }
    Source.Builder builder = input.toBuilder()
        .setCode(result.source)
        .setSourceMap(result.sourceMap);
    if (getRuntime().isPresent()) {
        builder.addRuntime(compilerSupplier.runtime(getRuntime().get()));
    }
    return builder.build();
  }

  /**
   * Wraps the Compiler into a more relevant interface, making it easy to test the
   * CompilerBasedTransformer without depending on implementation details of the Compiler itself.
   * Also works around the fact that the Compiler is not thread-safe (since we may do multiple
   * transpiles concurrently), so we supply a fresh instance each time when we're in single-file
   * mode.
   */
  @Immutable
  public static class CompilerSupplier {

    public CompileResult compile(Path path, String code) {
      Compiler compiler = compiler();
      Result result =
          compiler.compile(EXTERNS, SourceFile.fromCode(path.toString(), code), options());
      String source = compiler.toSource();
      StringBuilder sourceMap = new StringBuilder();
      if (result.sourceMap != null) {
        try {
          result.sourceMap.appendTo(sourceMap, path.toString());
        } catch (IOException e) {
          // impossible, and not a big deal even if it did happen.
        }
      }
      boolean transformed = transformed(result);
      return new CompileResult(
          source, result.errors, transformed, transformed ? sourceMap.toString() : "");
    }

    public boolean transformed(Result result) {
      return !result.transpiledFiles.isEmpty();
    }

    public String runtime(String library) {
      Compiler compiler = compiler();
      CompilerOptions options = options();
      options.setForceLibraryInjection(ImmutableList.of(library));
      compiler.compile(EXTERNS, EMPTY, options);
      return compiler.toSource();
    }

    protected Compiler compiler() {
      return new Compiler();
    }

    protected CompilerOptions options() {
      CompilerOptions options = new CompilerOptions();
      setOptions(options);
      return options;
    }

    protected void setOptions(CompilerOptions options) {
        // TODO(sdh): It would be nice to allow people to output code in
        // strict mode.  But currently we swallow all the input language
        // strictness checks, and there are various tests that are never
        // compiled and so are broken when we output 'use strict'.  We
        // could consider adding some sort of logging/warning/error in
        // cases where the input was not strict, though there could still
        // be semantic differences even if syntax is strict.  Possibly
        // the first step would be to allow the option of outputting strict
        // and then change the default and see what breaks.  b/33005948
        options.setLanguageOut(LanguageMode.ECMASCRIPT5);
        options.setQuoteKeywordProperties(true);
        options.setSkipNonTranspilationPasses(true);
        options.setVariableRenaming(VariableRenamingPolicy.OFF);
        options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
        options.setWrapGoogModulesForWhitespaceOnly(false);
        options.setPrettyPrint(true);
        options.setSourceMapOutputPath("/dev/null");
        options.setSourceMapIncludeSourcesContent(true);
        options.setWarningLevel(ES5_WARNINGS, CheckLevel.OFF);
    }

    protected static final SourceFile EXTERNS =
        SourceFile.fromCode("externs.js", "function Symbol() {}");
    protected static final SourceFile EMPTY = SourceFile.fromCode("empty.js", "");
    protected static final DiagnosticGroup ES5_WARNINGS =
        new DiagnosticGroup(DiagnosticType.error("JSC_CANNOT_CONVERT", ""));
  }

  /** The source together with the additional compilation results. */
  public static class CompileResult {
    public final String source;
    public final ImmutableList<JSError> errors;
    public final boolean transformed;
    public final String sourceMap;

    public CompileResult(
        String source, ImmutableList<JSError> errors, boolean transformed, String sourceMap) {
      this.source = checkNotNull(source);
      this.errors = checkNotNull(errors);
      this.transformed = transformed;
      this.sourceMap = checkNotNull(sourceMap);
    }
  }
}
