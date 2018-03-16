/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/** An optimization pass to rewrite J2CL String.valueOf. */
final class J2clStringValueOfRewriterPass extends AbstractPeepholeOptimization {

  private static final String METHOD_NAME =
      "module$exports$java$lang$String$impl.m_valueOf__java_lang_Object";

  private boolean shouldRunJ2clPasses = false;

  @Override
  void beginTraversal(AbstractCompiler compiler) {
    super.beginTraversal(compiler);
    shouldRunJ2clPasses = J2clSourceFileChecker.shouldRunJ2clPasses(compiler);
  }

  @Override
  public Node optimizeSubtree(Node node) {
    if (!shouldRunJ2clPasses || !isStringValueOfCall(node)) {
      return node;
    }

    Node replacement = tryRewriteStringValueOfCall(node);
    if (replacement != node) {
      replacement = replacement.useSourceInfoIfMissingFrom(node);
      node.replaceWith(replacement);
      compiler.reportChangeToEnclosingScope(replacement);
    }
    return replacement;
  }

  /*
   * Cases to rewrite:
   *   String.valueOf("foo") -> String("foo")
   *   String.valueOf(null) -> String("null")
   *   String.valueOf(undefined) -> "null"
   *   String.valueOf("foo" + bar) -> String("foo" + bar)
   *   String.valueOf(bar + "foo") -> String(bar + "foo")
   *   String.valueOf("foo" + String.valueOf(bar)) -> String("foo" + String.valueOf(bar))
   */
  private Node tryRewriteStringValueOfCall(Node n) {
    Node param = n.getSecondChild();
    if (NodeUtil.isUndefined(param)) {
      return IR.string("null");
    } else if (NodeUtil.isDefinedValue(param) && !param.isArrayLit()) {
      // Generate String(param), let other peephole optimizations handle the rest when safe
      return NodeUtil.newCallNode(IR.name("String").useSourceInfoFrom(n), param.detach());
    }
    return n;
  }

  private static boolean isStringValueOfCall(Node node) {
    return node.isCall()
        // Do not optimize if the parameter was removed
        && node.hasXChildren(2)
        && isStringValueOfMethodName(node.getFirstChild());
  }

  private static boolean isStringValueOfMethodName(Node fnName) {
    return fnName.isQualifiedName() && fnName.getOriginalQualifiedName().equals(METHOD_NAME);
  }
}
