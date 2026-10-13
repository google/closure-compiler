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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.testing.ScopeSubject.assertScope;
import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_NUMBER;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_NUMBER_STRING;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.CHECKED_UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_TYPE;
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
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionLookup;
import com.google.javascript.jscomp.DataFlowAnalysis.LinearFlowState;
import com.google.javascript.jscomp.NodeTraversal.AbstractScopedCallback;
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
import com.google.javascript.rhino.jstype.KnownSymbolType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.StaticTypedRef;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import com.google.javascript.rhino.jstype.StaticTypedSlot;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.testing.TypeSubject;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link TypeInference}.
 *
 * <p>These unit tests don't test the full type inference that happens in a normal compilation. This
 * is because a normal compilation creates many instances of the {@link TypeInference} class. More
 * precisely, one instance is created for every scope that is a valid root of a control-flow graph
 * (as defined by {@link NodeUtil#isValidCfgRoot(Node)} such as, for example, functions.
 *
 * <p>These unit tests only ever create a single {@link TypeInference} instance. That means: these
 * unit tests ignore any code nested within a function.
 *
 * <p>To write tests that more fully model the typechecking in a full compilation, which visits
 * every function body, use {@link TypedScopeCreatorTest} or {@link TypeCheckTest}.
 */
@RunWith(JUnit4.class)
public final class TypeInferenceTest {

  private Compiler compiler;
  private JSTypeRegistry registry;
  private JSTypeResolver.Closer closer;
  private Map<String, JSType> assumptions;
  private JSType assumedThisType;
  private @Nullable FlowScope returnScope;
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
    options.setLanguageIn(CompilerOptions.LanguageMode.UNSUPPORTED);
    compiler.initOptions(options);
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

  /**
   * Runs an instance of TypeInference over the given code after wrapping it in a function.
   *
   * <p>Does not visit any nested functions.
   */
  private void inFunction(String js) {
    // Parse the body of the function.
    String thisBlock = assumedThisType == null ? "" : "/** @this {" + assumedThisType + "} */";
    parseAndRunTypeInference("(" + thisBlock + " function() {" + js + "});");
  }

  /**
   * Runs an instance of TypeInference over the given code.
   *
   * <p>Does not visit any nested functions.
   */
  private void inScript(String js) {
    Node script = compiler.parseTestCode(js);
    assertWithMessage("parsing error: " + Joiner.on(", ").join(compiler.getErrors()))
        .that(compiler.getErrorCount())
        .isEqualTo(0);
    Node root = IR.root(IR.root(), IR.root(script));
    new GatherModuleMetadata(compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER)
        .process(root.getFirstChild(), root.getSecondChild());
    new ModuleMapCreator(compiler, compiler.getModuleMetadataMap())
        .process(root.getFirstChild(), root.getSecondChild());

    parseAndRunTypeInference(root, root);
  }

  /**
   * Runs an instance of TypeInference over the given code after wrapping it in a generator.
   *
   * <p>Does not visit any nested functions.
   */
  private void inGenerator(String js) {
    checkState(assumedThisType == null);
    parseAndRunTypeInference("(function *() {" + js + "});");
  }

  private void withModules(ImmutableList<String> js) {
    Node script = compiler.parseTestCode(js);
    JSChunk chunk = new JSChunk("entry");
    Collection<CompilerInput> inputs = compiler.getInputsById().values();
    for (CompilerInput input : inputs) {
      chunk.add(input.getSourceFile());
    }
    compiler.initChunks(ImmutableList.of(), ImmutableList.of(chunk), compiler.getOptions());
    compiler.initializeModuleLoader();
    assertWithMessage("parsing error: " + Joiner.on(", ").join(compiler.getErrors()))
        .that(compiler.getErrorCount())
        .isEqualTo(0);
    Node root = IR.root(IR.root(), IR.root(script));
    new GatherModuleMetadata(compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER)
        .process(root.getFirstChild(), root.getSecondChild());
    new ModuleMapCreator(compiler, compiler.getModuleMetadataMap())
        .process(root.getFirstChild(), root.getSecondChild());

    parseAndRunTypeInference(root, root);
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
      NodeTraversal.builder()
          .setCompiler(compiler)
          .setCallback(
              new AbstractScopedCallback() {
                @Override
                public void enterScope(NodeTraversal t) {
                  t.getTypedScope();
                }

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
              })
          .setScopeCreator(scopeCreator)
          .traverse(root);
      assumedScope = scopeCreator.createScope(cfgRoot);
      for (Map.Entry<String, JSType> entry : assumptions.entrySet()) {
        assumedScope.declare(entry.getKey(), null, entry.getValue(), null, false);
      }
      scopeCreator.resolveWeakImportsPreResolution();
    }
    scopeCreator.finishAndFreeze();
    // Create the control graph.
    ControlFlowGraph<Node> cfg =
        ControlFlowAnalysis.builder()
            .setCompiler(compiler)
            .setCfgRoot(cfgRoot)
            .setTraverseFunctions(true)
            .setIncludeEdgeAnnotations(true)
            .computeCfg();
    // Create a simple reverse abstract interpreter.
    ReverseAbstractInterpreter rai = compiler.getReverseAbstractInterpreter();
    // Do the type inference by data-flow analysis.
    TypeInference dfa =
        new TypeInference(compiler, cfg, rai, assumedScope, scopeCreator, ASSERTION_FUNCTION_MAP);
    dfa.analyze();
    // Get the scope of the implicit return.
    LinearFlowState<FlowScope> rtnState = cfg.getImplicitReturn().getAnnotation();
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
    assertWithMessage("The variable %s is missing from the scope.", name).that(var).isNotNull();
    return var.getType();
  }

  /** Returns the NAME node {@code name} from the PARAM_LIST of the top level of type inference. */
  private Node getParamNameNode(String name) {
    StaticTypedScope staticScope = checkNotNull(returnScope.getDeclarationScope(), returnScope);
    StaticTypedSlot slot = checkNotNull(staticScope.getSlot(name), staticScope);
    StaticTypedRef declaration = checkNotNull(slot.getDeclaration(), slot);
    Node node = checkNotNull(declaration.getNode(), declaration);

    assertNode(node).hasType(Token.NAME);
    assertThat(stream(node.getAncestors()).filter(Node::isParamList).collect(toImmutableList()))
        .isNotEmpty();

    return node;
  }

  private void verify(String name, JSType type) {
    assertWithMessage("Mismatch for %s", name).about(types()).that(getType(name)).isEqualTo(type);
  }

  private void verify(String name, JSTypeNative type) {
    verify(name, registry.getNativeType(type));
  }

  private void verifyUnequal(String name, JSType type) {
    assertWithMessage("Mismatch for %s", name)
        .about(types())
        .that(getType(name))
        .isNotEqualTo(type);
  }

  private void verifyUnequal(String name, JSTypeNative type) {
    verifyUnequal(name, registry.getNativeType(type));
  }

  private void verifySubtypeOf(String name, JSType type) {
    JSType varType = getType(name);
    assertWithMessage("The variable %s is missing a type.", name).that(varType).isNotNull();
    assertWithMessage("The type %s of variable %s is not a subtype of %s.", varType, name, type)
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
    return EnumType.builder(registry).setName(name).setElementType(elemType).build();
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
    return registry.createUnionType(registry.getNativeType(type1), registry.getNativeType(type2));
  }

  /** Returns a record type with a field `fieldName` and JSType specified by `fieldType`. */
  private JSType createRecordType(String fieldName, JSType fieldType) {
    ImmutableMap<String, JSType> property = ImmutableMap.of(fieldName, fieldType);
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

  // https://github.com/google/closure-compiler/issues/3678
  @Test
  public void testMissingTypeAnnotationsInfersUnknownReturnInArrow() {
    inFunction("const getNum = () => 2; const a = getNum();");
    verifyUnequal("a", NUMBER_TYPE);
    verify("a", UNKNOWN_TYPE);
  }

  // https://github.com/google/closure-compiler/issues/3678
  @Test
  public void testMissingTypeAnnotationsInfersUnknownReturn() {
    inFunction("const getNum = function() {return 2; }; const a = getNum();");
    verifyUnequal("a", NUMBER_TYPE);
    verify("a", UNKNOWN_TYPE);
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
  public void testOptChainGetProp_nullObject() {
    inFunction("let x = null; let a = x?.y;");
    verify("a", VOID_TYPE);
  }

  @Test
  public void testOptChainGetElem_accessedByName() {
    inFunction("let x = { y : 5}; let a = x?.[y];");
    verify("a", UNKNOWN_TYPE);
  }

  @Test
  public void testOptChainGetElem_accessedByString() {
    inFunction("let x = { y : 5}; let a = x?.['y'];");
    verify("a", UNKNOWN_TYPE);
  }

  @Test
  public void testNormalGetElem_accessedByString() {
    inFunction("let x = { y : 5}; let a = x['y'];");
    verify("a", UNKNOWN_TYPE);
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
    inFunction(
        """
        let x = 'x';
        a?.b(x=5);
        a;
        x;
        """);
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
    inFunction(
        """
        let x = 'x';
        let res = a(x = 'some')?.b
        a;
        x;
        """);
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
    inFunction(
        """
        let x = 'x';
        let res = a(x = 1).b
        a;
        x;
        """);
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
    inFunction(
        """
        let x = 'x';
        let res = a(x = 'some')?.b
        a;
        x;
        """);
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
    inFunction(
        """
        let x = 'x';
        let res = a(x = 1).b(x = 'x')
        a;
        x;
        """);
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
    inFunction(
        """
        let x = 'x';
        let res = a(x = 1)?.b(x = 'x')
        a;
        x; res;
        """);
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
    inFunction(
        """
        let x = 'x';
        let res = a(x = 1).b(x = 'x')
        a;
        x;
        """);
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
    inFunction(
        """
        let x = 'x';
        let res = a(x = 1)?.b(x = 'x')
        a;
        x;
        """);
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
    inFunction(
        """
        let x = 'x';
        a?.b(x=5);
        a;
        x;
        """);
    verify("a", lhsType);
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testRegularGetProp_unconditionalChangeToOuterVariable() {
    JSType lhsType =
        createRecordType("b", registry.createFunctionType(registry.getNativeType(NUMBER_TYPE)));
    assuming("a", lhsType);
    inFunction(
        """
        let x = 'x';
        a.b(x=5);
        a;
        x;
        """);
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
        """
        let x = 'x';
        a?.c(x=5); // even though prop `c` is inexistent, x=5 will run, and typeOf(x) will
        // change.
        a;
        x;
        """);
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
  public void testGetElemDereference_knownSymbol() {
    ObjectType array = getNativeObjectType(ARRAY_TYPE);
    KnownSymbolType sym = new KnownSymbolType(registry, "sym");
    array.defineDeclaredProperty(new Property.SymbolKey(sym), getNativeType(STRING_TYPE), null);
    assuming("o", array);
    assuming("sym", sym);

    inFunction("const x = o[sym];");
    verify("x", STRING_TYPE);
  }

  @Test
  public void testGetElemDereference_knownSymbol_nullable() {
    ObjectType array = getNativeObjectType(ARRAY_TYPE);
    KnownSymbolType sym = new KnownSymbolType(registry, "sym");
    array.defineDeclaredProperty(new Property.SymbolKey(sym), getNativeType(STRING_TYPE), null);
    assuming("o", createNullableType(array));
    assuming("sym", sym);

    inFunction("const x = o[sym];");
    verify("x", STRING_TYPE);
  }

  @Test
  public void testGetElemDereference_knownSymbol_union() {
    ObjectType array = getNativeObjectType(ARRAY_TYPE);
    ObjectType promise = getNativeObjectType(JSTypeNative.PROMISE_TYPE);
    KnownSymbolType sym = new KnownSymbolType(registry, "sym");
    array.defineDeclaredProperty(new Property.SymbolKey(sym), getNativeType(STRING_TYPE), null);
    promise.defineDeclaredProperty(new Property.SymbolKey(sym), getNativeType(NUMBER_TYPE), null);
    assuming("o", registry.createUnionType(array, promise));
    assuming("sym", sym);

    inFunction("const x = o[sym];");
    verify("x", registry.createUnionType(getNativeType(STRING_TYPE), getNativeType(NUMBER_TYPE)));
  }

  @Test
  public void testGetElemDereference_knownSymbol_union_notOnAllAlternates() {
    ObjectType array = getNativeObjectType(ARRAY_TYPE);
    ObjectType promise = getNativeObjectType(JSTypeNative.PROMISE_TYPE);
    KnownSymbolType sym = new KnownSymbolType(registry, "sym");
    array.defineDeclaredProperty(new Property.SymbolKey(sym), getNativeType(STRING_TYPE), null);
    // don't define sym on promise
    assuming("o", registry.createUnionType(array, promise));
    assuming("sym", sym);

    inFunction("const x = o[sym];");
    verify("x", UNKNOWN_TYPE);
  }

  @Test
  public void testGetElemDereference_knownSymbol_onAllType() {
    KnownSymbolType sym = new KnownSymbolType(registry, "sym");
    assuming("o", ALL_TYPE);
    assuming("sym", sym);

    inFunction("const x = o[sym];");
    verify("x", UNKNOWN_TYPE);
  }

  @Test
  public void testGetElemDereference_knownSymbol_onUnknownType() {
    KnownSymbolType sym = new KnownSymbolType(registry, "sym");
    assuming("o", UNKNOWN_TYPE);
    assuming("sym", sym);

    inFunction("const x = o[sym];");
    verify("x", UNKNOWN_TYPE);
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
    thisType.defineDeclaredProperty("foo", createUndefinableType(STRING_TYPE), null);
    assumingThisType(thisType);
    inFunction("var y = 1; if (this.foo) { y = this.foo; }");
    verify("y", createUnionType(NUMBER_TYPE, STRING_TYPE));
  }

  @Test
  public void testPropertyInference2() {
    ObjectType thisType = registry.createAnonymousObjectType(null);
    thisType.defineDeclaredProperty("foo", createUndefinableType(STRING_TYPE), null);
    assumingThisType(thisType);
    inFunction("var y = 1; this.foo = 'x'; y = this.foo;");
    verify("y", STRING_TYPE);
  }

  @Test
  public void testPropertyInference3() {
    ObjectType thisType = registry.createAnonymousObjectType(null);
    thisType.defineDeclaredProperty("foo", createUndefinableType(STRING_TYPE), null);
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
        """
        valueType = -x; objectType = -y; unknownType = -u; allType = -a; bigintNum = -z1;
         bigintOther = -z2;
        """);

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
    assuming("z1", BIGINT_NUMBER);
    // testing for a union between bigint and anything but number
    assuming("z2", createUnionType(BIGINT_TYPE, STRING_TYPE));

    inFunction(
        """
        valueType = ~x; objectType = ~y; unknownType = ~u; allType = ~a; bigintOrNumber = ~z1;
         bigintOrOther = ~z2;
        """);

    verify("valueType", BIGINT_TYPE);
    verify("objectType", BIGINT_TYPE);
    verify("unknownType", NUMBER_TYPE);
    verify("allType", NUMBER_TYPE);
    verify("bigintOrNumber", BIGINT_NUMBER);
    verify("bigintOrOther", BIGINT_NUMBER);
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
        """
        valueTypePlusSelf = b + b;
        objectTypePlusSelf = B + B;
        valuePlusObject = b + B;
        bigintPlusNumber = b + n;
        bigintNumberPlusSelf = bn + bn;
        bigintStringConcat = b + s;
        bigintNumberStringConcat = bn + s
        bigintOtherStringConcat = bs + s
        bigintStringConcatWithSelf = bs + bs
        bigintPlusUnknown = b + u;
        bigintPlusNumberString = b + ns;
        """);

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
        """
        valueTypeWithSelf = b * b;
        objectTypeWithSelf = B * B;
        valueWithObject = b * B;
        bigintWithNumber = b * n;
        bigintNumberWithSelf = bn * bn;
        bigintWithOther = b * s;
        bigintWithUnknown = b * u;
        bigintWithNumberString = b * ns;
        """);

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
        """
        bigintWithSelf *= b;
        bigintWithNumber *= n;
        bigintWithOther *= s;
        bigintConcatString += s
        stringConcatBigInt += b
        bigintWithUnknown *= u;
        bigintNumberWithSelf *= bn;
        bigintNumberWithBigInt *= b
        bigintNumberWithNumber *= n
        """);

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
        """
        bigintOnLeft = b >>> n;
        bigintOnRight = n >>> b;
        bigintOnBothSides = b >>> b;
        assignBigIntOnLeft >>>= n
        assignBigIntOnRight >>>= b
        assignBigIntOnBothSides >>>= b
        """);

    verify("bigintOnLeft", NO_TYPE);
    verify("bigintOnRight", NO_TYPE);
    verify("bigintOnBothSides", NO_TYPE);
    verify("assignBigIntOnLeft", NO_TYPE);
    verify("assignBigIntOnRight", NO_TYPE);
    verify("assignBigIntOnBothSides", NO_TYPE);
  }

  @Test
  public void testBigIntComparison() {
    assuming("b", BIGINT_TYPE);
    assuming("n", NUMBER_TYPE);

    inFunction("bigintOnly = b > b; bigintAndOther = b > n");

    verify("bigintOnly", BOOLEAN_TYPE);
    verify("bigintAndOther", BOOLEAN_TYPE);
  }

  @Test
  public void testLogicalBinaryOperatorsWithBigInt() {
    assuming("b", BIGINT_TYPE);
    assuming("B", BIGINT_OBJECT_TYPE);
    assuming("n", NUMBER_TYPE);
    assuming("bn", BIGINT_NUMBER);
    assuming("s", STRING_TYPE);
    assuming("u", UNKNOWN_TYPE);
    assuming("ns", NUMBER_STRING);

    inFunction(
        """
        valueTypeWithSelf = b && b;
        objectTypeWithSelf = B && B;
        valueWithObject = b && B;
        bigintWithNumber = b && n;
        bigintNumberWithSelf = bn && bn;
        bigintWithOther = b && s;
        bigintWithUnknown = b && u;
        bigintWithNumberString = b && ns;
        """);

    verify("valueTypeWithSelf", BIGINT_TYPE);
    verify("objectTypeWithSelf", BIGINT_OBJECT_TYPE);
    verify("valueWithObject", createUnionType(BIGINT_TYPE, BIGINT_OBJECT_TYPE));
    verify("bigintWithNumber", BIGINT_NUMBER);
    verify("bigintNumberWithSelf", BIGINT_NUMBER);
    verify("bigintWithOther", createUnionType(BIGINT_TYPE, STRING_TYPE));
    verify("bigintWithUnknown", createUnionType(BIGINT_TYPE, UNKNOWN_TYPE));
    verify("bigintWithNumberString", BIGINT_NUMBER_STRING);
  }

  @Test
  public void testTernaryOperatorWithBigInt() {
    assuming("b", BIGINT_TYPE);
    assuming("B", BIGINT_OBJECT_TYPE);
    assuming("n", NUMBER_TYPE);
    assuming("bn", BIGINT_NUMBER);
    assuming("s", STRING_TYPE);
    assuming("u", UNKNOWN_TYPE);
    assuming("v", VOID_TYPE);
    assuming("ns", NUMBER_STRING);

    inFunction(
        """
        valueTypeWithSelf = v ? b : b;
        objectTypeWithSelf = v ? B : B;
        valueWithObject = v ? b : B;
        bigintWithNumber = v ? b : n;
        bigintNumberWithSelf = v ? bn : bn;
        bigintWithOther = v ? b : s;
        bigintWithUnknown = v ? b : u;
        bigintWithNumberString = v ? b : ns;
        """);

    verify("valueTypeWithSelf", BIGINT_TYPE);
    verify("objectTypeWithSelf", BIGINT_OBJECT_TYPE);
    verify("valueWithObject", createUnionType(BIGINT_TYPE, BIGINT_OBJECT_TYPE));
    verify("bigintWithNumber", BIGINT_NUMBER);
    verify("bigintNumberWithSelf", BIGINT_NUMBER);
    verify("bigintWithOther", createUnionType(BIGINT_TYPE, STRING_TYPE));
    verify("bigintWithUnknown", createUnionType(BIGINT_TYPE, UNKNOWN_TYPE));
    verify("bigintWithNumberString", BIGINT_NUMBER_STRING);
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
        """
        out1 = x;
        out2 = /** @type {!Array} */ (goog.asserts.assertObject(x));
        """);

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
  public void testTypeInferenceOccursInConstObjectProperties() {
    inFunction(
        """
        /** @return {string} */
        function foo() { return ''; }

        const obj = {
           prop: foo(),
        }
        LABEL: obj.prop;
        """);

    assertTypeOfExpression("LABEL").toStringIsEqualTo("string");
    assertTypeOfExpression("LABEL").isNotEqualTo(UNKNOWN_TYPE);
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
        """
        var x = null;
        var i = null;
        for (i in y) {
          I_INSIDE_LOOP: i;
          X_AT_LOOP_START: x;
          x = 1;
          X_AT_LOOP_END: x;
        }
        X_AFTER_LOOP: x;
        I_AFTER_LOOP: i;
        """);
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
        """
        var i = null;
        for (var i in y) { // i redeclared here, but really the same variable
          I_INSIDE_LOOP: i;
        }
        I_AFTER_LOOP: i;
        """);
    assertScopeEnclosing("I_INSIDE_LOOP").declares("i").onClosestHoistScope();
    assertTypeOfExpression("I_INSIDE_LOOP").toStringIsEqualTo("string");

    assertScopeEnclosing("I_AFTER_LOOP").declares("i").directly();
    assertTypeOfExpression("I_AFTER_LOOP").toStringIsEqualTo("(null|string)");
  }

  @Test
  public void testForInWithLet() {
    assuming("y", OBJECT_TYPE);
    inFunction(
        """
        FOR_IN_LOOP: for (let i in y) { // preserve newlines
          I_INSIDE_LOOP: i;
        }
        AFTER_LOOP: 1;
        """);
    assertScopeEnclosing("I_INSIDE_LOOP").declares("i").onScopeLabeled("FOR_IN_LOOP");
    assertTypeOfExpression("I_INSIDE_LOOP").toStringIsEqualTo("string");

    assertScopeEnclosing("AFTER_LOOP").doesNotDeclare("i");
  }

  @Test
  public void testForInWithConst() {
    assuming("y", OBJECT_TYPE);
    inFunction(
        """
        FOR_IN_LOOP: for (const i in y) { // preserve newlines
          I_INSIDE_LOOP: i;
        }
        AFTER_LOOP: 1;
        """);
    assertScopeEnclosing("I_INSIDE_LOOP").declares("i").onScopeLabeled("FOR_IN_LOOP");
    assertTypeOfExpression("I_INSIDE_LOOP").toStringIsEqualTo("string");

    assertScopeEnclosing("AFTER_LOOP").doesNotDeclare("i");
  }

  @Test
  public void testFor4() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction(
        """
        var y = {};
        if (x) { for (var i = 0; i < 10; i++) { break; } y = x; }
        """);
    verifySubtypeOf("y", OBJECT_TYPE);
  }

  @Test
  public void testFor5() {
    assuming(
        "y",
        templatize(getNativeObjectType(ARRAY_TYPE), ImmutableList.of(getNativeType(NUMBER_TYPE))));
    inFunction("var x = null; for (var i = 0; i < y.length; i++) { x = y[i]; }");
    verify("x", createNullableType(NUMBER_TYPE));
    verify("i", NUMBER_TYPE);
  }

  @Test
  public void testFor6() {
    assuming("y", getNativeObjectType(ARRAY_TYPE));
    inFunction(
        """
        var x = null;
        for (var i = 0; i < y.length; i++) {
         if (y[i] == 'z') { x = y[i]; }
        }
        """);
    verify("x", getNativeType(UNKNOWN_TYPE));
    verify("i", NUMBER_TYPE);
  }

  @Test
  public void testSwitch1() {
    assuming("x", NUMBER_TYPE);
    inFunction(
        """
        var y = null; switch(x) {
        case 1: y = 1; break;
        case 2: y = {};
        case 3: y = {};
        default: y = 0;}
        """);
    verify("y", NUMBER_TYPE);
  }

  @Test
  public void testSwitch2() {
    assuming("x", ALL_TYPE);
    inFunction(
        """
        var y = null; switch (typeof x) {
        case 'string':
          y = x;
          return;
        default:
          y = 'a';
        }
        """);
    verify("y", STRING_TYPE);
  }

  @Test
  public void testSwitch3() {
    assuming("x", createNullableType(createUnionType(NUMBER_TYPE, STRING_TYPE)));
    inFunction(
        """
        var y; var z; switch (typeof x) {
        case 'string':
          y = 1; z = null;
          return;
        case 'number':
          y = x; z = null;
          return;
        default:
          y = 1; z = x;
        }
        """);
    verify("y", NUMBER_TYPE);
    verify("z", NULL_TYPE);
  }

  @Test
  public void testSwitch4() {
    assuming("x", ALL_TYPE);
    inFunction(
        """
        var y = null; switch (typeof x) {
        case 'string':
        case 'number':
          y = x;
          return;
        default:
          y = 1;
        }
        """);
    verify("y", createUnionType(NUMBER_TYPE, STRING_TYPE));
  }

  @Test
  public void testCall1() {
    assuming(
        "x", createNullableType(registry.createFunctionType(registry.getNativeType(NUMBER_TYPE))));
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
        """
        /**
         * @constructor
         * @param {T} x
         * @template T
         */
        function F(x) {}
        var x = /** @type {!Array<number>} */ ([]);
        var result = new F(x);
        """);

    assertThat(getType("result").toString()).isEqualTo("F<Array<number>>");
  }

  @Test
  public void testNew3() {
    inFunction(
        """
        /**
         * @constructor
         * @param {Array<T>} x
         * @param {T} y
         * @param {S} z
         * @template T,S
         */
        function F(x,y,z) {}
        var x = /** @type {!Array<number>} */ ([]);
        var y = /** @type {string} */ ('foo');
        var z = /** @type {boolean} */ (true);
        var result = new F(x,y,z);
        """);

    assertThat(getType("result").toString()).isEqualTo("F<(number|string),boolean>");
  }

  @Test
  public void testNew4() {
    inFunction(
        """
        /**
         * @constructor
         * @param {!Array<T>} x
         * @param {T} y
         * @param {S} z
         * @param {U} m
         * @template T,S,U
         */
        function F(x,y,z,m) {}
        var /** !Array<number> */ x = [];
        var y = 'foo';
        var z = true;
        var m = 9;
        var result = new F(x,y,z,m);
        """);

    assertThat(getType("result").toString()).isEqualTo("F<(number|string),boolean,number>");
  }

  @Test
  public void testNew_onCtor_instantiatingTemplatizedType_withNoTemplateInformation() {
    inFunction(
        """
        /**
         * @constructor
         * @template T
         */
        function Foo() {}

        var result = new Foo();
        """);

    assertThat(getType("result").toString()).isEqualTo("Foo<?>");
  }

  @Test
  public void testNew_onCtor_instantiatingTemplatizedType_specializedOnSecondaryTemplate() {
    inFunction(
        """
        /**
         * @constructor
         * @template T
         */
        function Foo() {}

        /**
         * @template U
         * @param {function(new:Foo<U>)} ctor
         * @param {U} arg
         * @return {!Foo<U>}
         */
        function create(ctor, arg) {
          return new ctor(arg);
        }

        var result = create(Foo, 0);
        """);

    assertThat(getType("result").toString()).isEqualTo("Foo<number>");
  }

  @Test
  public void testNewRest() {
    inFunction(
        """
        /**
         * @constructor
         * @param {Array<T>} x
         * @param {T} y
         * @param {...S} rest
         * @template T,S
         */
        function F(x, y, ...rest) {}
        var x = /** @type {!Array<number>} */ ([]);
        var y = /** @type {string} */ ('foo');
        var z = /** @type {boolean} */ (true);
        var result = new F(x,y,z);
        """);

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
        """
        (/** @param {!Iterable<number>=} unused */
        function([i] = /** @type ({!Array<number>} */ ([])) {})
        """);

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
        """
        BLOCK_SCOPE: {
          BEFORE_DEFINITION: f;
          function f() {}
          AFTER_DEFINITION: f;
        }
        AFTER_BLOCK: f;
        """);
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
    inFunction(
        """
        var y = 1;
        if (x == null) { throw new Error('x is null') }
        y = x;
        """);
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
    inFunction(
        """
        var y = null;
        try {  } catch (e) { y = null; } finally { y = x; }
        """);
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
    JSType enumType =
        createEnumType("MyEnum", createUnionType(STRING_TYPE, NUMBER_TYPE)).getElementsType();
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
  public void testAssignOrToNumeric() {
    // This in line with the existing type-inferencing logic for
    // or/and, since any boolean type is treated as {true, false},
    // but there can be improvement in precision here
    inFunction("var y = false; y ||= 20");
    verify("y", createUnionType(NUMBER_TYPE, BOOLEAN_TYPE));
  }

  @Test
  public void testComputedClassFieldsInControlFlow() {
    // Based on the class semantics, the static RHS expressions only execute after all of the
    // computed properties, so `y` will get the string value rather than the boolean here.
    inFunction(
        """
        let y;
        class Foo {
          static [y = null] = (y = '');
          [y = false] = [y = null];
        }
        """);
    verify("y", STRING_TYPE);
  }

  @Test
  public void testClassFieldsInControlFlow() {
    inFunction(
        """
        let y;
        class Foo {
          static y = (y = '');
          z = [y = null];
        }
        """);
    verify("y", STRING_TYPE);
  }

  @Test
  public void testThisTypeAfterMemberField() {
    JSType thisType = createRecordType("x", STRING_TYPE);
    assumingThisType(thisType);
    inFunction(
        """
        let thisDotX;
        (class C {
          /** @type {number} */
          static x = 0;
        }, thisDotX = this.x);
        """);
    verify("thisDotX", STRING_TYPE);
  }

  @Test
  public void testFunction() {
    // should verify y as string, but due to function-rooted CFG
    // being detached from larger, root CFG, verifies y as void
    inScript(
        """
        let y;
        function foo() {
          y = 'hi';
        }
        foo();
        """);
    verify("y", VOID_TYPE);
  }

  @Test
  public void testClassStaticBlock() {
    // should verify y as string, but due to static block-rooted CFG
    // being detached from larger, root CFG, verifies y as void
    inScript(
        """
        let y;
        class Foo {
          static {
            y = 'hi';
          }
        }
        """);
    verify("y", VOID_TYPE);
  }

  @Test
  public void testSuper() {
    // does not infer super
    inScript(
        """
        class Foo {
          static str;
        }
        class Bar extends Foo {
          static {
            super.str = 'hi';
          }
        }
        let x = Bar.str;
        """);
    verify("x", ALL_TYPE);
  }

  @Test
  public void testAssignOrNoAssign() {
    // The two examples below show imprecision of || operator
    // The resulting type of Node n is (boolean|string), when it can be
    // more precise by verifying `x` as a string
    inFunction("var x; var y; y = false; x = (y || 'foo');");
    verify("x", createUnionType(BOOLEAN_TYPE, STRING_TYPE));

    // Short-circuiting should occur, as `a` is a truthy value,
    // `c` would be assigned true, and `b` would remain undefined.
    // To be more precise, `c` may be verified as a BOOLEAN_TYPE,
    // `a` a BOOLEAN_TYPE, and `b` a VOID_TYPE.
    // The actual behavior considers `a` (a BOOLEAN_TYPE) to be {true, false},
    // and states that `c` can be (boolean|string).
    inFunction("var a; var b; var c; a = true; c = (a || (b = 'foo'));");
    verify("c", createUnionType(BOOLEAN_TYPE, STRING_TYPE));
    verify("a", BOOLEAN_TYPE);
    verify("b", createUnionType(VOID_TYPE, STRING_TYPE));

    // This test should not assign the string to `y` and
    // should verify `y` as BOOLEAN_TYPE (true).
    inFunction("var y; y = true; y ||= 'foo';");
    verify("y", createUnionType(BOOLEAN_TYPE, STRING_TYPE));
  }

  @Test
  public void testAssignOrToBooleanEitherAbsoluteFalseOrTrue() {
    assuming("x", NULL_TYPE);
    inFunction("x ||= 'foo';");
    verify("x", STRING_TYPE);

    assuming("y", OBJECT_TYPE);
    inFunction("y ||= 'foo';");
    verify("y", OBJECT_TYPE);
  }

  @Test
  public void testAssignOrLHSFalsyRHSTruthy() {
    assuming("x", NULL_TYPE);
    assuming("obj", OBJECT_TYPE);
    inFunction("x ||= obj;");
    verify("x", OBJECT_TYPE);
  }

  @Test
  public void testAssignAndToNumeric() {
    // This in line with the existing type-inferencing logic for
    // or/and, since any boolean type is treated as {true, false},
    // but there can be improvement in precision here
    inFunction("var y = true; y &&= 20");
    verify("y", createUnionType(NUMBER_TYPE, BOOLEAN_TYPE));
  }

  @Test
  public void testAssignAndNoAssign() {
    // This example below show imprecision of && operator
    // The resulting type of Node n is (boolean|string), when it can be
    // more precise by verifying `x` as a boolean type (true).
    inFunction("var x = true && 'foo';");
    verify("x", createUnionType(BOOLEAN_TYPE, STRING_TYPE));

    // This test should not assign the string to `y` and
    // should verify `y` as BOOLEAN_TYPE (false).
    inFunction("var y = false; y &&= 'foo';");
    verify("y", createUnionType(BOOLEAN_TYPE, STRING_TYPE));
  }

  @Test
  public void testAssignAndToBooleanEitherAbsoluteFalseOrTrue() {
    assuming("x", NULL_TYPE);
    inFunction("x &&= 'foo';");
    verify("x", NULL_TYPE);

    assuming("y", OBJECT_TYPE);
    inFunction("y &&= 'foo';");
    verify("y", STRING_TYPE);
  }

  @Test
  public void testAssignAndLHSTruthyRHSFalsy() {
    assuming("x", OBJECT_TYPE);
    assuming("null", NULL_TYPE);
    inFunction("x &&= null;");
    verify("x", NULL_TYPE);
  }

  @Test
  public void testAssignCoalesceToNumeric() {
    assuming("x", NULL_TYPE);
    inFunction("x ??= 10;");
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testAssignCoalesceNoAssign() {
    assuming("x", STRING_TYPE);
    inFunction("x ??= 10;");
    verify("x", STRING_TYPE);
  }

  @Test
  public void testAssignCoalesceRHSAssignmentScope() {
    // since lhs is null, ??= executes rhs;
    // precision can be improved in the future for `y` to expect a number and not undefined,
    // but this is currently in accordance with the AST and not an oversight.
    inFunction("var y; var x = null; x ??= (y = 6)");
    verify("y", createUnionType(VOID_TYPE, NUMBER_TYPE));
    verify("x", NUMBER_TYPE);

    // ??= does not execute rhs
    inFunction("var y; var x = true; x ??= (y = 'a' + 6)");
    verify("y", VOID_TYPE);
    verify("x", BOOLEAN_TYPE);
  }

  @Test
  public void testAssignCoalesceJoin() {
    assuming("x", createNullableType(NUMBER_TYPE));
    inFunction("var y = ''; x ??= (y = x, 1)");
    verify("y", createNullableType(STRING_TYPE));
    verify("x", NUMBER_TYPE);
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
  public void testComparisonWithBigInt() {
    inFunction("var x = 'foo'; var y = (x = 3n) < 4;");
    verify("x", BIGINT_TYPE);
    inFunction("var x = 'foo'; var y = (x = 3n) > 4;");
    verify("x", BIGINT_TYPE);
    inFunction("var x = 'foo'; var y = (x = 3n) <= 4;");
    verify("x", BIGINT_TYPE);
    inFunction("var x = 'foo'; var y = (x = 3n) >= 4;");
    verify("x", BIGINT_TYPE);
  }

  @Test
  public void testThrownExpression() {
    inFunction(
        """
        var x = 'foo';
        try { throw new Error(x = 3); } catch (ex) {}
        """);
    verify("x", NUMBER_TYPE);
  }

  @Test
  public void testObjectLit() {
    inFunction("var x = {}; var out = x.a;");
    verify("out", UNKNOWN_TYPE); // Shouldn't this be 'undefined'?

    inFunction("var x = {a:1}; var out = x.a;");
    verify("out", NUMBER_TYPE);

    inFunction("var x = {a:1}; var out = x.a; x.a = 'string'; var out2 = x.a;");
    verify("out", NUMBER_TYPE);
    verify("out2", STRING_TYPE);

    inFunction("var x = { get a() {return 1} }; var out = x.a;");
    verify("out", UNKNOWN_TYPE);

    inFunction(
        """
        var x = {
          /** @return {number} */ get a() {return 1}
        };
        var out = x.a;
        """);
    verify("out", NUMBER_TYPE);

    inFunction("var x = { set a(b) {} }; var out = x.a;");
    verify("out", UNKNOWN_TYPE);

    inFunction(
        """
        var x = {
        /** @param {number} b */ set a(b) {} };
        var out = x.a;
        """);
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
    inFunction("let spread = {before, ...obj, after};");

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
        """
        /** @return {boolean} */
        Object.prototype.method = function() { return true; };
        var x = /** @type {Object} */ (this).method;
        """);
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
        """
        /** @param {{foo: (number|undefined)}} x */
        function f(x) {}
        var y = {};
        f(y);
        """);

    assertThat(getType("y").toString()).isEqualTo("{foo: (number|undefined)}");
  }

  @Test
  public void testBackwardsInferenceCallRestParameter() {
    inFunction(
        """
        /** @param {...{foo: (number|undefined)}} rest */
        function f(...rest) {}
        var y = {};
        f(y);
        """);

    assertThat(getType("y").toString()).isEqualTo("{foo: (number|undefined)}");
  }

  @Test
  public void testBackwardsInferenceNew() {
    inFunction(
        """
        /**
         * @constructor
         * @param {{foo: (number|undefined)}} x
         */
        function F(x) {}
        var y = {};
        new F(y);
        """);

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
        """
        /** @param {{a: boolean}|{b: string}} x */
        function f(x) {}
        var out = {};
        f(out);
        """);
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
    inFunction(
        """
        /** @param {string|{prop: (string|undefined)}} x */
        function f(x) {}
        var out = {};
        f(out);
        """);
    assertThat(getType("out").toString()).isEqualTo("{prop: (string|undefined)}");
  }

  @Test
  public void testFunctionTemplateType_specializedFunctionType_copiesColorIdCompnents() {
    inFunction(
        """
        /**
         * @template T
         * @param {T} a
         * @return {T}
         */
        function f(a) {}
        TEMPLATE: f;

        SPECIALIZED: f(10);
        """);

    FunctionType templateFn =
        getLabeledStatement("TEMPLATE")
            .statementNode
            .getFirstChild()
            .getJSType()
            .toMaybeFunctionType();
    FunctionType specializedFn =
        getLabeledStatement("SPECIALIZED")
            .statementNode
            .getFirstFirstChild()
            .getJSType()
            .toMaybeFunctionType();

    assertThat(specializedFn).isNotEqualTo(templateFn);
    assertThat(specializedFn.getReferenceName()).isEqualTo(templateFn.getReferenceName());
    assertThat(specializedFn.getSource()).isEqualTo(templateFn.getSource());
    assertThat(specializedFn.getGoogModuleId()).isEqualTo(templateFn.getGoogModuleId());
  }

  @Test
  public void testFunctionTemplateType_literalParam() {
    inFunction(
        """
        /**
         * @template T
         * @param {T} a
         * @return {T}
         */
        function f(a){}

        var result = f(10);
        """);
    verify("result", NUMBER_TYPE);
  }

  @Test
  public void testFunctionTemplateType_unionsPossibilities() {
    inFunction(
        """
        /**
         * @template T
         * @param {T} a
         * @param {T} b
         * @return {T}
         */
        function f(a, b){}

        var result = f(10, 'x');
        """);
    verify("result", registry.createUnionType(NUMBER_TYPE, STRING_TYPE));
  }

  @Test
  public void testFunctionTemplateType_willUseUnknown() {
    inFunction(
        """
        /**
         * @template T
         * @param {T} a
         * @return {T}
         */
        function f(a){}

        var result = f(/** @type {?} */ ({}));
        """);
    verify("result", UNKNOWN_TYPE);
  }

  @Test
  public void testFunctionTemplateType_willUseUnknown_butPrefersTighterTypes() {
    inFunction(
        """
        /**
         * @template T
         * @param {T} a
         * @param {T} b
         * @param {T} c
         * @return {T}
         */
        function f(a, b, c){}

        // Make sure `?` is dispreferred before *and* after a known type.
        var result = f('x', /** @type {?} */ ({}), 5);
        """);
    verify("result", registry.createUnionType(NUMBER_TYPE, STRING_TYPE));
  }

  @Test
  public void testFunctionTemplateType_recursesIntoFunctionParams() {
    inFunction(
        """
        /**
         * @template T
         * @param {function(T)} a
         * @return {T}
         */
        function f(a){}

        var result = f(function(/** number */ a) { });
        """);
    verify("result", NUMBER_TYPE);
  }

  @Test
  public void testFunctionTemplateType_recursesIntoFunctionParams_throughUnknown() {
    inFunction(
        """
        /**
         * @template T
         * @param {function(T)=} a
         * @return {T}
         */
        function f(a){}

        var result = f(/** @type {?} */ ({}));
        """);
    verify("result", UNKNOWN_TYPE);
  }

  @Test
  public void testFunctionTemplateType_unpacksUnions_fromParamType() {
    inFunction(
        """
        /**
         * @template T
         * @param {!Iterable<T>|number} a
         * @return {T}
         */
        function f(a){}

        var result = f(/** @type {!Iterable<number>} */ ({}));
        """);
    verify("result", NUMBER_TYPE);
  }

  @Test
  public void testFunctionTemplateType_unpacksUnions_fromArgType() {
    inFunction(
        """
        /**
         * @template T
         * @param {!Iterable<T>} a
         * @return {T}
         */
        function f(a){}

        // The arg type is illegal, but the inference should still work.
        var result = f(/** @type {!Iterable<number>|number} */ ({}));
        """);
    verify("result", NUMBER_TYPE);
  }

  @Test
  public void testFunctionTemplateType_unpacksUnions_fromArgType_acrossSubtypes() {
    inFunction(
        """
        /**
         * @template T
         * @param {!Iterable<T>} a
         * @return {T}
         */
        function f(a){}

        var result = f(/** @type {!Array<number>|!Generator<string>} */ ({}));
        """);
    verify("result", registry.createUnionType(NUMBER_TYPE, STRING_TYPE));
  }

  @Test
  public void testTypeTransformationTypePredicate() {
    inFunction(
        """
        /**
         * @return {R}
         * @template R := 'number' =:
         */
        function f(a){}
        var result = f(10);
        """);
    verify("result", NUMBER_TYPE);
  }

  @Test
  public void testTypeTransformationConditional() {
    inFunction(
        """
        /**
         * @param {T} a
         * @param {N} b
         * @return {R}
         * @template T, N
         * @template R := cond( eq(T, N), 'string', 'boolean' ) =:
         */
        function f(a, b){}
        var result = f(1, 2);
        var result2 = f(1, 'a');
        """);
    verify("result", STRING_TYPE);
    verify("result2", BOOLEAN_TYPE);
  }

  @Test
  public void testTypeTransformationNoneType() {
    inFunction(
        """
        /**
         * @return {R}
         * @template R := none() =:
         */
        function f(){}
        var result = f(10);
        """);
    verify("result", JSTypeNative.UNKNOWN_TYPE);
  }

  @Test
  public void testTypeTransformationUnionType() {
    inFunction(
        """
        /**
         * @param {S} a
         * @param {N} b
         * @return {R}
         * @template S, N
         * @template R := union(S, N) =:
         */
        function f(a, b) {}
        var result = f(1, 'a');
        """);
    verify("result", createUnionType(STRING_TYPE, NUMBER_TYPE));
  }

  @Test
  public void testTypeTransformationMapunion() {
    inFunction(
        """
        /**
         * @param {U} a
         * @return {R}
         * @template U
         * @template R :=
         * mapunion(U, (x) => cond(eq(x, 'string'), 'boolean', 'null'))
         * =:
         */
        function f(a) {}
        /** @type {string|number} */ var x;
        var result = f(x);
        """);
    verify("result", createUnionType(BOOLEAN_TYPE, NULL_TYPE));
  }

  @Test
  public void testTypeTransformationObjectUseCase() {
    inFunction(
        """
        /**\s
         * @param {T} a
         * @return {R}
         * @template T\s
         * @template R :=\s
         * mapunion(T, (x) =>\s
         *      cond(eq(x, 'string'), 'String',
         *      cond(eq(x, 'number'), 'Number',
         *      cond(eq(x, 'boolean'), 'Boolean',
         *      cond(eq(x, 'null'), 'Object',\s
         *      cond(eq(x, 'undefined'), 'Object',
         *      x))))))\s
         * =:
         */
        function Object(a) {}
        /** @type {(string|number|boolean)} */
        var o;
        var r = Object(o);
        """);
    verify(
        "r",
        createMultiParamUnionType(
            STRING_OBJECT_TYPE, NUMBER_OBJECT_TYPE, JSTypeNative.BOOLEAN_OBJECT_TYPE));
  }

  @Test
  public void testTypeTransformationObjectUseCase2() {
    inFunction(
        """
        /**\s
         * @param {T} a
         * @return {R}
         * @template T\s
         * @template R :=\s
         * mapunion(T, (x) =>\s
         *      cond(eq(x, 'string'), 'String',
         *      cond(eq(x, 'number'), 'Number',
         *      cond(eq(x, 'boolean'), 'Boolean',
         *      cond(eq(x, 'null'), 'Object',\s
         *      cond(eq(x, 'undefined'), 'Object',
         *      x))))))\s
         * =:
         */
        function fn(a) {}
        /** @type {(string|null|undefined)} */
        var o;
        var r = fn(o);
        """);
    verify("r", OBJECT_TYPE);
  }

  @Test
  public void testTypeTransformationObjectUseCase3() {
    inFunction(
        """
        /**\s
         * @param {T} a
         * @return {R}
         * @template T\s
         * @template R :=\s
         * mapunion(T, (x) =>\s
         *      cond(eq(x, 'string'), 'String',
         *      cond(eq(x, 'number'), 'Number',
         *      cond(eq(x, 'boolean'), 'Boolean',
         *      cond(eq(x, 'null'), 'Object',\s
         *      cond(eq(x, 'undefined'), 'Object',
         *      x))))))\s
         * =:
         */
        function fn(a) {}
        /** @type {(Array|undefined)} */
        var o;
        var r = fn(o);
        """);
    verify("r", OBJECT_TYPE);
  }

  @Test
  public void testTypeTransformationTypeOfVarWithInstanceOfConstructor() {
    inFunction(
        """
        /** @constructor */
        function Bar() {}
        var b = new Bar();
        /**\s
         * @return {R}
         * @template R := typeOfVar('b') =:
         */
        function f(){}
        var r = f();
        """);
    verify("r", getType("b"));
  }

  @Test
  public void testTypeTransformationTypeOfVarWithConstructor() {
    inFunction(
        """
        /** @constructor */
        function Bar() {}
        /**\s
         * @return {R}
         * @template R := typeOfVar('Bar') =:
         */
        function f(){}
        var r = f();
        """);
    verify("r", getType("Bar"));
  }

  @Test
  public void testTypeTransformationTypeOfVarWithTypedef() {
    inFunction(
        """
        /** @typedef {(string|number)} */
        var NumberLike;
        /** @type {!NumberLike} */
        var x;
        /**
         * @return {R}
         * @template R := typeOfVar('x') =:
         */
        function f(){}
        var r = f();
        """);
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationWithTypeFromConstructor() {
    inFunction(
        """
        /** @constructor */
        function Bar(){}
        var x = new Bar();
        /**\s
         * @return {R}
         * @template R := 'Bar' =:
         */
        function f(){}
        var r = f();
        """);
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationWithTypeFromTypedef() {
    inFunction(
        """
        /** @typedef {(string|number)} */
        var NumberLike;
        /** @type {!NumberLike} */
        var x;
        /**
         * @return {R}
         * @template R := 'NumberLike' =:
         */
        function f(){}
        var r = f();
        """);
    verify("r", createUnionType(STRING_TYPE, NUMBER_TYPE));
  }

  @Test
  public void testTypeTransformationWithTypeFromNamespace() {
    inFunction(
        """
        var wiz
        /** @constructor */
        wiz.async.Response = function() {};
        /**
         * @return {R}
         * @template R := typeOfVar('wiz.async.Response') =:
         */
        function f(){}
        var r = f();
        """);
    verify("r", getType("wiz.async.Response"));
  }

  @Test
  public void testTypeTransformationWithNativeTypeExpressionFunction() {
    inFunction(
        """
        /** @type {function(string, boolean)} */
        var x;
        /**
         * @return {R}
         * @template R := typeExpr('function(string, boolean)') =:
         */
        function f(){}
        var r = f();
        """);
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationWithNativeTypeExpressionFunctionReturn() {
    inFunction(
        """
        /** @type {function(): number} */
        var x;
        /**
         * @return {R}
         * @template R := typeExpr('function(): number') =:
         */
        function f(){}
        var r = f();
        """);
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationWithNativeTypeExpressionFunctionThis() {
    inFunction(
        """
        /** @type {function(this:boolean, string)} */
        var x;
        /**
         * @return {R}
         * @template R := typeExpr('function(this:boolean, string)') =:
         */
        function f(){}
        var r = f();
        """);
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationWithNativeTypeExpressionFunctionVarargs() {
    inFunction(
        """
        /** @type {function(string, ...number): number} */
        var x;
        /**
         * @return {R}
         * @template R := typeExpr('function(string, ...number): number') =:
         */
        function f(){}
        var r = f();
        """);
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationWithNativeTypeExpressionFunctionOptional() {
    inFunction(
        """
        /** @type {function(?string=, number=)} */
        var x;
        /**
         * @return {R}
         * @template R := typeExpr('function(?string=, number=)') =:
         */
        function f(){}
        var r = f();
        """);
    verify("r", getType("x"));
  }

  @Test
  public void testTypeTransformationRecordFromObject() {
    inFunction(
        """
        /**\s
         * @param {T} a
         * @return {R}
         * @template T\s
         * @template R := record(T) =:
         */
        function f(a) {}
        /** @type {{foo:?}} */
        var e;
        /** @type {?} */
        var bar;
        var r = f({foo:bar});
        """);
    assertThat(getType("r").isRecordType()).isTrue();
    verify("r", getType("e"));
  }

  @Test
  public void testTypeTransformationRecordFromObjectNested() {
    inFunction(
        """
        /**\s
         * @param {T} a
         * @return {R}
         * @template T\s
         * @template R :=
         * maprecord(record(T), (k, v) => record({[k]:record(v)})) =:
         */
        function f(a) {}
        /** @type {{foo:!Object, bar:!Object}} */
        var e;
        var r = f({foo:{}, bar:{}});
        """);
    assertThat(getType("r").isRecordType()).isTrue();
    verify("r", getType("e"));
  }

  @Test
  public void testTypeTransformationRecordFromObjectWithTemplatizedType() {
    inFunction(
        """
        /**\s
         * @param {T} a
         * @return {R}
         * @template T\s
         * @template R := record(T) =:
         */
        function f(a) {}
        /** @type {{foo:!Array<number>}} */
        var e;
        /** @type {!Array<number>} */
        var something;
        var r = f({foo:something});
        """);
    assertThat(getType("r").isRecordType()).isTrue();
    verify("r", getType("e"));
  }

  @Test
  public void testTypeTransformationIsTemplatizedPartially() {
    inFunction(
        """
        /**
         * @constructor
         * @template T, U
         */
        function Foo() {}
        /**
         * @template T := cond(isTemplatized(type('Foo', 'number')), 'number', 'string') =:
         * @return {T}
         */
        function f() { return 123; }
        var x = f();
        """);
    assertThat(getType("x").isNumber()).isTrue();
  }

  @Test
  public void testAssertTypeofProp() {
    assuming("x", createNullableType(OBJECT_TYPE));
    inFunction(
        """
        goog.asserts.assert(typeof x.prop != 'undefined');
        out = x.prop;
        """);
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
        """
        var obj;
        yield (obj = {a: 3, b: '4'});
        var a = obj.a;
        var b = obj.b;
        """);

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
        """
        let x = 1; // x is initially a number
        let y = [...[x = 'hi', 'there']]; // reassign x a string in the spread
        X: x;
        """);
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
        """
        (
        /** // preserve newlines
         * @param {...number} nums
         */
        function(str, ...nums) {
          NUMS: nums;
          let n = null;
          N_START: n;
          if (nums.length > 0) {
            n = nums[0];
            N_IF_TRUE: n;
          } else {
            N_IF_FALSE: n;
          }
          N_FINAL: n;
        }
        );
        """);
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
        """
        let {x, y} = obj;  // preserve newline
        X: x;
        Y: y;
        """);
    assertTypeOfExpression("X").toStringIsEqualTo("string");
    assertTypeOfExpression("Y").toStringIsEqualTo("number");

    assertScopeEnclosing("X").declares("x").withTypeThat().toStringIsEqualTo("string");
  }

  @Test
  public void testObjectDestructuringDeclarationInferenceWithDefaultValue() {
    inFunction(
        """
        var /** {x: (?string|undefined)} */ obj;
        let {x = 3} = obj;  // preserve newline
        X: x;
        """);
    assertTypeOfExpression("X").toStringIsEqualTo("(null|number|string)");
  }

  @Test
  public void testObjectDestructuringDeclarationInferenceWithUnnecessaryDefaultValue() {
    inFunction(
        """
        var /** {x: string} */ obj;
        let {x = 3} = obj;  // we ignore the default value's type
        X: x;
        """);
    // TODO(b/77597706): should this just be `string`?
    // the legacy behavior (typechecking transpiled code) produces (number|string), but we should
    // possibly realize that the default value will never be evaluated.
    assertTypeOfExpression("X").toStringIsEqualTo("(number|string)");
  }

  @Test
  public void testObjectDestructuringDeclarationInference_unknownRhsAndKnownDefaultValue() {
    inFunction(
        """
        var /** ? */ obj;
        let {x = 3} = obj;  // preserve newline
        X: x;
        """);
    assertTypeOfExpression("X").toStringIsEqualTo("?");
  }

  @Test
  public void testObjectDestructuringDeclarationInference_knownRhsAndUnknownDefaultValue() {
    inFunction(
        """
        var /** {x: (string|undefined)} */ obj;
        let {x = someUnknown} = obj;  // preserve newline
        X: x;
        """);
    assertTypeOfExpression("X").toStringIsEqualTo("?");
  }

  @Test
  public void testObjectDestructuringDeclaration_defaultValueEvaluatedAfterComputedProperty() {
    // contrived example to verify that we traverse the computed property before the default value.

    inFunction(
        """
        var /** !Object<string, (number|undefined)> */ obj = {};
        var a = 1;
        const {[a = 'string']: b = a} = obj
        A: a
        B: b
        """);

    assertTypeOfExpression("A").toStringIsEqualTo("string");
    assertTypeOfExpression("B").toStringIsEqualTo("(number|string)");
  }

  @Test
  public void testObjectDestructuringDeclarationInferenceWithUnknownProperty() {
    JSType recordType = registry.createRecordType(ImmutableMap.of());
    assuming("obj", recordType);

    inFunction(
        """
        let {x} = obj;  // preserve newline
        X: x;
        """);
    assertTypeOfExpression("X").toStringIsEqualTo("?");
  }

  @Test
  public void testObjectDestructuringDoesInferenceWithinComputedProp() {
    inFunction(
        """
        let y = 'foobar';  // preserve newline
        let {[y = 3]: z} = {};
        Y: y
        Z: z
        """);

    assertTypeOfExpression("Y").toStringIsEqualTo("number");
    assertTypeOfExpression("Z").toStringIsEqualTo("?");
  }

  @Test
  public void testObjectDestructuringUsesIObjectTypeForComputedProp() {
    inFunction(
        """
        let /** !IObject<string, number> */ myObj = {['foo']: 3};  // preserve newline
        let {[42]: x} = myObj;
        X: x
        """);

    assertTypeOfExpression("X").toStringIsEqualTo("number");
  }

  @Test
  public void testObjectDestructuringDeclarationWithNestedPattern() {
    inFunction(
        """
        let /** {a: {b: number}} */ obj = {a: {b: 3}};
        let {a: {b: x}} = obj;
        X: x
        """);

    assertTypeOfExpression("X").toStringIsEqualTo("number");
  }

  @Test
  public void testObjectDestructuringAssignmentToQualifiedName() {
    inFunction(
        """
        const ns = {};
        ({x: ns.x} = {x: 3});
        X: ns.x;
        """);

    assertTypeOfExpression("X").toStringIsEqualTo("number");
  }

  @Test
  public void testObjectDestructuringDeclarationInForOf() {
    inFunction(
        """
        const /** !Iterable<{x: number}> */ data = [{x: 3}];
        for (let {x} of data) {
          X: x;
        }
        """);

    assertTypeOfExpression("X").toStringIsEqualTo("number");
  }

  @Test
  public void testObjectDestructuringAssignInForOf() {
    inFunction(
        """
        const /** !Iterable<{x: number}> */ data = [{x: 3}];
        var x;
        for ({x} of data) {
          X: x;
        }
        """);

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
  public void testObjectLiteralNoSideEffect() {
    // Repro for b/260837012.
    inFunction(
        """
          for (let x = 0; x < 3; x++) {
            obj = {
              data: {tipsMetadata: ''},
              ...{}
            };
          }
        """);
    assertWithMessage("Type inference must not alter OBJECT_TYPE.")
        .that(registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE).hasOwnProperty("data"))
        .isFalse();
    // Check that the inferred type of 'obj' has a property 'data' associated with it.
    assertWithMessage("Cannot resolve type of 'obj'").that(getType("obj")).isNotNull();
    assertWithMessage("Expect property 'data' to be defined on 'obj'")
        .that(registry.canPropertyBeDefined(getType("obj"), "data"))
        .isNotEqualTo(JSTypeRegistry.PropDefinitionKind.UNKNOWN);
  }

  @Test
  public void testArrayDestructuringDeclaration() {
    inFunction(
        """
        const /** !Iterable<number> */ numbers = [1, 2, 3];
        let [x, y] = numbers;
        X: x
        Y: y
        """);

    assertTypeOfExpression("X").toStringIsEqualTo("number");
    assertTypeOfExpression("Y").toStringIsEqualTo("number");
  }

  @Test
  public void testArrayDestructuringDeclarationWithDefaultValue() {
    inFunction(
        """
        const /** !Iterable<(number|undefined)> */ numbers = [1, 2, 3];
        let [x = 'x', y = 'y'] = numbers;
        X: x
        Y: y
        """);

    assertTypeOfExpression("X").toStringIsEqualTo("(number|string)");
    assertTypeOfExpression("Y").toStringIsEqualTo("(number|string)");
  }

  @Test
  public void testArrayDestructuringDeclarationWithDefaultValueForNestedPattern() {
    inFunction(
        """
        const /** !Iterable<({x: number}|undefined)> */ xNumberObjs = [];
        let [{x = 'foo'} = {}] = xNumberObjs;
        X: x
        Y: y
        """);

    assertTypeOfExpression("X").toStringIsEqualTo("(number|string)");
  }

  @Test
  public void testArrayDestructuringDeclarationWithRest() {
    inFunction(
        """
        const /** !Iterable<number> */ numbers = [1, 2, 3];
        let [x, ...y] = numbers;
        X: x
        Y: y
        """);

    assertTypeOfExpression("X").toStringIsEqualTo("number");
    assertTypeOfExpression("Y").toStringIsEqualTo("Array<number>");
  }

  @Test
  public void testArrayDestructuringDeclarationWithNestedArrayPattern() {
    inFunction(
        """
        const /** !Iterable<!Iterable<number>> */ numbers = [[1, 2, 3]];
        let [[x], y] = numbers;
        X: x
        Y: y
        """);

    assertTypeOfExpression("X").toStringIsEqualTo("number");
    assertTypeOfExpression("Y").toStringIsEqualTo("Iterable<number,?,?>");
  }

  @Test
  public void testArrayDestructuringDeclarationWithNestedObjectPattern() {
    inFunction(
        """
        const /** !Iterable<{x: number}> */ numbers = [{x: 3}, {x: 4}];
        let [{x}, {x: y}] = numbers;
        X: x
        Y: y
        """);

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
        """
        const ns = {};
        const /** !Iterable<number> */ numbers = [1, 2, 3];
        [ns.x] = numbers;
        NSX: ns.x;
        """);

    assertTypeOfExpression("NSX").toStringIsEqualTo("number");
  }

  @Test
  public void testArrayDestructuringAssignWithGetElem() {
    // we don't update the scope on an assignment to a getelem, so this test just verifies that
    // a) type inference doesn't crash and b) type info validation passes.
    inFunction(
        """
        const arr = [];
        const /** !Iterable<number> */ numbers = [1, 2, 3];
        [arr[1]] = numbers;
        ARR1: arr[1];
        """);

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
        """
        try {
          throw {err: 3};
        } catch ({[x = 'err']: /** number */ err}) {
          ERR: err;
          X: x;
        }
        """);

    assertTypeOfExpression("ERR").toStringIsEqualTo("number");
    // verify we do inference on the assignment to `x` inside the computed property
    assertTypeOfExpression("X").toStringIsEqualTo("string");
  }

  @Test
  public void testTypeInferenceOccursInDestructuringForIn() {
    assuming("x", NUMBER_TYPE);

    inFunction(
        """
        /** @type {number} */
        String.prototype.length;

        var obj = {};
        for ({length: obj.length} in {'1': 1, '22': 22}) {
          LENGTH: obj.length; // set to '1'.length and '22'.length
        }
        """);

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
        """
        const {data} = obj;
        OBJ: obj;
        DATA: data
        """);

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

    inFunction("/** @return {number} */ const fn = foo;");

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
    inFunction("/** @constructor @extends {Foo} */ const FooExtended = mixin();");

    JSType fooWithInterfaceType = getType("FooExtended");
    assertType(fooWithInterfaceType).isNotEqualTo(fooType);
    assertType(fooWithInterfaceType).toStringIsEqualTo("function(new:FooExtended): ?");
  }

  @Test
  public void testSideEffectsInEsExportDefaultInferred() {
    assuming("foo", NUMBER_TYPE);
    assuming("bar", UNKNOWN_TYPE);

    inScript("export default (bar = foo, foo = 'not a number');");

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

  @Test
  public void testDynamicImport() {
    inScript("const foo = import('foo');");

    JSType promiseOfUnknownType =
        registry.createTemplatizedType(
            registry.getNativeObjectType(JSTypeNative.PROMISE_TYPE),
            registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));
    assertType(getType("foo")).isSubtypeOf(promiseOfUnknownType);
  }

  @Test
  public void testDynamicImport2() {
    withModules(
        ImmutableList.of(
            "export default 1; export /** @return {string} */ function Bar() { return 'bar'; };",
            // modules are named of the format `testcode#` based off their index.
            // testcode0 refers the first module.
            "const foo = import('./testcode0');"));

    assertType(getType("foo"))
        .toStringIsEqualTo(
            """
            Promise<{
              Bar: function(): string,
              default: number
            }>\
            """);
  }

  @Test
  public void testRequireDynamic() {
    withModules(
        ImmutableList.of(
            "goog.module('foo'); exports.barFunc = function Bar() { return 'bar'; };",
            "const f = goog.requireDynamic('foo');",
            "const e = goog.require('foo');"));

    assertType(getType("f")).toStringIsEqualTo("IThenable<{barFunc: function(): ?}>");
    assertType(getType("e")).toStringIsEqualTo("{barFunc: function(): ?}");
  }

  @Test
  public void testDynamicImportAfterModuleRewriting() {
    withModules(
        ImmutableList.of(
            """
            const module$testcode0 = {};
            /** @const */ module$testcode0.default = 1;
            /** @return {string} */ function Bar() { return 'bar'; };
            /** @const */ module$testcode0.Bar = Bar;
            """,
            // modules are named of the format `testcode#` based off their index.
            // testcode0 refers the first module.
            "const foo = import('./testcode0');"));

    assertType(getType("foo"))
        .toStringIsEqualTo(
            """
            Promise<{
              Bar: function(): string,
              default: number
            }>\
            """);
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
