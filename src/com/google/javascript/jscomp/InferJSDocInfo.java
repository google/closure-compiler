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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;

import javax.annotation.Nullable;

/**
 * Set the JSDocInfo on all types.
 *
 * Propagates JSDoc across the type graph, but not across the symbol graph.
 * This means that if you have:
 * <code>
 * var x = new Foo();
 * x.bar;
 * </code>
 * then the JSType attached to x.bar may get associated JSDoc, but the
 * Node and Var will not.
 *
 * JSDoc is initially attached to AST Nodes at parse time.
 * There are 3 ways that JSDoc get propagated across the type system.
 * 1) Nominal types (e.g., constructors) may contain JSDocInfo for their
 *    declaration.
 * 2) Object types have a JSDocInfo slot for each property on that type.
 * 3) Shape types (like structural functions) may have JSDocInfo.
 *
 * #1 and #2 should be self-explanatory, and non-controversial. #3 is
 * a bit trickier. It means that if you have:
 * <code>
 * /** @param {number} x /
 * Foo.prototype.bar = goog.abstractMethod;
 * </code>
 * the JSDocInfo will appear in two places in the type system: in the 'bar'
 * slot of Foo.prototype, and on the function expression type created by
 * this expression.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class InferJSDocInfo extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  private final AbstractCompiler compiler;

  InferJSDocInfo(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    if (externs != null) {
      NodeTraversal.traverse(compiler, externs, this);
    }
    if (root != null) {
      NodeTraversal.traverse(compiler, root, this);
    }
  }

  @Override
  public void hotSwapScript(Node root, Node originalRoot) {
    Preconditions.checkNotNull(root);
    Preconditions.checkState(root.isScript());
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    JSDocInfo docInfo;

    switch (n.getType()) {
      // Infer JSDocInfo on types of all type declarations on variables.
      case Token.NAME:
        if (parent == null) {
          return;
        }

        // Only allow JSDoc on VARs, function declarations, and assigns.
        if (!parent.isVar() &&
            !NodeUtil.isFunctionDeclaration(parent) &&
            !(parent.isAssign() &&
              n == parent.getFirstChild())) {
          return;
        }

        // There are four places the doc info could live.
        // 1) A FUNCTION node.
        // /** ... */ function f() { ... }
        // 2) An ASSIGN parent.
        // /** ... */ x = function () { ... }
        // 3) A NAME parent.
        // var x, /** ... */ y = function() { ... }
        // 4) A VAR gramps.
        // /** ... */ var x = function() { ... }
        docInfo = n.getJSDocInfo();
        if (docInfo == null &&
            !(parent.isVar() &&
                !parent.hasOneChild())) {
          docInfo = parent.getJSDocInfo();
        }

        // Try to find the type of the NAME.
        JSType varType = n.getJSType();
        if (varType == null && parent.isFunction()) {
          varType = parent.getJSType();
        }

        // If we have no type to attach JSDocInfo to, then there's nothing
        // we can do.
        if (varType == null || docInfo == null) {
          return;
        }

        // Dereference the type. If the result is not an object, or already
        // has docs attached, then do nothing.
        ObjectType objType = dereferenceToObject(varType);
        if (objType == null || objType.getJSDocInfo() != null) {
          return;
        }

        attachJSDocInfoToNominalTypeOrShape(objType, docInfo, n.getString());
        break;

      case Token.STRING_KEY:
      case Token.GETTER_DEF:
      case Token.SETTER_DEF:
        docInfo = n.getJSDocInfo();
        if (docInfo == null) {
          return;
        }
        ObjectType owningType = dereferenceToObject(parent.getJSType());
        if (owningType != null) {
          String propName = n.getString();
          if (owningType.hasOwnProperty(propName)) {
            owningType.setPropertyJSDocInfo(propName, docInfo);
          }
        }
        break;

      case Token.GETPROP:
        // Infer JSDocInfo on properties.
        // There are two ways to write doc comments on a property.
        //
        // 1)
        // /** @deprecated */
        // obj.prop = ...
        //
        // 2)
        // /** @deprecated */
        // obj.prop;
        if (parent.isExprResult() ||
            (parent.isAssign() &&
             parent.getFirstChild() == n)) {
          docInfo = n.getJSDocInfo();
          if (docInfo == null) {
            docInfo = parent.getJSDocInfo();
          }
          if (docInfo != null) {
            ObjectType lhsType =
                dereferenceToObject(n.getFirstChild().getJSType());
            if (lhsType != null) {
              // Put the JSDoc in the property slot, if there is one.
              String propName = n.getLastChild().getString();
              if (lhsType.hasOwnProperty(propName)) {
                lhsType.setPropertyJSDocInfo(propName, docInfo);
              }

              // Put the JSDoc in any constructors or function shapes as well.
              ObjectType propType =
                  dereferenceToObject(lhsType.getPropertyType(propName));
              if (propType != null) {
                attachJSDocInfoToNominalTypeOrShape(
                    propType, docInfo, n.getQualifiedName());
              }
            }
          }
        }
        break;
    }
  }

  /**
   * Dereferences the given type to an object, or returns null.
   */
  private static ObjectType dereferenceToObject(JSType type) {
    return ObjectType.cast(type == null ? null : type.dereference());
  }

  /**
   * Handle cases #1 and #3 in the class doc.
   */
  private static void attachJSDocInfoToNominalTypeOrShape(
      ObjectType objType, JSDocInfo docInfo, @Nullable String qName) {
    if (objType.isConstructor() ||
        objType.isEnumType() ||
        objType.isInterface()) {
      // Named types.
      if (objType.hasReferenceName() &&
          objType.getReferenceName().equals(qName)) {
        objType.setJSDocInfo(docInfo);

        if (objType.isConstructor() || objType.isInterface()) {
          JSType.toMaybeFunctionType(objType).getInstanceType().setJSDocInfo(
              docInfo);
        } else if (objType instanceof EnumType) {
          ((EnumType) objType).getElementsType().setJSDocInfo(docInfo);
        }
      }
    } else if (!objType.isNativeObjectType() &&
        objType.isFunctionType()) {
      // Structural functions.
      objType.setJSDocInfo(docInfo);
    }
  }
}
