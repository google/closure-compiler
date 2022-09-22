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
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import org.jspecify.nullness.Nullable;

/**
 * Encapsulates something that could be a declaration.
 *
 * <p>This includes: var/let/const declarations, function/class declarations, method declarations,
 * assignments, goog.define calls, and even valueless property accesses (e.g. `/** @type {number} *
 * / Foo.prototype.bar`)
 */
abstract class PotentialDeclaration {
  // The threshold for trunctating string enum items. Any string literal longer than this will get
  // truncated in the generated IJS file. This value is decided by the length of the longest
  // security-sensitive attribute name.
  private static final int STRING_ENUM_RETAIN_CAP = 10;
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
    if (stringKeyNode.getString().equals("properties")) {
      JSDocInfo objLitJsDoc = NodeUtil.getBestJSDocInfo(stringKeyNode.getParent());
      if (objLitJsDoc != null && objLitJsDoc.isPolymerBehavior()) {
        return new PolymerBehaviorPropertiesDeclaration(name, stringKeyNode);
      }
    }
    return new StringKeyDeclaration(name, stringKeyNode);
  }

  static PotentialDeclaration fromDefine(Node callNode) {
    checkArgument(NodeUtil.isCallTo(callNode, "goog.define"));
    return DefineDeclaration.from(callNode);
  }

  static PotentialDeclaration fromAlias(Node nameNode) {
    checkArgument(nameNode.isQualifiedName(), nameNode);
    return new AliasDeclaration(nameNode.getQualifiedName(), nameNode);
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
   * Remove this "potential declaration" completely. Usually, this is because the same symbol has
   * already been declared in this file.
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
   * Simplify this declaration to only include what's necessary for typing. Usually, this means
   * removing the RHS and leaving a type annotation.
   */
  abstract void simplify(AbstractCompiler compiler);

  /**
   * Breaks down this declaration if it's a destructuring LHS by replacing it with a VAR node and
   * removing the RHS.
   *
   * <p>TODO(lharker): can we merge this code into {@link #simplify(AbstractCompiler)}?
   *
   * @return true if the declaration is broken down. Otherwise, returns false.
   */
  boolean breakDownDestructure(AbstractCompiler compiler) {
    return false;
  }

  /**
   * A potential declaration that has a fully qualified name to describe it. This includes things
   * like: var/let/const/function/class declarations, assignments to a fully qualified name, and
   * goog.module exports This is the most common type of potential declaration.
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
      if (nameNode.matchesName("exports")) {
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
      newStatement.srcrefTreeIfMissing(nameNode);
      Node oldStatement = getRemovableNode();
      NodeUtil.deleteChildren(oldStatement, compiler);
      if (oldStatement.isExport()) {
        oldStatement.addChildToBack(newStatement);
      } else {
        oldStatement.replaceWith(newStatement);
      }
      compiler.reportChangeToEnclosingScope(newStatement);
    }

    @Override
    boolean breakDownDestructure(AbstractCompiler compiler) {
      if (!NodeUtil.isLhsByDestructuring(getLhs())) {
        return false;
      }

      Node rootTarget = NodeUtil.getRootTarget(getLhs());

      checkState(rootTarget.getParent().isDestructuringLhs());
      if (!NodeUtil.isNameDeclaration(rootTarget.getGrandparent())) {
        return false;
      }

      Node definitionNode = rootTarget.getGrandparent();

      Node prev = null;

      ArrayList<Node> lhsNodes = new ArrayList<>();
      NodeUtil.visitLhsNodesInNode(rootTarget.getParent(), lhsNodes::add);
      for (Node n : lhsNodes) {
        n.detach();
        Node temp = IR.var(n);
        if (prev == null) {
          definitionNode.replaceWith(temp);
          compiler.reportChangeToEnclosingScope(temp);
        } else {
          temp.insertAfter(prev);
          compiler.reportChangeToEnclosingScope(temp);
          temp.srcrefTree(prev);
        }
        prev = temp;
      }
      return true;
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
              && (jsdoc == null || !JsdocUtil.hasAnnotatedType(jsdoc)))
          || (rhs != null && NodeUtil.isCallTo(rhs, "Polymer"))
          || isPolymerBehaviorAliasOrArray();
    }

    /**
     * Polymer Behaviors can take 3 forms:
     *
     * <pre>{@code
     * 1) /** @polymerBehavior *\/ export const MyBehavior = { ... };
     * 2) /** @polymerBehavior *\/ export const MyBehaviorAlias = MyBehavior;
     * 3) /** @polymerBehavior *\/ export const MyBehaviorArray = [Behavior1, Behavior2];
     * }</pre>
     *
     * Form #1 will be simplified by PolymerBehaviorPropertiesDeclaration. Forms #2 and #3 need to
     * be preserved here as-is so that the PolymerPass can follow the name references. Other forms
     * annotated with @polymerBehavior are invalid and can be simplified or removed like any other
     * variable.
     */
    boolean isPolymerBehaviorAliasOrArray() {
      JSDocInfo jsdoc = getJsDoc();
      Node rhs = getRhs();
      return jsdoc != null
          && jsdoc.isPolymerBehavior()
          && rhs != null
          && (rhs.isName() || rhs.isArrayLit());
    }
  }

  /** A declaration of a property on `this` inside a constructor. */
  private static class ThisPropDeclaration extends PotentialDeclaration {
    private final Node insertionPoint;

    ThisPropDeclaration(String fullyQualifiedName, Node lhs, Node rhs) {
      super(fullyQualifiedName, lhs, rhs);
      Node thisPropDefinition = NodeUtil.getEnclosingStatement(lhs);
      this.insertionPoint = NodeUtil.getEnclosingStatement(thisPropDefinition.getParent());
    }

    @Override
    void simplify(AbstractCompiler compiler) {
      if (shouldPreserve()) {
        return;
      }
      // Just completely remove the RHS, if present, and replace with a getprop.
      Node newStatement =
          NodeUtil.newQNameDeclaration(compiler, getFullyQualifiedName(), null, getJsDoc());
      newStatement.srcrefTreeIfMissing(getLhs());
      NodeUtil.deleteNode(getRemovableNode(), compiler);
      if (insertionPoint.hasParent()) {
        newStatement.insertAfter(insertionPoint);
        compiler.reportChangeToEnclosingScope(newStatement);
      }
    }
  }

  /**
   * A declaration declared by a call to `goog.define`. Note that a let, const, or var declaration
   * annotated with @define in its JSDoc and no 'goog.define' would be a NameDeclaration instead.
   */
  private static class DefineDeclaration extends PotentialDeclaration {
    DefineDeclaration(String qualifiedName, Node lhs, Node rhs) {
      super(qualifiedName, lhs, rhs);
    }

    @Override
    void simplify(AbstractCompiler compiler) {
      JSDocInfo info = getJsDoc();
      if (info != null && info.getType() != null) {
        Node newRhs = makeEmptyValueNode(info.getType());
        if (newRhs != null) {
          getRhs().replaceWith(newRhs);
          compiler.reportChangeToEnclosingScope(newRhs);
          return;
        }
      }
      NodeUtil.deleteNode(getRemovableNode(), compiler);
    }

    static DefineDeclaration from(Node callNode) {
      // Match a few different forms, depending on the call node's parent:
      //   1. EXPR_RESULT: goog.define('foo', 1);
      //   2. ASSIGN: a.b = goog.define('c', 2);
      //   3. NAME: var x = goog.define('d', 3);
      switch (callNode.getParent().getToken()) {
        case EXPR_RESULT:
          return new DefineDeclaration(
              callNode.getSecondChild().getString(), callNode, callNode.getLastChild());
        case ASSIGN:
          Node previous = callNode.getPrevious();
          return new DefineDeclaration(
              previous.getQualifiedName(), previous, callNode.getLastChild());
        case NAME:
          Node parent = callNode.getParent();
          return new DefineDeclaration(parent.getString(), parent, callNode.getLastChild());
        default:
          throw new IllegalStateException("Unexpected parent: " + callNode.getParent().getToken());
      }
    }

    static @Nullable Node makeEmptyValueNode(JSTypeExpression type) {
      Node n = type.getRoot();
      while (n != null && !n.isStringLit() && !n.isName()) {
        n = n.getFirstChild();
      }
      switch (n != null ? n.getString() : "") {
        case "boolean":
          return new Node(Token.FALSE);
        case "number":
          return Node.newNumber(0);
        case "string":
          return Node.newString("");
        default:
          return null;
      }
    }
  }

  /**
   * A declaration of a method defined using the ES6 method syntax or goog.defineClass. Note that a
   * method defined as an assignment to a prototype property would be a NameDeclaration instead.
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
      if (jsdoc == null || !jsdoc.containsDeclaration() || isConstToBeInferred()) {
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

  /**
   * Polymer Behaviors are mixin-like objects used in Polymer 1 for multiple inheritance. They are
   * also supported by Polymer 2 and 3 for backwards-compatibility, though their use is discouraged
   * in favor of regular JavaScript mixins.
   *
   * <p>Example:
   *
   * <pre>{@code
   * \/** @polymerBehavior *\/
   * export const MyBehavior = {
   *   properties: {
   *     foo: String,
   *     bar: {
   *       type: Number,
   *       value: 123
   *     }
   *   },
   *   baz: function() {}
   * };
   * }</pre>
   *
   * <p>For incremental compilation, it is important that the "properties" object is preserved,
   * because the PolymerPass injects the properties declared there onto the prototypes of the
   * Polymer elements that apply that behavior. Note that method signatures are already preserved so
   * don't need additional handling here.
   */
  private static class PolymerBehaviorPropertiesDeclaration extends PotentialDeclaration {
    PolymerBehaviorPropertiesDeclaration(String name, Node stringKeyNode) {
      super(name, stringKeyNode, stringKeyNode.getLastChild());
    }

    @Override
    void simplify(AbstractCompiler compiler) {
      if (isDetached()) {
        return;
      }
      Node propertiesObject = getRhs();
      if (!propertiesObject.isObjectLit() || !propertiesObject.hasChildren()) {
        return;
      }
      for (Node propKey = propertiesObject.getFirstChild();
          propKey != null;
          propKey = propKey.getNext()) {
        Node propDef = propKey.getOnlyChild();
        // A property definition is either a function reference (e.g. String, Number), or another
        // object literal. If it's an object literal, only the "type" sub-property matters for type
        // checking, so we can delete everything else (which may include e.g. a "value" sub-property
        // with a function expression).
        if (propDef.isObjectLit()) {
          for (Node subProp = propDef.getFirstChild(); subProp != null; ) {
            final Node next = subProp.getNext();
            if (!subProp.getString().equals("type")) {
              NodeUtil.deleteNode(subProp, compiler);
            }
            subProp = next;
          }
        }
      }
    }

    @Override
    boolean shouldPreserve() {
      return true;
    }

    @Override
    Node getRemovableNode() {
      return getLhs();
    }
  }

  private static class AliasDeclaration extends PotentialDeclaration {

    /**
     * @param name The alias name being declared.
     * @param lhs The NAME node that represents the name of the individual alias.
     */
    AliasDeclaration(String name, Node lhs) {
      super(name, lhs, null);
    }

    @Override
    void simplify(AbstractCompiler compiler) {
      // Does not simplify
    }

    /**
     * If the declaration is a destructuring declaration: 1) If the lhs's destructuring pattern
     * parent has only one child, e.g. const {Foo} = x; returns the enclosing statement to remove
     * the entire statement. 2) If the parent has more than one children, e.g. const {Foo, Bar} = x;
     * returns the lhs so that when Foo is removed, const {Foo, Bar} = x; becomes const {Bar} = x;
     * Otherwise, returns the enclosing statement.
     */
    @Override
    Node getRemovableNode() {
      Node lhs = getLhs();
      if (lhs.getParent().isArrayPattern() && lhs.getParent().hasMoreThanOneChild()) {
        return lhs;
      }
      if (lhs.getGrandparent().isObjectPattern() && lhs.getGrandparent().hasMoreThanOneChild()) {
        return lhs.getParent();
      }
      return NodeUtil.getEnclosingStatement(lhs);
    }

    @Override
    boolean isDefiniteDeclaration(AbstractCompiler compiler) {
      return true;
    }

    @Override
    boolean shouldPreserve() {
      return true;
    }
  }

  /** Remove values from enums */
  private void simplifyEnumValues(AbstractCompiler compiler) {
    if (getRhs().isObjectLit() && getRhs().hasChildren()) {
      boolean changed = false;
      for (Node key = getRhs().getFirstChild(); key != null; key = key.getNext()) {
        Node value = key.getOnlyChild();
        if (!value.isStringLit()) {
          removeStringKeyValue(key);
          changed = true;
        } else {
          String content = value.getString();
          if (content.length() > STRING_ENUM_RETAIN_CAP) {
            truncateStringKeyValue(key);
            changed = true;
          }
        }
      }
      if (changed) {
        compiler.reportChangeToEnclosingScope(getRhs());
      }
    }
  }

  boolean isDefiniteDeclaration(AbstractCompiler compiler) {
    Node parent = getLhs().getParent();
    switch (parent.getToken()) {
      case DEFAULT_VALUE:
        if (!parent.getParent().isStringKey()) {
          return false;
        }
        // fall through
      case COMPUTED_PROP:
      case STRING_KEY:
        if (NodeUtil.isLhsByDestructuring(getLhs())) {
          Node rootTarget = NodeUtil.getRootTarget(getLhs());
          checkState(rootTarget.getParent().isDestructuringLhs());
          if (NodeUtil.isNameDeclaration(rootTarget.getGrandparent())) {
            return true;
          }
        }
        return false;
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
    return isConst && !JsdocUtil.hasAnnotatedType(jsdoc) && !NodeUtil.isNamespaceDecl(nameNode);
  }

  private static final QualifiedName GOOG_ABSTRACTMETHOD = QualifiedName.of("goog.abstractMethod");
  private static final QualifiedName GOOG_REQUIRE = QualifiedName.of("goog.require");
  private static final QualifiedName GOOG_REQUIRETYPE = QualifiedName.of("goog.requireType");
  private static final QualifiedName GOOG_FORWARDDECLARE = QualifiedName.of("goog.forwardDeclare");
  private static final QualifiedName MODULE_EXPORTS = QualifiedName.of("module.exports");

  private static boolean isTypedRhs(Node rhs) {
    return rhs.isFunction()
        || rhs.isClass()
        || NodeUtil.isCallTo(rhs, "goog.defineClass")
        || (rhs.isQualifiedName() && GOOG_ABSTRACTMETHOD.matches(rhs));
  }

  private static boolean isExportLhs(Node lhs) {
    return (lhs.isName() && lhs.matchesName("exports"))
        || (lhs.isGetProp() && lhs.getFirstChild().matchesName("exports"))
        || MODULE_EXPORTS.matches(lhs);
  }

  static boolean isImportRhs(@Nullable Node rhs) {
    if (rhs == null || !rhs.isCall()) {
      return false;
    }
    Node callee = rhs.getFirstChild();
    return GOOG_REQUIRE.matches(callee)
        || GOOG_REQUIRETYPE.matches(callee)
        || GOOG_FORWARDDECLARE.matches(callee)
        || callee.matchesName("require");
  }

  static boolean isAliasDeclaration(Node lhs, @Nullable Node rhs) {
    return !ClassUtil.isThisProp(lhs)
        && isConstToBeInferred(lhs)
        && rhs != null
        && isQualifiedAliasExpression(rhs);
  }

  private static final QualifiedName GOOG_MODULE_GET = QualifiedName.of("goog.module.get");

  /**
   * Returns whether a node corresponds to a simple or a qualified name, such as <code>x</code> or
   * <code>a.b.c</code> or <code>this.a</code>.
   */
  public static final boolean isQualifiedAliasExpression(Node n) {
    switch (n.getToken()) {
      case NAME:
        return !n.getString().isEmpty();
      case THIS:
      case SUPER:
        return true;
      case GETPROP:
        return isQualifiedAliasExpression(n.getFirstChild());
      case CALL:
        if (GOOG_MODULE_GET.matches(n.getFirstChild())) {
          return true;
        }
        return false;

      case MEMBER_FUNCTION_DEF:
        // These are explicitly *not* qualified name components.
      default:
        return false;
    }
  }

  private static void removeStringKeyValue(Node stringKey) {
    Node value = stringKey.getOnlyChild();
    Node replacementValue = IR.number(0).srcrefTree(value);
    value.replaceWith(replacementValue);
  }

  private static void truncateStringKeyValue(Node stringKey) {
    Node value = stringKey.getOnlyChild();
    Node replacementValue =
        IR.string(value.getString().substring(0, STRING_ENUM_RETAIN_CAP - 2) + "..")
            .srcrefTree(value);
    value.replaceWith(replacementValue);
  }
}
