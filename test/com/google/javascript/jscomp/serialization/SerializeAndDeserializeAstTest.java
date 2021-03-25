/*
 * Copyright 2021 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.serialization;

import static com.google.javascript.jscomp.testing.ColorSubject.assertThat;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.AstValidator;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.colors.NativeColorId;
import com.google.javascript.jscomp.serialization.TypedAstDeserializer.DeserializedAst;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.serialization.SerializationOptions;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that both serialize and then deserialize a compiler AST.
 *
 * <p>Do to the difference from a normal compiler pass, this is not actually able to reuse much of
 * the infrastructure inherited from CompilerTestCase, and thus it may make sense to separate these
 * tests more fully.
 */
@RunWith(JUnit4.class)
public final class SerializeAndDeserializeAstTest extends CompilerTestCase {

  private Consumer<TypedAst> consumer = null;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new SerializeTypedAstPass(
        compiler, SerializationOptions.INCLUDE_DEBUG_INFO_AND_EXPENSIVE_VALIDITY_CHECKS, consumer);
  }

  @Test
  public void testConstNumberDeclaration() {
    testSame("const x = 7;");
  }

  @Test
  public void testConstBigIntDeclaration() {
    testSame("const x = 7n;");
  }

  @Test
  public void testConstStringDeclaration() {
    testSame("const x = 'x';");
  }

  @Test
  public void testConstRegexDeclaration() {
    testSame("const x = /regexp/;");
  }

  @Test
  public void testConstObjectDeclaration() {
    testSame("const obj = {x: 7};");
  }

  @Test
  public void testObjectWithShorthandProperty() {
    testSame("const z = 0; const obj = {z};");
  }

  @Test
  public void testObjectWithMethod() {
    testSame("let obj = {method() {}};");
  }

  @Test
  public void testObjectWithQuotedMethod() {
    testSame("let obj = {'method'() {}};");
  }

  @Test
  public void testObjectWithGetter() {
    testSame("let obj = {get x() {}};");
  }

  @Test
  public void testObjectWithQuotedGetter() {
    testSame("let obj = {get 'x'() {}};");
  }

  @Test
  public void testObjectWithSetter() {
    testSame("let obj = {set x(value) {}};");
  }

  @Test
  public void testObjectWithQuotedSetter() {
    testSame("let obj = {set 'x'(value) {}};");
  }

  @Test
  public void testSimpleTemplateLiteral() {
    testSame("let obj = `foobar`;");
  }

  @Test
  public void testTemplateLiteralWithSubstitution() {
    testSame("let obj = `Hello ${2+3}`;");
  }

  @Test
  public void testConstRegexpDeclaration() {
    testSame("const x = /hello world/;");
  }

  @Test
  public void testVanillaForLoop() {
    testSame("for (let x = 0; x < 10; ++x);");
  }

  @Test
  public void testConstructorJsdoc() {
    testSame("/** @constructor */ function Foo() {}");
  }

  @Test
  public void testSideEffectsJsdoc() {
    testSame("/** @nosideeffects */ function f(x) {}");
    testSame("/** @modifies {arguments} */ function f(x) {}");
    testSame("/** @modifies {this} */ function f(x) {}");
  }

  @Test
  public void testCollapsePropertiesJsdoc() {
    testSame("const ns = {}; /** @const */ ns.f = (x) => x;");
    testSame("const ns = {}; /** @nocollapse */ ns.f = (x) => x;");
    test(
        "/** @enum {string} */ const Enum = { A: 'string' };",
        "/** @enum {!JsdocSerializer_placeholder_type} */ const Enum = { A: 'string' };");
  }

  @Test
  public void testEmptyClassDeclaration() {
    testSame("class Foo {}");
  }

  @Test
  public void testEmptyClassDeclarationWithExtends() {
    testSame("class Foo {} class Foo extends Bar {}");
  }

  @Test
  public void testClassDeclarationWithMethods() {
    testSame(
        lines(
            "class Foo {",
            "  a() {}",
            "  'b'() {}",
            "  get c() {}",
            "  set d(x) {}",
            "  ['e']() {}",
            "}"));
  }

  @Test
  public void testVanillaFunctionDeclaration() {
    testSame("function f(x, y) { return x ** y; }");
  }

  @Test
  public void testBlockScopedFunctionDeclaration() {
    testSame("if (true) { function f() {} }");
  }

  @Test
  public void testAsyncVanillaFunctionDeclaration() {
    testSame("async function f() {}");
  }

  @Test
  public void testArrowFunctionDeclaration() {
    testSame("let fn = (x) => x >>> 0x80;");
  }

  @Test
  public void testAsyncFunctionDeclaration() {
    testSame("async function f(x) {};");
  }

  @Test
  public void testGeneratorFunctionDeclaration() {
    testSame("function* f(x) {};");
  }

  @Test
  public void testAsyncGeneratorFunctionDeclaration() {
    testSame("async function* f(x) {};");
  }

  @Test
  public void testYieldAll() {
    testSame("function* f(x) { yield* x; };");
  }

  @Test
  public void testFunctionCallRestAndSpread() {
    testSame("function f(...x) {} f(...[1, 2, 3]);");
  }

  @Test
  public void testFunctionDefaultAndDestructuringParameters() {
    testSame("function f(x = 0, {y, ...z} = {}, [a, b]) {}");
  }

  @Test
  public void testComputedProperty() {
    testSame("const o = {['foo']: 33};");
  }

  @Test
  public void testLabledStatement() {
    testSame("label: for (;;);");
  }

  @Test
  public void testIdGenerator() {
    testSame("/** @idGenerator {xid} */ function xid(id) {}");
  }

  @Test
  public void testUnpairedSurrogateStrings() {
    testSame("const s = '\ud800';");
  }

  @Test
  public void testConvertsNumberTypeToColor() {
    enableTypeCheck();

    TypedAstDeserializer.DeserializedAst result = testAndReturnResult("3", "3");
    Node newScript = result.getRoot().getSecondChild().getFirstChild();
    assertNode(newScript).hasToken(Token.SCRIPT);
    Node three = newScript.getFirstFirstChild();

    assertNode(three).hasToken(Token.NUMBER);
    assertThat(three.getColor()).isNative(NativeColorId.NUMBER);
    assertThat(three.getColor())
        .isSameInstanceAs(result.getColorRegistry().get(NativeColorId.NUMBER));
  }

  @Test
  public void testOriginalNamePreserved() {
    Node newRoot =
        testAndReturnResult("const x = 0;", "const x = 0;")
            .getRoot()
            .getSecondChild()
            .getFirstChild();

    Node constDeclaration = newRoot.getFirstChild();
    assertNode(constDeclaration).hasToken(Token.CONST);

    Node x = constDeclaration.getOnlyChild();
    assertNode(x).hasStringThat().isEqualTo("x");
    assertNode(x).hasOriginalNameThat().isEqualTo("x");
  }

  @Test
  public void testOriginalNamePreservedAfterModuleRewriting() {
    enableRewriteClosureCode();

    Node newRoot =
        testAndReturnResult(
                "goog.module('a.b.c'); const x = 0;",
                lines(
                    "/** @const */ var module$exports$a$b$c = {};",
                    "const module$contents$a$b$c_x = 0;"))
            .getRoot()
            .getSecondChild()
            .getFirstChild();

    Node constDeclaration = newRoot.getSecondChild();
    assertNode(constDeclaration).hasToken(Token.CONST);

    Node globalizedXName = constDeclaration.getOnlyChild();
    assertNode(globalizedXName).hasStringThat().isEqualTo("module$contents$a$b$c_x");
    assertNode(globalizedXName).hasOriginalNameThat().isEqualTo("x");
  }

  @Override
  public void testSame(String code) {
    this.test(code, code);
  }

  @Override
  public void test(String code, String expected) {
    testAndReturnResult(code, expected);
  }

  private DeserializedAst testAndReturnResult(String code, String expected) {
    TypedAst ast = compile(code);
    Node expectedRoot = getLastCompiler().parseSyntheticCode(expected);
    DeserializedAst result = TypedAstDeserializer.deserialize(ast);
    Node newRoot = result.getRoot().getLastChild().getFirstChild();
    assertNode(newRoot).isEqualIncludingJsDocTo(expectedRoot);
    new AstValidator(getLastCompiler(), /* validateScriptFeatures= */ true).validateScript(newRoot);
    consumer = null;
    return result;
  }

  TypedAst compile(String code) {
    TypedAst[] result = new TypedAst[1];
    consumer = ast -> result[0] = ast;
    super.testSame(code);
    byte[] serialized = result[0].toByteArray();
    try {
      return TypedAst.parseFrom(serialized);
    } catch (InvalidProtocolBufferException e) {
      throw new AssertionError(e);
    }
  }
}
