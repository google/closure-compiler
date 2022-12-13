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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.MapBasedScope;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link UnionType.Builder}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public class UnionTypeBuilderTest extends BaseJSTypeTestCase {

  private static final MapBasedScope EMPTY_SCOPE = MapBasedScope.emptyScope();

  private ObjectType base;
  private ObjectType sub;

  @Before
  public void setUp() throws Exception {
    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      FunctionType baseCtor =
          FunctionType.builder(registry).forConstructor().withName("Base").build();
      this.base = baseCtor.getInstanceType();
      FunctionType subCtor =
          FunctionType.builder(registry)
              .forConstructor()
              .withName("Sub")
              .withPrototypeBasedOn(base)
              .build();
      this.sub = subCtor.getInstanceType();
    }
  }

  @Test
  public void testWildcardType_allType() {
    assertUnion("*", ALL_TYPE);
    assertUnion("*", ALL_TYPE, ALL_TYPE);
    assertUnion("*", NUMBER_TYPE, ALL_TYPE);
    assertUnion("*", ALL_TYPE, NUMBER_TYPE);
    assertUnion("*", ALL_TYPE, NUMBER_TYPE, NO_TYPE);

    assertUnion("(*|undefined)", ALL_TYPE, VOID_TYPE);

    assertUnion("*", ALL_TYPE, UNKNOWN_TYPE); // TODO(b/137892871): Should be `?`.
    assertUnion("*", ALL_TYPE, CHECKED_UNKNOWN_TYPE); // TODO(b/137892871): Should be `??`.
  }

  @Test
  public void testWildcardType_unknownType() {
    assertUnion("?", UNKNOWN_TYPE);
    assertUnion("?", UNKNOWN_TYPE, UNKNOWN_TYPE);
    assertUnion("?", NUMBER_TYPE, UNKNOWN_TYPE);
    assertUnion("?", UNKNOWN_TYPE, NUMBER_TYPE);
    assertUnion("?", UNKNOWN_TYPE, NUMBER_TYPE, NO_TYPE);

    assertUnion("(?|undefined)", UNKNOWN_TYPE, VOID_TYPE);

    assertUnion("*", ALL_TYPE, UNKNOWN_TYPE); // TODO(b/137892871): Should be `?`.
    assertUnion("?", CHECKED_UNKNOWN_TYPE, UNKNOWN_TYPE);
  }

  @Test
  public void testWildcardType_checkedUnknownType() {
    assertUnion("??", CHECKED_UNKNOWN_TYPE);
    assertUnion("??", CHECKED_UNKNOWN_TYPE, CHECKED_UNKNOWN_TYPE);
    assertUnion("??", NUMBER_TYPE, CHECKED_UNKNOWN_TYPE);
    assertUnion("??", CHECKED_UNKNOWN_TYPE, NUMBER_TYPE);
    assertUnion("??", CHECKED_UNKNOWN_TYPE, NUMBER_TYPE, NO_TYPE);

    assertUnion("(??|undefined)", CHECKED_UNKNOWN_TYPE, VOID_TYPE);

    assertUnion("*", ALL_TYPE, CHECKED_UNKNOWN_TYPE); // TODO(b/137892871): Should be `??`.
    assertUnion("?", UNKNOWN_TYPE, CHECKED_UNKNOWN_TYPE);
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
    assertUnion("?", UNKNOWN_TYPE);
    assertUnion("?", UNKNOWN_TYPE, UNKNOWN_TYPE);
    assertUnion("(?|undefined)", UNKNOWN_TYPE, VOID_TYPE);
    assertUnion("(?|undefined)", VOID_TYPE, UNKNOWN_TYPE);
    assertUnion("(?|undefined)", VOID_TYPE, NUMBER_TYPE, UNKNOWN_TYPE);

    assertUnion("(*|undefined)", ALL_TYPE, VOID_TYPE, NULL_TYPE);
  }

  @Test
  public void testUnresolvedNamedTypes() {
    errorReporter.expectAllWarnings(
        "Bad type annotation. Unknown type not.resolved.A",
        "Bad type annotation. Unknown type not.resolved.A",
        "Bad type annotation. Unknown type not.resolved.B");

    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      JSType unresolvedNameA1 = registry.createNamedType(EMPTY_SCOPE, "not.resolved.A", "", -1, -1);
      JSType unresolvedNameA2 = registry.createNamedType(EMPTY_SCOPE, "not.resolved.A", "", -1, -1);
      JSType unresolvedNameB = registry.createNamedType(EMPTY_SCOPE, "not.resolved.B", "", -1, -1);

      // NOTE: "(?)" means there are multiple unknown types in the union.
      assertUnion("?", UNKNOWN_TYPE, unresolvedNameA1);
      assertUnion("(not.resolved.A|not.resolved.A)", unresolvedNameA1, unresolvedNameA2);
      assertUnion("(not.resolved.A|not.resolved.B)", unresolvedNameA1, unresolvedNameB);
      assertUnion("(Object|not.resolved.A)", unresolvedNameA1, OBJECT_TYPE);
    }
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
    assertThat(union.toString()).isEqualTo("(Base|function(): Base)");
  }

  @Test
  public void testRemovalOfDupes3() {
    JSType union =
        registry.createUnionType(
            base,
            createFunctionWithReturn(sub),
            sub,
            createFunctionWithReturn(base));
    assertThat(union.toString()).isEqualTo("(Base|function(): Base)");
  }

  @Test
  public void testRemovalOfIdenticalRecordTypes_beforeResolution() {
    UnionType recordUnion;
    try (JSTypeResolver.Closer closer = registry.getResolver().openForDefinition()) {
      // Create two identical anonymous record types
      JSType type = registry.createRecordType(ImmutableMap.of("prop", STRING_TYPE));

      recordUnion = (UnionType) registry.createUnionType(type, type, NULL_TYPE);

      // drop the identical references to 'type'
      assertThat(recordUnion.getAlternates()).hasSize(2);
    }
  }

  @Test
  public void testRemovalOfDuplicateRecordTypes_deferredUntilResolution() {
    UnionType recordUnion;
    try (JSTypeResolver.Closer closer = registry.getResolver().openForDefinition()) {
      // Create two identical anonymous record types
      JSType typeA = registry.createRecordType(ImmutableMap.of("prop", STRING_TYPE));
      JSType typeB = registry.createRecordType(ImmutableMap.of("prop", STRING_TYPE));

      recordUnion = (UnionType) registry.createUnionType(typeA, typeB);

      assertThat(recordUnion.getAlternates()).hasSize(2);
    }
    assertThat(recordUnion.getAlternates()).hasSize(1);
  }

  @Test
  public void testRemovalOfDuplicateRecordTypes1() {
    UnionType.Builder builder = UnionType.builder(registry);

    addRecordType(builder, false);
    addRecordType(builder, false);

    assertThat(builder.build().toMaybeUnionType()).isNull();
  }

  @Test
  public void testRemovalOfDuplicateRecordTypes2() {
    UnionType.Builder builder = UnionType.builder(registry);

    addRecordType(builder, true);
    addRecordType(builder, true);

    assertThat(builder.build().toMaybeUnionType()).isNull();
  }

  @Test
  public void testDifferentTemplateSpecializations_whenUnioned_doNotLeakRawType() {
    // Given
    JSType arrayOfString = registry.createTemplatizedType(ARRAY_TYPE, STRING_TYPE);
    JSType arrayOfNumber = registry.createTemplatizedType(ARRAY_TYPE, NUMBER_TYPE);
    JSType arrayOfUnknown = registry.createTemplatizedType(ARRAY_TYPE, UNKNOWN_TYPE);

    UnionType.Builder builder =
        UnionType.builder(registry).addAlternate(arrayOfString).addAlternate(arrayOfNumber);

    // When
    JSType result = builder.build();

    // Then
    assertThat(result.isRawTypeOfTemplatizedType()).isFalse();
    assertType(result).isEqualTo(arrayOfUnknown);
  }

  @Test
  public void testAfterBuild_cannotRebuild() {
    UnionType.Builder builder = UnionType.builder(registry);

    builder.build();

    assertThrows(Exception.class, builder::build);
  }

  @Test
  public void testAfterBuild_cannotAdd() {
    UnionType.Builder builder = UnionType.builder(registry);

    builder.build();

    assertThrows(Exception.class, () -> builder.addAlternate(NUMBER_TYPE));
  }

  private void addRecordType(UnionType.Builder builder, boolean inferred) {
    RecordTypeBuilder recBuilder = new RecordTypeBuilder(registry);
    recBuilder.setSynthesized(inferred);
    recBuilder.addProperty("prop", NUMBER_TYPE, null);
    builder.addAlternate(recBuilder.build());
  }

  private void assertUnion(String expected, JSType... types) {
    UnionType.Builder builder = UnionType.builder(registry);
    for (JSType type : types) {
      builder.addAlternate(type);
    }
    assertThat(builder.build().toString()).isEqualTo(expected);
  }

  private FunctionType createFunctionWithReturn(JSType type) {
    return FunctionType.builder(registry)
        .withParameters(registry.createParameters())
        .withReturnType(type)
        .build();
  }
}
