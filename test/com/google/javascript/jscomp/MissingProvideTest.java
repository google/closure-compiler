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

import static com.google.javascript.jscomp.ClosureRewriteModule.MISSING_MODULE;
import static com.google.javascript.jscomp.ClosureRewriteModule.MISSING_MODULE_OR_PROVIDE;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.MISSING_PROVIDE_ERROR;

import com.google.javascript.rhino.Node;

/**
 * Tests for the "missing provides" checks in {@link ProcessClosurePrimitives} and {@link
 * ClosureRewriteModule}.
 *
 */

public final class MissingProvideTest extends Es6CompilerTestCase {

  public MissingProvideTest() {
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
  // legacy  goog.require      module  normal       fail
  // legacy  goog.require      module  missing      fail
  // legacy  goog.module.get   module  normal       pass
  // legacy  goog.module.get   module  missing      fail
  // module  var goog.require  module  normal       pass
  // module  var goog.require  module  missing      fail
  // module  goog.module.get   module  normal       pass
  // module  goog.module.get   module  missing      pass
  // module  var goog.require  legacy  normal       pass
  // module  var goog.require  legacy  missing      fail
  // module  goog.module.get   legacy  normal       pass
  // module  goog.module.get   legacy  missing      pass
  // legacy  goog.require      legacy  normal       pass
  // legacy  goog.require      legacy  missing      fail
  // legacy  goog.module.get   legacy  normal       fail
  // legacy  goog.module.get   legacy  missing      fail

  public void test_Legacy_Require_Module_DeclLeg_Pass() {
    String googModule =
        LINE_JOINER.join(
            "goog.module('normal.goog.module.A');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */ function A() {}",
            "exports = A;");
    String legacyScript =
        LINE_JOINER.join(
            "goog.provide('legacy.script.B');",
            "goog.require('normal.goog.module.A');",
            "new normal.goog.module.A;");

    test(new String[] {googModule, legacyScript}, null, null, null, null);
  }

  public void test_Legacy_Require_Module_Normal_Fail() {
    String googModule =
        LINE_JOINER.join(
            "goog.module('normal.goog.module.A');",
            "/** @constructor */ function A() {}",
            "exports = A;");
    String legacyScript =
        LINE_JOINER.join(
            "goog.provide('legacy.script.B');",
            "goog.require('normal.goog.module.A');");

    String warning = "required \"normal.goog.module.A\" namespace never provided";
    test(new String[] {googModule, legacyScript}, null, MISSING_PROVIDE_ERROR, null, warning);
  }

  public void test_Legacy_Require_Module_Missing_Fail() {
    String legacyScript =
        LINE_JOINER.join(
            "goog.provide('legacy.script.B');",
            "goog.scope(function() {",
            "  var A = goog.module.get('missing.goog.module.A');",
            "});");

    String warning = "Required module \"missing.goog.module.A\" never defined.";
    test(legacyScript, null, MISSING_MODULE, null, warning);
  }

  public void test_Legacy_ModuleGet_Module_Normal_Pass() {
    String googModule =
        LINE_JOINER.join(
            "goog.module('normal.goog.module.A');",
            "/** @constructor */ function A() {}",
            "exports = A;");
    String legacyScript =
        LINE_JOINER.join(
            "goog.provide('legacy.script.B');",
            "goog.scope(function() {",
            "  var A = goog.module.get('normal.goog.module.A');",
            "});");

    test(new String[] {googModule, legacyScript}, null, null, null, null);
  }

  public void test_Legacy_ModuleGet_Module_Missing_Fail() {
    String legacyScript =
        LINE_JOINER.join(
            "goog.provide('legacy.script.B');",
            "goog.scope(function() {",
            "  var A = goog.module.get('missing.goog.module.A');",
            "});");

    String warning = "Required module \"missing.goog.module.A\" never defined.";
    test(legacyScript, null, MISSING_MODULE, null, warning);
  }

  public void test_Module_Require_Module_Normal_Pass() {
    String googModule1 =
        LINE_JOINER.join(
            "goog.module('normal.goog.module.A');",
            "/** @constructor */ function A() {}",
            "exports = A;");
    String googModule2 =
        LINE_JOINER.join(
            "goog.module('normal.goog.module.B');",
            "var A = goog.require('normal.goog.module.A');",
            "/** @constructor */ function B() {}",
            "B.prototype = new A;",
            "exports = B;");

    test(new String[] {googModule1, googModule2}, null, null, null, null);
  }

  public void test_Module_Require_Module_Missing_Fail() {
    // When something is goog.require()'d in a module and the referenced thing is missing it's not
    // really possible to know if the missing thing is a module or script.
    String googModule =
        LINE_JOINER.join(
            "goog.module('normal.goog.module.B');",
            "var A = goog.require('missing.goog.module.A');",
            "/** @constructor */ function B() {}",
            "B.prototype = new A;",
            "exports = B;");

    String warning = "Required namespace \"missing.goog.module.A\" never defined.";
    test(googModule, null, MISSING_MODULE_OR_PROVIDE, null, warning);
  }

  public void test_Module_ModuleGet_Module_Normal_Pass() {
    String googModule1 =
        LINE_JOINER.join(
            "goog.module('normal.goog.module.A');",
            "/** @constructor */ function A() {}",
            "exports = A;");
    String googModule2 =
        LINE_JOINER.join(
            "goog.module('normal.goog.module.B');",
            "var A = goog.forwardDeclare('normal.goog.module.A');",
            "/** @constructor */ function B() {}",
            "B.prototype.createA = function() {",
            "  A = goog.module.get('normal.goog.module.A');",
            "  new A;",
            "}",
            "exports = B;");

    test(new String[] {googModule1, googModule2}, null, null, null, null);
  }

  public void test_Module_ModuleGet_Module_Missing_Pass() {
    // When something is goog.module.get()'d in a module and the referenced thing is missing it's
    // not really possible to know if the missing thing is a module or script and since missing
    // scripts are allowed, this bad reference is allowed.
    String googModule =
        LINE_JOINER.join(
            "goog.module('normal.goog.module.B');",
            "var A = goog.forwardDeclare('missing.goog.module.A');",
            "/** @constructor */ function B() {}",
            "B.prototype.createA = function() {",
            "  A = goog.module.get('missing.goog.module.A');",
            "  new A;",
            "}",
            "exports = B;");

    test(new String[] {googModule}, null, null, null, null);
  }

  public void test_Module_Require_Legacy_Normal_Pass() {
    String legacyScript =
        LINE_JOINER.join(
            "goog.provide('legacy.script.A');",
            "/** @constructor */ legacy.script.A = function () {}");
    String googModule =
        LINE_JOINER.join(
            "goog.module('normal.goog.module.B');",
            "var A = goog.require('legacy.script.A');",
            "/** @constructor */ function B() {}",
            "B.prototype = new A;",
            "exports = B;");

    test(new String[] {legacyScript, googModule}, null, null, null, null);
  }

  public void test_Module_Require_Legacy_Missing_Fail() {
    // When something is goog.require()'d in a module and the referenced thing is missing it's not
    // really possible to know if the missing thing is a module or script.
    String googModule =
        LINE_JOINER.join(
            "goog.module('normal.goog.module.B');",
            "var A = goog.require('legacy.script.A');",
            "/** @constructor */ function B() {}",
            "B.prototype = new A;",
            "exports = B;");

    String warning = "Required namespace \"legacy.script.A\" never defined.";
    test(googModule, null, MISSING_MODULE_OR_PROVIDE, null, warning);
  }

  public void test_Module_ModuleGet_Legacy_Normal_Pass() {
    // goog.module.get inside of a goog.module() can reference legacy files, to be consistent with
    // goog.require() behavior in the same context.
    String legacyScript =
        LINE_JOINER.join(
            "goog.provide('legacy.script.A');",
            "/** @constructor */ legacy.script.A = function () {}");
    String googModule =
        LINE_JOINER.join(
            "goog.module('normal.goog.module.B');",
            "var A = goog.forwardDeclare('legacy.script.A');",
            "/** @constructor */ function B() {}",
            "B.prototype.createA = function() {",
            "  A = goog.module.get('legacy.script.A');",
            "  new A;",
            "}",
            "exports = B;");

    test(new String[] {legacyScript, googModule}, null, null, null, null);
  }

  public void test_Module_ModuleGet_Legacy_Missing_Pass() {
    // goog.module.get inside of a goog.module() can reference legacy files and referenced legacy
    // files are allowed to not exist.
    String googModule =
        LINE_JOINER.join(
            "goog.module('normal.goog.module.B');",
            "var A = goog.forwardDeclare('legacy.script.A');",
            "/** @constructor */ function B() {}",
            "B.prototype.createA = function() {",
            "  A = goog.module.get('legacy.script.A');",
            "  new A;",
            "}",
            "exports = B;");

    test(new String[] {googModule}, null, null, null, null);
  }

  public void test_Legacy_Require_Legacy_Normal_Pass() {
    String legacyScript =
        LINE_JOINER.join(
            "goog.provide('legacy.script.A');",
            "/** @constructor */ legacy.script.A = function () {}");
    String legacyScript2 =
        LINE_JOINER.join(
            "goog.provide('legacy.script.B');",
            "goog.require('legacy.script.A');",
            "new legacy.script.A;");

    test(new String[] {legacyScript, legacyScript2}, null, null, null, null);
  }

  public void test_Legacy_Require_Legacy_Missing_Fail() {
    String legacyScript =
        LINE_JOINER.join(
            "goog.provide('legacy.script.B');",
            "goog.require('legacy.script.A');",
            "new legacy.script.A;");

    String warning = "required \"legacy.script.A\" namespace never provided";
    test(legacyScript, null, MISSING_PROVIDE_ERROR, null, warning);
  }

  public void test_Legacy_ModuleGet_Legacy_Normal_Fail() {
    String legacyScript =
        LINE_JOINER.join(
            "goog.provide('legacy.script.A');",
            "/** @constructor */ legacy.script.A = function () {}");
    String legacyScript2 =
        LINE_JOINER.join(
            "goog.provide('legacy.script.B');",
            "/** @constructor */ legacy.script.B = function () {}",
            "B.prototype.createA = function() {",
            "  var A = goog.module.get('legacy.script.A');",
            "  new A;",
            "}");

    String warning = "Required module \"legacy.script.A\" never defined.";
    test(new String[] {legacyScript, legacyScript2}, null, MISSING_MODULE, null, warning);
  }

  public void test_Legacy_ModuleGet_Legacy_Missing_Fail() {
    String legacyScript =
        LINE_JOINER.join(
            "goog.provide('legacy.script.B');",
            "/** @constructor */ legacy.script.B = function () {}",
            "B.prototype.createA = function() {",
            "  var A = goog.module.get('legacy.script.A');",
            "  new A;",
            "}");

    String warning = "Required module \"legacy.script.A\" never defined.";
    test(legacyScript, null, MISSING_MODULE, null, warning);
  }
}
