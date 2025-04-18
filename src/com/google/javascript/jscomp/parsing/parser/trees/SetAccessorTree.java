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

public class SetAccessorTree extends ParseTree {

  public final Token propertyName;
  public final FormalParameterListTree parameter;
  public final boolean isClassMember;
  public final boolean isStatic;
  public final BlockTree body;

  public SetAccessorTree(
      SourceRange location,
      Token propertyName,
      boolean isClassMember,
      boolean isStatic,
      FormalParameterListTree parameter,
      BlockTree body) {
    super(ParseTreeType.SET_ACCESSOR, location);

    this.propertyName = propertyName;
    this.isClassMember = isClassMember;
    this.isStatic = isStatic;
    this.parameter = parameter;
    this.body = body;
  }
}
