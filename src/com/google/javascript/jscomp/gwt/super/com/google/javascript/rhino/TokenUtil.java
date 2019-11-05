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

package com.google.javascript.rhino;

import com.google.javascript.rhino.jstype.TernaryValue;
import elemental2.core.JsRegExp;
import jsinterop.base.Js;

/**
 * Helper methods for parsing JavaScript.
 */
public class TokenUtil {
  private static final JsRegExp WHITE_SPACE_REGEX = new JsRegExp("\\s");

  public static boolean isJSSpace(int c) {
    if (c <= 127) {
      return c == 0x20 || c == 0x9 || c == 0xC || c == 0xB;
    } else {
      return c == 0xA0; // TODO(moz): Correct this.
    }
  }

  public static boolean isJSFormatChar(int c) {
    return c > 127; // TODO(moz): Correct this.
  }

  public static boolean isWhitespace(int c) {
    return WHITE_SPACE_REGEX.test(Js.uncheckedCast(c));
  };

  public static TernaryValue isStrWhiteSpaceChar(int c) {
    switch (c) {
      case '\u000B': // <VT>
        return TernaryValue.UNKNOWN;  // Legacy IE says "no", ECMAScript says "yes"
      case ' ': // <SP>
      case '\n': // <LF>
      case '\r': // <CR>
      case '\t': // <TAB>
      case '\u00A0': // <NBSP>
      case '\u000C': // <FF>
      case '\u2028': // <LS>
      case '\u2029': // <PS>
      case '\uFEFF': // <BOM>
        return TernaryValue.TRUE;
      default:
        return TernaryValue.FALSE; // TODO(moz): Correct this.
    }
  }
}
