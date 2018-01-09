/*
 * Copyright 2018 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.ijs;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.WarningsGuard;

/**
 * A warnings guard that sets the errors found in .i.js files to be warnings, and only the warnings
 * found in the library in question to errors.
 */
public class CheckIjsWarningsGuard extends WarningsGuard {

  @Override
  public CheckLevel level(JSError error) {
    if (error.sourceName.endsWith(".i.js")) {
      return CheckLevel.WARNING;
    }
    return null;
  }
}