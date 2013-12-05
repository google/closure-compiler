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

import com.google.common.base.CaseFormat;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import org.json.JSONObject;

import java.util.Random;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class UnaryExprFuzzer extends Dispatcher {

  UnaryExprFuzzer(Random random, ScopeManager scopeManager,
      JSONObject config, StringNumberGenerator snGenerator) {
    super(random, scopeManager, config, snGenerator);
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.Dispatcher#initCandidates()
   */
  @Override
  protected void initCandidates() {
    Operator[] operators = Operator.values();
    candidates = new UnaryExprGenerator[operators.length];
    for (int i = 0; i < operators.length; i++) {
      candidates[i] = new UnaryExprGenerator(
          random, scopeManager, config, snGenerator, operators[i]);
    }
  }

  private class UnaryExprGenerator extends AbstractFuzzer {
    Operator operator;
    private AbstractFuzzer target;

    UnaryExprGenerator(Random random, ScopeManager scopeManager,
        JSONObject config, StringNumberGenerator snGenerator,
        Operator operator) {
      super(random, scopeManager, config, snGenerator);
      this.operator = operator;
    }

    private AbstractFuzzer getTarget() {
      if (target == null) {
        if (operator.hasSideEffect) {
          target = new AssignableExprFuzzer(
              random, scopeManager, config, snGenerator);
        } else {
          target = new ExpressionFuzzer(
              random, scopeManager, config, snGenerator);
        }
      }
      return target;
    }


    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
     */
    @Override
    protected Node generate(int budget) {
      Node node = new Node(operator.nodeType, getTarget().generate(budget - 1));
      if (operator == Operator.POST_INC || operator == Operator.POST_DEC) {
        node.putBooleanProp(Node.INCRDECR_PROP, true);
      }
      return node;
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
     */
    @Override
    protected boolean isEnough(int budget) {
      if (budget < 1) {
        return false;
      } else {
        return getTarget().isEnough(budget - 1);
      }
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
     */
    @Override
    protected String getConfigName() {
      return CaseFormat.UPPER_UNDERSCORE.to(
          CaseFormat.LOWER_CAMEL, operator.name());
    }

  }

  private enum Operator {
    VOID(Token.VOID),
    TYPEOF(Token.TYPEOF),
    POS(Token.POS),
    NEG(Token.NEG),
    BIT_NOT(Token.BITNOT),
    NOT(Token.NOT),
    INC(Token.INC, true),
    DEC(Token.DEC, true),
    DEL_PROP(Token.DELPROP, true),
    POST_INC(Token.INC, true),
    POST_DEC(Token.DEC, true);

    int nodeType;
    boolean hasSideEffect;
    Operator(int nodeType) {
      this(nodeType, false);
    }

    Operator(int nodeType, boolean hasSideEffect) {
      this.nodeType = nodeType;
      this.hasSideEffect = hasSideEffect;
    }
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "unaryExpr";
  }

}
