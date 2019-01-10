/*
 * Copyright 2017 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.rhino.Node;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class NameBasedDefinitionProviderTest {

  @Test
  public void testGetSimplifiedName() {
    assertThat(getSimplifiedName("x;")).isEqualTo("x");
    assertThat(getSimplifiedName("foo.bar;")).isEqualTo("this.bar");
    assertThat(getSimplifiedName("foo.bar.baz.bop;")).isEqualTo("this.bop");
    assertThat(getSimplifiedName("getLhs().beep;")).isEqualTo("this.beep");

    Node memberDef =
        parse("class C { member() {} }")  // SCRIPT
        .getOnlyChild()  // CLASS
        .getLastChild()  // CLASS_MEMBERS
        .getOnlyChild();  // MEMBER_DEF
    checkState(memberDef.isMemberFunctionDef(), memberDef);
    assertThat(getSimplifiedName(memberDef)).isEqualTo("this.member");
  }

  private static Node parse(String js) {
    CompilerOptions options = new CompilerOptions();

    Compiler compiler = new Compiler();
    compiler.initOptions(options);

    Node n = compiler.parseTestCode(js);
    assertThat(compiler.getErrors()).isEmpty();
    return n;
  }

  @Nullable
  private String getSimplifiedName(Node n) {
    return NameBasedDefinitionProvider.getSimplifiedName(n);
  }

  @Nullable
  private String getSimplifiedName(String js) {
    Node n = parse(js).getOnlyChild().getOnlyChild();
    return getSimplifiedName(n);
  }
}
