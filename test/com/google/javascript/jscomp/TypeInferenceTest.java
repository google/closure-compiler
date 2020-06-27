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
import static com.google.javascript.jscomp.testing.ScopeSubject.assertScope;
import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_NUMBER;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.CHECKED_UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_RESOLVED_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_STRING;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;
import static com.google.javascript.rhino.testing.TypeSubject.types;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionLookup;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DataFlowAnalysis.BranchedFlowState;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.TypeInference.BigIntPresence;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import com.google.javascript.jscomp.testing.ScopeSubject;
import com.google.javascript.jscomp.type.FlowScope;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.rhino.ClosurePrimitive;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.JSTypeResolver;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedRef;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import com.google.javascript.rhino.jstype.StaticTypedSlot;
import com.google.javascript.rhino.jstype.TemplateType;
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
  private JSTypeResolver.Closer closer;
  private Map<String, JSType> assumptions;
  private JSType assumedThisType;
  private FlowScope returnScope;
  private static final AssertionFunctionLookup ASSERTION_FUNCTION_MAP =
      AssertionFunctionLookup.of(new ClosureCodingConvention().getAssertionFunctions());

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
  @SuppressWarnings({"MustBeClosedChecker"})
  public void setUp() {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    compiler.initOptions(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    registry = compiler.getTypeRegistry();
    assumptions = new HashMap<>();
    returnScope = null;
    this.closer = this.registry.getResolver().openForDefinition();
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

  private void inModule(String js) {
    Node script = compiler.parseTestCode(js);
    assertWithMessage("parsing error: " + Joiner.on(", ").join(compiler.getErrors()))
        .that(compiler.getErrorCount())
        .isEqualTo(0);
    Node root = IR.root(IR.root(), IR.root(script));
    new GatherModuleMetadata(compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER)
        .process(root.getFirstChild(), root.getSecondChild());
    new ModuleMapCreator(compiler, compiler.getModuleMetadataMap())
        .process(root.getFirstChild(), root.getSecondChild());

    // SCRIPT -> MODULE_BODY
    Node moduleBody = script.getFirstChild();
    parseAndRunTypeInference(root, moduleBody);
  }

  private void inGenerator(String js) {
    checkState(assumedThisType == null);
    parseAndRunTypeInference("(function *() {" + js + "});");
  }

  private void parseAndRunTypeInference(String js) {
    Node script = compiler.parseTestCode(js);
    Node root = IR.root(IR.root(), IR.root(script));
    assertWithMessage("parsing error: " + Joiner.on(", ").join(compiler.getErrors()))
        .that(compiler.getErrorCount())
        .isEqualTo(0);

    // SCRIPT -> EXPR_RESULT -> FUNCTION
    // `(function() { TEST CODE HERE });`
    Node function = script.getFirstFirstChild();
    parseAndRunTypeInference(root, function);
  }

  @SuppressWarnings({"MustBeClosedChecker"})
  private void parseAndRunTypeInference(Node root, Node cfgRoot) {
    this.closer.close();

    TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);
    TypedScope assumedScope;
    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      // Create the scope with the assumptions.
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
      assumedScope = scopeCreator.createScope(cfgRoot);
      for (Map.Entry<String, JSType> entry : assumptions.entrySet()) {
        assumedScope.declare(entry.getKey(), null, entry.getValue(), null, false);
      }
      scopeCreator.resolveWeakImportsPreResolution();
    }
    scopeCreator.undoTypeAliasChains();
      // Create the control graph.
      ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, false);
      cfa.process(null, cfgRoot);
      ControlFlowGraph<Node> cfg = cfa.getCfg();
      // Create a simple reverse abstract interpreter.
      ReverseAbstractInterpreter rai = compiler.getReverseAbstractInterpreter();
      // Do the type inference by data-flow analysis.
      TypeInference dfa =
          new TypeInference(compiler, cfg, rai, assumedScope, scopeCreator, ASSERTION_FUNCTION_MAP);
      dfa.analyze();
      // Get the scope of the implicit return.
      BranchedFlowState<FlowScope> rtnState = cfg.getImplicitReturn().getAnnotation();
      if (cfgRoot.isFunction()) {
        // Reset the flow scope's syntactic scope to the function block, rather than the function
        // node
        // itself.  This allows pulling out local vars from the function by name to verify their
        // types.
        returnScope =
            rtnState.getIn().withSyntacticScope(scopeCreator.createScope(cfgRoot.getLastChild()));
      } else {
        returnScope = rtnState.getIn();
      }

    this.closer = this.registry.getResolver().openForDefinition();
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

  /** Returns the NAME node {@code name} from the PARAM_LIST of the top level of type inference. */
  private Node getParamNameNode(String name) {
    StaticTypedScope staticScope = checkNotNull(returnScope.getDeclarationScope(), returnScope);
    StaticTypedSlot slot = checkNotNull(staticScope.getSlot(name), staticScope);
    StaticTypedRef declaration = checkNotNull(slot.getDeclaration(), slot);
    Node node = checkNotNull(declaration.getNode(), declaration);

    assertNode(node).hasType(Token.NAME);
    Streams.stream(node.getAncestors())
        .filter(Node::isParamList)
        .findFirst()
        .orElseThrow(AssertionError::new);

    return node;
  }

  private void verify(String name, JSType type) {
    assertWithMessage("Mismatch for " + name).about(types()).that(getType(name)).isEqualTo(type);
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

  /** Returns a record type with a field `fieldName` and JSType specified by `fieldType`. */
  private JSType createRecordType(String fieldName, JSType fieldType) {
    Map<String, JSType> property = ImmutableMap.of(fieldName, fieldType);
    return registry.createRecordType(property);
  }

  private JSType createRecordType(String fieldName, JSTypeNative fieldType) {
    return createRecordType(fieldName, registry.getNativeType(fieldType));
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
  public void testSimpleGetProp_missingPropAccessedOnRecordType() {
    assuming("x", createRecordType("y", STRING_TYPE));
    inFunction("a = x.z;");
    verify("a", UNKNOWN_TYPE);
  }

  @Test
  public void testSimpleGetProp_missingPropAccessedOnStringType() {
    assuming("x", STRING_TYPE);
    inFunction("a = x.z;");
    verify("a", UNKNOWN_TYPE);
  }

  /**
   * Tests property access on NULL_TYPE gives undefined see -
   * https://github.com/tc39/proposal-optional-chaining#semantics
   */
  @Test
  public void testOptChainGetProp_nullObj() {
    assuming("x", NULL_TYPE);
    inFunction("let a = x?.y;");
    verify("a", VOID_TYPE);
  }

  // Tests inexistent prop accessed on UNKNOWN_TYPE gives UKNOWN_TYPE
  @Test
  public void testOptChain_accessingInexistentPropOnUnknownType() {
    assuming("x", UNKNOWN_TYPE);
    inFunction("a = x?.z");
    verify("a", UNKNOWN_TYPE);
  }

  // Tests existing String property access on non-null object gives STRING_TYPE
  @Test
  public void testOptChainGetProp_stringProp() {
    assuming("x", createRecordType("y", STRING_TYPE));
    inFunction("let a = x?.y;");
    verify("a", STRING_TYPE);
  }

  // Tests existing {?|STRING_TYPE} property access on non-null object gives {?|STRING_TYPE}
  @Test
  public void testOptChainGetProp_nullableStringProp() {
    assuming("x", createRecordType("y", createUnionType(NULL_TYPE, STRING_TYPE)));
    inFunction("let a = x?.y;");
    verify("a", createUnionType(NULL_TYPE, STRING_TYPE));
  }

  // Tests existing FUNCTION_TYPE property access on non-null object gives FUNCTION_TYPE.
  // Note - Although this test looks similar to the test above, this test is required to pass to
  // make sure the OptChain_CALL tests pass.
  @Test
  public void testOptChainGetProp_functionProp() {
    JSType funcType = registry.createFunctionType(registry.getNativeType(NUMBER_TYPE));
    JSType lhsType = createRecordType("y", funcType);
    assuming("x", lhsType);
    inFunction("let a = x?.y;");
    verify("x", lhsType);
    verify("a", funcType); // not the function `y`'s return type
  }

  // Tests existing FUNCTION_TYPE property access on nullable object returns
  // {VOID_TYPE|FUNCTION_TYPE}
  @Test
  public void testOptChainGetProp_functionProp_nullableObj() {
    assuming("x", createNullableType(createRecordType("y", FUNCTION_TYPE)));
    inFunction("let a = x?.y;");
    verify("a", createUnionType(VOID_TYPE, FUNCTION_TYPE));
  }

  // Tests existing STRING_TYPE property access on nullable object returns {VOID_TYPE|STRING_TYPE}
  @Test
  public void testOptChainGetProp_stringProp_nullableObj() {
    assuming("x", createNullableType(createRecordType("y", STRING_TYPE)));
    inFunction("let a = x?.y;");
    verify("a", createUnionType(STRING_TYPE, VOID_TYPE));
  }

  // Tests existing NUMBER_TYPE property access on non-null object returns NUMBER_TYPE
  @Test
  public void testOptChainGetProp_numberProp() {
    assuming("x", createRecordType("y", NUMBER_TYPE));
    inFunction("let a = x?.y;");
    verify("a", NUMBER_TYPE);
  }

  // Tests existing Number property access on nullable object returns {VOID_TYPE|NUMBER_TYPE}
  @Test
  public void testOptChainGetProp_numberProp_nullableObj() {
    assuming("x", createNullableType(createRecordType("y", NUMBER_TYPE)));
    inFunction("let a = x?.y;");
    verify("a", createUnionType(VOID_TYPE, NUMBER_TYPE));
  }

  // Tests valid, existing NULL_TYPE property access on non-null object gives NULL_TYPE
  @Test
  public void testOptChainGetProp_nullProp() {
    assuming("x", createRecordType("y", NULL_TYPE));
    inFunction("let a = x?.y;");
    verify("a", NULL_TYPE);
  }

  // Tests that accessing inexistent property on existing object gives UNKNOWN_TYPE (bottom)
  @Test
  public void testOptChainGetProp_inexistentProp() {
    assuming("x", createRecordType("y", STRING_TYPE));
    inFunction("let a = x?.z;");
    verify("a", UNKNOWN_TYPE);
  }

  @Test
  public void testSimpleOptChain_withUnsetType_trailingGetProp() {
    assuming("x", UNKNOWN_TYPE);
    inFunction("let a = (x?.y?.z).q;");
    // NOTE: The parentheses breaks the optional chain,  so the `.q`  is not optional.
    // We should issue a warning about this  in TypeCheck, but that isn't TypeInference's job,
    // so we'll just assume `.q` is UNKNOWN_TYPE.
    verify("a", UNKNOWN_TYPE);
  }

  // Tests de-referencing `(x?.y).z` where `x` is `{VOID_TYPE|{y:{z:STRING_TYPE}}}}` returns
  // STRING_TYPE
  @Test
  public void testGetProp_withOptionalChainObject_voidable() {
    JSType recordType = createRecordType("y", createRecordType("z", STRING_TYPE));
    JSType lhsType = registry.createUnionType(registry.getNativeType(VOID_TYPE), recordType);
    assuming("x", lhsType);
    inFunction("let a = (x?.y).z");
    verify("x", recordType); // Dereferencing non-optionally (`.z`) tightens `x` here
    verify("a", STRING_TYPE); // deliberate in TypeInf, must report in TypeChecking.
  }

  @Test
  public void testOptChainGetProp_unconditionalAssignmentToObjInInnerNodes() {
    assuming(
        "x",
        createRecordType("y", registry.createFunctionType(registry.getNativeType(NUMBER_TYPE))));
    inFunction("x?.y(x=5);");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testOptChainGetProp_conditionalAssignmentToObjInInnerNodes() {
    JSType nullableRecordType =
        createNullableType(
            createRecordType(
                "y", registry.createFunctionType(registry.getNativeType(NUMBER_TYPE))));
    assuming("x", nullableRecordType);
    inFunction("x?.y(x=5);");
    verify(
        "x",
        registry.createUnionType(
            registry.getNativeType(NUMBER_TYPE), registry.getNativeType(NULL_TYPE)));
  }

  @Test
  public void testOptChainGetProp_typeAnnotationOnObjInInnerNodes_unconditionalChain() {
    JSType recordType =
        createRecordType("y", registry.createFunctionType(registry.getNativeType(NUMBER_TYPE)));
    assuming("x", recordType);
    inFunction("x?.y(/** @type {number} */ (x)); ");
    verify("x", recordType);
  }

  @Test
  public void testGetProp_typeAnnotationOnObjInInnerNodes_unconditionalChain() {
    JSType recordType =
        createRecordType("y", registry.createFunctionType(registry.getNativeType(NUMBER_TYPE)));
    assuming("x", recordType);
    inFunction("x.y(/** @type {number} */ (x)); "); // regular GET_PROP
    verify("x", recordType);
  }

  @Test
  public void testOptChainGetProp_typeAnnotationOnObjInInnerNodes_conditionalChain() {
    JSType nullableRecordType =
        createNullableType(
            createRecordType(
                "y", registry.createFunctionType(registry.getNativeType(NUMBER_TYPE))));
    assuming("x", nullableRecordType);
    inFunction("x?.y(/** @type {number} */ (x)); ");
    verify("x", nullableRecordType);
  }

  // Since the type of objNode `a` is { ? | ...}, the statement `x=5` will conditionally execute and
  // the type of `x` must be {STRING_TYPE|NUMBER_TYPE}
  @Test
  public void testOptChainGetProp_conditionalChangeToOuterVariable() {
    JSType lhsNullableRecordType =
        createNullableType(
            createRecordType(
                "b", registry.createFunctionType(registry.getNativeType(NUMBER_TYPE))));
    assuming("a", lhsNullableRecordType);
    inFunction(lines("let x = 'x';", "a?.b(x=5);", "a; ", "x; "));
    verify("a", lhsNullableRecordType);
    verify("x", createUnionType(NUMBER_TYPE, STRING_TYPE));
  }

  // Tests optional chaining applied to a function call returning a nullable object
  @Test
  public void testOptChainGetProp_unconditionalChangeToOuterVariable_inNullableReceiver() {
    JSType recordType = createRecordType("b", registry.getNativeType(NUMBER_TYPE));
    JSType nullableRecordType = createNullableType(recordType);
    JSType lhsType = registry.createFunctionType(nullableRecordType);

    assuming("a", lhsType);
    inFunction(lines("let x = 'x';", "let res = a(x = 'some')?.b", "a; ", "x; "));
    verify("a", lhsType);
    verify("x", STRING_TYPE);
    verify("res", createUnionType(VOID_TYPE, NUMBER_TYPE));
  }

  // The receiver statement `a(x = 1)` conditionally executes and must change the type of `x`.
  @Test
  public void testRegularGetProp_unconditionalChangeToOuterVariable_inNullableReceiver() {
    JSType funcType = registry.createFunctionType(registry.getNativeType(NUMBER_TYPE));
    JSType recordType = createRecordType("b", funcType);
    JSType nullableRecordType = createNullableType(recordType);
    JSType lhsType = registry.createFunctionType(nullableRecordType);

    assuming("a", lhsType);
    inFunction(lines("let x = 'x';", "let res = a(x = 1).b", "a; ", "x; "));
    verify("a", lhsType);
    verify("x", NUMBER_TYPE);
    verify("res", funcType);
  }

  // Tests optional chaining applied to a function call returning a non-nullable object
  @Test
  public void testOptChainGetProp_unconditionalChangeToOuterVariable_inNonNullableReceiver() {
    JSType recordType = createRecordType("b", registry.getNativeType(NUMBER_TYPE));
    JSType lhsType = registry.createFunctionType(recordType);

    assuming("a", lhsType);
    inFunction(lines("let x = 'x';", "let res = a(x = 'some')?.b", "a; ", "x; "));
    verify("a", lhsType);
    verify("x", STRING_TYPE);
    verify("res", NUMBER_TYPE);
  }

  @Test
  public void testRegularGetProp_changeToOuterVariable_inNonNullableReceiver_andOptNodes() {
    JSType funcType = registry.createFunctionType(registry.getNativeType(NUMBER_TYPE));
    JSType recordType = createRecordType("b", funcType);
    JSType lhsType = registry.createFunctionType(recordType);
    assuming("a", lhsType);
    inFunction(lines("let x = 'x';", "let res = a(x = 1).b(x = 'x')", "a; ", "x; "));
    verify("a", lhsType);
    verify("x", STRING_TYPE);
    verify("res", NUMBER_TYPE);
  }

  @Test
  public void testOptChainGetProp_changeToOuterVariable_inNonNullableReceiver_andOptNodes() {
    JSType funcType = registry.createFunctionType(registry.getNativeType(NUMBER_TYPE));
    JSType recordType = createRecordType("b", funcType);
    JSType lhsType = registry.createFunctionType(recordType);
    assuming("a", lhsType);
    inFunction(lines("let x = 'x';", "let res = a(x = 1)?.b(x = 'x')", "a; ", "x; res; "));
    verify("a", lhsType);
    verify("x", STRING_TYPE);
    verify("res", NUMBER_TYPE);
  }

  @Test
  public void testRegularGetProp_changeToOuterVariable_inNullableReceiver_andOptNodes() {
    JSType funcType = registry.createFunctionType(registry.getNativeType(NUMBER_TYPE));
    JSType recordType = createRecordType("b", funcType);
    JSType nullableRecordType = createNullableType(recordType);
    JSType lhsType = registry.createFunctionType(nullableRecordType);
    assuming("a", lhsType);
    inFunction(lines("let x = 'x';", "let res = a(x = 1).b(x = 'x')", "a; ", "x; "));
    verify("a", lhsType);
    verify("x", registry.createUnionType(STRING_TYPE));
    verify("res", NUMBER_TYPE);
  }

  @Test
  public void testOptChainGetProp_changeToOuterVariable_inNullableReceiver_andOptNodes() {
    JSType funcType = registry.createFunctionType(registry.getNativeType(NUMBER_TYPE));
    JSType recordType = createRecordType("b", funcType);
    JSType nullableRecordType = createNullableType(recordType);
    JSType lhsType = registry.createFunctionType(nullableRecordType);
    assuming("a", lhsType);
    inFunction(lines("let x = 'x';", "let res = a(x = 1)?.b(x = 'x')", "a; ", "x; "));
    verify("a", lhsType);
    verify("x", registry.createUnionType(NUMBER_TYPE, STRING_TYPE));
    verify("res", createUnionType(VOID_TYPE, NUMBER_TYPE));
  }

  // Since the type of objNode `a` is well-defined (not nullable), the statement `x=5` will
  // unconditionally execute and the type of `x` must get changed to NUMBER_TYPE
  @Test
  public void testOptChainGetProp_unconditionalChangeToOuterVariable() {
    JSType lhsType =
        createRecordType("b", registry.createFunctionType(registry.getNativeType(NUMBER_TYPE)));
    assuming("a", lhsType);
    inFunction(lines("let x = 'x';", "a?.b(x=5);", "a; ", "x; "));
    verify("a", lhsType);
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testRegularGetProp_unconditionalChangeToOuterVariable() {
    JSType lhsType =
        createRecordType("b", registry.createFunctionType(registry.getNativeType(NUMBER_TYPE)));
    assuming("a", lhsType);
    inFunction(lines("let x = 'x';", "a.b(x=5);", "a; ", "x; "));
    verify("a", lhsType);
    verify("x", NUMBER_TYPE);
  }

  // Changing an outer variable `x` after accessing an inexistent property `c` should update the
  // type of the outer variable
  @Test
  public void testOptChainGetProp_invalidChangeToOuterVariable() {
    JSType lhsType =
        createRecordType("b", registry.createFunctionType(registry.getNativeType(NUMBER_TYPE)));
    assuming("a", lhsType);
    inFunction(
        lines(
            "let x = 'x';",
            "a?.c(x=5);", // even though prop `c` is inexistent, x=5 will run, and typeOf(x) will
            // change.
            "a; ",
            "x; "));
    verify("a", lhsType);
    verify("x", NUMBER_TYPE);
  }

  // a?.b(x?.y.z).c with non-nullable object
  @Test
  public void testOptChainGetProp_multipleChains() {
    JSType lhsType =
        createRecordType("b", registry.createFunctionType(registry.getNativeType(NUMBER_TYPE)));
    assuming("a", lhsType);
    inFunction("let res = a?.b(x?.y.z);");
    verify("res", NUMBER_TYPE);
  }

  // a?.b(x?.y.z).c with nullable object
  @Test
  public void testOptChainGetProp_multipleChains_nullableReceiver() {
    JSType lhsType =
        createNullableType(
            createRecordType(
                "b", registry.createFunctionType(registry.getNativeType(NUMBER_TYPE))));
    assuming("a", lhsType);
    inFunction("let res = a?.b(x?.y.z);");
    verify("res", registry.createUnionType(VOID_TYPE, NUMBER_TYPE));
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
  public void testNullishCoalesceNullableObject() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("let z = x ?? {}");
    verify("z", OBJECT_TYPE);
  }

  @Test
  public void testNullishCoalesceNullableUnion() {
    assuming("x", createNullableType(createUnionType(OBJECT_TYPE, STRING_TYPE)));
    inFunction("let z = x ?? {}");
    verify("z", createUnionType(STRING_TYPE, OBJECT_TYPE));
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
  public void testAssert5NullishCoalesce() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    assuming("y", startType);
    inFunction("goog.asserts.assert(x ?? y); out1 = x; out2 = y;");
    verify("out1", startType);
    verify("out2", startType);
  }

  @Test
  public void testAssert6() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", getNativeType(UNKNOWN_TYPE)); // Only global qname roots can be undeclared
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
  public void testAssert11() {
    JSType startType = createNullableType(OBJECT_TYPE);
    assuming("x", startType);
    assuming("y", startType);
    inFunction("var z = goog.asserts.assert(x || y);");
    verify("x", startType);
    verify("y", startType);
  }

  @Test
  public void testPrimitiveAssertTruthy_narrowsNullableObjectToObject() {
    JSType startType = createNullableType(OBJECT_TYPE);
    includePrimitiveTruthyAssertionFunction("assertTruthy");
    assuming("x", startType);

    inFunction("out1 = x; assertTruthy(x); out2 = x;");

    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  @Test
  public void testPrimitiveAssertTruthy_narrowsNullableObjectInNeqNullToObject() {
    JSType startType = createNullableType(OBJECT_TYPE);
    includePrimitiveTruthyAssertionFunction("assertTruthy");
    assuming("x", startType);

    inFunction("out1 = x; assertTruthy(x !== null); out2 = x;");

    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  @Test
  public void testPrimitiveAssertTruthy_ignoresSecondArgumentEvenIfNullable() {
    JSType startType = createNullableType(OBJECT_TYPE);
    includePrimitiveTruthyAssertionFunction("assertTruthy");
    assuming("x", startType);

    inFunction("assertTruthy(1, x); out1 = x;");

    verify("out1", startType);
  }

  @Test
  public void testAssertNumber_narrowsAllTypeToNumber() {
    JSType startType = createNullableType(ALL_TYPE);
    includeGoogAssertionFn("assertNumber", getNativeType(NUMBER_TYPE));
    assuming("x", startType);

    inFunction("out1 = x; goog.asserts.assertNumber(x); out2 = x;");

    verify("out1", startType);
    verify("out2", NUMBER_TYPE);
  }

  @Test
  public void testAssertNumber_doesNotNarrowNamesInExpression() {
    // Make sure it ignores expressions.
    JSType startType = createNullableType(ALL_TYPE);
    includeGoogAssertionFn("assertNumber", getNativeType(NUMBER_TYPE));
    assuming("x", startType);

    inFunction("goog.asserts.assertNumber(x + x); out1 = x;");

    verify("out1", startType);
  }

  @Test
  public void testAssertNumber_returnsNumberGivenExpression() {
    // Make sure it ignores expressions.
    JSType startType = createNullableType(ALL_TYPE);
    includeGoogAssertionFn("assertNumber", getNativeType(NUMBER_TYPE));
    assuming("x", startType);

    inFunction("out1 = x; out2 = goog.asserts.assertNumber(x + x);");

    verify("out1", startType);
    verify("out2", NUMBER_TYPE);
  }

  @Test
  public void testPrimitiveAssertNumber_narrowsAllTypeToNumber() {
    JSType startType = createNullableType(ALL_TYPE);
    includePrimitiveAssertionFn("assertNumber", getNativeType(NUMBER_TYPE));
    assuming("x", startType);

    inFunction("out1 = x; assertNumber(x); out2 = x;");

    verify("out1", startType);
    verify("out2", NUMBER_TYPE);
  }

  @Test
  public void testPrimitiveAssertNumber_doesNotNarrowNamesInExpression() {
    // Make sure it ignores expressions.
    JSType startType = createNullableType(ALL_TYPE);
    includePrimitiveAssertionFn("assertNumber", getNativeType(NUMBER_TYPE));
    assuming("x", startType);

    inFunction("assertNumber(x + x); out1 = x;");

    verify("out1", startType);
  }

  @Test
  public void testPrimitiveAssertNumber_returnsNumberGivenExpression() {
    // Make sure it ignores expressions.
    JSType startType = createNullableType(ALL_TYPE);
    includePrimitiveAssertionFn("assertNumber", getNativeType(NUMBER_TYPE));
    assuming("x", startType);

    inFunction("out1 = x; out2 = assertNumber(x + x);");

    verify("out1", startType);
    verify("out2", NUMBER_TYPE);
  }

  @Test
  public void testBigIntTypeAssignment() {
    assuming("x", UNKNOWN_TYPE);
    assuming("y", UNKNOWN_TYPE);

    inFunction("x = 1n; y = BigInt(1);");

    verify("x", BIGINT_TYPE);
    verify("y", BIGINT_TYPE);
  }

  @Test
  public void testAssertBigInt_narrowsAllTypeToBigInt() {
    JSType startType = createNullableType(ALL_TYPE);
    includePrimitiveAssertionFn("assertBigInt", getNativeType(BIGINT_TYPE));
    assuming("x", startType);

    inFunction("out1 = x; assertBigInt(x); out2 = x;");

    verify("out1", startType);
    verify("out2", BIGINT_TYPE);
  }

  @Test
  public void testBigIntPresence() {
    // Standard types
    testForAllBigInt(getNativeType(BIGINT_TYPE));
    testForAllBigInt(getNativeType(BIGINT_OBJECT_TYPE));
    testForNoBigInt(getNativeType(NUMBER_TYPE));
    testForNoBigInt(getNativeType(STRING_TYPE));
    testForNoBigInt(getNativeType(ALL_TYPE));
    testForNoBigInt(getNativeType(UNKNOWN_TYPE));
    testForNoBigInt(getNativeType(NO_TYPE));

    // Unions
    testForAllBigInt(createUnionType(BIGINT_TYPE, BIGINT_OBJECT_TYPE));
    testForBigIntOrNumber(getNativeType(BIGINT_NUMBER));
    testForBigIntOrOther(createUnionType(BIGINT_TYPE, STRING_TYPE));
    testForNoBigInt(getNativeType(NUMBER_STRING));

    // Union within union
    testForBigIntOrNumber(createUnionType(NUMBER_OBJECT_TYPE, BIGINT_NUMBER));
    testForBigIntOrNumber(createUnionType(BIGINT_OBJECT_TYPE, BIGINT_NUMBER));
    testForBigIntOrNumber(
        registry.createUnionType(
            getNativeType(NUMBER_TYPE), createUnionType(BIGINT_TYPE, BIGINT_OBJECT_TYPE)));
    testForBigIntOrNumber(
        registry.createUnionType(
            getNativeType(BIGINT_TYPE), createUnionType(NUMBER_TYPE, NUMBER_OBJECT_TYPE)));
    testForBigIntOrOther(
        registry.createUnionType(
            getNativeType(BIGINT_TYPE), createUnionType(STRING_TYPE, STRING_OBJECT_TYPE)));

    // Enum within union
    testForAllBigInt(
        registry.createUnionType(
            getNativeType(BIGINT_OBJECT_TYPE),
            createEnumType("Enum", BIGINT_TYPE).getElementsType()));
    testForBigIntOrNumber(
        registry.createUnionType(
            getNativeType(BIGINT_TYPE), createEnumType("Enum", NUMBER_TYPE).getElementsType()));
    testForBigIntOrOther(
        registry.createUnionType(
            getNativeType(BIGINT_TYPE), createEnumType("Enum", STRING_TYPE).getElementsType()));

    // Standard enum
    testForAllBigInt(createEnumType("Enum", BIGINT_TYPE).getElementsType());
    testForNoBigInt(createEnumType("Enum", NUMBER_TYPE).getElementsType());

    // Enum within enum
    testForAllBigInt(
        createEnumType("Enum", createEnumType("Enum", BIGINT_TYPE).getElementsType())
            .getElementsType());
    testForNoBigInt(
        createEnumType("Enum", createEnumType("Enum", NUMBER_TYPE).getElementsType())
            .getElementsType());

    // Union within enum
    testForAllBigInt(
        createEnumType("Enum", createUnionType(BIGINT_TYPE, BIGINT_OBJECT_TYPE)).getElementsType());
    testForBigIntOrNumber(createEnumType("Enum", BIGINT_NUMBER).getElementsType());
    testForBigIntOrOther(
        createEnumType("Enum", createUnionType(BIGINT_TYPE, STRING_TYPE)).getElementsType());
  }

  @Test
  public void testBigIntWithUnaryPlus() {
    assuming("x", BIGINT_TYPE);
    assuming("y", BIGINT_OBJECT_TYPE);
    assuming("z", BIGINT_NUMBER);

    inFunction("valueType = +x; objectType = +y; unionType = +z;");

    // Unary plus throws an exception when applied to a BigInt, so there is no valid type for its
    // result.
    verify("valueType", NO_TYPE);
    verify("objectType", NO_TYPE);
    verify("unionType", NO_TYPE);
  }

  @Test
  public void testBigIntEnumWithUnaryPlus() {
    EnumElementType enumElementBigIntType = createEnumType("MyEnum", BIGINT_TYPE).getElementsType();
    EnumElementType enumElementUnionType =
        createEnumType("MyEnum", BIGINT_NUMBER).getElementsType();
    assuming("x", enumElementBigIntType);
    assuming("y", registry.createUnionType(enumElementBigIntType, getNativeType(NUMBER_TYPE)));
    assuming("z", enumElementUnionType);

    inFunction("enumElementBigIntType = +x; unionEnumType = +y; enumElementUnionType = +z;");

    // Unary plus throws an exception when applied to a BigInt, so there is no valid type for its
    // result.
    verify("enumElementBigIntType", NO_TYPE);
    verify("unionEnumType", NO_TYPE);
    verify("enumElementUnionType", NO_TYPE);
  }

  @Test
  public void testBigIntWithLogicalNOT() {
    assuming("x", BIGINT_TYPE);
    assuming("y", BIGINT_OBJECT_TYPE);
    assuming("z", BIGINT_NUMBER);

    inFunction("valueType = !x; objectType = !y; unionType = !z;");

    verify("valueType", BOOLEAN_TYPE);
    verify("objectType", BOOLEAN_TYPE);
    verify("unionType", BOOLEAN_TYPE);
  }

  @Test
  public void testBigIntWithTypeOfOperation() {
    assuming("x", BIGINT_TYPE);
    assuming("y", BIGINT_OBJECT_TYPE);
    assuming("z", createUnionType(BIGINT_TYPE, BIGINT_OBJECT_TYPE));

    inFunction("valueType = typeof x; objectType = typeof y; unionType = typeof z;");

    verify("valueType", STRING_TYPE);
    verify("objectType", STRING_TYPE);
    verify("unionType", STRING_TYPE);
  }

  @Test
  public void testBigIntWithDeleteOperation() {
    assuming("x", BIGINT_TYPE);
    assuming("y", BIGINT_OBJECT_TYPE);
    assuming("z", createUnionType(BIGINT_TYPE, BIGINT_OBJECT_TYPE));

    inFunction("valueType = delete x; objectType = delete y; unionType = delete z;");

    verify("valueType", BOOLEAN_TYPE);
    verify("objectType", BOOLEAN_TYPE);
    verify("unionType", BOOLEAN_TYPE);
  }

  @Test
  public void testBigIntWithVoidOperation() {
    assuming("x", BIGINT_TYPE);
    assuming("y", BIGINT_OBJECT_TYPE);
    assuming("z", createUnionType(BIGINT_TYPE, BIGINT_OBJECT_TYPE));

    inFunction("valueType = void x; objectType = void y; unionType = void z;");

    verify("valueType", VOID_TYPE);
    verify("objectType", VOID_TYPE);
    verify("unionType", VOID_TYPE);
  }

  @Test
  public void testBigIntWithUnaryMinus() {
    assuming("x", BIGINT_TYPE);
    assuming("y", BIGINT_OBJECT_TYPE);
    assuming("u", UNKNOWN_TYPE);
    assuming("a", ALL_TYPE);
    assuming("z1", BIGINT_NUMBER);
    // testing for a union between bigint and anything but number
    assuming("z2", createUnionType(BIGINT_TYPE, STRING_TYPE));

    inFunction(
        "valueType = -x; objectType = -y; unknownType = -u; allType = -a; bigintNum = -z1;"
            + " bigintOther = -z2;");

    verify("valueType", BIGINT_TYPE);
    verify("objectType", BIGINT_TYPE);
    verify("unknownType", NUMBER_TYPE);
    verify("allType", NUMBER_TYPE);
    verify("bigintNum", BIGINT_NUMBER);
    verify("bigintOther", BIGINT_NUMBER);
  }

  @Test
  public void testBigIntWithBitwiseNOT() {
    assuming("x", BIGINT_TYPE);
    assuming("y", BIGINT_OBJECT_TYPE);
    assuming("u", UNKNOWN_TYPE);
    assuming("a", ALL_TYPE);
    assuming("z1", createUnionType(BIGINT_TYPE, NUMBER_TYPE));
    // testing for a union between bigint and anything but number
    assuming("z2", createUnionType(BIGINT_TYPE, STRING_TYPE));

    inFunction(
        "valueType = ~x; objectType = ~y; unknownType = ~u; allType = ~a; bigintOrNumber = ~z1;"
            + " bigintOrOther = ~z2;");

    verify("valueType", BIGINT_TYPE);
    verify("objectType", BIGINT_TYPE);
    verify("unknownType", NUMBER_TYPE);
    verify("allType", NUMBER_TYPE);
    verify("bigintOrNumber", createUnionType(BIGINT_TYPE, NUMBER_TYPE));
    verify("bigintOrOther", createUnionType(BIGINT_TYPE, NUMBER_TYPE));
  }

  @Test
  public void testIncrementOnBigInt() {
    assuming("x", BIGINT_TYPE);
    assuming("y", BIGINT_OBJECT_TYPE);
    assuming("z", BIGINT_NUMBER);
    assuming("a", createUnionType(BIGINT_TYPE, STRING_TYPE));

    inFunction("valueType = x++; objectType = y++; bigintNumber = z++; bigintOther = a++;");

    verify("valueType", BIGINT_TYPE);
    verify("objectType", BIGINT_TYPE);
    verify("bigintNumber", BIGINT_NUMBER);
    verify("bigintOther", BIGINT_NUMBER);
  }

  @Test
  public void testDecrementOnBigInt() {
    assuming("x", BIGINT_TYPE);
    assuming("y", BIGINT_OBJECT_TYPE);
    assuming("z", BIGINT_NUMBER);
    assuming("a", createUnionType(BIGINT_TYPE, STRING_TYPE));

    inFunction("valueType = x--; objectType = y--; bigintNumber = z++; bigintOther = a++;");

    verify("valueType", BIGINT_TYPE);
    verify("objectType", BIGINT_TYPE);
    verify("bigintNumber", BIGINT_NUMBER);
    verify("bigintOther", BIGINT_NUMBER);
  }

  @Test
  public void testAdditionWithBigInt() {
    assuming("b", BIGINT_TYPE);
    assuming("B", BIGINT_OBJECT_TYPE);
    assuming("n", NUMBER_TYPE);
    assuming("bn", BIGINT_NUMBER);
    assuming("bs", createUnionType(BIGINT_TYPE, STRING_TYPE));
    assuming("s", STRING_TYPE);
    assuming("u", UNKNOWN_TYPE);
    assuming("ns", NUMBER_STRING);

    inFunction(
        lines(
            "valueTypePlusSelf = b + b;",
            "objectTypePlusSelf = B + B;",
            "valuePlusObject = b + B;",
            "bigintPlusNumber = b + n;",
            "bigintNumberPlusSelf = bn + bn;",
            "bigintStringConcat = b + s;",
            "bigintNumberStringConcat = bn + s",
            "bigintOtherStringConcat = bs + s",
            "bigintStringConcatWithSelf = bs + bs",
            "bigintPlusUnknown = b + u;",
            "bigintPlusNumberString = b + ns;"));

    verify("valueTypePlusSelf", BIGINT_TYPE);
    verify("objectTypePlusSelf", BIGINT_TYPE);
    verify("valuePlusObject", BIGINT_TYPE);
    verify("bigintPlusNumber", NO_TYPE);
    verify("bigintNumberPlusSelf", BIGINT_NUMBER);
    verify("bigintStringConcat", STRING_TYPE);
    verify("bigintNumberStringConcat", STRING_TYPE);
    verify("bigintOtherStringConcat", STRING_TYPE);
    // In reality if you use '+' on 2 bigint|string operands, then the result will be bigint|string.
    // However, code that does that is almost certainly wrong and we should complain about it.
    // It also keeps the TypeInference logic simpler if we pretend this operation is an error.
    verify("bigintStringConcatWithSelf", NO_TYPE);
    verify("bigintPlusUnknown", NO_TYPE);
    verify("bigintPlusNumberString", NO_TYPE);
  }

  @Test
  public void testBigIntCompatibleBinaryOperator() {
    assuming("b", BIGINT_TYPE);
    assuming("B", BIGINT_OBJECT_TYPE);
    assuming("n", NUMBER_TYPE);
    assuming("bn", BIGINT_NUMBER);
    assuming("s", STRING_TYPE);
    assuming("u", UNKNOWN_TYPE);
    assuming("ns", NUMBER_STRING);

    inFunction(
        lines(
            "valueTypeWithSelf = b * b;",
            "objectTypeWithSelf = B * B;",
            "valueWithObject = b * B;",
            "bigintWithNumber = b * n;",
            "bigintNumberWithSelf = bn * bn;",
            "bigintWithOther = b * s;",
            "bigintWithUnknown = b * u;",
            "bigintWithNumberString = b * ns;"));

    verify("valueTypeWithSelf", BIGINT_TYPE);
    verify("objectTypeWithSelf", BIGINT_TYPE);
    verify("valueWithObject", BIGINT_TYPE);
    verify("bigintWithNumber", NO_TYPE);
    verify("bigintNumberWithSelf", BIGINT_NUMBER);
    verify("bigintWithOther", NO_TYPE);
    verify("bigintWithUnknown", NO_TYPE);
    verify("bigintWithNumberString", NO_TYPE);
  }

  @Test
  public void testAssignOpWithBigInt() {
    assuming("b", BIGINT_TYPE);
    assuming("n", NUMBER_TYPE);
    assuming("s", STRING_TYPE);
    assuming("u", UNKNOWN_TYPE);
    assuming("bn", BIGINT_NUMBER);
    assuming("bigintWithSelf", BIGINT_TYPE);
    assuming("bigintWithNumber", BIGINT_TYPE);
    assuming("bigintWithOther", BIGINT_TYPE);
    assuming("bigintConcatString", BIGINT_TYPE);
    assuming("stringConcatBigInt", STRING_TYPE);
    assuming("bigintWithUnknown", BIGINT_TYPE);
    assuming("bigintNumberWithSelf", BIGINT_NUMBER);
    assuming("bigintNumberWithBigInt", BIGINT_NUMBER);
    assuming("bigintNumberWithNumber", BIGINT_NUMBER);

    inFunction(
        lines(
            "bigintWithSelf *= b;",
            "bigintWithNumber *= n;",
            "bigintWithOther *= s;",
            "bigintConcatString += s",
            "stringConcatBigInt += b",
            "bigintWithUnknown *= u;",
            "bigintNumberWithSelf *= bn;",
            "bigintNumberWithBigInt *= b",
            "bigintNumberWithNumber *= n"));

    verify("bigintWithSelf", BIGINT_TYPE);
    verify("bigintWithNumber", NO_TYPE);
    verify("bigintWithOther", NO_TYPE);
    verify("bigintConcatString", STRING_TYPE);
    verify("stringConcatBigInt", STRING_TYPE);
    verify("bigintWithUnknown", NO_TYPE);
    verify("bigintNumberWithSelf", BIGINT_NUMBER);
    verify("bigintNumberWithBigInt", NO_TYPE);
    verify("bigintNumberWithNumber", NO_TYPE);
  }

  @Test
  public void testUnsignedRightShiftWithBigInt() {
    assuming("b", BIGINT_TYPE);
    assuming("n", NUMBER_TYPE);
    assuming("assignBigIntOnLeft", BIGINT_TYPE);
    assuming("assignBigIntOnRight", NUMBER_TYPE);
    assuming("assignBigIntOnBothSides", BIGINT_TYPE);

    inFunction(
        lines(
            "bigintOnLeft = b >>> n;",
            "bigintOnRight = n >>> b;",
            "bigintOnBothSides = b >>> b;",
            "assignBigIntOnLeft >>>= n",
            "assignBigIntOnRight >>>= b",
            "assignBigIntOnBothSides >>>= b"));

    verify("bigintOnLeft", NO_TYPE);
    verify("bigintOnRight", NO_TYPE);
    verify("bigintOnBothSides", NO_TYPE);
    verify("assignBigIntOnLeft", NO_TYPE);
    verify("assignBigIntOnRight", NO_TYPE);
    verify("assignBigIntOnBothSides", NO_TYPE);
  }

  @Test
  public void testAssertBoolean_narrowsAllTypeToBoolean() {
    JSType startType = createNullableType(ALL_TYPE);
    includeGoogAssertionFn("assertBoolean", getNativeType(BOOLEAN_TYPE));
    assuming("x", startType);

    inFunction("out1 = x; goog.asserts.assertBoolean(x); out2 = x;");

    verify("out1", startType);
    verify("out2", BOOLEAN_TYPE);
  }

  @Test
  public void testAssertString_narrowsAllTypeToString() {
    JSType startType = createNullableType(ALL_TYPE);
    includeGoogAssertionFn("assertString", getNativeType(STRING_TYPE));
    assuming("x", startType);

    inFunction("out1 = x; goog.asserts.assertString(x); out2 = x;");

    verify("out1", startType);
    verify("out2", STRING_TYPE);
  }

  @Test
  public void testAssertFunction_narrowsAllTypeToFunction() {
    JSType startType = createNullableType(ALL_TYPE);
    includeGoogAssertionFn("assertFunction", getNativeType(FUNCTION_TYPE));
    assuming("x", startType);

    inFunction("out1 = x; goog.asserts.assertFunction(x); out2 = x;");

    verify("out1", startType);
    verifySubtypeOf("out2", FUNCTION_TYPE);
  }

  @Test
  public void testAssertElement_doesNotChangeElementType() {
    JSType elementType =
        registry.createObjectType("Element", registry.getNativeObjectType(OBJECT_TYPE));
    includeGoogAssertionFn("assertElement", elementType);
    assuming("x", elementType);

    inFunction("out1 = x; goog.asserts.assertElement(x); out2 = x;");

    verify("out1", elementType);
    verify("out2", elementType);
  }

  @Test
  public void testAssertObject_narrowsNullableArrayToArray() {
    JSType startType = createNullableType(ARRAY_TYPE);
    includeGoogAssertionFn("assertObject", getNativeType(OBJECT_TYPE));
    assuming("x", startType);

    inFunction("out1 = x; goog.asserts.assertObject(x); out2 = x;");

    verify("out1", startType);
    verify("out2", ARRAY_TYPE);
  }

  @Test
  public void testAssertObject_narrowsNullableObjectToObject() {
    JSType startType = createNullableType(OBJECT_TYPE);
    includeGoogAssertionFn("assertObject", getNativeType(OBJECT_TYPE));
    assuming("x", startType);

    inFunction("out1 = x; goog.asserts.assertObject(x); out2 = x;");

    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  @Test
  public void testAssertObject_narrowsQualifiedNameArgument() {
    JSType startType = createNullableType(OBJECT_TYPE);
    includeGoogAssertionFn("assertObject", getNativeType(OBJECT_TYPE));
    assuming("x", getNativeType(UNKNOWN_TYPE)); // Only global qname roots can be undeclared
    assuming("x.y", startType);

    // test a property "x.y" instead of a simple name
    inFunction("out1 = x.y; goog.asserts.assertObject(x.y); out2 = x.y;");

    verify("out1", startType);
    verify("out2", OBJECT_TYPE);
  }

  @Test
  public void testAssertObject_inCastToArray_returnsArray() {
    JSType startType = createNullableType(ALL_TYPE);
    includeGoogAssertionFn("assertObject", getNativeType(OBJECT_TYPE));
    assuming("x", startType);

    inFunction(
        "out1 = x;" +
        "out2 = /** @type {!Array} */ (goog.asserts.assertObject(x));");

    verify("out1", startType);
    verify("out2", ARRAY_TYPE);
  }

  @Test
  public void testAssertArray_narrowsNullableAllTypeToArray() {
    JSType startType = createNullableType(ALL_TYPE);
    includeGoogAssertionFn("assertArray", getNativeType(ARRAY_TYPE));
    assuming("x", startType);

    inFunction("out1 = x; goog.asserts.assertArray(x); out2 = x;");

    verify("out1", startType);
    verifySubtypeOf("out2", ARRAY_TYPE);
  }

  @Test
  public void testAssertArray_narrowsObjectTypeToArray() {
    JSType startType = getNativeType(OBJECT_TYPE);
    includeGoogAssertionFn("assertArray", getNativeType(ARRAY_TYPE));
    assuming("x", startType);

    inFunction("out1 = x; goog.asserts.assertArray(x); out2 = x;");

    verify("out1", startType);
    verifySubtypeOf("out2", ARRAY_TYPE);
  }

  @Test
  public void testAssertInstanceof_invalidCall_setsArgToUnknownType() {
    // Test invalid assert (2 params are required)
    JSType startType = createNullableType(ALL_TYPE);
    includeAssertInstanceof();
    assuming("x", startType);

    inFunction("out1 = x; goog.asserts.assertInstanceof(x); out2 = x;");

    verify("out1", startType);
    verify("out2", UNKNOWN_TYPE);
  }

  @Test
  public void testAssertInstanceof_stringCtor_narrowsAllTypeToString() {
    JSType startType = createNullableType(ALL_TYPE);
    includeAssertInstanceof();
    assuming("x", startType);

    inFunction("out1 = x; goog.asserts.assertInstanceof(x, String); out2 = x;");

    verify("out1", startType);
    verify("out2", STRING_OBJECT_TYPE);
  }

  @Test
  public void testAssertInstanceof_unknownCtor_setsStringToUnknown() {
    includeAssertInstanceof();
    assuming("x", STRING_TYPE);
    assuming("Foo", UNKNOWN_TYPE);

    inFunction("out1 = x; goog.asserts.assertInstanceof(x, Foo); out2 = x;");

    verify("out1", STRING_TYPE);
    verify("out2", UNKNOWN_TYPE);
  }

  @Test
  public void testAssertInstanceof_stringCtor_narrowsUnknownToString() {
    JSType startType = registry.getNativeType(UNKNOWN_TYPE);
    includeAssertInstanceof();
    assuming("x", startType);

    inFunction("out1 = x; goog.asserts.assertInstanceof(x, String); out2 = x;");

    verify("out1", startType);
    verify("out2", STRING_OBJECT_TYPE);
  }

  @Test
  public void testAssertInstanceof_objectCtor_doesNotChangeStringType() {
    JSType startType = registry.getNativeType(STRING_OBJECT_TYPE);
    includeAssertInstanceof();
    assuming("x", startType);

    inFunction("out1 = x; goog.asserts.assertInstanceof(x, Object); out2 = x;");

    verify("out1", startType);
    verify("out2", STRING_OBJECT_TYPE);
  }

  @Test
  public void testAssertInstanceof_stringCtor_narrowsObjOrVoidToString() {
    JSType startType = createUnionType(OBJECT_TYPE, VOID_TYPE);
    includeAssertInstanceof();
    assuming("x", startType);

    inFunction("out1 = x; goog.asserts.assertInstanceof(x, String); var r = x;");

    verify("out1", startType);
    verify("x", STRING_OBJECT_TYPE);
  }

  @Test
  public void testAssertInstanceof_stringCtor_returnsStringFromObjOrVoid() {
    JSType startType = createUnionType(OBJECT_TYPE, VOID_TYPE);
    includeAssertInstanceof();
    assuming("x", startType);

    inFunction("out1 = x; var y = goog.asserts.assertInstanceof(x, String);");

    verify("out1", startType);
    verify("y", STRING_OBJECT_TYPE);
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
    assuming("x", createNullableType(registry.getNativeType(JSTypeNative.FUNCTION_TYPE)));
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
  public void testNew4() {
    inFunction(
        lines(
            "/**",
            " * @constructor",
            " * @param {!Array<T>} x",
            " * @param {T} y",
            " * @param {S} z",
            " * @param {U} m",
            " * @template T,S,U",
            " */",
            "function F(x,y,z,m) {}",
            "var /** !Array<number> */ x = [];",
            "var y = 'foo';",
            "var z = true;",
            "var m = 9;",
            "var result = new F(x,y,z,m);"));

    assertThat(getType("result").toString()).isEqualTo("F<(number|string),boolean,number>");
  }

  @Test
  public void testNew_onCtor_instantiatingTemplatizedType_withNoTemplateInformation() {
    inFunction(
        lines(
            "/**",
            " * @constructor",
            " * @template T",
            " */",
            "function Foo() {}",
            "",
            "var result = new Foo();"));

    assertThat(getType("result").toString()).isEqualTo("Foo<?>");
  }

  @Test
  public void testNew_onCtor_instantiatingTemplatizedType_specializedOnSecondaryTemplate() {
    inFunction(
        lines(
            "/**",
            " * @constructor",
            " * @template T",
            " */",
            "function Foo() {}",
            "",
            "/**",
            " * @template U",
            " * @param {function(new:Foo<U>)} ctor",
            " * @param {U} arg",
            " * @return {!Foo<U>}",
            " */",
            "function create(ctor, arg) {",
            "  return new ctor(arg);",
            "}",
            "",
            "var result = create(Foo, 0);"));

    assertThat(getType("result").toString()).isEqualTo("Foo<number>");
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
  public void testParamNodeType_simpleName() {
    parseAndRunTypeInference("(/** @param {number} i */ function(i) {})");

    assertNode(getParamNameNode("i")).hasJSTypeThat().isNumber();
  }

  @Test
  public void testParamNodeType_rest() {
    parseAndRunTypeInference("(/** @param {...number} i */ function(...i) {})");

    assertNode(getParamNameNode("i")).hasJSTypeThat().toStringIsEqualTo("Array<number>");
  }

  @Test
  public void testParamNodeType_arrayDestructuring() {
    parseAndRunTypeInference("(/** @param {!Iterable<number>} i */ function([i]) {})");

    // TODO(nickreid): Also check the types of the other nodes in the PARAM_LIST tree.
    assertNode(getParamNameNode("i")).hasJSTypeThat().isNumber();
  }

  @Test
  public void testParamNodeType_objectDestructuring() {
    parseAndRunTypeInference("(/** @param {{a: number}} i */ function({a: i}) {})");

    // TODO(nickreid): Also check the types of the other nodes in the PARAM_LIST tree.
    assertNode(getParamNameNode("i")).hasJSTypeThat().isNumber();
  }

  @Test
  public void testParamNodeType_simpleName_withDefault() {
    parseAndRunTypeInference("(/** @param {number=} i */ function(i = 9) {})");

    // TODO(nickreid): Also check the types of the other nodes in the PARAM_LIST tree.
    assertNode(getParamNameNode("i")).hasJSTypeThat().isNumber();
  }

  @Test
  public void testParamNodeType_arrayDestructuring_withDefault() {
    parseAndRunTypeInference(
        lines(
            "(/** @param {!Iterable<number>=} unused */",
            "function([i] = /** @type ({!Array<number>} */ ([])) {})"));

    // TODO(nickreid): Also check the types of the other nodes in the PARAM_LIST tree.
    // TODO(b/122904530): `i` should be `number`.
    assertNode(getParamNameNode("i")).hasJSTypeThat().isUnknown();
  }

  @Test
  public void testParamNodeType_objectDestructuring_withDefault() {
    parseAndRunTypeInference("(/** @param {{a: number}=} i */ function({a: i} = {a: 9}) {})");

    // TODO(nickreid): Also check the types of the other nodes in the PARAM_LIST tree.
    assertNode(getParamNameNode("i")).hasJSTypeThat().isNumber();
  }

  @Test
  public void testParamNodeType_arrayDestructuring_withDefault_nestedInPattern() {
    parseAndRunTypeInference("(/** @param {!Iterable<number>} i */ function([i = 9]) {})");

    // TODO(nickreid): Also check the types of the other nodes in the PARAM_LIST tree.
    assertNode(getParamNameNode("i")).hasJSTypeThat().isNumber();
  }

  @Test
  public void testParamNodeType_objectDestructuring_withDefault_nestedInPattern() {
    parseAndRunTypeInference("(/** @param {{a: number}} i */ function({a: i = 9}) {})");

    // TODO(nickreid): Also check the types of the other nodes in the PARAM_LIST tree.
    assertNode(getParamNameNode("i")).hasJSTypeThat().isNumber();
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
  public void testShortCircuitingNullishCoalesce() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; if (x ?? (y = 3)) { }");
    verify("y", NULL_TYPE);
  }

  @Test
  public void testShortCircuitingNullishCoalesceIf() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; var z = 5; if (x ?? (y = 3)) { z = y }");
    verify("y", NULL_TYPE);
  }

  @Test
  public void testShortCircuitingOr2() {
    assuming("x", NUMBER_TYPE);
    inFunction("var y = null; var z = 4; if (x || (y = 3)) { z = y; }");
    verify("z", createNullableType(NUMBER_TYPE));
  }

  @Test
  public void testShortCircuitingNullishCoalseceNumber() {
    assuming("x", NUMBER_TYPE);
    inFunction("var z = x ?? null");
    verify("z", NUMBER_TYPE);
  }

  @Test
  public void testNullishCoalesce() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var y = 1; x ?? (y = x);");
    verify("y", createNullableType(NUMBER_TYPE));
  }

  @Test
  public void nullishCoalesceWithHook() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction("var z = (x) ?? (x ? 'hi' : false)");
    // Looks like x should be (object|boolean) but hook always traverses both branches
    verify("z", createMultiParamUnionType(OBJECT_TYPE, BOOLEAN_TYPE, STRING_TYPE));
  }

  @Test
  public void nullishCoalesceRemoveNull() {
    assuming("x", createNullableType(NUMBER_TYPE));
    inFunction("x = x ?? 3");
    verify("x", NUMBER_TYPE); // nullability removed by ?? operation
  }

  @Test
  public void nullishCoalesceZeroIsValid() {
    // Making sure that ?? does not execute RHS (even if x is 0)
    assuming("x", createNullableType(NUMBER_TYPE));
    inFunction("var y = ''; if (x ?? (y = x)) { }");
    verify("y", createNullableType(STRING_TYPE));
  }

  @Test
  public void nullishCoalesceFalseIsValid() {
    // Making sure that ?? does not execute RHS (even if x is false)
    assuming("x", createNullableType(BOOLEAN_TYPE));
    inFunction("var y = ''; x ?? (y = x)");
    verify("y", createNullableType(STRING_TYPE));
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
  public void testObjectSpread_isInferredToBeObject() {
    // Given
    JSType recordType =
        registry.createRecordType(
            ImmutableMap.of(
                "x", getNativeType(STRING_TYPE),
                "y", getNativeType(NUMBER_TYPE)));
    assuming("obj", recordType);

    assuming("before", BOOLEAN_TYPE);
    assuming("after", NULL_TYPE);

    // When
    inFunction(lines("let spread = {before, ...obj, after};"));

    // Then

    // TODO(b/128355893): Do smarter inferrence. There are a lot of potential issues with
    // inference on object-rest, so for now we just give up and say `Object`. In theory we could
    // infer something like `{after: null, before: boolean, x: string, y: number}`.
    verify("spread", OBJECT_TYPE);
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
        .isEqualTo("{\n  a: (boolean|undefined),\n  b: (string|undefined)\n}");
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
  public void testFunctionTemplateType_literalParam() {
    inFunction(
        lines(
            "/**",
            " * @template T",
            " * @param {T} a",
            " * @return {T}",
            " */",
            "function f(a){}",
            "",
            "var result = f(10);"));
    verify("result", NUMBER_TYPE);
  }

  @Test
  public void testFunctionTemplateType_unionsPossibilities() {
    inFunction(
        lines(
            "/**",
            " * @template T",
            " * @param {T} a",
            " * @param {T} b",
            " * @return {T}",
            " */",
            "function f(a, b){}",
            "",
            "var result = f(10, 'x');"));
    verify("result", registry.createUnionType(NUMBER_TYPE, STRING_TYPE));
  }

  @Test
  public void testFunctionTemplateType_willUseUnknown() {
    inFunction(
        lines(
            "/**",
            " * @template T",
            " * @param {T} a",
            " * @return {T}",
            " */",
            "function f(a){}",
            "",
            "var result = f(/** @type {?} */ ({}));"));
    verify("result", UNKNOWN_TYPE);
  }

  @Test
  public void testFunctionTemplateType_willUseUnknown_butPrefersTighterTypes() {
    inFunction(
        lines(
            "/**",
            " * @template T",
            " * @param {T} a",
            " * @param {T} b",
            " * @param {T} c",
            " * @return {T}",
            " */",
            "function f(a, b, c){}",
            "",
            // Make sure `?` is dispreferred before *and* after a known type.
            "var result = f('x', /** @type {?} */ ({}), 5);"));
    verify("result", registry.createUnionType(NUMBER_TYPE, STRING_TYPE));
  }

  @Test
  public void testFunctionTemplateType_recursesIntoFunctionParams() {
    inFunction(
        lines(
            "/**",
            " * @template T",
            " * @param {function(T)} a",
            " * @return {T}",
            " */",
            "function f(a){}",
            "",
            "var result = f(function(/** number */ a) { });"));
    verify("result", NUMBER_TYPE);
  }

  @Test
  public void testFunctionTemplateType_recursesIntoFunctionParams_throughUnknown() {
    inFunction(
        lines(
            "/**",
            " * @template T",
            " * @param {function(T)=} a",
            " * @return {T}",
            " */",
            "function f(a){}",
            "",
            "var result = f(/** @type {?} */ ({}));"));
    verify("result", UNKNOWN_TYPE);
  }

  @Test
  public void testFunctionTemplateType_unpacksUnions_fromParamType() {
    inFunction(
        lines(
            "/**",
            " * @template T",
            " * @param {!Iterable<T>|number} a",
            " * @return {T}",
            " */",
            "function f(a){}",
            "",
            "var result = f(/** @type {!Iterable<number>} */ ({}));"));
    verify("result", NUMBER_TYPE);
  }

  @Test
  public void testFunctionTemplateType_unpacksUnions_fromArgType() {
    inFunction(
        lines(
            "/**",
            " * @template T",
            " * @param {!Iterable<T>} a",
            " * @return {T}",
            " */",
            "function f(a){}",
            "",
            // The arg type is illegal, but the inference should still work.
            "var result = f(/** @type {!Iterable<number>|number} */ ({}));"));
    verify("result", NUMBER_TYPE);
  }

  @Test
  public void testFunctionTemplateType_unpacksUnions_fromArgType_acrossSubtypes() {
    inFunction(
        lines(
            "/**",
            " * @template T",
            " * @param {!Iterable<T>} a",
            " * @return {T}",
            " */",
            "function f(a){}",
            "",
            "var result = f(/** @type {!Array<number>|!Generator<string>} */ ({}));"));
    verify("result", registry.createUnionType(NUMBER_TYPE, STRING_TYPE));
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
  public void testObjectRest_inferredGivenObjectLiteralType() {
    inFunction("var obj = {a: 1, b: 2, c: 3}; const {a, ...rest} = obj;  A: a; REST: rest;");

    assertTypeOfExpression("A").toStringIsEqualTo("number");
    assertTypeOfExpression("REST").isEqualTo(registry.getNativeType(OBJECT_TYPE));
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
  public void testInferringTypeInObjectPattern_fromTemplatizedProperty() {
    // create type Foo with one property templatized with type T
    TemplateType templateKey = registry.createTemplateType("T");
    FunctionType fooCtor =
        registry.createConstructorType(
            "Foo", null, registry.createParameters(), null, ImmutableList.of(templateKey), false);
    ObjectType fooInstanceType = fooCtor.getInstanceType();
    fooInstanceType.defineDeclaredProperty("data", templateKey, null);

    // create a variable obj with type Foo<number>
    JSType fooOfNumber = templatize(fooInstanceType, ImmutableList.of(getNativeType(NUMBER_TYPE)));
    assuming("obj", fooOfNumber);
    inFunction(
        lines(
            "const {data} = obj;", //
            "OBJ: obj;",
            "DATA: data"));

    assertTypeOfExpression("OBJ").toStringIsEqualTo("Foo<number>");
    assertTypeOfExpression("DATA").toStringIsEqualTo("number");
  }

  @Test
  public void testTypeInferenceOccursInsideVoidOperator() {
    inFunction("var x; var y = void (x = 3); X: x; Y: y");

    assertTypeOfExpression("X").toStringIsEqualTo("number");
    assertTypeOfExpression("Y").toStringIsEqualTo("undefined");
  }

  @Test
  public void constDeclarationWithReturnJSDoc_ignoresUnknownRhsType() {
    assuming("foo", UNKNOWN_TYPE);

    inFunction(lines("/** @return {number} */ const fn = foo;"));

    JSType fooWithInterfaceType = getType("fn");
    assertType(fooWithInterfaceType).isFunctionTypeThat().hasReturnTypeThat().isNumber();
  }

  @Test
  public void constDeclarationWithCtorJSDoc_ignoresKnownMixinReturnType() {
    // Create a function always returning a constructor for 'Foo'
    JSType fooType = FunctionType.builder(registry).forConstructor().withName("Foo").build();
    assuming("Foo", fooType);
    FunctionType mixinType = FunctionType.builder(registry).withReturnType(fooType).build();
    assuming("mixin", mixinType);

    // The @constructor JSDoc should declare a new type, and FooExtended should refer to that
    // type instead of the constructor for Foo
    inFunction(lines("/** @constructor @extends {Foo} */ const FooExtended = mixin();"));

    JSType fooWithInterfaceType = getType("FooExtended");
    assertType(fooWithInterfaceType).isNotEqualTo(fooType);
    assertType(fooWithInterfaceType).toStringIsEqualTo("function(new:FooExtended): ?");
  }

  @Test
  public void testSideEffectsInEsExportDefaultInferred() {
    assuming("foo", NUMBER_TYPE);
    assuming("bar", UNKNOWN_TYPE);

    inModule("export default (bar = foo, foo = 'not a number');");

    assertType(getType("bar")).isNumber();
    assertType(getType("foo")).isString();
  }

  @Test
  public void testShneTightensUnknownOperandOnLhs() {
    assuming("foo", NUMBER_TYPE);
    assuming("bar", UNKNOWN_TYPE);

    inFunction("if (bar === foo) { FOO: foo; BAR: bar; }");

    assertTypeOfExpression("FOO").isNumber();
    assertTypeOfExpression("BAR").isNumber();
  }

  @Test
  public void testShneTightensUnknownOperandOnRhs() {
    assuming("foo", NUMBER_TYPE);
    assuming("bar", UNKNOWN_TYPE);

    inFunction("if (foo === bar) { FOO: foo; BAR: bar; }");

    assertTypeOfExpression("FOO").isNumber();
    assertTypeOfExpression("BAR").isNumber();
  }

  private static void testForAllBigInt(JSType type) {
    assertThat(TypeInference.getBigIntPresence(type)).isEqualTo(BigIntPresence.ALL_BIGINT);
  }

  private static void testForNoBigInt(JSType type) {
    assertThat(TypeInference.getBigIntPresence(type)).isEqualTo(BigIntPresence.NO_BIGINT);
  }

  private static void testForBigIntOrNumber(JSType type) {
    assertThat(TypeInference.getBigIntPresence(type)).isEqualTo(BigIntPresence.BIGINT_OR_NUMBER);
  }

  private static void testForBigIntOrOther(JSType type) {
    assertThat(TypeInference.getBigIntPresence(type)).isEqualTo(BigIntPresence.BIGINT_OR_OTHER);
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

  /** Adds a goog.asserts.assert[name] function to the scope that asserts the given returnType */
  private void includeGoogAssertionFn(String fnName, JSType returnType) {
    String fullName = "goog.asserts." + fnName;
    FunctionType fnType =
        FunctionType.builder(registry)
            .withReturnType(returnType)
            .withParameters(registry.createParameters(getNativeType(UNKNOWN_TYPE)))
            .withName(fullName)
            .build();
    assuming(fullName, fnType);
  }

  /** Adds a function with {@link ClosurePrimitive#ASSERTS_TRUTHY} and the given name */
  private void includePrimitiveTruthyAssertionFunction(String fnName) {
    TemplateType t = registry.createTemplateType("T");
    FunctionType assertType =
        FunctionType.builder(registry)
            .withName(fnName)
            .withClosurePrimitiveId(ClosurePrimitive.ASSERTS_TRUTHY)
            .withReturnType(t)
            .withParameters(registry.createParameters(t))
            .withTemplateKeys(t)
            .build();
    assuming(fnName, assertType);
  }

  /**
   * Adds a function with {@link ClosurePrimitive#ASSERTS_MATCHES_RETURN} that asserts the given
   * returnType
   */
  private void includePrimitiveAssertionFn(String fullName, JSType returnType) {
    FunctionType fnType =
        FunctionType.builder(registry)
            .withReturnType(returnType)
            .withParameters(registry.createParameters(getNativeType(UNKNOWN_TYPE)))
            .withName(fullName)
            .withClosurePrimitiveId(ClosurePrimitive.ASSERTS_MATCHES_RETURN)
            .build();
    assuming(fullName, fnType);
  }

  /** Adds goog.asserts.assertInstanceof to the scope, to do fine-grained assertion testing */
  private void includeAssertInstanceof() {
    String fullName = "goog.asserts.assertInstanceof";
    TemplateType templateType = registry.createTemplateType("T");
    // Create the function type `function(new:T)`
    FunctionType templateTypeCtor =
        FunctionType.builder(registry).forConstructor().withTypeOfThis(templateType).build();
    // Create the function type `function(?, function(new:T)): T`
    // This matches the JSDoc for goog.asserts.assertInstanceof:
    //   /**
    //    * @param {?} value The value to check
    //    * @param {function(new:T)) type A user-defined ctor
    //    * @return {T}
    //    * @template T
    //    */
    FunctionType fnType =
        FunctionType.builder(registry)
            .withParameters(
                registry.createParameters(getNativeType(UNKNOWN_TYPE), templateTypeCtor))
            .withTemplateKeys(templateType)
            .withReturnType(templateType)
            .withName(fullName)
            .build();
    assuming(fullName, fnType);
  }
}
