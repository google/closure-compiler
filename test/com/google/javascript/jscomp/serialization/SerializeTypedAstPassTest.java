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
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.nullness.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SerializeTypedAstPassTest extends CompilerTestCase {

  private Consumer<TypedAst> astConsumer;
  // individual test cases may override this
  private @Nullable ImmutableSet<String> typesToForwardDeclare = null;

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
    enableDebugLogging(true);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new SerializeTypedAstPass(compiler, astConsumer, SerializationOptions.SKIP_DEBUG_INFO);
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
  public void testAst_constNumber() throws InvalidProtocolBufferException {
    int numberType = PrimitiveType.NUMBER_TYPE.getNumber();
    SerializationResult result = compile("const x = 5;");

    assertThat(result.sourceNodes.get(0))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .setRelativeLine(1)
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.CONST_DECLARATION)
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.IDENTIFIER)
                                .setStringValuePointer(result.findInStringPool("x"))
                                .setOriginalNamePointer(result.findInStringPool("x"))
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
    assertThat(compile("/** @externs */ function foo() {}").externNodes).hasSize(2);
  }

  @Test
  public void testAst_typeSummaryFiles_areNotSerialized_orSearchedForTypes() {
    enableCreateModuleMap();
    // SerializeTypedAstPass will drop the type summary files, which live on the externs root, to
    // avoid serializing them.
    allowExternsChanges();

    Externs closureExterns = externs(new TestExternsBuilder().addClosureExterns().build());
    SourceFile mandatorySource = SourceFile.fromCode("mandatory.js", "/* mandatory source */");

    SerializationResult typeSummaryResult =
        compile(
            closureExterns,
            srcs(
                mandatorySource,
                SourceFile.fromCode(
                    "foo.i.js",
                    lines(
                        "/** @fileoverview @typeSummary */", //
                        "",
                        "goog.loadModule(function(exports) {",
                        "  goog.module('a.Foo');",
                        "  class Foo { }",
                        "  exports = Foo;",
                        "});"))));
    SerializationResult emptyResult =
        compile(
            closureExterns, //
            srcs(mandatorySource));

    assertThat(typeSummaryResult.ast).isEqualTo(emptyResult.ast);
  }

  @Test
  public void testAst_letString() throws InvalidProtocolBufferException {
    int stringType = PrimitiveType.STRING_TYPE.getNumber();
    SerializationResult result = compile("let s = 'hello';");

    assertThat(result.sourceNodes.get(0))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .ignoringFieldDescriptors(
            AstNode.getDescriptor().findFieldByName("relative_line"),
            AstNode.getDescriptor().findFieldByName("relative_column"))
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.LET_DECLARATION)
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.IDENTIFIER)
                                .setStringValuePointer(result.findInStringPool("s"))
                                .setOriginalNamePointer(result.findInStringPool("s"))
                                .setType(stringType)
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.STRING_LITERAL)
                                        .setStringValuePointer(result.findInStringPool("hello"))
                                        .setType(stringType)
                                        .build())
                                .build())
                        .build())
                .build());
  }

  @Test
  public void testAst_templateLit_illegalEscape() throws InvalidProtocolBufferException {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Template_literals#es2018_revision_of_illegal_escape_sequences
    SerializationResult result = compile("latex`\\unicode`;");

    assertThat(result.sourceNodes.get(0))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .ignoringFieldDescriptors(
            AstNode.getDescriptor().findFieldByName("relative_line"),
            AstNode.getDescriptor().findFieldByName("relative_column"),
            AstNode.getDescriptor().findFieldByName("type"))
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.EXPRESSION_STATEMENT)
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.TAGGED_TEMPLATELIT)
                                .setBooleanProperties(1L << NodeProperty.FREE_CALL.getNumber())
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.IDENTIFIER)
                                        .setStringValuePointer(result.findInStringPool("latex"))
                                        .setOriginalNamePointer(result.findInStringPool("latex"))
                                        .build())
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.TEMPLATELIT)
                                        .addChild(
                                            AstNode.newBuilder()
                                                .setKind(NodeKind.TEMPLATELIT_STRING)
                                                .setTemplateStringValue(
                                                    TemplateStringValue.newBuilder()
                                                        .setCookedStringPointer(-1)
                                                        .setRawStringPointer(
                                                            result.findInStringPool("\\unicode"))
                                                        .build()))
                                        .build())
                                .build())
                        .build())
                .build());
  }

  @Test
  public void testAst_numberInCast() {
    // CAST nodes in JSCompiler are a combination of a child node + JSDoc @type. Because we don't
    // serialize JSDoc @types it doesn't make sense to serialize the CAST node.
    int unknownType = PrimitiveType.UNKNOWN_TYPE.getNumber();
    assertThat(compileToAstNode("/** @type {?} */ (1);"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .ignoringFieldDescriptors(
            AstNode.getDescriptor().findFieldByName("relative_line"),
            AstNode.getDescriptor().findFieldByName("relative_column"))
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.EXPRESSION_STATEMENT)
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.NUMBER_LITERAL)
                                .setBooleanProperties(
                                    (1L << NodeProperty.IS_PARENTHESIZED.getNumber())
                                        | (1L << NodeProperty.COLOR_FROM_CAST.getNumber()))
                                .setDoubleValue(1)
                                .setType(unknownType)
                                .build())
                        .build())
                .build());
  }

  @Test
  public void testAst_arrowFunction() throws InvalidProtocolBufferException {
    SerializationResult result = compile("() => 'hello';");

    assertThat(result.sourceNodes.get(0))
        .ignoringFieldDescriptors(
            AstNode.getDescriptor().findFieldByName("relative_line"),
            AstNode.getDescriptor().findFieldByName("type"),
            AstNode.getDescriptor().findFieldByName("relative_column"))
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.EXPRESSION_STATEMENT)
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.FUNCTION_LITERAL)
                                .setBooleanProperties(1L << NodeProperty.ARROW_FN.getNumber())
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
                                        .setStringValuePointer(result.findInStringPool("hello"))
                                        .build())
                                .build())
                        .build())
                .build());
  }

  @Test
  public void testRewrittenGoogModule_containsOriginalNames()
      throws InvalidProtocolBufferException {
    enableRewriteClosureCode();

    SerializationResult result = compile("goog.module('a.b.c'); function f(x) {}");

    assertThat(result.sourceNodes.get(0))
        .ignoringFieldDescriptors(
            AstNode.getDescriptor().findFieldByName("relative_line"),
            AstNode.getDescriptor().findFieldByName("relative_column"),
            AstNode.getDescriptor().findFieldByName("type"))
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .setBooleanProperties(1L << NodeProperty.GOOG_MODULE.getNumber())
                // `/** @const */ var module$exports$a$b$c = {};`
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.VAR_DECLARATION)
                        .setBooleanProperties(1L << NodeProperty.IS_NAMESPACE.getNumber())
                        .setJsdoc(OptimizationJsdoc.newBuilder().addKind(JsdocTag.JSDOC_CONST))
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.IDENTIFIER)
                                .setStringValuePointer(
                                    result.findInStringPool("module$exports$a$b$c"))
                                .addChild(AstNode.newBuilder().setKind(NodeKind.OBJECT_LITERAL))))
                // `function module$contents$a$b$c_f(x) {}`
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.FUNCTION_LITERAL)
                        .setOriginalNamePointer(result.findInStringPool("f"))
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.IDENTIFIER)
                                .setStringValuePointer(
                                    result.findInStringPool("module$contents$a$b$c_f"))
                                .setOriginalNamePointer(result.findInStringPool("f")))
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.PARAMETER_LIST)
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.IDENTIFIER)
                                        .setStringValuePointer(result.findInStringPool("x"))
                                        .setOriginalNamePointer(result.findInStringPool("x"))
                                        .build()))
                        .addChild(AstNode.newBuilder().setKind(NodeKind.BLOCK)))
                .build());
  }

  @Test
  public void serializesExternsFile() throws IOException {
    ensureLibraryInjected("base");

    SerializationResult result =
        compileWithExterns(
            new TestExternsBuilder().addMath().addObject().build(), "let s = 'hello';");

    LazyAst externAst = result.ast.getExternAstList().get(0);
    assertThat(
            result
                .ast
                .getSourceFilePool()
                .getSourceFileList()
                .get(externAst.getSourceFile() - 1)
                .getFilename())
        .isEqualTo("externs");
  }

  @Test
  public void serializesSourceOfSyntheticCode() throws IOException {
    ensureLibraryInjected("base");

    SerializationResult result =
        compileWithExterns(
            new TestExternsBuilder().addMath().addObject().build(), "let s = 'hello';");

    LazyAst lazyAst = result.ast.getCodeAstList().get(0);
    assertThat(
            result
                .ast
                .getSourceFilePool()
                .getSourceFileList()
                .get(lazyAst.getSourceFile() - 1)
                .getFilename())
        .isEqualTo("testcode");

    // 'base' is loaded at the beginning of the first script
    AstNode script = result.sourceNodes.get(0);
    AstNode baseLibraryStart = script.getChild(0);
    assertThat(
            result
                .ast
                .getSourceFilePool()
                .getSourceFileList()
                .get(baseLibraryStart.getSourceFile() - 1)
                .getFilename())
        .endsWith("js/base.js");
    // children of the synthetic subtree default to the root's file [synthetic:base]
    assertThat(baseLibraryStart.getChild(0).getSourceFile()).isEqualTo(0);
    // "let s = 'hello'" defaults to the parent's file 'testcode'
    assertThat(script.getChild(script.getChildCount() - 1).getSourceFile()).isEqualTo(0);
  }

  @Test
  public void serializesExternProperties() throws IOException {
    enableGatherExternProperties();

    SerializationResult result =
        compileWithExterns(
            lines(
                "class Foo {", //
                // Ensure JSDoc properties are included (b/180424427)
                "  /** @param {{arg: string}} x */",
                "  method(x) { }",
                "}"),
            "");

    assertThat(result.ast.getExternsSummary().getPropNamePtrList())
        .containsAtLeast(result.findInStringPool("method"), result.findInStringPool("arg"));
  }

  @Test
  public void serializeInlineSourceMappingURL() throws InvalidProtocolBufferException {
    // We want TypedAST to support inline source maps (which are input source maps passed
    // by embedding a `//# sourceMappingURL=<url>` where <url> is a base64-encoded "data url").
    String sourceMapTestCode =
        lines(
            "var X = (function () {",
            "    function X(input) {",
            "        this.y = input;",
            "    }",
            "    return X;",
            "}());");

    String base64Prefix = "data:application/json;base64,";
    String encodedSourceMap =
        "eyJ2ZXJzaW9uIjozLCJmaWxlIjoiZm9vLmpzIiwic291cmNlUm9vdCI6IiIsInNvdXJjZXMiOlsiZm9vLnRzIl0sIm5hbWVzIjpbXSwibWFwcGluZ3MiOiJBQUFBO0lBR0UsV0FBWSxLQUFhO1FBQ3ZCLElBQUksQ0FBQyxDQUFDLEdBQUcsS0FBSyxDQUFDO0lBQ2pCLENBQUM7SUFDSCxRQUFDO0FBQUQsQ0FBQyxBQU5ELElBTUM7QUFFRCxPQUFPLENBQUMsR0FBRyxDQUFDLElBQUksQ0FBQyxDQUFDLENBQUMsQ0FBQyxDQUFDLENBQUMifQ==";

    String code = sourceMapTestCode + "\n//# sourceMappingURL=" + base64Prefix + encodedSourceMap;

    SerializationResult result = compile(code);

    LazyAst lazyAst = result.ast.getCodeAstList().get(0);
    String sourceMappingURL = lazyAst.getSourceMappingUrl();

    assertThat(sourceMappingURL).isEqualTo(encodedSourceMap); // Do not serizile the base-64 prefix.
  }

  @Test
  public void doesNotSerializeInputSourceMappingURL() throws InvalidProtocolBufferException {
    // We do not want TypedAST to support input source maps (source maps in separate files).
    String sourceMapTestCode =
        lines(
            "var X = (function () {",
            "    function X(input) {",
            "        this.y = input;",
            "    }",
            "    return X;",
            "}());");

    String code = sourceMapTestCode + "\n//# sourceMappingURL=foo.js.map";

    SerializationResult result = compile(code);

    LazyAst lazyAst = result.ast.getCodeAstList().get(0);
    String sourceMappingURL = lazyAst.getSourceMappingUrl();

    assertThat(sourceMappingURL).isEmpty();
  }

  private AstNode compileToAstNode(String source) {
    return compile(source).sourceNodes.get(0);
  }

  private SerializationResult compile(String source) {
    return compileWithExterns(DEFAULT_EXTERNS, source);
  }

  private SerializationResult compile(TestPart... parts) {
    TypedAst[] resultAst = new TypedAst[1];
    astConsumer = (ast) -> resultAst[0] = ast;
    test(parts);
    try {
      return new SerializationResult(resultAst[0]);
    } catch (InvalidProtocolBufferException ex) {
      throw new AssertionError(ex);
    }
  }

  private static class SerializationResult {
    private final ImmutableList<AstNode> externNodes;
    private final ImmutableList<AstNode> sourceNodes;
    private final TypedAst ast;

    SerializationResult(TypedAst ast) throws InvalidProtocolBufferException {
      this.externNodes =
          ast.getExternAstList().stream()
              .map(lazyAst -> parseAstNode(lazyAst.getScript()))
              .collect(toImmutableList());
      this.sourceNodes =
          ast.getCodeAstList().stream()
              .map(lazyAst -> parseAstNode(lazyAst.getScript()))
              .collect(toImmutableList());
      this.ast = ast;
    }

    /**
     * Returns the offset of the given string in this ast's string pool, throwing an exception if
     * not present
     */
    int findInStringPool(String str) {
      if (str.isEmpty()) {
        return 0;
      }

      int offset =
          this.ast.getStringPool().getStringsList().indexOf(ByteString.copyFromUtf8(str)) + 1;
      checkState(
          offset != -1,
          "Could not find string '%s' in string pool %s",
          str,
          this.ast.getStringPool());
      return offset;
    }
  }

  private static AstNode parseAstNode(ByteString byteString) {
    try {
      return AstNode.parseFrom(byteString, ExtensionRegistry.getEmptyRegistry());
    } catch (InvalidProtocolBufferException ex) {
      throw new AssertionError(ex);
    }
  }

  private SerializationResult compileWithExterns(String externs, String source) {
    return compile(externs(externs), srcs(source));
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
          Path.of(
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
    var unused = new Gson().fromJson(src, clazz); // Throws if invalid
  }
}
