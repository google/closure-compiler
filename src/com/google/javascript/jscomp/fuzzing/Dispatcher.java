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
import com.google.javascript.rhino.Node;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Random;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
abstract class Dispatcher extends AbstractFuzzer {
  protected AbstractFuzzer[] candidates;

  Dispatcher(Random random, ScopeManager scopeManager, JSONObject config,
      StringNumberGenerator snGenerator) {
    super(random, scopeManager, config, snGenerator);
  }

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
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
   */
  @Override
  protected Node generate(int budget) {
    AbstractFuzzer fuzzer = selectFuzzer(budget);
    return fuzzer.generate(budget);
  }

  protected AbstractFuzzer selectFuzzer(int budget) {
    Preconditions.checkNotNull(
        getCandidates(),
        "Candidate fuzzers need to be initialized before being used.");
    JSONObject weightConfig = getOwnConfig().optJSONObject("weights");
    ArrayList<AbstractFuzzer> validFuzzers = Lists.newArrayList();
    ArrayList<Double> weights = Lists.newArrayList();
    int stepSize = 2;
    budget -= stepSize;
    do {
      // increase the budget until it is enough for some fuzzers
      budget += stepSize;
      for (AbstractFuzzer fuzzer : getCandidates()) {
        if (fuzzer.isEnough(budget)) {
          validFuzzers.add(fuzzer);
          try {
            weights.add(weightConfig.getDouble(fuzzer.getConfigName()));
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
      }
    } while (validFuzzers.size() == 0);
    DiscreteDistribution<AbstractFuzzer> dd =
        new DiscreteDistribution<>(random, validFuzzers, weights);
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
