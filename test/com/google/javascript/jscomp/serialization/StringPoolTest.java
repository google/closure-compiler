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

package com.google.javascript.jscomp.serialization;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StringPoolTest {

  @Test
  public void emptyPool_fromBuilderOrConstant() {
    assertIsEmptyPool(StringPool.empty());
    assertIsEmptyPool(StringPool.builder().build());
  }

  @Test
  public void emptyPool_serializesToDefaultProto() {
    assertThat(StringPool.empty().toProto()).isEqualToDefaultInstance();
  }

  @Test
  public void emptyPool_parsesFromDefaultProto() {
    assertIsEmptyPool(StringPool.fromProto(StringPoolProto.getDefaultInstance()));
  }

  private void assertIsEmptyPool(StringPool pool) {
    assertThat(pool.get(0)).isEmpty();
    assertThrows(Exception.class, () -> pool.get(1));
  }
}
