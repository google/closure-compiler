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
import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class UnaryExprFuzzer extends Dispatcher {

  UnaryExprFuzzer(FuzzingContext context) {
    super(context);
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.Dispatcher#initCandidates()
   */
  @Override
  protected void initCandidates() {
    Operator[] operators = Operator.values();
    candidates = new UnaryExprGenerator[operators.length];
    for (int i = 0; i < operators.length; i++) {
      candidates[i] = new UnaryExprGenerator(context, operators[i]);
    }
  }

  private static class UnaryExprGenerator extends AbstractFuzzer {
    Operator operator;
    private AbstractFuzzer target;

    UnaryExprGenerator(FuzzingContext context,
        Operator operator) {
      super(context);
      this.operator = operator;
    }

    private AbstractFuzzer getTarget() {
      if (target == null) {
        if (operator.hasSideEffect) {
          target = new AssignableExprFuzzer(context);
        } else {
          target = new ExpressionFuzzer(context);
        }
      }
      return target;
    }


    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
     */
    @Override
    protected Node generate(int budget, Set<Type> types) {
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

    @Override
    protected Set<Type> supportedTypes() {
      return operator.supportedTypes;
    }
  }

  private enum Operator {
    VOID(Token.VOID, false, Sets.newHashSet(Type.UNDEFINED)),
    TYPEOF(Token.TYPEOF, false, Sets.newHashSet(Type.STRING)),
    POS(Token.POS, false, Sets.newHashSet(Type.NUMBER)),
    NEG(Token.NEG, false, Sets.newHashSet(Type.NUMBER)),
    BIT_NOT(Token.BITNOT, false, Sets.newHashSet(Type.NUMBER)),
    NOT(Token.NOT, false, Sets.newHashSet(Type.BOOLEAN)),
    INC(Token.INC, true, Sets.newHashSet(Type.NUMBER)),
    DEC(Token.DEC, true, Sets.newHashSet(Type.NUMBER)),
    DEL_PROP(Token.DELPROP, true, Sets.newHashSet(Type.BOOLEAN)),
    POST_INC(Token.INC, true, Sets.newHashSet(Type.NUMBER)),
    POST_DEC(Token.DEC, true, Sets.newHashSet(Type.NUMBER));

    int nodeType;
    boolean hasSideEffect;
    Set<Type> supportedTypes;

    Operator(int nodeType, boolean hasSideEffect, Set<Type> supportedTypes) {
      this.nodeType = nodeType;
      this.hasSideEffect = hasSideEffect;
      this.supportedTypes = supportedTypes;
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
