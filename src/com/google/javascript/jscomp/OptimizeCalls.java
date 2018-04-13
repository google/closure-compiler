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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A root pass that container for other passes that should run on
 * with a single call graph (currently a DefinitionUseSiteFinder).
 * Expected passes include:
 *   - optimize parameters (unused and constant parameters)
 *   - optimize returns (unused)
 *
 * @author johnlenz@google.com (John Lenz)
 */
class OptimizeCalls implements CompilerPass {
  private final List<CallGraphCompilerPass> passes = new ArrayList<>();
  private final AbstractCompiler compiler;

  OptimizeCalls(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  interface CallGraphCompilerPass {
    void process(Node externs, Node root, ReferenceMap references);
  }

  OptimizeCalls addPass(CallGraphCompilerPass pass) {
    passes.add(pass);
    return this;
  }

  @Override
  public void process(Node externs, Node root) {
    if (!passes.isEmpty()) {
      ReferenceMap refMap = buildPropAndGlobalNameReferenceMap(
          compiler, externs, root);
      for (CallGraphCompilerPass pass : passes) {
        pass.process(externs, root, refMap);
      }
    }
  }

  /**
   * A reference map for global symbols and properties.
   */
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
     * Given a set of references, returns the set of known definitions. Specifically,
     * those of the form:
     *    function x() { }
     * or
     *    x = ...;
     *
     * As much as possible, functions are collected from conditional definitions. This
     * is useful for optimizations that can be performed when the callers are known but
     * all definitions may not be (unused call results, parameters that are never provided).
     * Examples expressions:
     *
     *   (a(), function() {})
     *   a && function(){}
     *   b || function(){}
     *   a ? function() {} : function() {}
     *
     */
    static ArrayList<Node> getFunctionNodes(List<Node> references) {
      ArrayList<Node> fns = new ArrayList<>();
      for (Node n : references) {
        addDefinitionFunctionNodes(fns, n);
      }
      return fns;
    }

    private static void addDefinitionFunctionNodes(ArrayList<Node> fns, Node n) {
      Node parent = n.getParent();
      if (parent != null) {
        switch (parent.getToken()) {
          case FUNCTION:
            fns.add(parent);
            break;
          case CLASS_MEMBERS:
            if (n.isMemberFunctionDef()) {
              fns.add(n.getLastChild());
            }
            break;
          case ASSIGN:
            // Only a candidate if the assign isn't consumed.
            Node target = parent.getFirstChild();
            Node value = parent.getLastChild();
            if (n == target) {
              addValueFunctionNodes(fns, value);
            }
            break;
          case CONST:
          case LET:
          case VAR:
            if (n.isName() && n.hasChildren()) {
              addValueFunctionNodes(fns, n.getFirstChild());
            }
            break;
          default:
            break;
        }
      }
    }

    private static void addValueFunctionNodes(ArrayList<Node> fns, Node n) {
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
     * .call expressions.  For example, returns true for 'x' in 'x.call()'.
     */
    static boolean isCallOrNewTarget(Node n) {
      return isCallTarget(n) || isNewTarget(n);
    }

    /**
     * Whether the provided node acts as the target function in a call expression including
     * .call expressions.  For example, returns true for 'x' in 'x.call()'.
     */
    static boolean isCallTarget(Node n) {
      Node parent = n.getParent();
      return ((parent.getFirstChild() == n) && parent.isCall())
          || (parent.isGetProp()
              && parent.getParent().isCall()
              && parent.getLastChild().getString().equals("call"));
    }

    /**
     * Whether the provided node acts as the target function in a new expression.
     */
    static boolean isNewTarget(Node n) {
      Node parent = n.getParent();
      return parent.isNew() && parent.getFirstChild() == n;
    }

    /**
     * Finds the associated call node for a node for which isCallOrNewTarget returns true.
     */
    static Node getCallOrNewNodeForTarget(Node n) {
      Node maybeCall = n.getParent();
      checkState(maybeCall.getFirstChild() == n);
      if (NodeUtil.isCallOrNew(maybeCall)) {
        return maybeCall;
      } else {
        Node child = maybeCall;
        maybeCall = child.getParent();
        checkState(
            child.isGetProp() && maybeCall.isCall() && maybeCall.getFirstChild() == child, child);
        return maybeCall;
      }
    }

    /**
     * Finds the call argument node matching the first parameter of the called function for a node
     * for which isCallOrNewTarget returns true.  Specifically, corrects for the additional
     * argument provided to .call expressions.
     */
    static Node getFirstArgumentForCallOrNewOrDotCall(Node n) {
      return getArgumentForCallOrNewOrDotCall(n, 0);
    }

    /**
     * Finds the call argument node matching the parameter at the specified index of the called
     * function for a node for which isCallOrNewTarget returns true.  Specifically, corrects for
     * the additional argument provided to .call expressions.
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

  static class ReferenceMapBuildingCallback implements ScopedCallback {
    AbstractCompiler compiler;
    final Set<String> externProps;
    final ReferenceMap references;
    private Scope globalScope;

    /**
     * @param compiler
     * @param references
     */
    public ReferenceMapBuildingCallback(AbstractCompiler compiler, ReferenceMap references) {
      this.compiler = compiler;
      this.externProps = safeSet(compiler.getExternProperties());
      this.references = references;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case NAME:
          maybeAddNameReference(n);
          break;

        case COMPUTED_PROP:
          // TODO(johnlenz): support symbols.
          break;
        case GETELEM:
          // ignore quoted keys.
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

        // TODO(johnlenz): object destructuring.

        default:
          break;
      }
    }

    private void maybeAddNameReference(Node n) {
      String name = n.getString();
      if (isGlobalNonExternNameReference(name)) {
        references.addNameReference(name, n);
      }
    }

    private void maybeAddPropReference(String name, Node n) {
      if (!externProps.contains(name)) {
        references.addPropReference(name, n);
      }
    }

    // As every name declaration is unique due to normalizations, it is only necessary to build
    // the global scope and ask it if it knows about a name as it can never be shadowed.
    private boolean isGlobalNonExternNameReference(String name) {
      Var v = globalScope.getSlot(name);
      return  v != null && !v.isExtern();
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isScript() || !t.getInput().isExtern();
    }

    @Override
    public void enterScope(NodeTraversal t) {
      if (t.inGlobalScope()) {
        this.globalScope = t.getScope();
        references.globalScope = this.globalScope;
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {
    }
  }

  static ReferenceMap buildPropAndGlobalNameReferenceMap(
      AbstractCompiler compiler, Node externs, Node root) {
    final ReferenceMap references = new ReferenceMap();
    NodeTraversal.traverseRoots(compiler, new ReferenceMapBuildingCallback(
        compiler, references), externs, root);
    return references;
  }

  /**
   * @return Whether the provide name may be a candidate for
   *    call optimizations.
   */
  static boolean mayBeOptimizableName(AbstractCompiler compiler, String name) {
    if (compiler.getCodingConvention().isExported(name)) {
      return false;
    }

    // Avoid modifying a few special case functions. Specifically, $jscomp.inherits to
    // recognize 'inherits' calls. (b/27244988)
    if (name.equals(NodeUtil.JSC_PROPERTY_NAME_FN)
        || name.equals(NodeUtil.EXTERN_OBJECT_PROPERTY_STRING)
        || name.equals("inherits")
        || name.equals("$jscomp$inherits")
        || name.equals("goog$inherits")) {
      return false;
    }
    return true;
  }

  /**
   * @return Whether the reference is a known non-aliasing reference.
   */
  static boolean isAllowedReference(Node n) {
    Node parent = n.getParent();
    switch (parent.getToken()) {
      case FOR_IN:
      case FOR_OF:
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
