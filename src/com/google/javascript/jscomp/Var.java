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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticRef;
import com.google.javascript.rhino.StaticSlot;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;

/**
 * Used by {@code Scope} to store information about variables.
 */
public class Var implements StaticSlot, StaticRef {
  final String name;

  /** Var node */
  final Node nameNode;

  /** Input source */
  final CompilerInput input;

  /**
   * The index at which the var is declared. e.g. if it's 0, it's the first
   * declared variable in that scope
   */
  final int index;

  /** The enclosing scope */
  final Scope scope;

  Var(String name, Node nameNode, Scope scope, int index, CompilerInput input) {
    this.name = name;
    this.nameNode = nameNode;
    this.scope = scope;
    this.index = index;
    this.input = input;
  }

  static Var makeArgumentsVar(Scope scope) {
    checkArgument(!(scope instanceof TypedScope));
    return new Arguments(scope);
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

  public Scope getScope() {
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

  public Node getNameNode() {
    return nameNode;
  }

  @Override
  public JSDocInfo getJSDocInfo() {
    return nameNode == null ? null : NodeUtil.getBestJSDocInfo(nameNode);
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
    return "Var " + name + " @ " + nameNode;
  }

  boolean isVar() {
    return declarationType() == Token.VAR;
  }

  boolean isCatch() {
    return declarationType() == Token.CATCH;
  }

  boolean isLet() {
    return declarationType() == Token.LET;
  }

  boolean isConst() {
    return declarationType() == Token.CONST;
  }

  boolean isClass() {
    return declarationType() == Token.CLASS;
  }

  boolean isParam() {
    return declarationType() == Token.PARAM_LIST;
  }

  boolean isDefaultParam() {
    Node parent = nameNode.getParent();
    return parent.getParent().isParamList() && parent.isDefaultValue()
        && parent.getFirstChild() == nameNode;
  }
  
  boolean isImport() { 
    return declarationType() == Token.IMPORT; 
  }

  public boolean isArguments() {
    return false;
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

  protected Token declarationType() {
    for (Node current = nameNode; current != null;
         current = current.getParent()) {
      if (DECLARATION_TYPES.contains(current.getToken())) {
        return current.getToken();
      }
    }
    throw new IllegalStateException("The nameNode for " + this + " must be a descendant"
        + " of one of: " + DECLARATION_TYPES);
  }


  /**
   * A special subclass of Var used to distinguish "arguments" in the current
   * scope.
   */
  private static class Arguments extends Var {
    Arguments(Scope scope) {
      super(
          "arguments", // always arguments
          null,  // no declaration node
          scope,
          -1,    // no variable index
          null   // input
      );
    }

    @Override
    public boolean isArguments() {
      return true;
    }

    @Override
    public StaticSourceFile getSourceFile() {
      return scope.getRootNode().getStaticSourceFile();
    }

    @Override
    public boolean isBleedingFunction() {
      return false;
    }

    @Override
    protected Token declarationType() {
      return null;
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
