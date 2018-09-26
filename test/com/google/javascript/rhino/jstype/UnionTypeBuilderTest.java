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

import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.MapBasedScope;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link UnionTypeBuilder}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public class UnionTypeBuilderTest extends BaseJSTypeTestCase {

  private static final MapBasedScope EMPTY_SCOPE = MapBasedScope.emptyScope();

  private ObjectType base;
  private ObjectType sub;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    FunctionType baseCtor = new FunctionBuilder(registry).forConstructor().withName("Base").build();
    this.base = baseCtor.getInstanceType();
    FunctionType subCtor =
        new FunctionBuilder(registry)
            .forConstructor()
            .withName("Sub")
            .withPrototypeBasedOn(base)
            .build();
    this.sub = subCtor.getInstanceType();
  }

  @Test
  public void testAllType() {
    assertUnion("*", ALL_TYPE);
    assertUnion("*", NUMBER_TYPE, ALL_TYPE);
    assertUnion("*", ALL_TYPE, NUMBER_TYPE);
    assertUnion("*", ALL_TYPE, NUMBER_TYPE, NO_TYPE);
  }

  @Test
  public void testEmptyUnion() {
    assertUnion("None");
    assertUnion("None", NO_TYPE, NO_TYPE);
  }

  @Test
  public void testUnionTypes() {
    JSType union = registry.createUnionType(STRING_TYPE, OBJECT_TYPE);

    assertUnion("*", ALL_TYPE, union);
    assertUnion("(Object|string)", OBJECT_TYPE, union);
    assertUnion("(Object|string)", union, OBJECT_TYPE);
    assertUnion("(Object|number|string)", NUMBER_TYPE, union);
    assertUnion("(Object|number|string)", union, NUMBER_TYPE);
    assertUnion("(Object|boolean|number|string)", union,
        registry.createUnionType(NUMBER_TYPE, BOOLEAN_TYPE));
    assertUnion("(Object|boolean|number|string)",
        registry.createUnionType(NUMBER_TYPE, BOOLEAN_TYPE), union);
    assertUnion("(Object|string)", union, STRING_OBJECT_TYPE);
  }

  @Test
  public void testUnknownTypes() {
    JSType unresolvedNameA1 = new NamedType(EMPTY_SCOPE, registry, "not.resolved.A", null, -1, -1);
    JSType unresolvedNameA2 = new NamedType(EMPTY_SCOPE, registry, "not.resolved.A", null, -1, -1);
    JSType unresolvedNameB = new NamedType(EMPTY_SCOPE, registry, "not.resolved.B", null, -1, -1);

    assertUnion("?", UNKNOWN_TYPE);
    assertUnion("?", UNKNOWN_TYPE, UNKNOWN_TYPE);
    assertUnion("(?|undefined)", UNKNOWN_TYPE, VOID_TYPE);
    assertUnion("(?|undefined)", VOID_TYPE, UNKNOWN_TYPE);
    assertUnion("(?|undefined)", VOID_TYPE, NUMBER_TYPE, UNKNOWN_TYPE);

    assertUnion("(*|undefined)", ALL_TYPE, VOID_TYPE, NULL_TYPE);

    // NOTE: "(?)" means there are multiple unknown types in the union.
    assertUnion("?", UNKNOWN_TYPE, unresolvedNameA1);
    assertUnion("not.resolved.A", unresolvedNameA1, unresolvedNameA2);
    assertUnion("(not.resolved.A|not.resolved.B)",
        unresolvedNameA1, unresolvedNameB);
    assertUnion("(Object|not.resolved.A)", unresolvedNameA1, OBJECT_TYPE);
  }

  @Test
  public void testRemovalOfDupes() {
    JSType stringAndObject = registry.createUnionType(STRING_TYPE, OBJECT_TYPE);
    assertUnion("(Object|string)", stringAndObject, STRING_OBJECT_TYPE);
    assertUnion("(Object|string)", STRING_OBJECT_TYPE, stringAndObject);
  }

  @Test
  public void testRemovalOfDupes2() {
    JSType union =
        registry.createUnionType(
            sub,
            createFunctionWithReturn(base),
            base,
            createFunctionWithReturn(sub));
    assertEquals("(Base|function(): Base)", union.toString());
  }

  @Test
  public void testRemovalOfDupes3() {
    JSType union =
        registry.createUnionType(
            base,
            createFunctionWithReturn(sub),
            sub,
            createFunctionWithReturn(base));
    assertEquals("(Base|function(): Base)", union.toString());
  }

  @Test
  public void testRemovalOfDuplicateRecordTypes1() {
    UnionTypeBuilder builder = UnionTypeBuilder.create(registry);

    addRecordType(builder, false);
    addRecordType(builder, false);

    assertEquals(1, builder.getAlternatesCount());
  }

  public void testDifferentTemplateSpecializations_whenUnioned_doNotLeakRawType() {
    // Given
    JSType arrayOfString = registry.createTemplatizedType(ARRAY_TYPE, STRING_TYPE);
    JSType arrayOfNumber = registry.createTemplatizedType(ARRAY_TYPE, NUMBER_TYPE);
    JSType arrayOfUnknown = registry.createTemplatizedType(ARRAY_TYPE, UNKNOWN_TYPE);

    UnionTypeBuilder builder =
        UnionTypeBuilder.create(registry).addAlternate(arrayOfString).addAlternate(arrayOfNumber);

    // When
    JSType result = builder.build();

    // Then
    assertType(result).isEqualTo(arrayOfUnknown);
    assertThat(result.getTemplateTypeMap().numUnfilledTemplateKeys()).isEqualTo(0);
  }

  @Test
  public void testRemovalOfDuplicateRecordTypes2() {
    UnionTypeBuilder builder = UnionTypeBuilder.create(registry);

    addRecordType(builder, true);
    addRecordType(builder, true);

    assertEquals(1, builder.getAlternatesCount());
  }

  private void addRecordType(UnionTypeBuilder builder, boolean inferred) {
    RecordTypeBuilder recBuilder = new RecordTypeBuilder(registry);
    recBuilder.setSynthesized(inferred);
    recBuilder.addProperty("prop", NUMBER_TYPE, null);
    builder.addAlternate(recBuilder.build());
  }

  public void assertUnion(String expected, JSType ... types) {
    UnionTypeBuilder builder = UnionTypeBuilder.create(registry);
    for (JSType type : types) {
      builder.addAlternate(type);
    }
    assertEquals(expected, builder.build().toString());
  }

  public FunctionType createFunctionWithReturn(JSType type) {
    return new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withReturnType(type)
        .build();
  }
}
