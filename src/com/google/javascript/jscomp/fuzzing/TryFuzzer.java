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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Arrays;
import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class TryFuzzer extends AbstractFuzzer {

  TryFuzzer(FuzzingContext context) {
    super(context);
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
   */
  @Override
  protected boolean isEnough(int budget) {
    return budget >= 3;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
   */
  @Override
  protected Node generate(int budget, Set<Type> types) {
    AbstractFuzzer[] fuzzers = {
        new BlockFuzzer(context),
        new CatchFuzzer(context),
        new FinallyFuzzer(context)
    };
    Node[] components = distribute(budget - 1, fuzzers);
    if (components[2] == null) {
      if (!components[1].hasChildren()) {
        // no catch or finally, add empty finally block
        components[2] = new Node(Token.BLOCK);
      } else {
        components = Arrays.copyOf(components, 2);
      }
    }
    return new Node(Token.TRY, components);
  }

  private static class CatchFuzzer extends AbstractFuzzer {

    CatchFuzzer(FuzzingContext context) {
      super(context);
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
     */
    @Override
    protected boolean isEnough(int budget) {
      return budget >= 3;
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
     */
    @Override
    protected Node generate(int budget, Set<Type> types) {
      Node catchBlock = new Node(Token.BLOCK);
      if (isEnough(budget)) {
        /**
         * The catch block is in the same scope as the try statement,
         * the catch parameter e is injected into the scope when the block
         * starts and removed from the scope when the block ends.
         */
        Node param =
            new IdentifierFuzzer(context).
            generate(1);
        catchBlock.addChildToBack(
            new Node(Token.CATCH, param,
            new BlockFuzzer(context).generate(budget - 1)));
        context.scopeManager.removeSymbol(param.getQualifiedName());
      }
      return catchBlock;
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
     */
    @Override
    protected String getConfigName() {
      return null;
    }
  }

  private static class FinallyFuzzer extends BlockFuzzer {

    FinallyFuzzer(FuzzingContext context) {
      super(context);
    }

    @Override
    protected Node generate(int budget) {
      if (isEnough(budget)) {
        return super.generate(budget);
      } else {
        return null;
      }
    }

  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "try";
  }
}
