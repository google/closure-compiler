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

    JSModule moduleBefore1 = new JSModule("m1");
    moduleBefore1.add(strongSrc1);
    moduleBefore1.add(weakSrc1);
    JSModule moduleAfter1 = new JSModule("m1");
    moduleAfter1.add(strongSrc1);

    JSModule moduleBefore2 = new JSModule("m2");
    moduleBefore2.add(weakSrc2);
    moduleBefore2.add(strongSrc2);
    JSModule moduleAfter2 = new JSModule("m2");
    moduleAfter2.add(strongSrc2);

    JSModule moduleBefore3 = new JSModule("m3");
    moduleBefore3.add(weakSrc3);
    JSModule moduleAfter3 = new JSModule("m3");
    moduleAfter3.add(fillFileSrc);

    JSModule weakModule = new JSModule("$weak$");
    weakModule.add(emptyWeakSrc1);
    weakModule.add(emptyWeakSrc2);
    weakModule.add(emptyWeakSrc3);

    // Expect the weak sources to be emptied and moved to a separate final module.
    test(
        srcs(new JSModule[] {moduleBefore1, moduleBefore2, moduleBefore3}),
        expected(new JSModule[] {moduleAfter1, moduleAfter2, moduleAfter3, weakModule}));
  }
}
