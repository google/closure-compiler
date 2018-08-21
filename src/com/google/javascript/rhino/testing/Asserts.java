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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;
import static com.google.javascript.rhino.testing.TypeSubject.types;

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
    assertWithMessage("JSType#resolve should not affect object equality")
        .about(types())
        .that(resolvedType)
        .isStructurallyEqualTo(type);
    return resolvedType;
  }

  public static <T extends JSType, S extends JSType> void
      assertTypeCollectionEquals(Iterable<T> a, Iterable<S> b) {
    assertThat(b).hasSize(Iterables.size(a));
    Iterator<T> aIterator = a.iterator();
    Iterator<S> bIterator = b.iterator();
    while (aIterator.hasNext()) {
      assertType(bIterator.next()).isStructurallyEqualTo(aIterator.next());
    }
  }

  /**
   * For the given equivalent types, run all type operations that
   * should have trivial solutions (getGreatestSubtype, isEquivalentTo, etc)
   */
  public static void assertEquivalenceOperations(JSType a, JSType b) {
    assertType(a).isStructurallyEqualTo(a);
    assertType(b).isStructurallyEqualTo(a);
    assertType(b).isStructurallyEqualTo(b);

    Assert.assertTrue(a.isSubtypeOf(b));
    Assert.assertTrue(a.isSubtypeOf(a));
    Assert.assertTrue(b.isSubtypeOf(b));
    Assert.assertTrue(b.isSubtypeOf(a));

    assertType(a.getGreatestSubtype(b)).isStructurallyEqualTo(a);
    assertType(a.getGreatestSubtype(a)).isStructurallyEqualTo(a);
    assertType(b.getGreatestSubtype(b)).isStructurallyEqualTo(a);
    assertType(b.getGreatestSubtype(a)).isStructurallyEqualTo(a);

    assertType(a.getLeastSupertype(b)).isStructurallyEqualTo(a);
    assertType(a.getLeastSupertype(a)).isStructurallyEqualTo(a);
    assertType(b.getLeastSupertype(b)).isStructurallyEqualTo(a);
    assertType(b.getLeastSupertype(a)).isStructurallyEqualTo(a);

    Assert.assertTrue(a.canCastTo(b));
    Assert.assertTrue(a.canCastTo(a));
    Assert.assertTrue(b.canCastTo(b));
    Assert.assertTrue(b.canCastTo(a));
  }
}
