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
package com.google.javascript.jscomp.lint;

import static com.google.javascript.jscomp.lint.CheckPrototypeProperties.ILLEGAL_PROTOTYPE_MEMBER;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;

public final class CheckPrototypePropertiesTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckPrototypeProperties(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  public void testNoWarning() {
    testSame("function C() {}; C.prototype.foo = null;");
    testSame("function C() {}; C.prototype.foo = undefined;");
    testSame("function C() {}; C.prototype.foo;");
    testSame("function C() {}; C.prototype.foo = 0;");
    testSame("function C() {}; C.prototype.foo = 'someString';");
    testSame("function C() {}; C.prototype.foo = function() {};");
    testSame("function C() {}; /** @enum {number} */ C.prototype.foo = { BAR: 0 };");
  }

  public void testWarnings() {
    testSame("function C() {}; C.prototype.foo = [];", ILLEGAL_PROTOTYPE_MEMBER);
    testSame("function C() {}; C.prototype.foo = {};", ILLEGAL_PROTOTYPE_MEMBER);
    testSame("function C() {}; C.prototype.foo = { BAR: 0 };", ILLEGAL_PROTOTYPE_MEMBER);
  }
}
