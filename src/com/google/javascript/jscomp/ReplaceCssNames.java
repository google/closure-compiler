/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeI;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * ReplaceCssNames replaces occurrences of goog.getCssName('foo') with
 * a shorter version from the passed in renaming map. There are two
 * styles of operation: for 'BY_WHOLE' we look up the whole string in the
 * renaming map. For 'BY_PART', all the class name's components,
 * separated by '-', are renamed individually and then recombined.
 *
 * Given the renaming map:
 *   {
 *     once:  'a',
 *     upon:  'b',
 *     atime: 'c',
 *     long:  'd',
 *     time:  'e',
 *     ago:   'f'
 *   }
 *
 * The following outputs are expected with the 'BY_PART' renaming style:
 *
 * goog.getCssName('once') -> 'a'
 * goog.getCssName('once-upon-atime') -> 'a-b-c'
 *
 * var baseClass = goog.getCssName('long-time');
 * el.className = goog.getCssName(baseClass, 'ago');
 * ->
 * var baseClass = 'd-e';
 * el.className = baseClass + '-f';
 *
 * However if we have the following renaming map with the 'BY_WHOLE' renaming style:
 *   {
 *     once: 'a',
 *     upon-atime: 'b',
 *     long-time: 'c',
 *     ago: 'd'
 *   }
 *
 * Then we would expect:
 *
 * goog.getCssName('once') -> 'a'
 *
 * var baseClass = goog.getCssName('long-time');
 * el.className = goog.getCssName(baseClass, 'ago');
 * ->
 * var baseClass = 'c';
 * el.className = baseClass + '-d';
 *
 * In addition, the CSS names before replacement can optionally be gathered.
 *
 */
class ReplaceCssNames implements CompilerPass {

  static final String GET_CSS_NAME_FUNCTION = "goog.getCssName";

  static final DiagnosticType INVALID_NUM_ARGUMENTS_ERROR =
      DiagnosticType.error("JSC_GETCSSNAME_NUM_ARGS",
          "goog.getCssName called with \"{0}\" arguments, expected 1 or 2.");

  static final DiagnosticType STRING_LITERAL_EXPECTED_ERROR =
      DiagnosticType.error("JSC_GETCSSNAME_STRING_LITERAL_EXPECTED",
          "goog.getCssName called with invalid argument, string literal " +
          "expected.  Was \"{0}\".");

  static final DiagnosticType UNEXPECTED_STRING_LITERAL_ERROR =
    DiagnosticType.error("JSC_GETCSSNAME_UNEXPECTED_STRING_LITERAL",
        "goog.getCssName called with invalid arguments, string literal " +
        "passed as first of two arguments.  Did you mean " +
        "goog.getCssName(\"{0}-{1}\")?");

  static final DiagnosticType UNKNOWN_SYMBOL_WARNING =
      DiagnosticType.warning("JSC_GETCSSNAME_UNKNOWN_CSS_SYMBOL",
         "goog.getCssName called with unrecognized symbol \"{0}\" in class " +
         "\"{1}\".");


  private final AbstractCompiler compiler;

  private final Map<String, Integer> cssNames;

  private CssRenamingMap symbolMap;

  private final Set<String> whitelist;

  private final TypeI nativeStringType;

  ReplaceCssNames(AbstractCompiler compiler,
      @Nullable Map<String, Integer> cssNames,
      @Nullable Set<String> whitelist) {
    this.compiler = compiler;
    this.cssNames = cssNames;
    this.whitelist = whitelist;
    this.nativeStringType =
        compiler.getTypeIRegistry().getNativeType(STRING_TYPE);
  }

  @Override
  public void process(Node externs, Node root) {
    // The CssRenamingMap may not have been available from the compiler when
    // this ReplaceCssNames pass was constructed, so getCssRenamingMap() should
    // only be called before this pass is actually run.
    symbolMap = getCssRenamingMap();

    NodeTraversal.traverse(compiler, root, new Traversal());
  }

  @VisibleForTesting
  protected CssRenamingMap getCssRenamingMap() {
    return compiler.getCssRenamingMap();
  }

  private class Traversal extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall() && n.getFirstChild().matchesQualifiedName(GET_CSS_NAME_FUNCTION)) {
        int count = n.getChildCount();
        Node first = n.getFirstChild().getNext();
        switch (count) {
          case 2:
            // Replace the function call with the processed argument.
            if (first.isString()) {
              processStringNode(t, first);
              n.removeChild(first);
              parent.replaceChild(n, first);
              compiler.reportCodeChange();
            } else {
              compiler.report(t.makeError(n, STRING_LITERAL_EXPECTED_ERROR,
                  Token.name(first.getType())));
            }
            break;

          case 3:
            // Replace function call with concatenation of two args.  It's
            // assumed the first arg has already been processed.

            Node second = first.getNext();

            if (!second.isString()) {
              compiler.report(t.makeError(n, STRING_LITERAL_EXPECTED_ERROR,
                  Token.name(second.getType())));
            } else if (first.isString()) {
              compiler.report(t.makeError(
                  n, UNEXPECTED_STRING_LITERAL_ERROR,
                  first.getString(), second.getString()));
            } else {
              processStringNode(t, second);
              n.removeChild(first);
              Node replacement = IR.add(first,
                  IR.string("-" + second.getString())
                      .copyInformationFrom(second))
                  .copyInformationFrom(n);
              replacement.setTypeI(nativeStringType);
              parent.replaceChild(n, replacement);
              compiler.reportCodeChange();
            }
            break;

          default:
            compiler.report(t.makeError(
                n, INVALID_NUM_ARGUMENTS_ERROR, String.valueOf(count)));
        }
      }
    }

    /**
     * Processes a string argument to goog.getCssName().  The string will be
     * renamed based off the symbol map.  If there is no map or any part of the
     * name can't be renamed, a warning is reported to the compiler and the node
     * is left unchanged.
     *
     * If the type is unexpected then an error is reported to the compiler.
     *
     * @param t The node traversal.
     * @param n The string node to process.
     */
    private void processStringNode(NodeTraversal t, Node n) {
      String name = n.getString();
      if (whitelist != null && whitelist.contains(name)) {
        // We apply the whitelist before splitting on dashes, and not after.
        // External substitution maps should do the same.
        return;
      }
      String[] parts = name.split("-");
      if (symbolMap != null) {
        String replacement = null;
        switch (symbolMap.getStyle()) {
          case BY_WHOLE:
            replacement = symbolMap.get(name);
            if (replacement == null) {
              compiler.report(
                  t.makeError(n, UNKNOWN_SYMBOL_WARNING, name, name));
              return;
            }
            break;
          case BY_PART:
            String[] replaced = new String[parts.length];
            for (int i = 0; i < parts.length; i++) {
              String part = symbolMap.get(parts[i]);
              if (part == null) {
                // If we can't encode all parts, don't encode any of it.
                compiler.report(
                    t.makeError(n, UNKNOWN_SYMBOL_WARNING, parts[i], name));
                return;
              }
              replaced[i] = part;
            }
            replacement = Joiner.on("-").join(replaced);
            break;
          default:
            throw new IllegalStateException(
              "Unknown replacement style: " + symbolMap.getStyle());
        }
        n.setString(replacement);
      }
      if (cssNames != null) {
        // We still want to collect statistics even if we've already
        // done the full replace. The statistics are collected on a
        // per-part basis.
        for (int i = 0; i < parts.length; i++) {
          Integer count = cssNames.get(parts[i]);
          if (count == null) {
            count = Integer.valueOf(0);
          }
          cssNames.put(parts[i], count.intValue() + 1);
        }
      }
    }
  }

}
