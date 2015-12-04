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

import com.google.gwt.regexp.shared.MatchResult;
import com.google.gwt.regexp.shared.RegExp;

/**
 * GWT-compatible minimal replacement for {@code Matcher}
 *
 * @author moz@google.com (Michael Zhou)
 */
public class Matcher {
  private final RegExp regExp;
  private final String input;
  private MatchResult result;
  private boolean hasExecuted;
  private int currIndex;

  Matcher(RegExp regExp, String input) {
    this.regExp = regExp;
    this.input = input;
    this.result = null;
    this.currIndex = 0;
    this.hasExecuted = false;
  }

  private boolean maybeExec() {
    if (!hasExecuted) {
      result = regExp.exec(input);
      currIndex = 0;
      hasExecuted = true;
    }
    return result != null;
  }

  public boolean matches() {
    if (maybeExec()) {
      String match = result.getGroup(0);
      return match != null && match.equals(input);
    } else {
      return false;
    }
  }

  public boolean find() {
    if (maybeExec()) {
      String match = result.getGroup(currIndex);
      currIndex++;
      return match != null && !match.isEmpty();
    } else {
      return false;
    }
  }

  public String group(int index) {
    if (maybeExec()) {
      return result.getGroup(index);
    } else {
      return null;
    }
  }

  public static String quoteReplacement(String input) {
    return RegExp.quote(input);
  }

  public String replaceAll(String replacement) {
    return RegExp.compile(regExp.getSource(), "g").replace(input, replacement);
  }

  public Matcher reset() {
    result = null;
    currIndex = 0;
    hasExecuted = false;
    return this;
  }
}
