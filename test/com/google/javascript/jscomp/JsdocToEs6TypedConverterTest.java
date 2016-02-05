/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.THROW_ASSERTION_ERROR;
import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.Token.ANY_TYPE;
import static com.google.javascript.rhino.Token.ARRAY_TYPE;
import static com.google.javascript.rhino.Token.BOOLEAN_TYPE;
import static com.google.javascript.rhino.Token.FUNCTION_TYPE;
import static com.google.javascript.rhino.Token.NAMED_TYPE;
import static com.google.javascript.rhino.Token.NUMBER_TYPE;
import static com.google.javascript.rhino.Token.PARAMETERIZED_TYPE;
import static com.google.javascript.rhino.Token.RECORD_TYPE;
import static com.google.javascript.rhino.Token.STRING_TYPE;
import static com.google.javascript.rhino.TypeDeclarationsIR.anyType;
import static com.google.javascript.rhino.TypeDeclarationsIR.arrayType;
import static com.google.javascript.rhino.TypeDeclarationsIR.booleanType;
import static com.google.javascript.rhino.TypeDeclarationsIR.functionType;
import static com.google.javascript.rhino.TypeDeclarationsIR.namedType;
import static com.google.javascript.rhino.TypeDeclarationsIR.numberType;
import static com.google.javascript.rhino.TypeDeclarationsIR.parameterizedType;
import static com.google.javascript.rhino.TypeDeclarationsIR.recordType;
import static com.google.javascript.rhino.TypeDeclarationsIR.stringType;
import static com.google.javascript.rhino.TypeDeclarationsIR.unionType;
import static java.util.Arrays.asList;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.JsdocToEs6TypedConverter.TypeDeclarationsIRFactory;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.jscomp.testing.NodeSubject;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.TypeDeclarationNode;

import java.util.LinkedHashMap;

/**
 * Tests the conversion of closure-style type declarations in JSDoc
 * to inline type declarations, by running both syntaxes through the parser
 * and verifying the resulting AST is the same.
 */
public final class JsdocToEs6TypedConverterTest extends CompilerTestCase {

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6_TYPED);
    compareJsDoc = false;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT6_TYPED);
    return options;
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new JsdocToEs6TypedConverter(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testVariableDeclaration() {
    test("/** @type {string} */ var print;", "var print: string;");
  }

  public void testVariableDeclarationWithoutDeclaredType() throws Exception {
    test("var print;", "var print;");
  }

  public void testFunctionReturnType() throws Exception {
    test("/** @return {boolean} */ function b(){}", "function b(): boolean {}");
  }

  public void testFunctionParameterTypes() throws Exception {
    test("/** @param {number} n @param {string} s */ function t(n,s){}",
        "function t(n: number, s: string) {}");
  }

  public void testFunctionInsideAssignment() throws Exception {
    test("/** @param {boolean} b @return {boolean} */ "
            + "var f = function(b){return !b};",
        "var f = function(b: boolean): boolean { return !b; };");
  }

  public void testNestedFunctions() throws Exception {
    test("/**@param {boolean} b*/ "
            + "var f = function(b){var t = function(l) {}; t();};",
            "var f = function(b: boolean) {"
            + "  var t = function(l) {"
            + "  };"
            + "  t();"
            + "};");
  }

  public void testUnknownType() throws Exception {
    test("/** @type {?} */ var n;", "var n: any;");
  }

  // TypeScript doesn't have a representation for the Undefined type,
  // so our transpilation is lossy here.
  public void testUndefinedType() throws Exception {
    test("/** @type {undefined} */ var n;", "var n;");
  }

  public void testConvertSimpleTypes() {
    assertParseTypeAndConvert("?").hasType(ANY_TYPE);
    assertParseTypeAndConvert("*").hasType(ANY_TYPE);
    assertParseTypeAndConvert("boolean").hasType(BOOLEAN_TYPE);
    assertParseTypeAndConvert("number").hasType(NUMBER_TYPE);
    assertParseTypeAndConvert("string").hasType(STRING_TYPE);
  }

  public void testConvertNamedTypes() throws Exception {
    assertParseTypeAndConvert("Window")
        .isEqualTo(namedType("Window"));
    assertParseTypeAndConvert("goog.ui.Menu")
        .isEqualTo(namedType("goog.ui.Menu"));

    assertNode(namedType("goog.ui.Menu"))
        .isEqualTo(new TypeDeclarationNode(NAMED_TYPE,
            IR.getprop(IR.getprop(IR.name("goog"), IR.string("ui")), IR.string("Menu"))));
  }

  public void testConvertTypeApplication() throws Exception {
    assertParseTypeAndConvert("Array.<string>")
        .isEqualTo(arrayType(stringType()));
    assertParseTypeAndConvert("Object.<string, number>")
        .isEqualTo(parameterizedType(namedType("Object"), asList(stringType(), numberType())));

    assertNode(parameterizedType(namedType("Array"), asList(stringType())))
        .isEqualTo(new TypeDeclarationNode(PARAMETERIZED_TYPE,
            new TypeDeclarationNode(NAMED_TYPE, IR.name("Array")),
            new TypeDeclarationNode(STRING_TYPE)));
  }

  public void testConvertTypeUnion() throws Exception {
    assertParseTypeAndConvert("(number|boolean)")
        .isEqualTo(unionType(numberType(), booleanType()));
  }

  public void testConvertRecordType() throws Exception {
    LinkedHashMap<String, TypeDeclarationNode> properties = new LinkedHashMap<>();
    properties.put("myNum", numberType());
    properties.put("myObject", null);

    assertParseTypeAndConvert("{myNum: number, myObject}")
        .isEqualTo(recordType(properties));
  }

  public void testCreateRecordType() throws Exception {
    LinkedHashMap<String, TypeDeclarationNode> properties = new LinkedHashMap<>();
    properties.put("myNum", numberType());
    properties.put("myObject", null);
    TypeDeclarationNode node = recordType(properties);

    Node prop1 = IR.stringKey("myNum");
    prop1.addChildToFront(new TypeDeclarationNode(NUMBER_TYPE));
    Node prop2 = IR.stringKey("myObject");

    assertNode(node)
        .isEqualTo(new TypeDeclarationNode(RECORD_TYPE, prop1, prop2));
  }

  public void testConvertRecordTypeWithTypeApplication() throws Exception {
    Node prop1 = IR.stringKey("length");
    assertParseTypeAndConvert("Array.<{length}>")
        .isEqualTo(new TypeDeclarationNode(ARRAY_TYPE,
            new TypeDeclarationNode(RECORD_TYPE, prop1)));
  }

  public void testConvertNullableType() throws Exception {
    assertParseTypeAndConvert("?number")
        .isEqualTo(numberType());
  }

  // TODO(alexeagle): change this test once we can capture nullability constraints in TypeScript
  public void testConvertNonNullableType() throws Exception {
    assertParseTypeAndConvert("!Object")
        .isEqualTo(namedType("Object"));
  }

  public void testConvertFunctionType() throws Exception {
    Node p1 = IR.name("p1");
    p1.setDeclaredTypeExpression(stringType());
    Node p2 = IR.name("p2");
    p2.setDeclaredTypeExpression(booleanType());
    assertParseTypeAndConvert("function(string, boolean)")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, anyType(), p1, p2));
  }

  public void testConvertFunctionReturnType() throws Exception {
    assertParseTypeAndConvert("function(): number")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, numberType()));
  }

  public void testConvertFunctionThisType() throws Exception {
    Node p1 = IR.name("p1");
    p1.setDeclaredTypeExpression(stringType());
    assertParseTypeAndConvert("function(this:goog.ui.Menu, string)")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, anyType(), p1));
  }

  public void testConvertFunctionNewType() throws Exception {
    Node p1 = IR.name("p1");
    p1.setDeclaredTypeExpression(stringType());
    assertParseTypeAndConvert("function(new:goog.ui.Menu, string)")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, anyType(), p1));
  }

  public void testConvertVariableParameters() throws Exception {
    Node p1 = IR.name("p1");
    p1.setDeclaredTypeExpression(stringType());
    Node p2 = IR.rest("p2");
    p2.setDeclaredTypeExpression(arrayType(numberType()));
    assertParseTypeAndConvert("function(string, ...number): number")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, numberType(), p1, p2));
  }

  public void testConvertOptionalFunctionParameters() throws Exception {
    LinkedHashMap<String, TypeDeclarationNode> requiredParams = new LinkedHashMap<>();
    LinkedHashMap<String, TypeDeclarationNode> optionalParams = new LinkedHashMap<>();
    optionalParams.put("p1", stringType());
    optionalParams.put("p2", numberType());
    assertParseTypeAndConvert("function(?string=, number=)")
        .isEqualTo(functionType(anyType(), requiredParams, optionalParams, null, null));
  }

  private NodeSubject assertParseTypeAndConvert(final String typeExpr) {
    Node oldAST = JsDocInfoParser.parseTypeString(typeExpr);
    assertNotNull(typeExpr + " did not produce a parsed AST", oldAST);
    return new NodeSubject(THROW_ASSERTION_ERROR,
        TypeDeclarationsIRFactory.convertTypeNodeAST(oldAST));
  }
}
