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

package com.google.javascript.jscomp.testing;

import com.google.javascript.jscomp.Region;
import com.google.javascript.jscomp.SourceExcerptProvider;
import com.google.javascript.jscomp.SourceFile;



/**
 * A simple source excerpt provider for testing.
 * @author nicksantos@google.com (Nick Santos)
 */
public class SimpleSourceExcerptProvider implements SourceExcerptProvider {

  private final SourceFile sourceFile;

  public SimpleSourceExcerptProvider(String source) {
    sourceFile = SourceFile.fromCode("input", source);
  }

  @Override
  public String getSourceLine(String sourceName, int lineNumber) {
    return sourceFile.getLine(lineNumber);
  }

  @Override
  public Region getSourceRegion(String sourceName, int lineNumber) {
    return sourceFile.getRegion(lineNumber);
  }
}
