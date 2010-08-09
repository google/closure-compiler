/*
 * Copyright 2009 Google Inc.
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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Creates synthetic blocks to prevent {@link FoldConstants} from moving code
 * past markers in the source.
 *
 *
 */
class CreateSyntheticBlocks implements CompilerPass {
  static final DiagnosticType UNMATCHED_START_MARKER = DiagnosticType.warning(
      "JSC_UNMATCHED_START_MARKER", "Unmatched {0}");

  static final DiagnosticType UNMATCHED_END_MARKER = DiagnosticType.warning(
      "JSC_UNMATCHED_END_MARKER", "Unmatched {1} - {0} not in the same block");

  private static class StartMarker {
    /** Records the source name for errors about unmatched start markers. */
    final String sourceName;

    /** Records the node for errors about unmatched start markers. */
    final Node node;

    /**
     * Records the ancestor block's child for insertion of a synthetic block.
     */
    final Node ancestorBlockChild;

    /** Records the ancestor block for insertion of a synthetic block. */
    final Node ancestorBlock;

    private StartMarker(String sourceName, Node n, Node ancestorBlockChild,
        Node ancestorBlock) {
      this.sourceName = sourceName;
      node = n;
      this.ancestorBlockChild = ancestorBlockChild;
      this.ancestorBlock = ancestorBlock;
    }
  }

  private final AbstractCompiler compiler;

  /** Name of the start marker. */
  private final String startMarkerName;

  /** Name of the end marker. */
  private final String endMarkerName;

  /**
   * Markers can be nested.
   */
  private Deque<StartMarker> startMarkerStack;

  public CreateSyntheticBlocks(AbstractCompiler compiler,
      String startMarkerName, String endMarkerName) {
    this.compiler = compiler;
    this.startMarkerName = startMarkerName;
    this.endMarkerName = endMarkerName;
    startMarkerStack = new ArrayDeque<StartMarker>();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new Callback());

    for (StartMarker startMarker : startMarkerStack) {
      compiler.report(JSError.make(startMarker.sourceName, startMarker.node,
          UNMATCHED_START_MARKER, startMarkerName));
    }
  }

  private class Callback extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getType() != Token.NAME || parent.getType() != Token.CALL) {
        return;
      }

      if (startMarkerName.equals(n.getString())) {
        // Record information needed for insertion of a synthetic block or
        // warning about an unmatched start marker.
        Node ancestorBlockChild = n;
        Node ancestorBlock = null;
        for (Node ancestor : n.getAncestors()) {
          int type = ancestor.getType();
          if (type == Token.SCRIPT || type == Token.BLOCK) {
            ancestorBlock = ancestor;
            break;
          }
          ancestorBlockChild = ancestor;
        }

        startMarkerStack.push(new StartMarker(t.getSourceName(), n,
            ancestorBlockChild, ancestorBlock));
      }

      if (!endMarkerName.equals(n.getString())) {
        return;
      }

      if (startMarkerStack.isEmpty()) {
        compiler.report(t.makeError(n, UNMATCHED_END_MARKER,
            startMarkerName, endMarkerName));
        return;
      }

      StartMarker startMarker = startMarkerStack.pop();

      Node endMarkerAncestorBlockChild = n;
      for (Node ancestor : n.getAncestors()) {
        int type = ancestor.getType();
        if (type == Token.SCRIPT || type == Token.BLOCK) {
          if (ancestor != startMarker.ancestorBlock) {
            // The end marker isn't in the same block as the start marker.
            compiler.report(t.makeError(n, UNMATCHED_END_MARKER,
                startMarkerName, endMarkerName));
            return;
          }
          break;
        }
        endMarkerAncestorBlockChild = ancestor;
      }

      Node block = new Node(Token.BLOCK);
      block.setIsSyntheticBlock(true);
      startMarker.ancestorBlock.addChildAfter(block,
          startMarker.ancestorBlockChild);
      Node removedNode = null;
      do {
        // Move the nodes into the synthetic block.
        removedNode = startMarker.ancestorBlock.removeChildAfter(block);
        block.addChildToBack(removedNode);
      } while (removedNode != endMarkerAncestorBlockChild);

      compiler.reportCodeChange();
    }
  }
}
