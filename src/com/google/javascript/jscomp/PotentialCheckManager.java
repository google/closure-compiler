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

import com.google.common.collect.Lists;

import java.util.List;

/**
 * A manager for {@link PotentialCheck}s, holding them and evaluating on demand.
 *
*
 */
class PotentialCheckManager {
  private final List<PotentialCheck> checks = Lists.newArrayList();

  /**
   * Adds a potential check for later evaluation.
   */
  void add(PotentialCheck check) {
    checks.add(check);
  }

  /**
   * Evaluates all pending potential checks. Each check is either reported or
   * permanently discarded.
   */
  void flush() {
    for (PotentialCheck check : checks) {
      check.evaluate();
    }
    checks.clear();
  }
}
