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

import static com.google.javascript.jscomp.parsing.Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link InferJSDocInfo}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
// TODO(nicksantos): A lot of this code is duplicated from
// TypedScopeCreatorTest. We should create a common test harness for
// assertions about type information.
@RunWith(JUnit4.class)
public final class InferJSDocInfoTest extends CompilerTestCase {

  private static enum DeclarationKeyword {
    VAR("var"),
    LET("let"),
    CONST("const");

    private final String string;

    private DeclarationKeyword(String string) {
      this.string = string;
    }

    public String toJs() {
      return string;
    }
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setParseJsDocDocumentation(INCLUDE_DESCRIPTIONS_NO_WHITESPACE);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);
        TypedScope topScope = scopeCreator.createScope(root.getParent(), null);

        new TypeInferencePass(
                compiler, compiler.getReverseAbstractInterpreter(), topScope, scopeCreator)
            .process(externs, root);

        new InferJSDocInfo(compiler).process(externs, root);
      }
    };
  }

  @Test
  public void testJSDocFromExternTypesIsPreserved() {
    // Given
    testSame(
        externs(
            lines(
                "/**",
                " * I'm an Object.",
                " * @param {*=} x",
                " * @return {!Object}",
                " * @constructor",
                " */",
                "function Object(x) {};")),
        srcs("var x = new Object();"));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Object", xType.toString());

    // Then
    assertEquals("I'm an Object.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocFromInstanceNodesIsIgnored() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm a user type.",
                " * @constructor",
                " */",
                "function Foo() {};",
                "",
                "/** I'm a Foo instance */", // This should not be attached to a type.
                "var x = new Foo();")));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a user type.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsNotPropagatedToNativeFunctionType() {
    // Given
    testSame(
        srcs(
            lines(
                "/**", //
                "* I'm some function.",
                " * @type {!Function}",
                " */",
                "var x;")));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Function", xType.toString());

    // Then
    assertNull(xType.getJSDocInfo());
  }

  @Test
  public void testJSDocFromNamedFunctionPropagatesToDefinedType() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm a user type.",
                " * @constructor",
                " */",
                "function Foo() {};",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a user type.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocFromNamedEs6ClassPropagatesToDefinedType() {
    // Given
    testSame(
        srcs(
            lines(
                "/** I'm a user type. */",
                "class Foo { };",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a user type.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocFromGlobalAssignmentPropagatesToDefinedType() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm a user type.",
                " * @constructor",
                " */",
                "Foo = function() {};",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a user type.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocFromNamespacedNameAssignmentPropagatesToDefinedType() {
    // Given
    testSame(
        srcs(
            lines(
                "var namespace = {}",
                "",
                "/**",
                " * I'm a user type.",
                " * @constructor",
                " */",
                "namespace.Foo = function() {};",
                "",
                "var x = new namespace.Foo();" // Just a hook to access type "Foo".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("namespace.Foo", xType.toString());

    // Then
    assertEquals("I'm a user type.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocFromVarAssignmentPropagatesToDefinedType() {
    testJSDocFromVariableAssignmentPropagatesToDefinedType(DeclarationKeyword.VAR);
  }

  @Test
  public void testJSDocFromLetAssignmentPropagatesToDefinedType() {
    testJSDocFromVariableAssignmentPropagatesToDefinedType(DeclarationKeyword.LET);
  }

  @Test
  public void testJSDocFromConstAssignmentPropagatesToDefinedType() {
    testJSDocFromVariableAssignmentPropagatesToDefinedType(DeclarationKeyword.CONST);
  }

  private void testJSDocFromVariableAssignmentPropagatesToDefinedType(DeclarationKeyword keyword) {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm a user type.",
                " * @constructor",
                " */",
                keyword.toJs() + " Foo = function() {};",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a user type.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocFromVariableNameAssignmentPropagatesToDefinedType() {
    // Given
    testSame(
        srcs(
            lines(
                "var /**",
                " * I'm a user type.",
                " * @constructor",
                " */",
                "Foo = function() {};",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a user type.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToClasses_Es5() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm a user class.",
                " * @constructor",
                " */",
                "var Foo = function() {};",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a user class.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToClasses_Es6() {
    // Given
    testSame(
        srcs(
            lines(
                "/** I'm a user class. */",
                "var Foo = class { };",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a user class.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToCtors() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm a user class.",
                " * @constructor",
                " */",
                "var Foo = function() {};",
                "",
                "var x = Foo;" // Just a hook to access type "ctor{Foo}".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("function(new:Foo): undefined", xType.toString());

    // Then
    assertEquals("I'm a user class.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToInterfaces() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm a user interface.",
                " * @interface",
                " */",
                "var Foo = function() {};",
                "",
                "var x = /** @type {!Foo} */ ({});" // Just a hook to access type "Foo".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a user interface.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToRecords() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm a user record.",
                " * @record",
                " */",
                "var Foo = function() {};",
                "",
                "var x = /** @type {!Foo} */ ({});" // Just a hook to access type "Foo".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a user record.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToEnums() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm a user enum.",
                " * @enum {number}",
                " */",
                "var Foo = {BAR: 0};",
                "",
                "var x = Foo;" // Just a hook to access "enum{Foo}".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("enum{Foo}", xType.toString());

    // Then
    assertEquals("I'm a user enum.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToEnumElements() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm a user enum.",
                " * @enum {number}",
                " */",
                "var Foo = {BAR: 0};",
                "",
                "var x = Foo.BAR;" // Just a hook to access "Foo".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo<number>", xType.toString());

    // Then
    assertEquals("I'm a user enum.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToFunctionTypes() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm a custom function.",
                " * @param {*} a",
                " * @return {string}",
                " */",
                "function test(a) {};",
                "",
                "var x = test;" // Just a hook to access type of "test".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("function(*): string", xType.toString());

    // Then
    assertEquals("I'm a custom function.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToScopedTypes() {
    // Given
    testSame(
        srcs(
            lines(
                "(() => {",
                "  /**",
                "   * I'm a scoped user class.",
                "   * @constructor",
                "   */",
                "  var Foo = function() {};",
                "",
                "  var x = new Foo();", // Just a hook to access type "Foo".
                "})();")));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a scoped user class.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsNotPropagatedToFunctionTypesFromMethodAssigments() {
    // Given
    testSame(
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {};",
                "",
                "/**",
                " * I'm a method.",
                " * @return {number} a",
                " */",
                "Foo.prototype.method = function() { };",
                "",
                "var x = Foo.prototype.method;" // Just a hook to access type "Foo::method".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("function(this:Foo): number", xType.toString());

    // Then
    assertNull(xType.getJSDocInfo());
  }

  @Test
  public void testJSDocIsPropagatedDistinctlyToMatchingStructuralTypes_ObjectLiteralTypes() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm test0.",
                " */",
                "var test0 = {a: 4, b: 5};",
                "",
                // The type of this object *looks* is the same, but it is different since the type
                // may get more properties later. Therefore, it should get a different JSDoc.
                "/**",
                " * I'm test1.",
                " */",
                "var test1 = {a: 4, b: 5};")));

    JSType test0Type = inferredTypeOfName("test0");
    JSType test1Type = inferredTypeOfName("test1");
    // For some reason `test0Type` and `test1Type` aren't equal. This is good, but unexpected.
    assertNotSame(test0Type, test1Type);

    // Then
    assertEquals("I'm test0.", test0Type.getJSDocInfo().getBlockDescription());
    assertEquals("I'm test1.", test1Type.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedDistinctlyToMatchingStructuralTypes_FunctionTypes() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm test0.",
                " * @param {*} a",
                " * @return {string}",
                " */",
                "function test0(a) {};",
                "",
                // The type of this function *looks* is the same, but it is different since the type
                // may get more properties later. Therefore, it should get a different JSDoc.
                "/**",
                " * I'm test1.",
                " * @param {*} a",
                " * @return {string}",
                " */",
                "function test1(a) {};")));

    JSType test0Type = inferredTypeOfName("test0");
    JSType test1Type = inferredTypeOfName("test1");
    // We really only care that they match, not about equality.
    // TODO(b/111070482): That fact that these are equal yet have different JSDocs is bad.
    assertEquals(test0Type, test1Type);
    assertNotSame(test0Type, test1Type);

    // Then
    assertEquals("I'm test0.", test0Type.getJSDocInfo().getBlockDescription());
    assertEquals("I'm test1.", test1Type.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsNotOverriddenByStructuralTypeAssignments_ObjectLiteralTypes() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm test0.",
                " */",
                "var test0 = {a: 4, b: 5};",
                "",
                // The type of these objects should be identical.
                "/**",
                " * I'm test1.",
                // We need this so inferrence understands thes two objects/types are stuck together.
                " * @const",
                " */",
                "var test1 = test0;")));

    JSType test0Type = inferredTypeOfName("test0");
    JSType test1Type = inferredTypeOfName("test1");
    assertSame(test0Type, test1Type);

    // Then
    assertEquals("I'm test0.", test0Type.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsNotOverriddenByStructuralTypeAssignments_FunctionTypes() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm test0.",
                " * @param {*} a",
                " * @return {string}",
                " */",
                "function test0(a) {};",
                "",
                // The type of these objects should be identical.
                "/**",
                " * I'm test1.",
                " * @param {string} a",
                " * @return {*}",
                // We need this so inferrence understands thes two objects/types are stuck together.
                " * @const",
                " */",
                "var test1 = test0;")));

    JSType test0Type = inferredTypeOfName("test0");
    JSType test1Type = inferredTypeOfName("test1");
    assertSame(test0Type, test1Type);

    // Then
    assertEquals("I'm test0.", test0Type.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToFieldProperties_Es5() {
    // Given
    testSame(
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {",
                "  /**",
                "   * I'm a field.",
                "   * @const",
                "   */",
                "   this.field = 5;",
                "};",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a field.", xType.getPropertyJSDocInfo("field").getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToFieldProperties_Es6Class() {
    // Given
    testSame(
        srcs(
            lines(
                "class Foo {",
                "  constructor() {",
                "    /**",
                "     * I'm a field.",
                "     * @const",
                "     */",
                "     this.field = 5;",
                "  }",
                "}",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a field.", xType.getPropertyJSDocInfo("field").getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToGetterProperties_Es5() {
    // Given
    testSame(
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {};",
                "",
                "Foo.prototype = {",
                "  /**",
                "   * I'm a getter.",
                "   * @return {number}",
                "   */",
                "  get getter() {}",
                "};",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a getter.", xType.getPropertyJSDocInfo("getter").getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToGetterProperties_Es6Class() {
    // Given
    testSame(
        srcs(
            lines(
                "class Foo {",
                "  /**",
                "   * I'm a getter.",
                "   * @return {number}",
                "   */",
                "  get getter() {}",
                "}",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a getter.", xType.getPropertyJSDocInfo("getter").getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToSetterProperties_Es5() {
    // Given
    testSame(
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {};",
                "",
                "Foo.prototype = {",
                "  /**",
                "   * I'm a setter.",
                "   * @param {number} a",
                "   */",
                "  set setter(a) {}",
                "};",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a setter.", xType.getPropertyJSDocInfo("setter").getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToSetterProperties_Es6Class() {
    // Given
    testSame(
        srcs(
            lines(
                "class Foo {",
                "  /**",
                "   * I'm a setter.",
                "   * @param {number} a",
                "   */",
                "  set setter(a) {}",
                "}",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a setter.", xType.getPropertyJSDocInfo("setter").getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToMethodProperties_Es5() {
    // Given
    testSame(
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {};",
                "",
                "/**",
                " * I'm a method.",
                " * @return {number} a",
                " */",
                "Foo.prototype.method = function() { };",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a method.", xType.getPropertyJSDocInfo("method").getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToMethodProperties_Es6Class() {
    // Given
    testSame(
        srcs(
            lines(
                "class Foo {",
                "  /**",
                "   * I'm a method.",
                "   * @return {number} a",
                "   */",
                "  method() { }",
                "}",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a method.", xType.getPropertyJSDocInfo("method").getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToAbstractMethodProperties_Es5() {
    // Given
    testSame(
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {};",
                "",
                "/**",
                " * I'm a method.",
                " * @abstract",
                " * @return {number} a",
                " */",
                "Foo.prototype.method = goog.abstractMethod;",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a method.", xType.getPropertyJSDocInfo("method").getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToAbstractMethodProperties_Es6Class() {
    // Given
    testSame(
        srcs(
            lines(
                "class Foo {",
                "  /**",
                "   * I'm a method.",
                "   * @return {number} a",
                "   * @abstract",
                "   */",
                "  method() { }",
                "}",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a method.", xType.getPropertyJSDocInfo("method").getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToStaticProperties_Es5() {
    // Given
    testSame(
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {};",
                "",
                "/**",
                " * I'm a static.",
                " * @return {number} a",
                " */",
                "Foo.static = function() { };",
                "",
                "var x = Foo;" // Just a hook to access type "ctor{Foo}".
                )));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("function(new:Foo): undefined", xType.toString());

    // Then
    assertEquals("I'm a static.", xType.getPropertyJSDocInfo("static").getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToStaticProperties_Es6Class() {
    // Given
    testSame(
        srcs(
            lines(
                "class Foo {",
                "  /**",
                "   * I'm a static.",
                "   * @return {number} a",
                "   */",
                "   static static() { }",
                "}",
                "",
                "var x = Foo;" // Just a hook to access type "ctor{Foo}".
                )));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("function(new:Foo): undefined", xType.toString());

    // Then
    assertEquals("I'm a static.", xType.getPropertyJSDocInfo("static").getBlockDescription());
  }

  // TODO(b/30710701): Constructor docs should be used in some way. This is probably similar to how
  // access control is being differentiated between constructor invocation and constructors as
  // namespaces. The decision for both of these cases should be made together.
  @Test
  public void testJSDocFromConstructorsIsIgnored_Es6Class() {
    // Given
    testSame(
        srcs(
            lines(
                "/** I'm a class. */",
                "class Foo {",
                "  /** I'm a constructor. */",
                "  constructor() { }",
                "}",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a class.", xType.getJSDocInfo().getBlockDescription());
    assertNotNull(xType.getPropertyJSDocInfo("constructor"));
  }

  @Test
  public void testJSDocDoesNotPropagateFromStructuralTypesToClassProperties() {
    // Given
    testSame(
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {};",
                "",
                "/**",
                " * I'm a free function.",
                " * @return {number}",
                " */",
                "function free() { return 0; }",
                "",
                "/**",
                " * I'm a method.",
                " * @return {number} a",
                " */",
                "Foo.prototype.method = free;",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    JSType freeType = inferredTypeOfName("free");
    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertNotSame(freeType, xType);

    // Then
    assertEquals("I'm a free function.", freeType.getJSDocInfo().getBlockDescription());
    assertEquals("I'm a method.", xType.getPropertyJSDocInfo("method").getBlockDescription());
  }

  @Test
  public void testJSDocDoesNotPropagateBackwardFromInstancesToTypes() {
    // Given
    testSame(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "",
            "var x = new Foo();",
            "",
            "/** @type {number} */",
            "x.bar = 4;"));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertFalse(xType.hasProperty("bar"));
    assertNull(xType.getOwnPropertyJSDocInfo("bar"));
  }

  @Test
  public void testJSDocFromDuplicateDefinitionDoesNotOverrideJSDocFromOriginal() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm a user type.",
                " * @constructor",
                " */",
                "var Foo = function() {};",
                "",
                "/**",
                " * I'm a different type.",
                " * @constructor",
                " */",
                "Foo = function() {};",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a user type.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocFromDuplicateDefinitionIsUsedIfThereWasNoOriginalJSDocFromOriginal() {
    // Given
    testSame(
        srcs(
            lines(
                "var Foo = function() {};",
                "",
                "/**",
                " * I'm a different type.",
                " * @constructor",
                " */",
                "Foo = function() {};",
                "",
                "var x = new Foo();" // Just a hook to access type "Foo".
                )));

    JSType xType = inferredTypeOfName("x");
    assertEquals("Foo", xType.toString());

    // Then
    assertEquals("I'm a different type.", xType.getJSDocInfo().getBlockDescription());
  }

  @Test
  public void testJSDocIsPropagatedToTypeFromObjectLiteralPrototype() {
    testSame(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "",
            "Foo.prototype = {",
            "  /** Property a. */ a: function() {},",
            "  /** Property b. */ get b() {},",
            "  /** Property c. */ set c(x) {},",
            "  /** Property d. */ d() {}",
            "};"));

    FunctionType ctor = inferredTypeOfName("Foo").toMaybeFunctionType();
    ObjectType prototype = ctor.getInstanceType().getImplicitPrototype();

    assertEquals("Property a.", prototype.getOwnPropertyJSDocInfo("a").getBlockDescription());
    assertEquals("Property b.", prototype.getOwnPropertyJSDocInfo("b").getBlockDescription());
    assertEquals("Property c.", prototype.getOwnPropertyJSDocInfo("c").getBlockDescription());
    assertEquals("Property d.", prototype.getOwnPropertyJSDocInfo("d").getBlockDescription());
  }

  @Test
  public void testClassWithUnknownTypeDoesNotCrash() {
    // Code that looks like this and @suppresses {checkTypes} is generated by tsickle.
    ignoreWarnings(TypeCheck.CONFLICTING_EXTENDED_TYPE);
    enableTypeCheck();
    testSame(
        lines(
            "/** @param {?} base */",
            "function mixin(base) {",
            "  return (/** @type {?} */ ((class extends base {",
            "    /** @param {...?} args */ constructor(...args) {}",
            "  })));",
            "}"));
  }

  /** Returns the inferred type of the reference {@code name} anywhere in the AST. */
  private JSType inferredTypeOfName(String name) {
    return inferredTypeOfLocalName(name, getLastCompiler().getRoot());
  }

  /** Returns the inferred type of the reference {@code name} under {@code root} AST node.. */
  private JSType inferredTypeOfLocalName(String name, Node root) {
    Deque<Node> queue = new ArrayDeque<>();
    queue.push(root);

    while (!queue.isEmpty()) {
      Node current = queue.pop();
      if (current.matchesQualifiedName(name) && current.getJSType() != null) {
        return current.getJSType();
      }

      for (Node child : current.children()) {
        queue.push(child);
      }
    }
    return null;
  }
}
