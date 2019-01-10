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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A nifty set of functions to deal with the issues of replacing function
 * parameters with a set of call argument expressions.
 *
 * @author johnlenz@google.com (John Lenz)
 */
class FunctionArgumentInjector {

  // A string to use to represent "this".  Anything that is not a valid
  // identifier can be used, so we use "this".
  static final String THIS_MARKER = "this";

  static final String REST_MARKER = "rest param";

  static final String DEFAULT_MARKER = "Default Value";

  static final String OBJECT_PATTERN_MARKER = "object pattern";

  private FunctionArgumentInjector() {
    // A private constructor to prevent instantiation.
  }

  /**
   * With the map provided, replace the names with expression trees.
   *
   * @param node The root node of the tree within which to perform the substitutions.
   * @param parent The parent root node.
   * @param replacements The map of names to template node trees with which to replace the name
   *     Nodes.
   * @return The root node or its replacement.
   */
  static Node inject(
      AbstractCompiler compiler, Node node, Node parent, Map<String, Node> replacements) {
    return inject(compiler, node, parent, replacements, /* replaceThis */ true);
  }

  private static Node inject(
      AbstractCompiler compiler,
      Node node,
      Node parent,
      Map<String, Node> replacements,
      boolean replaceThis) {
    if (node.isName()) {
      Node replacementTemplate = replacements.get(node.getString());
      if (replacementTemplate != null) {
        // This should not be replacing declared names.
        checkState(!(parent.isFunction() || parent.isVar() || parent.isCatch()), parent);
        // The name may need to be replaced more than once,
        // so we need to clone the node.
        Node replacement = replacementTemplate.cloneTree();
        parent.replaceChild(node, replacement);
        return replacement;
      }
    } else if (replaceThis && node.isThis()) {
      Node replacementTemplate = replacements.get(THIS_MARKER);
      checkNotNull(replacementTemplate);
      if (!replacementTemplate.isThis()) {
        // The name may need to be replaced more than once,
        // so we need to clone the node.
        Node replacement = replacementTemplate.cloneTree();
        parent.replaceChild(node, replacement);

        // Remove the value.  This isn't required but it ensures that we won't
        // inject side-effects multiple times as it will trigger the null
        // check above if we do.
        if (NodeUtil.mayHaveSideEffects(replacementTemplate, compiler)) {
          replacements.remove(THIS_MARKER);
        }

        return replacement;
      }
    } else if (node.isFunction() && !node.isArrowFunction()) {
      // Once we enter another non-arrow function the "this" value changes. Don't try
      // to replace it within an inner scope.
      replaceThis = false;
    }

    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      // We have to reassign c in case it was replaced, because the removed c's
      // getNext() would no longer be correct.
      c = inject(compiler, c, node, replacements, replaceThis);
    }

    return node;
  }

  /**
   * Get a mapping for function parameter names to call arguments.
   */
  static ImmutableMap<String, Node> getFunctionCallParameterMap(
      final Node fnNode, Node callNode, Supplier<String> safeNameIdSupplier) {
    checkNotNull(fnNode);
    // Create an argName -> expression map
    ImmutableMap.Builder<String, Node> argMap = ImmutableMap.builder();

    // CALL NODE: [ NAME, ARG1, ARG2, ... ]
    Node cArg = callNode.getSecondChild();
    if (cArg != null && NodeUtil.isFunctionObjectCall(callNode)) {
      argMap.put(THIS_MARKER, cArg);
      cArg = cArg.getNext();
    } else {
      // 'apply' isn't supported yet.
      checkState(!NodeUtil.isFunctionObjectApply(callNode), callNode);
      argMap.put(THIS_MARKER, NodeUtil.newUndefinedNode(callNode));
    }

    for (Node fnParam : NodeUtil.getFunctionParameters(fnNode).children()) {
      if (cArg != null) {
        if (fnParam.isRest()) {
          checkState(fnParam.getOnlyChild().isName(), fnParam.getOnlyChild());
          Node array = IR.arraylit();
          array.useSourceInfoIfMissingFromForTree(cArg);
          while (cArg != null) {
            array.addChildToBack(cArg.cloneTree());
            cArg = cArg.getNext();
          }
          argMap.put(fnParam.getOnlyChild().getString(), array);
          return argMap.build();
        } else {
          checkState(fnParam.isName(), fnParam);
          argMap.put(fnParam.getString(), cArg);
        }
        cArg = cArg.getNext();
      } else { // cArg != null
        if (fnParam.isRest()) {
          checkState(fnParam.getOnlyChild().isName(), fnParam);
          //No arguments for REST parameters
          Node array = IR.arraylit();
          argMap.put(fnParam.getOnlyChild().getString(), array);
        } else {
          checkState(fnParam.isName(), fnParam);
          Node srcLocation = callNode;
          argMap.put(fnParam.getString(), NodeUtil.newUndefinedNode(srcLocation));
        }
      }
    }

    // Add temp names for arguments that don't have named parameters in the
    // called function.
    while (cArg != null) {
      String uniquePlaceholder = getUniqueAnonymousParameterName(safeNameIdSupplier);
      argMap.put(uniquePlaceholder, cArg);
      cArg = cArg.getNext();
    }

    return argMap.build();
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
    ImmutableSet<String> names = getFunctionParameterSet(fnNode);
    Set<String> unsafeNames = new HashSet<>();
    return findModifiedParameters(fnNode.getLastChild(), names, unsafeNames, false);
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
   * @param unsafe The set of names that require aliases.
   * @param inInnerFunction Whether the inspection is occurring on a inner function.
   */
  private static Set<String> findModifiedParameters(
      Node n, ImmutableSet<String> names, Set<String> unsafe, boolean inInnerFunction) {
    checkArgument(unsafe != null);
    if (n.isName()) {
      if (names.contains(n.getString()) && (inInnerFunction || canNameValueChange(n))) {
        unsafe.add(n.getString());
      }
    } else if (n.isFunction()) {
      // A function parameter can not be replaced with a direct inlined value
      // if it is referred to by an inner function. The inner function
      // can out live the call we are replacing, so inner function must
      // capture a unique name.  This approach does not work within loop
      // bodies so those are forbidden elsewhere.
      inInnerFunction = true;
    }

    for (Node c : n.children()) {
      findModifiedParameters(c, names, unsafe, inInnerFunction);
    }

    return unsafe;
  }

  /**
   * This is similar to NodeUtil.isLValue except that object properties and
   * array member modification aren't important ("o" in "o.a = 2" is still "o"
   * after assignment, where in as "o = x", "o" is now "x").
   *
   * This also looks for the redefinition of a name.
   *   function (x) {var x;}
   *
   * @param n The NAME node in question.
   * @param parent The parent of the node.
   */
  private static boolean canNameValueChange(Node n) {
    return NodeUtil.isLValue(n)
        && !NodeUtil.getEnclosingStatement(n).isConst()
        && !NodeUtil.getEnclosingStatement(n).isLet();
  }

  /**
   * Updates the set of parameter names in set unsafe to include any arguments from the call site
   * that require aliases.
   *
   * @param fnNode The FUNCTION node to be inlined.
   * @param argMap The argument list for the call to fnNode.
   * @param namesNeedingTemps The set of names to update.
   */
  static void maybeAddTempsForCallArguments(
      AbstractCompiler compiler,
      Node fnNode,
      ImmutableMap<String, Node> argMap,
      Set<String> namesNeedingTemps,
      CodingConvention convention) {
    if (argMap.isEmpty()) {
      // No arguments to check, we are done.
      return;
    }

    checkArgument(fnNode.isFunction(), fnNode);
    Node block = fnNode.getLastChild();

    int argCount = argMap.size();
    // We limit the "trivial" bodies to those where there is a single expression or
    // return, the expression is
    boolean isTrivialBody = (!block.hasChildren()
        || (block.hasOneChild() && !bodyMayHaveConditionalCode(block.getLastChild())));
    boolean hasMinimalParameters = NodeUtil.isUndefined(argMap.get(THIS_MARKER))
        && argCount <= 2; // this + one parameter

    // Get the list of parameters that may need temporaries due to side-effects.
    ImmutableSet<String> namesAfterSideEffects = findParametersReferencedAfterSideEffect(
        argMap.keySet(), block);

    // Check for arguments that are evaluated more than once.
    for (Map.Entry<String, Node> entry : argMap.entrySet()) {
      String argName = entry.getKey();
      if (namesNeedingTemps.contains(argName)) {
        continue;
      }
      Node cArg = entry.getValue();
      boolean safe = true;
      int references = NodeUtil.getNameReferenceCount(block, argName);

      boolean argSideEffects = NodeUtil.mayHaveSideEffects(cArg, compiler);
      if (!argSideEffects && references == 0) {
        safe = true;
      } else if (isTrivialBody && hasMinimalParameters
          && references == 1
          && !(NodeUtil.canBeSideEffected(cArg) && namesAfterSideEffects.contains(argName))) {
        // For functions with a trivial body, and where the parameter evaluation order
        // can't change, and there aren't any side-effect before the parameter, we can
        // avoid creating a temporary.
        //
        // This is done to help inline common trivial functions
        safe = true;
      } else if (NodeUtil.mayEffectMutableState(cArg, compiler) && references > 0) {
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
      } else if (argSideEffects) {
        // Even if there are no references, we still need to evaluate the
        // expression if it has side-effects.
        safe = false;
      } else if (NodeUtil.canBeSideEffected(cArg) && namesAfterSideEffects.contains(argName)) {
        safe = false;
      } else if (references > 1) {
        // Safe is a misnomer, this is a check for "large".
        switch (cArg.getToken()) {
          case NAME:
            String name = cArg.getString();
            safe = !(convention.isExported(name));
            break;
          case THIS:
            safe = true;
            break;
          case STRING:
            safe = (cArg.getString().length() < 2);
            break;
          default:
            safe = NodeUtil.isImmutableValue(cArg);
            break;
        }
      }

      if (!safe) {
        namesNeedingTemps.add(argName);
      }
    }
  }

  /**
   * We consider a return or expression trivial if it doesn't contain a conditional expression or
   * a function.
   */
  static boolean bodyMayHaveConditionalCode(Node n) {
    if (!n.isReturn() && !n.isExprResult()) {
      return true;
    }
    return mayHaveConditionalCode(n);
  }

  /**
   * We consider an expression trivial if it doesn't contain a conditional expression or
   * a function.
   */
  static boolean mayHaveConditionalCode(Node n) {
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      switch (c.getToken()) {
        case FUNCTION:
        case AND:
        case OR:
        case HOOK:
          return true;
        default:
          break;
      }
      if (mayHaveConditionalCode(c)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Bootstrap a traversal to look for parameters referenced after a non-local side-effect.
   *
   * NOTE: This assumes no-inner functions.
   * @param parameters The set of parameter names.
   * @param root The function code block.
   * @return The subset of parameters referenced after the first
   *     seen non-local side-effect.
   */
  private static ImmutableSet<String> findParametersReferencedAfterSideEffect(
      ImmutableSet<String> parameters, Node root) {

    // TODO(johnlenz): Consider using scope for this.
    Set<String> locals = new HashSet<>(parameters);
    gatherLocalNames(root, locals);

    ReferencedAfterSideEffect collector = new ReferencedAfterSideEffect(
        parameters, ImmutableSet.copyOf(locals));
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
  private static class ReferencedAfterSideEffect implements Visitor, Predicate<Node> {
    private final ImmutableSet<String> parameters;
    private final ImmutableSet<String> locals;
    private boolean sideEffectSeen = false;
    private final Set<String> parametersReferenced = new HashSet<>();
    private int loopsEntered = 0;

    ReferencedAfterSideEffect(ImmutableSet<String> parameters, ImmutableSet<String> locals) {
      this.parameters = parameters;
      this.locals = locals;
    }

    ImmutableSet<String> getResults() {
      return ImmutableSet.copyOf(parametersReferenced);
    }

    @Override
    public boolean apply(Node node) {
      // Keep track of any loop structures entered.
      if (NodeUtil.isLoopStructure(node)) {
        loopsEntered++;
      }

      // If we have found all the parameters, don't bother looking
      // at the children.
      return !(sideEffectSeen && parameters.size() == parametersReferenced.size());
    }

    boolean inLoop() {
      return loopsEntered != 0;
    }

    @Override
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
        if (n.isName()) {
          String name = n.getString();
          if (parameters.contains(name)) {
            parametersReferenced.add(name);
          }
        } else if (n.isThis()) {
          parametersReferenced.add(THIS_MARKER);
        }
      }
    }

    /**
     * @return Whether the node may have non-local side-effects.
     */
    private boolean hasNonLocalSideEffect(Node n) {
      boolean sideEffect = false;
      Token type = n.getToken();
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
      if (node.isName()) {
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
    if (n.isFunction()) {
      if (NodeUtil.isFunctionDeclaration(n)) {
        names.add(n.getFirstChild().getString());
      }
      // Don't traverse into inner function scopes;
      return;
    } else if (n.isName()) {
      switch (n.getParent().getToken()) {
        case VAR:
        case LET:
        case CONST:
        case CATCH:
          names.add(n.getString());
          break;
        default:
          break;
      }
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      gatherLocalNames(c, names);
    }
  }

  /**
   * Get a set of function parameter names.
   */
  private static ImmutableSet<String> getFunctionParameterSet(Node fnNode) {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    for (Node n : NodeUtil.getFunctionParameters(fnNode).children()) {
      if (n.isRest()){
        builder.add(REST_MARKER);
      } else if (n.isDefaultValue() || n.isObjectPattern() || n.isArrayPattern()) {
        throw new IllegalStateException("Not supported: " + n);
      } else {
        builder.add(n.getString());
      }
    }
    return builder.build();
  }

}
