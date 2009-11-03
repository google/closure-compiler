/*
 * Copyright 2008 Google Inc.
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


/**
 * A potential check holds a warning or error for delayed evaluation, so that
 * its validity can be assessed based on information gathered after its
 * creation.
 *
 * @see PotentialCheckManager
 *
*
 */
abstract class PotentialCheck {
  private final AbstractCompiler compiler;
  private final JSError err;

  PotentialCheck(AbstractCompiler compiler, JSError err) {
    this.compiler = compiler;
    this.err = err;
  }

  /**
   * Reports the warning or error.
   */
  private void report() {
    compiler.report(err);
  }

  /**
   * Reports the warning or error only if it's still relevant.
   */
  void evaluate() {
    if (stillRelevant()) {
      report();
    }
  }

  /**
   * Assesses whether the check is still relevant.
   * @return {@code true} if the check should be reported, {@code false} if it
   *     should be discarded.
   */
  protected abstract boolean stillRelevant();
}
