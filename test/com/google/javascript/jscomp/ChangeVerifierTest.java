/*
 * Copyright 2017 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.jspecify.nullness.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test class for ChangeVerifier.java. Test cases in this class roughly do the following:
 *
 * <p>1. Creates a compiler instance and parses a script using it.
 *
 * <p>2. Creates a new ChangeVerifier class with the same compiler.
 *
 * <p>3. Saves a clean snapshot of the parsed script from step#1 using the ChangeVerifier instance
 * from step#2
 *
 * <p>4. Changes the script from step #1.
 *
 * <p>5. Either records/skips recording change to the script from #1 into the compiler.
 *
 * <p>6. Invokes changeVerifier (which already contains the clean snapshot) and gives it the changed
 * script from step#1 to compare nodes.
 */
@RunWith(JUnit4.class)
public final class ChangeVerifierTest {
  private @Nullable Compiler compiler;

  @Before
  public void setup() {
    compiler = null;
  }

  @Test
  public void testCorrectValidationOfScriptWithChangeAfterFunction() {
    Node script = parse("function A() {} if (0) { A(); }");
    checkState(script.isScript());

    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(script);

    getCallNode(script).detach();
    compiler.reportChangeToChangeScope(script);

    // checks that a change was made and reported.
    verifier.checkRecordedChanges("test1", script);
  }

  @Test
  public void testChangeToScriptNotReported() {
    Node script = parse("function A() {} if (0) { A(); }");
    checkState(script.isScript());

    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(script);

    // no change, no problem
    verifier.checkRecordedChanges("test1", script);

    // add a statement, but don't report the change.
    script.addChildToBack(IR.exprResult(IR.nullNode()));

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class, () -> verifier.checkRecordedChanges("test2", script));
    assertThat(e).hasMessageThat().contains("changed scope not marked as changed");
  }

  @Test
  public void testChangeToFunction_notReported() {
    Node script = parse("function A() {}");
    checkState(script.isScript());
    Node function = script.getFirstChild();
    checkState(function.isFunction());

    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(script);

    // no change, no problem.
    verifier.checkRecordedChanges("test1", script);

    // add a statement, but don't report the change.
    function.addChildToBack(IR.exprResult(IR.nullNode()));

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class, () -> verifier.checkRecordedChanges("test2", script));
    assertThat(e).hasMessageThat().contains("changed scope not marked as changed");
  }

  @Test
  public void testChangeToArrowFunction_notReported() {
    Node script = parse("() => {}");
    checkState(script.isScript());
    Node function = script.getFirstFirstChild();
    checkState(function.isArrowFunction());

    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(script);

    // no change, no problem.
    verifier.checkRecordedChanges("test1", script);

    // add a statement, but don't report the change.
    function.addChildToBack(IR.exprResult(IR.nullNode()));
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class, () -> verifier.checkRecordedChanges("test2", script));
    assertThat(e).hasMessageThat().contains("changed scope not marked as changed");
  }

  @Test
  public void testChangeToArrowFunction_correctlyReportedChange() {
    Node script = parse("() => {}");
    checkState(script.isScript());

    Node function = script.getFirstFirstChild();
    checkState(function.isArrowFunction());

    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(script);

    // no change, no problem.
    verifier.checkRecordedChanges("test1", script);

    // add a statement, and report the change.
    function.addChildToBack(IR.exprResult(IR.nullNode()));
    compiler.reportChangeToChangeScope(function);

    // checks that a change was made and recorded.
    verifier.checkRecordedChanges("test2", script);
  }

  @Test
  public void testDeletedFunction() {
    Node script = parse("function A() {}");

    checkState(script.isScript());

    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(script);

    // no change
    verifier.checkRecordedChanges("test1", script);

    // remove the function. report the change in the script but not the function deletion.
    Node fnNode = script.getFirstChild();
    fnNode.detach();
    compiler.reportChangeToChangeScope(script);

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class, () -> verifier.checkRecordedChanges("test2", script));
    assertThat(e).hasMessageThat().contains("deleted scope was not reported");

    // now try again after reporting the function deletion.
    compiler.reportFunctionDeleted(fnNode);

    // no longer throws an exception.
    verifier.checkRecordedChanges("test2", script);
  }

  @Test
  public void testNotDeletedFunction() {
    Node script = parse("function A() {}");

    checkState(script.isScript());

    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(script);

    // no change
    verifier.checkRecordedChanges("test1", script);

    // mark the function deleted even though it's alive.
    Node fnNode = script.getFirstChild();
    compiler.reportFunctionDeleted(fnNode);

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class, () -> verifier.checkRecordedChanges("test2", script));
    assertThat(e).hasMessageThat().contains("existing scope is improperly marked as deleted");
  }

  @Test
  public void testChangeVerification() {
    Node mainScript = parse("");

    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(mainScript);

    verifier.checkRecordedChanges(mainScript);

    mainScript.addChildToFront(IR.function(IR.name("A"), IR.paramList(), IR.block()));
    compiler.reportChangeToChangeScope(mainScript);

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class, () -> verifier.checkRecordedChanges("test2", mainScript));
    // ensure that e was thrown from the right code-path
    // especially important if it's something as frequent
    // as an IllegalArgumentException, etc.
    assertThat(e).hasMessageThat().contains("new scope not explicitly marked as changed:");

    // works fine when the newly created function scope is marked as changed.
    Node fnNode = mainScript.getFirstChild();
    compiler.reportChangeToChangeScope(fnNode);
    verifier.checkRecordedChanges(mainScript);
  }

  /** Initializes a new compiler, parses the script using it and returns the script node */
  private Node parse(String js) {
    compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    Node n = compiler.parseTestCode(js);
    assertThat(compiler.getErrors()).isEmpty();
    return n;
  }

  /** Performs a depth first search and returns the first call node it finds */
  private static Node getCallNode(Node n) {
    if (n.isCall()) {
      return n;
    }
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      Node result = getCallNode(c);
      if (result != null) {
        return result;
      }
    }
    return null;
  }
}
