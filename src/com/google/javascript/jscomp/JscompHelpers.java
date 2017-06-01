/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Splitter;
import java.nio.file.Paths;

/**
 * This file contains utility methods needed by Java version of the compiler but should be super
 * sourced (removed) by the GWT/J2CL version.
 *
 * @author tdeegan@google.com
 */
@GwtIncompatible
public class JscompHelpers {

  // Splitter.onPattern cannot be done because of regex incompatibility.
  static final Splitter LINE_SPLITTER = Splitter.onPattern("\\r?\\n").omitEmptyStrings();

  static String getAbsolutePath(String path) {
    return Paths.get(path).toAbsolutePath().toString();
  }
}
