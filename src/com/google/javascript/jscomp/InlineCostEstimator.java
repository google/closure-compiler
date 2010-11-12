/*
 * Copyright 2008 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;

/**
 * For use with CodeGenerator to determine the cost of generated code.
 *
 * @see CodeGenerator
 * @see CodePrinter
 */
class InlineCostEstimator {
  // For now simply assume identifiers are 2 characters.
  private static final String ESTIMATED_IDENTIFIER = "ab";
  static final int ESTIMATED_IDENTIFIER_COST = ESTIMATED_IDENTIFIER.length();

  private InlineCostEstimator() {
  }

  /**
   * Determines the size of the js code.
   */
  static int getCost(Node root) {
    return getCost(root, Integer.MAX_VALUE);
  }

  /**
   * Determines the estimated size of the js snippet represented by the node.
   */
  static int getCost(Node root, int costThreshhold) {
    CompiledSizeEstimator estimator = new CompiledSizeEstimator(costThreshhold);
    estimator.add(root);
    return estimator.getCost();
  }

  /**
   * Code consumer that estimates compiled size by assuming names are
   * shortened and all whitespace is stripped.
   */
  private static class CompiledSizeEstimator extends CodeConsumer {
    private int maxCost;
    private int cost = 0;
    private char last = '\0';
    private boolean continueProcessing = true;

    CompiledSizeEstimator(int costThreshhold) {
      this.maxCost = costThreshhold;
    }

    void add(Node root) {
      CodeGenerator cg = new CodeGenerator(this);
      cg.add(root);
    }

    int getCost() {
      return cost;
    }

    @Override
    boolean continueProcessing() {
      return continueProcessing;
    }

    @Override
    char getLastChar() {
      return last;
    }

    @Override
    void append(String str){
      last = str.charAt(str.length() - 1);
      cost += str.length();
      if (maxCost <= cost) {
        continueProcessing = false;
      }
    }

    @Override
    void addIdentifier(String identifier) {
      add(ESTIMATED_IDENTIFIER);
    }
  }
}
