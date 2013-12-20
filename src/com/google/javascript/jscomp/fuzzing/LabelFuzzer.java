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

import java.util.ArrayList;
import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
public class LabelFuzzer extends AbstractFuzzer {

  LabelFuzzer(FuzzingContext context) {
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
    String labelName = "x_" + context.snGenerator.getNextNumber();
    Node name = Node.newString(
        Token.LABEL_NAME, labelName);

    StatementFuzzer stmtFuzzer =
        new StatementFuzzer(context);
    AbstractFuzzer selectedFuzzer = stmtFuzzer.selectFuzzer(budget - 2, types);

    Scope localScope = context.scopeManager.localScope();
    ArrayList<String> currentLabels;
    if (selectedFuzzer instanceof ForFuzzer ||
        selectedFuzzer instanceof ForInFuzzer ||
        selectedFuzzer instanceof WhileFuzzer ||
        selectedFuzzer instanceof DoWhileFuzzer) {
      currentLabels = localScope.loopLabels;
    } else {
      currentLabels = localScope.otherLabels;
    }
    currentLabels.add(labelName);
    Node node =
        new Node(Token.LABEL, name, selectedFuzzer.generate(budget - 2));
    currentLabels.remove(labelName);
    return node;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "label";
  }

}
