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

/**
 * Annotates nodes with information from their original input file
 * before the compiler performs work that changes this information (such
 * as its original location, its original name, etc).
 *
 * Information saved:
 *
 * - Annotates all NAME nodes with an ORIGINALNAME_PROP indicating its original
 *   name.
 *
 * - Annotates all string GET_PROP nodes with an ORIGINALNAME_PROP.
 *
 * - Annotates all OBJECT_LITERAL unquoted string key nodes with an
 *   ORIGINALNAME_PROP.
 *
 * - Annotates all FUNCTION nodes with an ORIGINALNAME_PROP indicating its
 *   nearest original name.
 */
class SourceInformationAnnotator extends
  NodeTraversal.AbstractPostOrderCallback {
  private final String sourceFile;
  private final boolean checkAnnotated;

  public SourceInformationAnnotator(
      String sourceFile, boolean checkAnnotated) {
    this.sourceFile = sourceFile;
    this.checkAnnotated = checkAnnotated;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    // Verify the source file is annotated.
    if (checkAnnotated && sourceFile != null) {
      checkState(sourceFile.equals(n.getSourceFileName()));
    }

    // Annotate the original name.
    switch (n.getToken()) {
      case GETPROP:
        Node propNode = n.getLastChild();
        setOriginalName(n, propNode.getString());
        break;

      case FUNCTION:
        String functionName = NodeUtil.getNearestFunctionName(n);
        if (functionName != null) {
          setOriginalName(n, functionName);
        }
        break;

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

  static void setOriginalName(Node n, String name) {
    if (!name.isEmpty() && n.getOriginalName() == null) {
      n.setOriginalName(name);
    }
  }
}
