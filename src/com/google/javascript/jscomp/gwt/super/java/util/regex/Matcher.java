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

package java.util.regex;

import elemental2.core.JsRegExp;
import elemental2.core.JsString;
import elemental2.core.RegExpResult;

/** GWT-compatible minimal replacement for {@code Matcher} */
public class Matcher {

  private final JsRegExp regExp;
  private final JsRegExp regExpGlobal;

  private String input;

  Matcher(JsRegExp regExp, String input) {
    this.regExp = regExp;
    this.regExpGlobal = new JsRegExp(regExp.source, "g");
    this.input = input;
  }

  public boolean matches() {
    RegExpResult result = regExp.exec(input);

    if (result != null) {
      String match = result.getAt(0);
      if (match.equals(input)) {
        return true;
      }
    }
    return false;
  }

  public boolean find() {
    return regExpGlobal.exec(input) != null;
  }

  public static String quoteReplacement(String input) {
    return Pattern.quote(input);
  }

  public String replaceAll(String replacement) {
    return new JsString(input).replace(new JsRegExp(regExp.source, "g"), replacement).toString();
  }

  public Matcher reset() {
    regExp.lastIndex = 0;
    regExpGlobal.lastIndex = 0;
    return this;
  }

  public Matcher reset(String input) {
    this.input = input;
    return reset();
  }
}
