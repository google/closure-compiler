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
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES2018;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES2020;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES_NEXT;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES_NEXT_IN;

import com.google.javascript.jscomp.Es6RewriteDestructuring.ObjectDestructuringRewriteMode;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import java.util.List;

/**
 * Provides a single place to manage transpilation passes.
 */
public class TranspilationPasses {
  private TranspilationPasses() {}

  public static void addEs6ModulePass(
      List<PassFactory> passes,
      final PreprocessorSymbolTable.CachedInstanceFactory preprocessorTableFactory) {
    passes.add(
        PassFactory.builderForHotSwap()
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
            .setFeatureSet(ES_NEXT_IN)
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

  /** Adds transpilation passes that should run after all checks are done. */
  public static void addPostCheckTranspilationPasses(
      List<PassFactory> passes, CompilerOptions options) {
    // Note that, for features >ES2017 we detect feature by feature rather than by yearly languages
    // in order to handle FeatureSet.BROWSER_2020, which is ES2019 without the new RegExp features.
    if (options.needsTranspilationOf(Feature.NUMERIC_SEPARATOR)) {
      // Numeric separators are flagged as present by the parser,
      // but never actually represented in the AST.
      // The only thing we need to do is mark them as not present in the AST.
      passes.add(
          createFeatureRemovalPass("markNumericSeparatorsRemoved", Feature.NUMERIC_SEPARATOR));
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
    }

    if (options.needsTranspilationFrom(ES2017)) {
      passes.add(removeTrailingCommaFromParamList);
      passes.add(rewriteAsyncFunctions);
    }

    if (options.needsTranspilationFrom(ES2016)) {
      passes.add(rewriteExponentialOperator);
    }

    if (options.needsTranspilationFrom(ES2015)) {
      // TODO(b/73387406): Move passes here as typechecking & other check passes are updated to cope
      // with the features they transpile and as the passes themselves are updated to propagate type
      // information to the transpiled code.
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
    } else if (options.needsTranspilationOf(Feature.OBJECT_PATTERN_REST)) {
      passes.add(es6RenameVariablesInParamLists);
      passes.add(es6SplitVariableDeclarations);
      passes.add(getEs6RewriteDestructuring(ObjectDestructuringRewriteMode.REWRITE_OBJECT_REST));
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
      PassFactory.builderForHotSwap()
          .setName("rewriteAsyncFunctions")
          .setInternalFactory(
              (compiler) ->
                  new RewriteAsyncFunctions.Builder(compiler)
                      // If ES2015 classes will not be transpiled away later,
                      // transpile away property references that use `super` in async functions.
                      // See explanation in RewriteAsyncFunctions.
                      .rewriteSuperPropertyReferencesWithoutSuper(
                          !compiler.getOptions().needsTranspilationFrom(FeatureSet.ES2015))
                      .build())
          .setFeatureSet(ES_NEXT)
          .build();

  private static final PassFactory rewriteAsyncIteration =
      PassFactory.builderForHotSwap()
          .setName("rewriteAsyncIteration")
          .setInternalFactory(
              (compiler) ->
                  new RewriteAsyncIteration.Builder(compiler)
                      // If ES2015 classes will not be transpiled away later,
                      // transpile away property references that use `super` in async iteration.
                      // See explanation in RewriteAsyncIteration.
                      .rewriteSuperPropertyReferencesWithoutSuper(
                          !compiler.getOptions().needsTranspilationFrom(FeatureSet.ES2015))
                      .build())
          .setFeatureSet(ES_NEXT)
          .build();

  private static final PassFactory rewriteObjectSpread =
      PassFactory.builderForHotSwap()
          .setName("rewriteObjectSpread")
          .setInternalFactory(RewriteObjectSpread::new)
          .setFeatureSet(ES_NEXT)
          .build();

  private static final PassFactory rewriteCatchWithNoBinding =
      PassFactory.builderForHotSwap()
          .setName("rewriteCatchWithNoBinding")
          .setInternalFactory(RewriteCatchWithNoBinding::new)
          .setFeatureSet(ES_NEXT)
          .build();

  private static final PassFactory rewriteNewDotTarget =
      PassFactory.builderForHotSwap()
          .setName("rewriteNewDotTarget")
          .setInternalFactory(RewriteNewDotTarget::new)
          .setFeatureSet(ES_NEXT)
          .build();

  private static final PassFactory removeTrailingCommaFromParamList =
      PassFactory.builderForHotSwap()
          .setName("removeTrailingCommaFromParamList")
          .setInternalFactory(RemoveTrailingCommaFromParamList::new)
          .setFeatureSet(ES_NEXT)
          .build();

  private static final PassFactory reportBigIntLiteralTranspilationUnsupported =
      PassFactory.builderForHotSwap()
          .setName("reportBigIntTranspilationUnsupported")
          .setInternalFactory(ReportBigIntLiteralTranspilationUnsupported::new)
          .setFeatureSet(ES2020)
          .build();

  private static final PassFactory rewriteExponentialOperator =
      PassFactory.builderForHotSwap()
          .setName("rewriteExponentialOperator")
          .setInternalFactory(Es7RewriteExponentialOperator::new)
          .setFeatureSet(ES2018)
          .build();

  private static final PassFactory es6NormalizeShorthandProperties =
      PassFactory.builderForHotSwap()
          .setName("es6NormalizeShorthandProperties")
          .setInternalFactory(Es6NormalizeShorthandProperties::new)
          .setFeatureSet(ES2018)
          .build();

  static final PassFactory es6RewriteClassExtends =
      PassFactory.builderForHotSwap()
          .setName(PassNames.ES6_REWRITE_CLASS_EXTENDS)
          .setInternalFactory(Es6RewriteClassExtendsExpressions::new)
          .setFeatureSet(ES2018)
          .build();

  static final PassFactory es6ExtractClasses =
      PassFactory.builderForHotSwap()
          .setName(PassNames.ES6_EXTRACT_CLASSES)
          .setInternalFactory(Es6ExtractClasses::new)
          .setFeatureSet(ES2017)
          .build();

  static final PassFactory es6RewriteClass =
      PassFactory.builderForHotSwap()
          .setName("Es6RewriteClass")
          .setInternalFactory(Es6RewriteClass::new)
          .setFeatureSet(ES2017)
          .build();

  static final PassFactory getEs6RewriteDestructuring(ObjectDestructuringRewriteMode rewriteMode) {
    return PassFactory.builderForHotSwap()
        .setName("Es6RewriteDestructuring")
        .setInternalFactory(
            (compiler) ->
                new Es6RewriteDestructuring.Builder(compiler)
                    .setDestructuringRewriteMode(rewriteMode)
                    .build())
        .setFeatureSet(ES2018)
        .build();
  }

  static final PassFactory es6RenameVariablesInParamLists =
      PassFactory.builderForHotSwap()
          .setName("Es6RenameVariablesInParamLists")
          .setInternalFactory(Es6RenameVariablesInParamLists::new)
          .setFeatureSet(ES2018)
          .build();

  static final PassFactory es6RewriteArrowFunction =
      PassFactory.builderForHotSwap()
          .setName("Es6RewriteArrowFunction")
          .setInternalFactory(Es6RewriteArrowFunction::new)
          .setFeatureSet(ES2017)
          .build();

  static final PassFactory rewritePolyfills =
      PassFactory.builderForHotSwap()
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
      PassFactory.builderForHotSwap()
          .setName("Es6SplitVariableDeclarations")
          .setInternalFactory(Es6SplitVariableDeclarations::new)
          .setFeatureSet(FeatureSet.latest())
          .build();

  static final PassFactory es6ConvertSuper =
      PassFactory.builderForHotSwap()
          .setName("es6ConvertSuper")
          .setInternalFactory(Es6ConvertSuper::new)
          .setFeatureSet(ES2018)
          .build();

  /** Injects runtime library code needed for transpiled ES2015+ code. */
  static final PassFactory injectTranspilationRuntimeLibraries =
      PassFactory.builder()
          .setName("es6InjectRuntimeLibraries")
          .setInternalFactory(InjectTranspilationRuntimeLibraries::new)
          .setFeatureSet(ES_NEXT_IN)
          .build();

  /** Transpiles REST parameters and SPREAD in both array literals and function calls. */
  static final PassFactory es6RewriteRestAndSpread =
      PassFactory.builderForHotSwap()
          .setName("es6RewriteRestAndSpread")
          .setInternalFactory(Es6RewriteRestAndSpread::new)
          .setFeatureSet(FeatureSet.latest())
          .build();

  /**
   * Does the main ES2015 to ES3 conversion. There are a few other passes which run before this one,
   * to convert constructs which are not converted by this pass. This pass can run after NTI
   */
  static final PassFactory lateConvertEs6ToEs3 =
      PassFactory.builderForHotSwap()
          .setName("lateConvertEs6")
          .setInternalFactory(LateEs6ToEs3Converter::new)
          .setFeatureSet(FeatureSet.latest())
          .build();

  static final PassFactory es6ForOf =
      PassFactory.builderForHotSwap()
          .setName("es6ForOf")
          .setInternalFactory(Es6ForOfConverter::new)
          .setFeatureSet(ES2017)
          .build();

  static final PassFactory rewriteBlockScopedFunctionDeclaration =
      PassFactory.builderForHotSwap()
          .setName("Es6RewriteBlockScopedFunctionDeclaration")
          .setInternalFactory(Es6RewriteBlockScopedFunctionDeclaration::new)
          .setFeatureSet(ES2017)
          .build();

  static final PassFactory rewriteBlockScopedDeclaration =
      PassFactory.builderForHotSwap()
          .setName("Es6RewriteBlockScopedDeclaration")
          .setInternalFactory(Es6RewriteBlockScopedDeclaration::new)
          .setFeatureSet(ES2017)
          .build();

  static final PassFactory rewriteGenerators =
      PassFactory.builderForHotSwap()
          .setName("rewriteGenerators")
          .setInternalFactory(Es6RewriteGenerators::new)
          .setFeatureSet(ES2017)
          .build();

  static final PassFactory rewriteOptionalChainingOperator =
      PassFactory.builderForHotSwap()
          .setName("rewriteOptionalChainingOperator")
          .setInternalFactory(RewriteOptionalChainingOperator::new)
          // TODO(b/161166856) change to ES2020 when optional chaining moves there.
          .setFeatureSet(ES_NEXT_IN)
          .build();

  static final PassFactory rewriteNullishCoalesceOperator =
      PassFactory.builderForHotSwap()
          .setName("rewriteNullishCoalesceOperator")
          .setInternalFactory(RewriteNullishCoalesceOperator::new)
          .setFeatureSet(ES2020)
          .build();

  /**
   * @param script The SCRIPT node representing a JS file
   * @return If the file has any features which are part of ES2015 or higher but not part of ES5.
   */
  static boolean isScriptEs6OrHigher(Node script) {
    FeatureSet features = NodeUtil.getFeatureSetOfScript(script);
    return features != null && !FeatureSet.ES5.contains(features);
  }

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
   */
  static void processTranspile(
      AbstractCompiler compiler, Node combinedRoot, FeatureSet featureSet, Callback... callbacks) {
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
        for (Callback callback : callbacks) {
          singleRoot.putBooleanProp(Node.TRANSPILED, true);
          NodeTraversal.traverse(compiler, singleRoot, callback);
        }
      }
    }
  }

  /**
   * Hot-swap ES6+ transpilations if the input language needs transpilation from certain features,
   * on any JS file that has features not present in the compiler's output language mode.
   *
   * @param compiler An AbstractCompiler
   * @param scriptRoot The SCRIPT root for the JS file.
   * @param featureSet Ignored
   * @param callbacks The callbacks that should be invoked if the file has ES2015 features.
   */
  static void hotSwapTranspile(
      AbstractCompiler compiler, Node scriptRoot, FeatureSet featureSet, Callback... callbacks) {
    FeatureSet languageOutFeatures = compiler.getOptions().getOutputFeatureSet();
    if (doesScriptHaveUnsupportedFeatures(scriptRoot, languageOutFeatures)) {
      for (Callback callback : callbacks) {
        scriptRoot.putBooleanProp(Node.TRANSPILED, true);
        NodeTraversal.traverse(compiler, scriptRoot, callback);
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
    return PassFactory.builderForHotSwap()
        .setName(passName)
        .setInternalFactory(
            (compiler) ->
                new HotSwapCompilerPass() {
                  @Override
                  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
                    maybeMarkFeaturesAsTranspiledAway(
                        compiler, featureToRemove, moreFeaturesToRemove);
                  }

                  @Override
                  public void process(Node externs, Node root) {
                    maybeMarkFeaturesAsTranspiledAway(
                        compiler, featureToRemove, moreFeaturesToRemove);
                  }
                })
        .setFeatureSet(FeatureSet.ES_NEXT_IN)
        .build();
  }
}
