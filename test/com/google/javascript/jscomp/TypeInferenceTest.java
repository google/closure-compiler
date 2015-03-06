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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionSpec;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DataFlowAnalysis.BranchedFlowState;
import com.google.javascript.jscomp.type.FlowScope;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedSlot;
import com.google.javascript.rhino.testing.Asserts;

import junit.framework.TestCase;

import java.util.Map;

/**
 * Tests {@link TypeInference}.
 *
 */
public class TypeInferenceTest extends TestCase {

  private Compiler compiler;
  private JSTypeRegistry registry;
  private Map<String, JSType> assumptions;
  private JSType assumedThisType;
  private FlowScope returnScope;
  private static final Map<String, AssertionFunctionSpec>
      ASSERTION_FUNCTION_MAP = Maps.newHashMap();
  static {
    for (AssertionFunctionSpec func :
        new ClosureCodingConvention().getAssertionFunctions()) {
      ASSERTION_FUNCTION_MAP.put(func.getFunctionName(), func);
    }
  }

  @Override
  public void setUp() {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    compiler.initOptions(options);
    registry = compiler.getTypeRegistry();
    assumptions = Maps.newHashMap();
    returnScope = null;
  }

  private void assumingThisType(JSType type) {
    assumedThisType = type;
  }

  private void assuming(String name, JSType type) {
    assumptions.put(name, type);
  }

  private void assuming(String name, JSTypeNative type) {
    assuming(name, registry.getNativeType(type));
  }

  private void inFunction(String js) {
    // Parse the body of the function.
    String thisBlock = assumedThisType == null
        ? ""
        : "/** @this {" + assumedThisType + "} */";
    Node root = compiler.parseTestCode(
        "(" + thisBlock + " function() {" + js + "});");
    assertEquals("parsing error: " +
        Joiner.on(", ").join(compiler.getErrors()),
        0, compiler.getErrorCount());

    Node n = root.getFirstChild().getFirstChild();
    // Create the scope with the assumptions.
    TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);
    TypedScope assumedScope = scopeCreator.createScope(
        n, scopeCreator.createScope(root, null));
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
        ASSERTION_FUNCTION_MAP);
    dfa.analyze();
    // Get the scope of the implicit return.
    BranchedFlowState<FlowScope> rtnState =
        cfg.getImplicitReturn().getAnnotation();
    returnScope = rtnState.getIn();
  }

  private JSType getType(String name) {
    assertNotNull("The return scope should not be null.", returnScope);
    StaticTypedSlot<JSType> var = returnScope.getSlot(name);
    assertNotNull("The variable " + name + " is missing from the scope.", var);
    return var.getType();
  }

  private void verify(String name, JSType type) {
    Asserts.assertTypeEquals("Mismatch for " + name, type, getType(name));
  }

  private void verify(String name, JSTypeNative type) {
    verify(name, registry.getNativeType(type));
  }

  private void verifySubtypeOf(String name, JSType type) {
    JSType varType = getType(name);
    assertNotNull("The variable " + name + " is missing a type.", varType);
    assertTrue("The type " + varType + " of variable " + name +
        " is not a subtype of " + type +".", varType.isSubtype(type));
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

  public void testAssumption() {
    assuming("x", NUMBER_TYPE);
    inFunction("");
    verify("x", NUMBER_TYPE);
  }

  public void testVar() {
    inFunction("var x = 1;");
    verify("x", NUMBER_TYPE);
  }

  public void testEmptyVar() {
    inFunction("var x;");
    verify("x", VOID_TYPE);
  }

  public void testAssignment() {
    assuming("x", OBJECT_TYPE);
    inFunction("x = 1;");
    verify("x", NUMBER_TYPE);
  }

  public void testExprWithinCast() {
    assuming("x", OBJECT_TYPE);
    inFunction("/** @type {string} */ (x = 1);");
    verify("x", NUMBER_TYPE);
  }

  public void testGetProp() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("x.y();");
    verify("x", OBJECT_TYPE);
  }

  public void testGetElemDereference() {
    assuming("x", createUndefinableType(OBJECT_TYPE));
    inFunction("x['z'] = 3;");
    verify("x", OBJECT_TYPE);
  }

  public void testIf1() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = {}; if (x) { y = x; }");
    verifySubtypeOf("y", OBJECT_TYPE);
  }

  public void testIf1a() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = {}; if (x != null) { y = x; }");
    verifySubtypeOf("y", OBJECT_TYPE);
  }

  public void testIf2() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = x; if (x) { y = x; } else { y = {}; }");
    verifySubtypeOf("y", OBJECT_TYPE);
  }

  public void testIf3() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = 1; if (x) { y = x; }");
    verify("y", createUnionType(OBJECT_TYPE, NUMBER_TYPE));
  }

  public void testPropertyInference1() {
    ObjectType thisType = registry.createAnonymousObjectType(null);
    thisType.defineDeclaredProperty("foo",
        createUndefinableType(STRING_TYPE), null);
    assumingThisType(thisType);
    inFunction("var y = 1; if (this.foo) { y = this.foo; }");
    verify("y", createUnionType(NUMBER_TYPE, STRING_TYPE));
  }

  public void testPropertyInference2() {
    ObjectType thisType = registry.createAnonymousObjectType(null);
    thisType.defineDeclaredProperty("foo",
        createUndefinableType(STRING_TYPE), null);
    assumingThisType(thisType);
    inFunction("var y = 1; this.foo = 'x'; y = this.foo;");
    verify("y", STRING_TYPE);
  }

  public void testPropertyInference3() {
    ObjectType thisType = registry.createAnonymousObjectType(null);
    thisType.defineDeclaredProperty("foo",
        createUndefinableType(STRING_TYPE), null);
    assumingThisType(thisType);
    inFunction("var y = 1; this.foo = x; y = this.foo;");
    verify("y", CHECKED_UNKNOWN_TYPE);
  }

  public void testAssert1() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assert(x); out2 = x;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  public void testAssert1a() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assert(x !== null); out2 = x;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  public void testAssert2() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    inFunction("goog.asserts.assert(1, x); out1 = x;");
    verify("out1", startType);
  }

  public void testAssert3() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    assuming("y", startType);
    inFunction("out1 = x; goog.asserts.assert(x && y); out2 = x; out3 = y;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
    verify("out3", OBJECT_TYPE);
  }

  public void testAssert4() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    assuming("y", startType);
    inFunction("out1 = x; goog.asserts.assert(x && !y); out2 = x; out3 = y;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
    verify("out3", NULL_TYPE);
  }

  public void testAssert5() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    assuming("y", startType);
    inFunction("goog.asserts.assert(x || y); out1 = x; out2 = y;");
    verify("out1", startType);
    verify("out2", startType);
  }

  public void testAssert6() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x.y", startType);
    inFunction("out1 = x.y; goog.asserts.assert(x.y); out2 = x.y;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  public void testAssert7() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; out2 = goog.asserts.assert(x);");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  public void testAssert8() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; out2 = goog.asserts.assert(x != null);");
    verify("out1", startType);
    verify("out2", BOOLEAN_TYPE);
  }

  public void testAssert9() {
    JSType startType = createNullableType(NUMBER_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; out2 = goog.asserts.assert(y = x);");
    verify("out1", startType);
    verify("out2", NUMBER_TYPE);
  }

  public void testAssert10() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    assuming("y", startType);
    inFunction("out1 = x; out2 = goog.asserts.assert(x && y); out3 = x;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
    verify("out3", OBJECT_TYPE);
  }

  public void testAssert11() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    assuming("y", startType);
    inFunction("var z = goog.asserts.assert(x || y);");
    verify("x", startType);
    verify("y", startType);
  }

  public void testAssertNumber() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertNumber(x); out2 = x;");
    verify("out1", startType);
    verify("out2", NUMBER_TYPE);
  }

  public void testAssertNumber2() {
    // Make sure it ignores expressions.
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("goog.asserts.assertNumber(x + x); out1 = x;");
    verify("out1", startType);
  }

  public void testAssertNumber3() {
    // Make sure it ignores expressions.
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; out2 = goog.asserts.assertNumber(x + x);");
    verify("out1", startType);
    verify("out2", NUMBER_TYPE);
  }

  public void testAssertString() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertString(x); out2 = x;");
    verify("out1", startType);
    verify("out2", STRING_TYPE);
  }

  public void testAssertFunction() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertFunction(x); out2 = x;");
    verify("out1", startType);
    verifySubtypeOf("out2", FUNCTION_INSTANCE_TYPE);
  }

  public void testAssertObject() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertObject(x); out2 = x;");
    verify("out1", startType);
    verifySubtypeOf("out2", OBJECT_TYPE);
  }

  public void testAssertElement() {
    JSType elementType = registry.createObjectType("Element", null,
        registry.getNativeObjectType(OBJECT_TYPE));
    assuming("x", elementType);
    inFunction("out1 = x; goog.asserts.assertElement(x); out2 = x;");
    verify("out1", elementType);
  }

  public void testAssertObject2() {
    JSType startType = createNullableType(ARRAY_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertObject(x); out2 = x;");
    verify("out1", startType);
    verify("out2", ARRAY_TYPE);
  }

  public void testAssertObject3() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x.y", startType);
    inFunction("out1 = x.y; goog.asserts.assertObject(x.y); out2 = x.y;");
    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  public void testAssertObject4() {
    JSType startType = createNullableType(ARRAY_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; out2 = goog.asserts.assertObject(x);");
    verify("out1", startType);
    verify("out2", ARRAY_TYPE);
  }

  public void testAssertObject5() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction(
        "out1 = x;" +
        "out2 = /** @type {!Array} */ (goog.asserts.assertObject(x));");
    verify("out1", startType);
    verify("out2", ARRAY_TYPE);
  }

  public void testAssertArray() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertArray(x); out2 = x;");
    verify("out1", startType);
    verifySubtypeOf("out2", ARRAY_TYPE);
  }

  public void testAssertInstanceof1() {
    // Test invalid assert (2 params are required)
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertInstanceof(x); out2 = x;");
    verify("out1", startType);
    verify("out2", UNKNOWN_TYPE);
  }

  public void testAssertInstanceof2() {
    JSType startType = createNullableType(ALL_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertInstanceof(x, String); out2 = x;");
    verify("out1", startType);
    verify("out2", STRING_OBJECT_TYPE);
  }

  public void testAssertInstanceof3() {
    JSType unknownType = registry.getNativeType(UNKNOWN_TYPE);
    JSType startType = registry.getNativeType(STRING_TYPE);
    assuming("x", startType);
    assuming("Foo", unknownType);
    inFunction("out1 = x; goog.asserts.assertInstanceof(x, Foo); out2 = x;");
    verify("out1", startType);
    verify("out2", UNKNOWN_TYPE);
  }

  public void testAssertInstanceof3a() {
    JSType startType = registry.getNativeType(UNKNOWN_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertInstanceof(x, String); out2 = x;");
    verify("out1", startType);
    verify("out2", STRING_OBJECT_TYPE);
  }

  public void testAssertInstanceof4() {
    JSType startType = registry.getNativeType(STRING_OBJECT_TYPE);
    assuming("x", startType);
    inFunction("out1 = x; goog.asserts.assertInstanceof(x, Object); out2 = x;");
    verify("out1", startType);
    verify("out2", STRING_OBJECT_TYPE);
  }

  public void testAssertInstanceof5() {
    JSType startType = registry.getNativeType(ALL_TYPE);
    assuming("x", startType);
    inFunction(
        "out1 = x; goog.asserts.assertInstanceof(x, String); var r = x;");
    verify("out1", startType);
    verify("x", STRING_OBJECT_TYPE);
  }

  public void testAssertInstanceof6() {
    JSType startType = createUnionType(OBJECT_TYPE,VOID_TYPE);
    assuming("x", startType);
    inFunction(
        "out1 = x; goog.asserts.assertInstanceof(x, String); var r = x;");
    verify("out1", startType);
    verify("x", STRING_OBJECT_TYPE);
  }

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

  public void testReturn1() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("if (x) { return x; }\nx = {};\nreturn x;");
    verify("x", OBJECT_TYPE);
  }

  public void testReturn2() {
    assuming("x", createNullableType(NUMBER_TYPE));
    inFunction("if (!x) { x = 0; }\nreturn x;");
    verify("x", NUMBER_TYPE);
  }

  public void testWhile1() {
    assuming("x", createNullableType(NUMBER_TYPE));
    inFunction("while (!x) { if (x == null) { x = 0; } else { x = 1; } }");
    verify("x", NUMBER_TYPE);
  }

  public void testWhile2() {
    assuming("x", createNullableType(NUMBER_TYPE));
    inFunction("while (!x) { x = {}; }");
    verifySubtypeOf("x", createUnionType(OBJECT_TYPE, NUMBER_TYPE));
  }

  public void testDo() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("do { x = 1; } while (!x);");
    verify("x", NUMBER_TYPE);
  }

  public void testFor1() {
    assuming("y", NUMBER_TYPE);
    inFunction("var x = null; var i = null; for (i=y; !i; i=1) { x = 1; }");
    verify("x", createNullableType(NUMBER_TYPE));
    verify("i", NUMBER_TYPE);
  }

  public void testFor2() {
    assuming("y", OBJECT_TYPE);
    inFunction("var x = null; var i = null; for (i in y) { x = 1; }");
    verify("x", createNullableType(NUMBER_TYPE));
    verify("i", createNullableType(STRING_TYPE));
  }

  public void testFor3() {
    assuming("y", OBJECT_TYPE);
    inFunction("var x = null; var i = null; for (var i in y) { x = 1; }");
    verify("x", createNullableType(NUMBER_TYPE));
    verify("i", createNullableType(STRING_TYPE));
  }

  public void testFor4() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = {};\n"  +
        "if (x) { for (var i = 0; i < 10; i++) { break; } y = x; }");
    verifySubtypeOf("y", OBJECT_TYPE);
  }

  public void testFor5() {
    assuming("y", templatize(
        getNativeObjectType(ARRAY_TYPE),
        ImmutableList.of(getNativeType(NUMBER_TYPE))));
    inFunction(
        "var x = null; for (var i = 0; i < y.length; i++) { x = y[i]; }");
    verify("x", createNullableType(NUMBER_TYPE));
    verify("i", NUMBER_TYPE);
  }

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

  public void testSwitch1() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; switch(x) {\n" +
        "case 1: y = 1; break;\n" +
        "case 2: y = {};\n" +
        "case 3: y = {};\n" +
        "default: y = 0;}");
    verify("y", NUMBER_TYPE);
  }

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

  public void testCall1() {
    assuming("x",
        createNullableType(
            registry.createFunctionType(registry.getNativeType(NUMBER_TYPE))));
    inFunction("var y = x();");
    verify("y", NUMBER_TYPE);
  }

  public void testNew1() {
    assuming("x",
        createNullableType(
            registry.getNativeType(JSTypeNative.U2U_CONSTRUCTOR_TYPE)));
    inFunction("var y = new x();");
    verify("y", UNKNOWN_TYPE);
  }

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

    assertEquals("F<Array<number>>", getType("result").toString());
  }

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

    assertEquals("F<(number|string),boolean>", getType("result").toString());
  }

  public void testInnerFunction1() {
    inFunction("var x = 1; function f() { x = null; };");
    verify("x", NUMBER_TYPE);
  }

  public void testInnerFunction2() {
    inFunction("var x = 1; var f = function() { x = null; };");
    verify("x", NUMBER_TYPE);
  }

  public void testHook() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = x ? x : {};");
    verifySubtypeOf("y", OBJECT_TYPE);
  }

  public void testThrow() {
    assuming("x", createNullableType(NUMBER_TYPE));
    inFunction("var y = 1;\n" +
        "if (x == null) { throw new Error('x is null') }\n" +
        "y = x;");
    verify("y", NUMBER_TYPE);
  }

  public void testTry1() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; try { y = null; } finally { y = x; }");
    verify("y", NUMBER_TYPE);
  }

  public void testTry2() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null;\n" +
        "try {  } catch (e) { y = null; } finally { y = x; }");
    verify("y", NUMBER_TYPE);
  }

  public void testTry3() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; try { y = x; } catch (e) { }");
    verify("y", NUMBER_TYPE);
  }

  public void testCatch1() {
    inFunction("var y = null; try { foo(); } catch (e) { y = e; }");
    verify("y", UNKNOWN_TYPE);
  }

  public void testCatch2() {
    inFunction("var y = null; var e = 3; try { foo(); } catch (e) { y = e; }");
    verify("y", UNKNOWN_TYPE);
  }

  public void testUnknownType1() {
    inFunction("var y = 3; y = x;");
    verify("y", UNKNOWN_TYPE);
  }

  public void testUnknownType2() {
    assuming("x", ARRAY_TYPE);
    inFunction("var y = 5; y = x[0];");
    verify("y", UNKNOWN_TYPE);
  }

  public void testInfiniteLoop1() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("x = {}; while(x != null) { x = {}; }");
  }

  public void testInfiniteLoop2() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("x = {}; do { x = null; } while (x == null);");
  }

  public void testJoin1() {
    JSType unknownOrNull = createUnionType(NULL_TYPE, UNKNOWN_TYPE);
    assuming("x", BOOLEAN_TYPE);
    assuming("unknownOrNull", unknownOrNull);
    inFunction("var y; if (x) y = unknownOrNull; else y = null;");
    verify("y", unknownOrNull);
  }

  public void testJoin2() {
    JSType unknownOrNull = createUnionType(NULL_TYPE, UNKNOWN_TYPE);
    assuming("x", BOOLEAN_TYPE);
    assuming("unknownOrNull", unknownOrNull);
    inFunction("var y; if (x) y = null; else y = unknownOrNull;");
    verify("y", unknownOrNull);
  }

  public void testArrayLit() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = 3; if (x) { x = [y = x]; }");
    verify("x", createUnionType(NULL_TYPE, ARRAY_TYPE));
    verify("y", createUnionType(NUMBER_TYPE, OBJECT_TYPE));
  }

  public void testGetElem() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = 3; if (x) { x = x[y = x]; }");
    verify("x", UNKNOWN_TYPE);
    verify("y", createUnionType(NUMBER_TYPE, OBJECT_TYPE));
  }

  public void testEnumRAI1() {
    JSType enumType = createEnumType("MyEnum", ARRAY_TYPE).getElementsType();
    assuming("x", enumType);
    inFunction("var y = null; if (x) y = x;");
    verify("y", createNullableType(enumType));
  }

  public void testEnumRAI2() {
    JSType enumType = createEnumType("MyEnum", NUMBER_TYPE).getElementsType();
    assuming("x", enumType);
    inFunction("var y = null; if (typeof x == 'number') y = x;");
    verify("y", createNullableType(enumType));
  }

  public void testEnumRAI3() {
    JSType enumType = createEnumType("MyEnum", NUMBER_TYPE).getElementsType();
    assuming("x", enumType);
    inFunction("var y = null; if (x && typeof x == 'number') y = x;");
    verify("y", createNullableType(enumType));
  }

  public void testEnumRAI4() {
    JSType enumType = createEnumType("MyEnum",
        createUnionType(STRING_TYPE, NUMBER_TYPE)).getElementsType();
    assuming("x", enumType);
    inFunction("var y = null; if (typeof x == 'number') y = x;");
    verify("y", createNullableType(NUMBER_TYPE));
  }

  public void testShortCircuitingAnd() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; if (x && (y = 3)) { }");
    verify("y", createNullableType(NUMBER_TYPE));
  }

  public void testShortCircuitingAnd2() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; var z = 4; if (x && (y = 3)) { z = y; }");
    verify("z", NUMBER_TYPE);
  }

  public void testShortCircuitingOr() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; if (x || (y = 3)) { }");
    verify("y", createNullableType(NUMBER_TYPE));
  }

  public void testShortCircuitingOr2() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; var z = 4; if (x || (y = 3)) { z = y; }");
    verify("z", createNullableType(NUMBER_TYPE));
  }

  public void testAssignInCondition() {
    assuming("x", createNullableType(NUMBER_TYPE));
    inFunction("var y; if (!(y = x)) { y = 3; }");
    verify("y", NUMBER_TYPE);
  }

  public void testInstanceOf1() {
    assuming("x", OBJECT_TYPE);
    inFunction("var y = null; if (x instanceof String) y = x;");
    verify("y", createNullableType(STRING_OBJECT_TYPE));
  }

  public void testInstanceOf2() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = 1; if (x instanceof String) y = x;");
    verify("y", createUnionType(STRING_OBJECT_TYPE, NUMBER_TYPE));
  }

  public void testInstanceOf3() {
    assuming("x", createUnionType(STRING_OBJECT_TYPE, NUMBER_OBJECT_TYPE));
    inFunction("var y = null; if (x instanceof String) y = x;");
    verify("y", createNullableType(STRING_OBJECT_TYPE));
  }

  public void testInstanceOf4() {
    assuming("x", createUnionType(STRING_OBJECT_TYPE, NUMBER_OBJECT_TYPE));
    inFunction("var y = null; if (x instanceof String); else y = x;");
    verify("y", createNullableType(NUMBER_OBJECT_TYPE));
  }

  public void testInstanceOf5() {
    assuming("x", OBJECT_TYPE);
    inFunction("var y = null; if (x instanceof String); else y = x;");
    verify("y", createNullableType(OBJECT_TYPE));
  }

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

  public void testFlattening() {
    for (int i = 0; i < LinkedFlowScope.MAX_DEPTH + 1; i++) {
      assuming("s" + i, ALL_TYPE);
    }
    assuming("b", JSTypeNative.BOOLEAN_TYPE);
    StringBuilder body = new StringBuilder();
    body.append("if (b) {");
    for (int i = 0; i < LinkedFlowScope.MAX_DEPTH + 1; i++) {
      body.append("s");
      body.append(i);
      body.append(" = 1;\n");
    }
    body.append(" } else { ");
    for (int i = 0; i < LinkedFlowScope.MAX_DEPTH + 1; i++) {
      body.append("s");
      body.append(i);
      body.append(" = 'ONE';\n");
    }
    body.append("}");
    JSType numberORString = createUnionType(NUMBER_TYPE, STRING_TYPE);
    inFunction(body.toString());

    for (int i = 0; i < LinkedFlowScope.MAX_DEPTH + 1; i++) {
      verify("s" + i, numberORString);
    }
  }

  public void testUnary() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = +x;");
    verify("y", NUMBER_TYPE);
    inFunction("var z = -x;");
    verify("z", NUMBER_TYPE);
  }

  public void testAdd1() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = x + 5;");
    verify("y", NUMBER_TYPE);
  }

  public void testAdd2() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = x + '5';");
    verify("y", STRING_TYPE);
  }

  public void testAdd3() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = '5' + x;");
    verify("y", STRING_TYPE);
  }

  public void testAssignAdd() {
    assuming("x", NUMBER_TYPE);
    inFunction("x += '5';");
    verify("x", STRING_TYPE);
  }

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

  public void testThrownExpression() {
    inFunction("var x = 'foo'; "
               + "try { throw new Error(x = 3); } catch (ex) {}");
    verify("x", NUMBER_TYPE);
  }

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

  public void testCast1() {
    inFunction("var x = /** @type {Object} */ (this);");
    verify("x", createNullableType(OBJECT_TYPE));
  }

  public void testCast2() {
    inFunction(
        "/** @return {boolean} */" +
        "Object.prototype.method = function() { return true; };" +
        "var x = /** @type {Object} */ (this).method;");
    verify(
        "x",
        registry.createFunctionType(
            registry.getNativeObjectType(OBJECT_TYPE),
            registry.getNativeType(BOOLEAN_TYPE),
            ImmutableList.<JSType>of() /* params */));
  }

  public void testBackwardsInferenceCall() {
    inFunction(
        "/** @param {{foo: (number|undefined)}} x */" +
        "function f(x) {}" +
        "var y = {};" +
        "f(y);");

    assertEquals("{foo: (number|undefined)}", getType("y").toString());
  }

  public void testBackwardsInferenceNew() {
    inFunction(
        "/**\n" +
        " * @constructor\n" +
        " * @param {{foo: (number|undefined)}} x\n" +
        " */" +
        "function F(x) {}" +
        "var y = {};" +
        "new F(y);");

    assertEquals("{foo: (number|undefined)}", getType("y").toString());
  }

  public void testNoThisInference() {
    JSType thisType = createNullableType(OBJECT_TYPE);
    assumingThisType(thisType);
    inFunction("var out = 3; if (goog.isNull(this)) out = this;");
    verify("out", createUnionType(OBJECT_TYPE, NUMBER_TYPE));
  }

  public void testRecordInference() {
    inFunction(
        "/** @param {{a: (boolean|undefined)}|{b: (string|undefined)}} x */" +
        "function f(x) {}" +
        "var out = {};" +
        "f(out);");
    assertEquals("{a: (boolean|undefined), b: (string|undefined)}",
        getType("out").toString());
  }

  public void testLotsOfBranchesGettingMerged() {
    String code = "var a = -1;\n";
    code += "switch(foo()) { \n";
    for (int i = 0; i < 100; i++) {
      code += "case " + i + ": a = " + i + "; break; \n";
    }
    code += "default: a = undefined; break;\n";
    code += "}\n";
    inFunction(code);
    assertEquals("(number|undefined)", getType("a").toString());
  }

  public void testIssue785() {
    inFunction("/** @param {string|{prop: (string|undefined)}} x */" +
               "function f(x) {}" +
               "var out = {};" +
               "f(out);");
    assertEquals("{prop: (string|undefined)}", getType("out").toString());
  }

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

  public void testTypeTransformationNoneType() {
    inFunction(
        "/**\n"
        + " * @return {R}\n"
        + " * @template R := none() =:\n"
        + " */\n"
        + "function f(){}\n"
        + "var result = f(10);");
      verify("result", JSTypeNative.NO_TYPE);
  }

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
        + "function Object(a) {}\n"
        + "/** @type {(string|null|undefined)} */\n"
        + "var o;\n"
        + "var r = Object(o);");
    verify("r", OBJECT_TYPE);
  }

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
        + "function Object(a) {}\n"
        + "/** @type {(Array|undefined)} */\n"
        + "var o;\n"
        + "var r = Object(o);");
    verify("r", OBJECT_TYPE);
  }

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

  public void testTypeTransformationWithTypeFromNamespace() {
    inFunction("/** @constructor */\n"
        + "wiz.async.Response = function() {};"
        + "/**\n"
        + " * @return {R}\n"
        + " * @template R := typeOfVar('wiz.async.Response') =:"
        + " */\n"
        + "function f(){}\n"
        + "var r = f();");
    verify("r", getType("wiz.async.Response"));
  }

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
    assertTrue(getType("r").isRecordType());
    verify("r", getType("e"));
  }

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
    assertTrue(getType("r").isRecordType());
    verify("r", getType("e"));
  }

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
    assertTrue(getType("r").isRecordType());
    verify("r", getType("e"));
  }

  public void testAssertTypeofProp() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction(
        "goog.asserts.assert(typeof x.prop != 'undefined');" +
        "out = x.prop;");
    verify("out", CHECKED_UNKNOWN_TYPE);
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
