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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.testing.ColorSubject.assertThat;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.AstValidator;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.serialization.TypedAstDeserializer.DeserializedAst;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
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

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableCreateModuleMap();
    enableSourceInformationAnnotator();
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
  public void testEsModule() {
    testSame(
        new String[] {
          "export const x = 0; const y = 1; export {y};",
          "import {x, y as z} from './testcode0'; import * as input0 from './testcode0'"
        });
  }

  @Test
  public void testConvertsNumberTypeToColor() {
    enableTypeCheck();

    TypedAstDeserializer.DeserializedAst result = testAndReturnResult(srcs("3"), expected("3"));
    Node newScript = result.getRoot().getSecondChild().getFirstChild();
    assertNode(newScript).hasToken(Token.SCRIPT);
    Node three = newScript.getFirstFirstChild();

    assertNode(three).hasToken(Token.NUMBER);
    assertThat(three.getColor()).isSameInstanceAs(StandardColors.NUMBER);
  }

  @Test
  public void testOriginalNamePreserved() {
    Node newRoot =
        testAndReturnResult(srcs("const x = 0;"), expected("const x = 0;"))
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
                srcs("goog.module('a.b.c'); const x = 0;"),
                expected(
                    lines(
                        "/** @const */ var module$exports$a$b$c = {};",
                        "const module$contents$a$b$c_x = 0;")))
            .getRoot()
            .getSecondChild()
            .getFirstChild();

    Node constDeclaration = newRoot.getSecondChild();
    assertNode(constDeclaration).hasToken(Token.CONST);

    Node globalizedXName = constDeclaration.getOnlyChild();
    assertNode(globalizedXName).hasStringThat().isEqualTo("module$contents$a$b$c_x");
    assertNode(globalizedXName).hasOriginalNameThat().isEqualTo("x");
  }

  @Test
  public void serializesFileWithPreloadedCode() throws IOException {
    SourceFile a = SourceFile.fromCode("a.js", "const a = 0;");
    SourceFile b = SourceFile.fromCode("b.js", "const b = a;");

    DeserializedAst result =
        this.testAndReturnResult(srcs(ImmutableList.of(a, b)), expected(ImmutableList.of(a, b)));
    Node scriptA = result.getRoot().getSecondChild().getFirstChild();
    Node scriptB = result.getRoot().getSecondChild().getSecondChild();

    assertThat(scriptA.getStaticSourceFile()).isInstanceOf(SourceFile.class);
    assertThat(scriptB.getStaticSourceFile()).isInstanceOf(SourceFile.class);

    assertThat(((SourceFile) scriptA.getStaticSourceFile()).getCode()).isEqualTo("const a = 0;");
    assertThat(((SourceFile) scriptB.getStaticSourceFile()).getCode()).isEqualTo("const b = a;");
  }

  @Test
  public void serializeAndDeserializeFileOnDiskWithUTF16() throws IOException {
    Path pathA = Files.createTempFile("tmp", "a.js");
    Files.write(pathA, ImmutableList.of("const ಠ_ಠ = 0;"), UTF_16);

    SourceFile a = SourceFile.fromFile(pathA.toString(), UTF_16);

    DeserializedAst result =
        this.testAndReturnResult(srcs(ImmutableList.of(a)), expected(ImmutableList.of(a)));
    Node scriptA = result.getRoot().getSecondChild().getFirstChild();

    assertThat(scriptA.getStaticSourceFile()).isInstanceOf(SourceFile.class);
    assertThat(((SourceFile) scriptA.getStaticSourceFile()).getCode())
        .isEqualTo("const ಠ_ಠ = 0;\n");
  }

  @Test
  public void serializeAndDeserializeFileOnDiskWithOriginalName() throws IOException {
    Path pathA = Files.createTempFile("tmp", "a.js");
    Files.write(pathA, ImmutableList.of("const a = 0;"));

    SourceFile a =
        SourceFile.builder().withOriginalPath("original_a.js").buildFromFile(pathA.toString());

    DeserializedAst result =
        this.testAndReturnResult(srcs(ImmutableList.of(a)), expected(ImmutableList.of(a)));
    Node scriptA = result.getRoot().getSecondChild().getFirstChild();

    assertThat(scriptA.getStaticSourceFile()).isInstanceOf(SourceFile.class);
    assertThat(((SourceFile) scriptA.getStaticSourceFile()).getCode()).isEqualTo("const a = 0;\n");
    assertThat(scriptA.getSourceFileName()).isEqualTo("original_a.js");
  }

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void serializesZipEntries() throws IOException {
    // Setup environment.
    String expectedContent = "const a = 0;";
    Path jsZipPath = folder.newFile("test.js.zip").toPath();
    createZipWithContent(jsZipPath, expectedContent);

    SourceFile a = SourceFile.fromFile(jsZipPath + "!/a.js", UTF_8);

    DeserializedAst result =
        this.testAndReturnResult(srcs(ImmutableList.of(a)), expected(ImmutableList.of(a)));
    Node scriptA = result.getRoot().getSecondChild().getFirstChild();

    assertThat(scriptA.getStaticSourceFile()).isInstanceOf(SourceFile.class);
    assertThat(((SourceFile) scriptA.getStaticSourceFile()).getCode()).isEqualTo("const a = 0;");
    assertThat(((SourceFile) scriptA.getStaticSourceFile()).getName()).isEqualTo(a.getName());
  }

  @Test
  public void setsSourceFileOfSyntheticCode() throws IOException {
    ensureLibraryInjected("base");
    disableCompareSyntheticCode();

    DeserializedAst ast =
        this.testAndReturnResult(
            srcs("0;"),
            // the injected "base" library is merged into the first file's script. ensure that
            // SourceFiles are wired up correctly.
            expected(
                lines(
                    "/** @const */ var $jscomp = $jscomp || {};",
                    "/** @const */",
                    "$jscomp.scope = {};",
                    "0;")));

    Node script = ast.getRoot().getSecondChild().getFirstChild();
    assertNode(script).hasToken(Token.SCRIPT);
    assertThat(script.getSourceFileName()).isEqualTo("testcode");

    Node jscompDeclaration = script.getFirstChild();
    assertThat(jscompDeclaration.getSourceFileName()).isEqualTo(" [synthetic:base] ");
    assertThat(jscompDeclaration.getFirstChild().getSourceFileName())
        .isEqualTo(" [synthetic:base] ");

    Node number = script.getLastChild();
    assertThat(number.getSourceFileName()).isEqualTo("testcode");
  }

  @Override
  public void testSame(String code) {
    this.test(code, code);
  }

  @Override
  public void testSame(String[] sources) {
    this.testAndReturnResult(srcs(sources), expected(sources));
  }

  @Override
  public void test(String code, String expected) {
    this.testAndReturnResult(srcs(code), expected(expected));
  }

  private DeserializedAst testAndReturnResult(Sources code, Expected expected) {
    return this.testAndReturnResult(externs(ImmutableList.of()), code, expected);
  }

  private DeserializedAst testAndReturnResult(Externs externs, Sources code, Expected expected) {
    TypedAst ast = compile(externs, code);
    Node expectedRoot = this.parseExpectedJs(expected);
    DeserializedAst result = TypedAstDeserializer.deserialize(ast);
    Node newRoot = result.getRoot().getLastChild();
    assertNode(newRoot).isEqualIncludingJsDocTo(expectedRoot);
    new AstValidator(getLastCompiler(), /* validateScriptFeatures= */ true)
        .validateRoot(result.getRoot());
    consumer = null;
    return result;
  }

  TypedAst compile(Externs externs, Sources code) {
    TypedAst[] result = new TypedAst[1];
    consumer = ast -> result[0] = ast;
    super.testSame(externs, code);
    byte[] serialized = result[0].toByteArray();
    try {
      return TypedAst.parseFrom(serialized);
    } catch (InvalidProtocolBufferException e) {
      throw new AssertionError(e);
    }
  }

  private static void createZipWithContent(Path zipFile, String content) throws IOException {
    Instant lastModified = Instant.now();
    if (zipFile.toFile().exists()) {
      // Ensure that file modified date is updated, otherwise could cause flakiness (b/123962282).
      lastModified = Files.getLastModifiedTime(zipFile).toInstant().plusSeconds(1);
      zipFile.toFile().delete();
    }

    zipFile.toFile().createNewFile();
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
      zos.putNextEntry(new ZipEntry("a.js"));
      zos.write(content.getBytes(UTF_8));
      zos.closeEntry();
    }
    Files.setLastModifiedTime(zipFile, FileTime.from(lastModified));
  }
}
