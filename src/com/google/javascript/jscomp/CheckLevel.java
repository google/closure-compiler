/*
 * Copyright 2004 The Closure Compiler Authors.
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
 * Controls checking levels of certain options.  For all checks going
 * forward, this should be used instead of booleans, so teams and
 * individuals can control which checks are off, which produce only warnings,
 * and which produce errors, without everyone having to agree.
 */
public enum CheckLevel {
  ERROR,
  WARNING,
  OFF;

  boolean isOn() {
    return this != OFF;
  }
}
