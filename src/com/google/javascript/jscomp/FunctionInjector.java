/*
 * Copyright 2008 The Closure Compiler Authors.
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
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ExpressionDecomposer.DecompositionType;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A set of utility functions that replaces CALL with a specified
 * FUNCTION body, replacing and aliasing function parameters as
 * necessary.
 *
 * @author johnlenz@google.com (John Lenz)
 */
class FunctionInjector {

  private final AbstractCompiler compiler;
  private final Supplier<String> safeNameIdSupplier;
  private final boolean allowDecomposition;
  private Set<String> knownConstants = Sets.newHashSet();
  private final boolean assumeStrictThis;
  private final boolean assumeMinimumCapture;

  /**
   * @param allowDecomposition Whether an effort should be made to break down
   * expressions into simpler expressions to allow functions to be injected
   * where they would otherwise be disallowed.
   */
  public FunctionInjector(
      AbstractCompiler compiler,
      Supplier<String> safeNameIdSupplier,
      boolean allowDecomposition,
      boolean assumeStrictThis,
      boolean assumeMinimumCapture) {
    Preconditions.checkNotNull(compiler);
    Preconditions.checkNotNull(safeNameIdSupplier);
    this.compiler = compiler;
    this.safeNameIdSupplier = safeNameIdSupplier;
    this.allowDecomposition = allowDecomposition;
    this.assumeStrictThis = assumeStrictThis;
    this.assumeMinimumCapture = assumeMinimumCapture;
  }

  /** The type of inlining to perform. */
  enum InliningMode {
    /**
     * Directly replace the call expression. Only functions of meeting
     * strict preconditions can be inlined.
     */
    DIRECT,

    /**
     * Replaces the call expression with a block of statements. Conditions
     * on the function are looser in mode, but stricter on the call site.
     */
    BLOCK
  }

  /** Holds a reference to the call node of a function call */
  static class Reference {
    final Node callNode;
    final JSModule module;
    final InliningMode mode;

    Reference(Node callNode, JSModule module, InliningMode mode){
      this.callNode = callNode;
      this.module = module;
      this.mode = mode;
    }
  }

  /**
   * In order to estimate the cost of lining, we make the assumption that
   * Identifiers are reduced 2 characters. For the call arguments, the important
   * thing is that the cost is assumed to be the same in the call and the
   * function, so the actual length doesn't matter in most cases.
   */
  private static final int NAME_COST_ESTIMATE =
      InlineCostEstimator.ESTIMATED_IDENTIFIER_COST;

  /** The cost of a argument separator (a comma). */
  private static final int COMMA_COST = 1;

  /** The cost of the parentheses needed to make a call.*/
  private static final int PAREN_COST = 2;


  /**
   * @param fnName The name of this function. This either the name of the
   *  variable to which the function is assigned or the name from the FUNCTION
   *  node.
   * @param fnNode The FUNCTION node of the function to inspect.
   * @return Whether the function node meets the minimum requirements for
   * inlining.
   */
  boolean doesFunctionMeetMinimumRequirements(
      final String fnName, Node fnNode) {
    Node block = NodeUtil.getFunctionBody(fnNode);

    // Basic restrictions on functions that can be inlined:
    // 0) The function is inlinable by convention
    // 1) It contains a reference to itself.
    // 2) It uses its parameters indirectly using "arguments" (it isn't
    //    handled yet.
    // 3) It references "eval". Inline a function containing eval can have
    //    large performance implications.

    if (!compiler.getCodingConvention().isInlinableFunction(fnNode)) {
      return false;
    }

    final String fnRecursionName = fnNode.getFirstChild().getString();
    Preconditions.checkState(fnRecursionName != null);

    // If the function references "arguments" directly in the function
    boolean referencesArguments = NodeUtil.isNameReferenced(
        block, "arguments", NodeUtil.MATCH_NOT_FUNCTION);

    // or it references "eval" or one of its names anywhere.
    Predicate<Node> p = new Predicate<Node>(){
      @Override
      public boolean apply(Node n) {
        if (n.isName()) {
          return n.getString().equals("eval")
            || (!fnName.isEmpty()
                && n.getString().equals(fnName))
            || (!fnRecursionName.isEmpty()
                && n.getString().equals(fnRecursionName));
        }
        return false;
      }
    };

    return !referencesArguments
        && !NodeUtil.has(block, p, Predicates.<Node>alwaysTrue());
  }

  /**
   * @param t  The traversal use to reach the call site.
   * @param callNode The CALL node.
   * @param fnNode The function to evaluate for inlining.
   * @param needAliases A set of function parameter names that can not be
   *     used without aliasing. Returned by getUnsafeParameterNames().
   * @param mode Inlining mode to be used.
   * @param referencesThis Whether fnNode contains references to its this
   *     object.
   * @param containsFunctions Whether fnNode contains inner functions.
   * @return Whether the inlining can occur.
   */
  CanInlineResult canInlineReferenceToFunction(NodeTraversal t,
      Node callNode, Node fnNode, Set<String> needAliases,
      InliningMode mode, boolean referencesThis, boolean containsFunctions) {
    // TODO(johnlenz): This function takes too many parameter, without
    // context.  Modify the API to take a structure describing the function.

    // Allow direct function calls or "fn.call" style calls.
    if (!isSupportedCallType(callNode)) {
      return CanInlineResult.NO;
    }

    // Limit where functions that contain functions can be inline.  Introducing
    // an inner function into another function can capture a variable and cause
    // a memory leak.  This isn't a problem in the global scope as those values
    // last until explicitly cleared.
    if (containsFunctions) {
      if (!assumeMinimumCapture && !t.inGlobalScope()) {
        // TODO(johnlenz): Allow inlining into any scope without local names or
        // inner functions.
        return CanInlineResult.NO;
      } else if (NodeUtil.isWithinLoop(callNode)) {
        // An inner closure maybe relying on a local value holding a value for a
        // single iteration through a loop.
        return CanInlineResult.NO;
      }
    }

    // TODO(johnlenz): Add support for 'apply'
    if (referencesThis && !NodeUtil.isFunctionObjectCall(callNode)) {
      // TODO(johnlenz): Allow 'this' references to be replaced with a
      // global 'this' object.
      return CanInlineResult.NO;
    }

    if (mode == InliningMode.DIRECT) {
      return canInlineReferenceDirectly(callNode, fnNode);
    } else {
      return canInlineReferenceAsStatementBlock(
          t, callNode, fnNode, needAliases);
    }
  }

  /**
   * Only ".call" calls and direct calls to functions are supported.
   * @param callNode The call evaluate.
   * @return Whether the call is of a type that is supported.
   */
  private boolean isSupportedCallType(Node callNode) {
    if (!callNode.getFirstChild().isName()) {
      if (NodeUtil.isFunctionObjectCall(callNode)) {
        if (!assumeStrictThis) {
          Node thisValue = callNode.getFirstChild().getNext();
          if (thisValue == null || !thisValue.isThis()) {
            return false;
          }
        }
      } else if (NodeUtil.isFunctionObjectApply(callNode)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Inline a function into the call site.
   */
  Node inline(
      Node callNode, String fnName, Node fnNode, InliningMode mode) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized());

    if (mode == InliningMode.DIRECT) {
      return inlineReturnValue(callNode, fnNode);
    } else {
      return inlineFunction(callNode, fnNode, fnName);
    }
  }

  /**
   * Inline a function that fulfills the requirements of
   * canInlineReferenceDirectly into the call site, replacing only the CALL
   * node.
   */
  private Node inlineReturnValue(Node callNode, Node fnNode) {
    Node block = fnNode.getLastChild();
    Node callParentNode = callNode.getParent();

    // NOTE: As the normalize pass guarantees globals aren't being
    // shadowed and an expression can't introduce new names, there is
    // no need to check for conflicts.

    // Create an argName -> expression map, checking for side effects.
    Map<String, Node> argMap =
        FunctionArgumentInjector.getFunctionCallParameterMap(
            fnNode, callNode, this.safeNameIdSupplier);

    Node newExpression;
    if (!block.hasChildren()) {
      Node srcLocation = block;
      newExpression = NodeUtil.newUndefinedNode(srcLocation);
    } else {
      Node returnNode = block.getFirstChild();
      Preconditions.checkArgument(returnNode.isReturn());

      // Clone the return node first.
      Node safeReturnNode = returnNode.cloneTree();
      Node inlineResult = FunctionArgumentInjector.inject(
          null, safeReturnNode, null, argMap);
      Preconditions.checkArgument(safeReturnNode == inlineResult);
      newExpression = safeReturnNode.removeFirstChild();
    }

    callParentNode.replaceChild(callNode, newExpression);
    return newExpression;
  }

  /**
   * Supported call site types.
   */
  private enum CallSiteType {

    /**
     * Used for a call site for which there does not exist a method
     * to inline it.
     */
    UNSUPPORTED() {
      @Override
      public void prepare(FunctionInjector injector, Node callNode) {
        throw new IllegalStateException("unexpected");
      }
    },

    /**
     * A call as a statement. For example: "foo();".
     *   EXPR_RESULT
     *     CALL
     */
    SIMPLE_CALL() {
      @Override
      public void prepare(FunctionInjector injector, Node callNode) {
        // Nothing to do.
      }
    },

    /**
     * An assignment, where the result of the call is assigned to a simple
     * name. For example: "a = foo();".
     *   EXPR_RESULT
     *     NAME A
     *     CALL
     *       FOO
     */
    SIMPLE_ASSIGNMENT() {
      @Override
      public void prepare(FunctionInjector injector, Node callNode) {
        // Nothing to do.
      }
    },
    /**
     * An var declaration and initialization, where the result of the call is
     * assigned to the declared name
     * name. For example: "a = foo();".
     *   VAR
     *     NAME A
     *       CALL
     *         FOO
     */
    VAR_DECL_SIMPLE_ASSIGNMENT() {
      @Override
      public void prepare(FunctionInjector injector, Node callNode) {
        // Nothing to do.
      }
    },
    /**
     * An arbitrary expression, the root of which is a EXPR_RESULT, IF,
     * RETURN, SWITCH or VAR.  The call must be the first side-effect in
     * the expression.
     *
     * Examples include:
     *   "if (foo()) {..."
     *   "return foo();"
     *   "var a = 1 + foo();"
     *   "a = 1 + foo()"
     *   "foo() ? 1:0"
     *   "foo() && x"
     */
    EXPRESSION() {
      @Override
      public void prepare(FunctionInjector injector, Node callNode) {
        injector.getDecomposer().moveExpression(callNode);

        // Reclassify after move
        CallSiteType callSiteType = injector.classifyCallSite(callNode);
        Preconditions.checkState(this != callSiteType);
        callSiteType.prepare(injector, callNode);
      }
    },

    /**
     * An arbitrary expression, the root of which is a EXPR_RESULT, IF,
     * RETURN, SWITCH or VAR.  Where the call is not the first side-effect in
     * the expression.
     */
    DECOMPOSABLE_EXPRESSION() {
      @Override
      public void prepare(FunctionInjector injector, Node callNode) {
        injector.getDecomposer().maybeExposeExpression(callNode);

        // Reclassify after decomposition
        CallSiteType callSiteType = injector.classifyCallSite(callNode);
        Preconditions.checkState(this != callSiteType);
        callSiteType.prepare(injector, callNode);
      }
    };

    public abstract void prepare(FunctionInjector injector, Node callNode);
  }

  /**
   * Determine which, if any, of the supported types the call site is.
   */
  private CallSiteType classifyCallSite(Node callNode) {
    Node parent = callNode.getParent();
    Node grandParent = parent.getParent();

    // Verify the call site:
    if (NodeUtil.isExprCall(parent)) {
      // This is a simple call?  Example: "foo();".
      return CallSiteType.SIMPLE_CALL;
    } else if (NodeUtil.isExprAssign(grandParent)
        && !NodeUtil.isVarOrSimpleAssignLhs(callNode, parent)
        && parent.getFirstChild().isName()
        && !NodeUtil.isConstantName(parent.getFirstChild())) {
      // This is a simple assignment.  Example: "x = foo();"
      return CallSiteType.SIMPLE_ASSIGNMENT;
    } else if (parent.isName()
        && !NodeUtil.isConstantName(parent)
        && grandParent.isVar()
        && grandParent.hasOneChild()) {
      // This is a var declaration.  Example: "var x = foo();"
      // TODO(johnlenz): Should we be checking for constants on the
      // left-hand-side of the assignments and handling them as EXPRESSION?
      return CallSiteType.VAR_DECL_SIMPLE_ASSIGNMENT;
    } else {
      Node expressionRoot = ExpressionDecomposer.findExpressionRoot(callNode);
      if (expressionRoot != null) {
        ExpressionDecomposer decomposer = new ExpressionDecomposer(
            compiler, safeNameIdSupplier, knownConstants);
        DecompositionType type = decomposer.canExposeExpression(
            callNode);
        if (type == DecompositionType.MOVABLE) {
          return CallSiteType.EXPRESSION;
        } else if (type == DecompositionType.DECOMPOSABLE) {
          return CallSiteType.DECOMPOSABLE_EXPRESSION;
        } else {
          Preconditions.checkState(type == DecompositionType.UNDECOMPOSABLE);
        }
      }
    }

    return CallSiteType.UNSUPPORTED;
  }

  private ExpressionDecomposer getDecomposer() {
    return new ExpressionDecomposer(
        compiler, safeNameIdSupplier, knownConstants);
  }

  /**
   * If required, rewrite the statement containing the call expression.
   * @see ExpressionDecomposer#canExposeExpression
   */
  void maybePrepareCall(Node callNode) {
    CallSiteType callSiteType = classifyCallSite(callNode);
    callSiteType.prepare(this, callNode);
  }

  /**
   * Inline a function which fulfills the requirements of
   * canInlineReferenceAsStatementBlock into the call site, replacing the
   * parent expression.
   */
  private Node inlineFunction(
      Node callNode, Node fnNode, String fnName) {
    Node parent = callNode.getParent();
    Node grandParent = parent.getParent();

    // TODO(johnlenz): Consider storing the callSite classification in the
    // reference object and passing it in here.
    CallSiteType callSiteType = classifyCallSite(callNode);
    Preconditions.checkArgument(callSiteType != CallSiteType.UNSUPPORTED);

    boolean isCallInLoop = NodeUtil.isWithinLoop(callNode);

    // Store the name for the result. This will be used to
    // replace "return expr" statements with "resultName = expr"
    // to replace
    String resultName = null;
    boolean needsDefaultReturnResult = true;
    switch (callSiteType) {
      case SIMPLE_ASSIGNMENT:
        resultName = parent.getFirstChild().getString();
        break;

      case VAR_DECL_SIMPLE_ASSIGNMENT:
        resultName = parent.getString();
        break;

      case SIMPLE_CALL:
        resultName = null;  // "foo()" doesn't need a result.
        needsDefaultReturnResult = false;
        break;

      case EXPRESSION:
        throw new IllegalStateException(
            "Movable expressions must be moved before inlining.");

      case DECOMPOSABLE_EXPRESSION:
        throw new IllegalStateException(
            "Decomposable expressions must be decomposed before inlining.");

      default:
        throw new IllegalStateException("Unexpected call site type.");
    }

    FunctionToBlockMutator mutator = new FunctionToBlockMutator(
        compiler, this.safeNameIdSupplier);

    Node newBlock = mutator.mutate(
        fnName, fnNode, callNode, resultName,
        needsDefaultReturnResult, isCallInLoop);

    // TODO(nicksantos): Create a common mutation function that
    // can replace either a VAR name assignment, assignment expression or
    // a EXPR_RESULT.
    Node greatGrandParent = grandParent.getParent();
    switch (callSiteType) {
      case VAR_DECL_SIMPLE_ASSIGNMENT:
        // Remove the call from the name node.
        parent.removeChild(parent.getFirstChild());
        Preconditions.checkState(parent.getFirstChild() == null);
        // Add the call, after the VAR.
        greatGrandParent.addChildAfter(newBlock, grandParent);
        break;

      case SIMPLE_ASSIGNMENT:
        // The assignment is now part of the inline function so
        // replace it completely.
        Preconditions.checkState(grandParent.isExprResult());
        greatGrandParent.replaceChild(grandParent, newBlock);
        break;

      case SIMPLE_CALL:
        // If nothing is looking at the result just replace the call.
        Preconditions.checkState(parent.isExprResult());
        grandParent.replaceChild(parent, newBlock);
        break;

      default:
        throw new IllegalStateException("Unexpected call site type.");
    }

    return newBlock;
  }

  /**
   * Checks if the given function matches the criteria for an inlinable
   * function, and if so, adds it to our set of inlinable functions.
   */
  boolean isDirectCallNodeReplacementPossible(Node fnNode) {
    // Only inline single-statement functions
    Node block = NodeUtil.getFunctionBody(fnNode);

    // Check if this function is suitable for direct replacement of a CALL node:
    // a function that consists of single return that returns an expression.
    if (!block.hasChildren()) {
      // special case empty functions.
      return true;
    } else if (block.hasOneChild()) {
      // Only inline functions that return something.
      if (block.getFirstChild().isReturn()
          && block.getFirstChild().getFirstChild() != null) {
        return true;
      }
    }

    return false;
  }

  enum CanInlineResult {
    YES,
    AFTER_PREPARATION,
    NO
  }

  /**
   * Determines whether a function can be inlined at a particular call site.
   * There are several criteria that the function and reference must hold in
   * order for the functions to be inlined:
   * - It must be a simple call, or assignment, or var initialization.
   * <pre>
   *    f();
   *    a = foo();
   *    var a = foo();
   * </pre>
   */
  private CanInlineResult canInlineReferenceAsStatementBlock(
      NodeTraversal t, Node callNode, Node fnNode, Set<String> namesToAlias) {
    CallSiteType callSiteType = classifyCallSite(callNode);
    if (callSiteType == CallSiteType.UNSUPPORTED) {
      return CanInlineResult.NO;
    }

    if (!allowDecomposition
        && (callSiteType == CallSiteType.DECOMPOSABLE_EXPRESSION
            || callSiteType == CallSiteType.EXPRESSION)) {
      return CanInlineResult.NO;
    }

    if (!callMeetsBlockInliningRequirements(
            t, callNode, fnNode, namesToAlias)) {
      return CanInlineResult.NO;
    }

    if (callSiteType == CallSiteType.DECOMPOSABLE_EXPRESSION
        || callSiteType == CallSiteType.EXPRESSION) {
      return CanInlineResult.AFTER_PREPARATION;
    } else {
      return CanInlineResult.YES;
    }
  }

  /**
   * Determines whether a function can be inlined at a particular call site.
   * - Don't inline if the calling function contains an inner function and
   * inlining would introduce new globals.
   */
  private boolean callMeetsBlockInliningRequirements(
      NodeTraversal t, Node callNode, final Node fnNode,
      Set<String> namesToAlias) {
    final boolean assumeMinimumCapture = this.assumeMinimumCapture;

    // Note: functions that contain function definitions are filtered out
    // in isCandidateFunction.

    // TODO(johnlenz): Determining if the called function contains VARs
    // or if the caller contains inner functions accounts for 20% of the
    // run-time cost of this pass.

    // Don't inline functions with var declarations into a scope with inner
    // functions as the new vars would leak into the inner function and
    // cause memory leaks.
    boolean fnContainsVars = NodeUtil.has(
        NodeUtil.getFunctionBody(fnNode),
        new NodeUtil.MatchDeclaration(),
        new NodeUtil.MatchShallowStatement());
    boolean forbidTemps = false;
    if (!t.inGlobalScope()) {
      Node fnCaller = t.getScopeRoot();
      Node fnCallerBody = fnCaller.getLastChild();

      // Don't allow any new vars into a scope that contains eval or one
      // that contains functions (excluding the function being inlined).
      Predicate<Node> match = new Predicate<Node>(){
        @Override
        public boolean apply(Node n) {
          if (n.isName()) {
            return n.getString().equals("eval");
          }
          if (!assumeMinimumCapture && n.isFunction()) {
            return n != fnNode;
          }
          return false;
        }
      };
      forbidTemps = NodeUtil.has(fnCallerBody,
          match, NodeUtil.MATCH_NOT_FUNCTION);
    }

    if (fnContainsVars && forbidTemps) {
      return false;
    }

    // If the caller contains functions or evals, verify we aren't adding any
    // additional VAR declarations because aliasing is needed.
    if (forbidTemps) {
      Map<String, Node> args =
          FunctionArgumentInjector.getFunctionCallParameterMap(
              fnNode, callNode, this.safeNameIdSupplier);
      boolean hasArgs = !args.isEmpty();
      if (hasArgs) {
        // Limit the inlining
        Set<String> allNamesToAlias = Sets.newHashSet(namesToAlias);
        FunctionArgumentInjector.maybeAddTempsForCallArguments(
            fnNode, args, allNamesToAlias, compiler.getCodingConvention());
        if (!allNamesToAlias.isEmpty()) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Determines whether a function can be inlined at a particular call site.
   * There are several criteria that the function and reference must hold in
   * order for the functions to be inlined:
   * 1) If a call's arguments have side effects,
   * the corresponding argument in the function must only be referenced once.
   * For instance, this will not be inlined:
   * <pre>
   *     function foo(a) { return a + a }
   *     x = foo(i++);
   * </pre>
   */
  private CanInlineResult canInlineReferenceDirectly(
      Node callNode, Node fnNode) {
    if (!isDirectCallNodeReplacementPossible(fnNode)) {
      return CanInlineResult.NO;
    }

    Node block = fnNode.getLastChild();

    boolean hasSideEffects = false;  // empty function case
    if (block.hasChildren()) {
      Preconditions.checkState(block.hasOneChild());
      Node stmt = block.getFirstChild();
      if (stmt.isReturn()) {
        hasSideEffects = NodeUtil.mayHaveSideEffects(
            stmt.getFirstChild(), compiler);
      }
    }

    // CALL NODE: [ NAME, ARG1, ARG2, ... ]
    Node cArg = callNode.getFirstChild().getNext();

    // Functions called via 'call' and 'apply' have a this-object as
    // the first parameter, but this is not part of the called function's
    // parameter list.
    if (!callNode.getFirstChild().isName()) {
      if (NodeUtil.isFunctionObjectCall(callNode)) {
        // TODO(johnlenz): Support replace this with a value.
        if (cArg == null || !cArg.isThis()) {
          return CanInlineResult.NO;
        }
        cArg = cArg.getNext();
      } else {
        // ".apply" call should be filtered before this.
        Preconditions.checkState(!NodeUtil.isFunctionObjectApply(callNode));
      }
    }

    // FUNCTION NODE -> LP NODE: [ ARG1, ARG2, ... ]
    Node fnParam = NodeUtil.getFunctionParameters(fnNode).getFirstChild();
    while (cArg != null || fnParam != null) {
      // For each named parameter check if a mutable argument use more than one.
      if (fnParam != null) {
        if (cArg != null) {
          if (hasSideEffects && NodeUtil.canBeSideEffected(cArg)) {
            return CanInlineResult.NO;
          }

          // Check for arguments that are evaluated more than once.
          // Note: Unlike block inlining, there it is not possible that a
          // parameter reference will be in a loop.
          if (NodeUtil.mayEffectMutableState(cArg, compiler)
              && NodeUtil.getNameReferenceCount(
                  block, fnParam.getString()) > 1) {
            return CanInlineResult.NO;
          }
        }

        // Move to the next name.
        fnParam = fnParam.getNext();
      }

      // For every call argument check for side-effects, even if there
      // isn't a named parameter to match.
      if (cArg != null) {
        if (NodeUtil.mayHaveSideEffects(cArg, compiler)) {
          return CanInlineResult.NO;
        }
        cArg = cArg.getNext();
      }
    }

    return CanInlineResult.YES;
  }

  /**
   * Determine if inlining the function is likely to reduce the code size.
   * @param namesToAlias
   */
  boolean inliningLowersCost(
      JSModule fnModule, Node fnNode, Collection<? extends Reference> refs,
      Set<String> namesToAlias, boolean isRemovable, boolean referencesThis) {
    int referenceCount = refs.size();
    if (referenceCount == 0) {
      return true;
    }

    int referencesUsingBlockInlining = 0;

    boolean checkModules = isRemovable && fnModule != null;
    JSModuleGraph moduleGraph = compiler.getModuleGraph();

    for (Reference ref : refs) {
      if (ref.mode == InliningMode.BLOCK) {
        referencesUsingBlockInlining++;
      }

      // Check if any of the references cross the module boundaries.
      if (checkModules && ref.module != null) {
        if (ref.module != fnModule &&
            !moduleGraph.dependsOn(ref.module, fnModule)) {
          // Calculate the cost as if the function were non-removable,
          // if it still lowers the cost inline it.
          isRemovable = false;
          checkModules = false;  // no need to check additional modules.
        }
      }
    }

    int referencesUsingDirectInlining = referenceCount -
        referencesUsingBlockInlining;

    // Don't bother calculating the cost of function for simple functions where
    // possible.
    // However, when inlining a complex function, even a single reference may be
    // larger than the original function if there are many returns (resulting
    // in additional assignments) or many parameters that need to be aliased
    // so use the cost estimating.
    if (referenceCount == 1 && isRemovable &&
        referencesUsingDirectInlining == 1) {
      return true;
    }

    int callCost = estimateCallCost(fnNode, referencesThis);
    int overallCallCost = callCost * referenceCount;

    int costDeltaDirect = inlineCostDelta(
        fnNode, namesToAlias, InliningMode.DIRECT);
    int costDeltaBlock = inlineCostDelta(
        fnNode, namesToAlias, InliningMode.BLOCK);

    return doesLowerCost(fnNode, overallCallCost,
        referencesUsingDirectInlining, costDeltaDirect,
        referencesUsingBlockInlining, costDeltaBlock,
        isRemovable);
  }

  /**
   * @return Whether inlining will lower cost.
   */
  private boolean doesLowerCost(
      Node fnNode, int callCost,
      int directInlines, int costDeltaDirect,
      int blockInlines, int costDeltaBlock,
      boolean removable) {

    // Determine the threshold value for this inequality:
    //     inline_cost < call_cost
    // But solve it for the function declaration size so the size of it
    // is only calculated once and terminated early if possible.

    int fnInstanceCount = directInlines + blockInlines - (removable ? 1 : 0);
    // Prevent division by zero.
    if (fnInstanceCount == 0) {
      // Special case single reference function that are being block inlined:
      // If the cost of the inline is greater than the function definition size,
      // don't inline.
      if (blockInlines > 0 && costDeltaBlock > 0) {
        return false;
      }
      return true;
    }

    int costDelta = (directInlines * costDeltaDirect) +
        (blockInlines * costDeltaBlock);
    int threshold = (callCost - costDelta) / fnInstanceCount;

    return InlineCostEstimator.getCost(fnNode, threshold + 1) <= threshold;
  }

  /**
   * Gets an estimate of the cost in characters of making the function call:
   * the sum of the identifiers and the separators.
   * @param referencesThis
   */
  private static int estimateCallCost(Node fnNode, boolean referencesThis) {
    Node argsNode = NodeUtil.getFunctionParameters(fnNode);
    int numArgs = argsNode.getChildCount();

    int callCost = NAME_COST_ESTIMATE + PAREN_COST;
    if (numArgs > 0) {
      callCost += (numArgs * NAME_COST_ESTIMATE) + ((numArgs - 1) * COMMA_COST);
    }

    if (referencesThis) {
      // TODO(johnlenz): Update this if we start supporting inlining
      // other functions that reference this.
      // The only functions that reference this that are currently inlined
      // are those that are called via ".call" with an explicit "this".
      callCost += 5 + 5;  // ".call" + "this,"
    }

    return callCost;
  }

  /**
   * @return The difference between the function definition cost and
   *     inline cost.
   */
  private static int inlineCostDelta(
      Node fnNode, Set<String> namesToAlias, InliningMode mode) {
    // The part of the function that is never inlined:
    //    "function xx(xx,xx){}" (15 + (param count * 3) -1;
    int paramCount = NodeUtil.getFunctionParameters(fnNode).getChildCount();
    int commaCount = (paramCount > 1) ? paramCount - 1 : 0;
    int costDeltaFunctionOverhead = 15 + commaCount +
        (paramCount * InlineCostEstimator.ESTIMATED_IDENTIFIER_COST);

    Node block = fnNode.getLastChild();
    if (!block.hasChildren()) {
      // Assume the inline cost is zero for empty functions.
      return -costDeltaFunctionOverhead;
    }

    if (mode == InliningMode.DIRECT) {
      // The part of the function that is inlined using direct inlining:
      //    "return " (7)
      return -(costDeltaFunctionOverhead + 7);
    } else {
      int aliasCount = namesToAlias.size();

      // Originally, we estimated purely base on the function code size, relying
      // on later optimizations. But that did not produce good results, so here
      // we try to estimate the something closer to the actual inlined coded.

      // NOTE 1: Result overhead is only if there is an assignment, but
      // getting that information would require some refactoring.
      // NOTE 2: The aliasing overhead is currently an under-estimate,
      // as some parameters are aliased because of the parameters used.
      // Perhaps we should just assume all parameters will be aliased?
      final int inlineBlockOverhead = 4; // "X:{}"
      final int perReturnOverhead = 2;   // "return" --> "break X"
      final int perReturnResultOverhead = 3; // "XX="
      final int perAliasOverhead = 3; // "XX="

      // TODO(johnlenz): Counting the number of returns is relatively expensive
      //   this information should be determined during the traversal and
      //   cached.
      int returnCount = NodeUtil.getNodeTypeReferenceCount(
          block, Token.RETURN, new NodeUtil.MatchShallowStatement());
      int resultCount = (returnCount > 0) ? returnCount - 1 : 0;
      int baseOverhead = (returnCount > 0) ? inlineBlockOverhead : 0;

      int overhead = baseOverhead
          + returnCount * perReturnOverhead
          + resultCount * perReturnResultOverhead
          + aliasCount * perAliasOverhead;

      return (overhead - costDeltaFunctionOverhead);
    }
  }

  /**
   * Store the names of known constants to be used when classifying call-sites
   * in expressions.
   */
  public void setKnownConstants(Set<String> knownConstants) {
    // This is only expected to be set once. The same set should be used
    // when evaluating call-sites and inlining calls.
    Preconditions.checkState(this.knownConstants.isEmpty());
    this.knownConstants = knownConstants;
  }
}
