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

import elemental2.core.RegExp;
import jsinterop.annotations.JsMethod;

/**
 * GWT compatible minimal emulation of {@code Pattern}
 *
 * @author moz@google.com (Michael Zhou)
 */
public final class Pattern {
  private RegExp regExp;

  private Pattern() {}

  public static Pattern compile(String string) {
    Pattern pattern = new Pattern();
     pattern.regExp = new RegExp(string);
    return pattern;
  }

  @JsMethod
  public static native String quote(String input) /*-{
    return input.replace(/[.?*+^$[\]\\(){}|-]/g, '\\$&');
  }-*/;

  public Matcher matcher(String string) {
    return new Matcher(regExp, string);
  }
}
