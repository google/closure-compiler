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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.nullness.Nullable;

/**
 * A set of utility functions that replaces CALL with a specified FUNCTION body, replacing and
 * aliasing function parameters as necessary.
 */
class FunctionInjector {

  /** Sentinel value indicating that the key contains no functions. */
  private static final Node NO_FUNCTIONS = new Node(Token.FUNCTION);

  /** Sentinel value indicating that the key contains multiple distinct functions. */
  private static final Node MULTIPLE_FUNCTIONS = new Node(Token.FUNCTION);

  private final AbstractCompiler compiler;
  private final boolean allowDecomposition;
  private ImmutableSet<String> knownConstantFunctions = ImmutableSet.of();
  private final boolean assumeStrictThis;
  private final boolean assumeMinimumCapture;
  private final Supplier<String> safeNameIdSupplier;
  private final Supplier<String> throwawayNameSupplier =
      new Supplier<String>() {
        private int nextId = 0;

        @Override
        public String get() {
          return String.valueOf(nextId++);
        }
      };
  private final FunctionArgumentInjector functionArgumentInjector;

  /** Cache of function node to whether it deeply contains an {@code eval} call. */
  private final LinkedHashMap<Node, Boolean> referencesEvalCache = new LinkedHashMap<>();

  /** Cache of function node to any inner function. */
  private final LinkedHashMap<Node, Node> innerFunctionCache = new LinkedHashMap<>();

  private FunctionInjector(Builder builder) {
    this.compiler = checkNotNull(builder.compiler);
    this.safeNameIdSupplier = checkNotNull(builder.safeNameIdSupplier);
    this.assumeStrictThis = builder.assumeStrictThis;
    this.assumeMinimumCapture = builder.assumeMinimumCapture;
    this.allowDecomposition = builder.allowDecomposition;
    this.functionArgumentInjector = checkNotNull(builder.functionArgumentInjector);
  }

  static class Builder {

    private final AbstractCompiler compiler;
    private @Nullable Supplier<String> safeNameIdSupplier = null;
    private boolean assumeStrictThis = true;
    private boolean assumeMinimumCapture = true;
    private boolean allowDecomposition = true;
    private @Nullable FunctionArgumentInjector functionArgumentInjector = null;

    Builder(AbstractCompiler compiler) {
      this.compiler = checkNotNull(compiler);
    }

    /**
     * Provide the name supplier to use for injection.
     *
     * <p>If this method is not called, {@code compiler.getUniqueNameIdSupplier()} will be used.
     */
    @CanIgnoreReturnValue
    Builder safeNameIdSupplier(Supplier<String> safeNameIdSupplier) {
      this.safeNameIdSupplier = checkNotNull(safeNameIdSupplier);
      return this;
    }

    /**
     * Allow decomposition of expressions.
     *
     * <p>Default is {@code true}.
     */
    @CanIgnoreReturnValue
    Builder allowDecomposition(boolean allowDecomposition) {
      this.allowDecomposition = allowDecomposition;
      return this;
    }

    @CanIgnoreReturnValue
    Builder assumeStrictThis(boolean assumeStrictThis) {
      this.assumeStrictThis = assumeStrictThis;
      return this;
    }

    @CanIgnoreReturnValue
    Builder assumeMinimumCapture(boolean assumeMinimumCapture) {
      this.assumeMinimumCapture = assumeMinimumCapture;
      return this;
    }

    /**
     * Specify the {@code FunctionArgumentInjector} to be used.
     *
     * <p>Default is for the builder to create this. This method exists for testing purposes.
     */
    @CanIgnoreReturnValue
    public Builder functionArgumentInjector(FunctionArgumentInjector functionArgumentInjector) {
      this.functionArgumentInjector = checkNotNull(functionArgumentInjector);
      return this;
    }

    public FunctionInjector build() {
      if (safeNameIdSupplier == null) {
        safeNameIdSupplier = compiler.getUniqueNameIdSupplier();
      }
      if (functionArgumentInjector == null) {
        functionArgumentInjector =
            new FunctionArgumentInjector(checkNotNull(compiler.getAstAnalyzer()));
      }
      return new FunctionInjector(this);
    }
  }

  /** The type of inlining to perform. */
  enum InliningMode {
    /**
     * Directly replace the call expression. Only functions of meeting strict preconditions can be
     * inlined.
     */
    DIRECT,

    /**
     * Replaces the call expression with a block of statements. Conditions on the function are
     * looser in mode, but stricter on the call site.
     */
    BLOCK
  }

  /** Holds a reference to the call node of a function call */
  static class Reference {
    final Node callNode;
    final Scope scope;
    final JSChunk chunk;
    final InliningMode mode;

    Reference(Node callNode, Scope scope, JSChunk chunk, InliningMode mode) {
      this.callNode = callNode;
      this.scope = scope;
      this.chunk = chunk;
      this.mode = mode;
    }

    @Override
    public String toString() {
      return "Reference @ " + callNode;
    }
  }

  /**
   * In order to estimate the cost of lining, we make the assumption that Identifiers are reduced 2
   * characters. For the call arguments, the important thing is that the cost is assumed to be the
   * same in the call and the function, so the actual length doesn't matter in most cases.
   */
  private static final int NAME_COST_ESTIMATE = InlineCostEstimator.ESTIMATED_IDENTIFIER_COST;

  /** The cost of a argument separator (a comma). */
  private static final int COMMA_COST = 1;

  /** The cost of the parentheses needed to make a call. */
  private static final int PAREN_COST = 2;

  /**
   * @param fnName The name of this function. This either the name of the variable to which the
   *     function is assigned or the name from the FUNCTION node.
   * @param fnNode The FUNCTION node of the function to inspect.
   * @return Whether the function node meets the minimum requirements for inlining.
   */
  boolean doesFunctionMeetMinimumRequirements(final String fnName, Node fnNode) {
    Node block = NodeUtil.getFunctionBody(fnNode);

    // Basic restrictions on functions that can be inlined:
    // 1) It contains a reference to itself.
    // 2) It uses its parameters indirectly using "arguments" (it isn't
    //    handled yet.
    // 3) It references "eval". Inline a function containing eval can have
    //    large performance implications.

    final String fnRecursionName = fnNode.getFirstChild().getString();
    checkState(fnRecursionName != null);

    // If the function references "arguments" directly in the function or in an arrow function
    boolean referencesArguments =
        NodeUtil.isNameReferenced(
            block, "arguments", NodeUtil.MATCH_ANYTHING_BUT_NON_ARROW_FUNCTION);

    Predicate<Node> blocksInjection =
        (Node n) -> {
          if (n.isName()) {
            // References "eval" or one of its names anywhere.
            return n.getString().equals("eval")
                || (!fnName.isEmpty() && n.getString().equals(fnName))
                || (!fnRecursionName.isEmpty() && n.getString().equals(fnRecursionName));
          } else if (n.isSuper()) {
            // Don't inline if this function or its inner functions contains super
            return true;
          }
          return false;
        };

    return !referencesArguments && !NodeUtil.has(block, blocksInjection, Predicates.alwaysTrue());
  }

  /**
   * @param fnNode The function to evaluate for inlining.
   * @param needAliases A set of function parameter names that can not be used without aliasing.
   *     Returned by getUnsafeParameterNames().
   * @param referencesThis Whether fnNode contains references to its this object.
   * @param containsFunctions Whether fnNode contains inner functions.
   * @return Whether the inlining can occur.
   */
  CanInlineResult canInlineReferenceToFunction(
      Reference ref,
      Node fnNode,
      ImmutableSet<String> needAliases,
      boolean referencesThis,
      boolean containsFunctions) {
    // TODO(johnlenz): This function takes too many parameter, without
    // context.  Modify the API to take a structure describing the function.

    // Allow direct function calls or "fn.call" style calls.
    Node callNode = ref.callNode;
    if (!isSupportedCallType(callNode)) {
      return CanInlineResult.NO;
    }

    if (hasSpreadCallArgument(callNode)) {
      return CanInlineResult.NO;
    }

    // Limit where functions that contain functions can be inline.  Introducing
    // an inner function into another function can capture a variable and cause
    // a memory leak.  This isn't a problem in the global scope as those values
    // last until explicitly cleared.
    if (containsFunctions) {
      if (!assumeMinimumCapture && !ref.scope.isGlobal()) {
        // TODO(johnlenz): Allow inlining into any scope without local names or inner functions.
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

    if (ref.mode == InliningMode.DIRECT) {
      return canInlineReferenceDirectly(ref, fnNode, needAliases);
    } else {
      return canInlineReferenceAsStatementBlock(ref, fnNode, needAliases);
    }
  }

  /**
   * Only ".call" calls and direct calls to functions are supported.
   *
   * @param callNode The call evaluate.
   * @return Whether the call is of a type that is supported.
   */
  private boolean isSupportedCallType(Node callNode) {
    if (!callNode.getFirstChild().isName()) {
      if (NodeUtil.isFunctionObjectCall(callNode)) {
        if (!assumeStrictThis) {
          Node thisValue = callNode.getSecondChild();
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

  private static boolean hasSpreadCallArgument(Node callNode) {
    checkArgument(NodeUtil.isNormalOrOptChainCall(callNode), callNode);
    for (Node arg = callNode.getSecondChild(); arg != null; arg = arg.getNext()) {
      if (arg.isSpread()) {
        return true;
      }
    }
    return false;
  }

  /** Inline a function into the call site. */
  Node inline(Reference ref, String fnName, Node fnNode) {
    checkState(compiler.getLifeCycleStage().isNormalized());
    return internalInline(ref, fnName, fnNode);
  }

  /**
   * Inline a function into the call site. Note that this unsafe version doesn't verify if the AST
   * is normalized. You should use {@link #inline} instead, unless you are 100% certain that the bit
   * of code you're inlining is safe without being normalized first.
   */
  Node unsafeInline(Reference ref, String fnName, Node fnNode) {
    return internalInline(ref, fnName, fnNode);
  }

  private Node internalInline(Reference ref, String fnName, Node fnNode) {
    Node result;
    if (ref.mode == InliningMode.DIRECT) {
      result = inlineReturnValue(ref, fnNode);
    } else {
      result = inlineFunction(ref, fnNode, fnName);
    }
    compiler.reportChangeToEnclosingScope(result);
    return result;
  }

  /**
   * Inline a function that fulfills the requirements of canInlineReferenceDirectly into the call
   * site, replacing only the CALL node.
   */
  private Node inlineReturnValue(Reference ref, Node fnNode) {
    Node callNode = ref.callNode;
    Node block = fnNode.getLastChild();

    // NOTE: As the normalize pass guarantees globals aren't being
    // shadowed and an expression can't introduce new names, there is
    // no need to check for conflicts.

    // Create an argName -> expression map, checking for side effects.
    ImmutableMap<String, Node> argMap =
        functionArgumentInjector.getFunctionCallParameterMap(
            fnNode, callNode, this.safeNameIdSupplier);

    Node newExpression;
    if (!block.hasChildren()) {
      Node srcLocation = block;
      newExpression = NodeUtil.newUndefinedNode(srcLocation);
    } else {
      Node returnNode = block.getFirstChild();
      checkArgument(returnNode.isReturn(), returnNode);

      // Clone the return node first.
      Node safeReturnNode = returnNode.cloneTree();
      Node inlineResult = functionArgumentInjector.inject(null, safeReturnNode, null, argMap);
      checkArgument(safeReturnNode == inlineResult);
      newExpression = safeReturnNode.removeFirstChild();
      NodeUtil.markNewScopesChanged(newExpression, compiler);
    }

    // If the call site had a cast ensure it's persisted to the new expression that replaces it.
    JSType typeBeforeCast = callNode.getJSTypeBeforeCast();
    if (typeBeforeCast != null) {
      newExpression.setJSTypeBeforeCast(typeBeforeCast);
      newExpression.setJSType(callNode.getJSType());
    }
    // If the new expression has no color or the UNKNOWN color, attach the color the call node. It
    // may be more accurate if the call node was in a cast (we don't track information about casts,
    // though)
    if (callNode.getColor() != null && callNode.isColorFromTypeCast()) {
      newExpression.setColor(callNode.getColor());
      newExpression.setColorFromTypeCast();
    }
    callNode.replaceWith(newExpression);
    NodeUtil.markFunctionsDeleted(callNode, compiler);
    return newExpression;
  }

  /** Supported call site types. */
  private static enum CallSiteType {

    /** Used for a call site for which there does not exist a method to inline it. */
    UNSUPPORTED() {
      @Override
      public void prepare(FunctionInjector injector, Reference ref) {
        throw new IllegalStateException("unexpected: " + ref);
      }
    },

    /** A call as a statement. For example: "foo();". EXPR_RESULT CALL */
    SIMPLE_CALL() {
      @Override
      public void prepare(FunctionInjector injector, Reference ref) {
        // Nothing to do.
      }
    },

    /**
     * An assignment, where the result of the call is assigned to a simple name. For example: "a =
     * foo();". EXPR_RESULT NAME A CALL FOO
     */
    SIMPLE_ASSIGNMENT() {
      @Override
      public void prepare(FunctionInjector injector, Reference ref) {
        // Nothing to do.
      }
    },

    /**
     * An var declaration and initialization, where the result of the call is assigned to the
     * declared name name. For example: "var a = foo();". VAR NAME A CALL FOO
     */
    VAR_DECL_SIMPLE_ASSIGNMENT() {
      @Override
      public void prepare(FunctionInjector injector, Reference ref) {
        // Nothing to do.
      }
    },

    /**
     * An arbitrary expression, the root of which is a EXPR_RESULT, IF, RETURN, SWITCH or VAR. The
     * call must be the first side-effect in the expression.
     *
     * <p>Examples include: "if (foo()) {..." "return foo();" "var a = 1 + foo();" "a = 1 + foo()"
     * "foo() ? 1:0" "foo() && x"
     */
    EXPRESSION() {
      @Override
      public void prepare(FunctionInjector injector, Reference ref) {
        Node callNode = ref.callNode;
        injector.getDecomposer(ref.scope).moveExpression(callNode);

        // Reclassify after move
        CallSiteType callSiteType = injector.classifyCallSite(ref);
        checkState(this != callSiteType);
        callSiteType.prepare(injector, ref);
      }
    },

    /**
     * An arbitrary expression, the root of which is a EXPR_RESULT, IF, RETURN, SWITCH or VAR. Where
     * the call is not the first side-effect in the expression.
     */
    DECOMPOSABLE_EXPRESSION() {
      @Override
      public void prepare(FunctionInjector injector, Reference ref) {
        Node callNode = ref.callNode;
        injector.getDecomposer(ref.scope).maybeExposeExpression(callNode);

        // Reclassify after decomposition
        CallSiteType callSiteType = injector.classifyCallSite(ref);
        checkState(this != callSiteType);
        callSiteType.prepare(injector, ref);
      }
    };

    public abstract void prepare(FunctionInjector injector, Reference ref);
  }

  /**
   * Determine which, if any, of the supported types the call site is.
   *
   * <p>Constant vars are treated differently so that we don't break their const-ness when we
   * decompose the expression. Once the CONSTANT_VAR annotation is used everywhere instead of coding
   * conventions, we should just teach this pass how to remove the annotation.
   */
  private CallSiteType classifyCallSite(Reference ref) {
    Node callNode = ref.callNode;
    Node parent = callNode.getParent();
    Node grandParent = parent.getParent();

    // Verify the call site:
    if (NodeUtil.isExprCall(parent)) {
      // This is a simple call. Example: "foo();".
      return CallSiteType.SIMPLE_CALL;
    } else if (NodeUtil.isExprAssign(grandParent)
        && !NodeUtil.isNameDeclOrSimpleAssignLhs(callNode, parent)
        && parent.getFirstChild().isName()
        // TODO(nicksantos): Remove this once everyone is using
        // the CONSTANT_VAR annotation. We know how to remove that.
        && !NodeUtil.isConstantName(parent.getFirstChild())) {
      // This is a simple assignment.  Example: "x = foo();"
      return CallSiteType.SIMPLE_ASSIGNMENT;
    } else if (parent.isName()
        // TODO(nicksantos): Remove this once everyone is using the CONSTANT_VAR annotation.
        && !NodeUtil.isConstantName(parent)
        // Note: not let or const. See InlineFunctionsTest.testInlineFunctions35
        && grandParent.isVar()
        && grandParent.hasOneChild()) {
      // This is a var declaration.  Example: "var x = foo();"
      // TODO(johnlenz): Should we be checking for constants on the
      // left-hand-side of the assignments and handling them as EXPRESSION?
      return CallSiteType.VAR_DECL_SIMPLE_ASSIGNMENT;
    } else {
      ExpressionDecomposer decomposer = getDecomposer(ref.scope);
      switch (decomposer.canExposeExpression(callNode)) {
        case MOVABLE:
          return CallSiteType.EXPRESSION;
        case DECOMPOSABLE:
          return CallSiteType.DECOMPOSABLE_EXPRESSION;
        case UNDECOMPOSABLE:
          break;
      }
    }

    return CallSiteType.UNSUPPORTED;
  }

  private ExpressionDecomposer getDecomposer(Scope scope) {
    return compiler.createExpressionDecomposer(safeNameIdSupplier, knownConstantFunctions, scope);
  }

  /**
   * If required, rewrite the statement containing the call expression.
   *
   * @see ExpressionDecomposer#canExposeExpression
   */
  void maybePrepareCall(Reference ref) {
    CallSiteType callSiteType = classifyCallSite(ref);
    callSiteType.prepare(this, ref);
  }

  /**
   * Inline a function which fulfills the requirements of canInlineReferenceAsStatementBlock into
   * the call site, replacing the parent expression.
   */
  private Node inlineFunction(Reference ref, Node fnNode, String fnName) {
    Node callNode = ref.callNode;
    Node parent = callNode.getParent();
    Node grandParent = parent.getParent();

    // TODO(johnlenz): Consider storing the callSite classification in the
    // reference object and passing it in here.
    CallSiteType callSiteType = classifyCallSite(ref);
    checkArgument(callSiteType != CallSiteType.UNSUPPORTED);

    // Store the name for the result. This will be used to
    // replace "return expr" statements with "resultName = expr"
    // to replace
    String resultName = null;
    boolean needsDefaultReturnResult = true;
    switch (callSiteType) {
      case SIMPLE_ASSIGNMENT:
        resultName = parent.getFirstChild().getString();
        removeConstantVarAnnotation(ref.scope, resultName);
        break;

      case VAR_DECL_SIMPLE_ASSIGNMENT:
        resultName = parent.getString();
        removeConstantVarAnnotation(ref.scope, resultName);
        break;

      case SIMPLE_CALL:
        resultName = null; // "foo()" doesn't need a result.
        needsDefaultReturnResult = false;
        break;

      case EXPRESSION:
        throw new IllegalStateException("Movable expressions must be moved before inlining.");

      case DECOMPOSABLE_EXPRESSION:
        throw new IllegalStateException(
            "Decomposable expressions must be decomposed before inlining.");

      default:
        throw new IllegalStateException("Unexpected call site type.");
    }

    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, this.safeNameIdSupplier);

    boolean isCallInLoop = NodeUtil.isWithinLoop(callNode);
    Node newBlock =
        mutator.mutate(
            fnName, fnNode, callNode, resultName, needsDefaultReturnResult, isCallInLoop);
    NodeUtil.markNewScopesChanged(newBlock, compiler);

    // TODO(nicksantos): Create a common mutation function that
    // can replace either a VAR name assignment, assignment expression or
    // a EXPR_RESULT.
    switch (callSiteType) {
      case VAR_DECL_SIMPLE_ASSIGNMENT:
        // Remove the call from the name node.
        Node firstChild = parent.removeFirstChild();
        NodeUtil.markFunctionsDeleted(firstChild, compiler);
        Preconditions.checkState(!parent.hasChildren());
        // Add the call, after the VAR.
        newBlock.insertAfter(grandParent);
        break;

      case SIMPLE_ASSIGNMENT:
        // The assignment is now part of the inline function so
        // replace it completely.
        Preconditions.checkState(grandParent.isExprResult());
        grandParent.replaceWith(newBlock);
        NodeUtil.markFunctionsDeleted(grandParent, compiler);
        break;

      case SIMPLE_CALL:
        // If nothing is looking at the result just replace the call.
        Preconditions.checkState(parent.isExprResult());
        parent.replaceWith(newBlock);
        NodeUtil.markFunctionsDeleted(parent, compiler);
        break;

      default:
        throw new IllegalStateException("Unexpected call site type.");
    }

    return newBlock;
  }

  private static void removeConstantVarAnnotation(Scope scope, String name) {
    Var var = scope.getVar(name);
    Node nameNode = var == null ? null : var.getNameNode();
    if (nameNode == null) {
      return;
    }

    if (nameNode.isDeclaredConstantVar()) {
      nameNode.setDeclaredConstantVar(false);
    }
  }

  /**
   * Checks if the given function matches the criteria for an inlinable function, and if so, adds it
   * to our set of inlinable functions.
   */
  static boolean isDirectCallNodeReplacementPossible(Node fnNode) {
    // Only inline single-statement functions
    Node block = NodeUtil.getFunctionBody(fnNode);

    // Check if this function is suitable for direct replacement of a CALL node:
    // a function that consists of single return that returns an expression.
    if (!block.hasChildren()) {
      // special case empty functions.
      return true;
    } else if (block.hasOneChild()) {
      // Only inline functions that return something.
      if (block.getFirstChild().isReturn() && block.getFirstFirstChild() != null) {
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
   * Determines whether a function can be inlined at a particular call site. There are several
   * criteria that the function and reference must hold in order for the functions to be inlined: -
   * It must be a simple call, or assignment, or var initialization.
   *
   * <pre>
   *    f();
   *    a = foo();
   *    var a = foo();
   * </pre>
   */
  private CanInlineResult canInlineReferenceAsStatementBlock(
      Reference ref, Node fnNode, ImmutableSet<String> namesToAlias) {
    CallSiteType callSiteType = classifyCallSite(ref);
    if (callSiteType == CallSiteType.UNSUPPORTED) {
      return CanInlineResult.NO;
    }

    if (!allowDecomposition
        && (callSiteType == CallSiteType.DECOMPOSABLE_EXPRESSION
            || callSiteType == CallSiteType.EXPRESSION)) {
      return CanInlineResult.NO;
    }

    if (!callMeetsBlockInliningRequirements(ref, fnNode, namesToAlias)) {
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
   * Returns whether or not {@code fn} includes a call to `eval` in its scope.
   *
   * <p>Results are cached to make subsequent calls faster.
   */
  private boolean referencesEval(Node fn) {
    checkState(fn.isFunction());

    @Nullable Boolean cached = this.referencesEvalCache.get(fn);
    if (cached != null) {
      return cached;
    }

    boolean result =
        NodeUtil.has(
            fn, //
            (n) -> n.isName() && n.getString().equals("eval"), // Match predicate
            (n) -> !n.isFunction() || n.equals(fn)); // Explore node predicate
    this.referencesEvalCache.put(fn, result);
    return result;
  }

  /**
   * Returns any inner function of {@code containerFn}.
   *
   * <p>If there are no inner functions, or multilple inner functions, the sentinel values {@link
   * #NO_FUNCTIONS}, {@link #MULTIPLE_FUNCTIONS} are returned respectively.
   */
  private Node innerFunctionOf(Node containerFn) {
    checkState(containerFn.isFunction());

    @Nullable Node cached = this.innerFunctionCache.get(containerFn);
    if (cached != null) {
      return cached;
    }

    ArrayList<Node> innerFns = new ArrayList<>();
    NodeUtil.visitPreOrder(
        containerFn,
        (n) -> {
          if (n.equals(containerFn)) {
            return;
          }

          if (n.isFunction()) {
            innerFns.add(n);
          }
        });

    switch (innerFns.size()) {
      case 0:
        cached = NO_FUNCTIONS;
        break;
      case 1:
        cached = innerFns.get(0);
        break;
      default:
        cached = MULTIPLE_FUNCTIONS;
        break;
    }

    this.innerFunctionCache.put(containerFn, cached);
    return cached;
  }

  /**
   * Determines whether a function can be inlined at a particular call site. - Don't inline if the
   * calling function contains an inner function and inlining would introduce new globals.
   */
  private boolean callMeetsBlockInliningRequirements(
      Reference callRef, final Node calleeFn, ImmutableSet<String> namesToAlias) {
    // Note: functions that contain function definitions are filtered out
    // in isCandidateFunction.

    // TODO(johnlenz): Determining if the called function contains VARs
    // or if the caller contains inner functions accounts for 20% of the
    // run-time cost of this pass.
    //
    // Note that as of 2019-10-11, "eval" and function checking are cached so this may be less
    // of an issue.

    // Don't inline functions with var declarations into a scope with inner
    // functions as the new vars would leak into the inner function and
    // cause memory leaks.
    boolean calleeContainsVars =
        NodeUtil.has(
            NodeUtil.getFunctionBody(calleeFn),
            new NodeUtil.MatchDeclaration(),
            new NodeUtil.MatchShallowStatement());

    boolean forbidTemps = false;
    if (!callRef.scope.getClosestHoistScope().isGlobal()) {
      Node callerFn = callRef.scope.getClosestHoistScope().getRootNode().getParent();

      // Don't allow any new vars into a scope that contains eval or one
      // that contains functions (excluding the function being inlined).
      if (this.referencesEval(callerFn)) {
        forbidTemps = true;
      } else if (!this.assumeMinimumCapture) {
        Node innerFn = this.innerFunctionOf(callerFn);
        boolean calleeIsOnlyInnerFn = innerFn.equals(NO_FUNCTIONS) || innerFn.equals(calleeFn);
        forbidTemps = !calleeIsOnlyInnerFn;
      }
    }

    if (calleeContainsVars && forbidTemps) {
      return false;
    }

    // If the caller contains functions or evals, verify we aren't adding any
    // additional VAR declarations because aliasing is needed.
    if (forbidTemps) {
      ImmutableMap<String, Node> args =
          functionArgumentInjector.getFunctionCallParameterMap(
              calleeFn, callRef.callNode, this.safeNameIdSupplier);
      boolean hasArgs = !args.isEmpty();
      if (hasArgs) {
        // Limit the inlining
        Set<String> allNamesToAlias = new LinkedHashSet<>(namesToAlias);
        functionArgumentInjector.maybeAddTempsForCallArguments(
            compiler, calleeFn, args, allNamesToAlias, compiler.getCodingConvention());
        if (!allNamesToAlias.isEmpty()) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Determines whether a function can be inlined at a particular call site. There are several
   * criteria that the function and reference must hold in order for the functions to be inlined: 1)
   * If a call's arguments have side effects, the corresponding argument in the function must only
   * be referenced once. For instance, this will not be inlined:
   *
   * <pre>
   *     function foo(a) { return a + a }
   *     x = foo(i++);
   * </pre>
   */
  private CanInlineResult canInlineReferenceDirectly(
      Reference ref, Node fnNode, Set<String> namesToAlias) {
    if (!isDirectCallNodeReplacementPossible(fnNode)) {
      return CanInlineResult.NO;
    }

    // CALL NODE: [ NAME, ARG1, ARG2, ... ]
    Node callNode = ref.callNode;
    Node cArg = callNode.getSecondChild();

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
        checkState(!NodeUtil.isFunctionObjectApply(callNode));
      }
    }

    ImmutableMap<String, Node> args =
        functionArgumentInjector.getFunctionCallParameterMap(
            fnNode, callNode, this.throwawayNameSupplier);
    boolean hasArgs = !args.isEmpty();
    if (hasArgs) {
      // Limit the inlining
      Set<String> allNamesToAlias = new LinkedHashSet<>(namesToAlias);
      functionArgumentInjector.maybeAddTempsForCallArguments(
          compiler, fnNode, args, allNamesToAlias, compiler.getCodingConvention());
      if (!allNamesToAlias.isEmpty()) {
        return CanInlineResult.NO;
      }
    }

    return CanInlineResult.YES;
  }

  /** Determine if inlining the function is likely to reduce the code size. */
  boolean inliningLowersCost(
      JSChunk fnChunk,
      Node fnNode,
      Collection<? extends Reference> refs,
      Set<String> namesToAlias,
      boolean isRemovable,
      boolean referencesThis) {
    int referenceCount = refs.size();
    if (referenceCount == 0) {
      return true;
    }

    int referencesUsingBlockInlining = 0;

    boolean checkModules = isRemovable && fnChunk != null;
    JSChunkGraph chunkGraph = compiler.getChunkGraph();

    for (Reference ref : refs) {
      if (ref.mode == InliningMode.BLOCK) {
        referencesUsingBlockInlining++;
      }

      // Check if any of the references cross the module boundaries.
      if (checkModules && ref.chunk != null) {
        if (ref.chunk != fnChunk && !chunkGraph.dependsOn(ref.chunk, fnChunk)) {
          // Calculate the cost as if the function were non-removable,
          // if it still lowers the cost inline it.
          isRemovable = false;
          checkModules = false; // no need to check additional modules.
        }
      }
    }

    int referencesUsingDirectInlining = referenceCount - referencesUsingBlockInlining;

    // Don't bother calculating the cost of function for simple functions where
    // possible.
    // However, when inlining a complex function, even a single reference may be
    // larger than the original function if there are many returns (resulting
    // in additional assignments) or many parameters that need to be aliased
    // so use the cost estimating.
    if (referenceCount == 1 && isRemovable && referencesUsingDirectInlining == 1) {
      return true;
    }

    int callCost = estimateCallCost(fnNode, referencesThis);
    int overallCallCost = callCost * referenceCount;

    int costDeltaDirect = inlineCostDelta(fnNode, namesToAlias, InliningMode.DIRECT);
    int costDeltaBlock = inlineCostDelta(fnNode, namesToAlias, InliningMode.BLOCK);

    return doesLowerCost(
        fnNode,
        overallCallCost,
        referencesUsingDirectInlining,
        costDeltaDirect,
        referencesUsingBlockInlining,
        costDeltaBlock,
        isRemovable);
  }

  /**
   * @return Whether inlining will lower cost.
   */
  private static boolean doesLowerCost(
      Node fnNode,
      int callCost,
      int directInlines,
      int costDeltaDirect,
      int blockInlines,
      int costDeltaBlock,
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
      return blockInlines <= 0 || costDeltaBlock <= 0;
    }

    int costDelta = (directInlines * -costDeltaDirect) + (blockInlines * -costDeltaBlock);
    int threshold = (callCost + costDelta) / fnInstanceCount;

    return InlineCostEstimator.getCost(fnNode, threshold + 1) <= threshold;
  }

  /**
   * Gets an estimate of the cost in characters of making the function call: the sum of the
   * identifiers and the separators.
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
      callCost += 5 + 5; // ".call" + "this,"
    }

    return callCost;
  }

  /**
   * @return The difference between the function definition cost and inline cost.
   */
  private static int inlineCostDelta(Node fnNode, Set<String> namesToAlias, InliningMode mode) {
    // The part of the function that is never inlined:
    //    "function xx(xx,xx){}" (15 + (param count * 3) -1;
    int paramCount = NodeUtil.getFunctionParameters(fnNode).getChildCount();
    int commaCount = (paramCount > 1) ? paramCount - 1 : 0;
    int costDeltaFunctionOverhead =
        15 + commaCount + (paramCount * InlineCostEstimator.ESTIMATED_IDENTIFIER_COST);

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
      final int perReturnOverhead = 2; // "return" --> "break X"
      final int perReturnResultOverhead = 3; // "XX="
      final int perAliasOverhead = 3; // "XX="

      // TODO(johnlenz): Counting the number of returns is relatively expensive.
      //   This information should be determined during the traversal and cached.
      int returnCount =
          NodeUtil.getNodeTypeReferenceCount(
              block, Token.RETURN, new NodeUtil.MatchShallowStatement());
      int resultCount = (returnCount > 0) ? returnCount - 1 : 0;
      int baseOverhead = (returnCount > 0) ? inlineBlockOverhead : 0;

      int overhead =
          baseOverhead
              + returnCount * perReturnOverhead
              + resultCount * perReturnResultOverhead
              + aliasCount * perAliasOverhead;

      return (overhead - costDeltaFunctionOverhead);
    }
  }

  /** Store the names of known constants to be used when classifying call-sites in expressions. */
  public void setKnownConstantFunctions(ImmutableSet<String> knownConstantFunctions) {
    // This is only expected to be set once. The same set should be used
    // when evaluating call-sites and inlining calls.
    checkState(this.knownConstantFunctions.isEmpty());
    this.knownConstantFunctions = knownConstantFunctions;
  }
}
