/*
 * Copyright 2017 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.testing.TypeSubject.assertType;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;

/**
 * Integration tests that check types of nodes are correct after running transpilation after NTI.
 */
public final class TranspileAfterNTITest extends IntegrationTestCase {

  @Override
  CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setDevMode(DevMode.EVERY_PASS);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setNewTypeInference(true);
    options.setRunOTIafterNTI(false);
    options.setTypeCheckEs6Natively(true);
    return options;
  }

  private Compiler getCompilerForTypeInfoCheck(String original) {
    CompilerOptions options = createCompilerOptions();
    Compiler compiler = compile(options, original);
    assertEquals(
        "Expected no warnings or errors\n"
            + "Errors: \n"
            + Joiner.on("\n").join(compiler.getErrors())
            + "\n"
            + "Warnings: \n"
            + Joiner.on("\n").join(compiler.getWarnings()),
        0,
        compiler.getErrors().length + compiler.getWarnings().length);
    return compiler;
  }

  private Node typeInfoCheckAndGetRoot(String original) {
    Compiler compiler = getCompilerForTypeInfoCheck(original);
    Node root = compiler.getJsRoot();
    (new TypeInfoCheck(compiler)).process(null, root);
    assertThat(compiler.getErrors()).isEmpty();
    return root;
  }

  public void testShorthandObjProp() {
    Node root = typeInfoCheckAndGetRoot("var /** number */ p = 1; var obj = { p };");

    Node obj = root.getFirstChild().getSecondChild().getFirstChild();
    assertType(obj.getTypeI()).isObjectTypeWithProperty("p").withTypeOfProp("p").isNumber();
    assertType(obj.getFirstChild().getTypeI())
        .isObjectTypeWithProperty("p")
        .withTypeOfProp("p")
        .isNumber();
    assertType(obj.getFirstFirstChild().getTypeI()).isNumber();
    assertType(obj.getFirstFirstChild().getFirstChild().getTypeI()).isNumber();
  }

  public void testMemberFunctionDef() {
    Node root =
        typeInfoCheckAndGetRoot(
            LINE_JOINER.join(
                "var obj = {",
                "  /** @param {number} n */",
                "  method (n) {",
                "    return 'string';",
                "  }",
                "};",
                "var s = obj.method(1);"));

    Node obj = root.getFirstFirstChild().getFirstChild();
    assertType(obj.getTypeI())
        .isObjectTypeWithProperty("method")
        .withTypeOfProp("method")
        .toStringIsEqualTo("function(number):string");
    assertType(obj.getFirstChild().getTypeI())
        .isObjectTypeWithProperty("method")
        .withTypeOfProp("method")
        .toStringIsEqualTo("function(number):string");
    assertType(obj.getFirstFirstChild().getTypeI()).toStringIsEqualTo("function(number):string");

    Node s = root.getFirstChild().getSecondChild().getFirstChild();
    assertType(s.getTypeI()).isString();
    assertType(s.getFirstFirstChild().getTypeI()).toStringIsEqualTo("function(number):string");
  }

  public void testComputedProp1() {
    Node root =
        typeInfoCheckAndGetRoot(
            LINE_JOINER.join(
                "var /** number */ i = 1;",
                "var /** string */ a = '';",
                "var obj = {a , [i + 1]: 1};"));

    Node firstVar = root.getFirstChild().getChildAtIndex(2);
    Node compPropName = firstVar.getFirstChild();
    assertType(compPropName.getTypeI())
        .isObjectTypeWithProperty("a")
        .withTypeOfProp("a")
        .isString();
    assertType(compPropName.getFirstChild().getTypeI()).isLiteralObject();

    Node secondVar = firstVar.getNext();
    assertType(secondVar.getFirstChild().getTypeI())
        .isObjectTypeWithProperty("a")
        .withTypeOfProp("a")
        .isString();
    assertType(secondVar.getFirstChild().getTypeI()).isObjectTypeWithoutProperty("2");

    Node firstAssign = secondVar.getFirstFirstChild().getFirstChild();
    assertType(firstAssign.getTypeI()).isString();

    Node firstName = firstAssign.getSecondChild();
    assertType(firstName.getTypeI()).isString();
  }

  public void testComputedProp2() {
    Node root = typeInfoCheckAndGetRoot("var i = 1; var obj = {'a': i , [i + 1]: 1};");

    Node firstVar = root.getFirstChild().getSecondChild();
    Node compPropName = firstVar.getFirstChild();
    assertType(compPropName.getTypeI())
        .isObjectTypeWithProperty("a")
        .withTypeOfProp("a")
        .isNumber();
    assertType(compPropName.getFirstChild().getTypeI()).isLiteralObject();

    Node secondVar = firstVar.getNext();
    assertType(secondVar.getFirstChild().getTypeI())
        .isObjectTypeWithProperty("a")
        .withTypeOfProp("a")
        .isNumber();
    assertType(secondVar.getFirstChild().getTypeI()).isObjectTypeWithoutProperty("2");

    Node firstAssign = secondVar.getFirstFirstChild().getFirstChild();
    assertType(firstAssign.getTypeI()).isNumber();

    Node firstName = firstAssign.getSecondChild();
    assertType(firstName.getTypeI()).isNumber();
  }

  public void testForOf1() {
    Compiler compiler = getCompilerForTypeInfoCheck("for (var i of [1,2]) {}");
    Node root = compiler.getJsRoot();

    Node varNode = findDecl(root, "$jscomp$iter$0");
    (new TypeInfoCheck(compiler)).setCheckSubTree(varNode);
    assertThat(compiler.getErrors()).isEmpty();
    Node forNode = varNode.getNext();
    (new TypeInfoCheck(compiler)).setCheckSubTree(forNode);
    assertThat(compiler.getErrors()).isEmpty();

    assertType(varNode.getFirstChild().getTypeI()).toStringIsEqualTo("Iterator<number>");
    assertType(varNode.getFirstFirstChild().getTypeI()).toStringIsEqualTo("Iterator<number>");
    assertType(varNode.getFirstFirstChild().getFirstFirstChild().getTypeI())
        .toStringIsEqualTo("$jscomp");
    assertType(varNode.getFirstFirstChild().getSecondChild().getTypeI())
        .toStringIsEqualTo("Array<number>");

    Node decNode = forNode.getFirstChild();
    assertType(decNode.getFirstChild().getTypeI()).toStringIsEqualTo("IIterableResult<number>");
    assertType(decNode.getFirstFirstChild().getTypeI())
        .toStringIsEqualTo("IIterableResult<number>");
    assertType(decNode.getFirstFirstChild().getFirstFirstChild().getTypeI())
        .toStringIsEqualTo("Iterator<number>");

    Node boolNode = decNode.getNext();
    assertType(boolNode.getTypeI()).isBoolean();
    assertType(boolNode.getFirstChild().getTypeI()).isBoolean();
    assertType(boolNode.getFirstFirstChild().getTypeI())
        .toStringIsEqualTo("IIterableResult<number>");

    Node assignNode = boolNode.getNext();
    assertType(assignNode.getFirstChild().getTypeI()).toStringIsEqualTo("IIterableResult<number>");
    assertType(assignNode.getSecondChild().getTypeI()).toStringIsEqualTo("IIterableResult<number>");
    assertType(assignNode.getSecondChild().getFirstFirstChild().getTypeI())
        .toStringIsEqualTo("Iterator<number>");

    Node blockNode = assignNode.getNext();
    assertType(blockNode.getFirstFirstChild().getTypeI()).isNumber();
    assertType(blockNode.getFirstFirstChild().getFirstChild().getTypeI()).isNumber();
    assertType(blockNode.getFirstFirstChild().getFirstFirstChild().getTypeI())
        .toStringIsEqualTo("IIterableResult<number>");
  }

  public void testForOf2() {
    Compiler compiler =
        getCompilerForTypeInfoCheck(
            LINE_JOINER.join(
                "var /** string */ x = '';",
                "const /** !Array<string> */ iter = ['a', 'b'];",
                "for (x of iter) { var y = x; }"));
    Node root = compiler.getJsRoot();

    Node varNode = findDecl(root, "$jscomp$iter$0");
    (new TypeInfoCheck(compiler)).setCheckSubTree(varNode);
    assertThat(compiler.getErrors()).isEmpty();
    Node forNode = varNode.getNext();
    (new TypeInfoCheck(compiler)).setCheckSubTree(forNode);
    assertThat(compiler.getErrors()).isEmpty();

    assertType(varNode.getFirstChild().getTypeI()).toStringIsEqualTo("Iterator<string>");

    Node decNode = forNode.getFirstChild();
    assertType(decNode.getFirstChild().getTypeI()).toStringIsEqualTo("IIterableResult<string>");

    Node boolNode = decNode.getNext();
    assertType(boolNode.getFirstFirstChild().getTypeI())
        .toStringIsEqualTo("IIterableResult<string>");

    Node blockNode = boolNode.getNext().getNext();
    assertType(blockNode.getFirstFirstChild().getTypeI()).isString();
    assertType(blockNode.getFirstFirstChild().getFirstChild().getTypeI()).isString();
    assertType(blockNode.getFirstFirstChild().getSecondChild().getTypeI()).isString();
    assertType(blockNode.getFirstFirstChild().getSecondChild().getFirstChild().getTypeI())
        .toStringIsEqualTo("IIterableResult<string>");
    assertType(blockNode.getSecondChild().getFirstFirstChild().getTypeI()).isString();
  }

  public void testTemplateString() {
    Node root = typeInfoCheckAndGetRoot("var /** number */ x = 1; var temp = `template ${x} str`;");

    Node template = root.getFirstFirstChild().getNext().getFirstChild();
    assertType(template.getTypeI()).isString();
    assertType(template.getFirstChild().getTypeI()).isString();
    assertType(template.getFirstFirstChild().getTypeI()).isString();
    assertType(template.getFirstFirstChild().getSecondChild().getTypeI()).isNumber();
  }

  public void testTaggedTemplate1() {
    Node root =
        typeInfoCheckAndGetRoot(
            LINE_JOINER.join(
                "function tag(/** !ITemplateArray */ strings, /** number */ a){",
                "  return ''",
                "}",
                "var n = 1;",
                "var /** string */ s = tag`template ${n} string`;"));

    Node templateLit = root.getFirstFirstChild();
    assertType(templateLit.getFirstChild().getTypeI()).toStringIsEqualTo("ITemplateArray");
    assertType(templateLit.getFirstFirstChild().getTypeI()).toStringIsEqualTo("ITemplateArray");
    assertType(templateLit.getFirstFirstChild().getFirstChild().getTypeI()).isString();

    Node rawTemplateExpr = templateLit.getNext();
    assertType(rawTemplateExpr.getFirstChild().getTypeI()).toStringIsEqualTo("Array<string>");
    assertType(rawTemplateExpr.getFirstFirstChild().getTypeI()).toStringIsEqualTo("Array<string>");
    assertType(rawTemplateExpr.getFirstFirstChild().getFirstChild().getTypeI())
        .toStringIsEqualTo("ITemplateArray");
    assertType(rawTemplateExpr.getFirstChild().getSecondChild().getTypeI())
        .toStringIsEqualTo("Array<string>");

    Node s = rawTemplateExpr.getNext().getNext().getNext();
    assertType(s.getFirstChild().getTypeI()).isString();
    assertType(s.getFirstFirstChild().getTypeI()).isString();
    assertType(s.getFirstFirstChild().getFirstChild().getTypeI())
        .toStringIsEqualTo("function(ITemplateArray,number):string");
    assertType(s.getFirstFirstChild().getSecondChild().getTypeI())
        .toStringIsEqualTo("ITemplateArray");
    assertType(s.getFirstFirstChild().getChildAtIndex(2).getTypeI()).isNumber();
  }

  public void testTaggedTemplate2() {
    Node root =
        typeInfoCheckAndGetRoot(
            LINE_JOINER.join(
                "function tag(/** !ITemplateArray */ strings){",
                "  return (function () { return 1; });",
                "}",
                "var g = tag`template string`;",
                "var r = g()"));

    Node templateLit = root.getFirstFirstChild();
    assertType(templateLit.getFirstChild().getTypeI()).toStringIsEqualTo("ITemplateArray");
    assertType(templateLit.getFirstFirstChild().getTypeI()).toStringIsEqualTo("ITemplateArray");
    assertType(templateLit.getFirstFirstChild().getFirstChild().getTypeI()).isString();

    Node rawTemplateExpr = templateLit.getNext();
    assertType(rawTemplateExpr.getFirstChild().getTypeI()).toStringIsEqualTo("Array<string>");
    assertType(rawTemplateExpr.getFirstFirstChild().getTypeI()).toStringIsEqualTo("Array<string>");
    assertType(rawTemplateExpr.getFirstFirstChild().getFirstChild().getTypeI())
        .toStringIsEqualTo("ITemplateArray");
    assertType(rawTemplateExpr.getFirstChild().getSecondChild().getTypeI())
        .toStringIsEqualTo("Array<string>");

    Node g = rawTemplateExpr.getNext().getNext();
    assertType(g.getFirstChild().getTypeI()).toStringIsEqualTo("function():number");
    assertType(g.getFirstFirstChild().getTypeI()).toStringIsEqualTo("function():number");
    assertType(g.getFirstFirstChild().getFirstChild().getTypeI())
        .toStringIsEqualTo("function(ITemplateArray):function():number");
    assertType(g.getFirstFirstChild().getSecondChild().getTypeI())
        .toStringIsEqualTo("ITemplateArray");

    Node r = g.getNext();
    assertType(r.getFirstChild().getTypeI()).isNumber();
    assertType(r.getFirstFirstChild().getTypeI()).isNumber();
    assertType(r.getFirstFirstChild().getFirstChild().getTypeI())
        .toStringIsEqualTo("function():number");
  }

  public void testExponent() {
    Node root = typeInfoCheckAndGetRoot("var x = 2**3;");

    Node nameX = root.getFirstFirstChild().getFirstChild();
    assertType(nameX.getTypeI()).isNumber();
    assertType(nameX.getFirstChild().getTypeI()).isNumber();
    assertType(nameX.getFirstFirstChild().getTypeI())
        .toStringIsEqualTo("Math.pow<|function(?,?):number|>{prototype: ?}");
    assertType(nameX.getFirstFirstChild().getFirstChild().getTypeI()).toStringIsEqualTo("Math");
    assertType(nameX.getFirstChild().getSecondChild().getTypeI()).isNumber();
  }

  public void testAssignExponent() {
    Node root = typeInfoCheckAndGetRoot("var x = 1; x **= 2;");

    Node assign = root.getFirstFirstChild().getNext().getFirstChild();
    assertType(assign.getTypeI()).isNumber();
    assertType(assign.getFirstChild().getTypeI()).isNumber();
    assertType(assign.getSecondChild().getTypeI()).isNumber();
    assertType(assign.getSecondChild().getFirstChild().getTypeI())
        .toStringIsEqualTo("Math.pow<|function(?,?):number|>{prototype: ?}");
    assertType(assign.getSecondChild().getFirstFirstChild().getTypeI()).toStringIsEqualTo("Math");
    assertType(assign.getSecondChild().getSecondChild().getTypeI()).isNumber();
  }

  private Node findDecl(Node n, String name) {
    Node result = find(n, new NodeUtil.MatchNameNode(name), Predicates.<Node>alwaysTrue());
    return result.getParent();
  }

  /** @return Whether the predicate is true for the node or any of its descendants. */
  private static Node find(Node node, Predicate<Node> pred, Predicate<Node> traverseChildrenPred) {
    if (pred.apply(node)) {
      return node;
    }

    if (!traverseChildrenPred.apply(node)) {
      return null;
    }

    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      Node result = find(c, pred, traverseChildrenPred);
      if (result != null) {
        return result;
      }
    }

    return null;
  }
}
