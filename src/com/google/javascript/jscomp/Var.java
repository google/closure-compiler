/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.StaticTypedRef;
import com.google.javascript.rhino.jstype.StaticTypedSlot;

import java.util.Set;

/**
 * Used by {@code Scope} to store information about variables.
 */
public class Var implements StaticTypedSlot<JSType>, StaticTypedRef<JSType> {
  final String name;

  /** Var node */
  final Node nameNode;

  private JSType type;

  /**
   * Whether the variable's type has been inferred or is declared. An inferred
   * type may change over time (as more code is discovered), whereas a
   * declared type is a static contract that must be matched.
   */
  private final boolean typeInferred;

  /** Input source */
  final CompilerInput input;

  /**
   * The index at which the var is declared. e.g. if it's 0, it's the first
   * declared variable in that scope
   */
  final int index;

  /** The enclosing scope */
  final Scope scope;

  /** @see #isMarkedEscaped */
  private boolean markedEscaped = false;

  /** @see #isMarkedAssignedExactlyOnce */
  private boolean markedAssignedExactlyOnce = false;

  /**
   * Creates a variable.
   *
   * @param inferred whether its type is inferred (as opposed to declared)
   */
  Var(boolean inferred, String name, Node nameNode, JSType type,
      Scope scope, int index, CompilerInput input) {
    this.name = name;
    this.nameNode = nameNode;
    this.type = type;
    this.scope = scope;
    this.index = index;
    this.input = input;
    this.typeInferred = inferred;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Node getNode() {
    return nameNode;
  }

  CompilerInput getInput() {
    return input;
  }

  @Override
  public StaticSourceFile getSourceFile() {
    return nameNode.getStaticSourceFile();
  }

  @Override
  public Var getSymbol() {
    return this;
  }

  @Override
  public Var getDeclaration() {
    return nameNode == null ? null : this;
  }

  public Node getParentNode() {
    return nameNode == null ? null : nameNode.getParent();
  }

  /**
   * Whether this is a bleeding function (an anonymous named function
   * that bleeds into the inner scope).
   */
  public boolean isBleedingFunction() {
    return NodeUtil.isFunctionExpression(getParentNode());
  }

  Scope getScope() {
    return scope;
  }

  public boolean isGlobal() {
    return scope.isGlobal();
  }

  public boolean isLocal() {
    return scope.isLocal();
  }

  boolean isExtern() {
    return input == null || input.isExtern();
  }

  /**
   * Returns {@code true} if the variable is declared as a constant,
   * based on the value reported by {@code NodeUtil}.
   */
  public boolean isInferredConst() {
    if (nameNode == null) {
      return false;
    }

    return nameNode.getBooleanProp(Node.IS_CONSTANT_VAR)
        || nameNode.getBooleanProp(Node.IS_CONSTANT_NAME);
  }

  /**
   * Returns {@code true} if the variable is declared as a define.
   * A variable is a define if it is annotated by {@code @define}.
   */
  public boolean isDefine() {
    JSDocInfo info = getJSDocInfo();
    return info != null && info.isDefine();
  }

  public Node getInitialValue() {
    return NodeUtil.getRValueOfLValue(nameNode);
  }

  /**
   * Gets this variable's type. To know whether this type has been inferred,
   * see {@code #isTypeInferred()}.
   */
  @Override
  public JSType getType() {
    return type;
  }

  public Node getNameNode() {
    return nameNode;
  }

  @Override
  public JSDocInfo getJSDocInfo() {
    return nameNode == null ? null : NodeUtil.getBestJSDocInfo(nameNode);
  }

  void setType(JSType type) {
    this.type = type;
  }

  void resolveType(ErrorReporter errorReporter) {
    if (type != null) {
      type = type.resolve(errorReporter, scope);
    }
  }

  /**
   * Returns whether this variable's type is inferred. To get the variable's
   * type, see {@link #getType()}.
   */
  @Override
  public boolean isTypeInferred() {
    return typeInferred;
  }

  public String getInputName() {
    if (input == null) {
      return "<non-file>";
    }
    return input.getName();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Var)) {
      return false;
    }

    Var otherVar = (Var) other;
    return otherVar.nameNode == nameNode;
  }

  @Override
  public int hashCode() {
    return nameNode.hashCode();
  }

  @Override
  public String toString() {
    return "Scope.Var " + name + "{" + type + "}";
  }

  /**
   * Record that this is escaped by an inner scope.
   *
   * <p>In other words, it's assigned in an inner scope so that it's much harder
   * to make assertions about its value at a given point.
   */
  void markEscaped() {
    markedEscaped = true;
  }

  /**
   * Whether this is escaped by an inner scope.
   * Notice that not all scope creators record this information.
   */
  boolean isMarkedEscaped() {
    return markedEscaped;
  }

  /**
   * Record that this is assigned exactly once..
   *
   * <p>In other words, it's assigned in an inner scope so that it's much harder
   * to make assertions about its value at a given point.
   */
  void markAssignedExactlyOnce() {
    markedAssignedExactlyOnce = true;
  }

  /**
   * Whether this is assigned exactly once.
   * Notice that not all scope creators record this information.
   */
  boolean isMarkedAssignedExactlyOnce() {
    return markedAssignedExactlyOnce;
  }

  boolean isVar() {
    return declarationType() == Token.VAR;
  }

  boolean isLet() {
    return declarationType() == Token.LET;
  }

  boolean isConst() {
    return declarationType() == Token.CONST;
  }

  boolean isParam() {
    return declarationType() == Token.PARAM_LIST;
  }

  private int declarationType() {
    final Set<Integer> types = ImmutableSet.of(
        Token.VAR,
        Token.LET,
        Token.CONST,
        Token.FUNCTION,
        Token.CLASS,
        Token.CATCH,
        Token.PARAM_LIST);
    for (Node current = nameNode; current != null;
         current = current.getParent()) {
      if (types.contains(current.getType())) {
        return current.getType();
      }
    }
    throw new IllegalStateException("The nameNode for " + this + " must be a descendant"
        + " of one of: " + types);
  }

  static Var makeArgumentsVar(Scope s) {
    return new Arguments(s);
  }

  /**
   * A special subclass of Var used to distinguish "arguments" in the current
   * scope.
   */
  // TODO(johnlenz): Include this the list of Vars for the scope.
  public static class Arguments extends Var {
    Arguments(Scope scope) {
      super(
          false, // no inferred
          "arguments", // always arguments
          null,  // no declaration node
          // TODO(johnlenz): provide the type of "Arguments".
          null,  // no type info
          scope,
          -1,    // no variable index
          null   // input
            );
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Arguments)) {
        return false;
      }

      Arguments otherVar = (Arguments) other;
      return otherVar.scope.getRootNode() == scope.getRootNode();
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }
}
