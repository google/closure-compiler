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

import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class FunctionCallFuzzer extends Dispatcher {

  FunctionCallFuzzer(FuzzingContext context) {
    super(context);
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.Dispatcher#initCandidates()
   */
  @Override
  protected void initCandidates() {
    CallFuzzer constructorFuzzer =
        new CallFuzzer(context, true);
    CallFuzzer normalCallFuzzer =
        new CallFuzzer(context, false);
    candidates = new AbstractFuzzer[]{constructorFuzzer, normalCallFuzzer};
  }

  private class CallFuzzer extends AbstractFuzzer {
    private int nodeType;
    private CallableExprFuzzer callableExprFuzzer;
    private String configName;
    CallFuzzer(FuzzingContext context, boolean isConstructor) {
      super(context);
      if (isConstructor) {
        nodeType = Token.NEW;
        configName = "constructorCall";
      } else {
        nodeType = Token.CALL;
        configName = "normalCall";
      }
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
     */
    @Override
    protected boolean isEnough(int budget) {
      if (budget < 1) {
        return false;
      } else {
        return getCallableExprFuzzer().isEnough(budget - 1);
      }
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
     */
    @Override
    protected Node generate(int budget, Set<Type> types) {
      int maxParamBudget = budget - 2;
      if (maxParamBudget < 0) {
        maxParamBudget = 0;
      }
      // max number of arguments divided by maxParamBudget
      double argLength = getOwnConfig().get("argLength").getAsDouble();
      int numArgs = context.random.nextInt((int) (maxParamBudget * argLength) + 1);
      AbstractFuzzer[] fuzzers = new AbstractFuzzer[numArgs + 1];
      fuzzers[0] = getCallableExprFuzzer();
      ExpressionFuzzer exprFuzzer =
          new ExpressionFuzzer(context);
      for (int i = 1; i <= numArgs; i++) {
        fuzzers[i] = exprFuzzer;
      }
      Node[] components = distribute(maxParamBudget, fuzzers);
      Node node = new Node(nodeType, components);
      return node;
    }

    /**
     * @return the callableExprFuzzer
     */
    private CallableExprFuzzer getCallableExprFuzzer() {
      if (callableExprFuzzer == null) {
        callableExprFuzzer = new CallableExprFuzzer(context);

      }
      return callableExprFuzzer;
    }

    private class CallableExprFuzzer extends Dispatcher {

      CallableExprFuzzer(FuzzingContext context) {
        super(context);
      }

      /* (non-Javadoc)
       * @see com.google.javascript.jscomp.fuzzing.Dispatcher#initCandidates()
       */
      @Override
      protected void initCandidates() {
        ExistingIdentifierFuzzer idFuzzer = context.strict ?
            new ExistingIdentifierFuzzer(context, Type.FUNCTION, false) :
            new ExistingIdentifierFuzzer(context);
        candidates = new AbstractFuzzer[] {
            idFuzzer,
            new GetPropFuzzer(context),
            new GetElemFuzzer(context),
            new FunctionFuzzer(context, true)
          };
      }

      /* (non-Javadoc)
       * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
       */
      @Override
      protected String getConfigName() {
        return "callableExpr";
      }
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
     */
    @Override
    protected String getConfigName() {
      return configName;
    }
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "functionCall";
  }

}
