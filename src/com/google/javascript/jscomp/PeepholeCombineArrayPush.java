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

    for (Node child = subtree.getFirstChild(); child != null; child = child.getNext()) {
      if (isArrayDotPush(child)) {
        Node next = child.getNext();
        if (isArrayDotPush(next)) {
          Node firstCall = child.getFirstChild();
          Node secondCall = next.getFirstChild();
          String firstArrayName = firstCall.getFirstChild().getFirstChild().getString();
          String secondArrayName = secondCall.getFirstChild().getFirstChild().getString();
          if (firstArrayName.equals(secondArrayName)) {
            Node secondArgument = secondCall.getFirstChild().getNext();
            while (secondArgument != null) {
              Node temp = secondArgument.getNext();
              firstCall.addChildToBack(secondArgument.detach());
              secondArgument = temp;
            }
            subtree.removeChild(next);
            codeChanged = true;
          }
        }
      }
    }

    if (codeChanged) {
      reportChangeToEnclosingScope(subtree);
    }
    return subtree;
  }

  private boolean isArrayDotPush(Node child) {    
    if (child != null && child.getToken() == Token.EXPR_RESULT) {
      Node callNode = child.getFirstChild();
      if (callNode != null && callNode.getToken() == Token.CALL) {
        Node arrayDotPushNode = callNode.getFirstChild();
        if (arrayDotPushNode != null && arrayDotPushNode.getToken() == Token.GETPROP) {
          Node array = arrayDotPushNode.getFirstChild();
          if (array != null) {
            Node push = array.getNext();
            if (push != null) {
              JSType arrayJsType = array.getJSType();
              if (arrayJsType != null) {
                return (array.getToken() == Token.NAME && arrayJsType.isArrayType() && push.getString().equals("push"));
              }
            }
          }
        }
      }
    }
    return false;
  }
}
