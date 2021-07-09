/*
 * Copyright 2018 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticRef;
import com.google.javascript.rhino.StaticSlot;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.Token;
import javax.annotation.Nullable;

/**
 * Used by {@code Scope} to store information about variables.
 */
public class AbstractVar<S extends AbstractScope<S, V>, V extends AbstractVar<S, V>>
    extends ScopedName implements StaticSlot, StaticRef {

  private final String name;

  private final Node nameNode;

  // null if not an implicit goog namespace; otherwise starts out as an ArrayList then is frozen
  // into an ImmutableList.
  private SourceKind implicitGoogNamespaceStrength;

  /** Input source */
  private final CompilerInput input;

  /**
   * The index at which the var is declared. e.g. if it's 0, it's the first declared variable in
   * that scope
   */
  private final int index;

  private final S scope;

  /**
   * @param name The name of this var. Does not have to be semantically valid JS identifier.
   * @param nameNode The node representing this variables declaration or null
   * @param scope The scope containing this var
   * @param index The index at which the var is declared in the scope. Must be either positive, or
   *     -1 for implicit variables.
   * @param input The compiler input containing the given nameNode, if any. May be null to allow for
   *     declaring the native types in the type system.
   */
  AbstractVar(
      String name,
      @Nullable Node nameNode,
      @Nullable S scope,
      int index,
      @Nullable CompilerInput input,
      @Nullable Node implicitGoogNamespaceDefinition) {
    checkArgument(index >= -1, index);
    this.name = checkNotNull(name);
    if (implicitGoogNamespaceDefinition != null) {
      this.nameNode = null;
      this.implicitGoogNamespaceStrength = strengthOf(implicitGoogNamespaceDefinition);
    } else {
      this.nameNode = nameNode;
      this.implicitGoogNamespaceStrength = null;
    }
    this.scope = scope;
    this.index = index;
    this.input = input;
  }

  @Override
  public final String getName() {
    return name;
  }

  @Override
  public final Node getScopeRoot() {
    return scope.getRootNode();
  }

  @Override
  public final Node getNode() {
    return nameNode;
  }

  final CompilerInput getInput() {
    return input;
  }

  @Override
  public final StaticSourceFile getSourceFile() {
    return (this.getNode() != null ? this.getNode() : scope.getRootNode()).getStaticSourceFile();
  }

  @Override
  public final V getSymbol() {
    return thisVar();
  }

  @Override
  public final V getDeclaration() {
    return this.getNode() == null ? null : thisVar();
  }

  public final Node getParentNode() {
    return this.getNode() == null ? null : this.getNode().getParent();
  }

  /**
   * Whether this is a bleeding function (an anonymous named function
   * that bleeds into the inner scope).
   */
  public boolean isBleedingFunction() {
    Node parent = getParentNode();
    return parent != null && NodeUtil.isFunctionExpression(parent);
  }

  @Override
  public final S getScope() {
    return scope;
  }

  /**
   * The index at which the var is declared. e.g. if it's 0, it's the first declared variable in
   * that scope
   */
  int getIndex() {
    return index;
  }

  public final boolean isGlobal() {
    return scope.isGlobal();
  }

  public final boolean isLocal() {
    return scope.isLocal();
  }

  final boolean isExtern() {
    return input == null || input.isExtern();
  }

  /** Returns {@code true} if the variable is declared or inferred to be a constant. */
  public final boolean isDeclaredOrInferredConst() {
    Node declarationNode = this.getNode();
    if (declarationNode == null) {
      return false;
    }

    return declarationNode.isDeclaredConstantVar()
        || declarationNode.isInferredConstantVar()
        || declarationNode.getBooleanProp(Node.IS_CONSTANT_NAME);
  }

  /**
   * Returns {@code true} if the variable is declared as a define.
   * A variable is a define if it is annotated by {@code @define}.
   */
  public final boolean isDefine() {
    JSDocInfo info = getJSDocInfo();
    return info != null && info.isDefine();
  }

  public final Node getInitialValue() {
    return NodeUtil.getRValueOfLValue(this.getNode());
  }

  public final Node getNameNode() {
    return this.getNode();
  }

  @Override
  public final JSDocInfo getJSDocInfo() {
    return this.getNode() == null ? null : NodeUtil.getBestJSDocInfo(this.getNode());
  }

  final boolean isVar() {
    return declarationType() == Token.VAR;
  }

  final boolean isCatch() {
    return declarationType() == Token.CATCH;
  }

  final boolean isLet() {
    return declarationType() == Token.LET;
  }

  final boolean isConst() {
    return declarationType() == Token.CONST;
  }

  final boolean isClass() {
    return declarationType() == Token.CLASS;
  }

  final boolean isParam() {
    return declarationType() == Token.PARAM_LIST;
  }

  public final boolean isDefaultParam() {
    Node parent = this.getNode().getParent();
    return parent.getParent().isParamList() && parent.isDefaultValue()
        && parent.getFirstChild() == nameNode;
  }

  final boolean isImport() {
    return declarationType() == Token.IMPORT;
  }

  public final boolean isArguments() {
    return Var.ARGUMENTS.equals(name) && scope.isFunctionScope();
  }

  final boolean isGoogModuleExports() {
    return scope.isModuleScope() && "exports".equals(name) && isImplicit();
  }

  public final boolean isThis() {
    return "this".equals(name) && scope.isFunctionScope();
  }

  private boolean isImplicit() {
    if (isImplicitGoogNamespace()) {
      return true;
    }
    AbstractScope.ImplicitVar var = AbstractScope.ImplicitVar.of(name);
    return var != null && var.isMadeByScope(scope);
  }

  private static final ImmutableSet<Token> DECLARATION_TYPES = Sets.immutableEnumSet(
      Token.VAR,
      Token.LET,
      Token.CONST,
      Token.FUNCTION,
      Token.CLASS,
      Token.CATCH,
      Token.IMPORT,
      Token.PARAM_LIST);

  final Token declarationType() {
    if (isImplicitGoogNamespace()) {
      return null;
    }
    for (Node current = this.getNode(); current != null; current = current.getParent()) {
      if (DECLARATION_TYPES.contains(current.getToken())) {
        return current.getToken();
      }
    }
    checkState(
        isImplicit(),
        "The nameNode for %s must be a descendant of one of: %s", this, DECLARATION_TYPES);
    return null;
  }

  // This is safe because any concrete subclass of AbstractVar<V> should be assignable to V.
  // While it's theoretically possible to do otherwise, such a class would be very awkward to
  // implement, and is therefore not worth worrying about.
  @SuppressWarnings("unchecked")
  private V thisVar() {
    return (V) this;
  }

  /**
   * Whether this name comes from a goog.provide or legacy goog.module and does not have a normal
   * syntactical definition
   */
  final boolean isImplicitGoogNamespace() {
    return this.implicitGoogNamespaceStrength != null;
  }

  /**
   * Indicates this namespace was provided at the given node
   *
   * @param definition the STRINGLIT in a goog.provide or goog.module call
   */
  final void addImplicitGoogNamespaceDefinition(Node definition) {
    checkState(this.isImplicitGoogNamespace(), this);
    this.implicitGoogNamespaceStrength =
        strongerOf(this.implicitGoogNamespaceStrength, strengthOf(definition));
  }

  /** Returns the strongest kind of all definitions of this namespace */
  final SourceKind getImplicitGoogNamespaceStrength() {
    checkState(this.isImplicitGoogNamespace(), this);
    return this.implicitGoogNamespaceStrength;
  }

  private static final SourceKind strengthOf(Node n) {
    StaticSourceFile source = n.getStaticSourceFile();
    if (source == null) {
      return SourceKind.EXTERN;
    }

    return source.getKind();
  }

  private static SourceKind strongerOf(SourceKind left, SourceKind right) {
    if (left.equals(SourceKind.STRONG) || right.equals(SourceKind.STRONG)) {
      return SourceKind.STRONG;
    } else if (left.equals(SourceKind.EXTERN) || right.equals(SourceKind.EXTERN)) {
      // Externs are stronger because they aren't deleted.
      return SourceKind.EXTERN;
    }
    return SourceKind.WEAK;
  }
}
