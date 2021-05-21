/*
 * Copyright 2012 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES5;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides passes that should be run before hot-swap/incremental builds.
 */
class CleanupPasses extends PassConfig {

  public CleanupPasses(CompilerOptions options) {
    super(options);
  }

  @Override
  protected List<PassFactory> getChecks() {
    List<PassFactory> checks = new ArrayList<>();
    checks.add(fieldCleanupPassFactory);
    return checks;
  }

  @Override
  protected List<PassFactory> getOptimizations() {
    return ImmutableList.of();
  }

  final PassFactory fieldCleanupPassFactory =
      PassFactory.builderForHotSwap()
          .setName("FieldCleanupPassFactory")
          .setInternalFactory(FieldCleanupPass::new)
          .setFeatureSet(ES5)
          .build();
}
