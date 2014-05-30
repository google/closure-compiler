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

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

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
 *
 */
class SourceInformationAnnotator extends
  NodeTraversal.AbstractPostOrderCallback {
  private final String sourceFile;
  private final boolean doSanityChecks;

  public SourceInformationAnnotator(
      String sourceFile, boolean doSanityChecks) {
    this.sourceFile = sourceFile;
    this.doSanityChecks = doSanityChecks;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    // Verify the source file is annotated.
    if (doSanityChecks && sourceFile != null) {
      Preconditions.checkState(sourceFile.equals(
          n.getSourceFileName()));
    }

    // Annotate the original name.
    switch (n.getType()) {
      case Token.GETPROP:
        Node propNode = n.getLastChild();
        setOriginalName(n, propNode.getString());
        break;

      case Token.FUNCTION:
        String functionName = NodeUtil.getNearestFunctionName(n);
        if (functionName != null) {
          setOriginalName(n, functionName);
        }
        break;

      case Token.NAME:
        setOriginalName(n, n.getString());
        break;

      case Token.OBJECTLIT:
        for (Node key = n.getFirstChild(); key != null;
             key = key.getNext()) {
           // We only want keys were unquoted.
           if (!key.isComputedProp() && !key.isQuotedString()) {
             setOriginalName(key, key.getString());
           }
         }
        break;
    }
  }

  static void setOriginalName(Node n, String name) {
    if (!name.isEmpty() && n.getProp(Node.ORIGINALNAME_PROP) == null) {
      n.putProp(Node.ORIGINALNAME_PROP, name);
    }
  }
}
