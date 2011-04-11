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

package com.google.debugging.sourcemap;

import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;

/**
 * Interface for provide a way of mapping (line, column) positions back to
 * positions in the original (uncompiled) source code.
 *
 */
public interface SourceMapping {
  /**
   * Returns the original mapping for the line number and column position found
   * in the source map. Returns null if none is found.
   *
   * @param lineNumber The line number, with the first being '1'.
   * @param columnIndex The column index, with the first being '1'.
   */
  OriginalMapping getMappingForLine(int lineNumber, int columnIndex);
}
