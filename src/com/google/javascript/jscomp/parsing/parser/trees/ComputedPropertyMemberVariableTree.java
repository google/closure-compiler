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
package com.google.javascript.jscomp.parsing.parser.trees;

import com.google.javascript.jscomp.parsing.parser.TokenType;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

import javax.annotation.Nullable;

/**
 * Represents a member variable with a computed property name.
 */
public class ComputedPropertyMemberVariableTree extends ParseTree {
  public final ParseTree property;
  public final boolean isStatic;
  @Nullable public final TokenType access;
  @Nullable public final ParseTree declaredType;

  public ComputedPropertyMemberVariableTree(SourceRange location, ParseTree property,
      boolean isStatic,  @Nullable TokenType access, @Nullable ParseTree declaredType) {
    super(ParseTreeType.COMPUTED_PROPERTY_MEMBER_VARIABLE, location);

    this.property = property;
    this.isStatic = isStatic;
    this.access = access;
    this.declaredType = declaredType;
  }
}
