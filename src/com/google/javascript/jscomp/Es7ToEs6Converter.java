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
package com.google.javascript.jscomp;

import static com.google.javascript.jscomp.Es6ToEs3Util.createType;
import static com.google.javascript.jscomp.Es6ToEs3Util.withType;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;

/**
 * Converts ES7 code to valid ES6 code.
 *
 * Currently this class converts ** and **= operators to calling Math.pow
 */
public final class Es7ToEs6Converter implements NodeTraversal.Callback, HotSwapCompilerPass {
  private final AbstractCompiler compiler;
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.EXPONENT_OP);
  private final boolean addTypes;
  private final Supplier<Node> mathPow = Suppliers.memoize(new MathPowSupplier());

  public Es7ToEs6Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.addTypes = compiler.hasTypeCheckingRun();
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.markFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.markFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case EXPONENT:
        visitExponentiationExpression(n, parent);
        break;
      case ASSIGN_EXPONENT:
        visitExponentiationAssignmentExpression(n, parent);
        break;
      default:
        break;
    }
  }

  private void visitExponentiationExpression(Node n, Node parent) {
    Node left = n.removeFirstChild();
    Node right = n.removeFirstChild();
    Node mathDotPowCall =
        withType(IR.call(mathPow.get().cloneTree(), left, right), n.getJSType())
            .useSourceInfoIfMissingFromForTree(n);
    parent.replaceChild(n, mathDotPowCall);
    compiler.reportChangeToEnclosingScope(mathDotPowCall);
  }

  private void visitExponentiationAssignmentExpression(Node n, Node parent) {
    Node left = n.removeFirstChild();
    Node right = n.removeFirstChild();
    Node mathDotPowCall =
        withType(IR.call(mathPow.get().cloneTree(), left.cloneTree(), right), n.getJSType());
    Node assign =
        withType(IR.assign(left, mathDotPowCall), n.getJSType())
            .useSourceInfoIfMissingFromForTree(n);
    parent.replaceChild(n, assign);
    compiler.reportChangeToEnclosingScope(assign);
  }

  private class MathPowSupplier implements Supplier<Node> {
    @Override public Node get() {
      Node n = NodeUtil.newQName(compiler, "Math.pow");
      if (addTypes) {
        JSType mathType = compiler.getTypeRegistry().getGlobalType("Math");
        JSType mathPowType = mathType.toMaybeObjectType().getPropertyType("pow");
        JSType stringType =
            createType(addTypes, compiler.getTypeRegistry(), JSTypeNative.STRING_TYPE);
        n.setJSType(mathPowType);
        n.getFirstChild().setJSType(mathType);
        n.getSecondChild().setJSType(stringType);
      }
      return n;
    }
  }
}
