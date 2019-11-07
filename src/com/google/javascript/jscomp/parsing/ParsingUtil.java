/*
 * Copyright 2019 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.rhino.Node;
import java.util.function.Consumer;

/** Utility functions for parsing that don't depend on the compiler. */
public final class ParsingUtil {
  // Utility class; do not instantiate.
  private ParsingUtil() {}

  /** Calls {@code cb} with all NAMEs declared in a PARAM_LIST or destructuring pattern. */
  public static void getParamOrPatternNames(Node n, Consumer<Node> cb) {
    checkNotNull(n);
    switch (n.getToken()) {
      case EMPTY: // let [,] = ...
      case GETELEM: // [someArray[1]] = ...
      case GETPROP: // [someObj.someProp] = ...
        // An empty node or assigned to an existing value means nothings here and we can't recurse
        // further.
        break;

      case NAME: // let [someName] = ...
        // A name node generally means we found an assignment happening.
        cb.accept(n);
        break;

      case ITER_REST: // let [...name] = ...
      case OBJECT_REST: // let {...name} = ...
      case STRING_KEY: // let {name} = ...
      case DEFAULT_VALUE: // let {name = 10} = ...
        // For these node types there is a name node as the first child.
        getParamOrPatternNames(n.getFirstChild(), cb);
        break;

      case COMPUTED_PROP: // let {[someProp]: name} = ...
        // For this node types there is a name node as the second child.
        getParamOrPatternNames(n.getSecondChild(), cb);
        break;

      case OBJECT_PATTERN: // let {<pattern>} = ...
      case ARRAY_PATTERN: // let [<pattern>] = ...
      case PARAM_LIST: // function fn(<params>) ...
        // Each item in the destructuring pattern is scanned for assignments. Although PARAM_LIST
        // isn't technically a pattern, we know how to handle it anyway.
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
          getParamOrPatternNames(c, cb);
        }
        break;

      default:
        throw new IllegalStateException("Unexpected parameter structure");
    }
  }
}
