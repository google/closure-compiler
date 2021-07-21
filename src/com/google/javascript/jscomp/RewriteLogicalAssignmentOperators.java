/*
 * Copyright 2020 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;

/** Replaces the ES2020 `||=`, `&&=`, and `??=` operators. */
public final class RewriteLogicalAssignmentOperators
    implements NodeTraversal.Callback, CompilerPass {

  private static final String TEMP_VAR_NAME_PREFIX = "$jscomp$logical$assign$tmp";
  private static final String TEMP_INDEX_VAR_NAME_PREFIX = "$jscomp$logical$assign$tmpindex";

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;

  public RewriteLogicalAssignmentOperators(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, Feature.LOGICAL_ASSIGNMENT);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      FeatureSet scriptFeatures = NodeUtil.getFeatureSetOfScript(n);
      return scriptFeatures == null || scriptFeatures.contains(Feature.LOGICAL_ASSIGNMENT);
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node logicalAssignment, Node parent) {
    switch (logicalAssignment.getToken()) {
      case ASSIGN_OR:
      case ASSIGN_AND:
      case ASSIGN_COALESCE:
        visitLogicalAssignmentOperator(t, logicalAssignment);
        break;
      default:
        break;
    }
  }

  private void visitLogicalAssignmentOperator(NodeTraversal t, Node logicalAssignment) {
    Node enclosingStatement = NodeUtil.getEnclosingStatement(logicalAssignment);

    Node left = logicalAssignment.removeFirstChild();
    Node right = logicalAssignment.getLastChild().detach();

    Node replacement;

    if (left.isName()) {
      replacement = handleLHSName(logicalAssignment, left, right);

    } else {
      checkState(left.isGetProp() || left.isGetElem(), left);
      replacement =
          handleLHSPropertyReference(t, logicalAssignment, left, right, enclosingStatement);
      NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.LET_DECLARATIONS, compiler);
    }

    logicalAssignment.replaceWith(replacement);

    compiler.reportChangeToEnclosingScope(enclosingStatement);
  }

  public Node handleLHSName(Node logicalAssignment, Node left, Node right) {
    // handle name case
    // e.g. convert `name ??= something()` to
    // `name ?? (name = something())`
    Node assignToRHS = astFactory.createAssign(left, right);

    // TODO(bradfordcsmith): We should have an AstFactory method for this.
    Node replacement =
        new Node(NodeUtil.getOpFromAssignmentOp(logicalAssignment), left.cloneNode(), assignToRHS)
            .setJSType(logicalAssignment.getJSType()); // assignTmp, assignToRHS
    replacement.srcrefTreeIfMissing(left);

    return replacement;
  }

  public Node handleLHSPropertyReference(
      NodeTraversal t, Node logicalAssignment, Node left, Node right, Node enclosingStatement) {
    // handle getprop case and getelem case
    CompilerInput input = t.getInput();
    String uniqueId = compiler.getUniqueIdSupplier().getUniqueId(input);
    String tempVarName = TEMP_VAR_NAME_PREFIX + uniqueId;

    Node assignToRHS;
    Node newLHS;

    Node objectNode = left.removeFirstChild();

    Node let = astFactory.createSingleLetNameDeclaration(tempVarName).srcrefTreeIfMissing(left);
    let.insertBefore(enclosingStatement);

    Node tempName = astFactory.createName(tempVarName, objectNode.getJSType());
    Node assignTemp = astFactory.createAssign(tempName, objectNode); // (tmp = a())

    if (left.isGetProp()) {
      // handle getprop case
      // e.g. convert `(someExpression).property ??= something()` to
      // let tmp;
      // (tmp = someExpression).property ?? (tmp.property = something());
      String propertyName = left.getString();

      Node tempPropName = astFactory.createName(tempVarName, left.getJSType()); // (tmp.property)
      Node tempProp = astFactory.createGetProp(tempPropName, propertyName);
      assignToRHS = astFactory.createAssign(tempProp, right); // (tmp.property = b())
      newLHS = astFactory.createGetProp(assignTemp, propertyName);
    } else {
      // handle getelem case
      // e.g. convert `someExpression[indexExpression] ??= something()` to
      // let tmp;
      // let tmpIndex;
      // (tmp = someExpression)[tmpIndex = indexExpression] ?? (tmp[tmpIndex] = something());
      checkState(left.isGetElem(), left);
      String tempIndexVarName = TEMP_INDEX_VAR_NAME_PREFIX + uniqueId;

      JSType leftType = left.getJSType();
      Node indexExprNode = left.getLastChild().detach();

      Node letIndex =
          astFactory.createSingleLetNameDeclaration(tempIndexVarName).srcrefTreeIfMissing(left);
      Node tempIndexName = astFactory.createName(tempIndexVarName, indexExprNode.getJSType());

      Node assignTempIndex =
          astFactory.createAssign(tempIndexName, indexExprNode); // tmpIndex = indexExpression
      assignTempIndex.srcrefTreeIfMissing(left);

      Node tempElemName = astFactory.createName(tempVarName, leftType); // (tmp[tmpIndex])
      Node tempElem = astFactory.createGetElem(tempElemName, tempIndexName.cloneNode());

      assignToRHS = astFactory.createAssign(tempElem, right); // (tmp[tmpIndex] = b())
      newLHS = astFactory.createGetElem(assignTemp, assignTempIndex);

      letIndex.insertBefore(enclosingStatement);
    }
    // TODO(bradfordcsmith): We should have an AstFactory method for this.
    Node replacement =
        new Node(NodeUtil.getOpFromAssignmentOp(logicalAssignment), newLHS, assignToRHS)
            .setJSType(logicalAssignment.getJSType()); // assignTmp, assignToRHS
    replacement.srcrefTreeIfMissing(left);

    return replacement;
  }
}
