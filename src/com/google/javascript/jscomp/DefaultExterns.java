/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contains information on default externs files. Provide {@link DefaultExterns#prepareExterns} to
 * filter externs source files.
 */
public final class DefaultExterns {
  private DefaultExterns() {}

  // Core language externs. When the environment is CUSTOM, only these externs will be included.
  private static final List<String> BUILTIN_LANG_EXTERNS = ImmutableList.of(
      "es3.js",
      "es5.js",
      "es6.js",
      "es6_collections.js");

  // Ordered browser externs. Externs not included in this list are added last.
  private static final List<String> BROWSER_EXTERN_DEP_ORDER = ImmutableList.of(
      //-- browser externs --
      "intl.js",
      "w3c_event.js",
      "w3c_event3.js",
      "gecko_event.js",
      "ie_event.js",
      "webkit_event.js",
      "w3c_device_sensor_event.js",
      "w3c_dom1.js",
      "w3c_dom2.js",
      "w3c_dom3.js",
      "w3c_dom4.js",
      "gecko_dom.js",
      "ie_dom.js",
      "webkit_dom.js",
      "w3c_css.js",
      "gecko_css.js",
      "ie_css.js",
      "webkit_css.js",
      "w3c_touch_event.js");

  /**
   * Filters and orders the passed externs for the specified environment.
   *
   * @param env The environment being used.
   * @param externs Flat tilename to source externs map. Must be mutable and will be modified.
   * @return Ordered list of externs.
   */
  public static List<SourceFile> prepareExterns(CompilerOptions.Environment env,
        Map<String, SourceFile> externs) {
    List<SourceFile> out = new ArrayList<>();

    for (String key : BUILTIN_LANG_EXTERNS) {
      Preconditions.checkState(externs.containsKey(key), "Externs must contain builtin: %s", key);
      out.add(externs.remove(key));
    }

    if (env == CompilerOptions.Environment.BROWSER) {
      for (String key : BROWSER_EXTERN_DEP_ORDER) {
        Preconditions.checkState(externs.containsKey(key),
            "Externs must contain builtin for env %s: %s", env, key);
        out.add(externs.remove(key));
      }

      out.addAll(externs.values());
    }

    return out;
  }

}
