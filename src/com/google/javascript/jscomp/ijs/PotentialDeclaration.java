/*
 * Copyright 2017 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.ijs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import javax.annotation.Nullable;

/**
 * Encapsulates something that could be a declaration.
 *
 * This includes:
 *   var/let/const declarations,
 *   function/class declarations,
 *   method declarations,
 *   assignments,
 *   goog.define calls,
 *   and even valueless property accesses (e.g. `/** @type {number} * / Foo.prototype.bar`)
 */
abstract class PotentialDeclaration {
  // The fully qualified name of the declaration.
  private final String fullyQualifiedName;
  // The LHS node of the declaration.
  private final Node lhs;
  // The RHS node of the declaration, if it exists.
  private final @Nullable Node rhs;

  private PotentialDeclaration(String fullyQualifiedName, Node lhs, @Nullable Node rhs) {
    this.fullyQualifiedName = checkNotNull(fullyQualifiedName);
    this.lhs = checkNotNull(lhs);
    this.rhs = rhs;
  }

  static PotentialDeclaration fromName(Node nameNode) {
    checkArgument(nameNode.isQualifiedName(), nameNode);
    Node rhs = NodeUtil.getRValueOfLValue(nameNode);
    if (ClassUtil.isThisProp(nameNode)) {
      String name = ClassUtil.getPrototypeNameOfThisProp(nameNode);
      return new ThisPropDeclaration(name, nameNode, rhs);
    }
    return new NameDeclaration(nameNode.getQualifiedName(), nameNode, rhs);
  }

  static PotentialDeclaration fromMethod(Node functionNode) {
    checkArgument(ClassUtil.isClassMethod(functionNode));
    String name = ClassUtil.getPrototypeNameOfMethod(functionNode);
    return new MethodDeclaration(name, functionNode);
  }

  static PotentialDeclaration fromDefine(Node callNode) {
    checkArgument(NodeUtil.isCallTo(callNode, "goog.define"));
    return new DefineDeclaration(callNode);
  }

  String getFullyQualifiedName() {
    return fullyQualifiedName;
  }

  Node getLhs() {
    return lhs;
  }

  @Nullable
  Node getRhs() {
    return rhs;
  }

  Node getStatement() {
    return NodeUtil.getEnclosingStatement(lhs);
  }

  JSDocInfo getJsDoc() {
    return NodeUtil.getBestJSDocInfo(lhs);
  }

  private boolean isDetached() {
    for (Node current = lhs; current != null; current = current.getParent()) {
      if (current.isScript()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Remove this "potential declaration" completely.
   * Usually, this is because the same symbol has already been declared in this file.
   */
  void remove(AbstractCompiler compiler) {
    if (isDetached()) {
      return;
    }
    Node statement = getStatement();
    NodeUtil.deleteNode(statement, compiler);
    statement.removeChildren();
  }

  /**
   * Simplify this declaration to only include what's necessary for typing.
   * Usually, this means removing the RHS and leaving a type annotation.
   */
  abstract void simplify(AbstractCompiler compiler);

  /**
   * A potential declaration that has a fully qualified name to describe it.
   * This includes things like:
   *   var/let/const/function/class declarations,
   *   assignments to a fully qualfied name,
   *   and goog.module exports
   * This is the most common type of potential declaration.
   */
  static class NameDeclaration extends PotentialDeclaration {

    NameDeclaration(String fullyQualifiedName, Node lhs, Node rhs) {
      super(fullyQualifiedName, lhs, rhs);
    }

    @Override
    void simplify(AbstractCompiler compiler) {
      if (getRhs() == null) {
        return;
      }
      Node nameNode = getLhs();
      JSDocInfo jsdoc = getJsDoc();
      if (jsdoc != null && jsdoc.hasEnumParameterType()) {
        // Remove values from enums
        if (getRhs().isObjectLit() && getRhs().hasChildren()) {
          for (Node key : getRhs().children()) {
            removeStringKeyValue(key);
          }
          compiler.reportChangeToEnclosingScope(getRhs());
        }
        return;
      }
      if (NodeUtil.isNamespaceDecl(nameNode)) {
        Node objLit = getRhs();
        if (getRhs().isOr()) {
          objLit = getRhs().getLastChild().detach();
          getRhs().replaceWith(objLit);
          compiler.reportChangeToEnclosingScope(nameNode);
        }
        if (objLit.hasChildren()) {
          for (Node key : objLit.children()) {
            if (!isTypedRhs(key.getLastChild())) {
              removeStringKeyValue(key);
              JsdocUtil.updateJsdoc(compiler, key);
              compiler.reportChangeToEnclosingScope(key);
            }
          }
        }
        return;
      }
      if (nameNode.matchesQualifiedName("exports")) {
        // Replace the RHS of a default goog.module export with Unknown
        replaceRhsWithUnknown(getRhs());
        compiler.reportChangeToEnclosingScope(nameNode);
        return;
      }
      // Just completely remove the RHS, and replace with a getprop.
      Node newStatement =
          NodeUtil.newQNameDeclaration(compiler, nameNode.getQualifiedName(), null, jsdoc);
      newStatement.useSourceInfoIfMissingFromForTree(nameNode);
      Node oldStatement = getStatement();
      NodeUtil.deleteChildren(oldStatement, compiler);
      oldStatement.replaceWith(newStatement);
      compiler.reportChangeToEnclosingScope(newStatement);
    }

    private static void replaceRhsWithUnknown(Node rhs) {
      rhs.replaceWith(IR.cast(IR.number(0), JsdocUtil.getQmarkTypeJSDoc()).srcrefTree(rhs));
    }

    private static void removeStringKeyValue(Node stringKey) {
      Node value = stringKey.getOnlyChild();
      Node replacementValue = IR.number(0).srcrefTree(value);
      stringKey.replaceChild(value, replacementValue);
    }

  }

  /**
   * A declaration of a property on `this` inside a constructor.
   */
  static class ThisPropDeclaration extends PotentialDeclaration {
    private final Node insertionPoint;

    ThisPropDeclaration(String fullyQualifiedName, Node lhs, Node rhs) {
      super(fullyQualifiedName, lhs, rhs);
      Node thisPropDefinition = NodeUtil.getEnclosingStatement(lhs);
      this.insertionPoint = NodeUtil.getEnclosingStatement(thisPropDefinition.getParent());
    }

    @Override
    void simplify(AbstractCompiler compiler) {
      // Just completely remove the RHS, if present, and replace with a getprop.
      Node newStatement =
          NodeUtil.newQNameDeclaration(compiler, getFullyQualifiedName(), null, getJsDoc());
      newStatement.useSourceInfoIfMissingFromForTree(getLhs());
      NodeUtil.deleteNode(getStatement(), compiler);
      insertionPoint.getParent().addChildAfter(newStatement, insertionPoint);
      compiler.reportChangeToEnclosingScope(newStatement);
    }
  }


  /**
   * A declaration declared by a call to `goog.define`. Note that a let, const, or var declaration
   * annotated with @define in its JSDoc would be a NameDeclaration instead.
   */
  private static class DefineDeclaration extends PotentialDeclaration {
    DefineDeclaration(Node callNode) {
      super(callNode.getSecondChild().getString(), callNode, callNode.getLastChild());
    }

    @Override
    void simplify(AbstractCompiler compiler) {
      NodeUtil.deleteNode(getLhs().getLastChild(), compiler);
    }
  }

  /**
   * A declaration of a method defined using the ES6 method syntax or goog.defineClass. Note that
   * a method defined as an assignment to a prototype property would be a NameDeclaration instead.
   */
  private static class MethodDeclaration extends PotentialDeclaration {
    MethodDeclaration(String name, Node functionNode) {
      super(name, functionNode.getParent(), functionNode);
    }

    @Override
    void simplify(AbstractCompiler compiler) {}
  }

  static boolean isTypedRhs(Node rhs) {
    return rhs.isFunction()
        || rhs.isClass()
        || (rhs.isQualifiedName() && rhs.matchesQualifiedName("goog.abstractMethod"))
        || (rhs.isQualifiedName() && rhs.matchesQualifiedName("goog.nullFunction"));
  }
}
