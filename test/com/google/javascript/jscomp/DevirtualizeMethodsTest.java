/*
 * Copyright 2009 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.testing.ColorSubject.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.AccessorSummary.PropertyAccessKind;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import com.google.javascript.rhino.Node;
import org.jspecify.nullness.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DevirtualizeMethods} */
@RunWith(JUnit4.class)
public final class DevirtualizeMethodsTest extends CompilerTestCase {
  private static final String EXTERNAL_SYMBOLS =
      DEFAULT_EXTERNS + "var extern;extern.externalMethod";

  public DevirtualizeMethodsTest() {
    super(EXTERNAL_SYMBOLS);
  }

  @Override
  protected int getNumRepetitions() {
    // run pass once.
    return 1;
  }

  @Before
  public void customSetUp() throws Exception {
    enableNormalize(); // Required for `OptimizeCalls`.
    disableTypeCheck();
  }

  /** Combine source strings using ';' as the separator. */
  private static String semicolonJoin(String... parts) {
    return Joiner.on(";").join(parts);
  }

  @Test
  public void testRewritePrototypeMethodsWithCorrectColors() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    String input =
        lines(
            "/** @constructor */",
            "function A() { this.x = 3; }",
            "A: A;",
            "/** @return {number} */",
            "A.prototype.foo = function() { return this.x; };",
            "/** @param {number} p",
            "    @return {number} */",
            "A.prototype.bar = function(p) { return this.x; };",
            "A.prototype.baz = function() {};",
            "var o = new A();",
            "FOO_RESULT: o.foo();",
            "BAR_RESULT: o.bar(2);",
            "BAZ_RESULT: o.baz()");
    String expected =
        lines(
            "/** @constructor */",
            "function A(){ this.x = 3; }",
            "A: A;",
            "var JSCompiler_StaticMethods_foo = ",
            "  function(JSCompiler_StaticMethods_foo$self) {",
            "    return JSCompiler_StaticMethods_foo$self.x",
            "  };",
            "var JSCompiler_StaticMethods_bar = ",
            "  function(JSCompiler_StaticMethods_bar$self, p) {",
            "    return JSCompiler_StaticMethods_bar$self.x",
            "  };",
            "var JSCompiler_StaticMethods_baz = ",
            "  function(JSCompiler_StaticMethods_baz$self) {};",
            "var o = new A();",
            "FOO_RESULT: JSCompiler_StaticMethods_foo(o);",
            "BAR_RESULT: JSCompiler_StaticMethods_bar(o, 2);",
            "BAZ_RESULT: JSCompiler_StaticMethods_baz(o)");

    enableTypeCheck();
    replaceTypesWithColors();
    disableCompareJsDoc();

    test(input, expected);
    checkColorOfRewrittenMethods();
  }

  private void checkColorOfRewrittenMethods() {
    Color fooType = getLabelledExpression("FOO_RESULT").getFirstChild().getColor();
    Color barType = getLabelledExpression("BAR_RESULT").getFirstChild().getColor();
    Color bazType = getLabelledExpression("BAZ_RESULT").getFirstChild().getColor();
    Color fooResultType = getLabelledExpression("FOO_RESULT").getColor();
    Color barResultType = getLabelledExpression("BAR_RESULT").getColor();
    Color bazResultType = getLabelledExpression("BAZ_RESULT").getColor();

    assertThat(fooResultType).isEqualTo(StandardColors.NUMBER);
    assertThat(barResultType).isEqualTo(StandardColors.NUMBER);
    assertThat(bazResultType).isEqualTo(StandardColors.NULL_OR_VOID);

    assertThat(fooType).isEqualTo(StandardColors.TOP_OBJECT);
    assertThat(barType).isEqualTo(StandardColors.TOP_OBJECT);
    assertThat(bazType).isEqualTo(StandardColors.TOP_OBJECT);
  }

  private Node getLabelledExpression(String label) {
    Node root = getLastCompiler().getJsRoot();

    return checkNotNull(
        getLabelledExpressionIfPresent(label, root),
        "Could not find statement matching label %s",
        label);
  }

  private static @Nullable Node getLabelledExpressionIfPresent(String label, Node root) {
    if (root.isLabel() && root.getFirstChild().getString().equals(label)) {
      Node labelledBlock = root.getSecondChild();
      checkState(labelledBlock.isBlock(), labelledBlock);
      checkState(
          labelledBlock.hasOneChild() && labelledBlock.getOnlyChild().isExprResult(),
          "Unexpected children of BLOCK %s",
          labelledBlock);
      return labelledBlock.getOnlyChild().getOnlyChild();
    }
    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      Node possibleMatch = getLabelledExpressionIfPresent(label, child);
      if (possibleMatch != null) {
        return possibleMatch;
      }
    }
    return null;
  }

  @Test
  public void testRewriteChained() {
    String source =
        lines(
            "A.prototype.foo = function(){return this.b};",
            "B.prototype.bar = function(){};",
            "o.foo().bar()");

    String expected =
        lines(
            "var JSCompiler_StaticMethods_foo = ",
            "function(JSCompiler_StaticMethods_foo$self) {",
            "  return JSCompiler_StaticMethods_foo$self.b",
            "};",
            "var JSCompiler_StaticMethods_bar = ",
            "function(JSCompiler_StaticMethods_bar$self) {",
            "};",
            "JSCompiler_StaticMethods_bar(JSCompiler_StaticMethods_foo(o))");
    test(source, expected);
  }

  /** Inputs for declaration used as an r-value tests. */
  private static class NoRewriteDeclarationUsedAsRValue {
    static final String DECL = "a.prototype.foo = function() {}";
    static final String CALL = "o.foo()";

    private NoRewriteDeclarationUsedAsRValue() {}
  }

  @Test
  public void testRewriteDeclIsExpressionStatement() {
    test(
        semicolonJoin(NoRewriteDeclarationUsedAsRValue.DECL, NoRewriteDeclarationUsedAsRValue.CALL),
        "var JSCompiler_StaticMethods_foo ="
            + "function(JSCompiler_StaticMethods_foo$self) {};"
            + "JSCompiler_StaticMethods_foo(o)");
  }

  @Test
  public void testNoRewriteDeclUsedAsAssignmentRhs() {
    testSame(
        semicolonJoin(
            "var c = " + NoRewriteDeclarationUsedAsRValue.DECL,
            NoRewriteDeclarationUsedAsRValue.CALL));
  }

  @Test
  public void testNoRewriteDeclUsedAsCallArgument() {
    testSame(
        semicolonJoin(
            "f(" + NoRewriteDeclarationUsedAsRValue.DECL + ")",
            NoRewriteDeclarationUsedAsRValue.CALL));
  }

  @Test
  public void testRewrite_ifDefined_unconditionally() {
    test(
        lines(
            "function a(){}",
            "a.prototype.foo = function() {return this.x};",
            "var o = new a;",
            "o.foo()"),
        lines(
            "function a(){}",
            "var JSCompiler_StaticMethods_foo = ",
            "function(JSCompiler_StaticMethods_foo$self) {",
            "  return JSCompiler_StaticMethods_foo$self.x",
            "};",
            "var o = new a;",
            "JSCompiler_StaticMethods_foo(o);"));
  }

  @Test
  public void noRewrite_forPropertyNamesAccessedReflectively() {
    test(
        externs("function use() {}"),
        srcs(
            lines(
                "class C { m() {} n() {} }",
                "const c = new C();",
                "c.m();",
                "c.n();",
                // this call should prevent devirtualizing m() but not n()
                "use(C.prototype, $jscomp.reflectProperty('m', C.prototype));")),
        expected(
            lines(
                "var JSCompiler_StaticMethods_n = function(JSCompiler_StaticMethods_n$self) {};",
                "class C { m() {} }",
                "const c = new C();",
                "c.m();",
                "JSCompiler_StaticMethods_n(c);",
                "use(C.prototype, $jscomp.reflectProperty('m', C.prototype));")));
  }

  private void testNoRewriteIfDefinitionSiteBetween(String prefix, String suffix) {
    testSame(
        lines(
            "function a(){}",
            prefix + "a.prototype.foo = function() {return this.x}" + suffix + ";",
            "var o = new a;",
            "o.foo()"));
  }

  @Test
  public void testNoRewrite_ifDefinedIn_ifScope() {
    testNoRewriteIfDefinitionSiteBetween("if (true) ", "");
  }

  @Test
  public void testNoRewrite_ifDefinedIn_loopScope() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    testNoRewriteIfDefinitionSiteBetween("while (true) ", "");
  }

  @Test
  public void testNoRewrite_ifDefinedIn_switchScope() {
    testNoRewriteIfDefinitionSiteBetween("switch (true) { case true: ", "; }");
  }

  @Test
  public void testNoRewrite_ifDefinedIn_functionScope() {
    testNoRewriteIfDefinitionSiteBetween("function f() { ", "; }");
  }

  @Test
  public void testNoRewrite_ifDefinedIn_arrowFunctionScope() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    testNoRewriteIfDefinitionSiteBetween("() => ", "");
  }

  @Test
  public void testNoRewrite_ifDefinedIn_blockScope() {
    // Some declarations are block scoped in ES6 and so might have different values. This could make
    // multiple definitions with identical node structure behave differently.
    testNoRewriteIfDefinitionSiteBetween("{ ", "; }");
  }

  @Test
  public void testNoRewrite_ifDefinedBy_andExpression_withLiteral() {
    // TODO(nickreid): This may be unnecessarily restrictive. We could probably rely on the
    // definitions being identical or not to filter this case.
    testNoRewriteIfDefinitionSiteBetween("", " && function() {}");
  }

  @Test
  public void testNoRewrite_ifDefinedBy_andExpression_withReference() {
    testNoRewriteIfDefinitionSiteBetween("", " && bar");
  }

  @Test
  public void testNoRewrite_ifDefinedBy_orExpression_withLiteral() {
    // TODO(nickreid): This may be unnecessarily restrictive. We could probably rely on the
    // definitions being identical or not to filter this case.
    testNoRewriteIfDefinitionSiteBetween("", " || function() {}");
  }

  @Test
  public void testNoRewrite_ifDefinedBy_orExpression_withReference() {
    testNoRewriteIfDefinitionSiteBetween("", " || bar");
  }

  @Test
  public void testNoRewrite_ifDefinedBy_ternaryExpression_withLiteral() {
    // TODO(nickreid): This may be unnecessarily restrictive. We could probably rely on the
    // definitions being identical or not to filter this case.

    // Make the functions look different, just to be safe.
    testNoRewriteIfDefinitionSiteBetween("", " ? function() { this.a; } : function() { this.b; }");
  }

  @Test
  public void testNoRewrite_ifDefinedBy_ternaryExpression_withReference() {
    testNoRewriteIfDefinitionSiteBetween("", " ? function() { } : bar");
  }

  private void testRewritePreservesFunctionKind(String fnKeyword) {
    test(
        srcs(
            lines(
                "function a(){}",
                "a.prototype.foo = " + fnKeyword + "() { return 0; };",
                "",
                "(new a()).foo();")),
        expected(
            lines(
                "function a(){}",
                "var JSCompiler_StaticMethods_foo =",
                "    " + fnKeyword + "(JSCompiler_StaticMethods_foo$self) { return 0; };",
                "",
                "JSCompiler_StaticMethods_foo(new a());")));
  }

  @Test
  public void testRewrite_preservesAsync() {
    testRewritePreservesFunctionKind("async function");
  }

  @Test
  public void testRewrite_preservesAsyncGenerator() {
    testRewritePreservesFunctionKind("async function*");
  }

  @Test
  public void testRewrite_preservesGenerator() {
    testRewritePreservesFunctionKind("function*");
  }

  @Test
  public void testRewrite_replacesMultipleThisRefreences() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  a() {",
                "     alert(this);",
                "     alert(this, this);",
                "  }",
                "}",
                "new Foo().a();")),
        expected(
            lines(
                "var JSCompiler_StaticMethods_a = function(JSCompiler_StaticMethods_a$self) {",
                "  alert(JSCompiler_StaticMethods_a$self);",
                "  alert(JSCompiler_StaticMethods_a$self, JSCompiler_StaticMethods_a$self);",
                "};",
                "",
                "class Foo { }",
                "JSCompiler_StaticMethods_a(new Foo());")));
  }

  @Test
  public void testRewrite_defaultParams_usesThisReference() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  bar(y = this) { return y; }",
                "}",
                "",
                // We need at least one normal call to trigger rewriting.
                "x.bar();")),
        expected(
            lines(
                "var JSCompiler_StaticMethods_bar = function(",
                "    JSCompiler_StaticMethods_bar$self,",
                "    y = JSCompiler_StaticMethods_bar$self) {",
                "  return y;",
                "};",
                "class Foo { }", //
                "",
                "JSCompiler_StaticMethods_bar(x);")));
  }

  @Test
  public void testNoRewrite_defaultParams_usesSuperReference() {
    testSame(
        lines(
            "class Foo {", //
            "  bar(y = super.toString()) { return y; }",
            "}",
            "",
            // We need at least one normal call to trigger rewriting.
            "x.bar();"));
  }

  @Test
  public void testNoRewrite_ifDefinedByArrow() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    testSame(
        lines(
            "function a(){};", //
            "a.prototype.foo = () => 5;",
            "",
            "(new a()).foo();"));
  }

  @Test
  public void testNoRewrite_namespaceFunctions() {
    String source = "function a(){}; a.foo = function() {return this.x}; a.foo()";
    testSame(source);
  }

  @Test
  public void testRewrite_ifMultipleIdenticalDefinitions() {
    test(
        lines(
            "function A(){};",
            "A.prototype.getFoo = function() { return 1; }; ",
            "",
            "function B(){};",
            "B.prototype.getFoo = function() { return 1; }; ",
            "",
            "x.getFoo();"),
        lines(
            "function A() {}; ",
            "var JSCompiler_StaticMethods_getFoo =",
            "    function(JSCompiler_StaticMethods_getFoo$self) { return 1; };",
            "",
            "function B(){};",
            "B.prototype.getFoo = function() { return 1 };", // Dead definition.
            "",
            "JSCompiler_StaticMethods_getFoo(x);"));
  }

  @Test
  public void testRewrite_ifMultipleIdenticalDefinitions_ensuresDefinitionBeforeInvocations() {
    test(
        lines(
            "function A() {};",
            "A.prototype.getFoo = function() { return 1; }; ",
            "",
            "x.getFoo();",
            "",
            "function B() {};",
            "B.prototype.getFoo = function() { return 1; }; ",
            "",
            "y.getFoo();"),
        lines(
            "function A() {}; ",
            "var JSCompiler_StaticMethods_getFoo =",
            "    function(JSCompiler_StaticMethods_getFoo$self) { return 1; };",
            "",
            "JSCompiler_StaticMethods_getFoo(x);",
            "",
            "function B() {};",
            "B.prototype.getFoo=function() { return 1; };", // Dead definition.
            "",
            "JSCompiler_StaticMethods_getFoo(y);"));
  }

  @Test
  public void testRewrite_ifMultipleIdenticalDefinitions_withThis() {
    test(
        lines(
            "function A() {};",
            "A.prototype.getFoo = function() { return this._foo + 1; };",
            "",
            "function B() {};",
            "B.prototype.getFoo = function() { return this._foo + 1; }; ",
            "",
            "x.getFoo();"),
        lines(
            "function A() {}; ",
            "var JSCompiler_StaticMethods_getFoo =",
            "    function(JSCompiler_StaticMethods_getFoo$self) {",
            "      return JSCompiler_StaticMethods_getFoo$self._foo + 1",
            "    };",
            "",
            "function B() {};",
            "B.prototype.getFoo = function() { return this._foo + 1 };", // Dead definition.
            "",
            "JSCompiler_StaticMethods_getFoo(x);"));
  }

  @Test
  public void testRewrite_ifMultipleIdenticalDefinitions_withLocalNames() {
    // This case is included for completeness. `Normalization` is a prerequisite for this pass so
    // the naming conflict is resolved before devirtualization even begins. The change in names
    // invalidates devirtualization by making the definition subtrees unequal.
    test(
        lines(
            // Note how `f` refers to different objects in the function bodies, even though the
            // bodies are node-wise identical.
            "function A() {};",
            "A.prototype.getFoo = function f() { return f.prop; }; ",
            "",
            "function B() {};",
            "B.prototype.getFoo = function f() { return f.prop; }; ",
            "",
            "x.getFoo();"),
        lines(
            "function A() {};",
            "A.prototype.getFoo = function f() { return f.prop; }; ",
            "",
            "function B() {};",
            "B.prototype.getFoo = function f$jscomp$1() { return f$jscomp$1.prop; }; ",
            "",
            "x.getFoo();"));
  }

  @Test
  public void testNoRewrite_ifMultipleDistinctDefinitions() {
    testSame(
        lines(
            "function A(){}; A.prototype.getFoo = function() { return 1; }; ",
            "function B(){}; B.prototype.getFoo = function() { return 2; }; ",
            "var x = Math.random() ? new A() : new B();",
            "alert(x.getFoo());"));
  }

  /** Inputs for object literal tests. */
  private static class NoRewritePrototypeObjectLiteralsTestInput {
    static final String REGULAR = "b.prototype.foo = function() { return 1; }";
    static final String OBJ_LIT = "a.prototype = {foo : function() { return 2; }}";
    static final String CALL = "o.foo()";

    private NoRewritePrototypeObjectLiteralsTestInput() {}
  }

  @Test
  public void testRewritePrototypeNoObjectLiterals() {
    test(
        semicolonJoin(
            NoRewritePrototypeObjectLiteralsTestInput.REGULAR,
            NoRewritePrototypeObjectLiteralsTestInput.CALL),
        lines(
            "var JSCompiler_StaticMethods_foo = ",
            "function(JSCompiler_StaticMethods_foo$self) { return 1; };",
            "JSCompiler_StaticMethods_foo(o)"));
  }

  @Test
  public void testRewrite_definedUsingProtoObjectLit() {
    test(
        semicolonJoin(
            NoRewritePrototypeObjectLiteralsTestInput.OBJ_LIT,
            NoRewritePrototypeObjectLiteralsTestInput.CALL),
        lines(
            "var JSCompiler_StaticMethods_foo = function(JSCompiler_StaticMethods_foo$self) {",
            "  return 2;",
            "};",
            "a.prototype={};",
            "",
            "JSCompiler_StaticMethods_foo(o)"));
  }

  @Test
  public void testNoRewrite_multipleDefinitions_definedUsingProtoObjectLit_definedUsingGetProp() {
    testSame(
        semicolonJoin(
            NoRewritePrototypeObjectLiteralsTestInput.OBJ_LIT,
            NoRewritePrototypeObjectLiteralsTestInput.REGULAR,
            NoRewritePrototypeObjectLiteralsTestInput.CALL));
  }

  @Test
  public void testNoRewrite_noDefinition() {
    testSame("a.externalMethod()");
  }

  @Test
  public void testNoRewrite_externMethod() {
    testSame(
        externs("A.prototype.externalMethod = function(){};"), //
        srcs("o.externalMethod()"));
  }

  @Test
  public void testNoRewrite_exportedMethod_viaCodingConvention() {
    // no rewriting without call; regardless of leading underscore
    testSame("a.prototype.foo = function() {};");
    testSame("a.prototype._foo = function() {};");

    // renames as expected
    test(
        "function a() {} a.prototype.foo = function() {}; let o = new a; o.foo();",
        lines(
            "function a() {}",
            "var JSCompiler_StaticMethods_foo =",
            "function(JSCompiler_StaticMethods_foo$self) {};",
            "let o = new a;",
            "JSCompiler_StaticMethods_foo(o);"));

    // no renaming, as leading _ indicates exported symbol
    testSame("function a() {} a.prototype._foo = function() {}; let o = new a; o._foo();");
  }

  @Test
  public void testRewriteNoVarArgs() {
    String source =
        lines(
            "function a(){}",
            "a.prototype.foo = function(args) {return args};",
            "var o = new a;",
            "o.foo()");

    String expected =
        lines(
            "function a(){}",
            "var JSCompiler_StaticMethods_foo = ",
            "  function(JSCompiler_StaticMethods_foo$self, args) {return args};",
            "var o = new a;",
            "JSCompiler_StaticMethods_foo(o)");

    test(source, expected);
  }

  @Test
  public void testRewrite_argInsideOptionalChainingCall() {
    String source =
        lines(
            "function a(){}",
            "a.prototype.foo = function(args) {return args};",
            "var o = new a;",
            "a?.(o.foo())");

    String expected =
        lines(
            "function a(){}",
            "var JSCompiler_StaticMethods_foo = ",
            "  function(JSCompiler_StaticMethods_foo$self, args) {return args};",
            "var o = new a;",
            "a?.(JSCompiler_StaticMethods_foo(o))");

    test(source, expected);
  }

  @Test
  public void testRewrite_lhsOfOptionalChainingCall() {
    String source =
        lines(
            "function a(){}",
            "a.prototype.foo = function(args) {return args};",
            "var o = new a;",
            "o.foo()?.a");

    String expected =
        lines(
            "function a(){}",
            "var JSCompiler_StaticMethods_foo = ",
            "  function(JSCompiler_StaticMethods_foo$self, args) {return args};",
            "var o = new a;",
            "JSCompiler_StaticMethods_foo(o)?.a");

    test(source, expected);
  }

  @Test
  public void testNoRewriteVarArgs() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    String source =
        lines(
            "function a(){}",
            "a.prototype.foo = function(var_args) {return arguments};",
            "var o = new a;",
            "o.foo()");
    testSame(source);
  }

  /** Inputs for invalidating reference tests. */
  private static class NoRewriteNonCallReferenceTestInput {
    static final String BASE =
        lines(
            "function a() {}", //
            "a.prototype.foo = function() {return this.x};",
            "var o = new a;");

    private NoRewriteNonCallReferenceTestInput() {}
  }

  @Test
  public void testRewrite_callReference() {
    String expected =
        lines(
            "function a(){}",
            "var JSCompiler_StaticMethods_foo = ",
            "function(JSCompiler_StaticMethods_foo$self) {",
            "  return JSCompiler_StaticMethods_foo$self.x",
            "};",
            "var o = new a;",
            "JSCompiler_StaticMethods_foo(o);");

    test(NoRewriteNonCallReferenceTestInput.BASE + "o.foo()", expected);
  }

  @Test
  public void testNoRewrite_noReferences() {
    testSame(NoRewriteNonCallReferenceTestInput.BASE);
  }

  @Test
  public void testNoRewrite_nonCallReference_viaGetprop() {
    testSame(
        lines(
            NoRewriteNonCallReferenceTestInput.BASE, //
            "o.foo();", // We need at least one normal call to trigger rewriting.
            "o.foo;"));
  }

  @Test
  public void testNoRewrite_nonCallReference_viaDestructuring() {
    testSame(
        lines(
            NoRewriteNonCallReferenceTestInput.BASE, //
            "o.foo();", // We need at least one normal call to trigger rewriting.
            "const {foo: x} = o;"));
  }

  @Test
  public void testNoRewrite_nonCallReference_viaGetprop_usingFnCall() {
    // TODO(nickreid): Add rewriting support for this.
    testSame(
        lines(
            NoRewriteNonCallReferenceTestInput.BASE, //
            "o.foo();", // We need at least one normal call to trigger rewriting.
            "o.foo.call(null);"));
  }

  @Test
  public void testNoRewrite_nonCallReference_viaGetprop_usingFnApply() {
    // TODO(nickreid): Add rewriting support for this.
    testSame(
        lines(
            NoRewriteNonCallReferenceTestInput.BASE, //
            "o.foo();", // We need at least one normal call to trigger rewriting.
            "o.foo.apply(null);"));
  }

  @Test
  public void testNoRewrite_nonCallReference_viaGetprop_asArgument() {
    testSame(
        lines(
            NoRewriteNonCallReferenceTestInput.BASE, //
            "o.foo();", // We need at least one normal call to trigger rewriting.
            "bar(o.foo, null);"));
  }

  @Test
  public void testNoRewrite_nonCallReference_viaTaggedTemplateString() {
    // TODO(nickreid): Add rewriting support for this.
    testSame(
        lines(
            NoRewriteNonCallReferenceTestInput.BASE, //
            "o.foo();", // We need at least one normal call to trigger rewriting.
            "o.foo`Hello World!`;"));
  }

  @Test
  public void testNoRewrite_optChain() {
    testSame(
        lines(
            NoRewriteNonCallReferenceTestInput.BASE, //
            "o.foo();", // We need at least one normal call to trigger rewriting.
            "o.foo?.();"));
  }

  @Test
  public void testNoRewrite_nonCallReference_viaNew() {
    // TODO(nickreid): Add rewriting support for this.
    testSame(
        lines(
            NoRewriteNonCallReferenceTestInput.BASE, //
            "o.foo();",
            "new o.foo();"));
  }

  /** Inputs for nested definition tests. */
  private static class NoRewriteNestedFunctionTestInput {
    static final String PREFIX = "a.prototype.foo = function() {";
    static final String SUFFIX = "o.foo()";
    static final String INNER = "a.prototype.bar = function() {}; o.bar()";
    static final String EXPECTED_PREFIX =
        "var JSCompiler_StaticMethods_foo=" + "function(JSCompiler_StaticMethods_foo$self){";
    static final String EXPECTED_SUFFIX = "JSCompiler_StaticMethods_foo(o)";

    private NoRewriteNestedFunctionTestInput() {}
  }

  @Test
  public void testRewriteNoNestedFunction() {
    test(
        semicolonJoin(
            NoRewriteNestedFunctionTestInput.PREFIX + "}",
            NoRewriteNestedFunctionTestInput.SUFFIX,
            NoRewriteNestedFunctionTestInput.INNER),
        semicolonJoin(
            NoRewriteNestedFunctionTestInput.EXPECTED_PREFIX + "}",
            NoRewriteNestedFunctionTestInput.EXPECTED_SUFFIX,
            "var JSCompiler_StaticMethods_bar=" + "function(JSCompiler_StaticMethods_bar$self){}",
            "JSCompiler_StaticMethods_bar(o)"));
  }

  @Test
  public void testNoRewriteNestedFunction() {
    test(
        NoRewriteNestedFunctionTestInput.PREFIX
            + NoRewriteNestedFunctionTestInput.INNER
            + "};"
            + NoRewriteNestedFunctionTestInput.SUFFIX,
        NoRewriteNestedFunctionTestInput.EXPECTED_PREFIX
            + NoRewriteNestedFunctionTestInput.INNER
            + "};"
            + NoRewriteNestedFunctionTestInput.EXPECTED_SUFFIX);
  }

  @Test
  public void testRewrite_definedUsingGetProp_withArgs_callUsingGetProp() {
    String source =
        lines(
            "function a(){}",
            "a.prototype.foo = function(args) {return args};",
            "var o = new a;",
            "o.foo()");
    String expected =
        lines(
            "function a(){}",
            "var JSCompiler_StaticMethods_foo = ",
            "  function(JSCompiler_StaticMethods_foo$self, args) {return args};",
            "var o = new a;",
            "JSCompiler_StaticMethods_foo(o)");
    test(source, expected);
  }

  @Test
  public void testNoRewrite_optChainGetProp() {
    String source =
        lines(
            "function a(){}",
            "a.prototype.foo = function(args) {return args};",
            "var o = new a;",
            "o?.foo()");
    testSame(source);
  }

  @Test
  public void testRewrite_definedUsingGetElem_withArgs_callUsingGetProp() {
    String source =
        lines(
            "function a(){}",
            "a.prototype['foo'] = function(args) {return args};",
            "var o = new a;",
            "o.foo()");
    testSame(source);
  }

  @Test
  public void testNoRewrite_definedUsingGetProp_withArgs_noCall_bracketAccess() {
    String source =
        lines(
            "function a(){}",
            "a.prototype.foo = function(args) {return args};",
            "var o = new a;",
            "o['foo']");
    testSame(source);
  }

  @Test
  public void testNoRewrite_optChainGetElemAccess() {
    String source =
        lines(
            "function a(){}",
            "a.prototype.foo = function(args) {return args};",
            "var o = new a;",
            "o?.['foo']");
    testSame(source);
  }

  @Test
  public void testNoRewrite_definedUsingGetElem_withArgs_noCall_bracketAccess() {
    String source =
        lines(
            "function a(){}",
            "a.prototype['foo'] = function(args) {return args};",
            "var o = new a;",
            "o['foo']");
    testSame(source);
  }

  @Test
  public void testRewrite_definedInExpression_thatCreatesScope_reportsScopeAsDeleted() {
    test(
        lines(
            "(function() {}).prototype.foo = function() {extern();};",
            // A call is needed to trigger rewriting.
            "a.foo();"),
        lines(
            "var JSCompiler_StaticMethods_foo = function(JSCompiler_StaticMethods_foo$self) {",
            "  extern();",
            "};",
            "JSCompiler_StaticMethods_foo(a);"));
  }

  @Test
  public void testRewrite_definedInExpression_withSideEffects() {
    // TODO(nickreid): Expect this not to be a rewrite (or confirm it's safe).
    test(
        lines(
            "extern().prototype.foo = function() { };",
            // A call is needed to trigger rewriting.
            "a.foo()"),
        // We think this is risky because if `extern()` had side effects they'd be eliminated.
        lines(
            "var JSCompiler_StaticMethods_foo=function(JSCompiler_StaticMethods_foo$self){};",
            "JSCompiler_StaticMethods_foo(a)"));
  }

  @Test
  public void testRewrite_definedInExpression_withInvocation() {
    test(
        srcs(
            lines(
                "(", //
                "  class { bar() { } },",
                "  a.bar()",
                ");")),
        expected(
            lines(
                "var JSCompiler_StaticMethods_bar = function(JSCompiler_StaticMethods_bar$self) {",
                "};",
                "(",
                "  class { },",
                "  JSCompiler_StaticMethods_bar(a)",
                ");")));
  }

  @Test
  public void testNoRewrite_definedUsingStringKey_inPrototypeLiteral_usingSuper() {
    // TODO(b/120452418): Add rewriting support for this.
    testSame(
        srcs(
            lines(
                "function a(){}",
                "a.prototype = {",
                // Don't use `super.bar()` because that might effect the test.
                "  foo() { return super.x; }",
                "};",
                "",
                // We need at least one normal call to trigger rewriting.
                "x.foo()")));
  }

  @Test
  public void testRewrite_definedUsingClassMember_prototypeMethod() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  bar() { return 5; }",
                "}",
                "",
                "x.bar();")),
        expected(
            lines(
                "var JSCompiler_StaticMethods_bar = function(JSCompiler_StaticMethods_bar$self) {",
                "  return 5;",
                "};",
                "class Foo { }", //
                "",
                "JSCompiler_StaticMethods_bar(x);")));
  }

  @Test
  public void testRewrite_definedUsingClassMember_prototypeMethod_usingThis() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  bar() { return this; }",
                "}",
                "",
                // We need at least one normal call to trigger rewriting.
                "x.bar();")),
        expected(
            lines(
                "var JSCompiler_StaticMethods_bar = function(JSCompiler_StaticMethods_bar$self) {",
                "    return JSCompiler_StaticMethods_bar$self;",
                "};",
                "class Foo { }", //
                "",
                "JSCompiler_StaticMethods_bar(x);")));
  }

  @Test
  public void testNoRewrite_definedUsingClassMember_prototypeMethod_usingSuper() {
    // TODO(b/120452418): Add rewriting support for this.
    testSame(
        srcs(
            lines(
                "class Foo {", //
                // Don't use `super.bar()` because that might effect the test.
                "  bar() { return super.x; }",
                "}",
                "",
                // We need at least one normal call to trigger rewriting.
                "x.bar();")));
  }

  @Test
  public void testNoRewrite_definedUsingClassMember_constructor() {
    testSame(
        srcs(
            lines(
                "class Foo {", //
                "  constructor() { }",
                "}",
                "",
                // We need at least one normal call to trigger rewriting.
                "x.constructor();")));
  }

  @Test
  public void testRewrite_definedUsingClassMember_staticMethod() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  static bar() { return 5; }",
                "}",
                "",
                // We need at least one normal call to trigger rewriting.
                "x.bar();")),
        expected(
            lines(
                "var JSCompiler_StaticMethods_bar = function(JSCompiler_StaticMethods_bar$self) {",
                "  return 5;",
                "};",
                "class Foo { }", //
                "",
                "JSCompiler_StaticMethods_bar(x);")));
  }

  @Test
  public void testRewrite_definedUsingAssignment_staticMethod_onClass() {
    test(
        srcs(
            lines(
                "class Foo { }", //
                "Foo.bar = function() { return 5; }",
                "",
                // We need at least one normal call to trigger rewriting.
                "x.bar();")),
        expected(
            lines(
                "class Foo { }", //
                "",
                "var JSCompiler_StaticMethods_bar = function(JSCompiler_StaticMethods_bar$self) {",
                "  return 5;",
                "};",
                "",
                "JSCompiler_StaticMethods_bar(x);")));
  }

  @Test
  public void testRewrite_definedUsingAssignment_staticMethod_onFunction() {
    for (String annotation :
        ImmutableList.of("/** @constructor */", "/** @interface */", "/** @record*/")) {
      testRewrite_definedUsingAssignment_staticMethod_onFunction(annotation);
    }
  }

  private void testRewrite_definedUsingAssignment_staticMethod_onFunction(String annotation) {
    disableCompareJsDoc(); // multistage compilation simplifies jsdoc
    test(
        srcs(
            lines(
                annotation,
                "function Foo() { }", //
                "Foo.bar = function() { return 5; }",
                "",
                // We need at least one normal call to trigger rewriting.
                "x.bar();")),
        expected(
            lines(
                "function Foo() { }", //
                "",
                "var JSCompiler_StaticMethods_bar = function(JSCompiler_StaticMethods_bar$self) {",
                "  return 5;",
                "};",
                "",
                "JSCompiler_StaticMethods_bar(x);")));
  }

  @Test
  public void testRewrite_definedUsingClassMember_staticMethod_usingThis() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  static bar() { return this; }",
                "}",
                "",
                // We need at least one normal call to trigger rewriting.
                "x.bar();")),
        expected(
            lines(
                "var JSCompiler_StaticMethods_bar = function(JSCompiler_StaticMethods_bar$self) {",
                "  return JSCompiler_StaticMethods_bar$self;",
                "};",
                "class Foo { }", //
                "",
                "JSCompiler_StaticMethods_bar(x);")));
  }

  @Test
  public void testNoRewrite_definedUsingClassMember_staticMethod_usingSuper() {
    // TODO(b/120452418): Add rewriting support for this.
    testSame(
        srcs(
            lines(
                "class Foo {", //
                // Don't use `super.bar()` because that might effect the test.
                "  static bar() { return super.x; }",
                "}",
                "",
                // We need at least one normal call to trigger rewriting.
                "x.bar();")));
  }

  @Test
  public void testRewrite_callWithSuperReceiver_passesThis() {
    test(
        srcs(
            lines(
                "class Foo {", //
                // Don't use `super.bar()` because that might effect the test.
                "  bar() { return super.qux(); }",
                "}",
                "",
                "class Tig {",
                "  qux() { return 5; }",
                "}")),
        expected(
            lines(
                "class Foo {", //
                "  bar() { return JSCompiler_StaticMethods_qux(this); }",
                "}",
                "",
                "var JSCompiler_StaticMethods_qux = function(JSCompiler_StaticMethods_qux$self) {",
                "  return 5;",
                "};",
                "class Tig { }")));
  }

  @Test
  public void testNoRewrite_inClassWithLocalName_asClassName() {
    testSame(
        srcs(
            lines(
                "const Qux = class Foo {", //
                // TODO(nickreid): Add rewriting support for this so long as the local name is not
                // referenced.
                "  bar() { return 5; }",
                "}",
                "",
                // We need at least one normal call to trigger rewriting.
                "x.bar();")));
  }

  @Test
  public void testPropDefinition_notOnQualifiedName_doesNotCrash() {
    testSame(
        srcs(
            lines(
                "class Qux { }", //
                "",
                "new Qux().prop = function() { }")));
  }

  @Test
  public void testRewriteDeclWithConstJSDoc() {
    test(
        lines(
            "class C {", //
            "  /** @const */ foo() {}",
            "}",
            "o.foo();"),
        lines(
            "/** @const */",
            "var JSCompiler_StaticMethods_foo =",
            "  function(JSCompiler_StaticMethods_foo$self) {};",
            "class C {}",
            "JSCompiler_StaticMethods_foo(o)"));
  }

  @Test
  public void testNoRewriteGet1() {
    // Getters and setter require special handling.
    testSame("function a(){}; a.prototype = {get foo(){return f}}; var o = new a; o.foo()");
  }

  @Test
  public void testNoRewriteGet2() {
    // Getters and setter require special handling.
    testSame("function a(){}; a.prototype = {get foo(){return 1}}; var o = new a; o.foo");
  }

  @Test
  public void testNoRewriteSet1() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    // Getters and setter require special handling.
    String source = "function a(){}; a.prototype = {set foo(a){}}; var o = new a; o.foo()";
    testSame(source);
  }

  @Test
  public void testNoRewriteSet2() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    // Getters and setter require special handling.
    String source = "function a(){}; a.prototype = {set foo(a){}}; var o = new a; o.foo = 1";
    testSame(source);
  }

  @Test
  public void testNoRewrite_notImplementedMethod() {
    testSame("function a(){}; var o = new a; o.foo()");
  }

  @Test
  public void testWrapper() {
    testSame("(function() {})()");
  }

  @Test
  public void testRewrite_nestedFunction_hasThisBoundCorrectly() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  bar() {",
                "    return function() { return this; };",
                "  }",
                "}",
                "",
                // We need at least one normal call to trigger rewriting.
                "x.bar();")),
        expected(
            lines(
                "var JSCompiler_StaticMethods_bar = function(JSCompiler_StaticMethods_bar$self) {",
                "  return function() { return this; };",
                "};",
                "class Foo { }",
                "",
                "JSCompiler_StaticMethods_bar(x);")));
  }

  @Test
  public void testRewrite_nestedArrow_hasThisBoundCorrectly() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    test(
        srcs(
            lines(
                "class Foo {", //
                "  bar() {",
                "    return () => this;",
                "  }",
                "}",
                "",
                // We need at least one normal call to trigger rewriting.
                "x.bar();")),
        expected(
            lines(
                "var JSCompiler_StaticMethods_bar = function(JSCompiler_StaticMethods_bar$self) {",
                "  return () => JSCompiler_StaticMethods_bar$self;",
                "};",
                "class Foo { }",
                "",
                "JSCompiler_StaticMethods_bar(x);")));
  }

  @Test
  public void testExistenceOfAGetter_preventsDevirtualization() {
    declareAccessor("foo", PropertyAccessKind.GETTER_ONLY);

    // Imagine the getter returned a function.
    testSame(
        lines(
            "class Foo {", //
            "  foo() {}",
            "}",
            "x.foo();"));
  }

  @Test
  public void testExistenceOfASetter_preventsDevirtualization() {
    declareAccessor("foo", PropertyAccessKind.SETTER_ONLY);

    // This doesn't actually seem like a risk but it's hard to say, and other optimizations that use
    // optimize calls would be dangerous on setters.
    testSame(
        lines(
            "class Foo {", //
            "  foo() {}",
            "}",
            "x.foo();"));
  }

  @Test
  public void testThisProperty() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    testSame(
        lines(
            "class Foo {", //
            "  constructor() {",
            "    this.a = function b() { return 5; };",
            "    this.plus1 = (arg) => arg + 1;",
            "    this.tmp = this.plus1(1); ",
            "  }",
            "}",
            "console.log(new Foo().a());"));
  }

  @Test
  public void testNonStaticClassFieldNoRHS() {
    testSame(
        lines(
            "class Foo {", //
            "  a;",
            "}",
            "console.log(new Foo().a);"));
  }

  @Test
  public void testNonStaticClassFieldNonFunction() {
    testSame(
        lines(
            "class Foo {", //
            "  a = 2;",
            "}",
            "console.log(new Foo().a);"));
  }

  @Test
  public void testNonStaticClassFieldFunction() {
    testSame(
        lines(
            "class Foo {", //
            "  a = function x() { return 5; };",
            "}",
            "console.log(new Foo().a);"));
  }

  @Test
  public void testStaticClassFieldNoRHS() {
    testSame(
        lines(
            "class Foo {", //
            "  static a;",
            "}",
            "console.log(Foo.a);"));
  }

  @Test
  public void testStaticClassFieldNonFunction() {
    testSame(
        lines(
            "class Foo {", //
            "  static a = 2;",
            "}",
            "console.log(Foo.a);"));
  }

  @Test
  public void testStaticClassFieldFunction() {
    testSame(
        lines(
            "class Foo {", //
            "  static a = function x() { return 5; };",
            "}",
            "console.log(Foo.a);"));
  }

  private static class ModuleTestInput {
    static final String DEFINITION = "a.prototype.foo = function() {}";
    static final String USE = "x.foo()";

    static final String REWRITTEN_DEFINITION =
        "var JSCompiler_StaticMethods_foo=" + "function(JSCompiler_StaticMethods_foo$self){}";
    static final String REWRITTEN_USE = "JSCompiler_StaticMethods_foo(x)";

    private ModuleTestInput() {}
  }

  @Test
  public void testRewriteSameModule1() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk(semicolonJoin(ModuleTestInput.DEFINITION, ModuleTestInput.USE))
            // m2
            .addChunk("")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            semicolonJoin(ModuleTestInput.REWRITTEN_DEFINITION, ModuleTestInput.REWRITTEN_USE),
            // m2
            ""));
  }

  @Test
  public void testRewriteSameModule2() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("")
            // m2
            .addChunk(semicolonJoin(ModuleTestInput.DEFINITION, ModuleTestInput.USE))
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            semicolonJoin(ModuleTestInput.REWRITTEN_DEFINITION, ModuleTestInput.REWRITTEN_USE)));
  }

  @Test
  public void testRewriteSameModule3() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk(semicolonJoin(ModuleTestInput.USE, ModuleTestInput.DEFINITION))
            // m2
            .addChunk("")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            semicolonJoin(ModuleTestInput.REWRITTEN_USE, ModuleTestInput.REWRITTEN_DEFINITION),
            // m2
            ""));
  }

  @Test
  public void testRewrite_definitionModule_beforeUseModule() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk(ModuleTestInput.DEFINITION)
            // m2
            .addChunk(ModuleTestInput.USE)
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            ModuleTestInput.REWRITTEN_DEFINITION,
            // m2
            ModuleTestInput.REWRITTEN_USE));
  }

  @Test
  public void testNoRewrite_definitionModule_afterUseModule() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            .addChunk(ModuleTestInput.USE)
            .addChunk(ModuleTestInput.DEFINITION)
            .build();

    testSame(srcs(chunks));
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return OptimizeCalls.builder()
        .setCompiler(compiler)
        .setConsiderExterns(false)
        .addPass(new DevirtualizeMethods(compiler))
        .build();
  }
}
