/*
 * Copyright 2013 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.fuzzing;

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class ExistingIdentifierFuzzer extends AbstractFuzzer {
  Type type;
  boolean excludeExterns;

  ExistingIdentifierFuzzer(FuzzingContext context) {
    super(context);
  }

  ExistingIdentifierFuzzer(FuzzingContext context,
      Type type, boolean excludeExterns) {
    super(context);
    this.type = type;
    this.excludeExterns = excludeExterns;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
   */
  @Override
  protected Node generate(int budget, Set<Type> types) {
    Preconditions.checkState(
        isEnough(1),
        "No symbol defined.");
    int flags = 0;
    if (excludeExterns) {
      flags = ScopeManager.EXCLUDE_EXTERNS;
    }
    return Node.newString(
        Token.NAME, context.scopeManager.getRandomSymbol(type, flags).name);
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
   */
  @Override
  protected boolean isEnough(int budget) {
    return (context.scopeManager.getSize() > 0 || !excludeExterns)
        && budget >= 1;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "existingIdentifier";
  }

}
