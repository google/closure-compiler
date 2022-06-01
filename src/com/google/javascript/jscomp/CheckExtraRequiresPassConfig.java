/*
 * Copyright 2015 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.lint.CheckExtraRequires;

/**
 * A PassConfig to only run the CheckExtraRequires linter rule. This allows user to select which
 * goog.require imports to be removed, provided by requiresToRemove
 */
public final class CheckExtraRequiresPassConfig extends PassConfig.PassConfigDelegate {

  public CheckExtraRequiresPassConfig(CompilerOptions options) {
    super(new DefaultPassConfig(options));
  }

  @Override
  protected PassListBuilder getChecks() {
    PassListBuilder passes = new PassListBuilder(options);

    passes.maybeAdd(
        PassFactory.builder()
            .setName("removeUnusedImport")
            .setInternalFactory(
                (compiler) ->
                    new CombinedCompilerPass(
                        compiler,
                        ImmutableList.of(
                            new CheckExtraRequires(compiler, options.getUnusedImportsToRemove()))))
            .build());
    return passes;
  }

  @Override
  protected PassListBuilder getOptimizations() {
    return new PassListBuilder(options);
  }
}
