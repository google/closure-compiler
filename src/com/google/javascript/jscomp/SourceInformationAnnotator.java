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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.rhino.Node;
import javax.annotation.Nullable;

/**
 * Annotates nodes with information from their original input file before the compiler performs work
 * that changes this information (such as its original location, its original name, etc).
 *
 * <p>Information saved:
 *
 * <ul>
 *   <li>Annotates all NAME nodes with an ORIGINALNAME_PROP indicating its original name.
 *   <li>Annotates all GETPROP and OPTCHAIN_GETPROP nodes with an original name.
 *   <li>Annotates all OBJECT_LITERAL unquoted string key nodes with an original name.
 *   <li>Annotates all FUNCTION nodes with an original name indicating its nearest original name.
 * </ul>
 */
final class SourceInformationAnnotator extends NodeTraversal.AbstractPostOrderCallback {
  @Nullable private final String sourceFileToCheck;

  private SourceInformationAnnotator(String sourceFileToCheck) {
    this.sourceFileToCheck = sourceFileToCheck;
  }

  /** Returns an annotator that sets the original name of nodes */
  static SourceInformationAnnotator create() {
    return new SourceInformationAnnotator(null);
  }

  /**
   * Returns an annotator that both sets original name and verifies {@link Node#getSourceFileName}
   * is accurate
   */
  static SourceInformationAnnotator createWithAnnotationChecks(String sourceFile) {
    return new SourceInformationAnnotator(sourceFile);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    // Verify the source file is annotated.
    if (sourceFileToCheck != null) {
      checkState(sourceFileToCheck.equals(n.getSourceFileName()));
    }

    // Annotate the original name.
    switch (n.getToken()) {
      case FUNCTION:
        String functionName = NodeUtil.getNearestFunctionName(n);
        if (functionName != null) {
          setOriginalName(n, functionName);
        }
        break;

      case GETPROP:
      case OPTCHAIN_GETPROP:
      case NAME:
        setOriginalName(n, n.getString());
        break;

      case OBJECTLIT:
        for (Node key = n.getFirstChild(); key != null; key = key.getNext()) {
          // Set the original name for unquoted string properties.
          if (!key.isComputedProp() && !key.isQuotedString() && !key.isSpread()) {
            setOriginalName(key, key.getString());
          }
        }
        break;
      default:
        break;
    }
  }

  private static void setOriginalName(Node n, String name) {
    if (!name.isEmpty() && n.getOriginalName() == null) {
      n.setOriginalName(name);
    }
  }
}
