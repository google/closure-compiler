/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

abstract class TypeCheckTestCase extends CompilerTypeTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Enable missing override checks that are disabled by default.
    compiler.getOptions().setWarningLevel(DiagnosticGroups.MISSING_OVERRIDE, CheckLevel.WARNING);
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_MISSING_PROPERTIES, CheckLevel.WARNING);
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.WARNING);
  }

  protected void disableStrictMissingPropertyChecks() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_MISSING_PROPERTIES, CheckLevel.OFF);
  }

  protected static ObjectType getInstanceType(Node js1Node) {
    JSType type = js1Node.getFirstChild().getJSType();
    assertNotNull(type);
    assertThat(type).isInstanceOf(FunctionType.class);
    FunctionType functionType = (FunctionType) type;
    assertTrue(functionType.isConstructor());
    return functionType.getInstanceType();
  }

  protected double getTypedPercent(String js) {
    return getTypedPercentWithExterns("", js);
  }

  protected double getTypedPercentWithExterns(String externs, String js) {
    Node n = compiler.parseTestCode(js);

    Node externsRoot = compiler.parseTestCode(externs);
    IR.root(externsRoot, n);

    TypeCheck t = makeTypeCheck();
    t.processForTesting(null, n);
    return t.getTypedPercent();
  }

  protected void checkObjectType(ObjectType objectType, String propertyName, JSType expectedType) {
    assertTrue(
        "Expected " + objectType.getReferenceName() + " to have property " + propertyName,
        objectType.hasProperty(propertyName));
    assertTypeEquals(
        "Expected "
            + objectType.getReferenceName()
            + "'s property "
            + propertyName
            + " to have type "
            + expectedType,
        expectedType,
        objectType.getPropertyType(propertyName));
  }

  protected void testTypes(String js) {
    testTypes(js, (String) null);
  }

  protected void testTypes(String js, String description) {
    testTypes(js, description, false);
  }

  protected void testTypes(String js, DiagnosticType type) {
    testTypes(js, type, false);
  }

  protected void testTypes(String js, String description, boolean isError) {
    testTypesWithExterns("", js, description, isError);
  }

  protected void testTypes(String js, List<String> descriptions) {
    testTypesWithExterns("", js, descriptions, false);
  }

  void testTypes(String js, List<String> descriptions, boolean isError) {
    testTypesWithExterns("", js, descriptions, isError);
  }

  void testTypes(String js, DiagnosticType diagnosticType, boolean isError) {
    testTypesWithExterns("", js, diagnosticType, isError);
  }

  protected void testTypes(String js, String[] warnings) {
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
      for (int i = 0; i < warnings.length && i < compiler.getWarningCount(); i++) {
        assertEquals(warnings[i], messages[i].description);
      }
    } else {
      assertEquals(0, compiler.getWarningCount());
    }
  }

  protected void testTypesWithCommonExterns(String js, String description) {
    testTypesWithExterns(DEFAULT_EXTERNS, js, description, false);
  }

  protected void testTypesWithCommonExterns(String js) {
    testTypesWithExterns(DEFAULT_EXTERNS, js, (DiagnosticType) null, false);
  }

  protected void testTypesWithExterns(
      String externs, String js, String description, boolean isError) {
    testTypesWithExterns(
        externs,
        js,
        description != null ? ImmutableList.of(description) : ImmutableList.of(),
        isError);
  }

  void testTypesWithExterns(String externs, String js, List<String> descriptions, boolean isError) {
    parseAndTypeCheck(externs, js);

    JSError[] errors = compiler.getErrors();
    if (!descriptions.isEmpty() && isError) {
      assertTrue(
          "expected " + descriptions.size() + " error(s) but got " + errors.length,
          errors.length >= descriptions.size());
      for (int i = 0; i < descriptions.size(); i++) {
        assertEquals(descriptions.get(i), errors[i].description);
      }
      errors =
          Arrays.asList(errors).subList(descriptions.size(), errors.length).toArray(new JSError[0]);
    }
    if (errors.length > 0) {
      fail("unexpected error(s):\n" + LINE_JOINER.join(errors));
    }

    JSError[] warnings = compiler.getWarnings();
    if (!descriptions.isEmpty() && !isError) {
      assertTrue(
          "expected " + descriptions.size() + " warning(s) but got " + warnings.length,
          warnings.length >= descriptions.size());
      for (int i = 0; i < descriptions.size(); i++) {
        assertEquals(descriptions.get(i), warnings[i].description);
      }
      warnings =
          Arrays.asList(warnings)
              .subList(descriptions.size(), warnings.length)
              .toArray(new JSError[0]);
    }
    if (warnings.length > 0) {
      fail("unexpected warnings(s):\n" + LINE_JOINER.join(warnings));
    }
  }

  void testTypesWithExterns(
      String externs, String js, DiagnosticType diagnosticType, boolean isError) {
    parseAndTypeCheck(externs, js);

    JSError[] errors = compiler.getErrors();
    if (diagnosticType != null && isError) {
      assertTrue("expected an error", errors.length > 0);
      assertEquals(diagnosticType, errors[0].getType());
      errors =
          Arrays.asList(errors).subList(1, errors.length).toArray(new JSError[errors.length - 1]);
    }
    if (errors.length > 0) {
      fail("unexpected error(s):\n" + LINE_JOINER.join(errors));
    }

    JSError[] warnings = compiler.getWarnings();
    if (diagnosticType != null && !isError) {
      assertTrue("expected a warning", warnings.length > 0);
      assertEquals(diagnosticType, warnings[0].getType());
      warnings =
          Arrays.asList(warnings)
              .subList(1, warnings.length)
              .toArray(new JSError[warnings.length - 1]);
    }
    if (warnings.length > 0) {
      fail("unexpected warnings(s):\n" + LINE_JOINER.join(warnings));
    }
  }

  protected void testTypesWithExterns(String externs, String js, String description) {
    testTypesWithExterns(externs, js, description, false);
  }

  protected void testTypesWithExterns(String externs, String js) {
    testTypesWithExterns(externs, js, (String) null, false);
  }

  protected void testTypesWithExtraExterns(String externs, String js) {
    testTypesWithExterns(DEFAULT_EXTERNS + "\n" + externs, js, (String) null, false);
  }

  protected void testTypesWithExtraExterns(String externs, String js, String description) {
    testTypesWithExterns(DEFAULT_EXTERNS + "\n" + externs, js, description, false);
  }

  protected void testTypesWithExtraExterns(String externs, String js, DiagnosticType diag) {
    testTypesWithExterns(DEFAULT_EXTERNS + "\n" + externs, js, diag, false);
  }

  /** Parses and type checks the JavaScript code. */
  protected Node parseAndTypeCheck(String js) {
    return parseAndTypeCheck("", js);
  }

  protected Node parseAndTypeCheck(String externs, String js) {
    return parseAndTypeCheckWithScope(externs, js).root;
  }

  /**
   * Parses and type checks the JavaScript code and returns the TypedScope used whilst type
   * checking.
   */
  protected TypeCheckResult parseAndTypeCheckWithScope(String js) {
    return parseAndTypeCheckWithScope("", js);
  }

  protected TypeCheckResult parseAndTypeCheckWithScope(String externs, String js) {
    registry.clearNamedTypes();
    registry.clearTemplateTypeNames();
    compiler.init(
        ImmutableList.of(SourceFile.fromCode("[externs]", externs)),
        ImmutableList.of(SourceFile.fromCode("[testcode]", js)),
        compiler.getOptions());
    compiler.setFeatureSet(compiler.getFeatureSet().without(Feature.MODULES));

    Node jsNode = IR.root(compiler.getInput(new InputId("[testcode]")).getAstRoot(compiler));
    Node externsNode = IR.root(compiler.getInput(new InputId("[externs]")).getAstRoot(compiler));
    Node externAndJsRoot = IR.root(externsNode, jsNode);
    compiler.jsRoot = jsNode;
    compiler.externsRoot = externsNode;
    compiler.externAndJsRoot = externAndJsRoot;

    assertEquals(
        "parsing error: " + Joiner.on(", ").join(compiler.getErrors()),
        0,
        compiler.getErrorCount());

    if (compiler.getOptions().needsTranspilationFrom(FeatureSet.ES6)) {
      List<PassFactory> passes = new ArrayList<>();
      TranspilationPasses.addEs2017Passes(passes);
      TranspilationPasses.addEs2016Passes(passes);
      TranspilationPasses.addEs6PreTypecheckPasses(passes, compiler.getOptions());
      PhaseOptimizer phaseopt = new PhaseOptimizer(compiler, null);
      phaseopt.consume(passes);
      phaseopt.process(externsNode, jsNode);
    }

    TypedScope s = makeTypeCheck().processForTesting(externsNode, jsNode);
    return new TypeCheckResult(jsNode.getFirstChild(), s);
  }

  protected Node typeCheck(Node n) {
    Node externsNode = IR.root();
    Node externAndJsRoot = IR.root(externsNode);
    externAndJsRoot.addChildToBack(n);

    makeTypeCheck().processForTesting(null, n);
    return n;
  }

  protected TypeCheck makeTypeCheck() {
    return new TypeCheck(compiler, new SemanticReverseAbstractInterpreter(registry), registry);
  }

  protected String suppressMissingProperty(String... props) {
    String result = "function dummy(x) { ";
    for (String prop : props) {
      result += "x." + prop + " = 3;";
    }
    return result + "}";
  }

  protected String suppressMissingPropertyFor(String type, String... props) {
    String result = "function dummy(x) { ";
    for (String prop : props) {
      result += type + ".prototype." + prop + " = 3;";
    }
    return result + "}";
  }

  protected void assertHasXMorePropertiesThanNativeObject(
      ObjectType instanceType, int numExtraProperties) {
    assertEquals(
        getNativeObjectPropertiesCount() + numExtraProperties, instanceType.getPropertiesCount());
  }

  private int getNativeObjectPropertiesCount() {
    return getNativeObjectType().getPropertiesCount();
  }

  protected static class TypeCheckResult {
    final Node root;
    final TypedScope scope;

    private TypeCheckResult(Node root, TypedScope scope) {
      this.root = root;
      this.scope = scope;
    }
  }
}
