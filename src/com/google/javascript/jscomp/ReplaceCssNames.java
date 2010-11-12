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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * ReplaceCssNames replaces occurrences of goog.getCssName('foo') with a shorter
 * version from the passed in renaming map.
 *
 * Given the renaming map:
 *   {
 *     once:  a,
 *     upon:  b,
 *     atime: c,
 *     long:  d,
 *     time:  e
 *     ago:   f
 *   }
 *
 * The following outputs are expected:
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

  private final JSType nativeStringType;

  ReplaceCssNames(AbstractCompiler compiler,
      @Nullable Map<String, Integer> cssNames) {
    this.compiler = compiler;
    this.cssNames = cssNames;
    this.nativeStringType =  compiler.getTypeRegistry()
        .getNativeType(STRING_TYPE);
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
      if (n.getType() == Token.CALL &&
          GET_CSS_NAME_FUNCTION.equals(n.getFirstChild().getQualifiedName())) {
        int count = n.getChildCount();
        Node first = n.getFirstChild().getNext();
        switch (count) {
          case 2:
            // Replace the function call with the processed argument.
            if (first.getType() == Token.STRING) {
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

            if (first.getType() == Token.STRING) {
              compiler.report(t.makeError(
                  n, UNEXPECTED_STRING_LITERAL_ERROR,
                  first.getString(), second.getString()));

            } else if (second.getType() == Token.STRING) {
              processStringNode(t, second);
              n.removeChild(first);
              Node replacement = new Node(Token.ADD, first,
                  Node.newString("-" + second.getString())
                      .copyInformationFrom(second))
                  .copyInformationFrom(n);
              replacement.setJSType(nativeStringType);
              parent.replaceChild(n, replacement);
              compiler.reportCodeChange();

            } else {
              compiler.report(t.makeError(n, STRING_LITERAL_EXPECTED_ERROR,
                  Token.name(second.getType())));
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
      if (symbolMap != null || cssNames != null) {
        String[] parts = n.getString().split("-");
        for (int i = 0; i < parts.length; i++) {
          if (cssNames != null) {
            Integer count = cssNames.get(parts[i]);
            if (count == null) {
              count = Integer.valueOf(0);
            }
            cssNames.put(parts[i], count.intValue() + 1);
          }
          if (symbolMap != null) {
            String replacement = symbolMap.get(parts[i]);
            if (replacement == null) {
              // If we can't encode all parts, don't encode any of it.
              compiler.report(t.makeError(
                  n, UNKNOWN_SYMBOL_WARNING, parts[i], n.getString()));
              return;
            }
            parts[i] = replacement;
          }
        }
        if (symbolMap != null) {
          n.setString(Joiner.on("-").join(parts));
        }
      }
    }
  }

}
