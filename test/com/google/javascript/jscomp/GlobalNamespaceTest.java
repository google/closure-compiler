/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link GlobalNamespace}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public final class GlobalNamespaceTest extends TestCase {

  @Test
  public void testRemoveDeclaration1() {
    Name n = Name.createForTesting("a");
    Ref set1 = createNodelessRef(Ref.Type.SET_FROM_GLOBAL);
    Ref set2 = createNodelessRef(Ref.Type.SET_FROM_GLOBAL);

    n.addRef(set1);
    n.addRef(set2);

    assertThat(n.getDeclaration()).isEqualTo(set1);
    assertThat(n.getGlobalSets()).isEqualTo(2);
    assertThat(n.getRefs()).hasSize(2);

    n.removeRef(set1);

    assertThat(n.getDeclaration()).isEqualTo(set2);
    assertThat(n.getGlobalSets()).isEqualTo(1);
    assertThat(n.getRefs()).hasSize(1);
  }

  @Test
  public void testRemoveDeclaration2() {
    Name n = Name.createForTesting("a");
    Ref set1 = createNodelessRef(Ref.Type.SET_FROM_GLOBAL);
    Ref set2 = createNodelessRef(Ref.Type.SET_FROM_LOCAL);

    n.addRef(set1);
    n.addRef(set2);

    assertThat(n.getDeclaration()).isEqualTo(set1);
    assertThat(n.getGlobalSets()).isEqualTo(1);
    assertThat(n.getLocalSets()).isEqualTo(1);
    assertThat(n.getRefs()).hasSize(2);

    n.removeRef(set1);

    assertThat(n.getDeclaration()).isNull();
    assertThat(n.getGlobalSets()).isEqualTo(0);
  }

  private Ref createNodelessRef(Ref.Type type) {
    return Ref.createRefForTesting(type);
  }
}
