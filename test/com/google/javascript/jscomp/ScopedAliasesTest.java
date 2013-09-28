/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.CompilerOptions.AliasTransformation;
import com.google.javascript.jscomp.CompilerOptions.AliasTransformationHandler;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SourcePosition;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link ScopedAliases}
 *
 * @author robbyw@google.com (Robby Walker)
 */
public class ScopedAliasesTest extends CompilerTestCase {

  private static final String GOOG_SCOPE_START_BLOCK =
      "goog.scope(function() {";
  private static final String GOOG_SCOPE_END_BLOCK = "});";

  private static final String SCOPE_NAMESPACE =
      "var $jscomp = {}; $jscomp.scope = {};";

  private static String EXTERNS = "var window;";

  AliasTransformationHandler transformationHandler =
      CompilerOptions.NULL_ALIAS_TRANSFORMATION_HANDLER;

  public ScopedAliasesTest() {
    super(EXTERNS);
  }

  private void testScoped(String code, String expected) {
    test(GOOG_SCOPE_START_BLOCK + code + GOOG_SCOPE_END_BLOCK, expected);
  }

  private void testScopedNoChanges(String aliases, String code) {
    testScoped(aliases + code, code);
  }

  public void testOneLevel() {
    testScoped("var g = goog;g.dom.createElement(g.dom.TagName.DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  public void testTwoLevel() {
    testScoped("var d = goog.dom;d.createElement(d.TagName.DIV);",
               "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  public void testTransitive() {
    testScoped("var d = goog.dom;var DIV = d.TagName.DIV;d.createElement(DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  public void testTransitiveInSameVar() {
    testScoped("var d = goog.dom, DIV = d.TagName.DIV;d.createElement(DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  public void testMultipleTransitive() {
    testScoped(
        "var g=goog;var d=g.dom;var t=d.TagName;var DIV=t.DIV;" +
            "d.createElement(DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  public void testFourLevel() {
    testScoped("var DIV = goog.dom.TagName.DIV;goog.dom.createElement(DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  public void testWorksInClosures() {
    testScoped(
        "var DIV = goog.dom.TagName.DIV;" +
            "goog.x = function() {goog.dom.createElement(DIV);};",
        "goog.x = function() {goog.dom.createElement(goog.dom.TagName.DIV);};");
  }

  public void testOverridden() {
    // Test that the alias doesn't get unaliased when it's overridden by a
    // parameter.
    testScopedNoChanges(
        "var g = goog;", "goog.x = function(g) {g.z()};");
    // Same for a local.
    testScopedNoChanges(
        "var g = goog;", "goog.x = function() {var g = {}; g.z()};");
  }

  public void testTwoScopes() {
    test(
        "goog.scope(function() {var g = goog;g.method()});" +
        "goog.scope(function() {g.method();});",
        "goog.method();g.method();");
  }

  public void testTwoSymbolsInTwoScopes() {
    test(
        "var goog = {};" +
        "goog.scope(function() { var g = goog; g.Foo = function() {}; });" +
        "goog.scope(function() { " +
        "  var Foo = goog.Foo; goog.bar = function() { return new Foo(); };" +
        "});",
        "var goog = {};" +
        "goog.Foo = function() {};" +
        "goog.bar = function() { return new goog.Foo(); };");
  }

  public void testAliasOfSymbolInGoogScope() {
    test(
        "var goog = {};" +
        "goog.scope(function() {" +
        "  var g = goog;" +
        "  g.Foo = function() {};" +
        "  var Foo = g.Foo;" +
        "  Foo.prototype.bar = function() {};" +
        "});",
        "var goog = {}; goog.Foo = function() {};" +
        "goog.Foo.prototype.bar = function() {};");
  }

  public void testScopedFunctionReturnThis() {
    test("goog.scope(function() { " +
         "  var g = goog; g.f = function() { return this; };" +
         "});",
         "goog.f = function() { return this; };");
  }

  public void testScopedFunctionAssignsToVar() {
    test("goog.scope(function() { " +
         "  var g = goog; g.f = function(x) { x = 3; return x; };" +
         "});",
         "goog.f = function(x) { x = 3; return x; };");
  }

  public void testScopedFunctionThrows() {
    test("goog.scope(function() { " +
         "  var g = goog; g.f = function() { throw 'error'; };" +
         "});",
         "goog.f = function() { throw 'error'; };");
  }

  public void testPropertiesNotChanged() {
    testScopedNoChanges("var x = goog.dom;", "y.x();");
  }

  public void testShadowedVar() {
    test("var Popup = {};" +
         "var OtherPopup = {};" +
         "goog.scope(function() {" +
         "  var Popup = OtherPopup;" +
         "  Popup.newMethod = function() { return new Popup(); };" +
         "});",
         "var Popup = {};" +
         "var OtherPopup = {};" +
         "OtherPopup.newMethod = function() { return new OtherPopup(); };");
  }

  public void testShadowedScopedVar() {
    test("var goog = {};" +
         "goog.bar = {};" +
         "goog.scope(function() {" +
         "  var bar = goog.bar;" +
         // This is bogus, because when the aliases are expanded, goog will
         // shadow goog.bar.
         "  bar.newMethod = function(goog) { return goog + bar; };" +
         "});",
         "var goog={};" +
         "goog.bar={};" +
         "goog.bar.newMethod=function(goog$$1){return goog$$1 + goog.bar}");
  }

  public void testShadowedScopedVarTwoScopes() {
    test("var goog = {};" +
         "goog.bar = {};" +
         "goog.scope(function() {" +
         "  var bar = goog.bar;" +
         "  bar.newMethod = function(goog, a) { return bar + a; };" +
         "});" +
         "goog.scope(function() {" +
         "  var bar = goog.bar;" +
         "  bar.newMethod2 = function(goog, b) { return bar + b; };" +
         "});",
         "var goog={};" +
         "goog.bar={};" +
         "goog.bar.newMethod=function(goog$$1, a){return goog.bar + a};" +
         "goog.bar.newMethod2=function(goog$$1, b){return goog.bar + b};");
  }

  public void testUsingObjectLiteralToEscapeScoping() {
    // There are many ways to shoot yourself in the foot with goog.scope
    // and make the compiler generate bad code. We generally don't care.
    //
    // We only try to protect against accidental mis-use, not deliberate
    // mis-use.
    test(
        "var goog = {};" +
        "goog.bar = {};" +
        "goog.scope(function() {" +
        "  var bar = goog.bar;" +
        "  var baz = goog.bar.baz;" +
        "  goog.foo = function() {" +
        "    goog.bar = {baz: 3};" +
        "    return baz;" +
        "  };" +
        "});",
        "var goog = {};" +
        "goog.bar = {};" +
        "goog.foo = function(){" +
        "  goog.bar = {baz:3};" +
        "  return goog.bar.baz;" +
        "};");
  }

  private void testTypes(String aliases, String code) {
    testScopedNoChanges(aliases, code);
    verifyTypes();
  }

  private void verifyTypes() {
    Compiler lastCompiler = getLastCompiler();
    new TypeVerifyingPass(lastCompiler).process(lastCompiler.externsRoot,
        lastCompiler.jsRoot);
  }

  public void testJsDocType() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {x} */ types.actual;"
        + "/** @type {goog.Timer} */ types.expected;");
  }

  public void testJsDocParameter() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @param {x} a */ types.actual;"
        + "/** @param {goog.Timer} a */ types.expected;");
  }

  public void testJsDocExtends() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @extends {x} */ types.actual;"
        + "/** @extends {goog.Timer} */ types.expected;");
  }

  public void testJsDocImplements() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @implements {x} */ types.actual;"
        + "/** @implements {goog.Timer} */ types.expected;");
  }

  public void testJsDocEnum() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @enum {x} */ types.actual;"
        + "/** @enum {goog.Timer} */ types.expected;");
  }

  public void testJsDocReturn() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @return {x} */ types.actual;"
        + "/** @return {goog.Timer} */ types.expected;");
  }

  public void testJsDocThis() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @this {x} */ types.actual;"
        + "/** @this {goog.Timer} */ types.expected;");
  }

  public void testJsDocThrows() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @throws {x} */ types.actual;"
        + "/** @throws {goog.Timer} */ types.expected;");
  }

  public void testJsDocSubType() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {x.Enum} */ types.actual;"
        + "/** @type {goog.Timer.Enum} */ types.expected;");
  }

  public void testJsDocTypedef() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @typedef {x} */ types.actual;"
        + "/** @typedef {goog.Timer} */ types.expected;");
  }

  public void testArrayJsDoc() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {Array.<x>} */ types.actual;"
        + "/** @type {Array.<goog.Timer>} */ types.expected;");
  }

  public void testObjectJsDoc() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {{someKey: x}} */ types.actual;"
        + "/** @type {{someKey: goog.Timer}} */ types.expected;");
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {{x: number}} */ types.actual;"
        + "/** @type {{x: number}} */ types.expected;");
  }

  public void testUnionJsDoc() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {x|Object} */ types.actual;"
        + "/** @type {goog.Timer|Object} */ types.expected;");
  }

  public void testFunctionJsDoc() {
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {function(x) : void} */ types.actual;"
        + "/** @type {function(goog.Timer) : void} */ types.expected;");
    testTypes(
        "var x = goog.Timer;",
        ""
        + "/** @type {function() : x} */ types.actual;"
        + "/** @type {function() : goog.Timer} */ types.expected;");
  }

  public void testForwardJsDoc() {
    testScoped(
        "/**\n" +
        " * @constructor\n" +
        " */\n" +
        "foo.Foo = function() {};" +
        "/** @param {Foo.Bar} x */ foo.Foo.actual = function(x) {3};" +
        "var Foo = foo.Foo;" +
        "/** @constructor */ Foo.Bar = function() {};" +
        "/** @param {foo.Foo.Bar} x */ foo.Foo.expected = function(x) {};",

        "/**\n" +
        " * @constructor\n" +
        " */\n" +
        "foo.Foo = function() {};" +
        "/** @param {foo.Foo.Bar} x */ foo.Foo.actual = function(x) {3};" +
        "/** @constructor */ foo.Foo.Bar = function() {};" +
        "/** @param {foo.Foo.Bar} x */ foo.Foo.expected = function(x) {};");
    verifyTypes();
  }

  public void testTestTypes() {
    try {
      testTypes(
          "var x = goog.Timer;",
          ""
          + "/** @type {function() : x} */ types.actual;"
          + "/** @type {function() : wrong.wrong} */ types.expected;");
      fail("Test types should fail here.");
    } catch (AssertionError e) {
    }
  }

  public void testNullType() {
    testTypes(
        "var x = goog.Timer;",
        "/** @param draggable */ types.actual;"
        + "/** @param draggable */ types.expected;");
  }

  public void testIssue772() {
    testTypes(
        "var b = a.b;" +
        "var c = b.c;",
        "/** @param {c.MyType} x */ types.actual;" +
        "/** @param {a.b.c.MyType} x */ types.expected;");
  }

  // TODO(robbyw): What if it's recursive?  var goog = goog.dom;

  // FAILURE CASES

  private void testFailure(String code, DiagnosticType expectedError) {
    test(code, null, expectedError);
  }

  private void testScopedFailure(String code, DiagnosticType expectedError) {
    test("goog.scope(function() {" + code + "});", null, expectedError);
  }

  public void testScopedThis() {
    testScopedFailure("this.y = 10;", ScopedAliases.GOOG_SCOPE_REFERENCES_THIS);
    testScopedFailure("var x = this;",
        ScopedAliases.GOOG_SCOPE_REFERENCES_THIS);
    testScopedFailure("fn(this);", ScopedAliases.GOOG_SCOPE_REFERENCES_THIS);
  }

  public void testAliasRedefinition() {
    testScopedFailure("var x = goog.dom; x = goog.events;",
        ScopedAliases.GOOG_SCOPE_ALIAS_REDEFINED);
  }

  public void testAliasNonRedefinition() {
    test("var y = {}; goog.scope(function() { goog.dom = y; });",
         "var y = {}; goog.dom = y;");
  }

  public void testCtorAlias() {
    test("var x = {y: {}};" +
         "goog.scope(function() {" +
         "  var y = x.y;" +
         "  y.ClassA = function() { this.b = new ClassB(); };" +
         "  y.ClassB = function() {};" +
         "  var ClassB = y.ClassB;" +
         "});",
         "var x = {y: {}};" +
         "x.y.ClassA = function() { this.b = new x.y.ClassB(); };" +
         "x.y.ClassB = function() { };");
  }

  public void testAliasCycle() {
    test("var x = {y: {}};" +
         "goog.scope(function() {" +
         "  var y = z.x;" +
         "  var z = y.x;" +
         "  y.ClassA = function() {};" +
         "  z.ClassB = function() {};" +
         "});", null,
         ScopedAliases.GOOG_SCOPE_ALIAS_CYCLE);
  }

  public void testScopedReturn() {
    testScopedFailure("return;", ScopedAliases.GOOG_SCOPE_USES_RETURN);
    testScopedFailure("var x = goog.dom; return;",
        ScopedAliases.GOOG_SCOPE_USES_RETURN);
  }

  public void testScopedThrow() {
    testScopedFailure("throw 'error';", ScopedAliases.GOOG_SCOPE_USES_THROW);
  }

  public void testUsedImproperly() {
    testFailure("var x = goog.scope(function() {});",
        ScopedAliases.GOOG_SCOPE_USED_IMPROPERLY);
  }

  public void testBadParameters() {
    testFailure("goog.scope()", ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
    testFailure("goog.scope(10)", ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
    testFailure("goog.scope(function() {}, 10)",
        ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
    testFailure("goog.scope(function z() {})",
        ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
    testFailure("goog.scope(function(a, b, c) {})",
        ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
  }

  public void testNonAliasLocal() {
    testScopedFailure("function f() {}",
        ScopedAliases.GOOG_SCOPE_NON_ALIAS_LOCAL);
  }

  public void testOkAliasLocal() {
    testScoped("var x = 10;",
               SCOPE_NAMESPACE + "$jscomp.scope.x = 10");
    testScoped("var x = goog['dom'];",
               SCOPE_NAMESPACE + "$jscomp.scope.x = goog['dom']");
    testScoped("var x = 10, y = 9;",
               SCOPE_NAMESPACE + "$jscomp.scope.x = 10; $jscomp.scope.y = 9;");
    testScoped("var x = 10, y = 9; goog.getX = function () { return x + y; }",
               SCOPE_NAMESPACE + "$jscomp.scope.x = 10; $jscomp.scope.y = 9;" +
               "goog.getX = function () { " +
               "    return $jscomp.scope.x + $jscomp.scope.y; }");
  }

  public void testAliasReassign() {
    testScopedFailure("var x = 3; x = 5;",
        ScopedAliases.GOOG_SCOPE_ALIAS_REDEFINED);
  }

  public void testMultipleLocals() {
    test("goog.scope(function () { var x = 3; });" +
         "goog.scope(function () { var x = 4; });",
         SCOPE_NAMESPACE + "$jscomp.scope.x = 3; $jscomp.scope.x$1 = 4");
  }

  public void testIssue1103a() {
    test("goog.scope(function () {" +
         "  var a;" +
         "  foo.bar = function () { a = 1; };" +
         "});",
         SCOPE_NAMESPACE + "foo.bar = function () { $jscomp.scope.a = 1; }");
  }

  public void testIssue1103b() {
    test("goog.scope(function () {" +
         "  var a = foo, b, c = 1;" +
         "});",
         SCOPE_NAMESPACE + "$jscomp.scope.c=1");
  }

  public void testIssue1103c() {
    test("goog.scope(function () {" +
         "  /** @type {number} */ var a;" +
         "});",
         SCOPE_NAMESPACE + "/** @type {number} */ $jscomp.scope.a;");
  }

  // Alias Recording Tests
  // TODO(tylerg) : update these to EasyMock style tests once available
  public void testNoGoogScope() {
    String fullJsCode =
        "var g = goog;\n g.dom.createElement(g.dom.TagName.DIV);";
    TransformationHandlerSpy spy = new TransformationHandlerSpy();
    transformationHandler = spy;
    test(fullJsCode, fullJsCode);

    assertTrue(spy.observedPositions.isEmpty());
  }

  public void testRecordOneAlias() {
    String fullJsCode = GOOG_SCOPE_START_BLOCK
        + "var g = goog;\n g.dom.createElement(g.dom.TagName.DIV);\n"
        + GOOG_SCOPE_END_BLOCK;
    String expectedJsCode = "goog.dom.createElement(goog.dom.TagName.DIV);\n";

    TransformationHandlerSpy spy = new TransformationHandlerSpy();
    transformationHandler = spy;
    test(fullJsCode, expectedJsCode);

    assertTrue(spy.observedPositions.containsKey("testcode"));
    List<SourcePosition<AliasTransformation>> positions =
        spy.observedPositions.get("testcode");
    assertEquals(1, positions.size());
    verifyAliasTransformationPosition(1, 0, 2, 1, positions.get(0));

    assertEquals(1, spy.constructedAliases.size());
    AliasSpy aliasSpy = (AliasSpy) spy.constructedAliases.get(0);
    assertEquals("goog", aliasSpy.observedDefinitions.get("g"));
  }

  public void testRecordMultipleAliases() {
    String fullJsCode = GOOG_SCOPE_START_BLOCK
        + "var g = goog;\n var b= g.bar;\n var f = goog.something.foo;"
        + "g.dom.createElement(g.dom.TagName.DIV);\n b.foo();"
        + GOOG_SCOPE_END_BLOCK;
    String expectedJsCode =
        "goog.dom.createElement(goog.dom.TagName.DIV);\n goog.bar.foo();";
    TransformationHandlerSpy spy = new TransformationHandlerSpy();
    transformationHandler = spy;
    test(fullJsCode, expectedJsCode);

    assertTrue(spy.observedPositions.containsKey("testcode"));
    List<SourcePosition<AliasTransformation>> positions =
        spy.observedPositions.get("testcode");
    assertEquals(1, positions.size());
    verifyAliasTransformationPosition(1, 0, 3, 1, positions.get(0));

    assertEquals(1, spy.constructedAliases.size());
    AliasSpy aliasSpy = (AliasSpy) spy.constructedAliases.get(0);
    assertEquals("goog", aliasSpy.observedDefinitions.get("g"));
    assertEquals("g.bar", aliasSpy.observedDefinitions.get("b"));
    assertEquals("goog.something.foo", aliasSpy.observedDefinitions.get("f"));
  }

  public void testRecordAliasFromMultipleGoogScope() {
    String firstGoogScopeBlock = GOOG_SCOPE_START_BLOCK
        + "\n var g = goog;\n g.dom.createElement(g.dom.TagName.DIV);\n"
        + GOOG_SCOPE_END_BLOCK;
    String fullJsCode = firstGoogScopeBlock + "\n\nvar l = abc.def;\n\n"
        + GOOG_SCOPE_START_BLOCK
        + "\n var z = namespace.Zoo;\n z.getAnimals(l);\n"
        + GOOG_SCOPE_END_BLOCK;

    String expectedJsCode = "goog.dom.createElement(goog.dom.TagName.DIV);\n"
        + "\n\nvar l = abc.def;\n\n" + "\n namespace.Zoo.getAnimals(l);\n";

    TransformationHandlerSpy spy = new TransformationHandlerSpy();
    transformationHandler = spy;
    test(fullJsCode, expectedJsCode);


    assertTrue(spy.observedPositions.containsKey("testcode"));
    List<SourcePosition<AliasTransformation>> positions =
        spy.observedPositions.get("testcode");
    assertEquals(2, positions.size());

    verifyAliasTransformationPosition(1, 0, 6, 0, positions.get(0));

    verifyAliasTransformationPosition(8, 0, 11, 4, positions.get(1));

    assertEquals(2, spy.constructedAliases.size());
    AliasSpy aliasSpy = (AliasSpy) spy.constructedAliases.get(0);
    assertEquals("goog", aliasSpy.observedDefinitions.get("g"));

    aliasSpy = (AliasSpy) spy.constructedAliases.get(1);
    assertEquals("namespace.Zoo", aliasSpy.observedDefinitions.get("z"));
  }

  private void verifyAliasTransformationPosition(int startLine, int startChar,
      int endLine, int endChar, SourcePosition<AliasTransformation> pos) {
    assertEquals(startLine, pos.getStartLine());
    assertEquals(startChar, pos.getPositionOnStartLine());
    assertTrue(
        "expected endline >= " + endLine + ".  Found " + pos.getEndLine(),
        pos.getEndLine() >= endLine);
    assertTrue("expected endChar >= " + endChar + ".  Found "
        + pos.getPositionOnEndLine(), pos.getPositionOnEndLine() >= endChar);
  }

  @Override
  protected ScopedAliases getProcessor(Compiler compiler) {
    return new ScopedAliases(compiler, null, transformationHandler);
  }

  private static class TransformationHandlerSpy
      implements AliasTransformationHandler {

    private final Map<String, List<SourcePosition<AliasTransformation>>>
        observedPositions = Maps.newHashMap();

    public final List<AliasTransformation> constructedAliases =
        Lists.newArrayList();

    @Override
    public AliasTransformation logAliasTransformation(
        String sourceFile, SourcePosition<AliasTransformation> position) {
      if(!observedPositions.containsKey(sourceFile)) {
        observedPositions.put(sourceFile,
            Lists.<SourcePosition<AliasTransformation>> newArrayList());
      }
      observedPositions.get(sourceFile).add(position);
      AliasTransformation spy = new AliasSpy();
      constructedAliases.add(spy);
      return spy;
    }
  }

  private static class AliasSpy implements AliasTransformation {
    public final Map<String, String> observedDefinitions = Maps.newHashMap();

    @Override
    public void addAlias(String alias, String definition) {
      observedDefinitions.put(alias, definition);
    }
  }

  private static class TypeVerifyingPass
      implements CompilerPass, NodeTraversal.Callback {
    private final Compiler compiler;
    private List<Node> actualTypes = null;

    public TypeVerifyingPass(Compiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      NodeTraversal.traverse(compiler, root, this);
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
        Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSDocInfo info = n.getJSDocInfo();
      if (info != null) {
        Collection<Node> typeNodes = info.getTypeNodes();
        if (typeNodes.size() > 0) {
          if (actualTypes != null) {
            List<Node> expectedTypes = Lists.newArrayList();
            for (Node typeNode : info.getTypeNodes()) {
              expectedTypes.add(typeNode);
            }
            assertEquals("Wrong number of JsDoc types",
                expectedTypes.size(), actualTypes.size());
            for (int i = 0; i < expectedTypes.size(); i++) {
              assertNull(
                  expectedTypes.get(i).checkTreeEquals(actualTypes.get(i)));
            }
          } else {
            actualTypes = Lists.newArrayList();
            for (Node typeNode : info.getTypeNodes()) {
              actualTypes.add(typeNode);
            }
          }
        }
      }
    }
  }
}
