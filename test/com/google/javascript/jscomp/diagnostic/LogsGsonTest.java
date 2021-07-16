/*
 * Copyright 2021 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.diagnostic;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.truth.StringSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LogsGsonTest {

  @Test
  public void adapter_write_logsGsonable() {
    assertLogsGson(ImmutableList.of(new TestLogsGsonable())).isEqualTo("[[0]]");
  }

  static class TestLogsGsonable implements LogsGson.Able {
    @Override
    public Object toLogsGson() {
      return ImmutableList.of(0);
    }
  }

  @Test
  public void adapter_write_multimap() {
    // Given
    ImmutableMultimap<Object, Object> value =
        ImmutableMultimap.builder() //
            .put("foo", 0)
            .put("foo", 1)
            .put("bar", 2)
            .build();

    // Then
    assertLogsGson(value).isEqualTo("{\"foo\":[0,1],\"bar\":[2]}");
  }

  private StringSubject assertLogsGson(Object value) {
    return assertThat(LogsGson.toJson(value));
  }
}
