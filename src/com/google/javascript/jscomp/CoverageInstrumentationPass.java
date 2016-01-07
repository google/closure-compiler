/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This code implements the instrumentation pass over the AST
 * (returned by JSCompiler).
 * @author praveenk@google.com (Praveen Kumashi)
 *
 */
@GwtIncompatible("FileInstrumentationData")
class CoverageInstrumentationPass implements CompilerPass {

  final AbstractCompiler compiler;
  private Map<String, FileInstrumentationData> instrumentationData;
  private CoverageReach reach;

  private static final String JS_INSTRUMENTATION_EXTERNS_CODE = ""
      + "var JSCompiler_lcov_executedLines;\n"
      + "var JSCompiler_lcov_instrumentedLines;\n"
      + "var JSCompiler_lcov_fileNames;\n";

  public enum CoverageReach {
    ALL,
    CONDITIONAL
  }

  /**
   *
   * @param compiler the compiler which generates the AST.
   */
  public CoverageInstrumentationPass(AbstractCompiler compiler,
      CoverageReach reach) {
    this.compiler = compiler;
    this.reach = reach;
    instrumentationData = new LinkedHashMap<>();
  }

  /**
   * Creates the js code to be added to source. This code declares and
   * initializes the variables required for collection of coverage data.
   */
  private void addHeaderCode(Node script) {
    script.addChildToFront(
        createConditionalVarDecl("JSCompiler_lcov_executedLines", script));
    script.addChildToFront(
        createConditionalVarDecl("JSCompiler_lcov_instrumentedLines", script));
    script.addChildToFront(
        createConditionalVarDecl("JSCompiler_lcov_fileNames", script));
  }

  /**
   * Creates a node of externs code required for the arrays used for
   * instrumentation.
   */
  private Node getInstrumentationExternsNode() {
    Node externsNode = compiler.parseSyntheticCode(
        "ExternsCodeForCoverageInstrumentation",
        JS_INSTRUMENTATION_EXTERNS_CODE);

    return externsNode;
  }

  @Override
  public void process(Node externsNode, Node rootNode) {
    if (rootNode.hasChildren()) {
      NodeTraversal.traverseEs6(compiler, rootNode,
          new CoverageInstrumentationCallback(
              compiler, instrumentationData, reach));

      Node firstScript = rootNode.getFirstChild();
      Preconditions.checkState(firstScript.isScript());
      addHeaderCode(firstScript);
    }

    externsNode.addChildToBack(getInstrumentationExternsNode());
  }

  private static Node createConditionalVarDecl(String name, Node srcref) {
    Node var = IR.var(
        IR.name(name),
        IR.or(
            IR.name(name),
            IR.arraylit()));

    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordSuppressions(ImmutableSet.of("duplicate"));
    var.setJSDocInfo(builder.build());
    return var.useSourceInfoIfMissingFromForTree(srcref);
  }
}
