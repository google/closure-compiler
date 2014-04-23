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

import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class LiteralFuzzer extends Dispatcher {

  LiteralFuzzer(FuzzingContext context) {
    super(context);
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.Dispatcher#initCandidates()
   */
  @Override
  protected void initCandidates() {
    candidates = new AbstractFuzzer[]{
        new SimpleFuzzer(Token.NULL, "null", Type.OBJECT),
        // treating global values as literal
        new GlobalValueFuzzer("undefined", Type.UNDEFINED),
        new GlobalValueFuzzer("Infinity", Type.NUMBER),
        new GlobalValueFuzzer("NaN", Type.NUMBER),
        new BooleanFuzzer(context),
        new NumericFuzzer(context),
        new StringFuzzer(context),
        new ArrayFuzzer(context),
        new RegularExprFuzzer(context),
        new ObjectFuzzer(context),
      };
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "literal";
  }

  private static class GlobalValueFuzzer extends AbstractFuzzer {
    private String value;
    private Type type;
    GlobalValueFuzzer(String value, Type type) {
      super(null);
      this.value = value;
      this.type = type;
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
     */
    @Override
    protected boolean isEnough(int budget) {
      return budget >= 1;
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
     */
    @Override
    protected Node generate(int budget, Set<Type> types) {
      return Node.newString(Token.NAME, value);
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
     */
    @Override
    protected String getConfigName() {
      return value;
    }

    @Override
    protected Set<Type> supportedTypes() {
      return Sets.immutableEnumSet(type);
    }
  }
}
