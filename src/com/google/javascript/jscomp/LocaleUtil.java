/*
 * Copyright 2009 The Closure Compiler Authors.
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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locale utilities.
 *
 * Class duplicated from the i18n.LocaleUtil class.
 *
 * @author anatol@google.com (Anatol Pomazau)
 */
class LocaleUtil {
  private static final Pattern LOCALE_PATTERN =
      Pattern.compile("(^[^_-]*)(?:[_-]([^_-]*)(?:[_-]([^_-]*))?)?");

  /**
   * Convert a locale string from the RFC 3066 standard format
   * to the Java locale format. You can call this on
   * any locale string obtained from an external source
   * (cookie, URL parameter, header, etc.). This method accepts
   * more than just the standard format and will also tolerate
   * capitalization discrepancies and the use of an underscore
   * in place of a hyphen.
   *
   * @param s String representation for locale.
   * @return Parsed locale.
   */
  static Locale getLocaleFromStandardLocaleString(String s) {
    if (s == null) {
      return null;
    }

    Matcher matcher = LOCALE_PATTERN.matcher(s);

    // LOCALE_PATTERN will match any string, though it may not match the whole
    // string.  Specifically, it will not match a third _ or - or any
    // subsequent text.
    matcher.find();

    String language = Strings.makeSafe(matcher.group(1));
    String country = Strings.makeSafe(matcher.group(2));
    String variant = Strings.makeSafe(matcher.group(3));

    return new Locale(language, country, variant);
  }
}
