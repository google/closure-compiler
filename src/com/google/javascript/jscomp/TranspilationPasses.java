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

import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES8;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES8_MODULES;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES_NEXT;

import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.PassFactory.HotSwapPassFactory;
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
        new HotSwapPassFactory("es6RewriteModule") {
          @Override
          protected HotSwapCompilerPass create(AbstractCompiler compiler) {
            preprocessorTableFactory.maybeInitialize(compiler);
            return new Es6RewriteModules(compiler, preprocessorTableFactory.getInstanceOrNull());
          }

          @Override
          protected FeatureSet featureSet() {
            return ES_NEXT;
          }
        });
  }

  public static void addEs6ModuleToCjsPass(List<PassFactory> passes) {
    passes.add(es6RewriteModuleToCjs);
  }

  public static void addEs2018Passes(List<PassFactory> passes) {
    passes.add(rewriteObjRestSpread);
  }

  public static void addEs2017Passes(List<PassFactory> passes) {
    // Trailing commas in parameter lists are flagged as present by the parser,
    // but never actually represented in the AST.
    // The only thing we need to do is mark them as not present in the AST.
    passes.add(
        createFeatureRemovalPass(
            "markTrailingCommasInParameterListsRemoved", Feature.TRAILING_COMMA_IN_PARAM_LIST));
    passes.add(rewriteAsyncFunctions);
  }

  public static void addEs2016Passes(List<PassFactory> passes) {
    passes.add(convertEs7ToEs6);
  }

  public static void addEs6PreTypecheckPasses(List<PassFactory> passes) {
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
    passes.add(es6ConvertSuper);
    passes.add(es6RenameVariablesInParamLists);
    passes.add(es6SplitVariableDeclarations);
    passes.add(es6RewriteDestructuring);
    passes.add(es6RewriteArrowFunction);
    passes.add(es6ExtractClasses);
    passes.add(es6RewriteClass);
    passes.add(earlyConvertEs6ToEs3);
    passes.add(lateConvertEs6ToEs3);
    passes.add(es6ForOf);
    passes.add(rewriteBlockScopedFunctionDeclaration);
    passes.add(rewriteBlockScopedDeclaration);
    passes.add(rewriteGenerators);
  }

  /**
   * Adds all the ES6 transpilation passes which must run before NTI, because it doesn't
   * understand the features that they transpile.
   *
   * TODO(b/72551201): Remove this method when NTI is removed.
   */
  public static void addEs6PassesBeforeNTI(List<PassFactory> passes) {
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
    passes.add(es6ConvertSuper);
    passes.add(es6RenameVariablesInParamLists);
    passes.add(es6SplitVariableDeclarations);
    passes.add(es6RewriteDestructuring);
    passes.add(es6RewriteArrowFunction);
    passes.add(es6ExtractClasses);
    passes.add(es6RewriteClass);
    passes.add(earlyConvertEs6ToEs3);
    passes.add(rewriteBlockScopedFunctionDeclaration);
    passes.add(rewriteBlockScopedDeclaration);
  }

  /**
   * Adds all transpilation passes that can run after NTI.
   *
   * TODO(b/72551201): Remove this method when NTI is removed.
   */
  public static void addEs6PassesAfterNTI(List<PassFactory> passes) {
    passes.add(lateConvertEs6ToEs3);
    passes.add(es6ForOf);
    passes.add(rewriteGenerators);
  }

  /**
   * Adds the pass to inject ES6 polyfills, which goes after the late ES6 passes.
   */
  public static void addRewritePolyfillPass(List<PassFactory> passes) {
    passes.add(rewritePolyfills);
  }

  /** Rewrites ES6 modules */
  private static final PassFactory es6RewriteModuleToCjs =
      new PassFactory("es6RewriteModuleToCjs", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new Es6RewriteModulesToCommonJsModules(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  private static final PassFactory rewriteAsyncFunctions =
      new HotSwapPassFactory("rewriteAsyncFunctions") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new RewriteAsyncFunctions(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  private static final PassFactory rewriteObjRestSpread =
      new HotSwapPassFactory("rewriteObjRestSpread") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new EsNextToEs8Converter(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  private static final PassFactory convertEs7ToEs6 =
      new HotSwapPassFactory("convertEs7ToEs6") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new Es7ToEs6Converter(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  private static final HotSwapPassFactory es6NormalizeShorthandProperties =
      new HotSwapPassFactory("es6NormalizeShorthandProperties") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new Es6NormalizeShorthandProperties(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  static final HotSwapPassFactory es6ExtractClasses =
      new HotSwapPassFactory(PassNames.ES6_EXTRACT_CLASSES) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new Es6ExtractClasses(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  static final HotSwapPassFactory es6RewriteClass =
      new HotSwapPassFactory("Es6RewriteClass") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new Es6RewriteClass(compiler, true);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  static final HotSwapPassFactory es6RewriteDestructuring =
      new HotSwapPassFactory("Es6RewriteDestructuring") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new Es6RewriteDestructuring(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  static final HotSwapPassFactory es6RenameVariablesInParamLists =
      new HotSwapPassFactory("Es6RenameVariablesInParamLists") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new Es6RenameVariablesInParamLists(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  static final HotSwapPassFactory es6RewriteArrowFunction =
      new HotSwapPassFactory("Es6RewriteArrowFunction") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new Es6RewriteArrowFunction(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  static final HotSwapPassFactory rewritePolyfills =
      new HotSwapPassFactory("RewritePolyfills") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new RewritePolyfills(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  static final HotSwapPassFactory es6SplitVariableDeclarations =
      new HotSwapPassFactory("Es6SplitVariableDeclarations") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new Es6SplitVariableDeclarations(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  static final HotSwapPassFactory es6ConvertSuperConstructorCalls =
      new HotSwapPassFactory("es6ConvertSuperConstructorCalls") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new Es6ConvertSuperConstructorCalls(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  static final HotSwapPassFactory es6ConvertSuper =
      new HotSwapPassFactory("es6ConvertSuper") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new Es6ConvertSuper(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
  };

  /**
   * Does ES6 to ES3 conversion of Rest, Spread and Symbol.
   * There are a few other passes which run before or after this one,
   * to convert constructs which are not converted by this pass.
   */
  static final HotSwapPassFactory earlyConvertEs6ToEs3 =
      new HotSwapPassFactory("earlyConvertEs6") {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new EarlyEs6ToEs3Converter(compiler);
    }

    @Override
    protected FeatureSet featureSet() {
      return ES8;
    }
  };

  /**
   * Does the main ES6 to ES3 conversion. There are a few other passes which run before this one, to
   * convert constructs which are not converted by this pass. This pass can run after NTI
   */
  static final HotSwapPassFactory lateConvertEs6ToEs3 =
      new HotSwapPassFactory("lateConvertEs6") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new LateEs6ToEs3Converter(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  static final HotSwapPassFactory es6ForOf =
      new HotSwapPassFactory("es6ForOf") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new Es6ForOfConverter(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  static final HotSwapPassFactory rewriteBlockScopedFunctionDeclaration =
      new HotSwapPassFactory("Es6RewriteBlockScopedFunctionDeclaration") {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6RewriteBlockScopedFunctionDeclaration(compiler);
    }

    @Override
    protected FeatureSet featureSet() {
      return ES8;
    }
  };

  static final HotSwapPassFactory rewriteBlockScopedDeclaration =
      new HotSwapPassFactory("Es6RewriteBlockScopedDeclaration") {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6RewriteBlockScopedDeclaration(compiler);
    }

    @Override
    protected FeatureSet featureSet() {
      return ES8;
    }
  };

  static final HotSwapPassFactory rewriteGenerators =
      new HotSwapPassFactory("rewriteGenerators") {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6RewriteGenerators(compiler);
    }

    @Override
    protected FeatureSet featureSet() {
      return ES8;
    }
  };

  /**
   * @param script The SCRIPT node representing a JS file
   * @return If the file has any features which are part of ES6 or higher but not part of ES5.
   */
  static boolean isScriptEs6OrHigher(Node script) {
    FeatureSet features = (FeatureSet) script.getProp(Node.FEATURE_SET);
    return features != null && !FeatureSet.ES5.contains(features);
  }

  /**
   * Process transpilations if the input language needs transpilation from certain features, on any
   * JS file that has ES6 features.
   *
   * @param compiler An AbstractCompiler
   * @param combinedRoot The combined root for all JS files.
   * @param featureSet The features which this pass helps transpile.
   * @param callbacks The callbacks that should be invoked if a file has ES6 features.
   */
  static void processTranspile(
      AbstractCompiler compiler, Node combinedRoot, FeatureSet featureSet, Callback... callbacks) {
    if (compiler.getOptions().needsTranspilationFrom(featureSet)) {
      for (Node singleRoot : combinedRoot.children()) {
        // TODO(lharker): Only run callbacks if the script has features from the given featureSet,
        // instead of whenever the script has any ES6+ features. We can't do this until ensuring
        // that all passes correctly update the script's associated FeatureSet.
        // The same applies in hotSwapTranspile, hotSwapCheck, and processCheck.
        if (isScriptEs6OrHigher(singleRoot)) {
          for (Callback callback : callbacks) {
            singleRoot.putBooleanProp(Node.TRANSPILED, true);
            NodeTraversal.traverseEs6(compiler, singleRoot, callback);
          }
        }
      }
    }
  }

  /**
   * Hot-swap ES6+ transpilations if the input language needs transpilation from certain features,
   * on any JS file that has ES6 features.
   *
   * @param compiler An AbstractCompiler
   * @param scriptRoot The SCRIPT root for the JS file.
   * @param featureSet The features which this pass helps transpile.
   * @param callbacks The callbacks that should be invoked if the file has ES6 features.
   */
  static void hotSwapTranspile(
      AbstractCompiler compiler, Node scriptRoot, FeatureSet featureSet, Callback... callbacks) {
    if (compiler.getOptions().needsTranspilationFrom(featureSet)) {
      if (isScriptEs6OrHigher(scriptRoot)) {
        for (Callback callback : callbacks) {
          scriptRoot.putBooleanProp(Node.TRANSPILED, true);
          NodeTraversal.traverseEs6(compiler, scriptRoot, callback);
        }
      }
    }
  }

  /**
   * Adds transpilation passes that should run after type checking is done, but before the other
   * checks.
   */
  public static void addEs6PostTypecheckPasses(List<PassFactory> passes) {
    // TODO(b/73387406): Move passes here as typecheck passes are updated to cope with the features
    // they transpile and as the passes themselves are updated to propagate type information to the
    // transpiled code.
  }

  /** Adds transpilation passes that should run after all checks are done. */
  public static void addEs6PostCheckPasses(List<PassFactory> passes) {
    passes.add(es6ConvertSuperConstructorCalls);
  }

  static void markFeaturesAsTranspiledAway(
      AbstractCompiler compiler, FeatureSet transpiledFeatures) {
    compiler.setFeatureSet(compiler.getFeatureSet().without(transpiledFeatures));
  }

  static void markFeaturesAsTranspiledAway(
      AbstractCompiler compiler, Feature transpiledFeature, Feature... moreTranspiledFeatures) {
    compiler.setFeatureSet(
        compiler.getFeatureSet().without(transpiledFeature, moreTranspiledFeatures));
  }

  /**
   * Returns a pass that just removes features from the AST FeatureSet.
   *
   * <p>Doing this indicates that the AST no longer contains uses of the features, or that they are
   * no longer of concern for some other reason.
   */
  private static HotSwapPassFactory createFeatureRemovalPass(
      String passName, final Feature featureToRemove, final Feature... moreFeaturesToRemove) {
    return new HotSwapPassFactory(passName) {
      @Override
      protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
        return new HotSwapCompilerPass() {
          @Override
          public void hotSwapScript(Node scriptRoot, Node originalRoot) {
            markFeaturesAsTranspiledAway(compiler, featureToRemove, moreFeaturesToRemove);
          }

          @Override
          public void process(Node externs, Node root) {
            markFeaturesAsTranspiledAway(compiler, featureToRemove, moreFeaturesToRemove);
          }
        };
      }

      @Override
      protected FeatureSet featureSet() {
        return FeatureSet.latest();
      }
    };
  }
}
