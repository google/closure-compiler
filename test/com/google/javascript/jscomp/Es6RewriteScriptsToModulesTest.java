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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Es6RewriteScriptsToModules} */
@RunWith(JUnit4.class)
public final class Es6RewriteScriptsToModulesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteScriptsToModules(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    return options;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testImportedScript() {
    Compiler compiler = createCompiler();
    CompilerOptions options = getOptions();
    compiler.init(
        ImmutableList.<SourceFile>of(),
        ImmutableList.of(
            SourceFile.fromCode("/script.js", ""),
            SourceFile.fromCode("/module.js", "import '/script.js';")),
        options);
    Node root = compiler.parseInputs();
    assertThat(compiler.getErrors()).isEmpty();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = externsRoot.getNext();
    getProcessor(compiler).process(externsRoot, mainRoot);
    assertThat(mainRoot.getFirstFirstChild().isModuleBody()).isTrue();
  }

  @Test
  public void testNonImportedScript() {
    testSame(
        srcs(
            SourceFile.fromCode("/script.js", ""),
            SourceFile.fromCode("/module.js", "export default 'foo';")));
  }
}
