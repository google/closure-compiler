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
package com.google.javascript.jscomp;

import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MISSING_MODULE_OR_PROVIDE;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.MISSING_PROVIDE_ERROR;

import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the "missing provides" checks in {@link ProcessClosurePrimitives} and {@link
 * ClosureRewriteModule}.
 *
 * @author stalcup@google.com (John Stalcup)
 */

@RunWith(JUnit4.class)
public final class MissingProvideTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableRewriteClosureCode();
    enableClosurePass();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    // No-op. We're just checking for warnings during Closure passes.
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {}
    };
  }

  // Test matrix:
  // -----------------------------------------------------
  // Leaf    Import type       Root    File status  Result
  // -----------------------------------------------------
  // legacy  goog.require      module  decl leg     pass
  // legacy  goog.require      module  normal       pass
  // legacy  goog.require      module  missing      fail
  // legacy  goog.module.get   module  normal       pass
  // legacy  goog.module.get   module  missing      fail
  // module  var goog.require  module  normal       pass
  // module  var goog.require  module  missing      fail
  // module  goog.module.get   module  normal       pass
  // module  goog.module.get   module  missing      fail
  // module  var goog.require  legacy  normal       pass
  // module  var goog.require  legacy  missing      fail
  // module  goog.module.get   legacy  normal       pass
  // module  goog.module.get   legacy  missing      fail
  // legacy  goog.require      legacy  normal       pass
  // legacy  goog.require      legacy  missing      fail
  // legacy  goog.module.get   legacy  normal       pass
  // legacy  goog.module.get   legacy  missing      fail

  @Test
  public void test_Legacy_Require_Module_DeclLeg_Pass() {
    String googModule =
        lines(
            "goog.module('normal.goog.module.A');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */ function A() {}",
            "exports = A;");
    String legacyScript =
        lines(
            "goog.provide('legacy.script.B');",
            "goog.require('normal.goog.module.A');",
            "new normal.goog.module.A;");

    testNoWarning(srcs(new String[] {googModule, legacyScript}));
  }

  @Test
  public void test_Legacy_Require_Module_Normal_Pass() {
    String googModule =
        lines(
            "goog.module('normal.goog.module.A');",
            "/** @constructor */ function A() {}",
            "exports = A;");
    String legacyScript =
        lines(
            "goog.provide('legacy.script.B');",
            "goog.require('normal.goog.module.A');");

    testNoWarning(srcs(new String[] {googModule, legacyScript}));
  }

  @Test
  public void test_Legacy_Require_Module_Missing_Fail() {
    String legacyScript =
        lines(
            "goog.provide('legacy.script.B');",
            "goog.scope(function() {",
            "  var A = goog.module.get('missing.goog.module.A');",
            "});");

    String msg = "Required namespace \"missing.goog.module.A\" never defined.";
    testError(legacyScript, MISSING_MODULE_OR_PROVIDE, msg);
  }

  @Test
  public void test_Legacy_ModuleGet_Module_Normal_Pass() {
    String googModule =
        lines(
            "goog.module('normal.goog.module.A');",
            "/** @constructor */ function A() {}",
            "exports = A;");
    String legacyScript =
        lines(
            "goog.provide('legacy.script.B');",
            "goog.scope(function() {",
            "  var A = goog.module.get('normal.goog.module.A');",
            "});");

    testNoWarning(srcs(new String[] {googModule, legacyScript}));
  }

  @Test
  public void test_Legacy_ModuleGet_Module_Missing_Fail() {
    String legacyScript =
        lines(
            "goog.provide('legacy.script.B');",
            "goog.scope(function() {",
            "  var A = goog.module.get('missing.goog.module.A');",
            "});");

    String msg = "Required namespace \"missing.goog.module.A\" never defined.";
    testError(legacyScript, MISSING_MODULE_OR_PROVIDE, msg);
  }

  @Test
  public void test_Module_Require_Module_Normal_Pass() {
    String googModule1 =
        lines(
            "goog.module('normal.goog.module.A');",
            "/** @constructor */ function A() {}",
            "exports = A;");
    String googModule2 =
        lines(
            "goog.module('normal.goog.module.B');",
            "var A = goog.require('normal.goog.module.A');",
            "/** @constructor */ function B() {}",
            "B.prototype = new A;",
            "exports = B;");

    testNoWarning(srcs(new String[] {googModule1, googModule2}));
  }

  @Test
  public void test_Module_Require_Module_Missing_Fail() {
    // When something is goog.require()'d in a module and the referenced thing is missing it's not
    // really possible to know if the missing thing is a module or script.
    String googModule =
        lines(
            "goog.module('normal.goog.module.B');",
            "var A = goog.require('missing.goog.module.A');",
            "/** @constructor */ function B() {}",
            "B.prototype = new A;",
            "exports = B;");

    String warning = "Required namespace \"missing.goog.module.A\" never defined.";
    testError(googModule, MISSING_MODULE_OR_PROVIDE, warning);
  }

  @Test
  public void test_Module_ModuleGet_Module_Normal_Pass() {
    String googModule1 =
        lines(
            "goog.module('normal.goog.module.A');",
            "/** @constructor */ function A() {}",
            "exports = A;");
    String googModule2 =
        lines(
            "goog.module('normal.goog.module.B');",
            "var A = goog.forwardDeclare('normal.goog.module.A');",
            "/** @constructor */ function B() {}",
            "B.prototype.createA = function() {",
            "  A = goog.module.get('normal.goog.module.A');",
            "  new A;",
            "}",
            "exports = B;");

    testNoWarning(srcs(new String[] {googModule1, googModule2}));
  }

  @Test
  public void test_Module_Require_Legacy_Normal_Pass() {
    String legacyScript =
        lines(
            "goog.provide('legacy.script.A');",
            "/** @constructor */ legacy.script.A = function () {}");
    String googModule =
        lines(
            "goog.module('normal.goog.module.B');",
            "var A = goog.require('legacy.script.A');",
            "/** @constructor */ function B() {}",
            "B.prototype = new A;",
            "exports = B;");

    testNoWarning(srcs(new String[] {legacyScript, googModule}));
  }

  @Test
  public void test_Module_Require_Legacy_Missing_Fail() {
    // When something is goog.require()'d in a module and the referenced thing is missing it's not
    // really possible to know if the missing thing is a module or script.
    String googModule =
        lines(
            "goog.module('normal.goog.module.B');",
            "var A = goog.require('legacy.script.A');",
            "/** @constructor */ function B() {}",
            "B.prototype = new A;",
            "exports = B;");

    String msg = "Required namespace \"legacy.script.A\" never defined.";
    testError(googModule, MISSING_MODULE_OR_PROVIDE, msg);
  }

  @Test
  public void test_Module_ModuleGet_Legacy_Normal_Pass() {
    // goog.module.get inside of a goog.module() can reference legacy files, to be consistent with
    // goog.require() behavior in the same context.
    String legacyScript =
        lines(
            "goog.provide('legacy.script.A');",
            "/** @constructor */ legacy.script.A = function () {}");
    String googModule =
        lines(
            "goog.module('normal.goog.module.B');",
            "var A = goog.forwardDeclare('legacy.script.A');",
            "/** @constructor */ function B() {}",
            "B.prototype.createA = function() {",
            "  A = goog.module.get('legacy.script.A');",
            "  new A;",
            "}",
            "exports = B;");

    testNoWarning(srcs(new String[] {legacyScript, googModule}));
  }

  @Test
  public void test_Module_ModuleGet_Missing_Fail() {
    // goog.module.get inside of a goog.module() cannot reference files that do not exist.
    String googModule =
        lines(
            "goog.module('normal.goog.module.B');",
            "function f() {",
            "  return goog.module.get('missing.legacy.script.A');",
            "}",
            "exports = f;");

    String msg = "Required namespace \"missing.legacy.script.A\" never defined.";
    testError(googModule, MISSING_MODULE_OR_PROVIDE, msg);
  }

  @Test
  public void test_Module_ForwardDeclare_Missing_Fail() {
    // Short goog.forwardDeclare inside a goog.module() cannot reference files that do not exist.
    String googModule =
        lines(
            "goog.module('normal.goog.module.B');",
            "var A = goog.forwardDeclare('missing.legacy.script.A');",
            "/** @constructor */ function B() {}",
            "exports = B;");

    String msg = "Required namespace \"missing.legacy.script.A\" never defined.";
    testError(googModule, MISSING_MODULE_OR_PROVIDE, msg);
  }

  @Test
  public void test_Legacy_ForwardDeclare_Missing_Pass() {
    // Legacy goog.forwardDeclare inside a goog.module() works the same as outside a module.
    String googModule =
        lines(
            "goog.module('normal.goog.module.B');",
            "goog.forwardDeclare('missing.legacy.script.A');",
            "/** @constructor */ function B() {}",
            "exports = B;");

    testNoWarning(srcs(new String[] { googModule }));
  }

  @Test
  public void test_Legacy_Require_Legacy_Normal_Pass() {
    String legacyScript =
        lines(
            "goog.provide('legacy.script.A');",
            "/** @constructor */ legacy.script.A = function () {}");
    String legacyScript2 =
        lines(
            "goog.provide('legacy.script.B');",
            "goog.require('legacy.script.A');",
            "new legacy.script.A;");

    testNoWarning(srcs(new String[] {legacyScript, legacyScript2}));
  }

  @Test
  public void test_Legacy_Require_Legacy_Missing_Fail() {
    String legacyScript =
        lines(
            "goog.provide('legacy.script.B');",
            "goog.require('legacy.script.A');",
            "new legacy.script.A;");

    String msg = "required \"legacy.script.A\" namespace never provided";
    testError(legacyScript, MISSING_PROVIDE_ERROR, msg);
  }

  @Test
  public void test_Legacy_ModuleGet_Legacy_Normal_Pass() {
    String legacyScript =
        lines(
            "goog.provide('legacy.script.A');",
            "/** @constructor */ legacy.script.A = function () {}");
    String legacyScript2 =
        lines(
            "goog.provide('legacy.script.B');",
            "/** @constructor */ legacy.script.B = function () {}",
            "B.prototype.createA = function() {",
            "  var A = goog.module.get('legacy.script.A');",
            "  new A;",
            "}");

    testNoWarning(srcs(new String[] {legacyScript, legacyScript2}));
  }

  @Test
  public void test_Legacy_ModuleGet_Legacy_Missing_Fail() {
    String legacyScript =
        lines(
            "goog.provide('legacy.script.B');",
            "/** @constructor */ legacy.script.B = function () {}",
            "B.prototype.createA = function() {",
            "  var A = goog.module.get('legacy.script.A');",
            "  new A;",
            "}");

    String msg = "Required namespace \"legacy.script.A\" never defined.";
    testError(legacyScript, MISSING_MODULE_OR_PROVIDE, msg);
  }
}
