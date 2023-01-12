/*
 * Copyright 2006 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.InlineAndCollapseProperties.NAMESPACE_REDEFINED_WARNING;
import static com.google.javascript.jscomp.InlineAndCollapseProperties.PARTIAL_NAMESPACE_WARNING;
import static com.google.javascript.jscomp.InlineAndCollapseProperties.RECEIVER_AFFECTED_BY_COLLAPSE;
import static com.google.javascript.jscomp.deps.ModuleLoader.LOAD_WARNING;
import static org.junit.Assert.assertThrows;

import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link InlineAndCollapseProperties.CollapseProperties}. */
@RunWith(JUnit4.class)
public final class CollapsePropertiesTest extends CompilerTestCase {

  private static final String EXTERNS =
      lines(
          "var window;",
          "function alert(s) {}",
          "function parseInt(s) {}",
          "/** @constructor */ function String() {};",
          "var arguments");

  private PropertyCollapseLevel propertyCollapseLevel = PropertyCollapseLevel.ALL;

  public CollapsePropertiesTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return InlineAndCollapseProperties.builder(compiler)
        .setPropertyCollapseLevel(propertyCollapseLevel)
        .setChunkOutputType(ChunkOutputType.GLOBAL_NAMESPACE)
        .setHaveModulesBeenRewritten(false)
        .setModuleResolutionMode(ResolutionMode.BROWSER)
        .build();
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    disableCompareJsDoc();
    disableScriptFeatureValidation();
  }

  private void setupModuleExportsOnly() {
    enableProcessCommonJsModules();
    enableTranspile();
    propertyCollapseLevel = PropertyCollapseLevel.MODULE_EXPORT;
  }

  @Test
  public void doNotCollapseAStaticPropertyUsedInAGetter() {
    test(
        srcs(
            lines(
                "class C {",
                "  /** @type {number} */",
                "  static get p() { return this.p_; }",
                "}",
                // declaration and initialization of private static field
                "/** @private {number} */",
                "C.p_ = 1;",
                // reference via getter
                "alert(C.p);",
                "")),
        expected(
            lines(
                "class C {",
                "  /** @type {number} */",
                "  static get p() { return this.p_; }",
                "}",
                // TODO(b/130682799): C.p_ should not be collapsed
                "/** @private {number} */",
                "var C$p_ = 1;",
                "alert(C.p);",
                "")));
  }

  @Test
  public void doNotCollapseAStaticPropertyUsedInASetter() {
    test(
        srcs(
            lines(
                "class C {",
                "  /** @param {number} v */",
                "  static set p(v) { this.p_ = v; }",
                "}",
                // declaration and initialization of private static field
                "/** @private {number} */",
                "C.p_ = 0;",
                // changing private static field through setter property
                "C.p = 1;",
                "alert(C.p_);",
                "")),
        expected(
            lines(
                "class C {",
                "  /** @param {number} v */",
                "  static set p(v) { this.p_ = v; }",
                "}",
                // TODO(b/130682799): C.p_ should not be collapsed
                "/** @private {number} */",
                "var C$p_ = 0;",
                "C.p = 1;",
                "alert(C$p_);",
                "")));
    test(
        srcs(
            lines(
                "class C {",
                "  /** @param {number} v */",
                "  static set p(v) { this.p_ = v; }",
                "}",
                // declaration and initialization of private static field
                "/** @private {number} */",
                "C.p_ = 0;",
                // changing private static field through setter property
                "C.p = 1;",
                "alert?.(C?.p_);",
                "")),
        expected(
            lines(
                "class C {",
                "  /** @param {number} v */",
                "  static set p(v) { this.p_ = v; }",
                "}",
                // TODO(b/130682799): C.p_ should not be collapsed
                "/** @private {number} */",
                "var C$p_ = 0;",
                "C.p = 1;",
                "alert?.(C?.p_);",
                "")));
  }

  @Test
  public void doNotCollapseAStaticPropertyReadInAStaticMethod() {
    testWarning(
        lines(
            "class C {",
            "  /** @return {number} */",
            "  static getP() { return this.p_; }",
            "}",
            // declaration and initialization of private static field
            "/** @private {number} */",
            "C.p_ = 1;",
            // reference via method
            "alert(C.getP());",
            ""),
        // TODO(b/117437011): should recognize type of `this` in a static method
        RECEIVER_AFFECTED_BY_COLLAPSE);
  }

  @Test
  public void doNotCollapseAStaticPropertyAssignedInAStaticMethod() {
    testWarning(
        lines(
            "class C {",
            "  /** @param {number} v */",
            "  static setP(v) { this.p_ = v; }",
            "}",
            // declaration and initialization of private static field
            "/** @private {number} */",
            "C.p_ = 0;",
            // changing private static field through method
            "C.setP(1);",
            "alert(C.p_);",
            ""),
        // TODO(b/117437011): should recognize type of `this` in a static method
        RECEIVER_AFFECTED_BY_COLLAPSE);
  }

  @Test
  public void doNotCollapseAPropertyReadByAnObjectLiteralMethod() {
    test(
        srcs(
            lines(
                "const obj = {",
                "  /** @private {number} */",
                "  p_: 0,",
                "  /**",
                "   * @this {{p_: number}}",
                "   * @return {number}",
                "   */",
                "  getP() { return this.p_; }",
                "};",
                "alert(obj.getP());",
                "")),
        expected(
            lines(
                // TODO(b/130829946): Should not collapse obj.p_ and obj.getP
                "var obj$p_ = 0;",
                "var obj$getP = function() { return this.p_; };",
                "alert(obj$getP());",
                "")));
  }

  @Test
  public void doNotCollapseAPropertyAssignedInAnObjectLiteralMethod() {
    test(
        srcs(
            lines(
                "const obj = {",
                "  /** @private {number} */",
                "  p_: 0,",
                "  /**",
                "   * @this {{p_: number}}",
                "   * @param {number} v",
                "   */",
                "  setP(v) { this.p_ = v; }",
                "}",
                "obj.setP(1);",
                "alert(obj.p_);",
                "")),
        expected(
            lines(
                // TODO(b/130829946): Should not collapse obj.p_ and obj.getP
                "var obj$p_ = 0;",
                "var obj$setP = function(v) { this.p_ = v; };",
                "obj$setP(1);",
                "alert(obj$p_);",
                "")));
    testSame(
        lines(
            "const obj = {",
            "  /** @private {number} */",
            "  p_: 0,",
            "  /**",
            "   * @this {{p_: number}}",
            "   * @param {number} v",
            "   */",
            "  setP(v) { this.p_ = v; }",
            "}",
            "obj.setP?.(1);",
            "alert(obj?.p_);",
            ""));
  }

  @Test
  public void doNotCollapseAPropertyUsedInAnObjectLiteralGetter() {
    test(
        srcs(
            lines(
                "const obj = {",
                "  /** @private {number} */",
                "  p_: 0,",
                "  /** @type {number} */",
                "  get p() { return this.p_; }",
                "}",
                "alert(obj.p);",
                "")),
        expected(
            lines(
                // TODO(b/130829946): Should not collapse obj.p_
                "var obj$p_ = 0;",
                "const obj = {",
                "  /** @type {number} */",
                "  get p() { return this.p_; }",
                "}",
                "alert(obj.p);",
                "")));
    testSame(
        lines(
            "const obj = {",
            "  /** @private {number} */",
            "  p_: 0,",
            "  /** @type {number} */",
            "  get p() { return this?.p_; }",
            "}",
            "alert(obj?.p);",
            ""));
  }

  @Test
  public void doNotCollapseAPropertyUsedInAnObjectLiteralSetter() {
    test(
        srcs(
            lines(
                "const obj = {",
                "  /** @private {number} */",
                "  p_: 0,",
                "  /** @param {number} v */",
                "  set p(v) { this.p_ = v; }",
                "}",
                "obj.p = 1;",
                "alert(obj.p_);",
                "")),
        expected(
            lines(
                // TODO(b/130829946): Should not collapse obj.p_
                "var obj$p_ = 0;",
                "const obj = {",
                "  /** @param {number} v */",
                "  set p(v) { this.p_ = v; }",
                "}",
                "obj.p = 1;",
                "alert(obj$p_);",
                "")));
    testSame(
        lines(
            "const obj = {",
            "  /** @private {number} */",
            "  p_: 0,",
            "  /** @param {number} v */",
            "  set p(v) { this.p_ = v; }",
            "}",
            "obj.p = 1;",
            "alert?.(obj?.p_);",
            ""));
  }

  @Test
  public void testMultiLevelCollapse() {

    test(
        srcs("var a = {}; a.b = {}; a.b.c = {}; var d = 1; d = a.b.c;"),
        expected("var                   a$b$c = {}; var d = 1; d = a$b$c;"));
    test(
        srcs("var a = {}; a.b = {}; a.b.c = {}; var d = 1; d = a.b?.c;"),
        expected("var         a$b = {}; a$b.c = {}; var d = 1; d = a$b?.c;"));

    test(
        srcs("var a = {}; a.b = {}; /** @nocollapse */ a.b.c = {}; var d = 1; d = a.b.c;"),
        expected("var a$b = {}; /** @nocollapse */ a$b.c = {}; var d = 1; d = a$b.c;"));
  }

  @Test
  public void testDecrement() {
    test(srcs("var a = {}; a.b = 5; a.b--; a.b = 5"), expected("var a$b = 5; a$b--; a$b = 5"));
  }

  @Test
  public void testIncrement() {
    test(srcs("var a = {}; a.b = 5; a.b++; a.b = 5"), expected("var a$b = 5; a$b++; a$b = 5"));
  }

  @Test
  public void testObjLitDeclarationWithGet1() {
    testSame("var a = {get b(){}};");
  }

  @Test
  public void testObjLitDeclarationWithGet3() {
    test(
        srcs("var a = {b: {get c() { return 3; }}};"),
        expected("var a$b = {get c() { return 3; }};"));
  }

  @Test
  public void testObjLitDeclarationWithSet1() {
    testSame("var a = {set b(a){}};");
  }

  @Test
  public void testObjLitDeclarationWithSet3() {
    test(srcs("var a = {b: {set c(d) {}}};"), expected("var a$b = {set c(d) {}};"));
  }

  @Test
  public void testObjLitDeclarationWithUsedSetter() {
    testSame("var a = {set b(c) {}}; a.b = 4;");
  }

  @Test
  public void testObjLitDeclarationDoesntCollapsePropertiesOnGetter() {
    testSame(
        lines(
            "var a = {",
            "  get b() { return class {}; },",
            "  set b(c) {}",
            "};",
            "a.b = class {};",
            "a.b.c = 4;"));
  }

  @Test
  public void testObjLitDeclarationWithGetAndSet1() {
    test(
        srcs("var a = {b: {get c() { return 3; },set c(d) {}}};"),
        expected("var a$b = {get c() { return 3; },set c(d) {}};"));
  }

  @Test
  public void testObjLitAssignmentDepth1() {

    test(
        srcs("var a = {b: {}, c: {}}; var d = 1; var e = 1; d = a.b; e = a.c"),
        expected("var a$b = {}; var a$c = {}; var d = 1; var e = 1; d = a$b; e = a$c"));
    testSame("var a = {b: {}, c: {}}; var d = 1; var e = 1; d = a?.b; e = a?.c");

    test(
        srcs(
            lines(
                "var a = {b: {}, /** @nocollapse */ c: {}};",
                "var d = 1;",
                "d = a.b;",
                "var e = 1;",
                "e = a.c;")),
        expected(
            lines(
                "var a$b = {};",
                "var a = { /** @nocollapse */c: {}};",
                "var d = 1;",
                "d = a$b;",
                "var e = 1;",
                "e = a.c;")));
    testSame("var a = {b: {}, /** @nocollapse */ c: {}}; var d = 1; d = a?.b; var e = 1; e = a?.c");
  }

  @Test
  public void testObjLitAssignmentDepth2() {

    test(
        srcs("var a = {}; a.b = {c: {}, d: {}}; var e = 1; e = a.b.c; var f = 1; f = a.b.d"),
        expected("var a$b$c = {}; var a$b$d = {}; var e = 1; e = a$b$c; var f = 1; f = a$b$d;"));
    test(
        srcs("var a = {}; a.b = {c: {}, d: {}}; var e = 1; e = a.b?.c; var f = 1; f = a.b?.d"),
        expected(
            "var         a$b = {c: {}, d: {}}; var e = 1; e = a$b?.c; var f = 1; f = a$b?.d;"));

    test(
        srcs(
            lines(
                "var a = {}; a.b = {c: {}, /** @nocollapse */ d: {}}; var e = 1; e = a.b.c;",
                "var f = 1; f = a.b.d")),
        expected(
            lines(
                "var a$b$c = {}; var a$b = { /** @nocollapse */ d: {}}; var e = 1; e = a$b$c;",
                "var f = 1; f = a$b.d;")));
    test(
        srcs(
            lines(
                "var a = {}; a.b = {c: {}, /** @nocollapse */ d: {}}; var e = 1; e = a.b?.c;",
                "var f = 1; f = a.b?.d")),
        expected(
            lines(
                "var         a$b = {c: {}, /** @nocollapse */ d: {}}; var e = 1; e = a$b?.c;",
                "var f = 1; f = a$b?.d;")));
  }

  @Test
  public void testGlobalObjectDeclaredToPreserveItsPreviousValue1() {
    test(srcs("var a = a ? a : {}; a.c = 1;"), expected("var a = a ? a : {}; var a$c = 1;"));

    test(
        srcs("var a = a ? a : {}; /** @nocollapse */ a.c = 1;"),
        expected("var a = a ? a : {}; /** @nocollapse */ a.c = 1;"));
  }

  @Test
  public void testGlobalObjectDeclaredToPreserveItsPreviousValue2() {
    test(srcs("var a = a || {}; a.c = 1;"), expected("var a = a || {}; var a$c = 1;"));

    testSame("var a = a || {}; /** @nocollapse */ a.c = 1;");
  }

  @Test
  public void testGlobalObjectDeclaredToPreserveItsPreviousValue3() {
    test(
        srcs("var a = a || {get b() {}}; a.c = 1;"),
        expected("var a = a || {get b() {}}; var a$c = 1;"));

    testSame("var a = a || {get b() {}}; /** @nocollapse */ a.c = 1;");
  }

  @Test
  public void testGlobalObjectNameInBooleanExpressionDepth1_1() {
    test(
        srcs("var a = {b: 0}; a.c = 1; if (a) x();"),
        expected("var a$b = 0; var a = {}; var a$c = 1; if (a) x();"));

    test(
        srcs("var a = {/** @nocollapse */ b: 0}; a.c = 1; if (a) x();"),
        expected("var a = {/** @nocollapse */ b: 0}; var a$c = 1; if (a) x();"));

    test(
        srcs("var a = {b: 0}; /** @nocollapse */ a.c = 1; if (a) x();"),
        expected("var a$b = 0; var a = {}; /** @nocollapse */ a.c = 1; if (a) x();"));
  }

  @Test
  public void testGlobalObjectNameInBooleanExpressionDepth1_2() {

    test(
        srcs("var a = {b: 0}; a.c = 1; if (!(a && a.c)) x();"),
        expected("var a$b = 0; var a = {}; var a$c = 1; if (!(a && a$c)) x();"));
    testSame("var a = {b: 0}; a.c = 1; if (!(a && a?.c)) x?.();");

    testSame("var a = {/** @nocollapse */ b: 0}; a.c = 1; if (!(a && a?.c)) x?.();");

    test(
        srcs("var a = {b: 0}; /** @nocollapse */ a.c = 1; if (!(a && a.c)) x();"),
        expected("var a$b = 0; var a = {}; /** @nocollapse */ a.c = 1; if (!(a && a.c)) x();"));
    testSame("var a = {b: 0}; /** @nocollapse */ a.c = 1; if (!(a && a?.c)) x?.();");
  }

  @Test
  public void testGlobalObjectNameInBooleanExpressionDepth1_3() {

    test(
        srcs("var a = {b: 0}; a.c = 1; while (a || a.c) x();"),
        expected("var a$b = 0; var a = {}; var a$c = 1; while (a || a$c) x();"));
    testSame("var a = {b: 0}; a.c = 1; while (a || a?.c) x?.();");

    test(
        srcs("var a = {/** @nocollapse */ b: 0}; a.c = 1; while (a || a.c) x();"),
        expected("var a = {/** @nocollapse */ b: 0}; var a$c = 1; while (a || a$c) x();"));
    testSame("var a = {/** @nocollapse */ b: 0}; a.c = 1; while (a || a?.c) x?.();");

    test(
        srcs("var a = {b: 0}; /** @nocollapse */ a.c = 1; while (a || a.c) x();"),
        expected("var a$b = 0; var a = {}; /** @nocollapse */ a.c = 1; while (a || a.c) x();"));
    testSame("var a = {b: 0}; /** @nocollapse */ a.c = 1; while (a || a?.c) x?.();");
  }

  @Test
  public void testGlobalObjectNameInBooleanExpressionDepth1_4() {
    testSame("var a = {}; a.c = 1; var d = a || {}; a.c;");

    testSame("var a = {}; /** @nocollapse */ a.c = 1; var d = a || {}; a.c;");
  }

  @Test
  public void testGlobalObjectNameInBooleanExpressionDepth1_5() {
    testSame("var a = {}; a.c = 1; var d = a.c || a; a.c;");

    testSame("var a = {}; /** @nocollapse */ a.c = 1; var d = a.c || a; a.c;");
  }

  @Test
  public void testGlobalObjectNameInBooleanExpressionDepth1_6() {
    test(
        srcs("var a = {b: 0}; a.c = 1; var d = !(a.c || a); a.c;"),
        expected("var a$b = 0; var a = {}; var a$c = 1; var d = !(a$c || a); a$c;"));

    test(
        srcs("var a = {/** @nocollapse */ b: 0}; a.c = 1; var d = !(a.c || a); a.c;"),
        expected("var a = {/** @nocollapse */ b: 0}; var a$c = 1; var d = !(a$c || a); a$c;"));

    test(
        srcs("var a = {b: 0}; /** @nocollapse */ a.c = 1; var d = !(a.c || a); a.c;"),
        expected("var a$b = 0; var a = {}; /** @nocollapse */ a.c = 1; var d = !(a.c || a); a.c;"));
  }

  @Test
  public void testGlobalObjectNameInBooleanExpressionDepth2() {

    test(
        srcs("var a = {b: {}}; a.b.c = 1; if (a.b) x(a.b.c);"),
        expected("var a$b = {}; var a$b$c = 1; if (a$b) x(a$b$c);"));
    testSame("var a = {b: {}}; a.b.c = 1; if (a?.b) x?.(a.b?.c);");

    testSame("var a = {/** @nocollapse */ b: {}}; a.b.c = 1; if (a.b) x(a.b.c);");

    test(
        srcs("var a = {b: {}}; /** @nocollapse */ a.b.c = 1; if (a.b) x(a.b.c);"),
        expected("var a$b = {}; /** @nocollapse */ a$b.c = 1; if (a$b) x(a$b.c);"));
    testSame("var a = {b: {}}; /** @nocollapse */ a.b.c = 1; if (a?.b) x?.(a.b?.c);");
  }

  @Test
  public void testGlobalObjectNameInBooleanExpressionDepth3() {
    // TODO(user): Make CollapseProperties even more aggressive so that
    // a$b.z gets collapsed. Right now, it doesn't get collapsed because the
    // expression (a.b && a.b.c) could return a.b. But since it returns a.b iff
    // a.b *is* safely collapsible, the Boolean logic should be smart enough to
    // only consider the right side of the && as aliasing.
    test(
        lines(
            "var a = {}; a.b = {}; /** @constructor */ a.b.c = function(){};",
            " a.b.z = 1; var d = a.b && a.b.c;"),
        lines(
            "var a$b = {}; /** @constructor */ a$b.c = function(){};",
            " a$b.z = 1; var d = a$b && a$b.c;"),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testGlobalFunctionNameInBooleanExpressionDepth1() {

    test(
        srcs("function a() {}     a.c = 1; if (a) x(a.c);"),
        expected("function a() {} var a$c = 1; if (a) x(a$c);"));
    testSame("function a() {} a.c = 1; if (a) x?.(a?.c);");

    test(
        srcs("function a() {} /** @nocollapse */ a.c = 1; if (a) x(a.c);"),
        expected("function a() {} /** @nocollapse */ a.c = 1; if (a) x(a.c);"));
  }

  @Test
  public void testGlobalFunctionNameInBooleanExpressionDepth2() {

    test(
        srcs("var a = {b: function(){}}; a.b.c = 1; if (a.b) x(a.b.c);"),
        expected("var a$b = function(){}; var a$b$c = 1; if (a$b) x(a$b$c);"));
    testSame("var a = {b: function(){}}; a.b.c = 1; if (a?.b) x?.(a.b?.c);");

    testSame(
        lines(
            "var a = {/** @nocollapse */ b: function(){}}; a.b.c = 1; ", //
            "if (a.b) x(a.b.c);"));

    test(
        srcs(
            lines(
                "var a = {b: function(){}}; /** @nocollapse */ a.b.c = 1; ", //
                "if (a.b) x(a.b.c);")),
        expected("var a$b = function(){}; /** @nocollapse */ a$b.c = 1; if (a$b) x(a$b.c);"));
    testSame(
        lines(
            "var a = {b: function(){}};", //
            "/** @nocollapse */ a.b.c = 1;",
            "if (a?.b) x?.(a.b?.c);"));
  }

  @Test
  public void testDontCollapseObjectLiteralVarDeclarationInsideLoop() {
    // See https://github.com/google/closure-compiler/issues/3050
    // Another solution to the issue would be to explicitly initialize obj.val to undefined at the
    // start of the loop, but that requires some more refactoring of CollapseProperties.
    testSame(
        lines(
            "for (var i = 0; i < 2; i++) {",
            "  var obj = {};",
            "  if (i == 0) {",
            "    obj.val = 1;",
            "  }",
            "  alert(obj.val);",
            "}"));
  }

  @Test
  public void testDontCollapseObjectLiteralPropertyDeclarationInsideLoop() {
    // we can collapse `obj.x` but not `obj.x.val`
    test(
        srcs(
            lines(
                "var obj = {};",
                "for (var i = 0; i < 2; i++) {",
                "  obj.x = {};",
                "  if (i == 0) {",
                "    obj.x.val = 1;",
                "  }",
                "  alert(obj.x.val);",
                "}")),
        expected(
            lines(
                "for (var i = 0; i < 2; i++) {",
                "  var obj$x = {};",
                "  if (i == 0) {",
                "    obj$x.val = 1;",
                "  }",
                "  alert(obj$x.val);",
                "}")));
  }

  @Test
  public void testDontCollapseConstructorDeclarationInsideLoop() {
    testSame(
        lines(
            "for (var i = 0; i < 2; i++) {",
            "  /** @constructor */",
            "  var Foo = function () {}",
            "  if (i == 0) {",
            "    Foo.val = 1;",
            "  }",
            "  alert(Foo.val);",
            "}"));
  }

  @Test
  public void testDoCollapsePropertiesDeclaredInsideLoop() {
    // It's okay that this property is declared inside a loop as long as the object it's on is not.
    test(
        srcs(
            lines(
                "var obj = {};",
                "for (var i = 0; i < 2; i++) {",
                "  if (i == 0) {",
                "    obj.val = 1;",
                "  }",
                "  alert(obj.val);",
                "}")),
        expected(
            lines(
                "for (var i = 0; i < 2; i++) {",
                "  if (i == 0) {",
                "    var obj$val = 1;",
                "  }",
                "  alert(obj$val);",
                "}")));
    testSame(
        lines(
            "var obj = {};",
            "for (var i = 0; i < 2; i++) {",
            "  if (i == 0) {",
            "    obj.val = 1;",
            "  }",
            "  alert?.(obj?.val);",
            "}"));
  }

  @Test
  public void testDontCollapseAliasedNamespace_1() {
    testSame("var a = {b: 0}; f(a); a.b;");
  }

  @Test
  public void testDontCollapseAliasedNamespace_2() {
    testSame("var a = {b: 0}; new f(a); a.b;");
  }

  @Test
  public void testAliasCreatedForObjectDepth2_1() {
    test(
        srcs("var a = {}; a.b = {c: 0}; var d = 1; d = a.b; a.b.c == d.c;"),
        expected("var a$b = {c: 0}; var d = 1; d = a$b; a$b.c == d.c;"));
    testSame("var a = {}; a.b = {c: 0}; var d = 1; d = a?.b; a.b?.c == d?.c;");

    test(
        srcs(
            lines(
                "var a = {}; /** @nocollapse */ a.b = {c: 0}; var d = 1; d = a.b; ", //
                "a.b.c == d.c;")),
        expected("var a = {}; /** @nocollapse */ a.b = {c: 0}; var d = 1; d = a.b; a.b.c == d.c;"));
  }

  @Test
  public void testAliasCreatedForObjectDepth2_2() {
    test(
        srcs("var a = {}; a.b = {c: 0}; for (var p in a.b) { e(a.b[p]); }"),
        expected("var a$b = {c: 0}; for (var p in a$b) { e(a$b[p]); }"));
    testSame("var a = {}; a.b = {c: 0}; for (var p in a?.b) { e?.(a.b?.[p]); }");
  }

  @Test
  public void testEnumDepth1() {
    test(srcs("/** @enum */ var a = {b: 0, c: 1};"), expected("var a$b = 0; var a$c = 1;"));

    test(
        srcs("/** @enum */ var a = { /** @nocollapse */ b: 0, c: 1};"),
        expected("var a$c = 1; /** @enum */ var a = { /** @nocollapse */ b: 0};"));
  }

  @Test
  public void testEnumDepth2() {
    test(
        srcs("var a = {}; /** @enum */ a.b = {c: 0, d: 1};"),
        expected("var a$b$c = 0; var a$b$d = 1;"));

    testSame("var a = {}; /** @nocollapse @enum */ a.b = {c: 0, d: 1};");
  }

  @Test
  public void testAliasCreatedForEnumDepth1_1() {
    // An enum's values are always collapsed, even if the enum object is
    // referenced in a such a way that an alias is created for it.
    // Unless an enum property has @nocollapse
    test(
        srcs("/** @enum */ var a = {b: 0}; var c = 1; c = a; c.b = 1; a.b != c.b;"),
        expected(
            "var a$b = 0; /** @enum */ var a = {b: a$b}; var c = 1; c = a; c.b = 1; a$b != c.b;"));
    test(
        srcs(
            lines(
                "/** @enum */ var a = {b:   0};",
                "var c = 1;",
                "c = a;",
                "c.b = 1;",
                "a?.b != c?.b;")),
        expected(
            lines(
                "var a$b = 0;",
                "/** @enum */ var a = {b: a$b};",
                "var c = 1;",
                "c = a;",
                "c.b = 1;",
                "a?.b != c?.b")));

    test(
        srcs(
            lines(
                "/** @enum */ var a = { /** @nocollapse */ b: 0};",
                "var c = 1;",
                "c = a;",
                "c.b = 1;",
                "a.b == c.b")),
        expected(
            lines(
                "/** @enum */ var a = { /** @nocollapse */ b: 0};",
                "var c = 1;",
                "c = a;",
                "c.b = 1;",
                "a.b == c.b")));
  }

  @Test
  public void testAliasCreatedForEnumDepth1_2() {
    test(
        srcs("/** @enum */ var a = {b: 0}; f(a); a.b;"),
        expected("var a$b = 0; /** @enum */ var a = {b: a$b}; f(a); a$b;"));
  }

  @Test
  public void testAliasCreatedForEnumDepth1_3() {
    test(
        srcs("/** @enum */ var a = {b: 0}; new f(a); a.b;"),
        expected("var a$b = 0; /** @enum */ var a = {b: a$b}; new f(a); a$b;"));
  }

  @Test
  public void testAliasCreatedForEnumDepth1_4() {
    test(
        srcs("/** @enum */ var a = {b: 0}; for (var p in a) { f(a[p]); }"),
        expected("var a$b = 0; /** @enum */ var a = {b: a$b}; for (var p in a) { f(a[p]); }"));
  }

  @Test
  public void testAliasCreatedForEnumDepth2_1() {
    test(
        srcs(
            lines(
                "var a = {}; /** @enum */ a.b = {c: 0};", //
                "var d = 1; d = a.b; d.c = 1; a.b.c != d.c;")),
        expected(
            lines(
                "var a$b$c = 0; /** @enum */ var a$b = {c: a$b$c};",
                "var d = 1; d = a$b; d.c = 1; a$b$c != d.c;")));
    testSame(
        srcs(
            lines(
                "var a = {}; /** @enum */ a.b = {c: 0};", //
                "var d = 1; d = a?.b; d.c = 1; a.b?.c != d?.c;")),
        warning(PARTIAL_NAMESPACE_WARNING));

    testSame(
        lines(
            "var a = {}; /** @nocollapse @enum */ a.b = {c: 0};",
            "var d = 1; d = a.b; d.c = 1; a.b.c == d.c;"));
    testSame(
        srcs(
            lines(
                "var a = {}; /** @nocollapse @enum */ a.b = {c: 0};",
                "var d = 1; d = a?.b; d.c = 1; a.b?.c == d?.c;")),
        warning(PARTIAL_NAMESPACE_WARNING));

    test(
        srcs(
            lines(
                "var a = {}; /** @enum */ a.b = {/** @nocollapse */ c: 0};",
                "var d = 1; d = a.b; d.c = 1; a.b.c == d.c;")),
        expected(
            lines(
                "/** @enum */ var a$b = { /** @nocollapse */ c: 0};",
                "var d = 1; d = a$b; d.c = 1; a$b.c == d.c;")));
    testSame(
        srcs(
            lines(
                "var a = {}; /** @enum */ a.b = {/** @nocollapse */ c: 0};",
                "var d = 1; d = a?.b; d.c = 1; a.b?.c == d?.c;")),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testAliasCreatedForEnumDepth2_2() {
    test(
        srcs(
            lines(
                "var a = {}; /** @enum */ a.b = {c: 0};", //
                "for (var p in a.b) { f(a.b[p]); }")),
        expected(
            lines(
                "var a$b$c = 0; /** @enum */ var a$b = {c: a$b$c};",
                "for (var p in a$b) { f(a$b[p]); }")));
    testSame(
        srcs(
            lines(
                "var a = {}; /** @enum */ a.b = {c: 0};", //
                "for (var p in a?.b) { f?.(a.b?.[p]); }")),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testDontCollapseEnumWhenParentNamespaceAliased() {
    testSame(
        lines(
            "var a = {}; var d = 1; d = a; /** @enum */ a.b = {c: 0};",
            "for (var p in a.b) { f(a.b[p]); }"),
        PARTIAL_NAMESPACE_WARNING);
  }

  @Test
  public void testAliasCreatedForEnumOfObjects() {
    test(
        srcs(
            lines(
                "var a = {};",
                "/** @enum {Object} */ a.b = {c: {d: 1}};",
                "a.b.c;",
                "searchEnum(a.b);")),
        expected(
            lines(
                "var a$b$c = {d: 1};",
                "/** @enum {Object} */ var a$b = {c: a$b$c};",
                "a$b$c;",
                "searchEnum(a$b)")));
    testSame(
        srcs(
            lines(
                "var a = {};", //
                "/** @enum {Object} */",
                "a.b = {c: {d: 1}};",
                "a.b?.c;",
                "searchEnum(a?.b);")),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testAliasCreatedForEnumOfObjects2() {
    test(
        srcs(
            lines(
                "var a = {}; ",
                "/** @enum {Object} */ a.b = {c: {d: 1}}; a.b.c.d;",
                "searchEnum(a.b);")),
        expected(
            lines(
                "var a$b$c = {d: 1}; /** @enum {Object} */ var a$b = {c: a$b$c}; a$b$c.d; ",
                "searchEnum(a$b)")));
    testSame(
        srcs(
            lines(
                "var a = {}; ",
                "/** @enum {Object} */ a.b = {c: {d: 1}};",
                "a.b.c?.d;",
                "searchEnum?.(a?.b);")),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testAliasCreatedForPropertyOfEnumOfObjects() {
    test(
        srcs(
            lines(
                "var a = {}; ",
                "/** @enum {Object} */ a.b = {c: {d: 1}}; a.b.c;",
                "searchEnum(a.b.c);")),
        expected("var a$b$c = {d: 1}; a$b$c; searchEnum(a$b$c);"));
    test(
        srcs(
            lines(
                "var a = {}; ",
                "/** @enum {Object} */ a.b = {c: {d: 1}}; a.b?.c;",
                "searchEnum?.(a.b?.c);")),
        expected(
            lines(
                "var a$b$c = {d:1};",
                "/** @enum {!Object} */ var a$b = {c:a$b$c};",
                "a$b?.c;",
                "searchEnum?.(a$b?.c);")));
  }

  @Test
  public void testAliasCreatedForPropertyOfEnumOfObjects2() {
    test(
        srcs(
            lines(
                "var a = {}; ",
                "/** @enum {Object} */ a.b = {c: {d: 1}}; a.b.c.d;",
                "searchEnum(a.b.c);")),
        expected("var a$b$c = {d: 1}; a$b$c.d; searchEnum(a$b$c);"));
    test(
        srcs(
            lines(
                "var a = {}; ",
                "/** @enum {Object} */",
                "a.b = {c: {d: 1}};",
                "a.b.c?.d;",
                "searchEnum?.(a.b?.c);")),
        expected(
            lines(
                "var a$b$c = {d:1};",
                "/** @enum {!Object} */",
                "var a$b = {c:a$b$c};",
                "a$b$c?.d;",
                "searchEnum?.(a$b?.c);")));
  }

  @Test
  public void testMisusedEnumTag() {
    testSame("var a = {}; var d = 1; d = a; a.b = function() {}; /** @enum */ a.b.c = 0; a.b.c;");
  }

  @Test
  public void testAliasCreatedForFunctionDepth1_1() {
    testSame("var a = function(){}; a.b = 1; var c = 1; c = a; c.b = 2; a.b != c.b;");
  }

  @Test
  public void testAliasCreatedForFunctionDepth1_2() {
    testSame("var a = function(){}; a.b = 1; f(a); a.b;");
  }

  @Test
  public void testAliasCreatedForCtorDepth1_2() {
    test(
        srcs("/** @constructor */ var a = function(){}; a.b = 1; f(a); a.b;"),
        expected("/** @constructor */ var a = function(){}; var a$b = 1; f(a); a$b;"));

    testSame("/** @constructor */ var a = function(){}; /** @nocollapse */ a.b = 1; f(a); a.b;");
  }

  @Test
  public void testAliasCreatedForFunctionDepth1_3() {
    testSame("var a = function(){}; a.b = 1; new f(a); a.b;");
  }

  @Test
  public void testAliasCreatedForCtorDepth1_3() {
    test(
        srcs("/** @constructor */ var a = function(){}; a.b = 1; new f(a); a.b;"),
        expected("/** @constructor */ var a = function(){}; var a$b = 1; new f(a); a$b;"));

    testSame(
        lines(
            "/** @constructor */ var a = function(){};",
            "/** @nocollapse */ a.b = 1; new f(a); a.b;"));
  }

  @Test
  public void testAliasCreatedForClassDepth1_2() {
    testSame(
        "var a = {}; /** @constructor */ a.b = function(){}; f(a); a.b;",
        PARTIAL_NAMESPACE_WARNING);
  }

  @Test
  public void testAliasCreatedForClassDepth1_3() {
    testSame(
        "var a = {}; /** @constructor */ a.b = function(){}; new f(a); a.b;",
        PARTIAL_NAMESPACE_WARNING);
  }

  @Test
  public void testAliasCreatedForClassDepth2_1() {
    test(
        srcs(
            lines(
                "var a = {};", //
                "a.b = {};",
                "/** @constructor */",
                "a.b.c = function(){};",
                "a.b.c;")),
        expected(
            lines(
                "/** @constructor */", //
                "var a$b$c = function(){};",
                "a$b$c;")));

    test(
        srcs(
            lines(
                "var a = {};", //
                "a.b = {};",
                " /** @constructor @nocollapse */",
                " a.b.c = function(){}; ",
                "a.b.c;")),
        expected(
            lines(
                "var a$b = {};", //
                "/** @constructor @nocollapse */",
                "a$b.c = function(){};",
                "a$b.c;")));
  }

  @Test
  public void testDontCollapseClassIfParentNamespaceAliased() {
    test(
        lines(
            "var a = {};",
            "a.b = {};",
            "/** @constructor */",
            "a.b.c = function(){};",
            "var d = 1;",
            "d = a.b;",
            "a.b.c == d.c;"),
        lines(
            "var a$b = {}; ",
            "/** @constructor */",
            "a$b.c = function(){};",
            "var d = 1;",
            "d = a$b;",
            "a$b.c == d.c;"),
        warning(PARTIAL_NAMESPACE_WARNING));
    testSame(
        srcs(
            lines(
                "var a = {};",
                "a.b = {};",
                "/** @constructor */",
                "a.b.c = function(){};",
                "var d = 1;",
                "d = a?.b;",
                "a.b?.c == d?.c;")),
        // 2 warnings (for `a` and for `a.b`)
        warning(PARTIAL_NAMESPACE_WARNING),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testDontCollapseIfParentNamespaceAliased2() {
    test(
        "var a = {}; a.b = {}; /** @constructor */ a.b.c = function(){}; f(a.b); a.b.c;",
        "var a$b = {}; /** @constructor */ a$b.c = function(){}; f(a$b); a$b.c;",
        warning(PARTIAL_NAMESPACE_WARNING));
    testSame(
        srcs("var a = {}; a.b = {}; /** @constructor */ a.b.c = function(){}; f?.(a?.b); a.b?.c;"),
        // 2 warnings (`a` and `a.b`)
        warning(PARTIAL_NAMESPACE_WARNING),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testDontCollapseIfParentNamespaceAliased3() {
    test(
        "var a = {}; a.b = {}; /** @constructor */ a.b.c = function(){}; new f(a.b); a.b.c;",
        "var a$b = {}; /** @constructor */ a$b.c = function(){}; new f(a$b); a$b.c;",
        warning(PARTIAL_NAMESPACE_WARNING));
    testSame(
        srcs(
            lines(
                "var a = {};",
                "a.b = {};",
                "/** @constructor */",
                "a.b.c = function(){};",
                "new f(a?.b); a.b?.c;")),
        // 2 warnings (`a` and `a.b`)
        warning(PARTIAL_NAMESPACE_WARNING),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testAliasCreatedForClassProperty() {
    test(
        srcs(
            lines(
                "var a = {};",
                "/** @constructor */ a.b = function(){};",
                "a.b.c = {d:3};",
                "new f(a.b.c);",
                "a.b.c.d")),
        expected(
            lines(
                "/** @constructor */ var a$b = function(){};",
                "var a$b$c = {d:3};",
                "new f(a$b$c);",
                "a$b$c.d")));

    test(
        srcs(
            lines(
                "var a = {};", //
                "/** @constructor */",
                "a.b = function(){};",
                "a.b.c = {d:3};",
                "new f(a.b?.c);",
                "a.b.c?.d;")),
        expected(
            lines(
                "/** @constructor */",
                "var a$b = function(){};",
                "var a$b$c = {d:3};",
                // TODO(b/148237949): Collapsing breaks this reference
                "new f(a$b?.c);",
                "a$b$c?.d;")));

    testSame(
        lines(
            "var a = {}; /** @constructor @nocollapse */ a.b = function(){};",
            "a.b.c = {d: 3}; new f(a.b.c); a.b.c.d;"));

    test(
        srcs(
            lines(
                "var a = {};",
                "/** @constructor */",
                "a.b = function(){};",
                "/** @nocollapse */",
                " a.b.c = {d: 3};",
                " new f(a.b.c);",
                " a.b.c.d;")),
        expected(
            lines(
                "/** @constructor */",
                " var a$b = function(){};",
                " /** @nocollapse */",
                " a$b.c = {d:3};",
                " new f(a$b.c); ",
                "a$b.c.d;")));
  }

  @Test
  public void testNestedObjLit() {

    test(
        srcs("var a = {}; a.b = {f: 0, c: {d: 1}}; var e = 1; e = a.b.c.d"),
        expected("var a$b$f = 0; var a$b$c$d = 1; var e = 1; e = a$b$c$d;"));
    test(
        srcs("var a = {}; a.b = {f : 0,         c : {d: 1}}; var e = 1; e = a.b.c?.d"),
        expected("var            a$b$f = 0; var a$b$c = {d: 1} ; var e = 1; e = a$b$c?.d;"));

    test(
        srcs("var a = {}; a.b = {f: 0, /** @nocollapse */ c: {d: 1}}; var e = 1; e = a.b.c.d"),
        expected(
            "var a$b$f = 0; var a$b ={ /** @nocollapse */ c: { d: 1 }}; var e = 1; e = a$b.c.d;"));

    test(
        srcs("var a = {}; a.b = {f: 0, c: {/** @nocollapse */ d: 1}}; var e = 1; e = a.b.c.d"),
        expected("var a$b$f = 0; var a$b$c = { /** @nocollapse */ d: 1}; var e = 1; e = a$b$c.d;"));
  }

  @Test
  public void testPropGetInsideAnObjLit() {

    test(
        srcs("var x = {}; x.y = 1; var a = {}; a.b = {c: x.y}"),
        expected("var x$y = 1; var a$b$c = x$y;"));
    test(
        srcs("var x = {}; x.y = 1; var a = {}; a.b = {c : x?.y}"),
        expected("var x = {}; x.y = 1; var            a$b$c = x?.y;"));

    test(
        srcs("var x = {}; /** @nocollapse */ x.y = 1; var a = {}; a.b = {c: x.y}"),
        expected("var x = {}; /** @nocollapse */ x.y = 1; var a$b$c = x.y;"));

    test(
        srcs("var x = {}; x.y = 1; var a = {}; a.b = { /** @nocollapse */ c: x.y}"),
        expected("var x$y = 1; var a$b = { /** @nocollapse */ c: x$y};"));
    test(
        srcs("var x = {}; x.y = 1; var a = {}; a.b = { /** @nocollapse */ c: x?.y}"),
        expected("var x = {}; x.y = 1; var         a$b = { /** @nocollapse */ c: x?.y};"));

    testSame(
        lines(
            "var x = {}; /** @nocollapse */ x.y = 1; var a = {};",
            "/** @nocollapse */ a.b = {c: x.y}"));
  }

  @Test
  public void testObjLitWithQuotedKeyThatDoesNotGetRead() {

    test(
        srcs("var a = {}; a.b = {c: 0, 'd': 1}; var e = 1; e = a.b.c;"),
        expected("var a$b$c = 0; var a$b$d = 1; var e = 1; e = a$b$c;"));
    test(
        srcs("var a = {}; a.b = {c: 0, 'd': 1}; var e = 1; e = a.b?.c;"),
        expected("var         a$b = {c: 0, 'd': 1}; var e = 1; e = a$b?.c;"));

    test(
        srcs("var a = {}; a.b = {c: 0, /** @nocollapse */ 'd': 1}; var e = 1; e = a.b.c;"),
        expected("var a$b$c = 0; var a$b = {/** @nocollapse */ 'd': 1}; var e = 1; e = a$b$c;"));
    test(
        srcs("var a = {}; a.b = {c: 0, /** @nocollapse */ 'd': 1}; var e = 1; e = a.b?.c;"),
        expected("var         a$b = {c: 0, /** @nocollapse */ 'd': 1}; var e = 1; e = a$b?.c;"));
  }

  @Test
  public void testObjLitWithQuotedKeyThatGetsRead() {
    test(
        srcs("var a = {}; a.b = {c: 0, 'd': 1}; var e = a.b['d'];"),
        expected("var a$b = {c: 0, 'd': 1}; var e = a$b['d'];"));

    test(
        srcs("var a = {}; a.b = {c: 0, /** @nocollapse */ 'd': 1}; var e = a.b['d'];"),
        expected("var a$b = {c: 0, /** @nocollapse */ 'd': 1}; var e = a$b['d'];"));
  }

  @Test
  public void testObjLitWithQuotedKeyThatDoesNotGetReadComputed() {

    // quoted/computed does not get read
    test(
        srcs("    var a = {}; a.b = {c: 0, ['d']: 1}; var e = 1; e = a.b.c;"),
        expected("var            a$b$c = 0          ; var e = 1; e = a$b$c")); // incorrect
    test(
        srcs("var a = {}; a.b = {c: 0, ['d']: 1}; var e = 1; e = a.b?.c;"),
        expected("var         a$b = {c: 0, ['d']: 1}; var e = 1; e = a$b?.c"));

    // quoted/computed gets read
    test(
        srcs("var a = {}; a.b = {c: 0, ['d']: 1}; var e = a.b['d'];"),
        expected("var a$b = {c: 0, ['d']: 1}; var e = a$b['d'];"));

    // key collision
    test(
        srcs("    var a = {}; a.b = {c : 0, ['c']: 1}; var e = a.b.c;"),
        expected("var            a$b$c = 0;            var e =  null;")); // incorrect
  }

  @Test
  public void testFunctionWithQuotedPropertyThatDoesNotGetRead() {
    test(
        srcs("var a = {}; a.b = function() {}; a.b['d'] = 1;"),
        expected("var a$b = function() {}; a$b['d'] = 1;"));

    test(
        srcs("var a = {}; /** @nocollapse */ a.b = function() {}; a.b['d'] = 1;"),
        expected("var a = {}; /** @nocollapse */ a.b = function() {}; a.b['d'] = 1;"));

    test(
        srcs("var a = {}; a.b = function() {}; /** @nocollapse */ a.b['d'] = 1;"),
        expected("var a$b = function() {}; /** @nocollapse */ a$b['d'] = 1;"));
  }

  @Test
  public void testFunctionWithQuotedPropertyThatGetsRead() {
    test(
        srcs("var a = {}; a.b = function() {}; a.b['d'] = 1; f(a.b['d']);"),
        expected("var a$b = function() {}; a$b['d'] = 1; f(a$b['d']);"));

    testSame(
        lines(
            "var a = {}; /** @nocollapse */ a.b = function() {};", //
            "a.b['d'] = 1; f(a.b['d']);"));

    test(
        srcs("var a = {}; a.b = function() {}; /** @nocollapse */ a.b['d'] = 1; f(a.b['d']);"),
        expected("var a$b = function() {}; /** @nocollapse */ a$b['d'] = 1; f(a$b['d']);"));
  }

  @Test
  public void testObjLitAssignedToMultipleNames1() {
    // An object literal that's assigned to multiple names isn't collapsed.
    testSame("var a = b = {c: 0, d: 1}; var e = a.c; var f = b.d;");

    testSame(
        lines(
            "var a = b = {c: 0, /** @nocollapse */ d: 1}; var e = a.c;", //
            "var f = b.d;"));
  }

  @Test
  public void testObjLitAssignedToMultipleNames2() {
    testSame("a = b = {c: 0, d: 1}; var e = a.c; var f = b.d;");
  }

  @Test
  public void testObjLitRedefinedInGlobalScope() {
    testSame("a = {b: 0}; a = {c: 1}; var d = a.b; var e = a.c;");
  }

  @Test
  public void testObjLitRedefinedInLocalScope() {
    test(
        srcs("var a = {}; a.b = {c: 0}; function d() { a.b = {c: 1}; } e(a.b.c);"),
        expected("var a$b = {c: 0}; function d() { a$b = {c: 1}; } e(a$b.c);"));

    testSame("var a = {};/** @nocollapse */ a.b = {c: 0}; function d() { a.b = {c: 1};} e(a.b.c);");

    // redefinition with @nocollapse
    test(
        srcs(
            lines(
                "var a = {}; a.b = {c: 0}; ",
                "function d() { a.b = {/** @nocollapse */ c: 1}; } e(a.b.c);")),
        expected("var a$b = {c: 0}; function d() { a$b = {/** @nocollapse */ c: 1}; } e(a$b.c);"));
  }

  @Test
  public void testObjLitAssignedInTernaryExpression1() {
    testSame("a = x ? {b: 0} : d; var c = a.b;");
  }

  @Test
  public void testObjLitAssignedInTernaryExpression2() {
    testSame("a = x ? {b: 0} : {b: 1}; var c = a.b;");
  }

  @Test
  public void testGlobalVarSetToObjLitConditionally1() {
    testSame("var a; if (x) a = {b: 0}; var c = x ? a.b : 0;");
  }

  @Test
  public void testGlobalVarSetToObjLitConditionally1b() {
    test(
        srcs("if (x) var a = {b: 0}; var c = x ? a.b : 0;"),
        expected("if (x) var a$b = 0; var c = x ? a$b : 0;"));

    testSame("if (x) var a = { /** @nocollapse */ b: 0}; var c = x ? a.b : 0;");
  }

  @Test
  public void testGlobalVarSetToObjLitConditionally2() {

    test(
        srcs("if (x) var a = {b: 0}; var c = 1; c = a.b; var d = a.c;"),
        expected("if (x){ var a$b = 0; var a = {}; }var c = 1; c = a$b; var d = a.c;"));
    testSame("if (x) var a = {b: 0}; var c = 1; c = a?.b; var d = a?.c;");

    testSame("if (x) var a = {/** @nocollapse */ b: 0}; var c = 1; c = a.b; var d = a.c;");
  }

  @Test
  public void testGlobalVarSetToObjLitConditionally3() {
    testSame("var a; if (x) a = {b: 0}; else a = {b: 1}; var c = a.b;");

    testSame(
        lines(
            "var a; if (x) a = {b: 0}; else a = {/** @nocollapse */ b: 1};", //
            "var c = a.b;"));
  }

  @Test
  public void testObjectPropertySetToObjLitConditionally() {
    test(
        srcs("var a = {}; if (x) a.b = {c: 0}; var d = a.b ? a.b.c : 0;"),
        expected("if (x){ var a$b$c = 0; var a$b = {} } var d = a$b ? a$b$c : 0;"));

    test(
        srcs("var a = {}; if (x) a.b = {/** @nocollapse */ c: 0}; var d = a.b ? a.b.c : 0;"),
        expected("if (x){ var a$b = {/** @nocollapse */ c: 0};} var d = a$b ? a$b.c : 0;"));
  }

  @Test
  public void testFunctionPropertySetToObjLitConditionally() {
    test(
        srcs("function a() {} if (x) a.b = {c: 0}; var d = a.b ? a.b.c : 0;"),
        expected(
            lines(
                "function a() {} if (x){ var a$b$c = 0; var a$b = {} }", //
                "var d = a$b ? a$b$c : 0;")));

    testSame(
        lines(
            "function a() {} if (x) /** @nocollapse */ a.b = {c: 0};", //
            "var d = a.b ? a.b.c : 0;"));

    test(
        srcs(
            lines(
                "function a() {}",
                "if (x) a.b = {/** @nocollapse */ c: 0};",
                "var d = a.b ? a.b.c : 0;")),
        expected(
            lines(
                "function a() {}",
                "if (x) {",
                "  var a$b = {/** @nocollapse */ c: 0};",
                "}",
                "var d = a$b ? a$b.c : 0")));
  }

  @Test
  public void testPrototypePropertySetToAnObjectLiteral() {
    test(
        srcs("var a = {b: function(){}}; a.b.prototype.c = {d: 0};"),
        expected("var a$b = function(){}; a$b.prototype.c = {d: 0};"));

    testSame(
        lines(
            "var a = {/** @nocollapse */ b: function(){}};", //
            "a.b.prototype.c = {d: 0};"));
  }

  @Test
  public void testObjectPropertyResetInLocalScope() {

    test(
        srcs("var z = {}; z.a = 0; function f() {z.a = 5; return z.a}"),
        expected("var z$a = 0; function f() {z$a = 5; return z$a}"));
    testSame("var z = {}; z.a = 0; function f() {z.a = 5; return z?.a}");

    testSame(
        lines(
            "var z = {}; z.a = 0;", //
            "function f() { /** @nocollapse */ z.a = 5; return z.a}"));

    testSame(
        lines(
            "var z = {}; /** @nocollapse */ z.a = 0;", //
            "function f() {z.a = 5; return z.a}"));
  }

  @Test
  public void testFunctionPropertyResetInLocalScope() {

    test(
        srcs("function z() {}     z.a = 0; function f() {z.a = 5; return z.a}"),
        expected("function z() {} var z$a = 0; function f() {z$a = 5; return z$a}"));
    testSame("function z() {} z.a = 0; function f() {z.a = 5; return z?.a}");

    testSame(
        lines(
            "function z() {} /** @nocollapse */ z.a = 0;", //
            "function f() {z.a = 5; return z.a}"));

    testSame(
        lines(
            "function z() {} z.a = 0;", //
            "function f() { /** @nocollapse */ z.a = 5; return z.a}"));
  }

  @Test
  public void testNamespaceResetInGlobalScope1() {
    test(
        "var a = {}; /** @constructor */ a.b = function() {}; a = {};",
        "var a = {}; /** @constructor */ var a$b = function() {}; a = {};",
        warning(NAMESPACE_REDEFINED_WARNING));

    testSame(
        lines(
            "var a = {}; /** @constructor @nocollapse */a.b = function() {};", //
            "a = {};"),
        NAMESPACE_REDEFINED_WARNING);
  }

  @Test
  public void testNamespaceResetInGlobalScope2() {
    test(
        "var a = {}; a = {}; /** @constructor */ a.b = function() {};",
        "var a = {}; a = {}; /** @constructor */ var a$b = function() {};",
        warning(NAMESPACE_REDEFINED_WARNING));

    testSame(
        "var a = {}; a = {}; /** @constructor @nocollapse */a.b = function() {};",
        NAMESPACE_REDEFINED_WARNING);
  }

  @Test
  public void testNamespaceResetInGlobalScope3() {
    test(
        srcs("var a = {}; /** @constructor */ a.b = function() {}; a = a || {};"),
        expected("var a = {}; /** @constructor */ var a$b = function() {}; a = a || {};"));

    testSame("var a = {}; /** @constructor @nocollapse */ a.b = function() {}; a = a || {};");
  }

  @Test
  public void testNamespaceResetInGlobalScope4() {
    test(
        srcs("var a = {}; /** @constructor */ a.b = function() {}; var a = a || {};"),
        expected("var a = {}; /** @constructor */ var a$b = function() {}; var a = a || {};"));

    testSame("var a = {}; /** @constructor @nocollapse */a.b = function() {}; var a = a || {};");
  }

  @Test
  public void testNamespaceResetInLocalScope1() {
    test(
        "var a = {}; /** @constructor */ a.b = function() {}; function f() { a = {}; }",
        "var a = {}; /** @constructor */ var a$b = function() {}; function f() { a = {}; }",
        warning(NAMESPACE_REDEFINED_WARNING));

    testSame(
        lines(
            "var a = {}; /** @constructor @nocollapse */a.b = function() {};",
            " function f() { a = {}; }"),
        NAMESPACE_REDEFINED_WARNING);
  }

  @Test
  public void testNamespaceResetInLocalScope2() {
    test(
        "var a = {}; function f() { a = {}; } /** @constructor */ a.b = function() {};",
        "var a = {}; function f() { a = {}; } /** @constructor */ var a$b = function() {};",
        warning(NAMESPACE_REDEFINED_WARNING));

    testSame(
        lines(
            "var a = {}; function f() { a = {}; }",
            " /** @constructor @nocollapse */a.b = function() {};"),
        NAMESPACE_REDEFINED_WARNING);
  }

  @Test
  public void testNamespaceDefinedInLocalScope() {
    test(
        srcs(
            lines(
                "var a = {};",
                "(function() { a.b = {}; })();",
                "/** @constructor */ a.b.c = function() {};")),
        expected(
            lines(
                "var a$b;",
                "(function() { a$b = {}; })();",
                "/** @constructor */ var a$b$c = function() {}")));

    test(
        srcs(
            lines(
                "var a = {}; (function() { /** @nocollapse */ a.b = {}; })();",
                " /** @constructor */a.b.c = function() {};")),
        expected(
            lines(
                "var a = {}; (function() { /** @nocollapse */ a.b = {}; })();",
                " /** @constructor */ var a$b$c = function() {};")));

    test(
        srcs(
            lines(
                "var a = {}; (function() { a.b = {}; })();",
                " /** @constructor @nocollapse */a.b.c = function() {};")),
        expected(
            lines(
                "var a$b; (function() { a$b = {}; })();",
                "/** @constructor @nocollapse */ a$b.c = function() {};")));
  }

  @Test
  public void testAddPropertyToObjectInLocalScopeDepth1() {
    test(
        srcs("var a = {b: 0}; function f() { a.c = 5; return a.c; }"),
        expected("var a$b = 0; var a$c; function f() { a$c = 5; return a$c; }"));
    testSame("var a = {b: 0}; function f() { a.c = 5; return a?.c; }");
  }

  @Test
  public void testAddPropertyToObjectInLocalScopeDepth2() {
    test(
        srcs("var a = {}; a.b = {}; (function() {a.b.c = 0;})(); x = a.b.c;"),
        expected("var a$b$c; (function() {a$b$c = 0;})(); x = a$b$c;"));
    test(
        srcs("var a = {}; a.b = {}; (function() {a.b.c = 0;})(); x = a.b?.c;"),
        expected("var         a$b = {}; (function() {a$b.c = 0;})(); x = a$b?.c;"));
  }

  @Test
  public void testAddPropertyToFunctionInLocalScopeDepth1() {
    test(
        srcs("function a() {} function f() { a.c = 5; return a.c; }"),
        expected("function a() {} var a$c; function f() { a$c = 5; return a$c; }"));
    testSame("function a() {} function f() { a.c = 5; return a?.c; }");
  }

  @Test
  public void testAddPropertyToFunctionInLocalScopeDepth2() {
    test(
        srcs("var a = {}; a.b = function() {}; function f() {a.b.c = 0;}"),
        expected("var a$b = function() {}; var a$b$c; function f() {a$b$c = 0;}"));
  }

  @Test
  public void testAddPropertyToUncollapsibleFunctionInLocalScopeDepth1() {
    testSame("function a() {} var c = 1; c = a; (function() {a.b = 0;})(); a.b;");
  }

  @Test
  public void testAddPropertyToUncollapsibleFunctionInLocalScopeDepth2() {
    test(
        srcs(
            lines(
                "var a = {}; a.b = function (){}; var d = 1; d = a.b;",
                "(function() {a.b.c = 0;})(); a.b.c;")),
        expected(
            lines(
                "var a$b = function (){}; var d = 1; d = a$b;", //
                "(function() {a$b.c = 0;})(); a$b.c;")));
    testSame(
        lines(
            "var a = {}; a.b = function (){}; var d = 1; d = a?.b;",
            "(function() {a.b.c = 0;})(); a.b?.c;"));
  }

  @Test
  public void testResetObjectPropertyInLocalScope() {
    test(
        srcs("var a = {b: 0}; a.c = 1; function f() { a.c = 5; }"),
        expected("var a$b = 0; var a$c = 1; function f() { a$c = 5; }"));
  }

  @Test
  public void testResetFunctionPropertyInLocalScope() {
    test(
        srcs("function a() {}; a.c = 1; function f() { a.c = 5; }"),
        expected("function a() {}; var a$c = 1; function f() { a$c = 5; }"));
  }

  @Test
  public void testGlobalNameReferencedInLocalScopeBeforeDefined1() {
    // Because referencing global names earlier in the source code than they're
    // defined is such a common practice, we collapse them even though a runtime
    // exception could result (in the off-chance that the function gets called
    // before the alias variable is defined).
    test(
        srcs("var a = {b: 0}; function f() { a.c = 5; } a.c = 1;"),
        expected("var a$b = 0; function f() { a$c = 5; } var a$c = 1;"));
  }

  @Test
  public void testGlobalNameReferencedInLocalScopeBeforeDefined2() {

    test(
        srcs("var a = {b: 0}; function f() { return a.c; } a.c = 1;"),
        expected("var a$b = 0; function f() { return a$c; } var a$c = 1;"));
    testSame("var a = {b: 0}; function f() { return a?.c; } a.c = 1;");
  }

  @Test
  public void testTwiceDefinedGlobalNameDepth1_1() {
    testSame(
        lines(
            "var a = {}; function f() { a.b(); }", //
            "a = function() {}; a.b = function() {};"));
  }

  @Test
  public void testTwiceDefinedGlobalNameDepth1_2() {
    testSame(
        lines(
            "var a = {}; /** @constructor */ a = function() {};",
            "a.b = {}; a.b.c = 0; function f() { a.b.d = 1; }"));
  }

  @Test
  public void testTwiceDefinedGlobalNameDepth2() {
    test(
        srcs(
            lines(
                "var a = {}; a.b = {}; function f() { a.b.c(); }",
                "a.b = function() {}; a.b.c = function() {};")),
        expected(
            lines(
                "var a$b = {}; function f() { a$b.c(); }",
                "a$b = function() {}; a$b.c = function() {};")));
  }

  @Test
  public void testFunctionCallDepth1() {
    test(
        srcs("var a = {}; a.b = function(){}; var c = a.b();"),
        expected("var a$b = function(){}; var c = a$b()"));
  }

  @Test
  public void testFunctionCallDepth2() {
    test(
        srcs("var a = {}; a.b = {}; a.b.c = function(){}; a.b.c();"),
        expected("var a$b$c = function(){}; a$b$c();"));
  }

  @Test
  public void testFunctionAlias1() {

    test(
        srcs("var a = {}; a.b = {}; a.b.c = function(){}; a.b.d = a.b.c;a.b.d=null"),
        expected("var a$b$c = function(){}; var a$b$d = a$b$c;a$b$d=null;"));
    test(
        srcs("var a = {}; a.b = {}; a.b.c = function(){}; a.b.d = a.b?.c; a.b.d=null"),
        expected("var         a$b = {}; a$b.c = function(){}; a$b.d = a$b?.c; a$b.d=null;"));
  }

  @Test
  public void testCallToRedefinedFunction() {
    test(
        srcs("var a = {}; a.b = function(){}; a.b = function(){}; a.b();"),
        expected("var a$b = function(){}; a$b = function(){}; a$b();"));
  }

  @Test
  public void testCollapsePrototypeName() {
    test(
        srcs(
            lines(
                "var a = {}; a.b = {}; a.b.c = function(){}; ",
                "a.b.c.prototype.d = function(){}; (new a.b.c()).d();")),
        expected(
            lines(
                "var a$b$c = function(){}; a$b$c.prototype.d = function(){}; ", //
                "new a$b$c().d();")));
    test(
        srcs(
            lines(
                "var a = {}; a.b = {}; a.b.c = function(){}; ",
                "a.b.c.prototype.d = function(){}; (new a.b.c()).d?.();")),
        expected(
            lines(
                "var a$b$c = function(){}; a$b$c.prototype.d = function(){}; ", //
                "(new a$b$c).d?.();")));
  }

  @Test
  public void testReferencedPrototypeProperty() {
    test(
        srcs(
            lines(
                "var a = {b: {}}; a.b.c = function(){}; a.b.c.prototype.d = {};",
                "e = a.b.c.prototype.d;")),
        expected(
            lines(
                "var a$b$c = function(){}; a$b$c.prototype.d = {};", //
                "e = a$b$c.prototype.d;")));
  }

  @Test
  public void testSetStaticAndPrototypePropertiesOnFunction() {
    test(
        srcs("var a = {}; a.b = function(){}; a.b.prototype.d = 0; a.b.c = 1;"),
        expected("var a$b = function(){}; a$b.prototype.d = 0; var a$b$c = 1;"));
  }

  @Test
  public void testReadUndefinedPropertyDepth1() {
    test(srcs("var a = {b: 0}; var c = a.d;"), expected("var a$b = 0; var a = {}; var c = a.d;"));
  }

  @Test
  public void testReadUndefinedPropertyDepth2() {

    test(
        srcs("var a = {b: {c: 0}}; f(a.b.c); f(a.b.d);"),
        expected("var a$b$c = 0; var a$b = {}; f(a$b$c); f(a$b.d);"));
    test(
        srcs("var a = {b : {c: 0}}; f?.(a.b?.c); f?.(a.b?.d);"),
        expected("var    a$b = {c: 0} ; f?.(a$b?.c); f?.(a$b?.d);"));
  }

  @Test
  public void testCallUndefinedMethodOnObjLitDepth1() {
    test(srcs("var a = {b: 0}; a.c();"), expected("var a$b = 0; var a = {}; a.c();"));
  }

  @Test
  public void testCallUndefinedMethodOnObjLitDepth2() {
    test(
        srcs("var a = {b: {}}; a.b.c = function() {}; a.b.c(); a.b.d();"),
        expected("var a$b = {}; var a$b$c = function() {}; a$b$c(); a$b.d();"));
  }

  @Test
  public void testPropertiesOfAnUndefinedVar() {
    testSame("a.document = d; f(a.document.innerHTML);");
  }

  @Test
  public void testPropertyOfAnObjectThatIsNeitherFunctionNorObjLit() {
    testSame("var a = window; a.document = d; f(a.document)");
  }

  @Test
  public void testStaticFunctionReferencingThis1() {
    // Note: Google's JavaScript Style Guide says to avoid using the 'this'
    // keyword in a static function.
    test(
        "var a = {}; a.b = function() {this.c};",
        "var a$b = function() {this.c}; ",
        warning(InlineAndCollapseProperties.RECEIVER_AFFECTED_BY_COLLAPSE));
  }

  @Test
  public void testStaticFunctionReferencingThis2() {
    // This gives no warning, because `this` is in a scope whose name is not
    // getting collapsed.
    test(
        srcs("var a = {}; a.b = function() { return function(){ return this; }; };"),
        expected("var a$b = function() { return function(){ return this; }; };"));
  }

  @Test
  public void testPropAssignedToFunctionReferencingThis() {
    // This gives no warning, because `this` is in a scope whose name is not getting collapsed.
    testNoWarning("var a = {}; var b = function() { this.c;}; a.b=b;");
  }

  @Test
  public void testStaticFunctionReferencingThis3() {
    test(
        "var a = {b: function() {this.c}};",
        "var a$b = function() { this.c };",
        warning(InlineAndCollapseProperties.RECEIVER_AFFECTED_BY_COLLAPSE));
  }

  @Test
  public void testStaticFunctionReferencingThis4() {
    test(
        srcs("var a = {/** @this {Element} */ b: function() {this.c}};"),
        expected("var a$b = function() { this.c };"));
  }

  @Test
  public void testPrototypeMethodReferencingThis() {
    testSame("var A = function(){}; A.prototype = {b: function() {this.c}};");
  }

  @Test
  public void testConstructorReferencingThis() {
    test(
        srcs(
            lines(
                "var a = {}; ", //
                "/** @constructor */ a.b = function() { this.a = 3; };")),
        expected("/** @constructor */ var a$b = function() { this.a = 3; };"));
  }

  @Test
  public void testRecordReferencingThis() {
    test(
        srcs(
            lines(
                "/** @const */ var a = {}; ",
                "/** @record */ a.b = function() { /** @type {string} */ this.a; };")),
        expected("/** @record */ var a$b = function() { /** @type {string} */ this.a; };"));
  }

  @Test
  public void testSafeReferenceOfThis() {
    test(
        srcs("var a = {}; /** @this {Object} */ a.b = function() { this.a = 3; };"),
        expected(" /** @this {Object} */ var a$b = function() { this.a = 3; };"));
  }

  @Test
  public void testGlobalFunctionReferenceOfThis() {
    testSame("var a = function() { this.a = 3; };");
  }

  @Test
  public void testFunctionGivenTwoNames() {

    // It's okay to collapse f's properties because g is not added to the
    // global scope as an alias for f. (Try it in your browser.)
    test(
        srcs("var f = function g() {}; f.a = 1; h(f.a);"),
        expected("var f = function g() {}; var f$a = 1; h(f$a);"));
    testSame("var f = function g() {}; f.a = 1; h?.(f?.a);");
  }

  @Test
  public void testObjLitWithUsedNumericKey() {
    testSame("a = {40: {}, c: {}}; var d = a[40]; var e = a.c;");
  }

  @Test
  public void testObjLitWithUnusedNumericKey() {

    test(
        srcs("var a = {40: {},       c : {}}; var e = 1; e = a.c;"),
        expected("var    a$1 = {}; var a$c = {} ; var e = 1; e = a$c")); // incorrect
    testSame("var a = {40: {}, c: {}}; var e = 1; e =  a?.c;");
  }

  @Test
  public void testObjLitWithNonIdentifierKeys() {
    testSame("a = {' ': 0, ',': 1}; var c = a[' '];");
    testSame(
        lines(
            "var FOO = {",
            "  'bar': {",
            "    'baz,qux': {",
            "      'beep': 'xxxxx',",
            "    },",
            "  }",
            lines(
                "};", //
                "alert(FOO);")));
  }

  @Test
  public void testChainedAssignments1() {
    test(srcs("var x = {}; x.y = a = 0;"), expected("var x$y = a = 0;"));
  }

  @Test
  public void testChainedAssignments2() {
    test(srcs("var x = {}; x.y = a = b = c();"), expected("var x$y = a = b = c();"));
  }

  @Test
  public void testChainedAssignments3() {
    test(srcs("var x = {y: 1}; a = b = x.y;"), expected("var x$y = 1; a = b = x$y;"));
    testSame("var x = {y: 1}; a = b = x?.y;");
  }

  @Test
  public void testChainedAssignments4() {
    testSame("var x = {}; a = b = x.y;");
  }

  @Test
  public void testChainedAssignments5() {
    test(srcs("var x = {}; a = x.y = 0;"), expected("var x$y; a = x$y = 0;"));
  }

  @Test
  public void testChainedAssignments6() {
    test(srcs("var x = {}; a = x.y = b = c();"), expected("var x$y; a = x$y = b = c();"));
  }

  @Test
  public void testChainedAssignments7() {
    test(
        "var x = {}; a = x.y = {}; /** @constructor */ x.y.z = function() {};",
        "var x$y; a = x$y = {}; /** @constructor */ x$y.z = function() {};",
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testChainedVarAssignments1() {
    test(srcs("var x = {y: 1}; var a = x.y = 0;"), expected("var x$y = 1; var a = x$y = 0;"));
  }

  @Test
  public void testChainedVarAssignments2() {
    test(
        srcs("var x = {y: 1}; var a = x.y = b = 0;"),
        expected("var x$y = 1; var a = x$y = b = 0;"));
  }

  @Test
  public void testChainedVarAssignments3() {
    test(
        srcs("var x = {y: {z: 1}}; var b = 0; var a = x.y.z = 1; var c = 2;"),
        expected("var x$y$z = 1; var b = 0; var a = x$y$z = 1; var c = 2;"));
  }

  @Test
  public void testChainedVarAssignments4() {
    test(srcs("var x = {}; var a = b = x.y = 0;"), expected("var x$y; var a = b = x$y = 0;"));
  }

  @Test
  public void testChainedVarAssignments5() {
    test(
        srcs("var x = {y: {}}; var a = b = x.y.z = 0;"),
        expected("var x$y$z; var a = b = x$y$z = 0;"));
  }

  @Test
  public void testChainedVarAssignments6() {
    testSame("var a = x = 0; var x;");
  }

  @Test
  public void testChainedVarAssignments7() {
    testSame("x = {}; var a = x.y = 0; var x;");
  }

  @Test
  public void testPeerAndSubpropertyOfUncollapsibleProperty() {

    test(
        srcs(
            lines(
                "var x = {}; var a = x.y = 0; x.w = 1; x.y.z = 2;", //
                "b = x.w; c = x.y.z;")),
        expected(
            lines(
                "var x$y; var a = x$y = 0; var x$w = 1; x$y.z = 2;", //
                "b = x$w; c = x$y.z;")));
    testSame(
        lines(
            "var x = {}; var a = x.y = 0; x.w = 1; x.y.z = 2;", //
            "b = x?.w; c = x.y?.z;"));
  }

  @Test
  public void testComplexAssignmentAfterInitialAssignment() {
    test(
        srcs("var d = {}; d.e = {}; d.e.f = 0; a = b = d.e.f = 1;"),
        expected("var d$e$f = 0; a = b = d$e$f = 1;"));
  }

  @Test
  public void testRenamePrefixOfUncollapsibleProperty() {
    test(
        srcs("var d = {}; d.e = {}; a = b = d.e.f = 0;"),
        expected("var d$e$f; a = b = d$e$f = 0;"));
  }

  @Test
  public void testNewOperator() {
    // Using the new operator on a name doesn't prevent its (static) properties
    // from getting collapsed.
    test(
        srcs("var a = {}; a.b = function() {}; a.b.c = 1; var d = new a.b();"),
        expected("var a$b = function() {}; var a$b$c = 1; var d = new a$b();"));
  }

  @Test
  public void testMethodCall() {
    test(
        srcs("var a = {}; a.b = function() {}; var d = a.b();"),
        expected("var a$b = function() {}; var d = a$b();"));
  }

  @Test
  public void testObjLitDefinedInLocalScopeIsLeftAlone() {
    test(
        srcs(
            lines(
                "var a = {}; a.b = function() {};",
                "a.b.prototype.f_ = function() {",
                "  var x = { p: '', q: '', r: ''}; var y = x.q;",
                "};")),
        expected(
            lines(
                "var a$b = function() {};",
                "a$b.prototype.f_ = function() {",
                "  var x = { p: '', q: '', r: ''}; var y = x.q;",
                "};")));
  }

  @Test
  public void testPropertiesOnBothSidesOfAssignment() {

    // This verifies that replacements are done in the right order. Collapsing
    // the l-value in an assignment affects the parse tree immediately above
    // the r-value, so we update all rvalues before any lvalues.
    test(
        srcs("var a = {b: 0}; a.c = a.b;a.c = null"),
        expected("var a$b = 0; var a$c = a$b;a$c = null"));
    testSame("var a = {b: 0}; a.c = a?.b; a.c = null");
  }

  @Test
  public void testCallOnUndefinedProperty() {
    // The "inherits" property is not explicitly defined on a.b anywhere, but
    // it is accessed as though it certainly exists (it is called), so we infer
    // that it must be an uncollapsible property that has come into existence
    // some other way.
    test(
        srcs("var a = {}; a.b = function(){}; a.b.inherits(x);"),
        expected("var a$b = function(){}; a$b.inherits(x);"));
  }

  @Test
  public void testGetPropOnUndefinedProperty() {
    // The "superClass_" property is not explicitly defined on a.b anywhere,
    // but it is accessed as though it certainly exists (a subproperty of it
    // is accessed), so we infer that it must be an uncollapsible property that
    // has come into existence some other way.
    test(
        srcs(
            lines(
                "var a = {b: function(){}}; a.b.prototype.c =",
                "function() { a.b.superClass_.c.call(this); }")),
        expected(
            lines(
                "var a$b = function(){}; a$b.prototype.c =",
                "function() { a$b.superClass_.c.call(this); }")));
  }

  @Test
  public void testNonWellformedAlias1() {
    testSame("var a = {b: 3}; function f() { f(x); var x = a; f(x.b); }");
  }

  @Test
  public void testNonWellformedAlias2() {
    testSame(
        lines(
            "var a = {b: 3}; ", //
            "function f() { if (false) { var x = a; f(x.b); } f(x); }"));
  }

  @Test
  public void testInlineAliasWithModifications() {
    testSame("var x = 10; function f() { var y = x; x++; alert(y)} ");
    testSame("var x = 10; function f() { var y = x; x+=1; alert(y)} ");
    test(
        srcs("var x = {}; x.x = 10; function f() {var y=x.x; x.x++; alert(y)}"),
        expected("var x$x = 10; function f() {var y=x$x; x$x++; alert(y)}"));
    disableNormalize();
    test(
        srcs("var x = {}; x.x = 10; function f() {var y=x.x; x.x+=1; alert(y)}"),
        expected("var x$x = 10; function f() {var y=x$x; x$x+=1; alert(y)}"));
  }

  @Test
  public void testDoNotCollapsePropertyOnExternType() {
    testSame("String.myFunc = function() {}; String.myFunc()");
  }

  @Test
  public void testBug1704733() {
    String prelude =
        lines(
            "function protect(x) { return x; }",
            "function O() {}",
            "protect(O).m1 = function() {};",
            "protect(O).m2 = function() {};",
            "protect(O).m3 = function() {};");

    testSame(lines(prelude + "alert(O.m1); alert(O.m2()); alert(!O.m3);"));
  }

  @Test
  public void testBug1956277() {
    test(srcs("var CONST = {}; CONST.URL = 3;"), expected("var CONST$URL = 3;"));
  }

  @Test
  public void testBug1974371() {
    test(
        srcs("/** @enum {Object} */ var Foo = {A: {c: 2}, B: {c: 3}}; for (var key in Foo) {}"),
        expected(
            lines(
                "var Foo$A = {c: 2}; var Foo$B = {c: 3};",
                "/** @enum {Object} */ var Foo = {A: Foo$A, B: Foo$B};",
                "for (var key in Foo) {}")));
  }

  @Test
  public void testHasOwnProperty() {

    testSame("var a = {b: 3}; if (a.hasOwnProperty(foo)) { alert('ok'); }");
    testSame("var a = {b: 3}; if (a.hasOwnProperty(foo)) { alert('ok'); } a?.b;");
  }

  @Test
  public void testHasOwnPropertyOnNonGlobalName() {
    testSame(
        lines(
            "/** @constructor */",
            "function A() {",
            "  this.foo = {a: 1, b: 1};",
            "}",
            "A.prototype.bar = function(prop) {",
            "  return this.foo.hasOwnProperty(prop);",
            "}"));
    testSame("var a = {'b': {'c': 1}}; a['b'].hasOwnProperty('c');");
    testSame("var a = {b: 3}; if (Object.prototype.hasOwnProperty.call(a, 'b')) { alert('ok'); }");
  }

  @Test
  public void testHasOwnPropertyNested() {

    test(
        srcs("var a = {b: {c: 3}}; if (a.b.hasOwnProperty('c')) { alert('ok'); }"),
        expected("var a$b =   {c: 3};  if (a$b.hasOwnProperty('c')) { alert('ok'); }"));
    test(
        srcs("var a = {b: {c: 3}}; if (a.b.hasOwnProperty('c')) { alert('ok'); } a.b.c;"),
        expected("var a$b =   {c: 3};  if (a$b.hasOwnProperty('c')) { alert('ok'); } a$b.c;"));
    test(
        srcs("var a = {b: {c: 3}}; if (a.b.hasOwnProperty('c')) { alert('ok'); } a.b?.c;"),
        expected("var a$b =   {c: 3};  if (a$b.hasOwnProperty('c')) { alert('ok'); } a$b?.c;"));
    test(
        srcs("var a = {}; a.b = function(p) { log(a.b.c.hasOwnProperty(p)); }; a.b.c = {};"),
        expected("var a$b = function(p) { log(a$b$c.hasOwnProperty(p)); }; var a$b$c = {};"));
    test(
        srcs("var a = {}; a.b = function(p) { log(a.b.c?.hasOwnProperty(p)); };     a.b.c = {};"),
        expected(
            "var         a$b = function(p) { log(a$b$c?.hasOwnProperty(p)); }; var a$b$c = {};"));
  }

  @Test
  public void testHasOwnPropertyMultiple() {

    testSame("var a = {b: 3, c: 4, d: 5}; if (a.hasOwnProperty(prop)) { alert('ok'); }");
  }

  @Test
  public void testObjectStaticMethodsPreventCollapsing() {
    testSame("var a = {b: 3}; alert(Object.getOwnPropertyDescriptor(a, 'b'));");
    testSame("var a = {b: 3}; alert(Object.getOwnPropertyDescriptors(a));");
    testSame("var a = {b: 3}; alert(Object.getOwnPropertyNames(a));");
    testSame("var a = {b: 3, [Symbol('c')]: 4}; alert(Object.getOwnPropertySymbols(a));");
  }

  @Test
  public void testEnumOfObjects1() {
    test(
        srcs(
            lines(
                "/** @enum {Object} */", //
                "var Foo = {A: {c: 2}, B: {c: 3}};",
                "for (var key in Foo.A) {}")),
        expected(
            lines(
                "var Foo$A = {c: 2};", //
                "var Foo$B$c = 3;",
                "for (var key in Foo$A) {}")));
    test(
        srcs(
            lines(
                "/** @enum {Object} */", //
                "var Foo = {A: {c: 2}, B: {c: 3}};",
                "for (var key in Foo?.A) {}")),
        expected(
            lines(
                "var Foo$A = {c: 2};",
                "var Foo$B = {c: 3};",
                "/** @enum {!Object} */",
                "var Foo = {A:Foo$A, B:Foo$B};",
                "for (var key in Foo?.A) {}")));
  }

  @Test
  public void testEnumOfObjects2() {
    test(
        srcs(
            lines(
                "/** @enum {Object} */ var Foo = {A: {c: 2}, B: {c: 3}};", //
                "foo(Foo.A.c);")),
        expected("var Foo$A$c = 2; var Foo$B$c = 3; foo(Foo$A$c);"));
    test(
        srcs(
            lines(
                "/** @enum {Object} */", //
                "var Foo = {A: {c: 2}, B: {c: 3}};",
                "foo?.(Foo.A?.c);")),
        expected(lines("var Foo$A = {c: 2};", "var Foo$B$c = 3;", "foo?.(Foo$A?.c);")));
  }

  @Test
  public void testEnumOfObjects3() {
    test(
        srcs(
            lines(
                "var x = {c: 2};",
                "var y = {c: 3};",
                "/** @enum {Object} */",
                "var Foo = {A: x, B: y};",
                "for (var key in Foo) {}")),
        expected(
            lines(
                "var x = {c: 2};",
                "var y = {c: 3};",
                "var Foo$A = x;",
                "var Foo$B = y;",
                "/** @enum {Object} */",
                "var Foo = {A: Foo$A, B: Foo$B};",
                "for (var key in Foo) {}")));
  }

  @Test
  public void testEnumOfObjects4() {

    // Note that this produces bad code, but that's OK, because
    // checkConsts will yell at you for reassigning an enum value.
    // (enum values have to be constant).
    test(
        srcs(
            lines(
                "/** @enum {Object} */ var Foo = {A: {c: 2}, B: {c: 3}};",
                "for (var key in Foo) {} Foo.A = 3; alert(Foo.A);")),
        expected(
            lines(
                "var Foo$A = {c: 2}; var Foo$B = {c: 3};",
                "/** @enum {Object} */ var Foo = {A: Foo$A, B: Foo$B};",
                "for (var key in Foo) {} Foo$A = 3; alert(Foo$A);")));
    test(
        srcs(
            lines(
                "/** @enum {Object} */ var Foo = {A: {c: 2}, B: {c: 3}};",
                "for (var key in Foo) {} Foo.A = 3; alert?.(Foo?.A);")),
        expected(
            lines(
                "var Foo$A = {c: 2}; var Foo$B = {c: 3};",
                "/** @enum {Object} */ var Foo = {A: Foo$A, B: Foo$B};",
                "var key;",
                "for (key in Foo) {} Foo$A = 3; alert?.(Foo?.A);")));
  }

  @Test
  public void testObjectOfObjects1() {
    // Basically the same as testEnumOfObjects4, but without the
    // constant enum values.
    testSame("var Foo = {a: {c: 2}, b: {c: 3}}; for (var key in Foo) {} Foo.a = 3; alert(Foo.a);");
  }

  @Test
  public void testReferenceInAnonymousObject0() {
    test(
        srcs(
            lines(
                "var a = {};",
                "a.b = function(){};",
                "a.b.prototype.c = function(){};",
                "var d = a.b.prototype.c;")),
        expected(
            lines(
                "var a$b = function(){};",
                "a$b.prototype.c = function(){};",
                "var d = a$b.prototype.c;")));
  }

  @Test
  public void testReferenceInAnonymousObject1() {
    test(
        srcs(
            lines(
                "var a = {};", //
                "a.b = function(){};",
                "var d = a.b.prototype.c;")),
        expected(
            lines(
                "var a$b = function(){};", //
                "var d = a$b.prototype.c;")));
  }

  @Test
  public void testReferenceInAnonymousObject2() {
    test(
        srcs(
            lines(
                "var a = {};",
                "a.b = function(){};",
                "a.b.prototype.c = function(){};",
                "var d = {c: a.b.prototype.c};")),
        expected(
            lines(
                "var a$b = function(){};",
                "a$b.prototype.c = function(){};",
                "var d$c = a$b.prototype.c;")));
  }

  @Test
  public void testReferenceInAnonymousObject3() {
    test(
        srcs(
            lines(
                "function CreateClass(a$jscomp$1) {}",
                "var a = {};",
                "a.b = function(){};",
                "a.b.prototype.c = function(){};",
                "a.d = CreateClass({c: a.b.prototype.c});")),
        expected(
            lines(
                "function CreateClass(a$jscomp$1) {}",
                "var a$b = function(){};",
                "a$b.prototype.c = function(){};",
                "var a$d = CreateClass({c: a$b.prototype.c});")));
  }

  @Test
  public void testReferenceInAnonymousObject4() {
    test(
        srcs(
            lines(
                "function CreateClass(a) {}",
                "var a = {};",
                "a.b = CreateClass({c: function() {}});",
                "a.d = CreateClass({c: a.b.c});")),
        expected(
            lines(
                "function CreateClass(a$jscomp$1) {}",
                "var a$b = CreateClass({c: function() {}});",
                "var a$d = CreateClass({c: a$b.c});")));
  }

  @Test
  public void testReferenceInAnonymousObject5() {
    test(
        srcs(
            lines(
                "function CreateClass(a) {}",
                "var a = {};",
                "a.b = CreateClass({c: function() {}});",
                "a.d = CreateClass({c: a.b.prototype.c});")),
        expected(
            lines(
                "function CreateClass(a$jscomp$1) {}",
                "var a$b = CreateClass({c: function() {}});",
                "var a$d = CreateClass({c: a$b.prototype.c});")));
  }

  @Test
  public void testCrashInNestedAssign() {
    test(
        srcs("var a = {}; if (a.b = function() {}) a.b();"),
        expected("var a$b; if (a$b=function() {}) { a$b(); }"));
  }

  @Test
  public void testTwinReferenceCancelsChildCollapsing() {
    test(
        srcs("var a = {}; if (a.b = function() {}) { a.b.c = 3; a.b(a.b.c); }"),
        expected("var a$b; if (a$b = function() {}) { a$b.c = 3; a$b(a$b.c); }"));
  }

  @Test
  public void testPropWithDollarSign() {
    test(srcs("var a = {$: 3}"), expected("var a$$0 = 3;"));
  }

  @Test
  public void testPropWithDollarSign2() {
    test(srcs("var a = {$: function(){}}"), expected("var a$$0 = function(){};"));
  }

  @Test
  public void testPropWithDollarSign3() {
    test(
        srcs("var a = {b: {c: 3}, b$c: function(){}}"),
        expected("var a$b$c = 3; var a$b$0c = function(){};"));
  }

  @Test
  public void testPropWithDollarSign4() {
    test(srcs("var a = {$$: {$$$: 3}};"), expected("var a$$0$0$$0$0$0 = 3;"));
  }

  @Test
  public void testPropWithDollarSign5() {
    test(
        srcs("var a = {b: {$0c: true}, b$0c: false};"),
        expected("var a$b$$00c = true; var a$b$00c = false;"));
  }

  @Test
  public void testConstKey() {
    test(srcs("var foo = {A: 3};"), expected("var foo$A = 3;"));
  }

  @Test
  public void testPropertyOnGlobalCtor() {
    test(
        srcs("/** @constructor */ function Map() {} Map.foo = 3; Map;"),
        expected("/** @constructor */ function Map() {} var Map$foo = 3; Map;"));
  }

  @Test
  public void testPropertyOnGlobalInterface() {
    test(
        srcs("/** @interface */ function Map() {} Map.foo = 3; Map;"),
        expected("/** @interface */ function Map() {} var Map$foo = 3; Map;"));
  }

  @Test
  public void testPropertyOnGlobalFunction() {
    testSame("function Map() {} Map.foo = 3; alert(Map);");
  }

  @Test
  public void testIssue389() {
    testSame(
        lines(
            "function alias() {}",
            "var dojo = {};",
            "dojo.gfx = {};",
            "dojo.declare = function() {};",
            "/** @constructor */",
            "dojo.gfx.Shape = function() {};",
            "dojo.gfx.Shape = dojo.declare('dojo.gfx.Shape');",
            "alias(dojo);"),
        PARTIAL_NAMESPACE_WARNING);
  }

  @Test
  public void testAliasedTopLevelName() {
    testSame(
        lines(
            "function alias() {}",
            "var dojo = {};",
            "dojo.gfx = {};",
            "dojo.declare = function() {};",
            "dojo.gfx.Shape = {SQUARE: 2};",
            "dojo.gfx.Shape = dojo.declare('dojo.gfx.Shape');",
            "alias(dojo);",
            "alias(dojo$gfx$Shape$SQUARE);"));
  }

  @Test
  public void testAliasedTopLevelEnum() {
    testSame(
        lines(
            "function alias() {}",
            "var dojo = {};",
            "dojo.gfx = {};",
            "dojo.declare = function() {};",
            "/** @enum {number} */",
            "dojo.gfx.Shape = {SQUARE: 2};",
            "dojo.gfx.Shape = dojo.declare('dojo.gfx.Shape');",
            "alias(dojo);",
            "alias(dojo.gfx.Shape.SQUARE);"),
        PARTIAL_NAMESPACE_WARNING);
  }

  @Test
  public void testAssignFunctionBeforeDefinition() {
    testSame(
        lines(
            "f = function() {};", //
            "var f = null;"));
  }

  @Test
  public void testObjectLitBeforeDefinition() {
    testSame(
        lines(
            "a = {b: 3};", //
            "var a = null;",
            "this.c = a.b;"));
  }

  @Test
  public void testTypedef1() {
    test(
        srcs(
            lines(
                "var foo = {};", //
                "/** @typedef {number} */ foo.Baz;")),
        expected("var foo = {}; var foo$Baz;"));
  }

  @Test
  public void testTypedef2() {
    test(
        srcs(
            lines(
                "var foo = {};",
                "/** @typedef {number} */ foo.Bar.Baz;",
                "foo.Bar = function() {};")),
        expected("var foo$Bar$Baz; var foo$Bar = function(){};"));
  }

  @Test
  public void testDelete1() {
    testSame(
        lines(
            "var foo = {};", //
            "foo.bar = 3;",
            "delete foo.bar;"));
  }

  @Test
  public void testDelete2() {
    test(
        srcs(
            lines(
                "var foo = {};", //
                "foo.bar = 3;",
                "foo.baz = 3;",
                "delete foo.bar;")),
        expected(
            lines(
                "var foo = {};", //
                "foo.bar = 3;",
                "var foo$baz = 3;",
                "delete foo.bar;")));
    testSame(
        lines(
            "var foo = {};", //
            "foo.bar = 3;",
            "foo.baz = 3;",
            "delete foo?.bar;"));
  }

  @Test
  public void testDelete3() {
    testSame(
        lines(
            "var foo = {bar: 3};", //
            "delete foo.bar;"));
  }

  @Test
  public void testDelete4() {
    test(
        srcs(
            lines(
                "var foo = {bar: 3, baz: 3};", //
                "delete foo.bar;")),
        expected("var foo$baz=3;var foo={bar:3};delete foo.bar"));
    testSame(
        lines(
            "var foo = {bar: 3, baz: 3};", //
            "delete foo?.bar;"));
  }

  @Test
  public void testDelete5() {
    test(
        srcs(
            lines(
                "var x = {};", //
                "x.foo = {};",
                "x.foo.bar = 3;",
                "delete x.foo.bar;")),
        expected(
            lines(
                "var x$foo = {};", //
                "x$foo.bar = 3;",
                "delete x$foo.bar;")));
  }

  @Test
  public void testDelete6() {
    test(
        srcs(
            lines(
                "var x = {};", //
                "x.foo = {};",
                "x.foo.bar = 3;",
                "x.foo.baz = 3;",
                "delete x.foo.bar;")),
        expected(
            lines(
                "var x$foo = {};", //
                "x$foo.bar = 3;",
                "var x$foo$baz = 3;",
                "delete x$foo.bar;")));
    test(
        srcs(
            lines(
                "var x = {};", //
                "x.foo = {};",
                "x.foo.bar = 3;",
                "x.foo.baz = 3;",
                "delete x.foo?.bar;")),
        expected(
            lines(
                "var x$foo = {};", //
                "x$foo.bar = 3;",
                "x$foo.baz = 3;",
                "delete x$foo?.bar;")));
  }

  @Test
  public void testDelete7() {
    test(
        srcs(
            lines(
                "var x = {};", //
                "x.foo = {bar: 3};",
                "delete x.foo.bar;")),
        expected(
            lines(
                "var x$foo = {bar: 3};", //
                "delete x$foo.bar;")));
  }

  @Test
  public void testDelete8() {
    test(
        srcs(
            lines(
                "var x = {};", //
                "x.foo = {bar: 3, baz: 3};",
                "delete x.foo.bar;")),
        expected(
            lines(
                "var x$foo$baz = 3; var x$foo = {bar: 3};", //
                "delete x$foo.bar;")));
    test(
        srcs(
            lines(
                "var x = {};", //
                "x.foo = {bar: 3, baz: 3};",
                "delete x.foo?.bar;")),
        expected(
            lines(
                "var x$foo = {bar:3, baz:3};", //
                "delete x$foo?.bar;")));
  }

  @Test
  public void testDelete9() {
    testSame(
        lines(
            "var x = {};", //
            "x.foo = {};",
            "x.foo.bar = 3;",
            "delete x.foo;"));
  }

  @Test
  public void testDelete10() {
    testSame(
        lines(
            "var x = {};", //
            "x.foo = {bar: 3};",
            "delete x.foo;"));
  }

  @Test
  public void testDelete11() {
    // Constructors are always collapsed.
    test(
        lines(
            "var x = {};",
            "x.foo = {};",
            "/** @constructor */ x.foo.Bar = function() {};",
            "delete x.foo;"),
        lines(
            "var x = {};",
            "x.foo = {};",
            "/** @constructor */ var x$foo$Bar = function() {};",
            "delete x.foo;"),
        warning(NAMESPACE_REDEFINED_WARNING));
    testSame(
        srcs(
            lines(
                "var x = {};",
                "x.foo = {};",
                "/** @constructor */",
                "x.foo.Bar = function() {};",
                "delete x?.foo;")),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testDelete11WithOptionalChain() {
    testWarning(
        lines(
            lines(
                "var x = {};",
                "x.foo = {};",
                "/** @constructor */ x.foo.Bar = function() {};",
                // It's weird, but it is allowed to delete an optional chain.
                "delete x?.foo;")),
        // The optional chain effectively creates an alias for `x`,
        // so we get a partial namespace warning instead of a namespase
        // redefined warning.
        PARTIAL_NAMESPACE_WARNING);
    testWarning(
        lines(
            lines(
                "var x = {};",
                "x.foo = {};",
                "/** @constructor */ x.foo.Bar = function() {};",
                "delete x.foo;",
                // The optional chain starts late enough that it doesn't generate
                // a partial namespace warning.
                "x.foo.Bar?.baz")),
        NAMESPACE_REDEFINED_WARNING);
  }

  @Test
  public void testPreserveConstructorDoc() {
    test(
        srcs("var foo = {}; /** @constructor */ foo.bar = function() {}"),
        expected("/** @constructor */ var foo$bar = function() {}"));
  }

  @Test
  public void testTypeDefAlias2() {

    // TODO(johnlenz): make CollapseProperties safer around aliases of
    // functions and object literals.  Currently, this pass trades correctness
    // for code size.  We should able to create a safer compromise by teaching
    // the pass about goog.inherits and similiar calls.
    test(
        srcs(
            lines(
                "/** @constructor */ var D = function() {};",
                "/** @constructor */ D.L = function() {};",
                "/** @type {D.L} */ D.L.A = new D.L();",
                "",
                "/** @const */ var M = {};",
                "if (random) { /** @typedef {D.L} */ M.L = D.L; }",
                "",
                "use(M.L);",
                "use(M.L.A);")),
        expected(
            lines(
                "/** @constructor */ var D = function() {};",
                "/** @constructor */ var D$L = function() {};",
                "/** @type {D.L} */ var D$L$A = new D$L();",
                "if (random) { /** @typedef {D.L} */ var M$L = D$L; }",
                "use(M$L);",
                "use(M$L.A);")));
    test(
        srcs(
            lines(
                "/** @constructor */ var D = function() {};",
                "/** @constructor */ D.L = function() {};",
                "/** @type {D.L} */ D.L.A = new D.L();",
                "",
                "/** @const */ var M = {};",
                "if (random) { /** @typedef {D.L} */ M.L = D?.L; }",
                "",
                "use?.(M?.L);",
                "use?.(M.L?.A);")),
        expected(
            lines(
                "/** @constructor */ var D = function() {};",
                "/** @constructor */ var D$L = function() {};",
                "/** @type {D.L} */ var D$L$A = new D$L();",
                "/** @const */ var M = {};",
                "if (random) { /** @typedef {D.L} */ M.L = D?.L; }",
                // Similar to above, these optional chain references are broken by collapsing.
                "use?.(M?.L);",
                "use?.(M.L?.A);")));
  }

  @Test
  public void testGlobalCatch() {
    testSame(
        lines(
            "try {", //
            "  throw Error();",
            "} catch (e) {",
            "  console.log(e.name)",
            "}"));
  }

  @Test
  public void testCtorManyAssignmentsDontInlineDontWarn() {
    test(
        srcs(
            lines(
                "var a = {};",
                "/** @constructor */ a.b = function() {};",
                "a.b.staticProp = 5;",
                "function f(y, z) {",
                "  var x = a.b;",
                "  if (y) {",
                "    x = z;",
                "  }",
                "  return new x();",
                "}")),
        expected(
            lines(
                lines(
                    "/** @constructor */", //
                    "var a$b = function() {};"),
                "var a$b$staticProp = 5;",
                "function f(y, z) {",
                "  var x = a$b;",
                "  if (y) {",
                "    x = z;",
                "  }",
                "  return new x();",
                "}")));
  }

  @Test
  public void testExpressionResultReferenceWontPreventCollapse() {
    test(
        srcs(
            lines(
                "var ns = {};", //
                "ns.Outer = {};",
                "",
                "ns.Outer;",
                "ns.Outer.Inner = function() {}\n")),
        expected(
            lines(
                "var ns$Outer={};", //
                "ns$Outer;",
                "var ns$Outer$Inner=function(){};\n")));
  }

  @Test
  public void testNoCollapseWithInvalidEnums() {
    test(
        srcs(
            lines(
                lines(
                    "/** @enum { { a: { b: number}} } */",
                    "var e = { KEY1: { a: { /** @nocollapse */ b: 123}},"),
                "  KEY2: { a: { b: 456}}",
                "}")),
        expected("var e$KEY1$a={/** @nocollapse */ b:123}; var e$KEY2$a$b=456;"));

    test(
        srcs(
            lines(
                "/** @enum */ var e = { A: 1, B: 2 };",
                lines(
                    "/** @type {{ c: { d: number } }} */ e.name1 = {",
                    "  c: { /** @nocollapse */ d: 123 } };"))),
        expected(
            "var e$A=1; var e$B=2; var e$name1$c={/** @nocollapse */ /** @nocollapse */ d:123};"));

    test(
        srcs(
            lines(
                "/** @enum */ var e = { A: 1, B: 2};",
                "/** @nocollapse */ e.foo = { bar: true };")),
        expected(
            lines(
                "var e$A=1;",
                "var e$B=2;",
                "/** @enum */ var e = {};",
                "/** @nocollapse */ e.foo = { bar: true }")));
  }

  @Test
  public void testDontCrashNamespaceAliasAcrossScopes() {
    test(
        lines(
            "var ns = {};",
            "ns.VALUE = 0.01;",
            "function f() {",
            "    var constants = ns;",
            "    (function() {",
            "       var x = constants.VALUE;",
            "    })();",
            "}"),
        null);
  }

  @Test
  public void testCollapsedNameAlreadyTaken() {
    test(
        srcs(
            lines(
                "/** @constructor */ function Funny$Name(){};",
                "function Funny(){};",
                "Funny.Name = 5;",
                "var x = new Funny$Name();")),
        expected(
            lines(
                "/** @constructor */ function Funny$Name(){};",
                "function Funny(){};",
                "var Funny$Name$1 = 5;",
                "var x = new Funny$Name();")));

    test(
        srcs("var ns$x = 0; var ns$x$0 = 1; var ns = {}; ns.x = 8;"),
        expected("var ns$x = 0; var ns$x$0 = 1; var ns$x$1 = 8;"));

    test(
        srcs("var ns$x = 0; var ns$x$0 = 1; var ns$x$1 = 2; var ns = {}; ns.x = 8;"),
        expected("var ns$x = 0; var ns$x$0 = 1; var ns$x$1 = 2; var ns$x$2 = 8;"));

    test(
        srcs("var ns$x = {}; ns$x.y = 2; var ns = {}; ns.x = {}; ns.x.y = 8;"),
        expected("var ns$x$y = 2; var ns$x$1$y = 8;"));

    test(
        srcs(
            lines(
                "var ns$x = {};",
                "ns$x.y = 1;",
                "var ns = {};",
                "ns.x$ = {};",
                "ns.x$.y = 2;",
                "ns.x = {};",
                "ns.x.y = 3")),
        expected(
            lines(
                "var ns$x$y = 1;", //
                "var ns$x$0$y = 2;",
                "var ns$x$1$y = 3;")));
  }

  // New Es6 Feature Tests - Some do not pass yet.
  @Test
  public void testArrowFunctionProperties() {
    // Add property to arrow function in local scope
    test(
        srcs("function a() {} () => { a.c = 5; return a.c; }"),
        expected("function a() {} var a$c; () => { a$c = 5; return a$c; }"));
    testSame("function a() {} () => { a.c = 5; return a?.c; }");

    // Reassign function property
    test(
        srcs("function a() {}; a.c = 1; () => { a.c = 5; }"),
        expected("function a() {}; var a$c = 1; () => { a$c = 5; }"));

    // Arrow function assignment
    test(
        srcs("var a = () => {}; function f() { a.c = 5; }"),
        expected("var a = () => {}; var a$c; function f() { a$c = 5; }"));
  }

  @Test
  public void testDestructuredProperiesObjectLit() {
    // Using destructuring shorthand
    test(
        srcs("var { a, b } = { a: {}, b: {} }; a.a = 5; var c = a.a; "),
        expected("var {a:a,b:b}={a:{},b:{}};a.a=5;var c=a.a"));

    // Without destructuring shorthand
    test(
        srcs("var { a:a, b:b } = { a: {}, b: {} }; a.a = 5; var c = a.a; "),
        expected("var {a:a,b:b}={a:{},b:{}};a.a=5;var c=a.a"));

    // Test with greater depth
    test(
        srcs("var { a, b } = { a: {}, b: {} }; a.a.a = 5; var c = a.a; var d = c.a;"),
        expected("var {a:a,b:b}={a:{},b:{}};a.a.a=5;var c=a.a;var d=c.a"));
  }

  @Test
  public void testConditionalDestructuringAssignment() {
    testSame(
        lines(
            "const X = { a: 1, b: 2 };", //
            // Destructuring assignment effectively creates an alias for
            // X and its properties.
            // Make sure conditional assignment doesn't hide this.
            "const {a, b} = false || X;",
            "console.log(a, b);",
            ""));
  }

  @Test
  public void testDestructuredClassAlias() {
    test(
        lines(
            "const ns = {}",
            "ns.Foo = class {};",
            "ns.Foo.STR = '';",
            "const {Foo} = ns;",
            "foo(Foo.STR);",
            ""),
        lines(
            "var ns$Foo = class {};",
            "var ns$Foo$STR = '';",
            "const Foo = null;",
            "foo(ns$Foo$STR);",
            ""));
  }

  @Test
  public void testCanCollapseSinglePropertyInObjectPattern() {
    // Aggressive alias inlining replaces the reference to `y` with `x.y`
    // and sets `y` to `null`.
    test(
        "const x   = {y: 1}; const {y} = x   ; use(  y);", //
        "var   x$y =     1 ; const  y  = null; use(x$y);");
  }

  @Test
  public void testCanCollapseSinglePropertyInObjectPatternWithDefaultValue() {
    testSame("const x = {y: 1}; const {y = 0} = x; use(y);");
  }

  @Test
  public void testCannotCollapsePropertyInNestedObjectPattern() {
    testSame("const x = {y: {z: 1}}; const {y: {z}} = x; use(z);");
  }

  @Test
  public void testCanCollapseSinglePropertyInObjectPatternAssign() {
    testSame("const x = {y: 1}; var y; ({y} = x); use(y);");
  }

  @Test
  public void testCanCollapseSinglePropertyInObjectPatternInForLoopClosure() {
    testSame("const x = {y: 1}; for (const {y} = x; true;) { use(() => y); }");
  }

  @Test
  public void testPropertyInArray() {
    testSame("var a, b = [{}, {}]; a.foo = 5; b.bar = 6;");

    test(
        srcs("var a = {}; a.b = 5; var c, d = [6, a.b]"),
        expected("var a$b = 5; var c, d = [6, a$b];"));
  }

  @Test
  public void testCollapsePropertySetInPattern() {
    // TODO(b/120303257): collapse lvalues in destructuring patterns. We delayed implementing this
    // because it's uncommon to have properties as lvalues in destructuring patterns.
    test(
        srcs("var a = {}; a.b = {}; [a.b.c, a.b.d] = [1, 2];"),
        expected("var a$b = {}; [a$b.c, a$b.d] = [1, 2];"));

    test(
        srcs("var a = {}; a.b = {}; ({x: a.b.c, y: a.b.d} = {});"),
        expected("var a$b = {}; ({x: a$b.c, y: a$b.d} = {});"));
  }

  @Test
  public void testDontCollapsePropertiesOfSpreadNamespace() {
    // Notice we can still collapse the parent namespace, `a`.
    test(
        srcs("var a = {b: {c: 0}}; var d = {...a.b}; use(a.b.c);"),
        expected("var a$b = {c: 0}; use(a$b.c);"));
    // test transpiled version of spread.
    test(
        "var a = {b: {c: 0}}; var d = Object.assign({}, a.b); use(a.b.c);",
        "var a$b = {c: 0}; var d = Object.assign({}, a$b); use(a$b.c);");
  }

  @Test
  public void testDontCollapsePropertiesOfRestedNamespace() {
    test(
        srcs("var a = {b: {c: 0}}; var {...d} = a.b; use(a.b.c);"),
        expected("var a$b = {c: 0}; var {...d} = a$b; use(a$b.c);"));
    testSame("var a = {b: {c: 0}}; var {...d} = a?.b; use?.(a.b?.c);");

    // "a.b.c" is not aliased by the REST in this case.
    test(
        srcs("var a = {b: {c: 0}}; var {d: {...d}} = a.b; use(a.b.c);"),
        expected("var a$b = {c: 0}; var {d: {...d}} = a$b; use(a$b.c);"));
    testSame("var a = {b: {c: 0}}; var {d: {...d}} = a?.b; use?.(a.b?.c);");
  }

  @Test
  public void testCollapsePropertiesWhenDeclaredInObjectLitWithSpread() {
    testSame("var a = {b: 0, ...c}; use(a.b);");
    // transpiled spread
    testSame("var a = Object.assign({}, {b: 0}, c); use(a.b);");
    testSame("var a = {b: 0, ...c}; use?.(a?.b);");

    testSame("var a = {...c}; use?.(a?.b);");

    test(srcs("var a = {...c, b: 0}; use(a.b);"), expected("var a$b = 0; use(a$b);"));
    // test transpiled - does not collapse properties after spread.
    testSame("var a = Object.assign({}, c, {b: 0}); use(a.b);");

    testSame(
        lines(
            "var a = {...c, b: 0};", //
            "use?.(a?.b);"));
  }

  @Test
  public void testComputedPropertyNames() {
    // Computed property in object literal. This following test code is bad style - it does not
    // follow the assumptions of the pass and thus produces the following output.

    test(
        srcs(
            lines(
                "var a = {", //
                "  ['val' + ++i]: i,",
                "  ['val' + ++i]: i",
                "};",
                "a.val1;")),
        expected(
            lines(
                "var a = {", //
                "  ['val' + ++i]: i,",
                "  ['val' + ++i]: i",
                "};",
                "var a$val1;")));

    test(
        srcs("var a = { ['val']: i, ['val']: i }; a.val;"),
        expected("var a = { ['val']: i, ['val']: i }; var a$val;"));

    // Computed property method name in class
    testSame(
        lines(
            "class Bar {",
            "  constructor(){}",
            "  ['f'+'oo']() {",
            "    return 1",
            "  }",
            "}",
            "var bar = new Bar()",
            "bar.foo();"));

    // Computed property method name in class - no concatenation
    testSame(
        lines(
            "class Bar {",
            "  constructor(){}",
            "  ['foo']() {",
            "    return 1",
            "  }",
            "}",
            "var bar = new Bar()",
            "bar.foo();"));
  }

  @Test
  public void testClassGetSetMembers() {
    // Get and set methods
    testSame(
        lines(
            "class Bar {",
            "  constructor(x) {",
            "    this.x = x;",
            "  }",
            "  get foo() {",
            "    return this.x;",
            "  }",
            "  set foo(xIn) {",
            "    x = xIn;",
            "  }",
            "}",
            "var bar = new Bar(1);",
            "bar.foo;",
            "bar.foo = 2;"));
  }

  @Test
  public void testClassNonStaticMembers() {
    // Call class method inside class scope
    testSame(
        lines(
            "function getA() {};",
            "class Bar {",
            "  constructor(){}",
            "  getA() {",
            "    return 1;",
            "  }",
            "  getB(x) {",
            "    this.getA();",
            "  }",
            "}"));

    // Call class method outside class scope
    testSame(
        lines(
            "class Bar {",
            "  constructor(){}",
            "  getB(x) {}",
            "}",
            "var too;",
            "var too = new Bar();",
            "too.getB(too);"));

    // Non-static method
    testSame(
        lines(
            "class Bar {",
            "  constructor(x){",
            "    this.x = x;",
            "  }",
            "  double() {",
            "    return x*2;",
            "  }",
            "}",
            "var too;",
            "var too = new Bar();",
            "too.double(1);"));
  }

  @Test
  public void testClassDeclarationWithStaticMembers() {
    test(
        srcs("class Bar { static double(n) { return n*2; } } Bar.double(1);"),
        expected("var Bar$double = function(n) { return n * 2; }; class Bar {} Bar$double(1);"));
  }

  @Test
  public void testClassAssignmentWithStaticMembers() {
    test(
        srcs(
            lines(
                "const ns = {};",
                "ns.Bar = class {",
                "  static double(n) { return n*2; }",
                "}",
                "ns.Bar.double(1)")),
        expected(
            lines(
                "var ns$Bar$double = function(n) { return n * 2; }",
                "var ns$Bar = class {}",
                "ns$Bar$double(1);")));
  }

  @Test
  public void testClassWithMultipleStaticMembers() {
    test(
        srcs(
            lines(
                "class Bar {",
                "  static double(n) {",
                "    return n*2",
                "  }",
                "  static triple(n) {",
                "    return n*3;",
                "  }",
                "}",
                "Bar.double(1);",
                "Bar.triple(2);")),
        expected(
            lines(
                "var Bar$double = function(n) { return n * 2; }",
                "var Bar$triple = function(n) { return n * 3; }",
                "class Bar {}",
                "Bar$double(1);",
                "Bar$triple(2);")));
  }

  @Test
  public void testClassStaticMembersWithAdditionalUndeclaredProperty() {
    test(
        srcs(
            lines(
                "class Bar {",
                "  static double(n) {",
                "    return n*2",
                "  }",
                "}",
                "use(Bar.double.trouble);")),
        expected(
            lines(
                "var Bar$double = function(n) { return n * 2; }",
                "class Bar {}",
                "use(Bar$double.trouble);")));
  }

  @Test
  public void testClassStaticMembersWithAdditionalPropertySetLocally() {
    test(
        srcs(
            lines(
                "class Bar {",
                "  static double(n) {",
                "    return n*2",
                "  }",
                "}",
                "(function() { Bar.double.trouble = -1; })();",
                "use(Bar.double.trouble);")),
        expected(
            lines(
                "var Bar$double = function(n) { return n * 2; }",
                "class Bar {}",
                "var Bar$double$trouble;",
                "(function() { Bar$double$trouble = -1; })();",
                "use(Bar$double$trouble);")));
    test(
        srcs(
            lines(
                "class Bar {",
                "  static double(n) {",
                "    return n*2",
                "  }",
                "}",
                "(function() { Bar.double.trouble = -1; })();",
                "use?.(Bar.double?.trouble);")),
        expected(
            lines(
                "var Bar$double = function(n) { return n * 2; };",
                "class Bar {}",
                "(function() { Bar$double.trouble = -1; })();",
                "use?.(Bar$double?.trouble);")));
  }

  @Test
  public void testClassStaticMembersWithAdditionalPropertySetGlobally() {
    test(
        srcs(
            lines(
                "class Bar {",
                "  static double(n) {",
                "    return n*2",
                "  }",
                "}",
                "Bar.double.trouble = -1;",
                "use(Bar.double.trouble);")),
        expected(
            lines(
                "var Bar$double = function(n) { return n * 2; }",
                "class Bar {}",
                "var Bar$double$trouble = -1;",
                "use(Bar$double$trouble);")));
    test(
        srcs(
            lines(
                "class Bar {",
                "  static double(n) {",
                "    return n*2",
                "  }",
                "}",
                "Bar.double.trouble = -1;",
                "use?.(Bar.double?.trouble);")),
        expected(
            lines(
                "var Bar$double = function(n) {",
                "  return n * 2;",
                "}",
                "class Bar {",
                "}",
                "Bar$double.trouble = -1;",
                "use?.(Bar$double?.trouble);")));
  }

  @Test
  public void testClassDeclarationWithEscapedStaticMember_collapsesMemberButNotMemberProp() {
    test(
        srcs(
            lines(
                "class Bar {", //
                "  static double(n) {",
                "    return n*2",
                "  }",
                "}",
                "Bar.double.trouble = 4;",
                "use(Bar.double);",
                "use(Bar.double.trouble);")),
        expected(
            lines(
                "var Bar$double = function(n) { return n * 2; }",
                "class Bar {}",
                // don't collapse this property because Bar$double is escaped
                "Bar$double.trouble = 4;",
                "use(Bar$double);",
                "use(Bar$double.trouble);")));
    test(
        srcs(
            lines(
                "class Bar {", //
                "  static double(n) {",
                "    return n*2",
                "  }",
                "}",
                "Bar.double.trouble = 4;",
                "use?.(Bar?.double);",
                "use?.(Bar.double?.trouble);")),
        expected(
            lines(
                "var Bar$double = function(n) {",
                "  return n * 2;",
                "};",
                "class Bar {",
                "}",
                "Bar$double.trouble = 4;",
                // TODO(b/148237949): collapsing breaks `Bar?.double` reference
                "use?.(Bar?.double);",
                "use?.(Bar$double?.trouble);")));
  }

  @Test
  public void testClassDeclarationWithStaticMembersWithNoCollapse() {
    testSame(
        lines(
            "class Bar {",
            "  /** @nocollapse */",
            "  static double(n) {",
            "    return n*2",
            "  }",
            "}",
            "Bar.double(1);"));
  }

  @Test
  public void testClassStaticMemberWithUnreferencedInnerName() {
    test(
        srcs(
            lines(
                "const Bar = class BarInternal {",
                "  static baz(n) {",
                "    use(n);",
                "  }",
                "}",
                "Bar.baz();")),
        expected(
            lines(
                "var Bar$baz = function(n) { use(n); }",
                "const Bar = class BarInternal {}",
                "Bar$baz();")));
  }

  @Test
  public void testDontCollapseClassStaticMemberReferencingInnerName() {
    // probably we could do some rewriting to make this work, but for now just back off.
    testSame(
        lines(
            "const Bar = class BarInternal {",
            "  static baz(n) {",
            "    use(BarInternal);",
            "  }",
            "}",
            "Bar.baz();"));
  }

  @Test
  public void testDontCollapseLateAddedClassStaticMemberReferencedByInnerName() {
    test(
        srcs(
            lines(
                "const Bar = class BarInternal {",
                "  method() {",
                "    return BarInternal.Enum.E1;", // ref via inner name
                "  }",
                "}",
                "/** @enum {number} */",
                "Bar.Enum = { E1: 1 };", // added after class definition
                "")),
        expected(
            lines(
                "const Bar = class BarInternal {", //
                "  method() {",
                "    return Bar$Enum$E1;",
                "  }",
                "};",
                "var Bar$Enum$E1 = 1;",
                "")));
  }

  @Test
  public void testDontCollapseClassStaticMemberReferencingInnerNameInNestedFunction() {
    // probably we could do some rewriting to make this work, but for now just back off.
    testSame(
        lines(
            "const Bar = class BarInternal {",
            "  static baz(n) {",
            "    return function() { use(BarInternal); };",
            "  }",
            "}",
            "Bar.baz();"));
  }

  @Test
  public void testClassStaticMemberUsingSuperNotCollapsed() {
    testSame(
        lines(
            "class Baz extends Bar() {",
            "  static quadruple(n) {",
            "    return 2 * super.double(n);",
            " }",
            "}"));
  }

  @Test
  public void testClassStaticMemberUsingSuperInArrowFnNotCollapsed() {
    testSame(
        lines(
            "class Baz extends Bar() {",
            "  static quadruple(n) {",
            "    return () => 2 * super.double(n);",
            " }",
            "}"));
  }

  @Test
  public void testClassStaticMemberWithInnerClassUsingSuperIsCollapsed() {
    test(
        srcs(
            lines(
                "class Bar extends fn() {",
                "  static m() {",
                "    class Inner extends fn() {",
                "      static n() { super.n(); } ", // does not block collapsing Bar.m
                "    }",
                "  }",
                "}")),
        expected(
            lines(
                "var Bar$m = function() {",
                "  class Inner extends fn() {",
                "    static n() { super.n(); } ",
                "  }",
                "};",
                "class Bar extends fn() {}")));
  }

  @Test
  public void testEs6StaticMemberAddedAfterDefinition() {
    test(
        srcs(
            lines(
                "class Bar {}", //
                "Bar.double = function(n) {",
                "  return n*2",
                "}",
                "Bar.double(1);")),
        expected(
            lines(
                "class Bar {}",
                "var Bar$double = function(n) {",
                "  return n*2",
                "}",
                "Bar$double(1);")));
  }

  @Test
  public void testEs6StaticMemberOnEscapedClassIsCollapsed() {
    test(
        srcs("class Bar { static m() {} } use(Bar);"),
        expected("var Bar$m = function() {}; class Bar {} use(Bar);"));
  }

  @Test
  public void testClassStaticProperties() {
    test(
        srcs("class A {} A.foo = 'bar'; use(A.foo);"),
        expected("class A {} var A$foo = 'bar'; use(A$foo);"));
    test(
        srcs("class A {}     A.foo = 'bar'; use?.(A?.foo);"),
        expected("class A {} var A$foo = 'bar'; use?.(A?.foo);"));

    // Collapsing A.foo is known to be unsafe.
    test(
        srcs("class A { static useFoo() { alert(this.foo); } } A.foo = 'bar'; A.useFoo();"),
        expected(
            lines(
                "var A$useFoo = function() { alert(this.foo); };",
                "class A {}",
                "var A$foo = 'bar';",
                "A$useFoo();")),
        warning(RECEIVER_AFFECTED_BY_COLLAPSE));

    test(
        srcs(
            lines(
                "class A {",
                "  static useFoo() {",
                "    alert(this.foo);",
                "  }",
                "}",
                "/** @nocollapse */",
                "A.foo = 'bar';",
                "A.useFoo();")),
        expected(
            lines(
                "var A$useFoo = function() { alert(this.foo); }; ",
                "class A {}",
                "/** @nocollapse */",
                "A.foo = 'bar';",
                "A$useFoo();")),
        warning(RECEIVER_AFFECTED_BY_COLLAPSE));
  }

  @Test
  public void testClassStaticAndPrototypePropWithSameName() {
    test(
        srcs("const ns = {}; ns.C = class { x() {} }; ns.C.x = 3;"),
        expected("var ns$C = class { x() {} }; var ns$C$x = 3;"));
  }

  @Test
  public void testClassStaticProperties_locallyDeclared1() {
    test(
        srcs(
            lines(
                "class A {}",
                "function addStaticPropToA() {",
                "  A.staticProp = 5;",
                "}",
                "if (A.staticProp) {",
                "  use(A.staticProp);",
                "}")),
        expected(
            lines(
                "class A {}",
                "var A$staticProp;",
                "function addStaticPropToA() {",
                "  A$staticProp = 5;",
                "}",
                "if (A$staticProp) {",
                "  use(A$staticProp);",
                "}")));
    test(
        srcs(
            lines(
                "class A {}",
                "function addStaticPropToA() {",
                "  A.staticProp = 5;",
                "}",
                "if (A?.staticProp) {",
                "  use?.(A?.staticProp);",
                "}")),
        expected(
            lines(
                "class A {}",
                "var A$staticProp;",
                "function addStaticPropToA() {",
                "  A$staticProp = 5;",
                "}",
                // TODO(b/148237949): collapsing breaks `A?.staticProp` reference
                "if (A?.staticProp) {",
                "  use?.(A?.staticProp);",
                "}")));
  }

  @Test
  public void testClassStaticProperties_locallyDeclared2() {
    test(
        srcs(
            lines(
                "const A = class {}",
                "function addStaticPropToA() {",
                "  A.staticProp = 5;",
                "}",
                "if (A.staticProp) {",
                "  use(A.staticProp);",
                "}")),
        expected(
            lines(
                "const A = class {}",
                "var A$staticProp;",
                "function addStaticPropToA() {",
                "  A$staticProp = 5;",
                "}",
                "if (A$staticProp) {",
                "  use(A$staticProp);",
                "}")));
    test(
        srcs(
            lines(
                "const A = class {}",
                "function addStaticPropToA() {",
                "  A.staticProp = 5;",
                "}",
                "if (A?.staticProp) {",
                "  use?.(A?.staticProp);",
                "}")),
        expected(
            lines(
                "const A = class {}",
                "var A$staticProp;",
                "function addStaticPropToA() {",
                "  A$staticProp = 5;",
                "}",
                // TODO(b/148237949): collapsing breaks `A?.staticProp` reference
                "if (A?.staticProp) {",
                "  use?.(A?.staticProp);",
                "}")));
  }

  @Test
  public void testEs6ClassStaticInheritance() {

    test(
        srcs("class A {} A.foo = 5; use(A.foo); class B extends A {}"),
        expected("class A {} var A$foo = 5; use(A$foo); class B extends A {}"));
    test(
        srcs("class A {}     A.foo = 5; use?.(A?.foo); class B extends A {}"),
        expected("class A {} var A$foo = 5; use?.(A?.foo); class B extends A {}"));

    // We potentially collapse unsafely when the subclass accesses a static property on its
    // superclass. However, AggressiveInlineAliases tries to rewrite inherited accesses to make this
    // collapsing safe. See InlineAndCollapsePropertiesTests for examples.
    test(
        srcs("    class A {}     A.foo = 5; use(A.foo); class B extends A {} use(B.foo);"),
        expected("class A {} var A$foo = 5; use(A$foo); class B extends A {} use(A$foo);"));
    test(
        srcs("class A {}     A.foo = 5; use?.(A?.foo); class B extends A {} use?.(B?.foo);"),
        expected("class A {} var A$foo = 5; use?.(A?.foo); class B extends A {} use?.(B?.foo);"));

    test(
        srcs(
            lines(
                "class A {}",
                "A.foo = 5;",
                "class B extends A {}",
                "use(B.foo);",
                "B.foo = 6;",
                "use(B.foo)")),
        expected(
            lines(
                "class A {}",
                "var A$foo = 5;",
                "class B extends A {}",
                "use(B$foo);",
                "var B$foo = 6;",
                "use(B$foo)")));
    test(
        srcs(
            lines(
                "class A {}",
                "A.foo = 5;",
                "class B extends A {}",
                "use(B?.foo);",
                "B.foo = 6;",
                "use(B?.foo)")),
        expected(
            lines(
                "class A {}",
                "var A$foo = 5;",
                "class B extends A {}",
                "use(B?.foo);",
                "var B$foo = 6;",
                "use(B?.foo)")));

    testSame(
        lines(
            "class A {}",
            "/** @nocollapse */",
            "A.foo = 5;",
            "class B extends A {}",
            "use(B.foo);",
            "/** @nocollapse */",
            "B.foo = 6;",
            "use(B.foo);"));
  }

  @Test
  public void testEs6ClassExtendsChildClass() {

    test(
        srcs(
            lines(
                "class Thing {}",
                "Thing.Builder = class {};",
                "class Subthing {}",
                "Subthing.Builder = class extends Thing.Builder {}")),
        expected(
            lines(
                "class Thing {}",
                "var Thing$Builder = class {};",
                "class Subthing {}",
                "var Subthing$Builder = class extends Thing$Builder {}")));
  }

  @Test
  public void testSuperExtern() {
    testSame(
        lines(
            "class Foo {",
            "  constructor(){",
            "    this.x = x; ",
            "  }",
            "  getX() {",
            "    return this.x;",
            "  }",
            "}",
            "class Bar extends Foo {",
            "  constructor(x, y) {",
            "    super(x);",
            "    this.y = y;",
            "  }",
            "  getX() {",
            "    return super.getX() + this.y;",
            "  }",
            "}",
            "let too = new Bar();",
            "too.getX();"));
  }

  @Test
  public void testPropertyMethodAssignment_receiverAffectedByCollapse() {
    // ES5 version
    test(
        lines(
            "var foo = { ",
            "  bar: 1, ",
            "  myFunc: function myFunc() {",
            "    return this.bar",
            "  }",
            "};",
            "foo.myFunc();"),
        lines(
            "var foo$bar = 1;",
            "var foo$myFunc = function myFunc() {",
            "  return this.bar",
            "};",
            "foo$myFunc();"),
        warning(InlineAndCollapseProperties.RECEIVER_AFFECTED_BY_COLLAPSE));

    test(
        srcs(
            lines(
                "var foo = { ",
                "  bar: 1, ",
                "  myFunc: function myFunc() {",
                "    function inner() {",
                "      return this.bar;",
                "    }",
                "  }",
                "};",
                "foo.myFunc();")),
        expected(
            lines(
                "var foo$bar = 1;",
                "var foo$myFunc = function myFunc() {",
                "  function inner() {",
                "    return this.bar;",
                "  }",
                "};",
                "foo$myFunc();")));

    // ES6 version
    test(
        lines(
            "var foo = { ",
            "  bar: 1, ",
            "  myFunc() {",
            "    return this.bar;",
            "  }",
            "};",
            "foo.myFunc();"),
        lines(
            "var foo$bar = 1;",
            "var foo$myFunc = function() {",
            "  return this.bar",
            "};",
            "foo$myFunc();"),
        warning(InlineAndCollapseProperties.RECEIVER_AFFECTED_BY_COLLAPSE));

    // "this" is lexically scoped in arrow functions so collapsing is safe.
    test(
        srcs(
            lines(
                "var foo = { ", //
                "  myFunc: () => {",
                "    return this;",
                "  }",
                "};",
                "foo.myFunc();")),
        expected(
            lines(
                "var foo$myFunc = () => {", //
                "  return this",
                "};",
                "foo$myFunc();")));

    // but not in inner functions
    test(
        lines(
            "var foo = {", //
            "  myFunc: function() {",
            "    return (() => this);",
            "  }",
            "};",
            "foo.myFunc();"),
        lines(
            "var foo$myFunc = function() {", //
            "  return (() => this);",
            "};",
            "foo$myFunc();"),
        warning(InlineAndCollapseProperties.RECEIVER_AFFECTED_BY_COLLAPSE));

    // `super` is also a receiver reference
    // TODO(b/122665204): Make this case pass rather than crash.
    assertThrows(
        Exception.class,
        () ->
            test(
                lines(
                    "var foo = { ",
                    "  bar: 1, ",
                    "  myFunc() {",
                    "    return super.bar;",
                    "  }",
                    "};",
                    "foo.myFunc();"),
                lines(
                    "var foo$bar = 1;",
                    "var foo$myFunc = function() {",
                    "  return super.bar", // Unclear what goes here.
                    "};",
                    "foo$myFunc();"),
                warning(InlineAndCollapseProperties.RECEIVER_AFFECTED_BY_COLLAPSE)));

    // check references in param lists
    test(
        lines(
            "var foo = { ", //
            "  bar: 1, ",
            "  myFunc(x = this) {",
            "  }",
            "};",
            "foo.myFunc();"),
        lines(
            "var foo$bar = 1;", //
            "var foo$myFunc = function(x = this) {",
            "};",
            "foo$myFunc();"),
        warning(InlineAndCollapseProperties.RECEIVER_AFFECTED_BY_COLLAPSE));
  }

  @Test
  public void testPropertyMethodAssignment_noThis() {
    // ES5 Version
    test(
        srcs(
            lines(
                "var foo = { ",
                "  bar: 1, ",
                "  myFunc: function myFunc() {",
                "    return 5",
                "  }",
                "};",
                "foo.myFunc();")),
        expected(
            lines(
                "var foo$bar = 1;",
                "var foo$myFunc = function myFunc() {",
                "    return 5;",
                "};",
                "foo$myFunc();")));

    // ES6 version
    test(
        srcs(
            lines(
                "var foo = { ",
                "  bar: 1, ",
                "  myFunc() {",
                "    return 5;",
                "  }",
                "};",
                "foo.myFunc();")),
        expected(
            lines(
                "var foo$bar = 1;",
                "var foo$myFunc = function() {",
                "    return 5;",
                "};",
                "foo$myFunc();")));
  }

  @Test
  public void testMethodPropertyShorthand() {
    test(
        srcs(
            lines(
                "var foo = { ",
                "  bar: 1, ",
                "   myFunc() {",
                "    return 2",
                "  }",
                "};",
                "foo.myFunc();")),
        expected(
            lines(
                "var foo$bar = 1;",
                "var foo$myFunc = function() {",
                "    return 2;",
                "};",
                "foo$myFunc();")));
  }

  @Test
  public void testLetConstObjectAssignmentProperties() {

    // All qualified names - even for variables that are initially declared as LETS and CONSTS -
    // are being declared as VAR statements, but this is correct because we are only
    // collapsing for global names.

    test(
        srcs("let a = {}; a.b = {}; a.b.c = {}; let d = 1; d = a.b.c;"),
        expected("var a$b$c = {}; let d = 1; d = a$b$c;"));
    test(
        srcs("let a = {}; a.b = {}; a.b.c = {}; let d = 1; d = a.b?.c;"),
        expected("var         a$b = {}; a$b.c = {}; let d = 1; d = a$b?.c;"));

    test(srcs("let a = {}; if(1)  { a.b = 1; }"), expected("if(1) { var a$b = 1; }"));

    testSame("if(1) { let a = {}; a.b = 1; }");

    test(
        srcs("var a = {}; a.b = 1; if(1) { let a = {}; a.b = 2; }"),
        expected("var a$b = 1; if(1) { let a$jscomp$1 = {}; a$jscomp$1.b = 2; }"));
  }

  @Test
  public void testTemplateStrings() {
    test(
        srcs(
            lines(
                "var a = {};", //
                "a.b = 'foo';",
                "var c = `Hi ${a.b}`;")),
        expected(
            lines(
                "var a$b = 'foo';", //
                "var c = `Hi ${a$b}`;")));
  }

  @Test
  public void testDoesNotCollapseInEs6ModuleScope() {
    testSame("var a = {}; a.b = {}; a.b.c = 5; export default function() {};");

    ignoreWarnings(LOAD_WARNING);
    test(
        srcs("import * as a from './a.js'; let b = a.b;", "var a = {}; a.b = 5;"),
        expected("import * as a$jscomp$1 from './a.js'; let b = a$jscomp$1.b;", "var a$b = 5;"));
  }

  @Test
  public void testDefaultParameters() {
    testSame("var a = {b: 5}; function f(x=a) { alert(x.b); }");
    testSame("var a = {b: 5}; function f(x=a) { alert?.(x?.b); }");

    test(
        srcs("var a   = {b: {c: 5}}; function f(x=a.b) { alert(x.c); }"),
        expected("var a$b =     {c: 5} ; function f(x=a$b) { alert(x.c); }"));
    testSame("var a = {b: {c: 5}}; function f(x=a?.b) { alert?.(x?.c); }");

    test(
        srcs("var a = {b: 5}; function f(x=a.b) { alert(x); }"),
        expected("var a$b = 5; function f(x=a$b) { alert(x); }"));
    testSame("var a = {b: 5}; function f(x=a?.b) { alert?.(x); }");
  }

  @Test
  public void testModuleExportsBasicCommonJs() {

    this.setupModuleExportsOnly();

    ArrayList<SourceFile> inputs = new ArrayList<>();
    inputs.add(SourceFile.fromCode("mod1.js", "module.exports = 123;"));
    inputs.add(SourceFile.fromCode("entry.js", "var mod = require('./mod1.js'); alert(mod);"));

    ArrayList<SourceFile> expected = new ArrayList<>();
    expected.add(
        SourceFile.fromCode(
            "mod1.js",
            "/** @const */ var module$mod1={}; /** @const */ var module$mod1$default = 123;"));
    expected.add(
        SourceFile.fromCode(
            "entry.js", "var mod = module$mod1$default; alert(module$mod1$default);"));

    test(srcs(inputs), expected(expected));
  }

  @Test
  public void testModuleExportsBasicEsm() {

    this.setupModuleExportsOnly();

    ArrayList<SourceFile> inputs = new ArrayList<>();
    inputs.add(SourceFile.fromCode("mod1.js", "export default 123; export var bar = 'bar'"));
    inputs.add(
        SourceFile.fromCode(
            "entry.js", "import mod, {bar} from './mod1.js'; alert(mod); alert(bar)"));

    ArrayList<SourceFile> expected = new ArrayList<>();
    expected.add(
        SourceFile.fromCode(
            "mod1.js",
            lines(
                "var $jscompDefaultExport$$module$mod1=123;",
                "var bar$$module$mod1 = 'bar';",
                "/** @const */ var module$mod1={};",
                "/** @const */ var module$mod1$bar = bar$$module$mod1",
                "/** @const */ var module$mod1$default = $jscompDefaultExport$$module$mod1;")));
    expected.add(
        SourceFile.fromCode(
            "entry.js", "alert($jscompDefaultExport$$module$mod1); alert(bar$$module$mod1);"));

    test(srcs(inputs), expected(expected));
  }

  @Test
  public void testMutableModuleExportsBasicEsm() {

    this.setupModuleExportsOnly();

    ArrayList<SourceFile> inputs = new ArrayList<>();
    inputs.add(
        SourceFile.fromCode(
            "mod1.js",
            lines(
                "export default 123;", //
                "export var bar = 'ba';",
                "function f() { bar += 'r'; }")));
    inputs.add(
        SourceFile.fromCode(
            "entry.js", "import mod, {bar} from './mod1.js'; alert(mod); alert(bar)"));

    ArrayList<SourceFile> expected = new ArrayList<>();
    expected.add(
        SourceFile.fromCode(
            "mod1.js",
            lines(
                "var $jscompDefaultExport$$module$mod1=123;",
                "var bar$$module$mod1 = 'ba';",
                "function f$$module$mod1() { bar$$module$mod1 += 'r'; }",
                "/** @const */ var module$mod1= {",
                "  /** @return {?} */ get bar() { return bar$$module$mod1; },",
                "};",
                "/** @const */ var module$mod1$default = $jscompDefaultExport$$module$mod1;")));
    expected.add(
        SourceFile.fromCode(
            "entry.js",
            lines(
                "alert($jscompDefaultExport$$module$mod1);", //
                "alert(bar$$module$mod1);")));

    test(srcs(inputs), expected(expected));
  }

  @Test
  public void testModuleExportsObjectCommonJs() {

    this.setupModuleExportsOnly();

    ArrayList<SourceFile> inputs = new ArrayList<>();
    inputs.add(
        SourceFile.fromCode(
            "mod1.js",
            LINE_JOINER.join(
                "var foo ={};", "foo.bar = {};", "foo.bar.baz = 123;", "module.exports = foo;")));
    inputs.add(
        SourceFile.fromCode("entry.js", "var mod = require('./mod1.js'); alert(mod.bar.baz);"));

    ArrayList<SourceFile> expected = new ArrayList<>();
    expected.add(
        SourceFile.fromCode(
            "mod1.js",
            LINE_JOINER.join(
                "/** @const */ var module$mod1={};",
                " /** @const */ var module$mod1$default = {};",
                "module$mod1$default.bar = {};",
                "module$mod1$default.bar.baz = 123;")));
    expected.add(
        SourceFile.fromCode(
            "entry.js", "var mod = module$mod1$default; alert(module$mod1$default.bar.baz);"));

    test(srcs(inputs), expected(expected));
  }

  @Test
  public void testModuleExportsObjectEsm() {

    this.setupModuleExportsOnly();

    ArrayList<SourceFile> inputs = new ArrayList<>();
    inputs.add(
        SourceFile.fromCode(
            "mod1.js",
            lines(
                "var foo ={};", //
                "foo.bar = {};",
                "foo.bar.baz = 123;",
                "export default foo;")));
    inputs.add(SourceFile.fromCode("entry.js", "import mod from './mod1.js'; alert(mod.bar.baz);"));

    ArrayList<SourceFile> expected = new ArrayList<>();
    expected.add(
        SourceFile.fromCode(
            "mod1.js",
            lines(
                "var foo$$module$mod1 = {};",
                "foo$$module$mod1.bar = {};",
                "foo$$module$mod1.bar.baz = 123;",
                "var $jscompDefaultExport$$module$mod1 = foo$$module$mod1;",
                "/** @const */ var module$mod1={};",
                "/** @const */ var module$mod1$default = $jscompDefaultExport$$module$mod1;")));
    expected.add(
        SourceFile.fromCode("entry.js", "alert($jscompDefaultExport$$module$mod1.bar.baz);"));

    test(srcs(inputs), expected(expected));
  }

  @Test
  public void testModuleExportsObjectSubPropertyCommonJs() {

    this.setupModuleExportsOnly();

    ArrayList<SourceFile> inputs = new ArrayList<>();
    inputs.add(
        SourceFile.fromCode(
            "mod1.js",
            LINE_JOINER.join(
                "var foo ={};", "module.exports = foo;", "module.exports.bar = 'bar';")));
    inputs.add(SourceFile.fromCode("entry.js", "var mod = require('./mod1.js'); alert(mod.bar);"));

    ArrayList<SourceFile> expected = new ArrayList<>();
    expected.add(
        SourceFile.fromCode(
            "mod1.js",
            LINE_JOINER.join(
                "/** @const */ var module$mod1={};",
                " /** @const */ var module$mod1$default = {};",
                "module$mod1$default.bar = 'bar';")));
    expected.add(
        SourceFile.fromCode(
            "entry.js", "var mod = module$mod1$default; alert(module$mod1$default.bar);"));

    test(srcs(inputs), expected(expected));
  }

  @Test
  public void testOrExpression() {
    // left branch, read declared prop
    testSame("var a = {b: 1} || x; var t = a.b;");

    // left branch, read undeclared prop
    testSame("var a = {b: 1} || x; var t = a.c;");

    // left branch, write to declared prop
    testSame("var a = {b: 1} || x; a.b = 2;");

    // left branch, write to undeclared prop
    // TODO(tjgq): Consider also collapsing a.c in this case.
    testSame("var a = {b: 1} || x; a.c = 2;");

    // right branch, read declared prop
    // aggressive alias inlining still works
    test(
        "var a = x || {b: 1}; var t =  a.b; use(  t);", //
        "var a = x || {b: 1}; var t = null; use(a.b);");

    // right branch, read undeclared prop
    testSame("var a = x || {b: 1}; var t = a.c;");

    // right branch, write to declared prop
    testSame("var a = x || {b: 1}; a.b = 2;");

    // right branch, write to undeclared prop
    test(srcs("var a = x || {b: 1}; a.c = 2;"), expected("var a = x || {b: 1}; var a$c = 2;"));
  }

  @Test
  public void testTernaryExpression() {
    // left branch, read declared prop
    // aggressive alias inlining still works
    test(
        "var a = p ? {b: 1} : x; var t =  a.b; use(  t);", //
        "var a = p ? {b: 1} : x; var t = null; use(a.b);");

    // left branch, read undeclared prop
    testSame("var a = p ? {b: 1} : x; var t = a.c;");

    // left branch, write to declared prop
    testSame("var a = p ? {b: 1} : x; a.b = 2;");

    // left branch, write to undeclared prop
    test(
        srcs("var a = p ? {b: 1} : x; a.c = 2;"), expected("var a = p ? {b: 1} : x; var a$c = 2;"));

    // right branch, read declared prop
    // aggressive alias inlining still works
    test(
        "var a = p ? x : {b: 1}; var t =  a.b; use(  t);", //
        "var a = p ? x : {b: 1}; var t = null; use(a.b);");

    // right branch, read undeclared prop
    testSame("var a = p ? x : {b: 1}; var t = a.c;");

    // right branch, write to declared prop
    testSame("var a = p ? x : {b: 1}; a.b = 2;");

    // right branch, write to undeclared prop
    test(
        srcs("var a = p ? x : {b: 1}; a.c = 2;"), expected("var a = p ? x : {b: 1}; var a$c = 2;"));

    // right branch, use nested prop, alias is inlined but prop is not collapsed.
    test(
        srcs("    var a = p ? x : {b: { insideB: 1 }}; var t = a.b.insideB; use(          t);"),
        expected("var a = p ? x : {b: { insideB: 1 }}; var t = null       ; use(a.b.insideB);"));
  }

  @Test
  public void testModuleExportsOnly() {
    this.setupModuleExportsOnly();

    ArrayList<SourceFile> inputs = new ArrayList<>();
    inputs.add(
        SourceFile.fromCode(
            "entry.js",
            lines(
                "var mod = require('./mod1.js');",
                "alert(mod);",
                "var a = {};",
                "a.b = {};",
                "/** @constructor */",
                "a.b.c = function(){};")));
    inputs.add(SourceFile.fromCode("mod1.js", "module.exports = 123;"));

    ArrayList<SourceFile> expected = new ArrayList<>();
    expected.add(
        SourceFile.fromCode(
            "entry.js",
            lines(
                "var mod = module$mod1$default;",
                "alert(module$mod1$default);",
                "var a = {};",
                "a.b = {};",
                "/** @constructor */",
                "a.b.c = function(){};")));
    expected.add(
        SourceFile.fromCode(
            "mod1.js",
            lines(
                "/** @const */ var module$mod1 = {};",
                "/** @const */ var module$mod1$default = 123;")));

    test(srcs(inputs), expected(expected));
  }
}
