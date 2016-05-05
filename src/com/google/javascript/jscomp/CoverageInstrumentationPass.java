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
  private CoverageReach reach;

  public static final String JS_INSTRUMENTATION_OBJECT_NAME = "__jscov";

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
    script.addChildToFront(createConditionalObjectDecl(JS_INSTRUMENTATION_OBJECT_NAME, script));
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
  }

  private Node createConditionalObjectDecl(String name, Node srcref) {
    String jscovDecl =
        " var "
            + name
            + " = window.top.__jscov || "
            + "(window.top.__jscov = {fileNames:[], instrumentedLines: [], executedLines: []});";
    Node script = compiler.parseSyntheticCode(jscovDecl);
    Node var = script.removeFirstChild();
    return var.useSourceInfoIfMissingFromForTree(srcref);
  }
}
