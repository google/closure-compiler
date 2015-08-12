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
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.lint.CheckEmptyStatements;
import com.google.javascript.jscomp.lint.CheckEnums;
import com.google.javascript.jscomp.lint.CheckInterfaces;
import com.google.javascript.jscomp.lint.CheckJSDocStyle;
import com.google.javascript.jscomp.lint.CheckPrototypeProperties;
import com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted;

import java.util.List;

class LintPassConfig extends PassConfig.PassConfigDelegate {
  LintPassConfig(CompilerOptions options) {
    super(new DefaultPassConfig(options));
  }

  @Override protected List<PassFactory> getChecks() {
    return ImmutableList.of(lintChecks);
  }

  @Override protected List<PassFactory> getOptimizations() {
    return ImmutableList.of();
  }

  // This doesn't match the list of 'lintChecks' in DefaultPassConfig, because
  // DefaultPassConfig's list includes some checks that depend on type information,
  // and the linter skips typechecking to stay fast.
  private final PassFactory lintChecks =
      new PassFactory("lintChecks", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new CombinedCompilerPass(
              compiler,
              ImmutableList.<Callback>of(
                  new CheckEmptyStatements(compiler),
                  new CheckEnums(compiler),
                  new CheckInterfaces(compiler),
                  new CheckJSDocStyle(compiler),
                  new CheckJSDoc(compiler),
                  new CheckPrototypeProperties(compiler),
                  new CheckRequiresForConstructors(compiler),
                  new CheckRequiresAndProvidesSorted(compiler)));
        }
      };
}
