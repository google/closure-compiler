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

import java.util.ArrayList;
import java.util.Random;

/**
 * Data structure for holding information in each scope
 */
class Scope {
  ArrayList<Symbol> symbols = new ArrayList<>();
  int loopNesting = 0;
  int switchNesting = 0;
  ArrayList<String> loopLabels = new ArrayList<>();
  ArrayList<String> otherLabels = new ArrayList<>();

  String randomLabelForContinue(Random random) {
    Preconditions.checkState(!loopLabels.isEmpty());
    return loopLabels.get(random.nextInt(loopLabels.size()));
  }

  String randomLabelForBreak(Random random) {
    Preconditions.checkState(loopLabels.size() + otherLabels.size() > 0);
    int rand = random.nextInt(loopLabels.size() + otherLabels.size());
    if (rand < loopLabels.size()) {
      return loopLabels.get(rand);
    } else {
      return otherLabels.get(rand - loopLabels.size());
    }
  }
}
