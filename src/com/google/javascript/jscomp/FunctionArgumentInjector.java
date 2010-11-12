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
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A nifty set of functions to deal with the issues of replacing function
 * parameters with a set of call argument expressions.
 *
 * @author johnlenz@google.com (John Lenz)
 */
class FunctionArgumentInjector {

  private FunctionArgumentInjector() {
    // A private constructor to prevent instantiation.
  }

  /**
   * With the map provided, replace the names with expression trees.
   * @param node The root of the node tree within which to perform the
   *     substitutions.
   * @param parent The parent root node.
   * @param replacements The map of names to template node trees with which
   *     to replace the name Nodes.
   * @returns The root node or its replacement.
   */
  static Node inject(Node node, Node parent,
      Map<String, Node> replacements) {
    if (node.getType() == Token.NAME) {
      Node replacementTemplate = replacements.get(node.getString());
      if (replacementTemplate != null) {
        // This should not be replacing declared names.
        Preconditions.checkState(parent.getType() != Token.FUNCTION
            || parent.getType() != Token.VAR
            || parent.getType() != Token.CATCH);
        // The name may need to be replaced more than once,
        // so we need to clone the node.
        Node replacement = replacementTemplate.cloneTree();
        parent.replaceChild(node, replacement);
        return replacement;
      }
    }

    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      // We have to reassign c in case it was replaced, because the removed c's
      // getNext() would no longer be correct.
      c = inject(c, node, replacements);
    }

    return node;
  }

  /**
   * Get a mapping for function parameter names to call arguments.
   */
  static LinkedHashMap<String, Node> getFunctionCallParameterMap(
      Node fnNode, Node callNode, Supplier<String> safeNameIdSupplier) {
    // Create an argName -> expression map
    // NOTE: A linked map is created here to provide ordering.
    LinkedHashMap<String, Node> argMap = Maps.newLinkedHashMap();

    // CALL NODE: [ NAME, ARG1, ARG2, ... ]
    Node cArg = callNode.getFirstChild().getNext();
    if (callNode.getFirstChild().getType() != Token.NAME) {
      if (NodeUtil.isFunctionObjectCall(callNode)) {
        // TODO(johnlenz): Support replace this with a value.
        Preconditions.checkNotNull(cArg);
        Preconditions.checkState(cArg.getType() == Token.THIS);
        cArg = cArg.getNext();
      } else {
        Preconditions.checkState(!NodeUtil.isFunctionObjectApply(callNode));
      }
    }

    for (Node fnArg : NodeUtil.getFnParameters(fnNode).children()) {
      if (cArg != null) {
        argMap.put(fnArg.getString(), cArg);
        cArg = cArg.getNext();
      } else {
        Node srcLocation = callNode;
        argMap.put(fnArg.getString(), NodeUtil.newUndefinedNode(srcLocation));
      }
    }

    // Add temp names for arguments that don't have named parameters in the
    // called function.
    int anonArg = 0;
    while (cArg != null) {
      String uniquePlaceholder =
        getUniqueAnonymousParameterName(safeNameIdSupplier);
      argMap.put(uniquePlaceholder, cArg);
      cArg = cArg.getNext();
    }

    return argMap;
  }

  /**
   * Parameter names will be name unique when at a later time.
   */
  private static String getUniqueAnonymousParameterName(
      Supplier<String> safeNameIdSupplier) {
    return "JSCompiler_inline_anon_param_" + safeNameIdSupplier.get();
  }

  /**
   * Retrieve a set of names that can not be safely substituted in place.
   * Example:
   *   function(a) {
   *     a = 0;
   *   }
   * Inlining this without taking precautions would cause the call site value
   * to be modified (bad).
   */
  static Set<String> findModifiedParameters(Node fnNode) {
    Set<String> names = getFunctionParameterSet(fnNode);
    Set<String> unsafeNames = Sets.newHashSet();
    return findModifiedParameters(
        fnNode, null, names, unsafeNames);
  }

  /**
   * Check for uses of the named value that imply a pass-by-value
   * parameter is expected.  This is used to prevent cases like:
   *
   *   function (x) {
   *     x=2;
   *     return x;
   *   }
   *
   * We don't want "undefined" to be substituted for "x", and get
   *   undefined=2
   *
   * @param n The node in question.
   * @param parent The parent of the node.
   * @param names The set of names to check.
   */
  private static Set<String> findModifiedParameters(
      Node n, Node parent, Set<String> names, Set<String> unsafe) {
    Preconditions.checkArgument(unsafe != null);
    if (n.getType() == Token.NAME) {
      if (names.contains(n.getString())) {
        if (canNameValueChange(n, parent)) {
          unsafe.add(n.getString());
        }
      }
    }

    for (Node c : n.children()) {
      findModifiedParameters(c, n, names, unsafe);
    }

    return unsafe;
  }

  /**
   * This is similar to NodeUtil.isLValue except that object properties and
   * array member modification aren't important ("o" in "o.a = 2" is still "o"
   * after assignment, where in as "o = x", "o" is now "x").
   *
   * This also looks for the redefinition of a name.
   *   function (x){var x;}
   *
   * @param n The NAME node in question.
   * @param parent The parent of the node.
   */
  private static boolean canNameValueChange(Node n, Node parent) {
    int type = parent.getType();
    return (type == Token.VAR || type == Token.INC || type == Token.DEC ||
        (NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n));
  }

  /**
   * Updates the set of parameter names in set unsafe to include any
   * arguments from the call site that require aliases.
   * @param fnNode The FUNCTION node to be inlined.
   * @param argMap The argument list for the call to fnNode.
   * @param namesNeedingTemps The set of names to update.
   */
  static void maybeAddTempsForCallArguments(
      Node fnNode, Map<String, Node> argMap, Set<String> namesNeedingTemps,
      CodingConvention convention) {
    if (argMap.isEmpty()) {
      // No arguments to check, we are done.
      return;
    }

    Preconditions.checkArgument(fnNode.getType() == Token.FUNCTION);
    Node block = fnNode.getLastChild();

    Set<String> parameters = argMap.keySet();

    // Get the list of parameters that may need temporaries due to
    // side-effects.
    Set<String> namesAfterSideEffects = findParametersReferencedAfterSideEffect(
        parameters, block);

    // Check for arguments that are evaluated more than once.
    for (Map.Entry<String, Node> entry : argMap.entrySet()) {
      String argName = entry.getKey();
      if (namesNeedingTemps.contains(argName)) {
        continue;
      }
      Node cArg = entry.getValue();
      boolean safe = true;
      int references = NodeUtil.getNameReferenceCount(block, argName);

      if (NodeUtil.mayEffectMutableState(cArg) && references > 0) {
        // Note: Mutable arguments should be assigned to temps, as the
        // may be within in a loop:
        //   function x(a) {
        //     for(var i=0; i<0; i++) {
        //       foo(a);
        //     }
        //   x( [] );
        //
        //   The parameter in the call to foo should not become "[]".
        safe = false;
      } else if (NodeUtil.mayHaveSideEffects(cArg)) {
        // Even if there are no references, we still need to evaluate the
        // expression if it has side-effects.
        safe = false;
      } else if (NodeUtil.canBeSideEffected(cArg)
          && namesAfterSideEffects.contains(argName)) {
        safe = false;
      } else if (references > 1) {
        // Safe is a misnomer, this is a check for "large".
        switch (cArg.getType()) {
          case Token.NAME:
            String name = cArg.getString();
            safe = !(convention.isExported(name));
            break;
          case Token.NULL:
          case Token.VOID:
          case Token.THIS:
          case Token.FALSE:
          case Token.TRUE:
          case Token.NUMBER:
            safe = true;
            break;
          case Token.STRING:
            safe = (cArg.getString().length() < 2);
            break;
          default:
            safe = false;
            break;
        }
      }

      if (!safe) {
        namesNeedingTemps.add(argName);
      }
    }
  }

  /**
   * Boot strap a traversal to look for parameters referenced
   * after a non-local side-effect.
   * NOTE: This assumes no-inner functions.
   * @param parameters The set of parameter names.
   * @param root The function code block.
   * @return The subset of parameters referenced after the first
   *     seen non-local side-effect.
   */
  private static Set<String> findParametersReferencedAfterSideEffect(
      Set<String> parameters, Node root) {

    // TODO(johnlenz): Consider using scope for this.
    Set<String> locals = Sets.newHashSet(parameters);
    gatherLocalNames(root, locals);

    ReferencedAfterSideEffect collector = new ReferencedAfterSideEffect(
        parameters, locals);
    NodeUtil.visitPostOrder(
        root,
        collector,
        collector);
    return collector.getResults();
  }

  /**
   * Collect parameter names referenced after a non-local side-effect.
   *
   * Assumptions:
   * - We assume parameters are not modified in the function body
   * (that is checked separately).
   * - There are no inner functions (also checked separately).
   *
   * As we are trying to replace parameters with there passed in values
   * we are interested in anything that may affect those value.  So, ignoring
   * changes to local variables, we look for things that may affect anything
   * outside the local-state.  Once such a side-effect is seen any following
   * reference to the function parameters are collected.  These will need
   * to be assigned to temporaries to prevent changes to their value as would
   * have happened during the function call.
   *
   * To properly handle loop structures all references to the function
   * parameters are recorded and the decision to keep or throw away those
   * references is deferred until exiting the loop structure.
   */
  private static class ReferencedAfterSideEffect
      implements Visitor, Predicate<Node> {
    private final Set<String> parameters;
    private final Set<String> locals;
    private boolean sideEffectSeen = false;
    private Set<String> parametersReferenced = Sets.newHashSet();
    private int loopsEntered = 0;

    ReferencedAfterSideEffect(Set<String> parameters, Set<String> locals) {
      this.parameters = parameters;
      this.locals = locals;
    }

    Set<String> getResults() {
      return parametersReferenced;
    }

    @Override
    public boolean apply(Node node) {
      // Keep track of any loop structures entered.
      if (NodeUtil.isLoopStructure(node)) {
        loopsEntered++;
      }

      // If we have found all the paremeters, don't bother looking
      // at the children.
      return !(sideEffectSeen
          && parameters.size() == parametersReferenced.size());
    }

    boolean inLoop() {
      return loopsEntered != 0;
    }

    public void visit(Node n) {
      // If we are exiting a loop.
      if (NodeUtil.isLoopStructure(n)) {
        loopsEntered--;
        if (!inLoop() && !sideEffectSeen) {
          // Now that the loops has been fully traversed and
          // no side-effects have been seen, throw away
          // the references seen in them.
          parametersReferenced.clear();
        }
      }

      if (!sideEffectSeen) {
        // Look for side-effects.
        if (hasNonLocalSideEffect(n)) {
          sideEffectSeen = true;
        }
      }

      // If traversing the nodes of a loop save any references
      // that are seen.
      if (inLoop() || sideEffectSeen) {
        // Record references to parameters.
        if (n.getType() == Token.NAME) {
          String name = n.getString();
          if (parameters.contains(name)) {
            parametersReferenced.add(name);
          }
        }
      }
    }

    /**
     * @return Whether the node may have non-local side-effects.
     */
    private boolean hasNonLocalSideEffect(Node n) {
      boolean sideEffect = false;
      int type = n.getType();
      // Note: Only care about changes to non-local names, specifically
      // ignore VAR declaration assignments.
      if (NodeUtil.isAssignmentOp(n)
          || type == Token.INC
          || type == Token.DEC) {
        Node lhs = n.getFirstChild();
        // Ignore changes to local names.
        if (!isLocalName(lhs)) {
          sideEffect = true;
        }
      } else if (type == Token.CALL) {
        sideEffect = NodeUtil.functionCallHasSideEffects(n);
      } else if (type == Token.NEW) {
        sideEffect = NodeUtil.constructorCallHasSideEffects(n);
      } else if (type == Token.DELPROP) {
        sideEffect = true;
      }

      return sideEffect;
    }

    /**
     * @return Whether node is a reference to locally declared name.
     */
    private boolean isLocalName(Node node) {
      if (NodeUtil.isName(node)) {
        String name = node.getString();
        return locals.contains(name);
      }
      return false;
    }
  }

  /**
   * Gather any names declared in the local scope.
   */
  private static void gatherLocalNames(Node n, Set<String> names) {
    if (n.getType() == Token.FUNCTION) {
      if (NodeUtil.isFunctionDeclaration(n)) {
        names.add(n.getFirstChild().getString());
      }
      // Don't traverse into inner function scopes;
      return;
    } else if (n.getType() == Token.NAME) {
      switch (n.getParent().getType()) {
        case Token.VAR:
        case Token.CATCH:
          names.add(n.getString());
      }
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      gatherLocalNames(c, names);
    }
  }

  /**
   * Get a set of function parameter names.
   */
  private static Set<String> getFunctionParameterSet(Node fnNode) {
    Set<String> set = Sets.newHashSet();
    for (Node n : NodeUtil.getFnParameters(fnNode).children()) {
      set.add(n.getString());
    }
    return set;
  }

}
