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
  public void testSingleModule() {
    SourceFile strongSrc1 = SourceFile.fromCode("a.js", "var a = 1;", SourceKind.STRONG);
    SourceFile strongSrc2 = SourceFile.fromCode("b.js", "var b = 2;", SourceKind.STRONG);
    SourceFile weakSrc1 = SourceFile.fromCode("c.js", "var c = 3;", SourceKind.WEAK);
    SourceFile weakSrc2 = SourceFile.fromCode("d.js", "var d = 4;", SourceKind.WEAK);
    SourceFile emptyWeakSrc1 = SourceFile.fromCode("c.js", "");
    SourceFile emptyWeakSrc2 = SourceFile.fromCode("d.js", "");

    // Expect the weak sources to be emptied and moved to the end.
    test(
        srcs(strongSrc1, weakSrc1, strongSrc2, weakSrc2),
        expected(strongSrc1, strongSrc2, emptyWeakSrc1, emptyWeakSrc2));
  }

  @Test
  public void testMultipleModules() {
    SourceFile strongSrc1 = SourceFile.fromCode("a.js", "var a = 1;", SourceKind.STRONG);
    SourceFile strongSrc2 = SourceFile.fromCode("b.js", "var b = 2;", SourceKind.STRONG);
    SourceFile weakSrc1 = SourceFile.fromCode("c.js", "var c = 3;", SourceKind.WEAK);
    SourceFile weakSrc2 = SourceFile.fromCode("d.js", "var d = 4;", SourceKind.WEAK);
    SourceFile weakSrc3 = SourceFile.fromCode("e.js", "var e = 5;", SourceKind.WEAK);
    SourceFile emptyWeakSrc1 = SourceFile.fromCode("c.js", "");
    SourceFile emptyWeakSrc2 = SourceFile.fromCode("d.js", "");
    SourceFile emptyWeakSrc3 = SourceFile.fromCode("e.js", "");
    SourceFile fillFileSrc = SourceFile.fromCode("$fillFile", "");

    JSChunk chunkBefore1 = new JSChunk("m1");
    chunkBefore1.add(strongSrc1);
    chunkBefore1.add(weakSrc1);
    JSChunk chunkAfter1 = new JSChunk("m1");
    chunkAfter1.add(strongSrc1);

    JSChunk chunkBefore2 = new JSChunk("m2");
    chunkBefore2.add(weakSrc2);
    chunkBefore2.add(strongSrc2);
    JSChunk chunkAfter2 = new JSChunk("m2");
    chunkAfter2.add(strongSrc2);

    JSChunk chunkBefore3 = new JSChunk("m3");
    chunkBefore3.add(weakSrc3);
    JSChunk chunkAfter3 = new JSChunk("m3");
    chunkAfter3.add(fillFileSrc);

    JSChunk weakModule = new JSChunk("$weak$");
    weakModule.add(emptyWeakSrc1);
    weakModule.add(emptyWeakSrc2);
    weakModule.add(emptyWeakSrc3);

    // Expect the weak sources to be emptied and moved to a separate final module.
    test(
        srcs(new JSChunk[] {chunkBefore1, chunkBefore2, chunkBefore3}),
        expected(new JSChunk[] {chunkAfter1, chunkAfter2, chunkAfter3, weakModule}));
  }
}
