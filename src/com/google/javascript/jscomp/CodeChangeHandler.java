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

/**
 * A simple listener for code change events.
 * @author nicksantos@google.com (Nick Santos)
 */
interface CodeChangeHandler {

  // TODO(nicksantos): Add more to this interface, for more fine-grained
  // change reporting.

  /** Report a change to the AST. */
  void reportChange();

  /**
   * A trivial change handler that just records whether the code
   * has changed since the last reset.
   */
  static final class RecentChange implements CodeChangeHandler {
    private boolean hasChanged = false;

    public void reportChange() {
      hasChanged = true;
    }

    boolean hasCodeChanged() {
      return hasChanged;
    }

    void reset() {
      hasChanged = false;
    }
  }

  /**
   * A change handler that throws an exception if any changes are made.
   */
  static final class ForbiddenChange implements CodeChangeHandler {
    public void reportChange() {
      throw new IllegalStateException("Code changes forbidden");
    }
  }
}
