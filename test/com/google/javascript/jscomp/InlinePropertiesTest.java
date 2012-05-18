/*
 * Copyright 2012 The Closure Compiler Authors.
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


/**
 * @author johnlenz@google.com (John Lenz)
 */
public class InlinePropertiesTest extends CompilerTestCase {

  private static final String EXTERNS =
      "Function.prototype.call=function(){};" +
      "Function.prototype.inherits=function(){};" +
      "prop.toString;" +
      "var google = { gears: { factory: {}, workerPool: {} } };";

  public InlinePropertiesTest() {
    super(EXTERNS);
    enableNormalize();
    enableTypeCheck(CheckLevel.WARNING);
    enableClosurePass();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new InlineProperties(compiler);
  }

  public void testConstInstanceProp1() {
    // Replace a reference to known constant property.
    test(
        "/** @constructor */\n" +
        "function C() {\n" +
        "  this.foo = 1;\n" +
        "}\n" +
        "new C().foo;",
        "function C() {\n" +
        "  this.foo = 1;\n" +
        "}\n" +
        "new C(), 1;");
  }

  public void testConstInstanceProp2() {
    // Replace a constant reference
    test(
        "/** @constructor */\n" +
        "function C() {\n" +
        "  this.foo = 1;\n" +
        "}\n" +
        "var x = new C();\n" +
        "x.foo;",
        "function C() {\n" +
        "  this.foo = 1\n" +
        "}\n" +
        "var x = new C();\n" +
        "1;\n");
  }


  public void testConstInstanceProp3() {
    // Replace a constant reference
    test(
        "/** @constructor */\n" +
        "function C() {\n" +
        "  this.foo = 1;\n" +
        "}\n" +
        "/** @type {C} */\n" +
        "var x = new C();\n" +
        "x.foo;",
        "function C() {\n" +
        "  this.foo = 1\n" +
        "}\n" +
        "var x = new C();\n" +
        "1;\n");
  }

  public void testConstInstanceProp4() {
    // This pass replies on DisambiguateProperties to distinguish like named
    // properties so it doesn't handle this case.
    testSame(
        "/** @constructor */\n" +
        "function C() {\n" +
        "  this.foo = 1;\n" +
        "}\n" +
        "/** @constructor */\n" +
        "function B() {\n" +
        "  this.foo = 1;\n" +
        "}\n" +
        "new C().foo;\n");
  }


  public void testConstClassProps1() {
    // For now, don't inline constant class properties,
    // CollapseProperties should handle this in most cases.
    testSame(
        "/** @constructor */\n" +
        "function C() {\n" +
        "}\n" +
        "C.foo = 1;\n" +
        "C.foo;");
  }

  public void testConstClassProps2() {
    // Don't confuse, class properties with instance properties
    testSame(
        "/** @constructor */\n" +
        "function C() {\n" +
        "  this.foo = 1;\n" +
        "}\n" +
        "C.foo;");
  }

  public void testConstClassProps3() {
    // Don't confuse, class properties with prototype properties
    testSame(
        "/** @constructor */\n" +
        "function C() {}\n" +
        "C.prototype.foo = 1;\n" +
        "c.foo;\n");
  }

  public void testNonConstClassProp1() {
    testSame(
        "/** @constructor */\n" +
        "function C() {\n" +
        "  this.foo = 1;\n" +
        "}\n" +
        "var x = new C();\n" +
        "alert(x.foo);\n" +
        "delete x.foo;");
  }

  public void testNonConstClassProp2() {
    testSame(
        "/** @constructor */\n" +
        "function C() {\n" +
        "  this.foo = 1;\n" +
        "}\n" +
        "var x = new C();\n" +
        "alert(x.foo);\n" +
        "x.foo = 2;");
  }

  public void testNonConstructorClassProp1() {
    testSame(
        "function C() {\n" +
        "  this.foo = 1;\n" +
        "  return this;\n" +
        "}\n" +
        "C().foo;");
  }

  public void testConditionalClassProp1() {
    testSame(
        "/** @constructor */\n" +
        "function C() {\n" +
        "  if (false) this.foo = 1;\n" +
        "}\n" +
        "new C().foo;");
  }

  public void testConstPrototypeProp1() {
    test(
        "/** @constructor */\n" +
        "function C() {}\n" +
        "C.prototype.foo = 1;\n" +
        "new C().foo;\n",
        "function C() {}\n" +
        "C.prototype.foo = 1;\n" +
        "new C(), 1;\n");
  }

  public void testConstPrototypeProp2() {
    test(
        "/** @constructor */\n" +
        "function C() {}\n" +
        "C.prototype.foo = 1;\n" +
        "var x = new C();\n" +
        "x.foo;\n",
        "function C() {}\n" +
        "C.prototype.foo = 1;\n" +
        "var x = new C();\n" +
        "1;\n");
  }
}
