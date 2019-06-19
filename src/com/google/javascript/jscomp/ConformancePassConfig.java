/*
 * Copyright 2019 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Runs only the user-supplied conformance checks and any earlier passes required by conformance.
 */
public class ConformancePassConfig extends PassConfig {

  private final PassConfig delegate;

  public ConformancePassConfig(CompilerOptions options, PassConfig delegate) {
    super(options);
    this.delegate = delegate;
  }

  @Override
  protected List<PassFactory> getChecks() {
    List<PassFactory> fromDelegate = delegate.getChecks();
    int conformanceIndex = -1;
    for (int i = 0; i < fromDelegate.size(); i++) {
      if (PassNames.CHECK_CONFORMANCE.equals(fromDelegate.get(i).getName())) {
        conformanceIndex = i;
        break;
      }
    }
    // Return every check up to and including the "checkConformance" check. Return empty list if
    // "checkConformance" not found.
    return fromDelegate.subList(0, conformanceIndex + 1);
  }

  @Override
  protected List<PassFactory> getOptimizations() {
    return ImmutableList.of();
  }
}
