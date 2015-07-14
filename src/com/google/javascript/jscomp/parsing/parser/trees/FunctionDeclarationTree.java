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

import com.google.javascript.jscomp.parsing.parser.IdentifierToken;
import com.google.javascript.jscomp.parsing.parser.TokenType;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

import javax.annotation.Nullable;

public class FunctionDeclarationTree extends ParseTree {

  public static enum Kind {
    DECLARATION,
    EXPRESSION,
    MEMBER,
    ARROW
  }

  public final IdentifierToken name;
  public final GenericTypeListTree generics;
  public final FormalParameterListTree formalParameterList;
  @Nullable public final ParseTree returnType;
  public final ParseTree functionBody;
  public final boolean isStatic;
  public final boolean isGenerator;
  public final boolean isOptional;
  @Nullable public final TokenType access;
  public final Kind kind;

  public FunctionDeclarationTree(SourceRange location, IdentifierToken name,
      GenericTypeListTree generics, boolean isStatic,
      boolean isGenerator, boolean isOptional, @Nullable TokenType access, Kind kind,
      FormalParameterListTree formalParameterList, @Nullable ParseTree returnType,
      ParseTree functionBody) {
    super(ParseTreeType.FUNCTION_DECLARATION, location);

    this.name = name;
    this.generics = generics;
    this.isStatic = isStatic;
    this.isGenerator = isGenerator;
    this.isOptional = isOptional;
    this.access = access;
    this.kind = kind;
    this.formalParameterList = formalParameterList;
    this.returnType = returnType;
    this.functionBody = functionBody;
  }
}
