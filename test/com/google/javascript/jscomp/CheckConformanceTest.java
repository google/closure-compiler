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

package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CheckConformance.InvalidRequirementSpec;
import com.google.javascript.jscomp.ConformanceRules.AbstractRule;
import com.google.javascript.jscomp.ConformanceRules.ConformanceResult;
import com.google.javascript.jscomp.testing.BlackHoleErrorManager;
import com.google.javascript.rhino.Node;
import com.google.protobuf.TextFormat;

import java.util.List;

/**
 * Tests for {@link CheckConformance}.
 *
 */
public class CheckConformanceTest extends CompilerTestCase {
  private String configuration;

  private static final String EXTERNS =
      "/** @constructor */ var Window;\n" +
      "/** @type {Window} */ var window;\n" +
      "var Object;\n" +
      "/** @constructor */ var Arguments;\n" +
      "Arguments.prototype.callee;\n" +
      "Arguments.prototype.caller;\n" +
      "/** @type {Arguments} */ var arguments;\n" +
      "/** @constructor \n" +
      " * @param {*=} opt_message\n" +
      " * @param {*=} opt_file\n" +
      " * @param {*=} opt_line\n" +
      " * @return {!Error} \n" +
      "*/" +
      "var Error;" +
      "var alert;" +
      "";

  private static final String DEFAULT_CONFORMANCE =
      "requirement: {\n" +
      "  type: BANNED_NAME\n" +
      "  value: 'eval'\n" +
      "   error_message: 'eval is not allowed'\n" +
      "}\n" +
      "" +
      "requirement: {\n" +
      "  type: BANNED_PROPERTY\n" +
      "  value: 'Arguments.prototype.callee'\n" +
      "  error_message: 'Arguments.prototype.callee is not allowed'\n" +
      "}\n";

  public CheckConformanceTest() {
    super(EXTERNS, true);
    enableNormalize();
    enableClosurePass();
    enableClosurePassForExpected();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = new CompilerOptions();
    super.getOptions(options);
    options.setWarningLevel(
        DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);
    options.setCodingConvention(getCodingConvention());
    return options;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    super.enableTypeCheck(CheckLevel.OFF);
    configuration = DEFAULT_CONFORMANCE;
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();
    try {
      TextFormat.merge(configuration, builder);
    } catch (Exception e) {
      Throwables.propagate(e);
    }
    return new CheckConformance(compiler, ImmutableList.of(builder.build()));
  }

  @Override
  public int getNumRepetitions() {
    // This compiler pass is not idempotent and should only be run over a
    // parse tree once.
    return 1;
  }

  public void testViolation1() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_NAME\n" +
        "  value: 'eval'\n" +
        "  error_message: 'eval is not allowed'\n" +
        "}";

    testSame(
        "eval()",
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  public void testViolation2() {
    testSame(
        "function f() { arguments.callee }",
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  public void testNotViolation1() {
    testSame(
        "/** @constructor */ function Foo() { this.callee = 'string'; }\n" +
        "/** @constructor */ function Bar() { this.callee = 1; }\n" +
        "\n" +
        "\n" +
        "function f() {\n" +
        "  var x;\n" +
        "  switch(random()) {\n" +
        "    case 1:\n" +
        "      x = new Foo();\n" +
        "      break;\n" +
        "    case 2:\n" +
        "      x = new Bar();\n" +
        "      break;\n" +
        "    default:\n" +
        "      return;\n" +
        "  }\n" +
        "  var z = x.callee;\n" +
        "}");
  }

  public void testMaybeViolation1() {
    testSame(
        "function f() { y.callee }",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testSame(
        "function f() { new Foo().callee }",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testSame(
        "function f() { new Object().callee }",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testSame(
        "function f() { /** @type {*} */ var x; x.callee }",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testSame("function f() {/** @const */ var x = {}; x.callee = 1; x.callee}");
  }

  public void testBadWhitelist1() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n" +
        "  type: BANNED_NAME\n" +
        "  value: 'eval'\n" +
        "  error_message: 'placeholder'\n" +
        "  whitelist_regexp: '('\n" +
        "}";

    testSame(
        EXTERNS,
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: invalid regex pattern\n" +
        "Requirement spec:\n" +
        "error_message: \"placeholder\"\n" +
        "whitelist_regexp: \"(\"\n" +
        "type: BANNED_NAME\n" +
        "value: \"eval\"\n",
        true /* error */);
  }

  public void testViolationWhitelisted1() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_NAME\n" +
        "  value: 'eval'\n" +
        "  error_message: 'eval is not allowed'\n" +
        "  whitelist: 'testcode'\n " +
        "}";

    testSame(
        "eval()");
  }

  public void testViolationWhitelisted2() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_NAME\n" +
        "  value: 'eval'\n" +
        "  error_message: 'eval is not allowed'\n" +
        "  whitelist_regexp: 'code$'\n " +
        "}";

    testSame(
        "eval()");
  }

  public void testFileOnOnlyApplyToIsChecked() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_NAME\n" +
        "  value: 'eval'\n" +
        "  error_message: 'eval is not allowed'\n" +
        "  only_apply_to: 'foo.js'\n " +
        "}";
    ImmutableList<SourceFile> input = ImmutableList.of(
            SourceFile.fromCode("foo.js", "eval()"));
    test(input, input, null, CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: eval is not allowed");
  }

  public void testFileNotOnOnlyApplyToIsNotChecked() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_NAME\n" +
        "  value: 'eval'\n" +
        "  error_message: 'eval is not allowed'\n" +
        "  only_apply_to: 'foo.js'\n " +
        "}";
    testSame(ImmutableList.of(SourceFile.fromCode("bar.js", "eval()")));
  }

  public void testFileOnOnlyApplyToRegexpIsChecked() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_NAME\n" +
        "  value: 'eval'\n" +
        "  error_message: 'eval is not allowed'\n" +
        "  only_apply_to_regexp: 'test.js$'\n " +
        "}";
    ImmutableList<SourceFile> input = ImmutableList.of(
            SourceFile.fromCode("foo_test.js", "eval()"));
    test(input, input, null, CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: eval is not allowed");
  }

  public void testFileNotOnOnlyApplyToRegexpIsNotChecked() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_NAME\n" +
        "  value: 'eval'\n" +
        "  error_message: 'eval is not allowed'\n" +
        "  only_apply_to_regexp: 'test.js$'\n " +
        "}";
    testSame(ImmutableList.of(SourceFile.fromCode("bar.js", "eval()")));
  }

  public void testSpecifyingWhitelistAndOnlyApplyToIsRuntimeError() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_NAME\n" +
        "  value: 'eval'\n" +
        "  error_message: 'eval is not allowed'\n" +
        "  whitelist: 'blah'\n" +
        "  only_apply_to_regexp: 'test.js$'\n " +
        "}";
    try {
      testSame(ImmutableList.of(SourceFile.fromCode("bar.js", "eval()")));
      fail("expected IllegalArgumentException");
    } catch (Exception e) {
      assertTrue(e instanceof IllegalArgumentException);
    }
  }

  public void testBannedCodePattern1() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_CODE_PATTERN\n" +
        "  value: '/** @param {string|String} a */" +
                  "function template(a) {a.blink}'\n" +
        "  error_message: 'blink is annoying'\n" +
        "}";

    testSame(
        "/** @constructor */ function Foo() { this.blink = 1; }\n" +
        "var foo = new Foo();\n" +
        "foo.blink();");

    testSame(
        EXTERNS,
        "'foo'.blink;",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: blink is annoying");

    testSame(
        EXTERNS,
        "'foo'.blink();",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: blink is annoying");

    testSame(
        EXTERNS,
        "String('foo').blink();",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: blink is annoying");

    testSame(
        EXTERNS,
        "foo.blink();",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION,
        "Possible violation: blink is annoying");
  }

  public void testBannedDep1() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_DEPENDENCY\n" +
        "  value: 'testcode'\n" +
        "  error_message: 'testcode is not allowed'\n" +
        "}";

    testSame(
        EXTERNS,
        "anything;",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: testcode is not allowed");
  }

  public void testBannedProperty() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_PROPERTY\n" +
        "  value: 'C.prototype.p'\n" +
        "  error_message: 'C.p is not allowed'\n" +
        "}";

    String declarations =
        "/** @constructor */ function C() {}\n" +
        "/** @type {string} */\n" +
        "C.prototype.p;\n" +
        "/** @constructor */ function D() {}\n" +
        "/** @type {string} */\n" +
        "D.prototype.p;\n";

    testSame(
        declarations + "var d = new D(); d.p = 'boo';");

    testSame(
        declarations + "var c = new C(); c.p = 'boo';",
        CheckConformance.CONFORMANCE_VIOLATION);

    testSame(
        declarations + "var c = new C(); var foo = c.p;",
        CheckConformance.CONFORMANCE_VIOLATION);

    testSame(
        declarations + "var c = new C(); var foo = 'x' + c.p;",
        CheckConformance.CONFORMANCE_VIOLATION);

    testSame(
        declarations + "var c = new C(); c['p'] = 'boo';",
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  public void testBannedPropertyWrite() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_PROPERTY_WRITE\n" +
        "  value: 'C.prototype.p'\n" +
        "  error_message: 'Assignment to C.p is not allowed'\n" +
        "}";

    String declarations =
        "/** @constructor */ function C() {}\n" +
        "/** @type {string} */\n" +
        "C.prototype.p;\n" +
        "/** @constructor */ function D() {}\n" +
        "/** @type {string} */\n" +
        "D.prototype.p;\n";

    testSame(
        declarations + "var d = new D(); d.p = 'boo';");

    testSame(
        declarations + "var c = new C(); c.p = 'boo';",
        CheckConformance.CONFORMANCE_VIOLATION);

    testSame(
        declarations + "var c = new C(); var foo = c.p;");

    testSame(
        declarations + "var c = new C(); var foo = 'x' + c.p;");

    testSame(
        declarations + "var c = new C(); c['p'] = 'boo';",
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  public void testBannedPropertyWriteExtern() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_PROPERTY_WRITE\n" +
        "  value: 'Element.prototype.innerHTML'\n" +
        "  error_message: 'Assignment to Element.innerHTML is not allowed'\n" +
        "}";

    String externs =
        "/** @constructor */ function Element() {}\n" +
        "/** @type {string} @implicitCast */\n" +
        "Element.prototype.innerHTML;\n";

    testSame(
        externs,
        "var e = new Element(); e.innerHTML = '<boo>';",
        CheckConformance.CONFORMANCE_VIOLATION);

    testSame(
        externs,
        "var e = new Element(); e.innerHTML = {'foo': 'bar'};",
        CheckConformance.CONFORMANCE_VIOLATION);

    testSame(
        externs,
        "var e = new Element(); e['innerHTML'] = 'foo';",
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  public void testBannedPropertyRead() {
    configuration =
        "requirement: {\n" +
        "  type: BANNED_PROPERTY_READ\n" +
        "  value: 'C.prototype.p'\n" +
        "  error_message: 'Use of C.p is not allowed'\n" +
        "}";

    String declarations =
        "/** @constructor */ function C() {}\n" +
        "/** @type {string} */\n" +
        "C.prototype.p;\n" +
        "/** @constructor */ function D() {}\n" +
        "/** @type {string} */\n" +
        "D.prototype.p;\n" +
        "function use(a) {};";

    testSame(
        declarations + "var d = new D(); d.p = 'boo';");

    testSame(
        declarations + "var c = new C(); c.p = 'boo';");

    testSame(
        declarations + "var c = new C(); use(c.p);",
        CheckConformance.CONFORMANCE_VIOLATION);

    testSame(
        declarations + "var c = new C(); var foo = c.p;",
        CheckConformance.CONFORMANCE_VIOLATION);

    testSame(
        declarations + "var c = new C(); var foo = 'x' + c.p;",
        CheckConformance.CONFORMANCE_VIOLATION);

    testSame(
        declarations + "var c = new C(); c['p'] = 'boo';");

    testSame(
        declarations + "var c = new C(); use(c['p']);",
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  public void testRestrictedCall1() {
    configuration =
        "requirement: {\n" +
        "  type: RESTRICTED_METHOD_CALL\n" +
        "  value: 'C.prototype.m:function(number)'\n" +
        "  error_message: 'm method param must be number'\n" +
        "}";

    String code =
        "/** @constructor */ function C() {}\n" +
        "/** @param {*} a */\n" +
        "C.prototype.m = function(a){}\n";

    testSame(
        code + "new C().m(1);");

    testSame(
        code + "new C().m('str');",
        CheckConformance.CONFORMANCE_VIOLATION);

    testSame(
        code + "new C().m.call(this, 1);");

    testSame(
        code + "new C().m.call(this, 'str');",
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  public void testRestrictedCall2() {
    configuration =
        "requirement: {\n" +
        "  type: RESTRICTED_NAME_CALL\n" +
        "  value: 'C.m:function(number)'\n" +
        "  error_message: 'C.m method param must be number'\n" +
        "}";

    String code =
        "/** @constructor */ function C() {}\n" +
        "/** @param {*} a */\n" +
        "C.m = function(a){}\n";

    testSame(
        code + "C.m(1);");

    testSame(
        code + "C.m('str');",
        CheckConformance.CONFORMANCE_VIOLATION);

    testSame(
        code + "C.m.call(this, 1);");

    testSame(
        code + "C.m.call(this, 'str');",
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  public void testRestrictedCall3() {
    configuration =
        "requirement: {\n" +
        "  type: RESTRICTED_NAME_CALL\n" +
        "  value: 'C:function(number)'\n" +
        "  error_message: 'C method must be number'\n" +
        "}";

    String code =
        "/** @constructor @param {...*} a */ function C(a) {}\n";

    testSame(
        code + "new C(1);");

    testSame(
        code + "new C('str');",
        CheckConformance.CONFORMANCE_VIOLATION);

    testSame(
        code + "new C(1, 1);",
        CheckConformance.CONFORMANCE_VIOLATION);

    testSame(
        code + "new C();",
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  public void testRestrictedCall4() {
    configuration =
        "requirement: {\n" +
        "  type: RESTRICTED_NAME_CALL\n" +
        "  value: 'C:function(number)'\n" +
        "  error_message: 'C method must be number'\n" +
        "}";

    String code =
        "/** @constructor @param {...*} a */ function C(a) {}\n";

    testSame(
        code + "goog.inherits(A, C);");
  }

  public void testRestrictedMethodCallThisType() {
    configuration = ""
        + "requirement: {\n"
        + "  type: RESTRICTED_METHOD_CALL\n"
        + "  value: 'Base.prototype.m:function(this:Sub,number)'\n"
        + "  error_message: 'Only call m on the subclass'\n"
        + "}";

    String code =
        "/** @constructor */\n"
        + "function Base() {}\n"
        + "/** @constructor @extends {Base} */\n"
        + "function Sub() {}\n"
        + "var b = new Base();\n"
        + "var s = new Sub();\n"
        + "var maybeB = cond ? new Base() : null;\n"
        + "var maybeS = cond ? new Sub() : null;\n";

    testSame(code + "b.m(1)", CheckConformance.CONFORMANCE_VIOLATION);
    testSame(code + "maybeB.m(1)", CheckConformance.CONFORMANCE_VIOLATION);
    testSame(code + "s.m(1)");
    testSame(code + "maybeS.m(1)");
  }

  public void testRestrictedMethodCallUsingCallThisType() {
    configuration = ""
        + "requirement: {\n"
        + "  type: RESTRICTED_METHOD_CALL\n"
        + "  value: 'Base.prototype.m:function(this:Sub,number)'\n"
        + "  error_message: 'Only call m on the subclass'\n"
        + "}";

    String code =
        "/** @constructor */\n"
        + "function Base() {}\n"
        + "/** @constructor @extends {Base} */\n"
        + "function Sub() {}\n"
        + "var b = new Base();\n"
        + "var s = new Sub();\n"
        + "var maybeB = cond ? new Base() : null;\n"
        + "var maybeS = cond ? new Sub() : null;";

    testSame(code + "b.m.call(b, 1)", CheckConformance.CONFORMANCE_VIOLATION);
    testSame(code + "b.m.call(maybeB, 1)", CheckConformance.CONFORMANCE_VIOLATION);
    testSame(code + "b.m.call(s, 1)");
    testSame(code + "b.m.call(maybeS, 1)");
  }

  public void testCustom1() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  error_message: 'placeholder'\n" +
        "}";

    testSame(
        EXTERNS,
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: missing java_class\n" +
        "Requirement spec:\n" +
        "error_message: \"placeholder\"\n" +
        "type: CUSTOM\n",
        true /* error */);
  }

  public void testCustom2() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'MissingClass'\n" +
        "  error_message: 'placeholder'\n" +
        "}";

    testSame(
        EXTERNS,
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: JavaClass not found.\n" +
        "Requirement spec:\n" +
        "error_message: \"placeholder\"\n" +
        "type: CUSTOM\n" +
        "java_class: \"MissingClass\"\n",
        true /* error */);
  }

  public void testCustom3() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.CheckConformanceTest'\n" +
        "  error_message: 'placeholder'\n" +
        "}";

    testSame(
        EXTERNS,
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: JavaClass is not a rule.\n" +
        "Requirement spec:\n" +
        "error_message: \"placeholder\"\n" +
        "type: CUSTOM\n" +
        "java_class: \"com.google.javascript.jscomp.CheckConformanceTest\"\n" +
        "",
        true /* error */);
  }

  // A custom rule missing a callable constructor.
  public static class CustomRuleMissingPublicConstructor extends AbstractRule {
    CustomRuleMissingPublicConstructor(
        AbstractCompiler compiler, Requirement requirement)
            throws InvalidRequirementSpec {
      super(compiler, requirement);
      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      // Everything is ok.
      return ConformanceResult.CONFORMANCE;
    }
  }


  // A valid custom rule.
  public static class CustomRule extends AbstractRule {
    public CustomRule(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      // Everything is ok.
      return ConformanceResult.CONFORMANCE;
    }
  }

  // A valid custom rule.
  public static class CustomRuleReport extends AbstractRule {
    public CustomRuleReport(AbstractCompiler compiler, Requirement requirement)
        throws InvalidRequirementSpec {
      super(compiler, requirement);
      if (requirement.getValueCount() == 0) {
        throw new InvalidRequirementSpec("missing value");
      }
    }

    @Override
    protected ConformanceResult checkConformance(NodeTraversal t, Node n) {
      // Everything is ok.
      return n.isScript() ? ConformanceResult.VIOLATION
          : ConformanceResult.CONFORMANCE;
    }
  }

  public void testCustom4() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.CheckConformanceTest$" +
            "CustomRuleMissingPublicConstructor'\n" +
        "  error_message: 'placeholder'\n" +
        "}";

    testSame(
        EXTERNS,
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: No valid class constructors found.\n" +
        "Requirement spec:\n" +
        "error_message: \"placeholder\"\n" +
        "type: CUSTOM\n" +
        "java_class: \"com.google.javascript.jscomp.CheckConformanceTest$" +
        "CustomRuleMissingPublicConstructor\"\n" +
        "",
        true /* error */);
  }


  public void testCustom5() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.CheckConformanceTest$CustomRule'\n" +
        "  error_message: 'placeholder'\n" +
        "}";

    testSame(
        EXTERNS,
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: missing value\n" +
        "Requirement spec:\n" +
        "error_message: \"placeholder\"\n" +
        "type: CUSTOM\n" +
        "java_class: \"com.google.javascript.jscomp.CheckConformanceTest$CustomRule\"\n" +
        "",
        true /* error */);
  }

  public void testCustom6() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.CheckConformanceTest$CustomRule'\n" +
        "  value: 'placeholder'\n" +
        "  error_message: 'placeholder'\n" +
        "}";

    testSame(
        "anything;");
  }

  public void testCustom7() {
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.CheckConformanceTest$" +
        "CustomRuleReport'\n" +
        "  value: 'placeholder'\n" +
        "  error_message: 'CustomRule Message'\n" +
        "}";

    testSame(
        EXTERNS,
        "anything;",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: CustomRule Message");
  }

  public void testCustomBanExpose() {
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanExpose'\n" +
        "  error_message: 'BanExpose Message'\n" +
        "}";

    testSame(
        EXTERNS,
        "/** @expose */ var x;",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanExpose Message");
  }

  public void testCustomRestrictThrow1() {
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanThrowOfNonErrorTypes'\n" +
        "  error_message: 'BanThrowOfNonErrorTypes Message'\n" +
        "}";

    testSame(
        EXTERNS,
        "throw 'blah';",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanThrowOfNonErrorTypes Message");
  }

  public void testCustomRestrictThrow2() {
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanThrowOfNonErrorTypes'\n" +
        "  error_message: 'BanThrowOfNonErrorTypes Message'\n" +
        "}";

    testSame("throw new Error('test');");
  }

  public void testCustomBanUnknownThis1() {
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'\n" +
        "  error_message: 'BanUnknownThis Message'\n" +
        "}";

    testSame(
        EXTERNS,
        "function f() {alert(this);}",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanUnknownThis Message");
  }

  // TODO(johnlenz): add a unit test for templated "this" values.

  public void testCustomBanUnknownThis2() {
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'\n" +
        "  error_message: 'BanUnknownThis Message'\n" +
        "}";

    testSame(
        "/** @constructor */ function C() {alert(this);}");
  }

  public void testCustomBanUnknownThis3() {
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'\n" +
        "  error_message: 'BanUnknownThis Message'\n" +
        "}";

    testSame(
        "function f() {alert(/** @type {Error} */(this));}");
  }

  public void testCustomBanUnknownThis4() {
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'\n" +
        "  error_message: 'BanUnknownThis Message'\n" +
        "}";

    testSame(
        "function f() {goog.asserts.assertInstanceof(this, Error);}");
  }

  public void testCustomBanGlobalVars1() {
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanGlobalVars'\n" +
        "  error_message: 'BanGlobalVars Message'\n" +
        "}";

    testSame(
        EXTERNS,
        "var x;",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanGlobalVars Message");

    testSame(
        EXTERNS,
        "function fn() {}",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanGlobalVars Message");

    testSame(
        "goog.provide('x');");


    // TODO(johnlenz): This might be overly conservative but doing otherwise is more complicated
    // so let see if we can get away with this.
    testSame(
        EXTERNS,
        "goog.provide('x'); var x;",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanGlobalVars Message");
  }

  public void testRequireFileoverviewVisibility() {
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.ConformanceRules$" +
                       "RequireFileoverviewVisibility'\n" +
        "  error_message: 'RequireFileoverviewVisibility Message'\n" +
        "}";

    testSame(
        EXTERNS,
        "var foo = function() {};",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: RequireFileoverviewVisibility Message");

    testSame(
        EXTERNS,
        "/**\n" +
        "  * @fileoverview\n" +
        "  */\n" +
        "var foo = function() {};",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: RequireFileoverviewVisibility Message");

    testSame(
        EXTERNS,
        "/**\n" +
        "  * @package\n" +
        "  */\n" +
        "var foo = function() {};",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: RequireFileoverviewVisibility Message");

    testSame(
        "/**\n" +
        "  * @fileoverview\n" +
        "  * @package\n" +
        "  */\n" +
        "var foo = function() {};");
  }

  public void testNoImplicitlyPublicDecls() {
    configuration =
        "requirement: {\n" +
        "  type: CUSTOM\n" +
        "  java_class: 'com.google.javascript.jscomp.ConformanceRules$" +
                       "NoImplicitlyPublicDecls'\n" +
        "  error_message: 'NoImplicitlyPublicDecls Message'\n" +
        "}";

    testSame(
        EXTERNS,
        "goog.provide('foo.bar');\n" +
        "/** @constructor */foo.bar.Baz = function(){};",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: NoImplicitlyPublicDecls Message");
    testSame(
        "/** @package\n@fileoverview */\n" +
        "goog.provide('foo.bar');\n" +
        "/** @constructor */foo.bar.Baz = function(){};");
    testSame(
        "goog.provide('foo.bar');\n" +
        "/** @package @constructor */foo.bar.Baz = function(){};");

    testSame(
        EXTERNS,
        "goog.provide('foo.bar');\n" +
        "/** @public @constructor */foo.bar.Baz = function(){};\n" +
        "/** @type {number} */foo.bar.Baz.prototype.quux = 42;",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: NoImplicitlyPublicDecls Message");
    testSame(
        "/** @fileoverview\n@package*/\n" +
        "goog.provide('foo.bar');\n" +
        "/** @public @constructor */foo.bar.Baz = function(){};\n" +
        "/** @type {number} */foo.bar.Baz.prototype.quux = 42;");
    testSame(
        "goog.provide('foo.bar');\n" +
        "/** @public @constructor */foo.bar.Baz = function(){};\n" +
        "/** @package {number} */foo.bar.Baz.prototype.quux = 42;");

    testSame(
        EXTERNS,
        "goog.provide('foo');\n" +
        "/** @public @constructor */\n" +
        "foo.Bar = function() {\n" +
        "  /** @type {number} */ this.baz = 52;\n" +
        "};",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: NoImplicitlyPublicDecls Message");
    testSame(
        "goog.provide('foo');\n" +
        "/** @public @constructor */\n" +
        "foo.Bar = function() {\n" +
        "  /** @package {number} */ this.baz = 52;\n" +
        "};");
    testSame(
        "/** @fileoverview\n@package */\n" +
        "goog.provide('foo');\n" +
        "/** @constructor */\n" +
        "foo.Bar = function() {\n" +
        "  /** @type {number} */ this.baz = 52;\n" +
        "};");

    testSame("goog.provide('foo.bar');");

    testSame(
        "goog.provide('foo');\n" +
        "/** @public @constructor */" +
        "foo.Bar = function() {};\n" +
        "/** @public */foo.Bar.prototype.baz = function() {};\n" +
        "/** @public @constructor @extends {foo.Bar} */\n" +
        "foo.Quux = function() {};\n" +
        "/** @override */foo.Quux.prototype.baz = function() {};");

    // These kinds of declarations aren't currently caught by
    // NoImplicitlyPublicDecls, but they could be.
    testSame("var foo");
    testSame("var foo = 42;");
    testSame("goog.provide('foo');\n" +
        "/** @constructor @public */foo.Bar = function() {};\n" +
        "foo.Bar.prototype = {\n" +
        "  baz: function(){}\n" +
        "};");
  }

  public void testMergeRequirements() {
    Compiler compiler = createCompiler();
    ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();
    builder.addRequirementBuilder().setRuleId("a").addWhitelist("x").addWhitelistRegexp("m");
    builder.addRequirementBuilder().setExtends("a").addWhitelist("y").addWhitelistRegexp("n");
    List<Requirement> requirements =
        CheckConformance.mergeRequirements(compiler, ImmutableList.of(builder.build()));
    assertThat(requirements).hasSize(1);
    Requirement requirement = requirements.get(0);
    assertEquals(2, requirement.getWhitelistCount());
    assertEquals(2, requirement.getWhitelistRegexpCount());
  }

  public void testMergeRequirements_findsDuplicates() {
    Compiler compiler = createCompiler();
    ErrorManager errorManager = new BlackHoleErrorManager();
    compiler.setErrorManager(errorManager);
    ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();
    builder.addRequirementBuilder().addWhitelist("x").addWhitelist("x");
    CheckConformance.mergeRequirements(compiler, ImmutableList.of(builder.build()));
    assertEquals(1, errorManager.getErrorCount());
  }
}
