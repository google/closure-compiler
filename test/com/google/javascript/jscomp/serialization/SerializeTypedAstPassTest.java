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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SerializeTypedAstPassTest extends CompilerTestCase {

  private Consumer<TypedAst> astConsumer;
  // individual test cases may override this
  private ImmutableSet<String> typesToForwardDeclare = null;

  // Proto fields commonly ignored in tests because hardcoding their values is brittle
  private static final ImmutableList<FieldDescriptor> BRITTLE_TYPE_FIELDS =
      ImmutableList.of(
          ObjectTypeProto.getDescriptor().findFieldByName("uuid"),
          ObjectTypeProto.getDescriptor().findFieldByName("own_property"));

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    this.typesToForwardDeclare = ImmutableSet.of();
    enableSourceInformationAnnotator();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new SerializeTypedAstPass(
        compiler,
        SerializationOptions.INCLUDE_DEBUG_INFO_AND_EXPENSIVE_VALIDITY_CHECKS,
        astConsumer);
  }

  @Override
  protected Compiler createCompiler() {
    Compiler compiler = super.createCompiler();
    for (String typeName : typesToForwardDeclare) {
      compiler.forwardDeclareType(typeName);
    }
    return compiler;
  }

  @Test
  public void testAst_constNumber() {
    TypePointer numberType = pointerForType(PrimitiveType.NUMBER_TYPE);
    TypedAst ast = compile("const x = 5;");
    StringPoolProto stringPool = ast.getStringPool();

    assertThat(ast.getCodeFileList().get(0))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .setSourceFile(2)
                .setRelativeLine(1)
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.CONST_DECLARATION)
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.IDENTIFIER)
                                .setStringValuePointer(findInStringPool(stringPool, "x"))
                                .setOriginalNamePointer(findInStringPool(stringPool, "x"))
                                .setRelativeColumn(6)
                                .setType(numberType)
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.NUMBER_LITERAL)
                                        .setDoubleValue(5)
                                        .setRelativeColumn(4)
                                        .setType(numberType)
                                        .build())
                                .build())
                        .build())
                .build());
  }

  @Test
  public void testAst_externs() {
    // 2 externs files, the default (empty) externs file + the source file marked with @externs
    assertThat(compile("/** @externs */ function foo() {}").getExternFileList()).hasSize(2);
  }

  @Test
  public void testAst_typeSummary() {
    // @typeSummary files are omitted, leaving only 1 serialized extern file
    assertThat(compile("/** @typeSummary */ function foo() {}").getExternFileList()).hasSize(1);
  }

  @Test
  public void testAst_letString() {
    TypePointer stringType = pointerForType(PrimitiveType.STRING_TYPE);
    TypedAst ast = compile("let s = 'hello';");
    StringPoolProto stringPool = ast.getStringPool();

    assertThat(ast.getCodeFileList().get(0))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .ignoringFieldDescriptors(
            AstNode.getDescriptor().findFieldByName("relative_line"),
            AstNode.getDescriptor().findFieldByName("relative_column"))
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .setSourceFile(2)
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.LET_DECLARATION)
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.IDENTIFIER)
                                .setStringValuePointer(findInStringPool(stringPool, "s"))
                                .setOriginalNamePointer(findInStringPool(stringPool, "s"))
                                .setType(stringType)
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.STRING_LITERAL)
                                        .setStringValuePointer(
                                            findInStringPool(stringPool, "hello"))
                                        .setType(stringType)
                                        .build())
                                .build())
                        .build())
                .build());
  }

  @Test
  public void testAst_numberInCast() {
    TypePointer unknownType = pointerForType(PrimitiveType.UNKNOWN_TYPE);
    assertThat(compileToAst("/** @type {?} */ (1);"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .ignoringFieldDescriptors(
            AstNode.getDescriptor().findFieldByName("relative_line"),
            AstNode.getDescriptor().findFieldByName("relative_column"))
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .setSourceFile(2)
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.EXPRESSION_STATEMENT)
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.CAST)
                                .setType(unknownType)
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.NUMBER_LITERAL)
                                        .addBooleanProperty(NodeProperty.IS_PARENTHESIZED)
                                        .addBooleanProperty(NodeProperty.COLOR_FROM_CAST)
                                        .setDoubleValue(1)
                                        .setType(unknownType)
                                        .build())
                                .build()))
                .build());
  }

  @Test
  public void testAst_arrowFunction() {
    TypedAst ast = compile("() => 'hello';");

    assertThat(ast.getCodeFileList().get(0))
        .ignoringFieldDescriptors(
            AstNode.getDescriptor().findFieldByName("relative_line"),
            AstNode.getDescriptor().findFieldByName("type"),
            AstNode.getDescriptor().findFieldByName("relative_column"))
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .setSourceFile(2)
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.EXPRESSION_STATEMENT)
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.FUNCTION_LITERAL)
                                .addBooleanProperty(NodeProperty.ARROW_FN)
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.IDENTIFIER)
                                        .setStringValuePointer(0)
                                        .build())
                                .addChild(
                                    AstNode.newBuilder().setKind(NodeKind.PARAMETER_LIST).build())
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.STRING_LITERAL)
                                        .setStringValuePointer(
                                            findInStringPool(ast.getStringPool(), "hello"))
                                        .build())
                                .build())
                        .build())
                .build());
  }

  @Test
  public void testRewrittenGoogModule_containsOriginalNames() {
    enableRewriteClosureCode();

    TypedAst ast = compile("goog.module('a.b.c'); function f(x) {}");
    assertThat(ast.getCodeFileList().get(0))
        .ignoringFieldDescriptors(
            AstNode.getDescriptor().findFieldByName("relative_line"),
            AstNode.getDescriptor().findFieldByName("relative_column"),
            AstNode.getDescriptor().findFieldByName("type"))
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .setSourceFile(2)
                .addBooleanProperty(NodeProperty.GOOG_MODULE)
                // `/** @const */ var module$exports$a$b$c = {};`
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.VAR_DECLARATION)
                        .addBooleanProperty(NodeProperty.IS_NAMESPACE)
                        .setJsdoc(OptimizationJsdoc.newBuilder().addKind(JsdocTag.JSDOC_CONST))
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.IDENTIFIER)
                                .setStringValuePointer(
                                    findInStringPool(ast.getStringPool(), "module$exports$a$b$c"))
                                .addChild(AstNode.newBuilder().setKind(NodeKind.OBJECT_LITERAL))))
                // `function module$contents$a$b$c_f(x) {}`
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.FUNCTION_LITERAL)
                        .setOriginalNamePointer(findInStringPool(ast.getStringPool(), "f"))
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.IDENTIFIER)
                                .setStringValuePointer(
                                    findInStringPool(
                                        ast.getStringPool(), "module$contents$a$b$c_f"))
                                .setOriginalNamePointer(findInStringPool(ast.getStringPool(), "f")))
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.PARAMETER_LIST)
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.IDENTIFIER)
                                        .setStringValuePointer(
                                            findInStringPool(ast.getStringPool(), "x"))
                                        .setOriginalNamePointer(
                                            findInStringPool(ast.getStringPool(), "x"))
                                        .build()))
                        .addChild(AstNode.newBuilder().setKind(NodeKind.BLOCK)))
                .build());
  }

  @Test
  public void serializesExternsFile() throws IOException {
    ensureLibraryInjected("base");

    TypedAst ast =
        compileWithExterns(
            new TestExternsBuilder().addMath().addObject().build(), "let s = 'hello';");

    AstNode externs = ast.getExternFileList().get(0);
    assertThat(
            ast.getSourceFilePool()
                .getSourceFileList()
                .get(externs.getSourceFile() - 1)
                .getFilename())
        .isEqualTo("externs");
  }

  @Test
  public void serializesSourceOfSyntheticCode() throws IOException {
    ensureLibraryInjected("base");

    TypedAst ast =
        compileWithExterns(
            new TestExternsBuilder().addMath().addObject().build(), "let s = 'hello';");

    AstNode script = ast.getCodeFileList().get(0);
    assertThat(
            ast.getSourceFilePool()
                .getSourceFileList()
                .get(script.getSourceFile() - 1)
                .getFilename())
        .isEqualTo("testcode");

    // 'base' is loaded at the beginning of the first script
    AstNode baseLibraryStart = script.getChild(0);
    assertThat(
            ast.getSourceFilePool()
                .getSourceFileList()
                .get(baseLibraryStart.getSourceFile() - 1)
                .getFilename())
        .isEqualTo(" [synthetic:base] ");
    // children of the synthetic subtree default to the root's file [synthetic:base]
    assertThat(baseLibraryStart.getChild(0).getSourceFile()).isEqualTo(0);
    // "let s = 'hello'" defaults to the parent's file 'testcode'
    assertThat(script.getChild(script.getChildCount() - 1).getSourceFile()).isEqualTo(0);
  }


  private AstNode compileToAst(String source) {
    return compile(source).getCodeFileList().get(0);
  }

  private TypedAst compile(String source) {
    return compileWithExterns(DEFAULT_EXTERNS, source);
  }

  private TypedAst compileWithExterns(String externs, String source) {
    TypedAst[] resultAst = new TypedAst[1];
    astConsumer = (ast) -> resultAst[0] = ast;
    testNoWarning(externs(externs), srcs(source));
    return resultAst[0];
  }

  private static TypePointer pointerForType(PrimitiveType primitive) {
    return TypePointer.newBuilder().setPoolOffset(primitive.getNumber()).build();
  }

  /**
   * Returns the offset of the given string in the given pool, throwing an exception if not present
   */
  private static int findInStringPool(StringPoolProto stringPool, String str) {
    int offset = stringPool.getStringsList().indexOf(ByteString.copyFromUtf8(str));
    checkState(offset != -1, "Could not find string '%s' in string pool %s", str, stringPool);
    return offset;
  }

  private void generateDiagnosticFiles() {
    compile(
        lines(
            "class Foo0 {",
            "  w() { }",
            "}",
            "class Foo1 extends Foo0 {",
            "  y() { }",
            "}",
            "const ns = {Foo0, Foo1};",
            "/** @suppress {checkTypes} */",
            "const /** !Foo1 */ typeMismatch = new Foo0();"));
  }

  @GwtIncompatible
  private static String loadFile(Path path) {
    try (Stream<String> lines = Files.lines(path)) {
      return lines.collect(joining("\n"));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  @GwtIncompatible
  private ImmutableList<Path> debugLogFiles() {
    try {
      Path dir =
          Paths.get(
              this.getLastCompiler().getOptions().getDebugLogDirectory().toString(),
              SerializeTypesToPointers.class.getSimpleName());

      try (Stream<Path> files = Files.list(dir)) {
        return files.collect(toImmutableList());
      }
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static void assertValidJson(String src) {
    assertThat(src).isNotEmpty();

    Class<?> clazz = (src.charAt(0) == '{') ? LinkedHashMap.class : ArrayList.class;
    new Gson().fromJson(src, clazz); // Throws if invalid
  }
}
