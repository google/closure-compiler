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
  private String input;
  private MatchResult result;
  private boolean hasExecuted;
  private int findFromIndex;

  Matcher(RegExp regExp, String input) {
    this.regExp = regExp;
    this.input = input;
    this.result = null;
    this.hasExecuted = false;
    this.findFromIndex = 0;
  }

  public boolean matches() {
    result = regExp.exec(input);
    hasExecuted = true;
    findFromIndex = 0;

    if (result != null) {
      String match = result.getGroup(0);
      if (match.equals(input)) {
        return true;
      }
      result = null;  // matches() needs to match whole string, pretend we didn't match
    }
    return false;
  }

  public boolean find() {
    result = regExp.exec(input.substring(findFromIndex));
    hasExecuted = true;

    if (result != null) {
      findFromIndex += result.getGroup(0).length();
      return true;
    }
    return false;
  }

  public String group(int index) {
    if (!hasExecuted) {
      throw new IllegalStateException("regex not executed yet");
    } else if (result == null) {
      throw new IllegalStateException("regex did not match");
    } else {
      return result.getGroup(index);
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
    hasExecuted = false;
    findFromIndex = 0;
    return this;
  }

  public Matcher reset(String input) {
    this.input = input;
    return reset();
  }
}
