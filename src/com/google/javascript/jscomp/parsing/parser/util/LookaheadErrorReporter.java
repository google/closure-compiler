/*
 * Copyright 2011 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing.parser.util;

/**
 * An error reporter that throws exceptions for parse errors. Used
 * for speculative parses.
 */
public class LookaheadErrorReporter extends ErrorReporter {
  public static class ParseException extends RuntimeException {}

  @Override
  protected void reportMessage(
      SourcePosition location, String kind, String format, Object... arguments) {
    throw new ParseException();
  }

  @Override
  protected void reportMessage(SourcePosition location, String message) {
    throw new ParseException();
  }
}
