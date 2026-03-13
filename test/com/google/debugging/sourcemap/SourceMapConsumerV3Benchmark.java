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

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.Gson;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SourceMapConsumerV3Benchmark {
  private static final Gson GSON = new Gson();

  @Test
  public void testMemoryUsage() throws Exception {
    StringBuilder mappings = new StringBuilder();
    for (int i = 0; i < 1000000; i++) {
      mappings.append("AAAAA,QAASA,UAAS,EAAG;");
    }

    String json =
        GSON.toJson(
            TestJsonBuilder.create()
                .setVersion(3)
                .setFile("testcode")
                .setLineCount(1000000)
                .setMappings(mappings.toString())
                .setSources("testcode")
                .setNames("__BASIC__")
                .build());

    System.gc();
    Thread.sleep(100);
    long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long startAllocated = threadMxBean.getThreadAllocatedBytes(Thread.currentThread().threadId());

    SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
    consumer.parse(json);

    long endAllocated = threadMxBean.getThreadAllocatedBytes(Thread.currentThread().threadId());

    System.gc();
    Thread.sleep(100);
    long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    long memoryUsed = (after - before) / 1024 / 1024;
    System.out.println("Memory used: " + memoryUsed + " MB");
    assertThat(memoryUsed).isLessThan(70);

    long allocatedBytes = (endAllocated - startAllocated) / 1024 / 1024;
    System.out.println("Allocated memory: " + allocatedBytes + " MB");
    assertThat(allocatedBytes).isLessThan(180);
  }
}
