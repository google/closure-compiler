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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.AstFactory.type;

import com.google.common.base.Suppliers;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import java.util.function.Supplier;

/** Replaces the ES7 `**` and `**=` operators to calls to `Math.pow`. */
public final class Es7RewriteExponentialOperator implements NodeTraversal.Callback, CompilerPass {

  static final DiagnosticType TRANSPILE_EXPONENT_USING_BIGINT =
      DiagnosticType.error(
          "JSC_TRANSPILE_EXPONENT_USING_BIGINT",
          "Cannot transpile `**` operator applied to BigInt operands.");

  private static final String TEMP_VAR_NAME_PREFIX = "$jscomp$exp$assign$tmp";
  private static final String TEMP_INDEX_VAR_NAME_PREFIX = "$jscomp$exp$assign$tmpindex";

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final UniqueIdSupplier uniqueIdSupplier;
  // This node should only ever be cloned, not directly inserted.
  private final Supplier<Node> mathPowCall = Suppliers.memoize(this::createMathPowCall);

  public Es7RewriteExponentialOperator(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.uniqueIdSupplier = compiler.getUniqueIdSupplier();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, Feature.EXPONENT_OP);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      FeatureSet scriptFeatures = NodeUtil.getFeatureSetOfScript(n);
      return scriptFeatures == null || scriptFeatures.contains(Feature.EXPONENT_OP);
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case EXPONENT:
        if (checkOperatorType(n)) {
          visitExponentiationOperator(n);
        }
        break;
      case ASSIGN_EXPONENT:
        if (checkOperatorType(n)) {
          visitExponentiationAssignmentOperator(t, n);
        }
        break;
      default:
        break;
    }
  }

  private void visitExponentiationOperator(Node operator) {
    Node callClone = mathPowCall.get().cloneTree();
    callClone.addChildToBack(operator.removeFirstChild()); // Base argument.
    callClone.addChildToBack(operator.removeFirstChild()); // Exponent argument.

    callClone.srcrefTreeIfMissing(operator);
    operator.replaceWith(callClone);

    compiler.reportChangeToEnclosingScope(callClone);
  }

  private void visitExponentiationAssignmentOperator(NodeTraversal t, Node operator) {
    Node enclosingStatement = NodeUtil.getEnclosingStatement(operator);

    Node left = operator.removeFirstChild();

    Node replacement;

    if (left.isName()) {
      replacement = handleLHSName(operator, left);
    } else {
      checkState(left.isGetProp() || left.isGetElem(), left);
      replacement = handleLHSPropertyReference(t, operator, left, enclosingStatement);
    }

    operator.replaceWith(replacement);

    compiler.reportChangeToEnclosingScope(enclosingStatement);
  }

  private Node handleLHSName(Node operator, Node left) {
    // handle name case
    // e.g. convert `name **= value` to
    // `name = Math.pow(name, value)`
    Node callClone = mathPowCall.get().cloneTree();
    callClone.addChildToBack(left.cloneTree()); // Base argument.
    callClone.addChildToBack(operator.removeFirstChild()); // Exponent argument.

    return astFactory.createAssign(left, callClone).srcrefTreeIfMissing(operator);
  }

  private Node handleLHSPropertyReference(
      NodeTraversal t, Node operator, Node left, Node enclosingStatement) {
    // handle getprop case and getelem case
    CompilerInput input = t.getInput();
    String uniqueId = uniqueIdSupplier.getUniqueId(input);
    String tempVarName = TEMP_VAR_NAME_PREFIX + uniqueId;

    Node newLHS;
    Node tempPropOrElem;

    Node objectNode = left.removeFirstChild();

    Node let = astFactory.createSingleLetNameDeclaration(tempVarName).srcrefTree(operator);
    let.insertBefore(enclosingStatement);

    Node tempName = astFactory.createName(tempVarName, type(objectNode)).srcref(objectNode);
    Node assignTemp =
        astFactory.createAssign(tempName, objectNode).srcref(objectNode); // (tmp = someExpression)

    if (left.isGetProp()) {
      // handle getprop case
      // e.g. convert `(someExpression).property **= value` to
      // let tmp;
      // (tmp = someExpression).property = Math.pow(tmp.property, value);
      String propertyName = left.getString();

      tempPropOrElem =
          astFactory
              .createGetProp(tempName.cloneNode(), propertyName, type(left))
              .srcref(left); // (tmp.property)
      newLHS =
          astFactory
              .createGetProp(assignTemp, propertyName, type(left))
              .srcref(left); // ((tmp = someExpression).property)
    } else {
      // handle getelem case
      // e.g. convert `someExpression[indexExpression] **= value` to
      // let tmp;
      // let tmpIndex;
      // `(tmp = someExpression)[(tmpIndex = indexExpression)] = Math.pow(tmp[tmpIndex], value);`
      checkState(left.isGetElem(), left);
      String tempIndexVarName = TEMP_INDEX_VAR_NAME_PREFIX + uniqueId;

      Node indexExprNode = left.getLastChild().detach();

      Node letIndex =
          astFactory.createSingleLetNameDeclaration(tempIndexVarName).srcrefTree(operator);
      letIndex.insertBefore(enclosingStatement);

      Node tempIndexName =
          astFactory
              .createName(tempIndexVarName, type(indexExprNode))
              .srcref(indexExprNode); // tmpIndex

      Node assignTempIndex =
          astFactory
              .createAssign(tempIndexName, indexExprNode)
              .srcref(indexExprNode); // [tmpIndex = indexExpression]

      tempPropOrElem =
          astFactory
              .createGetElem(tempName.cloneNode(), tempIndexName.cloneNode())
              .copyTypeFrom(left)
              .srcref(left); // (tmp[tmpIndex])

      newLHS =
          astFactory
              .createGetElem(assignTemp, assignTempIndex)
              .copyTypeFrom(left)
              .srcref(left); // (tmp = someExpression)[tmpIndex = indexExpression]
    }
    Node callClone = mathPowCall.get().cloneTree();
    callClone.addChildToBack(tempPropOrElem.cloneTree()); // Base argument.
    callClone.addChildToBack(operator.removeFirstChild()); // Exponent argument.

    NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.LET_DECLARATIONS, compiler);

    return astFactory.createAssign(newLHS, callClone).srcrefTreeIfMissing(operator);
  }

  private Node createMathPowCall() {
    return astFactory.createCall(
        astFactory.createQName(compiler.getTranspilationNamespace(), "Math.pow"),
        type(StandardColors.NUMBER));
  }

  // Report an error if the `**` getting transpiled to `Math.pow()`is of BIGINT type
  private boolean checkOperatorType(Node operator) {
    checkArgument(operator.isExponent() || operator.isAssignExponent(), operator);
    if (compiler.hasTypeCheckingRun()) {
      if (operator.getColor().equals(StandardColors.BIGINT)) {
        compiler.report(JSError.make(operator, TRANSPILE_EXPONENT_USING_BIGINT));
        return false;
      }
    }
    return true;
  }
}
