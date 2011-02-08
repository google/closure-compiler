/*
 * Copyright 2008 The Closure Compiler Authors.
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

/**
 * Unit test for the JSCompiler CheckPropertyOrder pass.
 *
 */
public class CheckPropertyOrderTest extends CompilerTestCase {
  public CheckPropertyOrderTest() {
    super("", true);
    enableTypeCheck(CheckLevel.WARNING);
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new CheckPropertyOrder(
        compiler, CheckLevel.WARNING, true);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testNoBranches() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function() {"
             + "  this.a = 'a';"
             + "  this.b = 3;"
             + "  this.c = null;"
             + "};");
  }

  public void testIfBranchDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  if (a < 10) {"
             + "    this.a = a;"
             + "  }"
             + "};",
             CheckPropertyOrder.UNASSIGNED_PROPERTY);
  }

  public void testIfBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  if (a < 10) {"
             + "    this.a = a;"
             + "  } else {"
             + "    this.a = 10;"
             + "  }"
             + "};");
  }

  public void testHookBranchDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  var b = (a < 10) ? (this.a = 1) : 2"
             + "};",
             CheckPropertyOrder.UNASSIGNED_PROPERTY);
  }

  public void testHookBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  var b = (a < 10) ? (this.a = 1) : (this.a = 2)"
             + "};");
  }

  public void testAndBranchDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  (a < 10) && (this.a = 1);"
             + "};",
             CheckPropertyOrder.UNASSIGNED_PROPERTY);
  }

  public void testOrBranchDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  (a < 10) || (this.a = 1);"
             + "};",
             CheckPropertyOrder.UNASSIGNED_PROPERTY);
  }

  public void testAndOrBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function() {"
             + "  (this.a = 0) && (this.a = 1);"
             + "  (this.b = 2) || (this.b = 3);"
             + "};");
  }

  public void testWhileBranchDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function() {"
             + "  this.a = 0;"
             + "  while (this.a < 10) {"
             + "    this.b = 3;"
             + "    ++this.a;"
             + "  }"
             + "};",
             CheckPropertyOrder.UNASSIGNED_PROPERTY);
  }

  public void testWhileBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function() {"
             + "  this.a = 0;"
             + "  this.b = 0;"
             + "  while (this.a < 10) {"
             + "    this.b = 3;"
             + "    ++this.a;"
             + "  }"
             + "};");
  }

  public void testForBranchDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function() {"
             + "  for (this.a = 0; this.a < 10; ++this.a) {"
             + "    this.b = 3;"
             + "  }"
             + "};",
             CheckPropertyOrder.UNASSIGNED_PROPERTY);
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function() {"
             + "  for (; !this.b; this.b = 1) {}"
             + "  this.a = 1;"
             + "};",
             CheckPropertyOrder.UNEQUAL_PROPERTIES);
  }

  public void testForBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function() {"
             + "  this.b = 0;"
             + "  for (this.a = 0; this.a < 10; ++this.a) {"
             + "    this.b = 3;"
             + "  }"
             + "};");
  }

  public void testDoBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function() {"
             + "  this.a = 1;"
             + "  do {"
             + "    this.a = 2;"
             + "  } while (false);"
             + "};");
  }

  public void testReturnBranchDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  this.a = 1;"
             + "  if (a < 10) return;"
             + "  this.b = 2;"
             + "};",
             CheckPropertyOrder.UNASSIGNED_PROPERTY);
  }

  public void testReturnBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  this.a = 1;"
             + "  if (a < 10) return;"
             + "  this.a = 2;"
             + "};");
  }

// TODO(user): The type registery is behaves a bit different than before.
// The .b property is no longer on the InstanceObject instead, it is stored in
// JSTypeRegistry.typesIndexedByProperty. Fix this when we want to use this
// pass for real.
//  public void testUnassigned() {
//    testSame("var a = {};"
//             + "/** @constructor */"
//             + "a.F = function() {"
//             + "  this.a = 10;"
//             + "};"
//             + "function bar() {"
//             + "  var f = new a.F();"
//             + "  f.b = 11;"
//             + "}",
//             CheckPropertyOrder.UNASSIGNED_PROPERTY);
//  }

  public void testUnequalProperty() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function() {"
             + "  if (1) { this.a = 1; }"
             + "  else { this.b = 1; }"
             + "};",
             CheckPropertyOrder.UNEQUAL_PROPERTIES);
  }

  public void testAssignedAfterMerge() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  if (a > 0) { this.a = 1; }"
             + "  this.a = 2;"
             + "};");
  }

  public void testBreakBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function() {"
             + "  this.a = 1;"
             + "  do {"
             + "    if (true) {"
             + "      this.a = 2;"
             + "      break;"
             + "    }"
             + "  } while (false);"
             + "};");
  }

  public void testContinueBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function() {"
             + "  this.a = 1;"
             + "  do {"
             + "    if (true) {"
             + "      this.a = 2;"
             + "      continue;"
             + "    }"
             + "  } while (false);"
             + "};");
  }

  public void testLabeledBreakBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function() {"
             + "  this.a = 1;"
             + "  foo: while (1) {"
             + "    while (0) {"
             + "      this.a = 2;"
             + "      break foo;"
             + "    }"
             + "  }"
             + "};");
  }

  public void testLabeledContinueBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function() {"
             + "  this.a = 1;"
             + "  foo: while (1) {"
             + "    while (0) {"
             + "      this.a = 2;"
             + "      continue foo;"
             + "    }"
             + "  }"
             + "};");
  }

  public void testSwitchBranchDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  switch (a) {"
             + "    case 0:"
             + "    case 1:"
             + "      break;"
             + "    case 2:"
             + "      this.a = 2;"
             + "      break;"
             + "    default:"
             + "      this.a = 3;"
             + "  }"
             + "};",
             CheckPropertyOrder.UNASSIGNED_PROPERTY);
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  switch (a) {"
             + "    case 0:"
             + "    case 1:"
             + "      this.a = 1;"
             + "      break;"
             + "    case 2:"
             + "      break;"
             + "    default:"
             + "      this.a = 3;"
             + "  }"
             + "};",
             CheckPropertyOrder.UNASSIGNED_PROPERTY);
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  switch (a) {"
             + "    case 0:"
             + "    case 1:"
             + "      this.a = 1;"
             + "      break;"
             + "    case 2:"
             + "      this.a = 2;"
             + "      break;"
             + "    default:"
             + "  }"
             + "};",
             CheckPropertyOrder.UNASSIGNED_PROPERTY);
  }

  public void testSwitchBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  switch (a) {"
             + "    case 0:"
             + "    case 1:"
             + "      this.a = 1;"
             + "      break;"
             + "    case 2:"
             + "      this.a = 2;"
             + "      break;"
             + "    default:"
             + "      this.a = 3;"
             + "  }"
             + "};");
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  switch (a) {"
             + "    case 0:"
             + "    case 1:"
             + "      this.a = 1;"
             + "      break;"
             + "    case 2:"
             + "      this.a = 2;"
             + "    default:"
             + "      this.a = 3;"
             + "  }"
             + "};");
  }

  public void testTryCatchBranchDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  try { if (1){ throw Error(); } }"
             + "  catch (ex) { this.a = 1; }"
             + "};",
             CheckPropertyOrder.UNASSIGNED_PROPERTY);
  }

  public void testTryCatchBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  try { throw Error(); }"
             + "  catch (ex) { this.a = 1; }"
             + "};");
    // TODO(user): Fix this test case.
    //testSame("var a = {};"
    //         + "/** @constructor */"
    //         + "a.F = function(a) {"
    //         + "  try {}"
    //         + "  catch (ex) { this.a = 1; }"  // never executed
    //         + "};",
    //         CheckPropertyOrder.UNASSIGNED_PROPERTY);
  }

  public void testTryFinallyBranchNoDifference() {
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  if (1) {"
             + "    try { this.a = 2; }"
             + "    finally { }"
             + "  } else {"
             + "    this.a = 3;"
             + "  }"
             + "};");
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  if (1) {"
             + "    try {}"
             + "    finally { this.a = 2; }"
             + "  } else {"
             + "    this.a = 3;"
             + "  }"
             + "};");
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  if (1) {"
             + "    do {"
             + "      try { break; }"
             + "      finally {this.a = 2; }"
             + "    } while(1);"
             + "  } else {"
             + "    this.a = 3;"
             + "  }"
             + "};");
    testSame("var a = {};"
             + "/** @constructor */"
             + "a.F = function(a) {"
             + "  if (a) {"
             + "    this.a = 1;"
             + "  } else {"
             + "    try {"
             + "      this.a = (function() { throw 1; })();"
             + "    } finally {"
             + "    }"
             + "  }"
             + "};");
  }

  public void testGlobalFunction() {
    testSame("/** @constructor */"
             + "function F(a) {"
             + "  if (a < 10) {"
             + "    this.a = a;"
             + "  } else {"
             + "    this.a = 10;"
             + "  }"
             + "};");
    testSame("/** @constructor */"
             + "function F(a) {"
             + "  if (a < 10) {"
             + "    this.a = a;"
             + "  }"
             + "};",
             CheckPropertyOrder.UNASSIGNED_PROPERTY);
  }
}