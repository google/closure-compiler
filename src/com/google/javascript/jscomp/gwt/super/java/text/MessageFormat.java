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

package java.text;

/**
 * A simple MessageFormat that only supports string replacement.
 *
 * @author moz@google.com (Michael Zhou)
 */
public class MessageFormat {
  private String pattern;

  public MessageFormat(String pattern) {
    applyPattern(pattern);
  }

  public void applyPattern(String pattern) {
    this.pattern = pattern;
  }

  public static String format(String pattern, Object... args) {
    return justFormat(pattern, args);
  }

  public final String format(Object obj) {
    if (obj instanceof Object[]) {
      return justFormat(pattern, (Object[]) obj);
    } else {
      return justFormat(pattern, new Object[] {obj});
    }
  }

  private static String justFormat(String s, Object... args) {
    for (int i = 0; i < args.length; i++) {
      String toReplace = "{" + i + "}";
      while (s.contains(toReplace)) {
        s = s.replace(toReplace, String.valueOf(args[i]));
      }
    }
    return s;
  }
}
