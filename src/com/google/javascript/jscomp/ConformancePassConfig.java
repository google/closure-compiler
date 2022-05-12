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


/**
 * Runs only the user-supplied conformance checks and any earlier passes required by conformance.
 */
public class ConformancePassConfig extends PassConfig.PassConfigDelegate {

  private final PassConfig delegate;

  public ConformancePassConfig(PassConfig delegate) {
    super(delegate);
    this.delegate = delegate;
  }

  @Override
  protected PassListBuilder getChecks() {
    PassListBuilder fromDelegate = delegate.getChecks();

    // Return every check up to and including the "checkConformance" check. Return empty list if
    // "checkConformance" not found. This list may include some unnecessary checks that run before
    // conformance. However, there's no other reliable way to find a list of all the passes
    // that conformance depends on.
    PassListBuilder passes = new PassListBuilder(options);
    passes.addAllUpTo(fromDelegate, PassNames.CHECK_CONFORMANCE);
    return passes;
  }

  @Override
  protected PassListBuilder getOptimizations() {
    return new PassListBuilder(options);
  }
}
