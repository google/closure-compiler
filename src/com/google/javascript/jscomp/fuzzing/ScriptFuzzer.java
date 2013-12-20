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

import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Arrays;
import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
public class ScriptFuzzer extends AbstractFuzzer {

  ScriptFuzzer(FuzzingContext context) {
    super(context);
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
  public Node generate(int budget, Set<Type> types) {
    int numElements = generateLength(budget - 1);
    Node script;
    if (numElements > 0) {
      SourceElementFuzzer[] fuzzers = new SourceElementFuzzer[numElements];
      Arrays.fill(
          fuzzers,
          new SourceElementFuzzer(context));
      Node[] elements = distribute(budget - 1, fuzzers);
      script = new Node(Token.SCRIPT, elements);
    } else {
      script = new Node(Token.SCRIPT);
    }

    InputId inputId = new InputId("fuzzedInput");
    script.setInputId(inputId);
    script.setSourceFileForTesting(inputId.getIdName());
    return script;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "script";
  }
}
