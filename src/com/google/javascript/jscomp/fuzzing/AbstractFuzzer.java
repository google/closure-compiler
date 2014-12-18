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
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.google.javascript.jscomp.CodePrinter;
import com.google.javascript.rhino.Node;

import java.util.EnumSet;
import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
abstract class AbstractFuzzer {
  protected FuzzingContext context;

  AbstractFuzzer(FuzzingContext context) {
    this.context = context;
  }

  protected JsonObject getOwnConfig() {
    Preconditions.checkNotNull(context.config);
    return context.config.get(getConfigName()).getAsJsonObject();
  }

  /**
   * Decide if the budget is enough
   */
  protected abstract boolean isEnough(int budget);
  /**
   * @param budget When the budget is not enough, it will try to generate a node
   * with minimal budget
   */
  protected abstract Node generate(int budget, Set<Type> types);

  protected Node generate(int budget) {
    return generate(budget, supportedTypes());
  }

  protected Node[] distribute(int budget, AbstractFuzzer[] fuzzers) {
    Preconditions.checkArgument(fuzzers.length > 0);
    int numNodes = fuzzers.length;
    int[] subBudgets = new int[numNodes];
    if (budget > 3 * numNodes) {
      // when the budget is much greater than numNodes
      double[] rands = new double[numNodes];
      double sum = 0;
      for (int i = 0; i < numNodes; i++) {
        rands[i] = context.random.nextDouble();
        sum += rands[i];
      }
      for (int i = 0; i < numNodes; i++) {
        double additionalBudget = budget / sum * rands[i];
        subBudgets[i] += additionalBudget;
        budget -= additionalBudget;
      }
    }
    while (budget > 0) {
      subBudgets[context.random.nextInt(numNodes)]++;
      budget--;
    }
    Node[] nodes = new Node[numNodes];
    for (int i = 0; i < numNodes; i++) {
      nodes[i] = fuzz(fuzzers[i], subBudgets[i]);
    }
    return nodes;
  }

  protected Node fuzz(AbstractFuzzer fuzzer, int budget) {
    return fuzzer.generate(budget);
  }

  protected abstract String getConfigName();

  protected int generateLength(int budget) {
    return context.random.nextInt(
        (int) (budget * getOwnConfig().get("maxLength").getAsDouble()) + 1);
  }

  public static String getPrettyCode(Node root) {
    CodePrinter.Builder builder = new CodePrinter.Builder(root);
    builder.setPrettyPrint(true);
    builder.setLineBreak(true);
    return builder.build();
  }

  /**
   * @return All types by default. Subclasses may override to limit the
   * supported types
   */
  protected Set<Type> supportedTypes() {
    return Sets.immutableEnumSet(EnumSet.allOf(Type.class));
  }
}
