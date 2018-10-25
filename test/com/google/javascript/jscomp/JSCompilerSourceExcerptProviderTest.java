/*
 * Copyright 2007 The Closure Compiler Authors.
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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JSCompilerSourceExcerptProviderTest {
  private SourceExcerptProvider provider;

  @Before
  public void setUp() throws Exception {
    SourceFile foo = SourceFile.fromCode("foo",
        "foo:first line\nfoo:second line\nfoo:third line\n");
    SourceFile bar = SourceFile.fromCode("bar",
        "bar:first line\nbar:second line\nbar:third line\nbar:fourth line\n");
    SourceFile foo2 = SourceFile.fromCode("foo2",
        "foo2:first line\nfoo2:second line\nfoo2:third line");
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.init(
        ImmutableList.<SourceFile>of(),
        ImmutableList.of(foo, bar, foo2),
        options);
    this.provider = compiler;
  }

  @Test
  public void testExcerptOneLine() {
    assertThat(provider.getSourceLine("foo", 1)).isEqualTo("foo:first line");
    assertThat(provider.getSourceLine("foo", 2)).isEqualTo("foo:second line");
    assertThat(provider.getSourceLine("foo", 3)).isEqualTo("foo:third line");
    assertThat(provider.getSourceLine("bar", 1)).isEqualTo("bar:first line");
    assertThat(provider.getSourceLine("bar", 2)).isEqualTo("bar:second line");
    assertThat(provider.getSourceLine("bar", 3)).isEqualTo("bar:third line");
    assertThat(provider.getSourceLine("bar", 4)).isEqualTo("bar:fourth line");
  }

  @Test
  public void testExcerptLineFromInexistentSource() {
    assertThat(provider.getSourceLine("inexistent", 1)).isNull();
    assertThat(provider.getSourceLine("inexistent", 7)).isNull();
    assertThat(provider.getSourceLine("inexistent", 90)).isNull();
  }

  @Test
  public void testExcerptInexistentLine() {
    assertThat(provider.getSourceLine("foo", 0)).isNull();
    assertThat(provider.getSourceLine("foo", 4)).isNull();
    assertThat(provider.getSourceLine("bar", 0)).isNull();
    assertThat(provider.getSourceLine("bar", 5)).isNull();
  }

  @Test
  public void testExceptNoNewLine() {
    assertThat(provider.getSourceLine("foo2", 1)).isEqualTo("foo2:first line");
    assertThat(provider.getSourceLine("foo2", 2)).isEqualTo("foo2:second line");
    assertThat(provider.getSourceLine("foo2", 3)).isEqualTo("foo2:third line");
    assertThat(provider.getSourceLine("foo2", 4)).isNull();
  }

  @Test
  public void testExcerptRegion() {
    assertRegionWellFormed("foo", 1);
    assertRegionWellFormed("foo", 2);
    assertRegionWellFormed("foo", 3);
    assertRegionWellFormed("bar", 1);
    assertRegionWellFormed("bar", 2);
    assertRegionWellFormed("bar", 3);
    assertRegionWellFormed("bar", 4);
  }

  @Test
  public void testExcerptRegionFromInexistentSource() {
    assertThat(provider.getSourceRegion("inexistent", 0)).isNull();
    assertThat(provider.getSourceRegion("inexistent", 6)).isNull();
    assertThat(provider.getSourceRegion("inexistent", 90)).isNull();
  }

  @Test
  public void testExcerptInexistentRegion() {
    assertThat(provider.getSourceRegion("foo", 0)).isNull();
    assertThat(provider.getSourceRegion("foo", 4)).isNull();
    assertThat(provider.getSourceRegion("bar", 0)).isNull();
    assertThat(provider.getSourceRegion("bar", 5)).isNull();
  }

  /**
   * Asserts that a region is 'well formed': it must not be an empty and
   * cannot start or finish by a carriage return. In addition, it must
   * contain the line whose region we are taking.
   */
  private void assertRegionWellFormed(String sourceName, int lineNumber) {
    Region region = provider.getSourceRegion(sourceName, lineNumber);
    assertThat(region).isNotNull();
    String sourceRegion = region.getSourceExcerpt();
    assertThat(sourceRegion).isNotNull();
    if (lineNumber == 1) {
      assertThat(region.getBeginningLineNumber()).isEqualTo(1);
    } else {
      assertThat(region.getBeginningLineNumber()).isAtMost(lineNumber);
    }
    assertThat(lineNumber).isAtMost(region.getEndingLineNumber());
    assertWithMessage(sourceRegion).that(sourceRegion.length()).isNotSameAs(0);
    assertWithMessage(sourceRegion).that(sourceRegion.charAt(0)).isNotSameAs('\n');
    assertWithMessage(sourceRegion)
        .that(sourceRegion.charAt(sourceRegion.length() - 1))
        .isNotSameAs('\n');
    String line = provider.getSourceLine(sourceName, lineNumber);
    assertWithMessage(sourceRegion).that(sourceRegion.contains(line)).isTrue();
  }
}
