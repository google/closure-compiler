/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.CheckGlobalNames.UNDEFINED_NAME_WARNING;
import static com.google.javascript.jscomp.CheckGlobalNames.STRICT_MODULE_DEP_QNAME;

import com.google.javascript.rhino.Node;

/**
 * Tests for {@code CheckGlobalNames.java}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class CheckGlobalNamesTest extends CompilerTestCase {

  private boolean injectNamespace = false;

  public CheckGlobalNamesTest() {
    super("function alert() {}");
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    final CheckGlobalNames checkGlobalNames = new CheckGlobalNames(
        compiler, CheckLevel.WARNING);
    if (injectNamespace) {
      return new CompilerPass() {
        public void process(Node externs, Node js) {
          checkGlobalNames.injectNamespace(
              new GlobalNamespace(compiler, js))
              .process(externs, js);
        }
      };
    } else {
      return checkGlobalNames;
    }
  }

  @Override
  public void setUp() {
    injectNamespace = false;
    STRICT_MODULE_DEP_QNAME.level = CheckLevel.WARNING;
  }

  private static final String GET_NAMES = 
      "var a = {get d() {return 1}}; a.b = 3; a.c = {get e() {return 5}};";
  private static final String SET_NAMES =
      "var a = {set d(x) {}}; a.b = 3; a.c = {set e(y) {}};";
  private static final String NAMES = "var a = {d: 1}; a.b = 3; a.c = {e: 5};";

  public void testRefToDefinedProperties1() {
    testSame(NAMES + "alert(a.b); alert(a.c.e);");
    testSame(GET_NAMES + "alert(a.b); alert(a.c.e);");
    testSame(SET_NAMES + "alert(a.b); alert(a.c.e);");
  }

  public void testRefToDefinedProperties2() {
    testSame(NAMES + "a.x={}; alert(a.c);");
    testSame(GET_NAMES + "a.x={}; alert(a.c);");
    testSame(SET_NAMES + "a.x={}; alert(a.c);");
  }

  public void testRefToDefinedProperties3() {
    testSame(NAMES + "alert(a.d);");
    testSame(GET_NAMES + "alert(a.d);");
    testSame(SET_NAMES + "alert(a.d);");
  }

  public void testRefToMethod1() {
    testSame("function foo() {}; foo.call();");
  }

  public void testRefToMethod2() {
    testSame("function foo() {}; foo.call.call();");
  }

  public void testCallUndefinedFunctionGivesNoWaring() {
    // We don't bother checking undeclared variables--there's another
    // pass that does this already.
    testSame("foo();");
  }

  public void testRefToPropertyOfAliasedName() {
    // this is ok, because "a" was aliased
    testSame(NAMES + "alert(a); alert(a.x);");
  }

  public void testRefToUndefinedProperty1() {
    testSame(NAMES + "alert(a.x);", UNDEFINED_NAME_WARNING);
  }

  public void testRefToUndefinedProperty2() {
    testSame(NAMES + "a.x();", UNDEFINED_NAME_WARNING);
  }

  public void testRefToUndefinedProperty3() {
    testSame(NAMES + "alert(a.c.x);", UNDEFINED_NAME_WARNING);
    testSame(GET_NAMES + "alert(a.c.x);", UNDEFINED_NAME_WARNING);
    testSame(SET_NAMES + "alert(a.c.x);", UNDEFINED_NAME_WARNING);
  }
  
  public void testRefToUndefinedProperty4() {
    testSame(NAMES + "alert(a.d.x);");
    testSame(GET_NAMES + "alert(a.d.x);");
    testSame(SET_NAMES + "alert(a.d.x);");
  }  

  public void testRefToDescendantOfUndefinedProperty1() {
    testSame(NAMES + "var c = a.x.b;", UNDEFINED_NAME_WARNING);
  }

  public void testRefToDescendantOfUndefinedProperty2() {
    testSame(NAMES + "a.x.b();", UNDEFINED_NAME_WARNING);
  }

  public void testRefToDescendantOfUndefinedProperty3() {
    testSame(NAMES + "a.x.b = 3;", UNDEFINED_NAME_WARNING);
  }

  public void testUndefinedPrototypeMethodRefGivesNoWarning() {
    testSame("function Foo() {} var a = new Foo(); a.bar();");
  }

  public void testComplexPropAssignGivesNoWarning() {
    testSame("var a = {}; var b = a.b = 3;");
  }

  public void testTypedefGivesNoWarning() {
    testSame("var a = {}; /** @typedef {number} */ a.b;");
  }

  public void testRefToDescendantOfUndefinedPropertyGivesCorrectWarning() {
    testSame("", NAMES + "a.x.b = 3;", UNDEFINED_NAME_WARNING,
             UNDEFINED_NAME_WARNING.format("a.x"));
  }

  public void testNamespaceInjection() {
    injectNamespace = true;
    testSame(NAMES + "var c = a.x.b;", UNDEFINED_NAME_WARNING);
  }

  public void testNoWarningForSimpleVarModuleDep1() {
    testSame(createModuleChain(
        NAMES,
        "var c = a;"
    ));
  }

  public void testNoWarningForSimpleVarModuleDep2() {
    testSame(createModuleChain(
        "var c = a;",
        NAMES
    ));
  }
  public void testNoWarningForGoodModuleDep1() {
    testSame(createModuleChain(
        NAMES,
        "var c = a.b;"
    ));
  }

  public void testBadModuleDep1() {
    testSame(createModuleChain(
        "var c = a.b;",
        NAMES
    ), STRICT_MODULE_DEP_QNAME);
  }

  public void testBadModuleDep2() {
    testSame(createModuleStar(
        NAMES,
        "a.xxx = 3;",
        "var x = a.xxx;"
    ), STRICT_MODULE_DEP_QNAME);
  }

  public void testSelfModuleDep() {
    testSame(createModuleChain(
        NAMES + "var c = a.b;"
    ));
  }

  public void testUndefinedModuleDep1() {
    testSame(createModuleChain(
        "var c = a.xxx;",
        NAMES
    ), UNDEFINED_NAME_WARNING);
  }
}
