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

/**
 * The error handler is any generic sink for warnings and errors,
 * after they've passed through any filtering {@code WarningsGuard}s.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public interface ErrorHandler {
  /**
   * @param level the reporting level
   * @param error the error to report
   */
  void report(CheckLevel level, JSError error);
}
