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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Optimization for functions that have {@code var_args} or access the
 * arguments array.
 *
 * <p>Example:
 * <pre>
 * function() { alert(arguments[0] + argument[1]) }
 * </pre>
 * to:
 * <pre>
 * function(a, b) { alert(a, b) }
 * </pre>
 *
 * Each newly inserted variable name will be unique very much like the output
 * of the AST found after the {@link Normalize} pass.
 */
class OptimizeArgumentsArray implements CompilerPass, ScopedCallback {

  // The arguments object as described by ECMAScript version 3 section 10.1.8
  private static final String ARGUMENTS = "arguments";

  // To ensure that the newly introduced parameter names are unique. We will
  // use this string as prefix unless the caller specifies a different prefix.
  private static final String PARAMETER_PREFIX = "JSCompiler_OptimizeArgumentsArray_p";

  // The prefix for the newly introduced parameter name.
  private final String paramPrefix;

  // To make each parameter name unique in the function we append a unique integer.
  private int uniqueId = 0;

  // Reference to the compiler object to notify any changes to source code AST.
  private final AbstractCompiler compiler;

  // A stack of arguments access list to the corresponding outer functions.
  private final Deque<List<Node>> argumentsAccessStack = new ArrayDeque<>();

  // The `arguments` access in the current scope.
  //
  // The elements are NAME nodes. This initial value is a error-detecting sentinel for the global
  // scope, which is used because since `ArrayDeque` is null-hostile.
  private List<Node> currentArgumentsAccesses = ImmutableList.of();

  /**
   * Construct this pass and use {@link #PARAMETER_PREFIX} as the prefix for
   * all parameter names that it introduces.
   */
  OptimizeArgumentsArray(AbstractCompiler compiler) {
    this(compiler, PARAMETER_PREFIX);
  }

  /**
   * @param paramPrefix the prefix to use for all parameter names that this
   *     pass introduces
   */
  OptimizeArgumentsArray(AbstractCompiler compiler, String paramPrefix) {
    this.compiler = checkNotNull(compiler);
    this.paramPrefix = checkNotNull(paramPrefix);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, checkNotNull(root), this);
  }

  @Override
  public void enterScope(NodeTraversal traversal) {
    if (!definesArgumentsVar(traversal.getScopeRoot())) {
      return;
    }

    // Introduces a new access list and stores the access list of the outer scope.
    argumentsAccessStack.push(currentArgumentsAccesses);
    currentArgumentsAccesses = new ArrayList<>();
  }

  @Override
  public void exitScope(NodeTraversal traversal) {
    if (!definesArgumentsVar(traversal.getScopeRoot())) {
      return;
    }

    // Attempt to replace the argument access and if the AST has been change,
    // report back to the compiler.
    tryReplaceArguments(traversal.getScope());

    currentArgumentsAccesses = argumentsAccessStack.pop();
  }

  private static boolean definesArgumentsVar(Node root) {
    return root.isFunction() && !root.isArrowFunction();
  }

  @Override
  public boolean shouldTraverse(NodeTraversal unused0, Node unused1, Node unused2) {
    return true;
  }

  @Override
  public void visit(NodeTraversal traversal, Node node, Node parent) {
    if (traversal.inGlobalHoistScope()) {
      return; // Do no rewriting in the global scope.
    }

    if (node.isName() && ARGUMENTS.equals(node.getString())) {
      currentArgumentsAccesses.add(node); // Record all potential references to the arguments array.
    }
  }

  /**
   * Tries to optimize all the arguments array access in this scope by assigning a name to each
   * element.
   *
   * @param scope scope of the function
   */
  private void tryReplaceArguments(Scope scope) {
    // Find the number of parameters that can be accessed without using `arguments`.
    Node parametersList = NodeUtil.getFunctionParameters(scope.getRootNode());
    checkState(parametersList.isParamList(), parametersList);
    int numParameters = parametersList.getChildCount();

    // Determine the highest index that is used to make an access on `arguments`. By default, assume
    // that the value is the number of parameters to the function.
    int highestIndex = getHighestIndex(numParameters - 1);
    if (highestIndex < 0) {
      return;
    }

    ImmutableSortedMap<Integer, String> argNames =
        assembleParamNames(parametersList, highestIndex + 1);
    changeMethodSignature(argNames, parametersList);
    changeBody(argNames);
  }

  /**
   * Iterate through all the references to arguments array in the
   * function to determine the real highestIndex. Returns -1 when we should not
   * be replacing any arguments for this scope - we should exit tryReplaceArguments
   *
   * @param highestIndex highest index that has been accessed from the arguments array
   */
  private int getHighestIndex(int highestIndex) {
    for (Node ref : currentArgumentsAccesses) {

      Node getElem = ref.getParent();

      // Bail on anything but argument[c] access where c is a constant.
      // TODO(user): We might not need to bail out all the time, there might
      // be more cases that we can cover.
      if (!getElem.isGetElem() || ref != getElem.getFirstChild()) {
        return -1;
      }

      Node index = ref.getNext();

      // We have something like arguments[x] where x is not a constant. That
      // means at least one of the access is not known.
      if (!index.isNumber() || index.getDouble() < 0) {
        // TODO(user): Its possible not to give up just yet. The type
        // inference did a 'semi value propagation'. If we know that string
        // is never a subclass of the type of the index. We'd know that
        // it is never 'callee'.
        return -1; // Give up.
      }

      //We want to bail out if someone tries to access arguments[0.5] for example
      if (index.getDouble() != Math.floor(index.getDouble())){
        return -1;
      }

      Node getElemParent = getElem.getParent();
      // When we have argument[0](), replacing it with a() is semantically
      // different if argument[0] is a function call that refers to 'this'
      if (getElemParent.isCall() && getElemParent.getFirstChild() == getElem) {
        // TODO(user): We can consider using .call() if aliasing that
        // argument allows shorter alias for other arguments.
        return -1;
      }

      // Replace the highest index if we see an access that has a higher index
      // than all the one we saw before.
      int value = (int) index.getDouble();
      if (value > highestIndex) {
        highestIndex = value;
      }
    }
    return highestIndex;
  }

  /**
   * Inserts new formal parameters into the method's signature based on the given set of names.
   *
   * <p>Example: function() --> function(r0, r1, r2)
   *
   * @param argNames maps param index to param name, if the param with that index has a name.
   * @param paramList node representing the function signature
   */
  private void changeMethodSignature(ImmutableSortedMap<Integer, String> argNames, Node paramList) {
    ImmutableSortedMap<Integer, String> newParams = argNames.tailMap(paramList.getChildCount());
    for (String name : newParams.values()) {
      paramList.addChildToBack(IR.name(name).useSourceInfoIfMissingFrom(paramList));
    }
    if (!newParams.isEmpty()) {
      compiler.reportChangeToEnclosingScope(paramList);
    }
  }

  /**
   * Performs the replacement of arguments[x] -> a if x is known.
   *
   * @param argNames maps param index to param name, if the param with that index has a name.
   */
  private void changeBody(ImmutableMap<Integer, String> argNames) {
    for (Node ref : currentArgumentsAccesses) {
      Node index = ref.getNext();
      Node parent = ref.getParent();
      int value = (int) index.getDouble(); // This was validated earlier.

      @Nullable String name = argNames.get(value);
      if (name == null) {
        continue;
      }

      Node newName = IR.name(name).useSourceInfoIfMissingFrom(parent);
      parent.replaceWith(newName);
      // TODO(nickreid): See if we can do this fewer times. The accesses may be in different scopes.
      compiler.reportChangeToEnclosingScope(newName);
    }
  }

  /**
   * Generates a {@link Map} from argument indices to parameter names.
   *
   * <p>A {@link Map} is used because the sequence may be sparse in the case that there is an
   * anonymous param, such as a destructuring param. There may also be fewer returned names than
   * {@code maxCount} if there is a rest param, since no additional params may be synthesized.
   *
   * @param maxCount The maximum number of argument names in the returned map.
   */
  private ImmutableSortedMap<Integer, String> assembleParamNames(Node paramList, int maxCount) {
    checkArgument(paramList.isParamList(), paramList);

    ImmutableSortedMap.Builder<Integer, String> builder = ImmutableSortedMap.naturalOrder();
    int index = 0;

    // Collect all existing param names first...
    for (Node param = paramList.getFirstChild(); param != null; param = param.getNext()) {
      switch (param.getToken()) {
        case NAME:
          builder.put(index, param.getString());
          break;

        case ITER_REST:
          return builder.build();

        case DEFAULT_VALUE:
          // `arguments` doesn't consider default values. It holds exactly the provided args.
        case OBJECT_PATTERN:
        case ARRAY_PATTERN:
          // Patterns have no names to substitute into the body.
          break;

        default:
          throw new IllegalArgumentException(param.toString());
      }

      index++;
    }
    // ... then synthesize any additional param names.
    for (; index < maxCount; index++) {
      builder.put(index, paramPrefix + uniqueId++);
    }

    return builder.build();
  }
}
