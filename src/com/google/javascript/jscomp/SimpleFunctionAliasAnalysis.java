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

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Set;

/**
 * Uses {@link SimpleDefinitionFinder} to determine if a function has been
 * aliased or exposed to .call() or .apply().
 *
 * @author dcc@google.com (Devin Coughlin)
 */
class SimpleFunctionAliasAnalysis {
  private Set<Node> aliasedFunctions;

  private Set<Node> functionsExposedToCallOrApply;

  /**
   * Returns true if the function is aliased.
   *
   * Must only be called after {@link #analyze(SimpleDefinitionFinder)}
   * has been called.
   */
  public boolean isAliased(Node functionNode) {
    Preconditions.checkNotNull(aliasedFunctions);
    Preconditions.checkArgument(functionNode.isFunction());

    return aliasedFunctions.contains(functionNode);
  }

  /**
   * Returns true if the function ever exposed to .call() or .apply().
   *
   * Must only be called after {@link #analyze(SimpleDefinitionFinder)}
   * has been called.
   */
  public boolean isExposedToCallOrApply(Node functionNode) {
    Preconditions.checkNotNull(functionsExposedToCallOrApply);
    Preconditions.checkArgument(functionNode.isFunction());

    return functionsExposedToCallOrApply.contains(functionNode);
  }

  /**
   * Uses the provided {@link SimpleDefinitionFinder} to determine
   * which functions are aliased or exposed to .call() or .apply().
   */
  public void analyze(SimpleDefinitionFinder finder) {
    Preconditions.checkState(aliasedFunctions == null);

    aliasedFunctions = Sets.newHashSet();
    functionsExposedToCallOrApply = Sets.newHashSet();

    for (DefinitionSite definitionSite : finder.getDefinitionSites()) {
      Definition definition = definitionSite.definition;

      if (!definition.isExtern()) {
        Node rValue = definition.getRValue();

        if (rValue != null && rValue.isFunction()) {
          // rValue is a Token.FUNCTION from a definition

          for (UseSite useSite : finder.getUseSites(definition)) {
            updateFunctionForUse(rValue, useSite.node);
          }
        }
      }
    }
  }

  /**
   * Updates alias and exposure information based a site where the function is
   * used.
   *
   * Note: this method may be called multiple times per Function, each time
   * with a different useNode.
   */
  private void updateFunctionForUse(Node function, Node useNode) {
    Node useParent = useNode.getParent();
    int parentType = useParent.getType();

    if ((parentType == Token.CALL || parentType == Token.NEW)
        && useParent.getFirstChild() == useNode) {
      // Regular call sites don't count as aliases
    } else if (NodeUtil.isGet(useParent)) {
      // GET{PROP,ELEM} don't count as aliases
      // but we have to check for using them in .call and .apply.

      if (useParent.isGetProp()) {
        Node gramps = useParent.getParent();
        if (NodeUtil.isFunctionObjectApply(gramps) ||
            NodeUtil.isFunctionObjectCall(gramps)) {
          functionsExposedToCallOrApply.add(function);
        }
      }
    } else {
      aliasedFunctions.add(function);
    }
  }
}
