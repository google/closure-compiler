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
 * Visitor that performs naming operations on anonymous function by
 * means of the FunctionNamer interface.  Anonymous functions are
 * named based on context.  For example, the anonymous function below
 * would be given a name generated from goog.string.htmlEscape by the FunctionNamer.
 *
 * goog.string.htmlEscape = function(str) {
 * }
 *
 * This pass does not try to name FUNCTIONs with empty NAME nodes if doing so would violate AST
 * validity. Currently, we can never name arrow functions, which must stay anonymous, or getters,
 * setters, and member function definitions, which have a name elsewhere in the AST.
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
     * Sets the name of an anonymous function. Will only ever be called if the fnNode can be named
     * without making the AST invalid.
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
    switch (n.getToken()) {
      case FUNCTION:
        // this handles functions that are assigned to variables or
        // properties
        // e.g. goog.string.htmlEscape = function(str) {
        // }

        // get the function name and see if it's empty
        Node functionNameNode = n.getFirstChild();
        String functionName = functionNameNode.getString();
        if (functionName.isEmpty() && !n.isArrowFunction()) {
          if (parent.isAssign() || parent.isDefaultValue()) {
            // this is an assignment to a property, generally either a
            // static function or a prototype function, or a potential assignment of
            // a default value
            // e.g. goog.string.htmlEscape = function() { }
            //      goog.structs.Map.prototype.getCount = function() { } or
            //      function f(g = function() {}) { }
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
      case ASSIGN:
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
        break;
      default:
        break;
    }
  }

  private void nameObjectLiteralMethods(Node objectLiteral, String context) {
    for (Node keyNode = objectLiteral.getFirstChild();
         keyNode != null;
         keyNode = keyNode.getNext()) {

      // Object literal keys may be STRING_KEY, GETTER_DEF, SETTER_DEF,
      // MEMBER_FUNCTION_DEF (Shorthand function definition) or COMPUTED_PROP.
      // Getters, setters, and member function defs are skipped because their FUNCTION nodes must
      // have empty NAME nodes (currently enforced by CodeGenerator).
      if (keyNode.isStringKey() || keyNode.isComputedProp()) {
        // concatenate the context and key name to get a new qualified name.
        String name = namer.getCombinedName(context, namer.getName(keyNode));

        // computed property has 2 children -- index expression and value expression
        Node valueNode = keyNode.isStringKey() ? keyNode.getOnlyChild() : keyNode.getLastChild();

        Token type = valueNode.getToken();
        if (type == Token.FUNCTION && !valueNode.isArrowFunction()) {
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
