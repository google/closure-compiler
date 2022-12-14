/*
 * Copyright 2021 The Closure Compiler Authors.
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
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StripProtectionTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckSideEffects.StripProtection(compiler);
  }

  @Test
  public void testJ2clPreserveFunction() {
    test(
        srcs(SourceFile.fromCode("Foo.java.js", "$J2CL_PRESERVE$(Foo.prototype.prop);")),
        expected(""));

    // Not allowed in non-J2CL code.
    RuntimeException exception =
        assertThrows(
            RuntimeException.class, () -> testSame("$J2CL_PRESERVE$(Foo.prototype.prop);"));
    assertThat(exception.toString()).contains("Only allowed for J2CL code");
  }
}
