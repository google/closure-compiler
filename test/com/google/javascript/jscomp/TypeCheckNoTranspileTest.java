/*
 * Copyright 2017 The Closure Compiler Authors.
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
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.type.ClosureReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests {@link TypeCheck} on non-transpiled code.
 */
public final class TypeCheckNoTranspileTest extends CompilerTypeTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Enable missing override checks that are disabled by default.
    compiler.getOptions().setWarningLevel(DiagnosticGroups.MISSING_OVERRIDE, CheckLevel.WARNING);
    compiler.getOptions().setWarningLevel(DiagnosticGroups.STRICT_MISSING_PROPERTIES, CheckLevel.WARNING);
  }

  @Override
  protected CompilerOptions getDefaultOptions() {
    CompilerOptions options = super.getDefaultOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    return options;
  }

  public void testUnsupported() {
    testTypes("const x = 0;", "Internal Error: TypeCheck doesn't know how to handle CONST", true);
  }

  private void testTypes(String js) {
    testTypes(js, (String) null);
  }

  private void testTypes(String js, String description) {
    testTypes(js, description, false);
  }

  private void testTypes(String js, DiagnosticType type) {
    testTypes(js, type, false);
  }

  void testTypes(String js, String description, boolean isError) {
    testTypes(DEFAULT_EXTERNS, js, description, isError);
  }

  void testTypes(
      String externs, String js, String description, boolean isError) {
    parseAndTypeCheck(externs, js);

    JSError[] errors = compiler.getErrors();
    if (description != null && isError) {
      assertTrue("expected an error", errors.length > 0);
      assertEquals(description, errors[0].description);
      errors = Arrays.asList(errors).subList(1, errors.length).toArray(
          new JSError[errors.length - 1]);
    }
    if (errors.length > 0) {
      fail("unexpected error(s):\n" + LINE_JOINER.join(errors));
    }

    JSError[] warnings = compiler.getWarnings();
    if (description != null && !isError) {
      assertTrue("expected a warning", warnings.length > 0);
      assertEquals(description, warnings[0].description);
      warnings = Arrays.asList(warnings).subList(1, warnings.length).toArray(
          new JSError[warnings.length - 1]);
    }
    if (warnings.length > 0) {
      fail("unexpected warnings(s):\n" + LINE_JOINER.join(warnings));
    }
  }

  void testTypes(String js, DiagnosticType diagnosticType, boolean isError) {
    testTypes(DEFAULT_EXTERNS, js, diagnosticType, isError);
  }

  void testTypes(String externs, String js, DiagnosticType diagnosticType,
      boolean isError) {
    parseAndTypeCheck(externs, js);

    JSError[] errors = compiler.getErrors();
    if (diagnosticType != null && isError) {
      assertTrue("expected an error", errors.length > 0);
      assertEquals(diagnosticType, errors[0].getType());
      errors = Arrays.asList(errors).subList(1, errors.length).toArray(
          new JSError[errors.length - 1]);
    }
    if (errors.length > 0) {
      fail("unexpected error(s):\n" + LINE_JOINER.join(errors));
    }

    JSError[] warnings = compiler.getWarnings();
    if (diagnosticType != null && !isError) {
      assertTrue("expected a warning", warnings.length > 0);
      assertEquals(diagnosticType, warnings[0].getType());
      warnings = Arrays.asList(warnings).subList(1, warnings.length).toArray(
          new JSError[warnings.length - 1]);
    }
    if (warnings.length > 0) {
      fail("unexpected warnings(s):\n" + LINE_JOINER.join(warnings));
    }
  }

  void testTypes(String js, String[] warnings) {
    Node n = compiler.parseTestCode(js);
    assertEquals(0, compiler.getErrorCount());
    Node externsNode = IR.root();
    // create a parent node for the extern and source blocks
    IR.root(externsNode, n);

    makeTypeCheck().processForTesting(null, n);
    assertEquals(0, compiler.getErrorCount());
    if (warnings != null) {
      assertEquals(warnings.length, compiler.getWarningCount());
      JSError[] messages = compiler.getWarnings();
      for (int i = 0; i < warnings.length && i < compiler.getWarningCount();
           i++) {
        assertEquals(warnings[i], messages[i].description);
      }
    } else {
      assertEquals(0, compiler.getWarningCount());
    }
  }

  private void testClosureTypes(String js, String description) {
    testClosureTypesMultipleWarnings(js,
        description == null ? null : ImmutableList.of(description));
  }

  private void testClosureTypesMultipleWarnings(
      String js, List<String> descriptions) {
    compiler.initOptions(compiler.getOptions());
    Node n = compiler.parseTestCode(js);
    Node externs = IR.root();
    IR.root(externs, n);

    assertEquals("parsing error: " +
        Joiner.on(", ").join(compiler.getErrors()),
        0, compiler.getErrorCount());

    // For processing goog.addDependency for forward typedefs.
    new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR, false).process(externs, n);

    new TypeCheck(compiler,
        new ClosureReverseAbstractInterpreter(registry).append(
                new SemanticReverseAbstractInterpreter(registry))
            .getFirst(),
        registry)
        .processForTesting(null, n);

    assertEquals(
        "unexpected error(s) : " +
        Joiner.on(", ").join(compiler.getErrors()),
        0, compiler.getErrorCount());

    if (descriptions == null) {
      assertEquals(
          "unexpected warning(s) : " +
          Joiner.on(", ").join(compiler.getWarnings()),
          0, compiler.getWarningCount());
    } else {
      assertEquals(
          "unexpected warning(s) : " +
          Joiner.on(", ").join(compiler.getWarnings()),
          descriptions.size(), compiler.getWarningCount());
      Set<String> actualWarningDescriptions = new HashSet<>();
      for (int i = 0; i < descriptions.size(); i++) {
        actualWarningDescriptions.add(compiler.getWarnings()[i].description);
      }
      assertEquals(
          new HashSet<>(descriptions), actualWarningDescriptions);
    }
  }

  void testTypesWithExterns(String externs, String js) {
    testTypes(externs, js, (String) null, false);
  }

  void testTypesWithExtraExterns(String externs, String js) {
    testTypes(DEFAULT_EXTERNS + "\n" + externs, js, (String) null, false);
  }

  void testTypesWithExtraExterns(
      String externs, String js, String description) {
    testTypes(DEFAULT_EXTERNS + "\n" + externs, js, description, false);
  }

  void testTypesWithExtraExterns(String externs, String js, DiagnosticType diag) {
    testTypes(DEFAULT_EXTERNS + "\n" + externs, js, diag, false);
  }

  /**
   * Parses and type checks the JavaScript code.
   */
  private Node parseAndTypeCheck(String js) {
    return parseAndTypeCheck(DEFAULT_EXTERNS, js);
  }

  private Node parseAndTypeCheck(String externs, String js) {
    return parseAndTypeCheckWithScope(externs, js).root;
  }

  /**
   * Parses and type checks the JavaScript code and returns the TypedScope used
   * whilst type checking.
   */
  private TypeCheckResult parseAndTypeCheckWithScope(String js) {
    return parseAndTypeCheckWithScope(DEFAULT_EXTERNS, js);
  }

  private TypeCheckResult parseAndTypeCheckWithScope(String externs, String js) {
    registry.clearNamedTypes();
    registry.clearTemplateTypeNames();
    compiler.init(
        ImmutableList.of(SourceFile.fromCode("[externs]", externs)),
        ImmutableList.of(SourceFile.fromCode("[testcode]", js)),
        compiler.getOptions());

    Node n = IR.root(compiler.getInput(new InputId("[testcode]")).getAstRoot(compiler));
    Node externsNode = IR.root(compiler.getInput(new InputId("[externs]")).getAstRoot(compiler));
    Node externAndJsRoot = IR.root(externsNode, n);
    compiler.jsRoot = n;
    compiler.externsRoot = externsNode;
    compiler.externAndJsRoot = externAndJsRoot;

    assertEquals("parsing error: " +
        Joiner.on(", ").join(compiler.getErrors()),
        0, compiler.getErrorCount());

    TypedScope s = makeTypeCheck().processForTesting(externsNode, n);
    return new TypeCheckResult(n, s);
  }

  private Node typeCheck(Node n) {
    Node externsNode = IR.root();
    Node externAndJsRoot = IR.root(externsNode);
    externAndJsRoot.addChildToBack(n);

    makeTypeCheck().processForTesting(null, n);
    return n;
  }

  private TypeCheck makeTypeCheck() {
    return new TypeCheck(compiler, new SemanticReverseAbstractInterpreter(registry), registry);
  }

  String suppressMissingProperty(String ... props) {
    String result = "function dummy(x) { ";
    for (String prop : props) {
      result += "x." + prop + " = 3;";
    }
    return result + "}";
  }

  String suppressMissingPropertyFor(String type, String ... props) {
    String result = "function dummy(x) { ";
    for (String prop : props) {
      result += type + ".prototype." + prop + " = 3;";
    }
    return result + "}";
  }

  private static class TypeCheckResult {
    private final Node root;
    private final TypedScope scope;

    private TypeCheckResult(Node root, TypedScope scope) {
      this.root = root;
      this.scope = scope;
    }
  }
}
