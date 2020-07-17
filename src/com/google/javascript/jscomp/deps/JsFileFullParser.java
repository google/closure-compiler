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

import com.google.common.base.Strings;
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
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;

/**
 * A parser that extracts dependency information from a .js file, including goog.require,
 * goog.provide, goog.module, import/export statements, and JSDoc annotations related to dependency
 * management.
 */
public class JsFileFullParser {
  /** The dependency information contained in a .js source file. */
  public static final class FileInfo {
    /** The module system declared by the file, e.g. goog.provide/goog.module. */
    public enum ModuleType {
      UNKNOWN,
      GOOG_PROVIDE,
      GOOG_MODULE,
    }

    public boolean goog = false;
    public boolean isConfig = false;
    public boolean isExterns = false;
    public boolean provideGoog = false;
    public boolean testonly = false;
    public ModuleType moduleType = ModuleType.UNKNOWN;

    public final Set<String> hasSoyDelcalls = new TreeSet<>();
    public final Set<String> hasSoyDeltemplates = new TreeSet<>();
    // Use a LinkedHashSet as import order matters!
    public final Set<String> importedModules = new LinkedHashSet<>();
    public final List<String> modName = new ArrayList<>();
    public final List<String> mods = new ArrayList<>();

    // Note: multiple copies doesn't make much sense, but we report
    // each copy so that calling code can choose how to handle it
    public final Multiset<String> provides = TreeMultiset.create();
    public final Multiset<String> requires = TreeMultiset.create();
    public final Multiset<String> typeRequires = TreeMultiset.create();
    public final Multiset<String> requiresCss = TreeMultiset.create();
    public final Multiset<String> visibility = TreeMultiset.create();

    public final Multimap<String, String> customAnnotations = TreeMultimap.create();
    public final Multimap<String, String> loadFlags = TreeMultimap.create();
  }

  /** Represents a single JSDoc annotation, with an optional argument. */
  private static class CommentAnnotation {

    /** Annotation name, e.g. "@fileoverview" or "@externs". */
    final String name;
    /**
     * Annotation value: either the bare identifier immediately after the
     * annotation, or else string in braces.
     */
    final String value;

    CommentAnnotation(String name, String value) {
      this.name = name;
      this.value = value;
    }

    /** Returns all the annotations in a given comment string. */
    static List<CommentAnnotation> parse(String comment) {
      // TODO(b/139159612): This is reinventing a large part of JSDocInfoParser.  We should
      // try to consolidate as much as possible.  This requires several steps:
      //   1. Make all the annotations we look for first-class in JSDocInfo
      //   2. Support custom annotations (may already be done?)
      //   3. Fix up existing code so that all these annotations are in @fileoverview
      //   4. Change this code to simply inspect the script's JSDocInfo instead of re-parsing
      List<CommentAnnotation> out = new ArrayList<>();
      Matcher matcher = ANNOTATION_RE.matcher(comment);
      while (matcher.find()) {
        String name = matcher.group(ANNOTATION_NAME_GROUP);
        String value = Strings.nullToEmpty(matcher.group(ANNOTATION_VALUE_GROUP));
        out.add(new CommentAnnotation(name, value));
      }
      return out;
    }

    // Regex for a JSDoc annotation with an `@name` and an optional brace-delimited `{value}`.
    // The `@` should not match the middle of a word.
    private static final Pattern ANNOTATION_RE =
        Pattern.compile(
            "(?:[^a-zA-Z0-9_$]|^)(@[a-zA-Z]+)(?:\\s*\\{\\s*([^}\\t\\n\\v\\f\\r ]+)\\s*\\})?");
    private static final int ANNOTATION_NAME_GROUP = 1;
    private static final int ANNOTATION_VALUE_GROUP = 2;
  }

  /** Parses a JavaScript file for dependencies and annotations. */
  public static FileInfo parse(String code, String filename, @Nullable Reporter reporter) {
    ErrorReporter errorReporter = new DelegatingReporter(reporter);
    Compiler compiler =
        new Compiler(
            new BasicErrorManager() {
              @Override
              public void println(CheckLevel level, JSError error) {
                if (level == CheckLevel.ERROR) {
                  errorReporter.error(
                      error.getDescription(),
                      error.getSourceName(),
                      error.getLineNumber(),
                      error.getCharno());
                } else if (level == CheckLevel.WARNING) {
                  errorReporter.warning(
                      error.getDescription(),
                      error.getSourceName(),
                      error.getLineNumber(),
                      error.getCharno());
                }
              }

              @Override
              protected void printSummary() {}
            });
    SourceFile source = SourceFile.fromCode(filename, code);
    compiler.init(
        ImmutableList.<SourceFile>of(),
        ImmutableList.<SourceFile>of(source),
        new CompilerOptions());

    Config config =
        ParserRunner.createConfig(
            // TODO(sdh): ES8 STRICT, with a non-strict fallback - then give warnings.
            Config.LanguageMode.ECMASCRIPT8,
            Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE,
            Config.RunMode.KEEP_GOING,
            /* extraAnnotationNames */ ImmutableSet.<String>of(),
            /* parseInlineSourceMaps */ true,
            Config.StrictMode.SLOPPY);
    FileInfo info = new FileInfo();
    ParserRunner.ParseResult parsed = ParserRunner.parse(source, code, config, errorReporter);
    parsed.ast.setInputId(new InputId(filename));
    String version = parsed.features.version();
    if (!version.equals("es3")) {
      info.loadFlags.put("lang", version);
    }

    for (Comment comment : parsed.comments) {
      if (comment.type == Comment.Type.JSDOC) {
        parseComment(comment, info);
      }
    }
    GatherModuleMetadata gatherModuleMetadata =
        new GatherModuleMetadata(
            compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER);
    gatherModuleMetadata.process(new Node(Token.ROOT), parsed.ast);
    compiler.generateReport();
    ModuleMetadata module =
        Iterables.getOnlyElement(
            compiler.getModuleMetadataMap().getModulesByPath().values());
    if (module.isEs6Module()) {
      info.loadFlags.put("module", "es6");
    } else if (module.isGoogModule()) {
      info.loadFlags.put("module", "goog");
    }
    switch (module.moduleType()) {
      case GOOG_PROVIDE:
        info.moduleType = FileInfo.ModuleType.GOOG_PROVIDE;
        break;
      case GOOG_MODULE:
      case LEGACY_GOOG_MODULE:
        info.moduleType = FileInfo.ModuleType.GOOG_MODULE;
        break;
      case ES6_MODULE:
      case COMMON_JS:
      case SCRIPT:
        // Treat these as unknown for now; we can extend the enum if we care about these.
        info.moduleType = FileInfo.ModuleType.UNKNOWN;
        break;
    }
    info.goog = module.usesClosure();
    // If something doesn't have an external dependency on Closure, then it does not have any
    // externally required files or symbols to provide. This is needed for bundles that contain
    // base.js as well as other files. These bundles should look like they do not require or provide
    // anything at all.
    if (module.usesClosure()) {
      info.provides.addAll(module.googNamespaces());
      info.requires.addAll(module.stronglyRequiredGoogNamespaces());
      info.typeRequires.addAll(module.weaklyRequiredGoogNamespaces());
      info.testonly = module.isTestOnly();
    }
    info.importedModules.addAll(module.es6ImportSpecifiers().elementSet());
    return info;
  }

  /** Mutates {@code info} with information from the given {@code comment}. */
  private static void parseComment(Comment comment, FileInfo info) {
    boolean fileOverview = comment.value.contains("@fileoverview");
    for (CommentAnnotation annotation : CommentAnnotation.parse(comment.value)) {
      switch (annotation.name) {
        case "@fileoverview":
        case "@author":
        case "@see":
        case "@link":
          break;
        case "@mods":
          if (!annotation.value.isEmpty()) {
            info.mods.add(annotation.value);
          }
          break;
        case "@visibility":
          if (!annotation.value.isEmpty()) {
            info.visibility.add(annotation.value);
          }
          break;
        case "@modName":
          if (!annotation.value.isEmpty()) {
            info.modName.add(annotation.value);
          }
          break;
        case "@config":
          info.isConfig = true;
          break;
        case "@provideGoog":
          info.provideGoog = true;
          break;
        case "@requirecss":
          if (!annotation.value.isEmpty()) {
            info.requiresCss.add(annotation.value);
          }
          break;
        case "@hassoydeltemplate":
          if (!annotation.value.isEmpty()) {
            info.hasSoyDeltemplates.add(annotation.value);
          }
          break;
        case "@hassoydelcall":
          if (!annotation.value.isEmpty()) {
            info.hasSoyDelcalls.add(annotation.value);
          }
          break;
        case "@externs":
          info.isExterns = true;
          break;
        case "@enhanceable":
        case "@pintomodule":
          info.customAnnotations.put(annotation.name.substring(1), annotation.value);
          break;
        case "@enhance":
          if (!annotation.value.isEmpty()) {
            info.customAnnotations.put(annotation.name.substring(1), annotation.value);
          }
          break;
        default:
          if (fileOverview) {
            info.customAnnotations.put(annotation.name.substring(1), annotation.value);
          }
      }
    }
  }

  /** Interface for reporting errors. */
  public interface Reporter {
    void report(boolean fatal, String message, String sourceName, int line, int lineOffset);
  }

  private static final class DelegatingReporter implements ErrorReporter {
    final Reporter delegate;

    DelegatingReporter(Reporter delegate) {
      this.delegate = delegate != null ? delegate : NULL_REPORTER;
    }

    @Override
    public void warning(String message, String sourceName, int line, int lineOffset) {
      delegate.report(false, message, sourceName, line, lineOffset);
    }

    @Override
    public void error(String message, String sourceName, int line, int lineOffset) {
      delegate.report(true, message, sourceName, line, lineOffset);
    }
  }

  private static final Reporter NULL_REPORTER = new Reporter() {
    @Override
    public void report(
        boolean fatal, String message, String sourceName, int line, int lineOffset) {}
  };
}
