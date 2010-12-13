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

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.StaticScope;

import junit.framework.Assert;

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
    return assertValidResolve(type, new EmptyScope());
  }

  /** @return The resolved type */
  public static JSType assertValidResolve(
      JSType type, StaticScope<JSType> scope) {
    ErrorReporter t = TestErrorReporter.forNoExpectedReports();
    JSType resolvedType = type.resolve(t, scope);
    assertTypeEquals("JSType#resolve should not affect object equality",
        type, resolvedType);
    Assert.assertEquals("JSType#resolve should not affect hash codes",
        type.hashCode(), resolvedType.hashCode());
    return resolvedType;
  }

  public static void assertTypeNotEquals(JSType a, JSType b) {
    assertTypeNotEquals("", a, b);
  }

  public static void assertTypeNotEquals(String message, JSType a, JSType b) {
    Assert.assertFalse(
        message +
        (message.isEmpty() ? "" : "\n") +
        "Type: " + b + "\n",
        a.isEquivalentTo(b));
    Assert.assertFalse(
        message +
        " Equals is not symmetric.\n" +
        "Type: " + b + "\n",
        b.isEquivalentTo(a));
  }

  public static void assertTypeEquals(JSType a, JSType b) {
    assertTypeEquals("", a, b);
  }

  public static void assertTypeEquals(String message, JSType a, JSType b) {
    Assert.assertTrue(
        message +
        (message.isEmpty() ? "" : "\n") +
        "Expected: " + a + "\n" +
        "Actual  : " + b,
        a.isEquivalentTo(b));
    Assert.assertTrue(
        message +
        " Equals is not symmetric.\n" +
        "Expected: " + b + "\n" +
        "Actual  : " + a,
        b.isEquivalentTo(a));
  }
}
