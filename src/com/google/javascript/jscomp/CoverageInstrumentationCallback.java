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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.CoverageInstrumentationPass.CoverageReach;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.Map;

/**
 * This class implements a traversal to instrument an AST for code coverage.
 * @author praveenk@google.com (Praveen Kumashi)
 *
 */
@GwtIncompatible("FileInstrumentationData")
class CoverageInstrumentationCallback extends
    NodeTraversal.AbstractPostOrderCallback {

  private final AbstractCompiler compiler;
  private final Map<String, FileInstrumentationData> instrumentationData;
  private final CoverageReach reach;

  static final String ARRAY_NAME_PREFIX = "JSCompiler_lcov_data_";

  public CoverageInstrumentationCallback(
      AbstractCompiler compiler,
      Map<String, FileInstrumentationData> instrumentationData,
      CoverageReach reach) {
    this.compiler = compiler;
    this.instrumentationData = instrumentationData;
    this.reach = reach;
  }

  /**
   * Returns the name of the source file from which the given node originates.
   * @param traversal the traversal
   * @return the name of the file it originates from
   */
  private static String getFileName(NodeTraversal traversal) {
    return traversal.getSourceName();
  }

  /**
   * Returns a string that can be used as array name. The name is based on the
   * source filename of the AST node.
   */
  private String createArrayName(NodeTraversal traversal) {
    return ARRAY_NAME_PREFIX
        + CoverageUtil.createIdentifierFromText(getFileName(traversal));
  }

  /**
   * Creates and return a new instrumentation node. The instrumentation Node is
   * of the form: "arrayName[lineNumber] = true;"
   * Note 1: Node returns a 1-based line number.
   * Note 2: Line numbers in instrumentation are made 0-based. This is done to
   * map to their bit representation in BitField. Thus, there's a one-to-one
   * correspondence of the line number seen on instrumented files and their bit
   * encodings.
   *
   * @return an instrumentation node corresponding to the line number
   */
  private Node newInstrumentationNode(NodeTraversal traversal, Node node) {
    int lineNumber = node.getLineno();
    String arrayName = createArrayName(traversal);

    // Create instrumentation Node
    Node getElemNode = IR.getelem(
        IR.name(arrayName),
        IR.number(lineNumber - 1));  // Make line number 0-based
    Node exprNode = IR.exprResult(IR.assign(getElemNode, IR.trueNode()));

    // Note line as instrumented
    String fileName = getFileName(traversal);
    if (!instrumentationData.containsKey(fileName)) {
      instrumentationData.put(fileName,
                              new FileInstrumentationData(fileName, arrayName));
    }
    instrumentationData.get(fileName).setLineAsInstrumented(lineNumber);

    return exprNode.useSourceInfoIfMissingFromForTree(node);
  }

  /**
   * Create and return a new array declaration node. The array name is
   * generated based on the source filename, and declaration is of the form:
   * "var arrayNameUsedInFile = [];"
   */
  private Node newArrayDeclarationNode(NodeTraversal traversal) {
    return IR.var(
        IR.name(createArrayName(traversal)),
        IR.arraylit());
  }

  /**
   * @return a Node containing file specific setup logic.
   */
  private Node newHeaderNode(NodeTraversal traversal, Node srcref) {
    String fileName = getFileName(traversal);
    String arrayName = createArrayName(traversal);
    FileInstrumentationData data = instrumentationData.get(fileName);
    Preconditions.checkNotNull(data);

    return IR.block(
      newArrayDeclarationNode(traversal),
      IR.exprResult(IR.call(
          IR.getprop(
              IR.name("JSCompiler_lcov_executedLines"),
              IR.string("push")),
          IR.name(arrayName))),
      IR.exprResult(IR.call(
          IR.getprop(
              IR.name("JSCompiler_lcov_instrumentedLines"),
              IR.string("push")),
          IR.string(data.getInstrumentedLinesAsHexString()))),
      IR.exprResult(IR.call(
          IR.getprop(
              IR.name("JSCompiler_lcov_fileNames"),
              IR.string("push")),
          IR.string(fileName)))).useSourceInfoIfMissingFromForTree(srcref);
  }

  /**
   * Instruments the JS code by inserting appropriate nodes into the AST. The
   * instrumentation logic is tuned to collect "line coverage" data only.
   */
  @Override
  public void visit(NodeTraversal traversal, Node node, Node parent) {
    // SCRIPT node is special - it is the root of the AST for code from a file.
    // Append code to declare and initialize structures used in instrumentation.
    if (node.isScript()) {
      String fileName = getFileName(traversal);
      if (instrumentationData.get(fileName) != null) {
        node.addChildToFront(newHeaderNode(traversal, node));
      }
      compiler.reportCodeChange();
      return;
    }

    // Don't instrument global statements
    if (reach == CoverageReach.CONDITIONAL
        && parent != null && parent.isScript()) {
      return;
    }

    // For arrow functions whose body is an expression instead of a block,
    // convert it to a block so that it can be instrumented.
    if (node.isFunction() && !NodeUtil.getFunctionBody(node).isBlock()) {
      Node returnValue = NodeUtil.getFunctionBody(node);
      Node body = IR.block(IR.returnNode(returnValue.detachFromParent()));
      body.useSourceInfoIfMissingFromForTree(returnValue);
      node.addChildToBack(body);
    }

    // Add instrumentation code just before a function block.
    // Similarly before other constructs: 'with', 'case', 'default', 'catch'
    if (node.isFunction()
        || node.isWith()
        || node.isCase()
        || node.isDefaultCase()
        || node.isCatch()) {
      Node codeBlock = node.getLastChild();
      codeBlock.addChildToFront(
          newInstrumentationNode(traversal, node));
      compiler.reportCodeChange();
      return;
    }

    // Add instrumentation code as the first child of a 'try' block.
    if (node.isTry()) {
      Node firstChild = node.getFirstChild();
      firstChild.addChildToFront(
          newInstrumentationNode(traversal, node));
      compiler.reportCodeChange();
      return;
    }

    // For any other statement, add instrumentation code just before it.
    if (parent != null && NodeUtil.isStatementBlock(parent)) {
      parent.addChildBefore(
          newInstrumentationNode(traversal, node),
          node);
      compiler.reportCodeChange();
      return;
    }
  }
}
