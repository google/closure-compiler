/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.common.base.Strings;

import com.google.javascript.jscomp.CheckLevel;

/**
 * Verbose message formatter. This formatter generates very loud and long
 * messages with multi-line source excerpts.
 *
 */
class VerboseMessageFormatter extends AbstractMessageFormatter {
  VerboseMessageFormatter(SourceExcerptProvider source) {
    super(source);
  }

  @Override
  public String formatError(JSError error) {
    return getLevelName(CheckLevel.ERROR) + ": " + format(error);
  }

  @Override
  public String formatWarning(JSError warning) {
    return getLevelName(CheckLevel.WARNING) + ": " + format(warning);
  }

  private String format(JSError message) {
    String description = message.description;
    String sourceName = message.sourceName;
    int lineNumber = message.lineNumber;
    Region sourceRegion = getSource().getSourceRegion(sourceName, lineNumber);
    String lineSource = null;
    if (sourceRegion != null) {
      lineSource = sourceRegion.getSourceExcerpt();
    }
    return String.format("%s at %s line %s %s", description,
        (Strings.isNullOrEmpty(sourceName) ? "(unknown source)" : sourceName),
        ((lineNumber < 0) ? String.valueOf(lineNumber) : "(unknown line)"),
        ((lineSource != null) ? ":\n\n" + lineSource : "."));
  }
}
