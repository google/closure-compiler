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

import org.json.JSONObject;

import java.util.Random;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
public class BreakFuzzer extends AbstractFuzzer {
  public BreakFuzzer(Random random, ScopeManager scopeManager,
      JSONObject config) {
    this.random = random;
    this.scopeManager = scopeManager;
    this.config = config;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
   */
  @Override
  protected boolean isEnough(int budget) {
    Scope scope = scopeManager.localScope();
    if (scope.loopNesting + scope.switchNesting > 0) {
      return budget >= 1;
    } else {
      return false;
    }
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
   */
  @Override
  protected Node generate(int budget) {
    Node node = new Node(Token.BREAK);
    Scope localScope = scopeManager.localScope();
    double toLabel = getOwnConfig().optDouble("toLabel");
    if (budget > 1 &&
        localScope.loopLabels.size() + localScope.otherLabels.size() > 0 &&
        random.nextDouble() < toLabel) {
      node.addChildToBack(
          Node.newString(Token.LABEL_NAME,
              localScope.randomLabelForBreak(random)));
    }
    return node;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "break";
  }

}
