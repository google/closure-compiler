/*
 * Copyright 2025 The Closure Compiler Authors.
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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ChangeTrackerTest {

  @Test
  public void testReportChangeNoScopeFails() {
    ChangeTracker changeTracker = new ChangeTracker();

    Node detachedNode = IR.var(IR.name("foo"));

    Assert.assertThrows(
        IllegalStateException.class,
        () -> changeTracker.reportChangeToEnclosingScope(detachedNode));
  }

  @Test
  public void testReportChangeWithScopeSucceeds() {
    ChangeTracker changeTracker = new ChangeTracker();

    Node attachedNode = IR.var(IR.name("foo"));
    IR.function(IR.name("bar"), IR.paramList(), IR.block(attachedNode));

    // Succeeds without throwing an exception.
    changeTracker.reportChangeToEnclosingScope(attachedNode);
  }

  /**
   * See TimelineTest.java for the many timeline behavior tests that don't make sense to duplicate
   * here.
   */
  @Test
  public void testGetChangesAndDeletions_baseline() {
    ChangeTracker changeTracker = new ChangeTracker();

    // In the initial state nothing has been marked changed or deleted.
    assertThat(changeTracker.getChangedScopeNodesForPass("FunctionInliner")).isNull();
  }

  @Test
  public void testGetChangesAndDeletions_changeReportsVisible() {
    ChangeTracker changeTracker = new ChangeTracker();
    Node function1 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    Node function2 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    IR.root(IR.script(function1, function2));

    // Mark original baseline.
    var unused = changeTracker.getChangedScopeNodesForPass("FunctionInliner");

    // Mark both functions changed.
    changeTracker.reportChangeToChangeScope(function1);
    changeTracker.reportChangeToChangeScope(function2);

    // Both function1 and function2 are seen as changed and nothing is seen as deleted.
    assertThat(changeTracker.getChangedScopeNodesForPass("FunctionInliner"))
        .containsExactly(function1, function2);
  }

  @Test
  public void testGetChangesAndDeletions_deleteOverridesChange() {
    ChangeTracker changeTracker = new ChangeTracker();
    Node function1 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    Node function2 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    IR.root(IR.script(function1, function2));

    // Mark original baseline.
    var unused = changeTracker.getChangedScopeNodesForPass("FunctionInliner");

    // Mark both functions changed, then delete function2 and mark it deleted.
    changeTracker.reportChangeToChangeScope(function1);
    changeTracker.reportChangeToChangeScope(function2);
    function2.detach();
    changeTracker.reportFunctionDeleted(function2);

    // Now function1 will be seen as changed and function2 will be seen as deleted, since delete
    // overrides change.
    assertThat(changeTracker.getChangedScopeNodesForPass("FunctionInliner"))
        .containsExactly(function1);
  }

  @Test
  public void testGetChangesAndDeletions_changeDoesntOverrideDelete() {
    ChangeTracker changeTracker = new ChangeTracker();
    Node function1 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    Node function2 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    IR.root(IR.script(function1, function2));

    // Mark original baseline.
    var unused = changeTracker.getChangedScopeNodesForPass("FunctionInliner");

    // Mark function1 changed and function2 deleted, then try to mark function2 changed.
    changeTracker.reportChangeToChangeScope(function1);
    function2.detach();
    changeTracker.reportFunctionDeleted(function2);
    changeTracker.reportChangeToChangeScope(function2);

    // Now function1 will be seen as changed and function2 will be seen as deleted, since change
    // does not override delete.
    assertThat(changeTracker.getChangedScopeNodesForPass("FunctionInliner"))
        .containsExactly(function1);
  }

  @Test
  public void testGetChangesAndDeletions_onlySeesChangesSinceLastRequest() {
    ChangeTracker changeTracker = new ChangeTracker();
    Node function1 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    Node function2 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    IR.root(IR.script(function1, function2));

    // Mark original baseline.
    var unused = changeTracker.getChangedScopeNodesForPass("FunctionInliner");

    // Mark function1 changed and function2 deleted.
    changeTracker.reportChangeToChangeScope(function1);
    function2.detach();
    changeTracker.reportFunctionDeleted(function2);

    // Verify their respective states are seen.
    assertThat(changeTracker.getChangedScopeNodesForPass("FunctionInliner"))
        .containsExactly(function1);

    // Check states again. Should find nothing since nothing has changed since the last
    // 'FunctionInliner' request.
    assertThat(changeTracker.getChangedScopeNodesForPass("FunctionInliner")).isEmpty();
  }
}
