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
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_NAME;
import static com.google.javascript.jscomp.CheckAccessControls.VISIBILITY_MISMATCH;
import static com.google.javascript.jscomp.CheckLevel.ERROR;
import static com.google.javascript.jscomp.CheckLevel.OFF;
import static com.google.javascript.jscomp.CheckLevel.WARNING;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ShowByPathWarningsGuard.ShowType;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Testcase for WarningsGuard and its implementations.
 *
 * @author anatol@google.com (Anatol Pomazau)
 */
public final class WarningsGuardTest extends TestCase {
  private static final DiagnosticType BAR_WARNING =
      DiagnosticType.warning("BAR", "Bar description");

  private static final WarningsGuard accessControlsOff =
      new DiagnosticGroupWarningsGuard(
          DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.OFF);

  private static final WarningsGuard accessControlsWarning =
      new DiagnosticGroupWarningsGuard(
          DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.WARNING);

  public void testShowByPathGuard_Restrict() {
    WarningsGuard includeGuard = new ShowByPathWarningsGuard("/foo/",
        ShowType.INCLUDE);

    assertNull(includeGuard.level(makeError("asasasd/foo/hello.js", WARNING)));
    assertNull(includeGuard.level(makeError("asasasd/foo/hello.js", ERROR)));
    assertEquals(OFF, includeGuard.level(makeError("asasasd/hello.js",
        WARNING)));
    assertEquals(OFF, includeGuard.level(makeError("asasasd/hello.js", OFF)));
    assertNull(includeGuard.level(makeError("asasasd/hello.js", ERROR)));
    assertNull(includeGuard.level(makeError(null)));
    assertNull(includeGuard.level(makeError(null, WARNING)));

    assertFalse(includeGuard.disables(DiagnosticGroups.DEPRECATED));
  }

  public void testShowByPathGuard_Suppress() {
    WarningsGuard excludeGuard = new ShowByPathWarningsGuard(
        new String[] { "/foo/", "/bar/" }, ShowType.EXCLUDE);

    assertEquals(OFF, excludeGuard.level(makeError("asasasd/foo/hello.js",
        WARNING)));
    assertEquals(OFF, excludeGuard.level(makeError("asasasd/foo/bar/hello.js",
        WARNING)));
    assertEquals(OFF, excludeGuard.level(makeError("asasasd/bar/hello.js",
        WARNING)));
    assertEquals(OFF, excludeGuard.level(makeError("asasasd/foo/bar/hello.js",
        WARNING)));
    assertNull(excludeGuard.level(makeError("asasasd/foo/hello.js", ERROR)));
    assertNull(excludeGuard.level(makeError("asasasd/hello.js", WARNING)));
    assertNull(excludeGuard.level(makeError("asasasd/hello.js", OFF)));
    assertNull(excludeGuard.level(makeError("asasasd/hello.js", ERROR)));
    assertNull(excludeGuard.level(makeError(null)));
    assertNull(excludeGuard.level(makeError(null, WARNING)));

    assertFalse(excludeGuard.disables(DiagnosticGroups.DEPRECATED));
  }

  public void testStrictGuard() {
    WarningsGuard guard = new StrictWarningsGuard();

    assertEquals(ERROR, guard.level(makeError("foo/hello.js", WARNING)));
    assertNull(guard.level(makeError("foo/hello.js", OFF)));
    assertEquals(ERROR, guard.level(makeError("bar.js", ERROR)));

    assertFalse(guard.disables(DiagnosticGroups.DEPRECATED));
  }

  public void testByPathGuard() {
    WarningsGuard strictGuard = ByPathWarningsGuard
        .forPath(ImmutableList.of("/foo/"), ERROR);

    assertEquals(ERROR, strictGuard.level(makeError("asasasd/foo/hello.js",
        WARNING)));
    assertNull(strictGuard.level(makeError("asasasd/foo/hello.js", ERROR)));
    assertNull(strictGuard.level(makeError("asasasd/hello.js", WARNING)));
    assertNull(strictGuard.level(makeError("asasasd/hello.js", OFF)));
    assertNull(strictGuard.level(makeError("asasasd/hello.js", ERROR)));
    assertNull(strictGuard.level(makeError(null)));
    assertNull(strictGuard.level(makeError(null, WARNING)));

    assertFalse(strictGuard.disables(DiagnosticGroups.DEPRECATED));
  }

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

    assertNull(guard.level(makeError("aaa")));
    assertNull(guard.level(makeError("12345")));
    assertEquals(ERROR, guard.level(makeError("123456")));
    assertEquals(WARNING, guard.level(makeError("12345", 12)));
    assertNull(guard.level(makeError("12345", 13)));

    assertTrue(guard.disables(DiagnosticGroups.DEPRECATED));
  }

  public void testComposeGuard2() {
    WarningsGuard pathGuard = new ShowByPathWarningsGuard("/foo/");
    WarningsGuard strictGuard = new StrictWarningsGuard();

    WarningsGuard guard = new ComposeWarningsGuard(strictGuard, pathGuard);
    assertEquals(
        OFF, guard.level(makeError("asasasd/hello.js", WARNING)));
    assertEquals(
        ERROR, guard.level(makeError("asasasd/foo/hello.js", WARNING)));

    // Try again with the guards reversed.
    guard = new ComposeWarningsGuard(pathGuard, strictGuard);
    assertEquals(
        OFF, guard.level(makeError("asasasd/hello.js", WARNING)));
    assertEquals(
        ERROR, guard.level(makeError("asasasd/foo/hello.js", WARNING)));
  }

  public void testComposeGuard3() {
    // Confirm that explicit diagnostic groups override the promotion of
    // warnings to errors done by StrictWarningGuard.

    WarningsGuard typeGuard = new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.DEPRECATED, WARNING);
    WarningsGuard strictGuard = new StrictWarningsGuard();

    WarningsGuard guard = new ComposeWarningsGuard(strictGuard, typeGuard);
    assertEquals(WARNING, guard.level(
        JSError.make("example.js", 1, 0, CheckAccessControls.DEPRECATED_NAME)));

    // Ordering applied doesn't matter, do it again reversed.
    guard = new ComposeWarningsGuard(typeGuard, strictGuard);
    assertEquals(WARNING, guard.level(
        JSError.make("example.js", 1, 0, CheckAccessControls.DEPRECATED_NAME)));
  }

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
      assertTrue(
          guards.get(i).getPriority() >= guards.get(i - 1).getPriority());
    }
  }

  public void testComposeGuardOrdering2() {
    // Ensure that guards added later always override, when two guards
    // have the same priority.
    ComposeWarningsGuard guardA = new ComposeWarningsGuard();
    guardA.addGuard(accessControlsWarning);
    guardA.addGuard(accessControlsOff);
    guardA.addGuard(accessControlsWarning);

    ComposeWarningsGuard guardB = new ComposeWarningsGuard();
    guardB.addGuard(accessControlsOff);
    guardB.addGuard(accessControlsWarning);
    guardB.addGuard(accessControlsOff);

    assertFalse(guardA.disables(DiagnosticGroups.ACCESS_CONTROLS));
    assertTrue(guardA.enables(DiagnosticGroups.ACCESS_CONTROLS));
    assertTrue(guardB.disables(DiagnosticGroups.ACCESS_CONTROLS));
    assertFalse(guardB.enables(DiagnosticGroups.ACCESS_CONTROLS));
  }

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
    guardA.addGuard(accessControlsOff);

    assertTrue(guardA.disables(DiagnosticGroups.ACCESS_CONTROLS));
  }

  public void testComposeGuardOrdering4() {
    ComposeWarningsGuard guardA = new ComposeWarningsGuard();
    guardA.addGuard(accessControlsWarning);
    guardA.addGuard(accessControlsWarning);
    guardA.addGuard(accessControlsWarning);
    guardA.addGuard(accessControlsWarning);
    guardA.addGuard(accessControlsWarning);
    guardA.addGuard(accessControlsWarning);
    guardA.addGuard(accessControlsOff);

    assertTrue(guardA.disables(DiagnosticGroups.ACCESS_CONTROLS));
  }

  public void testEmergencyComposeGuard1() {
    ComposeWarningsGuard guard = new ComposeWarningsGuard();
    guard.addGuard(new StrictWarningsGuard());
    assertEquals(ERROR,
        guard.level(makeErrorWithLevel(WARNING)));
    assertEquals(WARNING,
        guard.makeEmergencyFailSafeGuard().level(
            makeErrorWithLevel(WARNING)));
  }

  public void testEmergencyComposeGuard2() {
    ComposeWarningsGuard guard = new ComposeWarningsGuard();
    guard.addGuard(
        new DiagnosticGroupWarningsGuard(
            DiagnosticGroups.ACCESS_CONTROLS, ERROR));
    assertEquals(ERROR,
        guard.level(makeErrorWithType(VISIBILITY_MISMATCH)));
    assertEquals(WARNING,
        guard.makeEmergencyFailSafeGuard().level(
            makeErrorWithType(VISIBILITY_MISMATCH)));
  }

  public void testEmergencyComposeGuard3() {
    ComposeWarningsGuard guard = new ComposeWarningsGuard();
    guard.addGuard(
        new DiagnosticGroupWarningsGuard(
            DiagnosticGroups.ACCESS_CONTROLS, ERROR));
    guard.addGuard(
        new DiagnosticGroupWarningsGuard(
            DiagnosticGroups.ACCESS_CONTROLS, OFF));
    assertEquals(OFF,
        guard.level(makeErrorWithType(VISIBILITY_MISMATCH)));
    assertEquals(OFF,
        guard.makeEmergencyFailSafeGuard().level(
            makeErrorWithType(VISIBILITY_MISMATCH)));
  }

  public void testDiagnosticGuard1() {
    WarningsGuard guard = new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.ACCESS_CONTROLS, ERROR);

    assertEquals(ERROR, guard.level(makeError("foo", VISIBILITY_MISMATCH)));
    assertEquals(ERROR, guard.level(makeError("foo", DEPRECATED_NAME)));

    assertFalse(guard.disables(DiagnosticGroups.DEPRECATED));
    assertFalse(guard.disables(DiagnosticGroups.VISIBILITY));
    assertFalse(guard.disables(DiagnosticGroups.ACCESS_CONTROLS));

    assertEnables(guard, DiagnosticGroups.DEPRECATED);
    assertEnables(guard, DiagnosticGroups.VISIBILITY);
    assertEnables(guard, DiagnosticGroups.ACCESS_CONTROLS);
    assertNotEnables(guard, DiagnosticGroups.MESSAGE_DESCRIPTIONS);
  }

  public void testDiagnosticGuard2() {
    WarningsGuard guard = new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.DEPRECATED, ERROR);

    assertNull(guard.level(makeError("foo", VISIBILITY_MISMATCH)));
    assertEquals(ERROR, guard.level(makeError("foo", DEPRECATED_NAME)));

    assertFalse(guard.disables(DiagnosticGroups.DEPRECATED));
    assertFalse(guard.disables(DiagnosticGroups.VISIBILITY));
    assertFalse(guard.disables(DiagnosticGroups.ACCESS_CONTROLS));

    assertEnables(guard, DiagnosticGroups.DEPRECATED);
    assertNotEnables(guard, DiagnosticGroups.VISIBILITY);
    assertEnables(guard, DiagnosticGroups.ACCESS_CONTROLS);
    assertNotEnables(guard, DiagnosticGroups.MESSAGE_DESCRIPTIONS);
  }

  public void testDiagnosticGuard3() {
    WarningsGuard guard = new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.ACCESS_CONTROLS, OFF);

    assertTrue(guard.disables(DiagnosticGroups.DEPRECATED));
    assertTrue(guard.disables(DiagnosticGroups.VISIBILITY));
    assertTrue(guard.disables(DiagnosticGroups.ACCESS_CONTROLS));

    assertNotEnables(guard, DiagnosticGroups.DEPRECATED);
    assertNotEnables(guard, DiagnosticGroups.VISIBILITY);
    assertNotEnables(guard, DiagnosticGroups.ACCESS_CONTROLS);
    assertNotEnables(guard, DiagnosticGroups.MESSAGE_DESCRIPTIONS);
  }

  public void testDiagnosticGuard4() {
    WarningsGuard guard = new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.DEPRECATED, OFF);

    assertTrue(guard.disables(DiagnosticGroups.DEPRECATED));
    assertFalse(guard.disables(DiagnosticGroups.VISIBILITY));
    assertFalse(guard.disables(DiagnosticGroups.ACCESS_CONTROLS));

    assertNotEnables(guard, DiagnosticGroups.DEPRECATED);
    assertNotEnables(guard, DiagnosticGroups.VISIBILITY);
    assertNotEnables(guard, DiagnosticGroups.ACCESS_CONTROLS);
    assertNotEnables(guard, DiagnosticGroups.MESSAGE_DESCRIPTIONS);
  }

  public void testSuppressGuard1() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    WarningsGuard guard = new SuppressDocWarningsGuard(map);

    Compiler compiler = new Compiler();
    Node code = compiler.parseTestCode(
        "/** @suppress {deprecated} */ function f() { a; } "
        + "function g() { b; }");
    assertNull(
        guard.level(JSError.make(code, BAR_WARNING)));
    assertEquals(
        OFF,
        guard.level(JSError.make(
            findNameNode(code, "a"), BAR_WARNING)));
    assertNull(
        guard.level(JSError.make(
            findNameNode(code, "b"), BAR_WARNING)));
  }

  public void testSuppressGuard2() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    WarningsGuard guard = new SuppressDocWarningsGuard(map);

    Compiler compiler = new Compiler();
    Node code = compiler.parseTestCode(
        "/** @fileoverview \n * @suppress {deprecated} */ function f() { a; } "
        + "function g() { b; }");
    assertEquals(
        OFF,
        guard.level(JSError.make(
            findNameNode(code, "a"), BAR_WARNING)));
    assertEquals(
        OFF,
        guard.level(JSError.make(
            findNameNode(code, "b"), BAR_WARNING)));
  }

  public void testSuppressGuard3() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    WarningsGuard guard = new SuppressDocWarningsGuard(map);

    Compiler compiler = new Compiler();
    Node code = compiler.parseTestCode(
        "/** @suppress {deprecated} */ var f = function() { a; }");
    assertEquals(
        OFF,
        guard.level(JSError.make(
            findNameNode(code, "a"), BAR_WARNING)));
  }

  public void testSuppressGuard4() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    WarningsGuard guard = new SuppressDocWarningsGuard(map);

    Compiler compiler = new Compiler();
    Node code = compiler.parseTestCode(
        "var goog = {}; "
        + "/** @suppress {deprecated} */ goog.f = function() { a; }");
    assertEquals(
        OFF,
        guard.level(JSError.make(
            findNameNode(code, "a"), BAR_WARNING)));
  }

  public void testSuppressGuard5() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    WarningsGuard guard = new SuppressDocWarningsGuard(map);

    Compiler compiler = new Compiler();
    Node code = compiler.parseTestCode(
        "var goog = {}; "
        + "goog.f = function() { /** @suppress {deprecated} */ (a); }");

    // We only care about @suppress annotations at the function and
    // script level.
    assertNull(
        guard.level(JSError.make(
            findNameNode(code, "a"), BAR_WARNING)));
  }

  public void testComposeGuardCycle() {
    ComposeWarningsGuard guard = new ComposeWarningsGuard(
        accessControlsOff, accessControlsWarning);
    guard.addGuard(guard);
    assertEquals(
        "DiagnosticGroup<accessControls>(WARNING), " +
        "DiagnosticGroup<accessControls>(OFF)",
        guard.toString());
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
    assertTrue((new ComposeWarningsGuard(guard)).enables(type));
  }

  private static void assertNotEnables(WarningsGuard guard,
      DiagnosticGroup type) {
    assertFalse((new ComposeWarningsGuard(guard)).enables(type));
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

  private static JSError makeErrorWithType(DiagnosticType type) {
    Node n = new Node(Token.EMPTY);
    n.setSourceFileForTesting("input");
    return JSError.make(n, type);
  }

  private static JSError makeError(String sourcePath, CheckLevel level) {
    Node n = new Node(Token.EMPTY);
    n.setSourceFileForTesting(sourcePath);
    return JSError.make(n,
        DiagnosticType.make("FOO", level, "Foo description"));
  }

  private static JSError makeErrorWithLevel(CheckLevel level) {
    Node n = new Node(Token.EMPTY);
    n.setSourceFileForTesting("input");
    return JSError.make(n,
        DiagnosticType.make("FOO", level, "Foo description"));
  }

  private static JSError makeError(String sourcePath, int lineno) {
    return JSError.make(sourcePath, lineno, -1, BAR_WARNING);
  }
}
