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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.javascript.rhino.FunctionTypeI;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TypeI;

/**
 * Tests for {@link DevirtualizePrototypeMethods}
 *
 */
public final class DevirtualizePrototypeMethodsTest extends TypeICompilerTestCase {
  private static final String EXTERNAL_SYMBOLS =
      DEFAULT_EXTERNS + "var extern;extern.externalMethod";

  public DevirtualizePrototypeMethodsTest() {
    super(EXTERNAL_SYMBOLS);
  }

  @Override
  protected int getNumRepetitions() {
    // run pass once.
    return 1;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    this.mode = TypeInferenceMode.NEITHER;
  }

  /**
   * Combine source strings using ';' as the separator.
   */
  private static String semicolonJoin(String ... parts) {
    return Joiner.on(";").join(parts);
  }

  public void testRewritePrototypeMethodsWithCorrectTypes() throws Exception {
    String input =
        lines(
            "/** @constructor */",
            "function A() { this.x = 3; }",
            "/** @return {number} */",
            "A.prototype.foo = function() { return this.x; };",
            "/** @param {number} p",
            "    @return {number} */",
            "A.prototype.bar = function(p) { return this.x; };",
            "A.prototype.baz = function() {};",
            "var o = new A();",
            "o.foo();",
            "o.bar(2);",
            "o.baz()");
    String expected =
        lines(
            "/** @constructor */",
            "function A(){ this.x = 3; }",
            "var JSCompiler_StaticMethods_foo = ",
            "function(JSCompiler_StaticMethods_foo$self) {",
            "  return JSCompiler_StaticMethods_foo$self.x",
            "};",
            "var JSCompiler_StaticMethods_bar = ",
            "function(JSCompiler_StaticMethods_bar$self, p) {",
            "  return JSCompiler_StaticMethods_bar$self.x",
            "};",
            "var JSCompiler_StaticMethods_baz = ",
            "function(JSCompiler_StaticMethods_baz$self) {",
            "};",
            "var o = new A();",
            "JSCompiler_StaticMethods_foo(o);",
            "JSCompiler_StaticMethods_bar(o, 2);",
            "JSCompiler_StaticMethods_baz(o)");

    this.mode = TypeInferenceMode.OTI_ONLY;
    test(input, expected);
    checkTypeOfRewrittenMethods();

    this.mode = TypeInferenceMode.NTI_ONLY;
    test(input, expected);
    checkTypeOfRewrittenMethods();
  }

  private void checkTypeOfRewrittenMethods() {
    TypeI thisType = getTypeAtPosition(0).toMaybeFunctionType().getInstanceType();
    FunctionTypeI fooType = getTypeAtPosition(1, 0, 0).toMaybeFunctionType();
    FunctionTypeI barType = getTypeAtPosition(2, 0, 0).toMaybeFunctionType();
    FunctionTypeI bazType = getTypeAtPosition(3, 0, 0).toMaybeFunctionType();
    TypeI fooResultType = getTypeAtPosition(5, 0);
    TypeI barResultType = getTypeAtPosition(6, 0);
    TypeI bazResultType = getTypeAtPosition(7, 0);

    TypeI number = fooResultType;
    TypeI receiver = fooType.getTypeOfThis();
    assertTrue("Expected number: " + number, number.isNumberValueType());
    // NOTE: OTI has the receiver as unknown, NTI has it as null.
    assertTrue(
        "Expected null or unknown: " + receiver, receiver == null || receiver.isUnknownType());
    assertThat(barResultType).isEqualTo(number);

    // Check that foo's type is {function(A): number}
    assertThat(fooType.getParameterTypes()).containsExactly(thisType);
    assertThat(fooType.getReturnType()).isEqualTo(number);
    assertThat(fooType.getTypeOfThis()).isEqualTo(receiver);

    // Check that bar's type is {function(A, number): number}
    assertThat(barType.getParameterTypes()).containsExactly(thisType, number).inOrder();
    assertThat(barType.getReturnType()).isEqualTo(number);
    assertThat(barType.getTypeOfThis()).isEqualTo(receiver);

    // Check that baz's type is {function(A): undefined} in OTI and {function(A): ?} in NTI
    assertThat(bazType.getParameterTypes()).containsExactly(thisType);
    assertThat(bazType.getTypeOfThis()).isEqualTo(receiver);

    // TODO(sdh): NTI currently fails to infer the result of the baz() call (b/37351897)
    // so we handle it more carefully.  When methods are deferred, this should be changed
    // to check that it's exactly unknown.
    assertTrue(
        "Expected undefined or unknown: " + bazResultType,
        bazResultType.isVoidType() || bazResultType.isUnknownType());
    assertTrue(
        "Expected undefined: " + bazType.getReturnType(), bazType.getReturnType().isVoidType());
  }

  private TypeI getTypeAtPosition(int... indices) {
    Node node = getLastCompiler().getJsRoot().getFirstChild();
    for (int index : indices) {
      node = node.getChildAtIndex(index);
    }
    return node.getTypeI();
  }


  public void testRewriteChained() throws Exception {
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

  /**
   * Inputs for declaration used as an r-value tests.
   */
  private static class NoRewriteDeclarationUsedAsRValue {
    static final String DECL = "a.prototype.foo = function() {}";
    static final String CALL = "o.foo()";

    private NoRewriteDeclarationUsedAsRValue() {}
  }

  public void testRewriteDeclIsExpressionStatement() throws Exception {
    test(semicolonJoin(NoRewriteDeclarationUsedAsRValue.DECL,
                       NoRewriteDeclarationUsedAsRValue.CALL),
         "var JSCompiler_StaticMethods_foo =" +
         "function(JSCompiler_StaticMethods_foo$self) {};" +
         "JSCompiler_StaticMethods_foo(o)");
  }

  public void testNoRewriteDeclUsedAsAssignmentRhs() throws Exception {
    testSame(semicolonJoin("var c = " + NoRewriteDeclarationUsedAsRValue.DECL,
                           NoRewriteDeclarationUsedAsRValue.CALL));
  }

  public void testNoRewriteDeclUsedAsCallArgument() throws Exception {
    testSame(semicolonJoin("f(" + NoRewriteDeclarationUsedAsRValue.DECL + ")",
                           NoRewriteDeclarationUsedAsRValue.CALL));
  }

  /**
   * Inputs for restrict-to-global-scope tests.
   */
  private static class NoRewriteIfNotInGlobalScopeTestInput {
    static final String INPUT =
        lines(
            "function a(){}",
            "a.prototype.foo = function() {return this.x};",
            "var o = new a;",
            "o.foo()");

    private NoRewriteIfNotInGlobalScopeTestInput() {}
  }

  public void testRewriteInGlobalScope() throws Exception {
    String expected =
        lines(
            "function a(){}",
            "var JSCompiler_StaticMethods_foo = ",
            "function(JSCompiler_StaticMethods_foo$self) {",
            "  return JSCompiler_StaticMethods_foo$self.x",
            "};",
            "var o = new a;",
            "JSCompiler_StaticMethods_foo(o);");

    test(NoRewriteIfNotInGlobalScopeTestInput.INPUT, expected);
  }

  public void testNoRewriteIfNotInGlobalScope1() throws Exception {
    setAcceptedLanguage(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    testSame("if(true){" + NoRewriteIfNotInGlobalScopeTestInput.INPUT + "}");
  }

  public void testNoRewriteIfNotInGlobalScope2() throws Exception {
    testSame("function enclosingFunction() {" +
             NoRewriteIfNotInGlobalScopeTestInput.INPUT +
             "}");
  }

  public void testNoRewriteNamespaceFunctions() throws Exception {
    String source = "function a(){}; a.foo = function() {return this.x}; a.foo()";
    testSame(source);
  }

  public void testRewriteIfDuplicates() throws Exception {
    test(
        lines(
            "function A(){}; A.prototype.getFoo = function() { return 1; }; ",
            "function B(){}; B.prototype.getFoo = function() { return 1; }; ",
            "var x = Math.random() ? new A() : new B();",
            "alert(x.getFoo());"),
        lines(
            "function A(){}; ",
            "var JSCompiler_StaticMethods_getFoo=",
            "function(JSCompiler_StaticMethods_getFoo$self){return 1};",
            "function B(){};",
            "B.prototype.getFoo=function(){return 1};",
            "var x = Math.random() ? new A() : new B();",
            "alert(JSCompiler_StaticMethods_getFoo(x));"));
  }

  public void testRewriteIfDuplicatesWithThis() throws Exception {
    test(
        lines(
            "function A(){}; A.prototype.getFoo = ",
            "function() { return this._foo + 1; }; ",
            "function B(){}; B.prototype.getFoo = ",
            "function() { return this._foo + 1; }; ",
            "var x = Math.random() ? new A() : new B();",
            "alert(x.getFoo());"),
        lines(
            "function A(){}; ",
            "var JSCompiler_StaticMethods_getFoo=",
            "function(JSCompiler_StaticMethods_getFoo$self){",
            "  return JSCompiler_StaticMethods_getFoo$self._foo + 1",
            "};",
            "function B(){};",
            "B.prototype.getFoo=function(){return this._foo + 1};",
            "var x = Math.random() ? new A() : new B();",
            "alert(JSCompiler_StaticMethods_getFoo(x));"));
  }

  public void testNoRewriteIfDuplicates() throws Exception {
    testSame(
        lines(
            "function A(){}; A.prototype.getFoo = function() { return 1; }; ",
            "function B(){}; B.prototype.getFoo = function() { return 2; }; ",
            "var x = Math.random() ? new A() : new B();",
            "alert(x.getFoo());"));
  }

  /**
   * Inputs for object literal tests.
   */
  private static class NoRewritePrototypeObjectLiteralsTestInput {
    static final String REGULAR = "b.prototype.foo = function() { return 1; }";
    static final String OBJ_LIT = "a.prototype = {foo : function() { return 2; }}";
    static final String CALL = "o.foo()";

    private NoRewritePrototypeObjectLiteralsTestInput() {}
  }

  public void testRewritePrototypeNoObjectLiterals() throws Exception {
    test(
        semicolonJoin(
            NoRewritePrototypeObjectLiteralsTestInput.REGULAR,
            NoRewritePrototypeObjectLiteralsTestInput.CALL),
        lines(
            "var JSCompiler_StaticMethods_foo = ",
            "function(JSCompiler_StaticMethods_foo$self) { return 1; };",
            "JSCompiler_StaticMethods_foo(o)"));
  }

  public void testRewritePrototypeObjectLiterals1() throws Exception {
    test(
        semicolonJoin(
            NoRewritePrototypeObjectLiteralsTestInput.OBJ_LIT,
            NoRewritePrototypeObjectLiteralsTestInput.CALL),
        lines(
            "a.prototype={};",
            "var JSCompiler_StaticMethods_foo=",
            "function(JSCompiler_StaticMethods_foo$self){ return 2; };",
            "JSCompiler_StaticMethods_foo(o)"));
  }

  public void testNoRewritePrototypeObjectLiterals2() throws Exception {
    testSame(semicolonJoin(NoRewritePrototypeObjectLiteralsTestInput.OBJ_LIT,
                           NoRewritePrototypeObjectLiteralsTestInput.REGULAR,
                           NoRewritePrototypeObjectLiteralsTestInput.CALL));
  }

  public void testNoRewriteExternalMethods1() throws Exception {
    testSame("a.externalMethod()");
  }

  public void testNoRewriteExternalMethods2() throws Exception {
    testSame("A.prototype.externalMethod = function(){}; o.externalMethod()");
  }

  public void testNoRewriteCodingConvention() throws Exception {
    // no rename, leading _ indicates exported symbol
    testSame("a.prototype._foo = function() {};");
  }

  public void testRewriteNoVarArgs() throws Exception {
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

  public void testNoRewriteVarArgs() throws Exception {
    String source =
        lines(
            "function a(){}",
            "a.prototype.foo = function(var_args) {return arguments};",
            "var o = new a;",
            "o.foo()");
    testSame(source);
  }

  /**
   * Inputs for invalidating reference tests.
   */
  private static class NoRewriteNonCallReferenceTestInput {
    static final String BASE =
        "function a(){}\na.prototype.foo = function() {return this.x};\nvar o = new a;";

    private NoRewriteNonCallReferenceTestInput() {}
  }

  public void testRewriteCallReference() throws Exception {
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

  public void testNoRewriteNoReferences() throws Exception {
    testSame(NoRewriteNonCallReferenceTestInput.BASE);
  }

  public void testNoRewriteNonCallReference() throws Exception {
    testSame(NoRewriteNonCallReferenceTestInput.BASE + "o.foo && o.foo()");
  }

  /**
   * Inputs for nested definition tests.
   */
  private static class NoRewriteNestedFunctionTestInput {
    static final String PREFIX = "a.prototype.foo = function() {";
    static final String SUFFIX = "o.foo()";
    static final String INNER = "a.prototype.bar = function() {}; o.bar()";
    static final String EXPECTED_PREFIX =
        "var JSCompiler_StaticMethods_foo=" +
        "function(JSCompiler_StaticMethods_foo$self){";
    static final String EXPECTED_SUFFIX =
        "JSCompiler_StaticMethods_foo(o)";

    private NoRewriteNestedFunctionTestInput() {}
  }

  public void testRewriteNoNestedFunction() throws Exception {
    test(semicolonJoin(
             NoRewriteNestedFunctionTestInput.PREFIX + "}",
             NoRewriteNestedFunctionTestInput.SUFFIX,
             NoRewriteNestedFunctionTestInput.INNER),
         semicolonJoin(
             NoRewriteNestedFunctionTestInput.EXPECTED_PREFIX + "}",
             NoRewriteNestedFunctionTestInput.EXPECTED_SUFFIX,
             "var JSCompiler_StaticMethods_bar=" +
             "function(JSCompiler_StaticMethods_bar$self){}",
             "JSCompiler_StaticMethods_bar(o)"));
  }

  public void testNoRewriteNestedFunction() throws Exception {
    test(NoRewriteNestedFunctionTestInput.PREFIX +
         NoRewriteNestedFunctionTestInput.INNER + "};" +
         NoRewriteNestedFunctionTestInput.SUFFIX,
         NoRewriteNestedFunctionTestInput.EXPECTED_PREFIX +
         NoRewriteNestedFunctionTestInput.INNER + "};" +
         NoRewriteNestedFunctionTestInput.EXPECTED_SUFFIX);
  }

  public void testRewriteImplementedMethod() throws Exception {
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

  public void testRewriteImplementedMethod2() throws Exception {
    String source =
        lines(
            "function a(){}",
            "a.prototype['foo'] = function(args) {return args};",
            "var o = new a;",
            "o.foo()");
    testSame(source);
  }

  public void testRewriteImplementedMethod3() throws Exception {
    String source =
        lines(
            "function a(){}",
            "a.prototype.foo = function(args) {return args};",
            "var o = new a;",
            "o['foo']");
    testSame(source);
  }

  public void testRewriteImplementedMethod4() throws Exception {
    String source =
        lines(
            "function a(){}",
            "a.prototype['foo'] = function(args) {return args};",
            "var o = new a;",
            "o['foo']");
    testSame(source);
  }

  public void testRewriteImplementedMethod5() throws Exception {
    String source = "(function() {this.foo()}).prototype.foo = function() {extern();};";
    testSame(source);
  }

  public void testRewriteImplementedMethodInObj() throws Exception {
    String source =
        lines(
            "function a(){}",
            "a.prototype = {foo: function(args) {return args}};",
            "var o = new a;",
            "o.foo()");
    test(source,
        "function a(){}" +
        "a.prototype={};" +
        "var JSCompiler_StaticMethods_foo=" +
        "function(JSCompiler_StaticMethods_foo$self,args){return args};" +
        "var o=new a;" +
        "JSCompiler_StaticMethods_foo(o)");
  }

  public void testNoRewriteGet1() throws Exception {
    // Getters and setter require special handling.
    testSame("function a(){}; a.prototype = {get foo(){return f}}; var o = new a; o.foo()");
  }

  public void testNoRewriteGet2() throws Exception {
    // Getters and setter require special handling.
    testSame("function a(){}; a.prototype = {get foo(){return 1}}; var o = new a; o.foo");
  }

  public void testNoRewriteSet1() throws Exception {
    // Getters and setter require special handling.
    String source = "function a(){}; a.prototype = {set foo(a){}}; var o = new a; o.foo()";
    testSame(source);
  }

  public void testNoRewriteSet2() throws Exception {
    // Getters and setter require special handling.
    String source = "function a(){}; a.prototype = {set foo(a){}}; var o = new a; o.foo = 1";
    testSame(source);
  }

  public void testNoRewriteNotImplementedMethod() throws Exception {
    testSame("function a(){}; var o = new a; o.foo()");
  }

  public void testWrapper() {
    testSame("(function() {})()");
  }

  private static class ModuleTestInput {
    static final String DEFINITION = "a.prototype.foo = function() {}";
    static final String USE = "x.foo()";

    static final String REWRITTEN_DEFINITION =
        "var JSCompiler_StaticMethods_foo=" +
        "function(JSCompiler_StaticMethods_foo$self){}";
    static final String REWRITTEN_USE =
        "JSCompiler_StaticMethods_foo(x)";

    private ModuleTestInput() {}
  }

  public void testRewriteSameModule1() throws Exception {
    JSModule[] modules = createModuleStar(
        // m1
        semicolonJoin(ModuleTestInput.DEFINITION,
                      ModuleTestInput.USE),
        // m2
        "");

    test(modules, new String[] {
        // m1
        semicolonJoin(ModuleTestInput.REWRITTEN_DEFINITION,
                      ModuleTestInput.REWRITTEN_USE),
        // m2
        "",
      });
  }

  public void testRewriteSameModule2() throws Exception {
    JSModule[] modules = createModuleStar(
        // m1
        "",
        // m2
        semicolonJoin(ModuleTestInput.DEFINITION,
                      ModuleTestInput.USE));

    test(modules, new String[] {
        // m1
        "",
        // m2
        semicolonJoin(ModuleTestInput.REWRITTEN_DEFINITION,
                      ModuleTestInput.REWRITTEN_USE)
      });
  }

  public void testRewriteSameModule3() throws Exception {
    JSModule[] modules = createModuleStar(
        // m1
        semicolonJoin(ModuleTestInput.USE,
                      ModuleTestInput.DEFINITION),
        // m2
        "");

    test(modules, new String[] {
        // m1
        semicolonJoin(ModuleTestInput.REWRITTEN_USE,
                      ModuleTestInput.REWRITTEN_DEFINITION),
        // m2
        ""
      });
  }

  public void testRewriteDefinitionBeforeUse() throws Exception {
    JSModule[] modules = createModuleStar(
        // m1
        ModuleTestInput.DEFINITION,
        // m2
        ModuleTestInput.USE);

    test(modules, new String[] {
        // m1
        ModuleTestInput.REWRITTEN_DEFINITION,
        // m2
        ModuleTestInput.REWRITTEN_USE
      });
  }

  public void testNoRewriteUseBeforeDefinition() throws Exception {
    JSModule[] modules = createModuleStar(
        // m1
        ModuleTestInput.USE,
        // m2
        ModuleTestInput.DEFINITION);

    testSame(modules);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new DevirtualizePrototypeMethods(compiler);
  }
}
