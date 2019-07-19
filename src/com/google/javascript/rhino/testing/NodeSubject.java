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

import static com.google.common.collect.Streams.stream;
import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.Streams;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;

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

    findFirstMismatch(actual, expected, checkJsdoc)
        .ifPresent(
            (mismatch) -> {
              ArrayList<Fact> facts = new ArrayList<>();
              facts.add(fact("Actual", serializeNode(actual)));
              facts.add(fact("Expected", serializeNode(expected)));

              Node misActual = mismatch.actual;
              Node misExpected = mismatch.expected;
              String misActualStr = serializeNode(misActual);
              String misExpectedStr = serializeNode(misExpected);

              facts.add(fact("Actual mismatch", misActualStr));
              if (misActualStr.equals(misExpectedStr)) {
                String misActualTreeStr = misActual.toStringTree();
                if (!misActualTreeStr.equals(misActualStr)) {
                  facts.add(fact("Actual mismatch AST", misActualTreeStr));
                }
                if (checkJsdoc) {
                  facts.add(fact("Actual JSDoc", jsdocToStringNullsafe(misActual.getJSDocInfo())));
                }
              }

              facts.add(fact("Expected mismatch", misExpectedStr));
              if (misActualStr.equals(misExpectedStr)) {
                String misExpectedTreeStr = misExpected.toStringTree();
                if (!misExpectedTreeStr.equals(misExpectedStr)) {
                  facts.add(fact("Expected mismatch AST", misExpectedTreeStr));
                }
                if (checkJsdoc) {
                  facts.add(
                      fact("Expected JSDoc", jsdocToStringNullsafe(misExpected.getJSDocInfo())));
                }
              }

              failWithoutActual(simpleFact("Node tree inequality"), facts.toArray(new Fact[0]));
            });
  }

  public NodeSubject isEquivalentTo(Node other) {
    check("isEquivalentTo(%s)", other).that(actual.isEquivalentTo(other)).isTrue();
    return this;
  }

  public NodeSubject isNotEquivalentTo(Node other) {
    check("isEquivalentTo(%s)", other).that(actual.isEquivalentTo(other)).isFalse();
    return this;
  }

  public TypeSubject hasJSTypeThat() {
    return TypeSubject.assertType(actual.getJSTypeRequired());
  }

  public void hasType(Token type) {
    hasToken(type);
  }

  public NodeSubject hasToken(Token token) {
    check("getToken()").that(actual.getToken()).isEqualTo(token);
    return this;
  }

  public NodeSubject isName(String name) {
    check("isName()").that(actual.isName()).isTrue();
    check("getString()").that(actual.getString()).isEqualTo(name);
    return this;
  }

  public NodeSubject isNumber(int value) {
    return isNumber((double) value);
  }

  public NodeSubject isNumber(double value) {
    check("isNumber()").that(actual.isNumber()).isTrue();
    check("getNumber()").that(actual.getDouble()).isEqualTo(value);
    return this;
  }

  public NodeSubject isAssign() {
    check("isAssign()").that(actual.isAssign()).isTrue();
    return this;
  }

  public NodeSubject isThis() {
    check("isThis()").that(actual.isThis()).isTrue();
    return this;
  }

  public NodeSubject isSuper() {
    check("isSuper()").that(actual.isSuper()).isTrue();
    return this;
  }

  public NodeSubject isArrowFunction() {
    check("isArrowFunction()").that(actual.isArrowFunction()).isTrue();
    return this;
  }

  public NodeSubject isCall() {
    check("isCall()").that(actual.isCall()).isTrue();
    return this;
  }

  public NodeSubject isMemberFunctionDef(String name) {
    check("isMemberFunction()").that(actual.isMemberFunctionDef()).isTrue();
    check("getString()").that(actual.getString()).isEqualTo(name);
    return this;
  }

  public NodeSubject matchesName(String qname) {
    check("matchesName(%s)", qname).that(actual.matchesName(qname)).isTrue();
    return this;
  }

  public NodeSubject matchesQualifiedName(String qname) {
    check("matchesQualifiedName(%s)", qname).that(actual.matchesQualifiedName(qname)).isTrue();
    return this;
  }

  public NodeSubject hasCharno(int charno) {
    check("getCharno()").that(actual.getCharno()).isEqualTo(charno);
    return this;
  }

  public NodeSubject hasLineno(int lineno) {
    check("getLineno()").that(actual.getLineno()).isEqualTo(lineno);
    return this;
  }

  public NodeSubject hasLength(int length) {
    check("getLength()").that(actual.getLength()).isEqualTo(length);
    return this;
  }

  public NodeSubject hasEqualSourceInfoTo(Node other) {
    return hasLineno(other.getLineno()).hasCharno(other.getCharno()).hasLength(other.getLength());
  }

  public NodeSubject isIndexable(boolean isIndexable) {
    check("isIndexable()").that(actual.isIndexable()).isEqualTo(isIndexable);
    return this;
  }

  public NodeSubject hasOriginalName(String originalName) {
    check("getOriginalName()").that(actual.getOriginalName()).isEqualTo(originalName);
    return this;
  }

  public NodeSubject hasChildren(boolean hasChildren) {
    check("hasChildren()").that(actual.hasChildren()).isEqualTo(hasChildren);
    return this;
  }

  @CheckReturnValue
  public StringSubject hasStringThat() {
    return check("getString()").that(actual.getString());
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
  private static Optional<NodeMismatch> findFirstMismatch(
      Node actual, Node expected, boolean jsDoc) {
    if (!actual.isEquivalentTo(
        expected, /* compareType= */ false, /* recurse= */ false, jsDoc, /* sideEffect= */ false)) {
      return Optional.of(new NodeMismatch(actual, expected));
    }

    // `isEquivalentTo` confirms that the number of children is the same.
    return Streams.zip(
            stream(actual.children()),
            stream(expected.children()),
            (actualChild, expectedChild) -> findFirstMismatch(actualChild, expectedChild, jsDoc))
        .filter(Optional::isPresent)
        .findFirst()
        .orElse(Optional.empty());
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
    } else {
      return node.toStringTree();
    }
  }

  private static String jsdocToStringNullsafe(@Nullable JSDocInfo jsdoc) {
    return jsdoc == null ? "(null)" : jsdoc.toStringVerbose();
  }
}
