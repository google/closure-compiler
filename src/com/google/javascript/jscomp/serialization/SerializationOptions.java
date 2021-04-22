/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.serialization;

/**
 * Congiguration options for serialization time.
 *
 * <p>Currently, this consists of whether or not type names (not used for optimizations) should be
 * included in the serialized output to make it more human readable.
 */
public enum SerializationOptions {
  SKIP_DEBUG_INFO(false, false),
  INCLUDE_DEBUG_INFO(true, false),
  INCLUDE_DEBUG_INFO_AND_EXPENSIVE_VALIDITY_CHECKS(true, true);

  private final boolean includeDebugInfo;
  private final boolean runValidation;

  private SerializationOptions(boolean includeDebugInfo, boolean runValidation) {
    this.includeDebugInfo = includeDebugInfo;
    this.runValidation = runValidation;
  }

  public boolean includeDebugInfo() {
    return this.includeDebugInfo;
  }

  public boolean runValidation() {
    return this.runValidation;
  }
}
