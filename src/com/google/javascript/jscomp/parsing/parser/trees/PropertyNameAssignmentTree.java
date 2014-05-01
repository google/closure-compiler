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

package com.google.javascript.jscomp.parsing.parser.trees;

import com.google.javascript.jscomp.parsing.parser.Token;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

public class PropertyNameAssignmentTree extends ParseTree {
  public final Token name;

  // May be null in ES6 and above.
  public final ParseTree value;

  public PropertyNameAssignmentTree(SourceRange location, Token name, ParseTree value) {
    super(ParseTreeType.PROPERTY_NAME_ASSIGNMENT, location);

    this.name = name;
    this.value = value;
  }

}
