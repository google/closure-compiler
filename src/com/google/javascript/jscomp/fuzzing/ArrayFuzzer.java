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

import java.util.Arrays;
import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class ArrayFuzzer extends AbstractFuzzer {

  ArrayFuzzer(FuzzingContext context) {
    super(context);
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int, Set<Type>)
   */
  @Override
  protected Node generate(int budget, Set<Type> types) {
    Node node = new Node(Token.ARRAYLIT);
    if (budget < 1) {
      budget = 1;
    }
    int arraySize = generateLength(budget - 1);
    if (arraySize > 0) {
      AbstractFuzzer[] fuzzers = new AbstractFuzzer[arraySize];
      Arrays.fill(fuzzers, new ExpressionFuzzer(context));
      Node[] elements = distribute(budget - 1, fuzzers);
      for (int i = 0; i < arraySize; i++) {
        node.addChildToBack(elements[i]);
      }
    }
    return node;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
   */
  @Override
  protected boolean isEnough(int budget) {
    return budget >= 1;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "array";
  }

  @Override
  protected Set<Type> supportedTypes() {
    return Sets.immutableEnumSet(Type.ARRAY);
  }

}
