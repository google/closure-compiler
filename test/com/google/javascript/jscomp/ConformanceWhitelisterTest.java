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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CompilerTestCase.lines;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.Requirement.Type;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@GwtIncompatible("Conformance")
@RunWith(JUnit4.class)
public class ConformanceWhitelisterTest {
  @Test
  public void testConformanceWhitelistAddNew() throws IOException {
    ImmutableList.Builder<SourceFile> sources = ImmutableList.builder();

    sources.add(
        SourceFile.fromCode(
            "/entry.js",
            lines("var foo = document.getElementById('name');", "foo.innerHTML = 'test';")));

    Requirement requirement =
        Requirement.newBuilder()
            .setType(Type.BANNED_PROPERTY)
            .setErrorMessage("Lorem Ipsum")
            .addValue("Object.prototype.innerHTML")
            .build();

    assertThat(testConformanceWhitelister(sources.build(), requirement))
        .containsExactly("/entry.js", 2);
  }

  @Test
  public void testConformanceWhitelistRemove() throws IOException {
    ImmutableList.Builder<SourceFile> sources = ImmutableList.builder();

    sources.add(
        SourceFile.fromCode(
            "/entry.js",
            lines("var foo = document.getElementById('name');", "foo.outerHTML = 'test';")));

    Requirement requirement =
        Requirement.newBuilder()
            .setType(Type.BANNED_PROPERTY)
            .setErrorMessage("Lorem Ipsum")
            .addValue("Object.prototype.innerHTML")
            .addWhitelist("/entry.js")
            .build();

    assertThat(testConformanceWhitelister(sources.build(), requirement)).isEmpty();
  }

  @Test
  public void testConformanceWhitelistPreserve() throws IOException {
    ImmutableList.Builder<SourceFile> sources = ImmutableList.builder();

    sources.add(
        SourceFile.fromCode(
            "/entry.js",
            lines("var foo = document.getElementById('name');", "foo.innerHTML = 'test';")));

    Requirement requirement =
        Requirement.newBuilder()
            .setType(Type.BANNED_PROPERTY)
            .setErrorMessage("Lorem Ipsum")
            .addValue("Object.prototype.innerHTML")
            .addWhitelist("/entry.js")
            .build();

    assertThat(testConformanceWhitelister(sources.build(), requirement))
        .containsExactly("/entry.js", 2);
  }

  // TODO(bangert): Evaluate if this is always the best behaviour.
  // The current behaviour pushes the behaviour of how to cluster the whitelist to the program
  // driving ConformanceWhitelister.
  @Test
  public void testConformanceWhitelistBreaksDownFolder() throws IOException {
    ImmutableList.Builder<SourceFile> sources = ImmutableList.builder();

    sources.add(
        SourceFile.fromCode(
            "/test/entry.js",
            lines("var foo = document.getElementById('name');", "foo.innerHTML = 'test';")));

    Requirement requirement =
        Requirement.newBuilder()
            .setType(Type.BANNED_PROPERTY)
            .setErrorMessage("Lorem Ipsum")
            .addValue("Object.prototype.innerHTML")
            .addWhitelist("/test/")
            .build();

    assertThat(testConformanceWhitelister(sources.build(), requirement))
        .containsExactly("/test/entry.js", 2);
  }

  private ImmutableMultimap<String, Integer> testConformanceWhitelister(
      ImmutableList<SourceFile> sources, Requirement config) throws IOException {

    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    // TODO(bangert): Support banned property on OBJECT even if types are not checked.
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setChecksOnly(true);
    List<SourceFile> externs =
        AbstractCommandLineRunner.getBuiltinExterns(options.getEnvironment());
    Compiler compiler = new Compiler();
    Result result = compiler.compile(externs, sources, options);
    assertThat(result.success).isTrue();

    ImmutableMultimap.Builder<String, Integer> errors = ImmutableMultimap.builder();
    for (Node node :
        ConformanceWhitelister.getViolatingNodes(
            compiler, compiler.getExternsRoot(), compiler.getJsRoot(), config)) {
      errors.put(node.getSourceFileName(), node.getLineno());
    }
    assertThat(errors.build().keySet())
        .containsExactlyElementsIn(
            ConformanceWhitelister.getViolatingPaths(
                compiler, compiler.getExternsRoot(), compiler.getJsRoot(), config));
    return errors.build();
  }
}
