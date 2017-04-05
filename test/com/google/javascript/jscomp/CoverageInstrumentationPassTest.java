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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import junit.framework.TestCase;

/**
 * Tests for function transformation in {@link CoverageInstrumentationPass}.
 */

public final class CoverageInstrumentationPassTest extends TestCase {

  private CompilerOptions options(LanguageMode inMode) {
    CompilerOptions options = GoldenFileComparer.options();
    options.setInstrumentForCoverage(true);
    options.setLanguageIn(inMode);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    return options;
  }

  private CompilerOptions branchOptions(LanguageMode inMode) {
    CompilerOptions options = GoldenFileComparer.options();
    options.setInstrumentForCoverage(true);
    options.setInstrumentBranchCoverage(true);
    options.setLanguageIn(inMode);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    return options;
  }

  private void compareFunctionOneMode(LanguageMode mode) throws Exception {
    GoldenFileComparer.compileAndCompare(
        "CoverageInstrumentationPassTest/FunctionGolden.jsdata",
        options(mode),
        "CoverageInstrumentationPassTest/Function.jsdata");
  }

  public void testFunction() throws Exception {
    compareFunctionOneMode(LanguageMode.ECMASCRIPT5);
    compareFunctionOneMode(LanguageMode.ECMASCRIPT_2015);
  }

  private void compareArrowOneMode(LanguageMode mode, String prefix) throws Exception {
    GoldenFileComparer.compileAndCompare(
        prefix + "Golden.jsdata", options(mode), prefix + ".jsdata");
  }

  // If the body of the arrow function is a block, it is instrumented.
  public void testArrowFunction_block() throws Exception {
    compareArrowOneMode(LanguageMode.ECMASCRIPT_2015, "CoverageInstrumentationPassTest/ArrowBlock");
  }

  // If the body of the arrow function is an expression, it is converted to a block,
  // then instrumented.
  public void testArrowFunction_expression() throws Exception {
    compareArrowOneMode(
        LanguageMode.ECMASCRIPT_2015, "CoverageInstrumentationPassTest/ArrowExpression");
  }

  private void compareIfBranch(LanguageMode mode) throws Exception {
    GoldenFileComparer.compileAndCompare(
        "CoverageInstrumentationPassTest/IfBranchGolden.jsdata",
        branchOptions(mode),
        "CoverageInstrumentationPassTest/IfBranch.jsdata");
  }

  public void testIfBranch() throws Exception {
    compareIfBranch(LanguageMode.ECMASCRIPT5);
    compareIfBranch(LanguageMode.ECMASCRIPT_2015);
  }

  private void compareIfElseBranch(LanguageMode mode) throws Exception {
    GoldenFileComparer.compileAndCompare(
        "CoverageInstrumentationPassTest/IfElseBranchGolden.jsdata",
        branchOptions(mode),
        "CoverageInstrumentationPassTest/IfElseBranch.jsdata");
  }

  public void testIfElseBranch() throws Exception {
    compareIfElseBranch(LanguageMode.ECMASCRIPT5);
    compareIfElseBranch(LanguageMode.ECMASCRIPT_2015);
  }

  private void compareForLoopBranch(LanguageMode mode) throws Exception {
    GoldenFileComparer.compileAndCompare(
        "CoverageInstrumentationPassTest/ForLoopBranchGolden.jsdata",
        branchOptions(mode),
        "CoverageInstrumentationPassTest/ForLoopBranch.jsdata");
  }

  public void testForLoopBranch() throws Exception {
    compareForLoopBranch(LanguageMode.ECMASCRIPT5);
    compareForLoopBranch(LanguageMode.ECMASCRIPT_2015);
  }

  private void compareDoWhileLoopBranch(LanguageMode mode) throws Exception {
    GoldenFileComparer.compileAndCompare(
        "CoverageInstrumentationPassTest/DoWhileLoopBranchGolden.jsdata",
        branchOptions(mode),
        "CoverageInstrumentationPassTest/DoWhileLoopBranch.jsdata");
  }

  public void testDoWhileLoopBranch() throws Exception {
    compareDoWhileLoopBranch(LanguageMode.ECMASCRIPT5);
    compareDoWhileLoopBranch(LanguageMode.ECMASCRIPT_2015);
  }

  private void compareDoWhileLoopMultiLineBranch(LanguageMode mode) throws Exception {
    GoldenFileComparer.compileAndCompare(
        "CoverageInstrumentationPassTest/DoWhileLoopMultiLineBranchGolden.jsdata",
        branchOptions(mode),
        "CoverageInstrumentationPassTest/DoWhileLoopMultiLineBranch.jsdata");
  }

  public void testDoWhileLoopMultiLineBranch() throws Exception {
    compareDoWhileLoopMultiLineBranch(LanguageMode.ECMASCRIPT5);
    compareDoWhileLoopMultiLineBranch(LanguageMode.ECMASCRIPT_2015);
  }

  private void compareWhileLoopBranch(LanguageMode mode) throws Exception {
    GoldenFileComparer.compileAndCompare(
        "CoverageInstrumentationPassTest/WhileLoopBranchGolden.jsdata",
        branchOptions(mode),
        "CoverageInstrumentationPassTest/WhileLoopBranch.jsdata");
  }

  public void testWhileLoopBranch() throws Exception {
    compareWhileLoopBranch(LanguageMode.ECMASCRIPT5);
    compareWhileLoopBranch(LanguageMode.ECMASCRIPT_2015);
  }

}
