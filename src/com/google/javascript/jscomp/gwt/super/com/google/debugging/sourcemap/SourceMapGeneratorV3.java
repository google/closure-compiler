/*
 * Copyright 2015 The Closure Compiler Authors.
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

import java.io.IOException;
import java.util.List;

/** GWT compatible no-op replacement for {@code SourceMapGeneratorV3} */
public final class SourceMapGeneratorV3 implements SourceMapGenerator {
  public void appendTo(Appendable out, String name) throws IOException {
  }

  public void appendIndexMapTo(
      Appendable out, String name, List<SourceMapSection> sections)
      throws IOException {
  }

  public void reset() {
  }

  public void addMapping(String sourceName, String symbolName,
           FilePosition sourceStartPosition,
           FilePosition outputStartPosition, FilePosition outputEndPosition) {

  }

  public void setWrapperPrefix(String prefix) {
  }

  public void setStartingPosition(int offsetLine, int offsetIndex) {
  }

  public void validate(boolean validate) {
  }
}
