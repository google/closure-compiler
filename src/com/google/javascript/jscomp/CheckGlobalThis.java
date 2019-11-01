/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Checks for certain uses of the {@code this} keyword that are considered
 * unsafe because they are likely to reference the global {@code this} object
 * unintentionally.
 *
 * <p>A use of {@code this} is considered unsafe if it's on the left side of an
 * assignment or a property access, and not inside one of the following:
 * <ol>
 * <li>a prototype method
 * <li>a function annotated with {@code @constructor}
 * <li>a function annotated with {@code @this}.
 * <li>a function where there's no logical place to put a
 *     {@code this} annotation.
 * </ol>
 *
 * <p>Note that this check does not track assignments of {@code this} to
 * variables or objects. The code
 * <pre>
 * function evil() {
 *   var a = this;
 *   a.useful = undefined;
 * }
 * </pre>
 * will not get flagged, even though it is semantically equivalent to
 * <pre>
 * function evil() {
 *   this.useful = undefined;
 * }
 * </pre>
 * which would get flagged.
 */
final class CheckGlobalThis implements Callback {

  static final DiagnosticType GLOBAL_THIS = DiagnosticType.warning(
      "JSC_USED_GLOBAL_THIS",
      "dangerous use of the global 'this' object");

  private final AbstractCompiler compiler;

  /**
   * If {@code assignLhsChild != null}, then the node being traversed is
   * a descendant of the first child of an ASSIGN node. assignLhsChild's
   * parent is this ASSIGN node.
   */
  private Node assignLhsChild = null;

  CheckGlobalThis(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  /**
   * Since this pass reports errors only when a global {@code this} keyword
   * is encountered, there is no reason to traverse non global contexts.
   */
  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {

    if (n.isFunction()) {
      // Arrow functions automatically get the "this" value from the
      // enclosing scope. e.g. the "this" in
      //   Foo.prototype.getBar = () => this.bar;
      // is the global "this", not an instance of Foo.
      if (n.isArrowFunction()) {
        return true;
      }

      // Don't traverse functions that are constructors or have the @this
      // or @override annotation.
      JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(n);
      if (jsDoc != null &&
          (jsDoc.isConstructor() ||
           jsDoc.isInterface() ||
           jsDoc.hasThisType() ||
           jsDoc.isOverride())) {
        return false;
      }

      if (jsDoc != null) {
        JSTypeExpression functionType = jsDoc.getType();
        if (functionType != null) {
          Node functionNode = functionType.getRoot();
          if (functionNode != null && functionNode.isFunction()) {
            // function(this: ThisType, ...)
            // `this:` is only allowed as the very first child of the
            // FUNCTION node.
            Node thisNode = functionNode.getFirstChild();
            if (thisNode != null && thisNode.isThis()) {
              // Type of `this` is specified, so no need to check further.
              return false;
            }
          }
        }
      }

      // Don't traverse functions unless they would normally
      // be able to have a @this annotation associated with them. e.g.,
      // var a = function() { }; // or
      // function a() {} // or
      // a.x = function() {}; // or
      // var a = {x: function() {}};
      Token pType = parent.getToken();
      if (!(pType == Token.BLOCK ||
            pType == Token.SCRIPT ||
            pType == Token.NAME ||
            pType == Token.ASSIGN ||

            // object literal keys
            pType == Token.STRING_KEY)) {
        return false;
      }

      // Don't traverse functions that are getting lent to a prototype.
      Node grandparent = parent.getParent();
      if (NodeUtil.mayBeObjectLitKey(parent)) {
        JSDocInfo maybeLends = grandparent.getJSDocInfo();
        if (maybeLends != null
            && maybeLends.hasLendsName()
            && maybeLends.getLendsName().getRoot().getString().endsWith(".prototype")) {
          return false;
        }
      }
    }

    if (parent != null && parent.isAssign()) {
      Node lhs = parent.getFirstChild();

      if (n == lhs) {
        // Always traverse the left side of the assignment. To handle
        // nested assignments properly (e.g., (a = this).property = c;),
        // assignLhsChild should not be overridden.
        if (assignLhsChild == null) {
          assignLhsChild = lhs;
        }
      } else {
        // Only traverse the right side if it's not an assignment to a prototype
        // property or subproperty.
        if (NodeUtil.isGet(lhs)) {
          if (lhs.isGetProp() &&
              lhs.getLastChild().getString().equals("prototype")) {
            return false;
          }
          Node llhs = lhs.getFirstChild();
          if (llhs.isGetProp() &&
              llhs.getLastChild().getString().equals("prototype")) {
            return false;
          }
        }
      }
    }

    return true;
  }

  @Override
  public void visit(NodeTraversal unused, Node n, Node parent) {
    if (n.isThis() && shouldReportThis(n)) {
      compiler.report(JSError.make(n, GLOBAL_THIS));
    }
    if (n == assignLhsChild) {
      assignLhsChild = null;
    }
  }

  private boolean shouldReportThis(Node n) {
    Node parent = n.getParent();
    if (assignLhsChild != null) {
      // Always report a THIS on the left side of an assign.
      return true;
    }

    // Also report a THIS with a property access.
    return parent != null && NodeUtil.isGet(parent);
  }
}
