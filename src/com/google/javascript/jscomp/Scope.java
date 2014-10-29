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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticReference;
import com.google.javascript.rhino.jstype.StaticScope;
import com.google.javascript.rhino.jstype.StaticSlot;
import com.google.javascript.rhino.jstype.StaticSourceFile;
import com.google.javascript.rhino.jstype.StaticSymbolTable;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Scope contains information about a variable scope in JavaScript.
 * Scopes can be nested, a scope points back to its parent scope.
 * A Scope contains information about variables defined in that scope.
 * <p>
 * A Scope is also used as a lattice element for flow-sensitive type inference.
 * As a lattice element, a Scope is viewed as a map from names to types. A name
 * not in the map is considered to have the bottom type. The join of two maps m1
 * and m2 is the map of the union of names with {@link JSType#getLeastSupertype}
 * to meet the m1 type and m2 type.
 *
 * @see NodeTraversal
 * @see DataFlowAnalysis
 *
 */
public class Scope
    implements StaticScope<JSType>, StaticSymbolTable<Scope.Var, Scope.Var> {
  private final Map<String, Var> vars = new LinkedHashMap<>();
  private final Scope parent;
  private final int depth;
  private final Node rootNode;

  /** Whether this is a bottom scope for the purposes of type inference. */
  private final boolean isBottom;

  private Var arguments;

  private static final Predicate<Var> DECLARATIVELY_UNBOUND_VARS_WITHOUT_TYPES =
      new Predicate<Var>() {
    @Override public boolean apply(Var var) {
      return var.getParentNode() != null &&
          var.getType() == null && // no declared type
          var.getParentNode().isVar() &&
          !var.isExtern();
    }
  };

  /** Stores info about a variable */
  public static class Var
      implements StaticSlot<JSType>, StaticReference<JSType> {
    /** name */
    final String name;

    /** Var node */
    final Node nameNode;

    /**
     * The variable's type.
     */
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
     * The index at which the var is declared. e..g if it's 0, it's the first
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
    private Var(boolean inferred, String name, Node nameNode, JSType type,
                Scope scope, int index, CompilerInput input) {
      this.name = name;
      this.nameNode = nameNode;
      this.type = type;
      this.scope = scope;
      this.index = index;
      this.input = input;
      this.typeInferred = inferred;
    }

    /**
     * Gets the name of the variable.
     */
    @Override
    public String getName() {
      return name;
    }

    /**
     * Gets the node for the name of the variable.
     */
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

    /**
     * Gets the parent of the name node.
     */
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

    /**
     * Gets the scope where this variable is declared.
     */
    Scope getScope() {
      return scope;
    }

    /**
     * Returns whether this is a global variable.
     */
    public boolean isGlobal() {
      return scope.isGlobal();
    }

    /**
     * Returns whether this is a local variable.
     */
    public boolean isLocal() {
      return scope.isLocal();
    }

    /**
     * Returns whether this is defined in an extern file.
     */
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

      return nameNode.getBooleanProp(Node.IS_CONSTANT_VAR) ||
          nameNode.getBooleanProp(Node.IS_CONSTANT_NAME);
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

    /**
     * Returns the name node that produced this variable.
     */
    public Node getNameNode() {
      return nameNode;
    }

    /**
     * Gets the JSDocInfo for the variable.
     */
    @Override
    public JSDocInfo getJSDocInfo() {
      return nameNode == null ? null : NodeUtil.getBestJSDocInfo(nameNode);
    }

    /**
     * Sets this variable's type.
     */
    void setType(JSType type) {
      this.type = type;
    }

    /**
     * Resolve this variable's type.
     */
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

    @Override public boolean equals(Object other) {
      if (!(other instanceof Var)) {
        return false;
      }

      Var otherVar = (Var) other;
      return otherVar.nameNode == nameNode;
    }

    @Override public int hashCode() {
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

    @Override public boolean equals(Object other) {
      if (!(other instanceof Arguments)) {
        return false;
      }

      Arguments otherVar = (Arguments) other;
      return otherVar.scope.rootNode == scope.rootNode;
    }

    @Override public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  /**
   * Creates a Scope given the parent Scope and the root node of the scope.
   * @param parent  The parent Scope. Cannot be null.
   * @param rootNode  Typically the FUNCTION node.
   */
  Scope(Scope parent, Node rootNode) {
    Preconditions.checkNotNull(parent);
    Preconditions.checkArgument(rootNode != parent.rootNode);

    this.parent = parent;
    this.rootNode = rootNode;
    this.isBottom = false;
    this.depth = parent.depth + 1;
  }

  /**
   * Creates a empty Scope (bottom of the lattice).
   * @param rootNode Typically a FUNCTION node or the global BLOCK node.
   * @param isBottom Whether this is the bottom of a lattice. Otherwise,
   *     it must be a global scope.
   */
  private Scope(Node rootNode, boolean isBottom) {
    this.parent = null;
    this.rootNode = rootNode;
    this.isBottom = isBottom;
    this.depth = 0;
  }

  static Scope createGlobalScope(Node rootNode) {
    return new Scope(rootNode, false);
  }

  static Scope createLatticeBottom(Node rootNode) {
    return new Scope(rootNode, true);
  }

  /** The depth of the scope. The global scope has depth 0. */
  int getDepth() {
    return depth;
  }

  /** Whether this is the bottom of the lattice. */
  boolean isBottom() {
    return isBottom;
  }

  /**
   * Gets the container node of the scope. This is typically the FUNCTION
   * node or the global BLOCK/SCRIPT node.
   */
  @Override
  public Node getRootNode() {
    return rootNode;
  }

  public Scope getParent() {
    return parent;
  }

  Scope getGlobalScope() {
    Scope result = this;
    while (result.getParent() != null) {
      result = result.getParent();
    }
    return result;
  }

  @Override
  public StaticScope<JSType> getParentScope() {
    return parent;
  }

  /**
   * Gets the type of {@code this} in the current scope.
   */
  @Override
  public JSType getTypeOfThis() {
    if (isGlobal()) {
      return ObjectType.cast(rootNode.getJSType());
    }

    Preconditions.checkState(rootNode.isFunction());
    JSType nodeType = rootNode.getJSType();
    if (nodeType != null && nodeType.isFunctionType()) {
      return nodeType.toMaybeFunctionType().getTypeOfThis();
    } else {
      return parent.getTypeOfThis();
    }
  }

  /**
   * Declares a variable whose type is inferred.
   *
   * @param name name of the variable
   * @param nameNode the NAME node declaring the variable
   * @param type the variable's type
   * @param input the input in which this variable is defined.
   */
  Var declare(String name, Node nameNode, JSType type, CompilerInput input) {
    return declare(name, nameNode, type, input, true);
  }

  /**
   * Declares a variable.
   *
   * @param name name of the variable
   * @param nameNode the NAME node declaring the variable
   * @param type the variable's type
   * @param input the input in which this variable is defined.
   * @param inferred Whether this variable's type is inferred (as opposed
   *     to declared).
   */
  Var declare(String name, Node nameNode,
      JSType type, CompilerInput input, boolean inferred) {
    Preconditions.checkState(name != null && !name.isEmpty());

    // Make sure that it's declared only once
    Preconditions.checkState(vars.get(name) == null);

    Var var = new Var(inferred, name, nameNode, type, this, vars.size(), input);
    vars.put(name, var);
    return var;
  }

  /**
   * Undeclares a variable, to be used when the compiler optimizes out
   * a variable and removes it from the scope.
   */
  void undeclare(Var var) {
    Preconditions.checkState(var.scope == this);
    Preconditions.checkState(vars.get(var.name) == var);
    vars.remove(var.name);
  }

  @Override
  public Var getSlot(String name) {
    return getVar(name);
  }

  @Override
  public Var getOwnSlot(String name) {
    return vars.get(name);
  }

  /**
   * Returns the variable, may be null
   */
  public Var getVar(String name) {
    Scope scope = this;
    while (scope != null) {
      Var var = scope.vars.get(name);
      if (var != null) {
        return var;
      }
      // Recurse up the parent Scope
      scope = scope.parent;
    }
    return null;
  }

  /**
   * Get a unique VAR object to represents "arguments" within this scope
   */
  public Var getArgumentsVar() {
    if (arguments == null) {
      arguments = new Arguments(this);
    }
    return arguments;
  }

  /**
   * Returns true if a variable is declared.
   */
  public boolean isDeclared(String name, boolean recurse) {
    Scope scope = this;
    while (true) {
      if (scope.vars.containsKey(name)) {
        return true;
      }
      if (scope.parent != null && recurse) {
        scope = scope.parent;
        continue;
      }
      return false;
    }
  }

  /**
   * Return an iterator over all of the variables declared in this scope.
   */
  public Iterator<Var> getVars() {
    return vars.values().iterator();
  }

  /**
   * Return an iterable over all of the variables declared in this scope.
   */
  Iterable<Var> getVarIterable() {
    return vars.values();
  }

  @Override
  public Iterable<Var> getReferences(Var var) {
    return ImmutableList.of(var);
  }

  @Override
  public StaticScope<JSType> getScope(Var var) {
    return var.scope;
  }

  @Override
  public Iterable<Var> getAllSymbols() {
    return Collections.unmodifiableCollection(vars.values());
  }

  /**
   * Returns number of variables in this scope
   */
  public int getVarCount() {
    return vars.size();
  }

  /**
   * Returns whether this is the global scope.
   */
  public boolean isGlobal() {
    return parent == null;
  }

  /**
   * Returns whether this is a local scope (i.e. not the global scope).
   */
  public boolean isLocal() {
    return parent != null;
  }

  /**
   * Gets all variables declared with "var" but without declared types attached.
   */
  public Iterator<Var> getDeclarativelyUnboundVarsWithoutTypes() {
    return Iterators.filter(
        getVars(), DECLARATIVELY_UNBOUND_VARS_WITHOUT_TYPES);
  }

  static interface TypeResolver {
    void resolveTypes();
  }

  private TypeResolver typeResolver;

  /** Resolve all type references. Only used on typed scopes. */
  void resolveTypes() {
    if (typeResolver != null) {
      typeResolver.resolveTypes();
      typeResolver = null;
    }
  }

  void setTypeResolver(TypeResolver resolver) {
    this.typeResolver = resolver;
  }

  public boolean isBlockScope() {
    return NodeUtil.createsBlockScope(rootNode);
  }

  /**
   * A hoist scope is the hoist target for enclosing var declarations. It is
   * either the top-level block of a function, a global scope, or a module scope.
   *
   * TODO(moz): Module scopes are not global, but are also hoist targets.
   * Support them once module is implemented.
   *
   * @return Whether the scope is a hoist target for var declarations.
   */
  public boolean isHoistScope() {
    return isFunctionBlockScope() || isGlobal();
  }

  public boolean isFunctionBlockScope() {
    return isBlockScope() && parent != null && parent.getRootNode().isFunction();
  }

  public Scope getClosestHoistScope() {
    Scope current = this;
    while (current != null) {
      if (current.isHoistScope()) {
        return current;
      }
      current = current.parent;
    }
    return null;
  }
}
