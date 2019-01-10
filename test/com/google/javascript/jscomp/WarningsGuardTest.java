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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CheckAccessControls.VISIBILITY_MISMATCH;
import static com.google.javascript.jscomp.CheckLevel.ERROR;
import static com.google.javascript.jscomp.CheckLevel.OFF;
import static com.google.javascript.jscomp.CheckLevel.WARNING;
import static com.google.javascript.jscomp.CompilerTestCase.lines;
import static com.google.javascript.jscomp.TypeCheck.DETERMINISTIC_TEST;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.ShowByPathWarningsGuard.ShowType;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Testcase for WarningsGuard and its implementations.
 *
 * @author anatol@google.com (Anatol Pomazau)
 */
@RunWith(JUnit4.class)
public final class WarningsGuardTest {
  private static final DiagnosticType BAR_WARNING =
      DiagnosticType.warning("BAR", "Bar description");

  private static final WarningsGuard visibilityOff =
      new DiagnosticGroupWarningsGuard(
          DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.OFF);

  private static final WarningsGuard visibilityWarning =
      new DiagnosticGroupWarningsGuard(
          DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.WARNING);

  @Test
  public void testShowByPathGuard_Restrict() {
    WarningsGuard includeGuard = new ShowByPathWarningsGuard("/foo/",
        ShowType.INCLUDE);

    assertThat(includeGuard.level(makeError("asasasd/foo/hello.js", WARNING))).isNull();
    assertThat(includeGuard.level(makeError("asasasd/foo/hello.js", ERROR))).isNull();
    assertThat(includeGuard.level(makeError("asasasd/hello.js", WARNING))).isEqualTo(OFF);
    assertThat(includeGuard.level(makeError("asasasd/hello.js", OFF))).isEqualTo(OFF);
    assertThat(includeGuard.level(makeError("asasasd/hello.js", ERROR))).isNull();
    assertThat(includeGuard.level(makeError(null))).isNull();
    assertThat(includeGuard.level(makeError(null, WARNING))).isNull();

    assertThat(includeGuard.disables(DiagnosticGroups.DEPRECATED)).isFalse();
  }

  @Test
  public void testShowByPathGuard_Suppress() {
    WarningsGuard excludeGuard = new ShowByPathWarningsGuard(
        new String[] { "/foo/", "/bar/" }, ShowType.EXCLUDE);

    assertThat(excludeGuard.level(makeError("asasasd/foo/hello.js", WARNING))).isEqualTo(OFF);
    assertThat(excludeGuard.level(makeError("asasasd/foo/bar/hello.js", WARNING))).isEqualTo(OFF);
    assertThat(excludeGuard.level(makeError("asasasd/bar/hello.js", WARNING))).isEqualTo(OFF);
    assertThat(excludeGuard.level(makeError("asasasd/foo/bar/hello.js", WARNING))).isEqualTo(OFF);
    assertThat(excludeGuard.level(makeError("asasasd/foo/hello.js", ERROR))).isNull();
    assertThat(excludeGuard.level(makeError("asasasd/hello.js", WARNING))).isNull();
    assertThat(excludeGuard.level(makeError("asasasd/hello.js", OFF))).isNull();
    assertThat(excludeGuard.level(makeError("asasasd/hello.js", ERROR))).isNull();
    assertThat(excludeGuard.level(makeError(null))).isNull();
    assertThat(excludeGuard.level(makeError(null, WARNING))).isNull();

    assertThat(excludeGuard.disables(DiagnosticGroups.DEPRECATED)).isFalse();
  }

  @Test
  public void testStrictGuard() {
    WarningsGuard guard = new StrictWarningsGuard();

    assertThat(guard.level(makeError("foo/hello.js", WARNING))).isEqualTo(ERROR);
    assertThat(guard.level(makeError("foo/hello.js", OFF))).isNull();
    assertThat(guard.level(makeError("bar.js", ERROR))).isEqualTo(ERROR);

    assertThat(guard.disables(DiagnosticGroups.DEPRECATED)).isFalse();
  }

  @Test
  public void testByPathGuard() {
    WarningsGuard strictGuard = ByPathWarningsGuard
        .forPath(ImmutableList.of("/foo/"), ERROR);

    assertThat(strictGuard.level(makeError("asasasd/foo/hello.js", WARNING))).isEqualTo(ERROR);
    assertThat(strictGuard.level(makeError("asasasd/foo/hello.js", ERROR))).isNull();
    assertThat(strictGuard.level(makeError("asasasd/hello.js", WARNING))).isNull();
    assertThat(strictGuard.level(makeError("asasasd/hello.js", OFF))).isNull();
    assertThat(strictGuard.level(makeError("asasasd/hello.js", ERROR))).isNull();
    assertThat(strictGuard.level(makeError(null))).isNull();
    assertThat(strictGuard.level(makeError(null, WARNING))).isNull();

    assertThat(strictGuard.disables(DiagnosticGroups.DEPRECATED)).isFalse();
  }

  @Test
  public void testComposeGuard() {
    WarningsGuard g1 = new WarningsGuard() {
      private static final long serialVersionUID = 1L;

      @Override
      public CheckLevel level(JSError error) {
        return error.sourceName.equals("123456") ? ERROR : null;
      }

      @Override
      public boolean disables(DiagnosticGroup otherGroup) {
        return false;
      }
    };

    WarningsGuard g2 = new WarningsGuard() {
      private static final long serialVersionUID = 1L;

      @Override
      public CheckLevel level(JSError error) {
        return error.lineNumber == 12 ? WARNING : null;
      }

      @Override
      public boolean disables(DiagnosticGroup otherGroup) {
        return true;
      }
    };

    WarningsGuard guard = new ComposeWarningsGuard(g1, g2);

    assertThat(guard.level(makeError("aaa"))).isNull();
    assertThat(guard.level(makeError("12345"))).isNull();
    assertThat(guard.level(makeError("123456"))).isEqualTo(ERROR);
    assertThat(guard.level(makeError("12345", 12))).isEqualTo(WARNING);
    assertThat(guard.level(makeError("12345", 13))).isNull();

    assertThat(guard.disables(DiagnosticGroups.DEPRECATED)).isTrue();
  }

  @Test
  public void testComposeGuard2() {
    WarningsGuard pathGuard = new ShowByPathWarningsGuard("/foo/");
    WarningsGuard strictGuard = new StrictWarningsGuard();

    WarningsGuard guard = new ComposeWarningsGuard(strictGuard, pathGuard);
    assertThat(guard.level(makeError("asasasd/hello.js", WARNING))).isEqualTo(OFF);
    assertThat(guard.level(makeError("asasasd/foo/hello.js", WARNING))).isEqualTo(ERROR);

    // Try again with the guards reversed.
    guard = new ComposeWarningsGuard(pathGuard, strictGuard);
    assertThat(guard.level(makeError("asasasd/hello.js", WARNING))).isEqualTo(OFF);
    assertThat(guard.level(makeError("asasasd/foo/hello.js", WARNING))).isEqualTo(ERROR);
  }

  @Test
  public void testComposeGuard3() {
    // Confirm that explicit diagnostic groups override the promotion of
    // warnings to errors done by StrictWarningGuard.

    WarningsGuard typeGuard = new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.DEPRECATED, WARNING);
    WarningsGuard strictGuard = new StrictWarningsGuard();

    WarningsGuard guard = new ComposeWarningsGuard(strictGuard, typeGuard);
    assertThat(guard.level(JSError.make("example.js", 1, 0, CheckAccessControls.DEPRECATED_NAME)))
        .isEqualTo(WARNING);

    // Ordering applied doesn't matter, do it again reversed.
    guard = new ComposeWarningsGuard(typeGuard, strictGuard);
    assertThat(guard.level(JSError.make("example.js", 1, 0, CheckAccessControls.DEPRECATED_NAME)))
        .isEqualTo(WARNING);
  }

  @Test
  public void testComposeGuardOrdering() {
    WarningsGuard pathGuard1 = new ShowByPathWarningsGuard("/foo/");
    WarningsGuard pathGuard2 = new ShowByPathWarningsGuard("/bar/");
    WarningsGuard strictGuard = new StrictWarningsGuard();
    WarningsGuard diagnosticGuard1 = new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.OFF);
    WarningsGuard diagnosticGuard2 = new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.OFF);
    WarningsGuard diagnosticGuard3 = new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.OFF);

    ComposeWarningsGuard guard = new ComposeWarningsGuard(pathGuard1,
        diagnosticGuard1, strictGuard, diagnosticGuard2, pathGuard2,
        diagnosticGuard3);

    List<WarningsGuard> guards = guard.getGuards();
    assertThat(guards).hasSize(6);
    for (int i = 1; i < 6; i++) {
      assertThat(guards.get(i).getPriority()).isAtLeast(guards.get(i - 1).getPriority());
    }
  }

  @Test
  public void testComposeGuardOrdering2() {
    // Ensure that guards added later always override, when two guards
    // have the same priority.
    ComposeWarningsGuard guardA = new ComposeWarningsGuard();
    guardA.addGuard(visibilityWarning);
    guardA.addGuard(visibilityOff);
    guardA.addGuard(visibilityWarning);

    ComposeWarningsGuard guardB = new ComposeWarningsGuard();
    guardB.addGuard(visibilityOff);
    guardB.addGuard(visibilityWarning);
    guardB.addGuard(visibilityOff);

    assertThat(guardA.disables(DiagnosticGroups.ACCESS_CONTROLS)).isFalse();
    assertThat(guardA.enables(DiagnosticGroups.ACCESS_CONTROLS)).isTrue();
    assertThat(guardB.disables(DiagnosticGroups.ACCESS_CONTROLS)).isTrue();
    assertThat(guardB.enables(DiagnosticGroups.ACCESS_CONTROLS)).isFalse();
  }

  @Test
  public void testComposeGuardOrdering3() {
    ComposeWarningsGuard guardA = new ComposeWarningsGuard();
    guardA.addGuard(
        new DiagnosticGroupWarningsGuard(
          DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.WARNING));
    guardA.addGuard(
        new DiagnosticGroupWarningsGuard(
          DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.WARNING));
    guardA.addGuard(
        new DiagnosticGroupWarningsGuard(
          DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.WARNING));
    guardA.addGuard(
        new DiagnosticGroupWarningsGuard(
          DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.WARNING));
    guardA.addGuard(visibilityOff);

    assertThat(guardA.disables(DiagnosticGroups.ACCESS_CONTROLS)).isTrue();
  }

  @Test
  public void testComposeGuardOrdering4() {
    ComposeWarningsGuard guardA = new ComposeWarningsGuard();
    guardA.addGuard(visibilityWarning);
    guardA.addGuard(visibilityWarning);
    guardA.addGuard(visibilityWarning);
    guardA.addGuard(visibilityWarning);
    guardA.addGuard(visibilityWarning);
    guardA.addGuard(visibilityWarning);
    guardA.addGuard(visibilityOff);

    assertThat(guardA.disables(DiagnosticGroups.ACCESS_CONTROLS)).isTrue();
  }

  @Test
  public void testEmergencyComposeGuard1() {
    ComposeWarningsGuard guard = new ComposeWarningsGuard();
    guard.addGuard(new StrictWarningsGuard());
    assertThat(guard.level(makeErrorWithLevel(WARNING))).isEqualTo(ERROR);
    assertThat(guard.makeEmergencyFailSafeGuard().level(makeErrorWithLevel(WARNING)))
        .isEqualTo(WARNING);
  }

  @Test
  public void testEmergencyComposeGuard2() {
    ComposeWarningsGuard guard = new ComposeWarningsGuard();
    guard.addGuard(
        new DiagnosticGroupWarningsGuard(
            DiagnosticGroups.ACCESS_CONTROLS, ERROR));
    assertThat(guard.level(makeErrorWithType(VISIBILITY_MISMATCH))).isEqualTo(ERROR);
    assertThat(guard.makeEmergencyFailSafeGuard().level(makeErrorWithType(VISIBILITY_MISMATCH)))
        .isEqualTo(WARNING);
  }

  @Test
  public void testEmergencyComposeGuard3() {
    ComposeWarningsGuard guard = new ComposeWarningsGuard();
    guard.addGuard(
        new DiagnosticGroupWarningsGuard(
            DiagnosticGroups.ACCESS_CONTROLS, ERROR));
    guard.addGuard(
        new DiagnosticGroupWarningsGuard(
            DiagnosticGroups.ACCESS_CONTROLS, OFF));
    assertThat(guard.level(makeErrorWithType(VISIBILITY_MISMATCH))).isEqualTo(OFF);
    assertThat(guard.makeEmergencyFailSafeGuard().level(makeErrorWithType(VISIBILITY_MISMATCH)))
        .isEqualTo(OFF);
  }

  @Test
  public void testDiagnosticGuard1() {
    WarningsGuard guard = new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.CHECK_TYPES, ERROR);

    assertThat(guard.level(makeError("foo", DETERMINISTIC_TEST))).isEqualTo(ERROR);

    assertThat(guard.disables(DiagnosticGroups.CHECK_TYPES)).isFalse();

    assertEnables(guard, DiagnosticGroups.CHECK_TYPES);
    assertNotEnables(guard, DiagnosticGroups.MESSAGE_DESCRIPTIONS);
  }

  @Test
  public void testDiagnosticGuard3() {
    WarningsGuard guard = new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.CHECK_TYPES, OFF);

    assertThat(guard.disables(DiagnosticGroups.CHECK_TYPES)).isTrue();

    assertNotEnables(guard, DiagnosticGroups.CHECK_TYPES);
    assertNotEnables(guard, DiagnosticGroups.MESSAGE_DESCRIPTIONS);
  }

  @Test
  public void testDiagnosticGuard4() {
    WarningsGuard guard = new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.DEPRECATED, OFF);

    assertThat(guard.disables(DiagnosticGroups.DEPRECATED)).isTrue();
    assertThat(guard.disables(DiagnosticGroups.VISIBILITY)).isFalse();
    assertThat(guard.disables(DiagnosticGroups.ACCESS_CONTROLS)).isFalse();

    assertNotEnables(guard, DiagnosticGroups.DEPRECATED);
    assertNotEnables(guard, DiagnosticGroups.VISIBILITY);
    assertNotEnables(guard, DiagnosticGroups.ACCESS_CONTROLS);
    assertNotEnables(guard, DiagnosticGroups.MESSAGE_DESCRIPTIONS);
  }

  @Test
  public void testSuppressGuard1() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    Compiler compiler = new Compiler();
    WarningsGuard guard = new SuppressDocWarningsGuard(compiler, map);

    Node code = compiler.parseTestCode(
        "/** @suppress {deprecated} */ function f() { a; } "
        + "function g() { b; }");
    assertThat(guard.level(JSError.make(code, BAR_WARNING))).isNull();
    assertThat(guard.level(JSError.make(findNameNode(code, "a"), BAR_WARNING))).isEqualTo(OFF);
    assertThat(guard.level(JSError.make(findNameNode(code, "b"), BAR_WARNING))).isNull();
  }

  @Test
  public void testSuppressGuard2() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    Compiler compiler = new Compiler();
    WarningsGuard guard = new SuppressDocWarningsGuard(compiler, map);

    Node code = compiler.parseTestCode(
        "/** @fileoverview \n * @suppress {deprecated} */ function f() { a; } "
        + "function g() { b; }");
    assertThat(guard.level(JSError.make(findNameNode(code, "a"), BAR_WARNING))).isEqualTo(OFF);
    assertThat(guard.level(JSError.make(findNameNode(code, "b"), BAR_WARNING))).isEqualTo(OFF);
  }

  @Test
  public void testSuppressGuard3() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    Compiler compiler = new Compiler();
    WarningsGuard guard = new SuppressDocWarningsGuard(compiler, map);

    Node code = compiler.parseTestCode(
        "/** @suppress {deprecated} */ var f = function() { a; }");
    assertThat(guard.level(JSError.make(findNameNode(code, "a"), BAR_WARNING))).isEqualTo(OFF);
  }

  @Test
  public void testSuppressGuard4() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    Compiler compiler = new Compiler();
    WarningsGuard guard = new SuppressDocWarningsGuard(compiler, map);

    Node code = compiler.parseTestCode(
        "var goog = {}; "
        + "/** @suppress {deprecated} */ goog.f = function() { a; }");
    assertThat(guard.level(JSError.make(findNameNode(code, "a"), BAR_WARNING))).isEqualTo(OFF);
  }

  @Test
  public void testSuppressGuard5() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    Compiler compiler = new Compiler();
    WarningsGuard guard = new SuppressDocWarningsGuard(compiler, map);

    Node code = compiler.parseTestCode(
        "var goog = {}; "
        + "goog.f = function() { /** @suppress {deprecated} */ (a); }");

    // We only care about @suppress annotations at the function and
    // script level.
    assertThat(guard.level(JSError.make(findNameNode(code, "a"), BAR_WARNING))).isNull();
  }

  @Test
  public void testSuppressGuard6() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    Compiler compiler = new Compiler();
    WarningsGuard guard = new SuppressDocWarningsGuard(compiler, map);

    Node code =
        compiler.parseTestCode("/** @fileoverview @suppress {deprecated} */\n console.log(a);");

    assertThat(guard.level(JSError.make(findNameNode(code, "a"), BAR_WARNING))).isEqualTo(OFF);
  }

  @Test
  public void testSuppressDocGuard_appliesSuppressionsOnComputedPropMethod_toPropNameExpression() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));

    Compiler compiler = new Compiler();
    WarningsGuard guard = new SuppressDocWarningsGuard(compiler, map);

    Node code =
        compiler.parseTestCode(
            lines(
                "class Foo {", //
                "  /** @suppress {deprecated} */",
                "  [a]() { }",
                "}"));

    assertThat(guard.level(JSError.make(findNameNode(code, "a"), BAR_WARNING))).isEqualTo(OFF);
  }

  @Test
  public void testComposeGuardCycle() {
    ComposeWarningsGuard guard = new ComposeWarningsGuard(
        visibilityOff, visibilityWarning);
    guard.addGuard(guard);
    assertThat(guard.toString())
        .isEqualTo("DiagnosticGroup<visibility>(WARNING), DiagnosticGroup<visibility>(OFF)");
  }

  private static Node findNameNode(Node root, String name) {
    if (root.isName() && root.getString().equals(name)) {
      return root;
    }

    for (Node n : root.children()) {
      Node result = findNameNode(n, name);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private static void assertEnables(WarningsGuard guard, DiagnosticGroup type) {
    assertThat(new ComposeWarningsGuard(guard).enables(type)).isTrue();
  }

  private static void assertNotEnables(WarningsGuard guard,
      DiagnosticGroup type) {
    assertThat(new ComposeWarningsGuard(guard).enables(type)).isFalse();
  }

  private static JSError makeError(String sourcePath) {
    Node n = new Node(Token.EMPTY);
    n.setSourceFileForTesting(sourcePath);
    return JSError.make(n, BAR_WARNING);
  }

  private static JSError makeError(String sourcePath, DiagnosticType type) {
    Node n = new Node(Token.EMPTY);
    n.setSourceFileForTesting(sourcePath);
    return JSError.make(n, type);
  }

  private static JSError makeError(String sourcePath, CheckLevel level) {
    Node n = new Node(Token.EMPTY);
    n.setSourceFileForTesting(sourcePath);
    return JSError.make(n, DiagnosticType.make("FOO", level, "Foo description"));
  }

  private static JSError makeError(String sourcePath, int lineno) {
    return JSError.make(sourcePath, lineno, -1, BAR_WARNING);
  }

  private static JSError makeErrorWithType(DiagnosticType type) {
    Node n = new Node(Token.EMPTY);
    n.setSourceFileForTesting("input");
    return JSError.make(n, type);
  }

  private static JSError makeErrorWithLevel(CheckLevel level) {
    Node n = new Node(Token.EMPTY);
    n.setSourceFileForTesting("input");
    return JSError.make(n,
        DiagnosticType.make("FOO", level, "Foo description"));
  }
}
