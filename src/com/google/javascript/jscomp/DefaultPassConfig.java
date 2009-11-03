/*
 * Copyright 2009 Google Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Pass factories and meta-data for native JSCompiler passes.
 *
*
 */
// TODO(nicksantos): This needs state for a variety of reasons. Some of it
// is to satisfy the existing API. Some of it is because passes really do
// need to share state in non-trivial ways. This should be audited and
// cleaned up.
public class DefaultPassConfig extends PassConfig {

  /* For the --mark-as-compiled pass */
  private static final String COMPILED_CONSTANT_NAME = "COMPILED";

  /* Constant name for Closure's locale */
  private static final String CLOSURE_LOCALE_CONSTANT_NAME = "goog.LOCALE";

  // Compiler errors when invalid combinations of passes are run.
  static final DiagnosticType TIGHTEN_TYPES_WITHOUT_TYPE_CHECK =
      DiagnosticType.error("JSC_TIGHTEN_TYPES_WITHOUT_TYPE_CHECK",
          "TightenTypes requires type checking. Please use --check_types.");

  static final DiagnosticType CANNOT_USE_PROTOTYPE_AND_VAR =
      DiagnosticType.error("JSC_CANNOT_USE_PROTOTYPE_AND_VAR",
          "Rename prototypes and inline variables cannot be used together");

  // Miscellaneous errors.
  static final DiagnosticType REPORT_PATH_IO_ERROR =
      DiagnosticType.error("JSC_REPORT_PATH_IO_ERROR",
          "Error writing compiler report to {0}");

  /**
   * A global namespace to share across checking passes.
   * TODO(nicksantos): This is a hack until I can get the namespace into
   * the symbol table.
   */
  private GlobalNamespace namespaceForChecks = null;

  /**
   * A type-tightener to share across optimization passes.
   */
  private TightenTypes tightenTypes = null;

  public DefaultPassConfig(CompilerOptions options) {
    super(options);
  }

  @Override
  protected List<PassFactory> getChecks() {
    List<PassFactory> checks = Lists.newArrayList();

    if (options.checkSuspiciousCode) {
      checks.add(suspiciousCode);
    }

    if (options.checkControlStructures)  {
      checks.add(checkControlStructures);
    }

    if (options.checkRequires.isOn()) {
      checks.add(checkRequires);
    }

    if (options.checkProvides.isOn()) {
      checks.add(checkProvides);
    }

    // The following passes are more like "preprocessor" passes.
    // It's important that they run before most checking passes.
    // Perhaps this method should be renamed?
    if (options.generateExports) {
      checks.add(generateExports);
    }

    if (options.exportTestFunctions) {
      checks.add(exportTestFunctions);
    }

    if (options.closurePass) {
      checks.add(closurePrimitives.makeOneTimePass());
    }

    if (options.closurePass && options.checkMissingGetCssNameLevel.isOn()) {
      checks.add(closureCheckGetCssName);
    }

    if (options.closurePass) {
      checks.add(closureReplaceGetCssName);
    }

    if (options.syntheticBlockStartMarker != null) {
      // This pass must run before the first fold constants pass.
      checks.add(createSyntheticBlocks);
    }

    if (options.checkSymbols) {
      checks.add(checkVars);
    }

    if (options.checkShadowVars.isOn()) {
      checks.add(checkShadowVars);
    }

    if (options.aggressiveVarCheck.isOn()) {
      checks.add(checkVariableReferences);
    }

    // This pass should run before types are assigned.
    if (options.processObjectPropertyString) {
      checks.add(objectPropertyStringPreprocess);
    }

    // Type-checking already does more accurate method arity checking, so don't
    // do legacy method arity checking unless checkTypes is OFF.
    if (options.checkTypes) {
      checks.add(resolveTypes.makeOneTimePass());
      checks.add(inferTypes.makeOneTimePass());
      checks.add(checkTypes.makeOneTimePass());
    } else {
      if (options.checkFunctions.isOn()) {
        checks.add(checkFunctions);
      }

      if (options.checkMethods.isOn()) {
        checks.add(checkMethods);
      }
    }

    if (options.checkUnreachableCode.isOn() ||
        (options.checkTypes && options.checkMissingReturn.isOn())) {
      checks.add(checkControlFlow);
    }

    // CheckAccessControls only works if check types is on.
    if (options.enables(DiagnosticGroups.ACCESS_CONTROLS)
        && options.checkTypes) {
      checks.add(checkAccessControls);
    }

    if (options.checkGlobalNamesLevel.isOn()) {
      checks.add(checkGlobalNames);
    }

    if (options.checkUndefinedProperties.isOn() ||
        options.checkUnusedPropertiesEarly) {
      checks.add(checkSuspiciousProperties);
    }

    if (options.checkCaja || options.checkEs5Strict) {
      checks.add(checkStrictMode);
    }

    // Defines in code always need to be processed.
    checks.add(processDefines);
    assertAllOneTimePasses(checks);
    return checks;
  }

  @Override
  protected List<PassFactory> getOptimizations() {
    List<PassFactory> passes = Lists.newArrayList();

    // TODO(nicksantos): The order of these passes makes no sense, and needs
    // to be re-arranged.

    if (!options.idGenerators.isEmpty()) {
      passes.add(replaceIdGenerators);
    }

    // Optimizes references to the arguments variable.
    if (options.optimizeArgumentsArray) {
      passes.add(optimizeArgumentsArray);
    }

    // Remove all parameters that are constants or unused.
    if (options.optimizeParameters) {
      passes.add(removeUselessParameters);
    }

    // Abstract method removal works best on minimally modified code, and also
    // only needs to run once.
    if (options.closurePass) {
      passes.add(removeAbstractMethods);
    }

    // Collapsing properties can undo constant inlining, so we do this before
    // the main optimization loop.
    if (options.collapseProperties) {
      passes.add(undoConstantRenaming);
      passes.add(collapseProperties);
      passes.add(renameConstants.makeOneTimePass());
    }

    // Tighten types based on actual usage.
    if (options.tightenTypes) {
      passes.add(tightenTypesBuilder);
    }

    // Property disambiguation should only run once and needs to be done
    // soon after type checking, both so that it can make use of type
    // information and so that other passes can take advantage of the renamed
    // properties.
    if (options.disambiguateProperties) {
      passes.add(disambiguateProperties);
    }

    if (options.computeFunctionSideEffects) {
      passes.add(markPureFunctions);
    } else if (options.markNoSideEffectCalls) {
      // TODO(user) The properties that this pass adds to CALL and NEW
      // AST nodes increase the AST's in-memory size.  Given that we are
      // already running close to our memory limits, we could run into
      // trouble if we end up using the @nosideeffects annotation a lot
      // or compute @nosideeffects annotations by looking at function
      // bodies.  It should be easy to propagate @nosideeffects
      // annotations as part of passes that depend on this property and
      // store the result outside the AST (which would allow garbage
      // collection once the pass is done).
      passes.add(markNoSideEffectCalls);
    }

    if (options.chainCalls) {
      passes.add(chainCalls);
    }

    // Method devirtualization benefits from property disambiguiation so
    // it should run after that pass but before passes that do
    // optimizations based on global names (like smart name removal and cross
    // module code motion)
    if (options.devirtualizePrototypeMethods) {
      passes.add(devirtualizePrototypeMethods);
    }

    // Constant checking must be done after property collapsing because
    // property collapsing can introduce new constants (e.g. enum values).
    if (options.inlineConstantVars) {
      passes.add(checkConsts);
    }

    assertAllOneTimePasses(passes);

    if (options.smartNameRemoval || options.reportPath != null) {
      passes.addAll(getCodeRemovingPasses(true));
      passes.add(smartNamePass);
    }

    if (options.customPasses != null) {
      passes.add(getCustomPasses(
          CustomPassExecutionTime.BEFORE_OPTIMIZATION_LOOP));
    }

    passes.add(createEmptyPass("beforeMainOptimizations"));

    passes.addAll(getMainOptimizationLoop());

    passes.add(createEmptyPass("beforeModuleMotion"));

    if (options.crossModuleCodeMotion) {
      passes.add(crossModuleCodeMotion);
    }

    if (options.crossModuleMethodMotion) {
      passes.add(crossModuleMethodMotion);
    }

    passes.add(createEmptyPass("afterModuleMotion"));

    // Some optimizations belong outside the loop because running them more
    // than once would either have no benefit or be incorrect.
    if (options.customPasses != null) {
      passes.add(getCustomPasses(
          CustomPassExecutionTime.AFTER_OPTIMIZATION_LOOP));
    }

    return passes;
  }

  /** Creates the passes for the main optimization loop. */
  private List<PassFactory> getMainOptimizationLoop() {
    List<PassFactory> passes = Lists.newArrayList();
    if (options.inlineGetters) {
      passes.add(inlineGetters);
    }

    passes.addAll(getCodeRemovingPasses(false));

    if (options.inlineFunctions) {
      passes.add(inlineFunctions);
    }

    if (options.removeUnusedVars) {
      if (options.deadAssignmentElimination) {
        passes.add(deadAssignmentsElimination);
      }
      passes.add(removeUnusedVars);
    }
    assertAllLoopablePasses(passes);
    return passes;
  }

  /** Creates several passes aimed at removing code. */
  private List<PassFactory> getCodeRemovingPasses(
      boolean beforeSmartNameRemoval) {
    List<PassFactory> passes = Lists.newArrayList();
    if (options.inlineVariables && !beforeSmartNameRemoval) {
      passes.add(inlineVariables);
    } else if (options.inlineConstantVars) {
      passes.add(inlineConstants);
    }

    if (options.removeConstantExpressions) {
      passes.add(removeConstantExpressions);
    }

    if (options.foldConstants) {
      // These used to be one pass.
      passes.add(minimizeExitPoints);
      passes.add(foldConstants);
    }

    if (options.removeDeadCode) {
      passes.add(removeUnreachableCode);
    }

    if (options.removeUnusedPrototypeProperties) {
      passes.add(removeUnusedPrototypeProperties);
    }

    assertAllLoopablePasses(passes);
    return passes;
  }

  /**
   * Checks for code that is probably wrong (such as stray expressions).
   */
  // TODO(bolinfest): Write a CompilerPass for this.
  final PassFactory suspiciousCode =
      new PassFactory("suspiciousCode", true) {

    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      List<Callback> sharedCallbacks = Lists.newArrayList();
      sharedCallbacks.add(new CheckAccidentalSemicolon(CheckLevel.WARNING));
      sharedCallbacks.add(new CheckSideEffects(CheckLevel.WARNING));
      if (options.checkGlobalThisLevel.isOn()) {
        sharedCallbacks.add(
            new CheckGlobalThis(compiler, options.checkGlobalThisLevel));
      }
      return combineChecks(compiler, sharedCallbacks);
    }

  };

  /** Verify that all the passes are one-time passes. */
  private void assertAllOneTimePasses(List<PassFactory> passes) {
    for (PassFactory pass : passes) {
      Preconditions.checkState(pass.isOneTimePass());
    }
  }

  /** Verify that all the passes are multi-run passes. */
  private void assertAllLoopablePasses(List<PassFactory> passes) {
    for (PassFactory pass : passes) {
      Preconditions.checkState(!pass.isOneTimePass());
    }
  }

  /** Checks for validity of the control structures. */
  private final PassFactory checkControlStructures =
      new PassFactory("checkControlStructures", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new ControlStructureCheck(compiler);
    }
  };

  /** Checks that all constructed classes are goog.require()d. */
  private final PassFactory checkRequires =
      new PassFactory("checkRequires", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CheckRequiresForConstructors(compiler, options.checkRequires);
    }
  };

  /** Makes sure @constructor is paired with goog.provides(). */
  private final PassFactory checkProvides =
      new PassFactory("checkProvides", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CheckProvides(compiler, options.checkProvides);
    }
  };

  private static final DiagnosticType GENERATE_EXPORTS_ERROR =
      DiagnosticType.error(
          "JSC_GENERATE_EXPORTS_ERROR",
          "Exports can only be generated if export symbol/property " +
          "functions are set.");

  /** Generates exports for @export annotations. */
  private final PassFactory generateExports =
      new PassFactory("generateExports", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      CodingConvention convention = compiler.getCodingConvention();
      if (convention.getExportSymbolFunction() != null &&
          convention.getExportPropertyFunction() != null) {
        return new GenerateExports(compiler,
            convention.getExportSymbolFunction(),
            convention.getExportPropertyFunction());
      } else {
        return new ErrorPass(compiler, GENERATE_EXPORTS_ERROR);
      }
    }
  };

  /** Generates exports for functions associated with JSUnit. */
  private final PassFactory exportTestFunctions =
      new PassFactory("exportTestFunctions", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      CodingConvention convention = compiler.getCodingConvention();
      if (convention.getExportSymbolFunction() != null) {
        return new ExportTestFunctions(compiler,
            convention.getExportSymbolFunction());
      } else {
        return new ErrorPass(compiler, GENERATE_EXPORTS_ERROR);
      }
    }
  };

  /** Closure pre-processing pass. */
  @SuppressWarnings("deprecation")
  final PassFactory closurePrimitives =
      new PassFactory("processProvidesAndRequires", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      final ProcessClosurePrimitives pass =
          options.brokenClosureRequiresLevel != null ?
          new ProcessClosurePrimitives(
              compiler,
              options.brokenClosureRequiresLevel,
              options.rewriteNewDateGoogNow) :
          new ProcessClosurePrimitives(
              compiler,
              options.allowBrokenClosureRequires,
              options.rewriteNewDateGoogNow);

      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          pass.process(externs, root);
          setExportedNames(pass.getExportedVariableNames());
        }
      };
    }
  };

  /** Checks that CSS class names are wrapped in goog.getCssName */
  private final PassFactory closureCheckGetCssName =
      new PassFactory("checkMissingGetCssName", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      String blacklist = options.checkMissingGetCssNameBlacklist;
      Preconditions.checkState(blacklist != null && !blacklist.isEmpty(),
          "Not checking use of goog.getCssName because of empty blacklist.");
      return new CheckMissingGetCssName(
          compiler, options.checkMissingGetCssNameLevel, blacklist);
    }
  };

  /**
   * Processes goog.getCssName.  The cssRenamingMap is used to lookup
   * replacement values for the classnames.  If null, the raw class names are
   * inlined.
   */
  private final PassFactory closureReplaceGetCssName =
      new PassFactory("renameCssNames", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          Map<String, Integer> cssNames = null;
          if (options.gatherCssNames) {
            cssNames = Maps.newHashMap();
          }
          (new ReplaceCssNames(compiler, cssNames)).process(
              externs, jsRoot);
          setCssNames(cssNames);
        }
      };
    }
  };

  /**
   * Creates synthetic blocks to prevent FoldConstants from moving code
   * past markers in the source.
   */
  private final PassFactory createSyntheticBlocks =
      new PassFactory("createSyntheticBlocks", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CreateSyntheticBlocks(compiler,
          options.syntheticBlockStartMarker,
          options.syntheticBlockEndMarker);
    }
  };

  /** Local constant folding */
  static final PassFactory foldConstants =
      new PassFactory("foldConstants", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new FoldConstants(compiler);
    }
  };

  /** Checks that all variables are defined. */
  private final PassFactory checkVars =
      new PassFactory("checkVars", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new VarCheck(compiler);
    }
  };

  /** Checks that no vars are illegally shadowed. */
  private final PassFactory checkShadowVars =
      new PassFactory("variableShadowDeclarationCheck", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new VariableShadowDeclarationCheck(
          compiler, options.checkShadowVars);
    }
  };

  /** Checks that references to variables look reasonable. */
  private final PassFactory checkVariableReferences =
      new PassFactory("checkVariableReferences", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new VariableReferenceCheck(
          compiler, options.aggressiveVarCheck);
    }
  };

  /** Pre-process goog.testing.ObjectPropertyString. */
  private final PassFactory objectPropertyStringPreprocess =
      new PassFactory("ObjectPropertyStringPreprocess", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new ObjectPropertyStringPreprocess(compiler);
    }
  };

  /** Checks number of args passed to functions. */
  private final PassFactory checkFunctions =
      new PassFactory("checkFunctions", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new FunctionCheck(compiler, options.checkFunctions);
    }
  };

  /** Checks number of args passed to methods. */
  private final PassFactory checkMethods =
      new PassFactory("checkMethods", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new MethodCheck(compiler, options.checkMethods);
    }
  };

  /** Creates a typed scope and adds types to the type registry. */
  final PassFactory resolveTypes =
      new PassFactory("resolveTypes", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new GlobalTypeResolver(compiler);
    }
  };

  /** Rusn type inference. */
  private final PassFactory inferTypes =
      new PassFactory("inferTypes", false) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          Preconditions.checkNotNull(topScope);
          Preconditions.checkNotNull(typedScopeCreator);

          makeTypeInference(compiler).process(externs, root);
        }
      };
    }
  };

  /** Checks type usage */
  private final PassFactory checkTypes =
      new PassFactory("checkTypes", false) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          Preconditions.checkNotNull(topScope);
          Preconditions.checkNotNull(typedScopeCreator);

          TypeCheck check = makeTypeCheck(compiler);
          check.process(externs, root);
          compiler.getErrorManager().setTypedPercent(check.getTypedPercent());
        }
      };
    }
  };

  /**
   * Checks possible execution paths of the program for problems: missing return
   * statements and dead code.
   */
  private final PassFactory checkControlFlow =
      new PassFactory("checkControlFlow", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      List<Callback> callbacks = Lists.newArrayList();
      if (options.checkUnreachableCode.isOn()) {
        callbacks.add(
            new CheckUnreachableCode(compiler, options.checkUnreachableCode));
      }
      if (options.checkMissingReturn.isOn() && options.checkTypes) {
        callbacks.add(
            new CheckMissingReturn(compiler, options.checkMissingReturn));
      }
      return combineChecks(compiler, callbacks);
    }
  };

  /** Checks access controls. Depends on type-inference. */
  private final PassFactory checkAccessControls =
      new PassFactory("checkAccessControls", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CheckAccessControls(compiler);
    }
  };

  /** Executes the given callbacks with a {@link CombinedCompilerPass}. */
  private static CompilerPass combineChecks(AbstractCompiler compiler,
      List<Callback> callbacks) {
    Preconditions.checkArgument(callbacks.size() > 0);
    Callback[] array = callbacks.toArray(new Callback[callbacks.size()]);
    return new CombinedCompilerPass(compiler, array);
  }

  /** A compiler pass that resolves types in the global scope. */
  private class GlobalTypeResolver implements CompilerPass {
    private final AbstractCompiler compiler;

    GlobalTypeResolver(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      if (topScope == null) {
        typedScopeCreator =
            new MemoizedScopeCreator(new TypedScopeCreator(compiler));
        topScope = typedScopeCreator.createScope(root.getParent(), null);
      } else {
        compiler.getTypeRegistry().resolveTypesInScope(topScope);
      }
    }
  }

  /** Checks global name usage. */
  private final PassFactory checkGlobalNames =
      new PassFactory("Check names", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          // Create a global namespace for analysis by check passes.
          // Note that this class does all heavy computation lazily,
          // so it's OK to create it here.
          namespaceForChecks = new GlobalNamespace(compiler, jsRoot);
          new CheckGlobalNames(compiler, options.checkGlobalNamesLevel)
              .injectNamespace(namespaceForChecks).process(externs, jsRoot);
        }
      };
    }
  };

  /** Checks for properties that are not read or written */
  private final PassFactory checkSuspiciousProperties =
      new PassFactory("checkSuspiciousProperties", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new SuspiciousPropertiesCheck(
          compiler,
          options.checkUndefinedProperties,
          options.checkUnusedPropertiesEarly ?
              CheckLevel.WARNING : CheckLevel.OFF);
    }
  };

  /** Checks that the code is ES5 or Caja compliant. */
  private final PassFactory checkStrictMode =
      new PassFactory("checkStrictMode", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new StrictModeCheck(compiler,
          !options.checkSymbols,  // don't check variables twice
          !options.checkCaja);    // disable eval check if not Caja
    }
  };

  /** Override @define-annotated constants. */
  final PassFactory processDefines =
      new PassFactory("processDefines", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          Map<String, Node> replacements = getAdditionalReplacements(options);
          replacements.putAll(options.getDefineReplacements());

          new ProcessDefines(compiler, replacements)
              .injectNamespace(namespaceForChecks).process(externs, jsRoot);

          // Kill the namespace in the other class
          // so that it can be garbage collected after all passes
          // are through with it.
          namespaceForChecks = null;
        }
      };
    }
  };

  /** Checks that all constants are not modified */
  private final PassFactory checkConsts =
      new PassFactory("checkConsts", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new ConstCheck(compiler);
    }
  };

  /** Generates unique ids. */
  private final PassFactory replaceIdGenerators =
      new PassFactory("replaceIdGenerators", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new ReplaceIdGenerators(compiler, options.idGenerators);
    }
  };

  /** Optimizes the "arguments" array. */
  private final PassFactory optimizeArgumentsArray =
      new PassFactory("optimizeArgumentsArray", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new OptimizeArgumentsArray(compiler);
    }
  };

  /** Removes unused or constant formal parameters. */
  private final PassFactory removeUselessParameters =
      new PassFactory("optimizeParameters", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          NameReferenceGraphConstruction c =
              new NameReferenceGraphConstruction(compiler);
          c.process(externs, root);

          (new OptimizeParameters(compiler, c.getNameReferenceGraph())).process(
              externs, root);
        }
      };
    }
  };

  /** Remove variables set to goog.abstractMethod. */
  private final PassFactory removeAbstractMethods =
      new PassFactory("removeAbstractMethods", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new GoogleCodeRemoval(compiler);
    }
  };

  /** Collapses names in the global scope. */
  private final PassFactory collapseProperties =
      new PassFactory("collapseProperties", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CollapseProperties(
          compiler, options.collapsePropertiesOnExternTypes,
          !isInliningForbidden());
    }
  };

  /**
   * Undo the renaming to $$CONSTANT that we're doing to preserve
   * annotations.
   */
  private final PassFactory undoConstantRenaming =
      new PassFactory("undoConstantNames", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new MakeDeclaredNamesUnique.UndoConstantRenaming(compiler);
    }
  };

  /**
   * Add $$CONSTANT to names of constant variables, to preserve the
   * {@code @const} annotation.
   */
  private final PassFactory renameConstants =
      new PassFactory("renameConstants", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new Normalize(compiler, false).new RenameConstants();
    }
  };

  /**
   * Try to infer the actual types, which may be narrower
   * than the declared types.
   */
  private final PassFactory tightenTypesBuilder =
      new PassFactory("tightenTypes", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      if (!options.checkTypes) {
        return new ErrorPass(compiler, TIGHTEN_TYPES_WITHOUT_TYPE_CHECK);
      }
      tightenTypes = new TightenTypes(compiler);
      return tightenTypes;
    }
  };

  /** Devirtualize property names based on type information. */
  private final PassFactory disambiguateProperties =
      new PassFactory("disambiguateProperties", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      if (tightenTypes == null) {
        return DisambiguateProperties.forJSTypeSystem(compiler);
      } else {
        return DisambiguateProperties.forConcreteTypeSystem(
            compiler, tightenTypes);
      }
    }
  };

  /**
   * Chain calls to functions that return this.
   */
  private final PassFactory chainCalls =
      new PassFactory("chainCalls", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new ChainCalls(compiler);
    }
  };

  /**
   * Rewrite instance methods as static methods, to make them easier
   * to inline.
   */
  private final PassFactory devirtualizePrototypeMethods =
      new PassFactory("devirtualizePrototypeMethods", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new DevirtualizePrototypeMethods(compiler);
    }
  };

  /**
   * Look for function calls that are pure, and annotate them
   * that way.
   */
  private final PassFactory markPureFunctions =
      new PassFactory("markPureFunctions", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new PureFunctionMarker(
          compiler, options.debugFunctionSideEffectsPath, false);
    }
  };

  /**
   * Look for function calls that have no side effects, and annotate them
   * that way.
   */
  private final PassFactory markNoSideEffectCalls =
      new PassFactory("markNoSideEffectCalls", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new MarkNoSideEffectCalls(compiler);
    }
  };

  /** Inlines variables heuristically. */
  private final PassFactory inlineVariables =
      new PassFactory("inlineVariables", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      if (isInliningForbidden()) {
        // In old renaming schemes, inlining a variable can change whether
        // or not a property is renamed. This is bad, and those old renaming
        // schemes need to die.
        return new ErrorPass(compiler, CANNOT_USE_PROTOTYPE_AND_VAR);
      } else {
        return new InlineVariables(compiler, false, true);
      }
    }
  };

  /** Inlines variables that are marked as constants. */
  private final PassFactory inlineConstants =
      new PassFactory("inlineConstants", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new InlineVariables(compiler, true, true);
    }
  };

  /**
   * Simplify expressions by removing the parts that have no side effects.
   */
  private final PassFactory removeConstantExpressions =
      new PassFactory("removeConstantExpressions", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new RemoveConstantExpressions(compiler);
    }
  };

  /**
   * Perform local control flow optimizations.
   */
  private final PassFactory minimizeExitPoints =
      new PassFactory("minimizeExitPoints", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new MinimizeExitPoints(compiler);
    }
  };

  /**
   * Use data flow analysis to remove dead branches.
   */
  private final PassFactory removeUnreachableCode =
      new PassFactory("removeUnreachableCode", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new UnreachableCodeElimination(compiler, true);
    }
  };

  /**
   * Remove prototype properties that do not appear to be used.
   */
  private final PassFactory removeUnusedPrototypeProperties =
      new PassFactory("removeUnusedPrototypeProperties", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new RemoveUnusedPrototypeProperties(
          compiler, options.removeUnusedPrototypePropertiesInExterns,
          !options.removeUnusedVars);
    }
  };

  /**
   * Process smart name processing - removes unused classes and does referencing
   * starting with minimum set of names.
   */
  private final PassFactory smartNamePass =
      new PassFactory("smartNamePass", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          NameAnalyzer na = new NameAnalyzer(compiler, false);
          na.process(externs, root);

          String reportPath = options.reportPath;
          if (reportPath != null) {
            try {
              Files.write(na.getHtmlReport(), new File(reportPath),
                  Charsets.UTF_8);
            } catch (IOException e) {
              compiler.report(JSError.make(REPORT_PATH_IO_ERROR, reportPath));
            }
          }

          if (options.smartNameRemoval) {
            na.removeUnreferenced();
          }
        }
      };
    }
  };

  /** Inlines simple methods, like getters */
  private PassFactory inlineGetters =
      new PassFactory("inlineGetters", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new InlineGetters(compiler);
    }
  };

  /** Kills dead assignments. */
  private PassFactory deadAssignmentsElimination =
      new PassFactory("deadAssignmentsElimination", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new DeadAssignmentsElimination(compiler);
    }
  };

  /** Inlines function calls. */
  private PassFactory inlineFunctions =
      new PassFactory("inlineFunctions", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      boolean enableBlockInlining = !isInliningForbidden();
      return new InlineFunctions(
          compiler, compiler.getUniqueNameIdSupplier(),
          enableBlockInlining, options.decomposeExpressions,
          options.inlineAnonymousFunctionExpressions);
    }
  };

  /** Removes variables that are never used. */
  private PassFactory removeUnusedVars =
      new PassFactory("removeUnusedVars", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new RemoveUnusedVars(
          compiler, options.removeUnusedVarsInGlobalScope);
    }
  };

  /**
   * Move global symbols to a deeper common module
   */
  private PassFactory crossModuleCodeMotion =
      new PassFactory("crossModuleCodeMotion", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CrossModuleCodeMotion(compiler, compiler.getModuleGraph());
    }
  };

  /**
   * Move methods to a deeper common module
   */
  private PassFactory crossModuleMethodMotion =
      new PassFactory("crossModuleMethodMotion", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CrossModuleMethodMotion(
          compiler, getCrossModuleIdGenerator(),
          // Only move properties in externs if we're not treating
          // them as exports.
          options.removeUnusedPrototypePropertiesInExterns);
    }
  };

  /**
   * Create a no-op pass that can only run once. Used to break up loops.
   */
  private static PassFactory createEmptyPass(String name) {
    return new PassFactory(name, true) {
      @Override
      protected CompilerPass createInternal(final AbstractCompiler compiler) {
        return runInSerial();
      }
    };
  }

  /**
   * Runs custom passes that are designated to run at a particular time.
   */
  private PassFactory getCustomPasses(
      final CustomPassExecutionTime executionTime) {
    return new PassFactory("runCustomPasses", true) {
      @Override
      protected CompilerPass createInternal(final AbstractCompiler compiler) {
        return runInSerial(options.customPasses.get(executionTime));
      }
    };
  }

  /**
   * All inlining is forbidden in heuristic renaming mode, because inlining
   * will ruin the invariants that it depends on.
   */
  private boolean isInliningForbidden() {
    return options.propertyRenaming == PropertyRenamingPolicy.HEURISTIC ||
        options.propertyRenaming ==
            PropertyRenamingPolicy.AGGRESSIVE_HEURISTIC;
  }

  /** Create a compiler pass that runs the given passes in serial. */
  private static CompilerPass runInSerial(final CompilerPass ... passes) {
    return runInSerial(Lists.newArrayList(passes));
  }

  /** Create a compiler pass that runs the given passes in serial. */
  private static CompilerPass runInSerial(
      final Collection<CompilerPass> passes) {
    return new CompilerPass() {
      @Override public void process(Node externs, Node root) {
        for (CompilerPass pass : passes) {
          pass.process(externs, root);
        }
      }
    };
  }

  @VisibleForTesting
  static Map<String, Node> getAdditionalReplacements(
      CompilerOptions options) {
    Map<String, Node> additionalReplacements = Maps.newHashMap();

    if (options.markAsCompiled || options.closurePass) {
      additionalReplacements.put(COMPILED_CONSTANT_NAME, new Node(Token.TRUE));
    }

    if (options.closurePass && options.locale != null) {
      additionalReplacements.put(CLOSURE_LOCALE_CONSTANT_NAME,
          Node.newString(options.locale));
    }

    return additionalReplacements;
  }

  /** A compiler pass that just reports an error. */
  private static class ErrorPass implements CompilerPass {
    private final AbstractCompiler compiler;
    private final DiagnosticType error;

    private ErrorPass(AbstractCompiler compiler, DiagnosticType error) {
      this.compiler = compiler;
      this.error = error;
    }

    @Override
    public void process(Node externs, Node root) {
      compiler.report(JSError.make(error));
    }
  }

  /** A compiler pass that marks pure functions. */
  private static class PureFunctionMarker implements CompilerPass {
    private final AbstractCompiler compiler;
    private final String reportPath;
    private final boolean useNameReferenceGraph;

    PureFunctionMarker(AbstractCompiler compiler, String reportPath,
        boolean useNameReferenceGraph) {
      this.compiler = compiler;
      this.reportPath = reportPath;
      this.useNameReferenceGraph = useNameReferenceGraph;
    }

    @Override
    public void process(Node externs, Node root) {
      DefinitionProvider definitionProvider = null;
      if (useNameReferenceGraph) {
        NameReferenceGraphConstruction graphBuilder =
            new NameReferenceGraphConstruction(compiler);
        graphBuilder.process(externs, root);
        definitionProvider = graphBuilder.getNameReferenceGraph();
      } else {
        SimpleDefinitionFinder defFinder = new SimpleDefinitionFinder(compiler);
        defFinder.process(externs, root);
        definitionProvider = defFinder;
      }

      PureFunctionIdentifier pureFunctionIdentifier =
          new PureFunctionIdentifier(compiler, definitionProvider);
      pureFunctionIdentifier.process(externs, root);

      if (reportPath != null) {
        try {
          Files.write(pureFunctionIdentifier.getDebugReport(),
              new File(reportPath),
              Charsets.UTF_8);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
