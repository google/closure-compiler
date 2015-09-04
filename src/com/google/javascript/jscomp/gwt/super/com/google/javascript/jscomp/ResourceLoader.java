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

package com.google.javascript.jscomp;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.TextResource;

/**
 * GWT compatible replacement for {@code ResourceLoader}
 *
 */
final class ResourceLoader {
  static interface Libraries extends ClientBundle {
    Libraries INSTANCE = GWT.create(Libraries.class);

    @Source("js/base.js")
    TextResource base();

    @Source("js/es6_runtime.js")
    TextResource es6Runtime();

    @Source("js/runtime_type_check.js")
    TextResource runtimeTypeCheck();
  }

  static String loadTextResource(Class<?> clazz, String path) {
    switch (path) {
      case "js/base.js":
        return Libraries.INSTANCE.base().getText();
      case "js/es6_runtime.js":
        return Libraries.INSTANCE.es6Runtime().getText();
      case "js/runtime_type_check.js":
        return Libraries.INSTANCE.runtimeTypeCheck().getText();
      default:
        throw new RuntimeException("Resource not found " + path);
    }
  }

  static boolean resourceExists(Class<?> clazz, String path) {
    return true; // GWT compilation would have failed otherwise
  }
}
