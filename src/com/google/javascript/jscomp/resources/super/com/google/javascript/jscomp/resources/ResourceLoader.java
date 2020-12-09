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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.ConformanceConfig;
import jsinterop.annotations.JsType;
import jsinterop.base.JsPropertyMap;

/**
 * J2CL compatible replacement for {@code ResourceLoader}.
 */
public final class ResourceLoader {
  @JsType(isNative = true, namespace = "com.google.javascript.jscomp")
  private static class Resources {
    public static native JsPropertyMap<Object> resources();
  }

  public static String loadTextResource(Class<?> clazz, String path) {
    if (Resources.resources().has(path)) {
      return (String) Resources.resources().get(path);
    }
    throw new RuntimeException("Resource not found: " + path);
  }

  public static ImmutableMap<String, String> loadPropertiesMap(String resourceName) {
    return PropertiesParser.parse(loadTextResource(null, resourceName));
  }

  public static ConformanceConfig loadGlobalConformance(Class<?> clazz) {
    return ConformanceConfig.newBuilder().build();
  }

  public static boolean resourceExists(Class<?> clazz, String path) {
    // TODO(sdh): this is supposed to be relative to the given class, but
    // GWT can't handle that - probably better to remove the class argument
    // and just require that paths be relative to c.g.javascript.jscomp.
    return Resources.resources().has(path);
  }
}
