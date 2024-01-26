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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CheckConformance.InvalidRequirementSpec;
import com.google.javascript.jscomp.ConformanceRules.AbstractRule;
import com.google.javascript.jscomp.ConformanceRules.ConformanceResult;
import com.google.javascript.jscomp.Requirement.WhitelistEntry;
import com.google.javascript.rhino.Node;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CheckConformance}. */
@RunWith(JUnit4.class)
public final class CheckConformanceTest extends CompilerTestCase {
  private String configuration;

  private static final String EXTERNS =
      lines(
          DEFAULT_EXTERNS,
          "/** @constructor */ function Window() {};",
          "/** @type {Window} */ var window;",
          "/** @type {Function} */ Arguments.prototype.callee;",
          "/** @type {Function} */ Arguments.prototype.caller;",
          "/** @type {Arguments} */ var arguments;",
          "/** @constructor ",
          " * @param {*=} opt_message",
          " * @param {*=} opt_file",
          " * @param {*=} opt_line",
          " * @return {!Error}",
          "*/",
          "function Error(opt_message, opt_file, opt_line) {};",
          "function alert(y) {};",
          "/** @constructor */ function ObjectWithNoProps() {};",
          "function eval() {}");

  private static final String DEFAULT_CONFORMANCE =
      lines(
          "requirement: {",
          "  type: BANNED_NAME",
          "  value: 'eval'",
          "   error_message: 'eval is not allowed'",
          "}",
          "",
          "requirement: {",
          "  type: BANNED_PROPERTY",
          "  value: 'Arguments.prototype.callee'",
          "  error_message: 'Arguments.prototype.callee is not allowed'",
          "}");

  public CheckConformanceTest() {
    super(EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    enableClosurePass();
    enableClosurePassForExpected();
    enableRewriteClosureCode();
    configuration = DEFAULT_CONFORMANCE;
    ignoreWarnings(DiagnosticGroups.MISSING_PROPERTIES);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();
    try {
      TextFormat.merge(configuration, builder);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
    return new CheckConformance(compiler, ImmutableList.of(builder.build()));
  }

  @Override
  protected int getNumRepetitions() {
    // This compiler pass is not idempotent and should only be run over a
    // parse tree once.
    return 1;
  }

  @Test
  public void testViolation1() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "}";

    testWarning("eval()", CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(
        "Function.prototype.name; eval.name.length", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testViolation2() {
    testWarning("function f() { arguments.callee }", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testConfigFile() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  config_file: 'foo_conformance_proto.txt'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "}";

    testWarning("eval()", CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(
        "Function.prototype.name; eval.name.length",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: eval is not allowed\n  defined in foo_conformance_proto.txt");
  }

  @Test
  public void testNotViolation1() {
    testNoWarning(
        "/** @constructor */ function Foo() { this.callee = 'string'; }\n"
            + "/** @constructor */ function Bar() { this.callee = 1; }\n"
            + "\n"
            + "\n"
            + "function f() {\n"
            + "  var x;\n"
            + "  switch(random()) {\n"
            + "    case 1:\n"
            + "      x = new Foo();\n"
            + "      break;\n"
            + "    case 2:\n"
            + "      x = new Bar();\n"
            + "      break;\n"
            + "    default:\n"
            + "      return;\n"
            + "  }\n"
            + "  var z = x.callee;\n"
            + "}");
  }

  @Test
  public void testNotViolation2() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'location'\n"
            + "  error_message: 'location is not allowed'\n"
            + "}";
    testNoWarning("function f() { var location = null; }");
  }

  @Test
  public void testMaybeViolation1() {
    testWarning("function f() { y.callee }", CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testWarning(
        "function f() { new Foo().callee }", CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testWarning(
        "function f() { new Object().callee }", CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testWarning(
        "function f() { /** @type {*} */ var x; x.callee }",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testNoWarning("function f() {/** @const */ var x = {}; x.callee = 1; x.callee}");
  }

  @Test
  public void testBadWhitelist1() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'placeholder'\n"
            + "  whitelist_regexp: '('\n"
            + "}";

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: invalid regex pattern\n"
            + "Requirement spec:\n"
            + "error_message: \"placeholder\"\n"
            + "whitelist_regexp: \"(\"\n"
            + "type: BANNED_NAME\n"
            + "value: \"eval\"\n");
  }

  @Test
  public void testBadAllowlist1() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'placeholder'\n"
            + "  allowlist_regexp: '('\n"
            + "}";

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: invalid regex pattern\n"
            + "Requirement spec:\n"
            + "error_message: \"placeholder\"\n"
            + "type: BANNED_NAME\n"
            + "value: \"eval\"\n"
            + "allowlist_regexp: \"(\"\n");
  }

  @Test
  public void testViolationWhitelisted1() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  whitelist: 'testcode'\n "
            + "}";

    testNoWarning("eval()");
  }

  @Test
  public void testViolationAllowlisted1() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  allowlist: 'testcode'\n "
            + "}";

    testNoWarning("eval()");
  }

  @Test
  public void testViolationWhitelistedAndAllowlistedDuplicated1() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  whitelist: 'testcode'\n "
            + "  allowlist: 'testcode'\n "
            + "}";

    testNoWarning("eval()");
  }

  @Test
  public void testViolationWhitelistedByWhitelistEntryPrefix() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  whitelist_entry {\n"
            + "    prefix: 'testcode'\n"
            + "  }\n"
            + "}";

    testNoWarning("eval()");
  }

  @Test
  public void testViolationAllowlistedByAllowlistEntryPrefix() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  allowlist_entry {\n"
            + "    prefix: 'testcode'\n"
            + "  }\n"
            + "}";

    testNoWarning("eval()");
  }

  @Test
  public void testViolationWhitelistedByWhitelistEntryRegexp() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  whitelist_entry {\n"
            + "    regexp: 'tes..ode'\n"
            + "  }\n"
            + "}";

    testNoWarning("eval()");
  }

  @Test
  public void testViolationAllowlistedByAllowlistEntryRegexp() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  allowlist_entry {\n"
            + "    regexp: 'tes..ode'\n"
            + "  }\n"
            + "}";

    testNoWarning("eval()");
  }

  @Test
  public void testViolationWhitelisted2() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  whitelist_regexp: 'code$'\n "
            + "}";

    testNoWarning("eval()");
  }

  @Test
  public void testViolationAllowlisted2() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  allowlist_regexp: 'code$'\n "
            + "}";

    testNoWarning("eval()");
  }

  @Test
  public void testViolationAllowlisted2Ts() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  allowlist_regexp: 'file.ts$'\n "
            + "}";

    testNoWarning(srcs(SourceFile.fromCode("file.closure.js", "eval()")));
  }

  @Test
  public void testViolationWhitelistedIgnoresRegex() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  whitelist: 'file.js'\n "
            + "}";

    testNoWarning(srcs(SourceFile.fromCode("test/google3/file.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/bin/file.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("google3/blaze-out/k8-opt/bin/file.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("bazel-out/k8-opt/bin/file.js", "eval()")));
  }

  @Test
  public void testViolationAllowlistedIgnoresRegex() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  allowlist: 'file.js'\n "
            + "}";

    testNoWarning(srcs(SourceFile.fromCode("test/google3/file.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/bin/file.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("google3/blaze-out/k8-opt/bin/file.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("bazel-out/k8-opt/bin/file.js", "eval()")));
  }

  @Test
  public void testViolationAllowlistedIgnoresRegexTs() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  allowlist: 'file.ts'\n "
            + "}";

    testNoWarning(srcs(SourceFile.fromCode("file.closure.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("test/google3/file.closure.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/bin/file.closure.js", "eval()")));
    testNoWarning(
        srcs(SourceFile.fromCode("google3/blaze-out/k8-opt/bin/file.closure.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("bazel-out/k8-opt/bin/file.closure.js", "eval()")));
  }

  @Test
  public void testViolationWhitelistedAndAllowlistedIgnoresRegex() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  whitelist: 'file1.js'\n "
            + "  allowlist: 'file2.js'\n "
            + "}";

    testNoWarning(srcs(SourceFile.fromCode("test/google3/file1.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("test/google3/file2.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/bin/file1.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/bin/file2.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("google3/blaze-out/k8-opt/bin/file1.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("google3/blaze-out/k8-opt/bin/file2.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("bazel-out/k8-opt/bin/file1.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("bazel-out/k8-opt/bin/file2.js", "eval()")));
  }

  @Test
  public void testViolationWhitelistedIgnoresRegex_absolutePath() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  whitelist: '/file.js'\n "
            + "}";

    testNoWarning(srcs(SourceFile.fromCode("/file.js", "eval()")));
  }

  @Test
  public void testViolationAllowlistedIgnoresRegex_absolutePath() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  allowlist: '/file.js'\n "
            + "}";

    testNoWarning(srcs(SourceFile.fromCode("/file.js", "eval()")));
  }

  @Test
  public void testViolationWhitelistedAndAllowlistedIgnoresRegex_absolutePath() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  whitelist: '/file1.js'\n "
            + "  allowlist: '/file2.js'\n "
            + "}";

    testNoWarning(srcs(SourceFile.fromCode("/file1.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("/file2.js", "eval()")));
  }

  @Test
  public void testViolationWhitelistedIgnoresRegex_genfiles() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  whitelist: 'genfiles/file.js'\n "
            + "}";

    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/genfiles/file.js", "eval()")));
  }

  @Test
  public void testViolationAllowlistedIgnoresRegex_genfiles() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  allowlist: 'genfiles/file.js'\n "
            + "}";

    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/genfiles/file.js", "eval()")));
  }

  @Test
  public void testViolationWhitelistedAndAllowlistedIgnoresRegex_genfiles() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  whitelist: 'genfiles/file1.js'\n "
            + "  allowlist: 'genfiles/file2.js'\n "
            + "}";

    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/genfiles/file1.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/genfiles/file2.js", "eval()")));
  }

  @Test
  public void testFileOnOnlyApplyToIsChecked() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  only_apply_to: 'foo.js'\n "
            + "}";
    ImmutableList<SourceFile> inputs = ImmutableList.of(SourceFile.fromCode("foo.js", "eval()"));
    testWarning(
        srcs(inputs),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage("Violation: eval is not allowed"));
  }

  @Test
  public void testFileOnOnlyApplyToIsCheckedTs() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  only_apply_to: 'foo.ts'\n "
            + "}";
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(SourceFile.fromCode("foo.closure.js", "eval()"));
    testWarning(
        srcs(inputs),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage("Violation: eval is not allowed"));
  }

  @Test
  public void testFileNotOnOnlyApplyToIsNotChecked() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  only_apply_to: 'foo.js'\n "
            + "}";
    testNoWarning(srcs(SourceFile.fromCode("bar.js", "eval()")));
  }

  @Test
  public void testFileOnOnlyApplyToRegexpIsChecked() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  only_apply_to_regexp: 'test.js$'\n "
            + "}";
    ImmutableList<SourceFile> input =
        ImmutableList.of(SourceFile.fromCode("foo_test.js", "eval()"));
    testWarning(
        srcs(input),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage("Violation: eval is not allowed"));
  }

  @Test
  public void testFileOnOnlyApplyToRegexpIsCheckedTs() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  only_apply_to_regexp: 'test.ts$'\n "
            + "}";
    ImmutableList<SourceFile> input =
        ImmutableList.of(SourceFile.fromCode("foo_test.closure.js", "eval()"));
    testWarning(
        srcs(input),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage("Violation: eval is not allowed"));
  }

  @Test
  public void testFileNotOnOnlyApplyToRegexpIsNotChecked() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'eval'\n"
            + "  error_message: 'eval is not allowed'\n"
            + "  only_apply_to_regexp: 'test.js$'\n "
            + "}";
    testNoWarning(srcs(SourceFile.fromCode("bar.js", "eval()")));
  }

  @Test
  public void testNoOp() {
    configuration =
        "requirement: { type: NO_OP, value: 'no matter', value: 'can be anything', error_message:"
            + " 'Never happens' }";
    testNoWarning("eval()");
  }

  @Test
  public void testBannedEnhance() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_ENHANCE\n"
            + "  value: '{some.banned.namespace}'\n"
            + "  value: '{another.banned.namespace}'\n"
            + "  error_message: 'Enhanced namespace is not allowed.'\n"
            + "}";
    String violationMessage =
        "Violation: Enhanced namespace is not allowed.\n" + "The enhanced namespace ";

    String ban1 = lines("/**", " * @enhance {some.banned.namespace}", " */");
    testWarning(
        ban1,
        CheckConformance.CONFORMANCE_VIOLATION,
        violationMessage + "\"{some.banned.namespace}\"");

    String ban2 = lines("/**", " * @enhance {another.banned.namespace}", " */");
    testWarning(
        ban2,
        CheckConformance.CONFORMANCE_VIOLATION,
        violationMessage + "\"{another.banned.namespace}\"");

    String allow1 = lines("/**", " * @enhance {some.allowed.namespace}", " */");
    testNoWarning(allow1);

    String allow2 =
        lines("/**", " * @fileoverview no enhance annotation should always pass.", " */");
    testNoWarning(allow2);
  }

  @Test
  public void testBannedModsRegex() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_MODS_REGEX\n"
            + "  value: '.+_bar$'\n"
            + "  error_message: 'Modding namespaces ending in _bar is NOT allowed.'\n"
            + "}";
    String violationMessage = "Violation: Modding namespaces ending in _bar is NOT allowed.";

    String ban = lines("/**", " * @mods {ns.foo_bar}", " * @modName {ns.foo_bar_baz}", " */");
    testWarning(ban, CheckConformance.CONFORMANCE_VIOLATION, violationMessage);

    String allow1 = lines("/**", " * @mods {ns.foo}", " * @modName {ns.foo_bar}", " */");
    testNoWarning(allow1);

    String allow2 =
        lines("/**", " * @fileoverview no enhance annotation should always pass.", " */");
    testNoWarning(allow2);
  }

  @Test
  public void testBannedNameCall() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME_CALL\n"
            + "  value: 'Function'\n"
            + "  error_message: 'Calling Function is not allowed.'\n"
            + "}";

    testNoWarning("f instanceof Function");
    testWarning("new Function(str);", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedName_googProvided() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_NAME\n"
            + "  value: 'foo.bar'\n"
            + "  error_message: 'foo.bar is not allowed'\n"
            + "  allowlist: 'SRC1'\n"
            + "}";

    testWarning(
        srcs(
            SourceFile.fromCode("SRC1", "goog.provide('foo.bar');"),
            SourceFile.fromCode("SRC2", "alert(foo.bar);")),
        CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(
        srcs(
            SourceFile.fromCode("SRC1", "goog.provide('foo.bar'); foo.bar = {};"),
            SourceFile.fromCode("SRC2", "alert(foo.bar);")),
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testInferredConstCheck() {
    configuration =
        lines(
            "requirement: {",
            "  type: CUSTOM",
            "  java_class: 'com.google.javascript.jscomp.ConformanceRules$InferredConstCheck'",
            "  error_message: 'Failed to infer type of constant'",
            "}");

    testNoWarning("/** @const */ var x = 0;");
    testNoWarning("/** @const */ var x = unknown;");
    testNoWarning("const x = unknown;");

    testWarning(
        lines(
            "/** @constructor */",
            "function f() {",
            "  /** @const */ this.foo = unknown;",
            "}",
            "var x = new f();"),
        CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(
        lines(
            "/** @constructor */",
            "function f() {}",
            "/** @this {f} */",
            "var init_f = function() {",
            "  /** @const */ this.foo = unknown;",
            "};",
            "var x = new f();"),
        CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(
        lines(
            "/** @constructor */",
            "function f() {}",
            "var init_f = function() {",
            "  /** @const */ this.FOO = unknown;",
            "};",
            "var x = new f();"),
        CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(
        lines(
            "/** @constructor */",
            "function f() {}",
            "f.prototype.init_f = function() {",
            "  /** @const */ this.FOO = unknown;",
            "};",
            "var x = new f();"),
        CheckConformance.CONFORMANCE_VIOLATION);

    testNoWarning(
        lines(
            "/** @constructor */",
            "function f() {}",
            "f.prototype.init_f = function() {",
            "  /** @const {?} */ this.FOO = unknown;",
            "};",
            "var x = new f();"));

    testNoWarning(
        lines("/** @const */", "var ns = {};", "/** @const */", "ns.subns = ns.subns || {};"));

    // We only check @const nodes, not @final nodes.
    testNoWarning(
        lines(
            "/** @constructor */",
            "function f() {",
            "  /** @final */ this.foo = unknown;",
            "}",
            "var x = new f();"));
  }

  @Test
  public void testBannedCodePattern1() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_CODE_PATTERN\n"
            + "  value: '/** @param {string|String} a */"
            + "function template(a) {a.blink}'\n"
            + "  error_message: 'blink is annoying'\n"
            + "}";

    String externs = EXTERNS + "String.prototype.blink;";

    testNoWarning(
        "/** @constructor */ function Foo() { this.blink = 1; }\n"
            + "var foo = new Foo();\n"
            + "foo.blink();");

    testWarning(
        externs(externs),
        srcs("'foo'.blink;"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: blink is annoying");

    testWarning(
        externs(externs),
        srcs("'foo'.blink();"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: blink is annoying");

    testWarning(
        externs(externs),
        srcs("String('foo').blink();"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: blink is annoying");

    testWarning(
        externs(externs),
        srcs("foo.blink();"),
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION,
        "Possible violation: blink is annoying\n"
            + "The type information available for this expression is too loose to ensure"
            + " conformance.");
  }

  @Test
  public void testBannedDep1() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_DEPENDENCY\n"
            + "  value: 'testcode'\n"
            + "  error_message: 'testcode is not allowed'\n"
            + "}";

    testWarning(
        "anything;", CheckConformance.CONFORMANCE_VIOLATION, "Violation: testcode is not allowed");
  }

  @Test
  public void testBannedDepRegexNoValue() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n"
            + "  type: BANNED_DEPENDENCY_REGEX\n"
            + "  error_message: 'testcode is not allowed'\n"
            + "}";

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: missing value (no banned dependency regexps)\n"
            + "Requirement spec:\n"
            + "error_message: \"testcode is not allowed\"\n"
            + "type: BANNED_DEPENDENCY_REGEX\n");
  }

  @Test
  public void testBannedDepRegex() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_DEPENDENCY_REGEX\n"
            + "  error_message: 'testcode is not allowed'\n"
            + "  value: '.*test.*'\n"
            + "}";

    testWarning(
        "anything;", CheckConformance.CONFORMANCE_VIOLATION, "Violation: testcode is not allowed");
  }

  @Test
  public void testBannedDepRegexWithWhitelist() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_DEPENDENCY_REGEX\n"
            + "  error_message: 'testcode is not allowed'\n"
            + "  value: '.*test.*'\n"
            + "  whitelist_regexp: 'testcode'\n"
            + "}";

    testNoWarning("anything;");
  }

  @Test
  public void testBannedDepRegexWithAllowlist() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_DEPENDENCY_REGEX\n"
            + "  error_message: 'testcode is not allowed'\n"
            + "  value: '.*test.*'\n"
            + "  allowlist_regexp: 'testcode'\n"
            + "}";

    testNoWarning("anything;");
  }

  @Test
  public void testReportLooseTypeViolations() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY_WRITE",
            "  value: 'HTMLScriptElement.prototype.textContent'",
            "  error_message: 'Setting content of <script> is dangerous.'",
            "  report_loose_type_violations: false",
            "}");

    String externs =
        lines(
            DEFAULT_EXTERNS,
            "/** @constructor */ function Element() {}",
            "/** @type {string} @implicitCast */",
            "Element.prototype.textContent;",
            "/** @constructor @extends {Element} */ function HTMLScriptElement() {}\n");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement).textContent = 'alert(1);'"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: Setting content of <script> is dangerous.");

    testWarning(
        externs(externs),
        srcs("HTMLScriptElement.prototype.textContent = 'alert(1);'"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: Setting content of <script> is dangerous.");

    testNoWarning(externs(externs), srcs("(new Element).textContent = 'safe'"));
  }

  @Test
  public void testDontCrashOnNonConstructorWithPrototype() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY_WRITE",
            "  value: 'Bar.prototype.method'",
            "  error_message: 'asdf'",
            "  report_loose_type_violations: false",
            "}");

    testNoWarning(
        externs(
            DEFAULT_EXTERNS
                + lines(
                    "/** @constructor */",
                    "function Bar() {}",
                    "Bar.prototype.method = function() {};")),
        srcs(lines("function Foo() {}", "Foo.prototype.method = function() {};")));
  }

  private void testConformance(String src1, String src2) {
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(SourceFile.fromCode("SRC1", src1), SourceFile.fromCode("SRC2", src2));
    testNoWarning(srcs(inputs));
  }

  private void testConformance(String src1, String src2, DiagnosticType warning) {
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(SourceFile.fromCode("SRC1", src1), SourceFile.fromCode("SRC2", src2));
    testWarning(srcs(inputs), warning(warning));
  }

  @Test
  public void testBannedPropertyWhitelist0() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'C.prototype.p'",
            "  error_message: 'C.p is not allowed'",
            "  whitelist: 'SRC1'",
            "}");

    String cDecl =
        lines("/** @constructor */", "function C() {}", "/** @type {string} */", "C.prototype.p;");

    String dDecl =
        lines("/** @constructor */ function D() {}", "/** @type {string} */", "D.prototype.p;");

    testConformance(cDecl, dDecl);
  }

  @Test
  public void testBannedPropertyAllowlist0() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'C.prototype.p'",
            "  error_message: 'C.p is not allowed'",
            "  allowlist: 'SRC1'",
            "}");

    String cDecl =
        lines("/** @constructor */", "function C() {}", "/** @type {string} */", "C.prototype.p;");

    String dDecl =
        lines("/** @constructor */ function D() {}", "/** @type {string} */", "D.prototype.p;");

    testConformance(cDecl, dDecl);
  }

  @Test
  public void testBannedPropertyWhitelist1() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'C.prototype.p'",
            "  error_message: 'C.p is not allowed'",
            "  whitelist: 'SRC1'",
            "}");

    String cDecl = lines("/** @constructor */", "function C() {", "  this.p = 'str';", "}");

    String dDecl = lines("/** @constructor */", "function D() {", "  this.p = 'str';", "}");

    testConformance(cDecl, dDecl);
  }

  @Test
  public void testBannedPropertyAllowlist1() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'C.prototype.p'",
            "  error_message: 'C.p is not allowed'",
            "  allowlist: 'SRC1'",
            "}");

    String cDecl = lines("/** @constructor */", "function C() {", "  this.p = 'str';", "}");

    String dDecl = lines("/** @constructor */", "function D() {", "  this.p = 'str';", "}");

    testConformance(cDecl, dDecl);
  }

  @Test
  public void testBannedPropertyWhitelist2() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'C.prototype.p'",
            "  error_message: 'C.p is not allowed'",
            "  whitelist: 'SRC1'",
            "}");

    String declarations =
        lines(
            "/** @constructor */ function SC() {}",
            "/** @constructor @extends {SC} */",
            "function C() {}",
            "/** @type {string} */",
            "C.prototype.p;",
            "/** @constructor */ function D() {}",
            "/** @type {string} */",
            "D.prototype.p;");

    testConformance(declarations, "var d = new D(); d.p = 'boo';");

    testConformance(
        declarations, "var c = new C(); c.p = 'boo';", CheckConformance.CONFORMANCE_VIOLATION);

    // Accessing property through a super type is possibly a violation.
    testConformance(
        declarations,
        "var sc = new SC(); sc.p = 'boo';",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testConformance(
        declarations, "var c = new C(); var foo = c.p;", CheckConformance.CONFORMANCE_VIOLATION);

    testConformance(
        declarations,
        "var c = new C(); var foo = 'x' + c.p;",
        CheckConformance.CONFORMANCE_VIOLATION);

    testConformance(
        declarations, "var c = new C(); c['p'] = 'boo';", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedPropertyAllowlist2() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'C.prototype.p'",
            "  error_message: 'C.p is not allowed'",
            "  allowlist: 'SRC1'",
            "}");

    String declarations =
        lines(
            "/** @constructor */ function SC() {}",
            "/** @constructor @extends {SC} */",
            "function C() {}",
            "/** @type {string} */",
            "C.prototype.p;",
            "/** @constructor */ function D() {}",
            "/** @type {string} */",
            "D.prototype.p;");

    testConformance(declarations, "var d = new D(); d.p = 'boo';");

    testConformance(
        declarations, "var c = new C(); c.p = 'boo';", CheckConformance.CONFORMANCE_VIOLATION);

    // Accessing property through a super type is possibly a violation.
    testConformance(
        declarations,
        "var sc = new SC(); sc.p = 'boo';",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testConformance(
        declarations, "var c = new C(); var foo = c.p;", CheckConformance.CONFORMANCE_VIOLATION);

    testConformance(
        declarations,
        "var c = new C(); var foo = 'x' + c.p;",
        CheckConformance.CONFORMANCE_VIOLATION);

    testConformance(
        declarations, "var c = new C(); c['p'] = 'boo';", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedPropertyWhitelist3() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'C.prototype.p'",
            "  error_message: 'C.p is not allowed'",
            "  whitelist: 'SRC1'",
            "}");

    String cdecl =
        lines(
            "/** @constructor */ function SC() {}",
            "/** @constructor @extends {SC} */",
            "function C() {}",
            "/** @type {string} */",
            "C.prototype.p;");
    String ddecl =
        lines(
            "/** @constructor @template T */ function D() {}",
            "/** @param {T} a */",
            "D.prototype.method = function(a) {",
            "  use(a.p);",
            "};");

    testConformance(cdecl, ddecl, CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyAllowlist3() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'C.prototype.p'",
            "  error_message: 'C.p is not allowed'",
            "  allowlist: 'SRC1'",
            "}");

    String cdecl =
        lines(
            "/** @constructor */ function SC() {}",
            "/** @constructor @extends {SC} */",
            "function C() {}",
            "/** @type {string} */",
            "C.prototype.p;");
    String ddecl =
        lines(
            "/** @constructor @template T */ function D() {}",
            "/** @param {T} a */",
            "D.prototype.method = function(a) {",
            "  use(a.p);",
            "};");

    testConformance(cdecl, ddecl, CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyWhitelist4() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'C.prototype.p'",
            "  error_message: 'C.p is not allowed'",
            "  whitelist: 'SRC1'",
            "}");

    String cdecl =
        lines(
            "/** @constructor */ function SC() {}",
            "/** @constructor @extends {SC} */",
            "function C() {}",
            "/** @type {string} */",
            "C.prototype.p;",
            "",
            "/**",
            " * @param {K} key",
            " * @param {V=} opt_value",
            " * @constructor",
            " * @struct",
            " * @template K, V",
            " * @private",
            " */",
            "var Entry_ = function(key, opt_value) {",
            "  /** @const {K} */",
            "  this.key = key;",
            "  /** @type {V|undefined} */",
            "  this.value = opt_value;",
            "};");

    String ddecl =
        lines(
            "/** @constructor @template T */ function D() {}",
            "/** @param {T} a */",
            "D.prototype.method = function(a) {",
            "  var entry = new Entry('key');",
            "  use(entry.value.p);",
            "};");

    testConformance(cdecl, ddecl, CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyAllowlist4() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'C.prototype.p'",
            "  error_message: 'C.p is not allowed'",
            "  allowlist: 'SRC1'",
            "}");

    String cdecl =
        lines(
            "/** @constructor */ function SC() {}",
            "/** @constructor @extends {SC} */",
            "function C() {}",
            "/** @type {string} */",
            "C.prototype.p;",
            "",
            "/**",
            " * @param {K} key",
            " * @param {V=} opt_value",
            " * @constructor",
            " * @struct",
            " * @template K, V",
            " * @private",
            " */",
            "var Entry_ = function(key, opt_value) {",
            "  /** @const {K} */",
            "  this.key = key;",
            "  /** @type {V|undefined} */",
            "  this.value = opt_value;",
            "};");

    String ddecl =
        lines(
            "/** @constructor @template T */ function D() {}",
            "/** @param {T} a */",
            "D.prototype.method = function(a) {",
            "  var entry = new Entry('key');",
            "  use(entry.value.p);",
            "};");

    testConformance(cdecl, ddecl, CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedProperty5() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'Array.prototype.push'",
            "  error_message: 'banned Array.prototype.push'",
            "}");

    testWarning("[1, 2, 3].push(4);\n", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedPropertyWhitelist_recordType() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'Logger.prototype.config'",
            "  error_message: 'Logger.config is not allowed'",
            "  whitelist: 'SRC1'",
            "}");

    String declaration = "class Logger { config() {} }";

    // Fine, because there is no explicit relationship between Logger & GoodRecord.
    testConformance(
        declaration,
        lines(
            "/** @record */",
            "class GoodRecord {",
            "  constructor() {",
            "    /** @type {Function} */ this.config;",
            "  }",
            "}"));

    // Bad, because there is a direct relationship.
    testConformance(
        "/** @implements {BadRecord} */ " + declaration,
        lines(
            "/** @record */",
            "class BadRecord {",
            "  constructor() {",
            "    /** @type {Function} */ this.config;",
            "  }",
            "}"),
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyAllowlist_recordType() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'Logger.prototype.config'",
            "  error_message: 'Logger.config is not allowed'",
            "  allowlist: 'SRC1'",
            "}");

    String declaration = "class Logger { config() {} }";

    // Fine, because there is no explicit relationship between Logger & GoodRecord.
    testConformance(
        declaration,
        lines(
            "/** @record */",
            "class GoodRecord {",
            "  constructor() {",
            "    /** @type {Function} */ this.config;",
            "  }",
            "}"));

    // Bad, because there is a direct relationship.
    testConformance(
        "/** @implements {BadRecord} */ " + declaration,
        lines(
            "/** @record */",
            "class BadRecord {",
            "  constructor() {",
            "    /** @type {Function} */ this.config;",
            "  }",
            "}"),
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyWhitelist_namespacedType() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'ns.C.prototype.p'",
            "  error_message: 'ns.C.p is not allowed'",
            "  whitelist: 'SRC1'",
            "}");

    String declarations =
        lines(
            "/** @const */",
            "var ns = {};",
            "/** @constructor */ function SC() {}",
            "/** @constructor @extends {SC} */",
            "ns.C = function() {}",
            "/** @type {string} */",
            "ns.C.prototype.p;",
            "/** @constructor */ function D() {}",
            "/** @type {string} */",
            "D.prototype.p;");

    testConformance(declarations, "var d = new D(); d.p = 'boo';");

    testConformance(
        declarations, "var c = new ns.C(); c.p = 'boo';", CheckConformance.CONFORMANCE_VIOLATION);

    testConformance(
        declarations,
        "var c = new SC(); c.p = 'boo';",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyAllowlist_namespacedType() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'ns.C.prototype.p'",
            "  error_message: 'ns.C.p is not allowed'",
            "  allowlist: 'SRC1'",
            "}");

    String declarations =
        lines(
            "/** @const */",
            "var ns = {};",
            "/** @constructor */ function SC() {}",
            "/** @constructor @extends {SC} */",
            "ns.C = function() {}",
            "/** @type {string} */",
            "ns.C.prototype.p;",
            "/** @constructor */ function D() {}",
            "/** @type {string} */",
            "D.prototype.p;");

    testConformance(declarations, "var d = new D(); d.p = 'boo';");

    testConformance(
        declarations, "var c = new ns.C(); c.p = 'boo';", CheckConformance.CONFORMANCE_VIOLATION);

    testConformance(
        declarations,
        "var c = new SC(); c.p = 'boo';",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyWhitelist_bundledNamespacedType() {
    disableRewriteClosureCode();

    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'test.bns.BC.prototype.p'",
            "  error_message: 'test.bns.BC.p is not allowed'",
            "  whitelist: 'SRC1'",
            "}");

    String declarations =
        lines(
            "/** @fileoverview @typeSummary */",
            "goog.loadModule(function(exports) {",
            "goog.module('test.bns');",
            "/** @constructor */ function SBC() {}",
            "exports.SBC = SBC;",
            "/** @constructor @extends {SBC} */",
            "BC = function() {}",
            "/** @type {string} */",
            "BC.prototype.p;",
            "exports.BC = BC;",
            "/** @constructor */ function D() {}",
            "exports.D = D;",
            "/** @type {string} */",
            "D.prototype.p;",
            "return exports;",
            "});");

    testConformance(
        declarations,
        lines(
            "goog.module('test');",
            "const {D} = goog.require('test.bns');",
            "var d = new D();",
            "d.p = 'boo';"));

    // This case should be a certain violation, but the compiler cannot figure out that the imported
    // type is the same as the one found from the type registry.
    testConformance(
        declarations,
        lines(
            "goog.module('test');",
            "const {BC} = goog.require('test.bns');",
            "var bc = new BC();",
            "bc.p = 'boo';"),
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testConformance(
        declarations,
        lines(
            "goog.module('test');",
            "const bns = goog.require('test.bns');",
            "var bc = new bns.SBC();",
            "bc.p = 'boo';"),
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyAllowlist_bundledNamespacedType() {
    disableRewriteClosureCode();

    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'test.bns.BC.prototype.p'",
            "  error_message: 'test.bns.BC.p is not allowed'",
            "  allowlist: 'SRC1'",
            "}");

    String declarations =
        lines(
            "/** @fileoverview @typeSummary */",
            "goog.loadModule(function(exports) {",
            "goog.module('test.bns');",
            "/** @constructor */ function SBC() {}",
            "exports.SBC = SBC;",
            "/** @constructor @extends {SBC} */",
            "BC = function() {}",
            "/** @type {string} */",
            "BC.prototype.p;",
            "exports.BC = BC;",
            "/** @constructor */ function D() {}",
            "exports.D = D;",
            "/** @type {string} */",
            "D.prototype.p;",
            "return exports;",
            "});");

    testConformance(
        declarations,
        lines(
            "goog.module('test');",
            "const {D} = goog.require('test.bns');",
            "var d = new D();",
            "d.p = 'boo';"));

    // This case should be a certain violation, but the compiler cannot figure out that the imported
    // type is the same as the one found from the type registry.
    testConformance(
        declarations,
        lines(
            "goog.module('test');",
            "const {BC} = goog.require('test.bns');",
            "var bc = new BC();",
            "bc.p = 'boo';"),
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testConformance(
        declarations,
        lines(
            "goog.module('test');",
            "const bns = goog.require('test.bns');",
            "var bc = new bns.SBC();",
            "bc.p = 'boo';"),
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyWhitelist_destructuring() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'C.prototype.p'",
            "  error_message: 'C.p is not allowed'",
            "  whitelist: 'SRC1'",
            "}");

    String declarations =
        lines(
            "/** @constructor */",
            "var C = function() {}",
            "/** @type {string} */",
            "C.prototype.p;",
            "/** @type {number} */",
            "C.prototype.m");

    testConformance(declarations, "var {m} = new C();");

    testConformance(declarations, "var {p} = new C();", CheckConformance.CONFORMANCE_VIOLATION);

    testConformance(
        declarations, "var {['p']: x} = new C();", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedPropertyAllowlist_destructuring() {
    configuration =
        lines(
            "requirement: {",
            "  type: BANNED_PROPERTY",
            "  value: 'C.prototype.p'",
            "  error_message: 'C.p is not allowed'",
            "  allowlist: 'SRC1'",
            "}");

    String declarations =
        lines(
            "/** @constructor */",
            "var C = function() {}",
            "/** @type {string} */",
            "C.prototype.p;",
            "/** @type {number} */",
            "C.prototype.m");

    testConformance(declarations, "var {m} = new C();");

    testConformance(declarations, "var {p} = new C();", CheckConformance.CONFORMANCE_VIOLATION);

    testConformance(
        declarations, "var {['p']: x} = new C();", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedPropertyWrite() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_PROPERTY_WRITE\n"
            + "  value: 'C.prototype.p'\n"
            + "  error_message: 'Assignment to C.p is not allowed'\n"
            + "}";

    String declarations =
        "/** @constructor */ function C() {}\n"
            + "/** @type {string} */\n"
            + "C.prototype.p;\n"
            + "/** @constructor */ function D() {}\n"
            + "/** @type {string} */\n"
            + "D.prototype.p;\n";

    testNoWarning(declarations + "var d = new D(); d.p = 'boo';");

    testWarning(
        declarations + "var c = new C(); c.p = 'boo';", CheckConformance.CONFORMANCE_VIOLATION);

    testNoWarning(declarations + "var c = new C(); var foo = c.p;");

    testNoWarning(declarations + "var c = new C(); var foo = 'x' + c.p;");

    testWarning(
        declarations + "var c = new C(); c['p'] = 'boo';", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedPropertyWriteExtern() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_PROPERTY_WRITE\n"
            + "  value: 'Element.prototype.innerHTML'\n"
            + "  error_message: 'Assignment to Element.innerHTML is not allowed'\n"
            + "}";

    String externs =
        DEFAULT_EXTERNS
            + "/** @constructor */ function Element() {}\n"
            + "/** @type {string} @implicitCast */\n"
            + "Element.prototype.innerHTML;\n";

    testWarning(
        externs(externs),
        srcs("var e = new Element(); e.innerHTML = '<boo>';"),
        warning(CheckConformance.CONFORMANCE_VIOLATION));

    testWarning(
        externs(externs),
        srcs("var e = new Element(); e.innerHTML = 'foo';"),
        warning(CheckConformance.CONFORMANCE_VIOLATION));

    testWarning(
        externs(externs),
        srcs("var e = new Element(); e['innerHTML'] = 'foo';"),
        warning(CheckConformance.CONFORMANCE_VIOLATION));
  }

  @Test
  public void testBannedPropertyNonConstantWrite() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_PROPERTY_NON_CONSTANT_WRITE\n"
            + "  value: 'C.prototype.p'\n"
            + "  error_message: 'Assignment of a non-constant value to C.p is not allowed'\n"
            + "}";

    String declarations =
        "/** @constructor */ function C() {}\n" + "/** @type {string} */\n" + "C.prototype.p;\n";

    testNoWarning(declarations + "var c = new C(); c.p = 'boo';");
    testNoWarning(declarations + "var c = new C(); c.p = 'foo' + 'bar';");

    testWarning(
        declarations + "var boo = 'boo'; var c = new C(); c.p = boo;",
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedPropertyRead() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_PROPERTY_READ\n"
            + "  value: 'C.prototype.p'\n"
            + "  error_message: 'Use of C.p is not allowed'\n"
            + "}";

    String declarations =
        "/** @constructor */ function C() {}\n"
            + "/** @type {string} */\n"
            + "C.prototype.p;\n"
            + "/** @constructor */ function D() {}\n"
            + "/** @type {string} */\n"
            + "D.prototype.p;\n"
            + "function use(a) {};";

    testNoWarning(declarations + "var d = new D(); d.p = 'boo';");

    testNoWarning(declarations + "var c = new C(); c.p = 'boo';");

    testWarning(
        declarations + "var c = new C(); use(c.p);", CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(
        declarations + "var c = new C(); var foo = c.p;", CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(
        declarations + "var c = new C(); var foo = 'x' + c.p;",
        CheckConformance.CONFORMANCE_VIOLATION);

    testNoWarning(declarations + "var c = new C(); c['p'] = 'boo';");

    testWarning(
        declarations + "var c = new C(); use(c['p']);", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedStringRegexMissingValues() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n"
            + "  type: BANNED_STRING_REGEX\n"
            + "  error_message: 'Empty string not allowed'\n"
            + "}";

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: missing value\n"
            + "Requirement spec:\n"
            + "error_message: \"Empty string not allowed\"\n"
            + "type: BANNED_STRING_REGEX\n");
  }

  @Test
  public void testBannedStringRegexEmpty() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n"
            + "  type: BANNED_STRING_REGEX\n"
            + "  value: ' '\n"
            + "  error_message: 'Empty string not allowed'\n"
            + "}";

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: empty strings or whitespace are not allowed\n"
            + "Requirement spec:\n"
            + "error_message: \"Empty string not allowed\"\n"
            + "type: BANNED_STRING_REGEX\n"
            + "value: \" \"\n");
  }

  @Test
  public void testBannedStringRegexMultipleValuesWithEmpty() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n"
            + "  type: BANNED_STRING_REGEX\n"
            + "  value: 'things'\n"
            + "  value: ' '\n"
            + "  error_message: 'Empty string not allowed'\n"
            + "}";

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: empty strings or whitespace are not allowed\n"
            + "Requirement spec:\n"
            + "error_message: \"Empty string not allowed\"\n"
            + "type: BANNED_STRING_REGEX\n"
            + "value: \"things\"\n"
            + "value: \" \"\n");
  }

  @Test
  public void testBannedStringRegex1() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_STRING_REGEX\n"
            + "  value: '.*some-attr.*'\n"
            + "  error_message: 'Empty string not allowed'\n"
            + "}";

    String declarations = "let dom = '<div>sample dom template content</div>';";

    testNoWarning(declarations);

    testWarning(
        declarations + "let moredom = '<p some-attr=\"testval\">reflected!</p>';",
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedStringRegex2() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_STRING_REGEX\n"
            + "  value: '.*things.*'\n"
            + "  value: 'stuff.*'\n"
            + "  error_message: 'Empty string not allowed'\n"
            + "}";

    String code =
        "/** @constructor */\n"
            + "function Base() {}; Base.prototype.m;\n"
            + "/** @constructor @extends {Base} */\n"
            + "function Sub() {}\n";
    String stuff = "var b = 'stuff and what not';\n";
    String things = "var s = 'special things';\n";

    testNoWarning(code);

    testWarning(code + stuff, CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(code + things, CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedStringRegexExactMatch() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_STRING_REGEX\n"
            + "  value: 'stuff'\n"
            + "  error_message: 'Empty string not allowed'\n"
            + "}";

    String code =
        "/** @constructor */\n"
            + "function Base() {}; Base.prototype.m;\n"
            + "/** @constructor @extends {Base} */\n"
            + "function Sub() {}\n";
    String noMatch = "var b = ' stuff ';\n";
    String shouldMatch = "var s = 'stuff';\n";

    testNoWarning(code + noMatch);

    testWarning(code + shouldMatch, CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedStringTemplateLiteral1() {
    configuration =
        "requirement: {\n"
            + "  type: BANNED_STRING_REGEX\n"
            + "  value: '.*good'\n"
            + "  error_message: 'Empty string not allowed'\n"
            + "}";

    String code =
        "/** @constructor */\n"
            + "function Base() {}; Base.prototype.m;\n"
            + "/** @constructor @extends {Base} */\n"
            + "function Sub() {}\n";
    String noMatch = "var b = `cheesy goodness`;\n";
    String shouldMatch = "var b = `cheesy good`;\n";

    testNoWarning(code + noMatch);

    testWarning(code + shouldMatch, CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testRestrictedCall1() {
    configuration =
        "requirement: {\n"
            + "  type: RESTRICTED_METHOD_CALL\n"
            + "  value: 'C.prototype.m:function(number)'\n"
            + "  error_message: 'm method param must be number'\n"
            + "}";

    String code =
        "/** @constructor */ function C() {}\n"
            + "/** @param {*} a */\n"
            + "C.prototype.m = function(a){}\n";

    testNoWarning(code + "new C().m(1);");

    testWarning(code + "new C().m('str');", CheckConformance.CONFORMANCE_VIOLATION);

    testNoWarning(code + "new C().m.call(new C(), 1);");

    testWarning(code + "new C().m.call(new C(), 'str');", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testRestrictedCall2() {
    configuration =
        "requirement: {\n"
            + "  type: RESTRICTED_NAME_CALL\n"
            + "  value: 'C.m:function(number)'\n"
            + "  error_message: 'C.m method param must be number'\n"
            + "}";

    String code =
        "/** @constructor */ function C() {}\n" + "/** @param {*} a */\n" + "C.m = function(a){}\n";

    testNoWarning(code + "C.m(1);");

    testWarning(code + "C.m('str');", CheckConformance.CONFORMANCE_VIOLATION);

    testNoWarning(code + "C.m.call(C, 1);");

    testWarning(code + "C.m.call(C, 'str');", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testRestrictedCall3() {
    configuration =
        "requirement: {\n"
            + "  type: RESTRICTED_NAME_CALL\n"
            + "  value: 'C:function(number)'\n"
            + "  error_message: 'C method must be number'\n"
            + "}";

    String code = "/** @constructor @param {...*} a */ function C(a) {}\n";

    testNoWarning(code + "new C(1);");

    testWarning(code + "new C('str');", CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(code + "new C(1, 1);", CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(code + "new C();", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testRestrictedCall4() {
    configuration =
        "requirement: {\n"
            + "  type: RESTRICTED_NAME_CALL\n"
            + "  value: 'C:function(number)'\n"
            + "  error_message: 'C method must be number'\n"
            + "}";

    String code = "/** @constructor @param {...*} a */ function C(a) {}\n";

    testNoWarning(externs(EXTERNS + "goog.inherits;"), srcs(code + "goog.inherits(A, C);"));
  }

  @Test
  public void testRestrictedMethodCallThisType() {
    configuration =
        ""
            + "requirement: {\n"
            + "  type: RESTRICTED_METHOD_CALL\n"
            + "  value: 'Base.prototype.m:function(this:Sub,number)'\n"
            + "  error_message: 'Only call m on the subclass'\n"
            + "}";

    String code =
        "/** @constructor */\n"
            + "function Base() {}; Base.prototype.m;\n"
            + "/** @constructor @extends {Base} */\n"
            + "function Sub() {}\n"
            + "var b = new Base();\n"
            + "var s = new Sub();\n"
            + "var maybeB = cond ? new Base() : null;\n"
            + "var maybeS = cond ? new Sub() : null;\n";

    testWarning(code + "b.m(1)", CheckConformance.CONFORMANCE_VIOLATION);
    testWarning(code + "maybeB.m(1)", CheckConformance.CONFORMANCE_VIOLATION);
    testNoWarning(code + "s.m(1)");
    testNoWarning(code + "maybeS.m(1)");
  }

  @Test
  public void testRestrictedMethodCallUsingCallThisType() {
    configuration =
        ""
            + "requirement: {\n"
            + "  type: RESTRICTED_METHOD_CALL\n"
            + "  value: 'Base.prototype.m:function(this:Sub,number)'\n"
            + "  error_message: 'Only call m on the subclass'\n"
            + "}";

    String code =
        "/** @constructor */\n"
            + "function Base() {}; Base.prototype.m;\n"
            + "/** @constructor @extends {Base} */\n"
            + "function Sub() {}\n"
            + "var b = new Base();\n"
            + "var s = new Sub();\n"
            + "var maybeB = cond ? new Base() : null;\n"
            + "var maybeS = cond ? new Sub() : null;";

    testWarning(code + "b.m.call(b, 1)", CheckConformance.CONFORMANCE_VIOLATION);
    testWarning(code + "b.m.call(maybeB, 1)", CheckConformance.CONFORMANCE_VIOLATION);
    testNoWarning(code + "b.m.call(s, 1)");
    testNoWarning(code + "b.m.call(maybeS, 1)");
  }

  @Test
  public void testRestrictedPropertyWrite() {
    configuration =
        ""
            + "requirement: {\n"
            + "  type: RESTRICTED_PROPERTY_WRITE\n"
            + "  value: 'Base.prototype.x:number'\n"
            + "  error_message: 'Only assign number'\n"
            + "}";

    String code =
        ""
            + "/** @constructor */\n"
            + "function Base() {}; Base.prototype.x;\n"
            + "var b = new Base();\n";

    testWarning(code + "b.x = 'a'", CheckConformance.CONFORMANCE_VIOLATION);
    testNoWarning(code + "b.x = 1");
    testNoWarning(code + "var a = {}; a.x = 'a'");
    testWarning(
        code + "/** @type {?} */ var a = {}; a.x = 'a'",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
    testWarning(
        code + "/** @type {*} */ var a = {}; a.x = 'a'",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testCustom1() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n" + "  type: CUSTOM\n" + "  error_message: 'placeholder'\n" + "}";

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: missing java_class\n"
            + "Requirement spec:\n"
            + "error_message: \"placeholder\"\n"
            + "type: CUSTOM\n");
  }

  @Test
  public void testCustom2() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'MissingClass'\n"
            + "  error_message: 'placeholder'\n"
            + "}";

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: JavaClass not found.\n"
            + "Requirement spec:\n"
            + "error_message: \"placeholder\"\n"
            + "type: CUSTOM\n"
            + "java_class: \"MissingClass\"\n");
  }

  @Test
  public void testCustom3() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.CheckConformanceTest'\n"
            + "  error_message: 'placeholder'\n"
            + "}";

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: JavaClass is not a rule.\n"
            + "Requirement spec:\n"
            + "error_message: \"placeholder\"\n"
            + "type: CUSTOM\n"
            + "java_class: \"com.google.javascript.jscomp.CheckConformanceTest\"\n");
  }

  // A custom rule missing a callable constructor.
  public static class CustomRuleMissingPublicConstructor extends AbstractRule {
    CustomRuleMissingPublicConstructor(AbstractCompiler compiler, Requirement requirement)
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
      return n.isScript() ? ConformanceResult.VIOLATION : ConformanceResult.CONFORMANCE;
    }
  }

  @Test
  public void testCustom4() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.CheckConformanceTest$"
            + "CustomRuleMissingPublicConstructor'\n"
            + "  error_message: 'placeholder'\n"
            + "}";

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: No valid class constructors found.\n"
            + "Requirement spec:\n"
            + "error_message: \"placeholder\"\n"
            + "type: CUSTOM\n"
            + "java_class: \"com.google.javascript.jscomp.CheckConformanceTest$"
            + "CustomRuleMissingPublicConstructor\"\n");
  }

  @Test
  public void testCustom5() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.CheckConformanceTest$CustomRule'\n"
            + "  error_message: 'placeholder'\n"
            + "}";

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        "Invalid requirement. Reason: missing value\n"
            + "Requirement spec:\n"
            + "error_message: \"placeholder\"\n"
            + "type: CUSTOM\n"
            + "java_class: \"com.google.javascript.jscomp.CheckConformanceTest$CustomRule\"\n");
  }

  @Test
  public void testCustom6() {
    allowSourcelessWarnings();
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.CheckConformanceTest$CustomRule'\n"
            + "  value: 'placeholder'\n"
            + "  error_message: 'placeholder'\n"
            + "}";

    testNoWarning("anything;");
  }

  @Test
  public void testCustom7() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.CheckConformanceTest$"
            + "CustomRuleReport'\n"
            + "  value: 'placeholder'\n"
            + "  error_message: 'CustomRule Message'\n"
            + "}";

    testWarning(
        "anything;", CheckConformance.CONFORMANCE_VIOLATION, "Violation: CustomRule Message");
  }

  @Test
  public void testCustomBanForOf() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanForOf'\n"
            + "  error_message: 'BanForOf Message'\n"
            + "}";

    testWarning(
        "for (var x of y) { var z = x; }",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanForOf Message");
  }

  @Test
  public void testCustomRestrictThrow1() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class:"
            + " 'com.google.javascript.jscomp.ConformanceRules$BanThrowOfNonErrorTypes'\n"
            + "  error_message: 'BanThrowOfNonErrorTypes Message'\n"
            + "}";

    testWarning(
        "throw 'blah';",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanThrowOfNonErrorTypes Message");
  }

  @Test
  public void testCustomRestrictThrow2() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class:"
            + " 'com.google.javascript.jscomp.ConformanceRules$BanThrowOfNonErrorTypes'\n"
            + "  error_message: 'BanThrowOfNonErrorTypes Message'\n"
            + "}";

    testNoWarning("throw new Error('test');");
  }

  @Test
  public void testCustomRestrictThrow3() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class:"
            + " 'com.google.javascript.jscomp.ConformanceRules$BanThrowOfNonErrorTypes'\n"
            + "  error_message: 'BanThrowOfNonErrorTypes Message'\n"
            + "}";

    testNoWarning(lines("/** @param {*} x */", "function f(x) {", "  throw x;", "}"));
  }

  @Test
  public void testCustomRestrictThrow4() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class:"
            + " 'com.google.javascript.jscomp.ConformanceRules$BanThrowOfNonErrorTypes'\n"
            + "  error_message: 'BanThrowOfNonErrorTypes Message'\n"
            + "}";

    testNoWarning(
        lines(
            "/** @constructor @extends {Error} */",
            "function MyError() {}",
            "/** @param {*} x */",
            "function f(x) {",
            "  if (x instanceof MyError) {",
            "  } else {",
            "    throw x;",
            "  }",
            "}"));
  }

  @Test
  public void testCustomBanUnknownThis1() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'\n"
            + "  error_message: 'BanUnknownThis Message'\n"
            + "}";

    testWarning(
        "function f() {alert(this);}",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanUnknownThis Message");
  }

  // TODO(johnlenz): add a unit test for templated "this" values.

  @Test
  public void testCustomBanUnknownThis2() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'\n"
            + "  error_message: 'BanUnknownThis Message'\n"
            + "}";

    testNoWarning("/** @constructor */ function C() {alert(this);}");
  }

  @Test
  public void testCustomBanUnknownThis3() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'\n"
            + "  error_message: 'BanUnknownThis Message'\n"
            + "}";

    testNoWarning("function f() {alert(/** @type {Error} */(this));}");
  }

  @Test
  public void testCustomBanUnknownThis_allowsClosurePrimitiveAssert() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'\n"
            + "  error_message: 'BanUnknownThis Message'\n"
            + "}";

    String assertInstanceof =
        lines(
            "/** @const */ var asserts = {};",
            "/**",
            " * @param {?} value The value to check.",
            " * @param {function(new: T, ...)} type A user-defined constructor.",
            " * @return {T}",
            " * @template T",
            " * @closurePrimitive {asserts.matchesReturn}",
            " */",
            "asserts.assertInstanceof = function(value, type) {",
            "  return value;",
            "};");

    testNoWarning(lines(assertInstanceof, "function f() {asserts.assertInstanceof(this, Error);}"));
  }

  @Test
  public void testCustomBanUnknownThis_allowsGoogAssert() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'\n"
            + "  error_message: 'BanUnknownThis Message'\n"
            + "}";

    testNoWarning("function f() {goog.asserts.assertInstanceof(this, Error);}");
  }

  @Test
  public void testCustomBanUnknownThis_allowsTs() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'\n"
            + "  error_message: 'BanUnknownThis Message'\n"
            + "}";

    testNoWarning(srcs(SourceFile.fromCode("file.closure.js", "function f() {alert(this);}")));
  }

  private static String config(String rule, String message, String... fields) {
    String result = "requirement: {\n" + "  type: CUSTOM\n" + "  java_class: '" + rule + "'\n";
    for (String field : fields) {
      result += field;
    }
    result += "  error_message: '" + message + "'\n" + "}";
    return result;
  }

  private static String rule(String rule) {
    return "com.google.javascript.jscomp.ConformanceRules$" + rule;
  }

  private static String value(String value) {
    return "  value: '" + value + "'\n";
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_implicitUnknownOnEs5Constructor_warn() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");

    testWarning(
        lines(
            "/** @constructor */",
            "function f() {}",
            "f.prototype.prop;",
            "f.prototype.method = function() { alert(this.prop); };"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message");
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_explicitUnknownOnEs5Constructor_ok() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");

    testNoWarning(
        lines(
            "/** @constructor */",
            "function f() {};",
            "/** @type {?} */",
            "f.prototype.prop;",
            "f.prototype.method = function() { alert(this.prop); }"));
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_implicitUnknownOnEs6Class_warn() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");

    testWarning(
        lines(
            "class F {",
            "  constructor() {",
            "    this.prop;",
            "  }",
            "  method() {",
            "    alert(this.prop);",
            "  }",
            "}"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message");
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_explicitUnknownOnEs6Class_ok() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");

    testNoWarning(
        lines(
            "class F {",
            "  constructor() {",
            "    /** @type {?} */",
            "    this.prop;",
            "  }",
            "  method() {",
            "    alert(this.prop);",
            "  }",
            "}"));
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_implicitUnknownOnClassField_warn() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");
    // TODO(b/192088118): need to fix so test gives warning for implicit field reference
    testNoWarning(
        lines(
            "class F {", //
            "  prop;",
            "  method() {",
            "    alert(this.prop);",
            "  }",
            "}"));
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_explicitUnknownOnClassField_ok() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");

    testNoWarning(
        lines(
            "class F {", //
            "  /** @type {?} */",
            "  prop = 2;",
            "  method() {",
            "    alert(this.prop);",
            "  }",
            "}"));
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_inferredNotUnknown_ok() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");

    testNoWarning(
        lines(
            "class F {",
            "  constructor() {",
            "    this.prop = 42;",
            "  }",
            "  method() {",
            "    alert(this.prop);",
            "  }",
            "}"));
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_implicitUnknownAssignedButNotUsed_ok() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");

    testNoWarning(
        lines(
            "/** @constructor */",
            "function f() {}",
            "f.prototype.prop;",
            "f.prototype.method = function() { this.prop = foo; };"));
  }

  @Test
  public void testCustomBanUnknownProp1() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testWarning(
        "/** @constructor */ function f() {}; f.prototype.prop;"
            + "f.prototype.method = function() { alert(this.prop); }",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message\nThe property \"prop\" on type \"f\"");
  }

  @Test
  public void testCustomBanUnknownProp_templateUnion() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testSame(
        lines(
            "/** @record @template T */",
            "class X {",
            "  constructor() {",
            "    /** @type {T|!Array<T>} */ this.x;",
            "    f(this.x);",
            "  }",
            "}",
            "",
            "/**",
            " * @param {T|!Array<T>} value",
            " * @template T",
            " */",
            "function f(value) {}"));
  }

  @Test
  public void testCustomBanUnknownProp1_es6Class() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testWarning(
        lines(
            "class F {",
            "  constructor() {",
            "    this.prop;",
            "  }",
            "}",
            "",
            "alert(new F().prop);"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message\nThe property \"prop\" on type \"F\"");
  }

  @Test
  public void testCustomBanUnknownProp2() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    String js =
        lines(
            "Object.prototype.foobar;",
            " /** @param {ObjectWithNoProps} a */",
            "function f(a) { alert(a.foobar); };");

    testWarning(
        js,
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message\nThe property \"foobar\" on type \"(ObjectWithNoProps|null)\"");
  }

  @Test
  public void testCustomBanUnknownProp3() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testNoWarning(
        "/** @constructor */ function f() {}"
            + "f.prototype.method = function() { this.prop = foo; };");
  }

  @Test
  public void testCustomBanUnknownProp4() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testNoWarning(
        lines(
            "/** @constructor */ function f() { /** @type {?} */ this.prop = null; };",
            "f.prototype.method = function() { alert(this.prop); }"));
  }

  @Test
  public void testCustomBanUnknownProp5() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testWarning(
        lines(
            "/** @typedef {?} */ var Unk;",
            "/** @constructor */ function f() { /** @type {?Unk} */ this.prop = null; };",
            "f.prototype.method = function() { alert(this.prop); }"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message\nThe property \"prop\" on type \"f\"");
  }

  @Test
  public void testCustomBanUnknownProp6() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testWarning(
        lines(
            "goog.module('example');",
            "/** @constructor */ function f() { this.prop; };",
            "f.prototype.method = function() { alert(this.prop); }"),
        CheckConformance.CONFORMANCE_VIOLATION,
        // TODO(tbreisacher): Can we display a more user-friendly name here?
        "Violation: My rule message\nThe property \"prop\" on type \"module$contents$example_f\"");
  }

  @Test
  public void testCustomBanUnknownProp7() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testNoWarning(
        lines(
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {!Object<number, number>} */",
            "  this.prop;",
            "}",
            "function f(/** !Foo */ x) {",
            "  return x.prop[1] + 123;",
            "}"));
  }

  @Test
  public void testCustomBanUnknownProp_getPropInVoidOperatorDoesntCauseSpuriousWarning() {
    // See b/112072360
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testNoWarning(
        lines(
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {!Object<number, number>} */",
            "  this.prop;",
            "}",
            "const foo = new Foo();",
            "const f = () => void foo.prop;"));
  }

  @Test
  public void testCustomBanUnknownProp_getPropInDestructuringDoesntCauseSpuriousWarning() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));
    test(
        externs("var unknownItem;"),
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {}",
                "const foo = new Foo();",
                // note that `foo.prop` is unknown here, but we don't warn because it's not being
                // used
                "[foo.prop] = unknownItem;")));
  }

  @Test
  public void testCustomBanUnknownProp_unionUndefined() {
    configuration = config(rule("BanUnknownTypedClassPropsReferences"), "My rule message");

    testNoWarning(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "if (false) {",
            "  /** @type {(null|?)} */",
            "  Foo.prototype.prop;",
            "}",
            "function f() {",
            "  return new Foo().prop;",
            "}"));
  }

  @Test
  public void testCustomBanUnknownProp_mergeConfigWithValue() {
    configuration =
        lines(
            config(
                rule("BanUnknownTypedClassPropsReferences"),
                "My message",
                "  rule_id: 'x'",
                "  allow_extending_value: true"),
            "requirement: {",
            "  extends: 'x'",
            "  value: 'F'",
            "}",
            "");

    testNoWarning(
        lines(
            "/** @typedef {?} */ var Unk;",
            "/** @constructor */ function F() { /** @type {?Unk} */ this.prop = null; };",
            "F.prototype.method = function() { alert(this.prop); }"));
  }

  @Test
  public void testCustomBanUnknownInterfaceProp1() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    String js =
        lines(
            "/** @interface */ function I() {}",
            "I.prototype.method = function() {};",
            "I.prototype.gak;",
            "/** @param {!I} a */",
            "function f(a) {",
            "  a.gak();",
            "}");

    testWarning(
        js,
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message\nThe property \"gak\" on type \"I\"");
  }

  @Test
  public void testCustomBanUnknownInterfaceProp2() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testNoWarning(
        lines(
            "/** @interface */ function I() {}",
            "I.prototype.method = function() {};",
            "/** @param {I} a */ function f(a) {",
            "  a.method();",
            "}"));
  }

  @Test
  public void testCustomBanGlobalVars1() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanGlobalVars'\n"
            + "  error_message: 'BanGlobalVars Message'\n"
            + "}";

    testWarning(
        "var x;", CheckConformance.CONFORMANCE_VIOLATION, "Violation: BanGlobalVars Message");

    testWarning(
        "function fn() {}",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanGlobalVars Message");

    testNoWarning("goog.provide('x');");

    testNoWarning("/** @externs */ var x;");

    // TODO(johnlenz): This might be overly conservative but doing otherwise is more complicated
    // so let see if we can get away with this.
    testWarning(
        "goog.provide('x'); var x;",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanGlobalVars Message");
  }

  @Test
  public void testCustomBanGlobalVars2() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanGlobalVars'\n"
            + "  error_message: 'BanGlobalVars Message'\n"
            + "}";

    testNoWarning(
        "goog.scope(function() {\n"
            + "  var x = {y: 'y'}\n"
            + "  var z = {\n"
            + "     [x.y]: 2\n"
            + "  }\n"
            + "});");

    // Test with let and const
    testNoWarning(
        lines(
            "goog.scope(function() {",
            "  let x = {y: 'y'}",
            "  let z = {",
            "     [x.y]: 2",
            "  }",
            "});"));

    testNoWarning(
        lines(
            "goog.scope(function() {",
            "  const x = {y: 'y'}",
            "  const z = {",
            "     [x.y]: 2",
            "  }",
            "});"));
  }

  @Test
  public void testCustomBanGlobalVarsWithDestructuring() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanGlobalVars'\n"
            + "  error_message: 'BanGlobalVars Message'\n"
            + "}";

    testWarning(
        "var [x] = [];",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanGlobalVars Message");

    testNoWarning("/** @externs */ var [x] = [];");
  }

  @Test
  public void testCustomBanGlobalVarsWithAllowlist() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanGlobalVars'\n"
            + "  value: 'foo'\n"
            + "  value: 'bar'\n"
            + "  error_message: 'BanGlobalVars Message'\n"
            + "}";

    testWarning(
        "var baz;", CheckConformance.CONFORMANCE_VIOLATION, "Violation: BanGlobalVars Message");

    testNoWarning("var foo; var bar;");
  }

  @Test
  public void testBanGlobalVarsInEs6Module() {
    // ES6 modules cannot be type checked yet
    disableTypeCheck();
    configuration =
        lines(
            "requirement: {",
            "  type: CUSTOM",
            "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanGlobalVars'",
            "  error_message: 'BanGlobalVars Message'",
            "}");

    testNoWarning("export function foo() {}");
    testNoWarning("var s; export {x}");
    testNoWarning("export var s;");
  }

  @Test
  public void testCustomBanUnresolvedType() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnresolvedType'\n"
            + "  error_message: 'BanUnresolvedType Message'\n"
            + "}";

    testWarning(
        "goog.forwardDeclare('Foo'); /** @param {Foo} a */ function f(a) {a.foo()};",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanUnresolvedType Message\nReference to type 'Foo' never resolved.");

    testWarning(
        "goog.forwardDeclare('Foo'); /** @type {Foo} */ var f = makeFoo(); f.foo();",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanUnresolvedType Message\nReference to type 'Foo' never resolved.");

    test(
        srcs(
            lines(
                "goog.forwardDeclare('Foo');",
                "goog.forwardDeclare('Bar');",
                "/** @param {?Foo|Bar} foobar */",
                "function f(foobar) {",
                "  return foobar.m();",
                "}")),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage(
                "Violation: BanUnresolvedType Message\n"
                    + "Reference to type 'Foo|Bar' never resolved."));

    testNoWarning(
        lines(
            "/**",
            " *  @param {!Object<string, ?>} data",
            " */",
            "function foo(data) {",
            "  data['bar'].baz();",
            "}"));
  }

  @Test
  public void testCustomStrictBanUnresolvedType() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class:"
            + " 'com.google.javascript.jscomp.ConformanceRules$StrictBanUnresolvedType'\n"
            + "  error_message: 'StrictBanUnresolvedType Message'\n"
            + "}";

    testWarning(
        "goog.forwardDeclare('Foo'); /** @param {Foo} a */ var f = function(a) {}",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: StrictBanUnresolvedType Message\nReference to type 'Foo' never resolved.");

    testWarning(
        srcs("goog.forwardDeclare('Foo'); /** @param {!Foo} a */ var f;", "f(5);"),
        TypeValidator.TYPE_MISMATCH_WARNING);

    testWarning(
        srcs(
            "goog.forwardDeclare('Foo'); /** @return {!Foo} */ var f;", //
            "f();"),
        CheckConformance.CONFORMANCE_VIOLATION);

    test(
        srcs(
            lines(
                "goog.forwardDeclare('Foo');",
                "goog.forwardDeclare('Bar');",
                "/**",
                " * @param {?Foo} foo",
                " * @param {?Bar} bar",
                " */",
                "function f(foo, bar) {",
                "  return foo || bar;",
                "}")),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage(
                "Violation: StrictBanUnresolvedType Message\n"
                    + "Reference to type 'Foo' never resolved."),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage(
                "Violation: StrictBanUnresolvedType Message\n"
                    + "Reference to type 'Bar' never resolved."),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage(
                "Violation: StrictBanUnresolvedType Message\n"
                    + "Reference to type 'Foo' never resolved."),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage(
                "Violation: StrictBanUnresolvedType Message\n"
                    + "Reference to type 'Bar|Foo' never resolved."),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage(
                "Violation: StrictBanUnresolvedType Message\n"
                    + "Reference to type 'Bar' never resolved."));
  }

  @Test
  public void testMergeRequirementsWhitelist() {
    Compiler compiler = createCompiler();
    ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();
    builder
        .addRequirementBuilder()
        .setRuleId("a")
        .addWhitelist("x")
        .addWhitelistRegexp("m")
        .addWhitelistEntry(WhitelistEntry.newBuilder().addPrefix("x2").addRegexp("m2").build());
    builder
        .addRequirementBuilder()
        .setExtends("a")
        .addWhitelist("y")
        .addWhitelistRegexp("n")
        .addWhitelistEntry(WhitelistEntry.newBuilder().addPrefix("a2").addRegexp("y2").build());
    List<Requirement> requirements =
        CheckConformance.mergeRequirements(compiler, ImmutableList.of(builder.build()));
    assertThat(requirements).hasSize(1);
    Requirement requirement = requirements.get(0);
    assertThat(requirement.getWhitelistCount()).isEqualTo(2);
    assertThat(requirement.getWhitelistRegexpCount()).isEqualTo(2);
    assertThat(requirement.getWhitelistEntryCount()).isEqualTo(2);
  }

  @Test
  public void testMergeRequirementsAllowlist() {
    Compiler compiler = createCompiler();
    ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();
    builder
        .addRequirementBuilder()
        .setRuleId("a")
        .addAllowlist("x")
        .addAllowlistRegexp("m")
        .addAllowlistEntry(WhitelistEntry.newBuilder().addPrefix("x2").addRegexp("m2").build());
    builder
        .addRequirementBuilder()
        .setExtends("a")
        .addAllowlist("y")
        .addAllowlistRegexp("n")
        .addAllowlistEntry(WhitelistEntry.newBuilder().addPrefix("a2").addRegexp("y2").build());
    List<Requirement> requirements =
        CheckConformance.mergeRequirements(compiler, ImmutableList.of(builder.build()));
    assertThat(requirements).hasSize(1);
    Requirement requirement = requirements.get(0);
    assertThat(requirement.getAllowlistCount()).isEqualTo(2);
    assertThat(requirement.getAllowlistRegexpCount()).isEqualTo(2);
    assertThat(requirement.getAllowlistEntryCount()).isEqualTo(2);
  }

  @Test
  public void testMergeRequirementsWhitelist_findsDuplicates() {
    Compiler compiler = createCompiler();
    ErrorManager errorManager = new BlackHoleErrorManager();
    compiler.setErrorManager(errorManager);
    ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();
    builder.addRequirementBuilder().addWhitelist("x").addWhitelist("x");
    List<Requirement> requirements =
        CheckConformance.mergeRequirements(compiler, ImmutableList.of(builder.build()));
    assertThat(requirements.get(0).getWhitelistCount()).isEqualTo(1);
    assertThat(errorManager.getErrorCount()).isEqualTo(0);
  }

  @Test
  public void testMergeRequirementsAllowlist_findsDuplicates() {
    Compiler compiler = createCompiler();
    ErrorManager errorManager = new BlackHoleErrorManager();
    compiler.setErrorManager(errorManager);
    ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();
    builder.addRequirementBuilder().addAllowlist("x").addAllowlist("x");
    List<Requirement> requirements =
        CheckConformance.mergeRequirements(compiler, ImmutableList.of(builder.build()));
    assertThat(requirements.get(0).getAllowlistCount()).isEqualTo(1);
    assertThat(errorManager.getErrorCount()).isEqualTo(0);
  }

  @Test
  public void testCustomBanNullDeref1() {
    configuration = config(rule("BanNullDeref"), "My rule message");

    String externs = EXTERNS + "String.prototype.prop;";

    testWarning(
        externs(externs),
        srcs("/** @param {string|null} n */ function f(n) { alert(n.prop); }"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message");

    testWarning(
        externs(externs),
        srcs("/** @param {string|null} n */ function f(n) { alert(n['prop']); }"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message");

    testWarning(
        externs(externs),
        srcs("/** @param {string|null} n */" + "function f(n) { alert('prop' in n); }"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message");

    testWarning(
        externs(externs),
        srcs("/** @param {string|undefined} n */ function f(n) { alert(n.prop); }"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message");

    testWarning(
        externs(externs),
        srcs("/** @param {?Function} fnOrNull */ function f(fnOrNull) { fnOrNull(); }"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message");

    testWarning(
        externs(externs),
        srcs("/** @param {?Function} fnOrNull */ function f(fnOrNull) { new fnOrNull(); }"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message");

    testNoWarning(
        externs(externs), srcs("/** @param {string} n */ function f(n) { alert(n.prop); }"));

    testNoWarning(externs(externs), srcs("/** @param {?} n */ function f(n) { alert(n.prop); }"));
  }

  @Test
  public void testCustomBanNullDeref2() {
    configuration = config(rule("BanNullDeref"), "My rule message");

    String externs = EXTERNS + "String.prototype.prop;";

    final String code = "/** @param {?String} n */ function f(n) { alert(n.prop); }";

    testWarning(
        externs(externs),
        srcs(code),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message");

    configuration = config(rule("BanNullDeref"), "My rule message", value("String"));

    testNoWarning(externs(externs), srcs(code));
  }

  @Test
  public void testCustomBanNullDeref3() {
    configuration = config(rule("BanNullDeref"), "My rule message");

    final String typedefExterns =
        lines(
            EXTERNS,
            "/** @fileoverview */",
            "/** @const */ var ns = {};",
            "/** @enum {number} */ ns.Type.State = {OPEN: 0};",
            "/** @typedef {{a:string}} */ ns.Type;",
            "");

    final String code =
        lines("/** @return {void} n */", "function f() { alert(ns.Type.State.OPEN); }");
    testNoWarning(externs(typedefExterns), srcs(code));
  }

  @Test
  public void testCustomBanNullDeref4() {
    configuration = config(rule("BanNullDeref"), "My rule message");

    testNoWarning(lines("/** @param {*} x */", "function f(x) {", "  return x.toString();", "}"));
  }

  @Test
  public void testRequireUseStrict0() {
    configuration = config(rule("RequireUseStrict"), "My rule message");

    testWarning("anything;", CheckConformance.CONFORMANCE_VIOLATION, "Violation: My rule message");
  }

  @Test
  public void testRequireUseStrictScript() {
    configuration = config(rule("RequireUseStrict"), "My rule message");

    testNoWarning("'use strict';");
  }

  @Test
  public void testRequireUseStrictGoogModule() {
    configuration = config(rule("RequireUseStrict"), "My rule message");

    testNoWarning("goog.module('foo');");
  }

  @Test
  public void testRequireUseStrictEs6Module() {
    configuration = config(rule("RequireUseStrict"), "My rule message");
    enableRewriteEsModules();

    testNoWarning("export var x = 2;");
  }

  @Test
  public void testBanCreateElement() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateElement'\n"
            + "  error_message: 'BanCreateElement Message'\n"
            + "  value: 'script'\n"
            + "}";

    testWarning(
        "goog.dom.createElement('script');",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateElement Message");

    testWarning(
        "goog.dom.createDom('script', {});",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateElement Message");

    String externs =
        lines(
            DEFAULT_EXTERNS,
            "/** @constructor */ function Document() {}",
            "/** @const {!Document} */ var document;",
            "/** @const */ goog.dom = {};",
            "/** @constructor */ goog.dom.DomHelper = function() {};");

    testWarning(
        externs(externs),
        srcs("document.createElement('script');"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateElement Message");

    testWarning(
        externs(externs),
        srcs("new goog.dom.DomHelper().createElement('script');"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateElement Message");

    testWarning(
        externs(externs),
        srcs("function f(/** ?Document */ doc) { doc.createElement('script'); }"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateElement Message");

    testNoWarning("goog.dom.createElement('iframe');");
    testNoWarning("goog.dom.createElement(goog.dom.TagName.SCRIPT);");
  }

  @Test
  public void testBanCreateDom() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateDom'\n"
            + "  error_message: 'BanCreateDom Message'\n"
            + "  value: 'iframe.src'\n"
            + "  value: 'div.class'\n"
            + "}";

    testWarning(
        "goog.dom.createDom('iframe', {'src': src});",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    testWarning(
        "goog.dom.createDom('iframe', {'src': src, 'name': ''}, '');",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    testWarning(
        "goog.dom.createDom(goog.dom.TagName.IFRAME, {'src': src});",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    testWarning(
        "goog.dom.createDom('div', 'red');",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    testWarning(
        "goog.dom.createDom('div', ['red']);",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    testWarning(
        "goog.dom.createDom('DIV', ['red']);",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    String externs =
        lines(
            DEFAULT_EXTERNS,
            "/** @const */ goog.dom = {};",
            "/** @constructor */ goog.dom.DomHelper = function() {};");

    testWarning(
        externs(externs),
        srcs("new goog.dom.DomHelper().createDom('iframe', {'src': src});"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    testWarning(
        "goog.dom.createDom(tag, {'src': src});",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION,
        "Possible violation: BanCreateDom Message");

    testWarning(
        "goog.dom.createDom('iframe', attrs);",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION,
        "Possible violation: BanCreateDom Message");

    testWarning(
        "goog.dom.createDom(tag, attrs);",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION,
        "Possible violation: BanCreateDom Message");

    testWarning(
        "goog.dom.createDom('iframe', x ? {'src': src} : 'class');",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION,
        "Possible violation: BanCreateDom Message");

    // Give a possible violation message if there are computed properties.
    testWarning(
        "goog.dom.createDom('iframe', {['not_src']: src});",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION,
        "Possible violation: BanCreateDom Message\n"
            + "The type information available for this expression is too loose to ensure"
            + " conformance.");

    testNoWarning("goog.dom.createDom('iframe');");
    testNoWarning("goog.dom.createDom('iframe', {'src': ''});");
    testNoWarning("goog.dom.createDom('iframe', {'name': name});");
    testNoWarning("goog.dom.createDom('iframe', 'red' + '');");
    testNoWarning("goog.dom.createDom('iframe', ['red']);");
    testNoWarning("goog.dom.createDom('iframe', undefined);");
    testNoWarning("goog.dom.createDom('iframe', null);");
    testNoWarning("goog.dom.createDom('img', {'src': src});");
    testNoWarning("goog.dom.createDom('img', attrs);");
    testNoWarning(
        lines(
            "goog.dom.createDom(",
            "'iframe', /** @type {?string|!Array|undefined} */ (className));"));
    testNoWarning("goog.dom.createDom(tag, {});");
    testNoWarning(
        "/** @enum {string} */ var Classes = {A: ''};\n"
            + "goog.dom.createDom('iframe', Classes.A);");
  }

  @Test
  public void testBanCreateDomIgnoreLooseType() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateDom'\n"
            + "  error_message: 'BanCreateDom Message'\n"
            + "  report_loose_type_violations: false\n"
            + "  value: 'iframe.src'\n"
            + "}";

    testWarning(
        "goog.dom.createDom('iframe', {'src': src});",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    testNoWarning("goog.dom.createDom('iframe', attrs);");
    testNoWarning("goog.dom.createDom(tag, {'src': src});");
  }

  @Test
  public void testBanCreateDomTagNameType() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateDom'\n"
            + "  error_message: 'BanCreateDom Message'\n"
            + "  value: 'div.class'\n"
            + "}";

    String externs =
        lines(
            DEFAULT_EXTERNS,
            "/** @const */ goog.dom = {};",
            "/** @constructor @template T */ goog.dom.TagName = function() {};",
            "/** @type {!goog.dom.TagName<!HTMLDivElement>} */",
            "goog.dom.TagName.DIV = new goog.dom.TagName();",
            "/** @constructor */ function HTMLDivElement() {}\n");

    testWarning(
        externs(externs),
        srcs(
            lines(
                "function f(/** !goog.dom.TagName<!HTMLDivElement> */ div) {",
                "  goog.dom.createDom(div, 'red');",
                "}")),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    testWarning(
        externs(externs),
        srcs(lines("const TagName = goog.dom.TagName;", "goog.dom.createDom(TagName.DIV, 'red');")),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");
  }

  @Test
  public void testBanCreateDomMultiType() {
    configuration =
        lines(
            "requirement: {",
            "  type: CUSTOM",
            "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateDom'",
            "  error_message: 'BanCreateDom Message'",
            "  value: 'h2.class'",
            "}");

    String externs =
        lines(
            DEFAULT_EXTERNS,
            "/** @const */ goog.dom = {};",
            "/** @constructor @template T */ goog.dom.TagName = function() {}",
            "/** @constructor */ function HTMLHeadingElement() {}\n");

    testWarning(
        externs(externs),
        srcs(
            lines(
                "function f(/** !goog.dom.TagName<!HTMLHeadingElement> */ heading) {",
                "  goog.dom.createDom(heading, 'red');",
                "}")),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");
  }

  @Test
  public void testBanCreateDomAnyTagName() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateDom'\n"
            + "  error_message: 'BanCreateDom Message'\n"
            + "  value: '*.innerHTML'\n"
            + "}";

    testWarning(
        "goog.dom.createDom('span', {'innerHTML': html});",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    testWarning(
        "goog.dom.createDom(tag, {'innerHTML': html});",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    testWarning(
        "goog.dom.createDom('span', attrs);",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION,
        "Possible violation: BanCreateDom Message");

    testNoWarning("goog.dom.createDom('span', {'innerHTML': ''});");
    testNoWarning("goog.dom.createDom('span', {'innerhtml': html});");
  }

  @Test
  public void testBanCreateDomTextContent() {
    configuration =
        "requirement: {\n"
            + "  type: CUSTOM\n"
            + "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateDom'\n"
            + "  error_message: 'BanCreateDom Message'\n"
            + "  value: 'script.textContent'\n"
            + "}";

    testWarning(
        "goog.dom.createDom('script', {}, source);",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    testWarning(
        "goog.dom.createDom('script', {'textContent': source});",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    testWarning(
        "goog.dom.createDom(script, {}, source);",
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION,
        "Possible violation: BanCreateDom Message");

    testNoWarning("goog.dom.createDom('script', {});");
    testNoWarning("goog.dom.createDom('span', {'textContent': text});");
    testNoWarning("goog.dom.createDom('span', {}, text);");
  }

  @Test
  public void testBanElementSetAttribute() {
    configuration =
        lines(
            "requirement: {\n",
            "  type: CUSTOM\n",
            "  value: 'src'\n",
            "  value: 'HREF'\n",
            "  java_class:"
                + " 'com.google.javascript.jscomp.ConformanceRules$BanElementSetAttribute'\n",
            "  report_loose_type_violations: false\n",
            "  error_message: 'BanSetAttribute Message'\n",
            "}");

    String externs =
        lines(
            DEFAULT_EXTERNS,
            "/** @constructor */ function Element() {}",
            "/** @constructor @extends {Element} */ function HTMLScriptElement() {}\n");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement).setAttribute('SRc', 'xxx')"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement).setAttribute('href', 'xxx')"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement).setAttributeNS(null, 'SRc', 'xxx')"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement)['SRc'] = 'xxx';"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement)['href'] = 'xxx';"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testNoWarning(
        externs(externs),
        srcs("(new HTMLScriptElement).setAttributeNS('ns', 'data-random', 'xxx')"));

    testNoWarning(
        externs(externs), srcs("(new HTMLScriptElement).setAttribute('data-random', 'xxx')"));

    testNoWarning(
        externs(externs),
        srcs("(new HTMLScriptElement).setAttributeNS(null, 'data-random', 'xxx')"));

    testNoWarning(externs(externs), srcs("(new HTMLScriptElement)['innerHTML'] = 'xxx';"));

    testNoWarning(
        externs(externs), srcs("var attr = 'unknown'; (new HTMLScriptElement)[attr] =  'xxx';"));

    testNoWarning(
        externs(externs),
        srcs("var attr = 'unknown'; (new HTMLScriptElement).setAttributeNS(null, attr, 'xxx')"));

    testNoWarning(
        externs(externs),
        srcs(
            "/** @param {string|null} attr */\n"
                + "function foo(attr) { (new HTMLScriptElement)[attr] =  'xxx'; }"));

    testNoWarning(
        externs(externs),
        srcs(
            lines(
                "const foo = 'safe';",
                "var bar = foo;",
                "(new HTMLScriptElement).setAttribute(bar, 'xxx');")));

    testNoWarning(externs(externs), srcs("(new HTMLScriptElement)['data-random'] = 'xxx';"));

    testNoWarning(
        externs(externs),
        srcs(
            lines(
                "const foo = 'safe';",
                "const bar = foo;",
                "(new HTMLScriptElement).setAttribute(bar, 'xxx');")));
  }

  @Test
  public void testBanElementSetAttributeLoose() {
    configuration =
        lines(
            "requirement: {\n",
            "  type: CUSTOM\n",
            "  value: 'src'\n",
            "  value: 'HREF'\n",
            "  java_class:"
                + " 'com.google.javascript.jscomp.ConformanceRules$BanElementSetAttribute'\n",
            "  error_message: 'BanSetAttribute Message'\n",
            "  report_loose_type_violations: true\n",
            "}");

    String externs =
        lines(
            DEFAULT_EXTERNS,
            "/** @constructor */ function Element() {}",
            "/** @constructor @extends {Element} */ function HTMLScriptElement() {}\n");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement).setAttribute('SRc', 'xxx')"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement).setAttribute('href', 'xxx')"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement).setAttributeNS(null, 'SRc', 'xxx')"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement).setAttributeNS('ns', 'data-random', 'xxx')"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs("var attr = 'unknown'; (new HTMLScriptElement).setAttributeNS(null, attr, 'xxx')"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testNoWarning(
        externs(externs), srcs("(new HTMLScriptElement).setAttribute('data-random', 'xxx')"));

    testNoWarning(
        externs(externs),
        srcs("(new HTMLScriptElement).setAttributeNS(null, 'data-random', 'xxx')"));

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement)['SRc'] = 'xxx';"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement)['src'] = 'xxx';"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement)['href'] = 'xxx';"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement)['innerHTML'] = 'xxx';"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testNoWarning(
        externs(externs), srcs("const attr = 'unknown'; (new HTMLScriptElement)[attr] =  'xxx';"));

    testWarning(
        externs(externs),
        srcs("var attr = 'unknown'; (new HTMLScriptElement)[attr] =  'xxx';"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs(
            "/** @param {string|null} attr */\n"
                + "function foo(attr) { (new HTMLScriptElement)[attr] =  'xxx'; }"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs(
            lines(
                "const foo = 'safe';",
                "var bar = foo;",
                "(new HTMLScriptElement).setAttribute(bar, 'xxx');")),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testNoWarning(externs(externs), srcs("(new HTMLScriptElement)['data-random'] = 'xxx';"));

    testNoWarning(
        externs(externs),
        srcs(
            lines(
                "const foo = 'safe';",
                "const bar = foo;",
                "(new HTMLScriptElement).setAttribute(bar, 'xxx');")));

    testNoWarning(
        externs(externs),
        srcs(
            lines(
                "/** @const */",
                "var foo = 'safe';",
                "(new HTMLScriptElement).setAttribute(foo, 'xxx');")));

    testNoWarning(
        externs(externs),
        srcs(
            lines(
                "goog.provide('test.Attribute');",
                "/** @const */",
                "test.Attribute.foo = 'safe';",
                "(new HTMLScriptElement).setAttribute(test.Attribute.foo, 'xxx');")));

    testNoWarning(
        externs(externs),
        srcs(
            new String[] {
              lines(
                  "goog.provide('test.Attribute');",
                  "",
                  "/** @enum {string} */",
                  "test.Attribute = {SRC: 'src', HREF: 'href', SAFE: 'safe'};"),
              lines(
                  "goog.module('test.setAttribute');",
                  "",
                  "const Attribute = goog.require('test.Attribute');",
                  "",
                  "const attr = Attribute.SAFE;",
                  "(new HTMLScriptElement).setAttribute(attr, 'xxx');")
            }));

    testNoWarning(
        externs(externs),
        srcs(
            lines(
                "goog.provide('xid');",
                "goog.provide('xid.String');",
                "/** @enum {string} */ xid.String = {DO_NOT_USE: ''};",
                "/**",
                " * @param {string} id",
                " * @return {xid.String}",
                " */",
                "xid = function(id) {return /** @type {xid.String} */ (id);};",
                "const attr = xid('src');",
                "(new HTMLScriptElement).setAttribute(attr, 'xxx');",
                "(new HTMLScriptElement)[attr] = 'xxx';")));
  }

  @Test
  public void testBanSettingAttributes() {
    configuration =
        lines(
            "requirement: {\n",
            "  type: CUSTOM\n",
            "  value: 'Element.prototype.attr'\n",
            "  value: 'Foo.prototype.attrib'\n",
            "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanSettingAttributes'\n",
            "  error_message: 'BanSettingAttributes Message'\n",
            "}");

    String externs =
        lines(
            DEFAULT_EXTERNS,
            "/** @constructor */ function Foo() {}\n",
            "/** @constructor */ function Bar() {}\n",
            "/** @constructor */ function Element() {}\n",
            "/** @constructor @extends {Element} */ function HTMLScriptElement() {}\n");

    testWarning(
        externs(externs),
        srcs("(new Foo).attrib('SRc', 'xxx')"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSettingAttributes Message");

    testWarning(
        externs(externs),
        srcs("(new HTMLScriptElement).attr('href', 'xxx')"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSettingAttributes Message");

    testNoWarning(externs(externs), srcs("(new Bar).attr('src', 'xxx')"));

    testNoWarning(externs(externs), srcs("(new Foo).attrib('src')"));

    testNoWarning(externs(externs), srcs("(new HTMLScriptElement).attrib('src', 'xxx')"));

    testNoWarning(externs(externs), srcs("(new HTMLScriptElement).attr('data-random', 'xxx')"));

    testWarning(
        externs(externs),
        srcs(
            lines(
                "const foo = 'safe';",
                "var bar = foo;",
                "(new HTMLScriptElement).attr(bar, 'xxx');")),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSettingAttributes Message");

    testNoWarning(
        externs(externs),
        srcs(
            lines(
                "const foo = 'safe';",
                "const bar = foo;",
                "(new HTMLScriptElement).attr(bar, 'xxx');")));

    testNoWarning(
        externs(externs),
        srcs(
            new String[] {
              lines(
                  "goog.provide('test.Attribute');",
                  "",
                  "/** @enum {string} */",
                  "test.Attribute = {SRC: 'src', HREF: 'href', SAFE: 'safe'};"),
              lines(
                  "goog.module('test.attr');",
                  "",
                  "const Attribute = goog.require('test.Attribute');",
                  "",
                  "const attr = Attribute.SAFE;",
                  "(new HTMLScriptElement).attr(attr, 'xxx');")
            }));

    testNoWarning(
        externs(externs),
        srcs(
            lines(
                "goog.provide('xid');",
                "goog.provide('xid.String');",
                "/** @enum {string} */ xid.String = {DO_NOT_USE: ''};",
                "/**",
                " * @param {string} id",
                " * @return {xid.String}",
                " */",
                "xid = function(id) {return /** @type {xid.String} */ (id);};",
                "const attr = xid('src');",
                "(new HTMLScriptElement).attr(attr, 'xxx');")));
  }

  @Test
  public void testBanExecCommand() {
    configuration =
        lines(
            "requirement: {\n",
            "  type: CUSTOM\n",
            "  value: 'insertHTML'\n",
            "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanExecCommand'\n",
            "  error_message: 'BanExecCommand message'\n",
            "}");

    String externs = lines(DEFAULT_EXTERNS, "/** @constructor */ function Document() {}");

    testWarning(
        externs(externs),
        srcs("(new Document).execCommand('insertHtml', false, 'xxx')"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanExecCommand message");

    testNoWarning(externs(externs), srcs("(new Document).execCommand('bold')"));
  }

  @Test
  public void testBanStaticThis() {
    configuration =
        lines(
            "requirement: {", //
            "  type: CUSTOM",
            "  java_class: 'com.google.javascript.jscomp.ConformanceRules$BanStaticThis'",
            "  error_message: 'BanStaticThis Message'",
            "}");

    testWarning(
        lines(
            "class Foo {", //
            "  static bar() {",
            "    this;",
            "  }",
            "}"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanStaticThis Message");
    testWarning(
        lines(
            "class Foo {", //
            "  static bar() {",
            "    this.buzz();",
            "  }",
            "  static buzz() {}",
            "}"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanStaticThis Message");
    testWarning(
        lines(
            "class Foo {", //
            "  static bar() {",
            "    let fn = () => this;",
            "  }",
            "}"),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanStaticThis Message");

    testNoWarning("let fn = function() { this.buzz(); };");
    testNoWarning(
        lines(
            "class Foo {", //
            "  bar() {",
            "    this.buzz();",
            "  }",
            "  buzz() {}",
            "}"));
    testNoWarning(
        lines(
            "class Foo {", //
            "  static buzz() {}",
            "}",
            "Foo.bar = function() {",
            "  this.buzz();",
            "}"));
    testNoWarning(
        lines(
            "class Foo {", //
            "  static bar() {",
            "    let fn = function() { this; };",
            "  }",
            "}"));
  }
}
