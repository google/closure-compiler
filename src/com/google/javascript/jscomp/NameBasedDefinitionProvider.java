/*
 * Copyright 2016 The Closure Compiler Authors.
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
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.DefinitionsRemover.ExternalNameOnlyDefinition;
import com.google.javascript.jscomp.DefinitionsRemover.UnknownDefinition;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Simple name-based definition gatherer that implements {@link DefinitionProvider}.
 *
 * <p>It treats all variable writes as happening in the global scope and treats all objects as
 * capable of having the same set of properties. The current implementation only handles definitions
 * whose right hand side is an immutable value or function expression. All complex definitions are
 * treated as unknowns.
 *
 * <p>This definition simply uses the variable name to determine a new definition site so
 * potentially it could return multiple definition sites for a single variable.  Although we could
 * use the type system to make this more accurate, in practice after disambiguate properties has
 * run, names are unique enough that this works well enough to accept the performance gain.
 */
public class NameBasedDefinitionProvider implements DefinitionProvider, CompilerPass {
  protected final Multimap<String, Definition> nameDefinitionMultimap = LinkedHashMultimap.create();
  protected final Map<Node, DefinitionSite> definitionNodeByDefinitionSite = new LinkedHashMap<>();
  protected final Set<Node> definitionNodes = new HashSet<>();
  protected final AbstractCompiler compiler;
  protected final boolean allowComplexFunctionDefs;

  protected boolean hasProcessBeenRun = false;


  public NameBasedDefinitionProvider(AbstractCompiler compiler, boolean allowComplexFunctionDefs) {
    this.compiler = compiler;
    this.allowComplexFunctionDefs = allowComplexFunctionDefs;
  }

  @Override
  public void process(Node externs, Node source) {
    Preconditions.checkState(!hasProcessBeenRun, "The definition provider is already initialized.");
    this.hasProcessBeenRun = true;

    NodeTraversal.traverseEs6(compiler, externs, new DefinitionGatheringCallback(true));
    dropUntypedExterns();

    NodeTraversal.traverseEs6(compiler, source, new DefinitionGatheringCallback(false));
  }

  /** @return Whether the node has a JSDoc that actually declares something. */
  private boolean jsdocContainsDeclarations(Node node) {
    JSDocInfo info = node.getJSDocInfo();
    return (info != null && info.containsDeclaration());
  }

  /**
   * Drop untyped stub definitions (ExternalNameOnlyDefinition) in externs if a typed extern of the
   * same qualified name also exists and has type annotations.
   *
   * <p>TODO: This hack is mostly for the purpose of preventing untyped stubs from showing up in the
   * {@link PureFunctionIdentifier} and causing unkown side effects from propagating everywhere.
   * This should probably be solved in one of the following ways instead:
   *
   * <p>a) Have a pass ealier in the compiler that goes in and removes these stub definitions.
   *
   * <p>b) Fix all extern files so that there are no untyped stubs mixed with typed ones and add a
   * restriction to the compiler to prevent this.
   *
   * <p>c) Drop these stubs in the {@link PureFunctionIdentifier} instead. This "DefinitionProvider"
   * should not have to drop definitions itself.
   */
  private void dropUntypedExterns() {
    for (String externName : nameDefinitionMultimap.keys()) {
      for (Definition def : new ArrayList<Definition>(nameDefinitionMultimap.get(externName))) {
        if (def instanceof ExternalNameOnlyDefinition) {
          Node node = def.getLValue();
          if (!jsdocContainsDeclarations(node)) {
            for (Definition prevDef : nameDefinitionMultimap.get(externName)) {
              if (prevDef != def && node.matchesQualifiedName(prevDef.getLValue())) {
                nameDefinitionMultimap.remove(externName, def);
                // Since it's a stub we know its keyed by the name/getProp node.
                Preconditions.checkState(
                    definitionNodeByDefinitionSite.containsKey(def.getLValue()));
                definitionNodeByDefinitionSite.remove(def.getLValue());
                break;
              }
            }
          }
        }
      }
    }
  }

  @Override
  public Collection<Definition> getDefinitionsReferencedAt(Node useSite) {
    Preconditions.checkState(hasProcessBeenRun, "The process was not run");
    Preconditions.checkArgument(useSite.isGetProp() || useSite.isName());
    if (definitionNodes.contains(useSite)) {
      return null;
    }

    if (useSite.isGetProp()) {
      String propName = useSite.getLastChild().getString();
      if (propName.equals("apply") || propName.equals("call")) {
        useSite = useSite.getFirstChild();
      }
    }

    String name = getSimplifiedName(useSite);
    if (name != null) {
      Collection<Definition> defs = nameDefinitionMultimap.get(name);
      return defs.isEmpty() ? null : defs;
    }
    return null;
  }

  private class DefinitionGatheringCallback implements Callback {
    private final boolean inExterns;

    DefinitionGatheringCallback(boolean inExterns) {
      this.inExterns = inExterns;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (inExterns) {
        if (n.isFunction() && !n.getFirstChild().isName()) {
          // No need to crawl functions in JSDoc
          return false;
        }
        if (parent != null && parent.isFunction() && n != parent.getFirstChild()) {
          // Arguments of external functions should not count as name
          // definitions.  They are placeholder names for documentation
          // purposes only which are not reachable from anywhere.
          return false;
        }
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
      if (inExterns) {
        visitExterns(traversal, node, parent);
      } else {
        visitCode(traversal, node);
      }
    }

    private void visitExterns(NodeTraversal traversal, Node node, Node parent) {
      if (node.getJSDocInfo() != null) {
        for (Node typeRoot : node.getJSDocInfo().getTypeNodes()) {
          traversal.traverse(typeRoot);
        }
      }

      Definition def = DefinitionsRemover.getDefinition(node, true);
      if (def != null) {
        String name = getSimplifiedName(def.getLValue());
        if (name != null) {
          Node rValue = def.getRValue();
          if ((rValue != null) && !NodeUtil.isImmutableValue(rValue) && !rValue.isFunction()) {
            // Unhandled complex expression
            Definition unknownDef = new UnknownDefinition(def.getLValue(), true);
            def = unknownDef;
          }
          addDefinition(name, def, node, traversal);
        }
      }
    }

    private void visitCode(NodeTraversal traversal, Node node) {
      Definition def = DefinitionsRemover.getDefinition(node, false);

      if (def != null) {
        String name = getSimplifiedName(def.getLValue());
        if (name != null) {
          Node rValue = def.getRValue();
          if (rValue != null
              && !NodeUtil.isImmutableValue(rValue)
              && !isKnownFunctionDefinition(rValue)) {
            // Unhandled complex expression
            def = new UnknownDefinition(def.getLValue(), false);
          }
          addDefinition(name, def, node, traversal);
        }
      }
    }

    boolean isKnownFunctionDefinition(Node n) {
      switch (n.getToken()) {
        case FUNCTION:
          return true;
        case HOOK:
          return allowComplexFunctionDefs
              && isKnownFunctionDefinition(n.getSecondChild())
              && isKnownFunctionDefinition(n.getLastChild());
        default:
          return false;
      }
    }
  }

  private void addDefinition(String name, Definition def, Node node, NodeTraversal traversal) {
    definitionNodes.add(def.getLValue());
    nameDefinitionMultimap.put(name, def);
    definitionNodeByDefinitionSite.put(
        node,
        new DefinitionSite(
            node, def, traversal.getModule(), traversal.inGlobalScope(), def.isExtern()));
  }

  /**
   * Extract a name from a node. In the case of GETPROP nodes, replace the namespace or object
   * expression with "this" for simplicity and correctness at the expense of inefficiencies due to
   * higher chances of name collisions.
   *
   * <p>TODO(user) revisit. it would be helpful to at least use fully qualified names in the case of
   * namespaces. Might not matter as much if this pass runs after {@link CollapseProperties}.
   */
  protected static String getSimplifiedName(Node node) {
    if (node.isName()) {
      String name = node.getString();
      if (name != null && !name.isEmpty()) {
        return name;
      } else {
        return null;
      }
    } else if (node.isGetProp()) {
      return "this." + node.getLastChild().getString();
    }
    return null;
  }


  /**
   * Returns the collection of definition sites found during traversal.
   *
   * @return definition site collection.
   */
  public Collection<DefinitionSite> getDefinitionSites() {
    Preconditions.checkState(hasProcessBeenRun, "The process was not run");
    return definitionNodeByDefinitionSite.values();
  }

  public DefinitionSite getDefinitionForFunction(Node function) {
    Preconditions.checkState(hasProcessBeenRun, "The process was not run");
    Preconditions.checkState(function.isFunction());
    return definitionNodeByDefinitionSite.get(NodeUtil.getNameNode(function));
  }
}
