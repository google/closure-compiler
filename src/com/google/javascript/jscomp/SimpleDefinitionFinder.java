/*
 * Copyright 2009 The Closure Compiler Authors.
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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.DefinitionsRemover.ExternalNameOnlyDefinition;
import com.google.javascript.jscomp.DefinitionsRemover.UnknownDefinition;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Simple name-based definition gatherer that implements
 * {@link DefinitionProvider}.
 *
 * It treats all variable writes as happening in the global scope and
 * treats all objects as capable of having the same set of properties.
 * The current implementation only handles definitions whose right
 * hand side is an immutable value or function expression.  All
 * complex definitions are treated as unknowns.
 *
 */
class SimpleDefinitionFinder implements CompilerPass, DefinitionProvider {
  private final AbstractCompiler compiler;
  private final Map<Node, DefinitionSite> definitionSiteMap;
  private final Multimap<String, Definition> nameDefinitionMultimap;
  private final Multimap<String, UseSite> nameUseSiteMultimap;

  public SimpleDefinitionFinder(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.definitionSiteMap = Maps.newLinkedHashMap();
    this.nameDefinitionMultimap = HashMultimap.create();
    this.nameUseSiteMultimap = HashMultimap.create();
  }

  /**
   * Returns the collection of definition sites found during traversal.
   *
   * @return definition site collection.
   */
  public Collection<DefinitionSite> getDefinitionSites() {
    return definitionSiteMap.values();
  }

  private DefinitionSite getDefinitionAt(Node node) {
    return definitionSiteMap.get(node);
  }

  DefinitionSite getDefinitionForFunction(Node function) {
    Preconditions.checkState(NodeUtil.isFunction(function));
    return getDefinitionAt(getNameNodeFromFunctionNode(function));
  }

  @Override
  public Collection<Definition> getDefinitionsReferencedAt(Node useSite) {
    if (definitionSiteMap.containsKey(useSite)) {
      return null;
    }

    if (NodeUtil.isGetProp(useSite)) {
      String propName = useSite.getLastChild().getString();
      if (propName.equals("apply") || propName.equals("call")) {
        useSite = useSite.getFirstChild();
      }
    }

    String name = getSimplifiedName(useSite);
    if (name != null) {
      Collection<Definition> defs = nameDefinitionMultimap.get(name);
      if (!defs.isEmpty()) {
        return defs;
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  @Override
  public void process(Node externs, Node source) {
    NodeTraversal.traverse(
        compiler, externs, new DefinitionGatheringCallback(true));
    NodeTraversal.traverse(
        compiler, source, new DefinitionGatheringCallback(false));
    NodeTraversal.traverse(
        compiler, source, new UseSiteGatheringCallback());
  }

  /**
   * Returns a collection of use sites that may refer to provided
   * definition.  Returns an empty collection if the definition is not
   * used anywhere.
   *
   * @param definition Definition of interest.
   * @return use site collection.
   */
  Collection<UseSite> getUseSites(Definition definition) {
    String name = getSimplifiedName(definition.getLValue());
    return nameUseSiteMultimap.get(name);
  }

  /**
   * Extract a name from a node.  In the case of GETPROP nodes,
   * replace the namespace or object expression with "this" for
   * simplicity and correctness at the expense of inefficiencies due
   * to higher chances of name collisions.
   *
   * TODO(user) revisit.  it would be helpful to at least use fully
   * qualified names in the case of namespaces.  Might not matter as
   * much if this pass runs after "collapsing properties".
   */
  private static String getSimplifiedName(Node node) {
    if (NodeUtil.isName(node)) {
      String name = node.getString();
      if (name != null && !name.isEmpty()) {
        return name;
      } else {
        return null;
      }
    } else if (NodeUtil.isGetProp(node)) {
      return "this." + node.getLastChild().getString();
    }
    return null;
  }

  private class DefinitionGatheringCallback extends AbstractPostOrderCallback {
    private boolean inExterns;

    DefinitionGatheringCallback(boolean inExterns) {
      this.inExterns = inExterns;
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
      // Arguments of external functions should not count as name
      // definitions.  They are placeholder names for documentation
      // purposes only which are not reachable from anywhere.
      if (inExterns && NodeUtil.isName(node) && parent.getType() == Token.LP) {
        return;
      }

      Definition def =
          DefinitionsRemover.getDefinition(node, inExterns);
      if (def != null) {
        String name = getSimplifiedName(def.getLValue());
        if (name != null) {
          Node rValue = def.getRValue();
          if ((rValue != null) &&
              !NodeUtil.isImmutableValue(rValue) &&
              !NodeUtil.isFunction(rValue)) {

            // Unhandled complex expression
            Definition unknownDef =
                new UnknownDefinition(def.getLValue(), inExterns);
            def = unknownDef;
          }

          // TODO(johnlenz) : remove this stub dropping code if it becomes
          // illegal to have untyped stubs in the externs definitions.
          if (inExterns) {
            // We need special handling of untyped externs stubs here:
            //    the stub should be dropped if the name is provided elsewhere.

            List<Definition> stubsToRemove = Lists.newArrayList();
            String qualifiedName = node.getQualifiedName();

            // If there is no qualified name for this, then there will be
            // no stubs to remove. This will happen if node is an object
            // literal key.
            if (qualifiedName != null) {
              for (Definition prevDef : nameDefinitionMultimap.get(name)) {
                if (prevDef instanceof ExternalNameOnlyDefinition
                    && !jsdocContainsDeclarations(node)) {
                  String prevName = prevDef.getLValue().getQualifiedName();
                  if (qualifiedName.equals(prevName)) {
                    // Drop this stub, there is a real definition.
                    stubsToRemove.add(prevDef);
                  }
                }
              }

              for (Definition prevDef : stubsToRemove) {
                nameDefinitionMultimap.remove(name, prevDef);
              }
            }
          }

          nameDefinitionMultimap.put(name, def);
          definitionSiteMap.put(node,
                                new DefinitionSite(node,
                                                   def,
                                                   traversal.getModule(),
                                                   traversal.inGlobalScope(),
                                                   inExterns));
        }
      }

      if (inExterns && (parent != null) && NodeUtil.isExpressionNode(parent)) {
        String name = getSimplifiedName(node);
        if (name != null) {

          // TODO(johnlenz) : remove this code if it becomes illegal to have
          // stubs in the externs definitions.

          // We need special handling of untyped externs stubs here:
          //    the stub should be dropped if the name is provided elsewhere.
          // We can't just drop the stub now as it needs to be used as the
          //    externs definition if no other definition is provided.

          boolean dropStub = false;
          if (!jsdocContainsDeclarations(node)) {
            String qualifiedName = node.getQualifiedName();
            if (qualifiedName != null) {
              for (Definition prevDef : nameDefinitionMultimap.get(name)) {
                String prevName = prevDef.getLValue().getQualifiedName();
                if (qualifiedName.equals(prevName)) {
                  dropStub = true;
                  break;
                }
              }
            }
          }

          if (!dropStub) {
            // Incomplete definition
            Definition definition = new ExternalNameOnlyDefinition(node);
            nameDefinitionMultimap.put(name, definition);
            definitionSiteMap.put(node,
                                  new DefinitionSite(node,
                                                     definition,
                                                     traversal.getModule(),
                                                     traversal.inGlobalScope(),
                                                     inExterns));
          }
        }
      }
    }

    /**
     * @return Whether the node has a JSDoc that actually declares something.
     */
    private boolean jsdocContainsDeclarations(Node node) {
      JSDocInfo info = node.getJSDocInfo();
      return (info != null && info.containsDeclaration());
    }
  }

  private class UseSiteGatheringCallback extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {

      Collection<Definition> defs = getDefinitionsReferencedAt(node);
      if (defs == null) {
        return;
      }

      Definition first = defs.iterator().next();

      String name = getSimplifiedName(first.getLValue());
      Preconditions.checkNotNull(name);
      nameUseSiteMultimap.put(
          name,
          new UseSite(node, traversal.getModule()));
    }
  }

  /**
   * @param use A use site to check.
   * @return Whether the use is a call or new.
   */
  static boolean isCallOrNewSite(UseSite use) {
    Node call = use.node.getParent();
    if (call == null) {
      // The node has been removed from the AST.
      return false;
    }
    // We need to make sure we're dealing with a call to the function we're
    // optimizing. If the the first child of the parent is not the site, this
    // is a nested call and it's a call to another function.
    return NodeUtil.isCallOrNew(call) && call.getFirstChild() == use.node;
  }

  /**
   * @return Whether the definition is directly exported.
   */
  static boolean maybeExported(
      AbstractCompiler compiler, Definition definition) {
    // Assume an exported method result is used.
    Node lValue = definition.getLValue();
    if (lValue == null) {
      return true;
    }

    String partialName;
    if (NodeUtil.isGetProp(lValue)) {
      partialName = lValue.getLastChild().getString();
    } else if (NodeUtil.isName(lValue)) {
      partialName = lValue.getString();
    } else {
      // GETELEM is assumed to be an export or other expression are unknown
      // uses.
      return true;
    }

    CodingConvention codingConvention = compiler.getCodingConvention();
    if (codingConvention.isExported(partialName)) {
      return true;
    }

    return false;
  }

  /**
   * @return Whether the function is defined in a non-aliasing expression.
   */
  static boolean isSimpleFunctionDeclaration(Node fn) {
    Node parent = fn.getParent();
    Node gramps = parent.getParent();

    // Simple definition finder doesn't provide useful results in some
    // cases, specifically:
    //  - functions with recursive definitions
    //  - functions defined in object literals
    //  - functions defined in array litersals
    // Here we defined a set of known function declaration that are 'ok'.

    // Some projects seem to actually define "JSCompiler_renameProperty"
    // rather than simply having an extern definition.  Don't mess with it.
    Node nameNode = SimpleDefinitionFinder.getNameNodeFromFunctionNode(fn);
    if (nameNode != null
        && NodeUtil.isName(nameNode)) {
      String name = nameNode.getString();
      if (name.equals(NodeUtil.JSC_PROPERTY_NAME_FN) ||
             name.equals(
                ObjectPropertyStringPreprocess.EXTERN_OBJECT_PROPERTY_STRING)) {
        return false;
      }
    }

    // example: function a(){};
    if (NodeUtil.isFunctionDeclaration(fn)) {
      return true;
    }

    // example: a = function(){};
    // example: var a = function(){};
    if (fn.getFirstChild().getString().isEmpty()
        && (NodeUtil.isExprAssign(gramps) || NodeUtil.isName(parent))) {
      return true;
    }

    return false;
  }

  /**
   * @return the node defining the name for this function (if any).
   */
  static Node getNameNodeFromFunctionNode(Node function) {
    Preconditions.checkState(NodeUtil.isFunction(function));
    if (NodeUtil.isFunctionDeclaration(function)) {
      return function.getFirstChild();
    } else {
      Node parent = function.getParent();
      if (NodeUtil.isVarDeclaration(parent)) {
        return parent;
      } else if (NodeUtil.isAssign(parent)) {
        return parent.getFirstChild();
      } else if (NodeUtil.isObjectLitKey(parent, parent.getParent())) {
        return parent;
      }
    }
    return null;
  }

  /**
   * Traverse a node and its children and remove any references to from
   * the structures.
   */
  void removeReferences(Node node) {
    if (DefinitionsRemover.isDefinitionNode(node)) {
      DefinitionSite defSite = definitionSiteMap.get(node);
      if (defSite != null) {
        Definition def = defSite.definition;
        String name = getSimplifiedName(def.getLValue());
        if (name != null) {
          this.definitionSiteMap.remove(node);
          this.nameDefinitionMultimap.remove(name, node);
        }
      }
    } else {
      Node useSite = node;
      if (NodeUtil.isGetProp(useSite)) {
        String propName = useSite.getLastChild().getString();
        if (propName.equals("apply") || propName.equals("call")) {
          useSite = useSite.getFirstChild();
        }
      }
      String name = getSimplifiedName(useSite);
      if (name != null) {
        this.nameUseSiteMultimap.remove(name, new UseSite(useSite, null));
      }
    }

    for (Node child : node.children()) {
      removeReferences(child);
    }
  }
}
