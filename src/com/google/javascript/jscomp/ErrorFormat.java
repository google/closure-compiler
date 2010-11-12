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

import com.google.javascript.jscomp.SourceExcerptProvider.SourceExcerpt;

/**
 * Error formats available.
 */
public enum ErrorFormat {
  LEGACY {
    @Override
    public MessageFormatter toFormatter(
        SourceExcerptProvider source, boolean colorize) {
      VerboseMessageFormatter formatter = new VerboseMessageFormatter(source);
      formatter.setColorize(colorize);
      return formatter;
    }
  },
  SINGLELINE {
    @Override
    public MessageFormatter toFormatter(
        SourceExcerptProvider source, boolean colorize) {
      LightweightMessageFormatter formatter = new LightweightMessageFormatter(
          source);
      formatter.setColorize(colorize);
      return formatter;
    }
  },
  MULTILINE {
    @Override
    public MessageFormatter toFormatter(
        SourceExcerptProvider source, boolean colorize) {
      LightweightMessageFormatter formatter = new LightweightMessageFormatter(
          source, SourceExcerpt.REGION);
      formatter.setColorize(colorize);
      return formatter;
    }
  },
  SOURCELESS {
    @Override
    public MessageFormatter toFormatter(
        SourceExcerptProvider source, boolean colorize) {
      LightweightMessageFormatter formatter =
          LightweightMessageFormatter.withoutSource();
      formatter.setColorize(colorize);
      return formatter;
    }
  };

  /**
   * Convert to a concrete formatter.
   */
  public abstract MessageFormatter toFormatter(
      SourceExcerptProvider source, boolean colorize);
}
