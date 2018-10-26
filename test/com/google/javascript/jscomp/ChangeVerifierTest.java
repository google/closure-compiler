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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ChangeVerifierTest {

  @Test
  public void testCorrectValidationOfScriptWithChangeAfterFunction() {
    Node script = parse("function A() {} if (0) { A(); }");
    checkState(script.isScript());

    Compiler compiler = new Compiler();
    compiler.incrementChangeStamp();
    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(script);

    // Here we make a change in that doesn't change the script node
    // child count.
    getCallNode(script).detach();

    // Mark the script as changed
    compiler.incrementChangeStamp();
    script.setChangeTime(compiler.getChangeStamp());

    // will throw if no change is detected.
    verifier.checkRecordedChanges("test1", script);
  }

  @Test
  public void testChangeToScriptNotReported() {
    Node script = parse("function A() {} if (0) { A(); }");
    checkState(script.isScript());

    Compiler compiler = new Compiler();
    compiler.incrementChangeStamp();
    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(script);

    // no change
    verifier.checkRecordedChanges("test1", script);

    // add a statement, but don't report the change.
    script.addChildToBack(IR.exprResult(IR.nullNode()));

    try {
      verifier.checkRecordedChanges("test2", script);
      assertWithMessage("exception expected").fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("changed scope not marked as changed");
    }
  }

  @Test
  public void testDeletedFunction() {
    Node script = parse("function A() {}");

    checkState(script.isScript());

    Compiler compiler = new Compiler();
    compiler.incrementChangeStamp();
    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(script);

    // no change
    verifier.checkRecordedChanges("test1", script);

    // remove the function. report the change in the script but not the function deletion.
    Node fnNode = script.getFirstChild();
    fnNode.detach();
    compiler.reportChangeToChangeScope(script);

    try {
      verifier.checkRecordedChanges("test2", script);
      assertWithMessage("exception expected").fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("deleted scope was not reported");
    }

    // now try again after reporting the function deletion.
    compiler.reportFunctionDeleted(fnNode);

    // no longer throws an exception.
    verifier.checkRecordedChanges("test2", script);
  }

  @Test
  public void testNotDeletedFunction() {
    Node script = parse("function A() {}");

    checkState(script.isScript());

    Compiler compiler = new Compiler();
    compiler.incrementChangeStamp();
    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(script);

    // no change
    verifier.checkRecordedChanges("test1", script);

    // mark the function deleted even though it's alive.
    Node fnNode = script.getFirstChild();
    compiler.reportFunctionDeleted(fnNode);

    try {
      verifier.checkRecordedChanges("test2", script);
      assertWithMessage("exception expected").fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("existing scope is improperly marked as deleted");
    }
  }

  @Test
  public void testChangeVerification() {
    Compiler compiler = new Compiler();

    Node mainScript = IR.script();
    Node main = IR.root(mainScript);

    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(main);

    verifier.checkRecordedChanges(main);

    mainScript.addChildToFront(
        IR.function(IR.name("A"), IR.paramList(), IR.block()));
    compiler.incrementChangeStamp();
    mainScript.setChangeTime(compiler.getChangeStamp());

    try {
      verifier.checkRecordedChanges(main);
      assertWithMessage("method should throw").fail();
    } catch (IllegalStateException e) {
      // ensure that e was thrown from the right code-path
      // especially important if it's something as frequent
      // as an IllegalArgumentException, etc.
      assertThat(e).hasMessageThat().startsWith("new scope not explicitly marked as changed:");
    }
  }

  private static Node parse(String js) {
    Compiler compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    Node n = compiler.parseTestCode(js);
    assertThat(compiler.getErrors()).isEmpty();
    return n;
  }

  private static Node getCallNode(Node n) {
    if (n.isCall()) {
      return n;
    }
    for (Node c : n.children()) {
      Node result = getCallNode(c);
      if (result != null) {
        return result;
      }
    }
    return null;
  }
}
