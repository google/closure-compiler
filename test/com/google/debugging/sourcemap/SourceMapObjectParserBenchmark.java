/*
 * Copyright 2026 The Closure Compiler Authors.
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

package com.google.debugging.sourcemap;

import com.google.gson.Gson;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class SourceMapObjectParserBenchmark {
  private String json;

  @Setup
  public void setup() {
    StringBuilder mappings = new StringBuilder();
    for (int i = 0; i < 400000; i++) {
      mappings.append("AAAAA,QAASA,UAAS,EAAG;");
    }

    String[] names = new String[5000];
    for (int i = 0; i < 5000; i++) {
      names[i] = "name_" + i;
    }

    String[] sources = new String[1000];
    for (int i = 0; i < 1000; i++) {
      sources[i] = "source_" + i + ".js";
    }

    json =
        new Gson()
            .toJson(
                TestJsonBuilder.create()
                    .setVersion(3)
                    .setFile("testcode")
                    .setLineCount(10000)
                    .setMappings(mappings.toString())
                    .setSources(sources)
                    .setNames(names)
                    .build());
  }

  @Benchmark
  public SourceMapObject testParse() throws SourceMapParseException {
    return SourceMapObjectParser.parse(json);
  }
}
