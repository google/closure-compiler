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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.List;

/**
 * Built on top of the {@link NameBasedDefinitionProvider}, this class additionally collects the use
 * sites for each definition. It is useful for constructing a full reference graph of the entire
 * ast.
 *
 */
// TODO(stalcup): track useSites in lhs of GET_ELEM nodes as well.
public class DefinitionUseSiteFinder extends NameBasedDefinitionProvider {

  private static class NameAndUseSite {
    final String name;
    final UseSite useSite;

    NameAndUseSite(String name, UseSite useSite) {
      this.name = name;
      this.useSite = useSite;
    }
  }

  private final Multimap<String, UseSite> useSitesByName;
  // Remember which UseSite instances are in which scopes, so that the knowledge about a changing
  // scope can be rebuilt later.
  private final Multimap<Node, NameAndUseSite> useSitesByScopeNode;

  @VisibleForTesting
  Multimap<String, UseSite> getUseSitesByName() {
    // Defensive copy.
    return ImmutableMultimap.copyOf(useSitesByName);
  }

  public DefinitionUseSiteFinder(AbstractCompiler compiler) {
    super(compiler, false);
    this.useSitesByName = LinkedHashMultimap.create();
    this.useSitesByScopeNode = HashMultimap.create();
  }

  @Override
  public void process(Node externs, Node source) {
    super.process(externs, source);
    NodeTraversal.traverse(compiler, source, new UseSiteGatheringCallback());
  }

  /**
   * Returns a collection of use sites that may refer to provided definition. Returns an empty
   * collection if the definition is not used anywhere.
   *
   * @param definition Definition of interest.
   * @return use site collection.
   */
  public Collection<UseSite> getUseSites(Definition definition) {
    checkState(hasProcessBeenRun, "Hasn't been initialized with process() yet.");
    return useSitesByName.get(definition.getSimplifiedName());
  }

  private class UseSiteGatheringCallback extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
      if (!node.isGetProp() && !node.isName()) {
        return;
      }

      Collection<Definition> defs = getDefinitionsReferencedAt(node);
      if (defs.isEmpty()) {
        return;
      }

      Definition first = defs.iterator().next();

      String name = getSimplifiedName(first.getLValue());
      checkNotNull(name);
      UseSite useSite = new UseSite(node, traversal.getScope(), traversal.getModule());
      useSitesByName.put(name, useSite);
      useSitesByScopeNode.put(
          NodeUtil.getEnclosingChangeScopeRoot(node), new NameAndUseSite(name, useSite));
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

  boolean canModifyDefinition(Definition definition) {
    if (isExported(definition)) {
      return false;
    }

    // Don't modify unused definitions for two reasons:
    // 1) It causes unnecessary churn
    // 2) Other definitions might be used to reflect on this one using
    //    goog.reflect.object (the check for definitions with uses is below).
    Collection<UseSite> useSites = getUseSites(definition);
    if (useSites.isEmpty()) {
      return false;
    }

    for (UseSite site : useSites) {
      // This catches the case where an object literal in goog.reflect.object
      // and a prototype method have the same property name.

      // NOTE(nicksantos): Maps and trogedit both do this by different
      // mechanisms.

      Node nameNode = site.node;
      Collection<Definition> singleSiteDefinitions = getDefinitionsReferencedAt(nameNode);
      if (singleSiteDefinitions.size() > 1) {
        return false;
      }

      checkState(!singleSiteDefinitions.isEmpty());
      checkState(singleSiteDefinitions.contains(definition));
    }

    return true;
  }

  /** @return Whether the definition is directly exported. */
  private boolean isExported(Definition definition) {
    // Assume an exported method result is used.
    Node lValue = definition.getLValue();
    if (lValue == null) {
      return true;
    }

    String partialName;
    if (lValue.isGetProp()) {
      partialName = lValue.getLastChild().getString();
    } else if (lValue.isName()) {
      partialName = lValue.getString();
    } else {
      // GETELEM is assumed to be an export or other expression are unknown
      // uses.
      return true;
    }

    CodingConvention codingConvention = compiler.getCodingConvention();
    return codingConvention.isExported(partialName);
  }

  @Override
  public void rebuildScopeRoots(List<Node> changedScopeRoots, List<Node> deletedScopeRoots) {
    super.rebuildScopeRoots(changedScopeRoots, deletedScopeRoots);

    for (Node scopeRoot : Iterables.concat(deletedScopeRoots, changedScopeRoots)) {
      for (NameAndUseSite nameAndUseSite : useSitesByScopeNode.removeAll(scopeRoot)) {
        useSitesByName.remove(nameAndUseSite.name, nameAndUseSite.useSite);
      }
    }

    NodeTraversal.traverseScopeRoots(
        compiler, null, changedScopeRoots, new UseSiteGatheringCallback(), false);
  }

  /** Traverse a node and its children and remove any references to from the structures. */
  void removeReferences(Node node) {
    if (DefinitionsRemover.isDefinitionNode(node)) {
      Node definitionSiteNode = node;
      DefinitionSite definitionSite = definitionSitesByDefinitionSiteNode.get(definitionSiteNode);
      if (definitionSite != null) {
        Definition definition = definitionSite.definition;
        String name = definition.getSimplifiedName();
        if (name != null) {
          Node definitionNode = definition.getLValue();
          definitionNodes.remove(definitionNode);
          definitionsByName.remove(name, definition);
          definitionSitesByDefinitionSiteNode.remove(definitionSiteNode);
          Node scopeNode = NodeUtil.getEnclosingChangeScopeRoot(definitionSiteNode);
          definitionSitesByScopeNode.remove(scopeNode, definitionSite);
        }
      }
    } else {
      Node useSiteNode = node;
      if (useSiteNode.isGetProp()) {
        String propName = useSiteNode.getLastChild().getString();
        if (propName.equals("apply") || propName.equals("call")) {
          useSiteNode = useSiteNode.getFirstChild();
        }
      }
      String name = getSimplifiedName(useSiteNode);
      if (name != null) {
        UseSite useSite = new UseSite(useSiteNode, null, null);
        useSitesByName.remove(name, useSite);
        useSitesByScopeNode.remove(
            NodeUtil.getEnclosingChangeScopeRoot(useSiteNode), new NameAndUseSite(name, useSite));
      }
    }

    for (Node child : node.children()) {
      removeReferences(child);
    }
  }
}
