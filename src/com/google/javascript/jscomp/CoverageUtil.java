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

/**
 * This class contains all general purpose static methods that are used by
 * coverage instrumentation related classes - not limited to them, though.
 * @author praveenk@google.com (Praveen Kumashi)
 */
class CoverageUtil {
  /**
   * Utility Class: do not instantiate.
   */
  private CoverageUtil() {}

  /**
   * Returns a string with all non-alphanumeric characters in the given string
   * replaced with underscrores. This is to create a valid identifier based on
   * the given text.
   * @param inputText the text to create an identifier from
   * @return the new string that can be used as in identifier
   */
  static String createIdentifierFromText(String inputText) {
    return inputText.replaceAll("[^\\p{Alnum}]", "_");
  }
}
