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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Iterables;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TypeIEnv;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import com.google.javascript.rhino.jstype.StaticTypedSlot;

/**
 * TypedScope contains information about variables and their types.
 * Scopes can be nested, a scope points back to its parent scope.
 * <p>
 * TypedScope is also used as a lattice element for flow-sensitive type inference.
 * As a lattice element, a scope is viewed as a map from names to types. A name
 * not in the map is considered to have the bottom type. The join of two maps m1
 * and m2 is the map of the union of names with {@link JSType#getLeastSupertype}
 * to meet the m1 type and m2 type.
 *
 * @see NodeTraversal
 * @see DataFlowAnalysis
 *
 * Several methods in this class, such as {@code isBlockScope} throw an exception when called.
 * The reason for this is that we want to shadow methods from the parent class, to avoid calling
 * them accidentally.
 */
public class TypedScope extends AbstractScope<TypedScope, TypedVar>
    implements StaticTypedScope<JSType>, TypeIEnv<JSType> {

  private final TypedScope parent;
  private final int depth;

  /** Whether this is a bottom scope for the purposes of type inference. */
  private final boolean isBottom;

  // Scope.java contains an arguments field.
  // We haven't added it here because it's unused by the passes that need typed scopes.

  TypedScope(TypedScope parent, Node rootNode) {
    super(rootNode);
    checkChildScope(parent);
    this.parent = parent;
    this.depth = parent.depth + 1;
    this.isBottom = false;
  }

  /**
   * Creates a empty Scope (bottom of the lattice).
   * @param rootNode Typically a FUNCTION node or the global BLOCK node.
   * @param isBottom Whether this is the bottom of a lattice. Otherwise,
   *     it must be a global scope.
   */
  private TypedScope(Node rootNode, boolean isBottom) {
    super(rootNode);
    checkRootScope();
    this.parent = null;
    this.depth = 0;
    this.isBottom = isBottom;
  }

  static TypedScope createGlobalScope(Node rootNode) {
    return new TypedScope(rootNode, false);
  }

  static TypedScope createLatticeBottom(Node rootNode) {
    return new TypedScope(rootNode, true);
  }

  @Override
  public TypedScope typed() {
    return this;
  }

  /** Whether this is the bottom of the lattice. */
  boolean isBottom() {
    return isBottom;
  }

  @Override
  public int getDepth() {
    return depth;
  }

  @Override
  public TypedScope getParent() {
    return parent;
  }

  /**
   * Gets the type of {@code this} in the current scope.
   */
  @Override
  public JSType getTypeOfThis() {
    if (isGlobal()) {
      return ObjectType.cast(getRootNode().getJSType());
    } else if (!getRootNode().isFunction()) {
      return getClosestContainerScope().getTypeOfThis();
    }

    checkState(getRootNode().isFunction());
    JSType nodeType = getRootNode().getJSType();
    if (nodeType != null && nodeType.isFunctionType()) {
      return nodeType.toMaybeFunctionType().getTypeOfThis();
    } else {
      // Executed when the current scope has not been typechecked.
      return null;
    }
  }

  TypedVar declare(String name, Node nameNode, JSType type, CompilerInput input) {
    return declare(name, nameNode, type, input, true);
  }

  TypedVar declare(String name, Node nameNode,
      JSType type, CompilerInput input, boolean inferred) {
    checkState(name != null && !name.isEmpty());
    TypedVar var = new TypedVar(inferred, name, nameNode, type, this, getVarCount(), input);
    declareInternal(name, var);
    return var;
  }

  @Override
  TypedVar makeImplicitVar(ImplicitVar var) {
    if (this.isGlobal()) {
      // TODO(sdh): This is incorrect for 'global this', but since that's currently not handled
      // by this code, it's okay to bail out now until we find the root cause.  See b/74980936.
      return null;
    }
    return new TypedVar(false, var.name, null, getImplicitVarType(var), this, -1, null);
  }

  private JSType getImplicitVarType(ImplicitVar var) {
    if (var == ImplicitVar.ARGUMENTS) {
      // Look for an extern named "arguments" and use its type if available.
      // TODO(sdh): consider looking for "Arguments" ctor rather than "arguments" var: this could
      // allow deleting the variable, which doesn't really belong in externs in the first place.
      TypedVar globalArgs = getGlobalScope().getVar(Var.ARGUMENTS);
      return globalArgs != null && globalArgs.isExtern()
          ? globalArgs.getType()
          : null;
    }
    // TODO(sdh): get the superclass for super?
    return getTypeOfThis();
  }

  public Iterable<TypedVar> getDeclarativelyUnboundVarsWithoutTypes() {
    return Iterables.filter(
        getVarIterable(),
        var ->
            // declaratively unbound vars without types
            var.getParentNode() != null
                && var.getType() == null
                && var.getParentNode().isVar()
                && !var.isExtern());
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

  @Override
  public JSType getNamespaceOrTypedefType(String typeName) {
    StaticTypedSlot<JSType> slot = getSlot(typeName);
    return slot == null ? null : slot.getType();
  }

  @Override
  public JSDocInfo getJsdocOfTypeDeclaration(String typeName) {
    StaticTypedSlot<JSType> slot = getSlot(typeName);
    return slot == null ? null : slot.getJSDocInfo();
  }
}
