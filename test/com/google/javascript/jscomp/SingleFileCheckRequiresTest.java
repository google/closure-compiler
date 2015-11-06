/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.CheckRequiresForConstructors.EXTRA_REQUIRE_WARNING;
import static com.google.javascript.jscomp.CheckRequiresForConstructors.MISSING_REQUIRE_WARNING;

/**
 * Tests for {@link CheckRequiresForConstructors} in single-file mode.
 */
public final class SingleFileCheckRequiresTest extends Es6CompilerTestCase {

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.setWarningLevel(DiagnosticGroups.MISSING_REQUIRE, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.ERROR);
    return super.getOptions(options);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CheckRequiresForConstructors(compiler,
        CheckRequiresForConstructors.Mode.SINGLE_FILE);
  }

  public void testReferenceToSingleName() {
    testSameEs6("new Foo();");
    testSameEs6("new Array();");
    testSameEs6("new Error();");
  }

  public void testReferenceToQualifiedName() {
    testErrorEs6("new bar.Foo();", MISSING_REQUIRE_WARNING);
  }

  public void testExtraRequire() {
    testErrorEs6("goog.require('foo.Bar');", EXTRA_REQUIRE_WARNING);
  }

  public void testReferenceToSingleNameWithRequire() {
    testSameEs6("goog.require('Foo'); new Foo();");
  }

  public void testReferenceInDefaultParam() {
    testSameEs6("function func( a = new Bar() ){}; func();");
  }

  public void testReferenceInDestructuringParam() {
    testSameEs6("var {a = new Bar()} = b;");
  }
}
