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

import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES2015;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES2016;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES2017;

import com.google.javascript.jscomp.Es6RewriteDestructuring.ObjectDestructuringRewriteMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import java.util.List;

/** Provides a single place to manage transpilation passes. */
public class TranspilationPasses {
  private TranspilationPasses() {}

  public static void addEs6ModulePass(
      List<PassFactory> passes,
      final PreprocessorSymbolTable.CachedInstanceFactory preprocessorTableFactory) {
    passes.add(
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
                      compiler.getTopScope());
                })
            .setFeatureSetForChecks()
            .build());
  }

  public static void addTranspilationRuntimeLibraries(
      List<PassFactory> passes, CompilerOptions options) {
    // Inject runtime libraries needed for the transpilation we will have to do. Should run before
    // typechecking.
    passes.add(injectTranspilationRuntimeLibraries);
  }

  public static void addEs6ModuleToCjsPass(List<PassFactory> passes) {
    passes.add(es6RewriteModuleToCjs);
  }

  public static void addEs6RewriteImportPathPass(List<PassFactory> passes) {
    passes.add(es6RelativizeImportPaths);
  }

  /** Adds transpilation passes that should run at the beginning of the optimization phase */
  public static void addEarlyOptimizationTranspilationPasses(
      List<PassFactory> passes, CompilerOptions options) {

    // Note that, for features >ES2017 we detect feature by feature rather than by yearly languages
    // in order to handle FeatureSet.BROWSER_2020, which is ES2019 without the new RegExp features.
    // However, RegExp features are not transpiled, and this does not imply that we allow arbitrary
    // selection of features to transpile.  They still must be done in chronological order based on.
    // This greatly simplifies testing and the requirements for the transpilation passes.

    if (options.needsTranspilationOf(Feature.PUBLIC_CLASS_FIELDS)) {
      passes.add(rewriteClassFields);
    }

    if (options.needsTranspilationOf(Feature.NUMERIC_SEPARATOR)) {
      // Numeric separators are flagged as present by the parser,
      // but never actually represented in the AST.
      // The only thing we need to do is mark them as not present in the AST.
      passes.add(
          createFeatureRemovalPass("markNumericSeparatorsRemoved", Feature.NUMERIC_SEPARATOR));
    }

    if (options.needsTranspilationOf(Feature.LOGICAL_ASSIGNMENT)) {
      passes.add(rewriteLogicalAssignmentOperatorsPass);
    }

    if (options.needsTranspilationOf(Feature.OPTIONAL_CHAINING)) {
      passes.add(rewriteOptionalChainingOperator);
    }

    if (options.needsTranspilationOf(Feature.BIGINT)) {
      passes.add(reportBigIntLiteralTranspilationUnsupported);
    }

    if (options.needsTranspilationOf(Feature.NULL_COALESCE_OP)) {
      passes.add(rewriteNullishCoalesceOperator);
    }

    if (options.needsTranspilationOf(Feature.OPTIONAL_CATCH_BINDING)) {
      passes.add(rewriteCatchWithNoBinding);
    }

    if (options.needsTranspilationOf(Feature.FOR_AWAIT_OF)
        || options.needsTranspilationOf(Feature.ASYNC_GENERATORS)) {
      passes.add(rewriteAsyncIteration);
    }

    if (options.needsTranspilationOf(Feature.OBJECT_LITERALS_WITH_SPREAD)
        || options.needsTranspilationOf(Feature.OBJECT_PATTERN_REST)) {
      passes.add(rewriteObjectSpread);
      if (!options.needsTranspilationFrom(ES2015)
          && options.needsTranspilationOf(Feature.OBJECT_PATTERN_REST)) {
        // We only need to transpile away object destructuring that uses `...`, rather than
        // all destructuring.
        // For this to work correctly for object destructuring in parameter lists and variable
        // declarations, we need to normalize them a bit first.
        passes.add(es6RenameVariablesInParamLists);
        passes.add(es6SplitVariableDeclarations);
        passes.add(getEs6RewriteDestructuring(ObjectDestructuringRewriteMode.REWRITE_OBJECT_REST));
      }
    }

    if (options.needsTranspilationFrom(ES2017)) {
      passes.add(removeTrailingCommaFromParamList);
      passes.add(rewriteAsyncFunctions);
    }

    if (options.needsTranspilationFrom(ES2016)) {
      passes.add(rewriteExponentialOperator);
    }

    if (options.needsTranspilationFrom(ES2015)) {
      // Binary and octal literals are effectively transpiled by the parser.
      // There's no transpilation we can do for the new regexp flags.
      passes.add(
          createFeatureRemovalPass(
              "markEs6FeaturesNotRequiringTranspilationAsRemoved",
              Feature.BINARY_LITERALS,
              Feature.OCTAL_LITERALS,
              Feature.REGEXP_FLAG_U,
              Feature.REGEXP_FLAG_Y));

      passes.add(es6NormalizeShorthandProperties);
      passes.add(es6RewriteClassExtends);
      passes.add(es6ConvertSuper);
      passes.add(es6RenameVariablesInParamLists);
      passes.add(es6SplitVariableDeclarations);
      passes.add(
          getEs6RewriteDestructuring(ObjectDestructuringRewriteMode.REWRITE_ALL_OBJECT_PATTERNS));
      passes.add(rewriteNewDotTarget);
      passes.add(es6RewriteArrowFunction);
      passes.add(es6ExtractClasses);
      passes.add(es6RewriteClass);
      passes.add(es6RewriteRestAndSpread);
      passes.add(lateConvertEs6ToEs3);
      passes.add(es6ForOf);
      passes.add(rewriteBlockScopedFunctionDeclaration);
      passes.add(rewriteBlockScopedDeclaration);
      passes.add(rewriteGenerators);
    }
  }

  /** Adds the pass to inject ES2015 polyfills, which goes after the late ES2015 passes. */
  public static void addRewritePolyfillPass(List<PassFactory> passes) {
    passes.add(rewritePolyfills);
  }

  /** Rewrites ES6 modules */
  private static final PassFactory es6RewriteModuleToCjs =
      PassFactory.builder()
          .setName("es6RewriteModuleToCjs")
          .setInternalFactory(Es6RewriteModulesToCommonJsModules::new)
          .setFeatureSet(FeatureSet.latest())
          .build();

  /** Rewrites ES6 modules import paths to be browser compliant */
  private static final PassFactory es6RelativizeImportPaths =
      PassFactory.builder()
          .setName("es6RelativizeImportPaths")
          .setInternalFactory(Es6RelativizeImportPaths::new)
          .setFeatureSet(FeatureSet.latest())
          .build();

  private static final PassFactory rewriteAsyncFunctions =
      PassFactory.builder()
          .setName("rewriteAsyncFunctions")
          .setInternalFactory(RewriteAsyncFunctions::create)
          .setFeatureSetForChecks()
          .build();

  private static final PassFactory rewriteAsyncIteration =
      PassFactory.builder()
          .setName("rewriteAsyncIteration")
          .setInternalFactory(RewriteAsyncIteration::create)
          .setFeatureSetForChecks()
          .build();

  private static final PassFactory rewriteObjectSpread =
      PassFactory.builder()
          .setName("rewriteObjectSpread")
          .setInternalFactory(RewriteObjectSpread::new)
          .setFeatureSetForChecks()
          .build();

  private static final PassFactory rewriteCatchWithNoBinding =
      PassFactory.builder()
          .setName("rewriteCatchWithNoBinding")
          .setInternalFactory(RewriteCatchWithNoBinding::new)
          .setFeatureSetForChecks()
          .build();

  private static final PassFactory rewriteNewDotTarget =
      PassFactory.builder()
          .setName("rewriteNewDotTarget")
          .setInternalFactory(RewriteNewDotTarget::new)
          .setFeatureSetForChecks()
          .build();

  private static final PassFactory removeTrailingCommaFromParamList =
      PassFactory.builder()
          .setName("removeTrailingCommaFromParamList")
          .setInternalFactory(RemoveTrailingCommaFromParamList::new)
          .setFeatureSetForChecks()
          .build();

  private static final PassFactory reportBigIntLiteralTranspilationUnsupported =
      PassFactory.builder()
          .setName("reportBigIntTranspilationUnsupported")
          .setInternalFactory(ReportBigIntLiteralTranspilationUnsupported::new)
          .setFeatureSetForChecks()
          .build();

  private static final PassFactory rewriteExponentialOperator =
      PassFactory.builder()
          .setName("rewriteExponentialOperator")
          .setInternalFactory(Es7RewriteExponentialOperator::new)
          .setFeatureSetForChecks()
          .build();

  private static final PassFactory es6NormalizeShorthandProperties =
      PassFactory.builder()
          .setName("es6NormalizeShorthandProperties")
          .setInternalFactory(Es6NormalizeShorthandProperties::new)
          .setFeatureSetForChecks()
          .build();

  static final PassFactory es6RewriteClassExtends =
      PassFactory.builder()
          .setName(PassNames.ES6_REWRITE_CLASS_EXTENDS)
          .setInternalFactory(Es6RewriteClassExtendsExpressions::new)
          .setFeatureSetForChecks()
          .build();

  static final PassFactory es6ExtractClasses =
      PassFactory.builder()
          .setName(PassNames.ES6_EXTRACT_CLASSES)
          .setInternalFactory(Es6ExtractClasses::new)
          .setFeatureSetForChecks()
          .build();

  static final PassFactory rewriteClassFields =
      PassFactory.builder()
          .setName("RewriteClassFields")
          .setInternalFactory(RewriteClassFields::new)
          .setFeatureSetForChecks()
          .build();

  static final PassFactory es6RewriteClass =
      PassFactory.builder()
          .setName("Es6RewriteClass")
          .setInternalFactory(Es6RewriteClass::new)
          .setFeatureSetForChecks()
          .build();

  static final PassFactory getEs6RewriteDestructuring(ObjectDestructuringRewriteMode rewriteMode) {
    return PassFactory.builder()
        .setName("Es6RewriteDestructuring")
        .setInternalFactory(
            (compiler) ->
                new Es6RewriteDestructuring.Builder(compiler)
                    .setDestructuringRewriteMode(rewriteMode)
                    .build())
        .setFeatureSetForChecks()
        .build();
  }

  static final PassFactory es6RenameVariablesInParamLists =
      PassFactory.builder()
          .setName("Es6RenameVariablesInParamLists")
          .setInternalFactory(Es6RenameVariablesInParamLists::new)
          .setFeatureSetForChecks()
          .build();

  static final PassFactory es6RewriteArrowFunction =
      PassFactory.builder()
          .setName("Es6RewriteArrowFunction")
          .setInternalFactory(Es6RewriteArrowFunction::new)
          .setFeatureSetForChecks()
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
          .setFeatureSet(FeatureSet.latest())
          .build();

  static final PassFactory es6SplitVariableDeclarations =
      PassFactory.builder()
          .setName("Es6SplitVariableDeclarations")
          .setInternalFactory(Es6SplitVariableDeclarations::new)
          .setFeatureSet(FeatureSet.latest())
          .build();

  static final PassFactory es6ConvertSuper =
      PassFactory.builder()
          .setName("es6ConvertSuper")
          .setInternalFactory(Es6ConvertSuper::new)
          .setFeatureSetForChecks()
          .build();

  /** Injects runtime library code needed for transpiled ES2015+ code. */
  static final PassFactory injectTranspilationRuntimeLibraries =
      PassFactory.builder()
          .setName("es6InjectRuntimeLibraries")
          .setInternalFactory(InjectTranspilationRuntimeLibraries::new)
          .setFeatureSetForChecks()
          .build();

  /** Transpiles REST parameters and SPREAD in both array literals and function calls. */
  static final PassFactory es6RewriteRestAndSpread =
      PassFactory.builder()
          .setName("es6RewriteRestAndSpread")
          .setInternalFactory(Es6RewriteRestAndSpread::new)
          .setFeatureSet(FeatureSet.latest())
          .build();

  /**
   * Does the main ES2015 to ES3 conversion. There are a few other passes which run before this one,
   * to convert constructs which are not converted by this pass.
   */
  static final PassFactory lateConvertEs6ToEs3 =
      PassFactory.builder()
          .setName("lateConvertEs6")
          .setInternalFactory(LateEs6ToEs3Converter::new)
          .setFeatureSet(FeatureSet.latest())
          .build();

  static final PassFactory es6ForOf =
      PassFactory.builder()
          .setName("es6ForOf")
          .setInternalFactory(Es6ForOfConverter::new)
          .setFeatureSetForChecks()
          .build();

  static final PassFactory rewriteBlockScopedFunctionDeclaration =
      PassFactory.builder()
          .setName("Es6RewriteBlockScopedFunctionDeclaration")
          .setInternalFactory(Es6RewriteBlockScopedFunctionDeclaration::new)
          .setFeatureSetForChecks()
          .build();

  static final PassFactory rewriteBlockScopedDeclaration =
      PassFactory.builder()
          .setName("Es6RewriteBlockScopedDeclaration")
          .setInternalFactory(Es6RewriteBlockScopedDeclaration::new)
          .setFeatureSetForChecks()
          .build();

  static final PassFactory rewriteGenerators =
      PassFactory.builder()
          .setName("rewriteGenerators")
          .setInternalFactory(Es6RewriteGenerators::new)
          .setFeatureSetForChecks()
          .build();

  static final PassFactory rewriteLogicalAssignmentOperatorsPass =
      PassFactory.builder()
          .setName("rewriteLogicalAssignmentOperatorsPass")
          .setInternalFactory(RewriteLogicalAssignmentOperatorsPass::new)
          .setFeatureSetForChecks()
          .build();

  static final PassFactory rewriteOptionalChainingOperator =
      PassFactory.builder()
          .setName("rewriteOptionalChainingOperator")
          .setInternalFactory(RewriteOptionalChainingOperator::new)
          .setFeatureSetForChecks()
          .build();

  static final PassFactory rewriteNullishCoalesceOperator =
      PassFactory.builder()
          .setName("rewriteNullishCoalesceOperator")
          .setInternalFactory(RewriteNullishCoalesceOperator::new)
          .setFeatureSetForChecks()
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
          singleRoot.putBooleanProp(Node.TRANSPILED, true);
          NodeTraversal.traverse(compiler, singleRoot, callback);
        }
      }
    }
  }

  static void maybeMarkFeaturesAsTranspiledAway(
      AbstractCompiler compiler, FeatureSet transpiledFeatures) {
    // We don't bother to do this if the compiler has halting errors, which avoids unnecessary
    // warnings from AstValidator warning that the features are still there.
    if (!compiler.hasHaltingErrors()) {
      compiler.setFeatureSet(compiler.getFeatureSet().without(transpiledFeatures));
    }
  }

  static void maybeMarkFeaturesAsTranspiledAway(
      AbstractCompiler compiler, Feature transpiledFeature, Feature... moreTranspiledFeatures) {
    if (!compiler.hasHaltingErrors()) {
      compiler.setFeatureSet(
          compiler.getFeatureSet().without(transpiledFeature, moreTranspiledFeatures));
    }
  }

  /**
   * Returns a pass that just removes features from the AST FeatureSet.
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
                        compiler, featureToRemove, moreFeaturesToRemove)))
        .setFeatureSetForChecks()
        .build();
  }
}
