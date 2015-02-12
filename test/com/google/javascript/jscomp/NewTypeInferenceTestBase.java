/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Use by the classes that test {@link NewTypeInference}.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public abstract class NewTypeInferenceTestBase extends CompilerTypeTestCase {
  protected List<PassFactory> passes;

  protected static final String CLOSURE_BASE = "var goog;";
  protected static final String DEFAULT_EXTERNS =
      CompilerTypeTestCase.DEFAULT_EXTERNS + "/** @return {string} */\n"
      + "String.prototype.toString = function() { return '' };\n"
      + "/**\n"
      + " * @constructor\n"
      + " * @param {*=} arg\n"
      + " * @return {number}\n"
      + " */\n"
      + "function Number(arg) {}\n"
      + "/** @return {string} */\n"
      + "Number.prototype.toString = function() { return '' };\n"
      + "/**\n"
      + " * @constructor\n"
      + " * @param {*=} arg\n"
      + " * @return {boolean}\n"
      + " */\n"
      + "function Boolean(arg) {}\n"
      + "/** @return {string} */\n"
      + "Boolean.prototype.toString = function() { return '' };\n"
      + "/**\n"
      + " * @param {?=} opt_begin\n"
      + " * @param {?=} opt_end\n"
      + " * @return {!Array.<T>}\n"
      + " * @this {{length: number}|string}\n"
      + " * @template T\n"
      + " */\n"
      + "Array.prototype.slice = function(opt_begin, opt_end) {};\n"
      + "/**\n"
      + " * @param {...?} var_args\n"
      + " * @return {!Array.<?>}\n"
      + " */\n"
      + "Array.prototype.concat = function(var_args) {};\n";

  @Override
  protected void setUp() {
    super.setUp();
    passes = new ArrayList<>();
  }

  protected final PassFactory makePassFactory(
      String name, final CompilerPass pass) {
    return new PassFactory(name, true/* one-time pass */) {
      @Override
      protected CompilerPass create(AbstractCompiler compiler) {
        return pass;
      }
    };
  }

  protected final void addES6TranspilationPasses() {
    passes.add(makePassFactory("Es6RenameVariablesInParamLists",
            new Es6RenameVariablesInParamLists(compiler)));
    passes.add(makePassFactory("Es6SplitVariableDeclarations",
            new Es6SplitVariableDeclarations(compiler)));
    passes.add(makePassFactory("es6ConvertSuper",
            new Es6ConvertSuper(compiler)));
    passes.add(makePassFactory("convertEs6",
            new Es6ToEs3Converter(compiler)));
    passes.add(makePassFactory("Es6RewriteLetConst",
            new Es6RewriteLetConst(compiler)));
    passes.add(makePassFactory("rewriteGenerators",
            new Es6RewriteGenerators(compiler)));
    passes.add(makePassFactory("Es6RuntimeLibrary",
            new InjectEs6RuntimeLibrary(compiler)));
    passes.add(makePassFactory("Es6StaticInheritance",
            new Es6ToEs3ClassSideInheritance(compiler)));
  }

  protected final void parseAndTypeCheck(String externs, String js) {
    setUp();
    final CompilerOptions options = compiler.getOptions();
    options.setClosurePass(true);
    compiler.init(
        Lists.newArrayList(SourceFile.fromCode("[externs]", externs)),
        Lists.newArrayList(SourceFile.fromCode("[testcode]", js)),
        options);

    Node externsRoot = IR.block();
    externsRoot.setIsSyntheticBlock(true);
    externsRoot.addChildToFront(
        compiler.getInput(new InputId("[externs]")).getAstRoot(compiler));
    Node astRoot = IR.block();
    astRoot.setIsSyntheticBlock(true);
    astRoot.addChildToFront(
        compiler.getInput(new InputId("[testcode]")).getAstRoot(compiler));

    assertEquals("parsing error: " + Joiner.on(", ").join(compiler.getErrors()),
        0, compiler.getErrorCount());
    assertEquals(
        "parsing warning: " + Joiner.on(", ").join(compiler.getWarnings()), 0,
        compiler.getWarningCount());

    // Create common parent of externs and ast; needed by Es6RewriteLetConst.
    IR.block(externsRoot, astRoot).setIsSyntheticBlock(true);

    GlobalTypeInfo symbolTable = new GlobalTypeInfo(compiler);
    passes.add(makePassFactory("GlobalTypeInfo", symbolTable));
    compiler.setSymbolTable(symbolTable);
    passes.add(makePassFactory("NewTypeInference",
            new NewTypeInference(compiler, options.closurePass)));

    PhaseOptimizer phaseopt = new PhaseOptimizer(compiler, null, null);
    phaseopt.consume(passes);
    phaseopt.process(externsRoot, astRoot);
  }

  protected final void typeCheck(String js, DiagnosticType... warningKinds) {
    typeCheck(DEFAULT_EXTERNS, js, warningKinds);
  }

  protected final void typeCheckCustomExterns(
      String externs, String js, DiagnosticType... warningKinds) {
    typeCheck(externs, js, warningKinds);
  }

  protected final void typeCheck(
      String externs, String js, DiagnosticType... warningKinds) {
    parseAndTypeCheck(externs, js);
    if (compiler.getErrors().length > 0) {
      fail("Expected no errors, but found: "
          + Arrays.toString(compiler.getErrors()));
    }
    JSError[] warnings = compiler.getWarnings();
    String errorMessage =
        "Expected warning of type:\n"
        + "================================================================\n"
        + Arrays.toString(warningKinds)
        + "================================================================\n"
        + "but found:\n"
        + "----------------------------------------------------------------\n"
        + Arrays.toString(warnings) + "\n"
        + "----------------------------------------------------------------\n";
    assertEquals(
        errorMessage + "Warning count", warningKinds.length, warnings.length);
    for (JSError warning : warnings) {
      assertTrue(
          "Wrong warning type\n" + errorMessage,
          Arrays.asList(warningKinds).contains(warning.getType()));
    }
  }
}
