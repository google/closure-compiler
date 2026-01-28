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
import com.google.javascript.jscomp.CompilerOptions.ConformanceReportingMode;
import com.google.javascript.jscomp.ConformanceRules.AbstractRule;
import com.google.javascript.jscomp.ConformanceRules.ConformanceResult;
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

  private String baseConfiguration;
  private String extendingConfiguration;
  private ConformanceReportingMode reportingMode =
      ConformanceReportingMode.IGNORE_LIBRARY_LEVEL_BEHAVIOR_SPECIFIED_IN_CONFIG;

  private static final String EXTERNS =
      DEFAULT_EXTERNS
          + """
          /** @constructor */ function Window() {};
          /** @type {Window} */ var window;
          /** @type {Function} */ Arguments.prototype.callee;
          /** @type {Function} */ Arguments.prototype.caller;
          /** @type {Arguments} */ var arguments;
          /** @constructor
           * @param {*=} opt_message
           * @param {*=} opt_file
           * @param {*=} opt_line
           * @return {!Error}
          */
          function Error(opt_message, opt_file, opt_line) {};
          function alert(y) {};
          /** @constructor */ function ObjectWithNoProps() {};
          function eval() {}
          """;

  private static final String DEFAULT_CONFORMANCE =
      """
      requirement: {
        type: BANNED_NAME
        value: 'eval'
         error_message: 'eval is not allowed'
      }

      requirement: {
        type: BANNED_PROPERTY
        value: 'Arguments.prototype.callee'
        error_message: 'Arguments.prototype.callee is not allowed'
      }
      """;

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
    ConformanceConfig.Builder baseBuilder = ConformanceConfig.newBuilder();
    ConformanceConfig.Builder extendingBuilder = ConformanceConfig.newBuilder();
    try {
      TextFormat.merge(configuration, builder);
      if (baseConfiguration != null) {
        TextFormat.merge(baseConfiguration, baseBuilder);
      }
      if (extendingConfiguration != null) {
        TextFormat.merge(extendingConfiguration, extendingBuilder);
      }
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }

    return new CheckConformance(
        compiler,
        ImmutableList.of(builder.build(), baseBuilder.build(), extendingBuilder.build()),
        reportingMode);
  }

  @Test
  public void testViolation1() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
        }
        """;

    testWarning("eval()", CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(
        "Function.prototype.name; eval.name.length", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testViolation_templateLiteralInvalidWithEscapeSequence() {
    configuration =
        """
        requirement: {
          type: BANNED_STRING_REGEX
          value: '.*blah.*'
          error_message: 'Example error message'
        }
        """;

    // invalid escape sequence is not allowed for template literals
    testError("`\\000`", RhinoErrorReporter.PARSE_ERROR);
    // invalid escape sequence is allowed for tagged template literals
    testNoWarning("function f(x) {  } f`\\000`;");

    testWarning("`blah`", CheckConformance.CONFORMANCE_VIOLATION);
    testWarning("function f(x) {  } f`\\000blah\\000`;", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testViolation2() {
    testWarning("function f() { arguments.callee }", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testConfigFile() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          config_file: 'foo_conformance_proto.txt'
          error_message: 'eval is not allowed'
        }
        """;

    testWarning("eval()", CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(
        "Function.prototype.name; eval.name.length",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: eval is not allowed\n  defined in foo_conformance_proto.txt");
  }

  @Test
  public void testNotViolation1() {
    testNoWarning(
        """
        /** @constructor */ function Foo() { this.callee = 'string'; }
        /** @constructor */ function Bar() { this.callee = 1; }


        function f() {
          var x;
          switch(random()) {
            case 1:
              x = new Foo();
              break;
            case 2:
              x = new Bar();
              break;
            default:
              return;
          }
          var z = x.callee;
        }
        """);
  }

  @Test
  public void testNotViolation2() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'location'
          error_message: 'location is not allowed'
        }
        """;
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
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'placeholder'
          whitelist_regexp: '('
        }
        """;

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        """
        Invalid requirement. Reason: invalid regex pattern
        Requirement spec:
        error_message: "placeholder"
        whitelist_regexp: "("
        type: BANNED_NAME
        value: "eval"
        """);
  }

  @Test
  public void testBadAllowlist1() {
    allowSourcelessWarnings();
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'placeholder'
          allowlist_regexp: '('
        }
        """;

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        """
        Invalid requirement. Reason: invalid regex pattern
        Requirement spec:
        error_message: "placeholder"
        type: BANNED_NAME
        value: "eval"
        allowlist_regexp: "("
        """);
  }

  @Test
  public void testViolationWhitelisted1() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          whitelist: 'testcode'
        }
        """;

    testNoWarning("eval()");
  }

  @Test
  public void testViolationAllowlisted1() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          allowlist: 'testcode'
        }
        """;

    testNoWarning("eval()");
  }

  @Test
  public void testViolationWhitelistedAndAllowlistedDuplicated1() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          whitelist: 'testcode'
          allowlist: 'testcode'
        }
        """;

    testNoWarning("eval()");
  }

  @Test
  public void testViolationWhitelistedByWhitelistEntryPrefix() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          whitelist_entry {
            prefix: 'testcode'
          }
        }
        """;

    testNoWarning("eval()");
  }

  @Test
  public void testViolationAllowlistedByAllowlistEntryPrefix() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          allowlist_entry {
            prefix: 'testcode'
          }
        }
        """;

    testNoWarning("eval()");
  }

  @Test
  public void testViolationWhitelistedByWhitelistEntryRegexp() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          whitelist_entry {
            regexp: 'tes..ode'
          }
        }
        """;

    testNoWarning("eval()");
  }

  @Test
  public void testViolationAllowlistedByAllowlistEntryRegexp() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          allowlist_entry {
            regexp: 'tes..ode'
          }
        }
        """;

    testNoWarning("eval()");
  }

  @Test
  public void testViolationWhitelisted2() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          whitelist_regexp: 'code$'
        }
        """;

    testNoWarning("eval()");
  }

  @Test
  public void testViolationAllowlisted2() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          allowlist_regexp: 'code$'
        }
        """;

    testNoWarning("eval()");
  }

  @Test
  public void testViolationAllowlisted2Ts() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          allowlist_regexp: 'file.ts$'
        }
        """;

    testNoWarning(srcs(SourceFile.fromCode("file.closure.js", "eval()")));
  }

  @Test
  public void testViolationWhitelistedIgnoresRegex() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          whitelist: 'file.js'
        }
        """;

    testNoWarning(srcs(SourceFile.fromCode("test/google3/file.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/bin/file.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("google3/blaze-out/k8-opt/bin/file.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("bazel-out/k8-opt/bin/file.js", "eval()")));
  }

  @Test
  public void testViolationAllowlistedIgnoresRegex() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          allowlist: 'file.js'
        }
        """;

    testNoWarning(srcs(SourceFile.fromCode("test/google3/file.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/bin/file.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("google3/blaze-out/k8-opt/bin/file.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("bazel-out/k8-opt/bin/file.js", "eval()")));
  }

  @Test
  public void testViolationAllowlistedIgnoresRegexTs() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          allowlist: 'file.ts'
        }
        """;

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
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          whitelist: 'file1.js'
          allowlist: 'file2.js'
        }
        """;

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
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          whitelist: '/file.js'
        }
        """;

    testNoWarning(srcs(SourceFile.fromCode("/file.js", "eval()")));
  }

  @Test
  public void testViolationAllowlistedIgnoresRegex_absolutePath() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          allowlist: '/file.js'
        }
        """;

    testNoWarning(srcs(SourceFile.fromCode("/file.js", "eval()")));
  }

  @Test
  public void testViolationWhitelistedAndAllowlistedIgnoresRegex_absolutePath() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          whitelist: '/file1.js'
          allowlist: '/file2.js'
        }
        """;

    testNoWarning(srcs(SourceFile.fromCode("/file1.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("/file2.js", "eval()")));
  }

  @Test
  public void testViolationWhitelistedIgnoresRegex_genfiles() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          whitelist: 'file.js'
        }
        """;

    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/genfiles/file.js", "eval()")));
  }

  @Test
  public void testViolationAllowlistedIgnoresRegex_genfiles() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          allowlist: 'file.js'
        }
        """;

    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/genfiles/file.js", "eval()")));
  }

  @Test
  public void testViolationWhitelistedAndAllowlistedIgnoresRegex_genfiles() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          whitelist: 'file1.js'
          allowlist: 'file2.js'
        }
        """;

    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/genfiles/file1.js", "eval()")));
    testNoWarning(srcs(SourceFile.fromCode("blaze-out/k8-opt/genfiles/file2.js", "eval()")));
  }

  @Test
  public void testFileOnOnlyApplyToIsChecked() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          only_apply_to: 'foo.js'
        }
        """;
    ImmutableList<SourceFile> inputs = ImmutableList.of(SourceFile.fromCode("foo.js", "eval()"));
    testWarning(
        srcs(inputs),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage("Violation: eval is not allowed"));
  }

  @Test
  public void testFileOnOnlyApplyToIsCheckedTs() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          only_apply_to: 'foo.ts'
        }
        """;
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
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          only_apply_to: 'foo.js'
        }
        """;
    testNoWarning(srcs(SourceFile.fromCode("bar.js", "eval()")));
  }

  @Test
  public void testFileOnOnlyApplyToRegexpIsChecked() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          only_apply_to_regexp: 'test.js$'
        }
        """;
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
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          only_apply_to_regexp: 'test.ts$'
        }
        """;
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
        """
        requirement: {
          type: BANNED_NAME
          value: 'eval'
          error_message: 'eval is not allowed'
          only_apply_to_regexp: 'test.js$'
        }
        """;
    testNoWarning(srcs(SourceFile.fromCode("bar.js", "eval()")));
  }

  @Test
  public void testNoOp() {
    configuration =
        """
        requirement: {
          type: NO_OP,
          value: 'no matter',
          value: 'can be anything',
          error_message: 'Never happens'
        }
        """;
    testNoWarning("eval()");
  }

  @Test
  public void testBannedEnhance() {
    configuration =
        """
        requirement: {
          type: BANNED_ENHANCE
          value: '{some.banned.namespace}'
          value: '{another.banned.namespace}'
          error_message: 'Enhanced namespace is not allowed.'
        }
        """;
    String violationMessage =
        """
        Violation: Enhanced namespace is not allowed.
        The enhanced namespace \
        """;

    String ban1 =
        """
        /**
         * @enhance {some.banned.namespace}
         */
        """;
    testWarning(
        ban1,
        CheckConformance.CONFORMANCE_VIOLATION,
        violationMessage + "\"{some.banned.namespace}\"");

    String ban2 =
        """
        /**
         * @enhance {another.banned.namespace}
         */
        """;
    testWarning(
        ban2,
        CheckConformance.CONFORMANCE_VIOLATION,
        violationMessage + "\"{another.banned.namespace}\"");

    String allow1 =
        """
        /**
         * @enhance {some.allowed.namespace}
         */
        """;
    testNoWarning(allow1);

    String allow2 =
        """
        /**
         * @fileoverview no enhance annotation should always pass.
         */
        """;
    testNoWarning(allow2);
  }

  @Test
  public void testBannedModsRegex() {
    configuration =
        """
        requirement: {
          type: BANNED_MODS_REGEX
          value: '.+_bar$'
          error_message: 'Modding namespaces ending in _bar is NOT allowed.'
        }
        """;
    String violationMessage = "Violation: Modding namespaces ending in _bar is NOT allowed.";

    String ban =
        """
        /**
         * @mods {ns.foo_bar}
         * @modName {ns.foo_bar_baz}
         */
        """;
    testWarning(ban, CheckConformance.CONFORMANCE_VIOLATION, violationMessage);

    String allow1 =
        """
        /**
         * @mods {ns.foo}
         * @modName {ns.foo_bar}
         */
        """;
    testNoWarning(allow1);

    String allow2 =
        """
        /**
         * @fileoverview no enhance annotation should always pass.
         */
        """;
    testNoWarning(allow2);
  }

  @Test
  public void testBannedNameCall() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME_CALL
          value: 'Function'
          error_message: 'Calling Function is not allowed.'
        }
        """;

    testNoWarning("f instanceof Function");
    testWarning("new Function(str);", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedName_googProvided() {
    configuration =
        """
        requirement: {
          type: BANNED_NAME
          value: 'foo.bar'
          error_message: 'foo.bar is not allowed'
          allowlist: 'SRC1'
        }
        """;

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
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$InferredConstCheck'
          error_message: 'Failed to infer type of constant'
        }
        """;

    testNoWarning("/** @const */ var x = 0;");
    testNoWarning("/** @const */ var x = unknown;");
    testNoWarning("const x = unknown;");

    testWarning(
        """
        /** @constructor */
        function f() {
          /** @const */ this.foo = unknown;
        }
        var x = new f();
        """,
        CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(
        """
        /** @constructor */
        function f() {}
        /** @this {f} */
        var init_f = function() {
          /** @const */ this.foo = unknown;
        };
        var x = new f();
        """,
        CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(
        """
        /** @constructor */
        function f() {}
        var init_f = function() {
          /** @const */ this.FOO = unknown;
        };
        var x = new f();
        """,
        CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(
        """
        /** @constructor */
        function f() {}
        f.prototype.init_f = function() {
          /** @const */ this.FOO = unknown;
        };
        var x = new f();
        """,
        CheckConformance.CONFORMANCE_VIOLATION);

    testNoWarning(
        """
        /** @constructor */
        function f() {}
        f.prototype.init_f = function() {
          /** @const {?} */ this.FOO = unknown;
        };
        var x = new f();
        """);

    testNoWarning(
        """
        /** @const */
        var ns = {};
        /** @const */
        ns.subns = ns.subns || {};
        """);

    // We only check @const nodes, not @final nodes.
    testNoWarning(
        """
        /** @constructor */
        function f() {
          /** @final */ this.foo = unknown;
        }
        var x = new f();
        """);
  }

  @Test
  public void testBannedCodePattern1() {
    configuration =
        """
        requirement: {
          type: BANNED_CODE_PATTERN
          value: '/** @param {string|String} a */function template(a) {a.blink}'
          error_message: 'blink is annoying'
        }
        """;

    String externs = EXTERNS + "String.prototype.blink;";

    testNoWarning(
        """
        /** @constructor */ function Foo() { this.blink = 1; }
        var foo = new Foo();
        foo.blink();
        """);

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
        """
        Possible violation: blink is annoying
        The type information available for this expression is too loose to ensure conformance.
        """);
  }

  @Test
  public void testBannedDep1() {
    configuration =
        """
        requirement: {
          type: BANNED_DEPENDENCY
          value: 'testcode'
          error_message: 'testcode is not allowed'
        }
        """;

    testWarning(
        "anything;", CheckConformance.CONFORMANCE_VIOLATION, "Violation: testcode is not allowed");
  }

  @Test
  public void testBannedDepRegexNoValue() {
    allowSourcelessWarnings();
    configuration =
        """
        requirement: {
          type: BANNED_DEPENDENCY_REGEX
          error_message: 'testcode is not allowed'
        }
        """;

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        """
        Invalid requirement. Reason: missing value (no banned dependency regexps)
        Requirement spec:
        error_message: "testcode is not allowed"
        type: BANNED_DEPENDENCY_REGEX
        """);
  }

  @Test
  public void testBannedDepRegex() {
    configuration =
        """
        requirement: {
          type: BANNED_DEPENDENCY_REGEX
          error_message: 'testcode is not allowed'
          value: '.*test.*'
        }
        """;

    testWarning(
        "anything;", CheckConformance.CONFORMANCE_VIOLATION, "Violation: testcode is not allowed");
  }

  @Test
  public void testBannedDepRegexWithWhitelist() {
    configuration =
        """
        requirement: {
          type: BANNED_DEPENDENCY_REGEX
          error_message: 'testcode is not allowed'
          value: '.*test.*'
          whitelist_regexp: 'testcode'
        }
        """;

    testNoWarning("anything;");
  }

  @Test
  public void testBannedDepRegexWithAllowlist() {
    configuration =
        """
        requirement: {
          type: BANNED_DEPENDENCY_REGEX
          error_message: 'testcode is not allowed'
          value: '.*test.*'
          allowlist_regexp: 'testcode'
        }
        """;

    testNoWarning("anything;");
  }

  @Test
  public void testReportLooseTypeViolations() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY_WRITE
          value: 'HTMLScriptElement.prototype.textContent'
          error_message: 'Setting content of <script> is dangerous.'
          report_loose_type_violations: false
        }
        """;

    String externs =
        DEFAULT_EXTERNS
            + """
            /** @constructor */ function Element() {}
            /** @type {string} @implicitCast */
            Element.prototype.textContent;
            /** @constructor @extends {Element} */ function HTMLScriptElement() {}

            """;

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
        """
        requirement: {
          type: BANNED_PROPERTY_WRITE
          value: 'Bar.prototype.method'
          error_message: 'asdf'
          report_loose_type_violations: false
        }
        """;

    testNoWarning(
        externs(
            DEFAULT_EXTERNS
                + """
                /** @constructor */
                function Bar() {}
                Bar.prototype.method = function() {};
                """),
        srcs(
            """
            function Foo() {}
            Foo.prototype.method = function() {};
            """));
  }

  private void testConformance(String src1, String src2) {
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(SourceFile.fromCode("SRC1", src1), SourceFile.fromCode("SRC2", src2));
    testNoWarning(srcs(inputs));
  }

  private void testConformance(String src1, String src2, String src3) {
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("SRC1", src1),
            SourceFile.fromCode("SRC2", src2),
            SourceFile.fromCode("SRC3", src3));
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
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'C.prototype.p'
          error_message: 'C.p is not allowed'
          whitelist: 'SRC1'
        }
        """;

    String cDecl =
        """
        /** @constructor */
        function C() {}
        /** @type {string} */
        C.prototype.p;
        """;

    String dDecl =
        """
        /** @constructor */ function D() {}
        /** @type {string} */
        D.prototype.p;
        """;

    testConformance(cDecl, dDecl);
    String typedefDecl =
        """
        /** @typedef {{p: string}} */ let E;
        /** @const {!E} */ const value = {p: 'foo'};
        /** @type {string} */ const s = value.p;
        """;

    testConformance(cDecl, dDecl, typedefDecl);
  }

  @Test
  public void testBannedPropertyAllowlist0() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'C.prototype.p'
          error_message: 'C.p is not allowed'
          allowlist: 'SRC1'
        }
        """;

    String cDecl =
        """
        /** @constructor */
        function C() {}
        /** @type {string} */
        C.prototype.p;
        """;

    String dDecl =
        """
        /** @constructor */ function D() {}
        /** @type {string} */
        D.prototype.p;
        """;

    testConformance(cDecl, dDecl);
  }

  @Test
  public void testBannedPropertyWhitelist1() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'C.prototype.p'
          error_message: 'C.p is not allowed'
          whitelist: 'SRC1'
        }
        """;

    String cDecl =
        """
        /** @constructor */
        function C() {
          this.p = 'str';
        }
        """;

    String dDecl =
        """
        /** @constructor */
        function D() {
          this.p = 'str';
        }
        """;

    testConformance(cDecl, dDecl);
  }

  @Test
  public void testBannedPropertyAllowlist1() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'C.prototype.p'
          error_message: 'C.p is not allowed'
          allowlist: 'SRC1'
        }
        """;

    String cDecl =
        """
        /** @constructor */
        function C() {
          this.p = 'str';
        }
        """;

    String dDecl =
        """
        /** @constructor */
        function D() {
          this.p = 'str';
        }
        """;

    testConformance(cDecl, dDecl);
  }

  @Test
  public void testBannedPropertyWhitelist2() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'C.prototype.p'
          error_message: 'C.p is not allowed'
          whitelist: 'SRC1'
        }
        """;

    String declarations =
        """
        /** @constructor */ function SC() {}
        /** @constructor @extends {SC} */
        function C() {}
        /** @type {string} */
        C.prototype.p;
        /** @constructor */ function D() {}
        /** @type {string} */
        D.prototype.p;
        """;

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
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'C.prototype.p'
          error_message: 'C.p is not allowed'
          allowlist: 'SRC1'
        }
        """;

    String declarations =
        """
        /** @constructor */ function SC() {}
        /** @constructor @extends {SC} */
        function C() {}
        /** @type {string} */
        C.prototype.p;
        /** @constructor */ function D() {}
        /** @type {string} */
        D.prototype.p;
        """;

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
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'C.prototype.p'
          error_message: 'C.p is not allowed'
          whitelist: 'SRC1'
        }
        """;

    String cdecl =
        """
        /** @constructor */ function SC() {}
        /** @constructor @extends {SC} */
        function C() {}
        /** @type {string} */
        C.prototype.p;
        """;
    String ddecl =
        """
        /** @constructor @template T */ function D() {}
        /** @param {T} a */
        D.prototype.method = function(a) {
          use(a.p);
        };
        """;

    testConformance(cdecl, ddecl, CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyAllowlist3() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'C.prototype.p'
          error_message: 'C.p is not allowed'
          allowlist: 'SRC1'
        }
        """;

    String cdecl =
        """
        /** @constructor */ function SC() {}
        /** @constructor @extends {SC} */
        function C() {}
        /** @type {string} */
        C.prototype.p;
        """;
    String ddecl =
        """
        /** @constructor @template T */ function D() {}
        /** @param {T} a */
        D.prototype.method = function(a) {
          use(a.p);
        };
        """;

    testConformance(cdecl, ddecl, CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyWhitelist4() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'C.prototype.p'
          error_message: 'C.p is not allowed'
          whitelist: 'SRC1'
        }
        """;

    String cdecl =
        """
        /** @constructor */ function SC() {}
        /** @constructor @extends {SC} */
        function C() {}
        /** @type {string} */
        C.prototype.p;

        /**
         * @param {K} key
         * @param {V=} opt_value
         * @constructor
         * @struct
         * @template K, V
         * @private
         */
        var Entry_ = function(key, opt_value) {
          /** @const {K} */
          this.key = key;
          /** @type {V|undefined} */
          this.value = opt_value;
        };
        """;

    String ddecl =
        """
        /** @constructor @template T */ function D() {}
        /** @param {T} a */
        D.prototype.method = function(a) {
          var entry = new Entry('key');
          use(entry.value.p);
        };
        """;

    testConformance(cdecl, ddecl, CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyAllowlist4() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'C.prototype.p'
          error_message: 'C.p is not allowed'
          allowlist: 'SRC1'
        }
        """;

    String cdecl =
        """
        /** @constructor */ function SC() {}
        /** @constructor @extends {SC} */
        function C() {}
        /** @type {string} */
        C.prototype.p;

        /**
         * @param {K} key
         * @param {V=} opt_value
         * @constructor
         * @struct
         * @template K, V
         * @private
         */
        var Entry_ = function(key, opt_value) {
          /** @const {K} */
          this.key = key;
          /** @type {V|undefined} */
          this.value = opt_value;
        };
        """;

    String ddecl =
        """
        /** @constructor @template T */ function D() {}
        /** @param {T} a */
        D.prototype.method = function(a) {
          var entry = new Entry('key');
          use(entry.value.p);
        };
        """;

    testConformance(cdecl, ddecl, CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedProperty5() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'Array.prototype.push'
          error_message: 'banned Array.prototype.push'
        }
        """;

    testWarning("[1, 2, 3].push(4);\n", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedPropertyWhitelist_recordType() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'Logger.prototype.config'
          error_message: 'Logger.config is not allowed'
          whitelist: 'SRC1'
        }
        """;

    String declaration = "class Logger { config() {} }";

    // Fine, because there is no explicit relationship between Logger & GoodRecord.
    testConformance(
        declaration,
        """
        /** @record */
        class GoodRecord {
          constructor() {
            /** @type {Function} */ this.config;
          }
        }
        """);

    // Bad, because there is a direct relationship.
    testConformance(
        "/** @implements {BadRecord} */ " + declaration,
        """
        /** @record */
        class BadRecord {
          constructor() {
            /** @type {Function} */ this.config;
          }
        }
        """,
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyAllowlist_recordType() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'Logger.prototype.config'
          error_message: 'Logger.config is not allowed'
          allowlist: 'SRC1'
        }
        """;

    String declaration = "class Logger { config() {} }";

    // Fine, because there is no explicit relationship between Logger & GoodRecord.
    testConformance(
        declaration,
        """
        /** @record */
        class GoodRecord {
          constructor() {
            /** @type {Function} */ this.config;
          }
        }
        """);

    // Bad, because there is a direct relationship.
    testConformance(
        "/** @implements {BadRecord} */ " + declaration,
        """
        /** @record */
        class BadRecord {
          constructor() {
            /** @type {Function} */ this.config;
          }
        }
        """,
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyWhitelist_namespacedType() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'ns.C.prototype.p'
          error_message: 'ns.C.p is not allowed'
          whitelist: 'SRC1'
        }
        """;

    String declarations =
        """
        /** @const */
        var ns = {};
        /** @constructor */ function SC() {}
        /** @constructor @extends {SC} */
        ns.C = function() {}
        /** @type {string} */
        ns.C.prototype.p;
        /** @constructor */ function D() {}
        /** @type {string} */
        D.prototype.p;
        """;

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
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'ns.C.prototype.p'
          error_message: 'ns.C.p is not allowed'
          allowlist: 'SRC1'
        }
        """;

    String declarations =
        """
        /** @const */
        var ns = {};
        /** @constructor */ function SC() {}
        /** @constructor @extends {SC} */
        ns.C = function() {}
        /** @type {string} */
        ns.C.prototype.p;
        /** @constructor */ function D() {}
        /** @type {string} */
        D.prototype.p;
        """;

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
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'test.bns.BC.prototype.p'
          error_message: 'test.bns.BC.p is not allowed'
          whitelist: 'SRC1'
        }
        """;

    String declarations =
        """
        /** @fileoverview @typeSummary */
        goog.loadModule(function(exports) {
        goog.module('test.bns');
        /** @constructor */ function SBC() {}
        exports.SBC = SBC;
        /** @constructor @extends {SBC} */
        BC = function() {}
        /** @type {string} */
        BC.prototype.p;
        exports.BC = BC;
        /** @constructor */ function D() {}
        exports.D = D;
        /** @type {string} */
        D.prototype.p;
        return exports;
        });
        """;

    testConformance(
        declarations,
        """
        goog.module('test');
        const {D} = goog.require('test.bns');
        var d = new D();
        d.p = 'boo';
        """);

    // This case should be a certain violation, but the compiler cannot figure out that the imported
    // type is the same as the one found from the type registry.
    testConformance(
        declarations,
        """
        goog.module('test');
        const {BC} = goog.require('test.bns');
        var bc = new BC();
        bc.p = 'boo';
        """,
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testConformance(
        declarations,
        """
        goog.module('test');
        const bns = goog.require('test.bns');
        var bc = new bns.SBC();
        bc.p = 'boo';
        """,
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyAllowlist_bundledNamespacedType() {
    disableRewriteClosureCode();

    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'test.bns.BC.prototype.p'
          error_message: 'test.bns.BC.p is not allowed'
          allowlist: 'SRC1'
        }
        """;

    String declarations =
        """
        /** @fileoverview @typeSummary */
        goog.loadModule(function(exports) {
        goog.module('test.bns');
        /** @constructor */ function SBC() {}
        exports.SBC = SBC;
        /** @constructor @extends {SBC} */
        BC = function() {}
        /** @type {string} */
        BC.prototype.p;
        exports.BC = BC;
        /** @constructor */ function D() {}
        exports.D = D;
        /** @type {string} */
        D.prototype.p;
        return exports;
        });
        """;

    testConformance(
        declarations,
        """
        goog.module('test');
        const {D} = goog.require('test.bns');
        var d = new D();
        d.p = 'boo';
        """);

    // This case should be a certain violation, but the compiler cannot figure out that the imported
    // type is the same as the one found from the type registry.
    testConformance(
        declarations,
        """
        goog.module('test');
        const {BC} = goog.require('test.bns');
        var bc = new BC();
        bc.p = 'boo';
        """,
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

    testConformance(
        declarations,
        """
        goog.module('test');
        const bns = goog.require('test.bns');
        var bc = new bns.SBC();
        bc.p = 'boo';
        """,
        CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);
  }

  @Test
  public void testBannedPropertyWhitelist_destructuring() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'C.prototype.p'
          error_message: 'C.p is not allowed'
          whitelist: 'SRC1'
        }
        """;

    String declarations =
        """
        /** @constructor */
        var C = function() {}
        /** @type {string} */
        C.prototype.p;
        /** @type {number} */
        C.prototype.m
        """;

    testConformance(declarations, "var {m} = new C();");

    testConformance(declarations, "var {p} = new C();", CheckConformance.CONFORMANCE_VIOLATION);

    testConformance(
        declarations, "var {['p']: x} = new C();", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedPropertyAllowlist_destructuring() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY
          value: 'C.prototype.p'
          error_message: 'C.p is not allowed'
          allowlist: 'SRC1'
        }
        """;

    String declarations =
        """
        /** @constructor */
        var C = function() {}
        /** @type {string} */
        C.prototype.p;
        /** @type {number} */
        C.prototype.m
        """;

    testConformance(declarations, "var {m} = new C();");

    testConformance(declarations, "var {p} = new C();", CheckConformance.CONFORMANCE_VIOLATION);

    testConformance(
        declarations, "var {['p']: x} = new C();", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedPropertyWrite() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY_WRITE
          value: 'C.prototype.p'
          error_message: 'Assignment to C.p is not allowed'
        }
        """;

    String declarations =
        """
        /** @constructor */ function C() {}
        /** @type {string} */
        C.prototype.p;
        /** @constructor */ function D() {}
        /** @type {string} */
        D.prototype.p;
        """;

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
        """
        requirement: {
          type: BANNED_PROPERTY_WRITE
          value: 'Element.prototype.innerHTML'
          error_message: 'Assignment to Element.innerHTML is not allowed'
        }
        """;

    String externs =
        DEFAULT_EXTERNS
            + """
            /** @constructor */ function Element() {}
            /** @type {string} @implicitCast */
            Element.prototype.innerHTML;
            """;

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
        """
        requirement: {
          type: BANNED_PROPERTY_NON_CONSTANT_WRITE
          value: 'C.prototype.p'
          error_message: 'Assignment of a non-constant value to C.p is not allowed'
        }
        """;

    String declarations =
        """
        /** @constructor */ function C() {}
        /** @type {string} */
        C.prototype.p;
        """;

    testNoWarning(declarations + "var c = new C(); c.p = 'boo';");
    testNoWarning(declarations + "var c = new C(); c.p = 'foo' + 'bar';");

    testWarning(
        declarations + "var boo = 'boo'; var c = new C(); c.p = boo;",
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedPropertyRead() {
    configuration =
        """
        requirement: {
          type: BANNED_PROPERTY_READ
          value: 'C.prototype.p'
          error_message: 'Use of C.p is not allowed'
        }
        """;

    String declarations =
        """
        /** @constructor */ function C() {}
        /** @type {string} */
        C.prototype.p;
        /** @constructor */ function D() {}
        /** @type {string} */
        D.prototype.p;
        function use(a) {};
        """;

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
        """
        requirement: {
          type: BANNED_STRING_REGEX
          error_message: 'Empty string not allowed'
        }
        """;

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        """
        Invalid requirement. Reason: missing value
        Requirement spec:
        error_message: "Empty string not allowed"
        type: BANNED_STRING_REGEX
        """);
  }

  @Test
  public void testBannedStringRegexEmpty() {
    allowSourcelessWarnings();
    configuration =
        """
        requirement: {
          type: BANNED_STRING_REGEX
          value: ' '
          error_message: 'Empty string not allowed'
        }
        """;

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        """
        Invalid requirement. Reason: empty strings or whitespace are not allowed
        Requirement spec:
        error_message: "Empty string not allowed"
        type: BANNED_STRING_REGEX
        value: " "
        """);
  }

  @Test
  public void testBannedStringRegexMultipleValuesWithEmpty() {
    allowSourcelessWarnings();
    configuration =
        """
        requirement: {
          type: BANNED_STRING_REGEX
          value: 'things'
          value: ' '
          error_message: 'Empty string not allowed'
        }
        """;

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        """
        Invalid requirement. Reason: empty strings or whitespace are not allowed
        Requirement spec:
        error_message: "Empty string not allowed"
        type: BANNED_STRING_REGEX
        value: "things"
        value: " "
        """);
  }

  @Test
  public void testBannedStringRegex1() {
    configuration =
        """
        requirement: {
          type: BANNED_STRING_REGEX
          value: '.*some-attr.*'
          error_message: 'Empty string not allowed'
        }
        """;

    String declarations = "let dom = '<div>sample dom template content</div>';";

    testNoWarning(declarations);

    testWarning(
        declarations + "let moredom = '<p some-attr=\"testval\">reflected!</p>';",
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedStringRegex2() {
    configuration =
        """
        requirement: {
          type: BANNED_STRING_REGEX
          value: '.*things.*'
          value: 'stuff.*'
          error_message: 'Empty string not allowed'
        }
        """;

    String code =
        """
        /** @constructor */
        function Base() {}; Base.prototype.m;
        /** @constructor @extends {Base} */
        function Sub() {}
        """;
    String stuff = "var b = 'stuff and what not';\n";
    String things = "var s = 'special things';\n";

    testNoWarning(code);

    testWarning(code + stuff, CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(code + things, CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedStringRegexExactMatch() {
    configuration =
        """
        requirement: {
          type: BANNED_STRING_REGEX
          value: 'stuff'
          error_message: 'Empty string not allowed'
        }
        """;

    String code =
        """
        /** @constructor */
        function Base() {}; Base.prototype.m;
        /** @constructor @extends {Base} */
        function Sub() {}
        """;
    String noMatch = "var b = ' stuff ';\n";
    String shouldMatch = "var s = 'stuff';\n";

    testNoWarning(code + noMatch);

    testWarning(code + shouldMatch, CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testBannedStringTemplateLiteral1() {
    configuration =
        """
        requirement: {
          type: BANNED_STRING_REGEX
          value: '.*good'
          error_message: 'Empty string not allowed'
        }
        """;

    String code =
        """
        /** @constructor */
        function Base() {}; Base.prototype.m;
        /** @constructor @extends {Base} */
        function Sub() {}
        """;
    String noMatch = "var b = `cheesy goodness`;\n";
    String shouldMatch = "var b = `cheesy good`;\n";

    testNoWarning(code + noMatch);

    testWarning(code + shouldMatch, CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testRestrictedCall1() {
    configuration =
        """
        requirement: {
          type: RESTRICTED_METHOD_CALL
          value: 'C.prototype.m:function(number)'
          error_message: 'm method param must be number'
        }
        """;

    String code =
        """
        /** @constructor */ function C() {}
        /** @param {*} a */
        C.prototype.m = function(a){}
        """;

    testNoWarning(code + "new C().m(1);");

    testWarning(code + "new C().m('str');", CheckConformance.CONFORMANCE_VIOLATION);

    testNoWarning(code + "new C().m.call(new C(), 1);");

    testWarning(code + "new C().m.call(new C(), 'str');", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testRestrictedCall2() {
    configuration =
        """
        requirement: {
          type: RESTRICTED_NAME_CALL
          value: 'C.m:function(number)'
          error_message: 'C.m method param must be number'
        }
        """;

    String code =
        """
        /** @constructor */ function C() {}
        /** @param {*} a */
        C.m = function(a){}
        """;

    testNoWarning(code + "C.m(1);");

    testWarning(code + "C.m('str');", CheckConformance.CONFORMANCE_VIOLATION);

    testNoWarning(code + "C.m.call(C, 1);");

    testWarning(code + "C.m.call(C, 'str');", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testRestrictedCall3() {
    configuration =
        """
        requirement: {
          type: RESTRICTED_NAME_CALL
          value: 'C:function(number)'
          error_message: 'C method must be number'
        }
        """;

    String code = "/** @constructor @param {...*} a */ function C(a) {}\n";

    testNoWarning(code + "new C(1);");

    testWarning(code + "new C('str');", CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(code + "new C(1, 1);", CheckConformance.CONFORMANCE_VIOLATION);

    testWarning(code + "new C();", CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testRestrictedCall4() {
    configuration =
        """
        requirement: {
          type: RESTRICTED_NAME_CALL
          value: 'C:function(number)'
          error_message: 'C method must be number'
        }
        """;

    String code = "/** @constructor @param {...*} a */ function C(a) {}\n";

    testNoWarning(externs(EXTERNS + "goog.inherits;"), srcs(code + "goog.inherits(A, C);"));
  }

  @Test
  public void testRestrictedMethodCallThisType() {
    configuration =
        """
        requirement: {
          type: RESTRICTED_METHOD_CALL
          value: 'Base.prototype.m:function(this:Sub,number)'
          error_message: 'Only call m on the subclass'
        }
        """;

    String code =
        """
        /** @constructor */
        function Base() {}; Base.prototype.m;
        /** @constructor @extends {Base} */
        function Sub() {}
        var b = new Base();
        var s = new Sub();
        var maybeB = cond ? new Base() : null;
        var maybeS = cond ? new Sub() : null;
        """;

    testWarning(code + "b.m(1)", CheckConformance.CONFORMANCE_VIOLATION);
    testWarning(code + "maybeB.m(1)", CheckConformance.CONFORMANCE_VIOLATION);
    testNoWarning(code + "s.m(1)");
    testNoWarning(code + "maybeS.m(1)");
  }

  @Test
  public void testRestrictedMethodCallUsingCallThisType() {
    configuration =
        """
        requirement: {
          type: RESTRICTED_METHOD_CALL
          value: 'Base.prototype.m:function(this:Sub,number)'
          error_message: 'Only call m on the subclass'
        }
        """;

    String code =
        """
        /** @constructor */
        function Base() {}; Base.prototype.m;
        /** @constructor @extends {Base} */
        function Sub() {}
        var b = new Base();
        var s = new Sub();
        var maybeB = cond ? new Base() : null;
        var maybeS = cond ? new Sub() : null;
        """;

    testWarning(code + "b.m.call(b, 1)", CheckConformance.CONFORMANCE_VIOLATION);
    testWarning(code + "b.m.call(maybeB, 1)", CheckConformance.CONFORMANCE_VIOLATION);
    testNoWarning(code + "b.m.call(s, 1)");
    testNoWarning(code + "b.m.call(maybeS, 1)");
  }

  @Test
  public void testRestrictedPropertyWrite() {
    configuration =
        """
        requirement: {
          type: RESTRICTED_PROPERTY_WRITE
          value: 'Base.prototype.x:number'
          error_message: 'Only assign number'
        }
        """;

    String code =
        """
        /** @constructor */
        function Base() {}; Base.prototype.x;
        var b = new Base();
        """;

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
        """
        requirement: {
          type: CUSTOM
          error_message: 'placeholder'
        }
        """;

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        """
        Invalid requirement. Reason: missing java_class
        Requirement spec:
        error_message: "placeholder"
        type: CUSTOM
        """);
  }

  @Test
  public void testCustom2() {
    allowSourcelessWarnings();
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'MissingClass'
          error_message: 'placeholder'
        }
        """;

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        """
        Invalid requirement. Reason: JavaClass not found.
        Requirement spec:
        error_message: "placeholder"
        type: CUSTOM
        java_class: "MissingClass"
        """);
  }

  @Test
  public void testCustom3() {
    allowSourcelessWarnings();
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.CheckConformanceTest'
          error_message: 'placeholder'
        }
        """;

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        """
        Invalid requirement. Reason: JavaClass is not a rule.
        Requirement spec:
        error_message: "placeholder"
        type: CUSTOM
        java_class: "com.google.javascript.jscomp.CheckConformanceTest"
        """);
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
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.CheckConformanceTest$CustomRuleMissingPublicConstructor'
          error_message: 'placeholder'
        }
        """;

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        """
        Invalid requirement. Reason: No valid class constructors found.
        Requirement spec:
        error_message: "placeholder"
        type: CUSTOM
        java_class: "com.google.javascript.jscomp.CheckConformanceTest$CustomRuleMissingPublicConstructor"
        """);
  }

  @Test
  public void testCustom5() {
    allowSourcelessWarnings();
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.CheckConformanceTest$CustomRule'
          error_message: 'placeholder'
        }
        """;

    testError(
        "anything;",
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        """
        Invalid requirement. Reason: missing value
        Requirement spec:
        error_message: "placeholder"
        type: CUSTOM
        java_class: "com.google.javascript.jscomp.CheckConformanceTest$CustomRule"
        """);
  }

  @Test
  public void testCustom6() {
    allowSourcelessWarnings();
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.CheckConformanceTest$CustomRule'
          value: 'placeholder'
          error_message: 'placeholder'
        }
        """;

    testNoWarning("anything;");
  }

  @Test
  public void testCustom7() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.CheckConformanceTest$CustomRuleReport'
          value: 'placeholder'
          error_message: 'CustomRule Message'
        }
        """;

    testWarning(
        "anything;", CheckConformance.CONFORMANCE_VIOLATION, "Violation: CustomRule Message");
  }

  @Test
  public void testCustomBanForOf() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanForOf'
          error_message: 'BanForOf Message'
        }
        """;

    testWarning(
        "for (var x of y) { var z = x; }",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanForOf Message");
  }

  @Test
  public void testCustomRestrictThrow1() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanThrowOfNonErrorTypes'
          error_message: 'BanThrowOfNonErrorTypes Message'
        }
        """;

    testWarning(
        "throw 'blah';",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanThrowOfNonErrorTypes Message");
  }

  @Test
  public void testCustomRestrictThrow2() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanThrowOfNonErrorTypes'
          error_message: 'BanThrowOfNonErrorTypes Message'
        }
        """;

    testNoWarning("throw new Error('test');");
  }

  @Test
  public void testCustomRestrictThrow3() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanThrowOfNonErrorTypes'
          error_message: 'BanThrowOfNonErrorTypes Message'
        }
        """;

    testNoWarning(
        """
        /** @param {*} x */
        function f(x) {
          throw x;
        }
        """);
  }

  @Test
  public void testCustomRestrictThrow4() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanThrowOfNonErrorTypes'
          error_message: 'BanThrowOfNonErrorTypes Message'
        }
        """;

    testNoWarning(
        """
        /** @constructor @extends {Error} */
        function MyError() {}
        /** @param {*} x */
        function f(x) {
          if (x instanceof MyError) {
          } else {
            throw x;
          }
        }
        """);
  }

  @Test
  public void testCustomBanUnknownThis1() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'
          error_message: 'BanUnknownThis Message'
        }
        """;

    testWarning(
        "function f() {alert(this);}",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanUnknownThis Message");
  }

  // TODO(johnlenz): add a unit test for templated "this" values.

  @Test
  public void testCustomBanUnknownThis2() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'
          error_message: 'BanUnknownThis Message'
        }
        """;

    testNoWarning("/** @constructor */ function C() {alert(this);}");
  }

  @Test
  public void testCustomBanUnknownThis3() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'
          error_message: 'BanUnknownThis Message'
        }
        """;

    testNoWarning("function f() {alert(/** @type {Error} */(this));}");
  }

  @Test
  public void testCustomBanUnknownThis_allowsClosurePrimitiveAssert() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'
          error_message: 'BanUnknownThis Message'
        }
        """;

    String assertInstanceof =
        """
        /** @const */ var asserts = {};
        /**
         * @param {?} value The value to check.
         * @param {function(new: T, ...)} type A user-defined constructor.
         * @return {T}
         * @template T
         * @closurePrimitive {asserts.matchesReturn}
         */
        asserts.assertInstanceof = function(value, type) {
          return value;
        };
        """;

    testNoWarning(assertInstanceof + "function f() {asserts.assertInstanceof(this, Error);}");
  }

  @Test
  public void testCustomBanUnknownThis_allowsGoogAssert() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'
          error_message: 'BanUnknownThis Message'
        }
        """;

    testNoWarning("function f() {goog.asserts.assertInstanceof(this, Error);}");
  }

  @Test
  public void testCustomBanUnknownThis_allowsTs() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnknownThis'
          error_message: 'BanUnknownThis Message'
        }
        """;

    testNoWarning(srcs(SourceFile.fromCode("file.closure.js", "function f() {alert(this);}")));
  }

  private static String config(String rule, String message, String... fields) {
    String result =
        """
        requirement: {
          type: CUSTOM
          java_class: 'RULE'
        """
            .replace("RULE", rule);
    for (String field : fields) {
      result += field;
    }
    result +=
        """
          error_message: 'MESSAGE'
        }
        """
            .replace("MESSAGE", message);
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
        """
        /** @constructor */
        function f() {}
        f.prototype.prop;
        f.prototype.method = function() { alert(this.prop); };
        """,
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message");
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_explicitUnknownOnEs5Constructor_ok() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");

    testNoWarning(
        """
        /** @constructor */
        function f() {};
        /** @type {?} */
        f.prototype.prop;
        f.prototype.method = function() { alert(this.prop); }
        """);
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_implicitUnknownOnEs6Class_warn() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");

    testWarning(
        """
        class F {
          constructor() {
            this.prop;
          }
          method() {
            alert(this.prop);
          }
        }
        """,
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message");
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_explicitUnknownOnEs6Class_ok() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");

    testNoWarning(
        """
        class F {
          constructor() {
            /** @type {?} */
            this.prop;
          }
          method() {
            alert(this.prop);
          }
        }
        """);
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_implicitUnknownOnClassField_warn() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");
    // TODO(b/192088118): need to fix so test gives warning for implicit field reference
    testNoWarning(
        """
        class F {
          prop;
          method() {
            alert(this.prop);
          }
        }
        """);
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_explicitUnknownOnClassField_ok() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");

    testNoWarning(
        """
        class F {
          /** @type {?} */
          prop = 2;
          method() {
            alert(this.prop);
          }
        }
        """);
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_inferredNotUnknown_ok() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");

    testNoWarning(
        """
        class F {
          constructor() {
            this.prop = 42;
          }
          method() {
            alert(this.prop);
          }
        }
        """);
  }

  @Test
  public void testBanUnknownDirectThisPropsReferences_implicitUnknownAssignedButNotUsed_ok() {
    configuration = config(rule("BanUnknownDirectThisPropsReferences"), "My rule message");

    testNoWarning(
        """
        /** @constructor */
        function f() {}
        f.prototype.prop;
        f.prototype.method = function() { this.prop = foo; };
        """);
  }

  @Test
  public void testCustomBanUnknownProp1() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testWarning(
        """
        /** @constructor */ function f() {}; f.prototype.prop;
        f.prototype.method = function() { alert(this.prop); }
        """,
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message\nThe property \"prop\" on type \"f\"");
  }

  @Test
  public void testCustomBanUnknownProp_templateUnion() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testSame(
        """
        /** @record @template T */
        class X {
          constructor() {
            /** @type {T|!Array<T>} */ this.x;
            f(this.x);
          }
        }

        /**
         * @param {T|!Array<T>} value
         * @template T
         */
        function f(value) {}
        """);
  }

  @Test
  public void testCustomBanUnknownProp1_es6Class() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testWarning(
        """
        class F {
          constructor() {
            this.prop;
          }
        }

        alert(new F().prop);
        """,
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message\nThe property \"prop\" on type \"F\"");
  }

  @Test
  public void testCustomBanUnknownProp2() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    String js =
        """
        Object.prototype.foobar;
         /** @param {ObjectWithNoProps} a */
        function f(a) { alert(a.foobar); };
        """;

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
        """
        /** @constructor */ function f() {}
        f.prototype.method = function() { this.prop = foo; };
        """);
  }

  @Test
  public void testCustomBanUnknownProp4() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testNoWarning(
        """
        /** @constructor */ function f() { /** @type {?} */ this.prop = null; };
        f.prototype.method = function() { alert(this.prop); }
        """);
  }

  @Test
  public void testCustomBanUnknownProp5() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testWarning(
        """
        /** @typedef {?} */ var Unk;
        /** @constructor */ function f() { /** @type {?Unk} */ this.prop = null; };
        f.prototype.method = function() { alert(this.prop); }
        """,
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message\nThe property \"prop\" on type \"f\"");
  }

  @Test
  public void testCustomBanUnknownProp6() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testWarning(
        """
        goog.module('example');
        /** @constructor */ function f() { this.prop; };
        f.prototype.method = function() { alert(this.prop); }
        """,
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: My rule message\nThe property \"prop\" on type \"f\"");
  }

  @Test
  public void testCustomBanUnknownProp7() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testNoWarning(
        """
        /** @constructor */
        function Foo() {
          /** @type {!Object<number, number>} */
          this.prop;
        }
        function f(/** !Foo */ x) {
          return x.prop[1] + 123;
        }
        """);
  }

  @Test
  public void testCustomBanUnknownProp_getPropInVoidOperatorDoesntCauseSpuriousWarning() {
    // See b/112072360
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    testNoWarning(
        """
        /** @constructor */
        function Foo() {
          /** @type {!Object<number, number>} */
          this.prop;
        }
        const foo = new Foo();
        const f = () => void foo.prop;
        """);
  }

  @Test
  public void testCustomBanUnknownProp_getPropInDestructuringDoesntCauseSpuriousWarning() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));
    test(
        externs("var unknownItem;"),
        srcs(
            """
            /** @constructor */
            function Foo() {}
            const foo = new Foo();
            // note that `foo.prop` is unknown here, but we don't warn because it's not being
            // used
            [foo.prop] = unknownItem;
            """));
  }

  @Test
  public void testCustomBanUnknownProp_unionUndefined() {
    configuration = config(rule("BanUnknownTypedClassPropsReferences"), "My rule message");

    testNoWarning(
        """
        /** @constructor */
        function Foo() {}
        if (false) {
          /** @type {(null|?)} */
          Foo.prototype.prop;
        }
        function f() {
          return new Foo().prop;
        }
        """);
  }

  @Test
  public void testSimpleExtends() {
    baseConfiguration =
        """
        requirement: {
          rule_id: "gws:goog.Promise.X"
          type: BANNED_NAME
          error_message: "Prefer using native Promise equivalents. See go/gws-js-conformance#goog-promise"

          value: "goog.Promise.all"
        }
        """;
    ;
    extendingConfiguration =
        """
        requirement: {
          extends: 'gws:goog.Promise.X'
        }
        """;

    testWarning(
        """
        goog.Promise.all();
        """,
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testSimpleExtends_withAllowlist() {
    baseConfiguration =
        """
        requirement: {
          rule_id: "gws:goog.Promise.X"
          type: BANNED_NAME
          error_message: "Prefer using native Promise equivalents. See go/gws-js-conformance#goog-promise"

          value: "goog.Promise.all"
        }
        """;
    ;
    extendingConfiguration =
        """
        requirement: {
          extends: 'gws:goog.Promise.X'
          allowlist: "testcode"
        }
        """;

    // respects the allowlist in the extending config and does not warn
    testNoWarning(
        """
        goog.Promise.all();
        """);
  }

  @Test
  public void testSimpleExtends_withAllowlistRegexp() {
    baseConfiguration =
        """
        requirement: {
          rule_id: "gws:goog.Promise.X"
          type: BANNED_NAME
          error_message: "Prefer using native Promise equivalents. See go/gws-js-conformance#goog-promise"

          value: "goog.Promise.all"
        }
        """;
    ;
    extendingConfiguration =
        """
        requirement: {
          extends: 'gws:goog.Promise.X'
          allowlist_regexp: "code$"
        }
        """;

    // respects the allowlist_regexp in the extending config and does not warn
    testNoWarning(
        """
        goog.Promise.all();
        """);
  }

  @Test
  public void testRequirementHasBothRuleIdAndExtends_reportsInvalidRequirementSpec() {
    allowSourcelessWarnings();
    baseConfiguration =
        """
        requirement: {
          rule_id: "gws:goog.Promise.X"
          type: BANNED_NAME
          error_message: "Prefer using native Promise equivalents. See go/gws-js-conformance#goog-promise"

          value: "goog.Promise.all"
        }
        """;
    ;
    // has both rule_id and extends fields set
    extendingConfiguration =
        """
        requirement: {
          rule_id: "extends:gws:goog.Promise.X"
          extends: 'gws:goog.Promise.X'
        }
        """;

    testError(
        """
        goog.Promise.all();
        """,
        CheckConformance.INVALID_REQUIREMENT_SPEC);
  }

  @Test
  public void testMissingBehaviorInLibraryLevelReportingMode() {
    // the conformance reporting mode is RESPECT_LIBRARY_LEVEL_BEHAVIOR_SPECIFIED_IN_CONFIG, i.e.
    // as if the CheckJS action is being run.
    reportingMode = ConformanceReportingMode.RESPECT_LIBRARY_LEVEL_BEHAVIOR_SPECIFIED_IN_CONFIG;
    allowSourcelessWarnings();
    baseConfiguration =
        """
        requirement: {
          rule_id: "gws:goog.Promise.X"
          type: BANNED_NAME
          error_message: "Prefer using native Promise equivalents. See go/gws-js-conformance#goog-promise"
          value: "goog.Promise.all"
        }
        """;
    testWarning(
        """
        goog.Promise.all();
        """,
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void
      testSimpleExtends_disallowsOverridingLibraryLevelBehaviorToADifferentValue_inLibraryLevelConformanceMode() {
    // the conformance reporting mode is RESPECT_LIBRARY_LEVEL_BEHAVIOR_SPECIFIED_IN_CONFIG, i.e.
    // as if the CheckJS action is being run.
    reportingMode = ConformanceReportingMode.RESPECT_LIBRARY_LEVEL_BEHAVIOR_SPECIFIED_IN_CONFIG;
    allowSourcelessWarnings();
    baseConfiguration =
        """
        library_level_non_allowlisted_conformance_violations_behavior: REPORT_AS_BUILD_ERROR
        requirement: {
          rule_id: "gws:goog.Promise.X"
          type: BANNED_NAME
          error_message: "Prefer using native Promise equivalents. See go/gws-js-conformance#goog-promise"

          value: "goog.Promise.all"
        }
        """;
    ;
    extendingConfiguration =
        """
        # tries to override the library-level behavior, not allowed
        library_level_non_allowlisted_conformance_violations_behavior: RECORD_ONLY
        requirement: {
          extends: 'gws:goog.Promise.X'
        }
        """;

    testError(
        """
        goog.Promise.all();
        """,
        CheckConformance.INVALID_REQUIREMENT_SPEC,
        """
        Invalid requirement. Reason: extending rule's config may not specify a different value of 'library_level_non_allowlisted_conformance_violations_behavior' than the base rule's config. Skipping all conformance checks.\nRequirement spec:\nextends: "gws:goog.Promise.X"
        """);
  }

  @Test
  public void testSimpleExtends_allowsOverridingLibraryLevelBehaviorWhenBaseRuleHasNoBehavior() {
    // the conformance reporting mode is RESPECT_LIBRARY_LEVEL_BEHAVIOR_SPECIFIED_IN_CONFIG, i.e.
    // as if the CheckJS action is being run.
    reportingMode = ConformanceReportingMode.RESPECT_LIBRARY_LEVEL_BEHAVIOR_SPECIFIED_IN_CONFIG;
    allowSourcelessWarnings();
    baseConfiguration =
        """
        requirement: {
          rule_id: "gws:goog.Promise.X"
          type: BANNED_NAME
          error_message: "Prefer using native Promise equivalents. See go/gws-js-conformance#goog-promise"

          value: "goog.Promise.all"
        }
        """;
    ;
    extendingConfiguration =
        """
        library_level_non_allowlisted_conformance_violations_behavior: REPORT_AS_BUILD_ERROR
        requirement: {
          extends: 'gws:goog.Promise.X'
        }
        """;

    testWarning(
        """
        goog.Promise.all();
        """,
        CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void testSimpleExtends_appliesLibraryLevelBehaviorThatIsSpecified() {
    reportingMode = ConformanceReportingMode.RESPECT_LIBRARY_LEVEL_BEHAVIOR_SPECIFIED_IN_CONFIG;
    allowSourcelessWarnings();
    baseConfiguration =
        """
        requirement: {
          rule_id: "gws:goog.Promise.X"
          type: BANNED_NAME
          error_message: "Prefer using native Promise equivalents. See go/gws-js-conformance#goog-promise"

          value: "goog.Promise.all"
        }
        """;
    ;
    extendingConfiguration =
        """
        library_level_non_allowlisted_conformance_violations_behavior: RECORD_ONLY
        requirement: {
          extends: 'gws:goog.Promise.X'
        }
        """;

    // respects the library-level behavior in the extending config and does not warn
    testNoWarning(
        """
        goog.Promise.all();
        """);
  }

  @Test
  public void
      testSimpleExtends_appliesLibraryLevelBehaviorThatIsSpecified_recordOnlyInBaseConfig() {
    reportingMode = ConformanceReportingMode.RESPECT_LIBRARY_LEVEL_BEHAVIOR_SPECIFIED_IN_CONFIG;
    allowSourcelessWarnings();
    baseConfiguration =
        """
        library_level_non_allowlisted_conformance_violations_behavior: RECORD_ONLY
        requirement: {
          rule_id: "gws:goog.Promise.X"
          type: BANNED_NAME
          error_message: "Prefer using native Promise equivalents. See go/gws-js-conformance#goog-promise"

          value: "goog.Promise.all"
        }
        """;
    ;
    extendingConfiguration =
        """
        requirement: {
          extends: 'gws:goog.Promise.X'
        }
        """;

    // respects the library-level behavior in the base config and does not warn
    testNoWarning(
        """
        goog.Promise.all();
        """);
  }

  @Test
  public void testCustomBanUnknownProp_mergeConfigWithValue() {
    configuration =
        config(
                rule("BanUnknownTypedClassPropsReferences"),
                "My message",
                "  rule_id: 'x'",
                "  allow_extending_value: true")
            + """
            requirement: {
              extends: 'x'
              value: 'F'
            }

            """;

    testNoWarning(
        """
        /** @typedef {?} */ var Unk;
        /** @constructor */ function F() { /** @type {?Unk} */ this.prop = null; };
        F.prototype.method = function() { alert(this.prop); }
        """);
  }

  @Test
  public void testCustomBanUnknownInterfaceProp1() {
    configuration =
        config(rule("BanUnknownTypedClassPropsReferences"), "My rule message", value("String"));

    String js =
        """
        /** @interface */ function I() {}
        I.prototype.method = function() {};
        I.prototype.gak;
        /** @param {!I} a */
        function f(a) {
          a.gak();
        }
        """;

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
        """
        /** @interface */ function I() {}
        I.prototype.method = function() {};
        /** @param {I} a */ function f(a) {
          a.method();
        }
        """);
  }

  @Test
  public void testCustomBanGlobalVars1() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanGlobalVars'
          error_message: 'BanGlobalVars Message'
        }
        """;

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
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanGlobalVars'
          error_message: 'BanGlobalVars Message'
        }
        """;

    testNoWarning(
        """
        goog.scope(function() {
          var x = {y: 'y'}
          var z = {
             [x.y]: 2
          }
        });
        """);

    // Test with let and const
    testNoWarning(
        """
        goog.scope(function() {
          let x = {y: 'y'}
          let z = {
             [x.y]: 2
          }
        });
        """);

    testNoWarning(
        """
        goog.scope(function() {
          const x = {y: 'y'}
          const z = {
             [x.y]: 2
          }
        });
        """);
  }

  @Test
  public void testCustomBanGlobalVarsWithDestructuring() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanGlobalVars'
          error_message: 'BanGlobalVars Message'
        }
        """;

    testWarning(
        "var [x] = [];",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanGlobalVars Message");

    testNoWarning("/** @externs */ var [x] = [];");
  }

  @Test
  public void testCustomBanGlobalVarsWithAllowlist() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanGlobalVars'
          value: 'foo'
          value: 'bar'
          error_message: 'BanGlobalVars Message'
        }
        """;

    testWarning(
        "var baz;", CheckConformance.CONFORMANCE_VIOLATION, "Violation: BanGlobalVars Message");

    testNoWarning("var foo; var bar;");
  }

  @Test
  public void testBanGlobalVarsInEs6Module() {
    // ES6 modules cannot be type checked yet
    disableTypeCheck();
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanGlobalVars'
          error_message: 'BanGlobalVars Message'
        }
        """;

    testNoWarning("export function foo() {}");
    testNoWarning("var s; export {x}");
    testNoWarning("export var s;");
  }

  @Test
  public void testCustomBanUnresolvedType() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanUnresolvedType'
          error_message: 'BanUnresolvedType Message'
        }
        """;

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
            """
            goog.forwardDeclare('Foo');
            goog.forwardDeclare('Bar');
            /** @param {?Foo|Bar} foobar */
            function f(foobar) {
              return foobar.m();
            }
            """),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage(
                """
                Violation: BanUnresolvedType Message
                Reference to type 'Foo|Bar' never resolved.
                """));

    testNoWarning(
        """
        /**
         *  @param {!Object<string, ?>} data
         */
        function foo(data) {
          data['bar'].baz();
        }
        """);
  }

  @Test
  public void testCustomStrictBanUnresolvedType() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$StrictBanUnresolvedType'
          error_message: 'StrictBanUnresolvedType Message'
        }
        """;

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
            """
            goog.forwardDeclare('Foo');
            goog.forwardDeclare('Bar');
            /**
             * @param {?Foo} foo
             * @param {?Bar} bar
             */
            function f(foo, bar) {
              return foo || bar;
            }
            """),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage(
                """
                Violation: StrictBanUnresolvedType Message
                Reference to type 'Foo' never resolved.
                """),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage(
                """
                Violation: StrictBanUnresolvedType Message
                Reference to type 'Bar' never resolved.
                """),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage(
                """
                Violation: StrictBanUnresolvedType Message
                Reference to type 'Foo' never resolved.
                """),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage(
                """
                Violation: StrictBanUnresolvedType Message
                Reference to type 'Bar|Foo' never resolved.
                """),
        warning(CheckConformance.CONFORMANCE_VIOLATION)
            .withMessage(
                """
                Violation: StrictBanUnresolvedType Message
                Reference to type 'Bar' never resolved.
                """));
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
        .addWhitelistEntry(
            RequirementScopeEntry.newBuilder().addPrefix("x2").addRegexp("m2").build());
    builder
        .addRequirementBuilder()
        .setExtends("a")
        .addWhitelist("y")
        .addWhitelistRegexp("n")
        .addWhitelistEntry(
            RequirementScopeEntry.newBuilder().addPrefix("a2").addRegexp("y2").build());
    CheckConformance checkConformance = new CheckConformance(compiler);
    List<Requirement> requirements =
        checkConformance.mergeRequirements(
            compiler,
            ImmutableList.of(builder.build()),
            /* isLibraryLevelConformanceReportingMode= */ false);
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
        .addAllowlistEntry(
            RequirementScopeEntry.newBuilder().addPrefix("x2").addRegexp("m2").build());
    builder
        .addRequirementBuilder()
        .setExtends("a")
        .addAllowlist("y")
        .addAllowlistRegexp("n")
        .addAllowlistEntry(
            RequirementScopeEntry.newBuilder().addPrefix("a2").addRegexp("y2").build());
    CheckConformance checkConformance = new CheckConformance(compiler);
    List<Requirement> requirements =
        checkConformance.mergeRequirements(
            compiler,
            ImmutableList.of(builder.build()),
            /* isLibraryLevelConformanceReportingMode= */ false);
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
    ConformanceConfig config = builder.build();
    CheckConformance checkConformance = new CheckConformance(compiler);
    List<Requirement> requirements =
        checkConformance.mergeRequirements(
            compiler,
            ImmutableList.of(config),
            /* isLibraryLevelConformanceReportingMode= */ false);
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
    ConformanceConfig config = builder.build();

    CheckConformance checkConformance = new CheckConformance(compiler);
    List<Requirement> requirements =
        checkConformance.mergeRequirements(
            compiler,
            ImmutableList.of(config),
            /* isLibraryLevelConformanceReportingMode= */ false);
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
        srcs(
            """
            /** @param {string|null} n */
            function f(n) { alert('prop' in n); }
            """),
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
        EXTERNS
            + """
            /** @fileoverview */
            /** @const */ var ns = {};
            /** @enum {number} */ ns.Type.State = {OPEN: 0};
            /** @typedef {{a:string}} */ ns.Type;
            """;

    final String code =
        """
        /** @return {void} n */
        function f() { alert(ns.Type.State.OPEN); }
        """;
    testNoWarning(externs(typedefExterns), srcs(code));
  }

  @Test
  public void testCustomBanNullDeref4() {
    configuration = config(rule("BanNullDeref"), "My rule message");

    testNoWarning(
        """
        /** @param {*} x */
        function f(x) {
          return x.toString();
        }
        """);
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
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateElement'
          error_message: 'BanCreateElement Message'
          value: 'script'
        }
        """;

    testWarning(
        "goog.dom.createElement('script');",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateElement Message");

    testWarning(
        "goog.dom.createDom('script', {});",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateElement Message");

    String externs =
        DEFAULT_EXTERNS
            + """
            /** @constructor */ function Document() {}
            /** @const {!Document} */ var document;
            /** @const */ goog.dom = {};
            /** @constructor */ goog.dom.DomHelper = function() {};
            """;

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

  private String getBannedNonLiteralArgsConfiguration() {
    return
"""
requirement: {
  type: CUSTOM
  java_class:'com.google.javascript.jscomp.ConformanceRules$BanNonLiteralArgsToGoogStringConstFrom'
  error_message: 'BanNonLiteralArgsToGoogStringConstFrom Message'
}
""";
  }

  @Test
  public void testBanNonLiteralArgsToGoogStringConstFrom_directCall() {
    configuration = getBannedNonLiteralArgsConfiguration();

    // test direct calls to goog.string.Const.from
    testNoWarning("goog.string.Const.from('foo');");
    testNoWarning("goog.string.Const.from(`foo`);");

    testWarning(
        "const foo = 42; goog.string.Const.from(foo);",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanNonLiteralArgsToGoogStringConstFrom Message");
    testWarning(
        "const foo = 42; goog.string.Const.from(`literal_with_interpolation_${foo}`);",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanNonLiteralArgsToGoogStringConstFrom Message");
  }

  @Test
  public void testBanNonLiteralArgsToGoogStringConstFrom_alias() {
    configuration = getBannedNonLiteralArgsConfiguration();

    // test aliases
    testNoWarning("const foo = goog.string.Const.from; foo('foo');");
    testWarning(
        "const foo = goog.string.Const.from; const bar = ''; foo(bar);",
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanNonLiteralArgsToGoogStringConstFrom Message");

    // test alias of alias
    testNoWarning("const foo = goog.string.Const.from; const bar = foo; bar('foo');");
    // this should be a warning, but it isn't.
    testNoWarning("const foo = goog.string.Const.from; const bar = foo; const baz = ''; bar(baz);");
  }

  @Test
  public void testBanNonLiteralArgsToGoogStringConstFrom_googRequire_noAlias() {
    configuration = getBannedNonLiteralArgsConfiguration();

    Externs externs =
        externs(
            DEFAULT_EXTERNS
                + """
                goog.provide('goog.string.Const');
                /** @constructor */ goog.string.Const = function() {};
                goog.string.Const.from = function(x) {};
                """);
    testNoWarning(
        externs,
        srcs(
            """
            goog.module('test')
            goog.require('goog.string.Const');
            goog.string.Const.from('');
            """));

    testWarning(
        externs,
        srcs(
            """
            goog.module('test')
            goog.require('goog.string.Const');
            const bar = '';
            goog.string.Const.from(bar);
            """),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanNonLiteralArgsToGoogStringConstFrom Message");
  }

  @Test
  public void testBanNonLiteralArgsToGoogStringConstFrom_googRequired_localAlias() {
    configuration = getBannedNonLiteralArgsConfiguration();

    Externs externs =
        externs(
            DEFAULT_EXTERNS
                + """
                goog.provide('goog.string.Const');
                /** @constructor */ goog.string.Const = function() {};
                goog.string.Const.from = function(x) {};
                """);
    testNoWarning(
        externs,
        srcs(
            """
            goog.module('test')
            const const1 = goog.require('goog.string.Const');
            const1.from('');
            """));

    testWarning(
        externs,
        srcs(
            """
            goog.module('test')
            const const1 = goog.require('goog.string.Const');
            const bar = '';
            const1.from(bar);
            """),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanNonLiteralArgsToGoogStringConstFrom Message");
  }

  @Test
  public void testBanNonLiteralArgsToGoogStringConstFrom_googRequired_destructuringImport() {
    configuration = getBannedNonLiteralArgsConfiguration();

    Externs externs =
        externs(
            DEFAULT_EXTERNS
                + """
                goog.provide('goog.string.Const');
                /** @constructor */ goog.string.Const = function() {};
                goog.string.Const.from = function(x) {};
                """);
    testNoWarning(
        externs,
        srcs(
            """
            goog.module('test')
            const {from} = goog.require('goog.string.Const');
            from('');
            """));
    testWarning(
        externs,
        srcs(
            """
            goog.module('test')
            const {from} = goog.require('goog.string.Const');
            const bar = '';
            from(bar);
            """),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanNonLiteralArgsToGoogStringConstFrom Message");
  }

  @Test
  public void testBanCreateDom() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateDom'
          error_message: 'BanCreateDom Message'
          value: 'iframe.src'
          value: 'div.class'
        }
        """;

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
        DEFAULT_EXTERNS
            + """
            /** @const */ goog.dom = {};
            /** @constructor */ goog.dom.DomHelper = function() {};
            """;

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
        """
        Possible violation: BanCreateDom Message
        The type information available for this expression is too loose to ensure conformance.
        """);

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
        """
        goog.dom.createDom(
        'iframe', /** @type {?string|!Array|undefined} */ (className));
        """);
    testNoWarning("goog.dom.createDom(tag, {});");
    testNoWarning(
        """
        /** @enum {string} */ var Classes = {A: ''};
        goog.dom.createDom('iframe', Classes.A);
        """);
  }

  @Test
  public void testBanCreateDomIgnoreLooseType() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateDom'
          error_message: 'BanCreateDom Message'
          report_loose_type_violations: false
          value: 'iframe.src'
        }
        """;

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
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateDom'
          error_message: 'BanCreateDom Message'
          value: 'div.class'
        }
        """;

    String externs =
        DEFAULT_EXTERNS
            + """
            /** @const */ goog.dom = {};
            /** @constructor @template T */ goog.dom.TagName = function() {};
            /** @type {!goog.dom.TagName<!HTMLDivElement>} */
            goog.dom.TagName.DIV = new goog.dom.TagName();
            /** @constructor */ function HTMLDivElement() {}

            """;

    testWarning(
        externs(externs),
        srcs(
            """
            function f(/** !goog.dom.TagName<!HTMLDivElement> */ div) {
              goog.dom.createDom(div, 'red');
            }
            """),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");

    testWarning(
        externs(externs),
        srcs(
            """
            const TagName = goog.dom.TagName;
            goog.dom.createDom(TagName.DIV, 'red');
            """),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");
  }

  @Test
  public void testBanCreateDomMultiType() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateDom'
          error_message: 'BanCreateDom Message'
          value: 'h2.class'
        }
        """;

    String externs =
        DEFAULT_EXTERNS
            + """
            /** @const */ goog.dom = {};
            /** @constructor @template T */ goog.dom.TagName = function() {}
            /** @constructor */ function HTMLHeadingElement() {}

            """;

    testWarning(
        externs(externs),
        srcs(
            """
            function f(/** !goog.dom.TagName<!HTMLHeadingElement> */ heading) {
              goog.dom.createDom(heading, 'red');
            }
            """),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanCreateDom Message");
  }

  @Test
  public void testBanCreateDomAnyTagName() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateDom'
          error_message: 'BanCreateDom Message'
          value: '*.innerHTML'
        }
        """;

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
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanCreateDom'
          error_message: 'BanCreateDom Message'
          value: 'script.textContent'
        }
        """;

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
        """
        requirement: {

          type: CUSTOM

          value: 'src'

          value: 'HREF'

          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanElementSetAttribute'

          report_loose_type_violations: false

          error_message: 'BanSetAttribute Message'

        }
        """;

    String externs =
        DEFAULT_EXTERNS
            + """
            /** @constructor */ function Element() {}
            /** @constructor @extends {Element} */ function HTMLScriptElement() {}

            """;

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
            """
            /** @param {string|null} attr */
            function foo(attr) { (new HTMLScriptElement)[attr] =  'xxx'; }
            """));

    testNoWarning(
        externs(externs),
        srcs(
            """
            const foo = 'safe';
            var bar = foo;
            (new HTMLScriptElement).setAttribute(bar, 'xxx');
            """));

    testNoWarning(externs(externs), srcs("(new HTMLScriptElement)['data-random'] = 'xxx';"));

    testNoWarning(
        externs(externs),
        srcs(
            """
            const foo = 'safe';
            const bar = foo;
            (new HTMLScriptElement).setAttribute(bar, 'xxx');
            """));
  }

  @Test
  public void testBanElementSetAttributeLoose() {
    configuration =
        """
        requirement: {
          type: CUSTOM
          value: 'src'
          value: 'HREF'
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanElementSetAttribute'
          error_message: 'BanSetAttribute Message'
          report_loose_type_violations: true
        }
        """;

    String externs =
        DEFAULT_EXTERNS
            + """
            /** @constructor */ function Element() {}
            /** @constructor @extends {Element} */ function HTMLScriptElement() {}
            """;

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
            """
            /** @param {string|null} attr */
            function foo(attr) { (new HTMLScriptElement)[attr] =  'xxx'; }
            """),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testWarning(
        externs(externs),
        srcs(
            """
            const foo = 'safe';
            var bar = foo;
            (new HTMLScriptElement).setAttribute(bar, 'xxx');
            """),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSetAttribute Message");

    testNoWarning(externs(externs), srcs("(new HTMLScriptElement)['data-random'] = 'xxx';"));

    testNoWarning(
        externs(externs),
        srcs(
            """
            const foo = 'safe';
            const bar = foo;
            (new HTMLScriptElement).setAttribute(bar, 'xxx');
            """));

    testNoWarning(
        externs(externs),
        srcs(
            """
            /** @const */
            var foo = 'safe';
            (new HTMLScriptElement).setAttribute(foo, 'xxx');
            """));

    testNoWarning(
        externs(externs),
        srcs(
            """
            goog.provide('test.Attribute');
            /** @const */
            test.Attribute.foo = 'safe';
            (new HTMLScriptElement).setAttribute(test.Attribute.foo, 'xxx');
            """));

    testNoWarning(
        externs(externs),
        srcs(
            """
            goog.provide('test.Attribute');

            /** @enum {string} */
            test.Attribute = {SRC: 'src', HREF: 'href', SAFE: 'safe'};
            """,
            """
            goog.module('test.setAttribute');

            const Attribute = goog.require('test.Attribute');

            const attr = Attribute.SAFE;
            (new HTMLScriptElement).setAttribute(attr, 'xxx');
            """));

    testNoWarning(
        externs(externs),
        srcs(
            """
            goog.provide('xid');
            goog.provide('xid.String');
            /** @enum {string} */ xid.String = {DO_NOT_USE: ''};
            /**
             * @param {string} id
             * @return {xid.String}
             */
            xid = function(id) {return /** @type {xid.String} */ (id);};
            const attr = xid('src');
            (new HTMLScriptElement).setAttribute(attr, 'xxx');
            (new HTMLScriptElement)[attr] = 'xxx';
            """));
  }

  @Test
  public void testBanSettingAttributes() {
    configuration =
        """
        requirement: {

          type: CUSTOM

          value: 'Element.prototype.attr'

          value: 'Foo.prototype.attrib'

          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanSettingAttributes'

          error_message: 'BanSettingAttributes Message'

        }
        """;

    String externs =
        DEFAULT_EXTERNS
            + """
            /** @constructor */ function Foo() {}
            /** @constructor */ function Bar() {}
            /** @constructor */ function Element() {}
            /** @constructor @extends {Element} */ function HTMLScriptElement() {}
            """;

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
            """
            const foo = 'safe';
            var bar = foo;
            (new HTMLScriptElement).attr(bar, 'xxx');
            """),
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanSettingAttributes Message");

    testNoWarning(
        externs(externs),
        srcs(
            """
            const foo = 'safe';
            const bar = foo;
            (new HTMLScriptElement).attr(bar, 'xxx');
            """));

    testNoWarning(
        externs(externs),
        srcs(
            """
            goog.provide('test.Attribute');

            /** @enum {string} */
            test.Attribute = {SRC: 'src', HREF: 'href', SAFE: 'safe'};
            """,
            """
            goog.module('test.attr');

            const Attribute = goog.require('test.Attribute');

            const attr = Attribute.SAFE;
            (new HTMLScriptElement).attr(attr, 'xxx');
            """));

    testNoWarning(
        externs(externs),
        srcs(
            """
            goog.provide('xid');
            goog.provide('xid.String');
            /** @enum {string} */ xid.String = {DO_NOT_USE: ''};
            /**
             * @param {string} id
             * @return {xid.String}
             */
            xid = function(id) {return /** @type {xid.String} */ (id);};
            const attr = xid('src');
            (new HTMLScriptElement).attr(attr, 'xxx');
            """));
  }

  @Test
  public void testBanExecCommand() {
    configuration =
        """
        requirement: {

          type: CUSTOM

          value: 'insertHTML'

          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanExecCommand'

          error_message: 'BanExecCommand message'

        }
        """;

    String externs = DEFAULT_EXTERNS + "/** @constructor */ function Document() {}";

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
        """
        requirement: {
          type: CUSTOM
          java_class: 'com.google.javascript.jscomp.ConformanceRules$BanStaticThis'
          error_message: 'BanStaticThis Message'
        }
        """;

    testWarning(
        """
        class Foo {
          static bar() {
            this;
          }
        }
        """,
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanStaticThis Message");
    testWarning(
        """
        class Foo {
          static bar() {
            this.buzz();
          }
          static buzz() {}
        }
        """,
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanStaticThis Message");
    testWarning(
        """
        class Foo {
          static bar() {
            let fn = () => this;
          }
        }
        """,
        CheckConformance.CONFORMANCE_VIOLATION,
        "Violation: BanStaticThis Message");

    testNoWarning("let fn = function() { this.buzz(); };");
    testNoWarning(
        """
        class Foo {
          bar() {
            this.buzz();
          }
          buzz() {}
        }
        """);
    testNoWarning(
        """
        class Foo {
          static buzz() {}
        }
        Foo.bar = function() {
          this.buzz();
        }
        """);
    testNoWarning(
        """
        class Foo {
          static bar() {
            let fn = function() { this; };
          }
        }
        """);
  }
}
