/*
 * Copyright 2016 The Closure Compiler Authors.
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

/** A GWT compatible version that doesn't estimate the gzip size */
final class PerformanceTrackerCodeSizeEstimator extends CodeConsumer {
  private int size = 0;
  private char lastChar = '\0';

  static PerformanceTrackerCodeSizeEstimator estimate(Node jsRoot, boolean trackGzSize) {
    PerformanceTrackerCodeSizeEstimator estimator = new PerformanceTrackerCodeSizeEstimator();
    CodeGenerator.forCostEstimation(estimator).add(jsRoot);
    return estimator;
  }

  private PerformanceTrackerCodeSizeEstimator() {
  }

  @Override
  void append(String str) {
    int len = str.length();
    if (len > 0) {
      size += len;
      lastChar = str.charAt(len - 1);
    }
  }

  @Override
  char getLastChar() {
    return lastChar;
  }

  int getCodeSize() {
    return size;
  }

  int getZippedCodeSize() {
    return 0;
  }
}
