/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.Node;

/**
 * Tests for {@link RemoveUnusedPrototypeProperties}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class RemoveUnusedNamesTest extends CompilerTestCase {
  private static final String EXTERNS =
      "/** @constructor */\n " +
      "function IFoo() { } \n" +
      "IFoo.prototype.bar; \n" +

      "/** @constructor */\n " +
      "var mExtern; \n" +
      "mExtern.bExtern; \n" +
      "mExtern['cExtern']; \n";

  public RemoveUnusedNamesTest() {
    super(EXTERNS);
    compareJsDoc = false;
  }

  private boolean canRemoveExterns = false;

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {

      @Override
      public void process(Node externs, Node root) {
        new TypeCheck(compiler,
            new SemanticReverseAbstractInterpreter(
                compiler.getTypeRegistry()),
            compiler.getTypeRegistry(),
            CheckLevel.ERROR).processForTesting(externs, root);

        new RemoveUnusedNames(
            compiler, canRemoveExterns).process(externs, root);

        // Use to remove side-effect-free artifacts that are left over.
        new UnreachableCodeElimination(compiler, true).process(externs, root);
      }
    };
  }

  @Override
  public void setUp() {
    canRemoveExterns = false;
  }

  public void testAnalyzeUnusedPrototypeProperties() {
    // Basic removal for prototype properties
    test("/** @constructor */ \n" +
        "function e(){} \n" +
        "e.prototype.a = function(){};" +
        "e.prototype.b = function(){};" +
        "var x = new e; x.a()",

        "function e(){}" +
        " e.prototype.a = function(){};" +
        "var x = new e; x.a()");
  }

  public void testAnalyzeUnusedPrototypeProperties2() {
    // TODO(user): Prototype literal not yet supported.
    // Basic removal for prototype replacement
    //test("/** @constructor */ \n" +
    //    "function e(){} \n" +
    //    "e.prototype = {a: function(){}, b: function(){}};" +
    //    "var x=new e; x.a()",
    //
    //    "function e(){}" +
    //    "e.prototype = {a: function(){}};" +
    //    "var x = new e; x.a()");
  }

  public void testAnalyzeUnusedPrototypeProperties3() {
    // Even if bExtern is out there somewhere. The type system tells us they
    // are unrelated.
    test("/** @constructor */ \n" +
        "function e(){} \n" +
           "e.prototype.a = function(){};" +
           "e.prototype.bExtern = function(){};" +
           "var x = new e;x.a()",
         "function e(){}" +
           "e.prototype.a = function(){};" +
           //"e.prototype.bExtern = function(){};" +
           "var x = new e; x.a()");

    // TODO(user): Prototype literal not yet supported.
    //test("/** @constructor */ \n" +
    //    "function e(){} \n" +
    //       "e.prototype = {a: function(){}, bExtern: function(){}};" +
    //       "var x = new e; x.a()",
    //     "function e(){}" +
    //       "e.prototype = {a: function(){}, bExtern: function(){}};" +
    //       "var x = new e; x.a()");
  }

  public void testAliasing() {
    // TODO(user): Not fully supported.
  }

  public void testStatement() {
    test("/**\n" +
         " * @fileoverview\n" +
         " * @notypecheck\n" +
         " */ \n" +
         "/** @constructor */" +
        "function e(){}" +
           "var x = e.prototype.method1 = function(){};" +
           "var y = new e; x()",
         "function e(){}" +
           "var x = function(){};" +
           "var y = new e; x()");
  }

  public void testExportedMethodsByNamingConvention() {
    // TODO(user): Not fully supported.
  }

  public void testExportedMethodsByNamingConventionAlwaysExported() {
    // TODO(user): Not fully supported.
  }
}
