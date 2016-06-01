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

package com.google.javascript.jscomp.gwt.client;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultiset;
import com.google.gwt.core.client.EntryPoint;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.Var;
import com.google.javascript.jscomp.gwt.client.Util.JsArray;
import com.google.javascript.jscomp.gwt.client.Util.JsObject;
import com.google.javascript.jscomp.gwt.client.Util.JsRegExp;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;

import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

/**
 * GWT module to parse files for dependency and
 * {@literal @}{@code fileoverview} annotation
 * information.
 */
public class JsfileParser implements EntryPoint {

  /**
   * All the information parsed out of a single file.
   * Exported as a JSON object:
   * <pre> {@code {
   *   "custom_annotations": {?Array<[string, string]>},  @.*
   *   "goog": {?bool},  whether 'goog' is implicitly required
   *   "has_soy_delcalls": {?Array<string>},  @fileoverview @hassoydelcall {.*}
   *   "has_soy_deltemplates": {?Array<string>},  @fileoverview @hassoydeltemplate {.*}
   *   "imported_modules": {?Array<string>},  import ... from .*
   *   "is_config": {?bool},  @fileoverview @config
   *   "is_externs": {?bool},  @fileoverview @externs
   *   "load_flags": {?Array<[string, string]>},
   *   "mod_name": {?Array<string>},  @fileoverview @modName .*, @modName {.*}
   *   "mods": {?Array<string>},  @fileoverview @mods {.*}
   *   "provide_goog": {?bool},  @fileoverview @provideGoog
   *   "provides": {?Array<string>},
   *   "requires": {?Array<string>},  note: look for goog.* for 'goog'
   *   "requires_css": {?Array<string>},  @fileoverview @requirecss {.*}
   *   "testonly": {?bool},  goog.setTestOnly
   *   "visibility: {?Array<string>},  @fileoverview @visibility {.*}
   * }}</pre>
   * Any trivial values are omitted.
   */
  static final class FileInfo {
    final ErrorReporter reporter;

    boolean goog = false;
    boolean isConfig = false;
    boolean isExterns = false;
    boolean provideGoog = false;
    boolean testonly = false;

    final Set<String> hasSoyDelcalls = new TreeSet<>();
    final Set<String> hasSoyDeltemplates = new TreeSet<>();
    final Set<String> importedModules = new TreeSet<>();
    final List<String> modName = new ArrayList<>();
    final List<String> mods = new ArrayList<>();

    // Note: multiple copies doesn't make much sense, but we report
    // each copy so that calling code can choose how to handle it
    final Multiset<String> provides = TreeMultiset.create();
    final Multiset<String> requires = TreeMultiset.create();
    final Multiset<String> requiresCss = TreeMultiset.create();
    final Multiset<String> visibility = TreeMultiset.create();

    final Set<JsArray<String>> customAnnotations = assoc();
    final Set<JsArray<String>> loadFlags = assoc();

    FileInfo(ErrorReporter reporter) {
      this.reporter = reporter;
    }

    private void handleGoog() {
      if (provideGoog) {
        provides.add("goog");
      } else if (goog) {
        requires.add("goog");
      }
    }

    /** Exports the file info as a JSON object. */
    JsObject<Object> full() {
      handleGoog();
      return new SparseObject()
          .set("custom_annotations", customAnnotations)
          .set("goog", goog)
          .set("has_soy_delcalls", hasSoyDelcalls)
          .set("has_soy_deltemplates", hasSoyDeltemplates)
          .set("imported_modules", importedModules)
          .set("is_config", isConfig)
          .set("is_externs", isExterns)
          .set("load_flags", loadFlags)
          .set("modName", modName)
          .set("mods", mods)
          .set("provide_goog", provideGoog)
          .set("provides", provides)
          .set("requires", requires)
          .set("requiresCss", requiresCss)
          .set("testonly", testonly)
          .set("visibility", visibility)
          .object;
    }
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
      // TODO(sdh): This is reinventing a large part of JSDocInfoParser.  We should
      // try to consolidate as much as possible.  This requires several steps:
      //   1. Make all the annotations we look for first-class in JSDocInfo
      //   2. Support custom annotations (may already be done?)
      //   3. Fix up existing code so that all these annotations are in @fileoverview
      //   4. Change this code to simply inspect the script's JSDocInfo instead of re-parsing
      JsRegExp re = new JsRegExp(
          ANNOTATION_RE,
          "g");
      JsRegExp.Match match;
      List<CommentAnnotation> out = new ArrayList<>();
      while ((match = re.exec(comment)) != null) {
        boolean modName = match.get(OTHER_ANNOTATION_GROUP) == null;
        String name = modName ? "@modName" : match.get(OTHER_ANNOTATION_GROUP);
        String value =
            Strings.nullToEmpty(match.get(modName ? MODNAME_VALUE_GROUP : OTHER_VALUE_GROUP));
        out.add(new CommentAnnotation(name, value));
      }
      return out;
    }

    private static final String ANNOTATION_RE =
        Joiner.on("").join(
            // Don't match "@" in the middle of a word
            "(?:[^a-zA-Z0-9_$]|^)",
            "(?:",
            // Case 1: @modName with a single identifier and no braces
            "@modName[\\t\\v\\f ]*([^{\\t\\n\\v\\f\\r ][^\\t\\n\\v\\f\\r ]*)",
            "|",
            // Case 2: Everything else, with an optional brace-delimited argument
            "(@[a-zA-Z]+)(?:\\s*\\{\\s*([^}\\t\\n\\v\\f\\r ]+)\\s*\\})?",
            ")");
    private static final int MODNAME_VALUE_GROUP = 1;
    private static final int OTHER_ANNOTATION_GROUP = 2;
    private static final int OTHER_VALUE_GROUP = 3;
  }

  /** Method exported to JS to parse a file for dependencies and annotations. */
  @JsMethod(name = "gjd", namespace = "jscomp")
  public static JsObject<Object> gjd(String code, String filename, @Nullable Reporter reporter) {
    return parse(code, filename, reporter).full();
  }

  /** Internal implementation to produce the {@link FileInfo} object. */
  private static FileInfo parse(String code, String filename, @Nullable Reporter reporter) {
    ErrorReporter errorReporter = new DelegatingReporter(reporter);
    Compiler compiler = new Compiler();
    compiler.init(
        ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(), new CompilerOptions());

    Config config =
        ParserRunner.createConfig(
            // TODO(sdh): ES6_STRICT, with a non-strict fallback - then give warnings.
            Config.LanguageMode.ECMASCRIPT6,
            Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE,
            Config.SourceLocationInformation.PRESERVE,
            Config.RunMode.KEEP_GOING,
            /* extraAnnotationNames */ ImmutableSet.<String>of());

    SourceFile source = SourceFile.fromCode(filename, code);
    FileInfo info = new FileInfo(errorReporter);
    ParserRunner.ParseResult parsed = ParserRunner.parse(source, code, config, errorReporter);
    parsed.ast.setInputId(new InputId(filename));
    String version = parsed.features.version();
    if (!version.equals("es3")) {
      info.loadFlags.add(JsArray.of("lang", version));
    }

    for (Comment comment : parsed.comments) {
      if (comment.type == Comment.Type.JSDOC) {
        parseComment(comment, info);
      }
    }
    NodeTraversal.traverseEs6(compiler, parsed.ast, new Traverser(info));
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
          info.customAnnotations.add(
              JsArray.of(annotation.name.substring(1), annotation.value));
          break;
        case "@enhance":
          if (!annotation.value.isEmpty()) {
            info.customAnnotations.add(
                JsArray.of(annotation.name.substring(1), annotation.value));
          }
          break;
        default:
          if (fileOverview) {
            info.customAnnotations.add(
                JsArray.of(annotation.name.substring(1), annotation.value));
          }
      }
    }
  }

  /** Traverser that mutates {@code #info} with information from the AST. */
  private static class Traverser extends AbstractPostOrderCallback {
    final FileInfo info;

    Traverser(FileInfo info) {
      this.info = info;
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
      // Look for goog.* calls
      if (node.isGetProp() && node.getFirstChild().isName()
          && node.getFirstChild().getString().equals("goog")) {
        Var root = traversal.getScope().getVar("goog");
        if (root == null) {
          info.goog = true;
          if (parent.isCall() && parent.getChildCount() < 3) {
            Node arg;
            switch (node.getLastChild().getString()) {
              case "module":
                info.loadFlags.add(JsArray.of("module", "goog"));
                // fall through
              case "provide":
                arg = parent.getSecondChild();
                if (arg.isString()) {
                  info.provides.add(arg.getString());
                } // TODO(sdh): else warning?
                break;
              case "require":
                arg = parent.getSecondChild();
                if (arg.isString()) {
                  info.requires.add(arg.getString());
                } // TODO(sdh): else warning?
                break;
              case "setTestOnly":
                info.testonly = true;
                break;
              default:
                // Do nothing
            }
          }
        }
      }

      // Look for ES6 import statements
      if (node.isImport()) {
        Node moduleSpecifier = node.getChildAtIndex(2);
        // NOTE: previous tool was more forgiving here.
        Preconditions.checkState(moduleSpecifier.isString());
        info.loadFlags.add(JsArray.of("module", "es6"));
        info.importedModules.add(moduleSpecifier.getString());
      } else if (node.isExport()) {
        info.loadFlags.add(JsArray.of("module", "es6"));
      }
    }
  }

  /** JS function interface for reporting errors. */
  @JsFunction
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

  @Override
  public void onModuleLoad() {}

  /** Returns an associative multimap. */
  private static Set<JsArray<String>> assoc() {
    return new TreeSet<>(
        Ordering.<String>natural()
            .lexicographical()
            .onResultOf(
                new Function<JsArray<String>, List<String>>() {
                  @Override public List<String> apply(JsArray<String> arg) {
                    return arg.asList();
                  }
                }));
  }

  /** Sparse object helper class: only adds non-trivial values. */
  private static class SparseObject {
    final JsObject<Object> object = new JsObject<>();

    SparseObject set(String key, Iterable<?> iterable) {
      JsArray<?> array = JsArray.copyOf(iterable);
      if (array.getLength() > 0) {
        object.set(key, array);
      }
      return this;
    }

    SparseObject set(String key, String value) {
      if (value != null && !value.isEmpty()) {
        object.set(key, value);
      }
      return this;
    }

    SparseObject set(String key, boolean value) {
      if (value) {
        object.set(key, value);
      }
      return this;
    }
  }
}
