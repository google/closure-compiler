/*
 * Copyright 2004 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;

/**
 * Utility class that extracts the qualified name out of a node.
 * Useful when trying to get a human-friendly string representation of
 * a property node that can be used to describe the node or name
 * related nodes based on it (as done by the NameAnonymousFunctions
 * compiler pass).
 *
 */
class NodeNameExtractor {
  private final char delimiter;
  private int nextUniqueInt = 0;

  NodeNameExtractor(char delimiter) {
    this.delimiter = delimiter;
  }

  /**
   * Returns a qualified name of the specified node. Dots and brackets
   * are changed to the delimiter passed in when constructing the
   * NodeNameExtractor object.  We also replace ".prototype" with the
   * delimiter to keep names short, while still differentiating them
   * from static properties.  (Prototype properties will end up
   * looking like "a$b$$c" if this.delimiter = '$'.)
   */
  String getName(Node node) {
    switch (node.getType()) {
      case Token.CLASS:
      case Token.FUNCTION:
        return NodeUtil.getName(node);
      case Token.GETPROP:
        Node lhsOfDot = node.getFirstChild();
        Node rhsOfDot = lhsOfDot.getNext();
        String lhsOfDotName = getName(lhsOfDot);
        String rhsOfDotName = getName(rhsOfDot);
        if ("prototype".equals(rhsOfDotName)) {
          return lhsOfDotName + delimiter;
        } else {
          return lhsOfDotName + delimiter + rhsOfDotName;
        }
      case Token.GETELEM:
        Node outsideBrackets = node.getFirstChild();
        Node insideBrackets = outsideBrackets.getNext();
        String nameOutsideBrackets = getName(outsideBrackets);
        String nameInsideBrackets = getName(insideBrackets);
        if ("prototype".equals(nameInsideBrackets)) {
          return nameOutsideBrackets + delimiter;
        } else {
          return nameOutsideBrackets + delimiter + nameInsideBrackets;
        }
      case Token.NAME:
        return node.getString();
      case Token.STRING:
      case Token.STRING_KEY:
      case Token.MEMBER_FUNCTION_DEF:
        return TokenStream.isJSIdentifier(node.getString()) ?
            node.getString() : ("__" + nextUniqueInt++);
      case Token.NUMBER:
        return NodeUtil.getStringValue(node);
      case Token.THIS:
        return "this";
      case Token.CALL:
        return getName(node.getFirstChild());
      default:
        StringBuilder sb = new StringBuilder();
        for (Node child = node.getFirstChild(); child != null;
             child = child.getNext()) {
          if (sb.length() > 0) {
            sb.append(delimiter);
          }
          sb.append(getName(child));
        }
        return sb.toString();
    }
  }
}
