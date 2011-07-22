/*
 * Copyright 2011 The Closure Compiler Authors.
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

import java.util.Collection;

/**
 * A SourceMappingReversable is a SourceMapping that can provide the reverse
 * (source --> target) source mapping.
 */
public interface SourceMappingReversable extends SourceMapping {

  /**
   * @return the collection of original sources in this source mapping
   */
  public Collection<String> getOriginalSources();

  /**
   * Given a source file, line, and column, return the reverse mapping (source --> target).
   * A collection is returned as in some cases (like a function being inlined), one source line
   * may map to more then one target location. An empty collection is returned if there were
   * no matches.
   * @param originalFile the source file
   * @param line the source line
   * @param column the source column
   * @return the reverse mapping (source --> target)
   */
  public Collection<OriginalMapping> getReverseMapping(String originalFile, int line, int column);

}
