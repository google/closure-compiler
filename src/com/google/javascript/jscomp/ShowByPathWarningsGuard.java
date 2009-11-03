/*
 * Copyright 2008 Google Inc.
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
 * Show warnings only for specific path. The rest of warnings should be
 * suppressed.
 *
*
 */
public class ShowByPathWarningsGuard extends WarningsGuard {
  private final String[] checkWarningsOnlyForPath;

  public ShowByPathWarningsGuard(String checkWarningsOnlyForPath) {
    Preconditions.checkArgument(checkWarningsOnlyForPath != null);
    this.checkWarningsOnlyForPath = new String[] { checkWarningsOnlyForPath };
  }

  public ShowByPathWarningsGuard(String[] checkWarningsOnlyForPath) {
    Preconditions.checkArgument(checkWarningsOnlyForPath != null);
    this.checkWarningsOnlyForPath = checkWarningsOnlyForPath;
  }

  @Override
  public CheckLevel level(JSError error) {
    // Check if we dont want to see these warnings
    final String filePath = error.sourceName;

    if (error.level != CheckLevel.ERROR && filePath != null) {
      boolean checkMe = false;
      for (String checkedPath : checkWarningsOnlyForPath) {
        checkMe |= filePath.contains(checkedPath);
      }

      if (!checkMe) {
        return CheckLevel.OFF;
      }
    }
    return null;
  }

  @Override
  protected int getPriority() {
    return 1; // applied first
  }
}
