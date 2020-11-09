/*
 * Copyright 2016 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.lint.CheckDefaultExportOfGoogModule.DEFAULT_EXPORT_GOOG_MODULE;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CheckDefaultExportOfGoogModuleTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckDefaultExportOfGoogModule(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Test
  public void testWarnsOnDefaultExportInGoogModule() {
    // goog.module default export
    test(
        srcs("goog.module('a'); class Foo {}; exports = Foo;"),
        warning(DEFAULT_EXPORT_GOOG_MODULE)
            .withMessageContaining(
                "Please use named exports instead (`exports = {Foo};`) and change the import sites"
                    + " to use destructuring (`const {Foo} = goog.require('...');`)."));

    test(
        srcs("goog.module('a'); exports = class Foo {};"),
        warning(DEFAULT_EXPORT_GOOG_MODULE)
            .withMessageContaining(
                "Please use named exports instead (`exports = {MyVariable};`) and change the"
                    + " import sites to use destructuring (`const {MyVariable} ="
                    + " goog.require('...');`)."));

    testNoWarning("goog.module('a'); class Foo {}; exports = {Foo};");
    testNoWarning("goog.module('a'); class Foo {}; exports.default = Foo;");

    // No warning on ES6 module default export.
    testNoWarning("export default class {};");

    // No warning on assignment to a variable named exports at SCRIPT.
    testNoWarning("class Foo {}; var exports = Foo;");
    testNoWarning("class Foo {}; var exports; exports = Foo;");
    testNoWarning("class Foo {}; var exports = {Foo};");
    testNoWarning("class Foo {}; var exports; exports = {Foo};");

    // No warning on assignment to a variable named exports inside goog.scope.
    testNoWarning(
        lines(
            "goog.scope(function() {",
            "  var namespace = my.namespace;",
            "  var module = goog.module.get('some.module');",
            "  class Foo {}",
            "  var exports = Foo;",
            "});"));
  }
}
