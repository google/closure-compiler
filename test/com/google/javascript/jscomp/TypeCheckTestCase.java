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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.testing.JSCompCorrespondences.DESCRIPTION_EQUALITY;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
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
import java.util.List;
import java.util.Objects;
import org.junit.Before;

abstract class TypeCheckTestCase extends CompilerTypeTestCase {

  private boolean reportUnknownTypes = false;
  private boolean runClosurePass = false;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    this.reportUnknownTypes = false;
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

  protected void enableReportUnknownTypes() {
    this.reportUnknownTypes = true;
  }

  protected void enableRunClosurePass() {
    this.runClosurePass = true;
  }

  protected static ObjectType getInstanceType(Node js1Node) {
    JSType type = js1Node.getFirstChild().getJSType();
    assertThat(type).isNotNull();
    assertThat(type).isInstanceOf(FunctionType.class);
    FunctionType functionType = (FunctionType) type;
    assertThat(functionType.isConstructor()).isTrue();
    return functionType.getInstanceType();
  }

  protected double getTypedPercent(String js) {
    return getTypedPercentWithExterns("", js);
  }

  protected double getTypedPercentWithExterns(String externs, String js) {
    Node jsRoot = IR.root(compiler.parseTestCode(js));

    Node externsRoot = IR.root(compiler.parseTestCode(externs));
    IR.root(externsRoot, jsRoot);

    TypeCheck t = makeTypeCheck();
    t.processForTesting(externsRoot, jsRoot);
    return t.getTypedPercent();
  }

  protected void checkObjectType(ObjectType objectType, String propertyName, JSType expectedType) {
    assertWithMessage(
            "Expected " + objectType.getReferenceName() + " to have property " + propertyName)
        .that(objectType.hasProperty(propertyName))
        .isTrue();
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
    assertThat(compiler.getErrors()).isEmpty();
    Node externsNode = IR.root();
    // create a parent node for the extern and source blocks
    Node root = IR.root(externsNode, IR.root(n));

    makeTypeCheck().processForTesting(root.getFirstChild(), root.getSecondChild());
    assertThat(compiler.getErrors()).isEmpty();
    checkReportedWarningsHelper(warnings);
  }

  protected void testTypesWithCommonExterns(String js, List<String> descriptions) {
    testTypesWithExterns(DEFAULT_EXTERNS, js, descriptions, false);
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

    if (isError) {
      assertWithMessage("Regarding errors:")
          .that(compiler.getErrors())
          .comparingElementsUsing(DESCRIPTION_EQUALITY)
          .containsExactlyElementsIn(descriptions)
          .inOrder();
      assertWithMessage("Regarding warnings").that(compiler.getWarnings()).isEmpty();
    } else {
      assertWithMessage("Regarding warnings:")
          .that(compiler.getWarnings())
          .comparingElementsUsing(DESCRIPTION_EQUALITY)
          .containsExactlyElementsIn(descriptions)
          .inOrder();
      assertWithMessage("Regarding errors:").that(compiler.getErrors()).isEmpty();
    }
  }

  void testTypesWithExterns(
      String externs, String js, DiagnosticType diagnosticType, boolean isError) {
    parseAndTypeCheck(externs, js);

    ImmutableList<DiagnosticType> expectedTypes =
        (diagnosticType == null) ? ImmutableList.of() : ImmutableList.of(diagnosticType);

    if (isError) {
      assertWithMessage("Regarding errors:")
          .that(compiler.getErrors())
          .comparingElementsUsing(DIAGNOSTIC_TYPE_EQUALITY)
          .containsExactlyElementsIn(expectedTypes)
          .inOrder();
      assertWithMessage("Regarding warnings").that(compiler.getWarnings()).isEmpty();
    } else {
      assertWithMessage("Regarding warnings:")
          .that(compiler.getWarnings())
          .comparingElementsUsing(DIAGNOSTIC_TYPE_EQUALITY)
          .containsExactlyElementsIn(expectedTypes)
          .inOrder();
      assertWithMessage("Regarding errors:").that(compiler.getErrors()).isEmpty();
    }
  }

  protected void testTypesWithExterns(String externs, String js, String description) {
    testTypesWithExterns(externs, js, description, false);
  }

  protected void testTypesWithExterns(String externs, String js, DiagnosticType diagnosticType) {
    testTypesWithExterns(externs, js, diagnosticType, false);
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
    compiler.init(
        ImmutableList.of(SourceFile.fromCode("[externs]", externs)),
        ImmutableList.of(SourceFile.fromCode("[testcode]", js)),
        compiler.getOptions());
    compiler.setFeatureSet(compiler.getFeatureSet().without(Feature.MODULES));
    compiler.getOptions().setClosurePass(runClosurePass);

    Node jsNode = IR.root(compiler.getInput(new InputId("[testcode]")).getAstRoot(compiler));
    Node externsNode = IR.root(compiler.getInput(new InputId("[externs]")).getAstRoot(compiler));
    Node externAndJsRoot = IR.root(externsNode, jsNode);
    compiler.jsRoot = jsNode;
    compiler.externsRoot = externsNode;
    compiler.externAndJsRoot = externAndJsRoot;
    new GatherModuleMetadata(compiler, false, ResolutionMode.BROWSER).process(externsNode, jsNode);
    new ModuleMapCreator(compiler, compiler.getModuleMetadataMap()).process(externsNode, jsNode);

    assertWithMessage("Regarding errors:").that(compiler.getErrors()).isEmpty();

    if (compiler.getOptions().needsTranspilationFrom(FeatureSet.ES6)) {
      List<PassFactory> passes = new ArrayList<>();
      TranspilationPasses.addPreTypecheckTranspilationPasses(passes, compiler.getOptions());
      PhaseOptimizer phaseopt = new PhaseOptimizer(compiler, null);
      phaseopt.consume(passes);
      phaseopt.process(externsNode, jsNode);
    }

    TypedScope s = makeTypeCheck().processForTesting(externsNode, jsNode);
    return new TypeCheckResult(jsNode.getFirstChild(), s);
  }

  /** @param n A valid statement node, SCRIPT, or ROOT node. */
  protected Node typeCheck(Node n) {
    Node jsRoot;
    if (n.isRoot()) {
      // This is fine as is.
      jsRoot = n;
    } else if (n.isScript()) {
      jsRoot = IR.root(n);
    } else {
      Node script = IR.script(n);
      jsRoot = IR.root(script);
      script.setInputId(new InputId("test"));
    }
    Node externsNode = IR.root();
    Node externAndJsRoot = IR.root(externsNode);
    externAndJsRoot.addChildToBack(jsRoot);

    makeTypeCheck().processForTesting(externsNode, externAndJsRoot.getSecondChild());
    return n;
  }

  protected TypeCheck makeTypeCheck() {
    return new TypeCheck(compiler, new SemanticReverseAbstractInterpreter(registry), registry)
        .reportUnknownTypes(reportUnknownTypes);
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
    assertThat(instanceType.getPropertiesCount())
        .isEqualTo(getNativeObjectPropertiesCount() + numExtraProperties);
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

  private static final Correspondence<JSError, DiagnosticType> DIAGNOSTIC_TYPE_EQUALITY =
      Correspondence.from(
          (error, type) -> Objects.equals(error.getType(), type), "has diagnostic type equal to");
}
