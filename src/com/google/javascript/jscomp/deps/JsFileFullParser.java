/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultimap;
import com.google.common.collect.TreeMultiset;
import com.google.javascript.jscomp.BasicErrorManager;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.GatherModuleMetadata;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A parser that extracts dependency information from a .js file, including goog.require,
 * goog.provide, goog.module, and import/export statements.
 */
public class JsFileFullParser {
  /** The dependency information contained in a .js source file. */
  public static final class FileInfo {
    /** The module system declared by the file, e.g. goog.provide/goog.module. */
    public enum ModuleType {
      UNKNOWN,
      GOOG_PROVIDE,
      GOOG_MODULE,
      ES_MODULE,
    }

    public boolean goog = false;
    public boolean testonly = false;
    public ModuleType moduleType = ModuleType.UNKNOWN;
    public boolean isLegacyNamespace = false;

    // Use a LinkedHashSet as import order matters!
    public final Set<String> importedModules = new LinkedHashSet<>();
    public final Set<String> dynamicRequires = new LinkedHashSet<>();
    public final Set<String> readToggles = new LinkedHashSet<>();

    // Note: multiple copies doesn't make much sense, but we report
    // each copy so that calling code can choose how to handle it
    public final Multiset<String> provides = TreeMultiset.create();
    public final Multiset<String> requires = TreeMultiset.create();
    public final Multiset<String> typeRequires = TreeMultiset.create();
    public final Multiset<String> maybeRequires = TreeMultiset.create();

    public final Multimap<String, String> loadFlags = TreeMultimap.create();
    public ParserRunner.ParseResult parsed = null;
  }

  private JsFileFullParser() {
    // no instantiation
  }

  /**
   * Parses a JavaScript file for dependencies and annotations
   *
   * @return empty info if a syntax error was encountered
   */
  public static FileInfo parse(String code, String filename, Reporter reporter) {
    DelegatingReporter errorReporter = new DelegatingReporter(reporter);
    Compiler compiler =
        new Compiler(
            new BasicErrorManager() {
              @Override
              public void println(CheckLevel level, JSError error) {
                if (level == CheckLevel.ERROR) {
                  errorReporter.error(
                      error.description(),
                      error.sourceName(),
                      error.getLineNumber(),
                      error.charno());
                } else if (level == CheckLevel.WARNING) {
                  errorReporter.warning(
                      error.description(),
                      error.sourceName(),
                      error.getLineNumber(),
                      error.charno());
                }
              }

              @Override
              protected void printSummary() {}
            });
    SourceFile source = SourceFile.fromCode(filename, code);

    CompilerOptions options = new CompilerOptions();
    options.setChecksOnly(true);

    compiler.init(ImmutableList.of(), ImmutableList.of(source), options);

    Config config =
        ParserRunner.createConfig(
            // TODO(sdh): ES8 STRICT, with a non-strict fallback - then give warnings.
            Config.LanguageMode.ES_NEXT,
            Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE,
            Config.RunMode.STOP_AFTER_ERROR,
            /* extraAnnotationNames= */ ImmutableSet.<String>of(),
            /* parseInlineSourceMaps= */ true,
            Config.StrictMode.SLOPPY);
    FileInfo info = new FileInfo();
    ParserRunner.ParseResult parsed = ParserRunner.parse(source, code, config, errorReporter);
    if (errorReporter.seenError) {
      return info;
    }
    info.parsed = parsed;

    parsed.ast.setInputId(new InputId(filename));
    String version = parsed.features.version();
    if (!version.equals("es3")) {
      info.loadFlags.put("lang", version);
    }

    GatherModuleMetadata gatherModuleMetadata =
        new GatherModuleMetadata(
            compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER);
    gatherModuleMetadata.process(new Node(Token.ROOT), parsed.ast);
    compiler.generateReport();
    if (compiler.getModuleMetadataMap().getModulesByPath().size() != 1) {
      return info; // Avoid potential crashes due to assumptions of the code below being violated.
    }
    ModuleMetadata module =
        Iterables.getOnlyElement(compiler.getModuleMetadataMap().getModulesByPath().values());
    if (module.isEs6Module()) {
      info.loadFlags.put("module", "es6");
    } else if (module.isGoogModule()) {
      info.loadFlags.put("module", "goog");
    }
    switch (module.moduleType()) {
      case GOOG_PROVIDE -> info.moduleType = FileInfo.ModuleType.GOOG_PROVIDE;
      case GOOG_MODULE -> info.moduleType = FileInfo.ModuleType.GOOG_MODULE;
      case LEGACY_GOOG_MODULE -> {
        info.moduleType = FileInfo.ModuleType.GOOG_MODULE;
        info.isLegacyNamespace = true;
      }
      case ES6_MODULE -> info.moduleType = FileInfo.ModuleType.ES_MODULE;
      case COMMON_JS, SCRIPT ->
          // Treat these as unknown for now; we can extend the enum if we care about these.
          info.moduleType = FileInfo.ModuleType.UNKNOWN;
    }
    info.goog = module.usesClosure();
    recordModuleMetadata(info, module);
    return info;
  }

  private static void recordModuleMetadata(FileInfo info, ModuleMetadata module) {
    info.importedModules.addAll(module.es6ImportSpecifiers().elementSet());

    // If something doesn't have an external dependency on Closure, then it does not have any
    // externally required files or symbols to provide. This is needed for bundles that contain
    // base.js as well as other files. These bundles should look like they do not require or provide
    // anything at all.
    if (module.usesClosure()) {
      info.provides.addAll(module.googNamespaces());
      info.requires.addAll(module.stronglyRequiredGoogNamespaces());
      info.maybeRequires.addAll(module.maybeRequiredGoogNamespaces());
      info.dynamicRequires.addAll(module.dynamicallyRequiredGoogNamespaces().elementSet());
      info.readToggles.addAll(module.readToggles().elementSet());
      info.typeRequires.addAll(module.weaklyRequiredGoogNamespaces());
      info.testonly = module.isTestOnly();
    }
    // Traverse any nested modules (goog.loadModule calls).
    for (ModuleMetadata nested : module.nestedModules()) {
      recordModuleMetadata(info, nested);
    }
  }

  /** Interface for reporting errors. */
  public interface Reporter {
    void report(boolean fatal, String message, String sourceName, int line, int lineOffset);
  }

  private static final class DelegatingReporter implements ErrorReporter {
    final Reporter delegate;
    private boolean seenError = false;

    DelegatingReporter(Reporter delegate) {
      this.delegate = checkNotNull(delegate);
    }

    @Override
    public void warning(String message, String sourceName, int line, int lineOffset) {
      delegate.report(false, message, sourceName, line, lineOffset);
    }

    @Override
    public void error(String message, String sourceName, int line, int lineOffset) {
      this.seenError = true;
      delegate.report(true, message, sourceName, line, lineOffset);
    }
  }
}
