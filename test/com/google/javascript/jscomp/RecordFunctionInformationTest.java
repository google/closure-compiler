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

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link RecordFunctionInformation}
 *
 */

@RunWith(JUnit4.class)
public final class RecordFunctionInformationTest {

  @Test
  public void testFunction() {
    String g = "function g(){}";
    String fAndG = "function f(){" + g + "}";
    String js = "var h=" + fAndG + ";h()";

    FunctionInformationMap expected =
        FunctionInformationMap.newBuilder()
            .addEntry(
                FunctionInformationMap.Entry.newBuilder()
                    .setId(0)
                    .setSourceName("testcode")
                    .setLineNumber(1)
                    .setModuleName("")
                    .setSize(g.length())
                    .setName("f::g")
                    .setCompiledSource(g)
                    .build())
            .addEntry(
                FunctionInformationMap.Entry.newBuilder()
                    .setId(1)
                    .setSourceName("testcode")
                    .setLineNumber(1)
                    .setModuleName("")
                    .setSize(fAndG.length())
                    .setName("f")
                    .setCompiledSource(fAndG)
                    .build())
            .build();

    test(js, expected);
  }

  @Test
  public void testMotionPreservesOriginalSourceName() {
    String f = "function f(){}";
    String g = "function g(){}";

    String m0_before = f + g;
    String m1_before = "";

    JSModule[] modules = CompilerTestCase.createModules(m0_before, m1_before);
    Compiler compiler = compilerFor(modules);
    Node root = root(compiler);
    Node externsRoot = externs(root);
    Node mainRoot = main(root);

    Node nodeG = mainRoot.getFirstChild().getLastChild();
    mainRoot.getFirstChild().removeChild(nodeG);
    mainRoot.getLastChild().addChildToBack(nodeG.cloneTree());

    FunctionInformationMap expected =
        FunctionInformationMap.newBuilder()
            .addEntry(
                FunctionInformationMap.Entry.newBuilder()
                    .setId(0)
                    .setSourceName("i0.js")
                    .setLineNumber(1)
                    .setModuleName("m0")
                    .setSize(g.length())
                    .setName("f")
                    .setCompiledSource(f)
                    .build())
            .addEntry(
                FunctionInformationMap.Entry.newBuilder()
                    .setId(1)
                    .setSourceName("i0.js")
                    .setLineNumber(1)
                    .setModuleName("m1")
                    .setSize(g.length())
                    .setName("g")
                    .setCompiledSource(g)
                    .build())
            .build();

    test(compiler, externsRoot, mainRoot, expected);
  }


  private void test(String js, FunctionInformationMap expected) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    compiler.init(ImmutableList.of(SourceFile.fromCode("externs", "")),
                  ImmutableList.of(SourceFile.fromCode("testcode", js)),
                  options);
    test(compiler, expected);
  }

  private void test(Compiler compiler, FunctionInformationMap expected) {
    Node root = root(compiler);
    test(compiler, externs(root), main(root), expected);
  }

  private void test(Compiler compiler, Node externsRoot, Node mainRoot,
      FunctionInformationMap expected) {
    CollectFunctionNames collectFunctionNames = new CollectFunctionNames(compiler);
    collectFunctionNames.process(externsRoot, mainRoot);

    RecordFunctionInformation processor =
        new RecordFunctionInformation(compiler, collectFunctionNames.getFunctionNames());
    processor.process(externsRoot, mainRoot);
    FunctionInformationMap result = processor.getMap();
    assertThat(result).isEqualTo(expected);
  }

  private Compiler compilerFor(JSModule[] modules) {
      Compiler compiler = new Compiler();
      CompilerOptions options = new CompilerOptions();
      options.setEmitUseStrict(false);
      compiler.initModules(
          ImmutableList.of(SourceFile.fromCode("externs", "")),
          ImmutableList.copyOf(modules),
          options);
      return compiler;
  }

  private Node root(Compiler compiler) {
    return compiler.parseInputs();
  }

  private Node externs(Node root) {
    return root.getFirstChild();
  }

  private Node main(Node root) {
    return root.getSecondChild();
  }
}
