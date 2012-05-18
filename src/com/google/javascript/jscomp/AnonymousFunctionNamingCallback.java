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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;


/**
 * Visitor that performs naming operations on anonymous functions by
 * means of the FunctionNamer interface.  Anonymous functions are
 * named based on context.  For example, the anonymous function on the
 * RHS based on the property at the LHS of the assignment operator.
 *
 * goog.string.htmlEscape = function(str) {
 * }
 *
 */
class AnonymousFunctionNamingCallback
    extends AbstractPostOrderCallback {
  private final FunctionNamer namer;

  /**
   * Interface used by AnonymousFunctionNamingCallback to set the name
   * of anonymous functions.
   */
  interface FunctionNamer {
    /**
     * Generates a string representation of a node for use by
     * setFunctionName.
     */
    String getName(Node node);

    /**
     * Sets the name of an anonymous function.
     * @param fnNode The function node to update
     * @param name The name
     */
    void setFunctionName(String name, Node fnNode);

    /**
     * Generate a name by "concatenating" the output of multiple calls
     * to getName.
     */
    String getCombinedName(String lhs, String rhs);
  }

  AnonymousFunctionNamingCallback(FunctionNamer namer) {
    this.namer = namer;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.FUNCTION:
        // this handles functions that are assigned to variables or
        // properties
        // e.g. goog.string.htmlEscape = function(str) {
        // }

        // get the function name and see if it's empty
        Node functionNameNode = n.getFirstChild();
        String functionName = functionNameNode.getString();
        if (functionName.length() == 0) {
          if (parent.isAssign()) {
            // this is an assignment to a property, generally either a
            // static function or a prototype function
            // e.g. goog.string.htmlEscape = function() { } or
            //      goog.structs.Map.prototype.getCount = function() { }
            Node lhs = parent.getFirstChild();
            String name = namer.getName(lhs);
            namer.setFunctionName(name, n);
          } else if (parent.isName()) {
            // this is an assignment to a variable
            // e.g. var handler = function() {}
            String name = namer.getName(parent);
            namer.setFunctionName(name, n);
          }
        }
        break;
      case Token.ASSIGN:
        // this handles functions that are assigned to a prototype through
        // an object literal
        // e.g. BuzzApp.prototype = {
        //        Start : function() { }
        //      }
        Node lhs = n.getFirstChild();
        Node rhs = lhs.getNext();
        if (rhs.isObjectLit()) {
          nameObjectLiteralMethods(rhs, namer.getName(lhs));
        }
    }
  }

  private void nameObjectLiteralMethods(Node objectLiteral, String context) {
    for (Node keyNode = objectLiteral.getFirstChild();
         keyNode != null;
         keyNode = keyNode.getNext()) {
      Node valueNode = keyNode.getFirstChild();

      // Object literal keys may be STRING_KEY, GETTER_DEF, SETTER_DEF.
      // Get and Set are skipped because they can not be named.
      if (keyNode.isStringKey()) {
        // concatenate the context and key name to get a new qualified name.
        String name = namer.getCombinedName(context, namer.getName(keyNode));

        int type = valueNode.getType();
        if (type == Token.FUNCTION) {
          // set name if function is anonymous
          Node functionNameNode = valueNode.getFirstChild();
          String functionName = functionNameNode.getString();
          if (functionName.isEmpty()) {
            namer.setFunctionName(name, valueNode);
          }
        } else if (type == Token.OBJECTLIT) {
          // process nested object literal
          nameObjectLiteralMethods(valueNode, name);
        }
      }
    }
  }
}
