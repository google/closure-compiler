/*
 * Copyright 2016 The Closure Compiler Authors.
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


import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.Es6RewriteDestructuring.ObjectDestructuringRewriteMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;

/** Provides a single place to manage transpilation passes. */
public class TranspilationPasses {

  private TranspilationPasses() {}

  public static void addEs6ModulePass(
      PassListBuilder passes,
      final PreprocessorSymbolTable.CachedInstanceFactory preprocessorTableFactory) {
    passes.maybeAdd(
        PassFactory.builder()
            .setName("es6RewriteModule")
            .setInternalFactory(
                (compiler) -> {
                  preprocessorTableFactory.maybeInitialize(compiler);
                  return new Es6RewriteModules(
                      compiler,
                      compiler.getModuleMetadataMap(),
                      compiler.getModuleMap(),
                      preprocessorTableFactory.getInstanceOrNull(),
                      compiler.getTopScope(),
                      compiler.getOptions().getChunkOutputType());
                })
            .build());
  }

  public static void addTranspilationRuntimeLibraries(PassListBuilder passes) {
    // Inject runtime libraries needed for the transpilation we will have to do. Should run before
    // typechecking.
    passes.maybeAdd(injectTranspilationRuntimeLibraries);
  }

  public static void addEs6ModuleToCjsPass(PassListBuilder passes) {
    passes.maybeAdd(es6RewriteModuleToCjs);
  }

  public static void addEs6RewriteImportPathPass(PassListBuilder passes) {
    passes.maybeAdd(es6RelativizeImportPaths);
  }

  /** Adds transpilation passes that should run at the beginning of the optimization phase */
  public static void addEarlyOptimizationTranspilationPasses(
      PassListBuilder passes, CompilerOptions options) {

    passes.maybeAdd(reportUntranspilableFeatures);

    // Note that we detect feature by feature rather than by yearly languages
    // in order to handle FeatureSet.BROWSER_2020, which is ES2019 without the new RegExp features.
    // However, RegExp features are not transpiled, and this does not imply that we allow arbitrary
    // selection of features to transpile.  They still must be done in chronological order based on.
    // This greatly simplifies testing and the requirements for the transpilation passes.

    if (options.needsTranspilationOf(Feature.REGEXP_FLAG_D)) {
      passes.maybeAdd(
          createFeatureRemovalPass(
              "markEs2022FeaturesNotRequiringTranspilationAsRemoved", Feature.REGEXP_FLAG_D));
    }

    if (options.needsTranspilationOf(Feature.PUBLIC_CLASS_FIELDS)
        || options.needsTranspilationOf(Feature.CLASS_STATIC_BLOCK)
        || options.needsTranspilationOf(Feature.CLASSES)) {
      // Make sure that a variable is created to hold every class definition.
      // This allows us to add static properties and methods by adding properties
      // to that variable.
      passes.maybeAdd(es6RewriteClassExtends);
      passes.maybeAdd(es6ExtractClasses);
    }

    if (options.needsTranspilationOf(Feature.PUBLIC_CLASS_FIELDS)
        || options.needsTranspilationOf(Feature.CLASS_STATIC_BLOCK)) {
      passes.maybeAdd(rewriteClassMembers);
    }

    if (options.needsTranspilationOf(Feature.NUMERIC_SEPARATOR)) {
      // Numeric separators are flagged as present by the parser,
      // but never actually represented in the AST.
      // The only thing we need to do is mark them as not present in the AST.
      passes.maybeAdd(
          createFeatureRemovalPass("markNumericSeparatorsRemoved", Feature.NUMERIC_SEPARATOR));
    }

    if (options.needsTranspilationOf(Feature.LOGICAL_ASSIGNMENT)) {
      passes.maybeAdd(rewriteLogicalAssignmentOperatorsPass);
    }

    if (options.needsTranspilationOf(Feature.OPTIONAL_CHAINING)) {
      passes.maybeAdd(rewriteOptionalChainingOperator);
    }

    if (options.needsTranspilationOf(Feature.BIGINT)) {
      passes.maybeAdd(createFeatureRemovalPass("markBigintsRemoved", Feature.BIGINT));
    }

    if (options.needsTranspilationOf(Feature.NULL_COALESCE_OP)) {
      passes.maybeAdd(rewriteNullishCoalesceOperator);
    }

    if (options.needsTranspilationOf(Feature.OPTIONAL_CATCH_BINDING)) {
      passes.maybeAdd(rewriteCatchWithNoBinding);
    }

    if (options.getChunkOutputType() != ChunkOutputType.ES_MODULES) {
      // Default output mode of JSCompiler is a script, unless chunkOutputType is set to
      // `ES_MODULES` where each output chunk is an ES module.
      passes.maybeAdd(createFeatureRemovalPass("markModulesRemoved", Feature.MODULES));
      // Since import.meta cannot be transpiled, it is passed-through when the output format
      // is a module. Otherwise it must be marked removed.
      passes.maybeAdd(createFeatureRemovalPass("markImportMetaRemoved", Feature.IMPORT_META));
      // Dynamic imports are preserved for open source output only when the chunk output type is
      // ES_MODULES
      passes.maybeAdd(createFeatureRemovalPass("markDynamicImportRemoved", Feature.DYNAMIC_IMPORT));
    }

    if (options.needsTranspilationOf(Feature.FOR_AWAIT_OF)
        || options.needsTranspilationOf(Feature.ASYNC_GENERATORS)) {
      passes.maybeAdd(rewriteAsyncIteration);
    }

    if (options.needsTranspilationOf(Feature.OBJECT_LITERALS_WITH_SPREAD)
        || options.needsTranspilationOf(Feature.OBJECT_PATTERN_REST)) {
      passes.maybeAdd(rewriteObjectSpread);
      if (!options.needsTranspilationOf(Feature.OBJECT_DESTRUCTURING)
          && options.needsTranspilationOf(Feature.OBJECT_PATTERN_REST)) {
        // We only need to transpile away object destructuring that uses `...`, rather than
        // all destructuring.
        // For this to work correctly for object destructuring in parameter lists and variable
        // declarations, we need to normalize them a bit first.
        passes.maybeAdd(es6RenameVariablesInParamLists);
        passes.maybeAdd(es6SplitVariableDeclarations);
        passes.maybeAdd(
            getEs6RewriteDestructuring(ObjectDestructuringRewriteMode.REWRITE_OBJECT_REST));
      }
    }

    if (options.needsTranspilationOf(Feature.ASYNC_FUNCTIONS)) {
      passes.maybeAdd(rewriteAsyncFunctions);
    }

    if (options.needsTranspilationOf(Feature.EXPONENT_OP)) {
      passes.maybeAdd(rewriteExponentialOperator);
    }

    if (options.needsTranspilationFrom(
        FeatureSet.BARE_MINIMUM.with(
            Feature.BINARY_LITERALS,
            Feature.OCTAL_LITERALS,
            Feature.REGEXP_FLAG_U,
            Feature.REGEXP_FLAG_Y))) {
      // Binary and octal literals are effectively transpiled by the parser.
      // There's no transpilation we can do for the new regexp flags.
      passes.maybeAdd(
          createFeatureRemovalPass(
              "markEs6FeaturesNotRequiringTranspilationAsRemoved",
              Feature.BINARY_LITERALS,
              Feature.OCTAL_LITERALS,
              Feature.REGEXP_FLAG_U,
              Feature.REGEXP_FLAG_Y));
    }

    if (options.needsTranspilationOf(Feature.EXTENDED_OBJECT_LITERALS)) {
      passes.maybeAdd(es6NormalizeShorthandProperties);
    }
    if (options.needsTranspilationOf(Feature.CLASSES)) {
      passes.maybeAdd(es6ConvertSuper);
    }
    if (options.needsTranspilationFrom(
        FeatureSet.BARE_MINIMUM.with(Feature.ARRAY_DESTRUCTURING, Feature.OBJECT_DESTRUCTURING))) {
      passes.maybeAdd(es6RenameVariablesInParamLists);
      passes.maybeAdd(es6SplitVariableDeclarations);
      passes.maybeAdd(
          getEs6RewriteDestructuring(ObjectDestructuringRewriteMode.REWRITE_ALL_OBJECT_PATTERNS));
    }
    if (options.needsTranspilationOf(Feature.NEW_TARGET)) {
      passes.maybeAdd(rewriteNewDotTarget);
    }
    if (options.needsTranspilationOf(Feature.ARROW_FUNCTIONS)) {
      passes.maybeAdd(es6RewriteArrowFunction);
    }
    if (options.needsTranspilationOf(Feature.CLASSES)) {
      passes.maybeAdd(es6RewriteClass);
    }
    if (options.needsTranspilationFrom(
        FeatureSet.BARE_MINIMUM.with(Feature.REST_PARAMETERS, Feature.SPREAD_EXPRESSIONS))) {
      passes.maybeAdd(es6RewriteRestAndSpread);
    }
    if (options.needsTranspilationFrom(
        FeatureSet.BARE_MINIMUM.with(
            Feature.COMPUTED_PROPERTIES, Feature.MEMBER_DECLARATIONS, Feature.TEMPLATE_LITERALS))) {
      passes.maybeAdd(lateConvertEs6ToEs3);
    }
    if (options.needsTranspilationOf(Feature.FOR_OF)) {
      passes.maybeAdd(es6ForOf);
    }
    if (options.needsTranspilationOf(Feature.BLOCK_SCOPED_FUNCTION_DECLARATION)) {
      passes.maybeAdd(rewriteBlockScopedFunctionDeclaration);
    }
    if (options.needsTranspilationFrom(
        FeatureSet.BARE_MINIMUM.with(Feature.LET_DECLARATIONS, Feature.CONST_DECLARATIONS))) {
      passes.maybeAdd(rewriteBlockScopedDeclaration);
    }
  }

  /** Adds transpilation passes that should not be run until after normalization has been done. */
  public static void addPostNormalizationTranspilationPasses(
      PassListBuilder passes, CompilerOptions options) {
    // TODO(b/197349249): Move passes from `addEarlyOptimizationTranspilationPasses()` to here
    // until that method can be deleted as a no-op.
    if (options.needsTranspilationOf(Feature.GENERATORS)) {
      passes.maybeAdd(rewriteGenerators);
    }
    passes.maybeAdd(
        TranspilationPasses.createPostTranspileUnsupportedFeaturesRemovedCheck(
            "postTranspileUnsupportedFeaturesRemovedCheck"));
  }

  /** Adds the pass to inject ES2015 polyfills, which goes after the late ES2015 passes. */
  public static void addRewritePolyfillPass(PassListBuilder passes) {
    passes.maybeAdd(rewritePolyfills);
  }

  /** Rewrites ES6 modules */
  private static final PassFactory es6RewriteModuleToCjs =
      PassFactory.builder()
          .setName("es6RewriteModuleToCjs")
          .setInternalFactory(Es6RewriteModulesToCommonJsModules::new)
          .build();

  /** Rewrites ES6 modules import paths to be browser compliant */
  private static final PassFactory es6RelativizeImportPaths =
      PassFactory.builder()
          .setName("es6RelativizeImportPaths")
          .setInternalFactory(Es6RelativizeImportPaths::new)
          .build();

  private static final PassFactory rewriteAsyncFunctions =
      PassFactory.builder()
          .setName("rewriteAsyncFunctions")
          .setInternalFactory(RewriteAsyncFunctions::create)
          .build();

  private static final PassFactory rewriteAsyncIteration =
      PassFactory.builder()
          .setName("rewriteAsyncIteration")
          .setInternalFactory(RewriteAsyncIteration::create)
          .build();

  private static final PassFactory rewriteObjectSpread =
      PassFactory.builder()
          .setName("rewriteObjectSpread")
          .setInternalFactory(RewriteObjectSpread::new)
          .build();

  private static final PassFactory rewriteCatchWithNoBinding =
      PassFactory.builder()
          .setName("rewriteCatchWithNoBinding")
          .setInternalFactory(RewriteCatchWithNoBinding::new)
          .build();

  private static final PassFactory rewriteNewDotTarget =
      PassFactory.builder()
          .setName("rewriteNewDotTarget")
          .setInternalFactory(RewriteNewDotTarget::new)
          .build();

  private static final PassFactory rewriteExponentialOperator =
      PassFactory.builder()
          .setName("rewriteExponentialOperator")
          .setInternalFactory(Es7RewriteExponentialOperator::new)
          .build();

  private static final PassFactory es6NormalizeShorthandProperties =
      PassFactory.builder()
          .setName("es6NormalizeShorthandProperties")
          .setInternalFactory(Es6NormalizeShorthandProperties::new)
          .build();

  static final PassFactory es6RewriteClassExtends =
      PassFactory.builder()
          .setName(PassNames.ES6_REWRITE_CLASS_EXTENDS)
          .setInternalFactory(Es6RewriteClassExtendsExpressions::new)
          .build();

  static final PassFactory es6ExtractClasses =
      PassFactory.builder()
          .setName(PassNames.ES6_EXTRACT_CLASSES)
          .setInternalFactory(Es6ExtractClasses::new)
          .build();

  static final PassFactory rewriteClassMembers =
      PassFactory.builder()
          .setName("RewriteClassMembers")
          .setInternalFactory(RewriteClassMembers::new)
          .build();

  static final PassFactory es6RewriteClass =
      PassFactory.builder()
          .setName("Es6RewriteClass")
          .setInternalFactory(Es6RewriteClass::new)
          .build();

  static final PassFactory getEs6RewriteDestructuring(ObjectDestructuringRewriteMode rewriteMode) {
    return PassFactory.builder()
        .setName("Es6RewriteDestructuring")
        .setInternalFactory(
            (compiler) ->
                new Es6RewriteDestructuring.Builder(compiler)
                    .setDestructuringRewriteMode(rewriteMode)
                    .build())
        .build();
  }

  static final PassFactory es6RenameVariablesInParamLists =
      PassFactory.builder()
          .setName("Es6RenameVariablesInParamLists")
          .setInternalFactory(Es6RenameVariablesInParamLists::new)
          .build();

  static final PassFactory es6RewriteArrowFunction =
      PassFactory.builder()
          .setName("Es6RewriteArrowFunction")
          .setInternalFactory(Es6RewriteArrowFunction::new)
          .build();

  static final PassFactory rewritePolyfills =
      PassFactory.builder()
          .setName("RewritePolyfills")
          .setInternalFactory(
              (compiler) ->
                  new RewritePolyfills(
                      compiler,
                      compiler.getOptions().getRewritePolyfills(),
                      compiler.getOptions().getIsolatePolyfills()))
          .build();

  static final PassFactory es6SplitVariableDeclarations =
      PassFactory.builder()
          .setName("Es6SplitVariableDeclarations")
          .setInternalFactory(Es6SplitVariableDeclarations::new)
          .build();

  static final PassFactory es6ConvertSuper =
      PassFactory.builder()
          .setName("es6ConvertSuper")
          .setInternalFactory(Es6ConvertSuper::new)
          .build();

  /** Injects runtime library code needed for transpiled ES2015+ code. */
  static final PassFactory injectTranspilationRuntimeLibraries =
      PassFactory.builder()
          .setName("es6InjectRuntimeLibraries")
          .setInternalFactory(InjectTranspilationRuntimeLibraries::new)
          .build();

  /** Transpiles REST parameters and SPREAD in both array literals and function calls. */
  static final PassFactory es6RewriteRestAndSpread =
      PassFactory.builder()
          .setName("es6RewriteRestAndSpread")
          .setInternalFactory(Es6RewriteRestAndSpread::new)
          .build();

  /**
   * Does the main ES2015 to ES3 conversion. There are a few other passes which run before this one,
   * to convert constructs which are not converted by this pass.
   */
  static final PassFactory lateConvertEs6ToEs3 =
      PassFactory.builder()
          .setName("lateConvertEs6")
          .setInternalFactory(LateEs6ToEs3Converter::new)
          .build();

  static final PassFactory es6ForOf =
      PassFactory.builder().setName("es6ForOf").setInternalFactory(Es6ForOfConverter::new).build();

  static final PassFactory rewriteBlockScopedFunctionDeclaration =
      PassFactory.builder()
          .setName("Es6RewriteBlockScopedFunctionDeclaration")
          .setInternalFactory(Es6RewriteBlockScopedFunctionDeclaration::new)
          .build();

  static final PassFactory rewriteBlockScopedDeclaration =
      PassFactory.builder()
          .setName("Es6RewriteBlockScopedDeclaration")
          .setInternalFactory(Es6RewriteBlockScopedDeclaration::new)
          .build();

  static final PassFactory rewriteGenerators =
      PassFactory.builder()
          .setName("rewriteGenerators")
          .setInternalFactory(Es6RewriteGenerators::new)
          .build();

  static final PassFactory rewriteLogicalAssignmentOperatorsPass =
      PassFactory.builder()
          .setName("rewriteLogicalAssignmentOperatorsPass")
          .setInternalFactory(RewriteLogicalAssignmentOperatorsPass::new)
          .build();

  static final PassFactory rewriteOptionalChainingOperator =
      PassFactory.builder()
          .setName("rewriteOptionalChainingOperator")
          .setInternalFactory(RewriteOptionalChainingOperator::new)
          .build();

  static final PassFactory rewriteNullishCoalesceOperator =
      PassFactory.builder()
          .setName("rewriteNullishCoalesceOperator")
          .setInternalFactory(RewriteNullishCoalesceOperator::new)
          .build();

  static final PassFactory reportUntranspilableFeatures =
      PassFactory.builder()
          .setName("reportUntranspilableFeatures")
          .setInternalFactory(
              (compiler) ->
                  new ReportUntranspilableFeatures(
                      compiler, compiler.getOptions().getOutputFeatureSet()))
          .build();

  /**
   * @param script The SCRIPT node representing a JS file
   * @return If the file has any features not in {@code supportedFeatures}
   */
  static boolean doesScriptHaveUnsupportedFeatures(Node script, FeatureSet supportedFeatures) {
    FeatureSet features = NodeUtil.getFeatureSetOfScript(script);
    return features != null && !supportedFeatures.contains(features);
  }

  /**
   * Process transpilations if the input language needs transpilation from certain features, on any
   * JS file that has features not present in the compiler's output language mode.
   *
   * @param compiler An AbstractCompiler
   * @param combinedRoot The combined root for all JS files.
   * @param featureSet Ignored
   * @param callbacks The callbacks that should be invoked if a file has ES2015 features.
   * @deprecated Please use a regular NodeTraversal object directly, using `shouldTraverse` to skip
   *     SCRIPT node if desired.
   */
  @Deprecated
  static void processTranspile(
      AbstractCompiler compiler,
      Node combinedRoot,
      FeatureSet featureSet,
      NodeTraversal.Callback... callbacks) {
    FeatureSet languageOutFeatures = compiler.getOptions().getOutputFeatureSet();
    for (Node singleRoot = combinedRoot.getFirstChild();
        singleRoot != null;
        singleRoot = singleRoot.getNext()) {

      // Only run the transpilation if this file has features not in the compiler's target output
      // language. For example, if this file is purely ES6 and the output language is ES6, don't
      // run any transpilation passes on it.
      // TODO(lharker): We could save time by being more selective about what files we transpile.
      // e.g. if a file has async functions but not `**`, don't run `**` transpilation on it.
      // Right now we know what features were in a file at parse time, but not what features were
      // added to that file by other transpilation passes.
      if (doesScriptHaveUnsupportedFeatures(singleRoot, languageOutFeatures)) {
        for (NodeTraversal.Callback callback : callbacks) {
          NodeTraversal.traverse(compiler, singleRoot, callback);
        }
      }
    }
  }

  /**
   * Removes the given features from the FEATURE_SET prop of all scripts under root. Also removes
   * from the compiler's featureset.
   */
  static void maybeMarkFeatureAsTranspiledAway(
      AbstractCompiler compiler, Node root, Feature feature) {
    // We don't bother to do this if the compiler has halting errors, which avoids unnecessary
    // warnings from AstValidator warning that the features are still there.
    if (!compiler.hasHaltingErrors()) {
      compiler.markFeatureNotAllowed(feature);
      NodeUtil.removeFeatureFromAllScripts(root, feature, compiler);
    }
  }

  /**
   * Removes the given features from the FEATURE_SET prop of all scripts under root. Also removes
   * from the compiler's featureset.
   */
  static void maybeMarkFeaturesAsTranspiledAway(
      AbstractCompiler compiler,
      Node root,
      Feature transpiledFeature,
      Feature... moreTranspiledFeatures) {
    if (!compiler.hasHaltingErrors()) {
      maybeMarkFeatureAsTranspiledAway(compiler, root, transpiledFeature);
      for (Feature feature : moreTranspiledFeatures) {
        maybeMarkFeatureAsTranspiledAway(compiler, root, feature);
      }
    }
  }

  /**
   * Removes the given features from the FEATURE_SET prop of all scripts under root. Also removes
   * from the compiler's featureset.
   */
  // TODO: b/293467820 - Potentially have a single method that accepts a Collection<Feature>
  static void maybeMarkFeaturesAsTranspiledAway(
      AbstractCompiler compiler, Node root, FeatureSet transpiledFeatures) {
    // We don't bother to do this if the compiler has halting errors, which avoids unnecessary
    // warnings from AstValidator warning that the features are still there.
    if (!compiler.hasHaltingErrors()) {
      for (Feature feature : transpiledFeatures.getFeatures()) {
        maybeMarkFeatureAsTranspiledAway(compiler, root, feature);
      }
    }
  }

  static void postTranspileCheckUnsupportedFeaturesRemoved(AbstractCompiler compiler) {
    FeatureSet outputFeatures = compiler.getOptions().getOutputFeatureSet();
    FeatureSet currentFeatures = compiler.getAllowableFeatures();
    // features modules, importMeta and Dynamic module import may exist in the output even though
    // unsupported
    if (compiler.getOptions().getChunkOutputType() == ChunkOutputType.ES_MODULES) {
      currentFeatures =
          currentFeatures
              .without(Feature.MODULES)
              .without(Feature.IMPORT_META)
              .without(Feature.DYNAMIC_IMPORT);
    }

    if (outputFeatures.getFeatures().isEmpty()) {
      // In some cases (e.g. targets built using `gen_closurized_js`), the outputFeatures is not
      // set. Only when set, confirm that the output featureSet is respected by JSCompiler.
      return;
    }

    if (!outputFeatures.contains(currentFeatures)) {
      // Confirm that the output featureSet is respected by JSCompiler.
      FeatureSet diff = currentFeatures.without(outputFeatures);
      throw new IllegalStateException(
          "Unsupported feature(s) leaked to output code:" + diff.getFeatures());
    }
  }

  /**
   * Returns a pass that just removes features from the source scripts' FeatureSet and the
   * compiler's featureset.
   *
   * <p>Doing this indicates that the AST no longer contains uses of the features, or that they are
   * no longer of concern for some other reason.
   */
  private static PassFactory createFeatureRemovalPass(
      String passName, final Feature featureToRemove, final Feature... moreFeaturesToRemove) {
    return PassFactory.builder()
        .setName(passName)
        .setInternalFactory(
            (compiler) ->
                ((Node externs, Node root) ->
                    maybeMarkFeaturesAsTranspiledAway(
                        compiler, root, featureToRemove, moreFeaturesToRemove)))
        .build();
  }

  /**
   * Returns a pass that just checks that post-transpile only supported features exist in the code.
   */
  private static PassFactory createPostTranspileUnsupportedFeaturesRemovedCheck(String passName) {
    return PassFactory.builder()
        .setName(passName)
        .setInternalFactory(
            (compiler) ->
                ((Node externs, Node root) ->
                    postTranspileCheckUnsupportedFeaturesRemoved(compiler)))
        .build();
  }
}
