/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.common.truth.Correspondence;
import com.google.javascript.jscomp.testing.JSCompCorrespondences;
import com.google.javascript.rhino.Node;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OptimizeCallsTest extends CompilerTestCase {

  // Will be assigned with the references collected from the most recent pass.
  private OptimizeCalls.ReferenceMap references;

  // Whether to consider externs during the next collection. Must be explicitly set.
  private Boolean considerExterns = null;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize(); // Required for `OptimizeCalls`.
    // Even if we're ignoring externs we need to know their names to do that.
    enableGatherExternProperties();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return OptimizeCalls.builder()
        .setCompiler(compiler)
        .setConsiderExterns(considerExterns)
        .addPass(
            (externs, root, references) -> {
              this.references = references;
            })
        .build();
  }

  @Test
  public void testReferenceCollection_ignoringExterns() throws Exception {
    considerExterns = false;

    test(
        externs(
            lines(
                "function externFoo(dummyParam1, dummyParam2) {",
                "  deadRef1;",
                "}",
                "",
                "var externSpace = {};",
                "",
                "externSpace.externProp = {",
                "  externChildProp: 0,",
                "",
                "  externChildMethod(dummyParam3) { }",
                "};",
                "",
                "class ExternBar { }")),
        srcs(
            lines(
                "function foo(param1, param2) {",
                "  var liveRef1;",
                "  new ExternBar();",
                "}",
                "",
                "var space = {};",
                "",
                "space.prop = {",
                "  childProp: foo,",
                "",
                "  childMethod(param3, externFoo) { }",
                "};",
                "",
                "class Bar { }",
                "",
                "new Bar()",
                "space.prop.childMethod(externSpace);")));

    assertThat(references.getNameReferences())
        .comparingElementsUsing(KEY_EQUALITY)
        // Only global names should be collected.
        .containsExactly("Bar", "foo", "space");

    assertThat(references.getPropReferences())
        .comparingElementsUsing(KEY_EQUALITY)
        .containsExactly("childMethod", "childProp", "prop");
  }

  @Test
  public void testReferenceCollection_consideringExterns() throws Exception {
    considerExterns = true;

    test(
        externs(
            lines(
                "function externFoo(dummyParam1, dummyParam2) {",
                "  deadRef1;",
                "}",
                "",
                "var externSpace = {};",
                "",
                "externSpace.externProp = {",
                "  externChildProp: 0,",
                "",
                "  externChildMethod(dummyParam3) { }",
                "};",
                "",
                "class ExternBar { }")),
        srcs(
            lines(
                "function foo(param1, param2) {",
                "  liveRef1;",
                "  new ExternBar();",
                "}",
                "",
                "var space = {};",
                "",
                "space.prop = {",
                "  childProp: foo,",
                "",
                "  childMethod(param3, externFoo) { }",
                "};",
                "",
                "class Bar { }",
                "",
                "new Bar()",
                "space.prop.childMethod(externSpace);")));

    assertThat(references.getNameReferences())
        .comparingElementsUsing(KEY_EQUALITY)
        // Only global names should be collected.
        .containsExactly("Bar", "ExternBar", "externFoo", "externSpace", "foo", "space");

    assertThat(references.getPropReferences())
        .comparingElementsUsing(KEY_EQUALITY)
        .containsExactly(
            "childMethod",
            "childProp",
            "externChildMethod",
            "externChildProp",
            "externProp",
            "prop");
  }

  @Test
  public void testReferenceCollection_doesNotCollectTheEmptyName() {
    considerExterns = true;

    test(srcs("var f = function() { }"));

    assertThat(references.getNameReferences())
        .comparingElementsUsing(KEY_EQUALITY)
        // Anonymous functions have the empty string as a name.
        .doesNotContain("");
  }

  private static final Correspondence<Map.Entry<String, Node>, String> KEY_EQUALITY =
      JSCompCorrespondences.transforming(Map.Entry::getKey, "has key");
}
