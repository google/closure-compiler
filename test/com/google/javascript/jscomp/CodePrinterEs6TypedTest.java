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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.TypeDeclarationsIR.anyType;
import static com.google.javascript.rhino.TypeDeclarationsIR.arrayType;
import static com.google.javascript.rhino.TypeDeclarationsIR.booleanType;
import static com.google.javascript.rhino.TypeDeclarationsIR.functionType;
import static com.google.javascript.rhino.TypeDeclarationsIR.namedType;
import static com.google.javascript.rhino.TypeDeclarationsIR.numberType;
import static com.google.javascript.rhino.TypeDeclarationsIR.optionalParameter;
import static com.google.javascript.rhino.TypeDeclarationsIR.parameterizedType;
import static com.google.javascript.rhino.TypeDeclarationsIR.recordType;
import static com.google.javascript.rhino.TypeDeclarationsIR.stringType;
import static com.google.javascript.rhino.TypeDeclarationsIR.undefinedType;
import static com.google.javascript.rhino.TypeDeclarationsIR.unionType;
import static com.google.javascript.rhino.TypeDeclarationsIR.voidType;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.LinkedHashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Round-trip test for TypeScript-style inline type syntax.
 *
 * <p>Each expression is manually constructed and then code printed, as parsing type syntax is not
 * supported.
 */
@RunWith(JUnit4.class)
public final class CodePrinterEs6TypedTest extends CodePrinterTestBase {

  void assertPrettyPrint(Node root, String js) {
    String parsed =
        new CodePrinter.Builder(root)
            .setCompilerOptions(
                newCompilerOptions(
                    new CompilerOptionBuilder() {
                      @Override
                      void setOptions(CompilerOptions options) {
                        options.setPrettyPrint(true);
                        options.setPreferSingleQuotes(true);
                        options.setGentsMode(true);
                      }
                    }))
            .build();
    parsed = parsed.trim(); // strip trailing line break.
    assertThat(parsed).isEqualTo(js);
  }

  @Test
  public void testVariableDeclaration() {
    assertPrettyPrint(constructVarDeclarationWithType("any"), "var foo: any = 'hello';");
    assertPrettyPrint(constructVarDeclarationWithType("number"), "var foo: number = 'hello';");
    assertPrettyPrint(constructVarDeclarationWithType("boolean"), "var foo: boolean = 'hello';");
    assertPrettyPrint(constructVarDeclarationWithType("string"), "var foo: string = 'hello';");
    assertPrettyPrint(constructVarDeclarationWithType("void"), "var foo: void = 'hello';");
    assertPrettyPrint(constructVarDeclarationWithType("hello"), "var foo: hello = 'hello';");
  }

  // Constructs `var foo: <typeName> = "hello"`;
  private static Node constructVarDeclarationWithType(String typeName) {
    Node lhs = IR.name("foo");
    lhs.setDeclaredTypeExpression(namedType(typeName));
    return IR.var(lhs, IR.string("hello"));
  }

  @Test
  public void testFunctionParamDeclaration() {
    Node stringType = namedType("string");
    Node xParam = IR.name("x");
    xParam.setDeclaredTypeExpression(stringType);
    Node fnNode = IR.function(IR.name("foo"), IR.paramList(xParam), IR.block());
    assertPrettyPrint(fnNode, "function foo(x: string) {\n}");
  }

  @Test
  public void testFunctionParamDeclaration_defaultValue() {
    Node stringType = namedType("string");
    Node xParam = IR.name("x");
    xParam.setDeclaredTypeExpression(stringType);
    Node fnNode =
        IR.function(
            IR.name("foo"),
            IR.paramList(new Node(Token.DEFAULT_VALUE, xParam, IR.string("hello"))),
            IR.block());
    assertPrettyPrint(fnNode, "function foo(x: string = 'hello') {\n}");
  }

  @Test
  public void testFunctionParamDeclaration_arrow() {
    Node stringType = namedType("string");
    Node param = IR.name("x");
    param.setDeclaredTypeExpression(stringType);
    Node fnNode =
        new Node(
            Token.FUNCTION,
            IR.name(""),
            IR.paramList(param),
            IR.add(IR.string("hello"), IR.name("x")));
    fnNode.setIsArrowFunction(true);
    Node fnStatement = IR.exprResult(fnNode);
    assertPrettyPrint(fnStatement, "(x: string) => 'hello' + x;");
  }

  @Test
  public void testFunctionReturn() {
    Node returnType = namedType("string");
    Node fnNode =
        IR.function(IR.name("foo"), IR.paramList(), IR.block(IR.returnNode(IR.string("hello"))));
    fnNode.setDeclaredTypeExpression(returnType);
    assertPrettyPrint(fnNode, "function foo(): string {\n  return 'hello';\n}");
  }

  @Test
  public void testFunctionReturn_arrow() {
    Node returnType = namedType("string");
    Node fnNode = new Node(Token.FUNCTION, IR.name(""), IR.paramList(), IR.string("hello"));
    fnNode.setDeclaredTypeExpression(returnType);
    fnNode.setIsArrowFunction(true);
    Node fnStatement = IR.exprResult(fnNode);
    assertPrettyPrint(fnStatement, "(): string => 'hello';");
  }

  @Test
  public void testCompositeType() {
    Node type = namedType("mymod.ns.Type");
    Node lhs = IR.name("foo");
    lhs.setDeclaredTypeExpression(type);
    Node var = IR.var(lhs);
    assertPrettyPrint(var, "var foo: mymod.ns.Type;");
  }

  @Test
  public void testArrayType() {
    Node type = new Node(Token.ARRAY_TYPE, namedType("string"));
    Node lhs = IR.name("foo");
    lhs.setDeclaredTypeExpression(type);
    Node var = IR.var(lhs);
    assertPrettyPrint(var, "var foo: string[];");
  }

  @Test
  public void testArrayType_qualifiedType() {
    Node arrayType = arrayType(namedType("mymod.ns.Type"));
    Node lhs = IR.name("foo");
    lhs.setDeclaredTypeExpression(arrayType);
    Node var = IR.var(lhs);
    assertPrettyPrint(var, "var foo: mymod.ns.Type[];");
  }

  @Test
  public void testParameterizedType() {
    Node type =
        parameterizedType(
            namedType("my.parameterized.Type"),
            ImmutableList.of(namedType("ns.A"), namedType("ns.B")));
    Node lhs = IR.name("x");
    lhs.setDeclaredTypeExpression(type);
    Node var = IR.var(lhs);
    assertPrettyPrint(var, "var x: my.parameterized.Type<ns.A, ns.B>;");
    assertThat(parameterizedType(namedType("ns.Plain"), ImmutableList.of()).getToken())
        .isEqualTo(Token.NAMED_TYPE);
  }

  @Test
  public void testTypeDeclarationsIrBuiltinRecordUnionAndFunctionTypes() {
    Node s = IR.name("s");
    s.setDeclaredTypeExpression(stringType());
    assertPrettyPrint(IR.var(s), "var s: string;");

    Node n = IR.name("n");
    n.setDeclaredTypeExpression(numberType());
    assertPrettyPrint(IR.var(n), "var n: number;");

    Node b = IR.name("b");
    b.setDeclaredTypeExpression(booleanType());
    assertPrettyPrint(IR.var(b), "var b: boolean;");

    Node a = IR.name("a");
    a.setDeclaredTypeExpression(anyType());
    assertPrettyPrint(IR.var(a), "var a: any;");

    Node v = IR.name("v");
    v.setDeclaredTypeExpression(voidType());
    assertPrettyPrint(IR.var(v), "var v: void;");

    assertThat(undefinedType().getToken()).isEqualTo(Token.UNDEFINED_TYPE);
    assertThat(optionalParameter(stringType()).getToken()).isEqualTo(Token.OPTIONAL_PARAMETER);

    LinkedHashMap<String, Node> props = new LinkedHashMap<>();
    props.put("count", numberType());
    props.put("label", stringType());
    Node rec = IR.name("rec");
    rec.setDeclaredTypeExpression(recordType(props));
    assertPrettyPrint(IR.var(rec), "var rec: {count:number, label:string};");

    Node union = IR.name("u");
    union.setDeclaredTypeExpression(unionType(stringType(), numberType()));
    assertPrettyPrint(IR.var(union), "var u: string | number;");

    LinkedHashMap<String, Node> required = new LinkedHashMap<>();
    required.put("x", numberType());
    LinkedHashMap<String, Node> optional = new LinkedHashMap<>();
    optional.put("y", stringType());
    Node fn = IR.name("fn");
    fn.setDeclaredTypeExpression(
        functionType(booleanType(), required, optional, "rest", arrayType(numberType())));
    assertPrettyPrint(IR.var(fn), "var fn: (x: number, y?: string, ...rest: number[]) => boolean;");
  }
}
