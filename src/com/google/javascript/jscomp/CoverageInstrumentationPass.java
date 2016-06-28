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
  private final CoverageReach reach;
  private final InstrumentOption instrumentOption;

  public enum InstrumentOption {
    ALL,   // Instrument to collect both line coverage and branch coverage.
    LINE_ONLY,  // Collect coverage for every executable statement.
    BRANCH_ONLY  // Collect coverage for control-flow branches.
  }

  public static final String JS_INSTRUMENTATION_OBJECT_NAME = "__jscov";

  public enum CoverageReach {
    ALL,         // Instrument all statements.
    CONDITIONAL  // Do not instrument global statements.
  }

  /**
   *
   * @param compiler the compiler which generates the AST.
   */
  public CoverageInstrumentationPass(
      AbstractCompiler compiler, CoverageReach reach, InstrumentOption instrumentOption) {
    this.compiler = compiler;
    this.reach = reach;
    this.instrumentOption = instrumentOption;
    instrumentationData = new LinkedHashMap<>();
  }

  public CoverageInstrumentationPass(AbstractCompiler compiler, CoverageReach reach) {
    this(compiler, reach, InstrumentOption.LINE_ONLY);
  }

  /**
   * Creates the js code to be added to source. This code declares and
   * initializes the variables required for collection of coverage data.
   */
  private void addHeaderCode(Node script) {
    script.addChildToFront(createConditionalObjectDecl(JS_INSTRUMENTATION_OBJECT_NAME, script));
  }

  @Override
  public void process(Node externsNode, Node rootNode) {
    if (rootNode.hasChildren()) {
      NodeTraversal.traverseEs6(
          compiler,
          rootNode,
          new CoverageInstrumentationCallback(
              compiler, instrumentationData, reach));

      Node firstScript = rootNode.getFirstChild();
      Preconditions.checkState(firstScript.isScript());
      addHeaderCode(firstScript);
    }
  }

  private Node createConditionalObjectDecl(String name, Node srcref) {
    String jscovData;
    if (instrumentOption != InstrumentOption.LINE_ONLY) {
      jscovData =
          "{fileNames:[], instrumentedLines: [], executedLines: [],"
              + "branchPrsent:[], branchesInLine: []}";
    } else {
      jscovData = "{fileNames:[], instrumentedLines: [], executedLines: []}";
    }

    String jscovDecl =
        " var " + name + " = window.top.__jscov || " + "(window.top.__jscov = " + jscovData + ");";

    Node script = compiler.parseSyntheticCode(jscovDecl);
    Node var = script.removeFirstChild();
    return var.useSourceInfoIfMissingFromForTree(srcref);
  }
}
