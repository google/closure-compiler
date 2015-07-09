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

import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

import javax.annotation.Nullable;

/**
 * Represents an callable or newable object in TypeScript.
 */
public class CallSignatureTree extends ParseTree {

  public final boolean isNew;
  public final GenericTypeListTree generics;
  public final FormalParameterListTree formalParameterList;
  @Nullable public final ParseTree returnType;

  public CallSignatureTree(SourceRange location, boolean isNew,
      GenericTypeListTree generics, FormalParameterListTree formalParameterList,
      @Nullable ParseTree returnType) {
    super(ParseTreeType.CALL_SIGNATURE, location);

    this.isNew = isNew;
    this.generics = generics;
    this.formalParameterList = formalParameterList;
    this.returnType = returnType;
  }
}
