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

package com.google.javascript.jscomp.parsing.parser;

/**
 * The set of all non-keyword, non-reserved words used in javascript.
 */
public final class PredefinedName {

  private PredefinedName() {}

  public static final String AS = "as";
  public static final String FROM = "from";
  public static final String GET = "get";
  public static final String OF = "of";
  public static final String SET = "set";
}
