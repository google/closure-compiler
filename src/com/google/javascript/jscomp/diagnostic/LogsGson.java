/*
 * Copyright 2019 The Closure Compiler Authors.
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

import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

/** A Gson instance tailored to generating logs output. */
public final class LogsGson {

  /**
   * A conversion to a preferred logging representation.
   *
   * <p>When an implementor is logged, the result of this method will be JSON stringified in place
   * of the implementor.
   *
   * <p>Use this interface when the default Gson serialization for a class is bad and the class
   * should always be logged another way. Primitive wrappers and entity type are ideal candidates.
   */
  public interface Able {
    Object toLogsGson();
  }

  static String toJson(Object o) {
    return GSON.toJson(o);
  }

  private static final Gson GSON =
      new GsonBuilder()
          .registerTypeHierarchyAdapter(
              Able.class,
              new TypeAdapter<Able>() {
                @Override
                public Able read(JsonReader in) {
                  throw new AssertionError();
                }

                @Override
                public void write(JsonWriter out, Able value) throws IOException {
                  out.jsonValue(GSON.toJson(value.toLogsGson()));
                }
              })
          .registerTypeHierarchyAdapter(
              Multimap.class,
              new TypeAdapter<Multimap<?, ?>>() {
                @Override
                public Multimap<?, ?> read(JsonReader in) {
                  throw new AssertionError();
                }

                @Override
                public void write(JsonWriter out, Multimap<?, ?> value) throws IOException {
                  out.jsonValue(GSON.toJson(value.asMap()));
                }
              })
          .create();

  private LogsGson() {
    throw new AssertionError();
  }
}
