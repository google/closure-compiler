/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.base;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.base.Tri.FALSE;
import static com.google.javascript.jscomp.base.Tri.TRUE;
import static com.google.javascript.jscomp.base.Tri.UNKNOWN;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TriTest {

  @Test
  public void or() {
    Tri[][] allExpected =
        new Tri[][] {
          {FALSE, UNKNOWN, TRUE}, //
          {UNKNOWN, UNKNOWN, TRUE},
          {TRUE, TRUE, TRUE},
        };

    for (Tri row : Tri.values()) {
      for (Tri col : Tri.values()) {
        Tri expected = allExpected[row.ordinal()][col.ordinal()];
        assertThat(row.or(col)).isEqualTo(expected);
        assertThat(col.or(row)).isEqualTo(expected);
      }
    }
  }

  @Test
  public void and() {
    Tri[][] allExpected =
        new Tri[][] {
          {FALSE, FALSE, FALSE}, //
          {FALSE, UNKNOWN, UNKNOWN},
          {FALSE, UNKNOWN, TRUE},
        };

    for (Tri row : Tri.values()) {
      for (Tri col : Tri.values()) {
        Tri expected = allExpected[row.ordinal()][col.ordinal()];
        assertThat(row.and(col)).isEqualTo(expected);
        assertThat(col.and(row)).isEqualTo(expected);
      }
    }
  }

  @Test
  public void xor() {
    Tri[][] allExpected =
        new Tri[][] {
          {FALSE, UNKNOWN, TRUE}, //
          {UNKNOWN, UNKNOWN, UNKNOWN},
          {TRUE, UNKNOWN, FALSE},
        };

    for (Tri row : Tri.values()) {
      for (Tri col : Tri.values()) {
        Tri expected = allExpected[row.ordinal()][col.ordinal()];
        assertThat(row.xor(col)).isEqualTo(expected);
        assertThat(col.xor(row)).isEqualTo(expected);
      }
    }
  }

  @Test
  public void not() {
    assertThat(FALSE.not()).isEqualTo(TRUE);
    assertThat(UNKNOWN.not()).isEqualTo(UNKNOWN);
    assertThat(TRUE.not()).isEqualTo(FALSE);
  }

  @Test
  public void toBoolean() {
    boolean[][] allExpected =
        new boolean[][] {
          {false, false}, //
          {false, true},
          {true, true},
        };

    for (Tri row : Tri.values()) {
      assertThat(row.toBoolean(false)).isEqualTo(allExpected[row.ordinal()][0]);
      assertThat(row.toBoolean(true)).isEqualTo(allExpected[row.ordinal()][1]);
    }
  }

  @Test
  public void forBoolean() {
    assertThat(Tri.forBoolean(false)).isEqualTo(FALSE);
    assertThat(Tri.forBoolean(true)).isEqualTo(TRUE);
  }
}
