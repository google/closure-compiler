/*
 * Copyright 2021 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.javascript.jscomp.AbstractCompiler.LocaleData;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A compiler pass to collect locale data for substitution at a later stage of the compilation,
 * where the locale data to be collected present in files annotated with `@localeFile`, and the
 * locale extracted for assignments annotated with `@localeObject` and specific values within the
 * structure are annotated with `@localeValue`.
 *
 * <p>The actual locale selection is a switch with a specific structure annotated with
 * `@localeSelect`. This switch is replaced with an assignment to a clone of the first locale with
 * the locale specific values replaced.
 */
@GwtIncompatible("Unnecessary")
final class LocaleDataPasses {

  private LocaleDataPasses() {}

  // Replacements for values that needed to be protected from optimizations.
  static final String GOOG_LOCALE_REPLACEMENT = "__JSC_LOCALE__";
  static final String LOCALE_VALUE_REPLACEMENT = "__JSC_LOCALE_VALUE__";

  static final DiagnosticType UNEXPECTED_GOOG_LOCALE =
      DiagnosticType.error(
          "JSC_UNEXPECTED_GOOG_LOCALE", "`goog.LOCALE` appears in a file lacking `@localeFile`.");

  public static final DiagnosticType LOCALE_FILE_MALFORMED =
      DiagnosticType.error("JSC_LOCALE_FILE_MALFORMED", "Malformed locale data file. {0}");

  public static final DiagnosticType LOCALE_MISSING_BASE_FILE =
      DiagnosticType.error(
          "JSC_LOCALE_MISSING_BASE_FILE", "Missing base file for extension file: {0}");

  private static class LocaleDataImpl implements LocaleData {
    ArrayList<LinkedHashMap<String, Node>> data;

    LocaleDataImpl(ArrayList<LinkedHashMap<String, Node>> data) {
      this.data = data;
    }
  }

  static class ExtractAndProtect implements CompilerPass {
    private final AbstractCompiler compiler;
    private LocaleData localeData;

    ExtractAndProtect(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      // Create the extern symbols
      NodeUtil.createSynthesizedExternsSymbol(compiler, GOOG_LOCALE_REPLACEMENT);
      ProtectCurrentLocale protectLocaleCallback = new ProtectCurrentLocale(compiler);
      NodeTraversal.traverse(compiler, root, protectLocaleCallback);
      localeData = new LocaleDataImpl(null);
    }

    public LocaleData getLocaleValuesDataMaps() {
      checkNotNull(localeData, "process must be called before getLocaleValuesDataMaps");
      return localeData;
    }
  }

  /**
   * To avoid the optimizations from optimizing known constants, make then non-const by replacing
   * them with a call to an extern value.
   */
  private static class ProtectCurrentLocale implements NodeTraversal.Callback {
    private final AbstractCompiler compiler;
    private boolean replaced = false;

    ProtectCurrentLocale(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !replaced;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (parent != null
          && parent.isAssign()
          && parent.getFirstChild() == n
          && isGoogDotLocaleReference(n)) {

        Node value = parent.getLastChild();
        Node replacement = IR.name(GOOG_LOCALE_REPLACEMENT);
        replacement.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        value.replaceWith(replacement);
        compiler.reportChangeToEnclosingScope(parent);

        // Mark the value as found.  The definition should be early in the traversal so
        // we can avoid most of the work by terminating early.
        replaced = true;
      }
    }
  }

  // matching against an actual Node is faster than matching against the string "goog.LOCALE"
  private static final Node QNAME_FOR_GOOG_LOCALE = IR.getprop(IR.name("goog"), "LOCALE");

  private static boolean isGoogDotLocaleReference(Node n) {
    // NOTE: Theoretically there could be a local variable named `goog`, but it's not worth checking
    // for that.
    return n.matchesQualifiedName(QNAME_FOR_GOOG_LOCALE);
  }

  private static String normalizeLocale(String locale) {
    return locale.replace('-', '_');
  }

  /**
   * This class performs the actual locale specific substitutions for the stand-in values create by
   * `ExtractAndProtectLocaleData` It will replace both __JSC_LOCALE__ and __JSC_LOCALE_VALUE__
   * references.
   */
  static class LocaleSubstitutions extends AbstractPostOrderCallback implements CompilerPass {

    private static final String DEFAULT_LOCALE = "en";
    private final Node qnameForLocale = IR.name(GOOG_LOCALE_REPLACEMENT);

    private final AbstractCompiler compiler;
    private final ArrayList<LinkedHashMap<String, Node>> localeValueMap;
    private final String locale;

    LocaleSubstitutions(AbstractCompiler compiler, String locale, LocaleData localeData) {
      this.compiler = compiler;
      // Use the "default" locale if not otherwise set.
      this.locale = locale == null ? DEFAULT_LOCALE : locale;
      this.localeValueMap = null;
    }

    @Override
    public void process(Node externs, Node root) {
      // Create the extern symbol
      NodeTraversal.traverse(compiler, root, this);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.matchesName(this.qnameForLocale)) {
        Node replacement = IR.string(checkNotNull(locale)).srcref(n);
        n.replaceWith(replacement);
        compiler.reportChangeToEnclosingScope(replacement);
      }
    }
  }

  /**
   * Hack things a bit and stuff some data into the AST in order to serialize it. It will be removed
   * when deserializing.
   */
  public static void addLocaleDataToAST(AbstractCompiler compiler, LocaleData localeData) {
    ArrayList<LinkedHashMap<String, Node>> data =
        localeData == null ? null : ((LocaleDataImpl) localeData).data;

    // Serialize locale data as:
    //   [{...},{...}]
    // Where each object is
    //   {"locale": value, "otherLocale": otherValue}
    Node arr = IR.arraylit();
    if (data != null) {
      for (LinkedHashMap<String, Node> localeValueEntry : data) {
        Node obj = IR.objectlit();
        arr.addChildToBack(obj);
        for (Map.Entry<String, Node> entry : localeValueEntry.entrySet()) {
          String locale = entry.getKey();
          Node value = entry.getValue();

          obj.addChildToBack(IR.quotedStringKey(locale, value));
        }
      }
    }

    Node stmt = IR.var(IR.name("__JSC_LOCALE_DATA__"), arr);

    Node root = compiler.getJsRoot();
    Node script = root.getFirstChild();
    checkState(script.isScript());
    stmt.srcrefTreeIfMissing(script);

    script.addChildToFront(stmt);
  }

  /** Remove LocaleData prepended in the AST for serialization, remove it and restore the AST. */
  public static LocaleData reconstituteLocaleDataFromAST(AbstractCompiler compiler) {
    ArrayList<LinkedHashMap<String, Node>> localeData = new ArrayList<>();

    // Remove the first expression which is expected to be an array literal,
    // Where every entry is an object literal where the key is the locale
    // and the data is value

    Node root = compiler.getJsRoot();
    Node script = root.getFirstChild();
    checkState(script.isScript());

    // Remove it from the AST.
    Node stmt = script.removeFirstChild();
    checkState(stmt.isVar());

    Node nameNode = stmt.getFirstChild();
    checkState(nameNode.isName());

    Node arrlit = nameNode.getLastChild();
    checkState(arrlit.isArrayLit());

    for (Node obj = arrlit.getFirstChild(); obj != null; obj = obj.getNext()) {
      checkState(obj.isObjectLit());
      LinkedHashMap<String, Node> map = new LinkedHashMap<>();
      localeData.add(map);
      for (Node member = obj.getFirstChild(); member != null; member = member.getNext()) {
        checkState(member.isStringKey());
        String locale = member.getString();
        Node value = member.removeFirstChild();

        map.put(normalizeLocale(locale), value);
      }
    }

    return new LocaleDataImpl(localeData);
  }
}
