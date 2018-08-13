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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.jstype.JSType;
import java.util.Iterator;
import org.junit.Assert;

/**
 * Helper methods for making assertions about the validity of types.
 * @author nicksantos@google.com (Nick Santos)
 */
public class Asserts {
  private Asserts() {} // all static

  public static JSType assertResolvesToSame(JSType type) {
    Assert.assertSame(type, assertValidResolve(type));
    return type;
  }

  /** @return The resolved type */
  public static JSType assertValidResolve(JSType type) {
    ErrorReporter reporter = TestErrorReporter.forNoExpectedReports();
    JSType resolvedType = type.resolve(reporter);
    assertTypeEquals("JSType#resolve should not affect object equality", type, resolvedType);
    return resolvedType;
  }

  public static void assertTypeNotEquals(JSType a, JSType b) {
    assertTypeNotEquals("", a, b);
  }

  public static void assertTypeNotEquals(String message, JSType a, JSType b) {
    if (!message.isEmpty()) {
      message += "\n";
    }
    String aDebug = debugStringOf(a);
    String bDebug = debugStringOf(b);

    Assert.assertFalse(
        lines(
            message,
            "Type should not be equal.",
            "First type              : " + aDebug,
            "was equal to second type: " + bDebug),
        a.isEquivalentTo(b));
    Assert.assertFalse(
        lines(
            message,
            "Inequality was found to be asymmetric.",
            "Type should not be equal.",
            "First type              : " + aDebug,
            "was equal to second type: " + bDebug),
        b.isEquivalentTo(a));
  }

  public static void assertTypeEquals(JSType a, JSType b) {
    assertTypeEquals("", a, b);
  }

  public static void assertTypeEquals(String message, JSType expected, JSType actual) {
    checkNotNull(expected);
    checkNotNull(actual);

    if (!message.isEmpty()) {
      message += "\n";
    }
    String expectedDebug = debugStringOf(expected);
    String actualDebug = debugStringOf(actual);

    Assert.assertTrue(
        lines(
            message, //
            "Types should be equal.",
            "Expected: " + expectedDebug,
            "Actual  : " + actualDebug),
        expected.isEquivalentTo(actual, true));
    Assert.assertTrue(
        lines(
            message,
            "Equality was found to be asymmetric.",
            "Types should be equal.",
            "Expected: " + expectedDebug,
            "Actual  : " + actualDebug),
        actual.isEquivalentTo(expected, true));

    // Recall the `equals-hashCode` contract: if two objects report being equal, their hashcodes
    // must also be equal. Breaking this contract breaks structures that depend on it (e.g.
    // `HashMap`).
    //
    // The implementations of `hashCode()` and `equals()` are a bit tricky in some of the `JSType`
    // classes, so we want to check specifically that this contract is fulfilled in all the unit
    // tests involving them.
    Assert.assertEquals(
        lines(
            message,
            "Types violate the `equals-hashCode`: types report e `hashCode()`s do not match.",
            "Expected: " + expected.hashCode() + " [on " + expectedDebug + "]",
            "Actual  : " + actual.hashCode() + " [on " + actualDebug + "]"),
        expected.hashCode(),
        actual.hashCode());
  }

  public static <T extends JSType, S extends JSType> void
      assertTypeCollectionEquals(Iterable<T> a, Iterable<S> b) {
    assertThat(b).hasSize(Iterables.size(a));
    Iterator<T> aIterator = a.iterator();
    Iterator<S> bIterator = b.iterator();
    while (aIterator.hasNext()) {
      assertTypeEquals(aIterator.next(), bIterator.next());
    }
  }

  /**
   * For the given equivalent types, run all type operations that
   * should have trivial solutions (getGreatestSubtype, isEquivalentTo, etc)
   */
  public static void assertEquivalenceOperations(JSType a, JSType b) {
    assertTypeEquals(a, a);
    assertTypeEquals(a, b);
    assertTypeEquals(b, b);

    Assert.assertTrue(a.isSubtypeOf(b));
    Assert.assertTrue(a.isSubtypeOf(a));
    Assert.assertTrue(b.isSubtypeOf(b));
    Assert.assertTrue(b.isSubtypeOf(a));

    assertTypeEquals(a, a.getGreatestSubtype(b));
    assertTypeEquals(a, a.getGreatestSubtype(a));
    assertTypeEquals(a, b.getGreatestSubtype(b));
    assertTypeEquals(a, b.getGreatestSubtype(a));

    assertTypeEquals(a, a.getLeastSupertype(b));
    assertTypeEquals(a, a.getLeastSupertype(a));
    assertTypeEquals(a, b.getLeastSupertype(b));
    assertTypeEquals(a, b.getLeastSupertype(a));

    Assert.assertTrue(a.canCastTo(b));
    Assert.assertTrue(a.canCastTo(a));
    Assert.assertTrue(b.canCastTo(b));
    Assert.assertTrue(b.canCastTo(a));
  }

  private static String debugStringOf(JSType type) {
    return (type == null)
        ? "<Java null>"
        : type.toString() + " [instanceof " + type.getClass().getName() + "]";
  }

  private static final String lines(String... lines) {
    return Joiner.on("\n").join(lines);
  }
}
