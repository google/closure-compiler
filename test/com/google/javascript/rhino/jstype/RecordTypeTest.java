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

package com.google.javascript.rhino.jstype;

import com.google.javascript.rhino.testing.Asserts;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;

public class RecordTypeTest extends BaseJSTypeTestCase {

  public void testRecursiveRecord() {
    ProxyObjectType loop = new ProxyObjectType(registry, NUMBER_TYPE);
    JSType record = new RecordTypeBuilder(registry)
        .addProperty("loop", loop, null)
        .addProperty("number", NUMBER_TYPE, null)
        .addProperty("string", STRING_TYPE, null)
        .build();
    assertEquals("{loop: number, number: number, string: string}",
        record.toString());

    loop.setReferencedType(record);
    assertEquals("{loop: {...}, number: number, string: string}",
        record.toString());
    assertEquals("{loop: ?, number: number, string: string}",
        record.toAnnotationString());

    Asserts.assertEquivalenceOperations(record, loop);
  }

  public void testLongToString() {
    JSType record = new RecordTypeBuilder(registry)
        .addProperty("a1", NUMBER_TYPE, null)
        .addProperty("a2", NUMBER_TYPE, null)
        .addProperty("a3", NUMBER_TYPE, null)
        .addProperty("a4", NUMBER_TYPE, null)
        .addProperty("a5", NUMBER_TYPE, null)
        .addProperty("a6", NUMBER_TYPE, null)
        .build();
    assertEquals("{a1: number, a2: number, a3: number, a4: number, ...}",
        record.toString());
    assertEquals(
        "{a1: number, a2: number, a3: number, a4: number," +
        " a5: number, a6: number}",
        record.toAnnotationString());
  }

  public void testSupAndInf() {
    JSType recordA = new RecordTypeBuilder(registry)
        .addProperty("a", NUMBER_TYPE, null)
        .addProperty("b", NUMBER_TYPE, null)
        .build();
    JSType recordC = new RecordTypeBuilder(registry)
        .addProperty("b", NUMBER_TYPE, null)
        .addProperty("c", NUMBER_TYPE, null)
        .build();
    ProxyObjectType proxyRecordA = new ProxyObjectType(registry, recordA);
    ProxyObjectType proxyRecordC = new ProxyObjectType(registry, recordC);

    JSType aInfC = new RecordTypeBuilder(registry)
        .addProperty("a", NUMBER_TYPE, null)
        .addProperty("b", NUMBER_TYPE, null)
        .addProperty("c", NUMBER_TYPE, null)
        .build();

    JSType aSupC = registry.createUnionType(recordA, recordC);

    Asserts.assertTypeEquals(
        aInfC, recordA.getGreatestSubtype(recordC));
    Asserts.assertTypeEquals(
        aSupC, recordA.getLeastSupertype(recordC));

    Asserts.assertTypeEquals(
        aInfC, proxyRecordA.getGreatestSubtype(proxyRecordC));
    Asserts.assertTypeEquals(
        aSupC, proxyRecordA.getLeastSupertype(proxyRecordC));
  }

  public void testSubtypeWithUnknowns() throws Exception {
    JSType recordA = new RecordTypeBuilder(registry)
        .addProperty("a", NUMBER_TYPE, null)
        .build();
    JSType recordB = new RecordTypeBuilder(registry)
        .addProperty("a", UNKNOWN_TYPE, null)
        .build();
    assertTrue(recordA.isSubtype(recordB));
    assertTrue(recordB.isSubtype(recordA));
  }

  public void testSubtypeWithUnknowns2() throws Exception {
    JSType recordA = new RecordTypeBuilder(registry)
        .addProperty("a",
            new FunctionBuilder(registry)
            .withReturnType(NUMBER_TYPE)
            .build(),
            null)
        .build();
    JSType recordB = new RecordTypeBuilder(registry)
        .addProperty("a",
            new FunctionBuilder(registry)
            .withReturnType(UNKNOWN_TYPE)
            .build(),
            null)
        .build();
    assertTrue(recordA.isSubtype(recordB));
    assertTrue(recordB.isSubtype(recordA));
  }

  public void testSubtypeWithFunctionProps() throws Exception {
    JSType recordA = new RecordTypeBuilder(registry)
        .addProperty("a",
            new FunctionBuilder(registry)
            .withReturnType(NUMBER_TYPE)
            .build(),
            null)
        .build();
    JSType recordB = new RecordTypeBuilder(registry)
        .addProperty("a",
            new FunctionBuilder(registry)
            .withReturnType(STRING_TYPE)
            .build(),
            null)
        .build();
    assertFalse(recordA.isSubtype(recordB));
    assertFalse(recordB.isSubtype(recordA));
  }

  public void testSubtypeWithManyProps() throws Exception {
    JSType recordA = new RecordTypeBuilder(registry)
        .addProperty("a", NUMBER_TYPE, null)
        .addProperty("b", NUMBER_TYPE, null)
        .build();
    JSType recordB = new RecordTypeBuilder(registry)
        .addProperty("a", NUMBER_TYPE, null)
        .addProperty("b", STRING_TYPE, null)
        .build();
    JSType recordC = new RecordTypeBuilder(registry)
        .addProperty("a", NUMBER_TYPE, null)
        .addProperty("b",
            registry.createUnionType(NUMBER_TYPE, STRING_TYPE), null)
        .build();
    assertFalse(recordA.isSubtype(recordB));
    assertFalse(recordB.isSubtype(recordA));
    assertFalse(recordC.isSubtype(recordB));
    assertFalse(recordB.isSubtype(recordC));
  }
}
