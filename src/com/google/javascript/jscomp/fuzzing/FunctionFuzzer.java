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

import java.util.Arrays;
import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class FunctionFuzzer extends AbstractFuzzer {
  private boolean isExpression;
  private IdentifierFuzzer idFuzzer;
  public FunctionFuzzer(FuzzingContext context,
      boolean isExpression) {
    super(context);
    this.isExpression = isExpression;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
   */
  @Override
  protected boolean isEnough(int budget) {
    if (isExpression) {
      return budget >= 3;
    } else {
      return budget >= 4;
    }
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
   */
  @Override
  protected Node generate(int budget, Set<Type> types) {
    int paramBodyBudget;
    Node name;
    ScopeManager scopeManager = context.scopeManager;
    if (isExpression) {
      Preconditions.checkArgument(budget >= 3);
      scopeManager.addScope();
      if (budget >= 4 && context.random.nextInt(2) == 0) {
        // the name of function expression is only visible in the function
        name = getIdFuzzer().generate(1);
        paramBodyBudget = budget - 3;
      } else {
        name = Node.newString(Token.NAME, "");
        paramBodyBudget = budget - 2;
      }
    } else {
      Preconditions.checkArgument(budget >= 4);
      name = getIdFuzzer().generate(1);
      paramBodyBudget = budget - 3;
      scopeManager.addScope();
    }
    int numParams = context.random.nextInt(getOwnConfig().get("maxParams").getAsInt() + 1);
    int bodyBudget = paramBodyBudget - numParams - 1;
    Node params =
        new ParamListFuzzer(context).
        generate(numParams);

    int numStmts =
        generateLength(bodyBudget);
    if (numStmts < 1) {
      // guarantee that the function body has at least a statement
      numStmts = 1;
    }
    AbstractFuzzer[] fuzzers = new AbstractFuzzer[numStmts];
    Arrays.fill(
        fuzzers,
        new SourceElementFuzzer(context));
    Node[] components = distribute(paramBodyBudget, fuzzers);
    Node body = new Node(Token.BLOCK);
    for (int i = 0; i < numStmts; i++) {
      body.addChildToBack(components[i]);
    }
    scopeManager.removeScope();
    return new Node(Token.FUNCTION,
        name, params, body);
  }

  /**
   * @return the idFuzzer
   */
  private IdentifierFuzzer getIdFuzzer() {
    if (idFuzzer == null) {
      idFuzzer = new IdentifierFuzzer(context);
    }
    return idFuzzer;
  }

  private class ParamListFuzzer extends AbstractFuzzer {

    ParamListFuzzer(FuzzingContext context) {
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
    protected Node generate(int budget, Set<Type> types) {
      Node node = new Node(Token.PARAM_LIST);
      for (int i = 0; i < budget - 1; i++) {
        node.addChildToBack(getIdFuzzer().generate(1));
      }
      return node;
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
     */
    @Override
    protected String getConfigName() {
      return null;
    }

  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "function";
  }
}
