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

package com.google.javascript.jscomp.j2clbuild.client;

import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.deps.JsFileFullParser;
import elemental2.core.JsArray;
import java.util.Map;
import javax.annotation.Nullable;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsMethod;
import jsinterop.base.JsPropertyMap;

/**
 * GWT module to parse files for dependency and {@literal @}{@code fileoverview} annotation
 * information.
 */
public class JsfileParserMain {

  /**
   * Method exported to JS to parse a file for dependencies and annotations.
   *
   * <p>The result is a JSON object:
   *
   * <pre> {@code {
   *   "custom_annotations": {?Array<[string, string]>},  @.*
   *   "goog": {?bool},  whether 'goog' is implicitly required
   *   "has_soy_delcalls": {?Array<string>},  @fileoverview @hassoydelcall {.*}
   *   "has_soy_deltemplates": {?Array<string>},  @fileoverview @hassoydeltemplate {.*}
   *   "imported_modules": {?Array<string>},  import ... from .*
   *   "is_config": {?bool},  @fileoverview @config
   *   "is_externs": {?bool},  @fileoverview @externs
   *   "load_flags": {?Array<[string, string]>},
   *   "mod_name": {?Array<string>},  @fileoverview @modName .*, @modName {.*}
   *   "mods": {?Array<string>},  @fileoverview @mods {.*}
   *   "provide_goog": {?bool},  @fileoverview @provideGoog
   *   "provides": {?Array<string>},
   *   "requires": {?Array<string>},  note: look for goog.* for 'goog'
   *   "requires_css": {?Array<string>},  @fileoverview @requirecss {.*}
   *   "testonly": {?bool},  goog.setTestOnly
   *   "type_requires": {?Array<string>},
   *   "visibility: {?Array<string>},  @fileoverview @visibility {.*}
   * }}</pre>
   *
   * <p>Any trivial values are omitted.
   */
  @JsMethod
  public static JsPropertyMap<Object> gjd(
      String code, String filename, @Nullable Reporter reporter) {
    JsFileFullParser.FileInfo info =
        JsFileFullParser.parse(code, filename, adaptReporter(reporter));
    if (info.provideGoog) {
      info.provides.add("goog");
    } else if (info.goog) {
      info.requires.add("goog");
    }

    return new SparseObject()
        .set("custom_annotations", info.customAnnotations)
        .set("goog", info.goog)
        .set("has_soy_delcalls", info.delcalls)
        .set("has_soy_deltemplates", info.deltemplates)
        .set("imported_modules", info.importedModules)
        .set("is_config", info.isConfig)
        .set("is_externs", info.isExterns)
        .set("load_flags", info.loadFlags)
        .set("modName", info.modName)
        .set("mods", info.mods)
        .set("provide_goog", info.provideGoog)
        .set("provides", info.provides)
        .set("requires", info.requires)
        .set("requiresCss", info.requiresCss)
        .set("testonly", info.testonly)
        .set("type_requires", info.typeRequires)
        .set("visibility", info.visibility)
        .object;
  }

  /** JS function interface for reporting errors. */
  @JsFunction
  public interface Reporter {
    void report(boolean fatal, String message, String sourceName, int line, int lineOffset);
  }

  private static JsFileFullParser.Reporter adaptReporter(@Nullable Reporter r) {
    if (r == null) {
      return null;
    }
    return (fatal, message, sourceName, line, lineOffset) -> {
      r.report(fatal, message, sourceName, line, lineOffset);
    };
  }

  /** Sparse object helper class: only adds non-trivial values. */
  private static class SparseObject {
    final JsPropertyMap<Object> object = JsPropertyMap.of();

    SparseObject set(String key, Iterable<String> iterable) {
      JsArray<String> array = new JsArray<>();
      for (String s : iterable) {
        array.push(s);
      }
      if (array.getLength() > 0) {
        object.set(key, array);
      }
      return this;
    }

    SparseObject set(String key, Multimap<String, String> map) {
      JsArray<JsArray<String>> array = new JsArray<>();
      for (Map.Entry<String, String> entry : map.entries()) {
        array.push(new JsArray<>(entry.getKey(), entry.getValue()));
      }
      if (array.getLength() > 0) {
        object.set(key, array);
      }
      return this;
    }

    SparseObject set(String key, String value) {
      if (value != null && !value.isEmpty()) {
        object.set(key, value);
      }
      return this;
    }

    SparseObject set(String key, boolean value) {
      if (value) {
        object.set(key, value);
      }
      return this;
    }
  }

  private JsfileParserMain() {}
}
