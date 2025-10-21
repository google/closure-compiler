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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.Es6RewriteDestructuring.ObjectDestructuringRewriteMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;

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

  /**
   * Adds transpilation passes.
   *
   * <p>Passes added in this method either use {@code TranspilationPasses.processTranspile} or
   * early-exit by checking their feature in the script's featureset. So they get run if the feature
   * they're responsible for removing exists in the script.
   */
  public static void addTranspilationPasses(PassListBuilder passes, CompilerOptions options) {

    // bag of small, local, independent rewritings being done in a single traversal
    passes.maybeAdd(peepholeTranspilationsPasses);

    passes.maybeAdd(createUnifiedFeatureRemovalPass("featureRemovalPasses", options));

    // Es6NormalizeClasses is always run so that future passes can make assumptions about classes.
    // Always does the following:
    // -  Extracts classes defined in expression contexts (e.g. `foo(class {})` and
    //    `class C extends class {} {}`).
    // -  Removes all static initialization (i.e. removes static initialization blocks and moves
    //    static field initializers into a generated static method).
    // -  Provides a single name for classes (i.e. removes the inner class name).
    // -  Moves computed field expressions into named variables.
    // If transpilation is needed for Feature.PUBLIC_CLASS_FIELDS:
    // -  Rewrites public fields as assignments in the constructor.
    // -  Removes all field declarations (public and static).
    passes.maybeAdd(es6NormalizeClasses);

    if (options.needsTranspilationOf(Feature.LOGICAL_ASSIGNMENT)) {
      passes.maybeAdd(rewriteLogicalAssignmentOperatorsPass);
    }

    if (options.needsTranspilationOf(Feature.OPTIONAL_CHAINING)) {
      passes.maybeAdd(rewriteOptionalChainingOperator);
    }

    if (options.needsTranspilationOf(Feature.NULL_COALESCE_OP)) {
      passes.maybeAdd(rewriteNullishCoalesceOperator);
    }

    // NOTE: This needs to be _before_ await and yield are transpiled away.
    if (options.getInstrumentAsyncContext()) {
      passes.maybeAdd(instrumentAsyncContext);
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

    if (options.needsTranspilationOf(Feature.CLASSES)) {
      passes.maybeAdd(es6ConvertSuper);
    }

    if (options.needsTranspilationFrom(
        FeatureSet.BARE_MINIMUM.with(Feature.ARRAY_DESTRUCTURING, Feature.OBJECT_DESTRUCTURING))) {
      passes.maybeAdd(
          getEs6RewriteDestructuring(ObjectDestructuringRewriteMode.REWRITE_ALL_OBJECT_PATTERNS));
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

    if (options.needsTranspilationOf(Feature.GENERATORS)) {
      passes.maybeAdd(rewriteGenerators);
    }
    // This pass must run at the end of all transpiler passes. It validates that only supported
    // features remain in the compiler's featureSet
    passes.maybeAdd(
        TranspilationPasses.createPostTranspileUnsupportedFeaturesRemovedCheck(
            "postTranspileUnsupportedFeaturesRemovedCheck"));
  }

  private static final PassFactory peepholeTranspilationsPasses =
      PassFactory.builder()
          .setName("peepholeTranspilationsPasses")
          .setInternalFactory(
              (compiler) -> {
                List<AbstractPeepholeTranspilation> peepholeTranspilations = new ArrayList<>();
                peepholeTranspilations.add(
                    new ReportUntranspilableFeatures(
                        compiler,
                        compiler.getOptions().getBrowserFeaturesetYearObject(),
                        compiler.getOptions().getOutputFeatureSet()));
                if (compiler.getOptions().needsTranspilationOf(Feature.PRIVATE_CLASS_PROPERTIES)) {
                  peepholeTranspilations.add(new RewritePrivateClassProperties(compiler));
                }
                if (compiler.getOptions().needsTranspilationOf(Feature.OPTIONAL_CATCH_BINDING)) {
                  peepholeTranspilations.add(new RewriteCatchWithNoBinding(compiler));
                }
                if (compiler
                    .getOptions()
                    .needsTranspilationOf(Feature.SHORTHAND_OBJECT_PROPERTIES)) {
                  peepholeTranspilations.add(new Es6NormalizeShorthandProperties(compiler));
                }
                if (compiler.getOptions().needsTranspilationOf(Feature.NEW_TARGET)) {
                  peepholeTranspilations.add(new RewriteNewDotTarget(compiler));
                }
                return PeepholeTranspilationsPass.create(compiler, peepholeTranspilations);
              })
          .build();

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

  static final PassFactory rewriteAsyncFunctions =
      PassFactory.builder()
          .setName("rewriteAsyncFunctions")
          .setInternalFactory(RewriteAsyncFunctions::create)
          .build();

  static final PassFactory rewriteAsyncIteration =
      PassFactory.builder()
          .setName("rewriteAsyncIteration")
          .setInternalFactory(RewriteAsyncIteration::create)
          .build();

  private static final PassFactory rewriteObjectSpread =
      PassFactory.builder()
          .setName("rewriteObjectSpread")
          .setInternalFactory(RewriteObjectSpread::new)
          .build();

  private static final PassFactory rewriteExponentialOperator =
      PassFactory.builder()
          .setName("rewriteExponentialOperator")
          .setInternalFactory(Es7RewriteExponentialOperator::new)
          .build();

  static final PassFactory es6NormalizeClasses =
      PassFactory.builder()
          .setName(PassNames.ES6_NORMALIZE_CLASSES)
          .setInternalFactory(Es6NormalizeClasses::new)
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
                      compiler.getOptions().getIsolatePolyfills(),
                      compiler.getOptions().getInjectPolyfillsNewerThan()))
          .build();

  static final PassFactory instrumentAsyncContext =
      PassFactory.builder()
          .setName("instrumentAsyncContext")
          .setInternalFactory(
              (compiler) ->
                  new InstrumentAsyncContext(
                      compiler,
                      compiler
                          .getOptions()
                          .getOutputFeatureSet()
                          .contains(Feature.ASYNC_FUNCTIONS)))
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

  /**
   * Returns true if the script's featureSet contains any feature from the given featureSet.
   *
   * <p>The script's featureSet gets accurately maintained during transpile. It can be relied upon
   * to check if a feature exists in the AST or not.
   */
  static boolean doesScriptHaveAnyOfTheseFeatures(Node script, FeatureSet featureSet) {
    FeatureSet features = NodeUtil.getFeatureSetOfScript(script);
    return features != null && features.containsAtLeastOneOf(featureSet);
  }

  /**
   * Runs the given transpilation callbacks on every source script if the given {@code
   * featuresToRunFor} are unsupported in the output language and exist in that script.
   *
   * @param compiler An AbstractCompiler
   * @param combinedRoot The combined root for all JS files
   * @param featuresToRunFor features for which these callbacks run
   * @param callbacks The callbacks that should be invoked if a file has output-unsupported
   *     features.
   * @deprecated Please use a regular NodeTraversal object directly, using `shouldTraverse` to skip
   *     SCRIPT node if desired.
   */
  @Deprecated
  static void processTranspile(
      AbstractCompiler compiler,
      Node combinedRoot,
      FeatureSet featuresToRunFor,
      NodeTraversal.Callback... callbacks) {
    FeatureSet languageOutFeatures = compiler.getOptions().getOutputFeatureSet();
    if (languageOutFeatures.contains(featuresToRunFor)) {
      // all featuresToRunFor are supported by languageOut and don't need to get transpiled.
      return;
    }

    for (Node singleRoot = combinedRoot.getFirstChild();
        singleRoot != null;
        singleRoot = singleRoot.getNext()) {
      checkState(singleRoot.isScript());
      // only run the callbacks if any feature from the given featureSet exists in the script
      if (doesScriptHaveAnyOfTheseFeatures(singleRoot, featuresToRunFor)) {
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
      AbstractCompiler compiler, Node root, FeatureSet transpiledFeatures) {
    // We don't bother to do this if the compiler has halting errors, which avoids unnecessary
    // warnings from AstValidator warning that the features are still there.
    if (!compiler.hasHaltingErrors()) {
      // remove the features from the compiler's featureSet and remove the features from every
      // script's featureset
      NodeUtil.removeFeaturesFromAllScripts(root, transpiledFeatures, compiler);
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
      String passName, final FeatureSet featuresToRemove) {
    return PassFactory.builder()
        .setName(passName)
        .setInternalFactory(
            (compiler) ->
                ((Node externs, Node root) ->
                    maybeMarkFeaturesAsTranspiledAway(compiler, root, featuresToRemove)))
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

  /**
   * Create a single pass to mark all transpiled features as removed from the source scripts'
   * FeatureSet and the compiler's featureset.
   *
   * <p>Doing this indicates that the AST no longer contains uses of the features, or that they are
   * no longer of concern for some other reason.
   */
  private static PassFactory createUnifiedFeatureRemovalPass(
      String passName, CompilerOptions options) {

    FeatureSet featuresToMarkRemoved = FeatureSet.BARE_MINIMUM;

    // The compiler doesn't transpile regex features.
    if (options.needsTranspilationOf(Feature.REGEXP_FLAG_D)) {
      featuresToMarkRemoved = featuresToMarkRemoved.with(Feature.REGEXP_FLAG_D);
    }
    if (options.needsTranspilationOf(Feature.BIGINT)) {
      featuresToMarkRemoved = featuresToMarkRemoved.with(Feature.BIGINT);
    }
    if (options.needsTranspilationOf(Feature.NUMERIC_SEPARATOR)) {
      // Numeric separators are flagged as present by the parser,
      // but never actually represented in the AST.
      // The only thing we need to do is mark them as not present in the AST.
      featuresToMarkRemoved = featuresToMarkRemoved.with(Feature.NUMERIC_SEPARATOR);
    }
    if (options.getChunkOutputType() != ChunkOutputType.ES_MODULES) {
      // Default output mode of JSCompiler is a script, unless chunkOutputType is set to
      // `ES_MODULES` where each output chunk is an ES module.
      featuresToMarkRemoved = featuresToMarkRemoved.with(Feature.MODULES);
      // Since import.meta cannot be transpiled, it is passed-through when the output format
      // is a module. Otherwise it must be marked removed.
      featuresToMarkRemoved = featuresToMarkRemoved.with(Feature.IMPORT_META);
      // Dynamic imports are preserved for open source output only when the chunk output type is
      // ES_MODULES
      featuresToMarkRemoved = featuresToMarkRemoved.with(Feature.DYNAMIC_IMPORT);
    }

    if (options.needsTranspilationFrom(
        FeatureSet.BARE_MINIMUM.with(
            Feature.BINARY_LITERALS,
            Feature.OCTAL_LITERALS,
            Feature.REGEXP_FLAG_U,
            Feature.REGEXP_FLAG_Y))) {
      // Binary and octal literals are effectively transpiled by the parser.
      // There's no transpilation we can do for the new regexp flags.
      featuresToMarkRemoved =
          featuresToMarkRemoved.with(
              Feature.BINARY_LITERALS,
              Feature.OCTAL_LITERALS,
              Feature.REGEXP_FLAG_U,
              Feature.REGEXP_FLAG_Y);
    }

    if (options.needsTranspilationFrom(FeatureSet.ES5)) {
      // this means we're transpiling to ES3 output
      featuresToMarkRemoved =
          featuresToMarkRemoved.with(
              // TODO(b/354075108): Stop tracking these 2 features. These don't get transpiled and
              // are not supported in ES3. But they get possibly inlined / renamed by the optimizer.
              Feature.ES3_KEYWORDS_AS_IDENTIFIERS, // does not get transpiled
              Feature.KEYWORDS_AS_PROPERTIES, // does not get transpiled
              // GETTERs and SETTERs get reported in lateConvertEs6ToEs3 for ES3 output. If
              // we're here it means that GETTERs and SETTERs don't exist.
              Feature.GETTER,
              Feature.SETTER);
    }

    // these ES5 features are transpiled away unconditionally regardless of output level.
    featuresToMarkRemoved =
        featuresToMarkRemoved.with(
            Feature.STRING_CONTINUATION, // transpiled away during parsing
            Feature.TRAILING_COMMA // transpiled away during Normalization
            );

    return createFeatureRemovalPass(passName, featuresToMarkRemoved);
  }
}
