/*
 * Copyright 2020 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil.AllVarsDeclaredInFunction;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Utility for testing {@link MaybeReachingVariableUse} and {@link MustBeReachingVariableDef} */
final class ReachingUseDefTester {
  private final Compiler compiler;
  private final SyntacticScopeCreator scopeCreator;
  private final LabelFinder labelFinder;
  private Node root;
  private MaybeReachingVariableUse reachingUse;
  private MustBeReachingVariableDef reachingDef;

  private ReachingUseDefTester(
      Compiler compiler, SyntacticScopeCreator scopeCreator, LabelFinder labelFinder) {
    this.compiler = compiler;
    this.scopeCreator = scopeCreator;
    this.labelFinder = labelFinder;
  }

  static ReachingUseDefTester create() {
    Compiler compiler = createCompiler();
    return new ReachingUseDefTester(
        compiler, new SyntacticScopeCreator(compiler), new LabelFinder());
  }

  private static Compiler createCompiler() {
    Compiler compiler = new Compiler();
    compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED);
    CompilerOptions options = new CompilerOptions();
    options.setCodingConvention(new GoogleCodingConvention());
    options.setLanguage(LanguageMode.UNSUPPORTED);
    compiler.initOptions(options);
    return compiler;
  }

  /**
   * Runs `MaybeReachingVariableUse` pass to compute and store the may-be-reaching uses for all
   * definitions of each variable in the test source.
   */
  void computeReachingUses(String src, boolean async) {
    Node script = parseScript(src, async);
    root = script.getFirstChild();
    Scope funcBlockScope = computeFunctionBlockScope(script, root);
    ControlFlowGraph<Node> cfg = computeCfg(root);
    HashSet<Var> escaped = new HashSet<>();

    AllVarsDeclaredInFunction allVarsDeclaredInFunction =
        NodeUtil.getAllVarsDeclaredInFunction(compiler, scopeCreator, funcBlockScope.getParent());
    Map<String, Var> allVarsInFn = allVarsDeclaredInFunction.getAllVariables();
    DataFlowAnalysis.computeEscaped(
        funcBlockScope.getParent(), escaped, compiler, scopeCreator, allVarsInFn);
    reachingUse = new MaybeReachingVariableUse(cfg, escaped, allVarsInFn);
    reachingUse.analyze();
  }

  /**
   * Runs `MustBeReachingVariableDef` pass to compute and store the must-be-reaching definition for
   * all uses of each variable in the test source.
   */
  void computeReachingDef(String src) {
    Node script = parseScript(src, /*async=*/ false);
    root = script.getFirstChild();
    Scope funcBlockScope = computeFunctionBlockScope(script, root);
    ControlFlowGraph<Node> cfg = computeCfg(root);
    HashSet<Var> escaped = new HashSet<>();

    AllVarsDeclaredInFunction allVarsDeclaredInFunction =
        NodeUtil.getAllVarsDeclaredInFunction(compiler, scopeCreator, funcBlockScope.getParent());
    Map<String, Var> allVarsInFn = allVarsDeclaredInFunction.getAllVariables();
    DataFlowAnalysis.computeEscaped(
        funcBlockScope.getParent(), escaped, compiler, scopeCreator, allVarsInFn);
    reachingDef = new MustBeReachingVariableDef(cfg, compiler, escaped, allVarsInFn);
    reachingDef.analyze();
  }

  private Node parseScript(String src, boolean async) {
    src = (async ? "async " : "") + "function _FUNCTION(param1, param2){" + src + "}";
    Node script = compiler.parseTestCode(src);
    assertThat(compiler.getErrors()).isEmpty();
    return script;
  }

  private Scope computeFunctionBlockScope(Node script, Node fn) {
    Node functionBlock = fn.getLastChild();
    Scope globalScope = scopeCreator.createScope(script, null);
    Scope functionScope = scopeCreator.createScope(root, globalScope);
    return scopeCreator.createScope(functionBlock, functionScope);
  }

  private ControlFlowGraph<Node> computeCfg(Node fn) {
    return ControlFlowAnalysis.builder()
        .setCompiler(compiler)
        .setCfgRoot(fn)
        .setIncludeEdgeAnnotations(true)
        .computeCfg();
  }

  /**
   * Returns may-be-reaching uses of definition of variable `x` on the node extracted at label `D:`.
   */
  ImmutableSet<Node> getComputedUses() {
    return ImmutableSet.copyOf(reachingUse.getUses("x", labelFinder.extractedDef));
  }

  /**
   * Returns must-be-reaching definition of variable `x` on the use node extracted at label `U:`.
   */
  Node getComputedDef() {
    return reachingDef.getDefNode("x", labelFinder.extractedUses.get(0));
  }

  // Run `LabelFinder` to find the `D:` and `U:` labels and save the def and uses of `x`
  void extractDefAndUsesFromInputLabels() {
    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(labelFinder)
        .setScopeCreator(scopeCreator)
        .traverse(root);
    assertWithMessage("Code should have an instruction labeled D")
        .that(labelFinder.extractedDef)
        .isNotNull();
    assertWithMessage("Code should have an instruction label starting withing U")
        .that(labelFinder.extractedUses.isEmpty())
        .isFalse();
  }

  /** Finds the D: and U: label and store which node they point to. */
  private static class LabelFinder extends AbstractPostOrderCallback {
    // Def and uses extracted from `D:` and `U:` labels respectively
    private Node extractedDef;
    private final List<Node> extractedUses;

    LabelFinder() {
      extractedUses = new ArrayList<>();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isLabel()) {
        if (n.getFirstChild().getString().equals("D")) {
          assertWithMessage("Multiple D: labels in test src").that(extractedDef).isNull();
          extractedDef = n.getLastChild();
        } else if (n.getFirstChild().getString().startsWith("U")) {
          extractedUses.add(n.getLastChild());
        }
      }
    }
  }

  Collection<Node> getExtractedUses() {
    return labelFinder.extractedUses;
  }

  Node getExtractedDef() {
    return labelFinder.extractedDef;
  }
}
