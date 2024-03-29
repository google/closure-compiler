/*
 * Copyright 2022 The Closure Compiler Authors.
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerTestCase.Expected;
import com.google.javascript.jscomp.CompilerTestCase.FlatSources;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

/** Helpers for JSCompiler unit tests. */
public final class UnitTestUtils {
  /**
   * Replaces the generic variable names in each of the expected test output files with a specific
   * name.
   *
   * <p>The specific name is generated by using the replacement value as prefix and appending the
   * calculated uniqueID hashString based on the corresponding test's input file.
   *
   * <p>The vars created during certain compiler passes have a filePath based uniqueID in them. For
   * readability of tests, this uniqueID is obfuscated by using a generic name, e.g.
   * `TAGGED_TEMPLATE_TMP_VAR` in the test sources. This function replaces that generic name by a
   * specific name consisting of the runtime-computed uniqueID before test execution.
   *
   * @param inputs input FlatSources. Does not work for ChunkSources.
   * @param outputs expected sources
   * @param replacementPrefixes mapping from generic variable names and actual variable name prefix
   * @return the updated output files with correct runtime variable names
   */
  public static ImmutableList<SourceFile> updateGenericVarNamesInExpectedFiles(
      FlatSources inputs, Expected outputs, ImmutableMap<String, String> replacementPrefixes) {
    Preconditions.checkState(inputs != null);
    // In FlatSources, we correspond each input SourceFile (i.e. each CompilerInput) with an
    // expected SourceFile. We can know what uniqueID to generate in the expected string by knowing
    // its CompilerInput.
    // ChunkSources consist of JSChunks, and each chunk has multiple CompilerInputs which can
    // correspond to an expected SourceFile. The uniqueID in the expected code could come from code
    // in any of those CompilerInputs, i.e. we do not know what uniqueID to generate in the expected
    // string. Hence ChunkSources are unsupported.
    int inLength = inputs.sources.size();
    int outLength = outputs.expected.size();
    Preconditions.checkArgument(inLength == outLength);

    List<SourceFile> updatedOutputs = new ArrayList<>();
    for (int i = 0; i < inLength; i++) {
      SourceFile inputScript = inputs.sources.get(i);
      SourceFile outputScript = outputs.expected.get(i);

      // UniqueIDSupplier creates IDs by hashing input's file name.
      int fileHashCode = inputScript.getName().hashCode();
      String fileHashString = (fileHashCode < 0) ? ("m" + -fileHashCode) : ("" + fileHashCode);
      String expectedCode = "";
      String expectedCodeFileName = outputScript.getName();
      try {
        expectedCode = outputScript.getCode();
      } catch (IOException e) {
        throw new RuntimeException("Read error: " + expectedCodeFileName, e);
      }
      for (Entry<String, String> entry : replacementPrefixes.entrySet()) {
        expectedCode = expectedCode.replace(entry.getKey(), entry.getValue() + fileHashString);
      }
      updatedOutputs.add(SourceFile.fromCode(expectedCodeFileName, expectedCode));
    }
    return ImmutableList.copyOf(updatedOutputs);
  }

  private UnitTestUtils() {}
}
