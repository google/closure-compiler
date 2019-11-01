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

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.TextResource;
import com.google.javascript.jscomp.ConformanceConfig;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;

/**
 * GWT compatible replacement for {@code ResourceLoader}.
 */
public final class ResourceLoader {
  static interface Libraries extends ClientBundle {
    Libraries INSTANCE = GWT.create(Libraries.class);

    // This is a generated file containing all the text resources we want to package
    // as a single JSON string mapping (relative) filename to file content strings.
    @Source("com/google/javascript/jscomp/resources/resources.json")
    TextResource resources();
  }

  private static final JsObject RESOURCES = parse(Libraries.INSTANCE.resources().getText());

  public static String loadTextResource(Class<?> clazz, String path) {
    String content = get(RESOURCES, path);
    if (content != null) {
      return content;
    }
    throw new RuntimeException("Resource not found: " + path);
  }

  public static ConformanceConfig loadGlobalConformance(Class<?> clazz) {
    return ConformanceConfig.newBuilder().build();
  }

  public static boolean resourceExists(Class<?> clazz, String path) {
    // TODO(sdh): this is supposed to be relative to the given class, but
    // GWT can't handle that - probably better to remove the class argument
    // and just require that paths be relative to c.g.javascript.jscomp.
    return get(RESOURCES, path) != null;
  }

  public static String[] resourceList(Class<?> clazz) {
    return keys(RESOURCES);
  }

  public static class JsObject {
    public JsObject() {}
  }

  private static native String get(JsObject obj, String key) /*-{
    return obj[key];
  }-*/;

  @JsMethod(namespace = JsPackage.GLOBAL, name = "JSON.parse")
  private static native JsObject parse(String json);

  @JsMethod(namespace = JsPackage.GLOBAL, name = "Object.keys")
  private static native String[] keys(JsObject obj);
}
