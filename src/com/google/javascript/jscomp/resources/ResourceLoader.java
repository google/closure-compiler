/*
 * Copyright 2015 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.resources;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.javascript.jscomp.ConformanceConfig;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Utility class that handles resource loading.
 */
@GwtIncompatible("getResource, java.io.InputStreamReader")
public final class ResourceLoader {
  public static String loadTextResource(Class<?> clazz, String path) {
    try {
      return CharStreams.toString(new InputStreamReader(clazz.getResourceAsStream(path), UTF_8));
    } catch (NullPointerException e) {
      if (!resourceExists(clazz, path)) {
        throw new RuntimeException("No such resource: " + path);
      }
      throw e;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static ImmutableMap<String, String> loadPropertiesMap(
      Class<?> clazz, String resourceName) {
    return PropertiesParser.parse(loadTextResource(clazz, resourceName));
  }

  /** Load the global ConformanceConfig */
  public static ConformanceConfig loadGlobalConformance(Class<?> clazz) {
    ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();
    if (resourceExists(clazz, "global_conformance.binarypb")) {
      try {
        builder.mergeFrom(clazz.getResourceAsStream("global_conformance.binarypb"));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return builder.build();
  }

  public static boolean resourceExists(Class<?> clazz, String path) {
    return clazz.getResource(path) != null;
  }

  private ResourceLoader() {}
}
