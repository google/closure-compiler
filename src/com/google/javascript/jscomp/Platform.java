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
package com.google.javascript.jscomp;

import java.io.File;
import java.text.MessageFormat;

/** A utility to abstract platform specific logic so it could be super-sourced for Web. */
final class Platform {

  static String getFileSeperator() {
    return File.separator;
  }

  static boolean isThreadInterrupted() {
    // Note that this clears interruption. Thread.isInterruped should be preferred instead.
    return Thread.interrupted();
  }

  static long freeMemory() {
    return Runtime.getRuntime().freeMemory();
  }

  static long totalMemory() {
    return Runtime.getRuntime().totalMemory();
  }

  static String formatMessage(String message, String... arguments) {
    // Note that MessageFormat is removing single quotes and in many cases intended ones. Consider
    // moving to a simpler formatting version like the Web one.
    return MessageFormat.format(message, (Object[]) arguments);
  }

  private Platform() {}
}
