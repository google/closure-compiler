/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;

/**
 * A pass that looks for consecutive array.push() calls and combines
 * them together.
 * 
 * e.g. array.push(1);array.push(2); is optimized to array.push(1,2);
 *
 * @author cshung@gmail.com (Andrew Au)
 */
final class PeepholeCombineArrayPush extends AbstractPeepholeOptimization {

  @Override
  Node optimizeSubtree(Node subtree) {
    if (!subtree.isScript() && !subtree.isBlock()) {
      return subtree;
    }

    boolean codeChanged = false;
    for (Node statement = subtree.getFirstChild(); statement != null; statement = statement.getNext()) {
      ArrayDotPushStatementSequence arrayDotPushStatementSequence = asArrayDotPushStatementSequence(statement);
      if (arrayDotPushStatementSequence != null) {
        arrayDotPushStatementSequence.combine(subtree);
        codeChanged = true;
      }
    }
    if (codeChanged) {
      reportChangeToEnclosingScope(subtree);
    }
    return subtree;
  }

  private ArrayDotPushStatementSequence asArrayDotPushStatementSequence(Node statement) {
    ArrayDotPushStatementSequence arrayDotPushStatementSequence = new ArrayDotPushStatementSequence();
    if (isArrayDotPush(statement, true, arrayDotPushStatementSequence)) {
      Node nextStatement = statement.getNext();
      if (isArrayDotPush(nextStatement, false, arrayDotPushStatementSequence)) {
        if (arrayDotPushStatementSequence.firstArrayName.equals(arrayDotPushStatementSequence.secondArrayName)) {
          return arrayDotPushStatementSequence;
        }
      }
    }
    return null;
  }

  private boolean isArrayDotPush(Node statement, boolean first, ArrayDotPushStatementSequence arrayDotPushStatementSequence) {
    if (statement != null && statement.isExprResult()) {
      Node callNode = statement.getOnlyChild();
      if (callNode != null && callNode.isCall()) {
        Node arrayDotPushNode = callNode.getFirstChild();
        if (arrayDotPushNode != null && arrayDotPushNode.isGetProp()) {
          Node array = arrayDotPushNode.getFirstChild();
          if (array != null) {
            Node push = array.getNext();
            if (push != null) {
              JSType arrayJsType = array.getJSType();
              if (arrayJsType != null) {
                boolean result = (array.getToken() == Token.NAME && arrayJsType.isArrayType() && push.getString().equals("push"));
                if (result) {
                  if (first) {
                    arrayDotPushStatementSequence.firstCall = callNode;
                    arrayDotPushStatementSequence.firstArrayName = array.getString();
                  } else {
                    arrayDotPushStatementSequence.nextStatement = statement;
                    arrayDotPushStatementSequence.secondCall = callNode;
                    arrayDotPushStatementSequence.secondArrayName = array.getString();
                    arrayDotPushStatementSequence.secondArgument = arrayDotPushNode.getNext();
                  }
                }
                return result;
              }
            }
          }
        }
      }
    }
    return false;
  }

  private class ArrayDotPushStatementSequence {
    public Node firstCall;
    public String firstArrayName;
    public Node nextStatement;
    public Node secondCall;
    public String secondArrayName;
    public Node secondArgument;

    public void combine(Node subtree) {
      while (secondArgument != null) {
        Node temp = secondArgument.getNext();
        firstCall.addChildToBack(secondArgument.detach());
        secondArgument = temp;
      }
      subtree.removeChild(nextStatement);
    }
  }
}
