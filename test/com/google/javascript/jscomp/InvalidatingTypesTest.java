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
package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.testing.TestErrorReporter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class InvalidatingTypesTest {
  private JSTypeRegistry registry;

  @Before
  public void setUp() {
    this.registry = new JSTypeRegistry(new TestErrorReporter());
  }

  @Test
  public void objectIsInvalidating() {
    InvalidatingTypes invalidatingTypes = new InvalidatingTypes.Builder(registry).build();

    assertThat(invalidatingTypes.isInvalidating(registry.getNativeObjectType(OBJECT_TYPE)))
        .isTrue();
  }

  @Test
  public void templatizedObjectIsInvalidating() {
    InvalidatingTypes invalidatingTypes = new InvalidatingTypes.Builder(registry).build();

    ObjectType objectOfString =
        registry.createTemplatizedType(
            registry.getNativeObjectType(OBJECT_TYPE), registry.getNativeType(NUMBER_TYPE));
    assertThat(invalidatingTypes.isInvalidating(objectOfString)).isTrue();
  }

  @Test
  public void invaliatingRawTypeAlsoInvalidatesTemplatization() {
    InvalidatingTypes invalidatingTypes =
        new InvalidatingTypes.Builder(registry)
            .addAllTypeMismatches(
                ImmutableList.of(
                    TypeMismatch.createForTesting(
                        registry.getNativeObjectType(ARRAY_TYPE),
                        registry.getNativeType(STRING_TYPE))))
            .build();

    ObjectType arrayOfNumber =
        registry.createTemplatizedType(
            registry.getNativeObjectType(ARRAY_TYPE), registry.getNativeType(NUMBER_TYPE));
    assertThat(invalidatingTypes.isInvalidating(arrayOfNumber)).isTrue();
  }

  @Test
  public void invalidatingOneTemplatizationInvalidatesAll() {
    ObjectType arrayOfString =
        registry.createTemplatizedType(
            registry.getNativeObjectType(ARRAY_TYPE), registry.getNativeType(STRING_TYPE));
    InvalidatingTypes invalidatingTypes =
        new InvalidatingTypes.Builder(registry)
            .addAllTypeMismatches(
                ImmutableList.of(
                    TypeMismatch.createForTesting(
                        arrayOfString, registry.getNativeType(STRING_TYPE))))
            .build();

    ObjectType arrayOfNumber =
        registry.createTemplatizedType(
            registry.getNativeObjectType(ARRAY_TYPE), registry.getNativeType(NUMBER_TYPE));
    assertThat(invalidatingTypes.isInvalidating(arrayOfNumber)).isTrue();
  }

  @Test
  public void invalidatingOneTemplatizationInvalidatesRawType() {
    ObjectType arrayOfString =
        registry.createTemplatizedType(
            registry.getNativeObjectType(ARRAY_TYPE), registry.getNativeType(STRING_TYPE));
    InvalidatingTypes invalidatingTypes =
        new InvalidatingTypes.Builder(registry)
            .addAllTypeMismatches(
                ImmutableList.of(
                    TypeMismatch.createForTesting(
                        arrayOfString, registry.getNativeType(STRING_TYPE))))
            .build();

    assertThat(invalidatingTypes.isInvalidating(registry.getNativeObjectType(ARRAY_TYPE))).isTrue();
  }

  @Test
  public void arrayNotInvalidating() {
    InvalidatingTypes invalidatingTypes = new InvalidatingTypes.Builder(registry).build();

    assertThat(
            invalidatingTypes.isInvalidating(
                registry.createTemplatizedType(
                    registry.getNativeObjectType(ARRAY_TYPE), registry.getNativeType(STRING_TYPE))))
        .isFalse();
  }

  @Test
  public void invalidatingInstanceTypeInvalidatesCtor() {
    InvalidatingTypes invalidatingTypes =
        new InvalidatingTypes.Builder(registry)
            .addAllTypeMismatches(
                ImmutableList.of(
                    TypeMismatch.createForTesting(
                        registry.getNativeObjectType(ARRAY_TYPE),
                        registry.getNativeType(STRING_TYPE))))
            .build();

    assertThat(
            invalidatingTypes.isInvalidating(
                registry.getNativeObjectType(ARRAY_TYPE).getConstructor()))
        .isTrue();
  }

  @Test
  public void invalidatingTemplatizedInstanceTypeInvalidatesCtor() {
    ObjectType arrayOfString =
        registry.createTemplatizedType(
            registry.getNativeObjectType(ARRAY_TYPE), registry.getNativeType(STRING_TYPE));
    InvalidatingTypes invalidatingTypes =
        new InvalidatingTypes.Builder(registry)
            .addAllTypeMismatches(
                ImmutableList.of(
                    TypeMismatch.createForTesting(
                        arrayOfString, registry.getNativeType(STRING_TYPE))))
            .build();

    assertThat(
            invalidatingTypes.isInvalidating(
                registry.getNativeObjectType(ARRAY_TYPE).getConstructor()))
        .isTrue();
  }
}
