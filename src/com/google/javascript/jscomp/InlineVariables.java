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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.LinkedHashMultimap;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.ReferenceCollector.Behavior;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.jspecify.nullness.Nullable;

/**
 * Using the infrastructure provided by {@link ReferenceCollector}, identify variables that are used
 * in a way that is safe to move, and then inline them.
 *
 * <p>This pass has two "modes." One mode only inlines variables declared as constants, for legacy
 * compiler clients. The second mode inlines any variable that we can provably inline. Note that the
 * second mode is a superset of the first mode. We only support the first mode for
 * backwards-compatibility with compiler clients that don't want --inline_variables.
 *
 * <p>The basic structure of this class is as follows.
 *
 * <ol>
 *   <li>{@link ReferenceCollector#process} is invoked on the AST with an instance of {@link
 *       InliningBehavior}
 *   <li>{@link InliningBehavior#afterExitScope} gets invoked on each scope in DFS order
 *   <li>It iterates through the variables defined in that scope:
 *       <ol>
 *         <li>For each variable a {@link VarExpert} is created. This object is responsible for
 *             determining:
 *             <ul>
 *               <li>Can it be inlined?
 *               <li>If so, how should the inlining be done?
 *               <li>If not, is it safe to inline aliases of the variable? (e.g. {@code const b =
 *                   doSomething();} isn't safe to inline due to side-effects, but it should be OK
 *                   to inline {@code const aliasB = b;} in many cases.
 *             </ul>
 *         <li>The {@link VarExpert} creates an {@link InlineVarAnalysis} object containing its
 *             decisions and possibly a method to invoke to do the inlining.
 *         <li>The {@link InlineVarAnalysis} may indicate that a variable is an alias of another
 *             variable. If so, {@link InliningBehavior} will wait until the aliased variable has
 *             been analyzed, then pass that analysis back to the {@link VarExpert} for the aliasing
 *             variable, so it can complete its decision.
 *       </ol>
 * </ol>
 */
class InlineVariables implements CompilerPass {

  private final AbstractCompiler compiler;

  enum Mode {
    // Only inline things explicitly marked as constant.
    CONSTANTS_ONLY(Var::isDeclaredOrInferredConst),
    // Locals only
    LOCALS_ONLY(Var::isLocal),
    ALL(Predicates.alwaysTrue());

    @SuppressWarnings("ImmutableEnumChecker")
    private final Predicate<Var> varPredicate;

    private Mode(Predicate<Var> varPredicate) {
      this.varPredicate = varPredicate;
    }
  }

  private final Mode mode;

  InlineVariables(AbstractCompiler compiler, Mode mode) {
    this.compiler = compiler;
    this.mode = mode;
  }

  @Override
  public void process(Node externs, Node root) {
    ReferenceCollector callback =
        new ReferenceCollector(
            compiler,
            new InliningBehavior(),
            new SyntacticScopeCreator(compiler),
            mode.varPredicate);
    callback.process(externs, root);
  }

  /** Responsible for analyzing a variable to determine if it can be inlined. */
  private abstract static class VarExpert {

    /** Called to conduct the initial analysis */
    abstract InlineVarAnalysis analyze();

    /**
     * Called for a Var that is an alias after the original variable is handled.
     *
     * @param aliasedVar The variable that was aliased.
     * @param aliasedVarAnalysis The analysis results for the aliased variable
     */
    InlineVarAnalysis reanalyzeAfterAliasedVar(
        Var aliasedVar, InlineVarAnalysis aliasedVarAnalysis) {
      throw new UnsupportedOperationException("not waiting for an aliased variable");
    }
  }

  // Canonical `VarExpert` objects for common cases.
  private static final VarExpert NO_INLINE_SELF_OR_ALIASES_EXPERT =
      new VarExpert() {
        @Override
        public InlineVarAnalysis analyze() {
          return NO_INLINE_SELF_OR_ALIASES_ANALYSIS;
        }
      };
  private static final VarExpert NO_INLINE_SELF_ALIASES_OK_EXPERT =
      new VarExpert() {
        @Override
        public InlineVarAnalysis analyze() {
          return NO_INLINE_SELF_ALIASES_OK_ANALYSIS;
        }
      };

  /**
   * The result of analyzing a variable to see if it can be inlined.
   *
   * <p>Non-abstract classes should be created by extending this class and overriding the methods
   * that should return `true` or perform an operation.
   */
  private abstract static class InlineVarAnalysis {

    /**
     * Should we inline this variable?
     *
     * <p>Mutually exclusive with `shouldWaitForAliasedVar()`.
     */
    public boolean shouldInline() {
      return false;
    }

    /**
     * True if this variable is an alias.
     *
     * <p>The caller should wait for the aliased variable to be handled, then call the expert's
     * `reanalyzeAfterAliasedVar()` method.
     *
     * <p>Mutually exclusive with `shouldInline()`
     */
    public boolean shouldWaitForAliasedVar() {
      return false;
    }

    /**
     * Gets the aliased variable, if this is an alias.
     *
     * @return The `Var` for which this one is an alias
     * @throws `UnsupportedOperationException` if `shouldWaitForAliasedVar()` is `false`.
     */
    public Var getAliasedVar() {
      throw new UnsupportedOperationException("no aliased Var");
    }

    /** True if it is safe for aliases of this variable to inline this variable's name. */
    public boolean isSafeToInlineAliases() {
      return false;
    }

    /** Performs the inline operation. */
    public void performInline() {
      throw new UnsupportedOperationException("cannot inline");
    }
  }

  // Canonical `InlineVarAnalysis` values.
  // We'll use these instead of creating new objects for each analysis.
  private static final InlineVarAnalysis NO_INLINE_SELF_OR_ALIASES_ANALYSIS =
      new InlineVarAnalysis() {};
  private static final InlineVarAnalysis NO_INLINE_SELF_ALIASES_OK_ANALYSIS =
      new InlineVarAnalysis() {
        @Override
        public boolean isSafeToInlineAliases() {
          return true;
        }
      };

  /**
   * Builds up information about nodes in each scope. When exiting the scope, inspects all variables
   * in that scope, and inlines any that we can.
   */
  private class InliningBehavior implements Behavior {

    /**
     * Records the analyses of variables that have already been handled in the current scope.
     *
     * <p>This is necessary in order for aliases of those variables to determine whether they may be
     * inlined.
     */
    final HashMap<Var, InlineVarAnalysis> currentScopeHandledVarAnalysesMap = new HashMap<>();

    /**
     * Records alias variables that are waiting for the original variables to be handled.
     *
     * <p>The value is an object that knows what to do when the original variable is handled.
     */
    final LinkedHashMultimap<Var, AliasInlineRetryHandler> varToAliasRetryHandlersMap =
        LinkedHashMultimap.create();

    @Override
    public void afterExitScope(NodeTraversal t, ReferenceMap referenceMap) {
      doInlinesForScope(t, referenceMap);
    }

    /**
     * For all variables in this scope, see if they are only used once. If it looks safe to do so,
     * inline them.
     */
    private void doInlinesForScope(NodeTraversal t, ReferenceMap referenceMap) {
      // Any variables we completed in an earlier scope are now out of scope,
      // so we can clear this map.
      currentScopeHandledVarAnalysesMap.clear();
      boolean mayBeAParameterModifiedViaArguments =
          varsInThisScopeMayBeModifiedUsingArguments(t.getScope(), referenceMap);
      for (Var v : t.getScope().getVarIterable()) {
        ReferenceCollection referenceInfo = referenceMap.getReferences(v);
        VarExpert expert = createVarExpert(v, referenceInfo, mayBeAParameterModifiedViaArguments);
        final InlineVarAnalysis analysis = expert.analyze();
        if (analysis.shouldWaitForAliasedVar()) {
          // We need to wait for the aliased variable to be handled.
          final AliasInlineRetryHandler retryHandler = new AliasInlineRetryHandler(v, expert);
          final Var aliasedVar = analysis.getAliasedVar();
          final InlineVarAnalysis aliasedVarAnalysis =
              currentScopeHandledVarAnalysesMap.get(aliasedVar);
          if (aliasedVarAnalysis != null) {
            // We've actually already completed the aliased Var
            retryHandler.handleAliasedVarCompletion(aliasedVar, aliasedVarAnalysis);
          } else {
            // wait for completion of the aliased Var
            varToAliasRetryHandlersMap.put(aliasedVar, retryHandler);
          }
        } else {
          if (analysis.shouldInline()) {
            analysis.performInline();
          }
          // Record the results for aliases we may find later
          currentScopeHandledVarAnalysesMap.put(v, analysis);
          // Retry any aliases we saw before handling this Var
          retryAliases(v, analysis);
        }
      }
    }

    /**
     * Returns true for function scopes (the whole function, not the body), if the function uses
     * `arguments` in some way other than a few that are known not to change the values of parameter
     * variables.
     *
     * <p>TODO(bradfordcsmith): In strict mode `arguments` is a copy of the parameters, so modifying
     * it cannot change the parameters. Sloppy mode is now so rare, that we should probably just
     * ignore the possibility of `arguments` being used to modify a parameter value. We ignore loose
     * mode issues or simply don't support them ("with" for instance).
     */
    private boolean varsInThisScopeMayBeModifiedUsingArguments(
        Scope scope, ReferenceMap referenceMap) {
      if (scope.isFunctionScope() && !scope.getRootNode().isArrowFunction()) {
        Var arguments = scope.getArgumentsVar();
        ReferenceCollection refs = referenceMap.getReferences(arguments);
        if (refs != null && !refs.references.isEmpty()) {
          for (Reference ref : refs.references) {
            if (!isSafeUseOfArguments(ref.getNode())) {
              return true;
            }
          }
        }
      }
      return false;
    }

    private void retryAliases(Var aliasedVar, InlineVarAnalysis aliasedVarAnalysis) {
      for (AliasInlineRetryHandler aliasInlineRetryHandler :
          varToAliasRetryHandlersMap.removeAll(aliasedVar)) {
        aliasInlineRetryHandler.handleAliasedVarCompletion(aliasedVar, aliasedVarAnalysis);
      }
    }

    /** Retries inlining an alias variable after the original variable has been dealt with. */
    private class AliasInlineRetryHandler {
      private final Var v;
      private final VarExpert expert;

      AliasInlineRetryHandler(Var v, VarExpert expert) {
        this.v = v;
        this.expert = expert;
      }

      void handleAliasedVarCompletion(Var aliasedVar, InlineVarAnalysis aliasedVarAnalyis) {
        InlineVarAnalysis newAnalysis =
            expert.reanalyzeAfterAliasedVar(aliasedVar, aliasedVarAnalyis);
        checkState(
            !newAnalysis.shouldWaitForAliasedVar(), "expert for %s asked to wait a second time", v);
        if (newAnalysis.shouldInline()) {
          newAnalysis.performInline();
        }
        currentScopeHandledVarAnalysesMap.put(v, newAnalysis);
        retryAliases(v, newAnalysis);
      }
    }

    /**
     * Knows how to analyze a variable to determine whether it should be inlined, how to do the
     * inlining, and whether it's OK to inline aliases of the variable.
     */
    private class StandardVarExpert extends VarExpert {
      private final Var v;
      private final ReferenceCollection referenceInfo;
      private final Reference declaration;
      private final boolean isDeclaredOrInferredConstant;
      private final boolean mayBeAParameterModifiedViaArguments;

      StandardVarExpert(VarExpertInitData initData) {
        this.v = initData.v;
        this.referenceInfo = initData.referenceInfo;
        this.declaration = initData.referenceInfo.references.get(0);
        this.isDeclaredOrInferredConstant = initData.isDeclaredOrInferredConstant;
        this.mayBeAParameterModifiedViaArguments = initData.mayBeAParameterModifiedViaArguments;
      }

      private final InitiallyUnknown<Boolean> isNeverAssigned = new InitiallyUnknown<>();

      private boolean isNeverAssigned() {
        if (isNeverAssigned.isKnown()) {
          return isNeverAssigned.getKnownValue();
        } else if (initializationReference.isKnownNotNull()
            || isAssignedOnceInLifetime.isKnownToBe(true)) {
          return isNeverAssigned.setKnownValueOnce(false);
        } else {
          return isNeverAssigned.setKnownValueOnce(referenceInfo.isNeverAssigned());
        }
      }

      private final InitiallyUnknown<Boolean> isWellDefined = new InitiallyUnknown<>();

      private boolean isWellDefined() {
        if (isWellDefined.isKnown()) {
          return isWellDefined.getKnownValue();
        } else {
          return isWellDefined.setKnownValueOnce(referenceInfo.isWellDefined());
        }
      }

      private final InitiallyUnknown<Boolean> isAssignedOnceInLifetime = new InitiallyUnknown<>();

      private boolean isAssignedOnceInLifetime() {
        if (isAssignedOnceInLifetime.isKnown()) {
          return isAssignedOnceInLifetime.getKnownValue();
        } else {
          return isAssignedOnceInLifetime.setKnownValueOnce(
              referenceInfo.isAssignedOnceInLifetime());
        }
      }

      private final InitiallyUnknown<Boolean> isWellDefinedAssignedOnce = new InitiallyUnknown<>();

      /**
       * A more efficient way to ask if the variable is both well defined and assigned exactly once
       * in its lifetime.
       */
      private boolean isWellDefinedAssignedOnce() {
        if (isWellDefinedAssignedOnce.isKnown()) {
          return isWellDefinedAssignedOnce.getKnownValue();
        } else {
          // Check first to see if either is already known to be false before calculating anything.
          return isWellDefinedAssignedOnce.setKnownValueOnce(
              !isWellDefined.isKnownToBe(false)
                  && !isAssignedOnceInLifetime.isKnownToBe(false)
                  // isWellDefined is generally less expensive to calculate
                  && isWellDefined()
                  && isAssignedOnceInLifetime());
        }
      }

      private final InitiallyUnknown<Reference> initializationReference = new InitiallyUnknown<>();

      private Reference getInitialization() {
        if (initializationReference.isKnown()) {
          return initializationReference.getKnownValue();
        } else {
          final Reference initRef =
              isDeclaredOrInferredConstant
                  ? referenceInfo.getInitializingReferenceForConstants()
                  : referenceInfo.getInitializingReference();
          return initializationReference.setKnownValueOnce(initRef);
        }
      }

      private boolean hasValidDeclaration() {
        return isValidDeclaration(declaration);
      }

      private boolean hasValidInitialization() {
        return isValidInitialization(getInitialization());
      }

      private final InitiallyUnknown<Boolean> allReferencesAreValid = new InitiallyUnknown<>();

      private boolean allReferencesAreValid() {
        if (allReferencesAreValid.isKnown()) {
          return allReferencesAreValid.getKnownValue();
        } else {
          final boolean hasValidDeclaration = hasValidDeclaration();
          final boolean hasValidInitialization = hasValidInitialization();
          final boolean neverAssigned = isNeverAssigned();
          if (hasValidDeclaration && (hasValidInitialization || neverAssigned)) {
            Reference initialization = getInitialization();
            for (Reference ref : referenceInfo.references) {
              if (ref != declaration && ref != initialization && !isValidReference(ref)) {
                return allReferencesAreValid.setKnownValueOnce(false);
              }
            }
            return allReferencesAreValid.setKnownValueOnce(true);
          }
          return allReferencesAreValid.setKnownValueOnce(false);
        }
      }

      @Override
      public InlineVarAnalysis analyze() {
        if (hasNoInlineAnnotation(v)) {
          return getNegativeInlineVarAnalysis();
        }
        final Reference initialization = getInitialization();
        final Node initValue = initialization == null ? null : initialization.getAssignedValue();
        final InitialValueAnalysis initialValueAnalysis = new InitialValueAnalysis(initValue);

        if (isDeclaredOrInferredConstant
            && initialValueAnalysis.isImmutableValue()
            // isAssignedOnceInLifetime() is much more expensive than the other checks
            && isAssignedOnceInLifetime()) {
          return createInlineWellDefinedVarAnalysis(initValue);
        }

        if (mode == Mode.CONSTANTS_ONLY) {
          // If we're in constants-only mode, don't run more aggressive
          // inlining heuristics. See InlineConstantsTest.
          return getNegativeInlineVarAnalysis();
        }

        if (initialValueAnalysis.isAlias()) {
          return new VarIsAliasAnalysis(initialValueAnalysis.getAliasedVar());
        }

        return analyzeWithInitialValue(initialization, initValue, initialValueAnalysis);
      }

      private InlineVarAnalysis analyzeWithInitialValue(
          Reference initialization, Node initValue, InitialValueAnalysis initialValueAnalysis) {
        final int refCount = referenceInfo.references.size();

        // TODO(bradfordcsmith): We could remove the `refCount > 1` here, but:
        //  1. That will require some additional logic to handle stuff like named function
        //     expressions and avoiding the removal of side-effects.
        //  2. RemoveUnusedCode will remove those cases anyway.
        //  3. The unit tests will have to be more verbose to make sure stuff we don't want to be
        //  removed is used.
        if (refCount > 1 && allReferencesAreValid()) {
          if (referenceInfo.isNeverAssigned()) {
            return new PositiveInlineVarAnalysis(
                () -> {
                  // Create a new `undefined` node to inline for a variable that is never
                  // initialized.
                  Node srcLocation = declaration.getNode();
                  final Node undefinedNode = NodeUtil.newUndefinedNode(srcLocation);
                  inlineWellDefinedVariable(v, undefinedNode, referenceInfo.references);
                });
          }
          if (isWellDefined()
              && (initialValueAnalysis.isImmutableValue()
                  || (initValue.isThis() && !referenceInfo.isEscaped()))) {
            // if the variable is referenced more than once, we can only
            // inline it if it's immutable and never defined before referenced.
            return createInlineWellDefinedVarAnalysis(initValue);
          }

          final int firstReadRefIndex = (declaration == initialization ? 1 : 2);
          final int numReadRefs = refCount - firstReadRefIndex;
          if (numReadRefs == 0) {
            // The only reference is the initialization.
            // Remove the assignment and the variable declaration.
            return createInlineWellDefinedVarAnalysis(initValue);
          }

          if (numReadRefs == 1) {
            // The variable is likely only read once, so we can try some more complex inlining
            // heuristics.
            final Reference singleReadReference = referenceInfo.references.get(firstReadRefIndex);
            if (canInline(declaration, initialization, singleReadReference, initValue)) {
              // A custom inline method is needed for this case.
              return new PositiveInlineVarAnalysis(
                  () -> inline(declaration, initialization, singleReadReference));
            }
          }
        }

        return getNegativeInlineVarAnalysis();
      }

      private InlineVarAnalysis getNegativeInlineVarAnalysis() {

        // If we already know whether it is safe to inline aliases of this variable,
        // use canonical analysis values to save on memory space.
        if (mayBeAParameterModifiedViaArguments
            || mode == Mode.CONSTANTS_ONLY
            || isWellDefinedAssignedOnce.isKnownToBe(false)) {
          return NO_INLINE_SELF_OR_ALIASES_ANALYSIS;
        }
        if (isWellDefinedAssignedOnce.isKnownToBe(true)) {
          return NO_INLINE_SELF_ALIASES_OK_ANALYSIS;
        }

        // Delay calculating safety until we're actually asked.
        return new InlineVarAnalysis() {
          @Override
          public boolean isSafeToInlineAliases() {
            return isWellDefinedAssignedOnce();
          }
        };
      }

      private PositiveInlineVarAnalysis createInlineWellDefinedVarAnalysis(Node initValue) {
        return new PositiveInlineVarAnalysis(
            () -> inlineWellDefinedVariable(v, initValue, referenceInfo.references));
      }

      @Override
      public InlineVarAnalysis reanalyzeAfterAliasedVar(
          Var aliasedVar, InlineVarAnalysis aliasedVarAnalysis) {
        final Reference initialization = getInitialization();
        final Node initValue = initialization == null ? null : initialization.getAssignedValue();
        final InitialValueAnalysis initialValueAnalysis = new InitialValueAnalysis(initValue);

        if (isDeclaredOrInferredConstant
            && initialValueAnalysis.isImmutableValue()
            // isAssignedOnceInLifetime() is much more expensive than the other checks
            && isAssignedOnceInLifetime()) {
          return createInlineWellDefinedVarAnalysis(initValue);
        }

        if (initialValueAnalysis.isAlias()
            && aliasedVarAnalysis.isSafeToInlineAliases()
            && isWellDefinedAssignedOnce()) {
          // The variable we aliased couldn't be inlined itself, or it was an alias for another
          // variable that got inlined in its place.
          // However, it is safe to inline the name assigned to this variable now.
          return createInlineWellDefinedVarAnalysis(initValue);
        }
        return analyzeWithInitialValue(initialization, initValue, initialValueAnalysis);
      }

      /** Information about a value used to initialize a variable. */
      private class InitialValueAnalysis {
        private final Node value;

        InitialValueAnalysis(Node value) {
          this.value = value;
        }

        private final InitiallyUnknown<Boolean> isImmutableValue = new InitiallyUnknown<>();

        boolean isImmutableValue() {
          if (isImmutableValue.isKnown()) {
            return isImmutableValue.getKnownValue();
          } else {
            return isImmutableValue.setKnownValueOnce(
                value != null && NodeUtil.isImmutableValue(value));
          }
        }

        private final InitiallyUnknown<Var> aliasedVar = new InitiallyUnknown<>();

        boolean isAlias() {
          return getAliasedVar() != null;
        }

        Var getAliasedVar() {
          if (aliasedVar.isKnown()) {
            return aliasedVar.getKnownValue();
          } else {
            if (value != null && value.isName()) {
              String aliasedName = value.getString();
              // Never consider this variable to be an alias of itself.
              return aliasedVar.setKnownValueOnce(
                  aliasedName.equals(v.getName()) ? null : v.getScope().getVar(aliasedName));
            }
            return aliasedVar.setKnownValueOnce(null);
          }
        }
      }
    }

    /** Indicates that the analyzed variable may be inlined. */
    private class PositiveInlineVarAnalysis extends InlineVarAnalysis {
      private final Runnable inliner;

      private PositiveInlineVarAnalysis(Runnable inliner) {
        this.inliner = inliner;
      }

      @Override
      public boolean shouldInline() {
        return true;
      }

      @Override
      public void performInline() {
        inliner.run();
      }

      @Override
      public boolean isSafeToInlineAliases() {
        // If we've inlined this variable, then any aliases of it should definitely try to
        // inline themselves with the new value that replaced this variable.
        // If for some reason the logic that requested this analysis decided not to actually
        // do the inlining, it's still safe to inline the name of this variable.
        // If it weren't, we wouldn't have said it was OK to inline its value.
        return true;
      }
    }

    /**
     * Indicates that the analyzed variable is an alias and the decision about whether to inline it
     * must wait until inlining has been done (or not) for the original value it aliases.
     */
    private class VarIsAliasAnalysis extends InlineVarAnalysis {
      private final Var aliasedVar;

      private VarIsAliasAnalysis(Var aliasedVar) {
        this.aliasedVar = aliasedVar;
      }

      @Override
      public boolean shouldWaitForAliasedVar() {
        return true;
      }

      @Override
      public Var getAliasedVar() {
        return aliasedVar;
      }

      @Override
      public boolean isSafeToInlineAliases() {
        throw new UnsupportedOperationException("analysis is incomplete");
      }
    }

    /** Creates a VarExpert object appropriate for the given variable. */
    private VarExpert createVarExpert(
        Var v, ReferenceCollection referenceInfo, boolean mayBeAParameterModifiedViaArguments) {
      if (referenceInfo == null) {
        // If we couldn't collect any reference info, don't try to inline and assume it's unsafe
        // to inline aliases of this variable, too.
        return NO_INLINE_SELF_OR_ALIASES_EXPERT;
      }

      final boolean isDeclaredOrInferredConstant = v.isDeclaredOrInferredConst();
      if (!isDeclaredOrInferredConstant && mode == Mode.CONSTANTS_ONLY) {
        // If we're only inlining constants, then we shouldn't inline an alias.
        return NO_INLINE_SELF_OR_ALIASES_EXPERT;
      }

      if (v.isExtern()) {
        // TODO(bradfordcsmith): Extern variables are generally unsafe to inline.
        return NO_INLINE_SELF_OR_ALIASES_EXPERT;
      }

      if (compiler.getCodingConvention().isExported(v.getName(), /* local= */ v.isLocal())) {
        // If the variable is exported, it might be assigned a new value by code we cannot see,
        // so aliases to it are creating snapshots of its state.
        // We cannot inline this variable or its aliases.
        return NO_INLINE_SELF_OR_ALIASES_EXPERT;
      }

      if (compiler.getCodingConvention().isPropertyRenameFunction(v.getNameNode())) {
        // It's not terribly likely that anything creates an alias of our special property rename
        // function, but its value never changes, so it should be safe to inline aliases to it.
        return NO_INLINE_SELF_ALIASES_OK_EXPERT;
      }

      final VarExpertInitData initData = new VarExpertInitData();
      initData.v = v;
      initData.referenceInfo = referenceInfo;
      initData.isDeclaredOrInferredConstant = isDeclaredOrInferredConstant;
      initData.mayBeAParameterModifiedViaArguments = mayBeAParameterModifiedViaArguments;

      return new StandardVarExpert(initData);
    }

    /** Used to initialize fields in a `StandardVarExpert` object. */
    private class VarExpertInitData {

      Var v;
      ReferenceCollection referenceInfo;
      boolean isDeclaredOrInferredConstant;
      boolean mayBeAParameterModifiedViaArguments;
    }

    /**
     * True if `argumentsNode` is a use of `arguments` we know won't modify any parameter values.
     *
     * <p>In sloppy mode it is possible to change the value of a function parameter by assigning to
     * the corresponding entry in {@code arguments}.
     *
     * <p>This method checks for just a few common uses that are known to be safe. However, we may
     * soon remove this check entirely because unsafe uses aren't worth worrying about. See the
     * comment on {@link #varsInThisScopeMayBeModifiedUsingArguments}.
     */
    boolean isSafeUseOfArguments(Node argumentsNode) {
      checkArgument(argumentsNode.matchesName("arguments"));
      // `arguments[i]` that is only read, not assigned
      // or `fn.apply(thisArg, arguments)`
      return isTargetOfPropertyRead(argumentsNode)
          || isSecondArgumentToDotApplyMethod(argumentsNode);
    }

    /**
     * True if `n` is the object in a property access expression (`obj[prop]` or `obj.prop` or an
     * optional chain version of one of those) and the property is only being read, not modified.
     */
    boolean isTargetOfPropertyRead(Node n) {
      Node getNode = n.getParent();
      return n.isFirstChildOf(getNode)
          && NodeUtil.isNormalOrOptChainGet(getNode)
          && !NodeUtil.isLValue(getNode);
    }

    /** True if `n` is being used in a call like this: `fn.apply(thisArg, n)`. */
    boolean isSecondArgumentToDotApplyMethod(Node n) {
      Node callNode = n.getParent();
      if (NodeUtil.isNormalOrOptChainCall(callNode)) {
        Node calleeNode = callNode.getFirstChild();
        if (NodeUtil.isNormalOrOptChainGetProp(calleeNode)) {
          if (calleeNode.getString().equals("apply")) {
            Node thisArgNode = calleeNode.getNext();
            if (thisArgNode != null) {
              return thisArgNode.getNext() == n;
            }
          }
        }
      }
      return false;
    }

    /** Do the actual work of inlining a single declaration into a single reference. */
    private void inline(Reference decl, Reference init, Reference ref) {
      Node value = init.getAssignedValue();
      checkState(value != null);
      // Check for function declarations before the value is moved in the AST.
      boolean isFunctionDeclaration = NodeUtil.isFunctionDeclaration(value);
      if (isFunctionDeclaration) {
        // In addition to changing the containing scope, inlining function declarations also changes
        // the function name scope from the containing scope to the inner scope.
        compiler.reportChangeToChangeScope(value);
        compiler.reportChangeToEnclosingScope(value.getParent());
      }
      inlineValue(ref.getNode(), value.detach());
      if (decl != init) {
        Node expressRoot = init.getGrandparent();
        checkState(expressRoot.isExprResult());
        NodeUtil.removeChild(expressRoot.getParent(), expressRoot);
      }
      // Function declarations have already been removed.
      if (!isFunctionDeclaration) {
        removeDeclaration(decl);
      }
    }

    /** Inline an immutable variable into all of its references. */
    private void inlineWellDefinedVariable(Var v, Node value, List<Reference> refSet) {
      for (Reference r : refSet) {
        if (r.getNode() == v.getNameNode()) {
          removeDeclaration(r);
        } else if (r.isSimpleAssignmentToName()) {
          /**
           * This is the initialization.
           *
           * <p>Replace the entire assignment with just the value, and use the original value node
           * in case it contains references to variables that still require inlining.
           */
          inlineValue(r.getParent(), value.detach());
        } else {
          Node clonedValue = value.cloneTree();
          NodeUtil.markNewScopesChanged(clonedValue, compiler);
          inlineValue(r.getNode(), clonedValue);
        }
      }
    }

    /** Remove the given VAR declaration. */
    private void removeDeclaration(Reference decl) {
      Node varNode = decl.getParent();
      checkState(NodeUtil.isNameDeclaration(varNode), varNode);
      Node grandparent = decl.getGrandparent();

      compiler.reportChangeToEnclosingScope(decl.getNode());
      decl.getNode().detach();
      // Remove var node if empty
      if (!varNode.hasChildren()) {
        NodeUtil.removeChild(grandparent, varNode);
      }
    }

    private void inlineValue(Node toRemove, Node toInsert) {
      compiler.reportChangeToEnclosingScope(toRemove);

      // Help type-based optimizations by propagating more specific types from type assertions
      if (toRemove.getColor() != null && toRemove.isColorFromTypeCast()) {
        toInsert.setColor(toRemove.getColor());
        toInsert.setColorFromTypeCast();
      }
      toRemove.replaceWith(toInsert);
      NodeUtil.markFunctionsDeleted(toRemove, compiler);
    }

    /**
     * @return true if the provided reference and declaration can be safely inlined according to our
     *     criteria
     */
    private boolean canInline(
        Reference declaration, Reference initialization, Reference reference, Node initValue) {
      // If the value is read more than once, skip it.
      // VAR declarations and EXPR_RESULT don't need the value, but other
      // ASSIGN expressions parents do.
      if (declaration != initialization && !initialization.getGrandparent().isExprResult()) {
        return false;
      }

      // Be very conservative and do not cross control structures or scope boundaries
      if (declaration.getBasicBlock() != initialization.getBasicBlock()
          || declaration.getBasicBlock() != reference.getBasicBlock()) {
        return false;
      }

      // Do not inline into a call node. This would change
      // the context in which it was being called. For example,
      //   var a = b.c;
      //   a();
      // should not be inlined, because it calls a in the context of b
      // rather than the context of the window.
      //   var a = b.c;
      //   f(a)
      // is OK.
      checkState(initValue != null);
      if (initValue.isGetProp()
          && reference.getParent().isCall()
          && reference.getParent().getFirstChild() == reference.getNode()) {
        return false;
      }

      if (initValue.isFunction()) {
        Node callNode = reference.getParent();
        if (reference.getParent().isCall()) {
          CodingConvention convention = compiler.getCodingConvention();
          // Bug 2388531: Don't inline subclass definitions into class defining
          // calls as this confused class removing logic.
          SubclassRelationship relationship = convention.getClassesDefinedByCall(callNode);
          if (relationship != null) {
            return false;
          }

          // issue 668: Don't inline singleton getter methods
          // calls as this confused class removing logic.
          if (convention.getSingletonGetterClassName(callNode) != null) {
            return false;
          }
        }
      }

      if (initialization.getScope() != declaration.getScope()
          || !initialization.getScope().contains(reference.getScope())) {
        return false;
      }

      return canMoveAggressively(initValue) || canMoveModerately(initialization, reference);
    }

    /** If the value is a literal, we can cross more boundaries to inline it. */
    private boolean canMoveAggressively(Node value) {
      // Function expressions and other mutable objects can move within
      // the same basic block.
      return NodeUtil.isLiteralValue(value, true) || value.isFunction();
    }

    /**
     * If the value of a variable is not constant, then it may read or modify state. Therefore it
     * cannot be moved past anything else that may modify the value being read or read values that
     * are modified.
     */
    private boolean canMoveModerately(Reference initialization, Reference reference) {
      // Check if declaration can be inlined without passing
      // any side-effect causing nodes.
      Iterator<Node> it;
      if (NodeUtil.isNameDeclaration(initialization.getParent())) {
        it =
            NodeIterators.LocalVarMotion.forVar(
                compiler,
                initialization.getNode(), // NAME
                initialization.getParent(), // VAR/LET/CONST
                initialization.getGrandparent()); // VAR/LET/CONST container
      } else if (initialization.getParent().isAssign()) {
        checkState(initialization.getGrandparent().isExprResult());
        it =
            NodeIterators.LocalVarMotion.forAssign(
                compiler,
                initialization.getNode(), // NAME
                initialization.getParent(), // ASSIGN
                initialization.getGrandparent(), // EXPR_RESULT
                initialization.getGrandparent().getParent()); // EXPR container
      } else {
        throw new IllegalStateException(
            "Unexpected initialization parent\n" + initialization.getParent().toStringTree());
      }
      Node targetName = reference.getNode();
      while (it.hasNext()) {
        Node curNode = it.next();
        if (curNode == targetName) {
          return true;
        }
      }

      return false;
    }

    /**
     * @return true if the reference is a normal VAR or FUNCTION declaration.
     */
    private boolean isValidDeclaration(Reference declaration) {
      return (NodeUtil.isNameDeclaration(declaration.getParent())
              && !NodeUtil.isLoopStructure(declaration.getGrandparent()))
          || NodeUtil.isFunctionDeclaration(declaration.getParent());
    }

    /**
     * @return Whether there is a initial value.
     */
    private boolean isValidInitialization(Reference initialization) {
      if (initialization == null) {
        return false;
      } else if (initialization.isDeclaration()) {
        // The reference is a FUNCTION declaration or normal VAR declaration
        // with a value.
        if (!NodeUtil.isFunctionDeclaration(initialization.getParent())
            && !initialization.getNode().hasChildren()) {
          return false;
        }
      } else {
        Node parent = initialization.getParent();
        checkState(parent.isAssign() && parent.getFirstChild() == initialization.getNode());
      }

      return true;
    }

    /**
     * @return true if the reference is a candidate for inlining
     */
    private boolean isValidReference(Reference reference) {
      return !reference.isDeclaration() && !reference.isLvalue();
    }
  }

  private static boolean hasNoInlineAnnotation(Var var) {
    JSDocInfo jsDocInfo = var.getJSDocInfo();
    return jsDocInfo != null && jsDocInfo.isNoInline();
  }

  private static class InitiallyUnknown<T> {
    protected boolean isKnown = false;
    protected @Nullable T value = null;

    boolean isKnown() {
      return isKnown;
    }

    boolean isKnownNotNull() {
      return isKnownNotToBe(null);
    }

    boolean isKnownToBe(T other) {
      return isKnown && value == other;
    }

    boolean isKnownNotToBe(T other) {
      return isKnown && value != other;
    }

    T setKnownValueOnce(T value) {
      checkState(!isKnown, "already known");
      this.value = value;
      isKnown = true;
      return value;
    }

    T getKnownValue() {
      checkState(isKnown, "not yet known");
      return value;
    }
  }
}
