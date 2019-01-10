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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.javascript.rhino.jstype.JSType.Nullability;
import com.google.javascript.rhino.testing.Asserts;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RecordTypeTest extends BaseJSTypeTestCase {

  @Test
  public void testRecursiveRecord() {
    ProxyObjectType loop = new ProxyObjectType(registry, NUMBER_TYPE);
    JSType record = new RecordTypeBuilder(registry)
        .addProperty("loop", loop, null)
        .addProperty("number", NUMBER_TYPE, null)
        .addProperty("string", STRING_TYPE, null)
        .build();
    assertThat(record.toString())
        .isEqualTo("{\n  loop: number,\n  number: number,\n  string: string\n}");

    loop.setReferencedType(record);
    assertThat(record.toString())
        .isEqualTo("{\n  loop: {...},\n  number: number,\n  string: string\n}");
    assertThat(record.toAnnotationString(Nullability.EXPLICIT))
        .isEqualTo("{loop: ?, number: number, string: string}");

    Asserts.assertEquivalenceOperations(record, loop);
  }

  @Test
  public void testLongToString() {
    JSType record = new RecordTypeBuilder(registry)
        .addProperty("a01", NUMBER_TYPE, null)
        .addProperty("a02", NUMBER_TYPE, null)
        .addProperty("a03", NUMBER_TYPE, null)
        .addProperty("a04", NUMBER_TYPE, null)
        .addProperty("a05", NUMBER_TYPE, null)
        .addProperty("a06", NUMBER_TYPE, null)
        .addProperty("a07", NUMBER_TYPE, null)
        .addProperty("a08", NUMBER_TYPE, null)
        .addProperty("a09", NUMBER_TYPE, null)
        .addProperty("a10", NUMBER_TYPE, null)
        .addProperty("a11", NUMBER_TYPE, null)
        .build();
    assertThat(record.toString())
        .isEqualTo(
            LINE_JOINER.join(
                "{",
                "  a01: number,",
                "  a02: number,",
                "  a03: number,",
                "  a04: number,",
                "  a05: number,",
                "  a06: number,",
                "  a07: number,",
                "  a08: number,",
                "  a09: number,",
                "  a10: number, ...",
                "}"));
    assertThat(record.toAnnotationString(Nullability.EXPLICIT))
        .isEqualTo(
            "{a01: number, a02: number, a03: number, a04: number, a05: number, a06: number,"
                + " a07: number, a08: number, a09: number, a10: number, a11: number}");
  }

  @Test
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

    assertType(recordA.getGreatestSubtype(recordC)).isStructurallyEqualTo(aInfC);
    assertType(recordA.getLeastSupertype(recordC)).isStructurallyEqualTo(aSupC);

    assertType(proxyRecordA.getGreatestSubtype(proxyRecordC)).isStructurallyEqualTo(aInfC);
    assertType(proxyRecordA.getLeastSupertype(proxyRecordC)).isStructurallyEqualTo(aSupC);
  }

  @Test
  public void testSubtypeWithUnknowns() {
    JSType recordA = new RecordTypeBuilder(registry)
        .addProperty("a", NUMBER_TYPE, null)
        .build();
    JSType recordB = new RecordTypeBuilder(registry)
        .addProperty("a", UNKNOWN_TYPE, null)
        .build();
    assertThat(recordA.isSubtypeOf(recordB)).isTrue();
    assertThat(recordB.isSubtypeOf(recordA)).isTrue();
  }

  @Test
  public void testSubtypeWithUnknowns2() {
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
    assertThat(recordA.isSubtypeOf(recordB)).isTrue();
    assertThat(recordB.isSubtypeOf(recordA)).isTrue();
  }

  @Test
  public void testSubtypeWithFunctionProps() {
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
    assertThat(recordA.isSubtypeOf(recordB)).isFalse();
    assertThat(recordB.isSubtypeOf(recordA)).isFalse();
  }

  @Test
  public void testSubtypeWithManyProps() {
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
    assertThat(recordA.isSubtypeOf(recordB)).isFalse();
    assertThat(recordB.isSubtypeOf(recordA)).isFalse();
    assertThat(recordC.isSubtypeOf(recordB)).isFalse();
    assertThat(recordB.isSubtypeOf(recordC)).isTrue();
    assertThat(recordA.isSubtypeOf(recordC)).isTrue();
  }
}
