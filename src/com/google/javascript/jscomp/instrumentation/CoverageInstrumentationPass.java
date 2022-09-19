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

package com.google.javascript.jscomp.instrumentation;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.errorprone.annotations.InlineMe;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerOptions.InstrumentOption;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.VariableMap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;
import java.util.Map;

/** This code implements the instrumentation pass over the AST (returned by JSCompiler). */
@GwtIncompatible("FileInstrumentationData")
public class CoverageInstrumentationPass implements CompilerPass {

  final AbstractCompiler compiler;
  private final Map<String, FileInstrumentationData> instrumentationData;
  private final CoverageReach reach;
  private final InstrumentOption instrumentOption;

  private final String productionInstrumentationArrayName;

  public static final String JS_INSTRUMENTATION_OBJECT_NAME = "__jscov";

  /** Configures which statements in the AST should be instrumented */
  public enum CoverageReach {
    ALL,         // Instrument all statements.
    CONDITIONAL  // Do not instrument global statements.
  }

  /** @param compiler the compiler which generates the AST. */
  public CoverageInstrumentationPass(
      AbstractCompiler compiler,
      CoverageReach reach,
      InstrumentOption instrumentOption,
      String productionInstrumentationArrayName) {
    this.compiler = compiler;
    this.reach = reach;
    this.instrumentOption = instrumentOption;
    this.productionInstrumentationArrayName = productionInstrumentationArrayName;
    instrumentationData = new LinkedHashMap<>();
  }

  @InlineMe(
      replacement = "this(compiler, reach, InstrumentOption.LINE_ONLY, \"\")",
      imports = "com.google.javascript.jscomp.CompilerOptions.InstrumentOption")
  @Deprecated
  public CoverageInstrumentationPass(AbstractCompiler compiler, CoverageReach reach) {
    this(compiler, reach, InstrumentOption.LINE_ONLY, "");
  }

  /**
   * Creates the js code to be added to source. This code declares and initializes the variables
   * required for collection of coverage data.
   */
  private void addHeaderCode(Node script) {
    script.addChildToFront(createConditionalObjectDecl(JS_INSTRUMENTATION_OBJECT_NAME, script));

    // Make subsequent usages of "window" and "window.top" work in a Web Worker context.
    script.addChildToFront(
        compiler
            .parseSyntheticCode(
                "coverage_instrumentation_header",
                "if (!self.window) { self.window = self; self.window.top = self; }")
            .removeFirstChild()
            .srcrefTreeIfMissing(script));
  }

  private void addProductionHeaderCode(Node script, String arrayName) {

    Node arrayLit = IR.arraylit();
    Node name = IR.name(arrayName);
    Node varNode = IR.var(name, arrayLit);
    script.addChildToFront(varNode.srcrefTreeIfMissing(script));
  }

  private void checkIfArrayNameExternDeclared(Node externsNode, String arrayName) {
    if (!NodeUtil.collectExternVariableNames(this.compiler, externsNode).contains(arrayName)) {
      throw new AssertionError(
          "The array name passed to --production_instrumentation_array_name was not "
              + "declared as an extern. This will result in undefined behaviour");
    }
  }

  @Override
  public void process(Node externsNode, Node rootNode) {
    if (rootNode.hasChildren()) {
      if (instrumentOption == InstrumentOption.BRANCH_ONLY) {
        NodeTraversal.traverse(
            compiler,
            rootNode,
            new BranchCoverageInstrumentationCallback(compiler, instrumentationData));
      } else if (instrumentOption == InstrumentOption.PRODUCTION) {

        ProductionCoverageInstrumentationCallback productionCoverageInstrumentationCallback =
            new ProductionCoverageInstrumentationCallback(
                compiler, productionInstrumentationArrayName);

        NodeTraversal.traverse(compiler, rootNode, productionCoverageInstrumentationCallback);

        checkIfArrayNameExternDeclared(externsNode, productionInstrumentationArrayName);

        VariableMap instrumentationMapping =
            productionCoverageInstrumentationCallback.getInstrumentationMapping();
        compiler.setInstrumentationMapping(instrumentationMapping);

      } else {
        NodeTraversal.traverse(
            compiler, rootNode, new CoverageInstrumentationCallback(instrumentationData, reach));
      }
      Node firstScript = rootNode.getFirstChild();
      checkState(firstScript.isScript());
      // If any passes run after we need to preserve the MODULE_BODY structure of scripts - we can't
      // just add to a script if it is a module.
      if (firstScript.hasChildren() && firstScript.getFirstChild().isModuleBody()) {
        firstScript = firstScript.getFirstChild();
      }

      if (instrumentOption == InstrumentOption.PRODUCTION) {
        addProductionHeaderCode(firstScript, productionInstrumentationArrayName);
      } else {
        addHeaderCode(firstScript);
      }
    }
  }

  private Node createConditionalObjectDecl(String name, Node srcref) {
    // Make sure to quote properties so they are not renamed.
    Node jscovData;
    switch (instrumentOption) {
      case BRANCH_ONLY:
        jscovData =
            IR.objectlit(
                IR.quotedStringKey("fileNames", IR.arraylit()),
                IR.quotedStringKey("branchPresent", IR.arraylit()),
                IR.quotedStringKey("branchesInLine", IR.arraylit()),
                IR.quotedStringKey("branchesTaken", IR.arraylit()));
        break;
      case LINE_ONLY:
        jscovData =
            IR.objectlit(
                IR.quotedStringKey("fileNames", IR.arraylit()),
                IR.quotedStringKey("instrumentedLines", IR.arraylit()),
                IR.quotedStringKey("executedLines", IR.arraylit()));
        break;
      default:
        throw new AssertionError("Unexpected option: " + instrumentOption);
    }

    // Add the __jscov var to the window as a quoted key so it can be found even if property
    // renaming is enabled.
    Node var =
        IR.var(
            IR.name(name),
            IR.or(
                IR.getelem(IR.getprop(IR.name("window"), "top"), IR.string("__jscov")),
                IR.assign(
                    IR.getelem(IR.getprop(IR.name("window"), "top"), IR.string("__jscov")),
                    jscovData)));
    return var.srcrefTreeIfMissing(srcref);
  }
}
