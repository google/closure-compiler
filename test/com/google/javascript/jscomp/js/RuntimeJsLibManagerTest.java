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

package com.google.javascript.jscomp.js;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.ChangeTracker;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.js.RuntimeJsLibManager.RuntimeLibraryMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RuntimeJsLibManagerTest {

  private static class StubResourceLoader implements RuntimeJsLibManager.ResourceProvider {
    private final ImmutableMap<String, String> resources;

    StubResourceLoader(ImmutableMap<String, String> resources) {
      this.resources = resources;
    }

    @Override
    public Node parse(String resourceName, String unused) {
      if (!resources.containsKey(resourceName)) {
        return null;
      }
      Compiler compiler = new Compiler();
      return compiler.parseSyntheticCode("lib.js", resources.get(resourceName));
    }
  }

  @Test
  public void noOpMode_doesNotRecordOrInject() {
    Node script = IR.script();
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.NO_OP,
            new StubResourceLoader(ImmutableMap.of("lib", "0")),
            new ChangeTracker(),
            () -> script);

    manager.ensureLibraryInjected("lib", /* force= */ false);

    assertThat(manager.getInjectedLibraries()).isEmpty();
  }

  @Test
  public void noOpMode_doesRecordAndInject_withForceInjection() {
    Node script = IR.script();
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.NO_OP,
            new StubResourceLoader(ImmutableMap.of("lib", "0")),
            new ChangeTracker(),
            () -> script);

    manager.ensureLibraryInjected("lib", /* force= */ true);

    assertThat(manager.getInjectedLibraries()).containsExactly("lib");
  }

  @Test
  public void recordOnlyMode_doesNotInject_butRecordsLibraries() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.RECORD_ONLY,
            new StubResourceLoader(ImmutableMap.of("lib", "0")),
            new ChangeTracker(),
            () -> IR.script());

    Node injected = manager.ensureLibraryInjected("lib", /* force= */ false);

    assertThat(injected).isNull();
    assertThat(manager.getInjectedLibraries()).containsExactly("lib");
  }

  @Test
  public void recordOnlyMode_doesRecordAndInject_withForceInjection() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.RECORD_ONLY,
            new StubResourceLoader(ImmutableMap.of("lib", "0")),
            new ChangeTracker(),
            () -> IR.script());

    manager.ensureLibraryInjected("lib", /* force= */ true);

    assertThat(manager.getInjectedLibraries()).containsExactly("lib");
  }

  @Test
  public void injectMode_doesRecordAndInject_andReturnsLastStatementInLibrary() {
    Node script = IR.script();
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.INJECT,
            new StubResourceLoader(
                ImmutableMap.of(
                    "lib",
                    """
                    function foo() {}
                    0;
                    """)),
            new ChangeTracker(),
            () -> script);

    Node injected = manager.ensureLibraryInjected("lib", /* force= */ false);

    assertThat(manager.getInjectedLibraries()).containsExactly("lib");
    assertNode(script).isEqualTo(js("function foo() {} 0;"));
    assertNode(script).hasSecondChildThat().isSameInstanceAs(injected);
  }

  @Test
  public void doesNotInjectSameLibraryTwice() {
    Node script = IR.script();
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.INJECT,
            new StubResourceLoader(ImmutableMap.of("lib", "0")),
            new ChangeTracker(),
            () -> script);

    Node injected1 = manager.ensureLibraryInjected("lib", /* force= */ false);
    Node injected2 = manager.ensureLibraryInjected("lib", /* force= */ false);

    assertNode(injected1).isSameInstanceAs(injected2);
    assertNode(script).hasOneChild();
  }

  @Test
  public void injectsBeforeExistingCode() {
    Node existing = IR.block();
    Node script = IR.script(existing);

    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.INJECT,
            new StubResourceLoader(ImmutableMap.of("lib", "0")),
            new ChangeTracker(),
            () -> script);

    Node injected = manager.ensureLibraryInjected("lib", /* force= */ false);

    assertNode(script).hasFirstChildThat().isEqualTo(injected);
    assertNode(script).hasSecondChildThat().isSameInstanceAs(existing);
  }

  @Test
  public void injectsAfterPreviouslyInjectedLib() {
    Node script = IR.script();
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.INJECT,
            new StubResourceLoader(ImmutableMap.of("lib0", "0", "lib1", "1")),
            new ChangeTracker(),
            () -> script);

    Node injected0 = manager.ensureLibraryInjected("lib0", /* force= */ false);
    Node injected1 = manager.ensureLibraryInjected("lib1", /* force= */ false);

    assertNode(script).hasFirstChildThat().isSameInstanceAs(injected0);
    assertNode(script).hasSecondChildThat().isSameInstanceAs(injected1);
    assertNode(script).isEqualTo(js("0; 1;"));
  }

  @Test
  public void injectsRequiredLibsOnlyOnce() {
    Node script = IR.script();
    var resourcesMap = ImmutableMap.<String, String>builder();
    // Create a dependency tree:
    //       (base)
    //      /      \
    //  (childA)   (childB)
    resourcesMap
        .put("base.js", "print('in BASE');")
        .put(
            "childA.js",
            """
            'require base.js';
            print('in CHILD_A');
            """)
        .put(
            "childB.js",
            """
            'require base.js';
            print('in CHILD_B');
            """);
    var resources = new StubResourceLoader(resourcesMap.buildOrThrow());
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.INJECT, resources, new ChangeTracker(), () -> script);

    var childA = manager.ensureLibraryInjected("childA.js", /* force= */ false);
    var childB = manager.ensureLibraryInjected("childB.js", /* force= */ false);

    assertThat(manager.getInjectedLibraries()).containsExactly("base.js", "childA.js", "childB.js");
    assertNode(script).hasSecondChildThat().isSameInstanceAs(childA);
    assertNode(script).hasLastChildThat().isSameInstanceAs(childB);
    assertNode(script)
        .isEqualTo(
            js(
                """
                print('in BASE');
                print('in CHILD_A');
                print('in CHILD_B');
                """));
  }

  @Test
  public void injectMode_doesNotPassAlongUseStrictDirective() {
    Node script = IR.script();
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.INJECT,
            new StubResourceLoader(
                ImmutableMap.of(
                    "lib",
                    """
                    'use strict';
                    0;
                    """)),
            new ChangeTracker(),
            () -> script);

    Node injected = manager.ensureLibraryInjected("lib", /* force= */ false);

    assertNode(injected).isEqualTo(js("0;").getOnlyChild());
    assertNode(script).hasOneChild();
    assertThat(manager.getInjectedLibraries()).containsExactly("lib");
  }

  @Test
  public void injectingMissingLib_throwsException() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.INJECT,
            new StubResourceLoader(ImmutableMap.of()),
            new ChangeTracker(),
            () -> IR.script());

    assertThrows(
        NullPointerException.class, () -> manager.ensureLibraryInjected("lib", /* force= */ false));
  }

  @Test
  public void injectLibForField_addsCorrespondingLibrary() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.RECORD_ONLY,
            new StubResourceLoader(ImmutableMap.of()),
            new ChangeTracker(),
            () -> IR.script());

    manager.injectLibForField("$jscomp.inherits");

    assertThat(manager.getInjectedLibraries()).containsExactly("es6/util/inherits");
  }

  @Test
  public void injectLibForField_crashesIfNonQualifiedName() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.RECORD_ONLY,
            new StubResourceLoader(ImmutableMap.of()),
            new ChangeTracker(),
            () -> IR.script());

    assertThrows(IllegalArgumentException.class, () -> manager.injectLibForField("3 + 4"));
  }

  @Test
  public void injectLibForField_crashesIfPassedNonJscompName() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.RECORD_ONLY,
            new StubResourceLoader(ImmutableMap.of()),
            new ChangeTracker(),
            () -> IR.script());

    assertThrows(IllegalArgumentException.class, () -> manager.injectLibForField("foobar"));
  }

  @Test
  public void injectLibForField_crashesIfPassedJustJscomp() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.RECORD_ONLY,
            new StubResourceLoader(ImmutableMap.of()),
            new ChangeTracker(),
            () -> IR.script());

    assertThrows(IllegalArgumentException.class, () -> manager.injectLibForField("$jscomp"));
  }

  @Test
  public void injectLibForField_crashesIfPassedMissingProperty() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.RECORD_ONLY,
            new StubResourceLoader(ImmutableMap.of()),
            new ChangeTracker(),
            () -> IR.script());

    var ex =
        assertThrows(
            NullPointerException.class, () -> manager.injectLibForField("$jscomp.doesNotExist"));
    assertThat(ex).hasMessageThat().contains("Cannot find definition of $jscomp.doesNotExist");
  }

  @Test
  public void injectLibForField_thenAssertInjectedAndGetQualifiedName_passes() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.RECORD_ONLY,
            new StubResourceLoader(ImmutableMap.of()),
            new ChangeTracker(),
            () -> IR.script());

    manager.injectLibForField("$jscomp.inherits");
    RuntimeJsLibManager.JsLibField field = manager.getJsLibField("$jscomp.inherits");

    assertThat(field.assertInjected().qualifiedName()).isEqualTo("$jscomp.inherits");
  }

  @Test
  public void noinjectLibForField_thenAssertInjected_recordAndValidateFieldsMode_fails() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.RECORD_AND_VALIDATE_FIELDS,
            new StubResourceLoader(ImmutableMap.of()),
            new ChangeTracker(),
            () -> IR.script());

    RuntimeJsLibManager.JsLibField field = manager.getJsLibField("$jscomp.inherits");

    assertThrows(IllegalStateException.class, field::assertInjected);
  }

  @Test
  public void noinjectLibForField_thenAssertInjected_injectMode_fails() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.RECORD_AND_VALIDATE_FIELDS,
            new StubResourceLoader(ImmutableMap.of()),
            new ChangeTracker(),
            () -> IR.script());

    RuntimeJsLibManager.JsLibField field = manager.getJsLibField("$jscomp.inherits");

    assertThrows(IllegalStateException.class, field::assertInjected);
  }

  @Test
  public void noinjectLibForField_thenAssertInjectedAndGetQualifiedName_recordOnlyMode_succeeds() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.RECORD_ONLY,
            new StubResourceLoader(ImmutableMap.of()),
            new ChangeTracker(),
            () -> IR.script());

    RuntimeJsLibManager.JsLibField field = manager.getJsLibField("$jscomp.inherits");

    var unused = field.assertInjected().qualifiedName();
  }

  @Test
  public void noinjectLibForField_thenAssertInjectedAndGetQualifiedName_noOpMode_succeeds() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.NO_OP,
            new StubResourceLoader(ImmutableMap.of()),
            new ChangeTracker(),
            () -> IR.script());

    RuntimeJsLibManager.JsLibField field = manager.getJsLibField("$jscomp.inherits");

    var unused = field.assertInjected().qualifiedName();
  }

  @Test
  public void field_withoutInjection_allowsCallingMatches() {
    RuntimeJsLibManager manager =
        RuntimeJsLibManager.create(
            RuntimeLibraryMode.RECORD_AND_VALIDATE_FIELDS,
            new StubResourceLoader(ImmutableMap.of()),
            new ChangeTracker(),
            () -> IR.script());

    RuntimeJsLibManager.JsLibField field = manager.getJsLibField("$jscomp.inherits");

    assertThat(field.matches(IR.name("foo"))).isFalse();
    assertThat(field.matches(JSCOMP_INHERITS)).isTrue();
  }

  private static Node js(String code) {
    Compiler compiler = new Compiler();
    return compiler.parseSyntheticCode("lib.js", code);
  }

  private static final Node JSCOMP_INHERITS = IR.getprop(IR.name("$jscomp"), "inherits");
}
