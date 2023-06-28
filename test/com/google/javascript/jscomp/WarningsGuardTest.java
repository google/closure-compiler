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
import static com.google.javascript.jscomp.CheckLevel.ERROR;
import static com.google.javascript.jscomp.CheckLevel.OFF;
import static com.google.javascript.jscomp.CheckLevel.WARNING;
import static com.google.javascript.jscomp.CompilerTestCase.lines;
import static com.google.javascript.jscomp.TypeCheck.DETERMINISTIC_TEST;
import static com.google.javascript.jscomp.TypeCheck.ILLEGAL_PROPERTY_CREATION_ON_UNION_TYPE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.ShowByPathWarningsGuard.ShowType;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import org.jspecify.nullness.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Testcase for WarningsGuard and its implementations. */
@RunWith(JUnit4.class)
public final class WarningsGuardTest {
  private static final DiagnosticType BAR_WARNING =
      DiagnosticType.warning("BAR", "Bar description");

  private static final WarningsGuard visibilityOff =
      new DiagnosticGroupWarningsGuard(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.OFF);

  private static final WarningsGuard visibilityWarning =
      new DiagnosticGroupWarningsGuard(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.WARNING);

  @Test
  public void testShowByPathGuard_Restrict() {
    WarningsGuard includeGuard = new ShowByPathWarningsGuard("/foo/", ShowType.INCLUDE);

    assertThat(includeGuard.level(makeError("asasasd/foo/hello.js", WARNING))).isNull();
    assertThat(includeGuard.level(makeError("asasasd/foo/hello.js", ERROR))).isNull();
    assertThat(includeGuard.level(makeError("asasasd/hello.js", WARNING))).isEqualTo(OFF);
    assertThat(includeGuard.level(makeError("asasasd/hello.js", OFF))).isEqualTo(OFF);
    assertThat(includeGuard.level(makeError("asasasd/hello.js", ERROR))).isNull();
    assertThat(includeGuard.level(makeError(null))).isNull();
    assertThat(includeGuard.level(makeError(null, WARNING))).isNull();

    assertThat(includeGuard.mustRunChecks(DiagnosticGroups.DEPRECATED)).isEqualTo(Tri.UNKNOWN);
  }

  @Test
  public void testShowByPathGuard_Suppress() {
    WarningsGuard excludeGuard =
        new ShowByPathWarningsGuard(new String[] {"/foo/", "/bar/"}, ShowType.EXCLUDE);

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

    assertThat(excludeGuard.mustRunChecks(DiagnosticGroups.DEPRECATED)).isEqualTo(Tri.UNKNOWN);
  }

  @Test
  public void testStrictGuard() {
    WarningsGuard guard = new StrictWarningsGuard();

    assertThat(guard.level(makeError("foo/hello.js", WARNING))).isEqualTo(ERROR);
    assertThat(guard.level(makeError("foo/hello.js", OFF))).isNull();
    assertThat(guard.level(makeError("bar.js", ERROR))).isEqualTo(ERROR);

    assertThat(guard.mustRunChecks(DiagnosticGroups.DEPRECATED)).isEqualTo(Tri.UNKNOWN);
  }

  @Test
  public void testByPathGuard() {
    WarningsGuard strictGuard = ByPathWarningsGuard.forPath(ImmutableList.of("/foo/"), ERROR);

    assertThat(strictGuard.level(makeError("asasasd/foo/hello.js", WARNING))).isEqualTo(ERROR);
    assertThat(strictGuard.level(makeError("asasasd/foo/hello.js", ERROR))).isNull();
    assertThat(strictGuard.level(makeError("asasasd/hello.js", WARNING))).isNull();
    assertThat(strictGuard.level(makeError("asasasd/hello.js", OFF))).isNull();
    assertThat(strictGuard.level(makeError("asasasd/hello.js", ERROR))).isNull();
    assertThat(strictGuard.level(makeError(null))).isNull();
    assertThat(strictGuard.level(makeError(null, WARNING))).isNull();

    assertThat(strictGuard.mustRunChecks(DiagnosticGroups.DEPRECATED)).isEqualTo(Tri.UNKNOWN);
  }

  @Test
  public void testComposeGuard() {
    WarningsGuard g1 =
        new WarningsGuard() {
          private static final long serialVersionUID = 1L;

          @Override
          public CheckLevel level(JSError error) {
            return error.getSourceName().equals("123456") ? ERROR : null;
          }
        };

    WarningsGuard g2 =
        new WarningsGuard() {
          private static final long serialVersionUID = 1L;

          @Override
          public CheckLevel level(JSError error) {
            return error.getLineNumber() == 12 ? WARNING : null;
          }
        };

    WarningsGuard guard = new ComposeWarningsGuard(g1, g2);

    assertThat(guard.level(makeError("aaa"))).isNull();
    assertThat(guard.level(makeError("12345"))).isNull();
    assertThat(guard.level(makeError("123456"))).isEqualTo(ERROR);
    assertThat(guard.level(makeError("12345", 12))).isEqualTo(WARNING);
    assertThat(guard.level(makeError("12345", 13))).isNull();

    assertThat(guard.mustRunChecks(DiagnosticGroups.DEPRECATED)).isEqualTo(Tri.UNKNOWN);
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

    WarningsGuard typeGuard =
        new DiagnosticGroupWarningsGuard(DiagnosticGroups.DEPRECATED, WARNING);
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
    WarningsGuard diagnosticGuard1 =
        new DiagnosticGroupWarningsGuard(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.OFF);
    WarningsGuard diagnosticGuard2 =
        new DiagnosticGroupWarningsGuard(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.OFF);
    WarningsGuard diagnosticGuard3 =
        new DiagnosticGroupWarningsGuard(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.OFF);

    ComposeWarningsGuard guard =
        new ComposeWarningsGuard(
            pathGuard1,
            diagnosticGuard1,
            strictGuard,
            diagnosticGuard2,
            pathGuard2,
            diagnosticGuard3);

    SortedSet<WarningsGuard> guards = guard.getGuards();
    assertThat(guards).hasSize(6);

    int prevPriority = Integer.MIN_VALUE;
    for (WarningsGuard g : guards) {
      assertThat(g.getPriority()).isAtLeast(prevPriority);
      prevPriority = g.getPriority();
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

    assertThat(guardA.mustRunChecks(DiagnosticGroups.ACCESS_CONTROLS)).isEqualTo(Tri.TRUE);
    assertThat(guardB.mustRunChecks(DiagnosticGroups.ACCESS_CONTROLS)).isEqualTo(Tri.FALSE);
  }

  @Test
  public void testComposeGuardOrdering3() {
    ComposeWarningsGuard guardA = new ComposeWarningsGuard();
    guardA.addGuard(
        new DiagnosticGroupWarningsGuard(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.WARNING));
    guardA.addGuard(
        new DiagnosticGroupWarningsGuard(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.WARNING));
    guardA.addGuard(
        new DiagnosticGroupWarningsGuard(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.WARNING));
    guardA.addGuard(
        new DiagnosticGroupWarningsGuard(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.WARNING));
    guardA.addGuard(visibilityOff);

    assertThat(guardA.mustRunChecks(DiagnosticGroups.ACCESS_CONTROLS)).isEqualTo(Tri.FALSE);
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

    assertThat(guardA.mustRunChecks(DiagnosticGroups.ACCESS_CONTROLS)).isEqualTo(Tri.FALSE);
  }

  @Test
  public void testDiagnosticGuard1() {
    WarningsGuard guard = new DiagnosticGroupWarningsGuard(DiagnosticGroups.CHECK_TYPES, ERROR);

    assertThat(guard.level(makeError("foo", DETERMINISTIC_TEST))).isEqualTo(ERROR);

    assertThat(guard.mustRunChecks(DiagnosticGroups.CHECK_TYPES)).isEqualTo(Tri.TRUE);
    assertThat(guard.mustRunChecks(DiagnosticGroups.MESSAGE_DESCRIPTIONS)).isEqualTo(Tri.UNKNOWN);
  }

  @Test
  public void testDiagnosticGuard3() {
    WarningsGuard guard = new DiagnosticGroupWarningsGuard(DiagnosticGroups.CHECK_TYPES, OFF);

    assertThat(guard.mustRunChecks(DiagnosticGroups.CHECK_TYPES)).isEqualTo(Tri.FALSE);
    assertThat(guard.mustRunChecks(DiagnosticGroups.MESSAGE_DESCRIPTIONS)).isEqualTo(Tri.UNKNOWN);
  }

  @Test
  public void testDiagnosticGuard4() {
    WarningsGuard guard = new DiagnosticGroupWarningsGuard(DiagnosticGroups.DEPRECATED, OFF);

    assertThat(guard.mustRunChecks(DiagnosticGroups.DEPRECATED)).isEqualTo(Tri.FALSE);
    assertThat(guard.mustRunChecks(DiagnosticGroups.VISIBILITY)).isEqualTo(Tri.UNKNOWN);
    assertThat(guard.mustRunChecks(DiagnosticGroups.ACCESS_CONTROLS)).isEqualTo(Tri.UNKNOWN);
    assertThat(guard.mustRunChecks(DiagnosticGroups.MESSAGE_DESCRIPTIONS)).isEqualTo(Tri.UNKNOWN);
  }

  @Test
  public void testSuppressGuard1() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    Compiler compiler = new Compiler();
    WarningsGuard guard = new SuppressDocWarningsGuard(compiler, map);

    Node code =
        compiler.parseTestCode(
            "/** @suppress {deprecated} */ function f() { a; } " + "function g() { b; }");
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

    Node code =
        compiler.parseTestCode(
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

    Node code = compiler.parseTestCode("/** @suppress {deprecated} */ var f = function() { a; }");
    assertThat(guard.level(JSError.make(findNameNode(code, "a"), BAR_WARNING))).isEqualTo(OFF);
  }

  @Test
  public void testSuppressGuard_strictMissingPropertyOnUnionTypes() {
    Compiler compiler = new Compiler();
    WarningsGuard guard =
        new SuppressDocWarningsGuard(compiler, DiagnosticGroups.getRegisteredGroups());

    Node code =
        compiler.parseTestCode(
            lines(
                "class C {}",
                "class D{}",
                "/** @type {(C|D)} */",
                "let obj;",
                "/** @suppress {strictMissingProperties} */",
                "obj.prop"));
    assertThat(
            guard.level(
                JSError.make(
                    findGetPropNode(code, "prop"), ILLEGAL_PROPERTY_CREATION_ON_UNION_TYPE)))
        .isEqualTo(OFF);
  }

  @Test
  public void testSuppressGuard4() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    Compiler compiler = new Compiler();
    WarningsGuard guard = new SuppressDocWarningsGuard(compiler, map);

    Node code =
        compiler.parseTestCode(
            "var goog = {}; " + "/** @suppress {deprecated} */ goog.f = function() { a; }");
    assertThat(guard.level(JSError.make(findNameNode(code, "a"), BAR_WARNING))).isEqualTo(OFF);
  }

  @Test
  public void testSuppressGuard5() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    Compiler compiler = new Compiler();
    WarningsGuard guard = new SuppressDocWarningsGuard(compiler, map);

    Node code =
        compiler.parseTestCode(
            "var goog = {}; " + "goog.f = function() { /** @suppress {deprecated} */ (a); }");

    assertThat(guard.level(JSError.make(findNameNode(code, "a"), BAR_WARNING))).isEqualTo(OFF);
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
  public void testSuppressGuard7() {
    Map<String, DiagnosticGroup> map = new HashMap<>();
    map.put("deprecated", new DiagnosticGroup(BAR_WARNING));
    Compiler compiler = new Compiler();
    WarningsGuard guard = new SuppressDocWarningsGuard(compiler, map);

    Node code = compiler.parseTestCode("console.log(/** @suppress {deprecated} */ (a));");

    // We don't care about @suppress annotations within nested expressions
    assertThat(guard.level(JSError.make(findNameNode(code, "a"), BAR_WARNING))).isNull();
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
  public void testSuppressGuard_onCompoundAssignment() {
    Compiler compiler = new Compiler();
    WarningsGuard guard =
        new SuppressDocWarningsGuard(
            compiler, ImmutableMap.of("deprecated", new DiagnosticGroup(BAR_WARNING)));

    Node code =
        compiler.parseTestCode("var goog = {}; " + "/** @suppress {deprecated} */ goog.f += a");
    assertThat(guard.level(JSError.make(findNameNode(code, "a"), BAR_WARNING))).isEqualTo(OFF);
  }

  @Test
  public void testSuppressGuard_onDetachedNode() {
    Compiler compiler = new Compiler();
    WarningsGuard guard =
        new SuppressDocWarningsGuard(
            compiler, ImmutableMap.of("deprecated", new DiagnosticGroup(BAR_WARNING)));

    Node code = compiler.parseTestCode("/** @fileoverview @suppress {deprecated} */\n\nvar x;");
    // Create an error that has the right source file, but no parents.
    // This is the state of JSDoc nodes.
    JSError error = makeError(code.getSourceFileName());
    assertThat(guard.level(error)).isEqualTo(OFF);
  }

  @Test
  public void testComposeGuardCycle() {
    ComposeWarningsGuard guard = new ComposeWarningsGuard(visibilityOff, visibilityWarning);
    guard.addGuard(guard);
    assertThat(guard.toString())
        .isEqualTo("DiagnosticGroup<visibility>(WARNING), DiagnosticGroup<visibility>(OFF)");
  }

  private static Node findNameNode(Node root, String name) {
    if (root.isName() && root.getString().equals(name)) {
      return root;
    }

    for (Node n = root.getFirstChild(); n != null; n = n.getNext()) {
      Node result = findNameNode(n, name);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private static Node findGetPropNode(Node root, String name) {
    if (root.isGetProp() && root.getString().equals(name)) {
      return root;
    }

    for (Node n = root.getFirstChild(); n != null; n = n.getNext()) {
      Node result = findGetPropNode(n, name);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private static JSError makeError(@Nullable String sourcePath) {
    Node n = new Node(Token.EMPTY);
    n.setSourceFileForTesting(sourcePath);
    return JSError.make(n, BAR_WARNING);
  }

  private static JSError makeError(String sourcePath, DiagnosticType type) {
    Node n = new Node(Token.EMPTY);
    n.setSourceFileForTesting(sourcePath);
    return JSError.make(n, type);
  }

  private static JSError makeError(@Nullable String sourcePath, CheckLevel level) {
    Node n = new Node(Token.EMPTY);
    n.setSourceFileForTesting(sourcePath);
    return JSError.make(n, DiagnosticType.make("FOO", level, "Foo description"));
  }

  private static JSError makeError(String sourcePath, int lineno) {
    return JSError.make(sourcePath, lineno, -1, BAR_WARNING);
  }
}
