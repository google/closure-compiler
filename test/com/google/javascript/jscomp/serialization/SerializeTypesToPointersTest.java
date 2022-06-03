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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CompilerTestCase.lines;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.TypeCheck;
import com.google.javascript.jscomp.type.ClosureReverseAbstractInterpreter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SerializeTypesToPointersTest {

  private Compiler compiler;
  private StringPool.Builder stringPoolBuilder;

  @Before
  public void setUp() {
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    compiler = new Compiler();
    compiler.initOptions(options);
    stringPoolBuilder = StringPool.builder();
  }

  @Test
  public void outputsTypePointerForClass() {
    Node src = parseAndTypecheckFiles("class Foo {}");
    JSType fooCtorType = getGlobalType("Foo").toObjectType().getConstructor();

    SerializeTypesToPointers serializer =
        SerializeTypesToPointers.create(
            compiler, stringPoolBuilder, SerializationOptions.INCLUDE_DEBUG_INFO);
    serializer.gatherTypesOnAst(src);

    assertThat(serializer.getTypePointersByJstype()).containsKey(fooCtorType);

    int fooCtorPointer = serializer.getTypePointersByJstype().get(fooCtorType);
    assertThat(
            serializer
                .getTypePool()
                .getType(TypePointers.trimOffset(fooCtorPointer))
                .getObject()
                .getMarkedConstructor())
        .isTrue();
  }

  @Test
  public void serializesPropertiesReferencedInSources() {
    Node src = parseAndTypecheckFiles("class Foo { serializeMe() {} } Foo.prototype;");
    JSType fooPrototypeType = getGlobalType("Foo").toObjectType().getImplicitPrototype();

    SerializeTypesToPointers serializer =
        SerializeTypesToPointers.create(
            compiler, stringPoolBuilder, SerializationOptions.INCLUDE_DEBUG_INFO);
    serializer.gatherTypesOnAst(src);

    assertThat(serializer.getTypePointersByJstype()).containsKey(fooPrototypeType);
    int fooPrototypePointer = serializer.getTypePointersByJstype().get(fooPrototypeType);
    assertThat(
            serializer
                .getTypePool()
                .getType(TypePointers.trimOffset(fooPrototypePointer))
                .getObject()
                .getOwnPropertyList())
        .isEqualTo(findAllInStringPool("serializeMe"));
  }

  @Test
  public void doesNotSerializePropertiesOnlyReferencedInTypeSummary() {
    Node root =
        parseAndTypecheckFiles(
            "/** @typeSummary */ class Foo { serializeMe() {} andMe() {} doNotSerializeMe() {} }",
            "new Foo().serializeMe(); const otherObj = {andMe: 0}; Foo.prototype;");
    JSType fooPrototypeType = getGlobalType("Foo").toObjectType().getImplicitPrototype();

    SerializeTypesToPointers serializer =
        SerializeTypesToPointers.create(
            compiler, stringPoolBuilder, SerializationOptions.INCLUDE_DEBUG_INFO);
    serializer.gatherTypesOnAst(root);

    assertThat(serializer.getTypePointersByJstype()).containsKey(fooPrototypeType);
    int fooPrototypePointer = serializer.getTypePointersByJstype().get(fooPrototypeType);
    assertThat(
            serializer
                .getTypePool()
                .getType(TypePointers.trimOffset(fooPrototypePointer))
                .getObject()
                .getOwnPropertyList())
        .isEqualTo(findAllInStringPool("andMe", "serializeMe"));
  }

  @Test
  public void serializePropertiesInNonTypeSummaryExterns() {
    Node root =
        parseAndTypecheckFiles(
            "/** @typeSummary @externs */ class Foo { serializeMe() {} doNotSerializeMe() {} }",
            lines(
                "/** @externs */",
                "class Bar { serializeMe() {} }",
                "/** @type {string} */",
                "Foo.prototype.andMe;"));
    JSType fooPrototypeType = getGlobalType("Foo").toObjectType().getImplicitPrototype();

    SerializeTypesToPointers serializer =
        SerializeTypesToPointers.create(
            compiler, stringPoolBuilder, SerializationOptions.INCLUDE_DEBUG_INFO);
    serializer.gatherTypesOnAst(root);

    assertThat(serializer.getTypePointersByJstype()).containsKey(fooPrototypeType);
    int fooPrototypePointer = serializer.getTypePointersByJstype().get(fooPrototypeType);
    assertThat(
            serializer
                .getTypePool()
                .getType(TypePointers.trimOffset(fooPrototypePointer))
                .getObject()
                .getOwnPropertyList())
        .isEqualTo(findAllInStringPool("andMe", "serializeMe"));
  }

  JSType getGlobalType(String typeName) {
    return compiler.getTypeRegistry().getGlobalType(typeName);
  }

  private Node parseAndTypecheckFiles(String... files) {
    Node root = IR.root();
    int index = 0;
    for (String file : files) {
      root.addChildToBack(compiler.parseSyntheticCode("test_" + index++, file));
    }
    assertThat(compiler.getErrors()).isEmpty();
    IR.root(/* externs */ IR.root(), /* js */ root); // make this a valid AST
    new TypeCheck(
            compiler,
            new ClosureReverseAbstractInterpreter(compiler.getTypeRegistry()),
            compiler.getTypeRegistry())
        .processForTesting(null, root);
    return root;
  }

  private int findInStringPool(String str) {
    return this.stringPoolBuilder.put(str);
  }

  private ImmutableList<Integer> findAllInStringPool(String... str) {
    return stream(str).map(this::findInStringPool).collect(toImmutableList());
  }
}
