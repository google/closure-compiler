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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.javascript.rhino.Node;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
abstract class Dispatcher extends AbstractFuzzer {
  private Set<Type> supportedTypes;
  Dispatcher(FuzzingContext context) {
    super(context);
  }
  protected AbstractFuzzer[] candidates;

  @Override
  protected boolean isEnough(int budget) {
    if (budget < 1) {
      return false;
    }
    Preconditions.checkNotNull(
        getCandidates(),
        "Candidate fuzzers need to be initialized before being used.");

    for (AbstractFuzzer fuzzer : getCandidates()) {
      if (fuzzer.isEnough(budget)) {
        return true;
      }
    }
    return false;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int, Set<Type>)
   */
  @Override
  protected Node generate(int budget, Set<Type> types) {
    AbstractFuzzer fuzzer = selectFuzzer(budget, types);
    return fuzzer.generate(budget, types);
  }

  @Override
  protected Set<Type> supportedTypes() {
    if (supportedTypes == null) {
      supportedTypes = EnumSet.noneOf(Type.class);
      for (AbstractFuzzer fuzzer : getCandidates()) {
        supportedTypes.addAll(fuzzer.supportedTypes());
      }
    }
    return Sets.immutableEnumSet(supportedTypes);
  }

  protected AbstractFuzzer selectFuzzer(int budget, Set<Type> types) {
    Preconditions.checkNotNull(
        getCandidates(),
        "Candidate fuzzers need to be initialized before being used.");
    ArrayList<AbstractFuzzer> typeCorrectCandidates;
    typeCorrectCandidates = Lists.newArrayList();
    for (AbstractFuzzer fuzzer : getCandidates()) {
      if (!Sets.intersection(fuzzer.supportedTypes(), types).
          isEmpty()) {
        typeCorrectCandidates.add(fuzzer);
      }
    }
    JsonObject weightConfig = getOwnConfig().get("weights").getAsJsonObject();
    ArrayList<AbstractFuzzer> validFuzzers = Lists.newArrayList();
    ArrayList<Double> weights = Lists.newArrayList();
    int stepSize = 2;
    budget -= stepSize;
    do {
      // increase the budget until it is enough for some fuzzers
      budget += stepSize;
      for (AbstractFuzzer fuzzer : typeCorrectCandidates) {
        if (fuzzer.isEnough(budget)) {
          validFuzzers.add(fuzzer);
          try {
            weights.add(weightConfig.get(fuzzer.getConfigName()).getAsDouble());
          } catch (JsonParseException e) {
            e.printStackTrace();
          }
        }
      }
    } while (validFuzzers.isEmpty());
    DiscreteDistribution<AbstractFuzzer> dd =
        new DiscreteDistribution<>(context.random, validFuzzers, weights);
    return dd.nextItem();
  }

  protected abstract void initCandidates();

  private AbstractFuzzer[] getCandidates() {
    if (candidates == null) {
      initCandidates();
    }
    return candidates;
  }
}
