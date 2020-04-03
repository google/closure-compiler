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

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

/** Replaces the ES7 `**` and `**=` operators to calls to `Math.pow`. */
public final class Es7RewriteExponentialOperator
    implements NodeTraversal.Callback, HotSwapCompilerPass {

  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.EXPONENT_OP);

  private final AbstractCompiler compiler;
  private final Node mathPowCall; // This node should only ever be cloned, not directly inserted.

  private final JSType numberType;
  private final JSType stringType;
  private final JSType mathType;
  private final JSType mathPowType;

  public Es7RewriteExponentialOperator(AbstractCompiler compiler) {
    this.compiler = compiler;

    if (compiler.hasTypeCheckingRun()) {
      JSTypeRegistry registry = compiler.getTypeRegistry();
      this.numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
      this.stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
      // TODO(nickreid): Get the actual type of the `Math` object here in case optimizations care.
      this.mathType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
      this.mathPowType = registry.createFunctionType(numberType, numberType, numberType);
    } else {
      this.numberType = null;
      this.stringType = null;
      this.mathType = null;
      this.mathPowType = null;
    }

    this.mathPowCall = createMathPowCall();
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case EXPONENT:
        visitExponentiationOperator(n);
        break;
      case ASSIGN_EXPONENT:
        visitExponentiationAssignmentOperator(n);
        break;
      default:
        break;
    }
  }

  private void visitExponentiationOperator(Node operator) {
    Node callClone = mathPowCall.cloneTree();
    callClone.addChildToBack(operator.removeFirstChild()); // Base argument.
    callClone.addChildToBack(operator.removeFirstChild()); // Exponent argument.

    callClone.useSourceInfoIfMissingFromForTree(operator);
    operator.replaceWith(callClone);

    compiler.reportChangeToEnclosingScope(callClone);
  }

  private void visitExponentiationAssignmentOperator(Node operator) {
    Node lValue = operator.removeFirstChild();

    Node callClone = mathPowCall.cloneTree();
    callClone.addChildToBack(lValue.cloneTree()); // Base argument.
    callClone.addChildToBack(operator.removeFirstChild()); // Exponent argument.

    Node assignment = IR.assign(lValue, callClone).setJSType(numberType);

    assignment.useSourceInfoIfMissingFromForTree(operator);
    operator.replaceWith(assignment);

    compiler.reportChangeToEnclosingScope(assignment);
  }

  private Node createMathPowCall() {
    return IR.call(
            IR.getprop(
                    IR.name("Math").setJSType(mathType), // Force wrapping.
                    IR.string("pow").setJSType(stringType))
                .setJSType(mathPowType))
        .setJSType(numberType);
  }
}
