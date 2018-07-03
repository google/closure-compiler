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
import static com.google.javascript.jscomp.parsing.Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tests for {@link InferJSDocInfo}.
 * @author nicksantos@google.com (Nick Santos)
 */
// TODO(nicksantos): A lot of this code is duplicated from
// TypedScopeCreatorTest. We should create a common test harness for
// assertions about type information.
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

  private TypedScope globalScope;

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setParseJsDocDocumentation(INCLUDE_DESCRIPTIONS_NO_WHITESPACE);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    return options;
  }

  private final Callback callback = new AbstractPostOrderCallback() {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      TypedScope s = t.getTypedScope();
      if (s.isGlobal()) {
        globalScope = s;
      }
    }
  };

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

        NodeTraversal t = new NodeTraversal(compiler, callback, scopeCreator);
        t.traverseRoots(externs, root);

        new InferJSDocInfo(compiler).process(externs, root);
      }
    };
  }

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

  public void testJSDocFromVarAssignmentPropagatesToDefinedType() {
    testJSDocFromVariableAssignmentPropagatesToDefinedType(DeclarationKeyword.VAR);
  }

  public void testJSDocFromLetAssignmentPropagatesToDefinedType() {
    testJSDocFromVariableAssignmentPropagatesToDefinedType(DeclarationKeyword.LET);
  }

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

  public void testJSDocIsPropagatedToClasses() {
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

  // TODO(b/111070482): Why is this expected? Why are there multiple type instances? This can cause
  // non-determinism.
  public void testJSDocIsPropagatedDistinctlyToStructuralTypes_ObjectLiteralTypes() {
    // Given
    testSame(
        srcs(
            lines(
                "/**",
                " * I'm test0.",
                " */",
                "var test0 = {a: 4, b: 5};",
                "",
                // The type of this object *looks* is the same, but it is different (for some
                // reason) and we should get a different JSDoc.
                "/**",
                " * I'm test1.",
                " */",
                "var test1 = {a: 4, b: 5};")));

    JSType test0Type = inferredTypeOfName("test0");
    JSType test1Type = inferredTypeOfName("test1");
    assertNotSame(test0Type, test1Type);

    // Then
    assertEquals("I'm test0.", test0Type.getJSDocInfo().getBlockDescription());
    assertEquals("I'm test1.", test1Type.getJSDocInfo().getBlockDescription());
  }

  // TODO(b/111070482): Why is this expected? Why are there multiple type instances? This can cause
  // non-determinism.
  public void testJSDocIsPropagatedDistinctlyToStructuralTypes_FunctionTypes() {
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
                // The type of this function *looks* is the same, but it is different (for some
                // reason) and we should get a different JSDoc.
                "/**",
                " * I'm test1.",
                " * @param {*} a",
                " * @return {string}",
                " */",
                "function test1(a) {};")));

    JSType test0Type = inferredTypeOfName("test0");
    JSType test1Type = inferredTypeOfName("test1");
    assertNotSame(test0Type, test1Type);

    // Then
    assertEquals("I'm test0.", test0Type.getJSDocInfo().getBlockDescription());
    assertEquals("I'm test1.", test1Type.getJSDocInfo().getBlockDescription());
  }

  // TODO(nickreid): The comments in `InferJSDocInfo` claim the opposite of this test should be
  // true, but as it stands, this is what happens.
  public void testJSDocPropagatesDistinctlyToStructuralTypes_FunctionAndMethodTypes() {
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
                " * @return {*} a",
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

  public void testJSDocIsPropagatedToFieldProperties() {
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

  public void testJSDocIsPropagatedToGetterProperties() {
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

  public void testJSDocIsPropagatedToSetterProperties() {
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

  public void testJSDocIsPropagatedToMethodProperties() {
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

  public void testJSDocIsPropagatedToAbstractMethodProperties() {
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

  public void testJSDocIsPropagatedToStaticProperties() {
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

  public void testJSDocIsPropagatedToTypeFromPrototypeObjectLiteral() {
    testSame(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "",
            "Foo.prototype = {",
            "  /** @protected */ a: function() {},",
            "  /** @protected */ get b() {},",
            "  /** @protected */ set c(x) {},",
            "  /** @protected */ d() {}",
            "};"));

    FunctionType ctor = inferredTypeOfName("Foo").toMaybeFunctionType();
    ObjectType prototype = ctor.getInstanceType().getImplicitPrototype();

    assertThat(prototype.getOwnPropertyJSDocInfo("a").getVisibility())
        .isEqualTo(Visibility.PROTECTED);
    assertThat(prototype.getOwnPropertyJSDocInfo("b").getVisibility())
        .isEqualTo(Visibility.PROTECTED);
    assertThat(prototype.getOwnPropertyJSDocInfo("c").getVisibility())
        .isEqualTo(Visibility.PROTECTED);
    assertThat(prototype.getOwnPropertyJSDocInfo("d").getVisibility())
        .isEqualTo(Visibility.PROTECTED);
  }

  /** Returns the inferred type of the reference {@code name} in the global scope. */
  private JSType inferredTypeOfName(String name) {
    return inferredTypeHavingScopedName(name, globalScope);
  }

  /** Returns the inferred type of the reference {@code name} in {@code scope}. */
  private JSType inferredTypeHavingScopedName(String name, TypedScope scope) {
    Node root = scope.getRootNode();
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
