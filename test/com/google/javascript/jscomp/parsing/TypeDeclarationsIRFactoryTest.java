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

package com.google.javascript.jscomp.parsing;

import static com.google.common.truth.Truth.THROW_ASSERTION_ERROR;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.anyType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.arrayType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.booleanType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.namedType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.numberType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.optionalParameter;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.parameterizedType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.recordType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.stringType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.unionType;
import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.Token.ANY_TYPE;
import static com.google.javascript.rhino.Token.ARRAY_TYPE;
import static com.google.javascript.rhino.Token.BOOLEAN_TYPE;
import static com.google.javascript.rhino.Token.FUNCTION_TYPE;
import static com.google.javascript.rhino.Token.NAMED_TYPE;
import static com.google.javascript.rhino.Token.NUMBER_TYPE;
import static com.google.javascript.rhino.Token.PARAMETERIZED_TYPE;
import static com.google.javascript.rhino.Token.RECORD_TYPE;
import static com.google.javascript.rhino.Token.REST_PARAMETER_TYPE;
import static com.google.javascript.rhino.Token.STRING_TYPE;
import static java.util.Arrays.asList;

import com.google.common.collect.Sets;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.testing.NodeSubject;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.TypeDeclarationNode;

import junit.framework.TestCase;

import java.util.LinkedHashMap;

/**
 * Tests the conversion of type ASTs from the awkward format inside a
 * jstypeexpression to the better format of native type declarations.
 *
 * @author alexeagle@google.com (Alex Eagle)
 */
public class TypeDeclarationsIRFactoryTest extends TestCase {

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
    Node stringKey = IR.stringKey("p1");
    stringKey.addChildToFront(stringType());
    Node stringKey1 = IR.stringKey("p2");
    stringKey1.addChildToFront(booleanType());
    assertParseTypeAndConvert("function(string, boolean)")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, anyType(), stringKey, stringKey1));
  }

  public void testConvertFunctionReturnType() throws Exception {
    assertParseTypeAndConvert("function(): number")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, numberType()));
  }

  public void testConvertFunctionThisType() throws Exception {
    Node stringKey1 = IR.stringKey("p1");
    stringKey1.addChildToFront(stringType());
    assertParseTypeAndConvert("function(this:goog.ui.Menu, string)")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, anyType(), stringKey1));
  }

  public void testConvertFunctionNewType() throws Exception {
    Node stringKey1 = IR.stringKey("p1");
    stringKey1.addChildToFront(stringType());
    assertParseTypeAndConvert("function(new:goog.ui.Menu, string)")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, anyType(), stringKey1));
  }

  public void testConvertVariableParameters() throws Exception {
    Node stringKey1 = IR.stringKey("p1");
    stringKey1.addChildToFront(stringType());
    Node stringKey2 = IR.stringKey("p2");
    stringKey2.addChildToFront(arrayType(numberType()));
    assertParseTypeAndConvert("function(string, ...number): number")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, numberType(),
            stringKey1, new TypeDeclarationNode(REST_PARAMETER_TYPE, stringKey2)));
  }

  public void testConvertOptionalFunctionParameters() throws Exception {
    LinkedHashMap<String, TypeDeclarationNode> parameters = new LinkedHashMap<>();
    parameters.put("p1", optionalParameter(stringType()));
    parameters.put("p2", optionalParameter(numberType()));
    assertParseTypeAndConvert("function(?string=, number=)")
        .isEqualTo(TypeDeclarationsIRFactory
            .functionType(anyType(), parameters, null, null));
  }

  public void testConvertVarArgs() throws Exception {
    assertParseJsDocAndConvert("@param {...*} p", "p")
        .isEqualTo(arrayType(anyType()));
  }

  // the JsDocInfoParser.parseTypeString helper doesn't understand an ELLIPSIS
  // as the root token, so we need a whole separate fixture just for that case.
  // This is basically inlining that helper and changing the entry point into
  // the parser.
  // TODO(alexeagle): perhaps we should fix the parseTypeString helper since
  // this seems like a bug, but it's not easy.
  private NodeSubject assertParseJsDocAndConvert(String jsDoc,
      String parameter) {
    // We need to tack a closing comment token on the end so the parser doesn't
    // think it reached premature EOL
    jsDoc = jsDoc + " */";
    Config config = new Config(
        Sets.<String>newHashSet(),
        Sets.<String>newHashSet(),
        false,
        LanguageMode.ECMASCRIPT3,
        false);
    JsDocInfoParser parser = new JsDocInfoParser(
        new JsDocTokenStream(jsDoc),
        jsDoc,
        0,
        null,
        null,
        config,
        NullErrorReporter.forOldRhino());
    assertTrue(parser.parse());
    JSTypeExpression parameterType = parser.retrieveAndResetParsedJSDocInfo()
        .getParameterType(parameter);
    assertNotNull(parameterType);
    Node oldAST = parameterType.getRoot();
    assertNotNull(jsDoc + " did not produce a parsed AST", oldAST);
    return new NodeSubject(THROW_ASSERTION_ERROR,
        TypeDeclarationsIRFactory.convertTypeNodeAST(oldAST));
  }

  private NodeSubject assertParseTypeAndConvert(final String typeExpr) {
    Node oldAST = JsDocInfoParser.parseTypeString(typeExpr);
    assertNotNull(typeExpr + " did not produce a parsed AST", oldAST);
    return new NodeSubject(THROW_ASSERTION_ERROR,
        TypeDeclarationsIRFactory.convertTypeNodeAST(oldAST));
  }
}
