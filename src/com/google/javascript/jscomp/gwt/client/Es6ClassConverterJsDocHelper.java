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
package com.google.javascript.jscomp.gwt.client;

import com.google.common.base.Strings;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.regexp.shared.RegExp;
import com.google.javascript.jscomp.JSDocInfoPrinter;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.jscomp.parsing.JsDocTokenStream;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import jsinterop.annotations.JsMethod;

/**
 * Utilities to parse JS doc and assist in upgrade ES5 to ES6 classes where JS Doc must be split
 * into class and constructor comments.
 */
final class Es6ClassConverterJsDocHelper implements EntryPoint {

  private Es6ClassConverterJsDocHelper() {}

  @Override
  public void onModuleLoad() {
    exportCode();
  }

  private native void exportCode() /*-{
    var getClassJsDoc = $entry(@com.google.javascript.jscomp.gwt.client.Es6ClassConverterJsDocHelper::getClassJsDoc(*));
    var getConstructorJsDoc = $entry(@com.google.javascript.jscomp.gwt.client.Es6ClassConverterJsDocHelper::getConstructorJsDoc(*));
    if (typeof module !== 'undefined' && module.exports) {
      module.exports.getClassJsDoc = getClassJsDoc;
      module.exports.getConstructorJsDoc = getConstructorJsDoc;
    }
  }-*/;

  /**
   * Gets JS Doc that should be retained on a class. Used to upgrade ES5 to ES6 classes and separate
   * class from constructor comments.
   */
  @JsMethod(name = "getClassJsDoc", namespace = "jscomp")
  public static String getClassJsDoc(String jsDoc) {
    if (Strings.isNullOrEmpty(jsDoc)) {
      return null;
    }
    Config config =
        Config.builder()
            .setLanguageMode(LanguageMode.ECMASCRIPT3)
            .setStrictMode(Config.StrictMode.SLOPPY)
            .setJsDocParsingMode(JsDocParsing.INCLUDE_DESCRIPTIONS_WITH_WHITESPACE)
            .build();
    JsDocInfoParser parser =
        new JsDocInfoParser(
            // Stream expects us to remove the leading /**
            new JsDocTokenStream(jsDoc.substring(3)),
            jsDoc,
            0,
            null,
            config,
            ErrorReporter.NULL_INSTANCE);
    parser.parse();
    JSDocInfo parsed = parser.retrieveAndResetParsedJSDocInfo();
    JSDocInfo classComments = parsed.cloneClassDoc();
    JSDocInfoPrinter printer =
        new JSDocInfoPrinter(/* useOriginalName= */ true, /* printDesc= */ true);
    String comment = printer.print(classComments);
    // Don't return empty comments, return null instead.
    if (comment == null || RegExp.compile("\\s*/\\*\\*\\s*\\*/\\s*").test(comment)) {
      return null;
    }
    return comment.trim();
  }

  /**
   * Gets JS Doc that should be moved to a constructor. Used to upgrade ES5 to ES6 classes and
   * separate class from constructor comments.
   */
  @JsMethod(name = "getConstructorJsDoc", namespace = "jscomp")
  public static String getConstructorJsDoc(String jsDoc) {
    if (Strings.isNullOrEmpty(jsDoc)) {
      return null;
    }
    Config config =
        Config.builder()
            .setLanguageMode(LanguageMode.ECMASCRIPT3)
            .setStrictMode(Config.StrictMode.SLOPPY)
            .setJsDocParsingMode(JsDocParsing.INCLUDE_DESCRIPTIONS_WITH_WHITESPACE)
            .build();
    JsDocInfoParser parser =
        new JsDocInfoParser(
            // Stream expects us to remove the leading /**
            new JsDocTokenStream(jsDoc.substring(3)),
            jsDoc,
            0,
            null,
            config,
            ErrorReporter.NULL_INSTANCE);
    parser.parse();
    JSDocInfo parsed = parser.retrieveAndResetParsedJSDocInfo();
    JSDocInfo params = parsed.cloneConstructorDoc();
    if (parsed.getParameterNames().isEmpty()
        && parsed.getSuppressions().isEmpty()
        && !parsed.isNgInject()) {
      return null;
    }
    JSDocInfoPrinter printer =
        new JSDocInfoPrinter(/* useOriginalName= */ true, /* printDesc= */ true);
    return printer.print(params).trim();
  }
}
