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

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.StaticTypedRef;
import com.google.javascript.rhino.jstype.StaticTypedSlot;

/**
 * Several methods in this class, such as {@code isVar} throw an exception when called.
 * The reason for this is that we want to shadow methods from the parent class, to avoid calling
 * them accidentally.
 */
public class TypedVar extends Var implements StaticTypedSlot<JSType>, StaticTypedRef<JSType> {

  final TypedScope scope;
  private JSType type;
  // The next two fields and the associated methods are only used by
  // TypeInference.java. Maybe there is a way to avoid having them in all typed variable instances.
  private boolean markedEscaped = false;
  private boolean markedAssignedExactlyOnce = false;

  /**
   * Whether the variable's type has been inferred or is declared. An inferred
   * type may change over time (as more code is discovered), whereas a
   * declared type is a static contract that must be matched.
   */
  private final boolean typeInferred;

  TypedVar(boolean inferred, String name, Node nameNode, JSType type,
      TypedScope scope, int index, CompilerInput input) {
    super(name, nameNode, scope, index, input);
    this.type = type;
    this.scope = scope;
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

  @Override
  CompilerInput getInput() {
    return input;
  }

  @Override
  public StaticSourceFile getSourceFile() {
    return nameNode.getStaticSourceFile();
  }

  @Override
  public TypedVar getSymbol() {
    return this;
  }

  @Override
  public TypedVar getDeclaration() {
    return nameNode == null ? null : this;
  }

  @Override
  public Node getParentNode() {
    return nameNode == null ? null : nameNode.getParent();
  }

  public boolean isBleedingFunction() {
    throw new IllegalStateException(
        "Method isBleedingFunction cannot be called on typed variables.");
  }

  @Override
  public TypedScope getScope() {
    return scope;
  }

  @Override
  public boolean isGlobal() {
    return scope.isGlobal();
  }

  @Override
  public boolean isLocal() {
    return scope.isLocal();
  }

  @Override
  boolean isExtern() {
    return input == null || input.isExtern();
  }

  public boolean isInferredConst() {
    throw new IllegalStateException("Method isInferredConst cannot be called on typed variables.");
  }

  public boolean isDefine() {
    throw new IllegalStateException("Method isDefine cannot be called on typed variables.");
  }

  @Override
  public Node getInitialValue() {
    return NodeUtil.getRValueOfLValue(nameNode);
  }

  @Override
  public Node getNameNode() {
    return nameNode;
  }

  @Override
  public JSDocInfo getJSDocInfo() {
    return nameNode == null ? null : NodeUtil.getBestJSDocInfo(nameNode);
  }

  /**
   * Gets this variable's type. To know whether this type has been inferred,
   * see {@code #isTypeInferred()}.
   */
  @Override
  public JSType getType() {
    return type;
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
    if (!(other instanceof TypedVar)) {
      return false;
    }
    return ((TypedVar) other).nameNode == nameNode;
  }

  @Override
  public int hashCode() {
    return nameNode.hashCode();
  }

  @Override
  public String toString() {
    return "Var " + name + "{" + type + "}";
  }

  void markEscaped() {
    markedEscaped = true;
  }

  boolean isMarkedEscaped() {
    return markedEscaped;
  }

  void markAssignedExactlyOnce() {
    markedAssignedExactlyOnce = true;
  }

  boolean isMarkedAssignedExactlyOnce() {
    return markedAssignedExactlyOnce;
  }

  boolean isVar() {
    throw new IllegalStateException("Method isVar cannot be called on typed variables.");
  }

  boolean isLet() {
    throw new IllegalStateException("Method isLet cannot be called on typed variables.");
  }

  boolean isConst() {
    throw new IllegalStateException("Method isConst cannot be called on typed variables.");
  }

  boolean isParam() {
    throw new IllegalStateException("Method isParam cannot be called on typed variables.");
  }
}
