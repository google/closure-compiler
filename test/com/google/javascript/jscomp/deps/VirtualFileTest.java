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

package com.google.javascript.jscomp.deps;

import static com.google.common.truth.Truth.assertThat;

import junit.framework.TestCase;

/**
 * Test for equals and hashCode.
 */
public class VirtualFileTest extends TestCase {
  public void testEquals() throws Exception {
    VirtualFile vf = new VirtualFile("name", "code");
    VirtualFile same = new VirtualFile("name", "code");
    VirtualFile other = new VirtualFile("otherName", "otherCode");
    assertThat(vf).isEqualTo(same);
    assertThat(same).isEqualTo(vf);
    assertThat(vf.equals(other)).isFalse();
    assertThat(other.equals(vf)).isFalse();
  }

  public void testHashCode() throws Exception {
    VirtualFile vf = new VirtualFile("name", "code");
    VirtualFile same = new VirtualFile("name", "code");
    VirtualFile other = new VirtualFile("otherName", "otherCode");
    assertThat(same.hashCode()).isEqualTo(vf.hashCode());
    assertThat(vf.hashCode() == other.hashCode()).isFalse();
  }
}
