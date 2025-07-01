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
 *   John Lenz
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

package com.google.javascript.rhino.jstype;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the semantics of {@link SymbolType} */
@RunWith(JUnit4.class)
public final class SymbolTypeTest extends BaseJSTypeTestCase {

  @Test
  public void symbolType_isSymbolValueType() {
    JSType symbolType = registry.getNativeType(JSTypeNative.SYMBOL_TYPE);
    assertThat(symbolType.isSymbolValueType()).isTrue();
  }

  @Test
  public void symbolType_isNotKnownSymbolValueType() {
    JSType symbolType = registry.getNativeType(JSTypeNative.SYMBOL_TYPE);
    assertThat(symbolType.isKnownSymbolValueType()).isFalse();
  }

  @Test
  public void symbolType_autoBoxesToSymbolObject() {
    JSType symbolType = registry.getNativeType(JSTypeNative.SYMBOL_TYPE);
    assertThat(symbolType.autoboxesTo())
        .isEqualTo(registry.getNativeType(JSTypeNative.SYMBOL_OBJECT_TYPE));
  }

  @Test
  public void knownSymbolType_isKnownSymbolValueType() {
    KnownSymbolType knownSymbolType = new KnownSymbolType(registry, "Symbol.iterator");
    assertThat(knownSymbolType.isKnownSymbolValueType()).isTrue();
  }

  @Test
  public void knownSymbolType_isSubtypeOfSymbolType() {
    KnownSymbolType knownSymbolType = new KnownSymbolType(registry, "Symbol.iterator");
    JSType symbolType = registry.getNativeType(JSTypeNative.SYMBOL_TYPE);
    assertThat(knownSymbolType.isSubtypeOf(symbolType)).isTrue();
  }

  @Test
  public void knownSymbolType_reflexiveEqualityAndSubtyping() {
    KnownSymbolType knownSymbolType = new KnownSymbolType(registry, "Symbol.iterator");

    assertType(knownSymbolType).isSubtypeOf(knownSymbolType);
    assertType(knownSymbolType).isEqualTo(knownSymbolType);
  }

  @Test
  public void knownSymbolType_notSubtypeOfOtherKnownSymbolTypes() {
    KnownSymbolType foo1 = new KnownSymbolType(registry, "foo");
    KnownSymbolType foo2 = new KnownSymbolType(registry, "foo");
    KnownSymbolType bar = new KnownSymbolType(registry, "bar");

    assertType(foo1).isNotSubtypeOf(foo2);
    assertType(foo1).isNotSubtypeOf(bar);
    assertType(foo2).isNotSubtypeOf(bar);
    assertType(bar).isNotSubtypeOf(foo1);
    assertType(bar).isNotSubtypeOf(foo2);
  }
}
