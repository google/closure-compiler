/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;

/**
 * Holds additional information for all function nodes defined in a JavaScript program.
 *
 * @author rluble@google.com (Roberto Lublinerman)
 */

interface FunctionNames {

  Iterable<Node> getFunctionNodeList();

  /**
   * Globaly unique id for {@code function}.
   */
  int getFunctionId(Node function);

  /**
   * Fully qualified name {@code function}.
   */
  String getFunctionName(Node function);
}
