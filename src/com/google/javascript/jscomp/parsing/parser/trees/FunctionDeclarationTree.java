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

import static com.google.common.base.Preconditions.checkNotNull;

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

  @Nullable public final IdentifierToken name;
  @Nullable public final GenericTypeListTree generics;
  public final FormalParameterListTree formalParameterList;
  @Nullable public final ParseTree returnType;
  public final ParseTree functionBody;
  public final boolean isStatic;
  public final boolean isGenerator;
  public final boolean isOptional;
  public final boolean isAsync;
  @Nullable public final TokenType access;
  public final Kind kind;

  public static Builder builder(Kind kind) {
    return new Builder(kind);
  }

  private FunctionDeclarationTree(Builder builder) {
    super(ParseTreeType.FUNCTION_DECLARATION, builder.location);

    this.name = builder.name;
    this.generics = builder.generics;
    this.isStatic = builder.isStatic;
    this.isGenerator = builder.isGenerator;
    this.isOptional = builder.isOptional;
    this.access = builder.access;
    this.kind = checkNotNull(builder.kind);
    this.formalParameterList = checkNotNull(builder.formalParameterList);
    this.returnType = builder.returnType;
    this.functionBody = checkNotNull(builder.functionBody);
    this.isAsync = builder.isAsync;
  }

  /**
   * Builds a {@link FunctionDeclarationTree}.
   */
  public static class Builder {
    private final Kind kind;

    @Nullable private IdentifierToken name = null;
    @Nullable private GenericTypeListTree generics = null;
    @Nullable private FormalParameterListTree formalParameterList = null;
    @Nullable private ParseTree returnType = null;
    @Nullable private ParseTree functionBody = null;
    @Nullable private TokenType access = null;
    private boolean isStatic = false;
    private boolean isGenerator = false;
    private boolean isOptional = false;
    private boolean isAsync = false;
    private SourceRange location;

    Builder(Kind kind) {
      this.kind = kind;
    }

    /**
     * Optional function name.
     *
     * <p> Default is {@code null}.
     */
    public Builder setName(IdentifierToken name) {
      this.name = name;
      return this;
    }

    /**
     * Optional generics information.
     *
     * <p> Default is {@code null}.
     */
    public Builder setGenerics(GenericTypeListTree generics) {
      this.generics = generics;
      return this;
    }

    /**
     * Required parameter list.
     */
    public Builder setFormalParameterList(FormalParameterListTree formalParameterList) {
      this.formalParameterList = formalParameterList;
      return this;
    }

    /**
     * Optional return type.
     *
     * <p> Default is {@code null}.
     */
    public Builder setReturnType(ParseTree returnType) {
      this.returnType = returnType;
      return this;
    }

    /**
     * Required function body.
     */
    public Builder setFunctionBody(ParseTree functionBody) {
      this.functionBody = functionBody;
      return this;
    }

    /**
     * Optional TypeScript accessibility modifier (PUBLIC, PROTECTED, PRIVATE).
     *
     * <p> Default is {@code null}.
     * Only relevant for method member declaration.
     */
    public Builder setAccess(TokenType access) {
      this.access = access;
      return this;
    }

    /**
     * Is the method static?
     *
     * <p> Default is {@code false}.
     * Only relevant for method member declarations.
     */
    public Builder setStatic(boolean isStatic) {
      this.isStatic = isStatic;
      return this;
    }

    /**
     * Is this a generator function?
     *
     * <p> Default is {@code false}.
     */
    public Builder setGenerator(boolean isGenerator) {
      this.isGenerator = isGenerator;
      return this;
    }

    /**
     * Is this the declaration of an optional function parameter? Default is {@code false}.
     *
     * <p> Only relevant for function declaration as a parameter to another function.
     */
    public Builder setOptional(boolean isOptional) {
      this.isOptional = isOptional;
      return this;
    }

    /**
     * Is this an asynchronous function?
     *
     * <p> Default is {@code false}.
     */
    public Builder setAsync(boolean isAsync) {
      this.isAsync = isAsync;
      return this;
    }

    /**
     * Return a new {@link FunctionDeclarationTree}.
     *
     * <p> The location is provided at this point because it cannot be correctly calculated
     * until the whole function has been parsed.
     */
    public FunctionDeclarationTree build(SourceRange location) {
      this.location = location;
      return new FunctionDeclarationTree(this);
    }
  }
}
