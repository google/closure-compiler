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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.serialization.SerializationOptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SerializeAndDeserializeAstTest extends CompilerTestCase {

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    disableValidateAstChangeMarking();
  }

  private void replaceScriptRoot(Node oldRoot, Node newRoot) {
    checkArgument(oldRoot.isRoot());
    checkArgument(newRoot.isRoot());
    oldRoot.removeChildren();
    for (Node script = newRoot.getFirstChild(); script != null; script = script.getNext()) {
      checkState(script.isScript());
      oldRoot.addChildToBack(script.detach());
    }
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new SerializeTypedAstPass(
        compiler,
        SerializationOptions.INCLUDE_DEBUG_INFO_AND_EXPENSIVE_VALIDITY_CHECKS,
        (typedAst) -> {
          Node oldExternsAndJsRoot = compiler.getRoot();
          Node newRoot = TypedAstDeserializer.deserialize(typedAst);
          NodeUtil.markFunctionsDeleted(oldExternsAndJsRoot, compiler);
          replaceScriptRoot(oldExternsAndJsRoot.getFirstChild(), newRoot.getFirstChild());
          replaceScriptRoot(oldExternsAndJsRoot.getLastChild(), newRoot.getLastChild());
        });
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
  public void testObjectWithMethod() {
    testSame("let obj = {method() {}};");
  }

  @Test
  public void testObjectWithGetter() {
    testSame("let obj = {get x() {}};");
  }

  @Test
  public void testObjectWithSetter() {
    testSame("let obj = {set x(value) {}};");
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
  public void testVanillaFunctionDeclaration() {
    testSame("function f(x, y) { return x ** y; }");
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
  public void testYieldAll() {
    testSame("function* f(x) { yield* x; };");
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
}
