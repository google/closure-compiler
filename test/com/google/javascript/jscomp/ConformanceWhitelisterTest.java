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
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.Requirement.Type;
import java.io.IOException;
import java.util.List;
import junit.framework.TestCase;

@GwtIncompatible("Conformance")
public class ConformanceWhitelisterTest extends TestCase {
  public void testConformanceWhitelistAddNew() throws IOException {
    ImmutableList.Builder<SourceFile> sources = ImmutableList.builder();

    sources.add(
        SourceFile.fromCode(
            "/entry.js",
            lines("var foo = document.getElementById('name');", "foo.innerHTML = 'test';")));

    Requirement.Builder requirement = Requirement.newBuilder();
    requirement
        .setType(Type.BANNED_PROPERTY)
        .setErrorMessage("Lorem Ipsum")
        .addValue("Object.prototype.innerHTML");

    assertThat(testConformanceWhitelister(sources.build(), requirement.build()))
        .containsExactly("/entry.js");
  }

  public void testConformanceWhitelistRemove() throws IOException {
    ImmutableList.Builder<SourceFile> sources = ImmutableList.builder();

    sources.add(
        SourceFile.fromCode(
            "/entry.js",
            lines("var foo = document.getElementById('name');", "foo.outerHTML = 'test';")));

    Requirement.Builder requirement = Requirement.newBuilder();
    requirement
        .setType(Type.BANNED_PROPERTY)
        .setErrorMessage("Lorem Ipsum")
        .addValue("Object.prototype.innerHTML")
        .addWhitelist("/entry.js");

    assertThat(testConformanceWhitelister(sources.build(), requirement.build())).isEmpty();
  }

  public void testConformanceWhitelistPreserve() throws IOException {
    ImmutableList.Builder<SourceFile> sources = ImmutableList.builder();

    sources.add(
        SourceFile.fromCode(
            "/entry.js",
            lines("var foo = document.getElementById('name');", "foo.innerHTML = 'test';")));

    Requirement.Builder requirement = Requirement.newBuilder();
    requirement
        .setType(Type.BANNED_PROPERTY)
        .setErrorMessage("Lorem Ipsum")
        .addValue("Object.prototype.innerHTML")
        .addWhitelist("/entry.js");

    assertThat(testConformanceWhitelister(sources.build(), requirement.build()))
        .containsExactly("/entry.js");
  }

  // TODO(bangert): Evaluate if this is always the best behaviour.
  // The current behaviour pushes the behaviour of how to cluster the whitelist to the program
  // driving ConformanceWhitelister. 
  public void testConformanceWhitelistBreaksDownFolder() throws IOException {
    ImmutableList.Builder<SourceFile> sources = ImmutableList.builder();

    sources.add(
        SourceFile.fromCode(
            "/test/entry.js",
            lines("var foo = document.getElementById('name');", "foo.innerHTML = 'test';")));

    Requirement.Builder requirement = Requirement.newBuilder();
    requirement
        .setType(Type.BANNED_PROPERTY)
        .setErrorMessage("Lorem Ipsum")
        .addValue("Object.prototype.innerHTML")
        .addWhitelist("/test/");

    assertThat(testConformanceWhitelister(sources.build(), requirement.build()))
        .containsExactly("/test/entry.js");
  }

  private ImmutableSet<String> testConformanceWhitelister(
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
    assertTrue(result.success);

    return ConformanceWhitelister.getViolatingPaths(
        compiler, compiler.getExternsRoot(), compiler.getJsRoot(), config);
  }
}
