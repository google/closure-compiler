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

package com.google.javascript.jscomp.lint;

import static com.google.javascript.jscomp.lint.CheckEs6Modules.DUPLICATE_IMPORT;
import static com.google.javascript.jscomp.lint.CheckEs6Modules.NO_DEFAULT_EXPORT;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CheckEs6ModulesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckEs6Modules(compiler);
  }

  @Test
  public void testDuplicateImports() {
    testSame("import singleImport from 'file';");
    testSame("import first from 'first'; import second from 'second';");
    testWarning("import * as first from 'file'; import {second} from 'file';", DUPLICATE_IMPORT);
  }

  @Test
  public void testNoDefaultExport() {
    testWarning("export default 0", NO_DEFAULT_EXPORT);
  }
}
