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

import static com.google.javascript.rhino.jstype.JSTypeNative.GLOBAL_THIS;

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticScope;
import com.google.javascript.rhino.jstype.StaticSlot;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scope contains information about a variable scope in javascript.
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
public class Scope implements StaticScope<JSType> {
  private final Map<String, Var> vars = new LinkedHashMap<String, Var>();
  private final Scope parent;
  private final Node rootNode;

  /** The type of {@code this} in the current scope. */
  private final ObjectType thisType;

  /** Whether this is a bottom scope for the purposes of type inference. */
  private final boolean isBottom;

  /** Stores info about a variable */
  public static class Var implements StaticSlot<JSType> {
    /** name */
    String name;

    /** Var node */
    Node nameNode;

    /**
     * The variable's type.
     */
    private JSType type;

    /**
     * The variable's doc info.
     */
    private JSDocInfo info = null;

    /**
     * Whether the variable's type has been inferred or is declared. An inferred
     * type may change over time (as more code is discovered), whereas a
     * declared type is a static contract that must be matched.
     */
    private final boolean typeInferred;

    /** Input source */
    CompilerInput input;

    /** Whether the variable is a define */
    boolean isDefine;

    /**
     * The index at which the var is declared. e..g if it's 0, it's the first
     * declared variable in that scope
     */
    int index;

    /** The enclosing scope */
    Scope scope;

    /**
     * Creates a variable.
     *
     * @param inferred whether its type is inferred (as opposed to declared)
     */
    private Var(boolean inferred) {
      this.typeInferred = inferred;
    }

    /**
     * Gets the name of the variable.
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the parent of the name node.
     */
    public Node getParentNode() {
      return nameNode == null ? null : nameNode.getParent();
    }

    /**
     * Gets the scope where this variable is declared.
     */
    Scope getScope() {
      return scope;
    }

    /**
     * Returns the index within the scope stack.
     * e.g. function Foo(a) { var b; function c(d) { } }
     * a = 0, b = 1, c = 2, d = 3
     */
    int getLocalVarIndex() {
      int num = index;
      Scope s = scope.getParent();
      if (s == null) {
        throw new IllegalArgumentException("Var is not local");
      }
      while (s.getParent() != null) {
        num += s.getVarCount();
        s = s.getParent();
      }
      return num;
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
    public boolean isConst() {
      return NodeUtil.isConstantName(nameNode);
    }

    /**
     * Returns {@code true} if the variable is declared as a define.
     * A variable is a define if it is annotaed by {@code @define}.
     */
    public boolean isDefine() {
      return isDefine;
    }

    public Node getInitialValue() {
      Node parent = getParentNode();
      int pType = parent.getType();
      if (pType == Token.FUNCTION) {
        return parent;
      } else if (pType == Token.ASSIGN) {
        return parent.getLastChild();
      } else if (pType == Token.VAR) {
        return nameNode.getFirstChild();
      } else {
        return null;
      }
    }

    /**
     * Gets this variable's type. To know whether this type has been inferred,
     * see {@code #isInferred()}.
     */
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
    public JSDocInfo getJSDocInfo() {
      return info;
    }

    /**
     * Sets this variable's type.
     * @throws IllegalStateException if the variable's type is not inferred
     */
    void setType(JSType type) {
      Preconditions.checkState(isTypeInferred());
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
    public boolean isTypeInferred() {
      return typeInferred;
    }

    public String getInputName() {
      if (input == null)
        return "<non-file>";
      else
        return input.getName();
    }

    public boolean isNoShadow() {
      if (info != null && info.isNoShadow()) {
        return true;
      } else {
        return false;
      }
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
      return "Scope.Var " + name;
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
    JSType nodeType = rootNode.getJSType();
    if (nodeType != null && nodeType instanceof FunctionType) {
      thisType = ((FunctionType) nodeType).getTypeOfThis();
    } else {
      thisType = parent.thisType;
    }
    this.isBottom = false;
  }


  /**
   * Creates a global Scope.
   * @param rootNode  Typically the global BLOCK node.
   */
  Scope(Node rootNode, AbstractCompiler compiler) {
    this.parent = null;
    this.rootNode = rootNode;
    thisType = compiler.getTypeRegistry().getNativeObjectType(GLOBAL_THIS);
    this.isBottom = false;
  }

  /**
   * Creates a empty Scope (bottom of the lattice).
   * @param rootNode Typically a FUNCTION node or the global BLOCK node.
   * @param thisType the type of {@code this} in this scope
   */
  Scope(Node rootNode, ObjectType thisType) {
    this.parent = null;
    this.rootNode = rootNode;
    this.thisType = thisType;
    this.isBottom = true;
  }

  /** Whether this is the bottom of the lattice. */
  boolean isBottom() {
    return isBottom;
  }

  /**
   * Gets the container node of the scope. This is typically the FUNCTION
   * node or the global BLOCK/SCRIPT node.
   */
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
  public ObjectType getTypeOfThis() {
    return thisType;
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
    Preconditions.checkState(name != null && name.length() > 0);

    // Make sure that it's declared only once
    Preconditions.checkState(vars.get(name) == null);

    Var var = new Var(inferred);
    var.name = name;
    var.nameNode = nameNode;
    var.type = type;
    var.scope = this;
    var.index = vars.size();
    var.input = input;

    // native variables do not have a name node.
    // TODO(user): make Var abstract and have NativeVar, NormalVar.
    JSDocInfo info = NodeUtil.getInfoForNameNode(nameNode);

    var.isDefine = info != null && info.isDefine();
    var.info = info;

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

  public StaticSlot<JSType> getSlot(String name) {
    return getVar(name);
  }

  public StaticSlot<JSType> getOwnSlot(String name) {
    return vars.get(name);
  }

  /**
   * Returns the variable, may be null
   */
  public Var getVar(String name) {
    Var var = vars.get(name);
    if (var != null) {
      return var;
    } else if (parent != null) { // Recurse up the parent Scope
      return parent.getVar(name);
    } else {
      return null;
    }
  }

  /**
   * Returns true if a variable is declared.
   */
  public boolean isDeclared(String name, boolean recurse) {
    Scope scope = this;
    if (scope.vars.containsKey(name))
      return true;

    if (scope.parent != null && recurse) {
      return scope.parent.isDeclared(name, recurse);
    }
    return false;
  }

  /**
   * Return an iterator over all of the variables declared in this scope.
   */
  public Iterator<Var> getVars() {
    return vars.values().iterator();
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
    return !isGlobal();
  }
}
