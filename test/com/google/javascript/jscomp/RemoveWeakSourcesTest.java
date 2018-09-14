/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit test for {@link RemoveWeakSources}. */
@RunWith(JUnit4.class)
public final class RemoveWeakSourcesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RemoveWeakSources(compiler);
  }

  @Test
  public void test() {
    SourceFile strongSrc1 = SourceFile.fromCode("a.js", "var a = 1;", SourceKind.STRONG);
    SourceFile weakSrc1 = SourceFile.fromCode("b.js", "var b = 2;", SourceKind.WEAK);
    SourceFile strongSrc2 = SourceFile.fromCode("c.js", "var c = 3;", SourceKind.STRONG);
    SourceFile weakSrc2 = SourceFile.fromCode("d.js", "var d = 4;", SourceKind.WEAK);

    test(srcs(strongSrc1, weakSrc1, strongSrc2, weakSrc2), expected("var a=1", "", "var c=3", ""));
  }
}
