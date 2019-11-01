/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A root pass that container for other passes that should run on with a single call graph.
 *
 * <p>Known passes include:
 *
 * <ul>
 *   <li>{@link OptimizeParameters} (remove unused and inline constant parameters)
 *   <li>{@link OptimizeReturns} (remove unused)
 *   <li>{@link DevirtualizeMethods}
 * </ul>
 */
class OptimizeCalls implements CompilerPass {

  private final AbstractCompiler compiler;
  private final ImmutableList<CallGraphCompilerPass> passes;
  private final boolean considerExterns;

  private OptimizeCalls(
      AbstractCompiler compiler,
      ImmutableList<CallGraphCompilerPass> passes,
      boolean considerExterns) {
    this.compiler = compiler;
    this.passes = passes;
    this.considerExterns = considerExterns;
  }

  static Builder builder() {
    return new Builder();
  }

  interface CallGraphCompilerPass {
    void process(Node externs, Node root, ReferenceMap references);
  }

  static final class Builder {
    private AbstractCompiler compiler;
    private ImmutableList.Builder<CallGraphCompilerPass> passes = ImmutableList.builder();
    @Nullable private Boolean considerExterns; // Nullable to force users to specify a value.

    public Builder setCompiler(AbstractCompiler compiler) {
      this.compiler = compiler;
      return this;
    }

    /**
     * Sets whether or not to include references to extern names and properties in the {@link
     * ReferenceMap} being generated.
     *
     * <p>If considered, references to externs in both extern code <em>and</em> executable code will
     * be collected. Otherwise, neither will be.
     *
     * <p>This setting allows extern references to be effectively invisible to passes that should
     * not mutate them.
     */
    public Builder setConsiderExterns(boolean b) {
      this.considerExterns = b;
      return this;
    }

    public Builder addPass(CallGraphCompilerPass pass) {
      this.passes.add(pass);
      return this;
    }

    public OptimizeCalls build() {
      checkNotNull(compiler);
      checkNotNull(considerExterns);

      return new OptimizeCalls(compiler, passes.build(), considerExterns);
    }

    private Builder() {}
  }

  @Override
  public void process(Node externs, Node root) {
    // Only global names are collected, which is insufficient if names have not been normalized.
    checkState(compiler.getLifeCycleStage() == LifeCycleStage.NORMALIZED);

    if (passes.isEmpty()) {
      return;
    }

    final ReferenceMap references = new ReferenceMap();
    NodeTraversal.traverseRoots(
        compiler, new ReferenceMapBuildingCallback(references), externs, root);
    eliminateAccessorsFrom(references);

    for (CallGraphCompilerPass pass : passes) {
      pass.process(externs, root, references);
    }
  }

  /**
   * Delete getter and setter names from {@code references}.
   *
   * <p>Accessor names are disqualified from being in the {@code ReferenceMap}. We don't
   * intentionally collect them, but other properties may share the same names. One reason why we do
   * this is exemplified below:
   *
   * <pre>{@code
   * class A {
   *   pure() { }
   * }
   *
   * class B {
   *   get pure() { return impure; }
   * }
   *
   * var x = (Math.random() > 0.5) ? new A() : new B();
   * x.pure(); // We can't safely optimize this call.
   * }</pre>
   */
  private void eliminateAccessorsFrom(ReferenceMap references) {
    references.props.keySet().removeAll(compiler.getAccessorSummary().getAccessors().keySet());
  }

  /** A reference map for global symbols and properties. */
  static class ReferenceMap {
    private Scope globalScope;
    private final LinkedHashMap<String, ArrayList<Node>> names = new LinkedHashMap<>();
    private final LinkedHashMap<String, ArrayList<Node>> props = new LinkedHashMap<>();

    private void addReference(LinkedHashMap<String, ArrayList<Node>> data, String name, Node n) {
      ArrayList<Node> refs = data.get(name);
      if (refs == null) {
        refs = new ArrayList<>();
        data.put(name, refs);
      }
      refs.add(n);
    }

    void addNameReference(String name, Node n) {
      addReference(names, name, n);
    }

    void addPropReference(String name, Node n) {
      addReference(props, name, n);
    }

    Scope getGlobalScope() {
      return globalScope;
    }

    Iterable<Map.Entry<String, ArrayList<Node>>> getNameReferences() {
      return names.entrySet();
    }

    Iterable<Map.Entry<String, ArrayList<Node>>> getPropReferences() {
      return props.entrySet();
    }

    /**
     * Given a set of references, returns the set of known definitions; specifically, those of the
     * form: `function x() { }` or `x = ...;`
     *
     * <p>As much as possible, functions are collected from conditional definitions. This is useful
     * for optimizations that can be performed when the callers are known but all definitions may
     * not be (unused call results, parameters that are never provided). Examples expressions:
     *
     * <ul>
     *   <li>`(a(), function() {})`
     *   <li>`a && function(){}`
     *   <li>`b || function(){}`
     *   <li>`a ? function() {} : function() {}`
     * </ul>
     *
     * @param definitionSites The definition site nodes to search for associated functions. These
     *     should taken from a {@link ReferenceMap} since only the node types collected by {@link
     *     ReferenceMap} are supported.
     * @return A mapping from the input {@code definitionSites} to each of their associated
     *     functions.
     */
    static ImmutableListMultimap<Node, Node> getFunctionNodes(List<Node> definitionSites) {
      ImmutableListMultimap.Builder<Node, Node> result = ImmutableListMultimap.builder();
      for (Node def : definitionSites) {
        result.putAll(def, definitionFunctionNodesFor(def));
      }
      return result.build();
    }

    /**
     * Collects potential definition FUNCTIONs associated with a method definition site.
     *
     * @see {@link #getFunctionNodes()}
     */
    private static ImmutableList<Node> definitionFunctionNodesFor(Node definitionSite) {
      if (definitionSite.isGetterDef() || definitionSite.isSetterDef()) {
        // TODO(nickreid): Support getters and setters. Ignore them for now since they aren't
        // "called".
        return ImmutableList.of();
      }

      // Ignore detached nodes.
      Node parent = definitionSite.getParent();
      if (parent == null) {
        return ImmutableList.of();
      }

      ImmutableList.Builder<Node> fns = ImmutableList.builder();
      switch (parent.getToken()) {
        case FUNCTION:
          fns.add(parent);
          break;

        case CLASS_MEMBERS:
          checkArgument(definitionSite.isMemberFunctionDef(), definitionSite);
          fns.add(definitionSite.getLastChild());
          break;

        case OBJECTLIT:
          checkArgument(
              definitionSite.isStringKey() || definitionSite.isMemberFunctionDef(), //
              definitionSite);
          addValueFunctionNodes(fns, definitionSite.getLastChild());
          break;

        case ASSIGN:
          // Only a candidate if the assign isn't consumed.
          Node target = parent.getFirstChild();
          Node value = parent.getLastChild();
          if (definitionSite == target) {
            addValueFunctionNodes(fns, value);
          }
          break;

        case CONST:
        case LET:
        case VAR:
          if (definitionSite.isName() && definitionSite.hasChildren()) {
            addValueFunctionNodes(fns, definitionSite.getFirstChild());
          }
          break;

        default:
          break;
      }
      return fns.build();
    }

    private static void addValueFunctionNodes(ImmutableList.Builder<Node> fns, Node n) {
      // TODO(johnlenz): add member definitions
      switch (n.getToken()) {
        case FUNCTION:
          fns.add(n);
          break;
        case HOOK:
          addValueFunctionNodes(fns, n.getSecondChild());
          addValueFunctionNodes(fns, n.getLastChild());
          break;
        case OR:
        case AND:
          addValueFunctionNodes(fns, n.getFirstChild());
          addValueFunctionNodes(fns, n.getLastChild());
          break;
        case CAST:
        case COMMA:
          addValueFunctionNodes(fns, n.getLastChild());
          break;

        default:
          // do nothing.
          break;
      }
    }

    /**
     * Whether the provided node acts as the target function in a new or call expression including
     * .call expressions. For example, returns true for 'x' in 'x.call()'.
     */
    static boolean isCallOrNewTarget(Node n) {
      return isCallTarget(n) || isNewTarget(n);
    }

    /**
     * Whether the provided node acts as the target function in a call expression including .call
     * expressions. For example, returns true for 'x' in 'x.call()'.
     */
    static boolean isCallTarget(Node n) {
      Node parent = n.getParent();
      return ((parent.getFirstChild() == n) && parent.isCall())
          || (parent.isGetProp()
              && parent.getParent().isCall()
              && parent.getLastChild().getString().equals("call"));
    }

    /** Whether the provided node acts as the target function in a new expression. */
    static boolean isNewTarget(Node n) {
      Node parent = n.getParent();
      return parent.isNew() && parent.getFirstChild() == n;
    }

    /** Finds the associated call node for a node for which isCallOrNewTarget returns true. */
    static Node getCallOrNewNodeForTarget(Node n) {
      Node maybeCall = n.getParent();
      checkState(n.isFirstChildOf(maybeCall), "%s\n\n%s", maybeCall, n);

      if (NodeUtil.isCallOrNew(maybeCall)) {
        return maybeCall;
      } else {
        Node child = maybeCall;
        maybeCall = child.getParent();

        checkState(child.isGetProp(), child);
        checkState(maybeCall.isCall(), maybeCall);
        checkState(child.isFirstChildOf(maybeCall), "%s\n\n%s", maybeCall, child);

        return maybeCall;
      }
    }

    /**
     * Finds the call argument node matching the first parameter of the called function for a node
     * for which isCallOrNewTarget returns true. Specifically, corrects for the additional argument
     * provided to .call expressions.
     */
    static Node getFirstArgumentForCallOrNewOrDotCall(Node n) {
      return getArgumentForCallOrNewOrDotCall(n, 0);
    }

    /**
     * Finds the call argument node matching the parameter at the specified index of the called
     * function for a node for which isCallOrNewTarget returns true. Specifically, corrects for the
     * additional argument provided to .call expressions.
     */
    static Node getArgumentForCallOrNewOrDotCall(Node n, int index) {
      int adjustedIndex = index;
      Node parent = n.getParent();
      if (!(parent.isCall() || parent.isNew())) {
        parent = parent.getParent();
        if (NodeUtil.isFunctionObjectCall(parent)) {
          adjustedIndex++;
        }
      }
      return NodeUtil.getArgumentForCallOrNew(parent, adjustedIndex);
    }

    static boolean isSimpleAssignmentTarget(Node n) {
      Node parent = n.getParent();
      return parent.isAssign() && n == parent.getFirstChild();
    }
  }

  private static Set<String> safeSet(@Nullable Set<String> set) {
    return (set != null) ? ImmutableSet.copyOf(set) : ImmutableSet.of();
  }

  private final class ReferenceMapBuildingCallback implements ScopedCallback {
    final Set<String> externProps;
    final ReferenceMap references;
    private Scope globalScope;

    /**
     * @param compiler
     * @param references
     */
    public ReferenceMapBuildingCallback(ReferenceMap references) {
      this.externProps = safeSet(compiler.getExternProperties());
      this.references = references;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node unused) {
      switch (n.getToken()) {
        case NAME:
          maybeAddNameReference(n);
          break;

        case GETPROP:
          maybeAddPropReference(n.getLastChild().getString(), n);
          break;

        case STRING_KEY:
        case GETTER_DEF:
        case SETTER_DEF:
        case MEMBER_FUNCTION_DEF:
          // ignore quoted keys.
          if (!n.isQuotedString()) {
            maybeAddPropReference(n.getString(), n);
          }
          break;

        case COMPUTED_PROP:
        case GETELEM:
          // Ignore quoted keys.
          // TODO(johnlenz): support symbols.
        case OBJECT_REST:
        case OBJECT_SPREAD:
          // Don't worry about invisible accesses using these. To be invoked there would need to be
          // downstream references that use the actual name. We'd see those.
        default:
          break;
      }
    }

    private void maybeAddNameReference(Node n) {
      String name = n.getString();
      // TODO(b/129503101): Why are we limiting ourselves to global names?
      Var var = globalScope.getSlot(name);
      if (var != null && (considerExterns || !var.isExtern())) {
        // As every name declaration is unique due to normalizations, it is only necessary to build
        // the global scope and ask it if it knows about a name as it can never be shadowed.
        references.addNameReference(name, n);
      }
    }

    private void maybeAddPropReference(String name, Node n) {
      if (considerExterns || !externProps.contains(name)) {
        references.addPropReference(name, n);
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        // Even when considering externs, we only care about top-level identifiers. Dummy function
        // parameters, for example, shouldn't be considered references.
        return (considerExterns && t.inGlobalScope()) || !n.isFromExterns();
      } else {
        return true;
      }
    }

    @Override
    public void enterScope(NodeTraversal t) {
      if (t.inGlobalScope()) {
        this.globalScope = t.getScope();
        references.globalScope = this.globalScope;
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {}
  }

  /** @return Whether the provide name may be a candidate for call optimizations. */
  static boolean mayBeOptimizableName(AbstractCompiler compiler, String name) {
    if (compiler.getCodingConvention().isExported(name)) {
      return false;
    }

    // Avoid modifying a few special case functions. Specifically, $jscomp.inherits to
    // recognize 'inherits' calls. (b/27244988)
    if (name.equals(NodeUtil.JSC_PROPERTY_NAME_FN)
        || name.equals("inherits")
        || name.equals("$jscomp$inherits")
        || name.equals("goog$inherits")) {
      return false;
    }
    return true;
  }

  /** @return Whether the reference is a known non-aliasing reference. */
  static boolean isAllowedReference(Node n) {
    Node parent = n.getParent();
    switch (parent.getToken()) {
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
        // inspecting the properties is allowed.
        return parent.getSecondChild() == n;
      case INSTANCEOF:
      case TYPEOF:
      case IN:
        return true;
      case GETELEM:
      case GETPROP:
        // Calls escape the "this" value. a.foo() aliases "a" as "this" but general
        // property references do not.
        Node grandparent = parent.getParent();
        if (n == parent.getFirstChild() && grandparent != null && grandparent.isCall()) {
          return false;
        }
        return true;
      default:
        if (NodeUtil.isNameDeclaration(parent) && !n.hasChildren()) {
          // allow "let x;"
          return true;
        }
    }
    return false;
  }
}
