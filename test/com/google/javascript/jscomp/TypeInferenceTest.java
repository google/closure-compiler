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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.CompilerTypeTestCase.lines;
import static com.google.javascript.jscomp.ScopeSubject.assertScope;
import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.CHECKED_UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_INSTANCE_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_RESOLVED_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;
import static com.google.javascript.rhino.testing.TypeSubject.types;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionSpec;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DataFlowAnalysis.BranchedFlowState;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.type.FlowScope;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedSlot;
import com.google.javascript.rhino.testing.TypeSubject;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link TypeInference}.
 *
 */
@RunWith(JUnit4.class)
public final class TypeInferenceTest {

  private Compiler compiler;
  private JSTypeRegistry registry;
  private Map<String, JSType> assumptions;
  private JSType assumedThisType;
  private FlowScope returnScope;
  // TODO(bradfordcsmith): This should be an ImmutableMap.
  private static final Map<String, AssertionFunctionSpec> ASSERTION_FUNCTION_MAP = new HashMap<>();

  static {
    for (AssertionFunctionSpec func :
        new ClosureCodingConvention().getAssertionFunctions()) {
      ASSERTION_FUNCTION_MAP.put(func.getFunctionName(), func);
    }
  }

  /**
   * Maps a label name to information about the labeled statement.
   *
   * <p>This map is recreated each time parseAndRunTypeInference() is executed.
   */
  private Map<String, LabeledStatement> labeledStatementMap;

  /** Stores information about a labeled statement and allows making assertions on it. */
  static class LabeledStatement {
    final Node statementNode;
    final TypedScope enclosingScope;

    LabeledStatement(Node statementNode, TypedScope enclosingScope) {
      this.statementNode = checkNotNull(statementNode);
      this.enclosingScope = checkNotNull(enclosingScope);
    }
  }

  @Before
  public void setUp() {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    compiler.initOptions(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2018);
    registry = compiler.getTypeRegistry();
    assumptions = new HashMap<>();
    returnScope = null;
  }

  private void assumingThisType(JSType type) {
    assumedThisType = type;
  }

  private void assuming(String name, JSType type) {
    assumptions.put(name, type);
  }

  /** Declares a name with a given type in the parent scope of the test case code. */
  private void assuming(String name, JSTypeNative type) {
    assuming(name, registry.getNativeType(type));
  }

  private void inFunction(String js) {
    // Parse the body of the function.
    String thisBlock = assumedThisType == null
        ? ""
        : "/** @this {" + assumedThisType + "} */";
    parseAndRunTypeInference("(" + thisBlock + " function() {" + js + "});");
  }

  private void inGenerator(String js) {
    checkState(assumedThisType == null);
    parseAndRunTypeInference("(function *() {" + js + "});");
  }

  private void parseAndRunTypeInference(String js) {
    Node root = compiler.parseTestCode(js);
    assertWithMessage("parsing error: " + Joiner.on(", ").join(compiler.getErrors()))
        .that(compiler.getErrorCount())
        .isEqualTo(0);

    // SCRIPT -> EXPR_RESULT -> FUNCTION
    // `(function() { TEST CODE HERE });`
    Node n = root.getFirstFirstChild();

    // Create the scope with the assumptions.
    TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);
    // Also populate a map allowing us to look up labeled statements later.
    labeledStatementMap = new HashMap<>();
    new NodeTraversal(
            compiler,
            new AbstractPostOrderCallback() {
              @Override
              public void visit(NodeTraversal t, Node n, Node parent) {
                TypedScope scope = t.getTypedScope();
                if (parent != null && parent.isLabel() && !n.isLabelName()) {
                  // First child of a LABEL is a LABEL_NAME, n is the second child.
                  Node labelNameNode = checkNotNull(n.getPrevious(), n);
                  checkState(labelNameNode.isLabelName(), labelNameNode);
                  String labelName = labelNameNode.getString();
                  assertWithMessage("Duplicate label name: %s", labelName)
                      .that(labeledStatementMap)
                      .doesNotContainKey(labelName);
                  labeledStatementMap.put(labelName, new LabeledStatement(n, scope));
                }
              }
            },
            scopeCreator)
        .traverse(root);
    TypedScope assumedScope = scopeCreator.createScope(n);
    for (Map.Entry<String,JSType> entry : assumptions.entrySet()) {
      assumedScope.declare(entry.getKey(), null, entry.getValue(), null, false);
    }
    // Create the control graph.
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, false);
    cfa.process(null, n);
    ControlFlowGraph<Node> cfg = cfa.getCfg();
    // Create a simple reverse abstract interpreter.
    ReverseAbstractInterpreter rai = compiler.getReverseAbstractInterpreter();
    // Do the type inference by data-flow analysis.
    TypeInference dfa = new TypeInference(compiler, cfg, rai, assumedScope,
        scopeCreator, ASSERTION_FUNCTION_MAP);
    dfa.analyze();
    // Get the scope of the implicit return.
    BranchedFlowState<FlowScope> rtnState =
        cfg.getImplicitReturn().getAnnotation();
    // Reset the flow scope's syntactic scope to the function block, rather than the function node
    // itself.  This allows pulling out local vars from the function by name to verify their types.
    returnScope = rtnState.getIn().withSyntacticScope(scopeCreator.createScope(n.getLastChild()));
  }

  private LabeledStatement getLabeledStatement(String label) {
    assertWithMessage("No statement found for label: %s", label)
        .that(labeledStatementMap)
        .containsKey(label);
    return labeledStatementMap.get(label);
  }

  /**
   * Returns a ScopeSubject for the scope containing the labeled statement.
   *
   * <p>Asserts that a statement with the given label existed in the code last passed to
   * parseAndRunTypeInference().
   */
  private ScopeSubject assertScopeEnclosing(String label) {
    return assertScope(getLabeledStatement(label).enclosingScope);
  }

  /**
   * Returns a TypeSubject for the JSType of the expression with the given label.
   *
   * <p>Asserts that a statement with the given label existed in the code last passed to
   * parseAndRunTypeInference(). Also asserts that the statement is an EXPR_RESULT whose expression
   * has a non-null JSType.
   */
  private TypeSubject assertTypeOfExpression(String label) {
    Node statementNode = getLabeledStatement(label).statementNode;
    assertWithMessage("Not an expression statement.").that(statementNode.isExprResult()).isTrue();
    JSType jsType = statementNode.getOnlyChild().getJSType();
    assertWithMessage("Expression type is null").that(jsType).isNotNull();
    return assertType(jsType);
  }

  private JSType getType(String name) {
    assertWithMessage("The return scope should not be null.").that(returnScope).isNotNull();
    StaticTypedSlot var = returnScope.getSlot(name);
    assertWithMessage("The variable " + name + " is missing from the scope.").that(var).isNotNull();
    return var.getType();
  }

  private void verify(String name, JSType type) {
    assertWithMessage("Mismatch for " + name)
        .about(types())
        .that(getType(name))
        .isStructurallyEqualTo(type);
  }

  private void verify(String name, JSTypeNative type) {
    verify(name, registry.getNativeType(type));
  }

  private void verifySubtypeOf(String name, JSType type) {
    JSType varType = getType(name);
    assertWithMessage("The variable " + name + " is missing a type.").that(varType).isNotNull();
    assertWithMessage(
            "The type " + varType + " of variable " + name + " is not a subtype of " + type + ".")
        .that(varType.isSubtypeOf(type))
        .isTrue();
  }

  private void verifySubtypeOf(String name, JSTypeNative type) {
    verifySubtypeOf(name, registry.getNativeType(type));
  }

  private EnumType createEnumType(String name, JSTypeNative elemType) {
    return createEnumType(name, registry.getNativeType(elemType));
  }

  private EnumType createEnumType(String name, JSType elemType) {
    return registry.createEnumType(name, null, elemType);
  }

  private JSType createUndefinableType(JSTypeNative type) {
    return registry.createUnionType(
        registry.getNativeType(type), registry.getNativeType(VOID_TYPE));
  }

  private JSType createNullableType(JSTypeNative type) {
    return createNullableType(registry.getNativeType(type));
  }

  private JSType createNullableType(JSType type) {
    return registry.createNullableType(type);
  }

  private JSType createUnionType(JSTypeNative type1, JSTypeNative type2) {
    return registry.createUnionType(
        registry.getNativeType(type1), registry.getNativeType(type2));
  }

  private JSType createMultiParamUnionType(JSTypeNative... variants) {
    return registry.createUnionType(variants);
  }

  @Test
  public void testAssumption() {
    assuming("x", NUMBER_TYPE);
    inFunction("");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testVar() {
    inFunction("var x = 1;");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testEmptyVar() {
    inFunction("var x;");
    verify("x", VOID_TYPE);
  }

  @Test
  public void testAssignment() {
    assuming("x", OBJECT_TYPE);
    inFunction("x = 1;");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testExprWithinCast() {
    assuming("x", OBJECT_TYPE);
    inFunction("/** @type {string} */ (x = 1);");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testGetProp() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("x.y();");
    verify("x", OBJECT_TYPE);
  }

  @Test
  public void testGetElemDereference() {
    assuming("x", createUndefinableType(OBJECT_TYPE));
    inFunction("x['z'] = 3;");
    verify("x", OBJECT_TYPE);
  }

  @Test
  public void testIf1() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = {}; if (x) { y = x; }");
    verifySubtypeOf("y", OBJECT_TYPE);
  }

  @Test
  public void testIf1a() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = {}; if (x != null) { y = x; }");
    verifySubtypeOf("y", OBJECT_TYPE);
  }

  @Test
  public void testIf2() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = x; if (x) { y = x; } else { y = {}; }");
    verifySubtypeOf("y", OBJECT_TYPE);
  }

  @Test
  public void testIf3() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = 1; if (x) { y = x; }");
    verify("y", createUnionType(OBJECT_TYPE, NUMBER_TYPE));
  }

  @Test
  public void testPropertyInference1() {
    ObjectType thisType = registry.createAnonymousObjectType(null);
    thisType.defineDeclaredProperty("foo",
        createUndefinableType(STRING_TYPE), null);
    assumingThisType(thisType);
    inFunction("var y = 1; if (this.foo) { y = this.foo; }");
    verify("y", createUnionType(NUMBER_TYPE, STRING_TYPE));
  }

  @Test
  public void testPropertyInference2() {
    ObjectType thisType = registry.createAnonymousObjectType(null);
    thisType.defineDeclaredProperty("foo",
        createUndefinableType(STRING_TYPE), null);
    assumingThisType(thisType);
    inFunction("var y = 1; this.foo = 'x'; y = this.foo;");
    verify("y", STRING_TYPE);
  }

  @Test
  public void testPropertyInference3() {
    ObjectType thisType = registry.createAnonymousObjectType(null);
    thisType.defineDeclaredProperty("foo",
        createUndefinableType(STRING_TYPE), null);
    assumingThisType(thisType);
    inFunction("var y = 1; this.foo = x; y = this.foo;");
    verify("y", createUndefinableType(STRING_TYPE));
  }

  @Test
  public void testAssert1() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assert(x); out2 = x;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  @Test
  public void testAssert1a() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assert(x !== null); out2 = x;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  @Test
  public void testAssert2() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    inFunction("goog.asserts.assert(1, x); out1 = x;");
    verify("out1", startType);
  }

  @Test
  public void testAssert3() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    assuming("y", startType);
    inFunction("out1 = x; goog.asserts.assert(x && y); out2 = x; out3 = y;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
    verify("out3", OBJECT_TYPE);
  }

  @Test
  public void testAssert4() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    assuming("y", startType);
    inFunction("out1 = x; goog.asserts.assert(x && !y); out2 = x; out3 = y;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
    verify("out3", NULL_TYPE);
  }

  @Test
  public void testAssert5() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    assuming("y", startType);
    inFunction("goog.asserts.assert(x || y); out1 = x; out2 = y;");
    verify("out1", startType);
    verify("out2", startType);
  }

  @Test
  public void testAssert6() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x.y", startType);
    inFunction("out1 = x.y; goog.asserts.assert(x.y); out2 = x.y;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  @Test
  public void testAssert7() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; out2 = goog.asserts.assert(x);");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  @Test
  public void testAssert8() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; out2 = goog.asserts.assert(x != null);");
    verify("out1", startType);
    verify("out2", BOOLEAN_TYPE);
  }

  @Test
  public void testAssert9() {
    JSType startType = createNullableType(NUMBER_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; out2 = goog.asserts.assert(y = x);");
    verify("out1", startType);
    verify("out2", NUMBER_TYPE);
  }

  @Test
  public void testAssert10() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    assuming("y", startType);
    inFunction("out1 = x; out2 = goog.asserts.assert(x && y); out3 = x;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
    verify("out3", OBJECT_TYPE);
  }

  @Test
  public void testAssert11() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    assuming("y", startType);
    inFunction("var z = goog.asserts.assert(x || y);");
    verify("x", startType);
    verify("y", startType);
  }

  @Test
  public void testAssertNumber() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertNumber(x); out2 = x;");
    verify("out1", startType);
    verify("out2", NUMBER_TYPE);
  }

  @Test
  public void testAssertNumber2() {
    // Make sure it ignores expressions.
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("goog.asserts.assertNumber(x + x); out1 = x;");
    verify("out1", startType);
  }

  @Test
  public void testAssertNumber3() {
    // Make sure it ignores expressions.
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; out2 = goog.asserts.assertNumber(x + x);");
    verify("out1", startType);
    verify("out2", NUMBER_TYPE);
  }

  @Test
  public void testAssertString() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertString(x); out2 = x;");
    verify("out1", startType);
    verify("out2", STRING_TYPE);
  }

  @Test
  public void testAssertFunction() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertFunction(x); out2 = x;");
    verify("out1", startType);
    verifySubtypeOf("out2", FUNCTION_INSTANCE_TYPE);
  }

  @Test
  public void testAssertObject() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertObject(x); out2 = x;");
    verify("out1", startType);
    verifySubtypeOf("out2", OBJECT_TYPE);
  }

  @Test
  public void testAssertElement() {
    JSType elementType =
        registry.createObjectType("Element", registry.getNativeObjectType(OBJECT_TYPE));
    assuming("x", elementType);
    inFunction("out1 = x; goog.asserts.assertElement(x); out2 = x;");
    verify("out1", elementType);
  }

  @Test
  public void testAssertObject2() {
    JSType startType = createNullableType(ARRAY_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertObject(x); out2 = x;");
    verify("out1", startType);
    verify("out2", ARRAY_TYPE);
  }

  @Test
  public void testAssertObject3() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x.y", startType);
    inFunction("out1 = x.y; goog.asserts.assertObject(x.y); out2 = x.y;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  @Test
  public void testAssertObject4() {
    JSType startType = createNullableType(ARRAY_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; out2 = goog.asserts.assertObject(x);");
    verify("out1", startType);
    verify("out2", ARRAY_TYPE);
  }

  @Test
  public void testAssertObject5() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction(
        "out1 = x;" +
        "out2 = /** @type {!Array} */ (goog.asserts.assertObject(x));");
    verify("out1", startType);
    verify("out2", ARRAY_TYPE);
  }

  @Test
  public void testAssertArray() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertArray(x); out2 = x;");
    verify("out1", startType);
    verifySubtypeOf("out2", ARRAY_TYPE);
  }

  @Test
  public void testAssertInstanceof1() {
    // Test invalid assert (2 params are required)
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertInstanceof(x); out2 = x;");
    verify("out1", startType);
    verify("out2", UNKNOWN_TYPE);
  }

  @Test
  public void testAssertInstanceof2() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertInstanceof(x, String); out2 = x;");
    verify("out1", startType);
    verify("out2", STRING_OBJECT_TYPE);
  }

  @Test
  public void testAssertInstanceof3() {
    JSType unknownType = registry.getNativeType(UNKNOWN_TYPE);
    JSType startType = registry.getNativeType(STRING_TYPE);
    assuming("x", startType);
    assuming("Foo", unknownType);
    inFunction("out1 = x; goog.asserts.assertInstanceof(x, Foo); out2 = x;");
    verify("out1", startType);
    verify("out2", UNKNOWN_TYPE);
  }

  @Test
  public void testAssertInstanceof3a() {
    JSType startType = registry.getNativeType(UNKNOWN_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertInstanceof(x, String); out2 = x;");
    verify("out1", startType);
    verify("out2", STRING_OBJECT_TYPE);
  }

  @Test
  public void testAssertInstanceof4() {
    JSType startType = registry.getNativeType(STRING_OBJECT_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertInstanceof(x, Object); out2 = x;");
    verify("out1", startType);
    verify("out2", STRING_OBJECT_TYPE);
  }

  @Test
  public void testAssertInstanceof5() {
    JSType startType = registry.getNativeType(ALL_TYPE);
    assuming("x", startType);
    inFunction(
        "out1 = x; goog.asserts.assertInstanceof(x, String); var r = x;");
    verify("out1", startType);
    verify("x", STRING_OBJECT_TYPE);
  }

  @Test
  public void testAssertInstanceof6() {
    JSType startType = createUnionType(OBJECT_TYPE,VOID_TYPE);
    assuming("x", startType);
    inFunction(
        "out1 = x; goog.asserts.assertInstanceof(x, String); var r = x;");
    verify("out1", startType);
    verify("x", STRING_OBJECT_TYPE);
  }

  @Test
  public void testAssertInstanceof7() {
    JSType startType = createUnionType(OBJECT_TYPE,VOID_TYPE);
    assuming("x", startType);
    inFunction(
        "out1 = x; var y = goog.asserts.assertInstanceof(x, String); var r = x;");
    verify("out1", startType);
    verify("y", STRING_OBJECT_TYPE);
    verify("r", STRING_OBJECT_TYPE);
    verify("x", STRING_OBJECT_TYPE);
  }

  @Test
  public void testAssertWithIsDefAndNotNull() {
    JSType startType = createNullableType(NUMBER_TYPE);
    assuming("x", startType);
    inFunction(
        "out1 = x;" +
        "goog.asserts.assert(goog.isDefAndNotNull(x));" +
        "out2 = x;");
    verify("out1", startType);
    verify("out2", NUMBER_TYPE);
  }

  @Test
  public void testIsDefAndNoResolvedType() {
    JSType startType = createUndefinableType(NO_RESOLVED_TYPE);
    assuming("x", startType);
    inFunction(
        "out1 = x;" +
        "if (goog.isDef(x)) { out2a = x; out2b = x.length; out2c = x; }" +
        "out3 = x;" +
        "if (goog.isDef(x)) { out4 = x; }");
    verify("out1", startType);
    verify("out2a", NO_RESOLVED_TYPE);
    verify("out2b", CHECKED_UNKNOWN_TYPE);
    verify("out2c", NO_RESOLVED_TYPE);
    verify("out3", startType);
    verify("out4", NO_RESOLVED_TYPE);
  }

  @Test
  public void testAssertWithNotIsNull() {
    JSType startType = createNullableType(NUMBER_TYPE);
    assuming("x", startType);
    inFunction(
        "out1 = x;" +
        "goog.asserts.assert(!goog.isNull(x));" +
        "out2 = x;");
    verify("out1", startType);
    verify("out2", NUMBER_TYPE);
  }

  @Test
  public void testReturn1() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("if (x) { return x; }\nx = {};\nreturn x;");
    verify("x", OBJECT_TYPE);
  }

  @Test
  public void testReturn2() {
    assuming("x", createNullableType(NUMBER_TYPE));
    inFunction("if (!x) { x = 0; }\nreturn x;");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testWhile1() {
    assuming("x", createNullableType(NUMBER_TYPE));
    inFunction("while (!x) { if (x == null) { x = 0; } else { x = 1; } }");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testWhile2() {
    assuming("x", createNullableType(NUMBER_TYPE));
    inFunction("while (!x) { x = {}; }");
    verifySubtypeOf("x", createUnionType(OBJECT_TYPE, NUMBER_TYPE));
  }

  @Test
  public void testDo() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("do { x = 1; } while (!x);");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testFor1() {
    assuming("y", NUMBER_TYPE);
    inFunction("var x = null; var i = null; for (i=y; !i; i=1) { x = 1; }");
    verify("x", createNullableType(NUMBER_TYPE));
    verify("i", NUMBER_TYPE);
  }

  @Test
  public void testForInWithExistingVar() {
    assuming("y", OBJECT_TYPE);
    inFunction(
        lines(
            "var x = null;",
            "var i = null;",
            "for (i in y) {",
            "  I_INSIDE_LOOP: i;",
            "  X_AT_LOOP_START: x;",
            "  x = 1;",
            "  X_AT_LOOP_END: x;",
            "}",
            "X_AFTER_LOOP: x;",
            "I_AFTER_LOOP: i;"));
    assertScopeEnclosing("I_INSIDE_LOOP").declares("i").onClosestHoistScope();
    assertScopeEnclosing("I_INSIDE_LOOP").declares("x").onClosestHoistScope();

    assertTypeOfExpression("I_INSIDE_LOOP").toStringIsEqualTo("string");
    assertTypeOfExpression("I_AFTER_LOOP").toStringIsEqualTo("(null|string)");

    assertTypeOfExpression("X_AT_LOOP_START").toStringIsEqualTo("(null|number)");
    assertTypeOfExpression("X_AT_LOOP_END").toStringIsEqualTo("number");
    assertTypeOfExpression("X_AFTER_LOOP").toStringIsEqualTo("(null|number)");
  }

  @Test
  public void testForInWithRedeclaredVar() {
    assuming("y", OBJECT_TYPE);
    inFunction(
        lines(
            "var i = null;",
            "for (var i in y) {", // i redeclared here, but really the same variable
            "  I_INSIDE_LOOP: i;",
            "}",
            "I_AFTER_LOOP: i;"));
    assertScopeEnclosing("I_INSIDE_LOOP").declares("i").onClosestHoistScope();
    assertTypeOfExpression("I_INSIDE_LOOP").toStringIsEqualTo("string");

    assertScopeEnclosing("I_AFTER_LOOP").declares("i").directly();
    assertTypeOfExpression("I_AFTER_LOOP").toStringIsEqualTo("(null|string)");
  }

  @Test
  public void testForInWithLet() {
    assuming("y", OBJECT_TYPE);
    inFunction(
        lines(
            "FOR_IN_LOOP: for (let i in y) {", // preserve newlines
            "  I_INSIDE_LOOP: i;",
            "}",
            "AFTER_LOOP: 1;",
            ""));
    assertScopeEnclosing("I_INSIDE_LOOP").declares("i").onScopeLabeled("FOR_IN_LOOP");
    assertTypeOfExpression("I_INSIDE_LOOP").toStringIsEqualTo("string");

    assertScopeEnclosing("AFTER_LOOP").doesNotDeclare("i");
  }

  @Test
  public void testForInWithConst() {
    assuming("y", OBJECT_TYPE);
    inFunction(
        lines(
            "FOR_IN_LOOP: for (const i in y) {", // preserve newlines
            "  I_INSIDE_LOOP: i;",
            "}",
            "AFTER_LOOP: 1;",
            ""));
    assertScopeEnclosing("I_INSIDE_LOOP").declares("i").onScopeLabeled("FOR_IN_LOOP");
    assertTypeOfExpression("I_INSIDE_LOOP").toStringIsEqualTo("string");

    assertScopeEnclosing("AFTER_LOOP").doesNotDeclare("i");
  }

  @Test
  public void testFor4() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = {};\n"  +
        "if (x) { for (var i = 0; i < 10; i++) { break; } y = x; }");
    verifySubtypeOf("y", OBJECT_TYPE);
  }

  @Test
  public void testFor5() {
    assuming("y", templatize(
        getNativeObjectType(ARRAY_TYPE),
        ImmutableList.of(getNativeType(NUMBER_TYPE))));
    inFunction(
        "var x = null; for (var i = 0; i < y.length; i++) { x = y[i]; }");
    verify("x", createNullableType(NUMBER_TYPE));
    verify("i", NUMBER_TYPE);
  }

  @Test
  public void testFor6() {
    assuming("y", getNativeObjectType(ARRAY_TYPE));
    inFunction(
        "var x = null;" +
        "for (var i = 0; i < y.length; i++) { " +
        " if (y[i] == 'z') { x = y[i]; } " +
        "}");
    verify("x", getNativeType(UNKNOWN_TYPE));
    verify("i", NUMBER_TYPE);
  }

  @Test
  public void testSwitch1() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; switch(x) {\n" +
        "case 1: y = 1; break;\n" +
        "case 2: y = {};\n" +
        "case 3: y = {};\n" +
        "default: y = 0;}");
    verify("y", NUMBER_TYPE);
  }

  @Test
  public void testSwitch2() {
    assuming("x", ALL_TYPE);
    inFunction("var y = null; switch (typeof x) {\n" +
        "case 'string':\n" +
        "  y = x;\n" +
        "  return;" +
        "default:\n" +
        "  y = 'a';\n" +
        "}");
    verify("y", STRING_TYPE);
  }

  @Test
  public void testSwitch3() {
    assuming("x",
        createNullableType(createUnionType(NUMBER_TYPE, STRING_TYPE)));
    inFunction("var y; var z; switch (typeof x) {\n" +
        "case 'string':\n" +
        "  y = 1; z = null;\n" +
        "  return;\n" +
        "case 'number':\n" +
        "  y = x; z = null;\n" +
        "  return;" +
        "default:\n" +
        "  y = 1; z = x;\n" +
        "}");
    verify("y", NUMBER_TYPE);
    verify("z", NULL_TYPE);
  }

  @Test
  public void testSwitch4() {
    assuming("x", ALL_TYPE);
    inFunction("var y = null; switch (typeof x) {\n" +
        "case 'string':\n" +
        "case 'number':\n" +
        "  y = x;\n" +
        "  return;\n" +
        "default:\n" +
        "  y = 1;\n" +
        "}\n");
    verify("y", createUnionType(NUMBER_TYPE, STRING_TYPE));
  }

  @Test
  public void testCall1() {
    assuming("x",
        createNullableType(
            registry.createFunctionType(registry.getNativeType(NUMBER_TYPE))));
    inFunction("var y = x();");
    verify("y", NUMBER_TYPE);
  }

  @Test
  public void testNew1() {
    assuming("x",
        createNullableType(
            registry.getNativeType(JSTypeNative.U2U_CONSTRUCTOR_TYPE)));
    inFunction("var y = new x();");
    verify("y", UNKNOWN_TYPE);
  }

  @Test
  public void testNew2() {
    inFunction(
        "/**\n" +
        " * @constructor\n" +
        " * @param {T} x\n" +
        " * @template T\n" +
        " */" +
        "function F(x) {}\n" +
        "var x = /** @type {!Array<number>} */ ([]);\n" +
        "var result = new F(x);");

    assertThat(getType("result").toString()).isEqualTo("F<Array<number>>");
  }

  @Test
  public void testNew3() {
    inFunction(
        "/**\n" +
        " * @constructor\n" +
        " * @param {Array<T>} x\n" +
        " * @param {T} y\n" +
        " * @param {S} z\n" +
        " * @template T,S\n" +
        " */" +
        "function F(x,y,z) {}\n" +
        "var x = /** @type {!Array<number>} */ ([]);\n" +
        "var y = /** @type {string} */ ('foo');\n" +
        "var z = /** @type {boolean} */ (true);\n" +
        "var result = new F(x,y,z);");

    assertThat(getType("result").toString()).isEqualTo("F<(number|string),boolean>");
  }

  @Test
  public void testNewRest() {
    inFunction(
        lines(
            "/**",
            " * @constructor",
            " * @param {Array<T>} x",
            " * @param {T} y",
            " * @param {...S} rest",
            " * @template T,S",
            " */",
            "function F(x, y, ...rest) {}",
            "var x = /** @type {!Array<number>} */ ([]);",
            "var y = /** @type {string} */ ('foo');",
            "var z = /** @type {boolean} */ (true);",
            "var result = new F(x,y,z);"));

    assertThat(getType("result").toString()).isEqualTo("F<(number|string),boolean>");
  }

  @Test
  public void testInnerFunction1() {
    inFunction("var x = 1; function f() { x = null; };");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testInnerFunction2() {
    inFunction("var x = 1; var f = function() { x = null; };");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testFunctionDeclarationHasBlockScope() {
    inFunction(
        lines(
            "BLOCK_SCOPE: {",
            "  BEFORE_DEFINITION: f;",
            "  function f() {}",
            "  AFTER_DEFINITION: f;",
            "}",
            "AFTER_BLOCK: f;"));
    // A block-scoped function declaration is hoisted to the beginning of its block, so it is always
    // defined within the block.
    assertScopeEnclosing("BEFORE_DEFINITION").declares("f").onScopeLabeled("BLOCK_SCOPE");
    assertTypeOfExpression("BEFORE_DEFINITION").toStringIsEqualTo("function(): undefined");
    assertTypeOfExpression("AFTER_DEFINITION").toStringIsEqualTo("function(): undefined");
    assertScopeEnclosing("AFTER_BLOCK").doesNotDeclare("f");
  }

  @Test
  public void testHook() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = x ? x : {};");
    verifySubtypeOf("y", OBJECT_TYPE);
  }

  @Test
  public void testThrow() {
    assuming("x", createNullableType(NUMBER_TYPE));
    inFunction("var y = 1;\n" +
        "if (x == null) { throw new Error('x is null') }\n" +
        "y = x;");
    verify("y", NUMBER_TYPE);
  }

  @Test
  public void testTry1() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; try { y = null; } finally { y = x; }");
    verify("y", NUMBER_TYPE);
  }

  @Test
  public void testTry2() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null;\n" +
        "try {  } catch (e) { y = null; } finally { y = x; }");
    verify("y", NUMBER_TYPE);
  }

  @Test
  public void testTry3() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; try { y = x; } catch (e) { }");
    verify("y", NUMBER_TYPE);
  }

  @Test
  public void testCatch1() {
    inFunction("var y = null; try { foo(); } catch (e) { y = e; }");
    verify("y", UNKNOWN_TYPE);
  }

  @Test
  public void testCatch2() {
    inFunction("var y = null; var e = 3; try { foo(); } catch (e) { y = e; }");
    verify("y", UNKNOWN_TYPE);
  }

  @Test
  public void testUnknownType1() {
    inFunction("var y = 3; y = x;");
    verify("y", UNKNOWN_TYPE);
  }

  @Test
  public void testUnknownType2() {
    assuming("x", ARRAY_TYPE);
    inFunction("var y = 5; y = x[0];");
    verify("y", UNKNOWN_TYPE);
  }

  @Test
  public void testInfiniteLoop1() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("x = {}; while(x != null) { x = {}; }");
  }

  @Test
  public void testInfiniteLoop2() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("x = {}; do { x = null; } while (x == null);");
  }

  @Test
  public void testJoin1() {
    JSType unknownOrNull = createUnionType(NULL_TYPE, UNKNOWN_TYPE);
    assuming("x", BOOLEAN_TYPE);
    assuming("unknownOrNull", unknownOrNull);
    inFunction("var y; if (x) y = unknownOrNull; else y = null;");
    verify("y", unknownOrNull);
  }

  @Test
  public void testJoin2() {
    JSType unknownOrNull = createUnionType(NULL_TYPE, UNKNOWN_TYPE);
    assuming("x", BOOLEAN_TYPE);
    assuming("unknownOrNull", unknownOrNull);
    inFunction("var y; if (x) y = null; else y = unknownOrNull;");
    verify("y", unknownOrNull);
  }

  @Test
  public void testArrayLit() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = 3; if (x) { x = [y = x]; }");
    verify("x", createUnionType(NULL_TYPE, ARRAY_TYPE));
    verify("y", createUnionType(NUMBER_TYPE, OBJECT_TYPE));
  }

  @Test
  public void testGetElem() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = 3; if (x) { x = x[y = x]; }");
    verify("x", UNKNOWN_TYPE);
    verify("y", createUnionType(NUMBER_TYPE, OBJECT_TYPE));
  }

  @Test
  public void testEnumRAI1() {
    JSType enumType = createEnumType("MyEnum", ARRAY_TYPE).getElementsType();
    assuming("x", enumType);
    inFunction("var y = null; if (x) y = x;");
    verify("y", createNullableType(enumType));
  }

  @Test
  public void testEnumRAI2() {
    JSType enumType = createEnumType("MyEnum", NUMBER_TYPE).getElementsType();
    assuming("x", enumType);
    inFunction("var y = null; if (typeof x == 'number') y = x;");
    verify("y", createNullableType(enumType));
  }

  @Test
  public void testEnumRAI3() {
    JSType enumType = createEnumType("MyEnum", NUMBER_TYPE).getElementsType();
    assuming("x", enumType);
    inFunction("var y = null; if (x && typeof x == 'number') y = x;");
    verify("y", createNullableType(enumType));
  }

  @Test
  public void testEnumRAI4() {
    JSType enumType = createEnumType("MyEnum",
        createUnionType(STRING_TYPE, NUMBER_TYPE)).getElementsType();
    assuming("x", enumType);
    inFunction("var y = null; if (typeof x == 'number') y = x;");
    verify("y", createNullableType(NUMBER_TYPE));
  }

  @Test
  public void testShortCircuitingAnd() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; if (x && (y = 3)) { }");
    verify("y", createNullableType(NUMBER_TYPE));
  }

  @Test
  public void testShortCircuitingAnd2() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; var z = 4; if (x && (y = 3)) { z = y; }");
    verify("z", NUMBER_TYPE);
  }

  @Test
  public void testShortCircuitingOr() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; if (x || (y = 3)) { }");
    verify("y", createNullableType(NUMBER_TYPE));
  }

  @Test
  public void testShortCircuitingOr2() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; var z = 4; if (x || (y = 3)) { z = y; }");
    verify("z", createNullableType(NUMBER_TYPE));
  }

  @Test
  public void testAssignInCondition() {
    assuming("x", createNullableType(NUMBER_TYPE));
    inFunction("var y; if (!(y = x)) { y = 3; }");
    verify("y", NUMBER_TYPE);
  }

  @Test
  public void testInstanceOf1() {
    assuming("x", OBJECT_TYPE);
    inFunction("var y = null; if (x instanceof String) y = x;");
    verify("y", createNullableType(STRING_OBJECT_TYPE));
  }

  @Test
  public void testInstanceOf2() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = 1; if (x instanceof String) y = x;");
    verify("y", createUnionType(STRING_OBJECT_TYPE, NUMBER_TYPE));
  }

  @Test
  public void testInstanceOf3() {
    assuming("x", createUnionType(STRING_OBJECT_TYPE, NUMBER_OBJECT_TYPE));
    inFunction("var y = null; if (x instanceof String) y = x;");
    verify("y", createNullableType(STRING_OBJECT_TYPE));
  }

  @Test
  public void testInstanceOf4() {
    assuming("x", createUnionType(STRING_OBJECT_TYPE, NUMBER_OBJECT_TYPE));
    inFunction("var y = null; if (x instanceof String); else y = x;");
    verify("y", createNullableType(NUMBER_OBJECT_TYPE));
  }

  @Test
  public void testInstanceOf5() {
    assuming("x", OBJECT_TYPE);
    inFunction("var y = null; if (x instanceof String); else y = x;");
    verify("y", createNullableType(OBJECT_TYPE));
  }

  @Test
  public void testInstanceOf6() {
    // Here we are using "instanceof" to restrict the unknown type to
    // the type of the instance.  This has the following problems:
    //   1) The type may actually be any sub-type
    //   2) The type may implement any interface
    // After the instanceof we will require casts for methods that require
    // sub-type or unrelated interfaces which would not have been required
    // before.
    JSType startType = registry.getNativeType(UNKNOWN_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; if (x instanceof String) out2 = x;");
    verify("out1", startType);
    verify("out2", STRING_OBJECT_TYPE);
  }

  @Test
  public void testUnary() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = +x;");
    verify("y", NUMBER_TYPE);
    inFunction("var z = -x;");
    verify("z", NUMBER_TYPE);
  }

  @Test
  public void testAdd1() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = x + 5;");
    verify("y", NUMBER_TYPE);
  }

  @Test
  public void testAdd2() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = x + '5';");
    verify("y", STRING_TYPE);
  }

  @Test
  public void testAdd3() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = '5' + x;");
    verify("y", STRING_TYPE);
  }

  @Test
  public void testAssignAdd() {
    assuming("x", NUMBER_TYPE);
    inFunction("x += '5';");
    verify("x", STRING_TYPE);
  }

  @Test
  public void testComparison() {
    inFunction("var x = 'foo'; var y = (x = 3) < 4;");
    verify("x", NUMBER_TYPE);
    inFunction("var x = 'foo'; var y = (x = 3) > 4;");
    verify("x", NUMBER_TYPE);
    inFunction("var x = 'foo'; var y = (x = 3) <= 4;");
    verify("x", NUMBER_TYPE);
    inFunction("var x = 'foo'; var y = (x = 3) >= 4;");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testThrownExpression() {
    inFunction("var x = 'foo'; "
               + "try { throw new Error(x = 3); } catch (ex) {}");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testObjectLit() {
    inFunction("var x = {}; var out = x.a;");
    verify("out", UNKNOWN_TYPE);  // Shouldn't this be 'undefined'?

    inFunction("var x = {a:1}; var out = x.a;");
    verify("out", NUMBER_TYPE);

    inFunction("var x = {a:1}; var out = x.a; x.a = 'string'; var out2 = x.a;");
    verify("out", NUMBER_TYPE);
    verify("out2", STRING_TYPE);

    inFunction("var x = { get a() {return 1} }; var out = x.a;");
    verify("out", UNKNOWN_TYPE);

    inFunction(
        "var x = {" +
        "  /** @return {number} */ get a() {return 1}" +
        "};" +
        "var out = x.a;");
    verify("out", NUMBER_TYPE);

    inFunction("var x = { set a(b) {} }; var out = x.a;");
    verify("out", UNKNOWN_TYPE);

    inFunction("var x = { " +
            "/** @param {number} b */ set a(b) {} };" +
            "var out = x.a;");
    verify("out", NUMBER_TYPE);
  }

  @Test
  public void testCast1() {
    inFunction("var x = /** @type {Object} */ (this);");
    verify("x", createNullableType(OBJECT_TYPE));
  }

  @Test
  public void testCast2() {
    inFunction(
        "/** @return {boolean} */" +
        "Object.prototype.method = function() { return true; };" +
        "var x = /** @type {Object} */ (this).method;");
    verify(
        "x",
        registry.createFunctionTypeWithInstanceType(
            registry.getNativeObjectType(OBJECT_TYPE),
            registry.getNativeType(BOOLEAN_TYPE),
            ImmutableList.<JSType>of() /* params */));
  }

  @Test
  public void testBackwardsInferenceCall() {
    inFunction(
        "/** @param {{foo: (number|undefined)}} x */" +
        "function f(x) {}" +
        "var y = {};" +
        "f(y);");

    assertThat(getType("y").toString()).isEqualTo("{foo: (number|undefined)}");
  }

  @Test
  public void testBackwardsInferenceCallRestParameter() {
    inFunction(
        lines(
            "/** @param {...{foo: (number|undefined)}} rest */",
            "function f(...rest) {}",
            "var y = {};",
            "f(y);"));

    assertThat(getType("y").toString()).isEqualTo("{foo: (number|undefined)}");
  }

  @Test
  public void testBackwardsInferenceNew() {
    inFunction(
        "/**\n" +
        " * @constructor\n" +
        " * @param {{foo: (number|undefined)}} x\n" +
        " */" +
        "function F(x) {}" +
        "var y = {};" +
        "new F(y);");

    assertThat(getType("y").toString()).isEqualTo("{foo: (number|undefined)}");
  }

  @Test
  public void testNoThisInference() {
    JSType thisType = createNullableType(OBJECT_TYPE);
    assumingThisType(thisType);
    inFunction("var out = 3; if (goog.isNull(this)) out = this;");
    verify("out", createUnionType(OBJECT_TYPE, NUMBER_TYPE));
  }

  @Test
  public void testRecordInference() {
    inFunction(
        "/** @param {{a: boolean}|{b: string}} x */" +
        "function f(x) {}" +
        "var out = {};" +
        "f(out);");
    assertThat(getType("out").toString())
        .isEqualTo("{a: (boolean|undefined), b: (string|undefined)}");
  }

  @Test
  public void testLotsOfBranchesGettingMerged() {
    String code = "var a = -1;\n";
    code += "switch(foo()) { \n";
    for (int i = 0; i < 100; i++) {
      code += "case " + i + ": a = " + i + "; break; \n";
    }
    code += "default: a = undefined; break;\n";
    code += "}\n";
    inFunction(code);
    assertThat(getType("a").toString()).isEqualTo("(number|undefined)");
  }

  @Test
  public void testIssue785() {
    inFunction("/** @param {string|{prop: (string|undefined)}} x */" +
               "function f(x) {}" +
               "var out = {};" +
               "f(out);");
    assertThat(getType("out").toString()).isEqualTo("{prop: (string|undefined)}");
  }

  @Test
  public void testTemplateForTypeTransformationTests() {
    inFunction(
        "/**\n"
        + " * @param {T} a\n"
        + " * @return {R}\n"
        + " * @template T, R\n"
        + " */\n"
        + "function f(a){}\n"
        + "var result = f(10);");
      verify("result", UNKNOWN_TYPE);
  }

  @Test
  public void testTypeTransformationTypePredicate() {
    inFunction(
        "/**\n"
        + " * @return {R}\n"
        + " * @template R := 'number' =:\n"
        + " */\n"
        + "function f(a){}\n"
        + "var result = f(10);");
      verify("result", NUMBER_TYPE);
  }

  @Test
  public void testTypeTransformationConditional() {
    inFunction(
        "/**\n"
        + " * @param {T} a\n"
        + " * @param {N} b\n"
        + " * @return {R}\n"
        + " * @template T, N\n"
        + " * @template R := cond( eq(T, N), 'string', 'boolean' ) =:\n"
        + " */\n"
        + "function f(a, b){}\n"
        + "var result = f(1, 2);"
        + "var result2 = f(1, 'a');");
      verify("result", STRING_TYPE);
      verify("result2", BOOLEAN_TYPE);
  }

  @Test
  public void testTypeTransformationNoneType() {
    inFunction(
        "/**\n"
        + " * @return {R}\n"
        + " * @template R := none() =:\n"
        + " */\n"
        + "function f(){}\n"
        + "var result = f(10);");
      verify("result", JSTypeNative.UNKNOWN_TYPE);
  }

  @Test
  public void testTypeTransformationUnionType() {
    inFunction(
        "/**\n"
        + " * @param {S} a\n"
        + " * @param {N} b\n"
        + " * @return {R}\n"
        + " * @template S, N\n"
        + " * @template R := union(S, N) =:\n"
        + " */\n"
        + "function f(a, b) {}\n"
        + "var result = f(1, 'a');");
      verify("result", createUnionType(STRING_TYPE, NUMBER_TYPE));
  }

  @Test
  public void testTypeTransformationMapunion() {
    inFunction(
        "/**\n"
        + " * @param {U} a\n"
        + " * @return {R}\n"
        + " * @template U\n"
        + " * @template R :=\n"
        + " * mapunion(U, (x) => cond(eq(x, 'string'), 'boolean', 'null'))\n"
        + " * =:\n"
        + " */\n"
        + "function f(a) {}\n"
        + "/** @type {string|number} */ var x;"
        + "var result = f(x);");
      verify("result", createUnionType(BOOLEAN_TYPE, NULL_TYPE));
  }

  @Test
  public void testTypeTransformationObjectUseCase() {
    inFunction("/** \n"
        + " * @param {T} a\n"
        + " * @return {R}\n"
        + " * @template T \n"
        + " * @template R := \n"
        + " * mapunion(T, (x) => \n"
        + " *      cond(eq(x, 'string'), 'String',\n"
        + " *      cond(eq(x, 'number'), 'Number',\n"
        + " *      cond(eq(x, 'boolean'), 'Boolean',\n"
        + " *      cond(eq(x, 'null'), 'Object', \n"
        + " *      cond(eq(x, 'undefined'), 'Object',\n"
        + " *      x)))))) \n"
        + " * =:\n"
        + " */\n"
        + "function Object(a) {}\n"
        + "/** @type {(string|number|boolean)} */\n"
        + "var o;\n"
        + "var r = Object(o);");
    verify("r", createMultiParamUnionType(STRING_OBJECT_TYPE,
        NUMBER_OBJECT_TYPE, JSTypeNative.BOOLEAN_OBJECT_TYPE));
  }

  @Test
  public void testTypeTransformationObjectUseCase2() {
    inFunction("/** \n"
        + " * @param {T} a\n"
        + " * @return {R}\n"
        + " * @template T \n"
        + " * @template R := \n"
        + " * mapunion(T, (x) => \n"
        + " *      cond(eq(x, 'string'), 'String',\n"
        + " *      cond(eq(x, 'number'), 'Number',\n"
        + " *      cond(eq(x, 'boolean'), 'Boolean',\n"
        + " *      cond(eq(x, 'null'), 'Object', \n"
        + " *      cond(eq(x, 'undefined'), 'Object',\n"
        + " *      x)))))) \n"
        + " * =:\n"
        + " */\n"
        + "function fn(a) {}\n"
        + "/** @type {(string|null|undefined)} */\n"
        + "var o;\n"
        + "var r = fn(o);");
    verify("r", OBJECT_TYPE);
  }

  @Test
  public void testTypeTransformationObjectUseCase3() {
    inFunction("/** \n"
        + " * @param {T} a\n"
        + " * @return {R}\n"
        + " * @template T \n"
        + " * @template R := \n"
        + " * mapunion(T, (x) => \n"
        + " *      cond(eq(x, 'string'), 'String',\n"
        + " *      cond(eq(x, 'number'), 'Number',\n"
        + " *      cond(eq(x, 'boolean'), 'Boolean',\n"
        + " *      cond(eq(x, 'null'), 'Object', \n"
        + " *      cond(eq(x, 'undefined'), 'Object',\n"
        + " *      x)))))) \n"
        + " * =:\n"
        + " */\n"
        + "function fn(a) {}\n"
        + "/** @type {(Array|undefined)} */\n"
        + "var o;\n"
        + "var r = fn(o);");
    verify("r", OBJECT_TYPE);
  }

  @Test
  public void testTypeTransformationTypeOfVarWithInstanceOfConstructor() {
    inFunction("/** @constructor */\n"
        + "function Bar() {}"
        + "var b = new Bar();"
        + "/** \n"
        + " * @return {R}\n"
        + " * @template R := typeOfVar('b') =:\n"
        + " */\n"
        + "function f(){}\n"
        + "var r = f();");
    verify("r", getType("b"));
  }

  @Test
  public void testTypeTransformationTypeOfVarWithConstructor() {
    inFunction("/** @constructor */\n"
        + "function Bar() {}"
        + "/** \n"
        + " * @return {R}\n"
        + " * @template R := typeOfVar('Bar') =:\n"
        + " */\n"
        + "function f(){}\n"
        + "var r = f();");
    verify("r", getType("Bar"));
  }

  @Test
  public void testTypeTransformationTypeOfVarWithTypedef() {
    inFunction("/** @typedef {(string|number)} */\n"
        + "var NumberLike;"
        + "/** @type {!NumberLike} */"
        + "var x;"
        + "/**\n"
        + " * @return {R}\n"
        + " * @template R := typeOfVar('x') =:"
        + " */\n"
        + "function f(){}\n"
        + "var r = f();");
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationWithTypeFromConstructor() {
    inFunction("/** @constructor */\n"
        + "function Bar(){}"
        + "var x = new Bar();"
        + "/** \n"
        + " * @return {R}\n"
        + " * @template R := 'Bar' =:"
        + " */\n"
        + "function f(){}\n"
        + "var r = f();");
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationWithTypeFromTypedef() {
    inFunction("/** @typedef {(string|number)} */\n"
        + "var NumberLike;"
        + "/** @type {!NumberLike} */"
        + "var x;"
        + "/**\n"
        + " * @return {R}\n"
        + " * @template R := 'NumberLike' =:"
        + " */\n"
        + "function f(){}\n"
        + "var r = f();");
    verify("r", createUnionType(STRING_TYPE, NUMBER_TYPE));
  }

  @Test
  public void testTypeTransformationWithTypeFromNamespace() {
    inFunction(
        lines(
            "var wiz",
            "/** @constructor */",
            "wiz.async.Response = function() {};",
            "/**",
            " * @return {R}",
            " * @template R := typeOfVar('wiz.async.Response') =:",
            " */",
            "function f(){}",
            "var r = f();"));
    verify("r", getType("wiz.async.Response"));
  }

  @Test
  public void testTypeTransformationWithNativeTypeExpressionFunction() {
    inFunction("/** @type {function(string, boolean)} */\n"
        + "var x;\n"
        + "/**\n"
        + " * @return {R}\n"
        + " * @template R := typeExpr('function(string, boolean)') =:\n"
        + " */\n"
        + "function f(){}\n"
        + "var r = f();");
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationWithNativeTypeExpressionFunctionReturn() {
    inFunction("/** @type {function(): number} */\n"
        + "var x;\n"
        + "/**\n"
        + " * @return {R}\n"
        + " * @template R := typeExpr('function(): number') =:\n"
        + " */\n"
        + "function f(){}\n"
        + "var r = f();");
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationWithNativeTypeExpressionFunctionThis() {
    inFunction("/** @type {function(this:boolean, string)} */\n"
        + "var x;\n"
        + "/**\n"
        + " * @return {R}\n"
        + " * @template R := typeExpr('function(this:boolean, string)') =:\n"
        + " */\n"
        + "function f(){}\n"
        + "var r = f();");
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationWithNativeTypeExpressionFunctionVarargs() {
    inFunction("/** @type {function(string, ...number): number} */\n"
        + "var x;\n"
        + "/**\n"
        + " * @return {R}\n"
        + " * @template R := typeExpr('function(string, ...number): number') =:\n"
        + " */\n"
        + "function f(){}\n"
        + "var r = f();");
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationWithNativeTypeExpressionFunctionOptional() {
    inFunction("/** @type {function(?string=, number=)} */\n"
        + "var x;\n"
        + "/**\n"
        + " * @return {R}\n"
        + " * @template R := typeExpr('function(?string=, number=)') =:\n"
        + " */\n"
        + "function f(){}\n"
        + "var r = f();");
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationRecordFromObject() {
    inFunction("/** \n"
        + " * @param {T} a\n"
        + " * @return {R}\n"
        + " * @template T \n"
        + " * @template R := record(T) =:"
        + " */\n"
        + "function f(a) {}\n"
        + "/** @type {{foo:?}} */"
        + "var e;"
        + "/** @type {?} */"
        + "var bar;"
        + "var r = f({foo:bar});");
    assertThat(getType("r").isRecordType()).isTrue();
    verify("r", getType("e"));
  }

  @Test
  public void testTypeTransformationRecordFromObjectNested() {
    inFunction("/** \n"
        + " * @param {T} a\n"
        + " * @return {R}\n"
        + " * @template T \n"
        + " * @template R :=\n"
        + " * maprecord(record(T), (k, v) => record({[k]:record(v)})) =:"
        + " */\n"
        + "function f(a) {}\n"
        + "/** @type {{foo:!Object, bar:!Object}} */"
        + "var e;"
        + "var r = f({foo:{}, bar:{}});");
    assertThat(getType("r").isRecordType()).isTrue();
    verify("r", getType("e"));
  }

  @Test
  public void testTypeTransformationRecordFromObjectWithTemplatizedType() {
    inFunction("/** \n"
        + " * @param {T} a\n"
        + " * @return {R}\n"
        + " * @template T \n"
        + " * @template R := record(T) =:"
        + " */\n"
        + "function f(a) {}\n"
        + "/** @type {{foo:!Array<number>}} */"
        + "var e;"
        + "/** @type {!Array<number>} */"
        + "var something;"
        + "var r = f({foo:something});");
    assertThat(getType("r").isRecordType()).isTrue();
    verify("r", getType("e"));
  }

  @Test
  public void testTypeTransformationIsTemplatizedPartially() {
    inFunction(
        Joiner.on('\n').join(
            "/**",
            " * @constructor",
            " * @template T, U",
            " */",
            "function Foo() {}",
            "/**",
            " * @template T := cond(isTemplatized(type('Foo', 'number')), 'number', 'string') =:",
            " * @return {T}",
            " */",
            "function f() { return 123; }",
            "var x = f();"));
    assertThat(getType("x").isNumber()).isTrue();
  }

  @Test
  public void testAssertTypeofProp() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction(
        "goog.asserts.assert(typeof x.prop != 'undefined');" +
        "out = x.prop;");
    verify("out", CHECKED_UNKNOWN_TYPE);
  }

  @Test
  public void testIsArray() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("goog.asserts.assert(Array.isArray(x));");
    verify("x", ARRAY_TYPE);
  }

  @Test
  public void testNotIsArray() {
    assuming("x", createUnionType(ARRAY_TYPE, NUMBER_TYPE));
    inFunction("goog.asserts.assert(!Array.isArray(x));");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testYield1() {
    inGenerator("var x = yield 3;");
    verify("x", registry.getNativeType(UNKNOWN_TYPE));
  }

  @Test
  public void testYield2() {
    // test that type inference happens inside the yield expression
    inGenerator(
        lines(
            "var obj;",
            "yield (obj = {a: 3, b: '4'});",
            "var a = obj.a;",
            "var b = obj.b;"
        ));

    verify("a", registry.getNativeType(NUMBER_TYPE));
    verify("b", registry.getNativeType(STRING_TYPE));
  }

  @Test
  public void testTemplateLiteral1() {
    inFunction("var x = `foobar`; X: x;");
    assertTypeOfExpression("X").isString();
  }

  @Test
  public void testSpreadExpression() {
    inFunction(
        lines(
            "let x = 1;", // x is initially a number
            "let y = [...[x = 'hi', 'there']];", // reassign x a string in the spread
            "X: x;"));
    assertTypeOfExpression("X").toStringIsEqualTo("string");
  }

  @Test
  public void testTaggedTemplateLiteral1() {
    assuming("getNumber", registry.createFunctionType(registry.getNativeType(NUMBER_TYPE)));
    inFunction("var num = getNumber``; NUM: num;");

    assertTypeOfExpression("NUM").isNumber();
  }

  @Test
  public void testRestParamType() {
    parseAndRunTypeInference(
        lines(
            "(",
            "/**", // preserve newlines
            " * @param {...number} nums",
            " */",
            "function(str, ...nums) {",
            "  NUMS: nums;",
            "  let n = null;",
            "  N_START: n;",
            "  if (nums.length > 0) {",
            "    n = nums[0];",
            "    N_IF_TRUE: n;",
            "  } else {",
            "    N_IF_FALSE: n;",
            "  }",
            "  N_FINAL: n;",
            "}",
            ");"));
    assertTypeOfExpression("N_START").toStringIsEqualTo("null");
    assertTypeOfExpression("N_IF_TRUE").toStringIsEqualTo("number");
    assertTypeOfExpression("N_IF_FALSE").toStringIsEqualTo("null");
    assertTypeOfExpression("N_FINAL").toStringIsEqualTo("(null|number)");
  }

  @Test
  public void testObjectDestructuringDeclarationInference() {
    JSType recordType =
        registry.createRecordType(
            ImmutableMap.of(
                "x", getNativeType(STRING_TYPE),
                "y", getNativeType(NUMBER_TYPE)));
    assuming("obj", recordType);

    inFunction(
        lines(
            "let {x, y} = obj; ", // preserve newline
            "X: x;",
            "Y: y;"));
    assertTypeOfExpression("X").toStringIsEqualTo("string");
    assertTypeOfExpression("Y").toStringIsEqualTo("number");

    assertScopeEnclosing("X").declares("x").withTypeThat().toStringIsEqualTo("string");
  }

  @Test
  public void testObjectDestructuringDeclarationInferenceWithDefaultValue() {
    inFunction(
        lines(
            "var /** {x: (?string|undefined)} */ obj;",
            "let {x = 3} = obj; ", // preserve newline
            "X: x;"));
    assertTypeOfExpression("X").toStringIsEqualTo("(null|number|string)");
  }

  @Test
  public void testObjectDestructuringDeclarationInferenceWithUnnecessaryDefaultValue() {
    inFunction(
        lines(
            "var /** {x: string} */ obj;",
            "let {x = 3} = obj; ", // we ignore the default value's type
            "X: x;"));
    // TODO(b/77597706): should this just be `string`?
    // the legacy behavior (typechecking transpiled code) produces (number|string), but we should
    // possibly realize that the default value will never be evaluated.
    assertTypeOfExpression("X").toStringIsEqualTo("(number|string)");
  }

  @Test
  public void testObjectDestructuringDeclarationInference_unknownRhsAndKnownDefaultValue() {
    inFunction(
        lines(
            "var /** ? */ obj;",
            "let {x = 3} = obj; ", // preserve newline
            "X: x;"));
    assertTypeOfExpression("X").toStringIsEqualTo("?");
  }

  @Test
  public void testObjectDestructuringDeclarationInference_knownRhsAndUnknownDefaultValue() {
    inFunction(
        lines(
            "var /** {x: (string|undefined)} */ obj;",
            "let {x = someUnknown} = obj; ", // preserve newline
            "X: x;"));
    assertTypeOfExpression("X").toStringIsEqualTo("?");
  }

  @Test
  public void testObjectDestructuringDeclaration_defaultValueEvaluatedAfterComputedProperty() {
    // contrived example to verify that we traverse the computed property before the default value.

    inFunction(
        lines(
            "var /** !Object<string, (number|undefined)> */ obj = {};",
            "var a = 1;",
            "const {[a = 'string']: b = a} = obj",
            "A: a",
            "B: b"));

    assertTypeOfExpression("A").toStringIsEqualTo("string");
    assertTypeOfExpression("B").toStringIsEqualTo("(number|string)");
  }

  @Test
  public void testObjectDestructuringDeclarationInferenceWithUnknownProperty() {
    JSType recordType = registry.createRecordType(ImmutableMap.of());
    assuming("obj", recordType);

    inFunction(
        lines(
            "let {x} = obj; ", // preserve newline
            "X: x;"));
    assertTypeOfExpression("X").toStringIsEqualTo("?");
  }

  @Test
  public void testObjectDestructuringDoesInferenceWithinComputedProp() {
    inFunction(
        lines(
            "let y = 'foobar'; ", // preserve newline
            "let {[y = 3]: z} = {};",
            "Y: y",
            "Z: z"));

    assertTypeOfExpression("Y").toStringIsEqualTo("number");
    assertTypeOfExpression("Z").toStringIsEqualTo("?");
  }

  @Test
  public void testObjectDestructuringUsesIObjectTypeForComputedProp() {
    inFunction(
        lines(
            "let /** !IObject<string, number> */ myObj = {['foo']: 3}; ", // preserve newline
            "let {[42]: x} = myObj;",
            "X: x"));

    assertTypeOfExpression("X").toStringIsEqualTo("number");
  }

  @Test
  public void testObjectDestructuringDeclarationWithNestedPattern() {
    inFunction(
        lines(
            "let /** {a: {b: number}} */ obj = {a: {b: 3}};", //
            "let {a: {b: x}} = obj;",
            "X: x"));

    assertTypeOfExpression("X").toStringIsEqualTo("number");
  }

  @Test
  public void testObjectDestructuringAssignmentToQualifiedName() {
    inFunction(
        lines(
            "const ns = {};", //
            "({x: ns.x} = {x: 3});",
            "X: ns.x;"));

    assertTypeOfExpression("X").toStringIsEqualTo("number");
  }

  @Test
  public void testObjectDestructuringDeclarationInForOf() {
    inFunction(
        lines(
            "const /** !Iterable<{x: number}> */ data = [{x: 3}];", //
            "for (let {x} of data) {",
            "  X: x;",
            "}"));

    assertTypeOfExpression("X").toStringIsEqualTo("number");
  }

  @Test
  public void testObjectDestructuringAssignInForOf() {
    inFunction(
        lines(
            "const /** !Iterable<{x: number}> */ data = [{x: 3}];", //
            "var x;",
            "for ({x} of data) {",
            "  X: x;",
            "}"));

    assertTypeOfExpression("X").toStringIsEqualTo("number");
  }

  @Test
  public void testObjectDestructuringParameterWithDefaults() {
    parseAndRunTypeInference(
        "(/** @param {{x: (number|undefined)}} data */ function f({x = 3}) { X: x; });");

    assertTypeOfExpression("X").toStringIsEqualTo("number");
  }

  @Test
  public void testObjectRestInferredAsObjectIfGivenUnknownType() {
    assuming("unknown", UNKNOWN_TYPE);
    inFunction("const {a, ...rest} = unknown;  A: a; REST: rest;");

    assertTypeOfExpression("REST").toStringIsEqualTo("Object");
  }

  @Test
  public void testObjectRestInferredGivenRecordType() {
    inFunction("var obj = {a: 1, b: 2, c: 3}; const {a, ...rest} = obj;  A: a; REST: rest;");

    assertTypeOfExpression("A").toStringIsEqualTo("number");
    assertTypeOfExpression("REST").toStringIsEqualTo("{b: number, c: number}");
  }

  @Test
  public void testObjectRestInferredGivenRecordTypeAndComputedProperty() {
    inFunction(
        "var obj =  {a: 1, b: 2, c: 3}; const {['a']: a, ...rest} = obj;  A: a; REST: rest;");

    assertTypeOfExpression("A").toStringIsEqualTo("?");
    assertTypeOfExpression("REST").toStringIsEqualTo("Object");
  }

  @Test
  public void testObjectRestInferredAsTemplatizedObjectType() {
    inFunction("var /** !Object<number, string> */ obj = {}; const {...rest} = obj; REST: rest;");

    assertTypeOfExpression("REST").toStringIsEqualTo("Object<number,string>");
  }

  @Test
  public void testArrayDestructuringDeclaration() {
    inFunction(
        lines(
            "const /** !Iterable<number> */ numbers = [1, 2, 3];",
            "let [x, y] = numbers;",
            "X: x",
            "Y: y"));

    assertTypeOfExpression("X").toStringIsEqualTo("number");
    assertTypeOfExpression("Y").toStringIsEqualTo("number");
  }

  @Test
  public void testArrayDestructuringDeclarationWithDefaultValue() {
    inFunction(
        lines(
            "const /** !Iterable<(number|undefined)> */ numbers = [1, 2, 3];",
            "let [x = 'x', y = 'y'] = numbers;",
            "X: x",
            "Y: y"));

    assertTypeOfExpression("X").toStringIsEqualTo("(number|string)");
    assertTypeOfExpression("Y").toStringIsEqualTo("(number|string)");
  }

  @Test
  public void testArrayDestructuringDeclarationWithDefaultValueForNestedPattern() {
    inFunction(
        lines(
            "const /** !Iterable<({x: number}|undefined)> */ xNumberObjs = [];",
            "let [{x = 'foo'} = {}] = xNumberObjs;",
            "X: x",
            "Y: y"));

    assertTypeOfExpression("X").toStringIsEqualTo("(number|string)");
  }

  @Test
  public void testArrayDestructuringDeclarationWithRest() {
    inFunction(
        lines(
            "const /** !Iterable<number> */ numbers = [1, 2, 3];",
            "let [x, ...y] = numbers;",
            "X: x",
            "Y: y"));

    assertTypeOfExpression("X").toStringIsEqualTo("number");
    assertTypeOfExpression("Y").toStringIsEqualTo("Array<number>");
  }

  @Test
  public void testArrayDestructuringDeclarationWithNestedArrayPattern() {
    inFunction(
        lines(
            "const /** !Iterable<!Iterable<number>> */ numbers = [[1, 2, 3]];",
            "let [[x], y] = numbers;",
            "X: x",
            "Y: y"));

    assertTypeOfExpression("X").toStringIsEqualTo("number");
    assertTypeOfExpression("Y").toStringIsEqualTo("Iterable<number>");
  }

  @Test
  public void testArrayDestructuringDeclarationWithNestedObjectPattern() {
    inFunction(
        lines(
            "const /** !Iterable<{x: number}> */ numbers = [{x: 3}, {x: 4}];",
            "let [{x}, {x: y}] = numbers;",
            "X: x",
            "Y: y"));

    assertTypeOfExpression("X").toStringIsEqualTo("number");
    assertTypeOfExpression("Y").toStringIsEqualTo("number");
  }

  @Test
  public void testArrayDestructuringDeclarationWithNonIterableRhs() {
    // TODO(lharker): make sure TypeCheck warns on this
    inFunction("let [x] = 3; X: x;");

    assertTypeOfExpression("X").toStringIsEqualTo("?");
  }

  @Test
  public void testArrayDestructuringAssignWithGetProp() {
    inFunction(
        lines(
            "const ns = {};", //
            "const /** !Iterable<number> */ numbers = [1, 2, 3];",
            "[ns.x] = numbers;",
            "NSX: ns.x;"));

    assertTypeOfExpression("NSX").toStringIsEqualTo("number");
  }

  @Test
  public void testArrayDestructuringAssignWithGetElem() {
    // we don't update the scope on an assignment to a getelem, so this test just verifies that
    // a) type inference doesn't crash and b) type info validation passes.
    inFunction(
        lines(
            "const arr = [];", //
            "const /** !Iterable<number> */ numbers = [1, 2, 3];",
            "[arr[1]] = numbers;",
            "ARR1: arr[1];"));

    assertTypeOfExpression("ARR1").toStringIsEqualTo("?");
  }

  @Test
  public void testDeclarationDoesntOverrideInferredTypeInDestructuringPattern() {
    inFunction("var [/** number */ x] = /** @type {?} */ ([null]); X: x");

    assertTypeOfExpression("X").toStringIsEqualTo("number");
  }

  @Test
  public void testDeclarationDoesntOverrideInferredTypeInForOfLoop() {
    inFunction("for (var /** number */ x of /** @type {?} */ [null]) { X: x; }");

    assertTypeOfExpression("X").toStringIsEqualTo("number");
  }

  @Test
  public void testTypeInferenceOccursInDestructuringCatch() {
    assuming("x", NUMBER_TYPE);

    inFunction(
        lines(
            "try {",
            "  throw {err: 3}; ",
            "} catch ({[x = 'err']: /** number */ err}) {",
            "  ERR: err;",
            "  X: x;",
            "}"));

    assertTypeOfExpression("ERR").toStringIsEqualTo("number");
    // verify we do inference on the assignment to `x` inside the computed property
    assertTypeOfExpression("X").toStringIsEqualTo("string");
  }

  @Test
  public void testTypeInferenceOccursInDestructuringForIn() {
    assuming("x", NUMBER_TYPE);

    inFunction(
        lines(
            "/** @type {number} */",
            "String.prototype.length;",
            "",
            "var obj = {};",
            "for ({length: obj.length} in {'1': 1, '22': 22}) {",
            "  LENGTH: obj.length;", // set to '1'.length and '22'.length
            "}"));

    assertTypeOfExpression("LENGTH").toStringIsEqualTo("number");
  }

  @Test
  public void testTypeInferenceOccursInsideVoidOperator() {
    inFunction("var x; var y = void (x = 3); X: x; Y: y");

    assertTypeOfExpression("X").toStringIsEqualTo("number");
    assertTypeOfExpression("Y").toStringIsEqualTo("undefined");
  }

  private ObjectType getNativeObjectType(JSTypeNative t) {
    return registry.getNativeObjectType(t);
  }

  private JSType getNativeType(JSTypeNative t) {
    return registry.getNativeType(t);
  }

  private JSType templatize(ObjectType objType, ImmutableList<JSType> t) {
    return registry.createTemplatizedType(objType, t);
  }
}
