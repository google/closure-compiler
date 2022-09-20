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
 *   Bob Jervis
 *   Google Inc.
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

package com.google.javascript.rhino.testing;

import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.javascript.jscomp.testing.ColorSubject.colors;
import static com.google.javascript.rhino.testing.TypeSubject.types;

import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.javascript.jscomp.testing.ColorSubject;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.nullness.Nullable;

/**
 * A Truth Subject for the Node class. Usage:
 *
 * <pre>
 *   import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
 *   ...
 *   assertNode(node1).isEqualTo(node2);
 *   assertNode(node1).hasType(Token.FUNCTION);
 * </pre>
 */
public final class NodeSubject extends Subject {

  private final Node actual;
  private Function<Node, String> serializer;

  @CheckReturnValue
  public static NodeSubject assertNode(Node node) {
    return assertAbout(nodes()).that(node);
  }

  public static Subject.Factory<NodeSubject, Node> nodes() {
    return NodeSubject::new;
  }

  private NodeSubject(FailureMetadata failureMetadata, Node node) {
    super(failureMetadata, node);
    this.actual = node;
  }

  /**
   * Specify a function to use when rendering {@code Node}s into assertion failure messages.
   *
   * <p>A common choice of serializer is {@link Compiler::toSource}, to as render JavaScript code.
   */
  @CanIgnoreReturnValue
  public NodeSubject usingSerializer(Function<Node, String> serializer) {
    this.serializer = serializer;
    return this;
  }

  @Override
  public void isEqualTo(Object expected) {
    throw new UnsupportedOperationException("Use an overload with a declared type.");
  }

  /**
   * Compare the node-trees of actual and expected.
   *
   * <p>The per-node comparison ignores:
   *
   * <ul>
   *   <li>Types
   *   <li>JSDoc
   *   <li>Side-effects
   * </ul>
   */
  // TODO(nickreid): This isn't really equality based. Use a different name.
  public void isEqualTo(Node expected) {
    isEqualToInternal(expected, /* checkJsdoc= */ false);
  }

  /**
   * Compare the node-trees of actual and expected.
   *
   * <p>The per-node comparison ignores:
   *
   * <ul>
   *   <li>Types
   *   <li>Side-effects
   * </ul>
   */
  // TODO(nickreid): This isn't really equality based. Use a different name.
  public void isEqualIncludingJsDocTo(Node expected) {
    isEqualToInternal(expected, /* checkJsdoc= */ true);
  }

  // TODO(nickreid): This isn't really equality based. Use a different name.
  public void isEqualToInternal(Node expected, boolean checkJsdoc) {
    isNotNull();
    assertNode(expected).isNotNull();

    NodeMismatch mismatch = findFirstMismatch(actual, expected, checkJsdoc);
    if (mismatch == null) {
      return;
    }

    ArrayList<Fact> facts = new ArrayList<>();
    final String expectedOutputJs = serializeNode(expected);
    final String actualOutputJs = serializeNode(actual);
    if (expectedOutputJs.equals(actualOutputJs)) {
      // The output code looks identical, so diff the AST instead to show what properties
      // are different.
      facts.addAll(
          new TextDiffFactsBuilder("AST diff")
              .expectedText(mismatch.expected.toStringTree())
              .actualText(mismatch.actual.toStringTree())
              .build());
    } else {
      facts.addAll(
          new TextDiffFactsBuilder("JS diff")
              .expectedText(expectedOutputJs)
              .actualText(actualOutputJs)
              .build());
    }
    if (checkJsdoc) {
      final String expectedJSDoc = jsdocToStringNullsafe(mismatch.expected.getJSDocInfo());
      final String actualJSDoc = jsdocToStringNullsafe(mismatch.actual.getJSDocInfo());
      if (!Objects.equals(expectedJSDoc, actualJSDoc)) {
        facts.addAll(
            new TextDiffFactsBuilder("JSDoc diff")
                .expectedText(expectedJSDoc)
                .actualText(actualJSDoc)
                .build());
      }
    }
    failWithoutActual(simpleFact("Node tree inequality"), facts.toArray(new Fact[0]));
  }

  @CanIgnoreReturnValue
  public NodeSubject isEquivalentTo(Node other) {
    check("isEquivalentTo(%s)", other).that(actual.isEquivalentTo(other)).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isNotEquivalentTo(Node other) {
    check("isEquivalentTo(%s)", other).that(actual.isEquivalentTo(other)).isFalse();
    return this;
  }

  public TypeSubject hasJSTypeThat() {
    return check("getJSType()").about(types()).that(actual.getJSTypeRequired());
  }

  public ColorSubject hasColorThat() {
    return check("getColor()").about(colors()).that(actual.getColor());
  }

  public void hasType(Token type) {
    hasToken(type);
  }

  @CanIgnoreReturnValue
  public NodeSubject hasToken(Token token) {
    check("getToken()").that(actual.getToken()).isEqualTo(token);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isString(String value) {
    check("getToken()").that(actual.getToken()).isEqualTo(Token.STRINGLIT);
    check("getString()").that(actual.getString()).isEqualTo(value);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isName(String name) {
    check("isName()").that(actual.isName()).isTrue();
    check("getString()").that(actual.getString()).isEqualTo(name);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isNumber(int value) {
    return isNumber((double) value);
  }

  @CanIgnoreReturnValue
  public NodeSubject isNumber(double value) {
    check("isNumber()").that(actual.isNumber()).isTrue();
    check("getNumber()").that(actual.getDouble()).isEqualTo(value);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isBigInt(BigInteger value) {
    check("isBigInt()").that(actual.isBigInt()).isTrue();
    check("getBigInt()").that(actual.getBigInt()).isEqualTo(value);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isAssign() {
    check("isAssign()").that(actual.isAssign()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isNullNode() {
    check("isNull()").that(actual.isNull()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isThis() {
    check("isThis()").that(actual.isThis()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isSuper() {
    check("isSuper()").that(actual.isSuper()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isFunction() {
    hasToken(Token.FUNCTION);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isArrowFunction() {
    check("isArrowFunction()").that(actual.isArrowFunction()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isStatic() {
    check("isStatic()").that(actual.isStaticMember()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject hasTrailingComma() {
    check("hasTrailingComma()").that(actual.hasTrailingComma()).isTrue();
    return this;
  }

  /**
   * indicates whether the node we are asserting is the start of an optional chain e.g. `a?.b` of
   * `a?.b.c`
   */
  @CanIgnoreReturnValue
  public NodeSubject isOptionalChainStart() {
    check("isOptionalChainStart()").that(actual.isOptionalChainStart()).isTrue();
    return this;
  }

  /**
   * indicates whether the node we are asserting is the start of an optional chain e.g. `b.c` of
   * `a?.b.c`
   */
  @CanIgnoreReturnValue
  public NodeSubject isNotOptionalChainStart() {
    check("isOptionalChainStart()").that(actual.isOptionalChainStart()).isFalse();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isParamList() {
    hasToken(Token.PARAM_LIST);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isCall() {
    check("isCall()").that(actual.isCall()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isFreeCall() {
    check("callable")
        .that(actual.isCall() || actual.isOptChainCall() || actual.isTaggedTemplateLit())
        .isTrue();
    check("getBooleanProp(Node.FREE_CALL)").that(actual.getBooleanProp(Node.FREE_CALL)).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isNotFreeCall() {
    check("getBooleanProp(Node.FREE_CALL)").that(actual.getBooleanProp(Node.FREE_CALL)).isFalse();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isConst() {
    check("isConst()").that(actual.isConst()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isVar() {
    check("isVar()").that(actual.isVar()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isGetProp() {
    check("isGetProp()").that(actual.isGetProp()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isBlock() {
    check("isBlock()").that(actual.isBlock()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isObjectLit() {
    check("isObjectLit()").that(actual.isObjectLit()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isScript() {
    check("isScript()").that(actual.isScript()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isReturn() {
    check("isReturn()").that(actual.isReturn()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isExprResult() {
    check("isExprResult()").that(actual.isExprResult()).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject isMemberFunctionDef(String name) {
    check("isMemberFunction()").that(actual.isMemberFunctionDef()).isTrue();
    check("getString()").that(actual.getString()).isEqualTo(name);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject matchesName(String qname) {
    check("matchesName(%s)", qname).that(actual.matchesName(qname)).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject matchesQualifiedName(String qname) {
    check("matchesQualifiedName(%s)", qname).that(actual.matchesQualifiedName(qname)).isTrue();
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject hasSourceFileName(String sourceFileName) {
    check("getSourceFileName()").that(actual.getSourceFileName()).isEqualTo(sourceFileName);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject hasCharno(int charno) {
    check("getCharno()").that(actual.getCharno()).isEqualTo(charno);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject hasLineno(int lineno) {
    check("getLineno()").that(actual.getLineno()).isEqualTo(lineno);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject hasLength(int length) {
    check("getLength()").that(actual.getLength()).isEqualTo(length);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject hasEqualSourceInfoTo(Node other) {
    return hasSourceFileName(other.getSourceFileName())
        .hasLineno(other.getLineno())
        .hasCharno(other.getCharno())
        .hasLength(other.getLength());
  }

  @CanIgnoreReturnValue
  public NodeSubject isIndexable(boolean isIndexable) {
    check("isIndexable()").that(actual.isIndexable()).isEqualTo(isIndexable);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject hasOriginalName(String originalName) {
    check("getOriginalName()").that(actual.getOriginalName()).isEqualTo(originalName);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject hasChildren(boolean hasChildren) {
    check("hasChildren()").that(actual.hasChildren()).isEqualTo(hasChildren);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject hasXChildren(int numChildren) {
    check("getChildCount()").that(actual.getChildCount()).isEqualTo(numChildren);
    return this;
  }

  @CanIgnoreReturnValue
  public NodeSubject hasNoChildren() {
    return hasXChildren(0);
  }

  @CanIgnoreReturnValue
  public NodeSubject hasOneChild() {
    return hasXChildren(1);
  }

  public NodeSubject hasOneChildThat() {
    hasOneChild();
    return assertNode(actual.getOnlyChild());
  }

  public NodeSubject hasFirstChildThat() {
    hasChildren(true);
    return assertNode(actual.getFirstChild());
  }

  public NodeSubject hasSecondChildThat() {
    check("getChildCount()").that(actual.getChildCount()).isAtLeast(2);
    return assertNode(actual.getSecondChild());
  }

  public NodeSubject hasLastChildThat() {
    hasChildren(true);
    return assertNode(actual.getLastChild());
  }

  @CanIgnoreReturnValue
  public NodeSubject isFromExterns() {
    check("isFromExterns()").that(actual.isFromExterns()).isTrue();
    return this;
  }

  @CheckReturnValue
  public StringSubject hasStringThat() {
    return check("getString()").that(actual.getString());
  }

  @CheckReturnValue
  public StringSubject hasOriginalNameThat() {
    return check("getOriginalName()").that(actual.getOriginalName());
  }

  @Override
  protected String actualCustomStringRepresentation() {
    return serializeNode(actual);
  }

  /**
   * Compare the given node-trees recursively and return the first pair of nodes that differs doing
   * a pre-order traversal.
   *
   * @param jsDoc Whether to check for differences in JSDoc.
   */
  private static @Nullable NodeMismatch findFirstMismatch(
      Node actual, Node expected, boolean jsDoc) {
    if (!actual.isEquivalentTo(
        expected, /* compareType= */ false, /* recurse= */ false, jsDoc, /* sideEffect= */ false)) {
      return new NodeMismatch(actual, expected);
    }

    // `isEquivalentTo` confirms that the number of children is the same.
    Node actualChild = actual.getFirstChild();
    Node expectedChild = expected.getFirstChild();
    while (actualChild != null) {
      NodeMismatch mismatch = findFirstMismatch(actualChild, expectedChild, jsDoc);
      if (mismatch != null) {
        return mismatch;
      }

      actualChild = actualChild.getNext();
      expectedChild = expectedChild.getNext();
    }

    return null;
  }

  /** A pair of nodes that were expected to match in some way but didn't. */
  private static final class NodeMismatch {
    final Node actual;
    final Node expected;

    NodeMismatch(Node actual, Node expected) {
      this.actual = actual;
      this.expected = expected;
    }
  }

  private String serializeNode(Node node) {
    if (serializer != null) {
      return serializer.apply(node);
    } else if (node == null) {
      return "<Java null>";
    } else {
      return node.toStringTree();
    }
  }

  private static String jsdocToStringNullsafe(@Nullable JSDocInfo jsdoc) {
    return jsdoc == null ? "(null)" : jsdoc.toStringVerbose();
  }
}
