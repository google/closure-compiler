/*
 * Copyright 2016 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.instrumentation;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.GwtIncompatible;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.ControlFlowGraph;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Instrument branch coverage for javascript. */
@GwtIncompatible("FileInstrumentationData")
public class BranchCoverageInstrumentationCallback extends NodeTraversal.AbstractCfgCallback {
  private final AbstractCompiler compiler;
  private final Map<String, FileInstrumentationData> instrumentationData;

  private static final String BRANCH_ARRAY_NAME_PREFIX = "JSCompiler_lcov_branch_data_";

  /** Returns a string that can be used for the branch coverage data. */
  private static String createArrayName(NodeTraversal traversal) {
    return BRANCH_ARRAY_NAME_PREFIX
        + CoverageUtil.createIdentifierFromText(traversal.getSourceName());
  }

  public BranchCoverageInstrumentationCallback(
      AbstractCompiler compiler, Map<String, FileInstrumentationData> instrumentationData) {
    this.compiler = compiler;
    this.instrumentationData = instrumentationData;
  }

  @Override
  public void visit(NodeTraversal traversal, Node node, Node parent) {
    String fileName = traversal.getSourceName();

    // If origin of node is not from sourceFile, do not instrument. This typically occurs when
    // polyfill code is injected into the sourceFile AST and this check avoids instrumenting it. We
    // avoid instrumentation as this callback does not distinguish between sourceFile code and
    // injected code and can result in an error.
    if (!Objects.equals(fileName, node.getSourceFileName())) {
      return;
    }

    if (node.isScript()) {
      if (instrumentationData.get(fileName) != null) {
        Node toAddTo =
            node.hasChildren() && node.getFirstChild().isModuleBody() ? node.getFirstChild() : node;
        // Add instrumentation code
        toAddTo.addChildrenToFront(newHeaderNode(traversal, toAddTo).removeChildren());
        compiler.reportChangeToEnclosingScope(node);
        instrumentBranchCoverage(traversal, instrumentationData.get(fileName));
      }
    }

    if (node.isIf()) {
      ControlFlowGraph<Node> cfg = getControlFlowGraph(compiler);
      boolean hasDefaultBlock = false;
      for (DiGraph.DiGraphEdge<Node, ControlFlowGraph.Branch> outEdge : cfg.getOutEdges(node)) {
        if (outEdge.getValue() == ControlFlowGraph.Branch.ON_FALSE) {
          Node destination = outEdge.getDestination().getValue();
          if (destination != null
              && destination.isBlock()
              && destination.getParent() != null
              && destination.getParent().isIf()) {
            hasDefaultBlock = true;
          }
          break;
        }
      }
      if (!hasDefaultBlock) {
        addDefaultBlock(node);
      }
      instrumentationData.computeIfAbsent(
          fileName, (String k) -> new FileInstrumentationData(k, createArrayName(traversal)));
      processBranchInfo(node, instrumentationData.get(fileName), getChildrenBlocks(node));
    } else if (NodeUtil.isLoopStructure(node)) {
      List<Node> blocks = getChildrenBlocks(node);
      ControlFlowGraph<Node> cfg = getControlFlowGraph(compiler);
      for (DiGraph.DiGraphEdge<Node, ControlFlowGraph.Branch> outEdge : cfg.getOutEdges(node)) {
        if (outEdge.getValue() == ControlFlowGraph.Branch.ON_FALSE) {
          Node destination = outEdge.getDestination().getValue();
          if (destination != null && destination.isBlock()) {
            blocks.add(destination);
          } else {
            Node exitBlock = IR.block();
            if (destination != null && destination.getParent().isBlock()) {
              // When the destination of an outEdge of the CFG is not null and the source node's
              // parent is a block. If parent is not a block it may result in an illegal state
              // exception
              exitBlock.insertBefore(destination);
            } else {
              // When the destination of an outEdge of the CFG is null then we need to add an empty
              // block directly after the loop structure that we can later instrument
              exitBlock.insertAfter(outEdge.getSource().getValue());
            }
            blocks.add(exitBlock);
          }
        }
      }
      instrumentationData.computeIfAbsent(
          fileName, (String k) -> new FileInstrumentationData(k, createArrayName(traversal)));
      processBranchInfo(node, instrumentationData.get(fileName), blocks);
    }
  }

  private List<Node> getChildrenBlocks(Node node) {
    List<Node> blocks = new ArrayList<>();
    for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
      if (child.isBlock()) {
        blocks.add(child);
      }
    }
    return blocks;
  }

  /**
   * Add instrumentation code for branch coverage. For each block that correspond to a branch,
   * insert an assignment of the branch coverage data to the front of the block.
   */
  private void instrumentBranchCoverage(NodeTraversal traversal, FileInstrumentationData data) {
    int maxLine = data.maxBranchPresentLine();
    int branchCoverageOffset = 0;
    for (int lineIdx = 1; lineIdx <= maxLine; ++lineIdx) {
      Integer numBranches = data.getNumBranches(lineIdx);
      if (numBranches != null) {
        for (int branchIdx = 1; branchIdx <= numBranches; ++branchIdx) {
          Node block = data.getBranchNode(lineIdx, branchIdx);
          block.addChildToFront(
              newBranchInstrumentationNode(traversal, block, branchCoverageOffset + branchIdx - 1));
          compiler.reportChangeToEnclosingScope(block);
        }
        branchCoverageOffset += numBranches;
      }
    }
  }

  /**
   * Create an assignment to the branch coverage data for the given index into the array.
   *
   * @return the newly constructed assignment node.
   */
  private Node newBranchInstrumentationNode(NodeTraversal traversal, Node node, int idx) {
    String arrayName = createArrayName(traversal);

    // Create instrumentation Node
    Node getElemNode = IR.getelem(IR.name(arrayName), IR.number(idx)); // Make line number 0-based
    Node exprNode = IR.exprResult(IR.assign(getElemNode, IR.trueNode()));

    // Note line as instrumented
    String fileName = traversal.getSourceName();
    instrumentationData.computeIfAbsent(
        fileName, (String k) -> new FileInstrumentationData(k, arrayName));
    return exprNode.srcrefTreeIfMissing(node);
  }

  /** Add branch instrumentation information for each block. */
  private void processBranchInfo(Node branchNode, FileInstrumentationData data, List<Node> blocks) {
    int lineNumber = branchNode.getLineno();
    data.setBranchPresent(lineNumber);

    // Instrument for each block
    int numBranches = 0;
    for (Node child : blocks) {
      data.putBranchNode(lineNumber, numBranches + 1, child);
      numBranches++;
    }
    data.addBranches(lineNumber, numBranches);
  }

  /** Add a default block for conditional statements, e.g., If, Switch. */
  private Node addDefaultBlock(Node node) {
    Node defaultBlock = IR.block();
    node.addChildToBack(defaultBlock);
    return defaultBlock.srcrefTreeIfMissing(node);
  }

  private Node newHeaderNode(NodeTraversal traversal, Node srcref) {
    String fileName = traversal.getSourceName();
    FileInstrumentationData data = instrumentationData.get(fileName);
    checkNotNull(data);

    // var JSCompiler_lcov_branch_data_xx = [];
    // __jscov['branchesTaken'].push(JSCompiler_lcov_branch_data_xx);
    String objName = CoverageInstrumentationPass.JS_INSTRUMENTATION_OBJECT_NAME;
    List<Node> nodes = new ArrayList<>();
    nodes.add(newArrayDeclarationNode(traversal));
    nodes.add(
        IR.exprResult(
            IR.call(
                IR.getprop(IR.getelem(IR.name(objName), IR.string("branchesTaken")), "push"),
                IR.name(createArrayName(traversal)))));
    // __jscov['branchPresent'].push(hex-data);
    nodes.add(
        IR.exprResult(
            IR.call(
                IR.getprop(IR.getelem(IR.name(objName), IR.string("branchPresent")), "push"),
                IR.string(data.getBranchPresentAsHexString()))));
    nodes.add(newBranchesInLineNode("JSCompiler_lcov_branchesInLine", data));
    // __jscov['branchesInLine'].push(JSCompiler_lcov_branchesInLine);
    nodes.add(
        IR.exprResult(
            IR.call(
                IR.getprop(IR.getelem(IR.name(objName), IR.string("branchesInLine")), "push"),
                IR.name("JSCompiler_lcov_branchesInLine"))));
    // __jscov['fileNames'].push(filename);
    nodes.add(
        IR.exprResult(
            IR.call(
                IR.getprop(IR.getelem(IR.name(objName), IR.string("fileNames")), "push"),
                IR.string(fileName))));
    return IR.block(nodes).srcrefTreeIfMissing(srcref);
  }

  private Node newArrayDeclarationNode(NodeTraversal traversal) {
    return IR.var(IR.name(createArrayName(traversal)), IR.arraylit());
  }

  private Node newBranchesInLineNode(String name, FileInstrumentationData data) {
    List<Node> assignments = new ArrayList<>();
    // var JSCompiler_lcov_branchesInLine = [];
    assignments.add(IR.var(IR.name(name), IR.arraylit()));
    int lineWithBranch = 0;
    for (int lineIdx = 1; lineIdx <= data.maxBranchPresentLine(); ++lineIdx) {
      Integer numBranches = data.getNumBranches(lineIdx);
      if (numBranches != null && numBranches > 0) {
        // JSCompiler_lcov_branchesInLine[<branch-index>] = 2;
        Node assignment =
            IR.exprResult(
                IR.assign(
                    IR.getelem(IR.name(name), IR.number(lineWithBranch++)),
                    IR.number(numBranches)));
        assignments.add(assignment.srcrefTreeIfMissing(assignment));
      }
    }
    return IR.block(assignments);
  }
}
