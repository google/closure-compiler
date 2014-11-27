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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

/**
 * Tests for {@link RecordFunctionInformation}
 *
 */

public class RecordFunctionInformationTest extends TestCase {

  public void testFunction() {
    String g = "function g(){}";
    String fAndG = "function f(){" + g + "}";
    String js = "var h=" + fAndG + ";h()";

    FunctionInformationMap.Builder expected =
        FunctionInformationMap.newBuilder();
    expected.addEntry(
        FunctionInformationMap.Entry.newBuilder()
        .setId(0)
        .setSourceName("testcode")
        .setLineNumber(1)
        .setModuleName("")
        .setSize(g.length())
        .setName("f::g")
        .setCompiledSource(g).build());
    expected.addEntry(
        FunctionInformationMap.Entry.newBuilder()
        .setId(1)
        .setSourceName("testcode")
        .setLineNumber(1)
        .setModuleName("")
        .setSize(fAndG.length())
        .setName("f")
        .setCompiledSource(fAndG).build());

    test(js, expected.build());
  }

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

    String m0_after = f;
    String m1_after = g;
    Node nodeG = mainRoot.getFirstChild().getLastChild();
    mainRoot.getFirstChild().removeChild(nodeG);
    mainRoot.getLastChild().addChildrenToBack(nodeG.cloneTree());

    FunctionInformationMap.Builder expected =
      FunctionInformationMap.newBuilder();
    expected.addEntry(
        FunctionInformationMap.Entry.newBuilder()
        .setId(0)
        .setSourceName("i0")
        .setLineNumber(1)
        .setModuleName("m0")
        .setSize(g.length())
        .setName("f")
        .setCompiledSource(f).build());
    expected.addEntry(
        FunctionInformationMap.Entry.newBuilder()
        .setId(1)
        .setSourceName("i0")
        .setLineNumber(1)
        .setModuleName("m1")
        .setSize(g.length())
        .setName("g")
        .setCompiledSource(g).build());

    test(compiler, externsRoot, mainRoot, expected.build());
  }


  private void test(String js, FunctionInformationMap expected) {
    Compiler compiler = new Compiler();
    compiler.init(ImmutableList.of(SourceFile.fromCode("externs", "")),
                  ImmutableList.of(SourceFile.fromCode("testcode", js)),
                  new CompilerOptions());
    test(compiler, expected);
  }

  private void test(JSModule[] modules, FunctionInformationMap expected) {
    test(compilerFor(modules), expected);
  }

  private void test(Compiler compiler, FunctionInformationMap expected) {
    Node root = root(compiler);
    test(compiler, externs(root), main(root), expected);
  }

  private void test(Compiler compiler, Node externsRoot, Node mainRoot,
      FunctionInformationMap expected) {
    FunctionNames functionNames = new FunctionNames(compiler);
    functionNames.process(externsRoot, mainRoot);

    RecordFunctionInformation processor =
        new RecordFunctionInformation(compiler, functionNames);
    processor.process(externsRoot, mainRoot);
    FunctionInformationMap result = processor.getMap();
    assertEquals(expected, result);
  }

  private Compiler compilerFor(JSModule[] modules) {
      Compiler compiler = new Compiler();
      compiler.initModules(
          ImmutableList.of(SourceFile.fromCode("externs", "")),
          Lists.newArrayList(modules),
          new CompilerOptions());
      return compiler;
  }

  private Node root(Compiler compiler) {
    return compiler.parseInputs();
  }

  private Node externs(Node root) {
    return root.getFirstChild();
  }

  private Node main(Node root) {
    return root.getFirstChild().getNext();
  }
}
