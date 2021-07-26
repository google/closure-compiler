/*
 * Copyright 2021 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.serialization;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.LinkedHashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class JSTypeColorIdHasherTest extends CompilerTestCase {

  private JSTypeColorIdHasher hasher;
  private LinkedHashMap<String, ColorId> labelToColorId;
  private LinkedHashMultimap<ColorId, JSType> colorIdToJSTypes; // Useful for debugging.

  private static final String CLOSURE_GLOBALS =
      lines(
          "var goog = {};", //
          "goog.loadModule = function(def) {};",
          "goog.module = function(name) {};",
          "goog.provide = function(id) {};");

  @Parameterized.Parameter public boolean rewriteClosureModules;

  @Parameterized.Parameters
  public static ImmutableList<Boolean> cases() {
    return ImmutableList.of(true, false);
  }

  public JSTypeColorIdHasherTest() {
    super(CLOSURE_GLOBALS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    this.enableCreateModuleMap();
    this.enableTypeCheck();

    if (this.rewriteClosureModules) {
      this.enableRewriteClosureCode();
    }
  }

  private class LabeledTypeFinder extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isLabel()) {
        return;
      }

      String label = n.getFirstChild().getString();
      ObjectType type = n.getSecondChild().getFirstChild().getJSType().toMaybeObjectType();
      ColorId id = hasher.hashObjectType(type);

      assertThat(labelToColorId).doesNotContainKey(label);
      labelToColorId.put(label, id);
      colorIdToJSTypes.put(id, type);
    }
  }

  private ImmutableSet<ColorId> labelIdSet() {
    return ImmutableSet.copyOf(this.labelToColorId.values());
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    this.hasher = new JSTypeColorIdHasher(compiler.getTypeRegistry());
    this.labelToColorId = new LinkedHashMap<>();
    this.colorIdToJSTypes = LinkedHashMultimap.create();

    return (Node externs, Node root) ->
        NodeTraversal.traverseRoots(compiler, new LabeledTypeFinder(), externs, root);
  }

  @Test
  public void haveDifferentIds_ctorInstancePrototype() {
    testSame(
        lines(
            "class Foo { }", //
            "",
            "CTOR: Foo;",
            "INST: new Foo();",
            "PROTO: Foo.prototype;"));

    assertThat(this.labelToColorId.values()).containsNoDuplicates();
    assertThat(this.labelToColorId.keySet()).containsExactly("CTOR", "INST", "PROTO");
  }

  @Test
  public void haveDifferentIds_qnamesWithSameEnding() {
    testSame(
        lines(
            "Foo = class { }", //
            "var a = {}; a.Foo = class { }",
            "var b = {}; b.Foo = class { }",
            "",
            "FOO: Foo;",
            "A_FOO: a.Foo;",
            "B_FOO: b.Foo;"));

    assertThat(this.labelToColorId.values()).containsNoDuplicates();
    assertThat(this.labelToColorId.keySet()).containsExactly("FOO", "A_FOO", "B_FOO");
  }

  @Test
  public void haveDifferentIds_classWithSameName_inDifferentGoogModules() {
    testSame(
        srcs(
            lines(
                "goog.module('a');", //
                "class Foo { }",
                "A_FOO: Foo;"),
            lines(
                "goog.module('b');", //
                "class Foo { }",
                "B_FOO: Foo;")));

    assertThat(this.labelToColorId.values()).containsNoDuplicates();
    assertThat(this.labelToColorId.keySet()).containsExactly("A_FOO", "B_FOO");
  }

  @Test
  public void haveDifferentIds_classWithSameName_inDifferentGoogModules_inLoadModuleFile() {
    testSame(
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('a');", //
            "",
            "  class Foo { }",
            "  A_FOO: Foo;",
            "",
            " return exports;",
            "});",
            "",
            "goog.loadModule(function(exports) {",
            "  goog.module('b');", //
            "",
            "  class Foo { }",
            "  B_FOO: Foo;",
            "",
            " return exports;",
            "});"));

    assertThat(this.labelToColorId.values()).containsNoDuplicates();
    assertThat(this.labelToColorId.keySet()).containsExactly("A_FOO", "B_FOO");
  }

  @Test
  public void haveDifferentIds_classWithSameName_insideAndOutsideGoogModule() {
    testSame(
        srcs(
            lines(
                "class Foo { }", //
                "FOO: Foo;"),
            lines(
                "goog.module('b');", //
                "class Foo { }",
                "B_FOO: Foo;")));

    assertThat(this.labelToColorId.values()).containsNoDuplicates();
    assertThat(this.labelToColorId.keySet()).containsExactly("FOO", "B_FOO");
  }

  @Test
  public void haveDifferentIds_anonymousTypes_withDifferentPropertyNames() {
    testSame(
        lines(
            "function withC() { };",
            "/** @type {number} */",
            "withC.c = 0;",
            "",
            "WITH_A: ({a: 0});", //
            "WITH_B: ({b: 0});",
            "WITH_C: withC;"));

    assertThat(this.labelToColorId.values()).containsNoDuplicates();
    assertThat(this.labelToColorId.keySet()).containsExactly("WITH_A", "WITH_B", "WITH_C");
  }

  @Test
  public void haveSameIds_anonymousTypes_withSamePropertyNames_andDiffererntPropertyTypes() {
    testSame(
        lines(
            "function withC() { };",
            "/** @type {number} */",
            "withC.c = 0;",
            "",
            "WITH_A: ({a: null});", //
            "WITH_B: ({b: ''});",
            "WITH_C: withC;"));

    assertThat(this.labelIdSet()).hasSize(3);
    assertThat(this.labelToColorId.keySet()).containsExactly("WITH_A", "WITH_B", "WITH_C");
  }

  // TODO(b/185519307): We should probably differentiate the scoped types from the unscoped one, but
  // it's hard to check the scoping after rewriting.
  @Test
  public void haveSameIds_nominalTypes_fromDifferentScopes_fromSameGoogModule() {
    testSame(
        lines(
            "goog.module('a');", //
            "",
            "class Foo { }",
            "OUTER_FOO: Foo;",
            "",
            "function scope0() {",
            "  class Foo { }",
            "  FOO_0: Foo;",
            "}",
            "",
            "function scope1() {",
            "  class Foo { }",
            "  FOO_1: Foo;",
            "}",
            "",
            "{",
            "  class Foo { }",
            "  FOO_BLOCK: Foo;",
            "}"));

    // TODO(b/185519307): With rewriting enabled, the reference name of OUTER_FOO is changed.
    int expectedIdCount = this.rewriteClosureModules ? 2 : 1;

    assertThat(this.labelIdSet()).hasSize(expectedIdCount);
    assertThat(this.labelToColorId.keySet())
        .containsExactly("OUTER_FOO", "FOO_0", "FOO_1", "FOO_BLOCK");
  }

  @Test
  public void haveRecompileStableIds_nominalTypes() {
    String code =
        lines(
            "class Foo { }", //
            "CLASS: Foo;",
            "INST: new Foo();",
            "PROTO: Foo.prototype;",
            "",
            "function namedFunction() { }",
            "FUNC: namedFunction;",
            "",
            "/** @enum */ const Enum = {};",
            "ENUM: Enum;");

    testSame(code);
    LinkedHashMap<String, ColorId> idsFromFirstCompile = this.labelToColorId;

    testSame(code);
    LinkedHashMap<String, ColorId> idsFromSecondCompile = this.labelToColorId;

    assertThat(idsFromFirstCompile).isNotEmpty();
    assertThat(idsFromFirstCompile).isNotSameInstanceAs(idsFromSecondCompile);
    assertThat(idsFromFirstCompile).isEqualTo(idsFromSecondCompile);
  }

  @Test
  public void haveRecompileStableIds_anonymousTypes() {
    String code =
        lines(
            "LAMBDA: (function() { });",
            "DOC_LAMBDA: /** @type {function()} */ (function() {});",
            "",
            "RECORD: ({a: 0});",
            "DOC_RECORD: /** @type {{a: (number|undefined)}} */ ({});");

    testSame(code);
    LinkedHashMap<String, ColorId> idsFromFirstCompile = this.labelToColorId;

    testSame(code);
    LinkedHashMap<String, ColorId> idsFromSecondCompile = this.labelToColorId;

    assertThat(idsFromFirstCompile).isNotEmpty();
    assertThat(idsFromFirstCompile).isNotSameInstanceAs(idsFromSecondCompile);
    assertThat(idsFromFirstCompile).isEqualTo(idsFromSecondCompile);
  }

  @Test
  public void haveHeaderDerivableIds_nominalTypes() {
    testSame(
        lines(
            "goog.module('a');",
            "",
            "class Foo {", //
            "  /** @return {number} */",
            "  someMethod() {",
            "    return this.toString().length;",
            "  }",
            "}",
            "CLASS: Foo;",
            "INST: new Foo();",
            "PROTO: Foo.prototype;",
            "",
            "  /** @return {number} */",
            "function namedFunction() {",
            "  return this.toString().length;",
            "}",
            "FUNC: namedFunction;",
            "",
            "/** @enum */ const Enum = {",
            "  A: 0,",
            "};",
            "ENUM: Enum;"));
    LinkedHashMap<String, ColorId> idsFromSource = this.labelToColorId;

    testSame(
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('a');",
            "",
            "  class Foo {", //
            "    /** @return {number} */",
            "    someMethod() {",
            "    }",
            "  }",
            "  CLASS: Foo;",
            "  INST: new Foo();",
            "  PROTO: Foo.prototype;",
            "",
            "    /** @return {number} */",
            "  function namedFunction() {",
            "  }",
            "  FUNC: namedFunction;",
            "",
            "  /** @enum */ const Enum = {",
            "    A: 0,",
            "  };",
            "  ENUM: Enum;",
            "",
            "  return exports;",
            "});"));
    LinkedHashMap<String, ColorId> idsFromHeader = this.labelToColorId;

    assertThat(idsFromSource).isNotEmpty();
    assertThat(idsFromSource).isNotSameInstanceAs(idsFromHeader);
    assertThat(idsFromSource).isEqualTo(idsFromHeader);
  }

  @Test
  public void haveHeaderDerivableIds_anonymousTypes() {
    testSame(
        lines(
            "goog.module('a');",
            "",
            "LAMBDA: (function() { });",
            "DOC_LAMBDA: /** @type {function()} */ (function() {});",
            "",
            "RECORD: ({a: 0});",
            "DOC_RECORD: /** @type {{a: (number|undefined)}} */ ({});"));
    LinkedHashMap<String, ColorId> idsFromSource = this.labelToColorId;

    testSame(
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('a');",
            "",
            "  LAMBDA: (function() { });",
            "  DOC_LAMBDA: /** @type {function()} */ (function() {});",
            "",
            "  RECORD: ({a: 0});",
            "  DOC_RECORD: /** @type {{a: (number|undefined)}} */ ({});",
            "",
            "  return exports;",
            "});"));
    LinkedHashMap<String, ColorId> idsFromHeader = this.labelToColorId;

    assertThat(idsFromSource).isNotEmpty();
    assertThat(idsFromSource).isNotSameInstanceAs(idsFromHeader);
    assertThat(idsFromSource).isEqualTo(idsFromHeader);
  }

  @Test
  public void returnsNativeId_forNativeTypes() {
    JSTypeRegistry registry = new JSTypeRegistry(ErrorReporter.ALWAYS_THROWS_INSTANCE);
    this.hasher = new JSTypeColorIdHasher(registry);

    JSTypeColorIdHasher.NATIVE_TYPE_TO_ID.forEach(
        (n, i) -> {
          ObjectType t = registry.getNativeObjectType(n);
          assertThat(this.hasher.hashObjectType(t)).isEqualTo(i);
        });
  }
}
