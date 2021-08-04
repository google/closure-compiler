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

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.AstFactory.type;

import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;

/* Helper class for RewriteLogicalAssignmentOperators */
class RewriteLogicalAssignmentOperatorsHelper {

  private static final String TEMP_VAR_NAME_PREFIX = "$jscomp$logical$assign$tmp";
  private static final String TEMP_INDEX_VAR_NAME_PREFIX = "$jscomp$logical$assign$tmpindex";

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final UniqueIdSupplier uniqueIdSupplier;

  public RewriteLogicalAssignmentOperatorsHelper(
      AbstractCompiler compiler, AstFactory astFactory, UniqueIdSupplier uniqueIdSupplier) {
    this.compiler = compiler;
    this.astFactory = astFactory;
    this.uniqueIdSupplier = uniqueIdSupplier;
  }

  public void visitLogicalAssignmentOperator(NodeTraversal t, Node logicalAssignment) {
    Node enclosingStatement = NodeUtil.getEnclosingStatement(logicalAssignment);

    Node left = logicalAssignment.removeFirstChild();
    Node right = logicalAssignment.getLastChild().detach();

    Node replacement;

    while (left.isCast()) {
      // This pass runs after type checking, which is what requires the CAST nodes.
      // They aren't needed after that, but they are still in the AST until
      // Normalization removes them. So, it's safe to just remove this CAST now to simplify things.
      // If this pass moves after Normalization, then it will no longer be necessary to check for
      // CAST nodes.
      left = left.removeFirstChild();
    }

    if (left.isName()) {
      replacement = handleLHSName(logicalAssignment, left, right);

    } else {
      checkState(left.isGetProp() || left.isGetElem(), left);
      replacement =
          handleLHSPropertyReference(t, logicalAssignment, left, right, enclosingStatement);
    }

    logicalAssignment.replaceWith(replacement);

    compiler.reportChangeToEnclosingScope(enclosingStatement);
  }

  public Node handleLHSName(Node logicalAssignment, Node left, Node right) {
    // handle name case
    // e.g. convert `name ??= something()` to
    // `name ?? (name = something())`
    Node assignToRHS = astFactory.createAssign(left, right).srcref(right);

    // TODO(bradfordcsmith): We should have an AstFactory method for this.
    return new Node(
            NodeUtil.getOpFromAssignmentOp(logicalAssignment), left.cloneNode(), assignToRHS)
        .copyTypeFrom(logicalAssignment)
        .srcref(logicalAssignment);
  }

  public Node handleLHSPropertyReference(
      NodeTraversal t, Node logicalAssignment, Node left, Node right, Node enclosingStatement) {
    // handle getprop case and getelem case
    CompilerInput input = t.getInput();
    String uniqueId = uniqueIdSupplier.getUniqueId(input);
    String tempVarName = TEMP_VAR_NAME_PREFIX + uniqueId;

    Node assignToRHS;
    Node newLHS;

    Node objectNode = left.removeFirstChild();

    Node let = astFactory.createSingleLetNameDeclaration(tempVarName).srcrefTree(logicalAssignment);
    let.insertBefore(enclosingStatement);

    Node tempName = astFactory.createName(tempVarName, type(objectNode)).srcref(objectNode);
    Node assignTemp =
        astFactory.createAssign(tempName, objectNode).srcref(objectNode); // (tmp = someExpression)

    if (left.isGetProp()) {
      // handle getprop case
      // e.g. convert `(someExpression).property ??= something()` to
      // let tmp;
      // (tmp = someExpression).property ?? (tmp.property = something());
      String propertyName = left.getString();

      Node tempProp =
          astFactory
              .createGetProp(tempName.cloneNode(), propertyName, type(left))
              .srcref(right); // (tmp.property)
      assignToRHS =
          astFactory.createAssign(tempProp, right).srcref(right); // (tmp.property = something())
      newLHS =
          astFactory
              .createGetProp(assignTemp, propertyName, type(left))
              .srcref(left); // ((tmp = someExpression).property)
    } else {
      // handle getelem case
      // e.g. convert `someExpression[indexExpression] ??= something()` to
      // let tmp;
      // let tmpIndex;
      // (tmp = someExpression)[tmpIndex = indexExpression] ?? (tmp[tmpIndex] = something());
      checkState(left.isGetElem(), left);
      String tempIndexVarName = TEMP_INDEX_VAR_NAME_PREFIX + uniqueId;

      Node indexExprNode = left.getLastChild().detach();

      Node letIndex =
          astFactory.createSingleLetNameDeclaration(tempIndexVarName).srcrefTree(logicalAssignment);
      letIndex.insertBefore(enclosingStatement);

      Node tempIndexName =
          astFactory
              .createName(tempIndexVarName, type(indexExprNode))
              .srcref(indexExprNode); // tmpIndex

      Node assignTempIndex =
          astFactory
              .createAssign(tempIndexName, indexExprNode)
              .srcref(indexExprNode); // [tmpIndex = indexExpression]

      Node tempElem =
          astFactory
              .createGetElem(tempName.cloneNode(), tempIndexName.cloneNode())
              .copyTypeFrom(left)
              .srcref(right); // (tmp[tmpIndex])

      assignToRHS =
          astFactory.createAssign(tempElem, right).srcref(right); // (tmp[tmpIndex] = something())
      newLHS =
          astFactory
              .createGetElem(assignTemp, assignTempIndex)
              .copyTypeFrom(left)
              .srcref(left); // (tmp = someExpression)[tmpIndex = indexExpression]
    }

    NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.LET_DECLARATIONS, compiler);

    // TODO(bradfordcsmith): We should have an AstFactory method for this.
    return new Node(NodeUtil.getOpFromAssignmentOp(logicalAssignment), newLHS, assignToRHS)
        .copyTypeFrom(logicalAssignment)
        .srcref(logicalAssignment);
  }
}
