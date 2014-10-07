/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.InputId;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Tests for {@link ES6ModuleLoader} that exercise file system resolution.
 *
 * @author sayrer@gmail.com (Rob Sayre)
 */

public class ES6ModuleLoaderFileSystemTest {
  private ES6ModuleLoader loader;
  private Compiler compiler;
  private String rootPath;

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private void writeFile(File f, String s) throws IOException {
    Files.write(f.toPath(), s.getBytes());
  }

  @Before
  public void setup() {
    final File indexA, indexB, appFile;
    try {
      final File tempDirA = tempFolder.newFolder("A");
      indexA = new File(tempDirA, "index.js");
      writeFile(indexA, "alert('A');");

      final File tempDirB = tempFolder.newFolder("B");
      indexB = new File(tempDirB, "index.js");
      writeFile(indexB, "alert('B');");

      appFile = tempFolder.newFile("app.js");
      writeFile(appFile, "alert('app');");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    SourceFile in1 = SourceFile.fromFile(indexA);
    SourceFile in2 = SourceFile.fromFile(indexB);
    SourceFile in3 = SourceFile.fromFile(appFile);

    compiler = new Compiler();
    compiler.init(
        ImmutableList.<SourceFile>of(),
        ImmutableList.of(in1, in2, in3),
        new CompilerOptions());

    rootPath = tempFolder.getRoot().getPath() + "/";
    loader = ES6ModuleLoader.createNaiveLoader(compiler, rootPath);
  }

  private CompilerInput getInput(String s) {
    return compiler.getInput(new InputId(rootPath + s));
  }

  @Test
  public void testFileSystem() {
    CompilerInput inputA = getInput("A/index.js");
    CompilerInput inputB = getInput("B/index.js");
    CompilerInput inputApp = getInput("app.js");
    Assert.assertEquals("A/index.js", loader.getLoadAddress(inputA));
    Assert.assertEquals("A/index.js", loader.locate("../A", inputB));
    Assert.assertEquals("A/index.js", loader.locate("./A", inputApp));
  }
}
