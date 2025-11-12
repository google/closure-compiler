/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import org.jspecify.annotations.Nullable;

/**
 * A SourceFileMapping maps a source file, line, and column into an {@link OriginalMapping}.
 *
 * @see com.google.debugging.sourcemap.SourceMapping
 */
public interface SourceFileMapping {
  /**
   * Returns the original mapping for the file name, line number and column position found in the
   * source map. Returns {@code null} if the source file does not have a input map, and returns
   * {@code OriginalMapping.getDefaultInstance()} if the input map does not map the location.
   *
   * @param lineNo The line number, 1-based.
   * @param columnNo The column index, 1-based.
   */
  @Nullable OriginalMapping getSourceMapping(String fileName, int lineNo, int columnNo);
}
