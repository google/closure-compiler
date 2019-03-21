/*
 * Copyright 2019 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.CheckLevel.OFF;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link J2clSuppressWarningsGuard}. */
@RunWith(JUnit4.class)
public final class J2clSuppressWarningsGuardTest {

  @Test
  public void testSuppress_j2cl() {
    WarningsGuard guard = new J2clSuppressWarningsGuard();

    assertThat(guard.level(makeError("hello.java.js"))).isNull();
    assertThat(guard.level(makeJ2clSuppressedError("hello.java.js"))).isEqualTo(OFF);
    assertThat(guard.level(makeJ2clSuppressedError("hello.impl.java.js"))).isEqualTo(OFF);
    assertThat(guard.level(makeJ2clSuppressedError("foo/hello.impl.java.js"))).isEqualTo(OFF);
  }

  @Test
  public void testSuppress_nonJ2cl() {
    WarningsGuard guard = new J2clSuppressWarningsGuard();

    assertThat(guard.level(makeError("hello.js"))).isNull();
    assertThat(guard.level(makeJ2clSuppressedError("hello.js"))).isNull();
    assertThat(guard.level(makeJ2clSuppressedError("kajava.js"))).isNull();
  }

  private static JSError makeError(String sourcePath) {
    Node n = new Node(Token.EMPTY);
    n.setSourceFileForTesting(sourcePath);
    return JSError.make(n, TypeCheck.INEXISTENT_PROPERTY);
  }

  private static JSError makeJ2clSuppressedError(String sourcePath) {
    Node n = new Node(Token.EMPTY);
    n.setSourceFileForTesting(sourcePath);
    return JSError.make(n, CheckSideEffects.USELESS_CODE_ERROR);
  }
}
