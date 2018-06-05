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
    String name = ClassUtil.getFullyQualifiedNameOfMethod(functionNode);
    return new MethodDeclaration(name, functionNode);
  }

  static PotentialDeclaration fromStringKey(Node stringKeyNode) {
    checkArgument(stringKeyNode.isStringKey());
    checkArgument(stringKeyNode.getParent().isObjectLit());
    String name = "this." + stringKeyNode.getString();
    return new StringKeyDeclaration(name, stringKeyNode);
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

  @Nullable
  JSDocInfo getJsDoc() {
    return NodeUtil.getBestJSDocInfo(lhs);
  }

  boolean isDetached() {
    for (Node current = lhs; current != null; current = current.getParent()) {
      if (current.isScript()) {
        return false;
      }
    }
    return true;
  }

  Node getRemovableNode() {
    return NodeUtil.getEnclosingStatement(lhs);
  }

  /**
   * Remove this "potential declaration" completely.
   * Usually, this is because the same symbol has already been declared in this file.
   */
  final void remove(AbstractCompiler compiler) {
    if (isDetached()) {
      return;
    }
    Node statement = getRemovableNode();
    NodeUtil.deleteNode(statement, compiler);
    statement.removeChildren();
  }

  /**
   * Simplify this declaration to only include what's necessary for typing.
   * Usually, this means removing the RHS and leaving a type annotation.
   */
  abstract void simplify(AbstractCompiler compiler);

  boolean isAliasDefinition() {
    Node rhs = getRhs();
    return isConstToBeInferred() && rhs != null && rhs.isQualifiedName();
  }

  /**
   * A potential declaration that has a fully qualified name to describe it.
   * This includes things like:
   *   var/let/const/function/class declarations,
   *   assignments to a fully qualified name,
   *   and goog.module exports
   * This is the most common type of potential declaration.
   */
  private static class NameDeclaration extends PotentialDeclaration {

    NameDeclaration(String fullyQualifiedName, Node lhs, Node rhs) {
      super(fullyQualifiedName, lhs, rhs);
    }

    private void simplifyNamespace(AbstractCompiler compiler) {
      if (getRhs().isOr()) {
        Node objLit = getRhs().getLastChild().detach();
        getRhs().replaceWith(objLit);
        compiler.reportChangeToEnclosingScope(getLhs());
      }
    }

    private void simplifySymbol(AbstractCompiler compiler) {
      checkArgument(NodeUtil.isCallTo(getRhs(), "Symbol"));
      Node callNode = getRhs();
      while (callNode.hasMoreThanOneChild()) {
        NodeUtil.deleteNode(callNode.getLastChild(), compiler);
      }
    }

    @Override
    void simplify(AbstractCompiler compiler) {
      if (getRhs() == null || shouldPreserve()) {
        return;
      }
      Node nameNode = getLhs();
      JSDocInfo jsdoc = getJsDoc();
      if (jsdoc != null && jsdoc.hasEnumParameterType()) {
        super.simplifyEnumValues(compiler);
        return;
      }
      if (NodeUtil.isNamespaceDecl(nameNode)) {
        simplifyNamespace(compiler);
        return;
      }
      if (nameNode.matchesQualifiedName("exports")) {
        // Replace the RHS of a default goog.module export with Unknown
        replaceRhsWithUnknown(getRhs());
        compiler.reportChangeToEnclosingScope(nameNode);
        return;
      }
      if (NodeUtil.isCallTo(getRhs(), "Symbol")) {
        simplifySymbol(compiler);
        return;
      }
      if (getLhs().getParent().isConst()) {
        jsdoc = JsdocUtil.markConstant(jsdoc);
      }
      // Just completely remove the RHS, and replace with a getprop.
      Node newStatement =
          NodeUtil.newQNameDeclaration(compiler, nameNode.getQualifiedName(), null, jsdoc);
      newStatement.useSourceInfoIfMissingFromForTree(nameNode);
      Node oldStatement = getRemovableNode();
      NodeUtil.deleteChildren(oldStatement, compiler);
      if (oldStatement.isExport()) {
        oldStatement.addChildToBack(newStatement);
      } else {
        oldStatement.replaceWith(newStatement);
      }
      compiler.reportChangeToEnclosingScope(newStatement);
    }

    private static void replaceRhsWithUnknown(Node rhs) {
      rhs.replaceWith(IR.cast(IR.number(0), JsdocUtil.getQmarkTypeJSDoc()).srcrefTree(rhs));
    }

    @Override
    boolean shouldPreserve() {
      Node rhs = getRhs();
      Node nameNode = getLhs();
      JSDocInfo jsdoc = getJsDoc();
      boolean isExport = isExportLhs(nameNode);
      return super.shouldPreserve()
          || isImportRhs(rhs)
          || (isExport && rhs != null && (rhs.isQualifiedName() || rhs.isObjectLit()))
          || (jsdoc != null && jsdoc.isConstructor() && rhs != null && rhs.isQualifiedName())
          || (rhs != null
              && rhs.isObjectLit()
              && !rhs.hasChildren()
              && (jsdoc == null || !JsdocUtil.hasAnnotatedType(jsdoc)));
    }
  }

  /**
   * A declaration of a property on `this` inside a constructor.
   */
  private static class ThisPropDeclaration extends PotentialDeclaration {
    private final Node insertionPoint;

    ThisPropDeclaration(String fullyQualifiedName, Node lhs, Node rhs) {
      super(fullyQualifiedName, lhs, rhs);
      Node thisPropDefinition = NodeUtil.getEnclosingStatement(lhs);
      this.insertionPoint = NodeUtil.getEnclosingStatement(thisPropDefinition.getParent());
    }

    @Override
    boolean isAliasDefinition() {
      // Constructor 'this' property declarations are executed in each constructor invocation
      // and are not aliases in the traditional sense
      return false;
    }

    @Override
    void simplify(AbstractCompiler compiler) {
      if (shouldPreserve()) {
        return;
      }
      // Just completely remove the RHS, if present, and replace with a getprop.
      Node newStatement =
          NodeUtil.newQNameDeclaration(compiler, getFullyQualifiedName(), null, getJsDoc());
      newStatement.useSourceInfoIfMissingFromForTree(getLhs());
      NodeUtil.deleteNode(getRemovableNode(), compiler);
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

    @Override
    Node getRemovableNode() {
      return getLhs();
    }
  }

  private static class StringKeyDeclaration extends PotentialDeclaration {
    StringKeyDeclaration(String name, Node stringKeyNode) {
      super(name, stringKeyNode, stringKeyNode.getLastChild());
    }

    @Override
    void simplify(AbstractCompiler compiler) {
      if (shouldPreserve()) {
        return;
      }
      JSDocInfo jsdoc = getJsDoc();
      if (jsdoc != null && jsdoc.hasEnumParameterType()) {
        super.simplifyEnumValues(compiler);
        return;
      }
      Node key = getLhs();
      removeStringKeyValue(key);
      compiler.reportChangeToEnclosingScope(key);
      if (jsdoc == null
          || !jsdoc.containsDeclaration()
          || isConstToBeInferred()) {
        key.setJSDocInfo(JsdocUtil.getUnusableTypeJSDoc(jsdoc));
      }
    }

    @Override
    boolean shouldPreserve() {
      return super.isDetached() || super.shouldPreserve() || !isInNamespace();
    }

    private boolean isInNamespace() {
      Node stringKey = getLhs();
      Node objLit = stringKey.getParent();
      Node lvalue = NodeUtil.getBestLValue(objLit);
      if (lvalue == null) {
        return false;
      }
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(lvalue);
      return !isExportLhs(lvalue)
          && !JsdocUtil.hasAnnotatedType(jsdoc)
          && NodeUtil.isNamespaceDecl(lvalue);
    }

    @Override
    Node getRemovableNode() {
      return getLhs();
    }

  }

  /** Remove values from enums */
  private void simplifyEnumValues(AbstractCompiler compiler) {
    if (getRhs().isObjectLit() && getRhs().hasChildren()) {
      for (Node key : getRhs().children()) {
        removeStringKeyValue(key);
      }
      compiler.reportChangeToEnclosingScope(getRhs());
    }
  }

  boolean isDefiniteDeclaration() {
    Node parent = getLhs().getParent();
    switch (parent.getToken()) {
      case VAR:
      case LET:
      case CONST:
      case CLASS:
      case FUNCTION:
        return true;
      default:
        return isExportLhs(getLhs())
            || (getJsDoc() != null && getJsDoc().containsDeclaration())
            || (getRhs() != null && PotentialDeclaration.isTypedRhs(getRhs()));
    }
  }

  boolean shouldPreserve() {
    return getRhs() != null && isTypedRhs(getRhs());
  }

  boolean isConstToBeInferred() {
    return isConstToBeInferred(getLhs());
  }

  static boolean isConstToBeInferred(Node nameNode) {
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(nameNode);
    boolean isConst =
        nameNode.getParent().isConst()
            || isExportLhs(nameNode)
            || (jsdoc != null && jsdoc.isConstant());
    return isConst
        && !JsdocUtil.hasAnnotatedType(jsdoc)
        && !NodeUtil.isNamespaceDecl(nameNode);
  }

  private static boolean isTypedRhs(Node rhs) {
    return rhs.isFunction()
        || rhs.isClass()
        || NodeUtil.isCallTo(rhs, "goog.defineClass")
        || (rhs.isQualifiedName() && rhs.matchesQualifiedName("goog.abstractMethod"))
        || (rhs.isQualifiedName() && rhs.matchesQualifiedName("goog.nullFunction"));
  }

  private static boolean isExportLhs(Node lhs) {
    return (lhs.isName() && lhs.matchesQualifiedName("exports"))
        || (lhs.isGetProp() && lhs.getFirstChild().matchesQualifiedName("exports"));
  }

  static boolean isImportRhs(@Nullable Node rhs) {
    if (rhs == null || !rhs.isCall()) {
      return false;
    }
    Node callee = rhs.getFirstChild();
    return callee.matchesQualifiedName("goog.require")
        || callee.matchesQualifiedName("goog.forwardDeclare");
  }

  private static void removeStringKeyValue(Node stringKey) {
    Node value = stringKey.getOnlyChild();
    Node replacementValue = IR.number(0).srcrefTree(value);
    stringKey.replaceChild(value, replacementValue);
  }

}
