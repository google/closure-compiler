/*
 * Copyright 2020 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.colors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.colors.ColorId.fromAscii;
import static com.google.javascript.jscomp.testing.ColorSubject.assertThat;
import static com.google.javascript.rhino.testing.Asserts.assertThrows;

import com.google.common.collect.ImmutableSet;
import java.util.function.BiFunction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for union Colors */
@RunWith(JUnit4.class)
public class ColorTest {

  private final Color numberOrString =
      Color.createUnion(ImmutableSet.of(StandardColors.STRING, StandardColors.NUMBER));

  @Test
  public void unionsReportIsUnion() {
    assertThat(numberOrString).isUnion();
  }

  @Test
  public void unionsThrowWhenAskedIsPrimitive() {
    assertThrows(Exception.class, numberOrString::isPrimitive);
  }

  @Test
  public void primitivesReportIsPrimitiveButNotUnion() {
    Color string = StandardColors.STRING;
    assertThat(string).isPrimitive();
    assertThat(string.isUnion()).isFalse();
  }

  @Test
  public void objectsReportIsObjectButNotUnionOrPrimitive() {
    Color foo = Color.singleBuilder().setId(fromAscii("Bar")).build();
    assertThat(foo.isPrimitive()).isFalse();
    assertThat(foo.isUnion()).isFalse();
  }

  @Test
  public void getAlternatesReturnsAlternatesList() {
    assertThat(numberOrString).hasAlternates(StandardColors.NUMBER, StandardColors.STRING);
  }

  @Test
  public void alternatesMayContainOtherUnion() {
    Color newUnion = Color.createUnion(ImmutableSet.of(numberOrString, StandardColors.BIGINT));

    assertThat(newUnion)
        .hasAlternates(StandardColors.STRING, StandardColors.BIGINT, StandardColors.NUMBER);
  }

  @Test
  public void alternatesAreDeduplicatedFromOtherUnion() {
    Color union = Color.createUnion(ImmutableSet.of(StandardColors.NUMBER, numberOrString));

    assertThat(union).hasAlternates(StandardColors.NUMBER, StandardColors.STRING);
  }

  @Test
  public void createUnionAllowsSingleAlternate() {
    assertThrows(Exception.class, () -> Color.createUnion(ImmutableSet.of()));
    assertThat(Color.createUnion(ImmutableSet.of(StandardColors.NUMBER)).isUnion()).isFalse();
  }

  @Test
  public void unknownTypeIsNotSpecialCased() {
    // In some "check" type systems, the top type + another type is equivalent to just the
    // top type. Colors don't replicate those semantics. Whatever serialized type structure the
    // colors are created from has already dealt with this.
    Color union = Color.createUnion(ImmutableSet.of(StandardColors.UNKNOWN, StandardColors.STRING));

    assertThat(union).isUnion();
    assertThat(union.getUnionElements()).hasSize(2);
  }

  @Test
  public void nullableUnionDoesNotInvalidate() {
    Color nonInvalidatingObject = Color.singleBuilder().setId(fromAscii("Bar")).build();
    Color objects =
        Color.createUnion(ImmutableSet.of(StandardColors.NULL_OR_VOID, nonInvalidatingObject));

    assertThat(objects).isNotInvalidating();
  }

  @Test
  public void union_isInvalidating_ored() {
    Color a = this.createUnionSettingBoolProps(Color.Builder::setInvalidating, true, false);
    assertThat(a.isInvalidating()).isTrue();

    Color b = this.createUnionSettingBoolProps(Color.Builder::setInvalidating, false, false);
    assertThat(b.isInvalidating()).isFalse();
  }

  @Test
  public void union_propertiesKeepOriginalName_ored() {
    Color a =
        this.createUnionSettingBoolProps(Color.Builder::setPropertiesKeepOriginalName, true, false);
    assertThat(a.getPropertiesKeepOriginalName()).isTrue();

    Color b =
        this.createUnionSettingBoolProps(
            Color.Builder::setPropertiesKeepOriginalName, false, false);
    assertThat(b.getPropertiesKeepOriginalName()).isFalse();
  }

  @Test
  public void union_isClosureAssert_anded() {
    Color a = this.createUnionSettingBoolProps(Color.Builder::setClosureAssert, true, true);
    assertThat(a.isClosureAssert()).isTrue();

    Color b = this.createUnionSettingBoolProps(Color.Builder::setClosureAssert, true, false);
    assertThat(b.isClosureAssert()).isFalse();
  }

  @Test
  public void union_isConstructor_anded() {
    Color a = this.createUnionSettingBoolProps(Color.Builder::setConstructor, true, true);
    assertThat(a.isConstructor()).isTrue();

    Color b = this.createUnionSettingBoolProps(Color.Builder::setConstructor, true, false);
    assertThat(b.isConstructor()).isFalse();
  }

  private Color createUnionSettingBoolProps(
      BiFunction<Color.Builder, Boolean, Color.Builder> setter, boolean first, boolean second) {
    return Color.createUnion(
        ImmutableSet.of(
            setter.apply(Color.singleBuilder().setId(fromAscii("Foo")), first).build(),
            setter.apply(Color.singleBuilder().setId(fromAscii("Bar")), second).build()));
  }

  @Test
  public void primitivesAreInvalidatingBasedOnConstantData() {
    assertThat(StandardColors.UNKNOWN).isInvalidating();
    assertThat(StandardColors.TOP_OBJECT).isInvalidating();
    assertThat(StandardColors.NUMBER).isNotInvalidating();
    assertThat(StandardColors.STRING).isNotInvalidating();
  }

  @Test
  public void objectEqualityBasedOnClassAndFileName() {
    assertThat(Color.singleBuilder().setId(fromAscii("Foo")).build())
        .isEqualTo(Color.singleBuilder().setId(fromAscii("Foo")).build());
  }

  @Test
  public void objectEqualityFalseIfInvalidatingMismatch() {
    assertThat(Color.singleBuilder().setId(fromAscii("Foo")).setInvalidating(true).build())
        .isNotEqualTo(Color.singleBuilder().setId(fromAscii("Foo")).build());
  }
}
