/*
 * Copyright 2008 The Closure Compiler Authors.
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
 * Control whether warnings should be restricted or suppressed for specified
 * paths.
 *
 * @author anatol@google.com (Anatol Pomazau)
 */
public class ShowByPathWarningsGuard extends WarningsGuard {
  /**
   * Controls whether warnings should be restricted to a specified path or
   * suppressed within the specified path.
   */
  public enum ShowType {
    INCLUDE,  // Suppress warnings outside the path.
    EXCLUDE;  // Suppress warnings within the path.
  }

  private final String[] paths;
  private final ShowType showType;

  public ShowByPathWarningsGuard(String checkWarningsOnlyForPath) {
    this(checkWarningsOnlyForPath, ShowType.INCLUDE);
  }

  public ShowByPathWarningsGuard(String[] checkWarningsOnlyForPath) {
    this(checkWarningsOnlyForPath, ShowType.INCLUDE);
  }

  public ShowByPathWarningsGuard(String path, ShowType showType) {
    this(new String[] { path }, showType);
  }

  public ShowByPathWarningsGuard(String[] paths, ShowType showType) {
    Preconditions.checkArgument(paths != null);
    Preconditions.checkArgument(showType != null);
    this.paths = paths;
    this.showType = showType;
  }

  @Override
  public CheckLevel level(JSError error) {
    final String errorPath = error.sourceName;
    if (error.level != CheckLevel.ERROR && errorPath != null) {
      boolean inPath = false;
      for (String path : paths) {
        inPath |= errorPath.contains(path);
      }
      if (inPath ^ (showType == ShowType.INCLUDE)) {
        return CheckLevel.OFF;
      }
    }
    return null;
  }

  @Override
  protected int getPriority() {
    return WarningsGuard.Priority.FILTER_BY_PATH.value; // applied first
  }
}
