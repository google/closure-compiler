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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Creates synthetic blocks to optimizations from moving code past markers in the source.
 */
class CreateSyntheticBlocks extends AbstractPostOrderCallback implements CompilerPass {
  static final DiagnosticType UNMATCHED_START_MARKER = DiagnosticType.error(
      "JSC_UNMATCHED_START_MARKER", "Unmatched {0}");

  static final DiagnosticType UNMATCHED_END_MARKER = DiagnosticType.error(
      "JSC_UNMATCHED_END_MARKER", "Unmatched {1} - {0} not in the same block");

  static final DiagnosticType INVALID_MARKER_USAGE = DiagnosticType.error(
      "JSC_INVALID_MARKER_USAGE", "Marker {0} can only be used in a simple call expression");

  private final AbstractCompiler compiler;

  /** Name of the start marker. */
  private final String startMarkerName;

  /** Name of the end marker. */
  private final String endMarkerName;

  /**
   * Markers can be nested.
   */
  private final Deque<Node> markerStack = new ArrayDeque<>();

  private final List<Marker> validMarkers = new ArrayList<>();

  private static class Marker {
    final Node startMarker;
    final Node endMarker;
    public Marker(Node startMarker, Node endMarker) {
      this.startMarker = startMarker;
      this.endMarker = endMarker;
    }
  }

  public CreateSyntheticBlocks(AbstractCompiler compiler,
      String startMarkerName, String endMarkerName) {
    this.compiler = compiler;
    this.startMarkerName = startMarkerName;
    this.endMarkerName = endMarkerName;
  }

  @Override
  public void process(Node externs, Node root) {
    // Find and validate the markers.
    NodeTraversal.traverse(compiler, root, this);

    // Complain about any unmatched markers.
    for (Node node : markerStack) {
      compiler.report(JSError.make(node, UNMATCHED_START_MARKER, startMarkerName));
    }

    // Add the block for the valid marker sets.
    for (Marker marker : validMarkers) {
      addBlocks(marker);
    }
  }

  /**
   * @param marker The marker to add synthetic blocks for.
   */
  private void addBlocks(Marker marker) {
    // Add block around the template section so that
    //   START
    //   BODY
    //   END
    // is transformed to:
    //   BLOCK (synthetic)
    //     START
    //       BLOCK (synthetic)
    //         BODY
    //     END
    // This prevents the start or end markers from mingling with the code in the block body.


    Node originalParent = marker.endMarker.getParent();
    Node outerBlock = IR.block();
    outerBlock.setIsSyntheticBlock(true);
    originalParent.addChildBefore(outerBlock, marker.startMarker);

    Node innerBlock = IR.block();
    innerBlock.setIsSyntheticBlock(true);
    // Move everything after the start Node up to the end Node into the inner block.
    moveSiblingExclusive(innerBlock, marker.startMarker, marker.endMarker);

    // Add the start node.
    outerBlock.addChildToBack(outerBlock.getNext().detach());
    // Add the inner block
    outerBlock.addChildToBack(innerBlock);
    // and finally the end node.
    outerBlock.addChildToBack(outerBlock.getNext().detach());

    compiler.reportChangeToEnclosingScope(outerBlock);
  }

  /**
   * Move the Nodes between start and end from the source block to the
   * destination block. If start is null, move the first child of the block.
   * If end is null, move the last child of the block.
   */
  private void moveSiblingExclusive(Node dest, Node start, Node end) {
    checkNotNull(start);
    checkNotNull(end);
    while (start.getNext() != end) {
      Node child = start.getNext().detach();
      dest.addChildToBack(child);
    }
  }

  @Override
  public void visit(NodeTraversal unused, Node n, Node parent) {
    if (!n.isCall() || !n.getFirstChild().isName()) {
      return;
    }

    Node callTarget = n.getFirstChild();
    String callName = callTarget.getString();

    if (startMarkerName.equals(callName)) {
      if (!parent.isExprResult()) {
        compiler.report(JSError.make(n, INVALID_MARKER_USAGE, startMarkerName));
        return;
      }
      markerStack.push(parent);
      return;
    }

    if (!endMarkerName.equals(callName)) {
      return;
    }

    Node endMarkerNode = parent;
    if (!endMarkerNode.isExprResult()) {
      compiler.report(JSError.make(n, INVALID_MARKER_USAGE, endMarkerName));
      return;
    }

    if (markerStack.isEmpty()) {
      compiler.report(JSError.make(n, UNMATCHED_END_MARKER, startMarkerName, endMarkerName));
      return;
    }

    Node startMarkerNode = markerStack.pop();
    if (endMarkerNode.getParent() != startMarkerNode.getParent()) {
      // The end marker isn't in the same block as the start marker.
      compiler.report(JSError.make(n, UNMATCHED_END_MARKER, startMarkerName, endMarkerName));
      return;
    }

    // This is a valid marker set add it to the list of markers to process.
    validMarkers.add(new Marker(startMarkerNode, endMarkerNode));
  }
}
