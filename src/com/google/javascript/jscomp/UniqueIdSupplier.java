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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.io.Serializable;

/**
 * Generates unique String Ids when requested via a compiler instance.
 *
 * <p>This supplier provides Ids that are deterministic and unique across all input files given to
 * the compiler. The generated ID format is: uniqueId = "fileHashCode$counterForThisFile"
 */
public final class UniqueIdSupplier implements Serializable {
  private final Multiset<Integer> counter;

  UniqueIdSupplier() {
    counter = HashMultiset.create();
  }

  /**
   * Creates and returns a unique Id across all compiler input source files.
   *
   * @param input The compiler input for which the unique Id is requested.
   * @return unique ID as String
   */
  public String getUniqueId(CompilerInput input) {
    int fileHashCode;
    String filePath = input.getSourceFile().getOriginalPath();
    fileHashCode = filePath.hashCode();
    int id = counter.add(fileHashCode, 1);
    String fileHashString = (fileHashCode < 0) ? ("m" + -fileHashCode) : ("" + fileHashCode);
    return fileHashString + "$" + id;
  }
}
