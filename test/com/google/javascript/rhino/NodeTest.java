/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Nick Santos
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.Asserts.assertThrows;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.serialization.NodeProperty;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.testing.TestErrorReporter;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NodeTest {
  @Test
  public void testValidatePropertiesForRoot() {
    final Node n = IR.root();
    assertThat(getMessagesFromValidateProperties(n)).isEmpty();

    // ROOT nodes shouldn't have properties, not even a source file
    n.setSourceFileForTesting("file.js");
    assertThat(getMessagesFromValidateProperties(n)).containsExactly("ROOT has properties");
  }

  private ImmutableList<String> getMessagesFromValidateProperties(Node n) {
    final ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    Consumer<String> violationMessageConsumer = listBuilder::add;
    n.validateProperties(violationMessageConsumer);

    return listBuilder.build();
  }

  @Test
  public void testValidatePropertiesForIsParenthesized() {
    Node n = IR.string("");
    n.setSourceFileForTesting("file.js"); // avoid error about missing source file

    n.setIsParenthesized(true);
    n.setToken(Token.STRING_KEY);
    assertThat(getMessagesFromValidateProperties(n))
        .containsExactly("non-expression is parenthesized");
  }

  @Test
  public void testValidatePropertiesForFunctionProperties() {
    Node n = IR.empty();
    n.setSourceFileForTesting("file.js"); // avoid error about missing source file

    // Change the Node type so we won't get thrown errors when we try to set the function-only
    // properties.
    n.setToken(Token.FUNCTION);
    n.setIsArrowFunction(true);
    n.setIsAsyncFunction(true);

    assertThat(getMessagesFromValidateProperties(n)).isEmpty();
    n.setToken(Token.EMPTY);
    assertThat(getMessagesFromValidateProperties(n))
        .containsExactly("invalid ARROW_FN prop", "invalid ASYNC_FN prop");
  }

  @Test
  public void testValidatePropertiesForSyntheticProperty() {
    Node n = IR.block();
    n.setSourceFileForTesting("file.js"); // avoid error about missing source file
    n.setIsSyntheticBlock(true);

    assertThat(getMessagesFromValidateProperties(n)).isEmpty();
    n.setToken(Token.EMPTY);
    assertThat(getMessagesFromValidateProperties(n)).containsExactly("invalid SYNTHETIC prop");
  }

  @Test
  public void testValidatePropertiesForColorFromCast() {
    Node n = IR.name("a");
    n.setSourceFileForTesting("file.js"); // avoid error about missing source file
    n.setColor(StandardColors.NUMBER);
    n.setColorFromTypeCast();

    assertThat(getMessagesFromValidateProperties(n)).isEmpty();
    n.setColor(null);
    assertThat(getMessagesFromValidateProperties(n))
        .containsExactly("COLOR_FROM_CAST with no Color");
  }

  @Test
  public void testValidatePropertiesForOptChain() {
    Node n = IR.empty();
    n.setSourceFileForTesting("file.js"); // avoid error about missing source file

    n.setToken(Token.OPTCHAIN_CALL);
    n.setIsOptionalChainStart(true);

    assertThat(getMessagesFromValidateProperties(n)).isEmpty();
    n.setToken(Token.OPTCHAIN_GETELEM);
    assertThat(getMessagesFromValidateProperties(n)).isEmpty();
    n.setToken(Token.OPTCHAIN_GETELEM);
    assertThat(getMessagesFromValidateProperties(n)).isEmpty();
    n.setToken(Token.EMPTY);
    assertThat(getMessagesFromValidateProperties(n))
        .containsExactly("START_OF_OPT_CHAIN on non-optional Node");
  }

  @Test
  public void testValidatePropertiesForConstVarFlags() {
    Node n = IR.name("a");
    n.setSourceFileForTesting("file.js"); // avoid error about missing source file
    n.setDeclaredConstantVar(true);
    n.setInferredConstantVar(true);

    assertThat(getMessagesFromValidateProperties(n)).isEmpty();
    n.setToken(Token.IMPORT_STAR);
    assertThat(getMessagesFromValidateProperties(n)).isEmpty();
    n.setToken(Token.STRINGLIT);
    assertThat(getMessagesFromValidateProperties(n)).containsExactly("invalid CONST_VAR_FLAGS");
  }

  @Test
  public void testLinenoCharnoNormal() {
    assertLinenoCharno(5, 6, 5, 6);
    assertLinenoCharno(456, 3423, 456, 3423);
    assertLinenoCharno(0, 0, 0, 0);
  }

  @Test
  public void testLinenoCharnoErroneous() {
    assertLinenoCharno(-5, 90, -1, -1);
    assertLinenoCharno(90, -1, -1, -1);
  }

  @Test
  public void testMergeOverflowGraciously() {
    assertLinenoCharno(89, 4096, 89, 4095);
  }

  private void assertLinenoCharno(int linenoIn, int charnoIn, int linenoOut, int charnoOut) {
    Node test = IR.block();
    test.setLinenoCharno(linenoIn, charnoIn);
    assertThat(test.getLineno()).isEqualTo(linenoOut);
    assertThat(test.getCharno()).isEqualTo(charnoOut);
  }

  @Test
  public void isEquivalentToConsidersStartOfOptionalChainProperty() {
    // `a?.b.c`
    Node singleSegmentOptChain =
        IR.continueOptChainGetprop(IR.startOptChainGetprop(IR.name("a"), "b"), "c");
    assertNode(singleSegmentOptChain).isEquivalentTo(singleSegmentOptChain.cloneTree());

    Node twoSegmentOptChain = singleSegmentOptChain.cloneTree();
    twoSegmentOptChain.setIsOptionalChainStart(true);
    assertNode(singleSegmentOptChain).isNotEquivalentTo(twoSegmentOptChain);
  }

  @Test
  public void isEquivalentToForFunctionsConsidersKindOfFunction() {
    Node normalFunction = IR.function(IR.name(""), IR.paramList(), IR.block());
    assertNode(normalFunction).isEquivalentTo(normalFunction.cloneTree());

    // normal vs async function
    Node asyncFunction = normalFunction.cloneTree();
    asyncFunction.setIsAsyncFunction(true);
    assertNode(asyncFunction)
        .isEquivalentTo(asyncFunction.cloneTree())
        .isNotEquivalentTo(normalFunction);

    // normal vs arrow function
    Node arrowFunction = normalFunction.cloneTree();
    arrowFunction.setIsArrowFunction(true);
    assertNode(arrowFunction)
        .isEquivalentTo(arrowFunction.cloneTree())
        .isNotEquivalentTo(normalFunction);

    // async arrow function vs async only vs arrow only
    Node asyncArrowFunction = arrowFunction.cloneTree();
    asyncArrowFunction.setIsAsyncFunction(true);
    assertNode(asyncArrowFunction)
        .isEquivalentTo(asyncArrowFunction.cloneTree())
        .isNotEquivalentTo(arrowFunction)
        .isNotEquivalentTo(asyncFunction);

    // normal vs generator function
    Node generatorFunction = normalFunction.cloneTree();
    generatorFunction.setIsGeneratorFunction(true);
    assertNode(generatorFunction)
        .isEquivalentTo(generatorFunction.cloneTree())
        .isNotEquivalentTo(normalFunction);

    // async generator function vs only async vs only generator
    Node asyncGeneratorFunction = generatorFunction.cloneTree();
    asyncGeneratorFunction.setIsAsyncFunction(true);
    assertNode(asyncGeneratorFunction)
        .isEquivalentTo(asyncGeneratorFunction.cloneTree())
        .isNotEquivalentTo(asyncFunction)
        .isNotEquivalentTo(generatorFunction);
  }

  @Test
  public void testIsEquivalentTo_withBoolean_isSame() {
    Node node1 = new Node(Token.LET);
    assertThat(node1.isEquivalentTo(node1)).isTrue();
  }

  @Test
  public void testIsEquivalentTo_withBoolean_isDifferent() {
    Node node1 = new Node(Token.LET);
    Node node2 = new Node(Token.VAR);
    assertThat(node1.isEquivalentTo(node2)).isFalse();
  }

  @Test
  public void testIsEquivalentTo_considersDifferentEsModuleExports() {
    Node exportAllFrom = new Node(Token.EXPORT); // export * from './other/module'
    exportAllFrom.putBooleanProp(Node.EXPORT_ALL_FROM, true);
    Node exportDefault = new Node(Token.EXPORT); // export default function foo() {
    exportDefault.putBooleanProp(Node.EXPORT_DEFAULT, true);
    Node simpleExport = new Node(Token.EXPORT); // export {x} or export const x = 0;

    assertThat(exportAllFrom.isEquivalentTo(exportDefault)).isFalse();
    assertThat(exportAllFrom.isEquivalentTo(simpleExport)).isFalse();
    assertThat(exportDefault.isEquivalentTo(simpleExport)).isFalse();
  }

  @Test
  public void testIsEquivalentTo_withSlashV_isDifferent() {
    Node node1 = Node.newString("\u000B");
    node1.putBooleanProp(Node.SLASH_V, true);
    Node node2 = Node.newString("\u000B");
    assertThat(node1.isEquivalentTo(node2)).isFalse();
  }

  @Test
  public void testIsEquivalentToNumber() {
    assertThat(Node.newNumber(1).isEquivalentTo(Node.newNumber(1))).isTrue();
    assertThat(Node.newNumber(1).isEquivalentTo(Node.newNumber(2))).isFalse();
  }

  @Test
  public void testNumberRejects_isNaN() {
    assertNumberNodeRejects(Double.NaN);
    assertNumberNodeRejects(-Double.NaN);
  }

  @Test
  public void testNumberRejects_negativeValues() {
    assertNumberNodeRejects(-1394793.114);
    assertNumberNodeRejects(-1.0);
    assertNumberNodeRejects(-0.0);
    assertNumberNodeRejects(Double.NEGATIVE_INFINITY);
  }

  private void assertNumberNodeRejects(double d) {
    assertThrows(Exception.class, () -> Node.newNumber(d));
    assertThrows(Exception.class, () -> Node.newNumber(0.0).setDouble(d));
  }

  @Test
  public void testBigintRejects_negativeValues() {
    assertBigIntNodeRejects(new BigInteger("-1394793"));
    assertBigIntNodeRejects(new BigInteger("-1"));
    assertThat(Node.newBigInt(new BigInteger("-0"))).isNotNull();
  }

  private void assertBigIntNodeRejects(BigInteger x) {
    assertThrows(Exception.class, () -> Node.newBigInt(x));
    assertThrows(Exception.class, () -> Node.newBigInt(BigInteger.ZERO).setBigInt(x));
  }

  @Test
  public void testIsEquivalentToBigInt() {
    assertThat(Node.newBigInt(BigInteger.ONE).isEquivalentTo(Node.newBigInt(BigInteger.ONE)))
        .isTrue();
    assertThat(Node.newBigInt(BigInteger.ONE).isEquivalentTo(Node.newBigInt(BigInteger.TEN)))
        .isFalse();
  }

  @Test
  public void testIsEquivalentToString() {
    assertThat(Node.newString("1").isEquivalentTo(Node.newString("1"))).isTrue();
    assertThat(Node.newString("1").isEquivalentTo(Node.newString("2"))).isFalse();
  }

  @Test
  public void testCheckTreeTypeAwareEqualsSame() {
    TestErrorReporter testErrorReporter = new TestErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(testErrorReporter);
    Node node1 = Node.newString(Token.NAME, "f");
    node1.setJSType(registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    Node node2 = Node.newString(Token.NAME, "f");
    node2.setJSType(registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    assertThat(node1.isEquivalentToTyped(node2)).isTrue();
    testErrorReporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCheckTreeTypeAwareEqualsSameNull() {
    Node node1 = Node.newString(Token.NAME, "f");
    Node node2 = Node.newString(Token.NAME, "f");
    assertThat(node1.isEquivalentToTyped(node2)).isTrue();
  }

  @Test
  public void testCheckTreeTypeAwareEqualsDifferent() {
    TestErrorReporter testErrorReporter = new TestErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(testErrorReporter);
    Node node1 = Node.newString(Token.NAME, "f");
    node1.setJSType(registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    Node node2 = Node.newString(Token.NAME, "f");
    node2.setJSType(registry.getNativeType(JSTypeNative.STRING_TYPE));
    assertThat(node1.isEquivalentToTyped(node2)).isFalse();
    testErrorReporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCheckTreeTypeAwareEqualsDifferentNull() {
    TestErrorReporter testErrorReporter = new TestErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(testErrorReporter);
    Node node1 = Node.newString(Token.NAME, "f");
    node1.setJSType(registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    Node node2 = Node.newString(Token.NAME, "f");
    assertThat(node1.isEquivalentToTyped(node2)).isFalse();
    testErrorReporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCheckTreeTypeAwareEqualsColorsSame() {
    Node node1 = Node.newString(Token.NAME, "f");
    node1.setColor(StandardColors.NUMBER);
    Node node2 = Node.newString(Token.NAME, "f");
    node2.setColor(StandardColors.NUMBER);
    assertThat(node1.isEquivalentToTyped(node2)).isTrue();
  }

  @Test
  public void testCheckTreeTypeAwareEqualsColorsSameNull() {
    Node node1 = Node.newString(Token.NAME, "f");
    Node node2 = Node.newString(Token.NAME, "f");
    assertThat(node1.isEquivalentToTyped(node2)).isTrue();
  }

  @Test
  public void testCheckTreeTypeAwareEqualsColorsDifferent() {
    Node node1 = Node.newString(Token.NAME, "f");
    node1.setColor(StandardColors.NUMBER);
    Node node2 = Node.newString(Token.NAME, "f");
    node2.setColor(StandardColors.STRING);
    assertThat(node1.isEquivalentToTyped(node2)).isFalse();
  }

  @Test
  public void testCheckTreeTypeAwareEqualsColorsDifferentNull() {
    Node node1 = Node.newString(Token.NAME, "f");
    node1.setColor(StandardColors.NUMBER);
    Node node2 = Node.newString(Token.NAME, "f");
    assertThat(node1.isEquivalentToTyped(node2)).isFalse();
  }

  @Test
  public void testIsQualifiedName() {
    assertThat(IR.name("a").isQualifiedName()).isTrue();
    assertThat(IR.name("$").isQualifiedName()).isTrue();
    assertThat(IR.name("_").isQualifiedName()).isTrue();
    assertThat(IR.getprop(IR.name("a"), "b").isQualifiedName()).isTrue();
    assertThat(IR.getprop(IR.thisNode(), "b").isQualifiedName()).isTrue();
    assertThat(IR.number(0).isQualifiedName()).isFalse();
    assertThat(IR.arraylit().isQualifiedName()).isFalse();
    assertThat(IR.objectlit().isQualifiedName()).isFalse();
    assertThat(IR.string("").isQualifiedName()).isFalse();
    assertThat(IR.getelem(IR.name("a"), IR.string("b")).isQualifiedName()).isFalse();
    assertThat( // a[b].c
            IR.getprop(IR.getelem(IR.name("a"), IR.string("b")), "c").isQualifiedName())
        .isFalse();
    assertThat( // a.b[c]
            IR.getelem(IR.getprop(IR.name("a"), "b"), IR.string("c")).isQualifiedName())
        .isFalse();
    assertThat(IR.call(IR.name("a")).isQualifiedName()).isFalse();
    assertThat( // a().b
            IR.getprop(IR.call(IR.name("a")), "b").isQualifiedName())
        .isFalse();
    assertThat( // (a.b)()
            IR.call(IR.getprop(IR.name("a"), "b")).isQualifiedName())
        .isFalse();
    assertThat(IR.string("a").isQualifiedName()).isFalse();
    assertThat(IR.regexp(IR.string("x")).isQualifiedName()).isFalse();
    assertThat(new Node(Token.INC, IR.name("x")).isQualifiedName()).isFalse();
  }

  @Test
  public void testMatchesQualifiedName1() {
    assertThat(IR.name("a").matchesQualifiedName("a")).isTrue();
    assertThat(IR.name("a").matchesQualifiedName("ab")).isFalse();
    assertThat(IR.name("a").matchesQualifiedName("a.b")).isFalse();
    assertThat(IR.name("a").matchesQualifiedName(".b")).isFalse();
    assertThat(IR.name("a").matchesQualifiedName("a.")).isFalse();

    assertThat(qname("a.b").matchesQualifiedName("a")).isFalse();
    assertThat(qname("a.b").matchesQualifiedName("a.b")).isTrue();
    assertThat(qname("a.b").matchesQualifiedName("a.bc")).isFalse();
    assertThat(qname("a.b").matchesQualifiedName(".b")).isFalse();
    assertThat(qname("a.b").matchesQualifiedName("this.b")).isFalse();

    assertThat(qname("this").matchesQualifiedName("this")).isTrue();
    assertThat(qname("this").matchesQualifiedName("thisx")).isFalse();

    assertThat(qname("this.b").matchesQualifiedName("a")).isFalse();
    assertThat(qname("this.b").matchesQualifiedName("a.b")).isFalse();
    assertThat(qname("this.b").matchesQualifiedName(".b")).isFalse();
    assertThat(qname("this.b").matchesQualifiedName("a.")).isFalse();
    assertThat(qname("this.b").matchesQualifiedName("super.b")).isFalse();
    assertThat(qname("this.b").matchesQualifiedName("this.b")).isTrue();

    assertThat(qname("super").matchesQualifiedName("super")).isTrue();
    assertThat(qname("super").matchesQualifiedName("superx")).isFalse();

    assertThat(qname("super.b").matchesQualifiedName("a")).isFalse();
    assertThat(qname("super.b").matchesQualifiedName("a.b")).isFalse();
    assertThat(qname("super.b").matchesQualifiedName(".b")).isFalse();
    assertThat(qname("super.b").matchesQualifiedName("a.")).isFalse();
    assertThat(qname("super.b").matchesQualifiedName("this.b")).isFalse();
    assertThat(qname("super.b").matchesQualifiedName("super.b")).isTrue();

    assertThat(qname("a.b.c").matchesQualifiedName("a.b.c")).isTrue();
    assertThat(qname("a.b.c").matchesQualifiedName("a.b.c")).isTrue();

    assertThat(IR.importStar("a").matchesQualifiedName("a")).isTrue();
    assertThat(IR.importStar("a").matchesQualifiedName("b")).isFalse();

    assertThat(IR.number(0).matchesQualifiedName("a.b")).isFalse();
    assertThat(IR.arraylit().matchesQualifiedName("a.b")).isFalse();
    assertThat(IR.objectlit().matchesQualifiedName("a.b")).isFalse();
    assertThat(IR.string("").matchesQualifiedName("a.b")).isFalse();
    assertThat(IR.getelem(IR.name("a"), IR.string("b")).matchesQualifiedName("a.b")).isFalse();
    assertThat( // a[b].c
            IR.getprop(IR.getelem(IR.name("a"), IR.string("b")), "c").matchesQualifiedName("a.b.c"))
        .isFalse();
    assertThat( // a.b[c]
            IR.getelem(IR.getprop(IR.name("a"), "b"), IR.string("c")).matchesQualifiedName("a.b.c"))
        .isFalse();
    assertThat(IR.call(IR.name("a")).matchesQualifiedName("a")).isFalse();
    assertThat( // a().b
            IR.getprop(IR.call(IR.name("a")), "b").matchesQualifiedName("a.b"))
        .isFalse();
    assertThat( // (a.b)()
            IR.call(IR.getprop(IR.name("a"), "b")).matchesQualifiedName("a.b"))
        .isFalse();
    assertThat(IR.string("a").matchesQualifiedName("a")).isFalse();
    assertThat(IR.regexp(IR.string("x")).matchesQualifiedName("x")).isFalse();
    assertThat(new Node(Token.INC, IR.name("x")).matchesQualifiedName("x")).isFalse();
  }

  @Test
  public void testMatchesQualifiedName2() {
    assertThat(IR.name("a").matchesQualifiedName(qname("a"))).isTrue();
    assertThat(IR.name("a").matchesQualifiedName(qname("a.b"))).isFalse();

    assertThat(qname("a.b").matchesQualifiedName(qname("a"))).isFalse();
    assertThat(qname("a.b").matchesQualifiedName(qname("a.b"))).isTrue();
    assertThat(qname("a.b").matchesQualifiedName(qname(".b"))).isFalse();
    assertThat(qname("a.b").matchesQualifiedName(qname("this.b"))).isFalse();

    assertThat(qname("this.b").matchesQualifiedName(qname("a"))).isFalse();
    assertThat(qname("this.b").matchesQualifiedName(qname("a.b"))).isFalse();
    assertThat(qname("this.b").matchesQualifiedName(qname("super.b"))).isFalse();
    assertThat(qname("this.b").matchesQualifiedName(qname("this.b"))).isTrue();

    assertThat(qname("super.b").matchesQualifiedName(qname("a"))).isFalse();
    assertThat(qname("super.b").matchesQualifiedName(qname("a.b"))).isFalse();
    assertThat(qname("super.b").matchesQualifiedName(qname("this.b"))).isFalse();
    assertThat(qname("super.b").matchesQualifiedName(qname("super.b"))).isTrue();

    assertThat(qname("a.b.c").matchesQualifiedName(qname("a.b.c"))).isTrue();
    assertThat(qname("a.b.c").matchesQualifiedName(qname("a.b.c"))).isTrue();

    assertThat(IR.number(0).matchesQualifiedName(qname("a.b"))).isFalse();
    assertThat(IR.arraylit().matchesQualifiedName(qname("a.b"))).isFalse();
    assertThat(IR.objectlit().matchesQualifiedName(qname("a.b"))).isFalse();
    assertThat(IR.string("").matchesQualifiedName(qname("a.b"))).isFalse();
    assertThat(IR.getelem(IR.name("a"), IR.string("b")).matchesQualifiedName(qname("a.b")))
        .isFalse();
    assertThat( // a[b].c
            IR.getprop(IR.getelem(IR.name("a"), IR.string("b")), "c")
                .matchesQualifiedName(qname("a.b.c")))
        .isFalse();
    assertThat( // a.b[c]
            IR.getelem(IR.getprop(IR.name("a"), "b"), IR.string("c")).matchesQualifiedName("a.b.c"))
        .isFalse();
    assertThat(IR.call(IR.name("a")).matchesQualifiedName(qname("a"))).isFalse();
    assertThat( // a().b
            IR.getprop(IR.call(IR.name("a")), "b").matchesQualifiedName(qname("a.b")))
        .isFalse();
    assertThat( // (a.b)()
            IR.call(IR.getprop(IR.name("a"), "b")).matchesQualifiedName(qname("a.b")))
        .isFalse();
    assertThat(IR.string("a").matchesQualifiedName(qname("a"))).isFalse();
    assertThat(IR.regexp(IR.string("x")).matchesQualifiedName(qname("x"))).isFalse();
    assertThat(new Node(Token.INC, IR.name("x")).matchesQualifiedName(qname("x"))).isFalse();
  }

  @Test
  public void testMatchesName() {
    // Empty string are treat as unique.
    assertThat(IR.name("").matchesName("")).isFalse();

    assertThat(IR.name("a").matchesName("a")).isTrue();
    assertThat(IR.name("a").matchesName("a.b")).isFalse();
    assertThat(IR.name("a").matchesName("")).isFalse();

    assertThat(IR.thisNode().matchesName("this")).isFalse();
    assertThat(IR.superNode().matchesName("super")).isFalse();
  }

  @Test
  public void testMatchesNameNodes() {
    assertThat(IR.name("a").matchesName(qname("a"))).isTrue();
    assertThat(IR.name("a").matchesName(qname("a.b"))).isFalse();

    assertThat(IR.thisNode().matchesName(qname("this"))).isFalse();
    assertThat(IR.superNode().matchesName(qname("super"))).isFalse();
  }

  public static Node qname(String name) {
    int endPos = name.indexOf('.');
    if (endPos == -1) {
      return IR.name(name);
    }
    Node node;
    String nodeName = name.substring(0, endPos);
    if ("this".equals(nodeName)) {
      node = IR.thisNode();
    } else if ("super".equals(nodeName)) {
      node = IR.superNode();
    } else {
      node = IR.name(nodeName);
    }
    int startPos;
    do {
      startPos = endPos + 1;
      endPos = name.indexOf('.', startPos);
      String part = (endPos == -1 ? name.substring(startPos) : name.substring(startPos, endPos));
      node = IR.getprop(node, part);
    } while (endPos != -1);

    return node;
  }

  @Test
  public void testCloneAnnontations() {
    Node n = getVarRef("a");
    n.setLength(1);
    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isFalse();
    n.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    Node nodeClone = n.cloneNode();
    assertThat(nodeClone.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();
    assertThat(nodeClone.getLength()).isEqualTo(1);
  }

  @Test
  public void testCloneValues() {
    Node number = Node.newNumber(100.0);
    assertThat(number.cloneNode().getDouble()).isEqualTo(100.0);

    Node string = Node.newString(new String("a"));
    assertThat(string.cloneNode().getString()).isSameInstanceAs(string.getString());

    Node template = Node.newTemplateLitString(new String("a"), new String("b"));
    assertThat(template.cloneNode().getCookedString()).isSameInstanceAs(template.getCookedString());
    assertThat(template.cloneNode().getRawString()).isSameInstanceAs(template.getRawString());

    Node bigint = Node.newBigInt(new BigInteger("100"));
    assertThat(bigint.cloneNode().getBigInt()).isSameInstanceAs(bigint.getBigInt());
  }

  @Test
  public void testSharedProps1() {
    Node n = getCall("A");
    n.setSideEffectFlags(5);
    Node m = new Node(Token.TRUE);
    m.clonePropsFrom(n);
    assertThat(n.getPropListHeadForTesting()).isEqualTo(m.getPropListHeadForTesting());
    assertThat(n.getSideEffectFlags()).isEqualTo(5);
    assertThat(m.getSideEffectFlags()).isEqualTo(5);
  }

  @Test
  public void testSharedProps2() {
    Node n = getCall("A");
    n.setSideEffectFlags(5);
    Node m = getCall("B");
    m.clonePropsFrom(n);

    n.setSideEffectFlags(6);
    assertThat(n.getSideEffectFlags()).isEqualTo(6);
    assertThat(m.getSideEffectFlags()).isEqualTo(5);
    assertThat(m.getPropListHeadForTesting() == n.getPropListHeadForTesting()).isFalse();

    m.setSideEffectFlags(7);
    assertThat(n.getSideEffectFlags()).isEqualTo(6);
    assertThat(m.getSideEffectFlags()).isEqualTo(7);
  }

  @Test
  public void testSharedProps3() {
    Node n = getCall("A");
    n.setSideEffectFlags(2);
    n.putBooleanProp(Node.INCRDECR_PROP, true);
    Node m = new Node(Token.TRUE);
    m.clonePropsFrom(n);
    n.setSideEffectFlags(4);

    assertThat(n.getSideEffectFlags()).isEqualTo(4);
    assertThat(m.getSideEffectFlags()).isEqualTo(2);
  }

  @Test
  public void testBooleanProp() {
    Node n = getVarRef("a");

    n.putBooleanProp(Node.IS_CONSTANT_NAME, false);

    assertThat(n.lookupProperty(Node.IS_CONSTANT_NAME)).isNull();
    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isFalse();

    n.putBooleanProp(Node.IS_CONSTANT_NAME, true);

    assertThat(n.lookupProperty(Node.IS_CONSTANT_NAME)).isNotNull();
    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    n.putBooleanProp(Node.IS_CONSTANT_NAME, false);

    assertThat(n.lookupProperty(Node.IS_CONSTANT_NAME)).isNull();
    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isFalse();
  }

  // Verify that annotations on cloned nodes are properly handled.
  @Test
  public void testCloneAnnontations2() {
    Node n = getVarRef("a");
    n.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    Node nodeClone = n.cloneNode();
    assertThat(nodeClone.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    assertThat(nodeClone.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();
  }

  @Test
  public void testSerializeProperties() {
    Node node = IR.function(IR.name(""), IR.paramList(), IR.block());
    node.setIsAsyncFunction(true);
    node.setIsGeneratorFunction(true);
    EnumSet<NodeProperty> result = node.serializeProperties();
    assertThat(result).containsExactly(NodeProperty.GENERATOR_FN, NodeProperty.ASYNC_FN);
  }

  @Test
  public void testSerializeProperties_isDeclaredConstant() {
    Node node = new Node(Token.NAME);
    node.setDeclaredConstantVar(true);
    EnumSet<NodeProperty> result = node.serializeProperties();
    assertThat(result).containsExactly(NodeProperty.IS_DECLARED_CONSTANT);
  }

  @Test
  public void testSerializeProperties_isInferredConstant() {
    Node node = new Node(Token.NAME);
    node.setInferredConstantVar(true);
    EnumSet<NodeProperty> result = node.serializeProperties();
    assertThat(result).containsExactly(NodeProperty.IS_INFERRED_CONSTANT);
  }

  @Test
  public void testSerializeProperties_untranslatableRhinoProp() {
    Node node = getCall("A");
    node.setSideEffectFlags(2);
    EnumSet<NodeProperty> result = node.serializeProperties();
    // Rhino node prop SIDE_EFFECT_FLAGS does not have a corresponding NodeProperty
    assertThat(node.getSideEffectFlags()).isEqualTo(2);
    assertThat(result).isEmpty();
  }

  @Test
  public void testSerializeProperties_typeBeforeCast() {
    TestErrorReporter testErrorReporter = new TestErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(testErrorReporter);
    Node node = Node.newString(Token.NAME, "f");
    node.setJSTypeBeforeCast(registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    EnumSet<NodeProperty> result = node.serializeProperties();
    // Special case: Rhino node prop TYPE_BEFORE_CAST is converted to NodeProperty.COLOR_FROM_CAST
    assertThat(result).containsExactly(NodeProperty.COLOR_FROM_CAST);
  }

  @Test
  public void testGetIndexOfChild() {
    Node assign = getAssignExpr("b", "c");
    assertThat(assign.getChildCount()).isEqualTo(2);

    Node firstChild = assign.getFirstChild();
    Node secondChild = firstChild.getNext();
    assertThat(secondChild).isNotNull();

    assertThat(assign.getIndexOfChild(firstChild)).isEqualTo(0);
    assertThat(assign.getIndexOfChild(secondChild)).isEqualTo(1);
    assertThat(assign.getIndexOfChild(assign)).isEqualTo(-1);
  }

  @Test
  public void testSrcrefIfMissing() {
    Node assign = getAssignExpr("b", "c");
    assign.setLinenoCharno(99, 0);
    assign.setSourceFileForTesting("foo.js");

    Node lhs = assign.getFirstChild();
    lhs.srcrefIfMissing(assign);
    assertNode(lhs).hasLineno(99);
    assertThat(lhs.getSourceFileName()).isEqualTo("foo.js");

    assign.setLinenoCharno(101, 0);
    assign.setSourceFileForTesting("bar.js");
    lhs.srcrefIfMissing(assign);
    assertNode(lhs).hasLineno(99);
    assertThat(lhs.getSourceFileName()).isEqualTo("foo.js");
  }

  @Test
  public void testSrcref() {
    Node assign = getAssignExpr("b", "c");
    assign.setLinenoCharno(99, 0);
    assign.setSourceFileForTesting("foo.js");

    Node lhs = assign.getFirstChild();
    lhs.srcref(assign);
    assertNode(lhs).hasLineno(99);
    assertThat(lhs.getSourceFileName()).isEqualTo("foo.js");

    assign.setLinenoCharno(101, 0);
    assign.setSourceFileForTesting("bar.js");
    lhs.srcref(assign);
    assertNode(lhs).hasLineno(101);
    assertThat(lhs.getSourceFileName()).isEqualTo("bar.js");
  }

  @Test
  public void testInvalidSourceOffset() {
    Node string = Node.newString("a");

    string.setLinenoCharno(-1, -1);
    assertThat(string.getSourceOffset()).isLessThan(0);

    string.setSourceFileForTesting("foo.js");
    assertThat(string.getSourceOffset()).isLessThan(0);
  }

  @Test
  public void testQualifiedName() {
    assertThat(IR.name("").getQualifiedName()).isNull();
    assertThat(IR.name("a").getQualifiedName()).isEqualTo("a");
    assertThat(IR.thisNode().getQualifiedName()).isEqualTo("this");
    assertThat(IR.superNode().getQualifiedName()).isEqualTo("super");
    assertThat(IR.getprop(IR.name("a"), "b").getQualifiedName()).isEqualTo("a.b");
    assertThat(IR.getprop(IR.thisNode(), "b").getQualifiedName()).isEqualTo("this.b");
    assertThat(IR.getprop(IR.superNode(), "b").getQualifiedName()).isEqualTo("super.b");
    assertThat(IR.getprop(IR.call(IR.name("a")), "b").getQualifiedName()).isNull();
  }

  @Test
  public void testJSDocInfoClone() {
    Node original = IR.var(IR.name("varName"));
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordType(new JSTypeExpression(IR.name("TypeName"), "blah"));
    JSDocInfo info = builder.build();
    original.getFirstChild().setJSDocInfo(info);

    // By default the JSDocInfo and JSTypeExpression objects are not cloned
    Node clone = original.cloneTree();
    assertThat(clone.getFirstChild().getJSDocInfo())
        .isSameInstanceAs(original.getFirstChild().getJSDocInfo());
    assertThat(clone.getFirstChild().getJSDocInfo().getType())
        .isSameInstanceAs(original.getFirstChild().getJSDocInfo().getType());
    assertThat(clone.getFirstChild().getJSDocInfo().getType().getRoot())
        .isSameInstanceAs(original.getFirstChild().getJSDocInfo().getType().getRoot());

    // If requested the JSDocInfo and JSTypeExpression objects are cloned.
    // This is required because compiler classes are modifying the type expressions in place
    clone = original.cloneTree(true);
    assertThat(clone.getFirstChild().getJSDocInfo())
        .isNotSameInstanceAs(original.getFirstChild().getJSDocInfo());
    assertThat(clone.getFirstChild().getJSDocInfo().getType())
        .isNotSameInstanceAs(original.getFirstChild().getJSDocInfo().getType());
    assertThat(clone.getFirstChild().getJSDocInfo().getType().getRoot())
        .isNotSameInstanceAs(original.getFirstChild().getJSDocInfo().getType().getRoot());
  }

  @Test
  public void testAddChildToFrontWithSingleNode() {
    Node root = new Node(Token.SCRIPT);
    Node nodeToAdd = new Node(Token.SCRIPT);

    root.addChildToFront(nodeToAdd);

    assertThat(nodeToAdd.getParent()).isEqualTo(root);
    assertThat(nodeToAdd).isEqualTo(root.getFirstChild());
    assertThat(nodeToAdd).isEqualTo(root.getLastChild());
    assertThat(nodeToAdd.getNext()).isNull();
  }

  @Test
  public void testAddChildToFrontWithLargerTree() {
    Node left = Node.newString("left");
    Node mid = Node.newString("mid");
    Node right = Node.newString("right");
    Node root = new Node(Token.SCRIPT, left, mid, right);
    Node nodeToAdd = new Node(Token.SCRIPT);

    root.addChildToFront(nodeToAdd);

    assertThat(nodeToAdd.getParent()).isEqualTo(root);
    assertThat(nodeToAdd).isEqualTo(root.getFirstChild());
    assertThat(nodeToAdd.getPrevious()).isNull();
    assertThat(nodeToAdd.getNext()).isEqualTo(left);
    assertThat(left.getPrevious()).isEqualTo(nodeToAdd);
  }

  @Test
  public void testDetach1() {
    Node left = Node.newString("left");
    Node mid = Node.newString("mid");
    Node right = Node.newString("right");
    Node root = new Node(Token.SCRIPT, left, mid, right);

    assertThat(mid.getParent()).isEqualTo(root);
    assertThat(mid.getPrevious()).isEqualTo(left);
    assertThat(mid.getNext()).isEqualTo(right);

    mid.detach();

    assertThat(mid.getParent()).isNull();
    assertThat(mid.getNext()).isNull();

    assertThat(right.getPrevious()).isEqualTo(left);
    assertThat(left.getNext()).isEqualTo(right);
  }

  @Test
  public void testGetAncestors() {
    Node grandparent = new Node(Token.ROOT);
    Node parent = new Node(Token.PLACEHOLDER1);
    Node node = new Node(Token.PLACEHOLDER2);

    grandparent.addChildToFront(parent);
    parent.addChildToFront(node);

    assertThat(node.getAncestors()).containsExactly(parent, grandparent);
  }

  @Test
  public void testGetAncestors_empty() {
    Node node = new Node(Token.ROOT);
    assertThat(node.getAncestors()).isEmpty();
  }

  @Test
  public void testTrailingComma() {
    Node list = new Node(Token.ARRAYLIT);
    list.setTrailingComma(true);
    assertNode(list).hasTrailingComma();
  }

  private static Node getVarRef(String name) {
    return Node.newString(Token.NAME, name);
  }

  private static Node getAssignExpr(String name1, String name2) {
    return new Node(Token.ASSIGN, getVarRef(name1), getVarRef(name2));
  }

  private static Node getCall(String name1) {
    return new Node(Token.CALL, getVarRef(name1));
  }
}
