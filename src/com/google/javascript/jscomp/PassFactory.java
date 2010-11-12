/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;



/**
 * A factory for creating JSCompiler passes based on the Options
 * injected.  Contains all meta-data about compiler passes (like
 * whether it can be run multiple times, a human-readable name for
 * logging, etc.).
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public abstract class PassFactory {

  private final String name;
  private final boolean isOneTimePass;
  private boolean isCreated = false;

  /**
   * @param name The name of the pass that this factory creates.
   * @param isOneTimePass If true, the pass produced by this factory can
   *     only be run once.
   */
  protected PassFactory(String name, boolean isOneTimePass) {
    this.name = name;
    this.isOneTimePass = isOneTimePass;
  }

  /**
   * @return The name of this pass.
   */
  String getName() {
    return name;
  }

  /**
   * @return Whether the pass produced by this factory can only be run once.
   */
  boolean isOneTimePass() {
    return isOneTimePass;
  }

  /**
   * Make a new pass factory that only creates one-time passes.
   */
  PassFactory makeOneTimePass() {
    if (isOneTimePass()) {
      return this;
    }

    final PassFactory self = this;
    return new PassFactory(name, true /* one time pass */) {
      @Override
      protected CompilerPass createInternal(AbstractCompiler compiler) {
        return self.createInternal(compiler);
      }
    };
  }

  /**
   * Creates a new compiler pass to be run.
   */
  final CompilerPass create(AbstractCompiler compiler) {
    Preconditions.checkState(!isCreated || !isOneTimePass,
        "One-time passes cannot be run multiple times: " + name);
    isCreated = true;
    return createInternal(compiler);
  }

  /**
   * Creates a new compiler pass to be run.
   */
  abstract protected CompilerPass createInternal(AbstractCompiler compiler);
}
