/*
 * Copyright 2014 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class UniqueIdSupplierTest {
  private UniqueIdSupplier uniqueIdSupplier;

  @Before
  public void setUp() {
    uniqueIdSupplier = new UniqueIdSupplier();
  }

  @Test
  public void testSingleCompilerInputGeneratesUniqueIds() {
    CompilerInput input = new CompilerInput(SourceFile.fromCode("tmp", "function foo() {}"));
    int fileHashCode = input.getSourceFile().getOriginalPath().hashCode();
    String inputHashString = (fileHashCode < 0) ? ("m" + -fileHashCode) : ("" + fileHashCode);

    String uniqueId1 = uniqueIdSupplier.getUniqueId(input);
    String uniqueId2 = uniqueIdSupplier.getUniqueId(input);
    assertThat(uniqueId1).isNotEmpty();
    assertThat(uniqueId2).isNotEmpty();
    assertThat(uniqueId1).isNotEqualTo(uniqueId2);
    assertThat(uniqueId1).contains(inputHashString + "$0");
    assertThat(uniqueId2).contains(inputHashString + "$1");
  }

  @Test
  public void testFilePathsHavingSameHashCode_generatesUniqueIds() {
    // Strings "FB" and "Ea" generate the same hashcode.
    CompilerInput input1 = new CompilerInput(SourceFile.fromCode("FB", "function foo() {}"));
    CompilerInput input2 = new CompilerInput(SourceFile.fromCode("Ea", "function foo() {}"));

    String uniqueId1 = uniqueIdSupplier.getUniqueId(input1);
    String uniqueId2 = uniqueIdSupplier.getUniqueId(input2);
    assertThat(uniqueId1).isNotEmpty();
    assertThat(uniqueId2).isNotEmpty();
    assertThat(uniqueId1).isNotEqualTo(uniqueId2);

    int fileHashCode1 = input1.getSourceFile().getOriginalPath().hashCode();
    int fileHashCode2 = input2.getSourceFile().getOriginalPath().hashCode();
    String inputHashString1 = (fileHashCode1 < 0) ? ("m" + -fileHashCode1) : ("" + fileHashCode1);
    String inputHashString2 = (fileHashCode2 < 0) ? ("m" + -fileHashCode2) : ("" + fileHashCode2);
    // The hash strings for the two files are the same, still the supplier generates unique IDs for
    // them
    assertThat(inputHashString1).isEqualTo(inputHashString2);
    assertThat(uniqueId1).contains(inputHashString1 + "$0");
    assertThat(uniqueId2).contains(inputHashString2 + "$1");
  }

  @Test
  public void testMultipleCompilerInputsGenerateUniqueIds() {
    CompilerInput input1 = new CompilerInput(SourceFile.fromCode("tmp1", "function foo() {}"));
    int inputHashCode1 = input1.getSourceFile().getOriginalPath().hashCode();
    String inputHashString1 =
        (inputHashCode1 < 0) ? ("m" + -inputHashCode1) : ("" + inputHashCode1);

    CompilerInput input2 = new CompilerInput(SourceFile.fromCode("tmp2", "function foo() {}"));
    int inputHashCode2 = input2.getSourceFile().getOriginalPath().hashCode();
    String inputHashString2 =
        (inputHashCode2 < 0) ? ("m" + -inputHashCode2) : ("" + inputHashCode2);

    String uniqueId1 = uniqueIdSupplier.getUniqueId(input1);
    String uniqueId2 = uniqueIdSupplier.getUniqueId(input2);
    assertThat(uniqueId1).isNotEmpty();
    assertThat(uniqueId2).isNotEmpty();
    assertThat(uniqueId1).isNotEqualTo(uniqueId2);
    assertThat(uniqueId1).contains(inputHashString1 + "$0");
    assertThat(uniqueId2).contains(inputHashString2 + "$0");
  }

  /** Ensures that unique Ids generated are the same across compiler runs */
  @Test
  public void testGeneratedIdsAreDeterministicAcrossRuns() {
    Compiler compiler1 = new Compiler();
    Compiler compiler2 = new Compiler();

    CompilerInput input1 = new CompilerInput(SourceFile.fromCode("tmp", "function foo() {}"));
    CompilerInput input2 = new CompilerInput(SourceFile.fromCode("tmp", "function foo() {}"));

    String uniqueId1 = compiler1.getUniqueIdSupplier().getUniqueId(input1);
    String differentUniqueId1 = compiler1.getUniqueIdSupplier().getUniqueId(input1);

    String uniqueId2 = compiler2.getUniqueIdSupplier().getUniqueId(input2);

    assertThat(uniqueId1).isNotEmpty();
    assertThat(uniqueId2).isNotEmpty();
    assertThat(uniqueId1)
        .isEqualTo(uniqueId2); // different IDs for same input with different compilers
    assertThat(uniqueId1)
        .isNotEqualTo(differentUniqueId1); // different IDs for same input with same compiler
  }
}
